package world.taqwa.app.watchfeed

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.concurrent.futures.await
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import world.taqwa.app.watch.WatchLinkContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Settings › Watch's Android source (plan 5 Task 6) over Play services tasks the test hands it, so
 * the real `await()` and the real reading of each answer's code space are on its path:
 * - the watches from `NodeClient.getConnectedNodes()` (each one's `displayName` and `isNearby`);
 * - which of them has Taqwa (`taqwa_watch`) and which has the face to add (`taqwa_face_addable`),
 *   each from its own `getCapability(…, FILTER_REACHABLE)`;
 * - each row's tap through `RemoteActivityHelper`, given the intent [remoteIntentSpec] makes;
 * - while Settings is in front, a listener on both capabilities (`CapabilityClient.addListener`),
 *   removed when it leaves.
 *
 * Like the feed's sender, it asks Play services' availability first, and makes no Wearable call at
 * all without it, so nothing can show an update prompt (`WearCodesTest` scans the package's calls).
 */
class PlayWatchTargetsTest {

    private val galaxy = WatchNode("4a1f", nearby = true)
    private val pixel = WatchNode("77c0", nearby = true)
    private val throughTheCloud = WatchNode("cloud", nearby = false)

    /** A [PlayWatchTargets] over tasks this test tells, counting every Wearable call it makes. */
    private class Tasks(private val availability: Int = ConnectionResult.SUCCESS) {
        var connected: Task<List<Node>> = FakeTask.succeeded(emptyList())
        val capabilities: MutableMap<String, Task<CapabilityInfo>> = mutableMapOf()
        val asked: MutableList<String> = mutableListOf()
        var remoteThrows: Throwable? = null
        var remoteWaits = false
        val remotes: MutableList<Pair<RemoteIntentSpec, String>> = mutableListOf()
        val listened: MutableList<Pair<String, CapabilityClient.OnCapabilityChangedListener>> = mutableListOf()
        val stopped: MutableList<Pair<String, CapabilityClient.OnCapabilityChangedListener>> = mutableListOf()
        var listenThrowsFor: String? = null
        var stopThrows = false

        val source = PlayWatchTargets(
            availability = { availability },
            connectedNodes = {
                asked += "connectedNodes"
                connected
            },
            capability = { name ->
                asked += name
                capabilities[name] ?: FakeTask.succeeded(capabilityOf(emptyList(), name))
            },
            remote = { spec, nodeId ->
                remotes += spec to nodeId
                if (remoteWaits) awaitCancellation()
                remoteThrows?.let { throw it }
            },
            listen = { name, listener ->
                asked += "listen $name"
                if (name == listenThrowsFor) throw IllegalStateException("addListener threw")
                listened += name to listener
                val stop = {
                    stopped += name to listener
                    if (stopThrows) throw IllegalStateException("removeListener threw")
                }
                stop
            },
        )

        fun connect(vararg nodes: WatchNode) {
            connected = FakeTask.succeeded(nodes.map(::nodeOf))
        }

        fun declare(name: String, vararg nodes: WatchNode) {
            capabilities[name] = FakeTask.succeeded(capabilityOf(nodes.toList(), name))
        }
    }

    private fun target(node: WatchNode) = WatchTarget(nodeId = node.id, name = "Watch ${node.id}")

    @Test
    fun withoutPlayServicesNothingIsAsked() = runTest {
        for (code in listOf(
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_DISABLED,
            ConnectionResult.SERVICE_INVALID,
            ConnectionResult.SERVICE_UPDATING,
        )) {
            val tasks = Tasks(availability = code)
            tasks.connect(galaxy)
            assertEquals(WatchTargets.NONE, tasks.source.targets(), "availability $code")
            assertEquals(emptyList(), tasks.asked, "availability $code: no Wearable call")
        }
    }

    @Test
    fun aPhoneWithoutTheWearApiIsNone() = runTest {
        val tasks = Tasks()
        tasks.connected = FakeTask.failed(ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED)))
        assertEquals(WatchTargets.NONE, tasks.source.targets())
        assertEquals(listOf("connectedNodes"), tasks.asked, "no capability asked")
    }

    @Test
    fun anyOtherFailureIsThrownAsItself() = runTest {
        for (code in listOf(CommonStatusCodes.CANCELED, CommonStatusCodes.INTERNAL_ERROR, CommonStatusCodes.TIMEOUT)) {
            val error = ApiException(Status(code))
            val tasks = Tasks()
            tasks.connected = FakeTask.failed(error)
            assertSame(error, assertFailsWith<ApiException> { tasks.source.targets() }, "status $code")
            // And Settings shows no section for it.
            val again = Tasks()
            again.connected = FakeTask.failed(ApiException(Status(code)))
            assertEquals(WatchTargets.NONE, loadWatchTargets(again.source), "status $code")
        }
        val tasks = Tasks()
        tasks.connect(galaxy)
        tasks.capabilities[WatchLinkContract.FACE_CAPABILITY] = FakeTask.cancelled()
        assertFailsWith<TaskCancelled> { tasks.source.targets() }
    }

    @Test
    fun theWatchesAndTheirCapabilitiesMakeTheRows() = runTest {
        val tasks = Tasks()
        tasks.connect(pixel, galaxy, throughTheCloud)
        tasks.declare(WatchLinkContract.WATCH_CAPABILITY, galaxy, throughTheCloud)
        tasks.declare(WatchLinkContract.FACE_CAPABILITY, galaxy, throughTheCloud)
        assertEquals(WatchTargets(install = target(pixel), addFace = target(galaxy)), tasks.source.targets())
        assertEquals(
            listOf("connectedNodes", WatchLinkContract.WATCH_CAPABILITY, WatchLinkContract.FACE_CAPABILITY),
            tasks.asked,
        )
    }

    @Test
    fun aWatchWithTaqwaAndItsFaceIsNone() = runTest {
        val tasks = Tasks()
        tasks.connect(galaxy)
        tasks.declare(WatchLinkContract.WATCH_CAPABILITY, galaxy)
        assertEquals(WatchTargets.NONE, tasks.source.targets())
    }

    @Test
    fun noWatchDirectlyConnectedAsksNoCapability() = runTest {
        val tasks = Tasks()
        tasks.connect(throughTheCloud)
        assertEquals(WatchTargets.NONE, tasks.source.targets())
        assertEquals(listOf("connectedNodes"), tasks.asked)
    }

    @Test
    fun aTapStartsItsIntentOnItsWatch() = runTest {
        val tasks = Tasks()
        assertTrue(tasks.source.open(target(galaxy), WatchAction.INSTALL))
        assertTrue(tasks.source.open(target(pixel), WatchAction.ADD_FACE))
        assertEquals(
            listOf(
                remoteIntentSpec(WatchAction.INSTALL) to galaxy.id,
                remoteIntentSpec(WatchAction.ADD_FACE) to pixel.id,
            ),
            tasks.remotes,
        )
    }

    @Test
    fun aTapTheWatchRefusesIsFalse() = runTest {
        val tasks = Tasks()
        tasks.remoteThrows = IllegalStateException("RemoteIntentException: the companion app answered RESULT_FAILED")
        assertFalse(tasks.source.open(target(galaxy), WatchAction.INSTALL))
        tasks.remoteThrows = ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))
        assertFalse(tasks.source.open(target(galaxy), WatchAction.ADD_FACE))
    }

    /** Ruling 76 item 9: the VM's own errors are no answer from the watch, and pass. */
    @Test
    fun anErrorOfTheVmIsNoAnswer() = runTest {
        val error = OutOfMemoryError("the VM's own")
        val tasks = Tasks()
        tasks.remoteThrows = error
        assertSame(error, assertFailsWith<OutOfMemoryError> { tasks.source.open(target(galaxy), WatchAction.ADD_FACE) })
    }

    /**
     * Ruling 76 item 9: the caller's own cancel passes, and is no answer; a stray cancel from
     * inside the start, the caller still there, is a watch that did not take it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aCancelledTapHasNoAnswerAndAStrayCancelIsFalse() = runTest {
        val tasks = Tasks()
        tasks.remoteWaits = true
        var answered: Boolean? = null
        val tap = launch { answered = tasks.source.open(target(galaxy), WatchAction.ADD_FACE) }
        runCurrent()
        assertEquals(1, tasks.remotes.size)
        tap.cancelAndJoin()
        assertNull(answered, "the caller's own cancel gave an answer")

        tasks.remoteWaits = false
        tasks.remoteThrows = CancellationException("a task cancelled inside the start")
        assertFalse(tasks.source.open(target(galaxy), WatchAction.ADD_FACE))
    }

    /**
     * Ruling 74 item 14's known limit: a companion that never answers, and lets the helper's result
     * receiver go, ends through `CallbackToFutureAdapter`'s garbage-collected path. Its completer
     * is finalised, and the future fails with `FutureGarbageCollectedException`, a `Throwable` that
     * is no `Exception`. That is a watch that did not take it: false ("Couldn’t reach your watch"),
     * never a throw out of the row's tap. The real adapter, and a real collection, so this leans on
     * JVM finalization, which JEP 421 deprecates for removal: on a JDK without it, this test fails
     * at its 30 s bound, and does not pass silently.
     */
    @Test
    fun aTapWhoseAnswerIsCollectedUnansweredIsFalse() = runBlocking {
        val source = PlayWatchTargets(
            availability = { ConnectionResult.SUCCESS },
            connectedNodes = { FakeTask.succeeded(emptyList()) },
            capability = { name -> FakeTask.succeeded(capabilityOf(emptyList(), name)) },
            // As RemoteActivityHelper's future, whose completer only its result receiver holds.
            remote = { _, _ -> CallbackToFutureAdapter.getFuture<Any?> { "a companion that never answers" }.await() },
            listen = { _, _ -> {} },
        )
        val opened = async(Dispatchers.Default) { runCatching { source.open(target(galaxy), WatchAction.ADD_FACE) } }
        withTimeout(30.seconds) {
            while (!opened.isCompleted) {
                System.gc()
                delay(20)
            }
        }
        assertEquals(Result.success(false), opened.await())
    }

    /**
     * The owner's report of 9 November: while Settings is in front it hears each change of either
     * capability, a watch getting Taqwa (`taqwa_watch`), taking its face (`taqwa_face_addable`
     * removed), or a watch with Taqwa coming and going, and reads the rows again. One listener on
     * both, added when Settings starts listening and removed from both when it stops; listening
     * itself reads nothing. Its first word is its own, once both are added
     * ([onceBothListenersAreAddedItSaysOneChange]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aListenerHearsBothCapabilitiesUntilSettingsStopsListening() = runTest {
        val tasks = Tasks()
        var heard = 0
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) { tasks.source.changes().collect { heard += 1 } }
        runCurrent()
        assertEquals(listOf(WatchLinkContract.WATCH_CAPABILITY, WatchLinkContract.FACE_CAPABILITY), tasks.listened.map { it.first })
        val listener = tasks.listened.map { it.second }.distinct().single()
        assertEquals(emptyList(), tasks.stopped, "removed while still listening")
        assertEquals(1, heard, "its own word, once both are added")

        listener.onCapabilityChanged(capabilityOf(listOf(galaxy), WatchLinkContract.WATCH_CAPABILITY))
        runCurrent()
        assertEquals(2, heard, "the watch got Taqwa")
        listener.onCapabilityChanged(capabilityOf(emptyList(), WatchLinkContract.FACE_CAPABILITY))
        runCurrent()
        assertEquals(3, heard, "the watch took its face")

        listening.cancelAndJoin()
        assertEquals(tasks.listened, tasks.stopped, "the same listener removed from both")
        listener.onCapabilityChanged(capabilityOf(listOf(galaxy), WatchLinkContract.WATCH_CAPABILITY))
        runCurrent()
        assertEquals(3, heard, "nothing heard once Settings stopped")
        assertEquals(
            listOf("listen ${WatchLinkContract.WATCH_CAPABILITY}", "listen ${WatchLinkContract.FACE_CAPABILITY}"),
            tasks.asked,
            "no node or capability read: Settings reads the rows itself",
        )
    }

    /**
     * The review of 9 November: Settings starts listening as its first reading of the rows asks,
     * and Play services adds a listener asynchronously, so a change that lands after that reading
     * asked and before the listener is in place would be neither read nor heard until a tap's
     * follow or the next resume. So once both are added the listening says one change of its own,
     * and Settings reads the rows once more, after them. Nothing is said before the second add, and
     * a listener that cannot be added says nothing at all
     * ([aListenerThatCannotBeAddedOrRemovedLeavesNothingBehind]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onceBothListenersAreAddedItSaysOneChange() = runTest {
        val tasks = Tasks()
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) { tasks.source.changes().collect { tasks.asked += "heard" } }
        runCurrent()
        assertEquals(
            listOf("listen ${WatchLinkContract.WATCH_CAPABILITY}", "listen ${WatchLinkContract.FACE_CAPABILITY}", "heard"),
            tasks.asked,
            "one word, after both adds and before any change",
        )
        listening.cancelAndJoin()
    }

    /** As [withoutPlayServicesNothingIsAsked]: no Wearable client, so no listener and nothing that can show. */
    @Test
    fun withoutPlayServicesNothingIsListenedTo() = runTest {
        for (code in listOf(
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_DISABLED,
            ConnectionResult.SERVICE_INVALID,
            ConnectionResult.SERVICE_UPDATING,
        )) {
            val tasks = Tasks(availability = code)
            assertEquals(emptyList(), tasks.source.changes().toList(), "availability $code: ends, saying nothing")
            assertEquals(emptyList(), tasks.asked, "availability $code: no Wearable call")
        }
    }

    /**
     * A listener that cannot be added ends the listening with its throw, which Settings drops, with
     * nothing said, and the one already added is removed. A removal that throws stops neither the other removal nor
     * Settings: a throw while Settings leaves would end its screen, not the listening.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aListenerThatCannotBeAddedOrRemovedLeavesNothingBehind() = runTest {
        val refused = Tasks()
        refused.listenThrowsFor = WatchLinkContract.FACE_CAPABILITY
        var said = 0
        assertFailsWith<IllegalStateException> { refused.source.changes().collect { said += 1 } }
        assertEquals(0, said, "nothing said by a listening that could not start")
        assertEquals(refused.listened, refused.stopped)
        assertEquals(WatchLinkContract.WATCH_CAPABILITY, refused.stopped.single().first)

        val tasks = Tasks()
        tasks.stopThrows = true
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) { tasks.source.changes().collect() }
        runCurrent()
        listening.cancelAndJoin()
        assertTrue(listening.isCancelled, "ended as cancelled, with no throw of its own")
        assertEquals(tasks.listened, tasks.stopped, "both removals tried")
    }

    @Test
    fun withoutPlayServicesATapIsFalseAndAsksNothing() = runTest {
        val tasks = Tasks(availability = ConnectionResult.SERVICE_MISSING)
        assertFalse(tasks.source.open(target(galaxy), WatchAction.INSTALL))
        assertEquals(emptyList(), tasks.remotes)
    }
}
