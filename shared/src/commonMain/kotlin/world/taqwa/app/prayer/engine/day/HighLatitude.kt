package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.prayer.engine.astro.Crossing
import world.taqwa.app.prayer.engine.astro.Side
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.math.abs

/**
 * Where the sun does not reach a twilight angle (spec §3.8, rules 1–4 and 6; the polar rule 5 and
 * the daily rate limit are [DayComputer]'s, since they work on whole days).
 *
 * 1. The real crossing whenever it happens, at any latitude.
 * 2. Otherwise the method's [HighLatRule] sets the sign at a fraction of the night: the night is
 *    last night (yesterday's sunset to today's sunrise) for a morning sign and tonight (sunset to
 *    tomorrow's sunrise) for an evening one; a morning sign is that fraction before sunrise, an
 *    evening sign that fraction after sunset. Where the neighbouring day has no sunset or sunrise
 *    (the edge of a polar day or night), the night borrows the day's own, a day away.
 *    - [HighLatRule.Standard] (and [HighLatRule.DumRtSummer], whose own times [DayComputer] puts in
 *      place of the estimate on the nights its dawn is missing): the MWL Fiqh
 *      Council's proportion (1986): the fraction the same sign takes of the same night at 45° in
 *      the point's own hemisphere, on its meridian and date.
 *    - [HighLatRule.NightFraction]: the authority's own fractions.
 *    - [HighLatRule.Legacy]: half the night (`middle`), a seventh (`seventh`) or the angle's
 *      sixtieth (`angle`).
 *
 * Each sign is read on a [Side] (the final review of 20 Nov 2026): a start's (Fajr, Isha) takes each crossing's later
 * moment and an end's (the end of eating) its earlier ([world.taqwa.app.prayer.engine.astro.Crossing]), the real sign's
 * and the night's alike, so an estimate for a start never comes before the one from the steps kept, nor one for an end
 * after it. The proportion's fraction reads the reference's sign on the same side and its night on the other, which
 * moves it the same way.
 */
object HighLatitude {
    /** The MWL's reference latitude for the proportion. */
    const val REFERENCE_LATITUDE = 45.0

    /** Where the sign is missing at 45° too (an angle deeper than about 21.5°): the middle of the night. */
    private const val UNREACHED_AT_REFERENCE = 0.5

    /** A sign surely occurs on a day whose sun sinks this much deeper than its angle at lower culmination. */
    private const val SURELY_REACHED_MARGIN_DEG = 1.0

    /** The sun's greatest declination, with a little to spare. */
    private const val MAX_DECLINATION_DEG = 23.5

    /**
     * The [twilight] sign on [date] under [sky], read on [side]: the real crossing when the sun reaches it, else
     * [rule]'s estimate. Null only when [date] itself has no sunset (for an evening sign) or no
     * sunrise (for a morning one) and the sign does not occur. Whether it is estimated does not depend on [side].
     */
    internal fun sign(sky: Sky, rule: HighLatRule, twilight: Twilight, date: LocalDate, side: Side): Sign? {
        real(sky, twilight, date, side)?.let { return Sign(it, estimated = false) }
        val (dusk, dawn) = night(sky, twilight, date, side) ?: return null
        val part = fraction(sky, rule, twilight, date, side) * (dawn - dusk)
        return Sign(if (twilight.morning) dawn - part else dusk + part, estimated = true)
    }

    /**
     * Whether the sign surely occurs at [sky]'s point on [date], judged from the sun's declination
     * alone (no crossing computed): at this latitude every day of the year, or this day with a
     * degree to spare at the sun's lower culmination.
     */
    internal fun surelyOccurs(sky: Sky, twilight: Twilight, date: LocalDate): Boolean =
        alwaysReached(sky, twilight, date) || surelyReached(sky, twilight, date)

    /** Whether the sun surely rises and sets at [sky]'s point on [date], with a degree to spare. */
    internal fun surelyRisesAndSets(sky: Sky, date: LocalDate): Boolean {
        val lat = abs(sky.point.lat)
        if (lat + MAX_DECLINATION_DEG + 1.0 + SURELY_REACHED_MARGIN_DEG <= 90.0) return true
        return 90.0 - lat - abs(sky.declinationDeg(date)) >= 1.0 + SURELY_REACHED_MARGIN_DEG
    }

    /** Whether the sign occurs every day of the year at this latitude (no sun needed). */
    private fun alwaysReached(sky: Sky, twilight: Twilight, date: LocalDate): Boolean =
        90.0 - abs(sky.point.lat) - MAX_DECLINATION_DEG - abs(twilight.biasOn(date)) >=
            twilight.degreesOn(date) + SURELY_REACHED_MARGIN_DEG

    /** Whether the sun sinks well past the sign's angle at its lower culmination on [date]. */
    private fun surelyReached(sky: Sky, twilight: Twilight, date: LocalDate): Boolean {
        val declination = sky.declinationDeg(date) + twilight.biasOn(date)
        val towardsPole = if (sky.point.lat >= 0) declination else -declination
        val deepest = 90.0 - abs(sky.point.lat) - towardsPole
        return deepest >= twilight.degreesOn(date) + SURELY_REACHED_MARGIN_DEG
    }

    private fun real(sky: Sky, twilight: Twilight, date: LocalDate, side: Side): Double? =
        sky.crossing(date, -twilight.degreesOn(date), twilight.morning, twilight.biasOn(date))?.on(side)

    /**
     * The sunset and the sunrise of the night a sign belongs to, each read on [side]. The sign's own day must have its
     * edge (sunrise for a morning sign, sunset for an evening one); a neighbour without one borrows
     * the day's own, a day away (ruling R18). Null when the day's own edge is missing.
     */
    private fun night(sky: Sky, twilight: Twilight, date: LocalDate, side: Side): Pair<Double, Double>? =
        if (twilight.morning) {
            val sunrise = sky.sunrise(date)?.on(side) ?: return null
            val sunset = sky.sunset(date.minus(1, DateTimeUnit.DAY))?.on(side)
                ?: sky.sunset(date)?.on(side)?.minus(SolarMath.SECONDS_PER_DAY)
                ?: return null
            sunset to sunrise
        } else {
            val sunset = sky.sunset(date)?.on(side) ?: return null
            val sunrise = sky.sunrise(date.plus(1, DateTimeUnit.DAY))?.on(side)
                ?: sky.sunrise(date)?.on(side)?.plus(SolarMath.SECONDS_PER_DAY)
                ?: return null
            sunset to sunrise
        }

    /**
     * How far the real sign lies into its night from the nearer edge (sunrise for a morning sign): the sign read on [side]
     * and its night on the other, so the fraction moves an estimate for a start later and one for an end earlier (a
     * morning start's estimate falls with the fraction, an evening start's rises with it).
     */
    private fun realFraction(sky: Sky, twilight: Twilight, date: LocalDate, side: Side): Double? {
        val sign = real(sky, twilight, date, side) ?: return null
        val (dusk, dawn) = night(sky, twilight, date, side.other) ?: return null
        val part = if (twilight.morning) dawn - sign else sign - dusk
        return part / (dawn - dusk)
    }

    private fun fraction(sky: Sky, rule: HighLatRule, twilight: Twilight, date: LocalDate, side: Side): Double =
        when (rule) {
            HighLatRule.Standard, is HighLatRule.DumRtSummer ->
                realFraction(sky.reference, twilight, date, side) ?: UNREACHED_AT_REFERENCE
            is HighLatRule.NightFraction -> if (twilight.morning) rule.fajrFraction else rule.ishaFraction
            is HighLatRule.Legacy -> when (rule.kind) {
                HighLatRule.Legacy.MIDDLE -> 0.5
                HighLatRule.Legacy.SEVENTH -> 1.0 / 7.0
                else -> twilight.degreesOn(date) / 60.0 // ANGLE, the only other kind Legacy accepts
            }
        }
}

/** A twilight sign: the sun [degreesOn] a date below the horizon (with [biasOn]'s declination bias). */
internal class Twilight(
    val morning: Boolean,
    val degreesOn: (LocalDate) -> Double,
    val biasOn: (LocalDate) -> Double = { 0.0 },
)

/** A sign's moment in UTC epoch seconds, and whether a rule set it. */
internal class Sign(val epochSeconds: Double, val estimated: Boolean)

/**
 * The sky over [point] for [method]: one [SunClock] per civil date, on the method's sun model and
 * horizon, with the zone's offset at local noon of that date. Crossings are remembered for the
 * life of the sky (one place's computation, across the days the rate limit reads back).
 */
internal class Sky(private val method: TimetableMethod, val point: GeoPoint, private val zone: TimeZone) {
    private data class Key(val epochDay: Long, val altitudeDeg: Double, val morning: Boolean, val biasDeg: Double)

    private val crossings = HashMap<Key, Crossing>()

    fun clock(date: LocalDate): SunClock =
        SunClock(point.lat, point.lon, date, noonOffsetSeconds(date), method.sunModel, method.phaseYear)

    /** [SunClock.crossing] on [date], remembered: each time read from it takes its own [Side]. */
    fun crossing(date: LocalDate, altitudeDeg: Double, morning: Boolean, biasDeg: Double = 0.0): Crossing? {
        val key = Key(date.toEpochDays(), altitudeDeg, morning, biasDeg)
        val crossing = crossings.getOrPut(key) { clock(date).crossing(altitudeDeg, morning, biasDeg) ?: NONE }
        return crossing.takeUnless { it === NONE }
    }

    fun sunrise(date: LocalDate): Crossing? = crossing(date, method.horizonDeg, morning = true)

    fun sunset(date: LocalDate): Crossing? = crossing(date, method.horizonDeg, morning = false)

    /** The sun's declination near local noon of [date]. */
    fun declinationDeg(date: LocalDate): Double {
        val noon = date.toEpochDays() * SolarMath.SECONDS_PER_DAY + (12.0 - point.lon / 15.0) * 3600.0
        return SolarMath.sun(SolarMath.julianDay(noon)).declinationDeg
    }

    /** The MWL reference sky: 45° in this point's hemisphere, on its meridian. */
    val reference: Sky by lazy {
        at(GeoPoint(if (point.lat >= 0) HighLatitude.REFERENCE_LATITUDE else -HighLatitude.REFERENCE_LATITUDE, point.lon))
    }

    /** The same method and zone over another point. */
    fun at(other: GeoPoint) = Sky(method, other, zone)

    private fun noonOffsetSeconds(date: LocalDate): Int =
        zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds

    private companion object {
        /** A crossing the sun does not make, remembered as one. */
        val NONE = Crossing(Double.NaN, Double.NaN)
    }
}
