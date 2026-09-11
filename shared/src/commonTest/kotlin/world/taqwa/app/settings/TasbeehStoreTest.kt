package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.tasbeeh.TasbeehState
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasbeehStoreTest {
    // Never pasted raw into source (the format's own rule): U+001F is invisible in an editor.
    private val fs = '\u001F'.toString()

    private fun dataStore() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-tasbeeh-${Random.nextULong()}.preferences_pb".toPath()
    }

    @Test
    fun theSelectionStartsAtTheDefaultAndFollowsSelect() = runTest {
        val store = TasbeehStore(dataStore())
        assertEquals("after_prayer", store.selectedId.first())
        store.select("astaghfirullah")
        assertEquals("astaghfirullah", store.selectedId.first())
    }

    @Test
    fun everyPresetKeepsItsOwnCountAndRound() = runTest {
        val store = TasbeehStore(dataStore())
        assertEquals(TasbeehState("after_prayer", 0, 1), store.stateOf("after_prayer").first())
        store.save(TasbeehState("after_prayer", 67, 3))
        store.save(TasbeehState("subhanallah", 12, 1))
        assertEquals(TasbeehState("after_prayer", 67, 3), store.stateOf("after_prayer").first())
        assertEquals(TasbeehState("subhanallah", 12, 1), store.stateOf("subhanallah").first())
        assertEquals(TasbeehState("allahu_akbar", 0, 1), store.stateOf("allahu_akbar").first())
    }

    @Test
    fun aMalformedStateIsReadAsAFreshOne() = runTest {
        val ds = dataStore()
        ds.edit { it[stringPreferencesKey("tasbeeh_state_after_prayer")] = "ninety" }
        assertEquals(TasbeehState("after_prayer", 0, 1), TasbeehStore(ds).stateOf("after_prayer").first())
    }

    @Test
    fun addCustomStampsTheIdTrimsThePhraseAndBoundsTheTarget() = runTest {
        val store = TasbeehStore(dataStore()) { 1_700L }
        val added = store.addCustom("  " + "x".repeat(70) + "  ", 5_000)
        assertEquals("custom_1700", added.id)
        assertEquals("x".repeat(60), added.parts.first().dhikr.arabic)
        assertEquals(1_000, added.total)
        assertTrue(added.custom)
        assertEquals(listOf(added), store.customPresets.first())
    }

    @Test
    fun aTargetBelowOneIsLiftedToOne() = runTest {
        val store = TasbeehStore(dataStore()) { 9L }
        assertEquals(1, store.addCustom("ya latif", 0).total)
    }

    @Test
    fun customPresetsComeBackInCreationOrderEvenWhenTwoShareAMillisecond() = runTest {
        var clock = 100L
        val store = TasbeehStore(dataStore()) { clock }
        val first = store.addCustom("first", 33)
        val second = store.addCustom("second", 33)
        clock = 300L
        val third = store.addCustom("third", 33)
        assertEquals("custom_100", first.id)
        assertEquals("custom_101", second.id)
        assertEquals("custom_300", third.id)
        assertEquals(listOf("first", "second", "third"), store.customPresets.first().map { it.parts.first().dhikr.arabic })
    }

    @Test
    fun removingACustomPresetClearsItsCountAndFallsTheSelectionBack() = runTest {
        val store = TasbeehStore(dataStore()) { 42L }
        val mine = store.addCustom("ya latif", 100)
        store.select(mine.id)
        store.save(TasbeehState(mine.id, 80, 2))
        store.removeCustom(mine.id)
        assertEquals(emptyList(), store.customPresets.first())
        assertEquals("after_prayer", store.selectedId.first())
        assertEquals(TasbeehState(mine.id, 0, 1), store.stateOf(mine.id).first())
    }

    @Test
    fun removingAPresetThatIsNotSelectedLeavesTheSelectionAlone() = runTest {
        var clock = 1L
        val store = TasbeehStore(dataStore()) { clock++ }
        val kept = store.addCustom("kept", 33)
        val gone = store.addCustom("gone", 33)
        store.select(kept.id)
        store.removeCustom(gone.id)
        assertEquals(listOf(kept), store.customPresets.first())
        assertEquals(kept.id, store.selectedId.first())
    }

    @Test
    fun malformedCustomEntriesAreSkippedNotThrownOn() = runTest {
        val ds = dataStore()
        ds.edit {
            it[stringSetPreferencesKey("tasbeeh_custom")] = setOf(
                "custom_10${fs}ya latif${fs}100",
                "custom_20${fs}no target",
                "custom_30${fs}not a number${fs}many",
                "${fs}blank id${fs}33",
                "garbage",
            )
        }
        val presets = TasbeehStore(ds).customPresets.first()
        assertEquals(listOf("custom_10"), presets.map { it.id })
        assertEquals("ya latif", presets.first().parts.first().dhikr.arabic)
        assertNull(presets.first().parts.first().dhikr.meaning)
    }
}
