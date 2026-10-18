package world.taqwa.app.prayer.engine.method

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunModel

/** A point on the earth in degrees, north and east positive. */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * When Asr begins: the shadow of an object has grown by once its height (Standard: the Shafi'i,
 * Maliki and Hanbali schools) or twice (Hanafi) beyond its noon shadow.
 */
enum class AsrSchool {
    STANDARD, HANAFI;

    val shadowFactor: Double get() = if (this == HANAFI) 2.0 else 1.0

    /** The school whose time the day carries as `asrOther`. */
    val other: AsrSchool get() = if (this == HANAFI) STANDARD else HANAFI
}

/** How Isha is found. */
sealed interface IshaRule {
    /** When the sun is [degrees] below the horizon in the evening. */
    data class Angle(val degrees: Double) : IshaRule

    /** Minutes after the authority's Maghrib (before rounding); `ramadanMinutes` on Ramadan dates. */
    data class AfterMaghrib(val minutes: Int, val ramadanMinutes: Int = minutes) : IshaRule
}

/** Signed values per event; `authorityMinutes` in minutes, `margins` in seconds (see Conventions). */
data class EventOffsets(
    val fajr: Int = 0, val sunrise: Int = 0, val dhuhr: Int = 0, val asr: Int = 0,
    val maghrib: Int = 0, val isha: Int = 0,
) {
    operator fun get(prayer: Prayer): Int = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}

/**
 * Which civil dates' astronomy a printed row holds.
 * - [SAME_DAY]: the row's own date.
 * - [NEIGHBOURS_MUIS]: MUIS prints one year's rows a day early and the next a day late, so starts
 *   take the later of the day before and the day after, and sunrise the earlier.
 * - [LAG_DATES_UQ]: on listed dates Umm al-Qura prints the previous day's astronomy, so starts take
 *   the later of the day and the day before, and sunrise the earlier; after the last date the list
 *   covers, every day (ruling R71).
 * - [LAG_EQUINOX_KEMENAG]: on 21 and 22 March Kemenag's tables hold a time where the day before put
 *   it (its step at the March equinox, on those dates in 2024, 2026 and 2027 and on 21 March alone in
 *   2028, never 20 March), so on those two dates starts take the later of the day and the day before,
 *   and the ends the earlier, as [LAG_DATES_UQ] does.
 */
enum class DayRule { SAME_DAY, NEIGHBOURS_MUIS, LAG_DATES_UQ, LAG_EQUINOX_KEMENAG }

/**
 * What a [TimetableMethod.fixedPoint] bounds (ruling R45): [BOTH] the starts (the later of it and the
 * user's point) and the ends (the earlier); [ENDS_ONLY] sunrise, sunset, the end of eating and imsak
 * alone, the starts staying at the user's point (beyond a point table's reach); [TABLE] every printed
 * time at the fixed point alone (ruling R118): where the authority itself defines a town's times as its
 * table point's plus a printed figure (the PA's al-Aqsa table and its town offsets), the user's own sun
 * is not the authority's construction, and on those times riding it only adds lateness; the sunset
 * Maghrib is held to moves with the town's figure for the ends (its sunrise minutes). The ends the
 * authority never prints (Asr's at the sunset, Maghrib's red twilight) stay bounded by the user's own
 * sky as well (spec §3.5, review r2 of R118).
 */
enum class FixedPointMode { BOTH, ENDS_ONLY, TABLE }

/** When the fast begins: the authority's printed column, never after the Fajr shown. */
sealed interface EndOfEating {
    /** The authority's Fajr dawn (its minutes, without our start margin), rounded down. */
    data object SameAsFajrDawn : EndOfEating

    /**
     * The sun [degrees] below the horizon in the morning, rounded down; with [bySlot], 366
     * depressions by month and day at [curveSlot] replacing [degrees] (ruling R39: an authority's
     * own end, never read from a late start curve).
     */
    data class DawnAngle(val degrees: Double, val bySlot: DoubleArray? = null) : EndOfEating {
        init {
            require(bySlot == null || bySlot.size == 366) { "an end-of-eating curve needs 366 values" }
        }

        /** The depression on [date]. */
        fun degreesOn(date: LocalDate): Double = bySlot?.get(curveSlot(date)) ?: degrees

        /** The deepest depression it reads on any day. */
        val deepest: Double get() = bySlot?.max() ?: degrees
    }

    /**
     * [minutes] before the authority's Fajr (Jamiatul Ulama: 5): its dawn as an end, rounded down
     * without our start margin, less the minutes, like imsak.
     */
    data class MinutesBeforeFajr(val minutes: Int) : EndOfEating

    /**
     * The sun [degrees] below the horizon in the morning, rounded down; on a night where the sun
     * does not get that low, the middle of the night (last sunset to sunrise), whatever the method's
     * own high-latitude rule. The UK's 18° families print their summer Fajr, which is also when
     * their fast begins, at the middle of the night on the nights without 18° (research-uk, ruling
     * R75), while their Fajr start keeps its own rule. With [bySlot], 366 depressions by month and
     * day at [curveSlot] replacing [degrees], as [DawnAngle]'s (the Moonsighting Committee's own Fajr
     * read as an end, whose slots on the edge of the midnight sun the sun does not reach: there the
     * middle of the night, T3 round of 11 Oct 2026).
     */
    data class DawnOrMiddle(val degrees: Double, val bySlot: DoubleArray? = null) : EndOfEating {
        init {
            require(bySlot == null || bySlot.size == 366) { "an end-of-eating curve needs 366 values" }
        }

        /** The depression on [date]. */
        fun degreesOn(date: LocalDate): Double = bySlot?.get(curveSlot(date)) ?: degrees

        /** The deepest depression it reads on any day. */
        val deepest: Double get() = bySlot?.max() ?: degrees
    }
}

/**
 * What changes on the authority's Ramadan dates besides an [IshaRule.AfterMaghrib]'s own minutes.
 * [ishaFloorMinutesAfterMaghrib] is for an [IshaRule.Angle]: on the entry's Ramadan dates, Isha is
 * never earlier than the shown Maghrib plus these minutes — a floor added on top of the angle, not a
 * replacement, so the angle still wins where it falls later. Null (the default) for every method that
 * does not need one (Sudan: 90, ruling R74).
 */
data class RamadanRule(val maghribExtraSeconds: Int = 0, val ishaFloorMinutesAfterMaghrib: Int? = null)

/**
 * Another table's own day beside a method's (the owner's decision of 17 Oct 2026, question 1: past its bound the nearest
 * table printed for its own point still rides, out to about five of its reaches): [method] at [point] alone, read there
 * with its own curves, regional variants and margins, exactly as that table's own point shows it. The day shown is the
 * safe side of the method's own day at the user's point and each such day (DayComputer's alongside step): every start
 * the later, sunrise, sunset, the end of eating, imsak and every end the earlier. Unlike [TimetableMethod.envelopePoints],
 * which read the method's one set of curves at every point, each day keeps its own: the user's own day is exactly what
 * it was without the table, and the table's exactly its own, so nothing is later than the later of the two needs.
 */
data class Alongside(val point: GeoPoint, val method: TimetableMethod) {
    init {
        require(method.alongside.isEmpty()) { "${method.id}: a day alongside carries no day alongside of its own" }
    }
}

/**
 * The authority's own rule where the twilight fails (applied by `HighLatitude` and `DayComputer`).
 * - [Standard]: the MWL Fiqh Council's proportion from 45°, only when the sign is missing.
 * - [DumRtSummer]: on the mornings whose Fajr dawn (18°) the sun does not reach at any of the
 *   place's points, the end of suhoor and the Fajr shown are sunrise −
 *   [DumRtSummer.sahurBeforeSunriseMinutes] (rounded down for the one, up for the other); on the
 *   evening before each such morning Isha is Maghrib + [DumRtSummer.ishaAfterMaghribMinutes]; the
 *   real signs elsewhere. DUM RT's 2026 table applies it on exactly those nights at each of its
 *   towns, so its dates move with latitude and year (Kazan 6 May–8 Aug in 2026). A night too close
 *   to call (ruling R61) takes each event's safe side of the two: the sun's lowest within
 *   [DumRtSummer.knifeEdgeDeg] of 18° at a table's own point (the method's fixed point), or within
 *   [DumRtSummer.bandDeg] at the user's own point, where the locality DUM RT would print for may lie
 *   that much latitude away. At the user's own point, on every night, Fajr and Isha are the latest
 *   and the end of suhoor the earliest of the tables across that band (ruling R65).
 * - [NightFraction]: Fajr the fraction of last night before sunrise, Isha of tonight after sunset,
 *   only when the sign is missing.
 */
sealed interface HighLatRule {
    data object Standard : HighLatRule
    data class DumRtSummer(
        val sahurBeforeSunriseMinutes: Int = 121,
        val ishaAfterMaghribMinutes: Int = 90,
        val knifeEdgeDeg: Double = 0.01,
        val bandDeg: Double = 0.2,
    ) : HighLatRule
    data class NightFraction(val fajrFraction: Double, val ishaFraction: Double) : HighLatRule

    /**
     * The Other methods' rules, only when the sign is missing: half the night ([MIDDLE]), a seventh
     * ([SEVENTH]) or the angle's sixtieth ([ANGLE]).
     */
    data class Legacy(val kind: String) : HighLatRule {
        init {
            require(kind in KINDS) { "unknown legacy high-latitude rule '$kind'" }
        }

        companion object {
            const val MIDDLE = "middle"
            const val SEVENTH = "seventh"
            const val ANGLE = "angle"
            private val KINDS = setOf(MIDDLE, SEVENTH, ANGLE)
        }
    }
}

/**
 * One authority's published method, as data. Angles are in degrees below the horizon (positive);
 * [horizonDeg] is the sun's altitude at sunrise and sunset (negative). Every time is computed at
 * the point given and, when set, at [fixedPoint] beside it (MUIS): starts take the later of the
 * two, sunrise, sunset and the end of eating the earlier (spec §3.5); with [fixedPointMode]
 * [FixedPointMode.ENDS_ONLY] the fixed point bounds the ends alone. Starts are also computed at
 * each of [startPoints] and the latest is taken; sunrise, sunset, the end of eating and imsak at each
 * of [endPoints] and the earliest is taken. [envelopePoints] are both: other tables' own points, each
 * printed for its point, whose reach holds the user beside the fixed point's (the city-point round of
 * 11 Oct 2026, the owner's rule "rather late, never early"): every start the latest and every end the
 * earliest over them too, and each end never after this method at that point alone (DayComputer's
 * point 7). [endsBoundAtUserPoint]: where the fixed point rides
 * for the starts too, the ends shown are also never after those this method shows at the user's
 * point alone (DayComputer's point 7); set only on a table printed for its own point riding beside a
 * method that is computed at the user's own point (`tablesAtTheirPoints`, the city-point audit of
 * 10–11 Oct 2026), never on an authority's own unit, whose ends follow its table (ruling R89); and, with
 * no fixed point, where a table's point rides beside the user's as a start and an end point (London Unified
 * chosen beyond R45's three reaches, round 2's fix of 11 Oct 2026), the ends never after those the method
 * shows at the user's point with no other point beside it. [alongside]: other tables' own days, each computed apart at
 * its own point ([Alongside]), whose safe side with this method's own day is the day shown (the nearest table's ride
 * past its bound, the owner's decision of 17 Oct 2026).
 *
 * - [twilightDipDeg] deepens the Fajr and Isha angles (INM, JAKIM highland zones).
 * - [fajrDeclinationBias] (degrees, by day of year) is added to the sun's declination for Fajr, and
 *   [asrBiasFactor] times it for Asr (Umm al-Qura).
 * - [authorityMinutes] are the authority's own offsets (Diyanet's temkin); [margins] are ours, in
 *   seconds, fitted to reproduce its minutes and rounding.
 * - [fixedDhuhrLocalMinutes]: Dhuhr is never before this local time (minutes after midnight).
 * - [fajrAfterDawnMinutes]: the Fajr shown begins this long after the dawn (Kosovo: imsak + 20).
 * - [imsakMinutesBeforeFajr]: the printed imsak, an end: the authority's dawn (its Fajr minutes
 *   and [endOfEatingMarginSeconds], rounded down) less these minutes.
 * - [fajrAngleByDayOfYear] / [ishaAngleByDayOfYear]: 366 angles, one per month and day at
 *   [curveSlot] (29 February is slot 59 in every year), replacing [fajrAngle] / an
 *   [IshaRule.Angle]'s degrees (London Unified).
 * - [monthlyOffsets]: 12 values in seconds per prayer (index month − 1), added before rounding like
 *   [authorityMinutes] (Tunisia's monthly Asr).
 * - [clockRule]: the authority's own clock rule, applied last with these margins (IRN Tromsø's
 *   Makkah time, ruling R82); see [ClockTimes] for what the day shows.
 * - [dayAroundDhuhrMinutes]: the authority's day is never shorter than twice these minutes around
 *   its Dhuhr (the transit plus its Dhuhr minutes): its sunrise no later than Dhuhr − minutes, its
 *   Maghrib no earlier than Dhuhr + minutes, each with the event's own margin (Diyanet's winter
 *   takdir at Trondheim: a 5-hour day, 150; monitor round, brief D). An [IshaRule.AfterMaghrib]
 *   still counts from the sun's own sunset.
 * - [declaresIshaBeforeMaghrib]: the authority's own Isha can fall at or before the Maghrib shown
 *   (Diyanet's from Umeå north in June, counted from a Maghrib its nineteen-hour day puts before the
 *   sunset); such a day shows Isha the minute after Maghrib and declares it not followed
 *   (DayComputer's point 9). Off, an Isha at or before Maghrib stays out of order for
 *   [world.taqwa.app.prayer.engine.day.Invariants.repair] and the gate to flag (review D round 1).
 * - [fixedTableYear]: the authority prints one fixed table, the same month-day times every year,
 *   on a fixed clock (the Islamic Foundation of Ireland's, in GMT): every date is computed as the
 *   same month and day of this year, the table's own, and moved onto the date at the same instant
 *   of the UTC day, so that each year shows the table's times and not the year's own sun (a 29
 *   February the year lacks is both its 28 February and its 1 March, as ruling R29 reads a phase
 *   year: starts the later, ends the earlier). The date's own sun still bounds what the table's
 *   year cannot know: the sunrise shown never after the sun's own, Maghrib never before the minute
 *   after the sun's own sunset, the sunset, Asr's end and the red twilight the date's own
 *   (DayComputer). Only for a method whose rules read no other date: no Ramadan rule (nor an Isha
 *   counted otherwise in Ramadan), day rule, clock rule or phase year, and no table carried to a
 *   town by a printed figure ([FixedPointMode.TABLE]), whose sunset is the table's.
 * - [fixedTableOwnDawn]: under [fixedTableYear], the dawn is also the date's own: Fajr never before,
 *   and the end of eating and imsak never after, what the method gives on the date's own sun (IZ
 *   BiH's edge, where no table is held and Taqwa keeps the real dawn, spec §10.8).
 *
 * The arrays compare by reference in [equals]; methods are registry singletons.
 */
data class TimetableMethod(
    val id: String,
    val fajrAngle: Double,
    val isha: IshaRule,
    val sunModel: SunModel = SunModel.EXACT,
    val phaseYear: Int? = null,
    val horizonDeg: Double = -0.8333,
    val twilightDipDeg: Double = 0.0,
    val asrModel: AsrModel = AsrModel.EXACT_MOMENT,
    val fajrDeclinationBias: Harmonics? = null,
    val asrBiasFactor: Double = 0.0,
    val authorityMinutes: EventOffsets = EventOffsets(),
    val margins: EventOffsets = EventOffsets(),
    val dayRule: DayRule = DayRule.SAME_DAY,
    val ramadan: RamadanRule? = null,
    val fixedDhuhrLocalMinutes: Int? = null,
    val endOfEating: EndOfEating = EndOfEating.SameAsFajrDawn,
    val endOfEatingMarginSeconds: Int = 0,
    val imsakMinutesBeforeFajr: Int? = null,
    val fajrAfterDawnMinutes: Int = 0,
    val highLatitude: HighLatRule = HighLatRule.Standard,
    val startPoints: List<GeoPoint> = emptyList(),
    val endPoints: List<GeoPoint> = emptyList(),
    val envelopePoints: List<GeoPoint> = emptyList(),
    val fixedPoint: GeoPoint? = null,
    val fixedPointMode: FixedPointMode = FixedPointMode.BOTH,
    val endsBoundAtUserPoint: Boolean = false,
    val fajrAngleByDayOfYear: DoubleArray? = null,
    val ishaAngleByDayOfYear: DoubleArray? = null,
    val monthlyOffsets: Map<Prayer, IntArray>? = null,
    val clockRule: ClockRule? = null,
    val dayAroundDhuhrMinutes: Int? = null,
    val declaresIshaBeforeMaghrib: Boolean = false,
    val fixedTableYear: Int? = null,
    val fixedTableOwnDawn: Boolean = false,
    val alongside: List<Alongside> = emptyList(),
) {
    init {
        require(dayAroundDhuhrMinutes == null || dayAroundDhuhrMinutes in 1 until 12 * 60) {
            "$id: dayAroundDhuhrMinutes is half a day's minimum length, under 12 hours"
        }
        require(fajrAngleByDayOfYear == null || fajrAngleByDayOfYear.size == DAYS_IN_CURVE) {
            "$id: fajrAngleByDayOfYear needs $DAYS_IN_CURVE values"
        }
        require(ishaAngleByDayOfYear == null || ishaAngleByDayOfYear.size == DAYS_IN_CURVE) {
            "$id: ishaAngleByDayOfYear needs $DAYS_IN_CURVE values"
        }
        require(ishaAngleByDayOfYear == null || isha is IshaRule.Angle) {
            "$id: ishaAngleByDayOfYear replaces an IshaRule.Angle"
        }
        require(monthlyOffsets == null || monthlyOffsets.values.all { it.size == 12 }) {
            "$id: monthlyOffsets need 12 values per prayer"
        }
        require(fixedDhuhrLocalMinutes == null || fixedDhuhrLocalMinutes in 0 until 24 * 60) {
            "$id: fixedDhuhrLocalMinutes is a local time of day"
        }
        require(
            fixedTableYear == null || (
                ramadan == null && dayRule == DayRule.SAME_DAY && clockRule == null && phaseYear == null &&
                    (isha !is IshaRule.AfterMaghrib || isha.ramadanMinutes == isha.minutes)
                ),
        ) {
            "$id: a fixed table's year reads no other date's rules (Ramadan, day, clock, phase year)"
        }
        require(fixedTableYear == null || fixedPointMode != FixedPointMode.TABLE) {
            "$id: a fixed table's day is held to the date's own sunset, not a table point's carried by a printed figure"
        }
        require(!fixedTableOwnDawn || fixedTableYear != null) {
            "$id: fixedTableOwnDawn bounds a fixed table's dawn (fixedTableYear)"
        }
    }

    private companion object {
        const val DAYS_IN_CURVE = 366
    }
}

/** The leap year whose calendar numbers a day-of-year curve's 366 slots (ruling R28). */
const val CURVE_REFERENCE_YEAR = 2028

/**
 * [date]'s slot in a day-of-year curve (ruling R28): its month and day on the 366-day leap
 * reference year, so 29 February is slot 59 and 1 March slot 60 in every year and a curve's value
 * for a date does not move with the leap cycle.
 */
fun curveSlot(date: LocalDate): Int = LocalDate(CURVE_REFERENCE_YEAR, date.month, date.day).dayOfYear - 1
