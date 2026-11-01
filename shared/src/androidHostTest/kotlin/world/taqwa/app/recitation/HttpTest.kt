package world.taqwa.app.recitation

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * The manifest GET against a real socket (security review A2): a body over [MANIFEST_MAX_BYTES]
 * is refused, whether or not it says how large it is.
 */
class HttpTest {

    private val cap = MANIFEST_MAX_BYTES.toInt()
    private var server: LoopbackServer? = null

    private fun serve(respond: (path: String, out: OutputStream) -> Unit): LoopbackServer =
        LoopbackServer(respond).also { server = it }

    @AfterTest
    fun stop() {
        server?.close()
    }

    @Test
    fun aBodyThatSaysItIsOverTheCapIsRefused() = runTest {
        val local = serve { _, out -> out.respond(ByteArray(cap + 1), declared = true) }
        assertNull(httpGet(local.url("/manifest.json")))
    }

    @Test
    fun aBodyThatSaysItIsOverTheCapIsRefusedWithoutWaitingForIt() = runTest {
        // A gigabyte declared, a few bytes sent and the connection then held open: a read would
        // wait out the twenty-second read timeout before giving up, so only the Content-Length
        // check can answer this quickly.
        val held = CountDownLatch(1)
        val local = serve { _, out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Length: ${1L shl 30}\r\n\r\n".encodeToByteArray())
            out.write(ByteArray(16))
            out.flush()
            held.await(1, TimeUnit.MINUTES)
        }
        try {
            val took = measureTime { assertNull(httpGet(local.url("/manifest.json"))) }
            assertTrue(took < 5.seconds, "the refusal took $took")
        } finally {
            held.countDown()
        }
    }

    @Test
    fun aBodyThatRunsPastTheCapWithoutSayingSoIsRefused() = runTest {
        // Delimited by the connection closing, as an HTTP/1.1 body may be: sixteen megabytes the
        // reader must stop well short of.
        val local = serve { _, out -> out.respond(ByteArray(8 * cap), declared = false) }
        assertNull(httpGet(local.url("/manifest.json")))
    }

    @Test
    fun aBodyAtTheCapIsRead() = runTest {
        val local = serve { _, out -> out.respond(ByteArray(cap) { 7 }, declared = false) }
        val body = assertNotNull(httpGet(local.url("/manifest.json")))
        assertEquals(cap, body.size)
    }

    @Test
    fun aManifestOfTodaysSizeIsReadWhole() = runTest {
        val manifest = ByteArray(153_734) { (it % 251).toByte() }
        val local = serve { _, out -> out.respond(manifest, declared = true) }
        assertEquals(manifest.toList(), httpGet(local.url("/manifest.json"))?.toList())
    }

    // ── Requests carry nothing extra (security review A4/C3) ───────────────────────────

    @Test
    fun theManifestRequestNamesTheAppAndNothingElse() = runTest {
        // PRIVACY.md: the daily check is the same for everyone and reveals nothing but the IP
        // address. The platform's own agent names the phone's model and build.
        val local = serve { _, out -> out.respond("{}".encodeToByteArray(), declared = true) }
        httpGet(local.url("/manifest.json"))
        assertEquals("Taqwa", headerOf(local.heads.single(), "User-Agent"))
    }

    @Test
    fun aSurahRequestNamesTheAppAndNothingElseThroughTheRedirect() = runTest {
        // A release asset answers with a redirect to its CDN, and both requests are the app's.
        val local = serve { path, out ->
            if (path.startsWith("/release/")) {
                out.respond(ByteArray(0), declared = true, status = "302 Found", extra = "Location: /cdn/ar.alafasy-112.taqa\r\n")
            } else {
                out.respond(ByteArray(10), declared = true, status = "206 Partial Content")
            }
        }
        val response = HttpByteSource().open(local.url("/release/ar.alafasy-112.taqa"), fromByte = 5)
        assertIs<ByteResponse.Body>(response).body.close()
        assertEquals(listOf("/release/ar.alafasy-112.taqa", "/cdn/ar.alafasy-112.taqa"), local.heads.map { pathOf(it) })
        for (head in local.heads) assertEquals("Taqwa", headerOf(head, "User-Agent"), head)
    }

    private fun pathOf(head: String): String = head.lineSequence().first().split(" ")[1]

    private fun headerOf(head: String, name: String): String? = head.lineSequence()
        .drop(1)
        .firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
}

/** A 200 with [body], its length in a Content-Length when [declared], and the connection closed. */
internal fun OutputStream.respond(body: ByteArray, declared: Boolean, status: String = "200 OK", extra: String = "") {
    val length = if (declared) "Content-Length: ${body.size}\r\n" else ""
    write("HTTP/1.1 $status\r\n${length}${extra}Connection: close\r\n\r\n".encodeToByteArray())
    write(body)
    flush()
}

/**
 * HTTP on the loopback interface, one exchange per connection, written by hand because the host
 * tests compile against android.jar, which has no HTTP server. The head of every request is kept,
 * so a test can read the headers the app sent.
 */
internal class LoopbackServer(private val respond: (path: String, out: OutputStream) -> Unit) : AutoCloseable {

    private val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())

    val heads: MutableList<String> = CopyOnWriteArrayList()

    fun url(path: String): String = "http://127.0.0.1:${socket.localPort}$path"

    init {
        thread(isDaemon = true, name = "loopback-http") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (closed: IOException) {
                    break
                }
                client.use {
                    val head = readHead(it.getInputStream())
                    heads += head
                    try {
                        respond(head.lineSequence().first().split(" ")[1], it.getOutputStream())
                    } catch (hungUp: IOException) {
                        // The client stopped reading, which is what a capped read is meant to do.
                    }
                }
            }
        }
    }

    private fun readHead(input: InputStream): String {
        val head = ByteArrayOutputStream()
        var tail = 0
        while (true) {
            val b = input.read()
            if (b == -1) break
            head.write(b)
            tail = (tail shl 8) or b
            if (tail == 0x0D0A0D0A) break
        }
        return head.toString(Charsets.ISO_8859_1.name())
    }

    override fun close() = socket.close()
}
