package world.taqwa.app.recitation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import world.taqwa.app.settings.SettingsKeys
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Keeps the catalogue current (spec 3a §4): one fetch of `manifest.json` a day at most, from the
 * public data repository, through [ManifestProvider.store] — which parses before it writes, so a
 * truncated body or a manifest from a later schema cannot take the app's catalogue away.
 *
 * Everything about this is deliberately quiet. It is called from the same start effect as the
 * library's reconciliation, and again when the picker or the Recitation settings open, and it
 * never reports anything: a reader with no network keeps the catalogue they have, which is
 * either yesterday's or the one bundled with the build, and both are correct answers. The only
 * thing a failure costs is a reciter added this morning not appearing until tomorrow.
 *
 * And it is gated (privacy spec §2.3): until the reader has used recitation it returns before
 * touching the network or the store, so an install that never opens recitation never opens a
 * socket.
 */
class ManifestRefresher(
    private val provider: ManifestProvider,
    private val store: DataStore<Preferences>,
    private val fetch: suspend (String) -> ByteArray? = { httpGet(it) },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /**
     * Whether the reader has ever used recitation (privacy spec §2.3). False means return before
     * reading or writing anything: an install that never opens recitation never opens a socket,
     * and never records an "attempt" either, so the first engaged launch fetches at once rather
     * than a day later.
     */
    private val engaged: suspend () -> Boolean = { true },
) {

    /**
     * Fetches if the last **attempt** was over a day ago. Attempt, not success: a repository that
     * is down, or a device with no network, must not turn every app start into an HTTP timeout.
     *
     * A stored time in the future — the clock moved back, a restored backup — is not honoured;
     * it would otherwise freeze the catalogue until the calendar caught up.
     */
    suspend fun refreshIfStale() {
        if (!engaged()) return
        // One at a time: the start effect and the picker can ask in the same second, and the
        // second caller must see the timestamp the first one wrote rather than fetch again.
        inFlight.withLock {
            val last = store.data.first()[SettingsKeys.RECITATION_MANIFEST_CHECKED] ?: 0L
            val at = now()
            if (at - last in 0 until INTERVAL_MILLIS) return
            store.edit { it[SettingsKeys.RECITATION_MANIFEST_CHECKED] = at }
            val bytes = try {
                fetch(MANIFEST_URL)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            } ?: return
            provider.store(bytes)
        }
    }

    private val inFlight = Mutex()

    companion object {
        /** Spec §12.3: the public repository, capital T, `main`. */
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/MohamedAbulgasem/Taqwa-data/main/manifest.json"

        const val INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    }
}

/**
 * One GET, whole body in memory, 20-second timeout, null for anything that is not a 2xx with a
 * body. `HttpURLConnection` on Android and an `NSURLSession` data task on iOS — the app still has
 * no HTTP client and one dependency for two requests is not worth it (spec §7).
 */
expect suspend fun httpGet(url: String): ByteArray?
