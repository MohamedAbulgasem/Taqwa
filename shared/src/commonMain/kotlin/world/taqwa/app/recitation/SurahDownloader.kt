package world.taqwa.app.recitation

import kotlinx.coroutines.flow.StateFlow
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.settings.SettingsRepository

/**
 * Fetching surahs (spec 3a §7, §12.8), on whichever of the two schedulers the platform gives us:
 * WorkManager on Android, a background `NSURLSession` on iOS. Both survive the app being
 * backgrounded, which is the whole reason neither side is a plain coroutine.
 *
 * The policy is identical on both and lives in [DownloadLoop] — Wi-Fi unless the reader has said
 * otherwise, 200 MB of headroom kept free, byte-exact resume, SHA-256 verified before a file may
 * be played. What differs is only who runs the transfer.
 *
 * [states] carries what is **in flight or failed**. A surah that finished is not in it: it is in
 * the library, and the library is what the UI reads for "the reader owns this".
 */
expect class SurahDownloader(
    library: RecitationLibrary,
    manifests: ManifestProvider,
    settings: SettingsRepository,
    quran: QuranSource,
) {

    val states: StateFlow<Map<DownloadKey, DownloadState>>

    /**
     * Accepts one surah. Returns at once — the answer arrives through [states].
     *
     * @param allowMobileOnce the download sheet's one-time override (spec §5.4). Not stored:
     * allowing this surah over mobile data is not a decision about the next one.
     */
    fun enqueue(key: DownloadKey, allowMobileOnce: Boolean = false)

    /**
     * "Download the whole Quran for this reciter" (spec §12.8): every surah the reader does not
     * already own, in surah order, as one batch that reports itself as a batch.
     */
    fun enqueueReciter(reciterId: String, allowMobileOnce: Boolean = false)

    fun cancel(key: DownloadKey)

    /** Cancels the batch and every surah of it still outstanding. */
    fun cancelReciter(reciterId: String)

    /** The download sheet's Retry: forgets the failure and starts the surah again. */
    suspend fun retry(key: DownloadKey)
}

/** The downloader the app runs on, built the way the rest of the graph is. */
expect fun createSurahDownloader(
    library: RecitationLibrary,
    manifests: ManifestProvider,
    settings: SettingsRepository,
    quran: QuranSource,
): SurahDownloader
