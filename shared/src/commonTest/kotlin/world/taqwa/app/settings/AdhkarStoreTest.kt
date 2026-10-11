package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import world.taqwa.app.adhkar.AdhkarProgress
import world.taqwa.app.adhkar.AdhkarSet
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdhkarStoreTest {

    private val today = LocalDate(2026, 10, 11)

    private fun dataStore() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-adhkar-${Random.nextULong()}.preferences_pb".toPath()
    }

    @Test fun `nothing is stored until something is counted`() = runTest {
        val store = DataStoreAdhkarStore(dataStore())
        assertNull(store.progress(AdhkarSet.MORNING).first())
        assertNull(store.progress(AdhkarSet.EVENING).first())
        assertNull(store.istighfar().first())
        // The translation is on until a reader turns it off (spec 3.6).
        assertTrue(store.translation.first())
    }

    @Test fun `each set keeps its own progress`() = runTest {
        val store = DataStoreAdhkarStore(dataStore())
        store.save(AdhkarSet.MORNING, AdhkarProgress(today, listOf(1, 3, 0)))
        store.save(AdhkarSet.EVENING, AdhkarProgress(today, listOf(1, 0)))
        assertEquals("2026-10-11|1,3,0", store.progress(AdhkarSet.MORNING).first())
        assertEquals("2026-10-11|1,0", store.progress(AdhkarSet.EVENING).first())
    }

    @Test fun `a new date overwrites the old one and nothing is kept`() = runTest {
        val store = DataStoreAdhkarStore(dataStore())
        store.save(AdhkarSet.MORNING, AdhkarProgress(today, listOf(1, 3, 0)))
        store.save(AdhkarSet.MORNING, AdhkarProgress(LocalDate(2026, 10, 12), listOf(0, 0, 0)))
        assertEquals("2026-10-12|0,0,0", store.progress(AdhkarSet.MORNING).first())
    }

    @Test fun `the istighfar is one counter shared by both sets and read like a one page progress`() = runTest {
        val store = DataStoreAdhkarStore(dataStore())
        store.saveIstighfar(today, 57)
        assertEquals("2026-10-11|57", store.istighfar().first())
        assertEquals(
            AdhkarProgress(today, listOf(57)),
            AdhkarProgress.decode(store.istighfar().first(), pages = 1),
        )
        store.saveIstighfar(today, 58)
        assertEquals("2026-10-11|58", store.istighfar().first())
    }

    @Test fun `the translation switch round trips and an off is not mistaken for nothing stored`() = runTest {
        val store = DataStoreAdhkarStore(dataStore())
        store.setTranslation(false)
        assertFalse(store.translation.first())
        store.setTranslation(true)
        assertTrue(store.translation.first())
    }

    @Test fun `a set's done marker round trips and is the set's own`() = runTest {
        val ds = dataStore()
        val store = DataStoreAdhkarStore(ds)
        assertNull(store.done(AdhkarSet.MORNING).first())
        assertNull(store.done(AdhkarSet.EVENING).first())

        store.markDone(AdhkarSet.MORNING, today)
        assertEquals(today, store.done(AdhkarSet.MORNING).first())
        assertNull(store.done(AdhkarSet.EVENING).first())
        // A later day overwrites the marker: only the latest date is kept, as for the progress.
        store.markDone(AdhkarSet.MORNING, LocalDate(2026, 10, 12))
        assertEquals(LocalDate(2026, 10, 12), store.done(AdhkarSet.MORNING).first())
        store.markDone(AdhkarSet.EVENING, today)
        assertEquals(today, store.done(AdhkarSet.EVENING).first())

        val stored = ds.data.first()
        assertEquals("2026-10-12", stored[stringPreferencesKey("adhkar_done_morning")])
        assertEquals("2026-10-11", stored[stringPreferencesKey("adhkar_done_evening")])
    }

    @Test fun `a marker that is not a date reads as none`() = runTest {
        val ds = dataStore()
        ds.edit { it[stringPreferencesKey("adhkar_done_morning")] = "yesterday" }
        assertNull(DataStoreAdhkarStore(ds).done(AdhkarSet.MORNING).first())
    }

    @Test fun `the keys are the ones the spec names`() = runTest {
        val ds = dataStore()
        val store = DataStoreAdhkarStore(ds)
        store.save(AdhkarSet.MORNING, AdhkarProgress(today, listOf(2)))
        store.save(AdhkarSet.EVENING, AdhkarProgress(today, listOf(3)))
        store.saveIstighfar(today, 4)
        val stored = ds.data.first()
        assertEquals("2026-10-11|2", stored[stringPreferencesKey("adhkar_morning")])
        assertEquals("2026-10-11|3", stored[stringPreferencesKey("adhkar_evening")])
        assertEquals("2026-10-11|4", stored[stringPreferencesKey("adhkar_istighfar")])
    }

    @Test fun `a stored value that does not read is handed back as it is for the caller to discard`() = runTest {
        val ds = dataStore()
        ds.edit { it[stringPreferencesKey("adhkar_morning")] = "garbage" }
        assertEquals("garbage", DataStoreAdhkarStore(ds).progress(AdhkarSet.MORNING).first())
    }
}
