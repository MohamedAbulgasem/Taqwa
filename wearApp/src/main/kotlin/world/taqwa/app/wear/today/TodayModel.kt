package world.taqwa.app.wear.today

import java.util.Locale
import kotlin.math.roundToInt
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchTime
import world.taqwa.app.watch.WatchTimes
import world.taqwa.app.watch.of
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.app.farEdge
import world.taqwa.app.wear.complication.ComplicationFields

/**
 * Where a row of the day's list stands at a moment: the lit prayer, a time whose start has passed,
 * or one still to come. Sunrise is never current: it is no prayer, and only ends Fajr.
 */
internal enum class RowStatus { CURRENT, PASSED, UPCOMING }

/**
 * Which phone surface's rule lights a row (spec 1.4): [LIST] is the Prayer screen's
 * (`WatchRules.litOnList`: Fajr unlit from sunrise and Asr from sunset), which Today's list copies;
 * [GLANCE] is the home-screen widgets' (`WatchRules.litOnGlance`: lit until the next begins),
 * which the Tile copies.
 */
internal enum class LitRule { LIST, GLANCE }

/**
 * One of the day's times as a row draws and says it: [name] and [time] as the phone writes
 * them; [status], the pip and the colours; [small] for Sunrise, which the drawings set smaller and
 * with no pip; [spoken], the phone's row description (`prayerRowDescription`: the name, then the
 * time as a sentence says it); and [state], what a screen reader is told after it ("Current
 * prayer", "Passed", "Next prayer"), or null.
 */
internal data class RowModel(
    val name: String,
    val time: String,
    val status: RowStatus,
    val small: Boolean,
    val spoken: String,
    val state: String?,
)

/**
 * The day's rows at [now] (epoch seconds), in the order of the day (`WatchTime.entries`): those of
 * the day [now] is on by the feed's own midnights (`WatchRules.today`), lit by [rule]. Empty when
 * the feed has no such day. They are its six times while the phone shows Sunrise
 * (`WatchUi.showSunrise`), and its five prayers alone while it does not, as the Prayer screen
 * leaves its Sunrise row out and changes nothing else (`TimelineBuilder`).
 *
 * A row is CURRENT for the lit prayer, PASSED once its start is at or before [now], and UPCOMING
 * otherwise. Its time is `WatchRules.clockText`, Sunrise's too, so a 12-hour phone's rows carry
 * their day period, and it is said through `WatchRules.spokenClock`, joined to the name by the
 * phone's comma as its `prayerRowDescription` joins them: ", ", and "، " in Arabic and Urdu (M8
 * point A5, Ruling 64 item 6). Its state is the phone's `PrayerTimeline` row's:
 * current, passed, or the next start (the same prayer at the same instant as the next start the
 * ring counts to), else none.
 */
internal fun dayRows(feed: WatchFeed, now: Long, rule: LitRule): List<RowModel> {
    val index = WatchRules.today(feed, now) ?: return emptyList()
    val day = feed.days[index]
    val words = feed.words()
    val lit = when (rule) {
        LitRule.LIST -> WatchRules.litOnList(feed, now)
        LitRule.GLANCE -> WatchRules.litOnGlance(feed, now)
    }?.let(WatchTime::of)
    val next = WatchRules.moment(feed, now)?.next
    return WatchTime.entries.filter { feed.ui.showSunrise || it != WatchTime.SUNRISE }.map { time ->
        val at = day.times.of(time)
        val status = when {
            time != WatchTime.SUNRISE && time == lit -> RowStatus.CURRENT
            at <= now -> RowStatus.PASSED
            else -> RowStatus.UPCOMING
        }
        val isNext = next != null && time != WatchTime.SUNRISE && WatchTime.of(next.prayer) == time && next.at == at
        RowModel(
            name = day.names.of(time),
            time = WatchRules.clockText(day, time),
            status = status,
            small = time == WatchTime.SUNRISE,
            spoken = day.names.of(time) + spokenSeparator(feed.ui.rtl) + WatchRules.spokenClock(day, time),
            state = when {
                status == RowStatus.CURRENT -> words.rowCurrent
                status == RowStatus.PASSED -> words.rowPassed
                isNext -> words.rowNext
                else -> null
            },
        )
    }
}

/**
 * The phone's countdown ring (`gToday`): [label] ("ASR IN"), the [countdown] H:MM:SS, and under it
 * the next start's clock [time], or in its place the update notice when [notice]; the ring filled to
 * [progress], anticlockwise when [rtl], as the phone's `RingArc`; and [spoken], the one sentence a
 * screen reader is told of it all, which changes once a minute and never each second.
 */
internal data class RingModel(
    val label: String,
    val countdown: String,
    val time: String,
    val progress: Float,
    val rtl: Boolean,
    val spoken: String,
    val notice: Boolean,
)

/** Today: the ring, the day's rows beneath it ([dayRows]), and the city the list ends in, whole (the layout cuts it). */
internal data class TodayModel(val ring: RingModel, val rows: List<RowModel>, val city: String)

/**
 * Today at [nowSeconds] (epoch seconds), or null when the feed is spent: no start is left ahead of
 * it (`WatchRules.moment`), and the page says so instead. [update] says a newer format has been
 * seen: the ring still counts down from the last feed, and says the update in the time's place
 * (the Lead's call 5).
 *
 * - The label is the phone's `nextIn` with the next start's name in it, in capitals under a Latin
 *   interface (`latinScript`), cased in the feed's language as `ComplicationFields.nextPrayer`
 *   cases the face's.
 * - The countdown is the phone ring's H:MM:SS, to the second, the hours unpadded, in its digits:
 *   `ringDigitZero`, or the feed's `digitZero` when it names none.
 * - The time is the next start's, from its own day (tomorrow's Fajr after Isha), as
 *   `WatchRules.clockText` draws it.
 * - The sentence is the feed's spoken countdown with that time as `WatchRules.spokenClock` says it,
 *   or the screen's own label and time from a feed with no spoken words. The update follows it after
 *   the phone's comma (`ComplicationFields.spokenSeparator`'s rule).
 */
internal fun today(feed: WatchFeed, nowSeconds: Long, update: Boolean = false): TodayModel? {
    val moment = WatchRules.moment(feed, nowSeconds) ?: return null
    val day = feed.days[moment.next.dayIndex]
    val time = WatchTime.of(moment.next.prayer)
    val name = day.names.of(time)
    val words = feed.words()

    val filled = WatchRules.filled(feed.ui.nextIn, name)
    val label = if (feed.ui.latinScript) filled.uppercase(Locale.forLanguageTag(feed.ui.language)) else filled
    val clock = WatchRules.clockText(day, time)
    val said = WatchRules.spokenCountdown(feed, name, WatchRules.spokenClock(day, time), moment.secondsLeft)
        ?: "$label $clock"

    val ring = RingModel(
        label = label,
        countdown = WatchRules.inDigits(hms(moment.secondsLeft), feed.ui.ringDigitZero ?: feed.ui.digitZero),
        time = if (update) words.updateOnWatch else clock,
        progress = moment.progress,
        rtl = feed.ui.rtl,
        spoken = if (update) said + spokenSeparator(feed.ui.rtl) + words.updateOnWatch else said,
        notice = update,
    )
    return TodayModel(ring, dayRows(feed, nowSeconds, LitRule.LIST), feed.place.city)
}

/** [seconds] as the phone ring's H:MM:SS (`CountdownFormatter`): the hours unpadded, never negative. */
private fun hms(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val minutes = (total % 3_600L) / 60L
    val secs = total % 60L
    return "${total / 3_600L}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

/**
 * The comma a spoken line is joined with, the Arabic one for a right-to-left phone: the one rule,
 * [ComplicationFields.spokenSeparator]'s, under the name Today and the Tile call.
 */
internal fun spokenSeparator(rtl: Boolean): String = ComplicationFields.spokenSeparator(rtl)

private fun WatchTimes.of(time: WatchTime): Long = when (time) {
    WatchTime.FAJR -> fajr
    WatchTime.SUNRISE -> sunrise
    WatchTime.DHUHR -> dhuhr
    WatchTime.ASR -> asr
    WatchTime.MAGHRIB -> maghrib
    WatchTime.ISHA -> isha
}

/**
 * Today's lines (call 15), each in the drawing's units with its cap: the ring's [label],
 * [countdown] and [time] (`gToday`), the [notice] that takes the time's place, and the list's
 * [rowName], [rowTime], [sunrise] and [city] (`gTile`'s rows). Only the notice may shrink below its
 * size, to fit two lines.
 *
 * The list's lines stop at [LIST_CAP], 1.12: a list longer than the screen stands higher at its
 * end, and its first row comes nearer the top, where the round screen is narrower. Grown to 1.3,
 * the round screen cut Fajr's 24-hour time there, and grown to 1.24, Wear OS's own largest, it cut
 * a 12-hour "4:33 AM", whose column had moved out for "Maghrib 7:04 PM" (emulator-5558, plan 3
 * Task 9 Step 4 and its review). Up to a growth of about 1.153, every row keeps the widest
 * column the times may take ([TodayLayout.maxEnds]) inside the frame's inset at the list's end,
 * on either clock and in any language
 * (`atItsEndTheListKeepsEveryRowsTimeOnTheRoundScreenAtEveryFontSize`). The cap is the largest of
 * Wear OS's own font sizes (1.06, 1.12, 1.18, 1.24 "Largest") within that
 * (`theCapIsTheLargestWearOsSizeTheRoundScreenAllows`), so its two larger sizes show the list the
 * wearer sees at 1.12. There a 12-hour Friday keeps "Jumuʿah" whole beside "12:33 PM", which a
 * list grown to 1.15 cut with an ellipsis on emulator-5558.
 */
internal object TodayType {
    const val LIST_CAP = 1.12f
    val label = Line(10.5f, 1.3f)
    val countdown = Line(38f, 1f)
    val time = Line(13f, 1.3f)
    val notice = Line(13f, 1.2f, maxLines = 2, floor = 9f)
    val rowName = Line(12f, LIST_CAP)
    val rowTime = Line(12f, LIST_CAP)
    val sunrise = Line(10f, LIST_CAP)
    val city = Line(10.5f, LIST_CAP)
}

/**
 * Where the day's list lies, in the drawing's units (`gTile`): the list is a screen of its own
 * below the ring, so that scrolled to its end it is the Tile's rows where the Tile has them, the
 * pips at 46, the names from 56 and the times ending at 150, the baselines from 64 down, and the
 * city at 184. Without Sunrise, where the phone hides it, the five rows share the six rows' room
 * ([pitch]), so the first row, the city and the screen keep their places and no gap is left. At a
 * larger font size each row grows with its name, and the list grows longer.
 */
internal object TodayLayout {
    /** The pips' centres across, and where the names begin and the times end. */
    const val PIP_X = 46f
    const val NAME_START = 56f
    const val TIME_END = 150f

    /** The least room between a name and its time. */
    const val GAP = 3f

    /** The list's room above its first row: the Tile's countdown line, which Today has in its ring. */
    const val LIST_TOP = 49.5f

    /** A row's baseline below its top, and the city's below its own, at the drawing's size. */
    const val ROW_BASELINE = 14.5f
    const val CITY_ROOM = 28.5f
    const val CITY_BASELINE = 12.5f

    /** A row's pitch in the drawing: 21 units, Sunrise's 17. */
    private const val ROW_PITCH = 21f
    private const val SUNRISE_PITCH = 17f

    /**
     * A row's pitch in a list without Sunrise (`WatchUi.showSunrise` false): the six rows' room,
     * five of 21 and Sunrise's 17, shared evenly by the five, 24.4 units each. The rows stand
     * 64, 88.4, 112.8, 137.2 and 161.6, between the countdown line at 40 and the city at 184, an
     * even step from the line to the city; and grown, the list is as long as the six rows' is.
     */
    private const val SHARED_PITCH = (5 * ROW_PITCH + SUNRISE_PITCH) / 5

    /** The inset a line keeps from the round edge (`WatchUnits.chordUnits`). */
    private const val INSET = 6f

    /**
     * A row's pitch (call 15): the drawing's, times its name's size as drawn ([drawn], in units)
     * over the line's own size, so that a row grows with its text and never overlaps the next. A
     * row of a list without Sunrise ([shared]) takes [SHARED_PITCH] as the drawing's.
     */
    fun pitch(small: Boolean, drawn: Float, shared: Boolean = false): Float = when {
        small -> SUNRISE_PITCH * drawn / TodayType.sunrise.units
        shared -> SHARED_PITCH * drawn / TodayType.rowName.units
        else -> ROW_PITCH * drawn / TodayType.rowName.units
    }

    /**
     * The list's geometry from its top, in units: each row's [tops] and [baselines], the city's room
     * from [cityTop] with its baseline at [city], and the list's whole [height].
     */
    data class ListGeometry(
        val tops: List<Float>,
        val baselines: List<Float>,
        val cityTop: Float,
        val city: Float,
        val height: Float,
    )

    /**
     * The list's geometry for rows that are [small] or not, each drawn [grow] times its line's size
     * (1 at the drawing's size) and the city [cityGrow] times its own: the room above, each row's
     * pitch with its baseline at [ROW_BASELINE] grown with it, then the city's room. At the drawing's
     * size it is `gTile`'s rows and city, 200 units in all, with Sunrise or without: rows none of
     * which is small are the day without its Sunrise, and share the six rows' room ([SHARED_PITCH]).
     */
    fun list(small: List<Boolean>, grow: List<Float> = List(small.size) { 1f }, cityGrow: Float = 1f): ListGeometry {
        require(grow.size == small.size) { "a growth for each row" }
        val shared = small.none { it }
        var top = LIST_TOP
        val tops = ArrayList<Float>(small.size)
        val baselines = small.indices.map { i ->
            tops += top
            val baseline = top + ROW_BASELINE * grow[i]
            top += pitch(small[i], (if (small[i]) TodayType.sunrise.units else TodayType.rowName.units) * grow[i], shared)
            baseline
        }
        return ListGeometry(tops, baselines, cityTop = top, city = top + CITY_BASELINE * cityGrow, height = top + CITY_ROOM * cityGrow)
    }

    /**
     * [units] from the list's top in whole pixels, at [unit] pixels a unit. An item's height is the
     * difference of its two edges so placed, so that a row's rounding never moves the rows after it
     * and the list's end is the Tile's to the pixel.
     */
    fun px(units: Float, unit: Float): Int = (units * unit).roundToInt()

    /**
     * Where the list's times end, in units: the rows' times are a column with one edge, at
     * [TIME_END] as the drawing has it, unless a row's name and time need more room
     * ([nameWidths] and [timeWidths], each as drawn at its largest), when the whole column moves
     * towards the round edge, as far as the narrowest row allows ([maxEnds], each row's
     * [maxEnd]). A name longer still is cut with an ellipsis ([nameRoom]). So a 12-hour Friday's
     * "Jumuʿah" keeps its whole name beside "12:33 PM", and the times still line up.
     */
    fun timeEnd(nameWidths: List<Float>, timeWidths: List<Float>, maxEnds: List<Float>): Float {
        require(nameWidths.size == timeWidths.size && timeWidths.size == maxEnds.size) { "a width of each and an edge for each row" }
        val needed = nameWidths.indices.maxOfOrNull { NAME_START + nameWidths[it] + GAP + timeWidths[it] } ?: TIME_END
        val edge = maxEnds.minOrNull() ?: TIME_END
        return needed.coerceIn(TIME_END, maxOf(TIME_END, edge))
    }

    /** The room a row's name has, in units, beside its time of [timeWidth] ending at [timeEnd]. */
    fun nameRoom(timeEnd: Float, timeWidth: Float): Float = (timeEnd - timeWidth - GAP - NAME_START).coerceAtLeast(0f)

    /**
     * How far right a row's time may reach when its baseline lies at [baseline] in the drawing: the
     * round screen's chord at the row's edge farthest from the middle, less the frame's inset, as
     * every line keeps (`WatchUnits.chordUnits`); the square screen's width, less the inset, on a
     * square watch. A row drawn [grow] times its line's size reaches that much further.
     */
    fun maxEnd(baseline: Float, small: Boolean, round: Boolean, grow: Float = 1f): Float {
        val line = if (small) TodayType.sunrise else TodayType.rowTime
        val edge = farEdge(baseline, centred = false, line = line, size = line.units * grow)
        return WatchUnits.ACROSS / 2 + WatchUnits.chordUnits(edge, round, INSET) / 2
    }

    /**
     * Each row's [maxEnd] where the drawing has it ([list] at the drawing's size), which bounds the
     * times' column ([timeEnd]): on a round screen it ends no further than Isha's round edge, 167.3
     * units, or 170.3 without Sunrise, where Isha stands higher. The list's lines stop at
     * [TodayType.LIST_CAP] so that scrolled to its end, at any font size, every row still holds a
     * column that far out inside the frame's inset.
     */
    fun maxEnds(small: List<Boolean>, round: Boolean): List<Float> {
        val drawing = list(small)
        return small.indices.map { maxEnd(drawing.baselines[it], small[it], round) }
    }
}
