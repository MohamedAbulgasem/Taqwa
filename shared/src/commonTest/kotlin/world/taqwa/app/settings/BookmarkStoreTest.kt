package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookmarkStoreTest {
    private fun dataStore() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-bookmarks-${Random.nextULong()}.preferences_pb".toPath()
    }

    @Test
    fun toggleAddsThenRemovesAndReportsTheNewState() = runTest {
        var clock = 1_000L
        val store = BookmarkStore(dataStore()) { clock }
        assertEquals(emptyList(), store.bookmarks.first())
        assertTrue(store.toggle(2, 255))
        assertEquals(listOf(Bookmark(2, 255, 1_000L)), store.bookmarks.first())
        assertFalse(store.toggle(2, 255))
        assertEquals(emptyList(), store.bookmarks.first())
    }

    @Test
    fun newestFirst() = runTest {
        var clock = 1L
        val store = BookmarkStore(dataStore()) { clock++ }
        store.toggle(1, 1); store.toggle(2, 255); store.toggle(18, 10)
        assertEquals(listOf(18 to 10, 2 to 255, 1 to 1), store.bookmarks.first().map { it.surah to it.ayah })
    }

    @Test
    fun removeIsIdempotent() = runTest {
        val store = BookmarkStore(dataStore()) { 5L }
        store.toggle(1, 1)
        store.remove(1, 1)
        store.remove(1, 1)
        assertEquals(emptyList(), store.bookmarks.first())
    }

    @Test
    fun malformedEntriesAreIgnoredNotThrown() = runTest {
        val ds = dataStore()
        ds.edit { it[stringSetPreferencesKey("quran_bookmarks")] = setOf("2:255:10", "garbage", "1:x:3", "3:4") }
        val store = BookmarkStore(ds) { 0L }
        assertEquals(listOf(Bookmark(2, 255, 10L)), store.bookmarks.first())
    }
}
