package world.taqwa.app.recitation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import world.taqwa.app.settings.SettingsKeys
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The daily catalogue refresh: once a day at most and silent about everything. */
class ManifestRefresherTest {

    private val fs = FakeFileSystem()
    private val paths = RecitationPaths("/files".toPath())
    private val provider = ManifestProvider(paths, fs) { TWO_RECITERS.encodeToByteArray() }
    private val fresh = TWO_RECITERS.replace("\"ar.husary\"", "\"ar.minshawi\"")

    private fun store(): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-refresher-${Random.nextULong()}.preferences_pb".toPath()
    }

    private var clock = 1_000_000_000_000L

    @Test
    fun theFirstRefreshFetchesAndStores() = runTest {
        var asked = 0
        val store = store()
        val refresher = ManifestRefresher(provider, store, { asked++; fresh.encodeToByteArray() }, { clock })

        refresher.refreshIfStale()

        assertEquals(1, asked)
        assertEquals(listOf("ar.alafasy", "ar.minshawi"), provider.current().reciters.map { it.id })
        assertEquals(clock, store.data.first()[SettingsKeys.RECITATION_MANIFEST_CHECKED])
    }

    @Test
    fun aSecondRefreshWithinTheDayDoesNothing() = runTest {
        var asked = 0
        val refresher = ManifestRefresher(provider, store(), { asked++; fresh.encodeToByteArray() }, { clock })

        refresher.refreshIfStale()
        clock += 23L * 60 * 60 * 1000
        refresher.refreshIfStale()
        assertEquals(1, asked)

        clock += 2L * 60 * 60 * 1000
        refresher.refreshIfStale()
        assertEquals(2, asked)
    }

    @Test
    fun theAttemptIsRecordedEvenWhenTheFetchFails() = runTest {
        var asked = 0
        val store = store()
        val refresher = ManifestRefresher(
            provider,
            store,
            { asked++; throw okio.IOException("the repository is down") },
            { clock },
        )

        refresher.refreshIfStale()
        assertEquals(1, asked)
        assertNotNull(store.data.first()[SettingsKeys.RECITATION_MANIFEST_CHECKED])

        // And the bundled catalogue is still the one in force.
        assertEquals(listOf("ar.alafasy", "ar.husary"), provider.current().reciters.map { it.id })

        refresher.refreshIfStale()
        assertEquals(1, asked)
    }

    @Test
    fun halfAManifestIsNotAllowedToReplaceAWholeOne() = runTest {
        val refresher = ManifestRefresher(
            provider,
            store(),
            { "{\"schema\": 1, \"gener".encodeToByteArray() },
            { clock },
        )

        refresher.refreshIfStale()
        assertEquals(listOf("ar.alafasy", "ar.husary"), provider.current().reciters.map { it.id })
        assertNull(fs.metadataOrNull(paths.manifestCache()))
    }

    @Test
    fun aClockThatWentBackwardsDoesNotFreezeTheCatalogue() = runTest {
        var asked = 0
        val store = store()
        val refresher = ManifestRefresher(provider, store, { asked++; fresh.encodeToByteArray() }, { clock })

        refresher.refreshIfStale()
        clock -= 5L * 24 * 60 * 60 * 1000
        refresher.refreshIfStale()

        assertEquals(2, asked)
    }

    // ── The engagement gate (privacy spec §2.3) ──────────────────────────────────────────

    @Test
    fun aReaderWhoNeverTouchedRecitationCausesNoFetchAndNoTimestamp() = runTest {
        var asked = 0
        val store = store()
        val refresher = ManifestRefresher(provider, store, { asked++; fresh.encodeToByteArray() }, { clock }, engaged = { false })

        refresher.refreshIfStale()

        assertEquals(0, asked)
        // Not even the attempt is recorded: nothing about this launch should say "checked".
        assertNull(store.data.first()[SettingsKeys.RECITATION_MANIFEST_CHECKED])
    }

    @Test
    fun aStaleTimestampStillDoesNotFetchWhileNotEngaged() = runTest {
        var asked = 0
        val store = store()
        store.edit { it[SettingsKeys.RECITATION_MANIFEST_CHECKED] = clock - 3L * ManifestRefresher.INTERVAL_MILLIS }
        val refresher = ManifestRefresher(provider, store, { asked++; fresh.encodeToByteArray() }, { clock }, engaged = { false })

        refresher.refreshIfStale()
        assertEquals(0, asked)
    }

    @Test
    fun theFirstEngagedLaunchFetchesAtOnce() = runTest {
        var asked = 0
        var engaged = false
        val refresher = ManifestRefresher(provider, store(), { asked++; fresh.encodeToByteArray() }, { clock }, engaged = { engaged })

        refresher.refreshIfStale()
        assertEquals(0, asked)
        engaged = true
        refresher.refreshIfStale()
        assertEquals(1, asked, "no stale window to wait out: the gate never wrote a timestamp")
    }
}
