package world.taqwa.app.recitation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * One GET through the shared session (spec §7 chose no HTTP client; `NSURLSession` is Foundation).
 * The surah downloads use their own background session — see `SurahDownloader.ios.kt` — because
 * they must outlive the app; a 30 KB manifest need not.
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
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, _ ->
        val status = (response as? platform.Foundation.NSHTTPURLResponse)?.statusCode?.toInt()
        val bytes = if (status != null && status in 200..299) data?.toByteArray() else null
        if (continuation.isActive) continuation.resume(bytes)
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}

private const val TIMEOUT_SECONDS = 20.0

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}
