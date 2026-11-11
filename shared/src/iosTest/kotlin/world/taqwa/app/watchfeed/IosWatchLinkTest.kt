package world.taqwa.app.watchfeed

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSData
import platform.Foundation.NSKeyedArchiver
import platform.Foundation.NSLocale
import platform.Foundation.NSPropertyListBinaryFormat_v1_0
import platform.Foundation.NSPropertyListSerialization
import platform.posix.memcpy
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.IosPlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watchfeed.WatchSendResult.FAILED
import world.taqwa.app.watchfeed.WatchSendResult.NO_FEED
import world.taqwa.app.watchfeed.WatchSendResult.NO_WATCH
import world.taqwa.app.watchfeed.WatchSendResult.SENT
import world.taqwa.app.watchfeed.WatchSendResult.TOO_LARGE
import world.taqwa.app.watchfeed.WatchSendResult.UNCHANGED
import world.taqwa.app.watchfeed.WatchSyncReason.PHRASES_CHANGED
import world.taqwa.app.watchfeed.WatchSyncReason.RESCHEDULED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_APPEARED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_ASKED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_CHANGED
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The iPhone's link (plan 2 Task 8, to Ruling 50): [IosWatchLink] over a fake [WatchSessionHook],
 * which plays what the Swift `TaqwaWatchSession` answers, and [WatchBridgeCalls], the bridge's
 * three entry points, over a real [WatchFeedSync].
 *
 * The tests that hold a sync run it on `Dispatchers.Default` in a scope of their own, and wait with
 * `runBlocking` for the bridge's answer, which comes from a coroutine of the bridge's own scope,
 * never on the caller's thread (`refreshRequested` answers WatchConnectivity's reply handler
 * asynchronously, Ruling 52 item 6); a virtual-time scheduler would let its bound pass at once.
 * They assert order and outcome only, never an instant: a real-time bound appears only as a guard
 * far beyond any answer. The hook answers at once, so no budget is ever reached.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
class IosWatchLinkTest {

    // ── The link alone ──

    @Test
    fun withoutASessionThereIsNoWatch() = runTest {
        val link = IosWatchLink(hook = { null })
        assertFalse(link.reachable())
        assertEquals(null, link.watchKey())
        assertEquals(NO_WATCH, link.send(WatchFeedJson.encode(syncFeed()), delivery(1)))
    }

    @Test
    fun aSessionNotReadyIsNoWatch() = runTest {
        val hook = FakeHook(ready = false)
        val link = IosWatchLink(hook = { hook })
        assertFalse(link.reachable())
        assertEquals(NO_WATCH, link.send(WatchFeedJson.encode(syncFeed()), delivery(1)))
        assertTrue(hook.sent.isEmpty(), "nothing handed over")
    }

    @Test
    fun aReadySessionIsReachable() = runTest {
        val hook = FakeHook(ready = true, key = "k0")
        val link = IosWatchLink(hook = { hook })
        assertTrue(link.reachable())
        assertEquals("k0", link.watchKey(), "the key of the watch the session now sends to")
    }

    @Test
    fun theFeedTravelsAsUtf8Data() = runTest {
        val hook = FakeHook()
        val link = IosWatchLink(hook = { hook })
        val json = WatchFeedJson.encode(arabicFeed())
        val utf8 = json.encodeToByteArray()
        assertTrue(utf8.size > json.length, "the feed holds non-ASCII text")

        assertEquals(SENT, link.send(json, delivery(7)))
        val (bytes, text) = hook.sent.single()
        assertContentEquals(utf8, bytes, "the JSON's UTF-8 bytes, as they are")
        assertEquals(delivery(7).text, text, "the delivery's text beside them")
        assertContentEquals(utf8, feedData(json).toByteArray())
    }

    @Test
    fun aRefusedContextIsFailed() = runTest {
        val hook = FakeHook(answer = WatchFeedBridge.sendFailed)
        val link = IosWatchLink(hook = { hook })
        assertEquals(FAILED, link.send(WatchFeedJson.encode(syncFeed()), delivery(1)))
        hook.answer = 7 // no answer the bridge gives
        assertEquals(FAILED, link.send(WatchFeedJson.encode(syncFeed()), delivery(2)))
    }

    @Test
    fun aContextTooLargeIsTooLarge() {
        runTest {
            val hook = FakeHook(answer = WatchFeedBridge.sendTooLarge)
            assertEquals(TOO_LARGE, IosWatchLink(hook = { hook }).send(WatchFeedJson.encode(syncFeed()), delivery(1)))
        }

        val withPhrases = syncFeed(phrases = (1..3).map(::syncPhrase))
        val hook = FakeHook()
        hook.answers += WatchFeedBridge.sendTooLarge
        hook.answers += WatchFeedBridge.sendQueued
        onRealSync(hook, feed = { withPhrases }) { sync, _, _ ->
            assertEquals(SENT, sync.sync(PHRASES_CHANGED))
        }
        assertEquals(2, hook.sent.size)
        val second = assertNotNull(WatchFeedJson.decode(hook.sent[1].first.decodeToString()))
        assertEquals(withPhrases.days, second.days, "the same days")
        assertEquals(emptyList(), second.tasbeeh, "and no phrases")
        assertEquals(WatchDelivery(TEST_ORIGIN, 2, fp(withPhrases.copy(tasbeeh = emptyList()))).text, hook.sent[1].second, "under the next number")
    }

    @Test
    fun aThrowingSessionIsAFailure() = runTest {
        val hook = FakeHook(throws = IllegalStateException("the session is gone"))
        assertEquals(FAILED, IosWatchLink(hook = { hook }).send(WatchFeedJson.encode(syncFeed()), delivery(1)))
        val notReady = FakeHook(readyThrows = IllegalStateException("no session"))
        assertEquals(FAILED, IosWatchLink(hook = { notReady }).send(WatchFeedJson.encode(syncFeed()), delivery(1)))
    }

    @Test
    fun theIphoneRemembersWhatItSent() {
        assertFalse(IosWatchLink(hook = { FakeHook() }).confirmsDelivery)
        assertFalse(createWatchLink().confirmsDelivery)
        assertTrue(createWatchLink() is IosWatchLink, "the app's link is the WatchConnectivity one")
    }

    // ── Through the sync (Ruling 50) ──

    @Test
    fun aHandshakeWithTheCurrentFeedIsAnsweredWithoutASend() {
        val hook = FakeHook()
        onRealSync(hook) { sync, calls, asks ->
            assertEquals(SENT, sync.sync(RESCHEDULED))
            val delivered = hook.sent.single().second
            assertTrue(calls.handshake(have = delivered), "the watch has the current feed")
            assertEquals(1, hook.sent.size, "the watch echoed the latest delivery: nothing sent")
            assertEquals(WATCH_ASKED to UNCHANGED, asks.last())
        }
    }

    @Test
    fun aHandshakeWithNothingIsSentTheFeed() {
        val hook = FakeHook()
        onRealSync(hook) { sync, calls, asks ->
            assertEquals(SENT, sync.sync(RESCHEDULED))
            assertTrue(calls.handshake(have = ""), "the watch is being given the current feed")
            assertEquals(2, hook.sent.size, "a watch that holds nothing is sent it again")
            assertEquals(WATCH_ASKED to SENT, asks.last())
        }
    }

    @Test
    fun theLaunchIsDampedAndTheWatchStateChangeIsNot() {
        val hook = FakeHook()
        onRealSync(hook) { sync, calls, asks ->
            calls.watchActivated()
            calls.watchActivated()
            assertTrue(sync.settle())
            assertEquals(listOf(WATCH_APPEARED to SENT, WATCH_APPEARED to UNCHANGED), asks.toList())
            assertEquals(1, hook.sent.size, "the same feed is not handed over again at a launch")

            calls.watchStateChanged()
            assertTrue(sync.settle())
            assertEquals(WATCH_CHANGED to SENT, asks.last(), "a watch-state change forgets first")
            assertEquals(2, hook.sent.size)
        }
    }

    @Test
    fun aConfirmationCountsOnlyForTheWatchItWentTo() {
        val hook = FakeHook(key = "k0")
        onRealSync(hook) { sync, calls, asks ->
            assertEquals(SENT, sync.sync(WATCH_APPEARED))
            assertEquals(UNCHANGED, sync.sync(WATCH_APPEARED))

            hook.key = "k1" // another watch, or the watch app reinstalled
            assertEquals(SENT, sync.sync(WATCH_APPEARED), "watch k1 is not known to hold the feed")
            assertEquals(2, hook.sent.size)

            // A handshake from k0, read as it arrived, served once the session's watch is k1: its
            // echo of nothing would forget k1's confirmation, and is ignored.
            hook.keyReads += "k0"
            assertTrue(calls.handshake(have = ""))
            assertEquals(WATCH_ASKED to UNCHANGED, asks.last())
            assertEquals(2, hook.sent.size, "k1 still holds the feed")
        }
    }

    @Test
    fun aSessionThatWentInactiveIsRefusedNotSent() {
        val hook = FakeHook(answer = WatchFeedBridge.sendFailed)
        onRealSync(hook) { sync, _, _ ->
            assertEquals(FAILED, sync.sync(RESCHEDULED), "the session's own check refused it")
            hook.answer = WatchFeedBridge.sendQueued
            assertEquals(SENT, sync.sync(RESCHEDULED), "nothing was confirmed, so the next ask sends")
            assertEquals(2, hook.sent.size)
            assertEquals(UNCHANGED, sync.sync(RESCHEDULED))
        }
    }

    // ── The bridge's own answers ──

    /**
     * Ruling 52 item 6: the handshake never blocks WatchConnectivity's delegate queue. The call
     * returns while the sync is still building the feed, and the reply comes later, from the
     * bridge's own coroutine.
     */
    @Test
    fun aHandshakeNeverBlocksWatchConnectivitysQueue() {
        val hook = FakeHook()
        val held = CompletableDeferred<WatchFeed?>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val sync = WatchFeedSync(
                feedFor = { held.await() },
                link = IosWatchLink(hook = { hook }),
                memory = FakeWatchFeedMemory(),
                scope = scope,
                budget = 1.minutes,
                callerBound = 1.minutes,
            )
            val calls = WatchBridgeCalls(sync = { sync }, hook = { hook }, scope = scope, time = FakeTime(), bound = 1.minutes)
            val answer = CompletableDeferred<Boolean>()
            calls.refreshRequested(have = "") { answer.complete(it) }
            assertFalse(answer.isCompleted, "the call returned before the sync answered")
            held.complete(syncFeed())
            assertTrue(runBlocking { withTimeout(GUARD) { answer.await() } }, "then the reply: the feed is being given")
            assertEquals(1, hook.sent.size)
        } finally {
            held.complete(null)
            scope.cancel()
        }
    }

    /**
     * Only `SENT` and `UNCHANGED` are true (Ruling 52 item 7): with no watch ready, or no feed to
     * send, the watch is neither holding nor being given the current feed.
     */
    @Test
    fun aHandshakeWithNoWatchOrNoFeedIsFalse() {
        val notReady = FakeHook(ready = false)
        onRealSync(notReady) { _, calls, asks ->
            assertFalse(calls.handshake(have = ""), "no watch ready")
            assertEquals(WATCH_ASKED to NO_WATCH, asks.last())
        }
        val hook = FakeHook()
        onRealSync(hook, feed = { null }) { _, calls, asks ->
            assertFalse(calls.handshake(have = ""), "no feed")
            assertEquals(WATCH_ASKED to NO_FEED, asks.last())
            assertTrue(hook.sent.isEmpty())
        }
    }

    /**
     * A sync whose worker has ended refuses the ask at once, so the bridge answers false at once.
     * The test waits for the worker to end first (Ruling 52 item 7), and every bound is a minute,
     * so only that refusal can answer within the guard.
     */
    @Test
    fun aHandshakeTheSyncCannotServeIsFalse() {
        val hook = FakeHook()
        val gone = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val ended = WatchFeedSync(
            feedFor = { syncFeed() },
            link = IosWatchLink(hook = { hook }),
            memory = FakeWatchFeedMemory(),
            scope = gone,
            callerBound = 1.minutes,
        )
        runBlocking { gone.coroutineContext.job.cancelAndJoin() }
        val bridge = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val calls = WatchBridgeCalls(sync = { ended }, hook = { hook }, scope = bridge, time = FakeTime(), bound = 1.minutes)
            assertFalse(runBlocking { calls.handshake(have = "", guard = 5.seconds) }, "refused at once, never by a bound")
        } finally {
            bridge.cancel()
        }

        // A worker that does not answer within the bridge's bound: false, and the watch asks again.
        val held = CompletableDeferred<WatchFeed?>()
        val slow = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val stuck = WatchFeedSync(
                feedFor = { held.await() },
                link = IosWatchLink(hook = { hook }),
                memory = FakeWatchFeedMemory(),
                scope = slow,
                budget = 1.minutes,
                callerBound = 1.minutes,
            )
            val calls = WatchBridgeCalls(sync = { stuck }, hook = { hook }, scope = slow, time = FakeTime(), bound = 200.milliseconds)
            assertFalse(runBlocking { calls.handshake(have = "") })
        } finally {
            held.complete(null)
            slow.cancel()
        }
    }

    /**
     * The reply handler is called once, whatever becomes of the handshake: a key that throws as
     * the message arrives, a container that cannot be built, or a bridge scope already gone.
     */
    @Test
    fun aHandshakeIsAnsweredOnceWhateverBecomesOfIt() {
        val gone = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { it.cancel() }
        val live = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val cases = listOf(
                "a key that throws" to WatchBridgeCalls(sync = { error("unused") }, hook = { FakeHook(keyThrows = IllegalStateException("no key")) }, scope = live, time = FakeTime()),
                "no container" to WatchBridgeCalls(sync = { error("no container") }, hook = { FakeHook() }, scope = live, time = FakeTime()),
                "the bridge's scope gone" to WatchBridgeCalls(sync = { error("unused") }, hook = { FakeHook() }, scope = gone, time = FakeTime()),
            )
            for ((case, calls) in cases) {
                val replies = AtomicInt(0)
                val first = CompletableDeferred<Boolean>()
                calls.refreshRequested(have = "") {
                    replies.incrementAndFetch()
                    first.complete(it)
                }
                assertFalse(runBlocking { withTimeout(GUARD) { first.await() } }, case)
                runBlocking { delay(300.milliseconds) } // room for a second reply, which must not come
                assertEquals(1, replies.load(), "$case: one reply")
            }
        } finally {
            live.cancel()
        }
    }

    /**
     * Ruling 53 item 4a: the handshake keeps WatchConnectivity's arrival order. Its ask reaches the
     * sync's queue before `refreshRequested` returns, on the caller's thread, so a watch-state
     * change that WatchConnectivity delivers after it is served after it. The bridge's scope here
     * runs only when the test says so, so what the queue holds is what the calls themselves put
     * there; a launch dispatched to that scope would queue both handshakes behind the change.
     */
    @Test
    fun aHandshakeReachesTheSyncInWatchConnectivitysOrder() {
        val hook = FakeHook()
        val scheduler = TestCoroutineScheduler()
        val bridge = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
        val work = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val reasons = mutableListOf<WatchSyncReason>()
        try {
            val sync = WatchFeedSync(
                feedFor = { syncFeed() },
                link = IosWatchLink(hook = { hook }),
                memory = FakeWatchFeedMemory(),
                scope = work,
                report = { reason, _, _ -> reasons += reason },
            )
            val calls = WatchBridgeCalls(sync = { sync }, hook = { hook }, scope = bridge, time = FakeTime())
            val answers = mutableListOf<Boolean>()
            calls.refreshRequested(have = "") { answers += it }
            calls.watchStateChanged()
            calls.refreshRequested(have = "") { answers += it }
            repeat(2) {
                assertTrue(runBlocking { withTimeout(GUARD) { sync.settle() } })
                scheduler.runCurrent()
            }
            assertEquals(listOf(WATCH_ASKED, WATCH_CHANGED, WATCH_ASKED), reasons, "served in the order the calls came")
            assertEquals(listOf(true, true), answers, "each handshake answered")
        } finally {
            bridge.cancel()
            work.cancel()
        }
    }

    /**
     * Ruling 53 item 4b: a handshake can wake the app in the background, so the bridge asks UIKit
     * for background time before anything else, holds it while the sync works, and ends it right
     * after the reply, once.
     */
    @Test
    fun aHandshakeHoldsBackgroundTimeUntilItIsAnswered() {
        val hook = FakeHook()
        val time = FakeTime()
        val held = CompletableDeferred<WatchFeed?>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val sync = WatchFeedSync(
                feedFor = { held.await() },
                link = IosWatchLink(hook = { hook }),
                memory = FakeWatchFeedMemory(),
                scope = scope,
                budget = 1.minutes,
                callerBound = 1.minutes,
            )
            val calls = WatchBridgeCalls(sync = { sync }, hook = { hook }, scope = scope, time = time, bound = 1.minutes)
            calls.refreshRequested(have = "") { time.note("reply $it") }
            assertEquals(listOf("begin 1"), time.events, "asked for as the message arrives, and held while the sync works")
            held.complete(syncFeed())
            assertEquals(1uL, runBlocking { withTimeout(GUARD) { time.ended.await() } })
            runBlocking { delay(300.milliseconds) } // room for a second end, which must not come
            assertEquals(listOf("begin 1", "reply true", "end 1"), time.events)
            assertEquals(1, hook.sent.size)
        } finally {
            held.complete(null)
            scope.cancel()
        }
    }

    /**
     * The background time ends right after the reply, whatever becomes of the handshake: its bound
     * passed, a key that throws as the message arrives, a container that cannot be built, the
     * bridge's scope gone, or the sync's worker gone. Without time given (UIKit's
     * `UIBackgroundTaskInvalid`) the handshake is still answered, and nothing is ended.
     */
    @Test
    fun theBackgroundTimeEndsRightAfterTheReplyWhateverBecomesOfTheHandshake() {
        val gone = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { it.cancel() }
        val live = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val held = CompletableDeferred<WatchFeed?>()
        try {
            val stuck = WatchFeedSync(
                feedFor = { held.await() },
                link = IosWatchLink(hook = { FakeHook() }),
                memory = FakeWatchFeedMemory(),
                scope = live,
                budget = 1.minutes,
                callerBound = 1.minutes,
            )
            val ended = WatchFeedSync(
                feedFor = { syncFeed() },
                link = IosWatchLink(hook = { FakeHook() }),
                memory = FakeWatchFeedMemory(),
                scope = gone,
                callerBound = 1.minutes,
            )
            runBlocking { gone.coroutineContext.job.cancelAndJoin() } // its worker has ended, its queue closed
            val cases: List<Pair<String, (HandshakeTime) -> WatchBridgeCalls>> = listOf(
                "its bound passed" to { time -> WatchBridgeCalls(sync = { stuck }, hook = { FakeHook() }, scope = live, time = time, bound = 200.milliseconds) },
                "a key that throws" to { time -> WatchBridgeCalls(sync = { error("unused") }, hook = { FakeHook(keyThrows = IllegalStateException("no key")) }, scope = live, time = time) },
                "no container" to { time -> WatchBridgeCalls(sync = { error("no container") }, hook = { FakeHook() }, scope = live, time = time) },
                "the bridge's scope gone" to { time -> WatchBridgeCalls(sync = { error("unused") }, hook = { FakeHook() }, scope = gone, time = time) },
                "the sync's worker gone" to { time -> WatchBridgeCalls(sync = { ended }, hook = { FakeHook() }, scope = live, time = time, bound = 1.minutes) },
            )
            for ((case, calls) in cases) {
                val time = FakeTime()
                calls(time).refreshRequested(have = "") { time.note("reply $it") }
                assertEquals(1uL, runBlocking { withTimeout(GUARD) { time.ended.await() } }, case)
                runBlocking { delay(300.milliseconds) } // room for a second reply or end, which must not come
                assertEquals(listOf("begin 1", "reply false", "end 1"), time.events, case)
            }

            val none = FakeTime(gives = false)
            val hook = FakeHook()
            val sync = WatchFeedSync(feedFor = { syncFeed() }, link = IosWatchLink(hook = { hook }), memory = FakeWatchFeedMemory(), scope = live)
            val answer = CompletableDeferred<Boolean>()
            WatchBridgeCalls(sync = { sync }, hook = { hook }, scope = live, time = none).refreshRequested(have = "") {
                none.note("reply $it")
                answer.complete(it)
            }
            assertTrue(runBlocking { withTimeout(GUARD) { answer.await() } }, "no background time: still answered")
            runBlocking { delay(300.milliseconds) }
            assertEquals(listOf("begin refused", "reply true"), none.events, "and nothing to end")
        } finally {
            held.complete(null)
            live.cancel()
        }
    }

    /**
     * When the system takes the time back first, its expiration handler answers the watch false
     * and ends the time there and then (Apple: "Failure to end the task explicitly will result in
     * the termination of the app"), even when it comes before `begin` has returned, as it may when
     * the system cannot grant the time. The ask stays queued and is served; the sync's later
     * answer replies nothing more and ends nothing more.
     */
    @Test
    fun theSystemTakingTheTimeBackAnswersAndEndsItThere() {
        for (atOnce in listOf(false, true)) {
            val case = if (atOnce) "taken back before it was given" else "taken back while the sync works"
            val hook = FakeHook()
            val time = FakeTime(expiresAtOnce = atOnce)
            val held = CompletableDeferred<WatchFeed?>()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            try {
                val sync = WatchFeedSync(
                    feedFor = { held.await() },
                    link = IosWatchLink(hook = { hook }),
                    memory = FakeWatchFeedMemory(),
                    scope = scope,
                    budget = 1.minutes,
                    callerBound = 1.minutes,
                )
                val calls = WatchBridgeCalls(sync = { sync }, hook = { hook }, scope = scope, time = time, bound = 1.minutes)
                calls.refreshRequested(have = "") { time.note("reply $it") }
                if (!atOnce) time.expire()
                assertEquals(listOf("begin 1", "reply false", "end 1"), time.events, "$case: answered and ended at once")
                held.complete(syncFeed())
                assertTrue(runBlocking { withTimeout(GUARD) { sync.settle() } })
                assertEquals(1, hook.sent.size, "$case: the ask was still served")
                runBlocking { delay(300.milliseconds) }
                assertEquals(listOf("begin 1", "reply false", "end 1"), time.events, "$case: nothing more")
            } finally {
                held.complete(null)
                scope.cancel()
            }
        }
    }

    @Test
    fun aBridgeCallNeverThrows() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val calls = WatchBridgeCalls(sync = { error("no container") }, hook = { FakeHook(keyThrows = IllegalStateException("no key")) }, scope = scope, time = FakeTime())
            assertFalse(runBlocking { calls.handshake(have = "") })
            calls.watchActivated()
            calls.watchStateChanged()
        } finally {
            scope.cancel()
        }
    }

    /** Ruling 52 item 4: the Swift session logs a delivery by its number and fingerprint alone. */
    @Test
    fun theBridgeLogsADeliveryWithoutItsOrigin() {
        val text = WatchDelivery(TEST_ORIGIN, 7, "0011223344556677").text
        assertEquals("7/0011223344556677", WatchFeedBridge.logged(delivery = text))
        assertEquals("none", WatchFeedBridge.logged(delivery = ""))
        assertEquals("unreadable", WatchFeedBridge.logged(delivery = TEST_ORIGIN))
    }

    @Test
    fun theContractKeysAreTheWatchcoreOnes() {
        assertEquals(WatchLinkContract.CONTEXT_FEED_KEY, WatchFeedBridge.contextFeedKey)
        assertEquals(WatchLinkContract.CONTEXT_DELIVERY_KEY, WatchFeedBridge.contextDeliveryKey)
        assertEquals(WatchLinkContract.REFRESH_MESSAGE_KEY, WatchFeedBridge.refreshMessageKey)
        assertEquals(WatchLinkContract.REFRESH_REPLY_KEY, WatchFeedBridge.refreshReplyKey)
        assertEquals(listOf(0, 1, 2), listOf(WatchFeedBridge.sendQueued, WatchFeedBridge.sendFailed, WatchFeedBridge.sendTooLarge))
    }

    // ── The size of the context (the Lead's call 4) ──

    @Test
    fun theLargestContextFitsTheSmallestLimit() = runTest {
        val spoken = watchSpoken()
        val cases = listOf(
            Triple("ar_SA", "ar-SA", UiLanguage.ARABIC),
            Triple("bn_BD", "bn-BD", UiLanguage.BENGALI),
        )
        for ((identifier, tag, language) in cases) {
            val format = IosPlatformFormat(ClockFormat.TWELVE_HOUR, NSLocale(localeIdentifier = identifier), tag)
            for (kind in listOf(PhraseKind.CONTROL, PhraseKind.EMOJI)) {
                // The simulator's resources are English, so the worst words stand in for the language's.
                val feed = assertNotNull(
                    WorstFeeds.largestFeed(CAPE_TOWN, format, language, spoken, WorstFeeds.SCREENS, kind, NOW, frozenTimes),
                    "$tag $kind",
                )
                assertEquals(31, feed.days.size, "$tag $kind")
                val json = WatchFeedJson.encode(feed)
                // The longest delivery there can be: the largest number a Long holds.
                val delivery = WatchDelivery("ffffffffffffffff", Long.MAX_VALUE, watchFeedFingerprint(feed))
                val context = mapOf(
                    WatchFeedBridge.contextFeedKey to feedData(json),
                    WatchFeedBridge.contextDeliveryKey to delivery.text,
                )
                val plist = assertNotNull(
                    NSPropertyListSerialization.dataWithPropertyList(context, NSPropertyListBinaryFormat_v1_0, 0u, null),
                    "$tag $kind: a property list",
                ).length.toInt()
                val archive = assertNotNull(
                    NSKeyedArchiver.archivedDataWithRootObject(context, requiringSecureCoding = false, error = null),
                    "$tag $kind: a keyed archive",
                ).length.toInt()
                val bytes = WatchFeedBuilder.sizeOf(json)
                println(
                    "[measure] iPhone context $tag/12 ${kind.name}: ${feed.tasbeeh.size} phrases, feed $bytes UTF-8 bytes, " +
                        "binary plist $plist bytes, keyed archive $archive bytes, of ${WatchLinkContract.USER_INFO_LIMIT_BYTES}",
                )
                assertTrue(bytes <= WatchLinkContract.FEED_BUDGET_BYTES, "$tag $kind: feed $bytes bytes")
                assertTrue(plist <= WatchLinkContract.USER_INFO_LIMIT_BYTES, "$tag $kind: plist $plist bytes")
                assertTrue(archive <= WatchLinkContract.USER_INFO_LIMIT_BYTES, "$tag $kind: archive $archive bytes")
                assertTrue(plist > bytes, "$tag $kind: the plist holds the feed's bytes whole")
            }
        }
    }

    // ── Helpers ──

    /**
     * What `TaqwaWatchSession` answers: [ready] for `isReady()`, [key] for `watchKey()` (unless
     * [keyReads] still holds one for that read, in order), and each send's answer from [answers] in
     * order, then [answer]. [sent] holds each send's bytes and delivery as the session was given
     * them.
     */
    private class FakeHook(
        var ready: Boolean = true,
        var key: String? = "k0",
        var answer: Int = WatchFeedBridge.sendQueued,
        val throws: Exception? = null,
        val readyThrows: Exception? = null,
        val keyThrows: Exception? = null,
    ) : WatchSessionHook {
        val answers = ArrayDeque<Int>()
        val keyReads = ArrayDeque<String?>()
        val sent = mutableListOf<Pair<ByteArray, String>>()

        override fun isReady(): Boolean {
            readyThrows?.let { throw it }
            return ready
        }

        override fun watchKey(): String? {
            keyThrows?.let { throw it }
            return if (keyReads.isEmpty()) key else keyReads.removeFirst()
        }

        override fun send(feed: NSData, delivery: String): Int {
            sent += feed.toByteArray() to delivery
            throws?.let { throw it }
            return answers.removeFirstOrNull() ?: answer
        }
    }

    /**
     * What UIKit's background time does for the bridge (Ruling 53 item 4b), as one log the test's
     * reply writes into too: `begin <id>` (or `begin refused` when [gives] is false, as UIKit's
     * `UIBackgroundTaskInvalid` is), `reply <queued>` and `end <id>`, in the order they happen.
     * [expire] plays the system taking the time back: each expiration handler, at once. With
     * [expiresAtOnce] the handler runs inside `begin`, before it returns. [ended] completes at the
     * first end.
     */
    private class FakeTime(private val gives: Boolean = true, private val expiresAtOnce: Boolean = false) : HandshakeTime {
        private val log = AtomicReference(emptyList<String>())
        private val handlers = AtomicReference(emptyList<() -> Unit>())
        private val ids = AtomicInt(0)
        val ended = CompletableDeferred<ULong>()

        val events: List<String> get() = log.load()

        fun note(event: String) {
            while (true) {
                val before = log.load()
                if (log.compareAndSet(before, before + event)) return
            }
        }

        fun expire() {
            for (handler in handlers.load()) handler()
        }

        override fun begin(expired: () -> Unit): ULong? {
            if (!gives) {
                note("begin refused")
                return null
            }
            val id = ids.incrementAndFetch()
            note("begin $id")
            while (true) {
                val before = handlers.load()
                if (handlers.compareAndSet(before, before + expired)) break
            }
            if (expiresAtOnce) expired()
            return id.toULong()
        }

        override fun end(task: ULong) {
            note("end $task")
            ended.complete(task)
        }
    }

    /**
     * A sync over an [IosWatchLink] on [hook], on `Dispatchers.Default` in a scope of its own, with
     * its feed from [feed] and its record in memory. [block] gets it, the bridge's calls over it,
     * and what each ask came to, in order (as `report` gives them), and runs inside `runBlocking`.
     */
    private fun onRealSync(
        hook: FakeHook,
        feed: suspend () -> WatchFeed? = { syncFeed() },
        block: suspend (WatchFeedSync, WatchBridgeCalls, List<Pair<WatchSyncReason, WatchSendResult>>) -> Unit,
    ) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val reports = mutableListOf<Pair<WatchSyncReason, WatchSendResult>>()
        try {
            val sync = WatchFeedSync(
                feedFor = { feed() },
                link = IosWatchLink(hook = { hook }),
                memory = FakeWatchFeedMemory(),
                scope = scope,
                report = { reason, result, _ -> reports += reason to result },
            )
            runBlocking { block(sync, WatchBridgeCalls(sync = { sync }, hook = { hook }, scope = scope, time = FakeTime()), reports) }
        } finally {
            scope.cancel()
        }
    }

    private fun delivery(seq: Long): WatchDelivery = WatchDelivery(TEST_ORIGIN, seq, "0011223344556677")

    /**
     * The watch's handshake as `TaqwaWatchSession` hands it over: the reply handler, awaited here
     * for at most [guard], a real-time guard far beyond any answer.
     */
    private suspend fun WatchBridgeCalls.handshake(have: String, guard: Duration = GUARD): Boolean {
        val answer = CompletableDeferred<Boolean>()
        refreshRequested(have) { answer.complete(it) }
        return withTimeout(guard) { answer.await() }
    }

    /** The sync's small feed in Arabic: its names, its direction and the user's phrases. */
    private fun arabicFeed(): WatchFeed {
        val feed = syncFeed(phrases = (1..3).map(::syncPhrase))
        return feed.copy(
            days = feed.days.map { it.copy(names = WatchSix("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")) },
            ui = feed.ui.copy(language = "ar", rtl = true),
        )
    }

    private companion object {
        /** How long a test waits for an answer that comes at once: never reached. */
        val GUARD = 30.seconds

        /** Sunday 18 October 2026, 08:00 in Cape Town: the samples' `writtenAt`. */
        val NOW: Instant = Instant.fromEpochSeconds(1_792_303_200L)
        val CAPE_TOWN = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", cityName = "Cape Town", cityId = 3369157)
        private val ZONE = TimeZone.of("Africa/Johannesburg")

        /**
         * The mockups' day on every date, frozen at the place's own midnight (the builder test's),
         * so the size is the rules' and never the engine's of the day.
         */
        val frozenTimes: (LocalDate) -> DayPrayerTimes = { date ->
            val midnight = date.atStartOfDayIn(ZONE)
            fun at(hour: Int, minute: Int): Instant = midnight + (hour * 60 + minute).minutes
            DayPrayerTimes(
                date = date,
                times = listOf(
                    PrayerTime(Prayer.FAJR, at(4, 33)),
                    PrayerTime(Prayer.SUNRISE, at(6, 1)),
                    PrayerTime(Prayer.DHUHR, at(12, 33)),
                    PrayerTime(Prayer.ASR, at(16, 10)),
                    PrayerTime(Prayer.MAGHRIB, at(19, 4)),
                    PrayerTime(Prayer.ISHA, at(20, 24)),
                ),
                highLatitudeRuleApplied = null,
                asrOther = at(17, 0),
                sunset = at(19, 2),
                endOfEating = at(4, 33),
                ends = mapOf(Prayer.FAJR to at(6, 1), Prayer.ASR to at(19, 2)),
                sourceEntryId = "test",
                entryClass = EntryClass.D_NONE,
            )
        }
    }
}

/** The bytes an `NSData` holds, as the watch would read them. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
