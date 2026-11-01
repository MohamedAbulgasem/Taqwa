package world.taqwa.app.recitation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTaskStateCanceling
import platform.Foundation.NSURLSessionTaskStateCompleted
import platform.Foundation.NSURLSessionTaskStateSuspended
import platform.Foundation.dataWithBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iOS manifest GET's delegate, driven by hand (security review A2): a body over the cap is
 * refused, whether or not it says how large it is. The task is made and never resumed, so
 * nothing here touches the network.
 */
@OptIn(ExperimentalForeignApi::class)
class CappedBodyTest {

    private val url = NSURL.URLWithString("https://example.invalid/manifest.json")!!
    private val session = NSURLSession.sharedSession
    private val task = session.dataTaskWithURL(url)

    private fun response(status: Long = 200, length: Long? = null) = NSHTTPURLResponse(
        uRL = url,
        statusCode = status,
        HTTPVersion = "HTTP/1.1",
        headerFields = length?.let { mapOf<Any?, Any?>("Content-Length" to it.toString()) },
    )!!

    private fun data(size: Int): NSData {
        val bytes = ByteArray(size) { 7 }
        return bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }
    }

    private val cancelled = NSError.errorWithDomain(NSURLErrorDomain, -999, null)

    /** Feeds [chunks] after [response], ends as iOS would, and returns what the delegate answered. */
    private fun run(cap: Long, response: NSHTTPURLResponse, chunks: List<Int>): Pair<NSURLSessionResponseDisposition?, ByteArray?> {
        var disposition: NSURLSessionResponseDisposition? = null
        var answer: ByteArray? = ByteArray(1)
        val body = CappedBody(cap) { answer = it }
        body.URLSession(session, task, didReceiveResponse = response, completionHandler = { disposition = it })
        if (disposition == NSURLSessionResponseAllow) {
            chunks.forEach { body.URLSession(session, task, didReceiveData = data(it)) }
        }
        // A task the delegate cancelled ends with the cancellation, as iOS would end it.
        body.URLSession(session, task, didCompleteWithError = if (task.state != NSURLSessionTaskStateSuspended) cancelled else null)
        return disposition to answer
    }

    @Test
    fun aBodyThatSaysItIsOverTheCapIsRefusedUnread() {
        val (disposition, answer) = run(cap = 10, response(length = 11), listOf(11))
        assertEquals(NSURLSessionResponseCancel, disposition)
        assertNull(answer)
    }

    @Test
    fun aBodyThatRunsPastTheCapWithoutSayingSoIsCancelled() {
        val (disposition, answer) = run(cap = 10, response(), listOf(6, 5, 100))
        assertEquals(NSURLSessionResponseAllow, disposition)
        assertNull(answer)
        assertTrue(task.state == NSURLSessionTaskStateCanceling || task.state == NSURLSessionTaskStateCompleted, "the task was not cancelled")
    }

    @Test
    fun aBodyAtTheCapIsRead() {
        val (_, answer) = run(cap = 10, response(length = 10), listOf(4, 6))
        assertEquals(10, answer?.size)
    }

    @Test
    fun aStatusThatIsNotTwoHundredIsNull() {
        val (disposition, answer) = run(cap = 10, response(status = 404), listOf(3))
        assertEquals(NSURLSessionResponseCancel, disposition)
        assertNull(answer)
    }
}
