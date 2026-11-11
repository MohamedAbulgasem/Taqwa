package world.taqwa.app.watchfeed

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watchfeed.WatchSendResult.FAILED
import world.taqwa.app.watchfeed.WatchSendResult.NO_FEED
import world.taqwa.app.watchfeed.WatchSendResult.NO_WATCH
import world.taqwa.app.watchfeed.WatchSendResult.SENT
import world.taqwa.app.watchfeed.WatchSendResult.UNCHANGED
import world.taqwa.app.watchfeed.WatchSyncReason.PHRASES_CHANGED
import world.taqwa.app.watchfeed.WatchSyncReason.RESCHEDULED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_APPEARED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_ASKED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_CHANGED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val X = syncFeed()
private val Y = syncFeed(fajrShiftMinutes = 1)
private val Z = syncFeed(fajrShiftMinutes = 2)

/** Over the link's limit: its times alone are X's very bytes. */
private val W = syncFeed(phrases = (1..3).map(::syncPhrase))

/**
 * A phone, its link and its watches, played step by step: every send's fate, every landing and
 * every echo is the script's. Each `UNCHANGED` is checked as it is answered, in the spirit of the
 * model test's property S: the watch checked holds the feed (on the iPhone, or has it as the
 * latest context handed over), unless it is marked [wiped] or the case accepts a transient. The
 * watch checked is the one that shook hands, for a handshake; for anything else, the watch the
 * phone heard from last (Android) or the session's watch (the iPhone). The [wiped] mark is the
 * script's own, simpler than S's times; see its KDoc for how it differs.
 */
private class Script(private val test: TestScope, val confirms: Boolean, watches: Int = 1) {
    val link = WorldLink(confirms, limitBytes = WatchFeedJson.encode(X).encodeToByteArray().size)
    var memory = FakeWatchFeedMemory()
    private var phone = SyncHarnessOver(test, link, memory)
    val watch = List(watches) { TestWatch() }
    val nearby = MutableList(watches) { true }
    var session = 0
    val keys = MutableList(watches) { "k$it" }
    private val inFlight = List(watches) { mutableListOf<FakeWatchLink.Sent>() }
    private val pending = MutableList<FakeWatchLink.Sent?>(watches) { null }
    val acks = mutableListOf<Pair<Int, String>>()
    private var heardFrom = 0
    val outcomes = ArrayDeque<Outcome>()

    /** Whether the link sees a watch. */
    private var reachable = true

    var current: WatchFeed = X
        set(value) {
            field = value
            phone.feed = value
        }

    init {
        link.outcome = { outcomes.removeFirstOrNull() ?: Outcome.SENT }
        link.reachable = { reachable }
        link.key = { if (confirms) null else keys[session] }
        link.onHanded = { sent ->
            if (confirms) watch.indices.filter { nearby[it] }.forEach { inFlight[it] += sent } else pending[session] = sent
        }
    }

    val last: FakeWatchLink.Sent get() = link.sent.last()
    val sends: Int get() = link.sent.size

    /** One ask; an `UNCHANGED` is checked against the watch the phone heard from last. */
    suspend fun ask(reason: WatchSyncReason = RESCHEDULED, vararg outcome: Outcome): WatchSendResult {
        outcomes += outcome
        val result = phone.sync.sync(reason)
        outcomes.clear()
        if (result == UNCHANGED) checkHeld(if (confirms) heardFrom else session)
        return result
    }

    /** Watch [w]'s handshake as it is now, to be served later. */
    fun handshakeOf(w: Int = 0): Pair<Int, String> = w to watch[w].echo

    /** A handshake served; an `UNCHANGED` is checked against the watch that sent it. */
    suspend fun serve(handshake: Pair<Int, String>, vararg outcome: Outcome): WatchSendResult {
        val (w, echo) = handshake
        outcomes += outcome
        val result = phone.sync.sync(WATCH_ASKED, have = echo, from = if (confirms) null else keys[w])
        outcomes.clear()
        heardFrom = w
        if (result == UNCHANGED) checkHeld(if (confirms || w == session) w else session)
        return result
    }

    /**
     * A handshake served whose ask ends, as [how] says, after its echo is taken in and before any
     * number is taken; the world is put back after it.
     */
    suspend fun serveUnnumbered(handshake: Pair<Int, String>, how: Unnumbered): WatchSendResult {
        when (how) {
            Unnumbered.NO_WATCH -> reachable = false
            Unnumbered.NO_FEED -> phone.feed = null
            Unnumbered.CUT_IN_THE_BUILD -> phone.buildTakes = 6.seconds // the ask's budget is 5 s
        }
        val result = serve(handshake)
        reachable = true
        phone.feed = current
        phone.buildTakes = Duration.ZERO
        return result
    }

    private fun checkHeld(w: Int) {
        val handed = pending[w]?.takeIf { WatchLinkContract.stores(watch[w].held?.delivery, it.delivery) }
        val held = watch[w].holds(current) ||
            (!confirms && handed != null && (handed.fingerprint == fp(current) || handed.fingerprint == fp(current.copy(tasbeeh = emptyList()))))
        if (!held && !wiped[w] && !acceptedTransient) {
            fail("a wrong UNCHANGED: watch $w holds ${watch[w].held?.delivery}, the phone's feed is ${fp(current)}")
        }
    }

    /**
     * Set by [wipe], and cleared only when an acknowledgement from that watch is taken in
     * ([ack]): any acknowledgement, even one the watch sent before the wipe. While set, an
     * `UNCHANGED` checked against that watch is excused. It differs from the model's S both ways:
     * an acknowledgement sent before the wipe clears it, where S still excuses the wipe (stricter
     * here); and a handshake served after the wipe leaves it set, where S stops excusing it once it
     * has taken in the watch's echo of nothing (looser here). Each case that wipes asserts its own
     * answers besides, so the mark is never what proves a case.
     */
    val wiped = MutableList(watches) { false }

    /** Set by a case the design checks accept as a transient outside the documented platform. */
    var acceptedTransient = false

    /** Send [seq] reaches watch [w] (Android), which stores it or drops it; its ack goes on its way. */
    fun land(seq: Long, w: Int = 0) {
        val sent = inFlight[w].firstOrNull { it.seq == seq } ?: fail("send $seq is not on its way to watch $w")
        inFlight[w].remove(sent)
        watch[w].take(sent)?.let { acks += w to it }
    }

    /** Send [seq] to watch [w] is lost. */
    fun lose(seq: Long, w: Int = 0) {
        inFlight[w].removeAll { it.seq == seq }
    }

    /** The iPhone's latest context for watch [w] reaches it. */
    fun landContext(w: Int = 0) {
        val sent = pending[w] ?: fail("no context for watch $w")
        pending[w] = null
        watch[w].take(sent)
    }

    /** The system loses the context it holds for watch [w] (a stress the platform does not document). */
    fun loseContext(w: Int = 0) {
        pending[w] = null
    }

    /** The acknowledgement watch [w] sent for [seq] reaches the phone. */
    suspend fun ack(seq: Long, w: Int = 0) {
        deliver(acks.firstOrNull { it.first == w && it.second.split('/')[1] == seq.toString() } ?: fail("no ack $seq from $w"))
    }

    private suspend fun deliver(ack: Pair<Int, String>) {
        acks.remove(ack)
        phone.sync.acknowledge(ack.second)
        heardFrom = ack.first
        wiped[ack.first] = false
    }

    fun loseAck(seq: Long, w: Int = 0) {
        acks.removeAll { it.first == w && it.second.split('/')[1] == seq.toString() }
    }

    fun wipe(w: Int = 0) {
        watch[w].wipe()
        wiped[w] = true
    }

    /** A new process: the queue gone, the record kept. */
    fun restart() {
        phone = SyncHarnessOver(test, link, memory).also { it.feed = current }
    }

    /** [f] sent, landed on watch [w] and, on Android, acknowledged. */
    suspend fun confirmed(f: WatchFeed, w: Int = 0) {
        current = f
        assertEquals(SENT, ask())
        if (confirms) {
            land(last.seq, w)
            ack(last.seq, w)
        } else {
            landContext(w)
        }
    }

    /**
     * The links quiet: everything on its way lands in order and every acknowledgement is taken in;
     * then each watch's own handshake, its answer landing too (property L). True when every watch
     * named ends on the current feed.
     */
    suspend fun quiet(watches: List<Int> = watch.indices.toList()): Boolean {
        drain()
        for (w in watches) {
            if (!confirms) session = w
            serve(handshakeOf(w))
            drain()
        }
        return watches.all { watch[it].holds(current) }
    }

    private suspend fun drain() {
        for (w in watch.indices) {
            if (confirms) while (inFlight[w].isNotEmpty()) land(inFlight[w].first().seq, w) else if (w == session && pending[w] != null) landContext(w)
        }
        while (acks.isNotEmpty()) deliver(acks.first())
    }
}

/** How an ask can end before it takes a number: no watch, no feed, or its budget run out in the build. */
private enum class Unnumbered { NO_WATCH, NO_FEED, CUT_IN_THE_BUILD }

/** A sync over a script's [WorldLink]: [SyncHarness]'s, over any link. */
private class SyncHarnessOver(test: TestScope, link: WatchLink, memory: FakeWatchFeedMemory) {
    var feed: WatchFeed? = X
    var buildTakes: Duration = Duration.ZERO
    val sync = WatchFeedSync(
        feedFor = {
            if (buildTakes > Duration.ZERO) delay(buildTakes)
            feed
        },
        link = link,
        memory = memory,
        scope = test.backgroundScope,
        work = kotlinx.coroutines.test.StandardTestDispatcher(test.testScheduler),
    )
}

/**
 * Ruling 48's named counterexamples (its three review rounds, plan 2's call 1 and Review Focus 2)
 * and the design checks' new ones, each as a plain test on every link it applies to (Ruling 50
 * item 7). Each ends with the links quiet and each watch's own handshake, which must leave every
 * watch on the current feed, and checks every `UNCHANGED` as it is answered.
 */
class WatchFeedNamedCasesTest {

    private val links = listOf(false, true)

    /** Ruling 48 item 1: X held, + to Y, − back to X, the handshake naming X served first. */
    @Test
    fun aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo() = runTest {
        for (confirms in links) for (cutOff in listOf(false, true)) for (restart in listOf(false, true)) {
            val case = "confirms $confirms, cut off $cutOff, restart $restart"
            val s = Script(this, confirms)
            s.confirmed(X)
            s.current = Y
            s.ask(RESCHEDULED, if (cutOff) Outcome.CUT_OFF else Outcome.SENT)
            val y = s.last.seq
            val handshake = s.handshakeOf() // the watch still holds X
            if (restart) s.restart() // round 2's p5: the process restarts with Y in flight
            s.current = X // the undo, its reschedule queued behind the handshake
            assertEquals(SENT, s.serve(handshake), "$case: the watch named an older delivery")
            val undo = s.ask()
            assertEquals(if (confirms) SENT else UNCHANGED, undo, case)
            if (confirms) s.land(y) else Unit // Y lands late, where it still can
            assertTrue(s.quiet(), case)
        }
    }

    /** R48's own: Y in flight at a handshake naming X, then Y's acknowledgement. */
    @Test
    fun aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives() = runTest {
        val s = Script(this, confirms = true)
        s.confirmed(X)
        s.current = Y
        assertEquals(SENT, s.ask())
        val y = s.last.seq
        s.current = X // the − undoes it, and the watch shakes hands before Y reaches it
        assertEquals(SENT, s.serve(s.handshakeOf()))
        s.land(y)
        s.ack(y) // Y arrives late, and the watch holds it
        assertEquals(SENT, s.ask(), "never UNCHANGED: the watch holds Y")
        assertTrue(s.quiet())
        assertEquals(listOf(fp(X), fp(Y), fp(X), fp(X)), s.link.sent.map { it.fingerprint })
    }

    /** Round 3's probe: the undo's own send not through (refused, no watch, cut off, thrown). */
    @Test
    fun aHandshakeAfterAnUndoThatDidNotGetThroughEndsWithTheWatchOnTheUndo() = runTest {
        for (confirms in links) for (undo in listOf(Outcome.REFUSED, Outcome.NO_WATCH, Outcome.CUT_OFF, Outcome.CUT_OFF_BEFORE, Outcome.THROWN, Outcome.THROWN_AFTER)) {
            if (!confirms && undo == Outcome.THROWN_AFTER) continue
            val case = "confirms $confirms, the undo's send $undo"
            val s = Script(this, confirms)
            s.confirmed(X)
            s.current = Y
            assertEquals(SENT, s.ask())
            val y = s.last.seq
            val handshake = s.handshakeOf() // stale: names X
            s.current = X
            assertEquals(undo.answer, s.ask(RESCHEDULED, undo), case)
            assertEquals(SENT, s.serve(handshake), case)
            if (confirms) {
                s.land(y)
                s.ack(y)
            }
            assertTrue(s.quiet(), case)
        }
    }

    /** The iPhone relaunched after a send that did not answer `SENT`, an undo, its stale handshake. */
    @Test
    fun aRelaunchedPhoneWhoseUndoDidNotGetThroughEndsWithTheWatchOnTheUndo() = runTest {
        for (how in listOf(Outcome.CUT_OFF, Outcome.THROWN, Outcome.REFUSED)) {
            val s = Script(this, confirms = false)
            s.confirmed(X)
            val handshake = s.handshakeOf() // names X
            s.current = Y
            s.ask(RESCHEDULED, how)
            s.restart()
            s.current = X
            assertEquals(SENT, s.serve(handshake), "$how")
            assertEquals(UNCHANGED, s.ask(), "$how")
            assertTrue(s.quiet(), "$how")
        }
    }

    /** Round 3 spec (1): Y's acknowledgement served before a stale handshake naming X, the undo after. */
    @Test
    fun anAckOvertakingAStaleHandshakeLeavesTheWatchOnTheUndo() = runTest {
        val s = Script(this, confirms = true)
        s.confirmed(X)
        val handshake = s.handshakeOf()
        s.current = Y
        assertEquals(SENT, s.ask())
        s.land(s.last.seq)
        s.ack(s.last.seq)
        s.current = X
        assertEquals(SENT, s.serve(handshake), "the watch holds Y, whatever the stale handshake named")
        assertEquals(SENT, s.ask())
        assertTrue(s.quiet())
    }

    /** Round 3 spec (2), ABA with a loss: X, Y, X sent, the last X lost, a stale handshake and a late ack. */
    @Test
    fun anAbaWhoseLastSendIsLostIsSentAgain() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.current = X
            assertEquals(SENT, s.ask())
            val x1 = s.last.seq
            if (confirms) s.land(x1) else s.landContext()
            val handshake = s.handshakeOf() // names the first X
            s.current = Y
            assertEquals(SENT, s.ask())
            val y = s.last.seq
            s.current = X
            assertEquals(SENT, s.ask())
            val x2 = s.last.seq
            if (confirms) {
                s.land(y)
                s.lose(x2)
                s.ack(y)
                s.ack(x1) // the late ack of the first X
            } else {
                s.loseContext() // a stress on the iPhone: the system loses the last X
            }
            assertEquals(SENT, s.serve(handshake), "confirms $confirms")
            assertTrue(s.quiet(), "confirms $confirms")
        }
    }

    /** Round 1's R3g: the watch on an older Y, W over the limit sent as its times alone, which never land. */
    @Test
    fun aWatchHoldingAnOlderFeedIsSentTheFeedTooLargeForTheLink() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.confirmed(Y)
            s.current = W
            assertEquals(SENT, s.ask(PHRASES_CHANGED), "refused, then the times alone")
            if (confirms) s.lose(s.last.seq) else s.loseContext()
            assertEquals(SENT, s.serve(s.handshakeOf()), "confirms $confirms: the watch holds Y, not the times")
            assertEquals(listOf(fp(Y), fp(W), fp(X), fp(W), fp(X)), s.link.sent.map { it.fingerprint })
            assertTrue(s.quiet(), "confirms $confirms")
        }
    }

    /** A watch holding X for another reason, then W, whose times alone are X's bytes: sent, then damped. */
    @Test
    fun aWatchHoldingTheSameTimesForAnotherReasonIsStillSentTheStandIn() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.confirmed(X)
            s.current = W
            assertEquals(SENT, s.ask(PHRASES_CHANGED), "confirms $confirms")
            if (confirms) {
                s.land(s.last.seq)
                s.ack(s.last.seq)
            } else {
                s.landContext()
            }
            assertEquals(UNCHANGED, s.ask(), "confirms $confirms")
            assertEquals(UNCHANGED, s.serve(s.handshakeOf()), "confirms $confirms")
        }
    }

    /** Round 1 (f): the stand-in sent, every phrase then deleted, the stand-in's ack arriving late. */
    @Test
    fun aLateStandInAckAfterThePhrasesAreDeletedConfirmsNothingWrong() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.current = W
            assertEquals(SENT, s.ask(PHRASES_CHANGED))
            val stand = s.last.seq
            if (!confirms) s.landContext()
            s.current = X // every phrase deleted: the feed is now those very bytes
            assertEquals(SENT, s.ask(PHRASES_CHANGED), "confirms $confirms: item 3 compares what the send stood for")
            if (confirms) {
                s.land(stand)
                s.land(s.last.seq)
                s.ack(s.last.seq)
                s.ack(stand) // late
            }
            assertTrue(s.quiet(), "confirms $confirms")
        }
    }

    /** Call 1's "one case left": two queued feeds delivered in the reverse of their order. */
    @Test
    fun twoFeedsDeliveredOutOfOrderLeaveTheWatchOnTheLater() = runTest {
        val s = Script(this, confirms = true)
        s.current = X
        s.ask()
        val x = s.last.seq
        s.current = Y
        s.ask(PHRASES_CHANGED)
        val y = s.last.seq
        s.land(y)
        s.land(x) // the earlier one, last: dropped, so never acknowledged
        assertEquals(1, s.acks.size)
        s.ack(y)
        assertTrue(s.watch[0].holds(Y))
        assertEquals(UNCHANGED, s.ask())
        assertEquals(2, s.sends)
    }

    /** aRelaunchedAndroidPhoneTakesTheWatchsWordForAFeedCutOff: a restart, Y cut off but landed, its ack lost. */
    @Test
    fun aRelaunchedAndroidPhoneTakesTheWatchsWordForAFeedCutOff() = runTest {
        val s = Script(this, confirms = true)
        s.confirmed(X)
        s.restart()
        s.current = Y
        s.ask(RESCHEDULED, Outcome.CUT_OFF)
        s.land(s.last.seq)
        s.loseAck(s.last.seq)
        assertEquals(UNCHANGED, s.serve(s.handshakeOf()))
        assertEquals(UNCHANGED, s.ask())
        assertEquals(2, s.sends)
    }

    /** A refused send leaves the watch on the feed before it; its handshake is sent the current one. */
    @Test
    fun aHandshakeAfterARefusedSendIsSentTheCurrentFeed() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.current = X
            s.ask()
            if (confirms) s.land(s.last.seq) else s.landContext()
            s.current = Y
            s.ask(RESCHEDULED, Outcome.REFUSED)
            assertEquals(SENT, s.serve(s.handshakeOf()), "confirms $confirms")
            assertTrue(s.quiet(), "confirms $confirms")
        }
    }

    /** A send that may have got through outnumbers the one before it; the next send outnumbers it. */
    @Test
    fun aSendThatMayHaveGotThroughStaysOnItsWayBesideTheOneBeforeIt() = runTest {
        for (confirms in links) for (how in listOf(Outcome.CUT_OFF, Outcome.THROWN_AFTER)) {
            if (!confirms && how == Outcome.THROWN_AFTER) continue
            val s = Script(this, confirms)
            s.current = Y
            s.ask()
            val y = s.last.seq
            s.current = X
            s.ask(RESCHEDULED, how) // may have been handed over
            val x = s.last.seq
            s.current = Y // back to Y, and the watch, on Y so far, names it
            if (confirms) s.land(y) else s.landContext()
            assertEquals(SENT, s.serve(s.handshakeOf()), "confirms $confirms, $how: X may land last")
            if (confirms) s.land(x) // X lands after all, then Y's newer number after it
            assertTrue(s.quiet(), "confirms $confirms, $how")
        }
    }

    /** Round 3: a refused resend of the feed on its way, then the undo and another stale handshake. */
    @Test
    fun aRefusedResendLeavesTheFeedOnItsWay() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.confirmed(X)
            s.current = Y
            s.ask()
            val y = s.last.seq
            assertEquals(WatchSendResult.FAILED, s.serve(s.handshakeOf(), Outcome.REFUSED), "Y once more, refused")
            s.current = X
            assertEquals(SENT, s.serve(s.handshakeOf()), "confirms $confirms")
            if (confirms) s.land(y)
            assertTrue(s.quiet(), "confirms $confirms")
        }
    }

    /** Round 2's p5 and C6: a restart with Y in flight, back to X, a stale handshake, Y landing late. */
    @Test
    fun aRestartWithAFeedInFlightEndsWithTheWatchOnTheUndo() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.confirmed(X)
            val handshake = s.handshakeOf()
            s.current = Y
            s.ask()
            val y = s.last.seq
            s.restart()
            s.current = X
            assertEquals(SENT, s.serve(handshake))
            s.ask()
            if (confirms) s.land(y)
            assertTrue(s.quiet(), "confirms $confirms")
        }
    }

    // ── The design checks' new cases ──

    /** n17: a second watch switched to, still on X, while A took and acknowledged Y. */
    @Test
    fun aSecondWatchSwitchedToIsSentTheFeedAtItsHandshake() = runTest {
        val s = Script(this, confirms = true, watches = 2)
        s.confirmed(X) // on A
        s.land(s.last.seq, 1) // and on B
        s.ack(s.last.seq, 1)
        s.nearby[1] = false // B away
        s.current = Y
        s.ask()
        s.land(s.last.seq, 0)
        s.ack(s.last.seq, 0)
        s.nearby[0] = false
        s.nearby[1] = true // the user wears B again
        assertEquals(UNCHANGED, s.ask(WATCH_APPEARED), "the phone cannot know B: A's echo was the last")
        assertEquals(SENT, s.serve(s.handshakeOf(1)), "B names X")
        assertTrue(s.quiet(listOf(1)))
    }

    /** n18: the watch wiped with its STARTED handshake lost, an older feed then landing on it. */
    @Test
    fun aWipedWatchThatTookALateOlderFeedIsSentTheCurrentOne() = runTest {
        val s = Script(this, confirms = true)
        s.current = Y
        s.ask()
        val y = s.last.seq
        s.current = X
        s.ask()
        s.land(s.last.seq)
        s.ack(s.last.seq)
        s.wipe()
        s.land(y) // the empty watch stores it, and acknowledges it
        s.ack(y)
        assertEquals(SENT, s.ask(), "never UNCHANGED: the watch holds Y")
        assertTrue(s.quiet())
    }

    /**
     * D1 (Ruling 50's review, round 1): X stored under number 1, its acknowledgement still on its
     * way; the watch wiped; its empty handshake taken in by an ask that ends before it takes a
     * number (no watch, no feed, or its budget run out in the build); then the late
     * acknowledgement of number 1, true when the watch sent it. The phone has heard the watch say
     * it holds nothing, so no echo sent before that may confirm the latest number again: an echo of
     * nothing sets a number aside (amendment A2), and the late one names a number below it. The
     * reschedule and the reconnect after it both send.
     */
    @Test
    fun aLateAckFromBeforeAWipeConfirmsNothingOnceTheWatchSaidItHoldsNothing() = runTest {
        for (how in Unnumbered.entries) for (next in listOf(RESCHEDULED, WATCH_APPEARED)) {
            val case = "$how, then $next"
            val s = Script(this, confirms = true)
            assertEquals(SENT, s.ask())
            s.land(s.last.seq) // X stored, its acknowledgement on its way
            s.wipe()
            val expected = when (how) {
                Unnumbered.NO_WATCH -> NO_WATCH
                Unnumbered.NO_FEED -> NO_FEED
                Unnumbered.CUT_IN_THE_BUILD -> FAILED
            }
            assertEquals(expected, s.serveUnnumbered(s.handshakeOf(), how), case)
            assertEquals(1, s.sends, "$case: nothing was sent")
            s.ack(1) // the acknowledgement from before the wipe
            assertEquals(SENT, s.ask(next), "$case: never UNCHANGED, the watch holds nothing")
            assertTrue(s.quiet(), case)
            assertEquals(UNCHANGED, s.ask(), case)
        }
    }

    /**
     * The known limit D1 leaves (a wiped watch whose handshake was lost): Y under number 1 still on
     * its way, X under number 2 stored, its acknowledgement late; the watch wiped, its handshake
     * lost; Y lands on the empty watch and its acknowledgement arrives first, then X's. Each echo
     * is true when sent, and echoes carry no time, so the phone cannot tell X's from a newer one:
     * the transient. The watch's own handshake heals it.
     */
    @Test
    fun aStaleAckOvertakingAWipedWatchsNewerOneIsHealedByItsHandshake() = runTest {
        val s = Script(this, confirms = true)
        s.current = Y
        assertEquals(SENT, s.ask())
        val y = s.last.seq
        s.current = X
        assertEquals(SENT, s.ask())
        val x = s.last.seq
        s.land(x) // X stored, its acknowledgement on its way
        s.wipe() // its start's handshake lost
        s.land(y) // the empty watch stores Y
        s.ack(y) // taken in before any ask
        s.acceptedTransient = true // X's acknowledgement, true when sent, overtaken by Y's
        s.ack(x)
        assertEquals(UNCHANGED, s.ask(), "the transient: X's late acknowledgement names the latest number")
        s.acceptedTransient = false
        assertEquals(SENT, s.serve(s.handshakeOf()), "the watch names Y: healed")
        assertTrue(s.quiet())
    }

    /** n19: two watches nearby at once; Y reaches A only; B's daily handshake names X. */
    @Test
    fun aWatchThatMissedTheFeedTheOtherTookIsSentItAtItsHandshake() = runTest {
        val s = Script(this, confirms = true, watches = 2)
        s.confirmed(X)
        s.land(s.last.seq, 1)
        s.ack(s.last.seq, 1)
        s.current = Y
        s.ask()
        s.land(s.last.seq, 0)
        s.lose(s.last.seq, 1)
        s.ack(s.last.seq, 0)
        assertEquals(SENT, s.serve(s.handshakeOf(1)))
        assertTrue(s.quiet())
    }

    /** n20: a new install (new origin, nothing restored), the watch kept with the old phone's number 3. */
    @Test
    fun aNewInstallStartsItsOwnNumbersAndTheWatchTakesThem() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            for (f in listOf(X, Y, Z)) s.confirmed(f)
            s.memory = FakeWatchFeedMemory(origin = "1111111111111111")
            s.restart()
            s.current = X
            assertEquals(SENT, s.ask(WATCH_APPEARED), "confirms $confirms")
            assertEquals(1, s.last.seq, "the new install's first number")
            if (confirms) {
                s.land(s.last.seq)
                s.ack(s.last.seq)
            } else {
                s.landContext()
            }
            assertTrue(s.watch[0].holds(X), "another origin's: stored")
            assertEquals(UNCHANGED, s.ask(), "confirms $confirms")
        }
    }

    /** The straggler rider: the old install's feed, handed over before the move, lands after the new one's. */
    @Test
    fun anOldInstallsStragglerIsHealedByTheWatchsNextEcho() = runTest {
        val s = Script(this, confirms = true)
        s.confirmed(X)
        s.current = Y
        s.ask() // the old install's Y, still on its way
        val straggler = s.last.seq
        s.memory = FakeWatchFeedMemory(origin = "1111111111111111")
        s.restart()
        s.current = X
        assertEquals(SENT, s.ask(WATCH_APPEARED))
        s.land(s.last.seq)
        s.ack(s.last.seq)
        s.land(straggler) // another origin's: it replaces the new install's feed
        assertEquals(SENT, s.serve(s.handshakeOf()), "healed by the watch's next echo")
        assertTrue(s.quiet())
    }

    /** n21: a record restored from an old snapshot; its sends are stale on the watch until they pass it. */
    @Test
    fun aRestoredRecordSendsUntilItPassesTheWatchsNumber() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.confirmed(X)
            s.confirmed(Y)
            val snapshot = s.memory.record
            s.confirmed(Z)
            s.confirmed(X)
            s.memory.record = snapshot
            s.restart()
            s.current = Z
            assertEquals(SENT, s.ask())
            if (confirms) s.land(s.last.seq) else s.landContext() // number 3: stale on the watch
            assertTrue(s.watch[0].holds(X), "the watch holds number 4")
            assertTrue(s.quiet(), "confirms $confirms: its echo of 4 lifts the numbers")
            assertEquals(5, s.last.seq)
        }
    }

    /**
     * n22 and N3 (amendment A4): a record restored from a snapshot taken at number 1 uses number 2
     * again, for Z, while the watch holds Y under number 2: it drops Z, since 2 is not greater, and
     * its handshake echoes 2 with Y. That is no confirmation: Z goes under number 3. (Before that
     * echo, a reschedule on the iPhone, which confirmed 2 on its send, would be the transient the
     * design check accepts: no phone-side rule can see it.)
     */
    @Test
    fun aNumberUsedAgainForAnotherFeedIsNoConfirmation() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.confirmed(X)
            val snapshot = s.memory.record
            s.confirmed(Y)
            s.memory.record = snapshot
            s.restart()
            s.current = Z
            assertEquals(SENT, s.ask())
            assertEquals(2, s.last.seq, "number 2 again")
            if (confirms) s.land(s.last.seq) else s.landContext()
            assertTrue(s.watch[0].holds(Y), "dropped: not greater")
            assertEquals(SENT, s.serve(s.handshakeOf()), "confirms $confirms: number 2 named Y, not Z")
            assertEquals(3, s.last.seq)
            assertTrue(s.quiet(), "confirms $confirms")
        }
    }

    /**
     * N1 (amendment A3): an iPhone with two watches; w1 got an older feed while it was worn, w0 then
     * confirmed B; the user switches back to w1 while the iPhone app is not running, so no
     * `WATCH_CHANGED` reaches it. The launch's `WATCH_APPEARED` is not damped: the confirmation was
     * w0's.
     */
    @Test
    fun anIphoneSwitchedToTheOtherWatchWhileTheAppWasNotRunningSendsTheFeed() = runTest {
        val s = Script(this, confirms = false, watches = 2)
        s.confirmed(X, 0)
        s.session = 1
        assertEquals(SENT, s.ask(WATCH_APPEARED))
        s.landContext(1) // w1 holds X under number 2
        s.session = 0
        assertEquals(SENT, s.ask(WATCH_APPEARED))
        s.landContext(0)
        s.current = Y
        assertEquals(SENT, s.ask())
        s.landContext(0) // w0 holds Y
        s.restart() // the app not running
        s.session = 1 // switched to w1, no WATCH_CHANGED told
        assertEquals(SENT, s.ask(WATCH_APPEARED), "the launch: w1 is not known to hold Y")
        assertTrue(s.quiet(listOf(1)))
    }

    /** N2: Quick Start carries the record but not the system's pending context, and new watch keys. */
    @Test
    fun aQuickStartedIphoneSendsTheFeedToTheWatchItMovedWith() = runTest {
        val s = Script(this, confirms = false)
        s.confirmed(X)
        s.current = Y
        assertEquals(SENT, s.ask()) // handed over, not landed
        s.loseContext() // the new phone's WatchConnectivity has none
        s.keys[0] = "k0-new" // the watch directory is the new phone's
        s.restart()
        assertEquals(SENT, s.ask(WATCH_APPEARED))
        assertTrue(s.quiet())
    }

    /**
     * N2b: Quick Start carries the record and the watch key too (out of the documented platform).
     * The launch is damped against the record while the watch holds X: the transient the design
     * check accepts. The watch's own handshake heals it.
     */
    @Test
    fun aQuickStartThatCarriedTheWatchKeyIsHealedByTheWatchsHandshake() = runTest {
        val s = Script(this, confirms = false)
        s.confirmed(X)
        s.current = Y
        assertEquals(SENT, s.ask())
        s.loseContext()
        s.restart()
        s.acceptedTransient = true // the context it handed over never arrives, and nothing says so
        assertEquals(UNCHANGED, s.ask(WATCH_APPEARED), "the transient: the record says the watch has Y")
        s.acceptedTransient = false
        assertEquals(SENT, s.serve(s.handshakeOf()), "the watch names X: healed")
        assertTrue(s.quiet())
    }

    /** A changed watch's `WATCH_CHANGED`, here Task 7's harness `send`: the feed goes again, and lands. */
    @Test
    fun aChangedWatchIsSentTheFeedAgain() = runTest {
        for (confirms in links) {
            val s = Script(this, confirms)
            s.confirmed(X)
            assertEquals(SENT, s.ask(WATCH_CHANGED))
            assertTrue(s.quiet(), "confirms $confirms")
            assertEquals(UNCHANGED, s.ask(), "confirms $confirms")
        }
    }
}
