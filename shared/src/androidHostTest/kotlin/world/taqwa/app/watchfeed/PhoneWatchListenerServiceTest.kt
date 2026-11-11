package world.taqwa.app.watchfeed

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import world.taqwa.app.di.appContainer
import world.taqwa.app.watch.WatchLinkContract
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The phone's listener for the watch (plan 2 Task 5, Ruling 54): what wakes it, as `:androidApp`'s
 * manifest and its `res/values/wear.xml` declare it, held to `WatchLinkContract`'s constants so
 * that neither side can drift silently (item 2); and the bounded wrapper every one of its bodies
 * runs in on the service's thread (item 5), with the service's own read of its container inside it
 * (Ruling 55 item 2).
 */
class PhoneWatchListenerServiceTest {

    // ── Item 2: the declarations Play services reads, tied to the contract ──

    /**
     * The listener wakes for the watch's handshake and its acknowledgement alone, by their literal
     * paths (`android:path`, never a prefix or a pattern), and for `taqwa_watch` changing. A path
     * renamed in the contract, or a filter widened to a prefix, fails here.
     */
    @Test
    fun theManifestWakesTheListenerForTheContractsPathsAlone() {
        val manifest = xml(androidApp("src/main/AndroidManifest.xml"))
        val service = manifest.getElementsByTagName("service").elements()
            .single { it.getAttributeNS(ANDROID, "name") == PhoneWatchListenerService::class.java.name }
        assertEquals("true", service.getAttributeNS(ANDROID, "exported"), "Play services binds to it")

        val wakes = service.children("intent-filter").map { filter ->
            val action = filter.children("action").single().getAttributeNS(ANDROID, "name")
            val data = filter.children("data").single()
            assertEquals(
                setOf("scheme", "host", "path"),
                data.androidAttributes(),
                "$action: the wear scheme, any host and one literal path, never a prefix or a pattern",
            )
            assertEquals("wear", data.getAttributeNS(ANDROID, "scheme"), action)
            assertEquals("*", data.getAttributeNS(ANDROID, "host"), action)
            action to data.getAttributeNS(ANDROID, "path")
        }
        assertEquals(
            listOf(
                MessageClient.ACTION_MESSAGE_RECEIVED to WatchLinkContract.REFRESH_PATH,
                MessageClient.ACTION_MESSAGE_RECEIVED to WatchLinkContract.ACK_PATH,
                CapabilityClient.ACTION_CAPABILITY_CHANGED to "/${WatchLinkContract.WATCH_CAPABILITY}",
            ),
            wakes,
        )
    }

    /**
     * The phone says it is Taqwa's by the contract's own capability, the one the watch shakes hands
     * with, and keeps the resource from shrinking, since only Play services reads it.
     */
    @Test
    fun thePhonesCapabilityIsTheContractsAndKept() {
        val wear = xml(androidApp("src/main/res/values/wear.xml"))
        val capabilities = wear.getElementsByTagName("string-array").elements()
            .single { it.getAttribute("name") == "android_wear_capabilities" }
        assertEquals(listOf(WatchLinkContract.PHONE_CAPABILITY), capabilities.children("item").map { it.textContent.trim() })
        assertEquals("@array/android_wear_capabilities", wear.documentElement.getAttributeNS(TOOLS, "keep"))
    }

    // ── Item 5: the wrapper around each body ──

    /**
     * A container that cannot be built (or any other throw) costs the watch that one message: the
     * wrapper returns, nothing is thrown on the service's thread, and the next message is served.
     * Any `Exception` (Ruling 55 item 2), not only an `IllegalStateException`: the lateinit
     * `appContext` read before it is set throws Kotlin's `UninitializedPropertyAccessException`, and
     * an `IOException` is checked, so not even a `RuntimeException`.
     */
    @Test
    fun aContainerThatThrowsCostsTheListenerThatMessageAlone() {
        val throws = listOf(
            IllegalStateException("the container cannot be built"),
            UninitializedPropertyAccessException("lateinit property appContext has not been initialized"),
            IOException("the settings file cannot be read"),
        )
        for (thrown in throws) {
            val container: () -> WatchFeedSync = { throw thrown }
            val bodies = listOf<suspend () -> Unit>(
                { takeWatchMessage(container(), WatchLinkContract.REFRESH_PATH, "".encodeToByteArray(), from = "node-a") },
                { takeWatchMessage(container(), WatchLinkContract.ACK_PATH, "".encodeToByteArray(), from = "node-a") },
                { takeCapabilityChange(container(), WatchLinkContract.WATCH_CAPABILITY, listOf(WatchNode("node-a", nearby = true))) },
            )
            for ((index, body) in bodies.withIndex()) {
                val run = onTheServicesThread { bounded(bound = 1.seconds, body) }
                assertTrue(run.returned, "${thrown::class.simpleName}, body $index returned")
                assertNull(run.thrown, "${thrown::class.simpleName}, body $index threw nothing")
            }
        }

        val served = mutableListOf<String>()
        val run = onTheServicesThread { bounded(bound = 1.seconds) { served += "next" } }
        assertTrue(run.returned)
        assertEquals(listOf("next"), served, "the next message is served")
    }

    /**
     * A sync whose worker is gone holds no body longer than the wrapper's bound: one whose scope was
     * cancelled answers at once, and one whose worker never serves (a dispatcher nothing drains, its
     * own caller bound an hour) is cut by the wrapper itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSyncWhoseWorkerIsGoneHoldsTheListenerNoLongerThanItsBound() {
        val stopped = CoroutineScope(Job()).apply { cancel() }
        val gone = sync(scope = stopped, work = Dispatchers.Unconfined, callerBound = 1.hours)
        val alive = CoroutineScope(Job())
        val neverServed = sync(scope = alive, work = StandardTestDispatcher(TestCoroutineScheduler()), callerBound = 1.hours)
        try {
            for ((name, sync) in listOf("gone" to gone, "never served" to neverServed)) {
                val bodies = listOf<suspend () -> Unit>(
                    { takeWatchMessage(sync, WatchLinkContract.REFRESH_PATH, "".encodeToByteArray(), from = "node-a") },
                    { takeWatchMessage(sync, WatchLinkContract.ACK_PATH, "".encodeToByteArray(), from = "node-a") },
                    { takeCapabilityChange(sync, WatchLinkContract.WATCH_CAPABILITY, listOf(WatchNode("node-a", nearby = true))) },
                )
                for ((index, body) in bodies.withIndex()) {
                    val run = onTheServicesThread { bounded(bound = BOUND, body) }
                    assertTrue(run.returned, "$name, body $index: returned within ${GUARD.inWholeSeconds} s")
                    assertNull(run.thrown, "$name, body $index threw nothing")
                    assertTrue(run.took < BOUND + SLACK, "$name, body $index took ${run.took}")
                }
            }
        } finally {
            alive.cancel()
        }
    }

    /**
     * The service itself reads `appContainer.watchFeedSync` inside the wrapper (Ruling 55 item 2),
     * so a container that cannot be built costs each of its bodies that one message, never the
     * service's thread. A host test has no `appContext`, so the container cannot be built here,
     * which is the very case. That is asserted first, so a test run that can one day build it fails
     * here instead of passing for another reason.
     */
    @Test
    fun theServiceReadsTheContainerInsideTheWrapper() {
        assertFailsWith<Exception>("no appContext in a host test, so no container") { appContainer }
        val service = PhoneWatchListenerService()
        val calls = listOf<Pair<String, () -> Unit>>(
            "the handshake" to { service.onMessageReceived(message(WatchLinkContract.REFRESH_PATH)) },
            "the acknowledgement" to { service.onMessageReceived(message(WatchLinkContract.ACK_PATH)) },
            "the capability change" to { service.onCapabilityChanged(capability(WatchLinkContract.WATCH_CAPABILITY)) },
        )
        for ((name, call) in calls) {
            val run = onTheServicesThread(call)
            assertTrue(run.returned, "$name returned")
            assertNull(run.thrown, "$name threw nothing on the service's thread")
        }
    }

    // ── Helpers ──

    /** A message from node A on [path], its echo empty, as Play services hands the service one. */
    private fun message(path: String): MessageEvent = object : MessageEvent {
        override fun getRequestId(): Int = 1
        override fun getPath(): String = path
        override fun getData(): ByteArray = ByteArray(0)
        override fun getSourceNodeId(): String = "node-a"
    }

    /** [name]'s nodes, as Play services tells the service of a change: node A, nearby. */
    private fun capability(name: String): CapabilityInfo = object : CapabilityInfo {
        override fun getName(): String = name
        override fun getNodes(): Set<Node> = setOf(
            object : Node {
                override fun getId(): String = "node-a"
                override fun getDisplayName(): String = "Watch A"
                override fun isNearby(): Boolean = true
            },
        )
    }

    private fun sync(scope: CoroutineScope, work: CoroutineDispatcher, callerBound: Duration) = WatchFeedSync(
        feedFor = { syncFeed() },
        link = FakeWatchLink(confirmsDelivery = true),
        memory = FakeWatchFeedMemory(),
        scope = scope,
        work = work,
        callerBound = callerBound,
    )

    /** How one body went on a thread of its own, as Play services runs the listener's. */
    private class Run(val returned: Boolean, val thrown: Throwable?, val took: Duration)

    /** Runs [body] on a thread of its own and waits for it at most [GUARD], so a wrapper that never returns fails the test. */
    private fun onTheServicesThread(body: () -> Unit): Run {
        var thrown: Throwable? = null
        val started = TimeSource.Monotonic.markNow()
        val worker = thread(isDaemon = true, name = "listener") {
            try {
                body()
            } catch (t: Throwable) {
                thrown = t
            }
        }
        worker.join(GUARD.inWholeMilliseconds)
        return Run(returned = !worker.isAlive, thrown = thrown, took = started.elapsedNow())
    }

    private fun androidApp(path: String): File =
        if (File("src/androidHostTest").isDirectory) File("../androidApp/$path") else File("androidApp/$path")

    private fun xml(file: File): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) }.filterIsInstance<Element>()

    private fun Element.children(tag: String): List<Element> = childNodes.elements().filter { it.tagName == tag }

    /** The names of this element's `android:` attributes. */
    private fun Element.androidAttributes(): Set<String> =
        (0 until attributes.length).map { attributes.item(it) }.filter { it.namespaceURI == ANDROID }.map { it.localName }.toSet()

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val TOOLS = "http://schemas.android.com/tools"

        /** The wrapper's bound in these tests: the service's is [WatchFeedSync.CALLER_BOUND]. */
        val BOUND: Duration = 300.milliseconds

        /** How much later than [BOUND] a cut body may return on a loaded machine. */
        val SLACK: Duration = 5.seconds

        /** How long a test waits for a body before calling it stuck. */
        val GUARD: Duration = 20.seconds
    }
}
