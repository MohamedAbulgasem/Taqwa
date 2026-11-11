package world.taqwa.app.wear.tile

import androidx.annotation.StringRes
import java.util.Locale
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchMoment
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchTime
import world.taqwa.app.watch.of
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.noFeedLine
import world.taqwa.app.wear.today.LitRule
import world.taqwa.app.wear.today.RowModel
import world.taqwa.app.wear.today.dayRows
import world.taqwa.app.wear.today.spokenSeparator

/**
 * The Tile's lines (call 15), in the drawing's units with their caps (`gTile`). The Tile cannot
 * scroll, so its pitches stay the drawing's and every line is capped at 1.1: a larger font size
 * grows each line by a tenth at most.
 *
 * ProtoLayout has no size that fits its box, so a Tile line is drawn at the top of its range,
 * `range(fontScale).endInclusive`, with its `maxLines` and an ellipsis. Two lines are fitted on the
 * widths the watch measures before the Tile is sent (`TileMeasure`), each down to its floor: the
 * countdown line, whose label shrinks with it (`fitLine`), and the update (`fitUpdate`). The sizes
 * go to the renderer in sp, which it turns into pixels through the platform's own non-linear
 * font-scale tables, so a size is `WatchUnits.sp(units, Density(screenDensity, fontScale))`, the
 * pages' own call, whose inverse is Compose's copy of those tables. What is left over is the
 * difference between Compose's copy and the renderer's, small at the Tile's sizes: even the old
 * `units × unitDp / fontScale` would draw at most 1.6% large there (15.22 dp for a 14.98 dp
 * target, 12 units at a font scale of 1.1, measured on ui-unit 1.10.0). Task 9 Step 4's large-text
 * shot of the Tile is the renderer's own answer.
 */
internal object TileType {
    /** The next start's name, shrinking with the countdown ([countdown]'s floor over its size). */
    val label = Line(10.5f, 1.1f, floor = 7f)

    /**
     * The countdown, fitted with the label inside the chord at 40 (`fitLine`): "MAGHRIB in under a
     * minute", or an Indonesian or French countdown, is wider than the chord at the drawing's size.
     */
    val countdown = Line(12f, 1.1f, floor = 8f)
    val rowName = Line(12f, 1.1f)
    val rowTime = Line(12f, 1.1f)
    val sunrise = Line(10f, 1.1f)

    /** One line, cut with an ellipsis inside the round screen's chord at 184 (`WatchUnits.chordUnits`). */
    val city = Line(10.5f, 1.1f)

    /**
     * "Update Taqwa on your watch" in the city's place (call 5), in two lines at most: at the city's
     * 10.5 units the English line is about half as wide again as the chord there, so it is fitted
     * (`fitUpdate`) at the largest size down to 8 units at which it fits.
     */
    val update = Line(10.5f, 1.1f, maxLines = 2, floor = 8f)

    /** The notice alone (no start left, or no feed): "Open Taqwa on your phone", centred. */
    val notice = Line(15f, 1.1f, maxLines = 3)

    val all: List<Line> = listOf(label, countdown, rowName, rowTime, sunrise, city, update, notice)
}

/** The three parts of a row of the Tile, in the order an English feed draws them from the left. */
internal enum class RowPart { PIP, NAME, TIME }

/**
 * [parts], given in the order a left-to-right feed draws them, in the order the Tile's renderer has
 * to be handed them for the feed's direction ([rtl]) to be what is seen. A renderer lays a Row out
 * in its host's direction ([hostRtl]), and what is seen must follow the phone's. So the parts are
 * reversed when the two differ and kept when they agree: an Arabic feed on a host that mirrors is
 * handed the English order, which the renderer turns into the Arabic one itself. The renderer
 * measured lays a Row out left to right whatever the watch's language (Step 5's first check), so
 * the service passes `hostRtl = false` (`TodayTileService.HOST_RTL`).
 */
internal fun <T> inTileOrder(parts: List<T>, rtl: Boolean, hostRtl: Boolean): List<T> =
    if (rtl xor hostRtl) parts.reversed() else parts

/** A row's parts in the renderer's order ([inTileOrder]). */
internal fun rowOrder(rtl: Boolean, hostRtl: Boolean): List<RowPart> =
    inTileOrder(listOf(RowPart.PIP, RowPart.NAME, RowPart.TIME), rtl, hostRtl)

/**
 * One minute of a countdown as the Tile says it: [minutesLeft] whole minutes left (0 for under a
 * minute), the countdown [text] in the rules' compact words (`WatchRules.compactCountdown`, "in
 * 1h 12m", Mohamed's report of 9 November 2026) and the [spoken] sentence in the full ones
 * (`WatchRules.spokenCountdown`), null for a feed with no spoken words; or, where the
 * entry says the start's time instead (the last minute of an entry that does not end at the start,
 * and past every minute it counts, `TileCountdown`), that time as the line draws it and as a
 * sentence says it ("Asr, 16:10").
 */
internal data class CountdownStep(val minutesLeft: Int, val text: String, val spoken: String?)

/**
 * The next start as one entry of the Tile draws it: [label], the name as the line writes it (in
 * capitals under a Latin interface, cased in the feed's language, as Today's ring is); [name], as a
 * sentence says it; [clock], the start's time as `WatchRules.clockText` draws it, and
 * [spokenClock], as `WatchRules.spokenClock` says it, of the start's own day; and the period from
 * [startAt] to [endAt] (epoch seconds). [clock] and [clockSentence] are call 6's form ("ASR 16:10",
 * said "Asr, 16:10"), true however late it is shown: what the line says past the moving hours, past
 * every minute its chain counts, and wherever a renderer drops the moving values (Ruling 65).
 *
 * [minutes] is every minute the entry can be shown at, from the margin before it
 * ([TilePlan.MARGIN_SECONDS]) to the last second the renderer may still show it
 * ([TileEntry.shownUntil]), each in the rules' words: the Tile's countdown and its sentence are
 * chains of these, which the watch's system moves on by itself (`TileCountdown`, erratum p3 #6).
 * Under a minute is said only by the entry that ends at the start ([TilePlan.LAST_MINUTE_SECONDS]):
 * an earlier one the renderer is late to change says the start's [clock] in that minute instead,
 * so its line keeps the drawing's size. From the start on, and once fewer minutes are left than
 * the first of [minutes] counts, the chains say the start's [clock] (`TileCountdown`, Ruling 65
 * item 3). [minutes] is empty past the timeline's first [TilePlan.MOVING_SECONDS], where the line
 * says the start's [clock] in the countdown's place.
 */
internal data class TileNext(
    val label: String,
    val name: String,
    val clock: String,
    val spokenClock: String,
    val startAt: Long,
    val endAt: Long,
    val minutes: List<CountdownStep>,
    /** Whether the feed's language is written right to left (`ui.rtl`), for [clockSentence]'s comma. */
    val rtl: Boolean,
) {
    /**
     * The start's time as a sentence says it, as Today's rows join a name and its time: "Asr, 16:10",
     * and "العصر، ١٦:١٠" with the Arabic comma in Arabic and Urdu (M8 point A5).
     */
    val clockSentence: String get() = name + spokenSeparator(rtl) + spokenClock
}

/**
 * What the Tile draws from [from] until [until] (epoch seconds; null for the last entry, which runs
 * on): the countdown line to [next], the day's [rows] by the widgets' rule (six, or five where the
 * phone hides Sunrise, `dayRows`), and [city], whole (the layout cuts it, never the plan). [update]
 * is "Update Taqwa on your watch" when a newer format has been seen, drawn in the city's place
 * (call 5). [notice] is the last entry's alone: the line the Tile says once there is nothing left
 * to count down to, with no [next] and no [rows].
 *
 * [shownUntil] is the last second the renderer may still show the entry, since it changes entries
 * late ([TilePlan.shownUntil]); null for the notice, which runs on. For as long as that is, the
 * entry says what is true: its countdown reaches it, and an entry that may still be shown once
 * the rows or the next start have changed carries what is drawn from then on, [after], which the
 * layout shows from [until] on by the platform's clock (`TileLayout`). [after] runs from [until]
 * to this entry's [shownUntil], or is the notice.
 */
internal data class TileEntry(
    val from: Long,
    val until: Long?,
    val next: TileNext?,
    val rows: List<RowModel>,
    val city: String,
    val notice: String?,
    val update: String?,
    val shownUntil: Long? = null,
    val after: TileEntry? = null,
) {
    /** Whether the entry counts down to its next start: one of the timeline's moving part. */
    val moves: Boolean get() = next?.minutes?.isNotEmpty() == true
}

/**
 * The Tile's timeline (spec 2.4), planned as plain entries and laid out afterwards (`TileLayout`),
 * so that a unit test can read it. What an entry draws is what the rules say at its first second,
 * and it lasts for as long as that stays so, apart from the countdown and its sentence, which the
 * watch's system moves on by itself.
 *
 * An entry is cut at every time of the day the rows show (each row's pip and colours change there;
 * Sunrise only while the phone shows it), at every turn of the date (the rows become the next
 * day's) and at every start (the next start changes): the changes. For the timeline's first
 * [MOVING_SECONDS] it is also cut shorter: the Tile renderer gives a layout 200 dynamic nodes (the
 * spike, P3.8 Step 0), and the countdown and its sentence are each a chain of the rules' own words,
 * one link of eight nodes (its fixed values counted, as Google counts them) for every minute the
 * entry can be shown at, so an entry can hold about ten minutes and not an hour. Plan 1's
 * countdown, which counts any period in one expression, is 380 such nodes, and the renderer never
 * binds it (erratum p3 #6).
 *
 * **The renderer changes entries late** (review round 3, seen on emulator-5558): it moves on to the
 * next entry on an inexact alarm ([shownUntil]), so an entry is shown for up to three quarters of
 * its length again past its end. So an entry's countdown reaches as far as it may be shown, which
 * keeps a moving entry to [STEP_SECONDS]; the entries before each change shorten towards it
 * ([nextCut]), so that none of them can still be shown when it comes; and the one that ends at it,
 * [LAST_MINUTE_SECONDS] long, which can, carries what is drawn after it ([TileEntry.after]).
 *
 * Past [MOVING_SECONDS] the line says the next start's time instead of counting down to it, as a
 * renderer without dynamic values has it (call 6): every entry carries its whole layout, about 5 KB
 * of the one one-way binder call the Tile is sent in, so a moving countdown for ten periods would be well
 * over a megabyte. Where even [MOVING_SECONDS] of them would be too heavy to send, far north on
 * some nights, the Tile that is sent ends the moving part sooner (`TileLayout.fitted`). The Tile
 * is fresh for less than the moving part ([freshnessMillis]), so the system asks for it again
 * while it still moves. Past the moving hours a late change can still
 * keep a start's time and the rows before it on the Tile once it has come, for up to three quarters
 * of that entry again: what a missed request leaves.
 */
internal object TilePlan {

    /** How many prayer periods the timeline holds at most, counting the one under way (`ComplicationTimelines.PERIODS`). */
    const val PERIODS = 10

    /**
     * How long the timeline's countdown moves at most: 80 minutes, after which its entries say the
     * next start's time. Its entries are five minutes long at most, shorter towards each change,
     * and each carries its whole layout (4.5 to 6.8 KB moving, and 8.7 to 12.1 KB for the one
     * before a change, by language and city), so the moving part is most of what one request
     * sends ([TileLayout.MAX_TIMELINE_BYTES]): with six-minute entries two hours made 216 KB at
     * Maghrib in the phone's Urdu. Eighty minutes fit through the day in Cape Town in each of the
     * phone's seven languages on both its clocks, with the longest city the bundle names. Far
     * north, where five changes fall within the hour, they do not: on Tromsø's heaviest night in
     * May they weigh 193 KB in English and 225 KB in Bengali on 12 hours with the update (review
     * round 5), so there the Tile that is sent ends the moving part sooner, where the timeline
     * fits (`TileLayout.fitted`), and is asked for again sooner ([freshnessMillis]).
     */
    const val MOVING_SECONDS = 80L * 60L

    /**
     * An entry of the moving hours is at most this long: five minutes, whose countdown, from the
     * margin before it to the last second the renderer may show it (three quarters of five minutes
     * past its end, [shownUntil]), is ten minutes, and the start's time past them (Ruling 65 item
     * 3). An entry is then 172 nodes at most, every 53 s through two days of requests in six
     * feeds, within the quota; six minutes were 188 without the start's time and 204 with it
     * (review round 4).
     */
    const val STEP_SECONDS = 300L

    /**
     * How far before its own time an entry's countdown still reads right: half a minute, for a
     * clock that moves back a little. Past its end, it reads right for as long as the renderer may
     * still show it ([shownUntil]).
     */
    const val MARGIN_SECONDS = 30L

    /**
     * Where the last entry before each change begins: two minutes before it. Before a start, only
     * that entry says "in under a minute", the widest of the countdown's words in most languages,
     * so the minutes before it keep the drawing's size (review round 2). It is the one entry the
     * renderer may still show once the change has come, and it carries what is drawn from then on
     * ([TileEntry.after]).
     */
    const val LAST_MINUTE_SECONDS = 120L

    /**
     * The renderer's change of entries, as measured on emulator-5558 (review round 3: SysUI's
     * `ProtoTilesTimelineMgr`, as androidx's `TilesTimelineManagerInternal`): it sets the change
     * with `AlarmManager.set(RTC, …)`, never sooner than [LEAST_CHANGE_SECONDS] after it last
     * changed, and an inexact alarm may come as late as three quarters of the time it was set for
     * again ("window +3m44s736ms" for one set five minutes ahead), which it did there.
     */
    const val LEAST_CHANGE_SECONDS = 60L

    /** What the renderer is allowed beyond its alarm's window: the alarm's delivery and the drawing, in whole seconds. */
    const val LATE_SLACK_SECONDS = 10L

    /**
     * The longest the Tile is fresh for: three quarters of an hour, within Ruling 22's six and
     * short of [MOVING_SECONDS], so that the system asks again while the countdown still moves,
     * even if its own request comes as late as an inexact alarm may, three quarters of the time
     * again (79 minutes of the 80). A moving part ended sooner keeps the Tile fresh for less
     * ([freshnessMillis]).
     */
    const val MAX_FRESH_MILLIS = 45L * 60_000L

    /**
     * The soonest the Tile is asked for again by itself: a minute. The Tiles reference says the
     * freshness "should not be used to update your tile more frequently than once a minute", and
     * that the system may throttle a Tile that asks sooner (Ruling 68 item 2 (a)).
     */
    const val MIN_FRESH_MILLIS = 60_000L

    /**
     * The last second the renderer may still show an entry from [from] until [until] (epoch
     * seconds): if it showed it from [from], its alarm for [until] may come three quarters of the
     * entry's length late; if it showed it later, sooner than that, but never sooner than a minute
     * after it did, with three quarters of that minute again, so up to 105 s past [until] for an
     * entry first shown in its last seconds. With [LATE_SLACK_SECONDS] on top.
     */
    fun shownUntil(from: Long, until: Long): Long =
        until + maxOf((until - from) * 3 / 4, LEAST_CHANGE_SECONDS * 7 / 4) + LATE_SLACK_SECONDS

    /**
     * The Tile's entries from [now] (epoch seconds), for [feed] and with a newer format seen or not
     * ([newerVersionSeen], `FeedStore.newerVersionSeen`): contiguous and none overlapping, the first
     * from [now], up to [PERIODS] prayer periods ahead (about two days), an entry that begins before
     * [movingUntil] counting down (the moving part, [MOVING_SECONDS] unless the Tile's weight ends
     * it sooner, `TileLayout.fitted`), then the notice, which runs on: "Update Taqwa on your watch" when a
     * newer format has been seen, else "Open Taqwa on your phone", in the feed's words. A feed with
     * no start left is the notice alone. With no feed there are none: the Tile then says the
     * watch's own line ([emptyLine]), as the pages do.
     */
    fun entries(feed: WatchFeed?, newerVersionSeen: Boolean, now: Long, movingUntil: Long = now + MOVING_SECONDS): List<TileEntry> {
        if (feed == null) return emptyList()
        val words = feed.words()
        val update = words.updateOnWatch.takeIf { newerVersionSeen }
        val city = feed.place.city
        val notice = update ?: words.openOnPhone
        val entries = ArrayList<TileEntry>()
        var at = now
        var periods = 0
        while (periods < PERIODS) {
            val moment = WatchRules.moment(feed, at) ?: break
            val endAt = moment.next.at
            val change = listOfNotNull(endAt, nextTimeOfDay(feed, at), nextDayChange(feed, at)).min()
            val moving = at < movingUntil
            val until = (if (moving) nextCut(change, at) else null) ?: change
            val shown = shownUntil(at, until)
            // The entry the renderer may still show once the change has come draws, from then on,
            // what the rules say there: the next entry's rows and line, or the notice.
            val after = if (moving && until == change) {
                WatchRules.moment(feed, change)?.let { then ->
                    TileEntry(change, shown, next(feed, then, change, shown, shown, moving = true), dayRows(feed, change, LitRule.GLANCE), city, null, update, shown)
                } ?: TileEntry(change, null, null, emptyList(), city, notice, update)
            } else {
                null
            }
            entries += TileEntry(at, until, next(feed, moment, at, until, shown, moving), dayRows(feed, at, LitRule.GLANCE), city, null, update, shown, after)
            if (until == endAt) periods++
            at = until
        }
        entries += TileEntry(at, null, null, emptyList(), city, notice, update)
        return entries
    }

    /** The watch's own line when there is no feed, as the pages say it (`noFeedLine`). */
    @StringRes
    fun emptyLine(newerVersionSeen: Boolean): Int = noFeedLine(newerVersionSeen)

    /**
     * How long the Tile planned as [entries] at [now] is fresh for, in milliseconds: until its notice
     * begins, at most [MAX_FRESH_MILLIS]. Once the notice shows (or there is no feed) it is 0, which
     * the system takes as never refreshing by itself: only a new feed changes it, and a stored feed
     * asks for the Tile again (`TileUpdates`).
     *
     * A moving part that ends before the notice ends at [MOVING_SECONDS] or later, which
     * [MAX_FRESH_MILLIS] is short of; one ended sooner, where the Tile's weight ends it
     * (`TileLayout.fitted`), keeps the Tile fresh for four sevenths of it at most, so that a
     * request as late as an inexact alarm may come, three quarters of the time again, still comes
     * while it moves. With nothing left to move, [MAX_FRESH_MILLIS].
     *
     * Whatever is left, never less than [MIN_FRESH_MILLIS] but 0: a moving part that ends within
     * 105 s, or a notice that begins within a minute, is asked for a minute on, and the line in
     * between says the start's time (call 6), as past the moving hours.
     */
    fun freshnessMillis(entries: List<TileEntry>, now: Long): Long {
        val notice = entries.lastOrNull() ?: return 0L
        val still = entries.firstOrNull { it.next != null && !it.moves }
        val most = if (still != null && entries.first().moves) minOf(MAX_FRESH_MILLIS, (still.from - now) * 1_000L * 4 / 7) else MAX_FRESH_MILLIS
        val fresh = ((notice.from - now) * 1_000L).coerceIn(0L, most)
        return if (fresh == 0L) 0L else maxOf(fresh, MIN_FRESH_MILLIS)
    }

    /**
     * The step of [next] for [secondsLeft] to its start, as its chains say it (`TileCountdown`):
     * the minute's own from 59 s; the start's time from the start on, and once fewer minutes are
     * left than its first counts.
     */
    fun stepAt(next: TileNext, secondsLeft: Long): CountdownStep {
        val clock = CountdownStep(0, next.clock, next.clockSentence)
        val minutes = (secondsLeft.coerceAtLeast(0L) / 60L).toInt()
        if (secondsLeft <= 0L || minutes < next.minutes.first().minutesLeft) return clock
        return next.minutes.first { it.minutesLeft == minutes }
    }

    /**
     * The next start of [moment] as the entry from [from] until [until] draws it, which the
     * renderer may still show until [shownUntil]: its countdown's minutes from the margin before
     * [from] (never before its period began) to [shownUntil], or to the start.
     */
    private fun next(feed: WatchFeed, moment: WatchMoment, from: Long, until: Long, shownUntil: Long, moving: Boolean): TileNext {
        val day = feed.days[moment.next.dayIndex]
        val time = WatchTime.of(moment.next.prayer)
        val name = day.names.of(time)
        val spokenClock = WatchRules.spokenClock(day, time)
        val clockText = WatchRules.clockText(day, time)
        val endAt = moment.next.at
        val clock = CountdownStep(0, clockText, name + spokenSeparator(feed.ui.rtl) + spokenClock)
        // Only the entry that ends at the start says under a minute (review round 2).
        val underAMinute = until == endAt
        fun stepOf(m: Int) =
            if (m == 0 && !underAMinute) clock else CountdownStep(m, WatchRules.compactCountdown(feed, m * 60L), WatchRules.spokenCountdown(feed, name, spokenClock, m * 60L))
        // The minutes the entry can be shown at, the time left falling as the clock goes on.
        val most = minutesLeft(endAt, maxOf(moment.previous.at, from - MARGIN_SECONDS))
        val least = minutesLeft(endAt, minOf(shownUntil, endAt - 1))
        val minutes = if (moving) (least..most).map(::stepOf) else emptyList()
        return TileNext(
            label = if (feed.ui.latinScript) name.uppercase(Locale.forLanguageTag(feed.ui.language)) else name,
            name = name,
            clock = clockText,
            spokenClock = spokenClock,
            startAt = moment.previous.at,
            endAt = endAt,
            minutes = minutes,
            rtl = feed.ui.rtl,
        )
    }

    /** The whole minutes left to [endAt] at the whole second [at], as the rules count them; 0 from the start on. */
    private fun minutesLeft(endAt: Long, at: Long): Int = ((endAt - at).coerceAtLeast(0L) / 60L).toInt()

    /**
     * The first of the day's times the rows show after [at], of the day [at] is on; null when all
     * have passed. Sunrise is one only while the phone shows it (`WatchUi.showSunrise`): hidden, it
     * changes nothing the Tile draws, and an entry cut there would only weigh the timeline.
     */
    private fun nextTimeOfDay(feed: WatchFeed, at: Long): Long? {
        val times = feed.days[WatchRules.today(feed, at) ?: return null].times
        val sunrise = if (feed.ui.showSunrise) times.sunrise else null
        return listOfNotNull(times.fajr, sunrise, times.dhuhr, times.asr, times.maghrib, times.isha).filter { it > at }.minOrNull()
    }

    /** Where the date turns over after [at], as the rules turn it (`WatchRules.dayStart`); null on the feed's last day. */
    private fun nextDayChange(feed: WatchFeed, at: Long): Long? {
        val index = WatchRules.today(feed, at) ?: return null
        return if (index + 1 < feed.days.size) WatchRules.dayStart(feed, index + 1).takeIf { it > at } else null
    }

    /**
     * The next cut after [at] before the [change] that follows it, in the moving hours; null when
     * the entry runs to the change. The last entry before it is [LAST_MINUTE_SECONDS] long, and
     * each before that as long as it may be, up to [STEP_SECONDS] in whole minutes, while the
     * renderer can never still show it when the change comes ([shownUntil]): so the cuts fall 2
     * and 4 minutes before it, then every five minutes (9, 14, 19 and so on). A cut never falls in
     * the last [LAST_MINUTE_SECONDS].
     */
    private fun nextCut(change: Long, at: Long): Long? {
        val left = change - at
        var before = LAST_MINUTE_SECONDS
        if (left <= before) return null
        while (true) {
            val further = before + longestEndingBefore(before)
            if (further >= left) return change - before
            before = further
        }
    }

    /**
     * The longest entry, in whole minutes up to [STEP_SECONDS], that ends [before] seconds before a
     * change and that the renderer can never still show when the change comes.
     */
    private fun longestEndingBefore(before: Long): Long {
        var length = STEP_SECONDS
        while (length > 60L && shownUntil(0L, length) - length >= before) length -= 60L
        return length
    }
}
