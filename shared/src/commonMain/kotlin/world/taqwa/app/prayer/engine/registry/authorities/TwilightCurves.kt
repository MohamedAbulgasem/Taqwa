package world.taqwa.app.prayer.engine.registry.authorities

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.method.CURVE_REFERENCE_YEAR
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.data.EndOfEatingDawns
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * Twilight rules that are minutes or fractions of the night rather than angles, turned into the
 * day-of-year angle curves a [TimetableMethod] carries ([TimetableMethod.fajrAngleByDayOfYear]),
 * for one latitude, in closed form (a few hundred microseconds a curve).
 *
 * - Slots follow ruling R28: slot i is the month and day of 1 Jan 2028 + i (29 February is slot
 *   59), read by the engine at `curveSlot(date)`; seasonal windows are month-day windows.
 * - Each slot's angle is the sun's depression at the rule's moment with that day's noon
 *   declination. A morning rule counts the shorter of the nights around the day and an evening
 *   rule the longer, so either definition of "the night" gives a Fajr no earlier and an Isha no
 *   earlier.
 * - Each value is then an envelope over its neighbouring slots (a Fajr takes the smallest depression
 *   of the day before, the day and the day after, an Isha the largest), so the leap cycle's drift
 *   of up to a day in the sun's position cannot make a start early.
 * - Where the sun does not rise or set the angle stays as it was and the day is the high-latitude
 *   rules' (Task 3).
 *
 * Provenance (ruling R42): these curves are computed from rules (fractions and minutes of the night),
 * not read from any table; where a rule's parameters were fitted to an authority's tables,
 * [PlaceCurves] names them at each rule. [endOfEating] reads [EndOfEatingDawns], whose depressions
 * each say which tables they come from. Every curve is an envelope of the sun's depressions, not
 * printed times, and no table is committed.
 */
internal object TwilightCurves {
    private const val SLOTS = 366
    private const val HORIZON = -0.8333
    private val referenceStart = LocalDate(CURVE_REFERENCE_YEAR, 1, 1)

    /**
     * One slot of the reference year (or of another year of its leap cycle, [fajrAsEnd]) at a
     * latitude; [delta] is the sun's declination at noon and [deltaPerDay] how fast it moves (radians a
     * day, where known).
     */
    class Day(
        val index: Int,
        val date: LocalDate,
        val phi: Double,
        val delta: Double,
        private val h0: Double,
        h0Before: Double,
        h0After: Double,
        private val deltaPerDay: Double = 0.0,
    ) {
        private val nightBefore = (360.0 - h0Before - h0) * 4.0
        private val nightAfter = (360.0 - h0 - h0After) * 4.0

        /** The night a morning rule counts, in minutes: the shorter one around the day (a later Fajr). */
        val morningNight: Double get() = min(nightBefore, nightAfter)

        /** The night an evening rule counts, in minutes: the longer one around the day (a later Isha). */
        val eveningNight: Double get() = max(nightBefore, nightAfter)

        /** The night from the day's sunset to the next sunrise, in minutes (adhan-js's for the day's Fajr). */
        val followingNight: Double get() = nightAfter

        /** From sunrise to the transit, in minutes: half the sun's own day. */
        val halfDayMinutes: Double get() = h0 * 4.0

        /** The sun's depression [minutes] after sunset (or, the same, before sunrise). */
        fun depression(minutes: Double): Double = depressionAt(phi, delta, h0 + minutes / 4.0)

        /** How many minutes before sunrise the sun is [depression] below the horizon (the middle of the night where it never is). */
        fun minutesBefore(depression: Double): Double = ((hourAngle(phi, delta, -depression) ?: 180.0) - h0) * 4.0

        /** The deepest the sun sinks that night. */
        val deepest: Double get() = depressionAt(phi, delta, 180.0)

        /**
         * The sun's depression [minutes] before sunrise, taking the declination at sunrise and at that
         * moment rather than at noon: for an end, where in a summer night at 53° a tenth of a degree
         * is a minute or two.
         */
        fun morningDepression(minutes: Double): Double {
            val h0Here = hourAngle(phi, delta - deltaPerDay * h0 / 360.0, HORIZON) ?: h0
            val h = h0Here + minutes / 4.0
            return depressionAt(phi, delta - deltaPerDay * h / 360.0, h)
        }
    }

    /**
     * Per slot the value [angle] gives, [fallback] where the sun does not rise or set. The sun is
     * taken at [latitude] rounded to 0.1° ([onGrid], every curve's default), or at [latitude] itself.
     */
    private fun perSlot(latitude: Double, fallback: Double, onGrid: Boolean = true, angle: (Day) -> Double): DoubleArray {
        val phi = SolarMath.rad(if (onGrid) round(latitude * 10.0) / 10.0 else latitude)
        // Slots −1 .. 366: the days either side of the reference year, for the nights.
        val declinations = DoubleArray(SLOTS + 2) { i ->
            val noon = referenceStart.plus(i - 1, DateTimeUnit.DAY).toEpochDays() * SolarMath.SECONDS_PER_DAY + 12 * 3600.0
            SolarMath.rad(SolarMath.sun(SolarMath.julianDay(noon)).declinationDeg)
        }
        val halfDays = DoubleArray(SLOTS + 2) { i -> hourAngle(phi, declinations[i], HORIZON) ?: Double.NaN }
        return DoubleArray(SLOTS) { i ->
            val h0 = halfDays[i + 1]
            if (h0.isNaN()) {
                fallback
            } else {
                val before = halfDays[i].takeUnless { it.isNaN() } ?: h0
                val after = halfDays[i + 2].takeUnless { it.isNaN() } ?: h0
                angle(Day(i, referenceStart.plus(i, DateTimeUnit.DAY), phi, declinations[i + 1], h0, before, after))
            }
        }
    }

    /** A Fajr's envelope: each slot the smallest depression of itself and its neighbours (the latest). */
    private fun fajrEnvelope(values: DoubleArray) = DoubleArray(SLOTS) { i ->
        minOf(values[(i + SLOTS - 1) % SLOTS], values[i], values[(i + 1) % SLOTS])
    }

    /** An Isha's envelope: each slot the largest depression of itself and its neighbours (the latest). */
    private fun ishaEnvelope(values: DoubleArray) = DoubleArray(SLOTS) { i ->
        maxOf(values[(i + SLOTS - 1) % SLOTS], values[i], values[(i + 1) % SLOTS])
    }

    /**
     * A Fajr never before [fraction] of the night before sunrise: the later (the smaller depression)
     * of [angle] and that moment; [fraction] null on days the rule does not apply. [onGrid] as
     * [perSlot]'s: the sun at the latitude rounded to 0.1°, unless false.
     */
    fun fajr(latitude: Double, angle: Double, onGrid: Boolean = true, fraction: (Day) -> Double?): DoubleArray = fajrEnvelope(
        perSlot(latitude, angle, onGrid) { d -> fraction(d)?.let { min(angle, d.depression(it * d.morningNight)) } ?: angle },
    )

    /**
     * An Isha no later than [fraction] of the night after sunset where the authority's code says so:
     * the earlier (the smaller depression) of [angle] and that moment, then the envelope's later.
     * [onGrid] as [fajr]'s.
     */
    fun isha(latitude: Double, angle: Double, onGrid: Boolean = true, fraction: (Day) -> Double?): DoubleArray = ishaEnvelope(
        perSlot(latitude, angle, onGrid) { d -> fraction(d)?.let { min(angle, d.depression(it * d.eveningNight)) } ?: angle },
    )

    /**
     * Ruling R80: a Fajr rule's own dawn read as an end, for an authority whose code prints its Fajr
     * as the start of the fast (the ICCI's, adhan-js's TwilightAngle rule): each slot the moment
     * [fraction] of the night before sunrise, or the sun [angle] down where that comes later, as the
     * code computes it, with the night it counts (the day's sunset to the next sunrise,
     * [Day.followingNight]; the ICCI's 2026 table follows it to the minute on every day) and the sun's
     * declination at that moment ([Day.morningDepression]). Never the start's curve, whose slots are
     * widened late and take the shorter night.
     *
     * The leap cycle (ruling R28's reason for its envelopes) is taken on the month and day itself: each
     * slot is the largest depression that rule gives on that month and day in each year of a leap
     * cycle ([leapCycle]), so the end is never after the rule's dawn in any year, without widening it
     * over the day either side, which in an Irish summer night, where the sun moves slowly under the
     * horizon, cost up to four minutes more. Never deeper than the sun sinks those nights, less
     * [REACH_SPARE_DEG], so the angle is always reached; where the sun does not rise or set, [angle].
     */
    fun fajrAsEnd(latitude: Double, angle: Double, fraction: (Day) -> Double?): DoubleArray =
        ruleAsEnd(latitude, angle) { d -> fraction(d)?.let { min(angle, d.morningDepression(it * d.followingNight)) } ?: angle }

    /**
     * The Moonsighting Committee's Fajr read as an end, as [fajrAsEnd] reads a night rule (by month
     * and day over a leap cycle, the declination at the moment, never deeper than the sun sinks): the
     * later of 18° and its seasonal minutes before sunrise, from 55° N the later of a seventh of the
     * night and those minutes, as adhan2's code has them (the minutes at [latitude] itself, from each
     * date's own day of the year and year's length; the night from sunset to the next sunrise). Its
     * minutes are neither an angle nor a fraction of the night, so no other latitude's dawns carry it
     * ([endOfEating]): the Grande Mosquée de Paris's end is floored with it where it applies
     * (PlaceCurves, ruling R80's fix round).
     */
    fun moonsightingAsEnd(latitude: Double): DoubleArray = ruleAsEnd(latitude, MOONSIGHTING_ANGLE) { d ->
        val minutes = morningMinutes(latitude, daysSinceSolstice(d.date, latitude))
        if (latitude >= SEVENTH_FROM_LATITUDE) {
            d.morningDepression(min(d.followingNight / 7, minutes))
        } else {
            min(MOONSIGHTING_ANGLE, d.morningDepression(minutes))
        }
    }

    /**
     * A morning rule read as an end ([fajrAsEnd], [moonsightingAsEnd]): each slot the largest
     * [depression] of that month and day over the [leapCycle], never deeper than the sun sinks those
     * nights less [REACH_SPARE_DEG]; [angle] where the sun does not rise or set on one of them.
     */
    private fun ruleAsEnd(latitude: Double, angle: Double, depression: (Day) -> Double): DoubleArray {
        val days = leapCycle.map { year -> daysIn(year, latitude) }
        val years = days.map { year -> DoubleArray(SLOTS) { i -> year[i]?.let(depression) ?: Double.NaN } }
        val deepest = days.map { year -> DoubleArray(SLOTS) { i -> year[i]?.deepest ?: Double.NaN } }
        return DoubleArray(SLOTS) { i ->
            val value = years.maxOf { it[i] }
            val reach = deepest.minOf { it[i] } - REACH_SPARE_DEG
            when {
                value.isNaN() -> angle
                reach.isNaN() -> value
                else -> min(value, reach)
            }
        }
    }

    /**
     * Each slot's month and day in [year] (29 February, in a common year, on the 28th) at a latitude,
     * with its noon declination and how fast it moves; null where the sun does not rise or set. For
     * [year] 2028 the same days as [perSlot].
     */
    private fun daysIn(year: Int, latitude: Double): Array<Day?> {
        val phi = SolarMath.rad(round(latitude * 10.0) / 10.0)
        val length = LocalDate(year, 12, 31).dayOfYear
        val first = LocalDate(year, 1, 1)
        // By day of the year, 0 .. length + 1: the days either side of the year, for the nights.
        val declinations = DoubleArray(length + 2) { k ->
            val noon = first.plus(k - 1, DateTimeUnit.DAY).toEpochDays() * SolarMath.SECONDS_PER_DAY + 12 * 3600.0
            SolarMath.rad(SolarMath.sun(SolarMath.julianDay(noon)).declinationDeg)
        }
        val halfDays = DoubleArray(length + 2) { k -> hourAngle(phi, declinations[k], HORIZON) ?: Double.NaN }
        return Array(SLOTS) { i ->
            val slot = referenceStart.plus(i, DateTimeUnit.DAY)
            val date = if (length != SLOTS && slot.month.number == 2 && slot.day == 29) LocalDate(year, 2, 28) else LocalDate(year, slot.month, slot.day)
            val k = date.dayOfYear
            val h0 = halfDays[k]
            if (h0.isNaN()) {
                null
            } else {
                val before = halfDays[k - 1].takeUnless { it.isNaN() } ?: h0
                val after = halfDays[k + 1].takeUnless { it.isNaN() } ?: h0
                Day(i, date, phi, declinations[k], h0, before, after, (declinations[k + 1] - declinations[k - 1]) / 2)
            }
        }
    }

    /** An Isha [minutes] after sunset. */
    fun ishaAfterSunset(latitude: Double, fallback: Double, minutes: (Day) -> Double): DoubleArray =
        ishaEnvelope(perSlot(latitude, fallback) { d -> d.depression(minutes(d)) })

    /**
     * Ruling R39: an end of eating at an authority's own earliest dawns, [depressions] by slot at its
     * table's [tableLatitude], as it applies at [latitude]:
     * - north of the table the same depression comes earlier before sunrise, so it stands;
     * - south of it, the larger of it and the depression at the same fraction of the night (the
     *   table's shorter night, the place's longer), whether the authority's rule is an angle or a
     *   fraction of the night, so the end is never later. Only for those two: a rule in minutes that
     *   vary with latitude (the Moonsighting Committee's, whose January Fajr takes a larger share of
     *   the night further south) is not carried by either, and needs its own rule read at the place
     *   ([moonsightingAsEnd]);
     * - each slot then the largest of its neighbours, and never deeper than the sun sinks there on
     *   the day and its neighbours less [REACH_SPARE_DEG], so the angle is always reached.
     */
    fun endOfEating(latitude: Double, tableLatitude: Double, depressions: DoubleArray): DoubleArray {
        val fractions = perSlot(tableLatitude, 0.0) { d -> d.minutesBefore(depressions[d.index]) / d.morningNight }
        val south = latitude < tableLatitude
        val here = perSlot(latitude, Double.NaN) { d ->
            val own = depressions[d.index]
            if (south) max(own, d.depression(fractions[d.index] * d.eveningNight)) else own
        }
        val deepest = perSlot(latitude, Double.NaN) { d -> d.deepest }
        fun reach(i: Int): Double = minOf(deepest[(i + SLOTS - 1) % SLOTS], deepest[i], deepest[(i + 1) % SLOTS]) - REACH_SPARE_DEG
        val widened = DoubleArray(SLOTS) { i ->
            val v = maxOf(here[(i + SLOTS - 1) % SLOTS], here[i], here[(i + 1) % SLOTS])
            if (v.isNaN()) depressions[i] else v
        }
        return DoubleArray(SLOTS) { i -> reach(i).let { r -> if (r.isNaN()) widened[i] else min(widened[i], r) } }
    }

    /** Moonsighting Committee's evening function (shafaq general), minutes after sunset. */
    fun moonsightingEveningMinutes(d: Day): Double {
        val lat = SolarMath.deg(d.phi)
        return eveningMinutes(lat, daysSinceSolstice(d.date, lat))
    }

    /**
     * The MWL Fiqh Council's proportion (1986): the fraction of the night the Fajr at [angle] takes at
     * 45° in the same hemisphere on the same day.
     */
    fun fractionAt45(d: Day, angle: Double): Double {
        val phi45 = SolarMath.rad(if (d.phi < 0) -45.0 else 45.0)
        val h0 = hourAngle(phi45, d.delta, HORIZON) ?: return 0.0
        val hFajr = hourAngle(phi45, d.delta, -angle) ?: 180.0
        return (hFajr - h0) / (360.0 - 2 * h0)
    }

    /** The slot's month number, for rules with a season (month-day windows, ruling R28). */
    fun month(d: Day): Int = d.date.month.number

    /**
     * Moonsighting Committee's seasonal twilight (adhan2's seasonAdjustedMorningTwilight and
     * seasonAdjustedEveningTwilight, shafaq general): the Fajr the later of 18° and the morning
     * function, the Isha the earlier of 18° and the evening function; from 55° N a seventh of the
     * night takes 18°'s place.
     */
    fun moonsighting(latitude: Double): Pair<DoubleArray, DoubleArray> {
        val lat = round(latitude * 10.0) / 10.0
        val fajr = perSlot(lat, MOONSIGHTING_ANGLE) { d ->
            val minutes = morningMinutes(lat, daysSinceSolstice(d.date, lat))
            if (lat >= SEVENTH_FROM_LATITUDE) {
                d.depression(min(d.morningNight / 7, minutes))
            } else {
                min(MOONSIGHTING_ANGLE, d.depression(minutes))
            }
        }
        val isha = perSlot(lat, MOONSIGHTING_ANGLE) { d ->
            val minutes = eveningMinutes(lat, daysSinceSolstice(d.date, lat))
            if (lat >= SEVENTH_FROM_LATITUDE) {
                d.depression(min(d.eveningNight / 7, minutes))
            } else {
                min(MOONSIGHTING_ANGLE, d.depression(minutes))
            }
        }
        return fajrEnvelope(fajr) to ishaEnvelope(isha)
    }

    private fun hourAngle(phi: Double, delta: Double, altitude: Double): Double? {
        val c = (sin(SolarMath.rad(altitude)) - sin(phi) * sin(delta)) / (cos(phi) * cos(delta))
        return if (c <= -1.0 || c >= 1.0) null else SolarMath.deg(acos(c))
    }

    private fun depressionAt(phi: Double, delta: Double, hourAngleDeg: Double): Double {
        val h = SolarMath.rad(min(hourAngleDeg, 180.0))
        return -SolarMath.deg(asin(sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(h)))
    }

    private fun seasonal(a: Double, b: Double, c: Double, d: Double, dyy: Int): Double = when {
        dyy < 91 -> a + (b - a) / 91.0 * dyy
        dyy < 137 -> b + (c - b) / 46.0 * (dyy - 91)
        dyy < 183 -> c + (d - c) / 46.0 * (dyy - 137)
        dyy < 229 -> d + (c - d) / 46.0 * (dyy - 183)
        dyy < 275 -> c + (b - c) / 46.0 * (dyy - 229)
        else -> b + (a - b) / 91.0 * (dyy - 275)
    }

    private fun morningMinutes(lat: Double, dyy: Int): Double {
        val l = abs(lat) / 55.0
        return seasonal(75 + 28.65 * l, 75 + 19.44 * l, 75 + 32.74 * l, 75 + 48.10 * l, dyy)
    }

    private fun eveningMinutes(lat: Double, dyy: Int): Double {
        val l = abs(lat) / 55.0
        return seasonal(75 + 25.60 * l, 75 + 2.050 * l, 75 - 9.210 * l, 75 + 6.140 * l, dyy)
    }

    /**
     * adhan2's daysSinceSolstice for [date], with its year's length; on the (leap) reference year's
     * slots the start curves' (the envelope covers a common year's day).
     */
    private fun daysSinceSolstice(date: LocalDate, lat: Double): Int {
        val daysInYear = LocalDate(date.year, 12, 31).dayOfYear
        return if (lat >= 0) {
            val d = date.dayOfYear + 10
            if (d >= daysInYear) d - daysInYear else d
        } else {
            val d = date.dayOfYear - if (daysInYear == SLOTS) 173 else 172
            if (d < 0) d + daysInYear else d
        }
    }

    private const val MOONSIGHTING_ANGLE = 18.0
    private const val SEVENTH_FROM_LATITUDE = 55.0

    /** How far short of the sun's deepest an end-of-eating depression stays, so that it is reached. */
    private const val REACH_SPARE_DEG = 0.25

    /** One leap cycle: the sun on a month and day moves by up to about three quarters of a day across it. */
    private val leapCycle = listOf(2025, 2026, 2027, CURVE_REFERENCE_YEAR)
}

/**
 * The methods whose Fajr or Isha follow a rule in minutes or fractions of the night even while the
 * sun still reaches their angle, by method id, and the curves each gets at a place. Task 3 applies
 * an authority's [world.taqwa.app.prayer.engine.method.HighLatRule] only where the sign is missing;
 * these authorities apply theirs whenever it gives a later Fajr, so the real angle alone would put
 * Fajr early (DUM RF's 2026 tables are later than 18° by up to about 45 min in August, at sunrise −
 * 0.3 × night). A Fajr rule gives the later of the angle and the rule. An Isha rule (the earlier of
 * the two) is used only where the authority's code fixes its fraction (adhan-js, praytimes);
 * elsewhere the real angle stays, late but never early (DUM RF's, Diyanet's and EMB's Isha fractions
 * vary by season: taking one put Isha up to 29 min early in spring).
 */
internal object PlaceCurves {
    /**
     * [ownLatitude]: the [fajr] and [isha] curves are also computed at the place's own latitude, not
     * only on the 0.1° grid, and each slot takes the later start of the two; the end of eating, where
     * the method reads it from its Fajr dawn ([EndOfEating.SameAsFajrDawn]), becomes that dawn's
     * earlier slot of the two ([EndOfEating.DawnAngle]). For a rule whose curve moves fast with
     * latitude, the grid alone leaves a place up to 0.05° from its own curve: south of it the Fajr
     * comes early, north of it the end of eating comes late (QMDB's AngleBased rule in northern
     * Kazakhstan, kz-north-west round of 6 Oct 2026).
     */
    private class Rule(
        val fajr: ((TwilightCurves.Day) -> Double?)? = null,
        val isha: ((TwilightCurves.Day) -> Double?)? = null,
        val moonsightingFajr: Boolean = false,
        val ishaAfterSunsetMinutes: ((TwilightCurves.Day) -> Double)? = null,
        val ownLatitude: Boolean = false,
    )

    private fun TwilightCurves.Day.latitude() = SolarMath.deg(phi)

    /**
     * DUM RF, 2025–26: Fajr sunrise − 0.3 × night when later than 18°; Isha sunset + 0.305 × night
     * when earlier than 15° (its rows run 0.25–0.301 by season, so the most of them, never early).
     * Fitted to its Moscow tables (not committed); at Moscow itself its own rows (Russia.moscowMethod).
     */
    private val dumRf = Rule(fajr = { 0.3 }, isha = { 0.305 })

    /**
     * Diyanet's European takdir north of 44.5°, away from its own city tables (whose point-table units
     * carry each table's own curves, Diyanet.cityMethod, under their own ids): fitted in core summer as
     * Fajr = sunrise − 20.6 % and Isha = Maghrib (sunset + 7) + 18.5–19 % of the night; with its
     * 20-minute transitions its Fajr ran up to 4 min after 20.6 % (Paris, May 2027), so 19 % here.
     * Fitted to its 2026–27 tables for eleven European cities (not committed). Isha keeps the plain
     * 16° (its fraction varies by season), which also drops a city's own Isha curve from the edge
     * beyond that city's reach, where the curve's depressions do not hold.
     *
     * Where the sun's own day is longer than Diyanet's cap (monitor round, brief D: its Nordic tables
     * hold the printed day to nineteen hours around Dhuhr and print Fajr 57–65 min before that capped
     * sunrise, never later than [DIYANET_CAPPED_FAJR_BEFORE_DHUHR] min before Dhuhr over 22 tables
     * from 55.6° to 69.6° N), 19 % of the night ran up to 42 min before Diyanet's Fajr at Trondheim:
     * the Fajr is then no earlier than that many minutes before Diyanet's Dhuhr (the transit + 5),
     * the later of the two. North of about 64.5° that moment comes after the sun has risen, as
     * Diyanet's own Fajr does there; the engine keeps Fajr before the sunrise it shows and declares it
     * (ruling R90), as at Tromsø under IRN.
     */
    private val diyanetEurope = Rule(
        fajr = { d ->
            if (d.latitude() > 44.5) {
                val cappedFajrBeforeSunrise = DIYANET_CAPPED_FAJR_BEFORE_DHUHR - DIYANET_DHUHR_MINUTES - d.halfDayMinutes
                min(0.19, cappedFajrBeforeSunrise / d.morningNight)
            } else {
                null
            }
        },
        isha = { null },
    )

    /**
     * EMB (Task 7g, fitted to its 2026 Brussels table, not committed). From 1 May to 31 July above 45°
     * its Fajr (and imsak) is the later of 18° and the earlier of a clock-time floor at Brussels and the MWL
     * proportion from 45°; from 1 May to 31 August its Isha is the earlier of 18° and the later of a clock-time
     * cap at Brussels (a few minutes later from July) and that proportion. The clock times are the table's,
     * kept here only as minutes after 00:00 UTC; the neighbour envelope carries the Fajr cap to 1 August.
     */
    private val emb = Rule(
        fajr = { d ->
            if (d.latitude() > 45.0 && TwilightCurves.month(d) in 5..7) {
                max(TwilightCurves.fractionAt45(d, 18.0), embClockFraction(d, EMB_FAJR_CAP_UTC, morning = true))
            } else {
                null
            }
        },
        isha = { d ->
            if (d.latitude() > 45.0 && TwilightCurves.month(d) in 5..8) {
                val cap = if (TwilightCurves.month(d) <= 6) EMB_SPRING_ISHA_CAP_UTC else EMB_ISHA_CAP_UTC
                max(TwilightCurves.fractionAt45(d, EMB_ISHA_PROPORTION_ANGLE), embClockFraction(d, cap, morning = false))
            } else {
                null
            }
        },
    )

    /**
     * The fraction of the night a clock time (minutes after 00:00 UTC) at EMB's Brussels point is before
     * sunrise ([morning]) or after sunset, on [d]'s date; 0 where it is on the other side.
     */
    private fun embClockFraction(d: TwilightCurves.Day, utcMinutes: Double, morning: Boolean): Double {
        val c = (sin(SolarMath.rad(-0.8333)) - sin(d.phi) * sin(d.delta)) / (cos(d.phi) * cos(d.delta))
        if (c <= -1.0 || c >= 1.0) return 0.0
        val h0 = SolarMath.deg(acos(c))
        val noonEpoch = d.date.toEpochDays() * SolarMath.SECONDS_PER_DAY + 12 * 3600.0
        val eot = SolarMath.sun(SolarMath.julianDay(noonEpoch)).equationOfTimeMinutes
        val noon = 720.0 - EMB_LON * 4.0 - eot
        val minutes = if (morning) noon - h0 * 4.0 - utcMinutes else utcMinutes - (noon + h0 * 4.0)
        return max(0.0, minutes) / if (morning) d.morningNight else d.eveningNight
    }

    /**
     * adhan-js's TwilightAngle rule (ICCI): Fajr no earlier than 18/60 of the night, Isha no later than
     * 17/60 (the ICCI site's documented code; checked on its Dublin table, not committed).
     */
    private val icci = Rule(fajr = { ICCI_FAJR_PORTION }, isha = { ICCI_ISHA_PORTION })

    /**
     * praytimes.js's AngleBased rule (QMDB): 15/60 of the night each side (its documented code). In
     * northern Kazakhstan the rule's moment moves by up to about a minute for every 0.1° of latitude
     * while it binds, so its curves are also read at the place's own latitude ([Rule.ownLatitude]):
     * on the grid alone, QMDB's own Bugrovoe, Spasovka and Oskemen (each just short of a rounding
     * step, so read about 0.05° south of itself) had a Fajr 1 min before QMDB's printed one on 14,
     * 11 and 2 days of 2026–27, from the solstice to mid-August, and its Kulomzino and Vagulino (just
     * past one, read 0.05° north) an end of eating 1 min after it on 2 days each in spring 2027.
     */
    private val qmdb = Rule(fajr = { 15.0 / 60 }, isha = { 15.0 / 60 }, ownLatitude = true)

    /**
     * IRN: its units carry their own curves (Europe.irnUnits: its Oslo and Trondheim calendars' own, ruling
     * R48; Tromsø's and the edge's the hour before sunrise from April to September), so no rule here.
     */
    private val irn = Rule()

    /**
     * The UK's 15° family on Mawaqit (Task 7g): Fajr the later of its angle and the MWL Fiqh Council's
     * proportion from 45° for that angle, all year (Birmingham's 15° tables apply it from early May while
     * 15° still exists; fitted by the survey, the tables not committed).
     */
    private val gbFifteen = Rule(fajr = { d -> TwilightCurves.fractionAt45(d, 14.6) })

    /**
     * Rabita Helsinki's family (Task 7g, its 2026 Mawaqit calendar, not committed): Isha at 17° while the
     * sun reaches it, else Maghrib (sunset + 3) + 88.
     */
    private val seRabita = Rule(
        ishaAfterSunsetMinutes = { d -> if (d.deepest > 17.0) d.minutesBefore(17.0) else 91.0 },
    )

    /**
     * Canada outside Toronto, Montreal and Ottawa (Task 7g, fitted to the Calgary and Edmonton mosques' 2026
     * tables, not committed). Calgary Islamic Centre SW's family (15°, Isha Maghrib + 90) keeps its Fajr no
     * earlier than 23.5 % of the night before sunrise. The Edmonton mosques' 18° family (Al Omari, Al Ansar,
     * Al Farooq) keeps its Fajr no earlier than a quarter of the night before sunrise, or the MWL Fiqh
     * Council's proportion from 45° where that is earlier (in summer, where the sun misses 18°); its Isha is the earlier of 18° and a
     * sixth of the night after sunset (its spring and autumn tables stay within it; in summer, Maghrib + 90
     * covers them). Edmonton's 15° family (Markaz Al Imam Malik) keeps its Fajr no earlier than a quarter of
     * the night and its Isha (16.5°) no later than 28 % of the night after sunset.
     */
    private val caIsha90 = Rule(fajr = { CA_ISHA90_FAJR_FRACTION })
    private val caFifteen = Rule(fajr = { CA_FIFTEEN_FAJR_FRACTION }, isha = { CA_FIFTEEN_ISHA_FRACTION })
    private val caEighteen = Rule(
        fajr = { d -> max(CA_EIGHTEEN_FAJR_FRACTION, TwilightCurves.fractionAt45(d, 18.0)) },
        isha = { CA_EIGHTEEN_ISHA_FRACTION },
    )

    /**
     * The Grande Mosquée de Paris (Task 7g): from January to September 2026 the Moonsighting
     * Committee's seasonal Fajr and Isha = sunset + 90 (its Maghrib + 87); from October to December its
     * page still carries its earlier method (Isha about 16°, Fajr about 18°), so there Isha is the later
     * of sunset + 90 and 16°, never before either. Fitted to its 2026 table and its October 2025 rows
     * (not committed).
     */
    private val gmp = Rule(
        moonsightingFajr = true,
        ishaAfterSunsetMinutes = { d ->
            // 2 October to 30 December: the neighbour envelope carries it to 1 October and 31 December.
            val earlier = d.date.dayOfYear in GMP_EARLIER_FROM..GMP_EARLIER_TO
            if (earlier) maxOf(GMP_ISHA_AFTER_SUNSET, d.minutesBefore(GMP_EARLIER_ISHA)) else GMP_ISHA_AFTER_SUNSET
        },
    )

    /**
     * Ruling R39: the end of eating of the authorities above whose Fajr start is late by design, read
     * from their own earliest dawns ([EndOfEatingDawns]) as they apply at a latitude (ruling R80 adds
     * the Grande Mosquée de Paris's, in place of R39's 18° dawn, and floors it at its own rule,
     * [ownRuleEnds]). In the other hemisphere a table's slots mean nothing, so the end is its deepest
     * depression.
     */
    private fun ownDawns(dawns: EndOfEatingDawns.Dawns, angle: Double): (Double) -> EndOfEating = { lat ->
        if (lat * dawns.latitude <= 0.0) {
            EndOfEating.DawnAngle(dawns.depressions.max())
        } else {
            EndOfEating.DawnAngle(angle, TwilightCurves.endOfEating(lat, dawns.latitude, dawns.depressions))
        }
    }

    /** Of an authority's [tables] (south to north), the nearest to the south of [lat], else the southernmost. */
    private fun southOf(tables: List<EndOfEatingDawns.Dawns>, lat: Double) = tables.lastOrNull { it.latitude <= lat } ?: tables.first()

    /** IRN's end: its Oslo, Trondheim or Tromsø table, the nearest to the south (at a table's own point, its own). */
    private val irnEnd: (Double) -> EndOfEating = { lat ->
        val tables = listOf(EndOfEatingDawns.irnOslo, EndOfEatingDawns.irnTrondheim, EndOfEatingDawns.irnTromso)
        ownDawns(southOf(tables, lat), 16.0)(lat)
    }

    /** Diyanet's European end: north of 44.5° its takdir, read from the nearest city table to the south (else Zürich's). */
    private val diyanetEuropeEnd: (Double) -> EndOfEating? = { lat ->
        if (lat <= DIYANET_TAKDIR_FROM) {
            null
        } else {
            ownDawns(southOf(EndOfEatingDawns.diyanetEurope.map { it.second }, lat), 18.0)(lat)
        }
    }

    private val dumRfEnd = ownDawns(EndOfEatingDawns.dumRfMoscow, 18.0)

    /**
     * Ruling R80: the ICCI prints no imsak, so the fast begins at its Fajr, which its code computes
     * from a rule: its own rule's dawn as an end ([TwilightCurves.fajrAsEnd]), in place of R39's 18°
     * dawn, which where the sun only just reaches 18° came up to 86 min before its printed Fajr.
     */
    private val icciEnd: (Double) -> EndOfEating = { lat ->
        EndOfEating.DawnAngle(ICCI_FAJR_ANGLE, TwilightCurves.fajrAsEnd(lat, ICCI_FAJR_ANGLE) { ICCI_FAJR_PORTION })
    }

    /**
     * The Moonsighting Committee's Other method prints no imsak either: its fast begins at its own Fajr,
     * the rule itself read as an end ([TwilightCurves.moonsightingAsEnd]). Its start curve, widened late
     * over the neighbouring days and on the shorter night, came after adhan2's own Fajr from about 47° N in
     * April and May (up to 62 s at London, 85 at Manchester, two minutes in Scotland, where a seventh of the
     * night takes 18°'s place).
     */
    private val moonsightingEnd: (Double) -> EndOfEating = { lat ->
        EndOfEating.DawnAngle(MOONSIGHTING_FAJR_ANGLE, TwilightCurves.moonsightingAsEnd(lat))
    }

    private val ends: Map<String, (Double) -> EndOfEating?> = mapOf(
        "other.moonsighting" to moonsightingEnd,
        "ru.dumrf" to dumRfEnd, "ru.dumrf.edge" to dumRfEnd, "ru.bashkortostan" to dumRfEnd,
        "tr.diyanet.europe" to diyanetEuropeEnd, "de.vikz" to diyanetEuropeEnd, "at.iggo" to diyanetEuropeEnd,
        "at.iggo.edge" to diyanetEuropeEnd, "ch.fids" to diyanetEuropeEnd, "ch.fids.edge" to diyanetEuropeEnd,
        "be.emb" to ownDawns(EndOfEatingDawns.embBrussels, 18.0), "no.irn" to irnEnd,
        "se.rabita" to ownDawns(EndOfEatingDawns.rabitaHelsinki, 18.0),
        "fr.gmp" to ownDawns(EndOfEatingDawns.gmpParis, 18.0), "ie.icci" to icciEnd,
    )

    /** The authorities whose [ends] are also floored at their own rule read as an end ([flooredAt]). */
    private val ownRuleEnds: Map<String, (Double) -> DoubleArray> = mapOf(
        "fr.gmp" to TwilightCurves::moonsightingAsEnd,
    )

    private val rules: Map<String, Rule> = mapOf(
        "ru.dumrf" to dumRf, "ru.dumrf.edge" to dumRf, "ru.bashkortostan" to dumRf,
        "tr.diyanet.europe" to diyanetEurope, "de.vikz" to diyanetEurope, "at.iggo" to diyanetEurope,
        "at.iggo.edge" to diyanetEurope, "ch.fids" to diyanetEurope, "ch.fids.edge" to diyanetEurope,
        "be.emb" to emb, "ie.icci" to icci, "kz.qmdb" to qmdb, "no.irn" to irn, "fr.gmp" to gmp,
        "gb.fifteen" to gbFifteen, "se.rabita" to seRabita, "ca.isha90" to caIsha90, "ca.eighteen" to caEighteen,
        "ca.fifteen" to caFifteen,
    )

    /** [method] as it applies at [point]: its curves for that latitude, and its own end of eating, where it has a rule. */
    fun at(method: TimetableMethod, point: GeoPoint): TimetableMethod {
        val placed = curvesAt(method, point)
        val key = keyOf(method.id, ends)
        val end = ends[key]?.invoke(point.lat) ?: return placed
        val rule = ownRuleEnds[key] ?: return placed.copy(endOfEating = end)
        return placed.copy(endOfEating = flooredAt(end, rule, zoneLatitudes(method, point)))
    }

    /**
     * Ruling R80's fix round: an authority whose Fajr follows a rule that [TwilightCurves.endOfEating]
     * cannot carry to another latitude (the Moonsighting Committee's seasonal minutes: the Grande
     * Mosquée de Paris's from January to September) also ends no later than that rule read as an end at
     * each of [latitudes], each slot the larger depression. At its table's own latitude the two nearly
     * agree (its dawns are that rule's, printed to the minute); away from it the table's dawns carried
     * by the fraction of the night come after the rule's own on some days (southern Corsica in January,
     * by up to 45 s). In the other hemisphere the end is already the table's deepest dawn, deeper than
     * the rule's 18°.
     */
    private fun flooredAt(end: EndOfEating, rule: (Double) -> DoubleArray, latitudes: List<Double>): EndOfEating {
        val own = (end as? EndOfEating.DawnAngle)?.bySlot ?: return end
        val floors = latitudes.map(rule)
        return EndOfEating.DawnAngle(end.degrees, DoubleArray(own.size) { i -> max(own[i], floors.maxOf { it[i] }) })
    }

    /**
     * The latitudes an end read at [point] must hold for: [point]'s own and, where [point] is a table's
     * fixed point (ruling R44; the end the earlier of that point's and the user's, both read on this
     * one curve, for every user within its reach), the south and north of that reach too, class D's
     * (the widest): spec 3.3's dawn at the earliest point of its zone. Within the Grande Mosquée's reach,
     * with its point's latitude alone, the end came up to 57 s after the rule's own moment at the user's
     * point (south of the mosque in winter, east-south-east of it in summer); the reach's south costs the
     * mosque's own point up to a minute more from February to May and in August and two in June and July
     * (up to 4 min before its printed Fajr, not 2).
     */
    private fun zoneLatitudes(method: TimetableMethod, point: GeoPoint): List<Double> {
        if (method.fixedPoint != point || method.fixedPointMode != FixedPointMode.BOTH) return listOf(point.lat)
        val reach = lateReachKm(point.lat, EntryClass.D_AUTHORITY) / distanceKm(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        return listOf(point.lat - reach, point.lat, point.lat + reach)
    }

    /**
     * [id]'s own Fajr rule read as an end at [latitude] ([TwilightCurves.fajrAsEnd]: its fraction of the night
     * where it has one here, else [angle] alone). An end carried from a table's dawns to another latitude
     * ([TwilightCurves.endOfEating]) is checked against it (brief L item 6: EMB's across southern Belgium,
     * ContinentalCautiousTest).
     */
    fun fajrRuleAsEnd(id: String, angle: Double, latitude: Double): DoubleArray =
        TwilightCurves.fajrAsEnd(latitude, angle, rules[keyOf(id, rules)]?.fajr ?: { null })

    /** A point table's edge ("<entry>.edge") keeps its authority's rules. */
    private fun keyOf(id: String, map: Map<String, *>): String = if (id in map) id else id.removeSuffix(".edge")

    private fun curvesAt(method: TimetableMethod, point: GeoPoint): TimetableMethod {
        if (method.id == "other.moonsighting") {
            val (fajr, isha) = TwilightCurves.moonsighting(point.lat)
            return method.copy(fajrAngleByDayOfYear = fajr, ishaAngleByDayOfYear = isha)
        }
        val rule = rules[keyOf(method.id, rules)] ?: return method
        if (rule.ownLatitude) return ownLatitudeCurves(method, point, rule)
        val fajr = when {
            rule.moonsightingFajr -> TwilightCurves.moonsighting(point.lat).first
            rule.fajr != null -> TwilightCurves.fajr(point.lat, method.fajrAngle, fraction = rule.fajr)
            else -> method.fajrAngleByDayOfYear
        }
        if (rule.ishaAfterSunsetMinutes != null) {
            // An Isha in minutes after sunset becomes an angle curve (the method's Isha rule is replaced).
            return method.copy(
                isha = IshaRule.Angle(CURVE_ISHA),
                fajrAngleByDayOfYear = fajr,
                ishaAngleByDayOfYear = TwilightCurves.ishaAfterSunset(point.lat, CURVE_ISHA, rule.ishaAfterSunsetMinutes),
            )
        }
        val ishaAngle = (method.isha as? IshaRule.Angle)?.degrees
        val isha = if (rule.isha != null && ishaAngle != null) {
            TwilightCurves.isha(point.lat, ishaAngle, fraction = rule.isha)
        } else {
            method.ishaAngleByDayOfYear
        }
        return method.copy(fajrAngleByDayOfYear = fajr, ishaAngleByDayOfYear = isha)
    }

    /**
     * [Rule.ownLatitude]'s curves at [point]: each of [rule]'s Fajr and Isha curves on the 0.1° grid
     * and at the point's own latitude, each slot the later start of the two (the smaller Fajr
     * depression, the larger Isha one), so no start is earlier than either; and an end of eating read
     * from the Fajr dawn takes the earlier dawn of the two, so it is later than neither.
     */
    private fun ownLatitudeCurves(method: TimetableMethod, point: GeoPoint, rule: Rule): TimetableMethod {
        val fajrRule = rule.fajr ?: return method
        val grid = TwilightCurves.fajr(point.lat, method.fajrAngle, onGrid = true, fraction = fajrRule)
        val own = TwilightCurves.fajr(point.lat, method.fajrAngle, onGrid = false, fraction = fajrRule)
        val fajr = DoubleArray(grid.size) { i -> min(grid[i], own[i]) }
        val ishaAngle = (method.isha as? IshaRule.Angle)?.degrees
        val isha = if (rule.isha != null && ishaAngle != null) {
            val ishaGrid = TwilightCurves.isha(point.lat, ishaAngle, onGrid = true, fraction = rule.isha)
            val ishaOwn = TwilightCurves.isha(point.lat, ishaAngle, onGrid = false, fraction = rule.isha)
            DoubleArray(ishaGrid.size) { i -> max(ishaGrid[i], ishaOwn[i]) }
        } else {
            method.ishaAngleByDayOfYear
        }
        val end = if (method.endOfEating == EndOfEating.SameAsFajrDawn) {
            EndOfEating.DawnAngle(method.fajrAngle, DoubleArray(grid.size) { i -> max(grid[i], own[i]) })
        } else {
            method.endOfEating
        }
        return method.copy(fajrAngleByDayOfYear = fajr, ishaAngleByDayOfYear = isha, endOfEating = end)
    }

    private const val CURVE_ISHA = 18.0
    private const val CA_ISHA90_FAJR_FRACTION = 0.235
    private const val CA_EIGHTEEN_FAJR_FRACTION = 0.25
    private const val CA_FIFTEEN_FAJR_FRACTION = 0.25
    private const val CA_FIFTEEN_ISHA_FRACTION = 0.28
    private const val CA_EIGHTEEN_ISHA_FRACTION = 0.165
    private const val GMP_ISHA_AFTER_SUNSET = 90.0

    /** The Moonsighting Committee's Fajr angle, where its seasonal minutes do not take over. */
    private const val MOONSIGHTING_FAJR_ANGLE = 18.0

    /** adhan-js's TwilightAngle rule for the ICCI's MWL angles: 18/60 and 17/60 of the night. */
    private const val ICCI_FAJR_ANGLE = 18.0
    private const val ICCI_FAJR_PORTION = ICCI_FAJR_ANGLE / 60
    private const val ICCI_ISHA_PORTION = 17.0 / 60

    /** EMB's point (Brussels) and its summer clock-time floor and caps, in minutes after 00:00 UTC. */
    private const val EMB_LON = 4.3525
    private const val EMB_FAJR_CAP_UTC = 84.0
    private const val EMB_SPRING_ISHA_CAP_UTC = 1286.0
    private const val EMB_ISHA_CAP_UTC = 1293.0

    /** EMB's summer Isha is 0.26–0.27 of the night in June, between the 45° proportions of 16° and 17°: the later. */
    private const val EMB_ISHA_PROPORTION_ANGLE = 17.5
    private const val GMP_EARLIER_ISHA = 16.0

    /** 2 October and 30 December on the leap reference year. */
    private const val GMP_EARLIER_FROM = 276
    private const val GMP_EARLIER_TO = 365
    private const val DIYANET_TAKDIR_FROM = 44.5

    /**
     * On the days Diyanet's nineteen-hour cap binds, its Fajr comes 624–635 min before its Dhuhr (the
     * capped sunrise 566–570 before Dhuhr, Fajr 57–65 before it) over its 22 Nordic tables held
     * (monitor round, brief D); the edge's Fajr is never earlier than the latest of them.
     */
    private const val DIYANET_CAPPED_FAJR_BEFORE_DHUHR = 624.0

    /** Diyanet's Dhuhr: the transit plus its temkin of 5 min. */
    private const val DIYANET_DHUHR_MINUTES = 5.0
}
