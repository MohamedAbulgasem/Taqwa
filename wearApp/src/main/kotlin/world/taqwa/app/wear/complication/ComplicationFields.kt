package world.taqwa.app.wear.complication

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchPrayer
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watch.WatchTime
import world.taqwa.app.watch.WatchUi
import world.taqwa.app.watch.of

/**
 * What the next-prayer source says at one instant, as [ComplicationFields.nextPrayer] works it out
 * from the feed.
 *
 * @property label The prayer's name in Latin capitals (`ASR`), or the Arabic name as the phone wrote
 *   it: capitals in the feed's own language (Turkish has a dotted İ), and none where the script has none.
 * @property name The prayer's name exactly as the phone wrote it (`Asr`), for a screen reader: a speech
 *   engine may spell out a word in capitals letter by letter, and the label's are the face's.
 * @property time The prayer's time as the phone writes a clock time, in its format and digits, bare:
 *   what a slot draws, `4:10` on a twelve-hour phone too, with no day period ([spokenTime] has it).
 * @property countdown The time left, in the feed's phrases and digits, floored to the minute.
 * @property progress How far from the previous start to this one the moment has got, 0 to 1.
 * @property startAt When the period began, in epoch seconds: the previous start.
 * @property endAt When it ends, in epoch seconds: the start that is next.
 * @property rtl Whether the phone's language is written right to left.
 * @property twelveHour Whether the phone shows a twelve-hour clock.
 * @property dhuhrAt The Dhuhr of the date the moment is on (epoch seconds): twelve o'clock on the
 *   Sun style's day dial, which the ring's numbers are counted from ([dialStart], [dialEnd]). It is
 *   the date the rules are on (`WatchRules.today`), so it turns to the next date's at midnight, in
 *   the middle of the night's period, and `ComplicationFields.dial` counts from the same one.
 * @property spokenTime The prayer's time as a screen reader is told it (`WatchRules.spokenClock`):
 *   a twelve-hour phone's with its day period as the Prayer screen writes it, `4:10 PM`, between a
 *   first-strong isolate and its pop so that the sentence around it cannot reorder it; [time] as it
 *   is on 24 hours, and from a feed written before the phone sent its twelve-hour times.
 */
data class NextPrayerFields(
    val label: String,
    val name: String,
    val time: String,
    val countdown: String,
    val progress: Float,
    val startAt: Long,
    val endAt: Long,
    val rtl: Boolean,
    val twelveHour: Boolean,
    val dhuhrAt: Long,
    val spokenTime: String = time,
) {
    /**
     * The period's start as minutes from [dhuhrAt], which is where the ring begins: negative before
     * Dhuhr (the night's start after midnight is the day before's Isha, about 16 hours before). The
     * ring's range is [dialStart] to [dialEnd] and its value the moment between them, so that a face
     * that draws the share, (value - start) / (end - start), draws [progress], and the Sun style
     * reads the same three numbers as places on its dial, a quarter of a degree a minute.
     */
    val dialStart: Float get() = (startAt - dhuhrAt).toFloat() / 60f

    /**
     * The period's end in the same minutes: [dialStart] and the period's length, added as the
     * ring's dynamic value adds them (`CountdownExpression.ring`), so that the value reaches this
     * end exactly at the period's last second and never a hair past it.
     */
    val dialEnd: Float get() = dialStart + (endAt - startAt).toFloat() / 60f

    /** The moment itself in those minutes, as the ring's value has it when the entry is made: [progress] of the way from [dialStart] to [dialEnd]. */
    val dialNow: Float get() = (dialStart + progress * (dialEnd - dialStart)).coerceIn(dialStart, dialEnd)
}

/**
 * The day as a dial of seven stretches, in the order of the clock from midnight: midnight to Fajr
 * (night), Fajr to Sunrise (twilight), Sunrise to Dhuhr, Dhuhr to Asr and Asr to Maghrib (the
 * daylight, in three), Maghrib to Isha (twilight), and Isha to the next midnight (night). Every
 * prayer period is made of whole stretches, so the one under way lights exactly. [weights] are the
 * stretches' lengths in minutes, every one longer than nothing and adding up to the 1,440 minutes
 * of a dial, which is a circle of 24 hours whatever the date's length: on a date of 23 or 25 hours
 * the night that ends it makes up the circle, so every other boundary is where its prayer is.
 * [colors] are their ARGB colours, one each: the palette's hairline, the gold of the daylight at 15%
 * for the twilights and at 34% for the daylight, as faces.js's sunFace draws them, and
 * [ComplicationFields.ACCENT_PLACEHOLDER] for a stretch that lies wholly inside the current prayer
 * period. The Taqwa face draws its own colours for these stretches (it lights the period from its
 * start to now, in its accent); the colours are for any other face.
 */
data class DayFields(val weights: List<Float>, val colors: List<Int>)

/**
 * The day dial's geometry, which the Taqwa face's Sun style draws its marks and turns its stretches
 * by (spec 3.4): each prayer, the Sunrise and the date's midnight as whole minutes from the Dhuhr of
 * the date the moment is on (`NextPrayerFields.dhuhrAt`, twelve o'clock on the dial, a quarter of a
 * degree a minute). Dhuhr is 0 by definition and has no field.
 *
 * Each prayer is the one the dial marks at that moment: the period's own start and end where the
 * period is that prayer's, so that the two the next prayer source counts between are marks on the
 * dial to the minute (after Isha the Fajr is the next date's, and before Fajr the Isha is the day
 * before's), and the date's own otherwise. A face cannot read a number out of a weighted
 * complication's weights, so these travel as a ranged value ([ComplicationFields.packDial]).
 */
data class DialFields(val midnight: Int, val fajr: Int, val sunrise: Int, val asr: Int, val maghrib: Int, val isha: Int)

/**
 * [DialFields] as the three numbers of a ranged value, two fields in each, so that the face can
 * read them with `floor` and `%` ([ComplicationFields.packDial] says how).
 */
data class PackedDial(val min: Float, val value: Float, val max: Float)

/**
 * The date lines: the Hijri date and the Gregorian one as the phone wrote them, and the weekday and
 * the day of the month alone. [hijriDayMonth] is the Hijri date as the face writes it, the day and
 * the month with no year (Ruling 24), or the full [hijri] from a feed written before the phone sent
 * that form. [gregorianSpoken] is the Gregorian as a screen reader is told it, in full words and
 * not the face's capitals (`Sunday 18 October`), or [gregorian] from a feed written before that.
 */
data class DatesFields(
    val hijri: String,
    val gregorian: String,
    val weekday: String,
    val dayNumber: String,
    val hijriDayMonth: String = hijri,
    val gregorianSpoken: String = gregorian,
)

/**
 * How the phone writes what the Taqwa face draws for itself (spec 3.3, 3.5): which way its lines
 * read, twelve hours or twenty-four, the digits of its clock and the day period's markers. The face
 * holds no code and its own clock is the watch's, so these travel as the two texts of its day dial's
 * slot (slot 7, `DayDialSource`), which no other face is given, in a form the face takes apart with
 * `subText` (tools/face-centre.py reads it so):
 *
 * - [title] is three letters and then the morning's marker: `R` or `L` for right to left or left to
 *   right; `1` for a twelve-hour clock or `2` for 24 hours; `A` when the digits are Arabic-Indic or
 *   extended Arabic-Indic, which the face draws in Noto Sans Arabic, or `L` for any other set, which
 *   it draws in Manrope (a set Manrope lacks, Bengali's, falls back to the system's font); then `am`
 *   (`AM`, `ص`): `L2LAM` is an English phone on 24 hours, `R1Aص` a Saudi one on twelve.
 * - [text] is the ten digits of the phone's set from its zero, which the face writes its clock in
 *   digit by digit, and then the afternoon's marker: `0123456789PM`, `٠١٢٣٤٥٦٧٨٩م`.
 *
 * Each marker is the last field of its text, so it may be of any length. Every character here is
 * one UTF-16 unit, which is what `subText` counts in: the markers of the seven languages are.
 */
data class FaceSettings(val title: String, val text: String)

/**
 * How much of the moon's disc is lit and which side it is lit on, as it is seen from the hemisphere
 * it is asked for. [litFraction] is 0 at the new moon and 1 at the full; [waxing] is whether it is
 * growing; [litOnLeft] is whether the lit part is on the left of the disc, which depends on the
 * hemisphere: waxing in the south is lit on the left, and in the north on the right.
 */
data class MoonShape(val litFraction: Float, val waxing: Boolean, val litOnLeft: Boolean)

/**
 * The fields the complication sources are made of, worked out from the feed and the clock and from
 * nothing else (no Android, no I/O), so that they are tested on a plain JVM. Every one of them is a
 * question to [WatchRules] and not an answer of this file's own: which prayer is next, how far the
 * period has got, how the countdown reads and which day it is are decided there, once, for the
 * face, the Tile and the app too.
 *
 * Each returns null when the feed has no honest answer, which is the sources' empty form: there is
 * no feed, the instant is before it begins, or its last start has passed, the three at once and not
 * one by one, so that a face never shows a countdown beside a date that is gone.
 */
object ComplicationFields {

    /** The hairline of the palette, `#232825`: the night stretches of the day dial. */
    const val HAIRLINE: Int = 0xFF232825.toInt()

    /** The gold of Sun's daylight, `#F0B429`, at 34%: the daylight stretches, whatever the face's accent. */
    const val DAYLIGHT: Int = 0x57F0B429

    /** The same gold at 15%, as faces.js draws the twilights: Fajr to Sunrise and Maghrib to Isha. */
    const val TWILIGHT: Int = 0x26F0B429

    /**
     * The colour of the stretches that make up the current period, which the face recolours with
     * its accent: Amber, `#F0B429`, the default accent, at full strength, so that the default
     * face is right before anything has been recoloured.
     */
    const val ACCENT_PLACEHOLDER: Int = 0xFFF0B429.toInt()

    // ---- The next prayer

    fun nextPrayer(feed: WatchFeed, now: Long): NextPrayerFields? {
        val moment = WatchRules.moment(feed, now) ?: return null
        val day = feed.days[moment.next.dayIndex]
        val prayer = moment.next.prayer
        return NextPrayerFields(
            label = day.names.of(prayer).uppercase(Locale.forLanguageTag(feed.ui.language)),
            name = day.names.of(prayer),
            time = day.clock.of(prayer),
            countdown = WatchRules.countdown(feed, moment.secondsLeft),
            progress = moment.progress,
            startAt = moment.previous.at,
            endAt = moment.next.at,
            rtl = feed.ui.rtl,
            twelveHour = feed.ui.twelveHour,
            dhuhrAt = feed.days[WatchRules.today(feed, now) ?: return null].times.dhuhr,
            spokenTime = WatchRules.spokenClock(day, WatchTime.of(prayer)),
        )
    }

    // ---- The day

    fun day(feed: WatchFeed, now: Long): DayFields? {
        val moment = WatchRules.moment(feed, now) ?: return null
        val index = WatchRules.today(feed, now) ?: return null
        val day = feed.days[index]
        val times = day.times
        val start = dialMidnight(feed, index)

        // The eight edges of the seven stretches as seconds into the dial: midnight, Fajr, Sunrise,
        // Dhuhr, Asr, Maghrib, Isha and the circle's end, 24 hours on. Each is held to the circle
        // and in order: in a high-latitude summer Maghrib and Isha can fall after the next
        // midnight, and a combined polar Maghrib and Isha are one instant.
        val edges = LongArray(8)
        val instants = longArrayOf(times.fajr, times.sunrise, times.dhuhr, times.asr, times.maghrib, times.isha)
        edges[7] = A_DAY
        for (i in instants.indices) edges[i + 1] = max(edges[i], (instants[i] - start).coerceIn(0L, A_DAY))

        // A stretch that lies wholly inside the current period is part of it, and is the accent's.
        // Every period is made of whole stretches; the night's runs past the circle's end into the
        // next date's first.
        val colors = List(7) { i ->
            val begins = start + edges[i]
            val ends = start + edges[i + 1]
            val inside = ends - begins >= 60L && moment.previous.at <= begins && ends <= moment.next.at
            if (inside) ACCENT_PLACEHOLDER else baseColor(i)
        }
        return DayFields(stretchesInMinutes(edges), colors)
    }

    /**
     * The day dial's geometry at [now] ([DialFields]): the date's times as whole minutes from its
     * Dhuhr, each prayer the instance the dial marks, or null when the feed has nothing honest to
     * say, as for the other fields.
     */
    fun dial(feed: WatchFeed, now: Long): DialFields? {
        val moment = WatchRules.moment(feed, now) ?: return null
        val index = WatchRules.today(feed, now) ?: return null
        val times = feed.days[index].times
        val dhuhr = times.dhuhr
        fun minutes(instant: Long): Int = ((instant - dhuhr) / 60.0).roundToInt()
        // The period's own start and end stand for their prayers: they may be another date's.
        fun marked(prayer: WatchPrayer, ofTheDate: Long): Long = when (prayer) {
            moment.previous.prayer -> moment.previous.at
            moment.next.prayer -> moment.next.at
            else -> ofTheDate
        }
        return DialFields(
            midnight = minutes(dialMidnight(feed, index)),
            fajr = minutes(marked(WatchPrayer.FAJR, times.fajr)),
            sunrise = minutes(times.sunrise),
            asr = minutes(marked(WatchPrayer.ASR, times.asr)),
            maghrib = minutes(marked(WatchPrayer.MAGHRIB, times.maghrib)),
            isha = minutes(marked(WatchPrayer.ISHA, times.isha)),
        )
    }

    /**
     * What a screen reader says for the day dial at [now] (`DayDialSource`, the face's slot 7): the
     * date's times as Today shows them, each after its name, as the phone writes them, in the order
     * the dial marks them:
     * `Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24`, and each of a
     * twelve-hour phone's with its day period (`WatchRules.spokenClock`), Sunrise's too. The
     * weighted day beside it says what the dial is ("Prayer times today", `DaySource`), and this
     * gives the times, so that a reader that lists both does not say the same thing twice. A
     * right-to-left phone's words are joined by the Arabic comma. Null where the dial has nothing to
     * say.
     *
     * Sunrise is said only while the phone shows it (`WatchUi.showSunrise`), as Today's rows and the
     * Tile's have it: otherwise the five prayers alone. The dial itself ([dial]) marks sunrise either
     * way, as the end of Fajr, so with Sunrise hidden the list is not every mark the dial draws: it is
     * the times Today shows.
     */
    fun dialSpoken(feed: WatchFeed, now: Long): String? {
        WatchRules.moment(feed, now) ?: return null
        val index = WatchRules.today(feed, now) ?: return null
        val day = feed.days[index]
        val said = WatchTime.entries.filter { feed.ui.showSunrise || it != WatchTime.SUNRISE }
        return said.joinToString(spokenSeparator(feed.ui)) { time ->
            "${day.names.of(time)} ${WatchRules.spokenClock(day, time)}"
        }
    }

    // ---- What a screen reader says (Task 12)

    /**
     * The comma a spoken line is joined with: the phone's direction's, so that a right-to-left
     * language is read with its own (`، `), as [dialSpoken] joins it.
     */
    fun spokenSeparator(ui: WatchUi): String = spokenSeparator(ui.rtl)

    /**
     * [spokenSeparator] for a line written right to left ([rtl]) or not: `، ` or `, `. The rule's
     * one definition: Today's and the Tile's `spokenSeparator` calls this.
     */
    fun spokenSeparator(rtl: Boolean): String = if (rtl) "، " else ", "

    /**
     * The next prayer and its time as a screen reader says them, `Dhuhr, 12:33`: the name as the
     * phone wrote it ([NextPrayerFields.name]) and not in the face's capitals, the phone's comma
     * ([spokenSeparator], `، ` right to left), and the time in the phone's format and digits,
     * which the reader says as a number, with a twelve-hour phone's day period
     * ([NextPrayerFields.spokenTime]). It is joined as the Tile and Today join the prayer and its
     * time, at every API level (Ruling 74 item 1), and only the comma is said: no slot draws it.
     */
    fun prayerSpoken(fields: NextPrayerFields): String = fields.name + spokenSeparator(fields.rtl) + fields.spokenTime

    /**
     * The two dates as a screen reader says them: the Hijri date as the Prayer screen writes it,
     * with its year ([DatesFields.hijri], which the face's line shortens to the day and the month),
     * then the Gregorian in the phone's spoken words ([DatesFields.gregorianSpoken], `Sunday 18
     * October` and not the face's `SUN 18 OCT`), joined by the phone's comma.
     */
    fun datesSpoken(dates: DatesFields, ui: WatchUi): String =
        listOf(dates.hijri, dates.gregorianSpoken).filter { it.isNotEmpty() }.joinToString(spokenSeparator(ui))

    /**
     * [dial] as the three numbers of a ranged value, which the face takes apart again:
     *
     * - min = `midnight' * 4096 + fajr'`
     * - value = `(sunrise' + 1) * 4096 + maghrib'`
     * - max = `(asr' + 2) * 4096 + isha'`
     *
     * where each field' is its minutes plus 1,440, held to 0..4093. The face reads a field with
     * `floor(n / 4096)` and `n % 4096`, less the same 1,440 (and the 1 and the 2):
     * tools/face-sun.py writes those expressions with these numbers, and [unpackDial] is the same
     * reading here. Every number is a whole number below 2^24, which a Float holds exactly, and 4096
     * is a power of two, so the face's division is exact too.
     *
     * A ranged value has to keep min <= value <= max, or the library ends the app's process on the
     * watch. Midnight, Sunrise and Asr come in that order on every date (Sunrise is before Dhuhr,
     * Asr after it), and the 1 and the 2 make each step strict: so they carry the order, held to it
     * here whatever they are given, and the three prayers that may be another date's (Fajr, Maghrib
     * and Isha) ride below them, where the order does not reach.
     */
    fun packDial(dial: DialFields): PackedDial {
        val first = code(dial.midnight)
        val second = max(code(dial.sunrise), first)
        val third = max(code(dial.asr), second)
        return PackedDial(
            min = (first * DIAL_BASE + code(dial.fajr)).toFloat(),
            value = ((second + 1) * DIAL_BASE + code(dial.maghrib)).toFloat(),
            max = ((third + 2) * DIAL_BASE + code(dial.isha)).toFloat(),
        )
    }

    /** The face's reading of a [packDial] ranged value, for the tests: the inverse of it, for any dial in order. */
    fun unpackDial(min: Float, value: Float, max: Float): DialFields {
        fun high(n: Float) = floor(n / DIAL_BASE).toInt()
        fun low(n: Float) = (n % DIAL_BASE).toInt()
        return DialFields(
            midnight = high(min) - DIAL_OFFSET,
            fajr = low(min) - DIAL_OFFSET,
            sunrise = high(value) - 1 - DIAL_OFFSET,
            maghrib = low(value) - DIAL_OFFSET,
            asr = high(max) - 2 - DIAL_OFFSET,
            isha = low(max) - DIAL_OFFSET,
        )
    }

    private fun code(minutes: Int): Int = (minutes + DIAL_OFFSET).coerceIn(0, DIAL_BASE - 3)

    /**
     * Where the date [index]'s dial begins: where the rules turn the date over (`WatchRules.dayStart`),
     * its midnight, or where the phone could not say (a midnight of 0) solar midnight, halfway
     * between the Dhuhrs, so that the dial turns with the rules to the second.
     */
    private fun dialMidnight(feed: WatchFeed, index: Int): Long = WatchRules.dayStart(feed, index)

    /**
     * The seven stretches between the eight [edges] of the dial in minutes, each longer than nothing
     * and still adding up to the dial: a stretch the engine's times leave empty is given a hundredth
     * of a minute, taken from its neighbour, because a weight of nothing is not a weight the
     * library accepts.
     */
    private fun stretchesInMinutes(edges: LongArray): List<Float> {
        val last = edges.lastIndex
        val b = DoubleArray(edges.size) { edges[it] / 60.0 }
        for (i in 1 until last) b[i] = max(b[i], b[i - 1] + SLIVER)
        for (i in last - 1 downTo 1) b[i] = min(b[i], b[i + 1] - SLIVER)
        return List(last) { (b[it + 1] - b[it]).toFloat() }
    }

    private fun baseColor(stretch: Int): Int = when (stretch) {
        0, 6 -> HAIRLINE
        1, 5 -> TWILIGHT
        else -> DAYLIGHT
    }

    // ---- The dates

    fun dates(feed: WatchFeed, now: Long): DatesFields? {
        if (WatchRules.moment(feed, now) == null) return null
        val day = feed.days[WatchRules.today(feed, now) ?: return null]
        return DatesFields(
            hijri = day.hijri,
            gregorian = day.gregorian,
            weekday = day.weekday,
            dayNumber = day.dayNumber,
            hijriDayMonth = day.hijriDayMonth.ifEmpty { day.hijri },
            gregorianSpoken = day.gregorianSpoken.ifEmpty { day.gregorian },
        )
    }

    // ---- The face's own settings

    /** [FaceSettings] for the feed's [ui]: what the Taqwa face needs to write its clock and its lines as the phone does. */
    fun faceSettings(ui: WatchUi): FaceSettings {
        val direction = if (ui.rtl) 'R' else 'L'
        val clock = if (ui.twelveHour) '1' else '2'
        val numerals = if (ui.digitZero in NOTO_ZEROS) 'A' else 'L'
        val digits = String(CharArray(10) { ui.digitZero + it })
        return FaceSettings(title = "$direction$clock$numerals${ui.am}", text = digits + ui.pm)
    }

    // ---- The moon

    /**
     * The moon at [epochSeconds] as it is seen from the hemisphere it is asked for.
     *
     * The phase is the moon's elongation from the sun: its mean value, which turns once a synodic
     * month (29.530588853 days) from the new moon of 6 January 2000 (18:14 UTC, where this reads
     * 0), and the six largest terms that correct it for the shape of both orbits (Meeus,
     * Astronomical Algorithms, equation 48.4, good to a degree). The mean cycle alone is as much as
     * 18 hours out (it puts October 2026's new moon 17 hours late), which moves the lit fraction by
     * up to 0.08 at the quarters; with the terms the phases of October 2026 are right to the hour
     * (`ComplicationFieldsTest.moonFollowsTheAlmanacsPhases`). The lit fraction is
     * (1 - cos(elongation)) / 2, and the moon waxes through the half of the cycle from the new
     * moon to the full.
     */
    fun moonLit(epochSeconds: Long, southern: Boolean): MoonShape {
        val elongation = moonElongationDegrees(epochSeconds)
        val lit = ((1.0 - cos(elongation * RADIANS)) / 2.0).toFloat()
        val waxing = elongation < 180.0
        // Seen from the south the lit side is mirrored: waxing is lit on the left there.
        return MoonShape(litFraction = lit, waxing = waxing, litOnLeft = waxing == southern)
    }

    private fun moonElongationDegrees(epochSeconds: Long): Double {
        // Julian centuries from J2000.0 (2000-01-01 12:00), taking UT for TT: the 69 seconds between
        // them are a thousandth of a degree.
        val t = (epochSeconds - J2000_EPOCH_SECONDS) / (36_525.0 * 86_400.0)
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t * t + t * t * t / 545_868.0 - t * t * t * t / 113_065_000.0
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t * t + t * t * t / 24_490_000.0
        val mm = 134.9633964 + 477198.8675055 * t + 0.0087414 * t * t + t * t * t / 69_699.0 - t * t * t * t / 14_712_000.0
        val corrected = d +
            6.289 * sin(mm * RADIANS) -
            2.100 * sin(m * RADIANS) +
            1.274 * sin((2 * d - mm) * RADIANS) +
            0.658 * sin(2 * d * RADIANS) +
            0.214 * sin(2 * mm * RADIANS) +
            0.110 * sin(d * RADIANS)
        return ((corrected % 360.0) + 360.0) % 360.0
    }

    /**
     * The lit part of the moon in an image [sizePx] pixels square, for a monochromatic image the
     * face tints: white with an alpha channel and nothing else, so that the face puts it over a dark
     * disc of its own. The disc fills the square. Each pixel's alpha is how much of it the lit part
     * covers, sampled 4 by 4, so the edge is smooth. ARGB, row by row from the top, as
     * `Bitmap.createBitmap(IntArray, ...)` takes it.
     *
     * The lit part lies between the limb on its side and the terminator, which is half an ellipse:
     * at a height with half-width `w` of the disc the terminator is `(2k - 1) * w` from the centre
     * toward the lit side, k being the lit fraction, so that a crescent (k < 0.5) is bounded on the
     * inner side by an arc curving the same way as the limb and a gibbous moon (k > 0.5) by one
     * curving the other way. The area lit comes to exactly k of the disc.
     */
    fun moonPixels(shape: MoonShape, sizePx: Int): IntArray {
        require(sizePx > 0) { "an image has a size" }
        val pixels = IntArray(sizePx * sizePx)
        val radius = sizePx / 2.0
        val terminator = 2.0 * shape.litFraction.coerceIn(0f, 1f) - 1.0
        val samples = MOON_SAMPLES
        val perPixel = samples * samples
        for (py in 0 until sizePx) {
            for (px in 0 until sizePx) {
                var covered = 0
                for (sy in 0 until samples) {
                    val y = (py + (sy + 0.5) / samples - radius) / radius
                    val halfWidth2 = 1.0 - y * y
                    if (halfWidth2 < 0.0) continue
                    val halfWidth = sqrt(halfWidth2)
                    for (sx in 0 until samples) {
                        val x = (px + (sx + 0.5) / samples - radius) / radius
                        if (x < -halfWidth || x > halfWidth) continue
                        val lit = if (shape.litOnLeft) x <= terminator * halfWidth else x >= -terminator * halfWidth
                        if (lit) covered++
                    }
                }
                if (covered > 0) {
                    val alpha = (covered * 255 + perPixel / 2) / perPixel
                    pixels[py * sizePx + px] = (alpha shl 24) or 0x00FFFFFF
                }
            }
        }
        return pixels
    }

    private fun <T> WatchSix<T>.of(prayer: WatchPrayer): T = when (prayer) {
        WatchPrayer.FAJR -> fajr
        WatchPrayer.DHUHR -> dhuhr
        WatchPrayer.ASR -> asr
        WatchPrayer.MAGHRIB -> maghrib
        WatchPrayer.ISHA -> isha
    }

    /** The zeros of the digit sets the face draws in Noto Sans Arabic: Arabic-Indic and extended Arabic-Indic. */
    private val NOTO_ZEROS = setOf('\u0660', '\u06F0')

    /** A hundredth of a minute: the least a stretch of the dial is given. */
    private const val SLIVER = 0.01

    /** The base of [packDial]'s two fields in a number: 2^12, so that two of them fill a Float's 24 bits. */
    const val DIAL_BASE: Int = 4096

    /** What [packDial] adds to a field's minutes, so that a day before Dhuhr's is not negative. */
    const val DIAL_OFFSET: Int = 1440

    private const val A_DAY = 86_400L
    private const val RADIANS = PI / 180.0
    private const val J2000_EPOCH_SECONDS = 946_728_000L
    private const val MOON_SAMPLES = 4
}
