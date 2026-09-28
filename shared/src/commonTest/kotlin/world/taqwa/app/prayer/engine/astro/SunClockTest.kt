package world.taqwa.app.prayer.engine.astro

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SunClockTest {
    private fun local(date: LocalDate, hms: String, offsetHours: Int): Double {
        val (h, m, s) = hms.split(":").map { it.toInt() }
        val ldt = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, h, m, s)
        return ldt.toInstant(TimeZone.UTC).epochSeconds.toDouble() - offsetHours * 3600
    }

    private fun near(expected: Double, actual: Double?, label: String) {
        assertTrue(actual != null && abs(actual - expected) <= 3.0, "$label: expected $expected, got $actual")
    }

    private fun within(expected: Double, actual: Double?, toleranceSeconds: Double, label: String) {
        assertTrue(
            actual != null && abs(actual - expected) < toleranceSeconds,
            "$label: expected within $toleranceSeconds s of $expected, got $actual",
        )
    }

    private data class Ref(val lat: Double, val lon: Double, val tz: Int, val date: LocalDate, val transit: String,
        val dawn18: String, val sunrise: String, val asr1: String, val asr2: String, val sunset: String, val dusk17: String)

    private val refs = listOf(
        Ref(51.5074, -0.1278, 1, LocalDate(2026, 10, 1), "12:50:11", "5:09:04", "7:01:03", "15:56:13", "16:43:21", "18:38:22", "20:23:14"),
        Ref(21.426666, 39.831666, 3, LocalDate(2026, 9, 20), "12:14:10", "4:54:48", "6:08:55", "15:39:07", "16:37:07", "18:19:06", "19:28:47"),
        Ref(3.139, 101.687, 8, LocalDate(2026, 2, 25), "13:26:20", "6:15:26", "7:25:01", "16:43:15", "17:45:28", "19:27:41", "20:33:11"),
        Ref(43.6532, -79.3832, -4, LocalDate(2026, 10, 1), "13:07:08", "5:39:33", "7:15:11", "16:22:26", "17:10:53", "18:58:22", "20:28:07"),
        Ref(32.1167, 20.0667, 2, LocalDate(2026, 9, 27), "12:30:45", "5:09:33", "6:31:00", "15:55:44", "16:47:53", "18:30:02", "19:46:34"),
    )

    @Test
    fun `exact model matches the NOAA reference events`() {
        for (r in refs) {
            val c = SunClock(r.lat, r.lon, r.date, r.tz * 3600, SunModel.EXACT)
            near(local(r.date, r.transit, r.tz), c.transit(), "transit ${r.date}")
            near(local(r.date, r.dawn18, r.tz), c.altitudeTime(-18.0, morning = true), "dawn ${r.date}")
            near(local(r.date, r.sunrise, r.tz), c.altitudeTime(-0.8333, morning = true), "sunrise ${r.date}")
            near(local(r.date, r.asr1, r.tz), c.asr(1.0, AsrModel.EXACT_MOMENT), "asr1 ${r.date}")
            near(local(r.date, r.asr2, r.tz), c.asr(2.0, AsrModel.EXACT_MOMENT), "asr2 ${r.date}")
            near(local(r.date, r.sunset, r.tz), c.altitudeTime(-0.8333, morning = false), "sunset ${r.date}")
            near(local(r.date, r.dusk17, r.tz), c.altitudeTime(-17.0, morning = false), "dusk ${r.date}")
        }
    }

    @Test
    fun `an altitude the sun never reaches returns null`() {
        val c = SunClock(51.5074, -0.1278, LocalDate(2026, 6, 21), 3600, SunModel.EXACT)
        assertNull(c.altitudeTime(-18.0, morning = false))
    }

    @Test
    fun `daily model stays within two minutes of exact at mid latitudes`() {
        val d = LocalDate(2026, 9, 26)
        val exact = SunClock(41.012, 28.974, d, 3 * 3600, SunModel.EXACT)
        val daily = SunClock(41.012, 28.974, d, 3 * 3600, SunModel.DAILY_0H_UT)
        val a = exact.altitudeTime(-0.8333, morning = false)!!
        val b = daily.altitudeTime(-0.8333, morning = false)!!
        assertTrue(abs(a - b) < 120.0, "sunset exact $a daily $b")
    }

    @Test
    fun `transit stays on the civil date for zones beyond twelve hours`() {
        val d = LocalDate(2026, 9, 26)
        val c = SunClock(1.87, -157.4, d, 14 * 3600, SunModel.EXACT)
        val t = c.transit()
        val localDayStart = LocalDateTime(2026, 9, 26, 0, 0).toInstant(TimeZone.UTC).epochSeconds - 14 * 3600
        assertTrue(t >= localDayStart && t < localDayStart + 86_400, "transit $t not on 26 Sep local")
    }

    @Test
    fun `classic noon matches exact within a minute near the equator`() {
        // Kuala Lumpur, close to the equator: the declination barely differs between the transit
        // moment (what CLASSIC_NOON freezes it at) and the actual dawn/sunset moments.
        val d = LocalDate(2026, 2, 25)
        val exact = SunClock(3.139, 101.687, d, 8 * 3600, SunModel.EXACT)
        val classic = SunClock(3.139, 101.687, d, 8 * 3600, SunModel.CLASSIC_NOON)
        within(exact.altitudeTime(-18.0, morning = true)!!, classic.altitudeTime(-18.0, morning = true), 60.0, "classic dawn")
        within(exact.altitudeTime(-0.8333, morning = false)!!, classic.altitudeTime(-0.8333, morning = false), 60.0, "classic sunset")
    }

    @Test
    fun `asr models agree with the exact moment within ninety seconds at Makkah`() {
        val d = LocalDate(2026, 9, 20)
        val c = SunClock(21.426666, 39.831666, d, 3 * 3600, SunModel.EXACT)
        for (shadowFactor in listOf(1.0, 2.0)) {
            val reference = c.asr(shadowFactor, AsrModel.EXACT_MOMENT)!!
            within(reference, c.asr(shadowFactor, AsrModel.NOON_SHADOW), 90.0, "noon-shadow asr $shadowFactor")
            within(reference, c.asr(shadowFactor, AsrModel.UTC12_ONE_SHOT), 90.0, "utc12 asr $shadowFactor")
            within(reference, c.asr(shadowFactor, AsrModel.DAILY_0H_UT), 90.0, "daily asr $shadowFactor")
        }
    }

    @Test
    fun `a positive declination bias moves Makkah dawn earlier by more than thirty seconds`() {
        // Hour angle: cosH = (sin(altitude) - sin(lat)*sin(declination)) / (cos(lat)*cos(declination)).
        // At Makkah in September the declination is still a few degrees north of the equator (a few
        // days before the equinox), so both sin(lat) and sin(declination) are positive: raising the
        // declination makes sin(lat)*sin(declination) bigger, which makes the numerator (already
        // negative, since sin(-18 degrees) is well below zero) more negative while the denominator
        // barely moves. So cosH decreases (more negative), H = acos(cosH) increases, and a morning
        // event's time (noon - H*240) moves earlier. A +0.3 degree bias should move dawn earlier.
        val d = LocalDate(2026, 9, 20)
        val c = SunClock(21.426666, 39.831666, d, 3 * 3600, SunModel.EXACT)
        val unbiased = c.altitudeTime(-18.0, morning = true)!!
        val biased = c.altitudeTime(-18.0, morning = true, declinationBiasDeg = 0.3)!!
        assertTrue(biased < unbiased, "expected the biased dawn ($biased) earlier than unbiased ($unbiased)")
        assertTrue(unbiased - biased > 30.0, "expected more than 30s earlier, got ${unbiased - biased}")
    }

    @Test
    fun `kazan's 18 degree dawn of 5 may 2026 is found though the sun only just reaches it`() {
        // The sun sinks to 18.05° at 23:39:55 on 4 May (Moscow time); at noon of the 5th its declination
        // is 0.14° higher, which put the depression out of reach for the first guess. DUM RT prints 23:54.
        val c = SunClock(55.78874, 49.12214, LocalDate(2026, 5, 5), 3 * 3600, SunModel.EXACT)
        within(local(LocalDate(2026, 5, 4), "23:53:10", 3), c.altitudeTime(-18.0, morning = true), 10.0, "Kazan 5 May dawn")
        // The next night it is out of reach (17.77°).
        assertNull(SunClock(55.78874, 49.12214, LocalDate(2026, 5, 6), 3 * 3600, SunModel.EXACT).altitudeTime(-18.0, morning = true))
    }
}
