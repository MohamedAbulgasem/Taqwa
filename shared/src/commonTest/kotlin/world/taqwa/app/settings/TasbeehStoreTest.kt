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
import kotlin.test.assertFailsWith
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

    /**
     * The store used to take a blank phrase and write it, and `parseCustom` then dropped the
     * entry on the way back out — a chip added from the sheet that silently never appeared.
     */
    @Test
    fun aBlankPhraseIsRefusedRatherThanWrittenAndDroppedOnRead() = runTest {
        val store = TasbeehStore(dataStore())
        assertFailsWith<IllegalArgumentException> { store.addCustom("   ", 33) }
        assertFailsWith<IllegalArgumentException> { store.addCustom(fs + fs, 33) }
        assertEquals(emptyList(), store.customPresets.first())
    }

    /** The 60 is characters as a reader counts them: a surrogate pair is one, and is never cut. */
    @Test
    fun thePhraseIsCutAtSixtyCodePointsNotSixtyUtf16Units() = runTest {
        val store = TasbeehStore(dataStore())
        // U+1F54C, a plane-1 character, is two UTF-16 units each: a `take(60)` would keep 30 of
        // them and leave a lone high surrogate as the 60th unit.
        val added = store.addCustom("🕌".repeat(70), 33)
        val phrase = added.parts.first().dhikr.arabic
        assertEquals(120, phrase.length)
        assertEquals("🕌".repeat(60), phrase)
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
    fun updateCustomKeepsTheIdAndThePlaceInTheOrder() = runTest {
        var clock = 10L
        val store = TasbeehStore(dataStore()) { clock++ }
        val first = store.addCustom("first", 33)
        val middle = store.addCustom("middle", 33)
        store.addCustom("last", 33)

        store.updateCustom(middle.id, "  middle, rewritten  ", 7)

        val presets = store.customPresets.first()
        assertEquals(listOf(first.id, middle.id, "custom_12"), presets.map { it.id })
        assertEquals(
            listOf("first", "middle, rewritten", "last"),
            presets.map { it.parts.first().dhikr.arabic },
            "the edited phrase is rewritten in place, trimmed, and stays second",
        )
        assertEquals(7, presets[1].total)
        assertTrue(presets[1].custom)
    }

    /**
     * A target dropped below the count already reached would leave a ring drawn past its own
     * circumference and an "8 of 5" that can never complete. At the target it reads 5 of 5.
     */
    @Test
    fun loweringTheTargetClampsTheSavedCountButNotTheRound() = runTest {
        val store = TasbeehStore(dataStore()) { 55L }
        val mine = store.addCustom("la hawla", 10)
        store.save(TasbeehState(mine.id, 8, 3))

        store.updateCustom(mine.id, "la hawla wa la quwwata", 5)

        assertEquals(TasbeehState(mine.id, 5, 3), store.stateOf(mine.id).first())
    }

    @Test
    fun raisingTheTargetLeavesTheCountWhereItWas() = runTest {
        val store = TasbeehStore(dataStore()) { 56L }
        val mine = store.addCustom("la hawla", 10)
        store.save(TasbeehState(mine.id, 8, 1))

        store.updateCustom(mine.id, "la hawla", 100)

        assertEquals(TasbeehState(mine.id, 8, 1), store.stateOf(mine.id).first())
        assertEquals(100, store.customPresets.first().single().total)
    }

    @Test
    fun updateCustomRefusesABlankPhraseAndCoercesTheTargetLikeAdd() = runTest {
        val store = TasbeehStore(dataStore()) { 77L }
        val mine = store.addCustom("ya latif", 33)

        assertFailsWith<IllegalArgumentException> { store.updateCustom(mine.id, "   ", 33) }
        assertFailsWith<IllegalArgumentException> { store.updateCustom(mine.id, fs + fs, 33) }
        assertEquals("ya latif", store.customPresets.first().single().parts.first().dhikr.arabic)

        // The same cut and the same ceiling `addCustom` applies: 60 characters as a reader counts
        // them — a surrogate pair is one — and a target held to 1..1000.
        store.updateCustom(mine.id, "  " + "\uD83D\uDD4C".repeat(70) + "  ", 5_000)
        val kept = store.customPresets.first().single()
        assertEquals("\uD83D\uDD4C".repeat(60), kept.parts.first().dhikr.arabic)
        assertEquals(120, kept.parts.first().dhikr.arabic.length)
        assertEquals(1_000, kept.total)

        store.updateCustom(mine.id, "ya latif", 0)
        assertEquals(1, store.customPresets.first().single().total)
    }

    /** Nothing to rewrite is nothing written: an edit never invents the entry it was given. */
    @Test
    fun updatingAPresetThatIsNotThereChangesNothing() = runTest {
        val store = TasbeehStore(dataStore()) { 88L }
        val mine = store.addCustom("ya latif", 33)
        store.updateCustom("custom_9999", "nowhere", 12)
        assertEquals(listOf(mine), store.customPresets.first())
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
