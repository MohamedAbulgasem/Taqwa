package world.taqwa.app.prayer.engine.astro

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The sun the real sun's floor reads (ruling R180: [SunModel.EXACT] on [SolarMath], the transit and the sea-level
 * sunset) held against a sun written out separately here, so that the floor's "real" is not checked against its own
 * code alone (the reviews of 13 Nov 2026). The second sun is the Astronomical Almanac's low-precision one: the mean
 * longitude and anomaly, two terms of the equation of the centre, the right ascension by its own arctangent and the
 * equation of time as mean longitude less right ascension, where [SolarMath] takes Meeus's longitude and Smart's
 * series. The Almanac states it to 0.01° and the equation of time to a tenth of a minute, 1950 to 2050.
 *
 * It bounds the model, it does not replace it: two low-precision suns agreeing says neither is grossly wrong (a sign,
 * a longitude, a day), not that either is right to the second. No ephemeris to the second is held here.
 */
class RealSunModelTest {

    private class Sun(val declinationRad: Double, val equationOfTimeSeconds: Double)

    private fun rad(d: Double) = d * kotlin.math.PI / 180.0

    private fun deg(r: Double) = r * 180.0 / kotlin.math.PI

    private fun wrap360(d: Double) = ((d % 360.0) + 360.0) % 360.0

    /** The Almanac's low-precision sun at [epochSeconds]. */
    private fun almanac(epochSeconds: Double): Sun {
        val n = epochSeconds / 86_400.0 + 2_440_587.5 - 2_451_545.0
        val meanLongitude = wrap360(280.460 + 0.9856474 * n)
        val g = rad(wrap360(357.528 + 0.9856003 * n))
        val lambda = rad(meanLongitude + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val epsilon = rad(23.439 - 0.0000004 * n)
        val rightAscension = wrap360(deg(atan2(cos(epsilon) * sin(lambda), cos(lambda))))
        val equation = wrap360(meanLongitude - rightAscension + 180.0) - 180.0
        return Sun(asin(sin(epsilon) * sin(lambda)), equation * 240.0)
    }

    /** The Almanac sun's transit at [lon] on the UTC [date] (the test's longitudes keep the transit on that date). */
    private fun almanacTransit(lon: Double, date: LocalDate): Double {
        val meanNoon = date.toEpochDays() * 86_400.0 + (12.0 - lon / 15.0) * 3600.0
        var t = meanNoon
        repeat(4) { t = meanNoon - almanac(t).equationOfTimeSeconds }
        return t
    }

    /** The Almanac sun's sunset at [lat], [lon] on [date]: the upper limb on the sea horizon with standard refraction. */
    private fun almanacSunset(lat: Double, lon: Double, date: LocalDate): Double? {
        val meanNoon = date.toEpochDays() * 86_400.0 + (12.0 - lon / 15.0) * 3600.0
        var t = almanacTransit(lon, date)
        repeat(4) {
            val sun = almanac(t)
            val cosH = (sin(rad(REAL_HORIZON_DEG)) - sin(rad(lat)) * sin(sun.declinationRad)) / (cos(rad(lat)) * cos(sun.declinationRad))
            if (abs(cosH) > 1.0) return null
            t = meanNoon - sun.equationOfTimeSeconds + deg(acos(cosH)) * 240.0
        }
        return t
    }

    private val longitudes = listOf(-150.0, -74.0, -8.0, 0.0, 31.25, 35.2, 67.0, 106.8, 170.0)

    /** Every 5th day of 2026 to 2035. */
    private fun dates(): Sequence<LocalDate> =
        generateSequence(LocalDate(2026, 1, 1)) { it.plus(5, DateTimeUnit.DAY) }.takeWhile { it.year <= 2035 }

    @Test
    fun `the exact transit is the almanac sun's to within the almanac's tenth of a minute`() {
        var worst = 0.0
        for (date in dates()) {
            for (lon in longitudes) {
                val exact = SunClock(0.0, lon, date, 0, SunModel.EXACT).transit()
                val other = almanacTransit(lon, date)
                worst = maxOf(worst, abs(exact - other))
                assertTrue(abs(exact - other) <= ALMANAC_SECONDS, "$date at $lon: the exact transit $exact against the Almanac sun's $other")
            }
        }
        assertTrue(worst > 0.0, "two suns, not one compared with itself")
    }

    @Test
    fun `the exact sea level sunset is the almanac sun's to within a quarter of a minute up to 60 degrees`() {
        for (date in dates()) {
            for (lon in listOf(-74.0, 31.25, 106.8)) {
                for (lat in listOf(-60.0, -45.0, -33.9, -6.2, 0.0, 21.4, 30.0, 41.0, 51.5, 60.0)) {
                    val exact = assertNotNull(SunClock(lat, lon, date, 0, SunModel.EXACT).altitudeTime(REAL_HORIZON_DEG, morning = false), "$date $lat")
                    val other = assertNotNull(almanacSunset(lat, lon, date), "$date $lat")
                    assertTrue(abs(exact - other) <= SUNSET_SECONDS, "$date at $lat, $lon: the exact sunset $exact against the Almanac sun's $other")
                }
            }
        }
    }

    private companion object {
        /** The floor's horizon, written again and not read from the pipeline. */
        const val REAL_HORIZON_DEG = -0.8333

        /** The Almanac's stated precision for its equation of time. */
        const val ALMANAC_SECONDS = 6.0

        /** That, and what the two declinations' hundredths of a degree move a sunset at 60°. */
        const val SUNSET_SECONDS = 15.0
    }
}
