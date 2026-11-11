package world.taqwa.app.wear.tile

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchTime
import world.taqwa.app.watch.of
import world.taqwa.app.watch.words
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.today.LitRule
import world.taqwa.app.wear.today.dayRows
import world.taqwa.app.wear.today.spokenSeparator

/**
 * The Tile as the renderer shows it when it changes entries late (review round 3): on
 * emulator-5558 it moved on from an entry as late as three quarters of the entry's length past its
 * end, so the countdown stood still on its entry's last minute ("in 59 min" from 15:10:47 to
 * 15:13:41, "in 1 h 9 min" from 19:15:07 to 19:15:49) and at a start the prayer before it stayed
 * lit beside "in under a minute" (19:04:00 to 19:05:08). Here the renderer's rule is played out
 * ([changes]) at its latest and on time, for Tiles asked for at the moments the reviews saw and
 * around each start of an afternoon and evening, and what it shows ([Seen]) is read every few
 * seconds through the moving hours against what the rules say then.
 */
class TileLateChangeTest {

    private val screen = TileScreen(227, 227, 2f, 1f, true, true)

    private val feeds: List<WatchFeed> = listOf(TestFeeds.sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.phone("capetown-ur24"))

    /** The moments the reviews asked at, and either side of each start and of the day's turn. */
    private val asked: List<Long> = listOf(
        TestFeeds.at(0, 14, 58),
        TestFeeds.at(0, 15, 5, 3),
        TestFeeds.at(0, 15, 9, 30),
        TestFeeds.at(0, 16, 0),
        TestFeeds.at(0, 16, 5),
        TestFeeds.at(0, 16, 7, 30),
        TestFeeds.at(0, 16, 8, 30),
        TestFeeds.at(0, 18, 50),
        TestFeeds.at(0, 18, 56),
        TestFeeds.at(0, 19, 2, 5),
        TestFeeds.at(0, 19, 9),
        TestFeeds.at(0, 23, 40),
        TestFeeds.at(1, 5, 50),
    )

    @Test
    fun howeverLateTheRendererChangesEntriesTheTileSaysWhatIsTrue() {
        for (feed in feeds) {
            for (now in asked) {
                for (late in listOf(true, false)) {
                    val entries = TilePlan.entries(feed, false, now)
                    val timeline = TileLayout.timeline(entries, screen, feed.ui.rtl, false, PACKAGE, TestMeasures.ROUGH).toProto()
                    val seen = changes(timeline, now * 1_000L, (now + TilePlan.MOVING_SECONDS) * 1_000L, late)
                    for (s in seconds(feed, now, seen)) {
                        val shown = showing(seen, s * 1_000L)
                        // Past the moving hours the line says a start's time, and a missed request is all that leaves it.
                        if (entries[shown].notice == null && entries[shown].next!!.minutes.isEmpty()) break
                        for (millis in listOf(0, 999)) {
                            val what = Seen(timeline.timelineEntriesList[shown].layout.root, s * 1_000L + millis)
                            assertTrue(isTrue(feed, s, what), "${feed.ui.language}, asked at ${clock(now)}, late $late: at ${clock(s)}+${millis}ms it shows ${what.texts} ${what.states}")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aRendererThatCannotHideAnElementSaysTheStartsTimeForAMinuteAndAHalfAtMost() {
        // Below schema 1.3 an entry cannot draw what comes after its change, so the one the
        // renderer may still show then says the start's time once it has come, never a countdown
        // that has run out, and its rows stay as they were until the renderer moves on: at most
        // 115 s, since that entry is two minutes long. Every other moment is as above.
        val old = screen.copy(hides = false)
        for (feed in feeds) {
            for (now in asked) {
                for (late in listOf(true, false)) {
                    val entries = TilePlan.entries(feed, false, now)
                    val timeline = TileLayout.timeline(entries, old, feed.ui.rtl, false, PACKAGE, TestMeasures.ROUGH).toProto()
                    val seen = changes(timeline, now * 1_000L, (now + TilePlan.MOVING_SECONDS) * 1_000L, late)
                    val changesOfTheRows = changesOfTheRows(feed)
                    for (s in seconds(feed, now, seen)) {
                        val shown = showing(seen, s * 1_000L)
                        if (entries[shown].notice == null && entries[shown].next!!.minutes.isEmpty()) break
                        val what = Seen(timeline.timelineEntriesList[shown].layout.root, s * 1_000L, hides = false)
                        val lagging = changesOfTheRows.any { c -> s - c in 0L..LAG_SECONDS && isTrue(feed, s, what, asOf = c - 1) }
                        assertTrue(isTrue(feed, s, what) || lagging, "${feed.ui.language}, asked at ${clock(now)}, late $late: at ${clock(s)} it shows ${what.texts} ${what.states}")
                    }
                }
            }
        }
    }

    @Test
    fun aRendererLaterThanThePlanAllowsForNeverSaysACountdownThatHasPassed() {
        // Ruling 65 item 3. On emulator-5558 deep idle held the renderer's change 2 min 16 s past
        // the last second the plan allows for (review round 4), and past its last link a chain
        // said its last countdown words: "ASR in 58 min" at 15:16:40, where the rules say 53. Such
        // a renderer may still show the rows from before a change (the plan's bound is what keeps
        // them right), but the line says the start's time once its chain has run out: whatever
        // the line shows is the start's time or the countdown to it as the rules say it then.
        for (feed in feeds) {
            for (now in asked) {
                for (beyond in listOf(136_000L, 600_000L)) {
                    val entries = TilePlan.entries(feed, false, now)
                    val timeline = TileLayout.timeline(entries, screen, feed.ui.rtl, false, PACKAGE, TestMeasures.ROUGH).toProto()
                    val seen = changes(timeline, now * 1_000L, (now + TilePlan.MOVING_SECONDS) * 1_000L, late = true, beyondMillis = beyond)
                    for (s in seconds(feed, now, seen)) {
                        val shown = showing(seen, s * 1_000L)
                        if (entries[shown].notice == null && entries[shown].next!!.minutes.isEmpty()) break
                        for (millis in listOf(0, 999)) {
                            val what = Seen(timeline.timelineEntriesList[shown].layout.root, s * 1_000L + millis)
                            assertTrue(theLineIsTrue(feed, entries[shown], s, what), "${feed.ui.language}, asked at ${clock(now)}, ${beyond / 1_000L} s beyond: at ${clock(s)}+${millis}ms it shows ${what.texts.take(2)}, said '${what.said.firstOrNull()}'")
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether the line [what] shows of [entry] at [s] is true however late it is shown: the start's
     * time, or the countdown to that start as the rules say it at [s], and its sentence the same.
     */
    private fun theLineIsTrue(feed: WatchFeed, entry: TileEntry, s: Long, what: Seen): Boolean {
        val side = entry.after?.takeIf { s >= entry.until!! } ?: entry
        side.notice?.let { return what.texts == listOf(it) }
        val next = side.next!!
        val left = next.endAt - s
        val words = setOf(next.clock) + if (left > 0) setOf(WatchRules.compactCountdown(feed, left)) else emptySet()
        val sentences = setOf(next.clockSentence) + if (left > 0) setOf(WatchRules.spokenCountdown(feed, next.name, next.spokenClock, left)) else emptySet()
        val line = what.texts.take(2).toSet()
        return line.contains(next.label) && (line - next.label).single() in words && what.said.first() in sentences
    }

    /** Every instant the rows or the next start change: each of the day's six times and each turn of the date. */
    private fun changesOfTheRows(feed: WatchFeed): List<Long> =
        feed.days.flatMap { day -> with(day.times) { listOf(fajr, sunrise, dhuhr, asr, maghrib, isha) } } + feed.days.indices.map { WatchRules.dayStart(feed, it) }

    /**
     * Whether [what] is what the rules say at [s], or, with [asOf], what they said at [asOf] with the
     * countdown read at [s] (an entry from before a change still shown after it): the line, its
     * sentence, the rows and their states.
     */
    private fun isTrue(feed: WatchFeed, s: Long, what: Seen, asOf: Long = s): Boolean {
        val moment = WatchRules.moment(feed, asOf) ?: return what.texts == listOf(feed.words().openOnPhone)
        val day = feed.days[moment.next.dayIndex]
        val time = WatchTime.of(moment.next.prayer)
        val name = day.names.of(time)
        val label = if (feed.ui.latinScript) name.uppercase(Locale.forLanguageTag(feed.ui.language)) else name
        val left = moment.next.at - s
        val spokenClock = WatchRules.spokenClock(day, time)
        val clock = WatchRules.clockText(day, time)
        // In the last minute an entry shown late may say the start's time in place of "in under a
        // minute", and from the start on that is all an entry from before it says.
        val words = if (left <= 0) setOf(clock) else setOf(WatchRules.compactCountdown(feed, left)) + if (left < 60) setOf(clock) else emptySet()
        val countdown = WatchRules.spokenCountdown(feed, name, spokenClock, left).takeIf { left > 0 }
        val sentences = setOfNotNull(countdown) + if (left < 60) setOf(name + spokenSeparator(feed.ui.rtl) + spokenClock) else emptySet()
        val rows = dayRows(feed, asOf, LitRule.GLANCE)
        val line = what.texts.take(2).toSet()
        return line.contains(label) && (line - label).single() in words &&
            what.said.first() in sentences &&
            what.said.subList(1, 7) == rows.map { it.spoken } &&
            what.states == rows.mapNotNull { it.state }
    }

    /**
     * The seconds worth reading from [now]: every fifth, each side of every change the renderer
     * makes, and each side of every start and turn of the date, through the moving hours.
     */
    private fun seconds(feed: WatchFeed, now: Long, seen: List<Pair<Long, Int>>): List<Long> {
        val end = now + TilePlan.MOVING_SECONDS
        val marks = (now until end step 5L).toSortedSet()
        for ((at, _) in seen) for (d in -1L..1L) marks += at / 1_000L + d
        for (day in feed.days) {
            val times = with(day.times) { listOf(fajr, sunrise, dhuhr, asr, maghrib, isha, day.midnight) }
            for (t in times) for (d in -1L..1L) marks += t + d
        }
        return marks.filter { it in now until end }
    }

    private fun clock(s: Long): String = java.time.Instant.ofEpochSecond(s).atZone(java.time.ZoneOffset.ofHours(2)).toLocalTime().toString()

    private companion object {
        const val PACKAGE = "world.taqwa.app"

        /** The longest a two-minute entry is still shown past its end: `TilePlan.shownUntil`'s floor of 105 s, and its slack. */
        const val LAG_SECONDS = 115L
    }
}
