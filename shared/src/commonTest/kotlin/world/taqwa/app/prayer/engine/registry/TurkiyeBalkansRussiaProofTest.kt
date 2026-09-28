package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Task 7b's committed data points, only where the data is open: DUM RT's 2026 table (CC BY 4.0,
 * tools/timetables/official/open/RU-DUMRT). Diyanet's tables are not open, so its İstanbul unit is
 * checked against its own algorithm rounded to the nearest minute, as Diyanet prints it, with no
 * printed time here (ruling R69). The gate (tr-diyanet.tsv, ru-dumrt.tsv) checks every held day;
 * these pin a few so that the app's own test run sees them.
 */
class TurkiyeBalkansRussiaProofTest {

    private fun local(date: LocalDate, hhmm: String, zone: TimeZone): Instant {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return LocalDateTime(date, LocalTime(h, m)).toInstant(zone)
    }

    private fun Instant.clock(zone: TimeZone): String = toLocalDateTime(zone).time.toString()

    private fun day(entryId: String, lat: Double, lon: Double, zone: String, country: String, date: LocalDate): PrayerDay =
        DayPipeline.day(Registry.byId(entryId)!!, Place(lat, lon, zone, country), date)

    /** A printed row: the start columns never before it, the end columns never after, and the lateness within [limit] minutes. */
    private fun assertNeverEarly(
        day: PrayerDay,
        zone: TimeZone,
        starts: Map<String, Pair<Instant, String>>,
        ends: Map<String, Pair<Instant, String>>,
        limit: Int,
    ) {
        for ((name, pair) in starts) {
            val (shown, printed) = pair
            val at = local(day.date, printed, zone)
            assertTrue(shown >= at, "${day.date} $name ${shown.clock(zone)} before $printed")
            assertTrue(shown - at <= limit.minutes, "${day.date} $name ${shown.clock(zone)} more than $limit min after $printed")
        }
        for ((name, pair) in ends) {
            val (shown, printed) = pair
            val at = local(day.date, printed, zone)
            assertTrue(shown <= at, "${day.date} $name ${shown.clock(zone)} after $printed")
            assertTrue(at - shown <= limit.minutes, "${day.date} $name ${shown.clock(zone)} more than $limit min before $printed")
        }
    }

    @Test
    fun `diyanet at istanbul is never before its own nearest minute and at most a minute after`() {
        // Diyanet prints its algorithm rounded to the nearest minute (DayComputerTest). The registry's
        // İstanbul unit, with its own safety margins, against the same method rounded that way: each
        // start never before, each end never after, and at most a minute apart, all through the autumn.
        val place = Place(41.012, 28.974, "Europe/Istanbul", "TR")
        val zone = TimeZone.of(place.zoneId)
        val resolution = Registry.resolve(place)
        assertEquals("tr.diyanet", resolution.entry.id)
        val method = resolution.method!!
        val nearest = method.copy(
            margins = EventOffsets(fajr = -30, sunrise = 30, dhuhr = -30, asr = -30, maghrib = -30, isha = -30),
            endOfEatingMarginSeconds = 30,
        )
        val ramadan = Registry.ramadanCalendarFor(resolution.entry)
        var later = 0
        var date = LocalDate(2026, 9, 1)
        while (date < LocalDate(2026, 12, 1)) {
            val shown = DayComputer.compute(method, resolution.point, date, zone, AsrSchool.STANDARD, ramadan)
            val printed = DayComputer.compute(nearest, resolution.point, date, zone, AsrSchool.STANDARD, ramadan)
            val starts = listOf(shown.fajr to printed.fajr, shown.dhuhr to printed.dhuhr, shown.asr to printed.asr,
                shown.maghrib to printed.maghrib, shown.isha to printed.isha)
            for ((at, own) in starts) {
                assertTrue(at >= own && at - own <= 1.minutes, "$date start ${at.clock(zone)} against ${own.clock(zone)}")
                if (at > own) later++
            }
            for ((at, own) in listOf(shown.sunrise to printed.sunrise, shown.endOfEating to printed.endOfEating)) {
                assertTrue(at <= own && own - at <= 1.minutes, "$date end ${at.clock(zone)} against ${own.clock(zone)}")
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        // Where a moment falls just past the half minute, the safety shows it a minute late.
        assertTrue(later > 0)
    }

    @Test
    fun `dum rt's open kazan rows are never early and within class b`() {
        // off_kzn.txt: end of sahur (also the Fajr shown), sunrise, Asr (Hanafi), Maghrib, Isha; Zuhr 12:00.
        val zone = TimeZone.of("Europe/Moscow")
        val rows = listOf(
            "2026-01-01 05:53 08:14 13:34 15:22 17:19",
            "2026-03-20 03:39 05:47 15:54 17:57 19:41",
            "2026-06-20 00:57 02:58 17:34 20:33 22:03",
            "2026-09-26 03:29 05:37 15:31 17:33 19:16",
            "2026-12-21 05:50 08:12 13:25 15:12 17:11",
        )
        for (row in rows) {
            val c = row.split(" ")
            val date = LocalDate.parse(c[0])
            val d = day("ru.dumrt", 55.78874, 49.12214, "Europe/Moscow", "RU", date)
            assertNeverEarly(
                d, zone,
                starts = mapOf("fajr" to (d.fajr to c[1]), "asr" to (d.asr to c[3]), "maghrib" to (d.maghrib to c[4]), "isha" to (d.isha to c[5])),
                ends = mapOf("sunrise" to (d.sunrise to c[2]), "end of sahur" to (d.endOfEating to c[1])),
                limit = 2,
            )
            assertEquals(local(date, "12:00", zone), d.dhuhr, "$date: the printed Zuhr")
        }
    }

    @Test
    fun `a dum rt town keeps its own printed zuhr`() {
        // dumrt-2026-agryz.txt: end of sahur, sunrise, Zuhr 11:45, Asr (Hanafi), Maghrib, Isha; held out from Kazan's fit.
        val zone = TimeZone.of("Europe/Moscow")
        val rows = listOf(
            "2026-01-01 05:39 08:03 11:45 13:14 15:01 17:02",
            "2026-09-26 03:11 05:21 11:45 15:14 17:17 19:02",
            "2026-12-21 05:36 08:01 11:45 13:05 14:51 16:53",
        )
        for (row in rows) {
            val c = row.split(" ")
            val date = LocalDate.parse(c[0])
            val d = day("ru.dumrt", 56.5234, 52.99431, "Europe/Moscow", "RU", date)
            assertNeverEarly(
                d, zone,
                starts = mapOf("fajr" to (d.fajr to c[1]), "asr" to (d.asr to c[4]), "maghrib" to (d.maghrib to c[5]), "isha" to (d.isha to c[6])),
                ends = mapOf("sunrise" to (d.sunrise to c[2]), "end of sahur" to (d.endOfEating to c[1])),
                limit = 2,
            )
            assertEquals(local(date, c[3], zone), d.dhuhr, "$date: Agryz's printed Zuhr")
        }
    }

    /** The sun's lowest over the night before [date]'s morning at [lat], [lon], by a 30 s scan: its altitude and moment. */
    private fun lowestBefore(lat: Double, lon: Double, date: LocalDate): Pair<Double, Double> {
        val culmination = SunClock(lat, lon, date, 3 * 3600, SunModel.EXACT).transit() - 43_200.0
        var lowest = 90.0
        var at = culmination
        var t = culmination - 2_400.0
        while (t <= culmination + 2_400.0) {
            val sun = SolarMath.sun(SolarMath.julianDay(t))
            val solarHours = ((t / 3600.0) % 24.0 + 24.0) % 24.0 + lon / 15.0 + sun.equationOfTimeMinutes / 60.0
            val h = SolarMath.rad((solarHours - 12.0) * 15.0)
            val phi = SolarMath.rad(lat)
            val d = SolarMath.rad(sun.declinationDeg)
            val altitude = SolarMath.deg(asin(sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h)))
            if (altitude < lowest) {
                lowest = altitude
                at = t
            }
            t += 30.0
        }
        return lowest to at
    }

    /**
     * Ruling R61's safe sides on [morning] under [method] at [point]: Fajr no earlier than sunrise − 121,
     * the end of sahur no later than the sun's lowest (and within 4 min of it) and, on the evening
     * before, Isha no earlier than Maghrib + 90 or the real 15° dusk.
     */
    private fun assertSafeSides(method: TimetableMethod, point: GeoPoint, morning: LocalDate, what: String) {
        val zone = TimeZone.of("Europe/Moscow")
        val calendar = Registry.ramadanCalendarFor(Registry.byId("ru.dumrt")!!)
        val day = DayComputer.compute(method, point, morning, zone, AsrSchool.HANAFI, calendar)
        assertTrue(day.fajr >= day.sunrise - 121.minutes, "$what $morning: fajr ${day.fajr.clock(zone)}")
        val lowest = lowestBefore(point.lat, point.lon, morning).second
        assertTrue(day.endOfEating.epochSeconds <= lowest + 30, "$what $morning: end of sahur ${day.endOfEating.clock(zone)}")
        assertTrue(lowest - day.endOfEating.epochSeconds <= 240, "$what $morning: end of sahur ${day.endOfEating.clock(zone)}")
        val evening = morning.minus(1, DateTimeUnit.DAY)
        val before = DayComputer.compute(method, point, evening, zone, AsrSchool.HANAFI, calendar)
        val dusk = SunClock(point.lat, point.lon, evening, 3 * 3600, SunModel.EXACT).altitudeTime(-15.0, morning = false)!!
        assertTrue(before.isha >= before.maghrib + 90.minutes, "$what $evening: isha ${before.isha.clock(zone)}")
        assertTrue(before.isha.epochSeconds >= dusk, "$what $evening: isha ${before.isha.clock(zone)} before the 15° dusk")
    }

    @Test
    fun `dum rt's white nights at every town are the nights its 18 degree dawn is missing`() {
        // DUM RT's 2026 table applies sahur = sunrise − 121 on exactly the mornings the sun misses 18° at
        // each town, and Isha = Maghrib + 90 on the evening before. Checked April to September of 2026
        // and of the leap year 2028 at all 19 towns against a scan of the sun's altitude. A night within
        // 0.01° of 18° is too close to call (ruling R61) and takes each event's safe side.
        val zone = TimeZone.of("Europe/Moscow")
        val entry = Registry.byId("ru.dumrt")!!
        var windowMornings = 0
        var tooClose = 0
        for (unit in Units.of("ru.dumrt")!!.units) {
            val method = Registry.resolveEntry(entry, Place(unit.point.lat, unit.point.lon, "Europe/Moscow", "RU")).method!!
            val missing = HashMap<LocalDate, Boolean?>()
            fun missingOn(date: LocalDate): Boolean? = missing.getOrPut(date) {
                val depth = -lowestBefore(unit.point.lat, unit.point.lon, date).first
                if (abs(depth - 18.0) < 0.01) null else depth < 18.0
            }
            for (year in listOf(2026, 2028)) {
                var date = LocalDate(year, 4, 1)
                while (date <= LocalDate(year, 9, 30)) {
                    val day = DayComputer.compute(method, unit.point, date, zone, AsrSchool.HANAFI, Registry.ramadanCalendarFor(entry))
                    val white = missingOn(date)
                    if (white == null) {
                        tooClose++
                        assertSafeSides(method, unit.point, date, unit.name)
                    } else {
                        assertEquals(white, Prayer.FAJR in day.setByRule, "${unit.name} $date: a white morning")
                        if (white) {
                            windowMornings++
                            assertEquals(day.sunrise - 121.minutes, day.endOfEating, "${unit.name} $date: sahur")
                        }
                    }
                    missingOn(date.plus(1, DateTimeUnit.DAY))?.let { whiteTomorrow ->
                        assertEquals(whiteTomorrow, day.isha == day.maghrib + 90.minutes, "${unit.name} $date: a white evening")
                    }
                    date = date.plus(1, DateTimeUnit.DAY)
                }
            }
        }
        assertTrue(windowMornings > 19 * 2 * 80, "$windowMornings white mornings")
        // Zelenodolsk on 5 May 2026 (17.998°), Yelabuga on 8 August 2026 (18.008°) and four nights of 2028.
        assertEquals(6, tooClose, "nights too close to call")
    }

    @Test
    fun `beyond the towns a white night within 0 point 2 degrees of 18 takes each event's safe side`() {
        // Ruling R61. Elsewhere in Tatarstan the app applies DUM RT's rule at the user's own point; the
        // locality whose table DUM RT would print may lie up to about 20 km away, where the night can
        // fall the other way. The review's cases: the edge 5.5 km from a town, against that town's open
        // table (the times below are DUM RT's own, CC BY 4.0).
        val zone = TimeZone.of("Europe/Moscow")
        val units = Units.of("ru.dumrt")!!
        val calendar = Registry.ramadanCalendarFor(Registry.byId("ru.dumrt")!!)
        fun edgeDay(point: GeoPoint, date: LocalDate): PrayerDay =
            DayComputer.compute(units.outside(point), point, date, zone, AsrSchool.HANAFI, calendar)
        val kazanSouth = GeoPoint(55.78874 - 0.05, 49.12214)
        val yelabugaNorth = GeoPoint(55.76232 + 0.05, 52.04425)
        val buinskNorth = GeoPoint(54.97422 + 0.05, 48.29088)

        // South of Kazan on 8 August its table applies the rule (sahur 02:01); the edge's Fajr was the
        // real dawn, 119 min before it.
        val august8 = LocalDate(2026, 8, 8)
        val kazan = edgeDay(kazanSouth, august8)
        assertTrue(kazan.fajr >= local(august8, "02:01", zone), "Kazan −0.05° fajr ${kazan.fajr.clock(zone)}")
        assertTrue(kazan.endOfEating <= local(august8, "02:01", zone), "Kazan −0.05° sahur ${kazan.endOfEating.clock(zone)}")
        // North of Yelabuga its table prints the real signs (Isha 21:59 on 7 August, sahur 23:44 that
        // night); the edge showed Isha 60 min early and the end of sahur 123 min late.
        val august7 = LocalDate(2026, 8, 7)
        val yelabugaEvening = edgeDay(yelabugaNorth, august7)
        assertTrue(yelabugaEvening.isha >= local(august7, "21:59", zone), "Yelabuga +0.05° isha ${yelabugaEvening.isha.clock(zone)}")
        val yelabugaMorning = edgeDay(yelabugaNorth, august8)
        assertTrue(yelabugaMorning.endOfEating <= local(august7, "23:44", zone), "Yelabuga +0.05° sahur ${yelabugaMorning.endOfEating.clock(zone)}")
        // North of Buinsk likewise (Isha 22:04 on 7 May, sahur 23:53 that night).
        val may7 = LocalDate(2026, 5, 7)
        val buinskEvening = edgeDay(buinskNorth, may7)
        assertTrue(buinskEvening.isha >= local(may7, "22:04", zone), "Buinsk +0.05° isha ${buinskEvening.isha.clock(zone)}")
        val buinskMorning = edgeDay(buinskNorth, LocalDate(2026, 5, 8))
        assertTrue(buinskMorning.endOfEating <= local(may7, "23:53", zone), "Buinsk +0.05° sahur ${buinskMorning.endOfEating.clock(zone)}")

        for ((point, date) in listOf(kazanSouth to august8, yelabugaNorth to august8, buinskNorth to LocalDate(2026, 5, 8))) {
            assertSafeSides(units.outside(point), point, date, "edge")
        }
    }

    @Test
    fun `beyond the towns no night is early against a table 22 km off`() {
        // Ruling R65. The edge 11 and 22 km north and south of Kazan and Yelabuga, every night from 20 April
        // to 12 May and 1 to 20 August (the white nights' edges and the weeks either side, where the dawn
        // moves most with latitude), against the town's open table: Fajr never before its end of sahur (the
        // Fajr it shows), the end of sahur never after it, Isha never before its Isha. Rows: date, end of
        // sahur (before noon the morning's, after it the evening before), Isha; DUM RT's own times (CC BY 4.0).
        val zone = TimeZone.of("Europe/Moscow")
        val units = Units.of("ru.dumrt")!!
        val calendar = Registry.ramadanCalendarFor(Registry.byId("ru.dumrt")!!)
        val tables = mapOf(
            GeoPoint(55.78874, 49.12214) to listOf(
                "2026-04-20 01:46 21:06",
                "2026-04-21 01:41 21:09",
                "2026-04-22 01:36 21:13",
                "2026-04-23 01:31 21:16",
                "2026-04-24 01:26 21:20",
                "2026-04-25 01:21 21:23",
                "2026-04-26 01:16 21:27",
                "2026-04-27 01:10 21:31",
                "2026-04-28 01:04 21:35",
                "2026-04-29 00:57 21:38",
                "2026-04-30 00:51 21:42",
                "2026-05-01 00:43 21:47",
                "2026-05-02 00:35 21:51",
                "2026-05-03 00:25 21:55",
                "2026-05-04 00:13 22:00",
                "2026-05-05 23:54 21:00",
                "2026-05-06 01:49 21:02",
                "2026-05-07 01:47 21:04",
                "2026-05-08 01:45 21:06",
                "2026-05-09 01:43 21:08",
                "2026-05-10 01:41 21:10",
                "2026-05-11 01:39 21:12",
                "2026-05-12 01:37 21:14",
                "2026-08-01 01:48 21:21",
                "2026-08-02 01:49 21:19",
                "2026-08-03 01:51 21:17",
                "2026-08-04 01:53 21:15",
                "2026-08-05 01:55 21:13",
                "2026-08-06 01:57 21:11",
                "2026-08-07 01:59 21:08",
                "2026-08-08 02:01 22:06",
                "2026-08-09 00:19 22:01",
                "2026-08-10 00:31 21:57",
                "2026-08-11 00:41 21:52",
                "2026-08-12 00:49 21:48",
                "2026-08-13 00:57 21:44",
                "2026-08-14 01:03 21:40",
                "2026-08-15 01:10 21:36",
                "2026-08-16 01:15 21:32",
                "2026-08-17 01:21 21:28",
                "2026-08-18 01:26 21:24",
                "2026-08-19 01:31 21:20",
                "2026-08-20 01:35 21:16",
            ),
            GeoPoint(55.76232, 52.04425) to listOf(
                "2026-04-20 01:34 20:54",
                "2026-04-21 01:30 20:57",
                "2026-04-22 01:25 21:01",
                "2026-04-23 01:20 21:04",
                "2026-04-24 01:15 21:08",
                "2026-04-25 01:10 21:11",
                "2026-04-26 01:04 21:15",
                "2026-04-27 00:59 21:19",
                "2026-04-28 00:53 21:23",
                "2026-04-29 00:46 21:26",
                "2026-04-30 00:39 21:30",
                "2026-05-01 00:32 21:35",
                "2026-05-02 00:24 21:39",
                "2026-05-03 00:14 21:43",
                "2026-05-04 00:03 21:48",
                "2026-05-05 23:45 20:48",
                "2026-05-06 01:38 20:50",
                "2026-05-07 01:36 20:52",
                "2026-05-08 01:34 20:54",
                "2026-05-09 01:32 20:56",
                "2026-05-10 01:30 20:58",
                "2026-05-11 01:28 21:00",
                "2026-05-12 01:26 21:02",
                "2026-08-01 01:36 21:09",
                "2026-08-02 01:38 21:07",
                "2026-08-03 01:40 21:05",
                "2026-08-04 01:42 21:03",
                "2026-08-05 01:43 21:01",
                "2026-08-06 01:45 20:59",
                "2026-08-07 01:47 21:59",
                "2026-08-08 23:44 21:54",
                "2026-08-09 00:08 21:49",
                "2026-08-10 00:21 21:45",
                "2026-08-11 00:30 21:40",
                "2026-08-12 00:38 21:36",
                "2026-08-13 00:46 21:32",
                "2026-08-14 00:52 21:28",
                "2026-08-15 00:58 21:24",
                "2026-08-16 01:04 21:20",
                "2026-08-17 01:09 21:16",
                "2026-08-18 01:14 21:12",
                "2026-08-19 01:19 21:08",
                "2026-08-20 01:24 21:04",
            ),
        )
        var checked = 0
        for ((town, rows) in tables) {
            for (dlat in listOf(-0.2, -0.1, 0.1, 0.2)) {
                val point = GeoPoint(town.lat + dlat, town.lon)
                val method = units.outside(point)
                for (row in rows) {
                    val (iso, sahur, isha) = row.split(" ")
                    val date = LocalDate.parse(iso)
                    val day = DayComputer.compute(method, point, date, zone, AsrSchool.HANAFI, calendar)
                    val where = "$town ${if (dlat > 0) "+" else ""}$dlat° $date"
                    val printed = local(if (sahur.startsWith("2")) date.minus(1, DateTimeUnit.DAY) else date, sahur, zone)
                    assertTrue(day.fajr >= printed, "$where: fajr ${day.fajr.clock(zone)} before $sahur")
                    assertTrue(day.endOfEating <= printed, "$where: end of sahur ${day.endOfEating.clock(zone)} after $sahur")
                    assertTrue(day.isha >= local(date, isha, zone), "$where: isha ${day.isha.clock(zone)} before $isha")
                    checked++
                }
            }
        }
        assertEquals(2 * 4 * 43, checked)
    }

    @Test
    fun `a town's night within 0 point 01 degrees of 18 takes each event's safe side`() {
        // Ruling R61 at a unit's own point, where DUM RT's sun and ours may fall either side of 18°:
        // Leninogorsk on 9 May 2028 (17.9999°) and Kazan on 8 August 2030 (17.996°).
        val entry = Registry.byId("ru.dumrt")!!
        for ((point, date) in listOf(
            GeoPoint(54.5971, 52.45124) to LocalDate(2028, 5, 9),
            GeoPoint(55.78874, 49.12214) to LocalDate(2030, 8, 8),
        )) {
            val method = Registry.resolveEntry(entry, Place(point.lat, point.lon, "Europe/Moscow", "RU")).method!!
            assertEquals(point, method.fixedPoint)
            assertSafeSides(method, point, date, "unit")
        }
    }
}
