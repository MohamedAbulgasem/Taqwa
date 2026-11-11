package world.taqwa.app.watchfeed

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.wearable.WearableStatusCodes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watchfeed.WatchSendResult.FAILED
import world.taqwa.app.watchfeed.WatchSendResult.NO_WATCH
import world.taqwa.app.watchfeed.WatchSendResult.SENT
import world.taqwa.app.watchfeed.WatchSendResult.UNCHANGED
import world.taqwa.app.watchfeed.WatchSyncReason.RESCHEDULED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_APPEARED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_ASKED
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Android phone's link to the user's own watch (plan 2 Task 5): the Wear Data Layer's
 * `MessageClient`, to the nodes with `taqwa_watch` that are directly connected when the send is
 * made, asked again at the send, each Play services answer read in its own code space. And the watch
 * key of Ruling 52 item 9: the Wear node id of the watch whose echo was taken in, which counts only
 * while that watch is nearby.
 *
 * The sync's cases run on virtual time over fakes alone: the sync's scope is `backgroundScope`, its
 * worker a `StandardTestDispatcher` of the test's own scheduler, and the Play services tasks of
 * [TaskWatchNodes] suspend in our own `await()` and nowhere else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidWatchLinkTest {

    private val a = WatchNode("node-a", nearby = true)
    private val b = WatchNode("node-b", nearby = true)
    private val cloudOnly = WatchNode("node-c", nearby = false)

    private val x = syncFeed()
    private val delivery = WatchDelivery(TEST_ORIGIN, 7, watchFeedFingerprint(x))

    /**
     * A sync over [link] and a record in memory, as plan 2 Task 4's tests build theirs. [duringBuild]
     * runs inside each build, for a world that moves while the feed is being built.
     */
    private class AndroidSync(test: TestScope, val link: AndroidWatchLink) {
        val memory = FakeWatchFeedMemory()
        var feed: WatchFeed? = syncFeed()
        var builds = 0
        var duringBuild: suspend () -> Unit = {}
        val reports = mutableListOf<Pair<WatchSendResult, Long>>()
        val sync = WatchFeedSync(
            feedFor = {
                builds++
                duringBuild()
                feed
            },
            link = link,
            memory = memory,
            scope = test.backgroundScope,
            work = StandardTestDispatcher(test.testScheduler),
            report = { _, result, _ -> reports += result to test.testScheduler.currentTime },
        )

        /** The echo of the latest delivery sent, as a watch that stored it says it back. */
        val latestEcho: String get() = WatchLinkContract.echoOf(memory.record?.lastDelivery)
    }

    private fun TestScope.over(nodes: WatchNodes): AndroidSync = AndroidSync(this, AndroidWatchLink(nodes))

    // ── The link's own rules ──

    @Test
    fun theLinkConfirmsDelivery() {
        val link = AndroidWatchLink(FakeWatchNodes())
        assertTrue(link.confirmsDelivery, "a send is only queued: only the watch's echo confirms")
    }

    @Test
    fun withoutPlayServicesNothingIsAskedOrShown() = runTest {
        // No Play services (or one being updated): the availability check alone, never a Wearable
        // client, which is where an update prompt or a notification could come from.
        for (code in listOf(ConnectionResult.SERVICE_MISSING, ConnectionResult.SERVICE_UPDATING)) {
            val tasks = TaskWatchNodes(availability = code)
            tasks.nearby = listOf(a)
            val link = AndroidWatchLink(tasks.nodes)
            assertFalse(link.reachable(), "ConnectionResult $code")
            assertEquals(NO_WATCH, link.send(WatchFeedJson.encode(x), delivery))
            assertEquals(0, tasks.capabilityAsked, "no capability query")
            assertEquals(emptyList(), tasks.sends.map { it.nodeId }, "no message")
        }

        val nodes = FakeWatchNodes()
        nodes.answers += null
        nodes.answers += null
        val link = AndroidWatchLink(nodes)
        assertFalse(link.reachable())
        assertEquals(NO_WATCH, link.send(WatchFeedJson.encode(x), delivery))
        assertEquals(emptyList(), nodes.sends.map { it.nodeId }, "send on the nodes is never called")
    }

    @Test
    fun theWearApiMissingIsNoWatchNotAFailure() = runTest {
        // Most Android phones: Play services, but no Wear OS app, so getCapability fails with 17.
        val tasks = TaskWatchNodes()
        tasks.capabilityTasks += FakeTask.failed(ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED)))
        val viaTasks = over(tasks.nodes)
        assertEquals(NO_WATCH, viaTasks.sync.sync(RESCHEDULED))
        assertEquals(0, viaTasks.builds, "nothing built")

        val nodes = FakeWatchNodes()
        nodes.answers += null
        val h = over(nodes)
        assertEquals(NO_WATCH, h.sync.sync(RESCHEDULED))
        assertEquals(0, h.builds)
        assertEquals(emptyList(), h.memory.calls, "and nothing read")
    }

    @Test
    fun aCancelledCapabilityQueryIsAFailure() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a))
        nodes.watchesThrows = TaskCancelled()
        val h = over(nodes)
        assertEquals(FAILED, h.sync.sync(RESCHEDULED), "never NO_WATCH")
        nodes.watchesThrows = null
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "the next ask is served")

        val tasks = TaskWatchNodes()
        tasks.nearby = listOf(a)
        tasks.capabilityTasks += FakeTask.cancelled()
        val viaTasks = over(tasks.nodes)
        assertEquals(FAILED, viaTasks.sync.sync(RESCHEDULED))
        assertEquals(SENT, viaTasks.sync.sync(RESCHEDULED))
    }

    @Test
    fun noWatchWithTaqwaIsNoWatch() = runTest {
        val nodes = FakeWatchNodes(steady = emptyList())
        val link = AndroidWatchLink(nodes)
        assertFalse(link.reachable())
        assertEquals(NO_WATCH, link.send(WatchFeedJson.encode(x), delivery))
        val h = over(nodes)
        assertEquals(NO_WATCH, h.sync.sync(RESCHEDULED))
        assertEquals(0, h.builds)
        assertEquals(emptyList(), nodes.sends.map { it.nodeId })
    }

    @Test
    fun aWatchReachedOnlyThroughTheCloudIsNotSent() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(cloudOnly))
        val link = AndroidWatchLink(nodes)
        assertFalse(link.reachable())
        assertEquals(NO_WATCH, link.send(WatchFeedJson.encode(x), delivery))
        assertEquals(emptyList(), nodes.sends.map { it.nodeId })
        assertEquals(emptySet(), link.watchKeys(), "and no watch a confirmation could count for")
    }

    @Test
    fun nearbyIsAskedAgainAtTheSend() = runTest {
        val nodes = FakeWatchNodes()
        nodes.answers += listOf(a)
        nodes.answers += listOf(WatchNode(a.id, nearby = false)) // gone over to the cloud by the send
        val link = AndroidWatchLink(nodes)
        assertTrue(link.reachable())
        assertEquals(NO_WATCH, link.send(WatchFeedJson.encode(x), delivery))
        assertEquals(2, nodes.watchesAsked)
        assertEquals(emptyList(), nodes.sends.map { it.nodeId }, "no message goes")
    }

    @Test
    fun aNodeThatLeftAtTheSendIsNoWatch() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a, b))
        nodes.onSend = { NodeSend.GONE }
        assertEquals(NO_WATCH, AndroidWatchLink(nodes).send(WatchFeedJson.encode(x), delivery))
        assertEquals(setOf(a.id, b.id), nodes.sends.map { it.nodeId }.toSet(), "both were tried")

        // What PlayWatchNodes reads as gone: the node (4000), or the API itself (17).
        for (code in listOf(WearableStatusCodes.TARGET_NODE_NOT_CONNECTED, CommonStatusCodes.API_NOT_CONNECTED)) {
            val tasks = TaskWatchNodes()
            tasks.sendTasks += FakeTask.failed(ApiException(Status(code)))
            assertEquals(NodeSend.GONE, tasks.nodes.send(a.id, WatchLinkContract.feedPath(delivery), byteArrayOf(1)), "status $code")
        }
        // Anything else is a failure, thrown as itself.
        for (code in listOf(CommonStatusCodes.CANCELED, CommonStatusCodes.INTERNAL_ERROR, CommonStatusCodes.TIMEOUT)) {
            val tasks = TaskWatchNodes()
            val error = ApiException(Status(code))
            tasks.sendTasks += FakeTask.failed(error)
            val thrown = assertFailsWith<ApiException> { tasks.nodes.send(a.id, WatchLinkContract.feedPath(delivery), byteArrayOf(1)) }
            assertSame(error, thrown, "status $code")
        }
    }

    @Test
    fun theFeedGoesToEveryNearbyWatchOnTheFeedPath() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a, cloudOnly, b))
        val json = WatchFeedJson.encode(x)
        assertEquals(SENT, AndroidWatchLink(nodes).send(json, delivery))
        assertEquals(setOf(a.id, b.id), nodes.sends.map { it.nodeId }.toSet(), "never the one reached through the cloud")
        assertEquals(2, nodes.sends.size)
        for (message in nodes.sends) {
            assertEquals(WatchLinkContract.feedPath(delivery), message.path)
            assertEquals("/taqwa/feed/$TEST_ORIGIN/7/${delivery.fingerprint}", message.path)
            assertContentEquals(json.encodeToByteArray(), message.data, "the JSON in UTF-8")
        }

        // Through the sync, the delivery the record wrote down travels on the path.
        val one = FakeWatchNodes(steady = listOf(a))
        val h = over(one)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        val sent = one.sends.single()
        assertEquals(WatchLinkContract.feedPath(WatchDelivery(TEST_ORIGIN, 1, watchFeedFingerprint(x))), sent.path)
        assertEquals(x, WatchFeedJson.decode(sent.data.decodeToString()))
    }

    @Test
    fun aFeedAtTheBudgetIsWithinTheMessageLimit() = runTest {
        // Task 3's largest feed: bn-BD on 12 hours, the worst city, a hundred control-character phrases.
        val feed = CapeTownSample.largestFeed("bn-BD", PhraseKind.CONTROL)
        val json = WatchFeedJson.encode(feed)
        val nodes = FakeWatchNodes(steady = listOf(a))
        // The longest path any delivery can have: the largest number an echo may lift the record to.
        val longest = WatchDelivery(TEST_ORIGIN, WatchFeedRecord.MAX_ECHOED_SEQ, watchFeedFingerprint(feed))
        assertEquals(SENT, AndroidWatchLink(nodes).send(json, longest))
        val message = nodes.sends.single()
        assertContentEquals(json.encodeToByteArray(), message.data, "the JSON's UTF-8")
        assertTrue(message.data.size <= WatchLinkContract.FEED_BUDGET_BYTES, "${message.data.size} bytes")
        assertTrue(message.path.encodeToByteArray().size <= WatchLinkContract.ENVELOPE_BYTES, message.path)
        assertTrue(
            message.data.size + WatchLinkContract.ENVELOPE_BYTES <= WatchLinkContract.MESSAGE_LIMIT_BYTES,
            "${message.data.size} bytes and the envelope within the message limit",
        )
        println("[measure] largest feed as a message: ${message.data.size} data bytes, path ${message.path.encodeToByteArray().size} bytes")
    }

    @Test
    fun oneWatchThatTookItIsSent() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a, b))
        nodes.onSend = { id -> if (id == a.id) NodeSend.QUEUED else throw IllegalStateException("the link broke") }
        assertEquals(SENT, AndroidWatchLink(nodes).send(WatchFeedJson.encode(x), delivery))
        assertEquals(setOf(a.id, b.id), nodes.sends.map { it.nodeId }.toSet(), "both were tried")

        nodes.onSend = { id -> if (id == a.id) throw IllegalStateException("the link broke") else NodeSend.QUEUED }
        nodes.sends.clear()
        assertEquals(SENT, AndroidWatchLink(nodes).send(WatchFeedJson.encode(x), delivery), "one node's throw never stops the next")
        assertEquals(2, nodes.sends.size)
    }

    @Test
    fun aThrowingNodeIsAFailureNotACrash() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a))
        nodes.onSend = { throw IllegalStateException("the link broke") }
        assertEquals(FAILED, AndroidWatchLink(nodes).send(WatchFeedJson.encode(x), delivery))

        val h = over(nodes)
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        nodes.onSend = { NodeSend.QUEUED }
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "the next ask is served")

        nodes.onSend = { id -> if (id == a.id) NodeSend.GONE else throw IllegalStateException("the link broke") }
        nodes.steady = listOf(a, b)
        assertEquals(FAILED, AndroidWatchLink(nodes).send(WatchFeedJson.encode(x), delivery), "one gone, one thrown")
    }

    // ── A Play services call that never answers ──

    @Test
    fun aWatchQueryThatNeverAnswersIsCutAndTheNextAskIsServed() = runTest {
        val tasks = TaskWatchNodes()
        tasks.nearby = listOf(a)
        val h = over(tasks.nodes)
        val start = currentTime

        tasks.capabilityTasks += FakeTask.never()
        assertEquals(FAILED, h.sync.sync(RESCHEDULED), "the reachability question never answers")
        assertEquals(start + 5_000, h.reports.last().second)

        assertEquals(SENT, h.sync.sync(RESCHEDULED), "the capability and the send answer at once")
        assertEquals(start + 5_000, h.reports.last().second)

        tasks.sendTasks += FakeTask.never()
        assertEquals(FAILED, h.sync.sync(RESCHEDULED), "sendMessage never answers")
        assertEquals(start + 10_000, h.reports.last().second, "5 s after it was served")

        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(start + 10_000, h.reports.last().second)
        assertEquals(listOf(FAILED, SENT, FAILED, SENT), h.reports.map { it.first })
    }

    // ── Ruling 54 item 3: each failure read as itself ──

    /**
     * Mutant G1: only the "no Wear API" status (17) is no watch. A capability query that fails with
     * any other `ApiException` is a failure, thrown as itself: `CANCELED` (16, the same number as
     * `ConnectionResult.API_UNAVAILABLE`), `INTERNAL_ERROR`, `TIMEOUT`, `NETWORK_ERROR` or `ERROR`.
     * The ask is `FAILED`, never `NO_WATCH`, nothing is built, and the next ask is served.
     */
    @Test
    fun aCapabilityQueryThatFailsOtherwiseIsAFailure() = runTest {
        val codes = listOf(
            CommonStatusCodes.CANCELED,
            CommonStatusCodes.INTERNAL_ERROR,
            CommonStatusCodes.TIMEOUT,
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.ERROR,
        )
        for (code in codes) {
            val error = ApiException(Status(code))
            val tasks = TaskWatchNodes()
            tasks.capabilityTasks += FakeTask.failed(error)
            assertSame(error, assertFailsWith<ApiException> { tasks.nodes.watches() }, "status $code")

            tasks.nearby = listOf(a)
            tasks.capabilityTasks += FakeTask.failed(ApiException(Status(code)))
            val h = over(tasks.nodes)
            assertEquals(FAILED, h.sync.sync(RESCHEDULED), "status $code: never NO_WATCH")
            assertEquals(0, h.builds, "status $code: nothing built")
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "status $code: the next ask is served")
        }
    }

    /**
     * Mutant G2: a send the ask's budget cuts calls no further node. The cut reaches the send's
     * `catch` as the ask's own cancellation, which passes through, so the second nearby watch is
     * never handed a message once the budget has ended.
     */
    @Test
    fun aSendCutByTheBudgetCallsNoFurtherNode() = runTest {
        val tasks = TaskWatchNodes()
        tasks.nearby = listOf(a, b)
        val h = over(tasks.nodes)
        val start = currentTime

        tasks.sendTasks += FakeTask.never() // A's message never answers
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        assertEquals(start + 5_000, h.reports.last().second, "cut at the ask's 5 s")
        assertEquals(listOf(a.id), tasks.sends.map { it.nodeId }, "B is never called after the cut")

        assertEquals(SENT, h.sync.sync(RESCHEDULED), "the next ask is served")
        assertEquals(listOf(a.id, a.id, b.id), tasks.sends.map { it.nodeId })
    }

    // ── Ruling 54 item 4: one capability reading per ask ──

    /**
     * `reachable()` and `watchKeys()` answer from the one reading an ask takes inside its budget,
     * and `send()` asks again at the send (plan 2: nearness as close to the send as the API
     * allows). So an ask that sends nothing makes one capability query, an ask that sends makes
     * two, and an acknowledgement none. A reading is the ask's own: the next ask reads again.
     */
    @Test
    fun anAskReadsTheWatchesOnceAndAgainAtTheSend() = runTest {
        val tasks = TaskWatchNodes()
        val h = over(tasks.nodes)
        var counted = 0
        fun queries(): Int = (tasks.capabilityAsked - counted).also { counted = tasks.capabilityAsked }

        assertEquals(NO_WATCH, h.sync.sync(RESCHEDULED))
        assertEquals(1, queries(), "no watch: the ask's one reading")

        tasks.nearby = listOf(a)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(2, queries(), "a send: the ask's reading, then again at the send")

        h.sync.acknowledge(h.latestEcho, from = a.id)
        assertEquals(0, queries(), "an acknowledgement asks nothing")

        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(1, queries(), "unchanged: reachable() and watchKeys() share the reading")
        assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED))
        assertEquals(1, queries())
        assertEquals(UNCHANGED, h.sync.sync(WATCH_ASKED, have = h.latestEcho, from = a.id))
        assertEquals(1, queries())

        h.feed = null
        assertEquals(WatchSendResult.NO_FEED, h.sync.sync(RESCHEDULED))
        assertEquals(1, queries(), "no feed: the reading alone")

        h.feed = syncFeed()
        tasks.nearby = listOf(b) // the user put on the other watch: the next ask reads it
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(2, queries())
        assertEquals(b.id, tasks.sends.last().nodeId)
    }

    /** The re-ask at the send counts inside an ask too: a watch gone over to the cloud by then is not sent. */
    @Test
    fun aWatchGoneByTheSendOfAnAskIsNotSent() = runTest {
        val tasks = TaskWatchNodes()
        tasks.capabilityTasks += FakeTask.succeeded(capabilityOf(listOf(a))) // the ask's reading
        tasks.capabilityTasks += FakeTask.succeeded(capabilityOf(listOf(WatchNode(a.id, nearby = false)))) // at the send
        val h = over(tasks.nodes)
        assertEquals(NO_WATCH, h.sync.sync(RESCHEDULED))
        assertEquals(2, tasks.capabilityAsked)
        assertEquals(emptyList(), tasks.sends.map { it.nodeId }, "no message goes")
    }

    // ── Ruling 52 item 9: the watch key is the Wear node id ──

    @Test
    fun theWatchKeyIsTheNodeIdOfTheWatchThatEchoed() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a, cloudOnly))
        val h = over(nodes)
        assertNull(h.link.watchKey(), "no one watch: a send goes to every nearby one")
        assertEquals(setOf<String?>(a.id), h.link.watchKeys(), "the nearby watches with Taqwa")

        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.latestEcho, from = a.id)
        assertEquals(a.id, h.memory.record?.confirmedFor)
        assertEquals(h.memory.record?.lastSeq, h.memory.record?.confirmedSeq)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED), "a reconnect with nothing changed is damped")
        assertEquals(UNCHANGED, h.sync.sync(WATCH_ASKED, have = h.latestEcho, from = a.id))
        assertEquals(1, nodes.sends.size)
    }

    /**
     * A watch switched to between two asks: the next ask reads it, and it is sent the feed until its
     * own echo says it holds it. One switched to while an ask builds its feed is the known limit of
     * [aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk].
     */
    @Test
    fun aSwitchedWatchNeverInheritsAnotherWatchsConfirmation() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a))
        val h = over(nodes)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.latestEcho, from = a.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        nodes.steady = listOf(b) // the user put on the other watch
        assertEquals(SENT, h.sync.sync(WATCH_APPEARED), "watch B is not known to hold the feed")
        assertEquals(b.id, nodes.sends.last().nodeId)
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "nor after another ask, until B says so")
        h.sync.acknowledge(h.latestEcho, from = b.id)
        assertEquals(b.id, h.memory.record?.confirmedFor)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        nodes.steady = listOf(a) // and back: A's own word was taken in before, for an older number
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(a.id, nodes.sends.last().nodeId)
    }

    /**
     * Ruling 52 item 9 names the watch by the node its echo came from, and counts its confirmation
     * only while that node is nearby. Every echo is taken in, one from a watch not nearby when it
     * is served among them (an acknowledgement that went through the cloud, a handshake from a
     * watch gone since): it is that watch's own word on what it holds. So a wipe heard from afar is
     * never lost, and a confirmation from afar sends once more, never answers `UNCHANGED` for the
     * watch that is nearby.
     */
    @Test
    fun anEchoFromAWatchNotNearbyCountsOnlyWhileItIsNearby() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a, cloudOnly))
        val h = over(nodes)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.latestEcho, from = a.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        // C, reached only through the cloud, says it holds nothing: the confirmation is forgotten.
        assertEquals(SENT, h.sync.sync(WATCH_ASKED, have = "", from = cloudOnly.id))
        assertEquals(a.id, nodes.sends.last().nodeId, "sent to the nearby watch alone")
        assertNull(h.memory.record?.confirmedSeq)
        h.sync.acknowledge(h.latestEcho, from = a.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        // C's word on the latest delivery counts for C, which is not nearby: A is sent it again.
        h.sync.acknowledge(h.latestEcho, from = cloudOnly.id)
        assertEquals(cloudOnly.id, h.memory.record?.confirmedFor)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(3, nodes.sends.size)
        assertTrue(nodes.sends.all { it.nodeId == a.id }, "never to the watch reached through the cloud")

        // Once C is nearby, its own confirmation counts.
        nodes.steady = listOf(a, WatchNode(cloudOnly.id, nearby = true))
        h.sync.acknowledge(h.latestEcho, from = cloudOnly.id)
        assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED))
    }

    @Test
    fun aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a))
        val h = over(nodes)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.latestEcho, from = a.id)

        nodes.steady = listOf(a, b)
        // Plan 2 call 1's known limit: the phone has not heard B yet, and A still holds the feed.
        assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED))
        // B's own handshake, on hearing the phone come back nearby, says it holds nothing.
        assertEquals(SENT, h.sync.sync(WATCH_ASKED, have = "", from = b.id))
        assertEquals(setOf(a.id, b.id), nodes.sends.drop(1).map { it.nodeId }.toSet(), "sent to both")
        val echo = h.latestEcho
        h.sync.acknowledge(echo, from = a.id)
        h.sync.acknowledge(echo, from = b.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(3, nodes.sends.size)
    }

    /** The delivery numbers of the messages [nodes] was asked to send, each with the node it went to. */
    private fun numbersSent(nodes: FakeWatchNodes): List<Pair<String, Long?>> =
        nodes.sends.map { it.nodeId to WatchLinkContract.deliveryIn(it.path)?.seq }

    /**
     * Ruling 72 item 4: P5.12 Step 3's odd deliveries, replayed from its logs (`step-3/logs.txt` and
     * the phone's logcat), with the node table it found: `emulator-5558` nearby, and Step 1's ghost
     * watch `5a3042ca` reached only through the cloud.
     * - A fresh phone, numbers from 1. The Wear app installed: its capability change sends 1
     *   (`WATCH_APPEARED SENT`). Its start's handshake, sent before 1 landed, says the watch holds
     *   nothing, so the phone forgets, which sets 2 aside (amendment A2, Ruling 52 item 2), and sends
     *   3 (`WATCH_ASKED SENT`). The watch accepts 1, then 3, and acknowledges both.
     * - The Wear app reinstalled, its files gone. Its capability change is `UNCHANGED`: the phone
     *   holds 3 confirmed, and only the watch can say it was wiped. Its start's and its opening's
     *   handshakes, sent together before anything landed, both say it holds nothing: 4 and 6 are set
     *   aside, and 5 and 7 sent.
     * So the even numbers were never sent to any watch, and the ghost was sent nothing at all.
     */
    @Test
    fun stepThreesOddDeliveriesAreTheNumbersAnEmptyWatchsHandshakesSetAside() = runTest {
        val watch = WatchNode("c76e057a", nearby = true)
        val ghost = WatchNode("5a3042ca", nearby = false)
        val nodes = FakeWatchNodes(steady = listOf(ghost, watch))
        val h = over(nodes)
        val holdsNothing = WatchLinkContract.echoOf(null)

        assertEquals(SENT, h.sync.sync(WATCH_APPEARED))
        val one = h.latestEcho
        assertEquals(SENT, h.sync.sync(WATCH_ASKED, have = holdsNothing, from = watch.id))
        val three = h.latestEcho
        h.sync.acknowledge(one, from = watch.id)
        h.sync.acknowledge(three, from = watch.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED), "the reinstalled watch: only its handshake says it was wiped")
        assertEquals(SENT, h.sync.sync(WATCH_ASKED, have = holdsNothing, from = watch.id), "ASK STARTED")
        val five = h.latestEcho
        assertEquals(SENT, h.sync.sync(WATCH_ASKED, have = holdsNothing, from = watch.id), "ASK OPENED, sent with it")
        val seven = h.latestEcho
        h.sync.acknowledge(five, from = watch.id)
        h.sync.acknowledge(seven, from = watch.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        assertEquals(listOf(1L, 3L, 5L, 7L).map { watch.id to it }, numbersSent(nodes), "the watch accepted these, and nothing else was sent")
        assertEquals(7L, h.memory.record?.lastSeq, "2, 4 and 6 set aside")
    }

    /**
     * Ruling 72 item 4, two watches on one phone: a send goes to every nearby watch under one
     * number, so the numbers never split between them. One of them reinstalled says it holds
     * nothing: a number is set aside, and both are sent the same feed under the next, the other
     * taking it as its own feed one number on. Both end on the phone's feed.
     */
    @Test
    fun twoWatchesOnOnePhoneTakeEveryNumberTogether() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a, b))
        val h = over(nodes)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.latestEcho, from = a.id)
        h.sync.acknowledge(h.latestEcho, from = b.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        assertEquals(SENT, h.sync.sync(WATCH_ASKED, have = WatchLinkContract.echoOf(null), from = b.id), "B reinstalled")
        assertEquals(listOf(a.id to 1L, b.id to 1L, a.id to 3L, b.id to 3L), numbersSent(nodes))
        assertEquals(1, nodes.sends.map { WatchLinkContract.deliveryIn(it.path)?.fingerprint }.toSet().size, "the same feed each time")
        h.sync.acknowledge(h.latestEcho, from = a.id)
        h.sync.acknowledge(h.latestEcho, from = b.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(4, nodes.sends.size)
    }

    /**
     * The known limit of one reading per ask (Ruling 54 item 4, its review's round 1): an ask judges
     * `UNCHANGED` by the watches it read before the build, so a watch switched to while the feed is
     * being built is answered for the watch read, and sent nothing at that ask. What tells the phone
     * of the switch comes after that reading, so it is queued behind the ask and served after it, on
     * a reading of its own: the new watch's capability change (`WATCH_APPEARED`) or its handshake on
     * finding the phone nearby. The capability change sends it the feed once the old watch has left,
     * as here. Otherwise the old watch's confirmation still counts there, so that ask is damped too,
     * and the handshake does (Ruling 55 item 1), as
     * [aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake] shows. The handshake here echoes
     * nothing; [aWatchSwitchedToDuringTheBuildHoldingAnOlderDeliveryIsSentTheFeedAtItsHandshake] is
     * the stronger case.
     */
    @Test
    fun aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk() = runTest {
        val heals: List<Pair<String, suspend (WatchFeedSync) -> Unit>> = listOf(
            "B's capability change" to { sync -> takeCapabilityChange(sync, WatchLinkContract.WATCH_CAPABILITY, listOf(b)) },
            "B's handshake" to { sync -> takeWatchMessage(sync, WatchLinkContract.REFRESH_PATH, "".encodeToByteArray(), from = b.id) },
        )
        for ((heal, tell) in heals) {
            val nodes = FakeWatchNodes(steady = listOf(a))
            val h = over(nodes)
            assertEquals(SENT, h.sync.sync(RESCHEDULED))
            h.sync.acknowledge(h.latestEcho, from = a.id)
            val asked = nodes.watchesAsked

            var told: Job? = null
            h.duringBuild = {
                h.duringBuild = {}
                nodes.steady = listOf(b) // the user puts on the other watch while the feed is built
                // The phone hears of it after the ask's reading: its ask is queued behind this one.
                told = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { tell(h.sync) }
            }
            assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "$heal: judged by the watches read before the build, A")
            checkNotNull(told) { heal }.join()

            assertEquals(listOf(SENT, UNCHANGED, SENT), h.reports.map { it.first }, "$heal: served after the ask, and sent")
            assertEquals(listOf(a.id, b.id), nodes.sends.map { it.nodeId }, "$heal: B is sent nothing until its own ask")
            assertEquals(3, nodes.watchesAsked - asked, "$heal: the switched ask's one reading, then B's ask's and its send's")
        }
    }

    /**
     * The stronger handshake case (Ruling 55 item 1): B was worn before and holds an older delivery
     * of its own, of an older feed, when it is switched to during the build. Its handshake names
     * that delivery: a number of this install's below the latest, not an echo of nothing, which
     * forgets everything whatever the rule. The record takes it in for B, and B is sent the feed,
     * since only the latest delivery echoed counts.
     */
    @Test
    fun aWatchSwitchedToDuringTheBuildHoldingAnOlderDeliveryIsSentTheFeedAtItsHandshake() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(b))
        val h = over(nodes)
        h.feed = syncFeed(fajrShiftMinutes = 1) // the feed B was worn with
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.latestEcho, from = b.id)
        val bHolds = h.latestEcho // number 1, of the older feed

        nodes.steady = listOf(a) // the user puts on A, and the feed changes
        h.feed = x
        assertEquals(SENT, h.sync.sync(WATCH_APPEARED))
        h.sync.acknowledge(h.latestEcho, from = a.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "A holds the latest")
        val asked = nodes.watchesAsked

        var told: Job? = null
        h.duringBuild = {
            h.duringBuild = {}
            nodes.steady = listOf(b) // back to B while the feed is built
            told = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                takeWatchMessage(h.sync, WatchLinkContract.REFRESH_PATH, bHolds.encodeToByteArray(), from = b.id)
            }
        }
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "judged by the watches read before the build, A")
        checkNotNull(told).join()

        assertEquals(listOf(SENT, SENT, UNCHANGED, UNCHANGED, SENT), h.reports.map { it.first }, "B's handshake is sent the feed")
        assertEquals(listOf(b.id, a.id, b.id), nodes.sends.map { it.nodeId })
        assertEquals(3, nodes.watchesAsked - asked, "the switched ask's one reading, then B's ask's and its send's")
        val record = checkNotNull(h.memory.record)
        assertEquals(b.id, record.confirmedFor, "B's word was taken in for B")
        assertEquals(1L, record.confirmedSeq, "for the older number it named, below the latest")
        assertEquals(3L, record.lastSeq)
        assertEquals(watchFeedFingerprint(x), record.lastStandsFor, "the latest feed went under the next number")

        h.sync.acknowledge(h.latestEcho, from = b.id)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "B holds it now")
        assertEquals(3, nodes.sends.size)
    }

    // ── The listener: each message for the watch it came from ──

    @Test
    fun theListenerServesEachMessageForTheWatchItCameFrom() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(a, b))
        val h = over(nodes)

        takeWatchMessage(h.sync, WatchLinkContract.REFRESH_PATH, "".encodeToByteArray(), from = b.id)
        assertEquals(listOf(SENT), h.reports.map { it.first }, "B's handshake of nothing: the feed is sent")
        assertEquals(2, nodes.sends.size)

        takeWatchMessage(h.sync, WatchLinkContract.ACK_PATH, h.latestEcho.encodeToByteArray(), from = b.id)
        assertEquals(b.id, h.memory.record?.confirmedFor, "the acknowledgement counts for the node it came from")
        assertEquals(h.memory.record?.lastSeq, h.memory.record?.confirmedSeq)

        takeWatchMessage(h.sync, WatchLinkContract.REFRESH_PATH, h.latestEcho.encodeToByteArray(), from = a.id)
        assertEquals(listOf(SENT, UNCHANGED), h.reports.map { it.first })
        assertEquals(a.id, h.memory.record?.confirmedFor)

        // Paths beside the two, and anything else, are no message of ours, whatever reaches the service.
        for (path in listOf("/taqwa/refreshed", "/taqwa/acknowledged", WatchLinkContract.feedPath(delivery), "/other")) {
            takeWatchMessage(h.sync, path, "".encodeToByteArray(), from = a.id)
        }
        assertEquals(2, h.reports.size)
        assertEquals(a.id, h.memory.record?.confirmedFor)

        takeCapabilityChange(h.sync, WatchLinkContract.WATCH_CAPABILITY, listOf(a, cloudOnly))
        assertEquals(listOf(SENT, UNCHANGED, UNCHANGED), h.reports.map { it.first }, "a watch back nearby: damped")
        takeCapabilityChange(h.sync, WatchLinkContract.WATCH_CAPABILITY, listOf(cloudOnly))
        takeCapabilityChange(h.sync, WatchLinkContract.WATCH_CAPABILITY, emptyList())
        takeCapabilityChange(h.sync, WatchLinkContract.PHONE_CAPABILITY, listOf(a))
        assertEquals(3, h.reports.size, "no nearby watch, or another capability: nothing asked")
    }
}
