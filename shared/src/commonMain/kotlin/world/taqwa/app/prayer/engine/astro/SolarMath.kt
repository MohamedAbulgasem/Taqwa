package world.taqwa.app.prayer.engine.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * The sun's apparent position by the NOAA / Meeus low-precision formulas, accurate to a few seconds
 * of time for prayer events. Pure; nothing but kotlin.math.
 */
object SolarMath {
    const val SECONDS_PER_DAY = 86_400.0
    private const val J2000 = 2_451_545.0

    data class Sun(val declinationDeg: Double, val equationOfTimeMinutes: Double)

    fun julianDay(epochSeconds: Double): Double = epochSeconds / SECONDS_PER_DAY + 2_440_587.5

    fun sun(jd: Double): Sun {
        val t = (jd - J2000) / 36_525.0
        val l0 = normalizeDegrees(280.46646 + t * (36_000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mr = rad(m)
        val c = sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mr) * (0.019993 - 0.000101 * t) + sin(3 * mr) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val declination = deg(asin(sin(rad(eps)) * sin(rad(lambda))))
        val y = tan(rad(eps / 2)).pow(2)
        val l0r = rad(l0)
        val eot = 4 * deg(
            y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) -
                0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr),
        )
        return Sun(declination, eot)
    }

    fun rad(d: Double) = d * PI / 180.0
    fun deg(r: Double) = r * 180.0 / PI
    fun normalizeDegrees(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
}
