package world.taqwa.app.prayer.engine.astro

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SunClock.crossing] (EXACT) across broad grids, for the final review of 20 Nov 2026 and the reviews of its minors.
 *
 * One question for both sides of a night: in a band a few millionths of a degree either side of the latitude where the sun
 * stops reaching an altitude, sampled a hundred-millionth of a degree apart (where R321 still found one side alone, the
 * iteration having settled within 1e-6° of an altitude the sun misses), a night's evening crossing and its next morning's are
 * found or not together.
 *
 * Never early, read on the sun itself: a start is never before the sun's own moment and an end never after it, that moment
 * found by this test alone ([sunAltitude], from [SolarMath]'s sun, scanned and halved by [ownMoment]), through none of
 * [SunClock]'s code. That a start is the later of the step kept and the bisection's moment and an end the earlier is so by
 * construction ([Crossing.start], [Crossing.end]) and is not what is tested. And a step clear of both culminations (an hour of
 * hour angle or more, short of 85°) is the sun's own moment, so nothing read from it moves.
 *
 * Host only: the code is common, and the iOS run would only repeat it far more slowly.
 */
class SunClockSidesWalkTest {

    private fun clock(lat: Double, lon: Double, date: LocalDate, offset: Int) = SunClock(lat, lon, date, offset, SunModel.EXACT)

    /** The latitude between [south] and [north] where [date]'s evening crossing of [altitude] stops being found. */
    private fun edge(lon: Double, date: LocalDate, offset: Int, altitude: Double, south: Double, north: Double): Double? {
        var a = south
        var b = north
        val foundSouth = clock(a, lon, date, offset).crossing(altitude, morning = false) != null
        val foundNorth = clock(b, lon, date, offset).crossing(altitude, morning = false) != null
        if (foundSouth == foundNorth) return null
        repeat(60) {
            val mid = (a + b) / 2
            if ((clock(mid, lon, date, offset).crossing(altitude, morning = false) != null) == foundSouth) a = mid else b = mid
        }
        return (a + b) / 2
    }

    /**
     * The sun's altitude in degrees at [t] (UTC epoch seconds) over [lat], [lon]: [SolarMath]'s sun at [t] (its declination
     * and equation of time) and the hour angle they give, written here and not read through [SunClock].
     */
    private fun sunAltitude(lat: Double, lon: Double, t: Double): Double {
        val sun = SolarMath.sun(SolarMath.julianDay(t))
        val midnight = floor(t / SECONDS_PER_DAY) * SECONDS_PER_DAY
        // Apparent solar time at [lon], in hours from its noon; the sun turns 15° an hour.
        val hours = (t - midnight) / 3_600.0 + lon / 15.0 + sun.equationOfTimeMinutes / 60.0 - 12.0
        val phi = lat * PI / 180.0
        val delta = sun.declinationDeg * PI / 180.0
        return asin(sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(hours * 15.0 * PI / 180.0)) * 180.0 / PI
    }

    /**
     * The sun's own moment of passing [altitude] the way a crossing does, rising in the [morning] and sinking in the evening,
     * found here: [sunAltitude] read every [SCAN_SECONDS] from [SCAN_MARGIN_SECONDS] before [from] to as far after [to], and
     * the one step over which it passes the altitude that way halved [HALVINGS] times. Null where the scan finds no such
     * step, more than one, or a pass the other way.
     */
    private fun ownMoment(lat: Double, lon: Double, altitude: Double, morning: Boolean, from: Double, to: Double): Double? {
        val first = from - SCAN_MARGIN_SECONDS
        val steps = ceil((to - from + 2 * SCAN_MARGIN_SECONDS) / SCAN_SECONDS).toInt()
        var a = first
        var fa = sunAltitude(lat, lon, a) - altitude
        var moment: Double? = null
        for (i in 1..steps) {
            val b = first + i * SCAN_SECONDS
            val fb = sunAltitude(lat, lon, b) - altitude
            val passes = if (morning) fa < 0 && fb >= 0 else fa > 0 && fb <= 0
            val back = if (morning) fa > 0 && fb <= 0 else fa < 0 && fb >= 0
            if (back) return null
            if (passes) {
                if (moment != null) return null
                var before = a
                var after = b
                repeat(HALVINGS) {
                    val mid = (before + after) / 2
                    if ((sunAltitude(lat, lon, mid) - altitude > 0) == (fa > 0)) before = mid else after = mid
                }
                moment = (before + after) / 2
            }
            a = b
            fa = fb
        }
        return moment
    }

    @Test
    fun `a night's evening crossing and its next morning's are found or not together at the edge of reaching it`() {
        var pairs = 0
        var nightsAtTheEdge = 0
        val failures = mutableListOf<String>()
        for ((lon, offset) in listOf(18.852 to 7200, 12.533 to 7200, -150.0 to -36_000)) {
            for ((altitude, south, north) in listOf(Triple(-0.8333, 58.0, 75.0), Triple(-12.0, 48.0, 66.0), Triple(-18.0, 44.0, 60.0))) {
                var date = LocalDate(2026, 5, 10)
                while (date < LocalDate(2026, 7, 30)) {
                    val next = date.plus(1, DateTimeUnit.DAY)
                    val at = edge(lon, date, offset, altitude, south, north)
                    if (at != null) {
                        nightsAtTheEdge++
                        for (k in -300..300 step 3) {
                            val lat = at + k * 1e-8
                            val evening = clock(lat, lon, date, offset).crossing(altitude, morning = false)
                            val morning = clock(lat, lon, next, offset).crossing(altitude, morning = true)
                            pairs++
                            if ((evening == null) != (morning == null) && failures.size < 20) {
                                failures += "$lat, $lon, $date, $altitude: evening ${evening?.kept}, next morning ${morning?.kept}"
                            }
                        }
                    }
                    date = date.plus(4, DateTimeUnit.DAY)
                }
            }
        }
        assertTrue(failures.isEmpty(), "one side of a night alone:\n${failures.joinToString("\n")}")
        assertTrue(nightsAtTheEdge > 100 && pairs > 20_000, "$nightsAtTheEdge nights at the edge, $pairs pairs")
    }

    @Test
    fun `a start is never before the sun's own moment and an end never after it and a step clear of both culminations does not move`() {
        val altitudes = listOf(-0.8333, -6.0, -12.0, -15.0, -18.0, -19.5, 6.5)
        var crossings = 0
        var read = 0
        var flat = 0
        var moved = 0
        var eveningEarly = 0.0
        var morningLate = 0.0
        // The most a start came before the sun's own moment, and an end after it (negative: never), in seconds.
        var startBefore = Double.NEGATIVE_INFINITY
        var endAfter = Double.NEGATIVE_INFINITY
        val failures = mutableListOf<String>()
        for (lon in listOf(-150.0, 18.852, 120.0)) {
            val offset = (lon / 15.0).roundToInt() * 3600
            for (step in 0..90) {
                val lat = 40.0 + step * 0.5
                var date = LocalDate(2026, 1, 3)
                while (date.year == 2026) {
                    val c = clock(lat, lon, date, offset)
                    val noon = c.transit()
                    for (altitude in altitudes) for (morning in listOf(true, false)) {
                        val crossing = c.crossing(altitude, morning) ?: continue
                        crossings++
                        val at = "$lat $lon $date $altitude ${if (morning) "morning" else "evening"}"
                        // The sun's own moment, by this test's own reading, wherever the sun's altitude is not flat there.
                        val rate = abs(sunAltitude(lat, lon, crossing.exact + 1) - sunAltitude(lat, lon, crossing.exact - 1)) / 2
                        if (rate < FLAT_DEG_PER_SECOND) {
                            flat++
                        } else {
                            val own = ownMoment(lat, lon, altitude, morning, minOf(crossing.kept, crossing.exact), maxOf(crossing.kept, crossing.exact))
                            if (own == null) {
                                if (failures.size < 20) {
                                    failures += "$at: the sun does not pass the altitude that way once within $SCAN_MARGIN_SECONDS s of ${crossing.kept} and ${crossing.exact}"
                                }
                            } else {
                                read++
                                startBefore = maxOf(startBefore, own - crossing.start)
                                endAfter = maxOf(endAfter, crossing.end - own)
                                if (crossing.start < own - TOLERANCE_SECONDS && failures.size < 20) failures += "$at: start ${crossing.start}, before the sun's own $own"
                                if (crossing.end > own + TOLERANCE_SECONDS && failures.size < 20) failures += "$at: end ${crossing.end}, after the sun's own $own"
                            }
                        }
                        // The hour angle the step stopped at, a degree inside the bounds the clock itself reads its own step by.
                        val hourAngle = abs(crossing.kept - noon) / 240.0
                        val clear = hourAngle in 16.0..164.0
                        if (crossing.kept != crossing.exact) {
                            moved++
                            if (clear && failures.size < 20) failures += "$at: a clear step moved, ${crossing.kept} -> ${crossing.exact}"
                            val by = crossing.kept - crossing.exact
                            if (morning) morningLate = maxOf(morningLate, by) else eveningEarly = maxOf(eveningEarly, -by)
                        }
                    }
                    date = date.plus(7, DateTimeUnit.DAY)
                }
            }
        }
        fun ms(seconds: Double) = (seconds * 1e6).roundToInt() / 1e3
        println(
            "SunClockSidesWalkTest: $crossings crossings, $read read against the sun's own moment (a start at most ${ms(startBefore)} ms " +
                "before it, an end at most ${ms(endAfter)} ms after it), $flat left out where the sun's altitude moves under " +
                "$FLAT_DEG_PER_SECOND° a second; $moved with the step off the sun's own moment, evening steps up to $eveningEarly s " +
                "early, morning steps up to $morningLate s late",
        )
        assertEquals(emptyList(), failures)
        assertTrue(crossings > 100_000 && read + flat == crossings && flat < 100, "$crossings crossings, $read read, $flat left out")
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400.0

        /** The scan's step, and how far it reaches beyond the crossing's two moments either way. */
        const val SCAN_SECONDS = 0.05
        const val SCAN_MARGIN_SECONDS = 1.0

        /** Halvings of a scan step: 0.05 s / 2^20, under the quarter microsecond a double resolves at these epoch seconds. */
        const val HALVINGS = 20

        /**
         * How far a start may sit before the sun's own moment, or an end after it: a millisecond. A step clear of both
         * culminations is the clock's moment as the iteration left it, settled to within 1e-6° of the altitude and
         * never halved, and stands a fraction of a millisecond off the sun's own moment (0.64 ms at most on 4.7 million
         * clear steps up to 85° N, in the review of the minors); the clock's bisection resolves to 10 µs and this
         * test's halving to under a microsecond; and where the sun moves at least [FLAT_DEG_PER_SECOND] the altitude's
         * own float noise (about 3e-10°, the Julian day's 40 µs steps) moves a moment by 0.3 ms at most. A time is shown
         * to the minute.
         */
        const val TOLERANCE_SECONDS = 1e-3

        /**
         * Where the sun's altitude moves by less than this at a crossing (a difference of [sunAltitude] a second either side
         * of its moment), at the edge of a night or of a noon, the crossing is left out of the reading and counted: there the
         * altitude is flat, its float noise (about 3e-10°) can move the moment by more than 0.3 ms or split it into several,
         * and no one moment is the sun's to a millisecond.
         */
        const val FLAT_DEG_PER_SECOND = 1e-6
    }
}
