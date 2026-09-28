package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.ClockRule
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class HighLatitudeTest {
    private val noRamadan = RamadanCalendar { false }

    private val london = GeoPoint(51.5074, -0.1278)
    private val londonZone = TimeZone.of("Europe/London")
    private val moscow = TimeZone.of("Europe/Moscow")

    private val plain = TimetableMethod(id = "test.plain", fajrAngle = 18.0, isha = IshaRule.Angle(17.0))

    private fun compute(
        method: TimetableMethod,
        point: GeoPoint,
        date: LocalDate,
        zone: TimeZone,
        school: AsrSchool = AsrSchool.STANDARD,
    ) = DayComputer.compute(method, point, date, zone, school, noRamadan)

    private fun LocalDate.next() = plus(1, DateTimeUnit.DAY)
    private fun LocalDate.previous() = minus(1, DateTimeUnit.DAY)

    private fun clock(point: GeoPoint, date: LocalDate, zone: TimeZone): SunClock {
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        return SunClock(point.lat, point.lon, date, offset, SunModel.EXACT)
    }

    private fun sunrise(point: GeoPoint, date: LocalDate, zone: TimeZone) =
        clock(point, date, zone).altitudeTime(-0.8333, morning = true)!!

    private fun sunset(point: GeoPoint, date: LocalDate, zone: TimeZone) =
        clock(point, date, zone).altitudeTime(-0.8333, morning = false)!!

    /** Isha's place in tonight's night (sunset to tomorrow's sunrise), 0 at sunset and 1 at sunrise. */
    private fun ishaFraction(isha: Double, point: GeoPoint, date: LocalDate, zone: TimeZone): Double {
        val dusk = sunset(point, date, zone)
        return (isha - dusk) / (sunrise(point, date.next(), zone) - dusk)
    }

    /** Fajr's distance before sunrise as a part of last night (yesterday's sunset to today's sunrise). */
    private fun fajrFraction(fajr: Double, point: GeoPoint, date: LocalDate, zone: TimeZone): Double {
        val dawn = sunrise(point, date, zone)
        return (dawn - fajr) / (dawn - sunset(point, date.previous(), zone))
    }

    private fun realIsha(point: GeoPoint, date: LocalDate, zone: TimeZone, degrees: Double) =
        clock(point, date, zone).altitudeTime(-degrees, morning = false)

    private fun realFajr(point: GeoPoint, date: LocalDate, zone: TimeZone, degrees: Double) =
        clock(point, date, zone).altitudeTime(-degrees, morning = true)

    /** The MWL 1986 fractions at 45° in the point's own hemisphere. */
    private fun fractionsAt45(point: GeoPoint, date: LocalDate, zone: TimeZone): Pair<Double, Double> {
        val at45 = GeoPoint(if (point.lat >= 0) 45.0 else -45.0, point.lon)
        val fajr = fajrFraction(realFajr(at45, date, zone, 18.0)!!, at45, date, zone)
        val isha = ishaFraction(realIsha(at45, date, zone, 17.0)!!, at45, date, zone)
        return fajr to isha
    }

    private fun startOf(epochSeconds: Double) = Instant.fromEpochSeconds(ceil(epochSeconds / 60.0).toLong() * 60)

    private fun endOf(epochSeconds: Double) = Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    private fun local(date: LocalDate, hhmm: String, zone: TimeZone): Instant {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return date.atTime(h, m).toInstant(zone)
    }

    private fun Instant.clock(zone: TimeZone): String = toLocalDateTime(zone).time.toString()

    private fun PrayerDay.inOrder(): Boolean {
        val order = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
        return endOfEating <= fajr && order.zipWithNext().all { (a, b) -> a < b } && maghrib >= sunset
    }

    private fun PrayerDay.everyInstant() =
        listOfNotNull(fajr, sunrise, dhuhr, asr, asrOther, maghrib, isha, sunset, endOfEating, imsak)

    // Rule 1: real times whenever the sign occurs, at any latitude.

    @Test
    fun `the real fajr and isha are kept wherever the sign occurs`() {
        val cases = listOf(
            Triple(london, londonZone, LocalDate(2026, 1, 15)),
            Triple(GeoPoint(69.6492, 18.9553), TimeZone.of("Europe/Oslo"), LocalDate(2026, 2, 15)),
            Triple(GeoPoint(64.1466, -21.9426), TimeZone.of("Atlantic/Reykjavik"), LocalDate(2026, 3, 20)),
        )
        for ((point, zone, date) in cases) {
            val day = compute(plain, point, date, zone)
            assertEquals(emptySet(), day.setByRule, "$point $date")
            assertFalse(day.polar, "$point $date")
            assertEquals(startOf(realFajr(point, date, zone, 18.0)!!), day.fajr, "$point $date fajr")
            assertEquals(startOf(realIsha(point, date, zone, 17.0)!!), day.isha, "$point $date isha")
        }
    }

    // Rule 2: the MWL Fiqh Council's proportion from 45°, and the daily rate limit (ruling R17).

    @Test
    fun `london at midsummer takes fajr and isha from the proportion at 45 degrees`() {
        val date = LocalDate(2026, 6, 21)
        val day = compute(plain, london, date, londonZone)
        assertEquals(setOf(Prayer.FAJR, Prayer.ISHA), day.setByRule)
        assertFalse(day.polar)
        assertNull(realIsha(london, date, londonZone, 17.0), "no 17° dusk in London at midsummer")
        assertTrue(day.isha > day.maghrib, "isha ${day.isha.clock(londonZone)} maghrib ${day.maghrib.clock(londonZone)}")
        assertTrue(day.fajr < day.sunrise, "fajr ${day.fajr.clock(londonZone)} sunrise ${day.sunrise.clock(londonZone)}")
        assertTrue(day.inOrder())

        // The rate limit let go of the last real signs weeks ago: the plain 45° proportion.
        val (fFajr, fIsha) = fractionsAt45(london, date, londonZone)
        val dusk = sunset(london, date, londonZone)
        val isha = dusk + fIsha * (sunrise(london, date.next(), londonZone) - dusk)
        val dawn = sunrise(london, date, londonZone)
        val fajr = dawn - fFajr * (dawn - sunset(london, date.previous(), londonZone))
        assertEquals(startOf(isha), day.isha)
        assertEquals(startOf(fajr), day.fajr)
    }

    @Test
    fun `the proportion comes from 45 degrees south in the southern hemisphere`() {
        val puntaArenas = GeoPoint(-53.1638, -70.9171)
        val zone = TimeZone.of("America/Punta_Arenas")
        val date = LocalDate(2026, 12, 21)
        val day = compute(plain, puntaArenas, date, zone)
        assertEquals(setOf(Prayer.FAJR, Prayer.ISHA), day.setByRule)
        val (fFajr, fIsha) = fractionsAt45(puntaArenas, date, zone)
        val dusk = sunset(puntaArenas, date, zone)
        val dawn = sunrise(puntaArenas, date, zone)
        assertEquals(startOf(dusk + fIsha * (sunrise(puntaArenas, date.next(), zone) - dusk)), day.isha)
        assertEquals(startOf(dawn - fFajr * (dawn - sunset(puntaArenas, date.previous(), zone))), day.fajr)
    }

    @Test
    fun `london summer edges never move isha 20 minutes earlier or the end of eating 20 minutes later overnight`() {
        // Walk London from May to mid-August, comparing each day with the day before in UTC seconds
        // a whole day apart (the clock change moves nothing).
        var previous: PrayerDay? = null
        var ishaHeld = 0
        var eatingHeld = 0
        var date = LocalDate(2026, 5, 1)
        while (date <= LocalDate(2026, 8, 15)) {
            val day = compute(plain, london, date, londonZone)
            previous?.let { before ->
                assertTrue(
                    day.isha >= before.isha + 1.days - 20.minutes,
                    "$date isha ${day.isha.clock(londonZone)} after ${before.isha.clock(londonZone)}",
                )
                assertTrue(
                    day.endOfEating <= before.endOfEating + 1.days + 20.minutes,
                    "$date end of eating ${day.endOfEating.clock(londonZone)} after ${before.endOfEating.clock(londonZone)}",
                )
            }
            val (fFajr45, fIsha45) = fractionsAt45(london, date, londonZone)
            val dusk = sunset(london, date, londonZone)
            val dawn = sunrise(london, date, londonZone)

            // Isha: never before the real sign; where it is missing, never before the 45° estimate.
            val realDusk = realIsha(london, date, londonZone, 17.0)
            if (realDusk != null) {
                assertTrue(day.isha >= startOf(realDusk), "$date isha before the real dusk")
            } else {
                assertTrue(Prayer.ISHA in day.setByRule, "$date isha")
                val estimate = startOf(dusk + fIsha45 * (sunrise(london, date.next(), londonZone) - dusk))
                assertTrue(day.isha >= estimate, "$date isha before the estimate")
                if (day.isha > estimate) ishaHeld++
            }

            // The Fajr start is never limited; the end of eating never after the real dawn or the estimate.
            val realDawn = realFajr(london, date, londonZone, 18.0)
            if (realDawn != null) {
                assertEquals(startOf(realDawn), day.fajr, "$date fajr")
                assertTrue(day.endOfEating <= endOf(realDawn), "$date end of eating after the real dawn")
            } else {
                val estimate = dawn - fFajr45 * (dawn - sunset(london, date.previous(), londonZone))
                assertEquals(startOf(estimate), day.fajr, "$date: the Fajr start is the plain estimate")
                assertTrue(day.endOfEating <= endOf(estimate), "$date end of eating after the estimate")
                if (day.endOfEating < endOf(estimate)) eatingHeld++
            }
            previous = day
            date = date.next()
        }
        assertTrue(ishaHeld >= 3, "the limit held Isha on $ishaHeld days")
        assertTrue(eatingHeld >= 3, "the limit held the end of eating on $eatingHeld days")
    }

    @Test
    fun `the limit steps london's first estimated isha down from the last real one`() {
        // 27 May has the last real 17° dusk (00:44 on the 28th); the Isha shown then steps down by 20
        // minutes a night to the 45° estimate, which it meets on 1 June.
        val june = LocalDate(2026, 6, 1)
        val shown = (27..31).map { compute(plain, london, LocalDate(2026, 5, it), londonZone).isha.clock(londonZone) } +
            compute(plain, london, june, londonZone).isha.clock(londonZone)
        assertEquals(listOf("00:44", "00:24", "00:04", "23:44", "23:24", "23:07"), shown)
        assertEquals(null, realIsha(london, LocalDate(2026, 5, 28), londonZone, 17.0))
        val fIsha45 = fractionsAt45(london, june, londonZone).second
        val dusk = sunset(london, june, londonZone)
        val estimate = startOf(dusk + fIsha45 * (sunrise(london, june.next(), londonZone) - dusk))
        assertEquals("23:07", estimate.clock(londonZone))
    }

    @Test
    fun `the limit holds on the days after a missing sign returns in both hemispheres`() {
        // Ruling R22: once the dawn and dusk return, the days that follow are still limited against
        // the estimated ones before them (the review found the end of eating 26 minutes later
        // overnight at 55.79° N, 32 at 61.5° N and 34 with ISNA at 64.5° N).
        val isna = TimetableMethod(id = "test.isna", fajrAngle = 15.0, isha = IshaRule.Angle(15.0))
        val cases = listOf(
            Triple(plain, GeoPoint(55.79, 20.0), TimeZone.of("Europe/Kaliningrad")) to (LocalDate(2026, 7, 20) to LocalDate(2026, 8, 25)),
            Triple(plain, GeoPoint(61.5, 20.0), TimeZone.of("Europe/Stockholm")) to (LocalDate(2026, 8, 10) to LocalDate(2026, 9, 10)),
            Triple(isna, GeoPoint(64.5, 20.0), TimeZone.of("Europe/Stockholm")) to (LocalDate(2026, 7, 20) to LocalDate(2026, 9, 10)),
            Triple(plain, GeoPoint(-55.79, -68.3), TimeZone.of("America/Argentina/Ushuaia")) to (LocalDate(2026, 1, 10) to LocalDate(2026, 2, 28)),
        )
        for ((setting, dates) in cases) {
            val (method, point, zone) = setting
            var before = compute(method, point, dates.first, zone)
            var date = dates.first.next()
            while (date <= dates.second) {
                val day = compute(method, point, date, zone)
                assertTrue(
                    day.endOfEating <= before.endOfEating + 1.days + 20.minutes,
                    "${method.id} $point $date end of eating ${day.endOfEating.clock(zone)} after ${before.endOfEating.clock(zone)}",
                )
                assertTrue(
                    day.isha >= before.isha + 1.days - 20.minutes,
                    "${method.id} $point $date isha ${day.isha.clock(zone)} after ${before.isha.clock(zone)}",
                )
                before = day
                date = date.next()
            }
        }
    }

    @Test
    fun `borrowed polar times keep the daily limit when the substitute latitude steps`() {
        // Ruling R24: the limit binds the shown Isha and end of eating whatever their source. The
        // review saw Isha 23 minutes earlier overnight at Murmansk (13 to 14 June), Tromsø (18 to
        // 19 May) and Longyearbyen (26 to 27 April) as the substitute latitude stepped.
        val cities = listOf(
            Triple(GeoPoint(68.97, 33.07), moscow, "Murmansk"),
            Triple(GeoPoint(69.6492, 18.9553), oslo, "Tromsø"),
            Triple(GeoPoint(78.2232, 15.6267), oslo, "Longyearbyen"),
        )
        for ((point, zone, name) in cities) {
            var before = compute(plain, point, LocalDate(2026, 4, 1), zone)
            var date = LocalDate(2026, 4, 2)
            while (date <= LocalDate(2026, 9, 15)) {
                val day = compute(plain, point, date, zone)
                assertTrue(
                    day.isha >= before.isha + 1.days - 20.minutes,
                    "$name $date isha ${day.isha.clock(zone)} after ${before.isha.clock(zone)}",
                )
                assertTrue(
                    day.endOfEating <= before.endOfEating + 1.days + 20.minutes,
                    "$name $date end of eating ${day.endOfEating.clock(zone)} after ${before.endOfEating.clock(zone)}",
                )
                before = day
                date = date.next()
            }
        }
    }

    @Test
    fun `the limit never holds isha into the next night's end at longyearbyen`() {
        // Ruling R62: Isha shown = max(the rule's own Isha, min(the limited Isha, the next day's end
        // of eating − 1 min)). With Isha 90 minutes after Maghrib the rule's own Isha is exactly
        // Maghrib + 90, so every day shows which of the three it is.
        val longyearbyen = GeoPoint(78.2232, 15.6267)
        val after90 = TimetableMethod(id = "test.after-90", fajrAngle = 18.5, isha = IshaRule.AfterMaghrib(90))
        var capped = 0
        var before = compute(after90, longyearbyen, LocalDate(2026, 3, 31), oslo)
        var day = compute(after90, longyearbyen, LocalDate(2026, 4, 1), oslo)
        while (day.date < LocalDate(2026, 10, 1)) {
            val next = compute(after90, longyearbyen, day.date.next(), oslo)
            val own = day.maghrib + 90.minutes
            val label = "${day.date}: isha ${day.isha.clock(oslo)}, own ${own.clock(oslo)}, next end ${next.endOfEating.clock(oslo)}"
            assertTrue(day.isha >= own, "$label: before its own rule")
            // Held later than its own rule by the limit: never into the next night's end.
            if (day.isha > own) assertTrue(day.isha < next.endOfEating, "$label: the limit holds it into the next night")
            if (day.isha < before.isha + 1.days - 20.minutes) {
                // Where the limit would have held it later, the next night's end stopped it.
                assertEquals(maxOf(own, next.endOfEating - 1.minutes), day.isha, label)
                capped++
            }
            before = day
            day = next
        }
        assertTrue(capped > 0, "the next night's end binds on some day")
    }

    @Test
    fun `an end of eating at a dawn angle that is never reached is estimated and stays before fajr`() {
        val method = plain.copy(endOfEating = EndOfEating.DawnAngle(19.5))
        val day = compute(method, london, LocalDate(2026, 6, 21), londonZone)
        assertTrue(day.endOfEating <= day.fajr)
        assertTrue(day.endOfEating < day.sunrise)
    }

    // Rule 5: polar day or night, only where the day itself has no sunrise or no sunset (ruling
    // R18), and bound by the place's own real signs (rule 1 beats rule 5, ruling R19).

    private val murmansk = GeoPoint(68.97, 33.07)
    private val tromso = GeoPoint(69.6492, 18.9553)
    private val oslo = TimeZone.of("Europe/Oslo")

    private fun hasSunriseAndSunset(point: GeoPoint, date: LocalDate, zone: TimeZone): Boolean {
        val c = clock(point, date, zone)
        return c.altitudeTime(-0.8333, morning = true) != null && c.altitudeTime(-0.8333, morning = false) != null
    }

    private fun PrayerDay.row(zone: TimeZone) = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha).map { it.clock(zone) }

    @Test
    fun `murmansk at midsummer is computed at the nearest latitude where the sun rises and sets`() {
        val date = LocalDate(2026, 6, 21)
        val day = compute(plain, murmansk, date, moscow)
        assertTrue(day.polar)
        // Ruling R21: only the borrowed times are set by rule. Dhuhr (the same transit on the same
        // meridian) and Asr (Murmansk's own, later) are the place's own real signs.
        val borrowed = setOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.MAGHRIB, Prayer.ISHA)
        assertEquals(borrowed, day.setByRule)
        assertTrue(day.inOrder(), "out of order: $day")
        assertTrue(Invariants.holds(day), "$day")
        day.everyInstant().forEach { assertEquals(0L, it.epochSeconds % 60) }

        // 65.97° still has the midnight sun; 65.47° (seven half-degree steps south) is the nearest
        // latitude on Murmansk's meridian where the sun sets.
        assertNull(clock(GeoPoint(68.97 - 3.0, 33.07), date, moscow).altitudeTime(-0.8333, morning = false))
        val nearest = compute(plain, GeoPoint(68.97 - 3.5, 33.07), date, moscow)
        assertFalse(nearest.polar)
        assertEquals(listOf("01:04", "01:24", "12:50", "17:47", "00:15", "00:34"), nearest.row(moscow))

        // Murmansk's own sun is up all day, so the place gives its own Asr (ruling R23), and Dhuhr
        // ends there; the rest is borrowed from 65.47°.
        val own = clock(murmansk, date, moscow)
        val ownAsr = startOf(own.asr(1.0, AsrModel.EXACT_MOMENT)!!)
        val ownHanafi = startOf(own.asr(2.0, AsrModel.EXACT_MOMENT)!!)
        assertTrue(ownAsr > nearest.asr)
        assertEquals(listOf("01:04", "01:24", "12:50", "17:59", "00:15", "00:34"), day.row(moscow))
        assertEquals(
            nearest.copy(
                asr = ownAsr, asrOther = ownHanafi,
                ends = nearest.ends + (Prayer.DHUHR to ownAsr),
                polar = true, setByRule = borrowed,
            ),
            day,
        )
    }

    @Test
    fun `a polar night is computed at the nearest latitude where the sun rises and keeps its own dawn`() {
        val date = LocalDate(2026, 12, 21)
        assertNull(clock(tromso, date, oslo).altitudeTime(-0.8333, morning = true))
        val day = compute(plain, tromso, date, oslo, school = AsrSchool.HANAFI)
        assertTrue(day.polar)
        // Ruling R21: Tromsø's own dawn wins, so Fajr is its real sign and not set by rule; Dhuhr is
        // the same transit on the same meridian. Sunrise, Asr (no shadow in the polar night),
        // Maghrib and Isha (its own 17° dusk is earlier) are borrowed from the substitute.
        val borrowed = setOf(Prayer.SUNRISE, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        assertEquals(borrowed, day.setByRule)
        assertFalse(Prayer.FAJR in day.setByRule)
        assertTrue(day.inOrder(), "out of order: $day")
        assertTrue(Invariants.holds(day), "$day")

        // 67.65° has no sunrise yet; 67.15° (five steps south) is the nearest latitude that has one.
        assertNull(clock(GeoPoint(69.6492 - 2.0, 18.9553), date, oslo).altitudeTime(-0.8333, morning = true))
        val substitute = compute(plain, GeoPoint(69.6492 - 2.5, 18.9553), date, oslo, school = AsrSchool.HANAFI)
        assertFalse(substitute.polar)
        assertEquals(listOf("06:20", "11:06", "11:43", "11:51", "12:18", "16:55"), substitute.row(oslo))

        // Tromsø's own 18° dawn is real and 9 minutes later than the substitute's: the Fajr shown
        // waits for it. Its own red twilight ends earlier, so Maghrib ends there.
        val ownDawn = startOf(realFajr(tromso, date, oslo, 18.0)!!)
        assertEquals("06:29", ownDawn.clock(oslo))
        assertEquals(ownDawn, day.fajr)
        assertTrue(day.fajr >= ownDawn && day.isha >= startOf(realIsha(tromso, date, oslo, 17.0)!!))
        val ownRedTwilightEnd = endOf(realIsha(tromso, date, oslo, 17.0)!!)
        assertEquals(
            substitute.copy(
                fajr = ownDawn,
                ends = substitute.ends + (Prayer.MAGHRIB to ownRedTwilightEnd),
                polar = true, setByRule = borrowed,
            ),
            day,
        )
        assertEquals(listOf("06:29", "11:06", "11:43", "11:51", "12:18", "16:55"), day.row(oslo))
    }

    @Test
    fun `a polar day with one of its own edges computes at the place what the place can give`() {
        // Ruling R23: on the first midnight-sun day the place has a sunrise but no sunset, on the
        // last a sunset but no sunrise. The review found Fajr after the place's own sunrise and Isha
        // before its own Maghrib on these days; every day around them must keep its order unrepaired.
        val stockholm = TimeZone.of("Europe/Stockholm")
        val edges = listOf(
            Triple(GeoPoint(66.0, 20.0), stockholm, LocalDate(2026, 6, 12)),
            Triple(GeoPoint(66.0, 20.0), stockholm, LocalDate(2026, 6, 30)),
            Triple(GeoPoint(67.4981, 64.0341), moscow, LocalDate(2026, 5, 29)), // Vorkuta
            Triple(GeoPoint(67.4981, 64.0341), moscow, LocalDate(2026, 7, 14)),
            Triple(GeoPoint(69.3535, 88.2027), TimeZone.of("Asia/Krasnoyarsk"), LocalDate(2026, 5, 19)), // Norilsk
            Triple(GeoPoint(69.9689, 23.2716), oslo, LocalDate(2026, 5, 16)), // Alta
            Triple(GeoPoint(69.9689, 23.2716), oslo, LocalDate(2026, 7, 27)),
            Triple(GeoPoint(71.2906, -156.7886), TimeZone.of("America/Anchorage"), LocalDate(2026, 5, 10)), // Utqiagvik
            Triple(GeoPoint(71.2906, -156.7886), TimeZone.of("America/Anchorage"), LocalDate(2026, 8, 1)),
            Triple(GeoPoint(78.2232, 15.6267), oslo, LocalDate(2026, 4, 18)), // Longyearbyen
            Triple(GeoPoint(66.5039, 25.7294), TimeZone.of("Europe/Helsinki"), LocalDate(2026, 7, 6)), // Rovaniemi
        )
        for ((point, zone, around) in edges) {
            for (offset in -3..3) {
                val date = around.plus(offset, DateTimeUnit.DAY)
                val day = compute(plain, point, date, zone)
                assertTrue(Invariants.holds(day), "$point $date: ${day.row(zone)} sunset ${day.sunset.clock(zone)}")
            }
        }

        // 66° N on 12 June: a sunrise (the sun then stays up), and last night's sunset the day before.
        val date = LocalDate(2026, 6, 12)
        val point = GeoPoint(66.0, 20.0)
        val own = clock(point, date, stockholm)
        val ownSunrise = own.altitudeTime(-0.8333, morning = true)
        val lastSunset = clock(point, date.previous(), stockholm).altitudeTime(-0.8333, morning = false)
        assertTrue(ownSunrise != null && lastSunset != null && own.altitudeTime(-0.8333, morning = false) == null)
        val day = compute(plain, point, date, stockholm)
        assertTrue(day.polar)
        assertEquals(endOf(ownSunrise!!), day.sunrise, "the place's own sunrise")
        assertFalse(Prayer.SUNRISE in day.setByRule)
        // Fajr from the place's own last night: its 45° estimate, set by rule, before its own sunrise.
        val (fFajr45, _) = fractionsAt45(point, date, stockholm)
        assertEquals(startOf(ownSunrise - fFajr45 * (ownSunrise - lastSunset!!)), day.fajr)
        assertTrue(Prayer.FAJR in day.setByRule && day.fajr < day.sunrise)
    }

    @Test
    fun `murmansk on the edges of its midnight sun and polar night keeps its own sunrise and sunset`() {
        // Each edge day has a sunrise and a sunset; its neighbour has not.
        val edges = listOf(
            LocalDate(2026, 5, 20) to LocalDate(2026, 5, 21),
            LocalDate(2026, 7, 23) to LocalDate(2026, 7, 22),
            LocalDate(2026, 12, 1) to LocalDate(2026, 12, 2),
            LocalDate(2026, 1, 11) to LocalDate(2026, 1, 10),
        )
        val afterMaghrib = plain.copy(id = "test.after", isha = IshaRule.AfterMaghrib(90))
        for ((date, neighbour) in edges) {
            assertTrue(hasSunriseAndSunset(murmansk, date, moscow), "$date")
            assertFalse(hasSunriseAndSunset(murmansk, neighbour, moscow), "$neighbour")
            val ownSunrise = endOf(sunrise(murmansk, date, moscow))
            val ownMaghrib = startOf(sunset(murmansk, date, moscow))
            val angle = compute(plain, murmansk, date, moscow)
            val after = compute(afterMaghrib, murmansk, date, moscow)
            val cautious = Cautious.combine(listOf(angle, after), mostFollowed = 0)
            for (day in listOf(angle, after, cautious)) {
                assertFalse(day.polar, "$date ${day.methodId}")
                assertTrue(day.maghrib >= ownMaghrib, "$date ${day.methodId}: maghrib ${day.maghrib.clock(moscow)}")
                assertTrue(day.sunrise <= ownSunrise, "$date ${day.methodId}: sunrise ${day.sunrise.clock(moscow)}")
                assertTrue(Invariants.holds(day), "$date ${day.methodId}: $day")
            }
        }
    }

    // Rule 3: DUM RT's white nights. Official rows: tools/timetables/official/open/RU-DUMRT/off_kzn.txt
    // (CC BY 4.0): end of suhoor, sunrise, Dhuhr, Asr, Maghrib, Isha, Europe/Moscow.

    private val kazan = GeoPoint(55.79, 49.12)

    /** DUM RT at Kazan's table, which rides as the fixed point as the Kazan unit's does. */
    private val dumRt = TimetableMethod(
        id = "test.dumrt",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(15.0),
        highLatitude = HighLatRule.DumRtSummer(),
        fixedPoint = kazan,
    )

    /** The moment of the sun's lowest over the night before [date]'s morning at [point], by a 5 s scan. */
    private fun lowestMoment(point: GeoPoint, date: LocalDate): Double {
        val culmination = clock(point, date, moscow).transit() - 43_200.0
        var lowest = 90.0
        var at = culmination
        var t = culmination - 2_400.0
        while (t <= culmination + 2_400.0) {
            val sun = SolarMath.sun(SolarMath.julianDay(t))
            val solarHours = ((t / 3600.0) % 24.0 + 24.0) % 24.0 + point.lon / 15.0 + sun.equationOfTimeMinutes / 60.0
            val h = SolarMath.rad((solarHours - 12.0) * 15.0)
            val phi = SolarMath.rad(point.lat)
            val d = SolarMath.rad(sun.declinationDeg)
            val altitude = SolarMath.deg(asin(sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h)))
            if (altitude < lowest) {
                lowest = altitude
                at = t
            }
            t += 5.0
        }
        return at
    }

    private fun assertNear(official: String, shown: Instant, date: LocalDate, what: String) {
        val gap = shown - local(date, official, moscow)
        assertTrue(gap >= (-1).minutes && gap <= 1.minutes, "$date $what: shown ${shown.clock(moscow)} official $official")
    }

    /** The Fajr shown is the end of suhoor rounded up instead of down: the same minute or the next. */
    private fun assertFajrIsTheEndOfSuhoor(day: PrayerDay) {
        val gap = day.fajr - day.endOfEating
        assertTrue(gap >= 0.minutes && gap <= 1.minutes, "${day.date}: fajr ${day.fajr} end of suhoor ${day.endOfEating}")
    }

    @Test
    fun `kazan in the white nights ends suhoor 121 minutes before sunrise and prays isha 90 minutes after maghrib`() {
        val date = LocalDate(2026, 6, 20)
        val day = compute(dumRt, kazan, date, moscow, school = AsrSchool.HANAFI)
        assertEquals(day.sunrise - 121.minutes, day.endOfEating)
        assertFajrIsTheEndOfSuhoor(day)
        assertEquals(day.maghrib + 90.minutes, day.isha)
        assertTrue(Prayer.FAJR in day.setByRule && Prayer.ISHA in day.setByRule)
        assertFalse(day.polar)
        // 2026-06-20: 00:57 02:58 11:46 17:34 20:33 22:03
        assertNear("00:57", day.endOfEating, date, "end of suhoor")
        assertNear("00:57", day.fajr, date, "fajr")
        assertNear("02:58", day.sunrise, date, "sunrise")
        assertNear("11:46", day.dhuhr, date, "dhuhr")
        assertNear("17:34", day.asr, date, "asr (Hanafi)")
        assertNear("20:33", day.maghrib, date, "maghrib")
        assertNear("22:03", day.isha, date, "isha")
    }

    @Test
    fun `in the white nights the fajr shown is never before and the end of suhoor never after dum rt's printed time`() {
        // off_kzn.txt: date, end of suhoor (= the Fajr shown) and sunrise, from the window.
        val rows = listOf(
            "2026-05-06 01:49", "2026-05-20 01:23", "2026-06-01 01:07", "2026-07-01 01:03",
            "2026-07-15 01:19", "2026-08-01 01:48", "2026-08-07 01:59", "2026-08-08 02:01",
        )
        for (row in rows) {
            val (iso, sahur) = row.split(" ")
            val date = LocalDate.parse(iso)
            val day = compute(dumRt, kazan, date, moscow, school = AsrSchool.HANAFI)
            val printed = local(date, sahur, moscow)
            assertTrue(day.fajr >= printed, "$date fajr ${day.fajr.clock(moscow)} before $sahur")
            assertTrue(day.endOfEating <= printed, "$date end of suhoor ${day.endOfEating.clock(moscow)} after $sahur")
            assertFajrIsTheEndOfSuhoor(day)
        }
    }

    @Test
    fun `the white nights are the nights whose 18 degree dawn is missing and the evenings before them`() {
        // At Kazan in 2026 those are the mornings of 6 May to 8 August, as DUM RT's table prints them.
        // 5 May: suhoor ends at the 18° dawn (23:54 the evening before; the sun reaches 18.05°), Isha is
        // already Maghrib + 90 (21:00).
        val may5 = compute(dumRt, kazan, LocalDate(2026, 5, 5), moscow, school = AsrSchool.HANAFI)
        assertNotEquals(may5.sunrise - 121.minutes, may5.endOfEating)
        // The real 18° dawn, minutes from the sun's lowest point, where a kilometre moves it a minute.
        val printed = local(LocalDate(2026, 5, 4), "23:54", moscow)
        assertTrue(may5.endOfEating <= printed && printed - may5.endOfEating <= 3.minutes, "end of suhoor ${may5.endOfEating}")
        assertFalse(Prayer.FAJR in may5.setByRule)
        assertEquals(may5.maghrib + 90.minutes, may5.isha)
        assertNear("21:00", may5.isha, LocalDate(2026, 5, 5), "isha")

        // In the leap year 2028 the sun misses 18° on 5 May (17.91°): the window opens a night earlier.
        val may5in2028 = compute(dumRt, kazan, LocalDate(2028, 5, 5), moscow, school = AsrSchool.HANAFI)
        assertEquals(may5in2028.sunrise - 121.minutes, may5in2028.endOfEating)
        val may4in2028 = compute(dumRt, kazan, LocalDate(2028, 5, 4), moscow, school = AsrSchool.HANAFI)
        assertEquals(may4in2028.maghrib + 90.minutes, may4in2028.isha)

        // 6 May: the first window morning (01:49).
        val may6 = compute(dumRt, kazan, LocalDate(2026, 5, 6), moscow, school = AsrSchool.HANAFI)
        assertEquals(may6.sunrise - 121.minutes, may6.endOfEating)
        assertFajrIsTheEndOfSuhoor(may6)
        assertNear("01:49", may6.endOfEating, LocalDate(2026, 5, 6), "end of suhoor")

        // 8 August: the last window morning (02:01); that evening's Isha is the real 15° dusk (22:06).
        val august8 = compute(dumRt, kazan, LocalDate(2026, 8, 8), moscow, school = AsrSchool.HANAFI)
        assertEquals(august8.sunrise - 121.minutes, august8.endOfEating)
        assertFajrIsTheEndOfSuhoor(august8)
        assertNear("02:01", august8.endOfEating, LocalDate(2026, 8, 8), "end of suhoor")
        assertNotEquals(august8.maghrib + 90.minutes, august8.isha)
        assertFalse(Prayer.ISHA in august8.setByRule)
        assertEquals(startOf(realIsha(kazan, LocalDate(2026, 8, 8), moscow, 15.0)!!), august8.isha)

        // 9 August: outside the window altogether.
        val august9 = compute(dumRt, kazan, LocalDate(2026, 8, 9), moscow, school = AsrSchool.HANAFI)
        assertNotEquals(august9.sunrise - 121.minutes, august9.endOfEating)
        assertEquals(emptySet(), august9.setByRule)
    }

    @Test
    fun `a white night too close to call takes each event's safe side`() {
        // Ruling R61. The later Fajr (sunrise − 121, which no real dawn this close to the sun's lowest
        // comes after), the earlier end of suhoor (the sun's lowest: a sun that only just reaches 18°
        // dawns there) and, on the evening before, the later Isha (Maghrib + 90 or the real 15° dusk).
        fun assertSafeSides(method: TimetableMethod, point: GeoPoint, morning: LocalDate) {
            val day = compute(method, point, morning, moscow, school = AsrSchool.HANAFI)
            assertTrue(day.fajr >= day.sunrise - 121.minutes, "$morning fajr ${day.fajr.clock(moscow)}")
            assertTrue(Prayer.FAJR in day.setByRule, "$morning fajr set by rule")
            val lowest = lowestMoment(point, morning)
            assertTrue(day.endOfEating.epochSeconds <= lowest + 30, "$morning end of suhoor ${day.endOfEating.clock(moscow)}")
            assertTrue(lowest - day.endOfEating.epochSeconds <= 120, "$morning end of suhoor ${day.endOfEating.clock(moscow)}")
            val evening = morning.previous()
            val before = compute(method, point, evening, moscow, school = AsrSchool.HANAFI)
            val dusk = startOf(realIsha(point, evening, moscow, 15.0)!!)
            assertEquals(maxOf(before.maghrib + 90.minutes, dusk), before.isha, "$evening isha")
        }

        // At the user's own point (no table's point beside it) within 0.2° of 18°: 5.5 km south of
        // Kazan on 8 August 2026 the sun reaches 18.03° while Kazan's table applies the rule (17.98°),
        // and 5.5 km north of Yelabuga on the same morning it misses 18° while Yelabuga's table prints
        // its real dawn.
        val atTheUser = dumRt.copy(fixedPoint = null)
        assertSafeSides(atTheUser, GeoPoint(kazan.lat - 0.05, kazan.lon), LocalDate(2026, 8, 8))
        assertSafeSides(atTheUser, GeoPoint(55.81232, 52.04425), LocalDate(2026, 8, 8))

        // At a table's own point only a sun within 0.01° of 18° is too close to call: Kazan on
        // 8 August 2030 (17.995°), where DUM RT's sun and ours may fall either side.
        assertSafeSides(dumRt, kazan, LocalDate(2030, 8, 8))
        // Kazan's own nights of 2026 are clear (5 May 18.05°, 8 August 17.98°): its table's rule, exactly.
        val august8 = compute(dumRt, kazan, LocalDate(2026, 8, 8), moscow, school = AsrSchool.HANAFI)
        assertEquals(august8.sunrise - 121.minutes, august8.endOfEating)
    }

    // Rules 4 and 6: an authority's night fraction and the legacy rules, only when the sign is missing.

    @Test
    fun `a night fraction rule applies only on days the sign is missing`() {
        val method = plain.copy(highLatitude = HighLatRule.NightFraction(fajrFraction = 0.3, ishaFraction = 0.25))
        val date = LocalDate(2026, 6, 21)
        val day = compute(method, london, date, londonZone)
        assertEquals(setOf(Prayer.FAJR, Prayer.ISHA), day.setByRule)
        val dusk = sunset(london, date, londonZone)
        val dawn = sunrise(london, date, londonZone)
        assertEquals(startOf(dusk + 0.25 * (sunrise(london, date.next(), londonZone) - dusk)), day.isha)
        assertEquals(startOf(dawn - 0.3 * (dawn - sunset(london, date.previous(), londonZone))), day.fajr)

        val winter = LocalDate(2026, 1, 15)
        assertEquals(
            compute(plain, london, winter, londonZone).copy(methodId = method.id),
            compute(method, london, winter, londonZone),
        )
    }

    @Test
    fun `the legacy rules take half a seventh or an angle's sixtieth of the night`() {
        val date = LocalDate(2026, 6, 21)
        val dusk = sunset(london, date, londonZone)
        val night = sunrise(london, date.next(), londonZone) - dusk
        val dawn = sunrise(london, date, londonZone)
        val lastNight = dawn - sunset(london, date.previous(), londonZone)
        val cases = mapOf(
            "middle" to (0.5 to 0.5),
            "seventh" to (1.0 / 7 to 1.0 / 7),
            "angle" to (18.0 / 60 to 17.0 / 60),
        )
        for ((kind, fractions) in cases) {
            val method = plain.copy(highLatitude = HighLatRule.Legacy(kind))
            val day = compute(method, london, date, londonZone)
            assertEquals(setOf(Prayer.FAJR, Prayer.ISHA), day.setByRule, kind)
            assertEquals(startOf(dawn - fractions.first * lastNight), day.fajr, kind)
            assertEquals(startOf(dusk + fractions.second * night), day.isha, kind)
            val winter = LocalDate(2026, 1, 15)
            assertEquals(
                compute(plain, london, winter, londonZone).copy(methodId = method.id),
                compute(method, london, winter, londonZone),
                kind,
            )
        }
        assertFailsWith<IllegalArgumentException> { HighLatRule.Legacy("quarter") }
    }

    /**
     * A clock rule with an end of eating only, half an hour before the day the plain method shows
     * (an authority's printed precaution): the next day's end shown is not the one the rate limit's
     * own look ahead computes.
     */
    private class EarlierEating(private val plain: TimetableMethod) : ClockRule {
        override fun on(date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTimes {
            val own = DayComputer.compute(plain, point, date, zone, AsrSchool.STANDARD, { false }).endOfEating
            return ClockTimes(
                fajr = null, sunrise = null, dhuhr = null, asrStandard = null, asrHanafi = null, maghrib = null, isha = null,
                endOfEating = own.epochSeconds - 30 * 60.0, sunset = null, noSunset = false,
            )
        }

        override fun toString() = "EarlierEating(${plain.id})"
    }

    @Test
    fun `the isha cap reads the next day's end as shown after its own clock rule`() {
        // Ruling R92: R62's cap holds a limited Isha a minute before the next day's end of eating
        // as that day shows it, after every step (here a clock rule moving the end earlier), so it
        // never lands on the end shown.
        val longyearbyen = GeoPoint(78.2232, 15.6267)
        val after90 = TimetableMethod(id = "test.after-90", fajrAngle = 18.5, isha = IshaRule.AfterMaghrib(90))
        val ruled = after90.copy(id = "test.after-90.ruled", clockRule = EarlierEating(after90))
        var capped = 0
        var before = compute(ruled, longyearbyen, LocalDate(2026, 3, 31), oslo)
        var day = compute(ruled, longyearbyen, LocalDate(2026, 4, 1), oslo)
        while (day.date < LocalDate(2026, 10, 1)) {
            val next = compute(ruled, longyearbyen, day.date.next(), oslo)
            val plainNext = compute(after90, longyearbyen, day.date.next(), oslo)
            assertTrue(next.endOfEating <= plainNext.endOfEating - 30.minutes, "${day.date}: the rule's end is the one shown")
            val own = day.maghrib + 90.minutes
            val label = "${day.date}: isha ${day.isha.clock(oslo)}, own ${own.clock(oslo)}, next end ${next.endOfEating.clock(oslo)}"
            assertTrue(day.isha >= own, "$label: before its own rule")
            if (day.isha > own) assertTrue(day.isha < next.endOfEating, "$label: the limit holds it into the next night's end as shown")
            if (day.isha < before.isha + 1.days - 20.minutes) {
                assertEquals(maxOf(own, next.endOfEating - 1.minutes), day.isha, label)
                capped++
            }
            before = day
            day = next
        }
        assertTrue(capped > 0, "the next night's end as shown binds on some day")
    }
}
