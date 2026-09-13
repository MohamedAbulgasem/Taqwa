package world.taqwa.app.recitation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import world.taqwa.app.settings.SettingsKeys
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Counts writes, so "mark() twice writes once" is a number rather than a hope. */
private class CountingDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    var writes = 0
    override val data: Flow<Preferences> get() = delegate.data
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        writes++
        return delegate.updateData(transform)
    }
}

/**
 * "Engaged" is the one fact the catalogue refresh is gated on: a person who installed Taqwa for
 * prayer times and never opened recitation causes no network request, ever (privacy spec §2.1).
 */
class RecitationEngagementTest {

    private val fs = FakeFileSystem()
    private val paths = RecitationPaths("/files".toPath())

    private fun store() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-engagement-${Random.nextULong()}.preferences_pb".toPath()
    }

    private fun engagement(store: DataStore<Preferences>) =
        RecitationEngagement(store, RecitationLibrary(paths, store, fs))

    @Test
    fun aFreshInstallIsNotEngaged() = runTest {
        assertFalse(engagement(store()).isEngaged())
    }

    @Test
    fun aDownloadMadeBeforeTheFlagExistedCountsAsEngaged() = runTest {
        val store = store()
        // 0.11.0 wrote the registry but never a flag, and reconcile() rebuilds the registry from
        // disk on a reinstall, so the registry is the right thing to fall back on.
        store.edit { it[SettingsKeys.recitationDownloadedKey("ar.alafasy")] = setOf("1", "112") }
        assertTrue(engagement(store).isEngaged())
    }

    @Test
    fun anEmptyRegistryEntryIsNotADownload() = runTest {
        val store = store()
        store.edit { it[SettingsKeys.recitationDownloadedKey("ar.alafasy")] = emptySet() }
        assertFalse(engagement(store).isEngaged())
    }

    @Test
    fun markThenIsEngaged() = runTest {
        val store = store()
        val engagement = engagement(store)
        engagement.mark()
        assertTrue(engagement.isEngaged())
        assertEquals(true, store.data.first()[SettingsKeys.RECITATION_ENGAGED])
    }

    @Test
    fun markTwiceWritesOnce() = runTest {
        val counting = CountingDataStore(store())
        val engagement = RecitationEngagement(counting, RecitationLibrary(paths, counting, fs))
        engagement.mark()
        engagement.mark()
        assertEquals(1, counting.writes)
    }

    @Test
    fun hasAnyDownloadsReadsTheRegistryOnly() = runTest {
        val store = store()
        val library = RecitationLibrary(paths, store, fs)
        assertFalse(library.hasAnyDownloads())
        store.edit { it[SettingsKeys.recitationDownloadedKey("ar.husary")] = setOf("36") }
        assertTrue(library.hasAnyDownloads())
    }
}
