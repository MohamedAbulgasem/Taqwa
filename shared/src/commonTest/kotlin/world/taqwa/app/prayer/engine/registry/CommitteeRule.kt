package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Moonsighting Committee's rule computed directly at one place and date, with this core's own sun
 * (the exact model, iterated to each crossing) and none of the engine's curves: the reference
 * [MoonsightingCommitteeTest] and the JVM sweep hold the Other method to (T3 round of 11 Oct 2026).
 *
 * Fajr is the later of 18° and the seasonal minutes before sunrise, Isha the earlier of 18° and the
 * seasonal minutes after sunset (shafaq general); from 55° of latitude, north or south, a seventh of
 * the night takes 18°'s place. Two Fajr definitions are kept, each the code of a source the method
 * follows: [committeeFajr] counts the night before sunrise for its seventh (moonsighting.com's own
 * tables, held in the archive, fit it), [adhan2Fajr] the night after sunset, from the day's sunset to
 * the next sunrise, as adhan2's code does (and only from 55° N: south of 55° S its Fajr and Isha are the
 * seasonal rule alone, which [adhan2Fajr] and [adhan2Isha] give there). The seasonal minutes are
 * adhan2's, rounded to the second as it rounds them. Epoch seconds, unrounded.
 */
internal class CommitteeRule(
    val sunrise: Double,
    val committeeFajr: Double,
    val adhan2Fajr: Double,
    val isha: Double,
    val adhan2Isha: Double,
    /** The night before sunrise and the night after sunset, in seconds. */
    val nightBefore: Double,
    val nightAfter: Double,
) {
    /** The later of the two Fajr definitions: no start may come before it. */
    val laterFajr: Double get() = max(committeeFajr, adhan2Fajr)

    /** The earlier of the two: an end after it is after one of them. */
    val earlierFajr: Double get() = min(committeeFajr, adhan2Fajr)

    companion object {
        /** The rule at ([lat], [lon]) on [date], the zone [offsetSeconds] from UTC; null where the sun does not rise or set on [date] or around it. */
        fun at(lat: Double, lon: Double, date: LocalDate, offsetSeconds: Int): CommitteeRule? {
            val today = SunClock(lat, lon, date, offsetSeconds, SunModel.EXACT)
            val sunrise = today.altitudeTime(HORIZON, true) ?: return null
            val sunset = today.altitudeTime(HORIZON, false) ?: return null
            val nextSunrise = SunClock(lat, lon, date.plus(1, DateTimeUnit.DAY), offsetSeconds, SunModel.EXACT).altitudeTime(HORIZON, true) ?: return null
            val lastSunset = SunClock(lat, lon, date.plus(-1, DateTimeUnit.DAY), offsetSeconds, SunModel.EXACT).altitudeTime(HORIZON, false) ?: return null
            val after = nextSunrise - sunset
            val before = sunrise - lastSunset
            val l = abs(lat) / 55.0
            val k = daysSinceSolstice(date, lat)
            val morning = seasonal(75 + 28.65 * l, 75 + 19.44 * l, 75 + 32.74 * l, 75 + 48.10 * l, k)
            val evening = seasonal(75 + 25.60 * l, 75 + 2.050 * l, 75 - 9.210 * l, 75 + 6.140 * l, k)
            val seasonalFajr = sunrise - (morning * 60.0).roundToInt()
            val seasonalIsha = sunset + (evening * 60.0).roundToInt()
            val dawn18 = today.altitudeTime(-18.0, true)
            val dusk18 = today.altitudeTime(-18.0, false)
            fun fajr(seventh: Boolean, night: Double): Double {
                val f = if (seventh) sunrise - floor(night / 7) else dawn18
                return if (f == null || f < seasonalFajr) seasonalFajr else f
            }
            fun isha(seventh: Boolean): Double {
                val i = if (seventh) sunset + floor(after / 7) else dusk18
                return if (i == null || i > seasonalIsha) seasonalIsha else i
            }
            return CommitteeRule(
                sunrise = sunrise,
                committeeFajr = fajr(abs(lat) >= SEVENTH_FROM, before),
                adhan2Fajr = fajr(lat >= SEVENTH_FROM, after),
                isha = isha(abs(lat) >= SEVENTH_FROM),
                adhan2Isha = isha(lat >= SEVENTH_FROM),
                nightBefore = before,
                nightAfter = after,
            )
        }

        /** [t] rounded to the nearest minute, as a table prints it. */
        fun printed(t: Double): Long = floor(t / 60.0 + 0.5).toLong() * 60

        private fun seasonal(a: Double, b: Double, c: Double, d: Double, dyy: Int): Double = when {
            dyy < 91 -> a + (b - a) / 91.0 * dyy
            dyy < 137 -> b + (c - b) / 46.0 * (dyy - 91)
            dyy < 183 -> c + (d - c) / 46.0 * (dyy - 137)
            dyy < 229 -> d + (c - d) / 46.0 * (dyy - 183)
            dyy < 275 -> c + (b - c) / 46.0 * (dyy - 229)
            else -> b + (a - b) / 91.0 * (dyy - 275)
        }

        private fun daysSinceSolstice(date: LocalDate, lat: Double): Int {
            val days = LocalDate(date.year, 12, 31).dayOfYear
            return if (lat >= 0) {
                val d = date.dayOfYear + 10
                if (d >= days) d - days else d
            } else {
                val d = date.dayOfYear - if (days == 366) 173 else 172
                if (d < 0) d + days else d
            }
        }

        private const val HORIZON = -0.8333
        private const val SEVENTH_FROM = 55.0
    }
}
