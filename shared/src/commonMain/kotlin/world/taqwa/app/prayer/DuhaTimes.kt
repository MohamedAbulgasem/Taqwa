package world.taqwa.app.prayer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * One morning's Duha (Duha and Iqama spec §2): [start] is when its time is in for everyone,
 * [best] the midpoint of sunrise and solar noon (never before [start]), and [end] ten minutes
 * before noon. Starts are rounded up to the minute and the end down, so neither is ever shown
 * outside the time.
 */
data class Duha(val start: Instant, val best: Instant, val end: Instant)

/**
 * Duha's times, one rule for everyone (spec §2, decision 2). Beside [NightThirds], and like it
 * pure: [of] holds the rules on given instants, [forDay] reads the sun at the user's own point.
 */
object DuhaTimes {

    /** Duha begins this long after sunrise at the earliest: Diyanet's 40–50 minutes at their latest. */
    val AFTER_SUNRISE = 50.minutes

    /** And not before the sun is this high, so a far-north morning's slow sun is waited for. */
    const val START_ALTITUDE_DEG = 6.5

    /** Duha ends this long before solar noon, never later than any authority's own end. */
    val BEFORE_NOON = 10.minutes

    /**
     * Duha on a morning whose sun rose at [trueSunrise], whose authority shows sunrise at
     * [shownSunrise], whose sun reached 6.5° at [sunAt6_5] and crossed the meridian at
     * [solarNoon]. The later of the two sunrises counts, so an authority's margin can never pull
     * Duha earlier. Null where the sun does not rise, never reaches 6.5° on its way up, or where
     * Duha's start would not be before its end.
     */
    fun of(trueSunrise: Instant?, shownSunrise: Instant, sunAt6_5: Instant?, solarNoon: Instant): Duha? {
        if (trueSunrise == null || sunAt6_5 == null) return null
        val sunrise = maxOf(trueSunrise, shownSunrise)
        val start = ceilToMinute(maxOf(sunrise + AFTER_SUNRISE, sunAt6_5))
        val best = maxOf(ceilToMinute(sunrise + (solarNoon - sunrise) / 2), start)
        val end = floorToMinute(solarNoon - BEFORE_NOON)
        if (start >= end || best >= end) return null
        return Duha(start, best, end)
    }

    /**
     * Duha at [location] on [date], with the sun as the engine's own sky builds it: the exact
     * model, the zone's offset at local noon ([DayPipeline.realSunset] does the same), the true
     * sunrise at [DayPipeline.REAL_HORIZON_DEG]. [shownSunrise] is the day's shown sunrise.
     */
    fun forDay(location: GeoLocation, date: LocalDate, shownSunrise: Instant): Duha? {
        val zone = TimeZone.of(location.timeZoneId)
        val noonOffset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val sun = SunClock(location.latitude, location.longitude, date, noonOffset, SunModel.EXACT)
        return of(
            trueSunrise = sun.altitudeTime(DayPipeline.REAL_HORIZON_DEG, morning = true)?.let(::instantOf),
            shownSunrise = shownSunrise,
            sunAt6_5 = sun.altitudeTime(START_ALTITUDE_DEG, morning = true)?.let(::instantOf),
            solarNoon = instantOf(sun.transit()),
        )
    }

    private fun instantOf(epochSeconds: Double): Instant =
        Instant.fromEpochMilliseconds((epochSeconds * 1000.0).roundToLong())

    private fun floorToMinute(at: Instant): Instant =
        Instant.fromEpochSeconds(at.epochSeconds.floorDiv(60L) * 60L)

    private fun ceilToMinute(at: Instant): Instant {
        val floor = floorToMinute(at)
        return if (floor == at) floor else floor + 1.minutes
    }
}
