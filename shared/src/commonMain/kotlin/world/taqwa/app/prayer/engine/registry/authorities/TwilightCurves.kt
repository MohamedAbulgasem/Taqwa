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
import kotlin.math.floor
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

        /**
         * The longer of the nights around the day, in minutes: a morning rule whose authorities count
         * different nights, read as an end (the earlier dawn of the two; the Moonsighting Committee's own
         * tables count the night before for their Fajr, adhan2 the night after).
         */
        val longerNight: Double get() = max(nightBefore, nightAfter)

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

        /** [morningDepression]'s mirror after sunset: the sun's depression [minutes] after it, with the declination at sunset and at that moment. */
        fun eveningDepression(minutes: Double): Double {
            val h0Here = hourAngle(phi, delta + deltaPerDay * h0 / 360.0, HORIZON) ?: h0
            val h = h0Here + minutes / 4.0
            return depressionAt(phi, delta + deltaPerDay * h / 360.0, h)
        }
    }

    /**
     * The sun's noon declination (radians) for slots −1 .. 366 of the reference year: the days either side of it,
     * for the nights. The same for every latitude, so computed once (a QMDB place's curves are read at two
     * latitudes, on the grid and at their own, for each of its 5,676 units; the city-points round of 9 Oct 2026).
     */
    private val referenceDeclinations: DoubleArray by lazy {
        DoubleArray(SLOTS + 2) { i ->
            val noon = referenceStart.plus(i - 1, DateTimeUnit.DAY).toEpochDays() * SolarMath.SECONDS_PER_DAY + 12 * 3600.0
            SolarMath.rad(SolarMath.sun(SolarMath.julianDay(noon)).declinationDeg)
        }
    }

    /**
     * The sun's declination (degrees) every 6 h from 00:00 UTC on 30 December before the [leapCycle] to
     * 4 January after it, computed once: the Moonsighting Committee's reads take the sun at many moments
     * of every day of the cycle at each place ([moonsighting], [moonsightingAsEnd]), about 36,000
     * positions a place otherwise (5.5 ms on the Mac, T3 round of 11 Oct 2026). Read by straight lines
     * between samples ([declinationAt]), off the sun by under a ten-thousandth of a degree.
     */
    private val cycleDeclinations: DoubleArray by lazy {
        val start = cycleStartSeconds()
        DoubleArray(cycleSamples()) { k -> SolarMath.sun(SolarMath.julianDay(start + k * CYCLE_STEP_SECONDS)).declinationDeg }
    }

    private val cycleStart: Double by lazy { cycleStartSeconds() }

    private fun cycleStartSeconds(): Double = LocalDate(LEAP_CYCLE_FIRST - 1, 12, 30).toEpochDays() * SolarMath.SECONDS_PER_DAY

    private fun cycleSamples(): Int =
        ((LocalDate(CURVE_REFERENCE_YEAR + 1, 1, 4).toEpochDays() - LocalDate(LEAP_CYCLE_FIRST - 1, 12, 30).toEpochDays()) * 4 + 1).toInt()

    /** The sun's declination (degrees) at [epochSeconds], from [cycleDeclinations] where it holds the moment, else the sun itself. */
    private fun declinationAt(epochSeconds: Double): Double {
        val table = cycleDeclinations
        val x = (epochSeconds - cycleStart) / CYCLE_STEP_SECONDS
        val k = floor(x).toInt()
        if (k < 0 || k + 1 >= table.size) return SolarMath.sun(SolarMath.julianDay(epochSeconds)).declinationDeg
        return table[k] + (table[k + 1] - table[k]) * (x - k)
    }

    /**
     * [referenceDeclinations]' days, with the sun at the local noon at [longitude] rather than at 12:00 UTC
     * (from [cycleDeclinations]): at 150° E 12:00 UTC is ten hours after the local noon, and with the leap
     * cycle's own drift that put the Moonsighting Committee's Fajr, read at the place itself, a minute early
     * near 65° N in July and August (T3 round of 11 Oct 2026).
     */
    private fun localNoonDeclinations(longitude: Double): DoubleArray = DoubleArray(SLOTS + 2) { i ->
        val noon = (referenceStart.toEpochDays() + i - 1) * SolarMath.SECONDS_PER_DAY + (12.0 - longitude / 15.0) * 3600.0
        SolarMath.rad(declinationAt(noon))
    }

    /**
     * Per slot the value [angle] gives, [fallback] where the sun does not rise or set. The sun is
     * taken at [latitude] rounded to 0.1° ([onGrid], every curve's default), or at [latitude] itself,
     * and at 12:00 UTC, or at the local noon at [longitude] where given.
     */
    private fun perSlot(
        latitude: Double,
        fallback: Double,
        onGrid: Boolean = true,
        longitude: Double? = null,
        angle: (Day) -> Double,
    ): DoubleArray {
        val phi = SolarMath.rad(if (onGrid) round(latitude * 10.0) / 10.0 else latitude)
        val declinations = if (longitude == null) referenceDeclinations else localNoonDeclinations(longitude)
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
     * and day over a leap cycle, the declination at the moment): the later of 18° and its seasonal
     * minutes before sunrise, from 55° of latitude, north or south, the later of a seventh of the night
     * and those minutes ([moonsighting]; the minutes at [latitude] itself, from each date's own day of
     * the year and year's length). Its minutes are neither an angle nor a fraction of the night, so no
     * other latitude's dawns carry it ([endOfEating]): the Grande Mosquée de Paris's end is floored with
     * it where it applies (PlaceCurves, ruling R80's fix round).
     *
     * T3 round of 11 Oct 2026 ([CommitteeReads.end]):
     * - the seventh counts the longer of the nights around the day, the earlier dawn: the Committee's
     *   own tables count the night before for their Fajr (to the minute on 72–88 % of their days from
     *   55° to 66° N, where the night after fits 42–82 %), adhan2's code the night after;
     * - read twice, with the sun on the 0.1° grid and at [latitude] and [longitude] itself (the
     *   declination at the place's own noon), each slot the earlier end of the two: on the grid alone
     *   a place just past a step read the sun a twentieth of a degree from itself (its end up to 38 s
     *   after the rule's own moment near 64° N), and 12:00 UTC is ten hours off the local noon at
     *   150° E (a minute late near 65.5° N in May and June);
     * - never deeper than the sun sinks those nights at [latitude] ([shallowestNights], two thousandths
     *   of a degree short of it); where the rule's dawn is deeper than that in some year of the cycle,
     *   on the edge of the midnight sun, the slot is left unreached (18°), and the end is then read as
     *   the middle of the night (PlaceCurves' DawnOrMiddle). [REACH_SPARE_DEG]'s quarter of a degree had
     *   put the end after sunrise near 65.5° N in June, where the whole night lies within it of the
     *   sun's lowest, so the fast began at the Fajr shown, up to 6 min after the Committee's own printed
     *   Fajr at Luleå and Akureyri.
     */
    fun moonsightingAsEnd(latitude: Double, longitude: Double = 0.0): DoubleArray = CommitteeReads(latitude, longitude).end

    /**
     * A morning rule read as an end ([fajrAsEnd]): each slot the largest [depression] of that month
     * and day over the [leapCycle], never deeper than the sun sinks those nights less
     * [REACH_SPARE_DEG]; [angle] where the sun does not rise or set on one of them.
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
     * Per slot, the least the sun sinks at [latitude] on the night before that month and day's
     * sunrise, in any year of the [leapCycle] and at any longitude, less [END_REACH_SPARE_DEG]: so a
     * dawn no deeper is reached that night. The night's lowest point lies between 10:00 UTC the day
     * before and 14:00 UTC on the day, whatever the longitude (the transit less 12 h, as the engine
     * looks for it, with the equation of time and the zones a little either side); the sun is taken
     * at its declination furthest towards the pole in that window, at the window's ends and at every
     * one of [cycleDeclinations]' samples between them (its straight lines have their extremes there).
     * For the evening ([morning] false), the night after that day's sunset, a day later. NaN where the
     * sun does not rise or set on one of them.
     */
    private fun shallowestNights(latitude: Double, morning: Boolean): DoubleArray {
        val phi = SolarMath.rad(latitude)
        val towardsPole = if (latitude >= 0) 1.0 else -1.0
        val samples = if (morning) LOWEST_POINT_SAMPLES_HOURS else LOWEST_POINT_SAMPLES_HOURS.map { it + 24.0 }
        val years = leapCycle.map { year ->
            val length = LocalDate(year, 12, 31).dayOfYear
            DoubleArray(SLOTS) { i ->
                val midnight = slotDate(year, length, i).toEpochDays() * SolarMath.SECONDS_PER_DAY
                val noon = SolarMath.rad(declinationAt(midnight + 12 * 3600.0))
                if (hourAngle(phi, noon, HORIZON) == null) return@DoubleArray Double.NaN
                val furthest = samples.maxOf { h -> towardsPole * declinationAt(midnight + h * 3600.0) }
                depressionAt(phi, SolarMath.rad(towardsPole * furthest), 180.0)
            }
        }
        return DoubleArray(SLOTS) { i -> years.minOf { it[i] } - END_REACH_SPARE_DEG }
    }

    /** Slot [i]'s month and day in [year], [length] days long (29 February, in a common year, on the 28th). */
    private fun slotDate(year: Int, length: Int, i: Int): LocalDate {
        cycleSlotDates[year]?.let { return it[i] }
        val slot = referenceStart.plus(i, DateTimeUnit.DAY)
        return if (length != SLOTS && slot.month.number == 2 && slot.day == 29) LocalDate(year, 2, 28) else LocalDate(year, slot.month, slot.day)
    }

    /** Every slot's date in each year of the [leapCycle], made once. */
    private val cycleSlotDates: Map<Int, Array<LocalDate>> by lazy {
        leapCycle.associateWith { year ->
            val length = LocalDate(year, 12, 31).dayOfYear
            Array(SLOTS) { i ->
                val slot = referenceStart.plus(i, DateTimeUnit.DAY)
                if (length != SLOTS && slot.month.number == 2 && slot.day == 29) LocalDate(year, 2, 28) else LocalDate(year, slot.month, slot.day)
            }
        }
    }

    /**
     * Each slot's month and day in [year] (29 February, in a common year, on the 28th) at a latitude
     * (rounded to 0.1° where [onGrid]), with its noon declination and how fast it moves; null where the
     * sun does not rise or set. Noon is 12:00 UTC (for [year] 2028 the same days as [perSlot]), or the
     * local noon at [longitude] (the Moonsighting Committee's own reads: at 150° E, 12:00 UTC is ten
     * hours after the local noon, and the sun's declination that far off put the end of eating a
     * minute after the rule's own moment near 65.5° N in May and June). [cycled]: the declination read
     * from [cycleDeclinations] rather than the sun's own model each time.
     */
    private fun daysIn(year: Int, latitude: Double, onGrid: Boolean = true, longitude: Double = 0.0, cycled: Boolean = false): Array<Day?> {
        val phi = SolarMath.rad(if (onGrid) round(latitude * 10.0) / 10.0 else latitude)
        val length = LocalDate(year, 12, 31).dayOfYear
        val firstDay = LocalDate(year, 1, 1).toEpochDays()
        // By day of the year, 0 .. length + 1: the days either side of the year, for the nights.
        val declinations = DoubleArray(length + 2) { k ->
            val noon = (firstDay + k - 1) * SolarMath.SECONDS_PER_DAY + (12.0 - longitude / 15.0) * 3600.0
            SolarMath.rad(if (cycled) declinationAt(noon) else SolarMath.sun(SolarMath.julianDay(noon)).declinationDeg)
        }
        val halfDays = DoubleArray(length + 2) { k -> hourAngle(phi, declinations[k], HORIZON) ?: Double.NaN }
        return Array(SLOTS) { i ->
            val date = slotDate(year, length, i)
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
     * function, the Isha the earlier of 18° and the evening function; from 55° of latitude a seventh
     * of the night takes 18°'s place.
     *
     * The rule followed is the Committee's own, as its own tables print it (moonsighting.com's
     * praytable, 2026, at 24 places from 43.7° N to 69.6° N and from 54.8° S to 63.4° S, held in the
     * archive; T3 round of 11 Oct 2026):
     * - the switch to the seventh is at the place's own latitude, as adhan2 has it: the seasonal rule
     *   at Newcastle upon Tyne (54.97° N), the seventh at Gosforth (55.0° N) and Tynemouth (55.02° N);
     * - it switches in the south too, from 55° S, where adhan2's preset does not: at Isla Hornos, in the
     *   Drake Passage, at Orcadas and Esperanza the Committee's Fajr is up to 102 min after adhan2's
     *   and its Isha up to 53 min before it, and the engine follows the Committee's;
     * - its Fajr's seventh counts the night before; a start takes the shorter night around the day
     *   ([Day.morningNight]), so it is never before adhan2's either, which counts the night after.
     *
     * Each start curve is read twice, each slot the later start of the two (the smallest Fajr
     * depression, the largest Isha one; [CommitteeReads]): with the sun and the minutes on the 0.1°
     * grid and at [latitude] itself; and where the night is short (the sun no lower than
     * [SHORT_NIGHT_DEG] on some night of the cycle) a third time, on each day of the leap cycle at
     * [latitude] and [longitude] itself. The switch at 55° is decided at [latitude] itself in every
     * read. On the grid alone, with
     * the switch read there too, a place from 54.95° to 55° N read the seventh of the night (its
     * latitude rounds to 55.0): its Isha came up to 24 min before the Committee's own printed one from
     * May to July at Newcastle, and its Fajr up to 68 min after it (found in the kz-north-west round of
     * 7 Oct 2026). Where the rule's moment lies near the sun's lowest point (from about 64° N, and south
     * of about 54° S) a twentieth of a degree of latitude moved the Fajr by up to 5 min in the north
     * and 8 in the south, early on the poleward side of a step. Then each slot is held to what the sun
     * reaches there ([shallowestNights]): a Fajr dawn no deeper, an Isha deeper than that left unreached
     * (18°), so that the method's high-latitude rule sets it, later than the rule's (on the edge of the
     * midnight sun, where the neighbouring day's dusk the envelope takes can lie below the sun's lowest
     * of the night, which put Isha up to 2 min early).
     */
    fun moonsighting(latitude: Double, longitude: Double = 0.0): Pair<DoubleArray, DoubleArray> =
        CommitteeReads(latitude, longitude).let { it.fajr to it.isha }

    /** [moonsighting]'s Fajr curve alone (the Grande Mosquée de Paris's Fajr beyond its table's reach, PlaceCurves). */
    fun moonsightingFajr(latitude: Double, longitude: Double = 0.0): DoubleArray = CommitteeReads(latitude, longitude).fajr

    /**
     * [moonsighting]'s Fajr as the grid alone reads it, the curve before the T3 round: the Grande Mosquée de
     * Paris's at its own table's point, where the gate holds it to the table itself (PlaceCurves).
     */
    fun moonsightingGridFajr(latitude: Double): DoubleArray = CommitteeReads(latitude, 0.0).gridFajr

    /** [moonsighting]'s Fajr and Isha curves and [moonsightingAsEnd]'s end at one place, from the same reads. */
    fun moonsightingCurves(latitude: Double, longitude: Double = 0.0): Triple<DoubleArray, DoubleArray, DoubleArray> =
        CommitteeReads(latitude, longitude).let { Triple(it.fajr, it.isha, it.end) }

    /**
     * The Moonsighting Committee's reads at one place ([moonsighting], [moonsightingAsEnd]), each made
     * once and shared by its curves.
     */
    private class CommitteeReads(private val latitude: Double, private val longitude: Double) {
        private val seventh = abs(latitude) >= SEVENTH_FROM_LATITUDE

        /** The leap cycle's days at [latitude] and [longitude] itself, and on the 0.1° grid (at 12:00 UTC). */
        private val ownDays by lazy { leapCycle.map { daysIn(it, latitude, onGrid = false, longitude = longitude, cycled = true) } }
        private val gridDays by lazy { leapCycle.map { daysIn(it, latitude, onGrid = true, cycled = true) } }
        private val dawns by lazy { shallowestNights(latitude, morning = true) }
        private val dusks by lazy { shallowestNights(latitude, morning = false) }

        /** The Fajr start curve on the grid alone ([moonsightingGridFajr]). */
        val gridFajr: DoubleArray by lazy { startFajr(onGrid = true) }

        val fajr: DoubleArray by lazy {
            val grid = gridFajr
            val own = startFajr(onGrid = false)
            val years by lazy { cycleStarts(morning = true) }
            DoubleArray(SLOTS) { i ->
                val read = min(grid[i], own[i])
                val value = if (shortNight(dawns[i])) min(read, years[i]) else read
                if (dawns[i].isNaN()) value else min(value, dawns[i])
            }
        }

        val isha: DoubleArray by lazy {
            val grid = startIsha(onGrid = true)
            val own = startIsha(onGrid = false)
            val years by lazy { cycleStarts(morning = false) }
            DoubleArray(SLOTS) { i ->
                val read = max(grid[i], own[i])
                val value = if (shortNight(dusks[i])) max(read, years[i]) else read
                if (dusks[i].isNaN() || value <= dusks[i]) value else MOONSIGHTING_ANGLE
            }
        }

        /**
         * Whether a slot's night is short enough for [cycleStarts] to matter: the sun no lower than
         * [SHORT_NIGHT_DEG] on it in some year of the cycle, or not setting at all (NaN). Elsewhere the
         * reference year's curve, widened a day either side, already holds every year, and the third read
         * would only add seconds of lateness (a minute on up to a third of the days from 40° to 64° N).
         */
        private fun shortNight(shallowest: Double): Boolean = !(shallowest >= SHORT_NIGHT_DEG)

        /** [moonsightingAsEnd]: the earlier end of the grid's read and the place's own, held to what the sun reaches. */
        val end: DoubleArray by lazy {
            val grid = endValues(gridDays)
            val own = endValues(ownDays)
            DoubleArray(SLOTS) { i ->
                val value = max(grid[i], own[i])
                when {
                    value.isNaN() -> MOONSIGHTING_ANGLE
                    dawns[i].isNaN() || value <= dawns[i] -> value
                    else -> MOONSIGHTING_ANGLE
                }
            }
        }

        /**
         * The Fajr start curve on the reference year, with the sun and the minutes on the grid at 12:00 UTC
         * ([onGrid]), or at [latitude] itself at its own local noon.
         */
        private fun startFajr(onGrid: Boolean): DoubleArray {
            val lat = if (onGrid) round(latitude * 10.0) / 10.0 else latitude
            return fajrEnvelope(
                perSlot(lat, MOONSIGHTING_ANGLE, onGrid = false, longitude = if (onGrid) null else longitude) { d ->
                    val minutes = morningMinutes(lat, daysSinceSolstice(d.date, lat))
                    if (seventh) d.depression(min(d.morningNight / 7, minutes)) else min(MOONSIGHTING_ANGLE, d.depression(minutes))
                },
            )
        }

        /** [startFajr]'s Isha. */
        private fun startIsha(onGrid: Boolean): DoubleArray {
            val lat = if (onGrid) round(latitude * 10.0) / 10.0 else latitude
            return ishaEnvelope(
                perSlot(lat, MOONSIGHTING_ANGLE, onGrid = false, longitude = if (onGrid) null else longitude) { d ->
                    val minutes = eveningMinutes(lat, daysSinceSolstice(d.date, lat))
                    if (seventh) d.depression(min(d.eveningNight / 7, minutes)) else min(MOONSIGHTING_ANGLE, d.depression(minutes))
                },
            )
        }

        /**
         * The third read: the rule on each month and day of every year of the [leapCycle] at the place
         * itself, with the sun's declination at the moment ([Day.morningDepression],
         * [Day.eveningDepression]), each slot the smallest Fajr ([morning]) or the largest Isha depression
         * of the day and the day either side over those years; ±∞ where none has a night. The reference
         * year's curves, widened over the day either side, cover the leap cycle's drift where the night
         * changes slowly; on the edge of the midnight sun it changes by minutes a day and the reference
         * year's own day can have no night at all (its curve then took a neighbour's dawn, deeper than
         * the same day's in another year: Fajr 2 min before the rule's own at 65.8° N on 16 June 2027).
         */
        private fun cycleStarts(morning: Boolean): DoubleArray {
            val values = DoubleArray(SLOTS) { if (morning) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY }
            for (days in ownDays) {
                for (i in 0 until SLOTS) {
                    val d = days[i] ?: continue
                    val k = daysSinceSolstice(d.date, latitude)
                    val value = if (morning) {
                        val minutes = morningMinutes(latitude, k)
                        if (seventh) d.morningDepression(min(d.morningNight / 7, minutes)) else min(MOONSIGHTING_ANGLE, d.morningDepression(minutes))
                    } else {
                        val minutes = eveningMinutes(latitude, k)
                        if (seventh) d.eveningDepression(min(d.eveningNight / 7, minutes)) else min(MOONSIGHTING_ANGLE, d.eveningDepression(minutes))
                    }
                    for (j in i - 1..i + 1) {
                        val s = (j + SLOTS) % SLOTS
                        values[s] = if (morning) min(values[s], value) else max(values[s], value)
                    }
                }
            }
            return values
        }

        /**
         * Each slot the deepest dawn of the rule read as an end over the leap cycle's [days] (the minutes
         * at [latitude] itself; the seventh counting the longer night around the day, [Day.longerNight]);
         * NaN where the sun does not rise or set on one of them.
         */
        private fun endValues(days: List<Array<Day?>>): DoubleArray = DoubleArray(SLOTS) { i ->
            days.maxOf { year ->
                year[i]?.let { d ->
                    val minutes = morningMinutes(latitude, daysSinceSolstice(d.date, latitude))
                    if (seventh) d.morningDepression(min(d.longerNight / 7, minutes)) else min(MOONSIGHTING_ANGLE, d.morningDepression(minutes))
                } ?: Double.NaN
            }
        }
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

    /**
     * How far short of the least the sun sinks at the latitude itself ([shallowestNights]) the Moonsighting
     * Committee's end stays: that lowest is computed from the sun's own declination over the whole window
     * the night's lowest point can fall in, so only rounding is left to cover.
     */
    private const val END_REACH_SPARE_DEG = 0.002

    /**
     * The Moonsighting Committee's starts take their leap-cycle read where the sun sinks no lower than
     * this on some night of the cycle ([CommitteeReads]): within about 3° of the midnight sun's latitude
     * (63.6° N and S at the solstice), where the night's length changes by minutes a day.
     */
    private const val SHORT_NIGHT_DEG = 3.0

    /**
     * Hours from 00:00 UTC on a date at which [shallowestNights] reads the sun: 10:00 UTC the day before
     * to 14:00 UTC, and [cycleDeclinations]' samples between them.
     */
    private val LOWEST_POINT_SAMPLES_HOURS = listOf(-14.0, -12.0, -6.0, 0.0, 6.0, 12.0, 14.0)

    /** One leap cycle: the sun on a month and day moves by up to about three quarters of a day across it. */
    private val leapCycle = listOf(LEAP_CYCLE_FIRST, 2026, 2027, CURVE_REFERENCE_YEAR)

    private const val LEAP_CYCLE_FIRST = 2025

    /** [cycleDeclinations]' step: 6 h. */
    private const val CYCLE_STEP_SECONDS = 21600.0
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
     * Kazakhstan, kz-north-west round of 6 Oct 2026). Only for a rule with a Fajr fraction and no
     * [moonsightingFajr] or [ishaAfterSunsetMinutes], which [ownLatitudeCurves] does not read: the
     * check below refuses any other, so no curve can be dropped without a word.
     */
    private class Rule(
        val fajr: ((TwilightCurves.Day) -> Double?)? = null,
        val isha: ((TwilightCurves.Day) -> Double?)? = null,
        val moonsightingFajr: Boolean = false,
        val ishaAfterSunsetMinutes: ((TwilightCurves.Day) -> Double)? = null,
        val ownLatitude: Boolean = false,
    ) {
        init {
            require(!ownLatitude || (fajr != null && !moonsightingFajr && ishaAfterSunsetMinutes == null)) {
                "ownLatitude reads only a Fajr fraction and an Isha fraction"
            }
        }
    }

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
     *
     * Its Fajr at its table's own point (the method's fixed point, inside its reach) is the Committee's
     * curve on the 0.1° grid alone, the one its margins were fitted on and the gate holds to the table
     * itself; beyond its reach, at the user's own point, it is the Committee's rule read in full
     * ([TwilightCurves.moonsighting]: also at the place's own latitude and on each day of the leap
     * cycle). Read in full at the table's point too, it moved the Fajr shown a minute later on at least
     * 85 of its 303 checked days (the exact share from 57 % to 29 %) and was never earlier (T3 round of
     * 11 Oct 2026).
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
     * The Moonsighting Committee's Other method at [point]: its Fajr and Isha curves and its end, from one
     * set of reads ([TwilightCurves.moonsightingCurves]). It prints no imsak either: its fast begins at its
     * own Fajr, the rule itself read as an end ([TwilightCurves.moonsightingAsEnd]). Its start curve,
     * widened late over the neighbouring days and on the shorter night, came after adhan2's own Fajr from
     * about 47° N in April and May (up to 62 s at London, 85 at Manchester, two minutes in Scotland, where
     * a seventh of the night takes 18°'s place).
     *
     * On the edge of the midnight sun (from about 65.7°) the rule's moment can lie within a thousandth of a
     * degree of the sun's lowest point, closer than one curve can hold for every year of a leap cycle;
     * there the end's slots are left unreached and the end is the middle of the night
     * ([EndOfEating.DawnOrMiddle], whatever a migrated high-latitude preference says): never after the
     * Committee's own Fajr, which counts the night before, and after adhan2's only on the first night
     * after the midnight sun, where adhan2, counting the night to the next sunrise, puts its Fajr before
     * the middle of the night just ended (T3 round of 11 Oct 2026, its report).
     */
    private fun moonsightingAt(method: TimetableMethod, point: GeoPoint): TimetableMethod {
        val (fajr, isha, end) = TwilightCurves.moonsightingCurves(point.lat, point.lon)
        return method.copy(
            fajrAngleByDayOfYear = fajr,
            ishaAngleByDayOfYear = isha,
            endOfEating = EndOfEating.DawnOrMiddle(MOONSIGHTING_FAJR_ANGLE, end),
        )
    }

    /** An end that depends on the latitude alone, read at a point. */
    private fun ((Double) -> EndOfEating?).byLatitude(): (GeoPoint) -> EndOfEating? = { point -> this(point.lat) }

    private val ends: Map<String, (GeoPoint) -> EndOfEating?> = mapOf(
        "ru.dumrf" to dumRfEnd.byLatitude(), "ru.dumrf.edge" to dumRfEnd.byLatitude(), "ru.bashkortostan" to dumRfEnd.byLatitude(),
        "tr.diyanet.europe" to diyanetEuropeEnd.byLatitude(), "de.vikz" to diyanetEuropeEnd.byLatitude(),
        "at.iggo" to diyanetEuropeEnd.byLatitude(), "at.iggo.edge" to diyanetEuropeEnd.byLatitude(),
        "ch.fids" to diyanetEuropeEnd.byLatitude(), "ch.fids.edge" to diyanetEuropeEnd.byLatitude(),
        "be.emb" to ownDawns(EndOfEatingDawns.embBrussels, 18.0).byLatitude(), "no.irn" to irnEnd.byLatitude(),
        "se.rabita" to ownDawns(EndOfEatingDawns.rabitaHelsinki, 18.0).byLatitude(),
        "fr.gmp" to ownDawns(EndOfEatingDawns.gmpParis, 18.0).byLatitude(), "ie.icci" to icciEnd.byLatitude(),
    )

    /** The authorities whose [ends] are also floored at their own rule read as an end ([flooredAt]). */
    private val ownRuleEnds: Map<String, (GeoPoint) -> DoubleArray> = mapOf(
        "fr.gmp" to { point -> TwilightCurves.moonsightingAsEnd(point.lat, point.lon) },
    )

    private val rules: Map<String, Rule> = mapOf(
        "ru.dumrf" to dumRf, "ru.dumrf.edge" to dumRf, "ru.bashkortostan" to dumRf,
        "tr.diyanet.europe" to diyanetEurope, "de.vikz" to diyanetEurope, "at.iggo" to diyanetEurope,
        "at.iggo.edge" to diyanetEurope, "ch.fids" to diyanetEurope, "ch.fids.edge" to diyanetEurope,
        "be.emb" to emb, "ie.icci" to icci, "kz.qmdb" to qmdb, "no.irn" to irn, "fr.gmp" to gmp,
        "gb.fifteen" to gbFifteen, "se.rabita" to seRabita, "ca.isha90" to caIsha90, "ca.eighteen" to caEighteen,
        "ca.fifteen" to caFifteen,
    )

    /**
     * [method] as it applies at [point]: its curves for that latitude, and its own end of eating, where it has a rule.
     * [also] is the user's own point where [point] is a unit's (the method's fixed point, ruling R44): a rule read at
     * each place's own latitude ([Rule.ownLatitude], QMDB's) is then read at both latitudes, each slot the later start
     * and the earlier end, so that the unit's point never makes the user's own start earlier, or end later, than it
     * is without the unit (the city-points round of 9 Oct 2026: a unit reaches about half a degree north and south of
     * its point, and the AngleBased rule moves by up to a minute a tenth of a degree). Every other rule keeps the
     * fixed point's curves alone.
     */
    fun at(method: TimetableMethod, point: GeoPoint, also: GeoPoint = point): TimetableMethod {
        if (method.id == MOONSIGHTING_ID) return moonsightingAt(method, point)
        val placed = curvesAt(method, point, also)
        val key = keyOf(method.id, ends)
        val end = ends[key]?.invoke(point) ?: return placed
        val rule = ownRuleEnds[key] ?: return placed.copy(endOfEating = end)
        return placed.copy(endOfEating = flooredAt(end, rule, zonePoints(method, point)))
    }

    /**
     * Ruling R80's fix round: an authority whose Fajr follows a rule that [TwilightCurves.endOfEating]
     * cannot carry to another latitude (the Moonsighting Committee's seasonal minutes: the Grande
     * Mosquée de Paris's from January to September) also ends no later than that rule read as an end at
     * each of [points], each slot the larger depression. At its table's own latitude the two nearly
     * agree (its dawns are that rule's, printed to the minute); away from it the table's dawns carried
     * by the fraction of the night come after the rule's own on some days (southern Corsica in January,
     * by up to 45 s). In the other hemisphere the end is already the table's deepest dawn, deeper than
     * the rule's 18°.
     */
    private fun flooredAt(end: EndOfEating, rule: (GeoPoint) -> DoubleArray, points: List<GeoPoint>): EndOfEating {
        val own = (end as? EndOfEating.DawnAngle)?.bySlot ?: return end
        val floors = points.map(rule)
        return EndOfEating.DawnAngle(end.degrees, DoubleArray(own.size) { i -> max(own[i], floors.maxOf { it[i] }) })
    }

    /**
     * The points (on [point]'s meridian) an end read at [point] must hold for: [point] and, where [point] is a table's
     * fixed point (ruling R44; the end the earlier of that point's and the user's, both read on this
     * one curve, for every user within its reach), the south and north of that reach too, class D's
     * (the widest): spec 3.3's dawn at the earliest point of its zone. Within the Grande Mosquée's reach,
     * with its point's latitude alone, the end came up to 57 s after the rule's own moment at the user's
     * point (south of the mosque in winter, east-south-east of it in summer); the reach's south costs the
     * mosque's own point up to a minute more from February to May and in August and two in June and July
     * (up to 4 min before its printed Fajr, not 2).
     */
    private fun zonePoints(method: TimetableMethod, point: GeoPoint): List<GeoPoint> {
        if (method.fixedPoint != point || method.fixedPointMode != FixedPointMode.BOTH) return listOf(point)
        val reach = lateReachKm(point.lat, EntryClass.D_AUTHORITY) / distanceKm(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        return listOf(GeoPoint(point.lat - reach, point.lon), point, GeoPoint(point.lat + reach, point.lon))
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

    private fun curvesAt(method: TimetableMethod, point: GeoPoint, also: GeoPoint): TimetableMethod {
        val rule = rules[keyOf(method.id, rules)] ?: return method
        if (rule.ownLatitude) return ownLatitudeCurves(method, listOf(point.lat, also.lat).distinct(), rule)
        val fajr = when {
            rule.moonsightingFajr && method.fixedPoint == point -> TwilightCurves.moonsightingGridFajr(point.lat)
            rule.moonsightingFajr -> TwilightCurves.moonsightingFajr(point.lat, point.lon)
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
     * [Rule.ownLatitude]'s curves at [latitudes] (a place's own, and the user's beside a unit's point,
     * [at]): each of [rule]'s Fajr and Isha curves on the 0.1° grid and at each latitude's own value,
     * each slot the later start of them all (the smallest Fajr depression, the largest Isha one), so no
     * start is earlier than any; and an end of eating read from the Fajr dawn takes the earliest dawn of
     * them all, so it is later than none.
     *
     * That end is still read from the Fajr START envelopes (the shorter night, each slot widened late
     * over the neighbouring days), not from the rule read as an end ([TwilightCurves.fajrAsEnd],
     * rulings R39 and R80); the entry's region margins carry the difference (kz.qmdb's −374 s at and
     * above 48° N and −150 s from 46° to 48° N, `CentralAsia.kt`, fitted to 0 late ends).
     */
    private fun ownLatitudeCurves(method: TimetableMethod, latitudes: List<Double>, rule: Rule): TimetableMethod {
        val fajrRule = requireNotNull(rule.fajr) { "an ownLatitude rule needs its Fajr fraction" }
        val fajrs = latitudes.flatMap { lat ->
            listOf(true, false).map { onGrid -> TwilightCurves.fajr(lat, method.fajrAngle, onGrid = onGrid, fraction = fajrRule) }
        }
        val slots = fajrs.first().size
        val fajr = DoubleArray(slots) { i -> fajrs.minOf { it[i] } }
        val ishaAngle = (method.isha as? IshaRule.Angle)?.degrees
        val isha = if (rule.isha != null && ishaAngle != null) {
            val ishas = latitudes.flatMap { lat ->
                listOf(true, false).map { onGrid -> TwilightCurves.isha(lat, ishaAngle, onGrid = onGrid, fraction = rule.isha) }
            }
            DoubleArray(slots) { i -> ishas.maxOf { it[i] } }
        } else {
            method.ishaAngleByDayOfYear
        }
        val end = if (method.endOfEating == EndOfEating.SameAsFajrDawn) {
            EndOfEating.DawnAngle(method.fajrAngle, DoubleArray(slots) { i -> fajrs.maxOf { it[i] } })
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
    private const val MOONSIGHTING_ID = "other.moonsighting"

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
