package world.taqwa.app.recitation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.appendData
import platform.Foundation.dataTaskWithRequest
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * One GET through a session of its own (spec §7 chose no HTTP client; `NSURLSession` is
 * Foundation), made for the call and let go when its one task ends: this runs once a day at most.
 * The surah downloads use their own background session — see `SurahDownloader.ios.kt` — because
 * they must outlive the app; a 154 KB manifest need not.
 *
 * A delegate rather than a completion handler, because a completion handler is handed the body
 * only once all of it has arrived: [CappedBody] refuses a body that says it is over
 * [MANIFEST_MAX_BYTES] and cancels one that runs past it (security review A2, C2).
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun httpGet(url: String): ByteArray? = suspendCancellableCoroutine { continuation ->
    val target = NSURL.URLWithString(url)
    if (target == null) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    // GET with a twenty-second budget; no mutable request needed, so none is made.
    val request = NSURLRequest.requestWithURL(target, NSURLRequestUseProtocolCachePolicy, TIMEOUT_SECONDS)
    val body = CappedBody(MANIFEST_MAX_BYTES) { bytes -> if (continuation.isActive) continuation.resume(bytes) }
    // No queue given: the session makes a serial one of its own, which CappedBody relies on.
    val session = NSURLSession.sessionWithConfiguration(manifestConfiguration(), body, null)
    val task = session.dataTaskWithRequest(request)
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
    // A session holds its delegate until it is invalidated; this lets both go once the task ends.
    session.finishTasksAndInvalidate()
}

private const val TIMEOUT_SECONDS = 20.0

/**
 * The manifest GET's configuration: a session of its own rather than the shared one, which keeps
 * the system's cookie jar and an on-disk cache. Ephemeral, and [carryingNothingExtra] with no
 * cache at all: the manifest is fetched once a day and read straight into the provider.
 */
internal fun manifestConfiguration(): NSURLSessionConfiguration =
    NSURLSessionConfiguration.ephemeralSessionConfiguration.carryingNothingExtra().apply {
        setURLCache(null)
    }

/**
 * What every session the app makes shares (security review A4, B2). No cookie is kept or sent:
 * PRIVACY.md promises none, and a host that began setting one — GitHub's release CDN, or a later
 * base — would otherwise get it back with every later request and could link one reader's
 * downloads across days and addresses. The User-Agent is [HTTP_USER_AGENT] rather than the
 * system's, which names the app's build, and the Accept-Language is `*` rather than CFNetwork's,
 * which lists the reader's own languages in their order: neither file has a language to choose.
 */
internal fun NSURLSessionConfiguration.carryingNothingExtra(): NSURLSessionConfiguration = apply {
    setHTTPShouldSetCookies(false)
    setHTTPCookieStorage(null)
    setHTTPCookieAcceptPolicy(NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever)
    setHTTPAdditionalHeaders(mapOf<Any?, Any?>("User-Agent" to HTTP_USER_AGENT, "Accept-Language" to "*"))
}

/**
 * The manifest's body as it arrives, never more than [cap] of it. Touched only on the session's
 * serial delegate queue. [done] is called once, when the task ends: with the body, or with null
 * for a status that is not 2xx, a body over the cap, or a failure.
 */
@OptIn(ExperimentalForeignApi::class)
internal class CappedBody(
    private val cap: Long,
    private val done: (ByteArray?) -> Unit,
) : NSObject(), NSURLSessionDataDelegateProtocol {

    private val body = NSMutableData()
    private var refused = false

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        val status = (didReceiveResponse as? NSHTTPURLResponse)?.statusCode?.toInt()
        // A length the server does not give reads as -1, and is the data callback's to catch.
        refused = status == null || status !in 200..299 || didReceiveResponse.expectedContentLength > cap
        completionHandler(if (refused) NSURLSessionResponseCancel else NSURLSessionResponseAllow)
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (refused) return
        if (body.length.toLong() + didReceiveData.length.toLong() > cap) {
            refused = true
            dataTask.cancel()
            return
        }
        body.appendData(didReceiveData)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        done(if (didCompleteWithError == null && !refused) body.toByteArray() else null)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}
