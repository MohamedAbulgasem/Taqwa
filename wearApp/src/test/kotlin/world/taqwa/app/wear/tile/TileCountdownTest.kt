package world.taqwa.app.wear.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.complication.DynamicEvaluator

/**
 * The Tile's countdown and its sentence (erratum p3 #6): for each entry a chain of the rules' own
 * words, one for each minute the entry can be shown at, which the watch's system evaluates every
 * second with the app's process dead. They are evaluated here at the edges of every minute and at
 * several milliseconds into each second, as the face's are (`CountdownExpressionTest`), over the
 * entry's time, the margin before it, and as long after it as the renderer may still show it,
 * since it changes entries late (review round 3).
 */
class TileCountdownTest {

    private val feeds: List<WatchFeed> = listOf(
        TestFeeds.sample,
        TestFeeds.other("en12"),
        TestFeeds.other("ar-sa"),
        TestFeeds.other("ar-ly12"),
        // An Urdu phone's digits, which no sample carries.
        TestFeeds.sample.copy(ui = TestFeeds.sample.ui.copy(digitZero = '۰')),
    )

    /**
     * The instants of [entry] worth reading: every edge of every minute of the time left, from the
     * margin before it to the last second the renderer may still show it.
     */
    private fun instants(entry: TileEntry, next: TileNext): List<Long> {
        val until = assertNotNull(entry.until)
        val shown = assertNotNull(entry.shownUntil)
        val earliest = (entry.from - TilePlan.MARGIN_SECONDS).coerceAtLeast(next.startAt)
        return (earliest..shown).filter { s ->
            val intoMinute = Math.floorMod(next.endAt - s, 60L)
            intoMinute == 0L || intoMinute == 1L || intoMinute == 59L || s == entry.from || s == until - 1 || s == until || s == shown
        }
    }

    /**
     * Whether [entry] says the start's time at [s] rather than a countdown: from the start on, and
     * in the last minute unless it is the entry that ends at the start (review round 3).
     */
    private fun saysTheTime(entry: TileEntry, next: TileNext, s: Long): Boolean {
        val left = next.endAt - s
        return left <= 0 || (left < 60 && entry.until != next.endAt)
    }

    @Test
    fun theCountdownReadsAsTheRulesDoAtEveryMinutesEdge() {
        for (feed in feeds) {
            for (entry in TilePlan.entries(feed, false, TestFeeds.at(0, 14, 58)).take(40)) {
                val next = entry.next?.takeIf { it.minutes.isNotEmpty() } ?: continue
                val text = TileCountdown.text(next).toDynamicStringProto()
                for (s in instants(entry, next)) {
                    for (millis in listOf(0, 1, 500, 999)) {
                        assertEquals(
                            if (saysTheTime(entry, next, s)) directed(next.clock, next.rtl) else WatchRules.compactCountdown(feed, next.endAt - s),
                            DynamicEvaluator(s, millis).string(text),
                            "${feed.ui.language} ${feed.ui.digitZero} at $s and $millis ms",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theSentenceReadsAsTheRulesDoAtEveryMinutesEdge() {
        for (feed in feeds) {
            for (entry in TilePlan.entries(feed, false, TestFeeds.at(0, 14, 58)).take(40)) {
                val next = entry.next?.takeIf { it.minutes.isNotEmpty() } ?: continue
                val spoken = assertNotNull(TileCountdown.spoken(next)).toDynamicStringProto()
                for (s in instants(entry, next)) {
                    for (millis in listOf(0, 999)) {
                        assertEquals(
                            if (saysTheTime(entry, next, s)) next.clockSentence else WatchRules.spokenCountdown(feed, next.name, next.spokenClock, next.endAt - s),
                            DynamicEvaluator(s, millis).string(spoken),
                            "${feed.ui.language} at $s and $millis ms",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun pastTheLastSecondItsEntryMayBeShownTheChainSaysTheStartsTime() {
        // Ruling 65 item 3: a renderer later than shownUntil (deep idle held one on emulator-5558
        // 2 min 16 s past it, review round 4) still says something true. Past its last link a
        // chain said its last countdown words, "in 58 min" at 15:16:40 where the rules say 53;
        // now, once fewer minutes are left than its first link's, and from the start on, it says
        // the start's time, call 6's form. Every moving entry from 14:58, and what the one before
        // each change draws after it, read from just past its shownUntil to ten minutes past the
        // start.
        for (feed in feeds) {
            for (entry in TilePlan.entries(feed, false, TestFeeds.at(0, 14, 58)).take(40).flatMap { listOfNotNull(it, it.after) }) {
                val next = entry.next?.takeIf { it.minutes.isNotEmpty() } ?: continue
                val shown = assertNotNull(entry.shownUntil)
                val text = TileCountdown.text(next).toDynamicStringProto()
                val spoken = assertNotNull(TileCountdown.spoken(next)).toDynamicStringProto()
                val fewest = next.minutes.first().minutesLeft
                val marks = (shown + 1..maxOf(shown, next.endAt) + 600L).filter { s ->
                    val intoMinute = Math.floorMod(next.endAt - s, 60L)
                    intoMinute == 0L || intoMinute == 1L || intoMinute == 59L || s - shown <= 3L || s == next.endAt
                }
                for (s in marks) {
                    val left = next.endAt - s
                    val counted = !saysTheTime(entry, next, s) && left / 60L >= fewest
                    for (millis in listOf(0, 999)) {
                        val at = "${feed.ui.language} ${feed.ui.digitZero}, the entry from ${entry.from} shown to $shown, at $s and $millis ms"
                        assertEquals(if (counted) WatchRules.compactCountdown(feed, left) else directed(next.clock, next.rtl), DynamicEvaluator(s, millis).string(text), at)
                        assertEquals(
                            if (counted) WatchRules.spokenCountdown(feed, next.name, next.spokenClock, left) else next.clockSentence,
                            DynamicEvaluator(s, millis).string(spoken),
                            at,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun underAMinuteItSaysUnderAMinuteAndFromTheStartTheStartsTime() {
        // The last minute's own entry, from 16:08 (review round 2). A renderer that has not yet
        // changed entries once the start has come never says a countdown that has run out: the
        // line says the start's time (review round 3; on emulator-5558 "in under a minute" stood
        // on from 19:04:00 to 19:05:08).
        val last = TilePlan.entries(TestFeeds.sample, false, TestFeeds.at(0, 16, 5)).first { it.until == it.next?.endAt }
        assertEquals(TestFeeds.at(0, 16, 8), last.from)
        val next = assertNotNull(last.next)
        val text = TileCountdown.text(next).toDynamicStringProto()
        val spoken = assertNotNull(TileCountdown.spoken(next)).toDynamicStringProto()

        assertEquals("in under a minute", DynamicEvaluator(TestFeeds.at(0, 16, 9, 30)).string(text))
        assertEquals("in under a minute", DynamicEvaluator(TestFeeds.at(0, 16, 9, 59), 999).string(text))
        for (s in listOf(TestFeeds.at(0, 16, 10), TestFeeds.at(0, 16, 10, 20), TestFeeds.at(0, 16, 12))) {
            assertEquals("\u200E16:10", DynamicEvaluator(s).string(text))
            assertEquals("Asr, 16:10", DynamicEvaluator(s).string(spoken))
        }
    }

    @Test
    fun theStartsTimeIsSetInTheFeedsDirection() {
        // Where a chain says the start's time, a mark of the feed's direction leads it, so that a
        // text view sets a time of digits alone, which has no direction of its own, as the feed
        // runs, and aligns it against the name (TileLayout's line). The sentence has none.
        for ((feed, mark) in listOf(TestFeeds.sample to '\u200E', TestFeeds.other("ar-sa") to '\u200F')) {
            val last = TilePlan.entries(feed, false, TestFeeds.at(0, 16, 5)).first { it.until == it.next?.endAt }
            val next = assertNotNull(last.next)
            val atTheStart = DynamicEvaluator(TestFeeds.at(0, 16, 10))
            assertEquals("$mark${next.clock}", atTheStart.string(TileCountdown.text(next).toDynamicStringProto()), feed.ui.language)
            assertEquals(next.clockSentence, atTheStart.string(assertNotNull(TileCountdown.spoken(next)).toDynamicStringProto()), feed.ui.language)
        }
    }

    @Test
    fun aFeedWithoutSpokenWordsHasNoMovingSentence() {
        val unspoken = TestFeeds.sample.copy(ui = TestFeeds.sample.ui.copy(spoken = null))
        val next = assertNotNull(TilePlan.entries(unspoken, false, TestFeeds.at(0, 14, 58)).first().next)

        assertNull(TileCountdown.spoken(next))
    }

    @Test
    fun aChainIsNoLongerThanItsMinutes() {
        // Five dynamic nodes a minute (the condition, its comparison and the time it reads: the
        // duration's minutes, the duration, the platform's clock), the two joins of the words
        // every link shares, and one link more for the start's time past them (Ruling 65 item 3);
        // the words and numbers are fixed.
        val next = assertNotNull(TilePlan.entries(TestFeeds.sample, false, TestFeeds.at(0, 15, 0)).first { it.from == TestFeeds.at(0, 15, 1) }.next)
        val nodes = DynamicNodes.of(TileCountdown.text(next).toDynamicStringProto())
        val (head, tail) = TileCountdown.sharedEnds(next.minutes.map { it.text })
        val joins = listOf(head, tail).count { it.isNotEmpty() }

        assertEquals(5 * next.minutes.size + joins, nodes.dynamic)
        // Counting the fixed values as well, as Google counts them: three more a link (the start,
        // the minutes and the words), the last link's words, and the words each join adds.
        assertEquals(8 * next.minutes.size + 1 + 2 * joins, nodes.all)
        // A five-minute entry, the half minute before it and the 3 min 55 s after it that the
        // renderer may still show it in (review round 4): ten minutes, so nine links, and the
        // start's time past them.
        assertEquals(10, next.minutes.size)
        assertEquals((60..69).toList(), next.minutes.map { it.minutesLeft })
    }

    @Test
    fun theWordsEveryLinkSharesAreSaidOnce() {
        assertEquals("in 1 h " to " min", TileCountdown.sharedEnds(listOf("in 1 h 12 min", "in 1 h 9 min", "in 1 h 59 min")))
        // A head or tail never splits a character of two UTF-16 units: here two emoji share their
        // first unit, and two others their last.
        assertEquals("ab" to "x", TileCountdown.sharedEnds(listOf("ab\uD83D\uDE00x", "ab\uD83D\uDE01x")))
        assertEquals("a" to "", TileCountdown.sharedEnds(listOf("a\uD83D\uDE00", "a\uD83E\uDE00")))
        // Words that are all the same, or one alone, share nothing a chain needs.
        assertEquals("" to "", TileCountdown.sharedEnds(listOf("in under a minute")))
        // A head and a tail never overlap in the shortest of the words.
        val (h, t) = TileCountdown.sharedEnds(listOf("aa", "aaa"))
        assertTrue(h.length + t.length <= 2)
    }
}
