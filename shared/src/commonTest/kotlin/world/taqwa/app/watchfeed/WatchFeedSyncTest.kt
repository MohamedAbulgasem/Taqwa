package world.taqwa.app.watchfeed

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watchfeed.WatchAskStep.BUILD
import world.taqwa.app.watchfeed.WatchAskStep.ECHO
import world.taqwa.app.watchfeed.WatchAskStep.ENCODE
import world.taqwa.app.watchfeed.WatchAskStep.FORGET
import world.taqwa.app.watchfeed.WatchAskStep.RECORD
import world.taqwa.app.watchfeed.WatchAskStep.SEND
import world.taqwa.app.watchfeed.WatchAskStep.WATCHES
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
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal fun fp(feed: WatchFeed): String = watchFeedFingerprint(feed)

/** A sync over [link] and a record in memory, whose feed is [feed] unless [feedFor] says otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SyncHarness(
    test: TestScope,
    val link: FakeWatchLink,
    val memory: FakeWatchFeedMemory = FakeWatchFeedMemory(),
    scope: CoroutineScope = test.backgroundScope,
    work: CoroutineDispatcher = StandardTestDispatcher(test.testScheduler),
    feedFor: (suspend (GeoLocation?) -> WatchFeed?)? = null,
    onReport: (Reported) -> Unit = {},
) {
    class Reported(val reason: WatchSyncReason, val result: WatchSendResult, val at: Long)

    var feed: WatchFeed? = syncFeed()
    val reports = mutableListOf<Reported>()

    /** Each ask's steps, timed on the test's virtual clock (Ruling 70 item 1). */
    val times = mutableListOf<AskTimes>()
    var builds = 0
    val sync = WatchFeedSync(
        feedFor = { location ->
            builds++
            if (feedFor != null) feedFor(location) else feed
        },
        link = link,
        memory = memory,
        scope = scope,
        work = work,
        report = { reason, result, _ ->
            val reported = Reported(reason, result, test.testScheduler.currentTime)
            reports += reported
            onReport(reported)
        },
        clock = test.testScheduler.timeSource,
        timed = { times += it },
    )

    /** Whether the record says the session's watch has confirmed [f] (item 3). */
    fun knows(f: WatchFeed): Boolean = memory.record?.holds(fp(f), link.key) == true

    /** The watch's handshake, echoing [echo], from the session's watch. */
    suspend fun handshake(echo: String?): WatchSendResult = sync.sync(WATCH_ASKED, have = echo, from = link.key)

    /** [x] sent, and the watch known to hold it: its acknowledgement where the link confirms. */
    suspend fun remembered(x: WatchFeed) {
        feed = x
        assertEquals(SENT, sync.sync(RESCHEDULED))
        if (link.confirmsDelivery) sync.acknowledge(link.sent.last().echo)
        assertTrue(knows(x), "the record confirms $x")
    }

    val fingerprints: List<String> get() = link.sent.map { it.fingerprint }
}

/**
 * When the phone sends its watch a feed, and what it records of it (plan 2 Task 4, the Lead's call
 * 1, Ruling 50), on virtual time over fakes alone: the sync's scope is `backgroundScope`, its worker
 * runs on a `StandardTestDispatcher` of the test's own scheduler, and every "at N s" is that
 * scheduler's `currentTime` as the `report` callback reads it.
 *
 * Two kinds of link: one that confirms delivery, as Android's does (a send is only queued, so the
 * phone counts a feed held only once the watch echoes its delivery, in its acknowledgement or its
 * handshake), and one that does not, as the iPhone's application context (the system delivers the
 * latest context it was handed, so a send that got through confirms its number). The named
 * counterexamples of Ruling 48 and the design checks are in `WatchFeedNamedCasesTest`, and the
 * randomized model in `WatchFeedDeliveryModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchFeedSyncTest {

    private val x = syncFeed()

    /** X with one Fajr a minute later, as the Manual adjustments stepper's + makes it. */
    private val y = syncFeed(fajrShiftMinutes = 1)

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val tripoli = GeoLocation(32.8872, 13.1913, "Africa/Tripoli", "Tripoli", "LY")

    @Test
    fun anUnchangedFeedIsNotSentAgain() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        h.feed = syncFeed(writtenAt = 1_792_303_200L)
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.feed = syncFeed(writtenAt = 1_792_306_800L)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(1, h.link.sent.size)
    }

    @Test
    fun aChangedFeedIsSent() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            h.feed = y
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "confirms $confirms")
            assertEquals(listOf(fp(x), fp(y)), h.fingerprints)
        }
    }

    // ── Item 1: every send carries the origin and the next number, written down first ──

    /**
     * Each send's delivery is the install's origin, the next number and the feed's own fingerprint,
     * and the record names that number before the link is called (item 1), so a send cut off, a
     * throw or a process that dies inside the call can never leave a number to be used again.
     */
    @Test
    fun eachSendIsWrittenDownBeforeTheLinkIsCalled() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            val written = mutableListOf<Long?>()
            h.link.onSend = { written += h.memory.record?.takeIf { r -> r.lastDelivery == it.delivery }?.lastSeq }
            h.link.limitBytes = WatchFeedJson.encode(x).encodeToByteArray().size
            h.feed = syncFeed(phrases = (1..3).map(::syncPhrase)) // over the limit: two sends
            assertEquals(SENT, h.sync.sync(PHRASES_CHANGED), "confirms $confirms")
            h.feed = y
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "confirms $confirms")
            assertEquals(listOf<Long?>(1, 2, 3), written, "confirms $confirms: each number on record at its send")
            assertEquals(List(3) { TEST_ORIGIN }, h.link.sent.map { it.delivery.origin })
        }
    }

    /**
     * A number is never used twice (item 1): not after a refusal, a cut-off, a throw or a feed too
     * large for the link, and not after a restart, whose new sync reads the record.
     */
    @Test
    fun aNumberIsNeverUsedTwice() = runTest {
        for (confirms in listOf(false, true)) {
            val memory = FakeWatchFeedMemory()
            val link = FakeWatchLink(confirms)
            val h = SyncHarness(this, link, memory)
            link.answers += FAILED
            assertEquals(FAILED, h.sync.sync(RESCHEDULED))
            link.answers += NO_WATCH
            assertEquals(NO_WATCH, h.sync.sync(RESCHEDULED))
            link.gate = CompletableDeferred() // never completed
            assertEquals(FAILED, h.sync.sync(RESCHEDULED))
            link.gate = null
            link.sendThrows = IllegalStateException("the link broke")
            assertEquals(FAILED, h.sync.sync(RESCHEDULED))
            link.sendThrows = null
            link.answers += TOO_LARGE
            assertEquals(SENT, h.sync.sync(RESCHEDULED))
            val after = SyncHarness(this, link, memory) // a new process, the record kept
            after.feed = y
            assertEquals(SENT, after.sync.sync(RESCHEDULED))
            assertEquals((1L..7L).toList(), link.sent.map { it.seq }, "confirms $confirms")
        }
    }

    @Test
    fun theDeliveryTravelsWithTheFeed() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        val feeds = listOf(x, y, syncFeed(phrases = listOf(syncPhrase(1))), syncFeed(writtenAt = 1L))
        for (feed in feeds) {
            h.feed = feed
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "nothing is acknowledged, so each is sent")
        }
        assertEquals(feeds.size, h.link.sent.size)
        for ((index, pair) in h.link.sent.zip(feeds).withIndex()) {
            val (sent, feed) = pair
            assertEquals(feed, sent.feed, "the JSON is the feed's")
            assertEquals(WatchDelivery(TEST_ORIGIN, index + 1L, fp(sent.feed)), sent.delivery)
        }
    }

    // ── Item 3: UNCHANGED exactly when the watch has echoed the latest delivery ──

    @Test
    fun aWatchComingBackWithTheFeedItHoldsIsNotSentItAgain() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED), "confirms $confirms")
            assertEquals(1, h.link.sent.size, "the watch holds it, so it is not sent again")
            h.feed = y
            assertEquals(SENT, h.sync.sync(WATCH_APPEARED), "confirms $confirms")
            assertEquals(2, h.link.sent.size)
        }
    }

    /**
     * A phrase change is damped as a reschedule is (spec 1.2's idempotent send; Ruling 48's mutant
     * MC): a phrase added and taken out again leaves the feed the watch holds, so nothing is sent.
     */
    @Test
    fun aPhraseChangeTheWatchAlreadyHoldsIsNotSentAgain() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            assertEquals(UNCHANGED, h.sync.sync(PHRASES_CHANGED), "confirms $confirms")
            assertEquals(1, h.link.sent.size, "the watch holds it, so it is not sent again")
            h.feed = syncFeed(phrases = listOf(syncPhrase(1)))
            assertEquals(SENT, h.sync.sync(PHRASES_CHANGED), "confirms $confirms")
            assertEquals(2, h.link.sent.size)
        }
    }

    /**
     * On either link the watch's handshake echoing the latest delivery is `UNCHANGED` and confirms
     * it, even before Android's acknowledgement comes: the watch named it itself. So the next
     * reschedule sends nothing.
     */
    @Test
    fun theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            assertEquals(SENT, h.sync.sync(RESCHEDULED)) // X, not acknowledged
            assertEquals(UNCHANGED, h.handshake(h.link.sent.single().echo), "confirms $confirms")
            assertTrue(h.knows(x), "confirms $confirms")
            assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "confirms $confirms")
            assertEquals(1, h.link.sent.size)
        }
    }

    @Test
    fun theWatchsHandshakeWithAnOlderFeedIsSent() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(y)
            h.feed = x
            assertEquals(SENT, h.handshake(h.link.sent.single().echo), "confirms $confirms")
            assertEquals(listOf(fp(y), fp(x)), h.fingerprints)
        }
    }

    /**
     * A handshake that names no delivery of this install is always sent the feed: nothing, a bare
     * fingerprint (the form before Ruling 50), something that is not a delivery, another install's.
     */
    @Test
    fun aHandshakeWithoutADeliveryIsSent() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        val notOurs = listOf(null, "", "xyz", fp(x), "$TEST_ORIGIN/1/${fp(x)}/x", "1111111111111111/1/${fp(x)}")
        for (have in notOurs) {
            assertEquals(SENT, h.handshake(have), "have $have")
        }
        assertEquals(notOurs.size, h.link.sent.size)
    }

    @Test
    fun aReinstalledWatchIsHealedByItsOwnHandshake() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.remembered(x)
        // A reinstall and a reconnect look the same to the phone.
        assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED))
        // The reinstalled watch holds nothing, and says so.
        assertEquals(SENT, h.handshake(""))
        assertEquals(2, h.link.sent.size)
        h.sync.acknowledge(h.link.sent.last().echo)
        assertEquals(UNCHANGED, h.sync.sync(WATCH_APPEARED))
        assertEquals(2, h.link.sent.size)
    }

    // ── Item 4: Android confirms only by echo; the iPhone also by a send that got through ──

    @Test
    fun aConfirmingLinkRemembersOnlyWhatTheWatchAcknowledged() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "queued is not delivered")
        assertFalse(h.knows(x))
        h.sync.acknowledge(h.link.sent.first().echo) // the first copy's word: not the latest
        assertFalse(h.knows(x))
        h.sync.acknowledge(h.link.sent.last().echo)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        h.feed = y
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(3, h.link.sent.size)
    }

    /**
     * On the iPhone's link a send that got through confirms its number at once (item 4), and any
     * other answer (a refusal, a throw, one the budget cut off) leaves the confirmation where it was,
     * so the next ask sends again: one more send, never a wrong `UNCHANGED`.
     */
    @Test
    fun onTheIphoneASendThatDidNotAnswerSentConfirmsNothing() = runTest {
        for (how in listOf("refused", "no watch", "thrown", "cut off")) {
            val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
            h.remembered(x)
            h.feed = y
            when (how) {
                "refused" -> h.link.answers += FAILED
                "no watch" -> h.link.answers += NO_WATCH
                "thrown" -> h.link.sendThrows = IllegalStateException("updateApplicationContext threw")
                else -> h.link.gate = CompletableDeferred() // never completed
            }
            assertNotEquals(SENT, h.sync.sync(RESCHEDULED), how)
            h.link.sendThrows = null
            h.link.gate = null
            assertFalse(h.knows(y), how)
            assertFalse(h.knows(x), "$how: the number moved on, so X is not confirmed either")
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "$how: sent again")
            h.feed = x
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "$how: X too, once more")
            assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), how)
        }
    }

    @Test
    fun nothingIsRememberedWhenNoWatchTookIt() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        h.link.answers += NO_WATCH
        assertEquals(NO_WATCH, h.sync.sync(RESCHEDULED))
        assertFalse(h.knows(x))
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertTrue(h.knows(x))
        assertEquals(2, h.link.sent.size)
    }

    @Test
    fun aChangeUndoneBeforeItsAckIsSentAgain() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.remembered(x)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))

        h.feed = y // the stepper's +
        assertEquals(SENT, h.sync.sync(RESCHEDULED))

        h.feed = x // the − undoes it before Y's acknowledgement
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "never UNCHANGED: the watch may hold Y")
        assertEquals(listOf(fp(x), fp(y), fp(x)), h.fingerprints)

        h.sync.acknowledge(h.link.sent.last().echo)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
    }

    /**
     * A late acknowledgement of an older feed costs one more send, never a wrong `UNCHANGED`
     * (Ruling 50's design check): the confirmation is the last echo taken in, lower or higher, so
     * the phone sends the current feed again under a new number, and the watch takes it.
     */
    @Test
    fun aLateAckForAnOlderFeedCostsOneMoreSendNeverAWrongUnchanged() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        val watch = TestWatch()
        h.feed = y
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.feed = x
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        val (ackY, ackX) = h.link.sent.map { watch.take(it)!! }
        h.sync.acknowledge(ackX)
        h.sync.acknowledge(ackY) // the earlier feed's, arriving last
        assertFalse(h.knows(x))
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "one more send")
        h.sync.acknowledge(watch.take(h.link.sent.last())!!)
        assertTrue(watch.holds(x))
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(listOf(fp(y), fp(x), fp(x)), h.fingerprints)
    }

    @Test
    fun aSendCutOffByItsBudgetClaimsNothing() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            h.feed = y
            h.link.gate = CompletableDeferred() // never completed
            val start = currentTime
            assertEquals(FAILED, h.sync.sync(RESCHEDULED), "confirms $confirms")
            assertEquals(start + 5_000, h.reports.last().at)
            h.link.gate = null
            h.feed = x
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "Y may have been queued: confirms $confirms")
        }

        // On the confirming link, Y's acknowledgement after its FAILED still counts: its number is
        // the latest.
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.remembered(x)
        h.feed = y
        h.link.gate = CompletableDeferred()
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        h.link.gate = null
        h.sync.acknowledge(h.link.sent.last().echo)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
    }

    @Test
    fun anAcknowledgementIsServedInTurn() = runTest {
        var confirmedAtReport: Boolean? = null
        lateinit var h: SyncHarness
        h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), onReport = { confirmedAtReport = h.knows(x) })
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.sync.request(RESCHEDULED)
        runCurrent()
        assertEquals(1, h.link.sent.size, "the send is in flight")
        val ack = launch { h.sync.acknowledge(h.link.sent.single().echo) }
        runCurrent()
        assertFalse(h.knows(x), "the acknowledgement waits its turn behind the send")
        gate.complete(Unit)
        ack.join()
        assertEquals(false, confirmedAtReport, "nothing was confirmed before the send reported")
        assertTrue(h.knows(x))

        h.link.gate = null
        h.feed = y
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.link.sent.last().echo)
        assertTrue(h.knows(y))
        for (notOurs in listOf("", "xyz", fp(y))) {
            h.sync.acknowledge(notOurs)
            assertFalse(h.knows(y), "an echo of nothing we sent: '$notOurs'")
        }
    }

    // ── Item 5 and A4: the numbers fell behind the watch's ──

    /**
     * A record restored from a backup despite item 1 has numbers behind the watch's. Its echo above
     * the last number lifts it (item 5), so the next send goes above, and the watch takes it.
     */
    @Test
    fun anEchoAboveTheLastNumberLiftsIt() = runTest {
        for (confirms in listOf(false, true)) {
            val memory = FakeWatchFeedMemory()
            val h = SyncHarness(this, FakeWatchLink(confirms), memory)
            val watch = TestWatch()
            h.remembered(x)
            val snapshot = memory.record
            h.feed = y
            assertEquals(SENT, h.sync.sync(RESCHEDULED))
            h.feed = syncFeed(fajrShiftMinutes = 2)
            assertEquals(SENT, h.sync.sync(RESCHEDULED))
            h.link.sent.forEach { watch.take(it) } // the watch holds number 3

            memory.record = snapshot // restored: number 1, X confirmed
            h.feed = x
            assertEquals(SENT, h.handshake(watch.echo), "confirms $confirms: the watch holds number 3")
            assertEquals(4, h.link.sent.last().seq, "above the watch's")
            h.handshake(watch.take(h.link.sent.last()))
            assertTrue(watch.holds(x), "confirms $confirms")
            assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "confirms $confirms")
        }
    }

    /**
     * A restored record that used a number again for another feed (amendment A4, the design checks'
     * n22 and N3): the watch dropped it, since it is not greater, and echoes the number with the
     * feed it holds. That is no confirmation: the next ask sends under the next number.
     */
    @Test
    fun anEchoOfTheLastNumberWithAnotherFeedIsSentTheFeed() = runTest {
        for (confirms in listOf(false, true)) {
            val memory = FakeWatchFeedMemory()
            val h = SyncHarness(this, FakeWatchLink(confirms), memory)
            val watch = TestWatch()
            h.remembered(x)
            val snapshot = memory.record
            h.feed = y
            assertEquals(SENT, h.sync.sync(RESCHEDULED))
            watch.take(h.link.sent[0])
            watch.take(h.link.sent[1]) // the watch holds Y under number 2

            memory.record = snapshot
            val t = syncFeed(fajrShiftMinutes = 3)
            h.feed = t
            assertEquals(SENT, h.sync.sync(RESCHEDULED)) // number 2 again, for T
            assertEquals(null, watch.take(h.link.sent.last()), "not greater: dropped")
            assertEquals(SENT, h.handshake(watch.echo), "confirms $confirms: number 2 named Y, not T")
            h.handshake(watch.take(h.link.sent.last()))
            assertTrue(watch.holds(t), "confirms $confirms")
            assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "confirms $confirms")
        }
    }

    /**
     * Ruling 52 item 3: an echo at the largest number a `Long` holds once lifted `lastSeq` there,
     * so the next send's number overflowed and every ask after it was `FAILED`, for good. Above
     * half a `Long` it counts as another install's: forgotten, and the feed goes under the next
     * small number.
     */
    @Test
    fun anEchoAtTheLargestNumberNeverWedgesTheSync() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            val largest = WatchDelivery(TEST_ORIGIN, Long.MAX_VALUE, fp(x)).text
            assertEquals(SENT, h.handshake(largest), "confirms $confirms: another install's, so the feed goes")
            assertEquals(3, h.link.sent.last().seq, "confirms $confirms: after the number the forget set aside")
            if (confirms) h.sync.acknowledge(h.link.sent.last().echo)
            assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), "confirms $confirms: the sync goes on")
        }
    }

    // ── Item 6 and A3: a changed watch, and the watch a confirmation counts for ──

    @Test
    fun aChangedWatchForgetsFirst() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            assertEquals(SENT, h.sync.sync(WATCH_CHANGED), "confirms $confirms")
            assertEquals(2, h.link.sent.size, "the confirmation was forgotten first, so X went again")

            if (confirms) h.sync.acknowledge(h.link.sent.last().echo)
            assertTrue(h.knows(x))
            h.link.reachable = false
            assertEquals(NO_WATCH, h.sync.sync(WATCH_CHANGED), "confirms $confirms")
            assertFalse(h.knows(x), "even with no watch there, nothing is confirmed")
            h.link.reachable = true
            assertEquals(SENT, h.sync.sync(RESCHEDULED), "the watch back, X goes again: confirms $confirms")
        }
    }

    /**
     * On the iPhone a confirmation counts only for the watch it was made for (amendment A3): the
     * key the bridge keeps in that watch's own directory. The session moved to another watch, even
     * with no `WATCH_CHANGED` told (the app was not running at the switch), is sent the feed at the
     * next ask; and an echo from a watch that is not the session's is ignored.
     */
    @Test
    fun onTheIphoneAConfirmationCountsOnlyForItsWatch() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        h.link.key = "k0"
        h.remembered(x) // handed over to watch k0
        h.link.key = "k1" // the user switched watches
        assertEquals(SENT, h.sync.sync(WATCH_APPEARED), "watch k1 is not known to hold X")
        assertTrue(h.knows(x), "for k1 now")
        val k1sDelivery = h.link.sent.last().echo

        assertEquals(UNCHANGED, h.sync.sync(WATCH_ASKED, have = "", from = "k0"), "k0's word on k1's record: ignored")
        assertTrue(h.knows(x))
        h.sync.acknowledge("") // no key: not the session's watch either
        assertTrue(h.knows(x))
        assertEquals(UNCHANGED, h.sync.sync(WATCH_ASKED, have = k1sDelivery, from = "k1"))
        assertEquals(2, h.link.sent.size)
    }

    /**
     * Ruling 54 item 5: what an acknowledgement with no key comes to on the iPhone, where nothing
     * sends one (its watch shakes hands and never acknowledges). It is read as the word of a watch
     * with no key: ignored while the session's watch has a key, and taken in for the session's
     * watch while it has none (`watchKey()` null), as that watch's handshake with no key would be.
     */
    @Test
    fun onTheIphoneAnAcknowledgementWithNoKeyIsAKeylessWatchsWord() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        h.link.key = "k0"
        h.remembered(x)
        h.sync.acknowledge("")
        assertTrue(h.knows(x), "the session's watch has a key: ignored")

        h.link.key = null
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "the keyless watch is not known to hold X")
        assertTrue(h.knows(x), "handed over to it")
        h.sync.acknowledge("")
        assertFalse(h.knows(x), "taken in for the keyless watch: an echo of nothing forgets")
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals(3, h.link.sent.size)
    }

    /**
     * Ruling 52 item 9: Android's acknowledgement names the node it came from, and its confirmation
     * counts for that node alone, while a send goes to it ([WatchLink.watchKeys]).
     */
    @Test
    fun anAcknowledgementIsTakenInFromTheWatchItCameFrom() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.link.key = "node-a"
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        val echo = h.link.sent.last().echo
        h.sync.acknowledge(echo, from = "node-b")
        assertEquals("node-b", h.memory.record?.confirmedFor)
        assertFalse(h.knows(x), "another watch's word counts for that watch, which a send does not go to")
        h.sync.acknowledge(echo)
        assertFalse(h.knows(x), "no node named")
        h.sync.acknowledge(echo, from = "node-a")
        assertTrue(h.knows(x))
        assertEquals("node-a", h.memory.record?.confirmedFor)
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(1, h.link.sent.size)
    }

    /**
     * Amendment A3's key is read BEFORE the link is called (Ruling 52 item 7, mutant M9): a
     * hand-over confirms its number for the watch the session was sending to. When the session
     * moves to another watch while the context is being handed over, the confirmation stays with
     * the first, and the new watch is sent the feed at the next ask.
     */
    @Test
    fun onTheIphoneAHandOverCountsForTheWatchKeyReadBeforeTheCall() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        h.link.key = "k0"
        h.link.onSend = { h.link.key = "k1" } // the user switched watches during the call
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertEquals("k0", h.memory.record?.confirmedFor, "the watch the context went to")
        h.link.onSend = null
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "watch k1 is not known to hold X")
        assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED))
        assertEquals(2, h.link.sent.size)
    }

    // ── Over the link's limit: the times alone stand for the full feed ──

    @Test
    fun aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases() = runTest {
        val withPhrases = syncFeed(phrases = (1..3).map(::syncPhrase))
        val timesAlone = withPhrases.copy(tasbeeh = emptyList())

        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.feed = withPhrases
            h.link.answers += TOO_LARGE
            h.link.answers += SENT
            assertEquals(SENT, h.sync.sync(PHRASES_CHANGED), "confirms $confirms")
            val (first, second) = h.link.sent
            assertEquals(withPhrases, first.feed)
            assertEquals(withPhrases.days, second.feed.days)
            assertEquals(emptyList(), second.feed.tasbeeh)
            assertEquals(WatchDelivery(TEST_ORIGIN, 2, fp(timesAlone)), second.delivery, "the next number")
            if (confirms) {
                assertFalse(h.knows(withPhrases))
                h.sync.acknowledge(second.echo)
            }
            // What got through stood for the feed the link refused (Ruling 48 item 3).
            assertTrue(h.knows(withPhrases), "confirms $confirms")
        }

        val twice = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        twice.feed = withPhrases
        twice.link.result = TOO_LARGE
        assertEquals(FAILED, twice.sync.sync(PHRASES_CHANGED), "an ask never ends in TOO_LARGE")
        assertEquals(2, twice.link.sent.size)
        assertFalse(twice.knows(withPhrases))

        val slow = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        slow.feed = withPhrases
        slow.link.answers += TOO_LARGE
        slow.link.answers += SENT
        slow.link.sendTakes = 3.seconds
        val start = currentTime
        assertEquals(FAILED, slow.sync.sync(PHRASES_CHANGED), "both sends share the one budget")
        assertEquals(start + 5_000, slow.reports.single().at)
    }

    /**
     * An unchanged feed over the link's limit is not sent again at every ask (Ruling 48 item 3,
     * spec 1.2): not at a reschedule, a phrase change or a watch appearing, nor at the watch's
     * handshake echoing the times alone. A changed feed is sent.
     */
    @Test
    fun anUnchangedFeedTooLargeForTheLinkIsNotSentAgain() = runTest {
        val withPhrases = syncFeed(phrases = (1..3).map(::syncPhrase))
        val timesAlone = withPhrases.copy(tasbeeh = emptyList())

        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.link.limitBytes = WatchFeedJson.encode(timesAlone).encodeToByteArray().size
            h.feed = withPhrases
            assertEquals(SENT, h.sync.sync(PHRASES_CHANGED), "confirms $confirms")
            assertEquals(listOf(fp(withPhrases), fp(timesAlone)), h.fingerprints)
            if (confirms) h.sync.acknowledge(h.link.sent.last().echo)
            assertTrue(h.knows(withPhrases), "confirms $confirms")

            for (reason in listOf(RESCHEDULED, PHRASES_CHANGED, WATCH_APPEARED)) {
                assertEquals(UNCHANGED, h.sync.sync(reason), "$reason, confirms $confirms")
            }
            assertEquals(UNCHANGED, h.handshake(h.link.sent.last().echo), "the handshake, confirms $confirms")
            assertEquals(2, h.link.sent.size, "nothing sent again: confirms $confirms")

            h.feed = syncFeed(phrases = (1..4).map(::syncPhrase)) // one more phrase
            assertEquals(SENT, h.sync.sync(PHRASES_CHANGED), "confirms $confirms")
            assertEquals(4, h.link.sent.size, "the new feed, refused, then its times alone")
        }
    }

    /**
     * A handshake echoing the times alone still on their way counts as holding the feed they stand
     * for: on Android before their acknowledgement, and on either link after their send was cut
     * off by its budget, when the phone cannot know they got through.
     */
    @Test
    fun aHandshakeNamingTheTimesStillOnTheirWayIsRemembered() = runTest {
        val w = syncFeed(phrases = (1..3).map(::syncPhrase))
        val timesAlone = w.copy(tasbeeh = emptyList())
        for ((confirms, cutOff) in listOf(false to true, true to false, true to true)) {
            val case = "confirms $confirms, cut off $cutOff"
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.link.limitBytes = WatchFeedJson.encode(timesAlone).encodeToByteArray().size
            if (cutOff) {
                h.link.takes += 0.seconds // W, refused at once
                h.link.takes += 10.seconds // its times alone, past the budget
            }
            h.feed = w
            assertEquals(if (cutOff) FAILED else SENT, h.sync.sync(PHRASES_CHANGED), case)
            assertFalse(h.knows(w), case)

            assertEquals(UNCHANGED, h.handshake(h.link.sent.last().echo), case)
            assertTrue(h.knows(w), "$case: the watch holds what stands for W")
            assertEquals(UNCHANGED, h.sync.sync(RESCHEDULED), case)
            assertEquals(listOf(fp(w), fp(timesAlone)), h.fingerprints, case)
        }
    }

    // ── Ruling 70 item 1: an ask the budget cuts is asked again, once ──

    /**
     * P2.7's round 2 case: a reinstalled watch's handshake, whose ask the 5 s budget cut after
     * step 0 and before anything was sent (`WATCH_ASKED FAILED 5262ms`). The sync asks it again
     * 30 s after the cut, through the same path (the same reason and echo, a budget of its own),
     * so the watch is sent the feed without waiting for the next reschedule, opening, reconnect
     * or daily nudge.
     */
    @Test
    fun anAskTheBudgetCutsIsAskedAgainThirtySecondsLater() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            h.reports.clear()
            h.link.reachableGate = CompletableDeferred() // the capability query never answers
            val start = currentTime
            assertEquals(FAILED, h.handshake(""), "confirms $confirms")
            h.link.reachableGate = null
            delay(1.minutes) // a minute passes
            assertEquals(listOf(WATCH_ASKED to FAILED, WATCH_ASKED to SENT), h.reports.map { it.reason to it.result }, "confirms $confirms")
            assertEquals(listOf(start + 5_000, start + 35_000), h.reports.map { it.at }, "confirms $confirms")
            assertEquals(listOf(fp(x), fp(x)), h.fingerprints, "confirms $confirms: the reinstalled watch is sent X")
        }
    }

    /** The ask asked again is the same ask: a reschedule's is built for the location it planned. */
    @Test
    fun theAskAskedAgainIsBuiltForTheSameLocation() = runTest {
        val asked = mutableListOf<GeoLocation?>()
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), feedFor = { asked += it; x })
        h.link.gate = CompletableDeferred() // the first send never answers
        assertEquals(FAILED, h.sync.sync(RESCHEDULED, london))
        h.link.gate = null
        delay(1.minutes) // a minute passes
        assertEquals(listOf<GeoLocation?>(london, london), asked)
        assertEquals(listOf(RESCHEDULED to FAILED, RESCHEDULED to SENT), h.reports.map { it.reason to it.result })
    }

    /** Asked again once: a second cut waits for the usual triggers, which still serve. */
    @Test
    fun anAskCutAgainWaitsForTheUsualTriggers() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.link.gate = CompletableDeferred() // no send answers
        val start = currentTime
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        delay(10.minutes) // nothing else asks meanwhile
        assertEquals(listOf(FAILED, FAILED), h.reports.map { it.result })
        assertEquals(listOf(start + 5_000, start + 40_000), h.reports.map { it.at })
        assertEquals(2, h.link.sent.size)
        h.link.gate = null
        assertEquals(SENT, h.sync.sync(RESCHEDULED), "the next trigger")
        delay(1.minutes) // a minute passes
        assertEquals(3, h.reports.size)
    }

    /** Only the budget's cut is asked again: a refusal, a throw, a feed that cannot be written or any other answer waits for the usual triggers. */
    @Test
    fun onlyAnAskTheBudgetCutIsAskedAgain() = runTest {
        for (how in listOf("refused", "send threw", "question threw", "unwritable", "no watch", "no feed", "sent", "unchanged")) {
            val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
            if (how == "unchanged") h.remembered(x)
            h.reports.clear()
            when (how) {
                "refused" -> h.link.answers += FAILED
                "send threw" -> h.link.sendThrows = IllegalStateException("the link broke")
                "question threw" -> h.link.reachableThrows = IllegalStateException("the question broke")
                "unwritable" -> h.feed = syncFeed(declination = Double.NaN)
                "no watch" -> h.link.reachable = false
                "no feed" -> h.feed = null
            }
            h.sync.request(RESCHEDULED)
            delay(1.minutes) // a minute passes
            assertEquals(1, h.reports.size, "$how: asked once")
        }
    }

    /**
     * An ask made after a reschedule's cut one, queued behind it or made in the 30 s after the cut,
     * takes its place: it is served after the cut one with the newer plan, so the cut ask, built
     * for the location it planned, is not asked again, and the watch is never sent the older plan's
     * feed after the newer one.
     */
    @Test
    fun aLaterAskTakesThePlaceOfTheAskAskedAgain() = runTest {
        val first = syncFeed(fajrShiftMinutes = 1)
        val second = syncFeed(fajrShiftMinutes = 2)
        for (made in listOf("queued behind it", "in the 30 s after the cut")) {
            var londonBuilds = 0
            val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), feedFor = {
                if (it == london) {
                    if (++londonBuilds == 1) CompletableDeferred<Unit>().await() // the first build never ends
                    first
                } else {
                    second
                }
            })
            h.sync.request(RESCHEDULED, london)
            if (made == "queued behind it") {
                h.sync.request(RESCHEDULED, tripoli)
            } else {
                delay(20.seconds)
                h.sync.request(RESCHEDULED, tripoli)
            }
            delay(1.minutes) // a minute passes
            assertEquals(listOf(FAILED, SENT), h.reports.map { it.result }, made)
            assertEquals(1, londonBuilds, "$made: London's plan is not asked again")
            assertEquals(listOf(fp(second)), h.fingerprints, made)
        }
    }

    /** The ask asked again never holds the asks behind it: they are served at the cut, and a settle made then answers at once. */
    @Test
    fun theAskAskedAgainHoldsNothingBehindIt() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.link.reachableGate = CompletableDeferred()
        val start = currentTime
        h.sync.request(WATCH_ASKED, have = "")
        assertTrue(h.sync.settle())
        assertEquals(start + 5_000, currentTime, "settled at the cut, not 30 s after it")
    }

    /** Once the sync's scope is cancelled, an ask the budget cut before that is never asked again. */
    @Test
    fun cancellingTheScopeDropsTheAskAskedAgain() = runTest {
        val child = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), scope = child)
        h.link.gate = CompletableDeferred()
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        child.cancel()
        h.link.gate = null
        delay(1.minutes) // a minute passes
        assertEquals(1, h.builds)
        assertEquals(listOf(FAILED), h.reports.map { it.result })
    }

    /**
     * Review round 1's case, on both links: a reinstalled watch's handshake (an echo of nothing),
     * cut in its echo step by a first record read of 6 s, with the capability change a reinstall
     * also sends on Android queued behind it. The capability change finds the record still holding
     * X, since the echo was never taken in, and answers UNCHANGED; the handshake asked again 30 s
     * after its cut takes the echo in and sends the watch X, so it is not left empty until its next
     * handshake.
     */
    @Test
    fun aHandshakeCutInItsEchoIsAskedAgainThoughAnAskWasServedMeanwhile() = runTest {
        for (confirms in listOf(false, true)) {
            val h = SyncHarness(this, FakeWatchLink(confirms))
            h.remembered(x)
            h.reports.clear()
            h.memory.takes += 6.seconds // the handshake's first record read
            val start = currentTime
            h.sync.request(WATCH_ASKED, have = "", from = h.link.key)
            h.sync.request(WATCH_APPEARED)
            delay(1.minutes) // a minute passes
            assertEquals(
                listOf(
                    Triple(WATCH_ASKED, FAILED, start + 5_000),
                    Triple(WATCH_APPEARED, UNCHANGED, start + 5_000),
                    Triple(WATCH_ASKED, SENT, start + 35_000),
                ),
                h.reports.map { Triple(it.reason, it.result, it.at) },
                "confirms $confirms",
            )
            assertEquals(listOf(fp(x), fp(x)), h.fingerprints, "confirms $confirms: the reinstalled watch is sent X")
        }
    }

    /**
     * An ask with no planned location builds from what is stored when it is served, so it can
     * never send an older plan, and no ask served in the 30 s takes its place: every one of them is
     * asked again, a reschedule from the debug seam (no location) among them.
     */
    @Test
    fun anAskWithNoPlannedLocationIsAskedAgainWhateverIsServedMeanwhile() = runTest {
        for (reason in listOf(WATCH_ASKED, WATCH_CHANGED, WATCH_APPEARED, PHRASES_CHANGED, RESCHEDULED)) {
            val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
            h.remembered(x)
            h.reports.clear()
            h.memory.takes += 6.seconds // the cut ask's first record read
            val start = currentTime
            h.sync.request(reason, have = if (reason == WATCH_ASKED) "" else null)
            delay(20.seconds)
            h.sync.request(RESCHEDULED, tripoli)
            delay(1.minutes) // a minute passes
            assertEquals(
                listOf(reason to start + 5_000, RESCHEDULED to start + 20_000, reason to start + 35_000),
                h.reports.map { it.reason to it.at },
                "$reason",
            )
        }
    }

    /**
     * Only an ask takes the place of a reschedule's ask asked again: a settle (each background
     * entry point's last step) or the watch's acknowledgement served in the 30 s carries no plan,
     * so London's ask is still asked again.
     */
    @Test
    fun aSettleOrAnAcknowledgementNeverTakesThePlaceOfTheAskAskedAgain() = runTest {
        for (between in listOf("settle", "acknowledgement")) {
            var builds = 0
            val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), feedFor = {
                if (++builds == 1) CompletableDeferred<Unit>().await() // the first build never ends
                x
            })
            val start = currentTime
            h.sync.request(RESCHEDULED, london)
            delay(6.seconds)
            if (between == "settle") assertTrue(h.sync.settle()) else h.sync.acknowledge("")
            delay(1.minutes) // a minute passes
            assertEquals(listOf(FAILED to start + 5_000, SENT to start + 35_000), h.reports.map { it.result to it.at }, between)
            assertEquals(2, builds, "$between: London's plan is asked again")
        }
    }

    /**
     * An ask asked again takes no place either: only an ask as made is counted. A handshake cut
     * before a reschedule's cut is asked again first, and cut again; London's ask is still asked
     * again after it, so a second cut of the one never leaves the other's plan unasked.
     */
    @Test
    fun anAskAskedAgainNeverTakesThePlaceOfAnother() = runTest {
        var londonBuilds = 0
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), feedFor = {
            if (it == london && ++londonBuilds == 1) CompletableDeferred<Unit>().await() // London's first build never ends
            x
        })
        h.memory.takes += listOf(6.seconds, 6.seconds) // the handshake's first record read, each time
        val start = currentTime
        h.sync.request(WATCH_ASKED, have = "")
        h.sync.request(RESCHEDULED, london)
        delay(1.minutes) // a minute passes
        assertEquals(
            listOf(
                Triple(WATCH_ASKED, FAILED, start + 5_000),
                Triple(RESCHEDULED, FAILED, start + 10_000),
                Triple(WATCH_ASKED, FAILED, start + 40_000),
                Triple(RESCHEDULED, SENT, start + 40_000),
            ),
            h.reports.map { Triple(it.reason, it.result, it.at) },
        )
        assertEquals(2, londonBuilds, "London's plan is asked again")
    }

    /**
     * The ask asked again is the same ask, its echo and its watch too. Each handshake is cut in its
     * echo step by a first record read of 6 s. On the iPhone, the session's watch w1 held X and,
     * wiped, echoes nothing: the ask again forgets w1's confirmation and sends X. On Android, X was
     * sent and its acknowledgement not yet heard, and the handshake echoes X's delivery: the ask
     * again confirms it, UNCHANGED, nothing sent twice.
     */
    @Test
    fun theAskAskedAgainCarriesItsEchoAndItsWatch() = runTest {
        val phone = SyncHarness(this, FakeWatchLink(confirmsDelivery = false).apply { key = "w1" })
        phone.remembered(x)
        phone.reports.clear()
        phone.memory.takes += 6.seconds
        assertEquals(FAILED, phone.sync.sync(WATCH_ASKED, have = "", from = "w1"))
        delay(1.minutes) // a minute passes
        assertEquals(listOf(FAILED, SENT), phone.reports.map { it.result }, "the iPhone: w1's echo of nothing")
        assertEquals(listOf(fp(x), fp(x)), phone.fingerprints, "the iPhone: w1 is sent X again")

        val android = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        assertEquals(SENT, android.sync.sync(RESCHEDULED))
        android.memory.takes += 6.seconds
        assertEquals(FAILED, android.handshake(android.link.sent.last().echo))
        delay(1.minutes) // a minute passes
        assertEquals(listOf(SENT, FAILED, UNCHANGED), android.reports.map { it.result }, "Android: the echo of X's delivery")
        assertTrue(android.knows(x), "Android: the watch is known to hold X")
        assertEquals(listOf(fp(x)), android.fingerprints, "Android: X is sent once")
    }

    // ── Ruling 70 item 1: each ask's steps, timed for the debug log ──

    /**
     * Each step an ask takes is timed apart, in the order taken, on the sync's clock, so a slow one
     * can be named: the capability query, the build, the encoding, the record's read, and the send
     * (its number written down first, then the link).
     */
    @Test
    fun eachAskTimesItsSteps() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), feedFor = { delay(2.seconds); x })
        h.link.reachableTakes = 1.seconds
        h.link.sendTakes = 1.seconds
        h.memory.readTakes = 200.milliseconds
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        val times = h.times.single()
        assertEquals(
            listOf(AskStep(WATCHES, 1_000), AskStep(BUILD, 2_000), AskStep(ENCODE, 0), AskStep(RECORD, 200), AskStep(SEND, 1_200)),
            times.steps,
        )
        assertEquals(RESCHEDULED, times.reason)
        assertEquals(SENT, times.result)
        assertFalse(times.cut)
        assertFalse(times.again)
    }

    /** Step 0 is timed too, by what it does: a changed watch's forget, and the echo a handshake takes in. */
    @Test
    fun stepZeroIsTimedByWhatItDoes() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.memory.readTakes = 200.milliseconds
        assertEquals(SENT, h.sync.sync(WATCH_CHANGED))
        assertEquals(UNCHANGED, h.handshake(h.link.sent.last().echo))
        assertEquals(
            listOf(
                listOf(AskStep(FORGET, 200), AskStep(WATCHES, 0), AskStep(BUILD, 0), AskStep(ENCODE, 0), AskStep(RECORD, 200), AskStep(SEND, 200)),
                listOf(AskStep(ECHO, 200), AskStep(WATCHES, 0), AskStep(BUILD, 0), AskStep(ENCODE, 0), AskStep(RECORD, 200)),
            ),
            h.times.map { it.steps },
        )
    }

    /** An ask's steps end where it does; a feed too large for the link is encoded and sent twice. */
    @Test
    fun anAsksStepsEndWhereItEnds() = runTest {
        for ((how, steps) in listOf(
            "no watch" to listOf(WATCHES),
            "no feed" to listOf(WATCHES, BUILD),
            "unchanged" to listOf(WATCHES, BUILD, ENCODE, RECORD),
            "sent" to listOf(WATCHES, BUILD, ENCODE, RECORD, SEND),
            "too large" to listOf(WATCHES, BUILD, ENCODE, RECORD, SEND, ENCODE, SEND),
        )) {
            val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
            if (how == "unchanged") h.remembered(x)
            h.times.clear()
            when (how) {
                "no watch" -> h.link.reachable = false
                "no feed" -> h.feed = null
                "too large" -> {
                    h.link.limitBytes = WatchFeedJson.encode(x).encodeToByteArray().size
                    h.feed = syncFeed(phrases = (1..3).map(::syncPhrase))
                }
            }
            h.sync.sync(RESCHEDULED)
            assertEquals(steps, h.times.single().steps.map { it.step }, how)
            assertTrue(h.times.single().steps.all { it.done }, how)
        }
    }

    /**
     * The step the budget cut is named, unfinished, with the time it had run (P2.7 round 2's
     * question: where did the 5 s go?), and the ask says it was cut; the ask asked again says so.
     */
    @Test
    fun theStepTheBudgetCutIsNamed() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), feedFor = { CompletableDeferred<WatchFeed>().await() })
        h.link.reachableTakes = 1.seconds
        assertEquals(FAILED, h.handshake(""))
        val cut = h.times.single()
        assertEquals(listOf(AskStep(ECHO, 0), AskStep(WATCHES, 1_000), AskStep(BUILD, 4_000, done = false)), cut.steps)
        assertEquals(WATCH_ASKED, cut.reason)
        assertEquals(FAILED, cut.result)
        assertTrue(cut.cut)
        assertFalse(cut.again)
        delay(1.minutes) // a minute passes
        val again = h.times.drop(1).single()
        assertEquals(WATCH_ASKED, again.reason)
        assertTrue(again.again)
        assertTrue(again.cut)
        assertEquals(listOf(ECHO, WATCHES, BUILD), again.steps.map { it.step })
    }

    /** A step a throw ended is unfinished too, and the ask was not cut. */
    @Test
    fun theStepAThrowEndedIsUnfinished() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.feed = syncFeed(declination = Double.NaN)
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        val times = h.times.single()
        assertEquals(listOf(AskStep(WATCHES, 0), AskStep(BUILD, 0), AskStep(ENCODE, 0, done = false)), times.steps)
        assertFalse(times.cut)
    }

    // ── Kept from Ruling 48: one budget, never a throw, the order, the closed channel ──

    @Test
    fun noWatchBuildsNothing() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.link.reachable = false
        assertEquals(NO_WATCH, h.sync.sync(RESCHEDULED, london))
        assertEquals(0, h.builds)
        assertEquals(emptyList(), h.link.sent)
        assertEquals(emptyList(), h.memory.calls, "and reads nothing")
    }

    @Test
    fun noLocationSendsNothing() = runTest {
        var asked: GeoLocation? = null
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), feedFor = { asked = it; null })
        assertEquals(NO_FEED, h.sync.sync(RESCHEDULED, london))
        assertEquals(london, asked, "the location planned is the one the feed is built for")
        assertEquals(emptyList(), h.link.sent)
    }

    @Test
    fun aFeedThatCannotBeWrittenIsNotSentAndNotThrown() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = false))
        h.remembered(x)
        h.memory.calls.clear()
        h.feed = syncFeed(declination = Double.NaN)
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        assertEquals(1, h.link.sent.size, "only X, before")
        assertEquals(emptyList(), h.memory.calls, "no number taken for a feed never sent")
        assertTrue(h.knows(x))
    }

    @Test
    fun theWholeSyncHasOneBudget() = runTest {
        val slowBuild = SyncHarness(this, FakeWatchLink(), feedFor = { delay(4.seconds); x })
        slowBuild.link.sendTakes = 2.seconds
        var start = currentTime
        assertEquals(FAILED, slowBuild.sync.sync(RESCHEDULED))
        assertEquals(start + 5_000, slowBuild.reports.single().at)

        val neverBuilt = SyncHarness(this, FakeWatchLink(), feedFor = { CompletableDeferred<WatchFeed>().await() })
        start = currentTime
        assertEquals(FAILED, neverBuilt.sync.sync(RESCHEDULED))
        assertEquals(start + 5_000, neverBuilt.reports.single().at)

        val neverReached = SyncHarness(this, FakeWatchLink())
        neverReached.link.reachableGate = CompletableDeferred()
        start = currentTime
        assertEquals(FAILED, neverReached.sync.sync(RESCHEDULED))
        assertEquals(start + 5_000, neverReached.reports.single().at)
        assertEquals(0, neverReached.builds)
    }

    @Test
    fun anAskWaitingItsTurnKeepsItsWholeBudget() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.link.takes += 4.seconds
        h.link.takes += 3.seconds
        val start = currentTime
        h.sync.request(RESCHEDULED)
        h.sync.request(RESCHEDULED)
        assertTrue(h.sync.settle())
        assertEquals(listOf(SENT, SENT), h.reports.map { it.result })
        assertEquals(listOf(start + 4_000, start + 7_000), h.reports.map { it.at })
    }

    @Test
    fun theFeedIsBuiltOnTheSyncsOwnDispatcher() = runTest {
        val work = StandardTestDispatcher(testScheduler)
        var builtOn: ContinuationInterceptor? = null
        val h = SyncHarness(
            this,
            FakeWatchLink(),
            work = work,
            feedFor = { builtOn = currentCoroutineContext()[ContinuationInterceptor]; x },
        )
        assertEquals(SENT, h.sync.sync(RESCHEDULED))
        assertSame<ContinuationInterceptor?>(work, builtOn)
        assertNotEquals(currentCoroutineContext()[ContinuationInterceptor], builtOn, "never the caller's")
    }

    @Test
    fun aLinkThatThrowsIsAFailureNotACrash() = runTest {
        val h = SyncHarness(this, FakeWatchLink())
        h.link.sendThrows = IllegalStateException("the link broke")
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        h.link.sendThrows = null
        assertEquals(SENT, h.sync.sync(RESCHEDULED))

        h.link.reachableThrows = IllegalStateException("the question broke")
        assertEquals(FAILED, h.sync.sync(WATCH_CHANGED))
        h.link.reachableThrows = null
        assertEquals(SENT, h.sync.sync(WATCH_CHANGED))
    }

    @Test
    fun aStrayCancellationIsAFailureAndTheWorkerGoesOn() = runTest {
        // On the JVM this is java.util.concurrent.CancellationException, which a Play services
        // Task's getResult() throws.
        val h = SyncHarness(this, FakeWatchLink())
        h.link.sendThrows = CancellationException("stray")
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        h.link.sendThrows = null
        assertEquals(SENT, h.sync.sync(RESCHEDULED))

        h.link.reachableThrows = CancellationException("stray")
        assertEquals(FAILED, h.sync.sync(WATCH_CHANGED))
        h.link.reachableThrows = null
        assertEquals(SENT, h.sync.sync(WATCH_CHANGED))
    }

    @Test
    fun cancellingTheScopeStillStopsTheWorker() = runTest {
        val child = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val h = SyncHarness(this, FakeWatchLink(), scope = child)
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.sync.request(RESCHEDULED)
        runCurrent()
        assertEquals(1, h.link.sent.size, "the send is in flight")
        child.cancel()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(emptyList(), h.reports.map { it.result }, "the send in flight never reports")

        h.sync.request(RESCHEDULED) // and asking still returns
        advanceUntilIdle()
        assertEquals(emptyList(), h.reports.map { it.result })
        assertEquals(1, h.link.sent.size)
    }

    @Test
    fun aStoppedWorkerNeverHoldsACaller() = runTest {
        val child = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), scope = child)
        child.cancel()

        var start = currentTime
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        assertTrue(currentTime - start <= WatchFeedSync.CALLER_BOUND.inWholeMilliseconds, "sync at ${currentTime - start} ms")

        start = currentTime
        h.sync.acknowledge("")
        assertTrue(currentTime - start <= WatchFeedSync.CALLER_BOUND.inWholeMilliseconds, "acknowledge at ${currentTime - start} ms")

        start = currentTime
        assertFalse(h.sync.settle())
        assertTrue(currentTime - start <= WatchFeedSync.CALLER_BOUND.inWholeMilliseconds, "settle at ${currentTime - start} ms")
        assertEquals(10.seconds, WatchFeedSync.CALLER_BOUND)
    }

    /**
     * Once the worker has ended, its queue is closed (Ruling 48 item 4), so an ask, an
     * acknowledgement or a settle made after that is answered at once, never after
     * [WatchFeedSync.CALLER_BOUND]: whether the sync's scope was cancelled after the sync was built,
     * or before it.
     */
    @Test
    fun aSyncWhoseWorkerHasEndedAnswersAtOnce() = runTest {
        for (cancelledFirst in listOf(false, true)) {
            val child = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
            if (cancelledFirst) child.cancel()
            val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), scope = child)
            child.cancel()
            runCurrent() // the worker's end is served

            val start = currentTime
            assertEquals(FAILED, h.sync.sync(RESCHEDULED), "cancelled first $cancelledFirst")
            h.sync.acknowledge("")
            assertFalse(h.sync.settle(), "cancelled first $cancelledFirst")
            assertEquals(start, currentTime, "answered at once: cancelled first $cancelledFirst")
            h.sync.request(RESCHEDULED) // and asking still returns
            advanceUntilIdle()
            assertEquals(0, h.builds)
            assertEquals(emptyList(), h.reports.map { it.result })
        }
    }

    /**
     * The same for a worker that ends while it serves an ask (Ruling 50's 'kept from Ruling 48',
     * item 4): its send in flight never reports, and what is asked after the end is answered at
     * once.
     */
    @Test
    fun aWorkerThatEndsWhileServingAnswersAtOnceAfterwards() = runTest {
        val child = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true), scope = child)
        h.link.gate = CompletableDeferred() // never completed
        h.sync.request(RESCHEDULED)
        runCurrent()
        assertEquals(1, h.link.sent.size, "serving: the send is in flight")
        child.cancel()
        runCurrent() // the worker's end is served

        val start = currentTime
        assertEquals(FAILED, h.sync.sync(RESCHEDULED))
        h.sync.acknowledge(h.link.sent.single().echo)
        assertFalse(h.sync.settle())
        assertEquals(start, currentTime, "answered at once")
        assertEquals(emptyList(), h.reports.map { it.result })
        assertEquals(1, h.builds)
    }

    @Test
    fun asksAreServedInTheOrderMade() = runTest {
        val first = syncFeed(fajrShiftMinutes = 1)
        val second = syncFeed(fajrShiftMinutes = 2)
        val h = SyncHarness(this, FakeWatchLink(), feedFor = { if (it == london) first else second })
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.sync.request(RESCHEDULED, london)
        assertEquals(0, currentTime)
        h.sync.request(RESCHEDULED, tripoli)
        assertEquals(0, currentTime)
        runCurrent()
        assertEquals(listOf(fp(first)), h.fingerprints, "the second waits its turn")
        gate.complete(Unit)
        assertTrue(h.sync.settle())
        assertEquals(listOf(fp(first), fp(second)), h.fingerprints)
    }

    @Test
    fun requestNeverWaitsForTheWorker() = runTest {
        // A second scheduler cannot share runTest's scope, so the sync gets a scope of its own on it.
        val other = TestCoroutineScheduler()
        val scope = CoroutineScope(StandardTestDispatcher(other) + Job())
        val h = SyncHarness(
            this,
            FakeWatchLink(confirmsDelivery = true),
            scope = scope,
            work = StandardTestDispatcher(other),
        )
        repeat(3) { h.sync.request(RESCHEDULED) }
        runCurrent()
        assertEquals(0, h.builds, "nothing is built until the worker's own scheduler runs")
        other.advanceUntilIdle()
        assertEquals(3, h.builds)
        assertEquals(listOf(SENT, SENT, SENT), h.reports.map { it.result })
        scope.cancel()
    }

    @Test
    fun settleWaitsForWhatWasAsked() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        val gate = CompletableDeferred<Unit>()
        h.link.gate = gate
        h.sync.request(RESCHEDULED)
        h.sync.request(WATCH_APPEARED)
        var settled: Boolean? = null
        var reportedBySettle = -1
        val waiting = launch {
            settled = h.sync.settle()
            reportedBySettle = h.reports.size
        }
        runCurrent()
        assertEquals(null, settled, "the first send is still in flight")
        gate.complete(Unit)
        waiting.join()
        assertEquals(true, settled)
        assertEquals(2, reportedBySettle)
    }
}
