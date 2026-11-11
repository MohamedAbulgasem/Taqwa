package world.taqwa.app.watchfeed

import world.taqwa.app.watch.WatchDelivery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The phone's one record of its watch (Ruling 50 item 3, as the design checks amended it), as pure
 * rules: what makes an ask `UNCHANGED`, what a send writes down before the link is called, and what
 * the watch's echo does to the record.
 */
class WatchFeedRecordTest {

    private val origin = "0123456789abcdef"
    private val x = "000000000000000a"
    private val y = "000000000000000b"
    private val times = "00000000000000ce" // the times alone of an over-limit feed
    private val w = "00000000000000fe" // that feed

    private fun fresh() = WatchFeedRecord(origin)

    private fun WatchFeedRecord.echo(seq: Long, fingerprint: String, from: String? = null, session: String? = from) =
        echoed(WatchDelivery(origin, seq, fingerprint), from, session)

    @Test
    fun aNewRecordKnowsOfNothing() {
        assertFalse(fresh().holds(x, key = null))
        assertEquals(0, fresh().lastSeq)
    }

    /** Item 1: each send takes the next number, written down with what it stands for and carries. */
    @Test
    fun aSendTakesTheNextNumber() {
        val first = fresh().sending(standsFor = x, sent = x)
        assertEquals(1, first.lastSeq)
        assertEquals(x, first.lastStandsFor)
        assertEquals(x, first.lastSent)
        assertEquals(WatchDelivery(origin, 1, x), first.lastDelivery)
        val second = first.sending(standsFor = w, sent = times)
        assertEquals(2, second.lastSeq)
        assertEquals(WatchDelivery(origin, 2, times), second.lastDelivery)
        assertFalse(second.holds(w, key = null), "a send alone confirms nothing")
    }

    /** Item 3: `UNCHANGED` exactly when the watch has echoed the latest delivery, of this very feed. */
    @Test
    fun theEchoOfTheLatestDeliveryConfirmsIt() {
        val sent = fresh().sending(x, x)
        val echoed = sent.echo(1, x)
        assertTrue(echoed.holds(x, key = null))
        assertFalse(echoed.holds(y, key = null), "another feed is sent")
        assertFalse(echoed.sending(y, y).holds(y, key = null), "a later send is not confirmed yet")
    }

    /**
     * Item 3 as amended (A1/A1b): the confirmation is the number named by the last echo of the
     * phone's own origin, lower or higher. A watch only moves forward within one origin, but a
     * second watch, a wiped one or another install can echo a lower number: it is taken in, and
     * costs one more send at most.
     */
    @Test
    fun theLastEchoCountsLowerOrHigher() {
        val two = fresh().sending(y, y).sending(x, x)
        val atTwo = two.echo(2, x)
        assertTrue(atTwo.holds(x, key = null))
        val atOne = atTwo.echo(1, y)
        assertEquals(1, atOne.confirmedSeq)
        assertFalse(atOne.holds(x, key = null), "a lower echo is taken in, never ignored")
        assertTrue(atOne.echo(2, x).holds(x, key = null))
    }

    /** An echo of nothing, or of another origin, is a forget: nothing confirmed, a number set aside (A2). */
    @Test
    fun anEchoOfNothingOrOfAnotherOriginClearsIt() {
        val confirmed = fresh().sending(x, x).echo(1, x)
        for (after in listOf(
            confirmed.echoed(null, from = null, session = null),
            confirmed.echoed(WatchDelivery("1111111111111111", 1, x), from = null, session = null),
        )) {
            assertEquals(confirmed.forgotten(), after)
            assertNull(after.confirmedSeq)
            assertFalse(after.holds(x, key = null))
        }
    }

    /**
     * D1 (Ruling 50's review, round 1, amendment A2): once the watch has said it holds nothing, an
     * echo it sent before that (an acknowledgement still on its way when it was wiped) confirms
     * nothing: the forget set number 2 aside, so the late echo of number 1 is below the latest.
     * Nothing confirms again until a send after the forget is echoed.
     */
    @Test
    fun anEchoSentBeforeAForgetConfirmsNothing() {
        val sent = fresh().sending(x, x) // X under number 1, stored; its echo still on its way
        val forgotten = sent.echoed(null, from = null, session = null) // the wiped watch's handshake
        val late = forgotten.echo(1, x)
        assertFalse(late.holds(x, key = null), "a wrong UNCHANGED: the watch holds nothing")
        val again = late.sending(x, x)
        assertEquals(3, again.lastSeq)
        assertTrue(again.echo(3, x).holds(x, key = null))
    }

    /** Item 5: a record restored from a backup despite item 1; the watch's echo lifts the numbers. */
    @Test
    fun anEchoAboveTheLastNumberLiftsIt() {
        val behind = fresh().sending(x, x) // lastSeq 1, while the watch holds number 7
        val lifted = behind.echo(7, y)
        assertEquals(7, lifted.lastSeq)
        assertNull(lifted.lastStandsFor)
        assertFalse(lifted.holds(y, key = null))
        assertFalse(lifted.holds(x, key = null))
        assertEquals(8, lifted.sending(x, x).lastSeq, "the next send goes above what the watch holds")
    }

    /**
     * Ruling 52 item 3: an echo numbered above half a `Long` counts as another install's, a forget,
     * so neither the send nor the forget after it can run the numbers past a `Long` (an echo at
     * `Long.MAX_VALUE` once lifted `lastSeq` there, and every later send overflowed). At half a
     * `Long` it is still this install's and lifts the numbers as item 5 does, with room above.
     */
    @Test
    fun anEchoNumberedAboveHalfALongIsAnotherInstalls() {
        val confirmed = fresh().sending(x, x).echo(1, x)
        for (seq in listOf(Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE)) {
            val after = confirmed.echo(seq, x)
            assertEquals(confirmed.forgotten(), after, "number $seq")
            assertEquals(WatchDelivery(origin, 3, x), after.sending(x, x).lastDelivery, "number $seq")
        }
        val atHalf = confirmed.echo(Long.MAX_VALUE / 2, x)
        assertEquals(Long.MAX_VALUE / 2, atHalf.lastSeq, "lifted, as item 5 lifts")
        assertEquals(WatchDelivery(origin, Long.MAX_VALUE / 2 + 2, x), atHalf.forgotten().sending(x, x).lastDelivery)
    }

    /**
     * Amendment A4: an echo of the last number with another fingerprint is a number used twice (a
     * record restored from an older snapshot), so it confirms nothing and the next ask sends.
     */
    @Test
    fun anEchoOfTheLastNumberWithAnotherFeedIsNoConfirmation() {
        val sent = fresh().sending(x, x).sending(y, y)
        val reused = sent.echo(2, x)
        assertFalse(reused.holds(y, key = null))
        assertFalse(reused.holds(x, key = null))
        assertEquals(3, reused.sending(y, y).lastSeq)
    }

    /** The times alone after a `TOO_LARGE` stand for the full feed, and carry their own fingerprint. */
    @Test
    fun theTimesAloneStandForTheFeedTheLinkRefused() {
        val record = fresh().sending(standsFor = w, sent = w).sending(standsFor = w, sent = times)
        val echoed = record.echo(2, times)
        assertTrue(echoed.holds(w, key = null))
        assertFalse(echoed.holds(times, key = null), "item 3 compares what the send stood for")
        assertFalse(record.echo(2, w).holds(w, key = null), "the number carried the times alone, not the feed")
    }

    /** Item 4: on a link that does not confirm, a hand-over confirms its number for the watch it went to. */
    @Test
    fun aHandOverConfirmsItsNumberForThatWatch() {
        val handed = fresh().sending(x, x).handedOver(key = "k0")
        assertEquals(1, handed.confirmedSeq)
        assertTrue(handed.holds(x, key = "k0"))
        assertFalse(handed.holds(x, key = "k1"), "another watch (amendment A3)")
        assertFalse(handed.holds(x, key = null))
    }

    /**
     * Amendment A3: an echo counts for the watch it came from, and an echo from a watch that is not
     * the session's is ignored.
     */
    @Test
    fun anEchoCountsForTheWatchItCameFrom() {
        val sent = fresh().sending(x, x)
        val fromK1 = sent.echo(1, x, from = "k1", session = "k1")
        assertTrue(fromK1.holds(x, key = "k1"))
        assertFalse(fromK1.holds(x, key = "k0"))
        val confirmed = sent.handedOver(key = "k0")
        assertSame(confirmed, confirmed.echoed(null, from = "k1", session = "k0"), "not the session's watch: ignored")
        assertSame(confirmed, confirmed.echoed(null, from = null, session = "k0"))
    }

    /**
     * Ruling 52 item 9: on Android a send goes to every nearby watch with Taqwa, so the session's
     * watches are their node ids, and the watch key is the node id of the watch whose echo was
     * taken in. Its confirmation counts only while that watch is among them: a watch switched to
     * never inherits it. A second watch beside it is the known limit of plan 2's call 1, which its
     * own handshake heals.
     */
    @Test
    fun aConfirmationCountsOnlyWhileItsWatchIsAmongTheSessions() {
        val confirmed = fresh().sending(x, x).echoed(WatchDelivery(origin, 1, x), from = "node-a", keys = setOf("node-a"))
        assertEquals(1, confirmed.confirmedSeq)
        assertEquals("node-a", confirmed.confirmedFor)
        assertTrue(confirmed.holds(x, keys = setOf("node-a")))
        assertTrue(confirmed.holds(x, keys = setOf("node-a", "node-b")), "a second watch beside it: the known limit")
        assertFalse(confirmed.holds(x, keys = setOf("node-b")), "a watch switched to never inherits it")
        assertFalse(confirmed.holds(x, keys = emptySet()), "no watch nearby")
        assertFalse(confirmed.holds(y, keys = setOf("node-a")), "another feed is sent")
    }

    /** Ruling 52 item 9, as A3 is on the iPhone: an echo from a watch outside the session's is ignored. */
    @Test
    fun anEchoFromAWatchOutsideTheSessionsIsIgnored() {
        val confirmed = fresh().sending(x, x).echoed(WatchDelivery(origin, 1, x), from = "node-a", keys = setOf("node-a"))
        assertSame(confirmed, confirmed.echoed(null, from = "node-b", keys = setOf("node-a")), "not nearby: ignored")
        assertSame(confirmed, confirmed.echoed(WatchDelivery(origin, 1, x), from = "node-b", keys = emptySet()))
        assertSame(confirmed, confirmed.echoed(null, from = null, keys = setOf("node-a")), "no node named")
        val fromB = confirmed.echoed(null, from = "node-b", keys = setOf("node-a", "node-b"))
        assertNull(fromB.confirmedSeq, "a nearby watch that holds nothing forgets the confirmation")
        assertFalse(fromB.holds(x, keys = setOf("node-a", "node-b")))
        val bConfirms = fromB.sending(x, x).echoed(WatchDelivery(origin, 3, x), from = "node-b", keys = setOf("node-a", "node-b"))
        assertEquals("node-b", bConfirms.confirmedFor)
        assertTrue(bConfirms.holds(x, keys = setOf("node-a", "node-b")))
    }

    /**
     * Item 6 and amendment A2: a changed watch forgets the confirmation, and sets the next number
     * aside, standing for nothing, so no echo sent before the forget can name the latest number.
     * The number is never sent: the next send takes the one after it.
     */
    @Test
    fun aChangedWatchForgetsTheConfirmation() {
        val forgotten = fresh().sending(x, x).handedOver("k0").forgotten()
        assertNull(forgotten.confirmedSeq)
        assertNull(forgotten.confirmedFor)
        assertFalse(forgotten.holds(x, key = "k0"))
        assertEquals(2, forgotten.lastSeq, "number 2 set aside")
        assertNull(forgotten.lastStandsFor)
        assertNull(forgotten.lastSent)
        assertNull(forgotten.lastDelivery, "a number set aside is never sent")
        assertFalse(forgotten.echo(1, x, from = "k0").holds(x, key = "k0"), "an echo from before the forget")
        assertEquals(WatchDelivery(origin, 3, x), forgotten.sending(x, x).lastDelivery)
    }
}
