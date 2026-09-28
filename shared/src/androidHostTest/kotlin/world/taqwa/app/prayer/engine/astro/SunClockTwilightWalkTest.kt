package world.taqwa.app.prayer.engine.astro

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [SunClock.altitudeTime] (EXACT) against a brute-force scan of the sun's altitude: every night of
 * 2026 and of the leap year 2028, at 44–66° N in half-degree steps and three longitudes, for the
 * depressions authorities use (12°, 15°, 17°, 18°, 19.5°), mornings and evenings.
 *
 * The scan takes the sun's altitude every 30 s over the 80 minutes around its lower culmination
 * (transit ∓ 12 h), with its own formula (solar time from UTC, longitude and that moment's equation
 * of time). The verdict must agree: a depression the scan reaches is found, one it misses is not.
 * Where the found moment is checked, the scanned altitude must cross the depression within 5 s of
 * it. Nights whose lowest sun lies within [AMBIGUOUS_DEG] of the depression are too close for a scan
 * to call and are counted, not checked.
 *
 * It runs on the JVM host only (about 6 s there): the code it checks is common, and the iOS run
 * would only repeat it far more slowly.
 */
class SunClockTwilightWalkTest {

    /** The sun's altitude in degrees at [t], independently of [SunClock]. */
    private fun altitude(lat: Double, lon: Double, t: Double): Double {
        val sun = SolarMath.sun(SolarMath.julianDay(t))
        val utcHours = ((t / 3600.0) % 24.0 + 24.0) % 24.0
        val solarHours = utcHours + lon / 15.0 + sun.equationOfTimeMinutes / 60.0
        val h = SolarMath.rad((solarHours - 12.0) * 15.0)
        val phi = SolarMath.rad(lat)
        val d = SolarMath.rad(sun.declinationDeg)
        return SolarMath.deg(asin(sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h)))
    }

    @Test
    fun `a twilight the sun reaches is found and one it misses is not`() {
        val latitudes = (0..44).map { 44.0 + it * 0.5 }
        val longitudes = listOf(-100.0, 10.0, 120.0)
        val depressions = listOf(12.0, 15.0, 17.0, 18.0, 19.5)
        var checked = 0
        var found = 0
        var ambiguous = 0
        val failures = mutableListOf<String>()
        for (year in listOf(2026, 2028)) for (lat in latitudes) for (lon in longitudes) {
            val offset = (lon / 15.0).roundToInt() * 3600
            var date = LocalDate(year, 1, 1)
            while (date.year == year) {
                val clock = SunClock(lat, lon, date, offset, SunModel.EXACT)
                val noon = clock.transit()
                for (morning in listOf(true, false)) {
                    val culmination = if (morning) noon - 43_200.0 else noon + 43_200.0
                    var lowest = 90.0
                    var t = culmination - SCAN_HALF_WIDTH
                    while (t <= culmination + SCAN_HALF_WIDTH) {
                        lowest = min(lowest, altitude(lat, lon, t))
                        t += SCAN_STEP
                    }
                    for (depression in depressions) {
                        val reach = -lowest - depression // > 0: the sun sinks past the depression
                        if (abs(reach) < AMBIGUOUS_DEG) {
                            ambiguous++
                            continue
                        }
                        checked++
                        val at = clock.altitudeTime(-depression, morning)
                        val where = "$date ${if (morning) "morning" else "evening"} $lat $lon ${depression}°"
                        when {
                            reach > 0 && at == null -> if (failures.size < 20) failures += "$where: reached by ${-lowest}°, reported missing"
                            reach < 0 && at != null -> if (failures.size < 20) failures += "$where: missed (lowest ${-lowest}°), reported at $at"
                            at != null -> {
                                found++
                                val before = altitude(lat, lon, at - WITHIN_SECONDS)
                                val after = altitude(lat, lon, at + WITHIN_SECONDS)
                                val crosses = if (morning) before <= -depression && after >= -depression else before >= -depression && after <= -depression
                                if (!crosses && failures.size < 20) failures += "$where: found at $at, the scan does not cross within ${WITHIN_SECONDS}s"
                            }
                        }
                    }
                }
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
        assertTrue(checked > 400_000 && found > 200_000, "checked $checked, found $found")
        assertTrue(failures.isEmpty(), "${failures.size}+ failures of $checked (ambiguous $ambiguous):\n" + failures.joinToString("\n"))
    }

    private companion object {
        /** The scan runs 40 min either side of the lower culmination, where the sun is lowest. */
        const val SCAN_HALF_WIDTH = 2_400.0
        const val SCAN_STEP = 30.0
        const val WITHIN_SECONDS = 5.0

        /** A lowest sun this close to a depression is left uncalled (the 30 s scan's own error is under 0.0001°). */
        const val AMBIGUOUS_DEG = 0.001
    }
}
