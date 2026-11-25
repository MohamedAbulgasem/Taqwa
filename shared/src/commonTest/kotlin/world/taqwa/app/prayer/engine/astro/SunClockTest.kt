package world.taqwa.app.prayer.engine.astro

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun `a crossing the sun does not make is not found under ruling R321`() {
        // 69.895, 18.852 (Tromsø's reach, IRN) on 16 May 2026: the sun sinks to -0.83324° at 22:40:32.7 UTC, above the
        // -0.8333° of the real horizon, and is up all night. The iteration from noon crept toward the lower culmination
        // and its sixth step, 22:40:58, read -0.83321°: no sunset, though one was reported, with no sunrise after it.
        val oslo = 2 * 3600
        assertNull(SunClock(69.895, 18.852, LocalDate(2026, 5, 16), oslo, SunModel.EXACT).altitudeTime(-0.8333, morning = false))
        assertNull(SunClock(69.895, 18.852, LocalDate(2026, 5, 17), oslo, SunModel.EXACT).altitudeTime(-0.8333, morning = true))
        // The same at other latitudes, days and depressions (the scan of ruling R321: 40-80° N, every day of 2026, at
        // 18.852° E, 25° E and 150° W): evening and morning, the sun's horizon and a twilight's. Each was a crossing before
        // R321 (a sunset at 22:41:26.7 UTC at 67.5875°, a sunrise at 22:51:40.8 UTC at 69.9045°, a 14° dusk at 22:42:42.3
        // and an 8.5° dawn at 22:51:01.1), where the sun at its lowest stays above the altitude by 1.1e-6°, 3.7e-5°,
        // 9.4e-5° and 6.1e-5°.
        val cet = 3600
        assertNull(SunClock(67.5875, 18.852, LocalDate(2026, 5, 28), cet, SunModel.EXACT).altitudeTime(-0.8333, morning = false))
        assertNull(SunClock(69.9045, 18.852, LocalDate(2026, 7, 27), cet, SunModel.EXACT).altitudeTime(-0.8333, morning = true))
        assertNull(SunClock(63.22, 18.852, LocalDate(2026, 4, 23), cet, SunModel.EXACT).altitudeTime(-14.0, morning = false))
        assertNull(SunClock(63.68, 18.852, LocalDate(2026, 8, 2), cet, SunModel.EXACT).altitudeTime(-8.5, morning = true))
        // A crossing the sun does make, however close to its lowest, is still found where it was: the sun sinks to
        // -0.833318° at 66.885° on 2 June, and to -0.833309° at 67.608° before dawn on 15 July.
        within(1.780440128E9, SunClock(66.885, 18.852, LocalDate(2026, 6, 2), cet, SunModel.EXACT).altitudeTime(-0.8333, morning = false), 1.0, "66.885 2 June sunset")
        within(1.784069468E9, SunClock(67.608, 18.852, LocalDate(2026, 7, 15), cet, SunModel.EXACT).altitudeTime(-0.8333, morning = true), 1.0, "67.608 15 July sunrise")
    }

    @Test
    fun `the evening's and the next morning's crossing of a night are found or not together settled or not`() {
        // The final review of 20 Nov 2026: R321 asked whether the sun reaches the altitude only where the iteration had not
        // settled to within 1e-6° of it. Within about a week of the solstice the iteration settles onto the sun's lowest
        // point, so on a night the sun misses the altitude by less than that one side was still found and the other not.
        // At 65.802, 12.533 on the app's own grid (Helgeland, Norway's Cautious ground) the sun stays 6.3e-8° above the
        // real horizon in the night of 25 to 26 June 2026: no sunset, yet a sunrise at 23:12:45 UTC was reported.
        val oslo = 2 * 3600
        fun night(lat: Double, lon: Double, date: LocalDate, altitude: Double): Pair<Double?, Double?> =
            SunClock(lat, lon, date, oslo, SunModel.EXACT).altitudeTime(altitude, morning = false) to
                SunClock(lat, lon, LocalDate.fromEpochDays(date.toEpochDays() + 1), oslo, SunModel.EXACT).altitudeTime(altitude, morning = true)
        assertEquals(null to null, night(65.802, 12.533, LocalDate(2026, 6, 25), -0.8333))
        // The same band at 18.852° E: the horizon on 16 June (a sunset at 22:45:22 UTC with no sunrise after it; the sun
        // 8.4e-7° above at its lowest), and 18° below it on 26 June (a dawn at 22:47:38 UTC with no dusk before it; 6.0e-7°).
        assertEquals(null to null, night(65.795243, 18.852, LocalDate(2026, 6, 16), -0.8333))
        assertEquals(null to null, night(48.66981, 18.852, LocalDate(2026, 6, 26), -18.0))
        // Where the sun dips below by as little, both are found: 1.6e-7° below the horizon at 65.795242 on 16 June.
        val (set, rise) = night(65.795242, 18.852, LocalDate(2026, 6, 16), -0.8333)
        assertTrue(set != null && rise != null && rise > set, "65.795242 16 June: sunset $set, sunrise $rise")
    }

    @Test
    fun `a start reads the sun's own moment where the step stops before it and an end where it stops after it`() {
        // The final review of 20 Nov 2026: near a culmination the step the iteration stops on is kept beside the sun's own
        // moment, and a time takes the side that is never wrong. At 69.671, 18.900 on a night of May 2026 the evening's step is
        // 22:39:54.0 UTC and the sun sets at 22:40:00.4: a start (Maghrib's floor) reads the sunset, an end the step.
        val evening = assertNotNull(SunClock(69.671, 18.900, LocalDate(2026, 5, 17), 2 * 3600, SunModel.EXACT).crossing(-0.8333, morning = false))
        within(1.779057594020E9, evening.kept, 0.01, "17 May, the step")
        within(1.779057600440E9, evening.exact, 0.01, "17 May, the sun's own sunset")
        assertEquals(evening.exact, evening.start)
        assertEquals(evening.kept, evening.end)
        // The mirror at 67.608, 18.852 on 15 July 2026: the morning's step is 22:51:08.1 UTC (14 July), the sunrise 22:51:02.6.
        val morning = assertNotNull(SunClock(67.608, 18.852, LocalDate(2026, 7, 15), 3600, SunModel.EXACT).crossing(-0.8333, morning = true))
        within(1.784069468117E9, morning.kept, 0.01, "15 July, the step")
        within(1.784069462619E9, morning.exact, 0.01, "15 July, the sun's own sunrise")
        assertEquals(morning.exact, morning.end)
        assertEquals(morning.kept, morning.start)
        // The grazing sunset of 2 June at 66.885: the step 22:42:08.3, the sun's own 22:42:10.4.
        val june = assertNotNull(SunClock(66.885, 18.852, LocalDate(2026, 6, 2), 3600, SunModel.EXACT).crossing(-0.8333, morning = false))
        within(1.780440130388E9, june.start, 0.01, "2 June, the sunset a start reads")
        // A step clear of both culminations is the sun's own moment: nothing read from it moves.
        val london = assertNotNull(SunClock(51.5074, -0.1278, LocalDate(2026, 10, 1), 3600, SunModel.EXACT).crossing(-0.8333, morning = false))
        assertEquals(london.kept, london.exact)
        assertEquals(london.start, london.end)
    }
}
