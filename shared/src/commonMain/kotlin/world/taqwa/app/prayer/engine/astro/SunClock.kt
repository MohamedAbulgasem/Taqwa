package world.taqwa.app.prayer.engine.astro

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** How solar noon and other events are located within the civil date. */
enum class SunModel { EXACT, DAILY_0H_UT, CLASSIC_NOON }

/** How the Asr shadow altitude and the moment it is reached are sourced. */
enum class AsrModel { EXACT_MOMENT, NOON_SHADOW, UTC12_ONE_SHOT, DAILY_0H_UT }

/** Which side of a [Crossing] a time is read on: a start's, never before the sun's moment, or an end's, never after it. */
enum class Side {
    START, END;

    /** The other side. */
    val other: Side get() = if (this == START) END else START
}

/**
 * One crossing of an altitude by the sun ([SunClock.crossing]), in UTC epoch seconds: [kept], the moment the EXACT model's
 * iteration stopped on, or the bisection's where it found no step, and [exact], the sun's own moment there, by bisection on
 * its altitude. Up to 20 Nov 2026 [SunClock] gave that one moment, [kept], for every crossing it found; it also found some
 * it no longer does, where a step settled on a night the sun only grazes the altitude and does not cross it, and there it
 * now finds none (the final review of 20 Nov 2026). The two are one moment under the other models and wherever the step
 * settled at least an hour from both culminations. Near one, or where it did not settle, the step can stop off the sun's
 * moment, as a rule an evening's before it and a morning's after it: on Tromsø's grid by up to 18 s, and by up to about a
 * minute near 80° N. So a time read from a crossing takes the side that is never wrong (the final review of 20 Nov
 * 2026): a start the later moment ([start]), an end the earlier ([end]). Maghrib and Isha, read from evening crossings,
 * move later or stay; sunrise and the end of eating, from morning ones, earlier or stay; Fajr, a morning start, stays on
 * its step, the later.
 */
class Crossing(val kept: Double, val exact: Double) {
    /** A start's moment: the later of the two. */
    val start: Double get() = if (exact > kept) exact else kept

    /** An end's moment: the earlier of the two. */
    val end: Double get() = if (exact < kept) exact else kept

    /** [start] or [end]. */
    fun on(side: Side): Double = if (side == Side.START) start else end
}

/**
 * Solar event times (transit, altitude crossings, Asr) for one place and civil date, all returned
 * as UTC epoch seconds (`Double`, unrounded). Built on [SolarMath]; nothing but kotlin.math and
 * kotlinx-datetime, so it compiles anywhere `shared` does, including the plain-JVM `tools/timetables`.
 *
 * [lat]/[lon] in degrees, east and north positive. [utcOffsetSeconds] is the zone offset used to
 * decide which events fall on [date]. [phaseYear], when set, replaces the sun's calendar position
 * for [SunModel.DAILY_0H_UT] and [AsrModel.DAILY_0H_UT] with 00:00 UTC of the same month/day in that
 * year (29 February maps to 28 February) — used to phase these models to a fixed reference year.
 */
class SunClock(
    private val lat: Double,
    private val lon: Double,
    private val date: LocalDate,
    private val utcOffsetSeconds: Int,
    private val model: SunModel,
    private val phaseYear: Int? = null,
) {
    private val dayStartUtc: Double = date.toEpochDays() * SolarMath.SECONDS_PER_DAY

    /** the epoch instant (00:00 UTC) the DAILY_0H_UT models sample the sun at. */
    private val dailyEpoch: Double by lazy {
        val year = phaseYear
        if (year == null) {
            dayStartUtc
        } else {
            val month = date.monthNumber
            val day = if (month == 2 && date.dayOfMonth == 29) 28 else date.dayOfMonth
            LocalDate(year, month, day).toEpochDays() * SolarMath.SECONDS_PER_DAY
        }
    }

    private val dailySun: SolarMath.Sun by lazy { SolarMath.sun(SolarMath.julianDay(dailyEpoch)) }

    /** the local civil date (per [utcOffsetSeconds]) of a UTC epoch-second instant. */
    private fun localDateOf(epochSeconds: Double): LocalDate {
        val localSeconds = epochSeconds + utcOffsetSeconds
        val epochDays = floor(localSeconds / SolarMath.SECONDS_PER_DAY).toLong()
        return LocalDate.fromEpochDays(epochDays)
    }

    /** solar noon for [sun]'s equation of time, snapped onto [date] in the local zone. */
    private fun noonFor(sun: SolarMath.Sun): Double {
        var noon = dayStartUtc + (12.0 - lon / 15.0) * 3600.0 - sun.equationOfTimeMinutes * 60.0
        while (localDateOf(noon) < date) noon += SolarMath.SECONDS_PER_DAY
        while (localDateOf(noon) > date) noon -= SolarMath.SECONDS_PER_DAY
        return noon
    }

    /** the longitude-only noon estimate (equation of time zero), snapped onto [date]. */
    private fun initialNoonGuess(): Double = noonFor(SolarMath.Sun(0.0, 0.0))

    /** hour angle in degrees for [altitudeDeg] at declination [declinationDeg]; null if never reached. */
    private fun hourAngleDeg(altitudeDeg: Double, declinationDeg: Double): Double? {
        val phi = SolarMath.rad(lat)
        val delta = SolarMath.rad(declinationDeg)
        val cosH = (sin(SolarMath.rad(altitudeDeg)) - sin(phi) * sin(delta)) / (cos(phi) * cos(delta))
        if (abs(cosH) > 1.0) return null
        return SolarMath.deg(acos(cosH))
    }

    /** the Asr shadow altitude for shadow factor [shadowFactor] at shadow-declination [declinationDeg]. */
    private fun asrAltitudeDeg(shadowFactor: Double, declinationDeg: Double): Double =
        SolarMath.deg(atan(1.0 / (shadowFactor + tan(abs(SolarMath.rad(lat) - SolarMath.rad(declinationDeg))))))

    /** solar transit (local noon), in UTC epoch seconds. */
    fun transit(): Double = when (model) {
        SunModel.DAILY_0H_UT -> noonFor(dailySun)
        SunModel.EXACT, SunModel.CLASSIC_NOON -> {
            var t = initialNoonGuess()
            repeat(6) {
                val sun = SolarMath.sun(SolarMath.julianDay(t))
                t = noonFor(sun)
            }
            t
        }
    }

    /**
     * The sun's crossing of [altitudeDeg], before transit when [morning] else after ([Crossing]: the step kept and the
     * sun's own moment, a start reading the later and an end the earlier). Null when the sun never reaches that altitude
     * on [date] (e.g. midnight sun, polar night).
     *
     * [SunModel.EXACT] iterates from noon with the sun at each candidate moment. That iteration can
     * miss a twilight the sun only just reaches: its first guess judges the depression with noon's
     * declination, which in spring (mornings) and autumn (evenings) is a tenth of a degree shallower
     * than the sun's at its lowest point, and near that point the iteration does not settle. So when
     * it finds nothing, the crossing is looked for on the sun's own altitude between the sun's lowest
     * point that night and transit ([bisect]); null only when the sun at its lowest point stays above
     * [altitudeDeg] (Kazan's 18° dawn of 5 May 2026, at 18.05° only 14 min after the sun's lowest
     * point, was reported missing before).
     *
     * The other way round too (ruling R321): near either culmination the iteration can end on a moment that is no
     * crossing. Each step reads the hour angle with the declination at the step before, so on a night the sun only grazes
     * [altitudeDeg] it creeps toward the lower culmination and its last step lands short of it, where the sun is still
     * above [altitudeDeg]: at 69.895° N, 18.852° E on 16 May 2026 the sun sinks to -0.83324° at 22:40:32.7 UTC, above
     * the real horizon, yet a sunset at 22:40:58 UTC (the sun there at -0.83321°) was reported, with no sunrise after
     * it. Within about a week of the solstices the steps even settle there, onto the sun's lowest point, to within 1e-6°
     * of an altitude the sun misses by less (the final review of 20 Nov 2026: at 65.802° N, 12.533° E the sun stays
     * 6.3e-8° above the real horizon in the night of 25 to 26 June 2026, and a sunrise at 23:12:45 UTC was reported with
     * no sunset before it). So whether the sun crosses is one question, asked of every crossing, settled or not
     * ([bracket]): above [altitudeDeg] at this date's transit, and below it at its own lowest point that night, which is
     * found from the evening's side for the next morning too, so that a night's evening crossing and its next morning's
     * are found or not together wherever the sun is above [altitudeDeg] at both transits. The one step that need not ask
     * is a step settled at least an hour of hour angle from both culminations ([clear]): there the sun is surely above
     * at transit and below at its lowest, by far more than the step's 1e-6° ([CLEAR_HOUR_ANGLE_DEG]). Where the sun
     * crosses, the step is kept beside the sun's own moment there ([bisect]), and where no step was found the moment is
     * the bisection's alone.
     */
    fun crossing(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double = 0.0): Crossing? =
        when (model) {
            SunModel.DAILY_0H_UT -> {
                val declination = dailySun.declinationDeg + declinationBiasDeg
                val h = hourAngleDeg(altitudeDeg, declination) ?: return null
                val noon = noonFor(dailySun)
                at(if (morning) noon - h * SECONDS_PER_HOUR_ANGLE_DEGREE else noon + h * SECONDS_PER_HOUR_ANGLE_DEGREE)
            }
            SunModel.CLASSIC_NOON -> {
                val t0 = transit()
                val sun = SolarMath.sun(SolarMath.julianDay(t0))
                val declination = sun.declinationDeg + declinationBiasDeg
                val h = hourAngleDeg(altitudeDeg, declination) ?: return null
                at(if (morning) t0 - h * SECONDS_PER_HOUR_ANGLE_DEGREE else t0 + h * SECONDS_PER_HOUR_ANGLE_DEGREE)
            }
            SunModel.EXACT -> {
                val step = iteratedCrossing(altitudeDeg, morning, declinationBiasDeg)
                if (step != null && clear(step, altitudeDeg, declinationBiasDeg)) {
                    at(step.at)
                } else {
                    val bracket = bracket(altitudeDeg, morning, declinationBiasDeg) ?: return null
                    val exact = bisect(bracket, altitudeDeg, declinationBiasDeg)
                    Crossing(step?.at ?: exact, exact)
                }
            }
        }

    /** [crossing]'s moment for a start ([Crossing.start]); null where the sun does not cross [altitudeDeg]. */
    fun startTime(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double = 0.0): Double? =
        crossing(altitudeDeg, morning, declinationBiasDeg)?.start

    /** [crossing]'s moment for an end ([Crossing.end]); null where the sun does not cross [altitudeDeg]. */
    fun endTime(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double = 0.0): Double? =
        crossing(altitudeDeg, morning, declinationBiasDeg)?.end

    /** A crossing at one moment, kept and exact alike. */
    private fun at(epochSeconds: Double): Crossing = Crossing(epochSeconds, epochSeconds)

    /** A step of the EXACT iteration: the moment it stopped on, and the hour angle (0° to 180°) that step read. */
    private class Step(val at: Double, val hourAngleDeg: Double)

    /** The EXACT crossing by fixed-point iteration from noon; null when an iterate finds no hour angle. */
    private fun iteratedCrossing(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double): Step? {
        var t = initialNoonGuess()
        var h = 0.0
        repeat(6) {
            val sun = SolarMath.sun(SolarMath.julianDay(t))
            val noon = noonFor(sun)
            val declination = sun.declinationDeg + declinationBiasDeg
            h = hourAngleDeg(altitudeDeg, declination) ?: return null
            t = if (morning) noon - h * SECONDS_PER_HOUR_ANGLE_DEGREE else noon + h * SECONDS_PER_HOUR_ANGLE_DEGREE
        }
        return Step(t, h)
    }

    /**
     * Whether [step] answers [crossing]'s question by itself: settled (the sun at its moment within [SETTLED_DEG] of
     * [altitudeDeg]) at least [CLEAR_HOUR_ANGLE_DEG] of hour angle from both culminations, short of the poles.
     */
    private fun clear(step: Step, altitudeDeg: Double, declinationBiasDeg: Double): Boolean =
        step.hourAngleDeg in CLEAR_HOUR_ANGLE_DEG..(180.0 - CLEAR_HOUR_ANGLE_DEG) && abs(lat) <= CLEAR_LATITUDE_DEG &&
            abs(altitudeAt(step.at, declinationBiasDeg) - altitudeDeg) <= SETTLED_DEG

    /** The moments a crossing lies between, the sun above [above] and below [below]. */
    private class Bracket(val above: Double, val below: Double)

    /**
     * Whether the sun crosses [altitudeDeg] on the event's side of transit, as [crossing] asks it of every crossing it
     * reports (rulings R321 and the final review of 20 Nov 2026): above it at this date's transit and below it at its own
     * lowest point in the night on the event's side, that point found by [nightLowest] from the evening before it (the
     * date before, for a morning), so that the evening's crossing and the next morning's read one and the same moment and
     * altitude. The two moments the crossing lies between where it does; null where it does not.
     */
    private fun bracket(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double): Bracket? {
        val noon = transit()
        if (altitudeAt(noon, declinationBiasDeg) <= altitudeDeg) return null
        val evening = if (morning) dayBefore() else this
        val lowest = evening.nightLowest(declinationBiasDeg)
        if (evening.altitudeAt(lowest, declinationBiasDeg) >= altitudeDeg) return null
        return Bracket(above = noon, below = lowest)
    }

    /** The same sky on the date before [date]: the evening a morning's night begins on. */
    private fun dayBefore(): SunClock = SunClock(lat, lon, LocalDate.fromEpochDays(date.toEpochDays() - 1), utcOffsetSeconds, model, phaseYear)

    /** The moment of the sun's lowest altitude in the night after this date's transit. */
    private fun nightLowest(declinationBiasDeg: Double): Double = lowest(transit() + HALF_DAY_SECONDS, declinationBiasDeg)

    /**
     * The EXACT crossing by bisection on the sun's own altitude (its declination and equation of time at each moment)
     * across [bracket]: from transit to the sun's lowest point on the event's side, over which it sinks (or, before
     * transit, rises) steadily.
     */
    private fun bisect(bracket: Bracket, altitudeDeg: Double, declinationBiasDeg: Double): Double {
        var below = bracket.below
        var above = bracket.above
        repeat(BISECTION_STEPS) {
            val mid = (below + above) / 2
            if (altitudeAt(mid, declinationBiasDeg) < altitudeDeg) below = mid else above = mid
        }
        return (below + above) / 2
    }

    /**
     * The moment of the sun's lowest altitude within an hour of the lower culmination [culmination], by golden-section
     * search (ruling R321). Not the culmination itself: the sun's declination moves, so near the polar circle its lowest
     * point is up to about half a minute from the culmination and up to about 1e-4° lower, and a night the sun dips below
     * an altitude by less than that has its crossings all the same, the evening's and the next morning's alike (at
     * 70.132° N, 18.852° E the sun sinks 2e-5° below the real horizon in the night of 27 to 28 July 2026: the sunrise was
     * found and the sunset before it was not).
     */
    private fun lowest(culmination: Double, declinationBiasDeg: Double): Double {
        var a = culmination - LOWEST_WINDOW_SECONDS
        var b = culmination + LOWEST_WINDOW_SECONDS
        var c = b - GOLDEN * (b - a)
        var d = a + GOLDEN * (b - a)
        var fc = altitudeAt(c, declinationBiasDeg)
        var fd = altitudeAt(d, declinationBiasDeg)
        repeat(LOWEST_STEPS) {
            if (fc < fd) {
                b = d; d = c; fd = fc; c = b - GOLDEN * (b - a); fc = altitudeAt(c, declinationBiasDeg)
            } else {
                a = c; c = d; fc = fd; d = a + GOLDEN * (b - a); fd = altitudeAt(d, declinationBiasDeg)
            }
        }
        return (a + b) / 2
    }

    /** The sun's altitude in degrees at [epochSeconds] (its hour angle from [date]'s noon by that moment's equation of time). */
    private fun altitudeAt(epochSeconds: Double, declinationBiasDeg: Double): Double {
        val sun = SolarMath.sun(SolarMath.julianDay(epochSeconds))
        val hourAngle = SolarMath.rad((epochSeconds - noonFor(sun)) / SECONDS_PER_HOUR_ANGLE_DEGREE)
        val phi = SolarMath.rad(lat)
        val delta = SolarMath.rad(sun.declinationDeg + declinationBiasDeg)
        return SolarMath.deg(asin(sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(hourAngle)))
    }

    /**
     * The UTC epoch second the sun's shadow reaches [shadowFactor] times an object's height, after
     * transit. Null when the target altitude is never reached.
     */
    fun asr(shadowFactor: Double, asrModel: AsrModel, declinationBiasDeg: Double = 0.0): Double? =
        when (asrModel) {
            AsrModel.UTC12_ONE_SHOT -> {
                val sun = SolarMath.sun(SolarMath.julianDay(dayStartUtc + 12.0 * 3600.0))
                val declination = sun.declinationDeg + declinationBiasDeg
                val altitude = asrAltitudeDeg(shadowFactor, declination)
                val h = hourAngleDeg(altitude, declination) ?: return null
                noonFor(sun) + h * SECONDS_PER_HOUR_ANGLE_DEGREE
            }
            AsrModel.DAILY_0H_UT -> {
                val declination = dailySun.declinationDeg + declinationBiasDeg
                val altitude = asrAltitudeDeg(shadowFactor, declination)
                val h = hourAngleDeg(altitude, declination) ?: return null
                noonFor(dailySun) + h * SECONDS_PER_HOUR_ANGLE_DEGREE
            }
            AsrModel.NOON_SHADOW -> {
                val shadowSun = SolarMath.sun(SolarMath.julianDay(transit()))
                val shadowDeclination = shadowSun.declinationDeg + declinationBiasDeg
                val altitude = asrAltitudeDeg(shadowFactor, shadowDeclination)
                var t = initialNoonGuess()
                repeat(6) {
                    val sun = SolarMath.sun(SolarMath.julianDay(t))
                    val noon = noonFor(sun)
                    val declinationH = sun.declinationDeg + declinationBiasDeg
                    val h = hourAngleDeg(altitude, declinationH) ?: return null
                    t = noon + h * SECONDS_PER_HOUR_ANGLE_DEGREE
                }
                t
            }
            AsrModel.EXACT_MOMENT -> {
                var t = initialNoonGuess()
                repeat(6) {
                    val sun = SolarMath.sun(SolarMath.julianDay(t))
                    val noon = noonFor(sun)
                    val declination = sun.declinationDeg + declinationBiasDeg
                    val altitude = asrAltitudeDeg(shadowFactor, declination)
                    val h = hourAngleDeg(altitude, declination) ?: return null
                    t = noon + h * SECONDS_PER_HOUR_ANGLE_DEGREE
                }
                t
            }
        }

    private companion object {
        const val SECONDS_PER_HOUR_ANGLE_DEGREE = 240.0
        const val HALF_DAY_SECONDS = 43_200.0

        /** Halvings of the 12 h bracket: 2⁻³² of it is 10 µs. */
        const val BISECTION_STEPS = 32

        /**
         * How near [crossing]'s step must put the sun to the altitude asked for to count as settled ([clear]). Settled
         * is not enough near a culmination, where the sun's altitude barely moves: there a step within 1e-6° can still be
         * no crossing at all (the sun's lowest point 6.3e-8° above the real horizon at 65.802° N, 12.533° E in the night
         * of 25 to 26 June 2026), or seconds from the sun's own moment. Two quantities, not one, on the night of ruling
         * R321 (69.895° N, 18.852° E, 16 May 2026): the step's offset from the horizon, 9.5e-5°, and the margin the sun
         * stays above it by at its lowest, 6.3e-5°.
         */
        const val SETTLED_DEG = 1e-6

        /**
         * How far in hour angle from both culminations (an hour of the sun's turn) a settled step stands without [bracket]'s
         * question (the final review of 20 Nov 2026). There the sun at transit is above the step's altitude, and at its
         * lowest below it, by far more than [SETTLED_DEG]: over half a degree at Tromsø, still a tenth of a degree at
         * 85°; the declination's drift over the hours between is smaller. Every other step asks.
         */
        const val CLEAR_HOUR_ANGLE_DEG = 15.0

        /** Beyond this latitude the sun's turn is too flat for [CLEAR_HOUR_ANGLE_DEG]'s margin to be sure: every step asks. */
        const val CLEAR_LATITUDE_DEG = 85.0

        /** [lowest]'s search: an hour either side of the culmination, to under a millisecond. */
        const val LOWEST_WINDOW_SECONDS = 3_600.0
        const val LOWEST_STEPS = 40
        const val GOLDEN = 0.6180339887498949
    }
}
