package world.taqwa.app.recitation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.settings.SettingsRepository

/**
 * Set from `iOSApp.swift`'s app delegate when iOS wakes the app to say a background session has
 * finished its work. Foundation requires the completion handler to be called once every delegate
 * callback has been delivered, and only Swift can hold it.
 */
var iosBackgroundDownloadsCompletion: (() -> Unit)? = null

/**
 * A background `NSURLSession` (spec 3a §7). One download task per surah, the key as the task's
 * description, and the session identified by the package name so iOS hands the same tasks back
 * after the app has been suspended, killed or relaunched.
 *
 * The Wi-Fi-only rule is the configuration's `allowsCellularAccess` rather than a reachability
 * poll: iOS then refuses the transfer itself and reports `NSURLErrorDataNotAllowed`, which is
 * exactly [DownloadFailure.NEEDS_WIFI] and is the platform's own answer rather than our guess at
 * it. There is no equivalent of Android's pre-flight metered refusal, and there does not need to
 * be — nothing is transferred either way.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SurahDownloader actual constructor(
    private val library: RecitationLibrary,
    private val manifests: ManifestProvider,
    private val settings: SettingsRepository,
    private val quran: QuranSource,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fs = FileSystem.SYSTEM
    private val reported = MutableStateFlow<Map<DownloadKey, DownloadState>>(emptyMap())

    /** What each in-flight task started from, so `didWriteData`'s count can be made absolute. */
    private val startedAt = mutableMapOf<String, Long>()
    private val sizes = mutableMapOf<String, Long>()

    actual val states: StateFlow<Map<DownloadKey, DownloadState>> = reported.asStateFlow()

    private val loop = DownloadLoop(
        library = library,
        // The transfer belongs to the session, so the loop is used only for its two ends: the
        // free-space refusal before a task is created, and the verify-and-commit after one lands.
        source = ByteSource { _, _ -> ByteResponse.Failure(DownloadFailure.SERVER) },
        conditions = IosDownloadConditions(),
        fs = fs,
    )

    private val delegate = object : NSObject(), NSURLSessionDownloadDelegateProtocol {

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didWriteData: Long,
            totalBytesWritten: Long,
            totalBytesExpectedToWrite: Long,
        ) {
            val wire = downloadTask.taskDescription ?: return
            val key = DownloadKey.parse(wire) ?: return
            val base = startedAt[wire] ?: 0L
            val total = sizes[wire] ?: (base + totalBytesExpectedToWrite)
            report(key, DownloadState.Downloading(base + totalBytesWritten, total))
        }

        /**
         * The temporary file is deleted the moment this returns, so it is appended to the `.part`
         * here and now; verification and the commit — which hash 58 MB — are handed to a coroutine.
         */
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL,
        ) {
            val wire = downloadTask.taskDescription ?: return
            val key = DownloadKey.parse(wire) ?: return
            val temporary = didFinishDownloadingToURL.path?.toPath() ?: return
            val status = (downloadTask.response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            val part = library.partFor(key.reciterId, key.surah)
            val appended = runCatching {
                part.parent?.let { fs.createDirectories(it) }
                // 200 rather than 206 means the server ignored the Range and has sent the whole
                // file; anything already on disk would end up in front of a complete copy.
                if (status != 206) runCatching { fs.delete(part) }
                val sink = if (fs.exists(part)) fs.appendingSink(part) else fs.sink(part)
                sink.buffer().use { out -> fs.source(temporary).use { out.writeAll(it) } }
            }.isSuccess
            startedAt.remove(wire)
            if (!appended) {
                report(key, DownloadState.Failed(DownloadFailure.SERVER))
                return
            }
            report(key, DownloadState.Verifying)
            scope.launch { commit(key) }
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            val wire = task.taskDescription ?: return
            val key = DownloadKey.parse(wire) ?: return
            val error = didCompleteWithError ?: return
            startedAt.remove(wire)
            sizes.remove(wire)
            when (error.code) {
                // The reader cancelled it; the sheet goes back to offering the download.
                NSURL_ERROR_CANCELLED -> clear(key)
                NSURL_ERROR_DATA_NOT_ALLOWED ->
                    report(key, DownloadState.Failed(DownloadFailure.NEEDS_WIFI))
                NSURL_ERROR_NOT_CONNECTED, NSURL_ERROR_NETWORK_LOST ->
                    report(key, DownloadState.Failed(DownloadFailure.NO_NETWORK))
                else -> report(key, DownloadState.Failed(DownloadFailure.SERVER))
            }
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
            val completion = iosBackgroundDownloadsCompletion ?: return
            dispatch_async(dispatch_get_main_queue()) { completion() }
        }
    }

    /**
     * **Two** sessions, because `allowsCellularAccess` is fixed for the life of a background
     * session and the Wi-Fi-only rule is per download. A surah the reader has not allowed onto
     * mobile data goes through the Wi-Fi session, which iOS itself refuses to run over cellular
     * (reporting `NSURLErrorDataNotAllowed`, the platform's own [DownloadFailure.NEEDS_WIFI]);
     * one they have allowed goes through the other. Both share this delegate, so nothing else in
     * the class has to know which one a task belongs to.
     *
     * `NSOperationQueue()` rather than the main queue: appending a finished 58 MB file happens on
     * a delegate callback, and that must not be the main thread.
     */
    private val wifiSession: NSURLSession by lazy { makeSession(SESSION_ID, false) }
    private val mobileSession: NSURLSession by lazy { makeSession(MOBILE_SESSION_ID, true) }

    private fun makeSession(identifier: String, allowsCellular: Boolean): NSURLSession {
        val configuration = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(identifier)
        configuration.setAllowsCellularAccess(allowsCellular)
        configuration.setSessionSendsLaunchEvents(true)
        return NSURLSession.sessionWithConfiguration(configuration, delegate, NSOperationQueue())
    }

    /**
     * Rebuilds both sessions so iOS can deliver the callbacks it has been holding. Called from
     * `iOSApp.swift` when the system relaunches the app for a background session — a session's
     * delegate callbacks only arrive once a session with that identifier exists again.
     */
    fun attach() {
        wifiSession
        mobileSession
    }

    actual fun enqueue(key: DownloadKey, allowMobileOnce: Boolean) {
        scope.launch { runCatching { start(listOf(key.surah), key.reciterId, allowMobileOnce) } }
    }

    actual fun enqueueReciter(reciterId: String, allowMobileOnce: Boolean) {
        scope.launch {
            runCatching {
                val reciter = manifests.current().reciter(reciterId) ?: return@runCatching
                val owned = library.downloaded(reciterId).first()
                start(reciter.surahs.map { it.n }.sorted().filter { it !in owned }, reciterId, allowMobileOnce)
            }
        }
    }

    actual fun cancel(key: DownloadKey) {
        clear(key)
        cancelTasks { DownloadKey.parse(it) == key }
    }

    actual fun cancelReciter(reciterId: String) {
        reported.value = reported.value.filterKeys { it.reciterId != reciterId }
        cancelTasks { DownloadKey.parse(it)?.reciterId == reciterId }
    }

    actual suspend fun retry(key: DownloadKey) {
        cancel(key)
        runCatching { start(listOf(key.surah), key.reciterId, false) }
    }

    private suspend fun start(surahs: List<Int>, reciterId: String, allowMobileOnce: Boolean) {
        if (surahs.isEmpty()) return
        val manifest = manifests.current()
        val reciter = manifest.reciter(reciterId) ?: return
        val allowCellular = allowMobileOnce || settings.recitationSettings.first().downloadOnMobileData
        val session = if (allowCellular) mobileSession else wifiSession
        for (surah in surahs) {
            val asset = reciter.surah(surah) ?: continue
            val key = DownloadKey(reciterId, surah)
            loop.precheck(asset, allowCellular)?.let {
                report(key, DownloadState.Failed(it))
                continue
            }
            val part = library.partFor(reciterId, surah)
            var have = fs.metadataOrNull(part)?.size ?: 0L
            if (have >= asset.bytes) {
                runCatching { fs.delete(part) }
                have = 0L
            }
            val url = NSURL.URLWithString(manifest.assetUrl(reciter, surah)) ?: continue
            val request = (NSMutableURLRequest.requestWithURL(url) as NSMutableURLRequest).apply {
                // A background session honours the headers a request carries, which is what makes
                // resume possible without the session's own resume-data machinery — that only
                // survives a cancellation we performed, not a process the system reclaimed.
                if (have > 0L) setValue("bytes=$have-", forHTTPHeaderField = "Range")
            }
            val task = session.downloadTaskWithRequest(request)
            task.taskDescription = key.wire
            startedAt[key.wire] = have
            sizes[key.wire] = asset.bytes
            report(key, if (have > 0L) DownloadState.Downloading(have, asset.bytes) else DownloadState.Queued)
            task.resume()
        }
    }

    private suspend fun commit(key: DownloadKey) {
        val asset = manifests.current().reciter(key.reciterId)?.surah(key.surah)
        if (asset == null) {
            report(key, DownloadState.Failed(DownloadFailure.SERVER))
            return
        }
        sizes.remove(key.wire)
        when (val outcome = loop.finish(key, asset)) {
            DownloadOutcome.Done -> clear(key)
            is DownloadOutcome.Failed -> report(key, DownloadState.Failed(outcome.reason))
        }
    }

    private fun cancelTasks(matches: (String) -> Boolean) {
        listOf(wifiSession, mobileSession).forEach { session -> cancelTasksIn(session, matches) }
    }

    private fun cancelTasksIn(session: NSURLSession, matches: (String) -> Boolean) {
        session.getTasksWithCompletionHandler { _, _, downloads ->
            downloads?.forEach { task ->
                val download = task as? NSURLSessionDownloadTask ?: return@forEach
                val wire = download.taskDescription ?: return@forEach
                if (matches(wire)) {
                    startedAt.remove(wire)
                    sizes.remove(wire)
                    download.cancel()
                }
            }
        }
    }

    private fun report(key: DownloadKey, state: DownloadState) {
        reported.value = reported.value + (key to state)
    }

    /** A committed surah leaves the map: from here on the library is what reports it. */
    private fun clear(key: DownloadKey) {
        reported.value = reported.value - key
    }

    private companion object {
        /** Spec: the session identifier is the recitation package. */
        const val SESSION_ID = "world.taqwa.app.recitation"

        /** Its cellular twin; see [wifiSession]. */
        const val MOBILE_SESSION_ID = "world.taqwa.app.recitation.mobile"

        const val NSURL_ERROR_CANCELLED = -999L
        const val NSURL_ERROR_NOT_CONNECTED = -1009L
        const val NSURL_ERROR_NETWORK_LOST = -1005L
        const val NSURL_ERROR_DATA_NOT_ALLOWED = -1020L
    }
}

/**
 * Free space on the volume Application Support lives on, and whether there is a network at all.
 *
 * **Metered is still the session configuration's question**, not this one (see above): refusing
 * here as well would refuse a download iOS is perfectly willing to make over Wi-Fi. But *no
 * connection at all* has to be answered before a task is created, or the reader in a dead spot is
 * shown a progress bar for a transfer that has not begun — the same defect the Android side had.
 * So this answers only two of the three: [NetworkKind.NONE] when the path is unsatisfied,
 * [NetworkKind.UNMETERED] otherwise.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadConditions : DownloadConditions {

    init {
        // Started here rather than at the first question, so the monitor has had the whole of the
        // app's life to answer one by the time a reader taps Download.
        IosNetworkPath.start()
    }

    override suspend fun network(): NetworkKind =
        if (IosNetworkPath.connected()) NetworkKind.UNMETERED else NetworkKind.NONE

    override suspend fun freeBytes(): Long {
        val directory = recitationFilesDirectory().toString()
        val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(directory, null)
        return (attributes?.get(NSFileSystemFreeSize) as? NSNumberLike)?.longValue ?: Long.MAX_VALUE
    }
}

private typealias NSNumberLike = platform.Foundation.NSNumber

/**
 * `NWPathMonitor`, once for the process: is there a network the system would let a transfer over.
 *
 * A monitor rather than a one-shot check because Network.framework has no one-shot check — the
 * path arrives on a callback. The first answer takes a few milliseconds, so [connected] waits a
 * moment for it and **defaults to connected** if it has not come: a downloader that refused
 * because it had not been told yet would refuse the first tap of every launch.
 */
@OptIn(ExperimentalForeignApi::class)
private object IosNetworkPath {

    @Volatile
    private var satisfied = true

    private val first = CompletableDeferred<Unit>()

    /** Held for the life of the process; a monitor nobody references stops reporting. */
    private var monitor: nw_path_monitor_t? = null

    fun start() {
        if (monitor != null) return
        val made = nw_path_monitor_create() ?: return
        monitor = made
        nw_path_monitor_set_update_handler(made) { path ->
            satisfied = path != null && nw_path_get_status(path) == nw_path_status_satisfied
            first.complete(Unit)
        }
        nw_path_monitor_set_queue(made, dispatch_get_main_queue())
        nw_path_monitor_start(made)
    }

    suspend fun connected(): Boolean {
        start()
        withTimeoutOrNull(FIRST_ANSWER_MS) { first.await() }
        return satisfied
    }

    /** Long enough for a path that is already known, short enough not to be felt under a thumb. */
    private const val FIRST_ANSWER_MS = 400L
}

/**
 * The hook `iOSApp.swift` calls from `application(_:handleEventsForBackgroundURLSession:)`.
 *
 * Two things have to happen there and only there: the completion handler iOS hands over must be
 * remembered so it can be called once every delegate callback has been delivered, and the session
 * has to be recreated, because a relaunched process has no sessions at all and iOS delivers
 * nothing until one with the right identifier exists.
 */
fun wakeRecitationDownloads(completion: () -> Unit) {
    iosBackgroundDownloadsCompletion = completion
    world.taqwa.app.di.appContainer.surahDownloader.attach()
}

actual fun createSurahDownloader(
    library: RecitationLibrary,
    manifests: ManifestProvider,
    settings: SettingsRepository,
    quran: QuranSource,
): SurahDownloader = SurahDownloader(library, manifests, settings, quran)
