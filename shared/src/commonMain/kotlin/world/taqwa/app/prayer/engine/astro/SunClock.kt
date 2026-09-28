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
     * The UTC epoch second the sun crosses [altitudeDeg], before transit when [morning] else after.
     * Null when the sun never reaches that altitude on [date] (e.g. midnight sun, polar night).
     *
     * [SunModel.EXACT] iterates from noon with the sun at each candidate moment. That iteration can
     * miss a twilight the sun only just reaches: its first guess judges the depression with noon's
     * declination, which in spring (mornings) and autumn (evenings) is a tenth of a degree shallower
     * than the sun's at its lowest point, and near that point the iteration does not settle. So when
     * it finds nothing, the crossing is looked for on the sun's own altitude between the lower
     * culmination and transit ([bracketedCrossing]); null only when the sun at its lowest point stays
     * above [altitudeDeg] (Kazan's 18° dawn of 5 May 2026, at 18.05° only 14 min after the sun's
     * lowest point, was reported missing before).
     */
    fun altitudeTime(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double = 0.0): Double? =
        when (model) {
            SunModel.DAILY_0H_UT -> {
                val declination = dailySun.declinationDeg + declinationBiasDeg
                val h = hourAngleDeg(altitudeDeg, declination) ?: return null
                val noon = noonFor(dailySun)
                if (morning) noon - h * SECONDS_PER_HOUR_ANGLE_DEGREE else noon + h * SECONDS_PER_HOUR_ANGLE_DEGREE
            }
            SunModel.CLASSIC_NOON -> {
                val t0 = transit()
                val sun = SolarMath.sun(SolarMath.julianDay(t0))
                val declination = sun.declinationDeg + declinationBiasDeg
                val h = hourAngleDeg(altitudeDeg, declination) ?: return null
                if (morning) t0 - h * SECONDS_PER_HOUR_ANGLE_DEGREE else t0 + h * SECONDS_PER_HOUR_ANGLE_DEGREE
            }
            SunModel.EXACT ->
                iteratedCrossing(altitudeDeg, morning, declinationBiasDeg)
                    ?: bracketedCrossing(altitudeDeg, morning, declinationBiasDeg)
        }

    /** The EXACT crossing by fixed-point iteration from noon; null when an iterate finds no hour angle. */
    private fun iteratedCrossing(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double): Double? {
        var t = initialNoonGuess()
        repeat(6) {
            val sun = SolarMath.sun(SolarMath.julianDay(t))
            val noon = noonFor(sun)
            val declination = sun.declinationDeg + declinationBiasDeg
            val h = hourAngleDeg(altitudeDeg, declination) ?: return null
            t = if (morning) noon - h * SECONDS_PER_HOUR_ANGLE_DEGREE else noon + h * SECONDS_PER_HOUR_ANGLE_DEGREE
        }
        return t
    }

    /**
     * The EXACT crossing by bisection on the sun's own altitude (its declination and equation of time
     * at each moment) between the lower culmination on the event's side (transit ∓ 12 h, where the
     * sun is at its lowest to well under a thousandth of a degree) and transit, over which it rises
     * (or, after transit, sinks) steadily; null when it is above [altitudeDeg] at the culmination.
     */
    private fun bracketedCrossing(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double): Double? {
        val noon = transit()
        val lowest = if (morning) noon - HALF_DAY_SECONDS else noon + HALF_DAY_SECONDS
        if (altitudeAt(lowest, declinationBiasDeg) >= altitudeDeg) return null
        if (altitudeAt(noon, declinationBiasDeg) <= altitudeDeg) return null
        // Morning: below the altitude at `below`, above it at `above` (and the mirror after transit).
        var below = lowest
        var above = noon
        repeat(BISECTION_STEPS) {
            val mid = (below + above) / 2
            if (altitudeAt(mid, declinationBiasDeg) < altitudeDeg) below = mid else above = mid
        }
        return (below + above) / 2
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
    }
}
