package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Levant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Task 7e's proof for the Levant, Iraq and Yemen (docs/research/2026-09-prayer-times/proof/7e-levant.md).
 * No printed time from any of these authorities' tables may be committed here (none is openly
 * licensed, and none is already public in the spec, unlike Diyanet İstanbul): every number the gate
 * proved lives instead in the git-ignored stamps and in proof.md. What this test can and does check,
 * without repeating a single official minute, is that the registry carries the class the gate found
 * for each entry, and that each method's own day is never out of order at the point the gate proved
 * it at.
 */
class LevantProofTest {

    private fun day(id: String, lat: Double, lon: Double, zone: String, date: LocalDate) =
        DayComputer.compute(
            requireNotNull(Registry.byId(id)?.method) { "$id has no single method" },
            GeoPoint(lat, lon), date, TimeZone.of(zone), AsrSchool.STANDARD, Registry.ramadanCalendar(),
        )

    /** No prayer's time is out of its natural order (a necessary condition for never-early). */
    private fun assertOrdered(label: String, d: world.taqwa.app.prayer.engine.day.PrayerDay) {
        assertTrue(d.fajr < d.sunrise, "$label: fajr before sunrise")
        assertTrue(d.sunrise < d.dhuhr, "$label: sunrise before dhuhr")
        assertTrue(d.dhuhr < d.asr, "$label: dhuhr before asr")
        assertTrue(d.asr < d.maghrib, "$label: asr before maghrib")
        assertTrue(d.maghrib < d.isha, "$label: maghrib before isha")
    }

    @Test
    fun `jordan is class A at amman and no stronger than B or D authority anywhere else`() {
        val entry = requireNotNull(Registry.byId("jo.awqaf"))
        // The entry's own class is B ("an unverified unit"): every one of its 80 fitted dates is
        // Amman's own (the calculator's <option selected> names it on every capture), so nothing
        // beyond Amman is measured.
        assertEquals(EntryClass.B, entry.entryClass)
        assertTrue(!entry.measured)
        val amman = Registry.resolve(Place(31.955, 35.945, "Asia/Amman", "JO"))
        assertEquals("jo.awqaf", amman.entry.id)
        assertEquals("Amman", amman.unitName)
        assertEquals(EntryClass.A, amman.entryClass)
        assertTrue(amman.measured)
        // Aqaba, far enough from Amman to fall outside its reach: no unit, so an A/B entry reads
        // D_AUTHORITY there (spec §6.2 a), never the entry's own claimed class.
        val aqaba = Registry.resolve(Place(29.5267, 35.0078, "Asia/Amman", "JO"))
        assertEquals("jo.awqaf", aqaba.entry.id)
        assertEquals(null, aqaba.unitName)
        assertEquals(EntryClass.D_AUTHORITY, aqaba.entryClass)
        assertTrue(!aqaba.measured)
        assertOrdered("jo.awqaf@amman", day("jo.awqaf", 31.955, 35.945, "Asia/Amman", LocalDate(2026, 6, 21)))
        assertOrdered("jo.awqaf@amman", day("jo.awqaf", 31.955, 35.945, "Asia/Amman", LocalDate(2026, 12, 21)))
    }

    @Test
    fun `the pa's al-aqsa table is class D authority, measured at every printed town (ruling R118)`() {
        // The whole perpetual table runs 3 min late on four events even at al-Aqsa (ruling R57): D.
        val entry = requireNotNull(Registry.byId("ps.iftaa"))
        assertEquals(EntryClass.D_AUTHORITY, entry.entryClass)
        assertTrue(entry.measured)
        assertOrdered("ps.iftaa", day("ps.iftaa", 31.7767, 35.2345, "Asia/Hebron", LocalDate(2026, 6, 21)))
        // Every town the offset list prints is its own unit at its own point, measured, class D: the Palestinian
        // towns, and the list's towns in Israel (units only for the places in Palestine nearer them).
        assertEquals(
            listOf(
                "Jerusalem", "Ramallah", "Bethlehem", "Jenin", "Nablus", "Jericho", "Hebron", "Idhna", "Dura", "Beit Awwa",
                "Tulkarm", "Qalqilya", "Gaza", "Rafah", "Khan Yunis", "Deir al-Balah",
            ),
            Levant.paTowns.filter { it.country == "PS" }.map { it.name },
        )
        assertEquals(
            listOf(
                "Nazareth", "Umm al-Fahm", "Tiberias", "Safed", "Beisan", "Haifa", "Acre", "Kafr Qasim", "Tayibe", "Lydd",
                "Ramla", "Beersheba", "Jaffa",
            ),
            Levant.paTowns.filter { it.country == "IL" }.map { it.name },
        )
        assertEquals(setOf("PS"), entry.countries, "the entry's scope stays Palestine")
        for (town in Levant.paTowns) {
            val here = Registry.resolveEntry(entry, Place(town.point.lat, town.point.lon, zoneOf(town), "PS"))
            assertEquals(town.name, here.unitName, town.name)
            assertEquals(EntryClass.D_AUTHORITY, here.entryClass, town.name)
            assertTrue(here.measured, town.name)
        }
        // Gaza's cautious times place their PA member at Gaza's own printed table.
        assertEquals("ps.gaza.cautious", Registry.resolve(Place(31.50161, 34.46672, "Asia/Gaza", "PS")).entry.id)
        // A unit's exception, where one is ever given, carries its reason (ruling R41).
        for (unit in requireNotNull(Units.of("ps.iftaa")).units) {
            for (limit in unit.lateLimits) assertTrue(limit.reason.isNotBlank(), unit.id)
        }
    }

    @Test
    fun `every printed town's day is al-aqsa's day plus the town's own printed minutes (ruling R118)`() {
        // The authority's own construction: a town's times are al-Aqsa's plus its printed figure, one for every
        // time. So wherever al-Aqsa's day is never early (the gate's proof), every town's is, and an end never
        // later: Qalqilya's minute and a half is + 2 on a start (never before the half minute) and + 1 on an end.
        val entry = requireNotNull(Registry.byId("ps.iftaa"))
        val printed = mapOf(
            "Jerusalem" to (0 to 0), "Ramallah" to (0 to 0), "Bethlehem" to (0 to 0), "Jenin" to (0 to 0), "Nablus" to (0 to 0),
            "Jericho" to (-1 to -1), "Hebron" to (1 to 1), "Idhna" to (1 to 1), "Dura" to (1 to 1), "Beit Awwa" to (1 to 1),
            "Tulkarm" to (1 to 1), "Qalqilya" to (2 to 1), "Gaza" to (3 to 3), "Rafah" to (4 to 4), "Khan Yunis" to (4 to 4),
            "Deir al-Balah" to (4 to 4), "Nazareth" to (0 to 0), "Umm al-Fahm" to (0 to 0), "Tiberias" to (-1 to -1),
            "Safed" to (-1 to -1), "Beisan" to (-1 to -1), "Haifa" to (1 to 1), "Acre" to (1 to 1), "Kafr Qasim" to (1 to 1),
            "Tayibe" to (1 to 1), "Lydd" to (2 to 1), "Ramla" to (2 to 1), "Beersheba" to (2 to 2), "Jaffa" to (2 to 2),
        )
        var date = LocalDate(2027, 1, 1)
        while (date.year == 2027) {
            for (town in Levant.paTowns) {
                val zone = zoneOf(town)
                val aqsa = DayPipeline.day(entry, Place(Levant.aqsa.lat, Levant.aqsa.lon, zone, "PS"), date)
                val here = DayPipeline.day(entry, Place(town.point.lat, town.point.lon, zone, "PS"), date)
                val (starts, ends) = requireNotNull(printed[town.name]) { town.name }
                val label = "${town.name} $date"
                assertEquals(aqsa.fajr + starts.minutes, here.fajr, "$label fajr")
                assertEquals(aqsa.dhuhr + starts.minutes, here.dhuhr, "$label dhuhr")
                assertEquals(aqsa.asr + starts.minutes, here.asr, "$label asr")
                assertEquals(aqsa.maghrib + starts.minutes, here.maghrib, "$label maghrib")
                assertEquals(aqsa.isha + starts.minutes, here.isha, "$label isha")
                assertEquals(aqsa.sunrise + ends.minutes, here.sunrise, "$label sunrise")
                assertEquals(aqsa.endOfEating + ends.minutes, here.endOfEating, "$label end of eating")
                assertTrue(here.endOfEating <= here.fajr, "$label end of eating never after Fajr")
            }
            date = date.plus(9, DateTimeUnit.DAY)
        }
    }

    @Test
    fun `every palestinian place takes its nearest printed town's own table, never a neighbour's (ruling R118)`() {
        // The units share one reach, so the unit a place resolves to is the nearest printed town: a place between
        // Tulkarm (+ 1) and Nablus (+ 0), or between Gaza (+ 3) and Deir al-Balah (+ 4), takes the nearer's own, and a
        // place nearer a town of the list in Israel (Lydd's + 1.5 by Ni'lin) takes that town's, never a farther one's.
        val entry = requireNotNull(Registry.byId("ps.iftaa"))
        val reach = requireNotNull(Units.of("ps.iftaa")).units.map { it.radiusKm }.toSet()
        assertEquals(1, reach.size, "one reach for every town")
        var lat = 31.20
        while (lat <= 32.56) {
            var lon = 34.20
            while (lon <= 35.58) {
                val nearest = Levant.paTowns.minBy { distanceKm(GeoPoint(lat, lon), it.point) }
                val zone = if (lon < 34.6) "Asia/Gaza" else "Asia/Hebron"
                val expected = nearest.name.takeIf { distanceKm(GeoPoint(lat, lon), nearest.point) <= reach.single() }
                assertEquals(expected, Registry.resolveEntry(entry, Place(lat, lon, zone, "PS")).unitName, "$lat,$lon")
                lon += 0.02
            }
            lat += 0.02
        }
    }

    private fun zoneOf(town: Levant.PaTown) = when {
        town.country == "IL" -> "Asia/Jerusalem"
        town.point.lon < 34.6 -> "Asia/Gaza"
        else -> "Asia/Hebron"
    }

    @Test
    fun `west bank villages by the green line take the nearest printed town's figure through the app's engine`() {
        // Review r2 of R118: these villages' nearest printed town is one of the list's towns in Israel. Through the
        // app's own pipeline, with the PA's table chosen and on Automatic, each shows that town's table: al-Aqsa's day
        // plus its figure (Lydd's minute and a half as + 2 on a start, + 1 on an end).
        val entry = requireNotNull(Registry.byId("ps.iftaa"))
        val villages = listOf(
            Triple("Ni'lin", GeoPoint(31.951, 35.021), "Lydd"), Triple("Qibya", GeoPoint(31.976, 35.009), "Lydd"),
            Triple("Barta'a ash-Sharqiya", GeoPoint(32.473, 35.087), "Umm al-Fahm"),
            Triple("Bardala", GeoPoint(32.398, 35.553), "Beisan"), Triple("Ein al-Beida", GeoPoint(32.377, 35.548), "Beisan"),
        )
        val chosen = EngineSettings(timetable = TimetableChoice.Entry("ps.iftaa"), timetableConfirmed = true)
        for ((name, point, townName) in villages) {
            val town = Levant.paTowns.single { it.name == townName }
            assertEquals(town, Levant.paTowns.minBy { distanceKm(point, it.point) }, "$name's nearest printed town")
            val place = Place(point.lat, point.lon, "Asia/Hebron", "PS")
            var date = LocalDate(2026, 1, 1)
            while (date.year <= 2031) {
                val viaApp = PrayerEngine.dayTimes(place, date, chosen)
                assertEquals(townName, viaApp.effective.unitName, "$name with the PA's table chosen")
                val automatic = PrayerEngine.dayTimes(place, date, EngineSettings())
                assertEquals(townName, automatic.resolution.unitName, "$name on Automatic")
                val aqsa = DayPipeline.day(entry, Place(Levant.aqsa.lat, Levant.aqsa.lon, "Asia/Hebron", "PS"), date)
                for (shown in listOf(viaApp.day, automatic.day)) {
                    val label = "$name $date"
                    assertEquals(aqsa.fajr + town.startMinutes.minutes, shown.fajr, "$label fajr")
                    assertEquals(aqsa.dhuhr + town.startMinutes.minutes, shown.dhuhr, "$label dhuhr")
                    assertEquals(aqsa.asr + town.startMinutes.minutes, shown.asr, "$label asr")
                    assertEquals(aqsa.maghrib + town.startMinutes.minutes, shown.maghrib, "$label maghrib")
                    assertEquals(aqsa.isha + town.startMinutes.minutes, shown.isha, "$label isha")
                    assertEquals(aqsa.sunrise + town.endMinutes.minutes, shown.sunrise, "$label sunrise")
                    assertEquals(aqsa.endOfEating + town.endMinutes.minutes, shown.endOfEating, "$label end of eating")
                }
                date = date.plus(29, DateTimeUnit.DAY)
            }
        }
    }

    @Test
    fun `a town's unprinted ends are never after the same method's ends at the place's own point`() {
        // Review r2 of R118: a town's printed times come from al-Aqsa's table alone, but the ends the PA never prints
        // (Asr's at the sunset, Maghrib's at the red twilight) stay bounded by the user's own sky (spec §3.5).
        val entry = requireNotNull(Registry.byId("ps.iftaa"))
        val points = Levant.paTowns.filter { it.country == "PS" }.map { it.point to zoneOf(it) } + listOf(
            GeoPoint(31.86, 35.50) to "Asia/Hebron", GeoPoint(32.40, 35.40) to "Asia/Hebron",
            GeoPoint(32.30, 35.37) to "Asia/Hebron", GeoPoint(31.40, 35.10) to "Asia/Hebron",
            GeoPoint(31.25, 34.25) to "Asia/Gaza", GeoPoint(32.47, 35.09) to "Asia/Hebron",
        )
        for ((point, zone) in points) {
            var date = LocalDate(2026, 1, 1)
            while (date.year == 2026) {
                val shown = DayPipeline.day(entry, Place(point.lat, point.lon, zone, "PS"), date)
                val own = DayComputer.compute(Levant.paMethod, point, date, TimeZone.of(zone), AsrSchool.STANDARD, Registry.ramadanCalendar())
                for (prayer in listOf(Prayer.ASR, Prayer.MAGHRIB)) {
                    val end = requireNotNull(shown.ends[prayer]) { "$point $date $prayer" }
                    assertTrue(end <= requireNotNull(own.ends[prayer]), "$point $date: $prayer's end after the place's own")
                }
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    @Test
    fun `gaza's cautious entry ranks its ministry of awqaf over the pa`() {
        val gaza = requireNotNull(Registry.byId("ps.gaza.cautious"))
        assertEquals(EntryClass.C, gaza.entryClass)
        assertEquals(listOf("ps.gaza.awqaf", "ps.iftaa"), gaza.members.sortedBy { it.shareRank }.map { it.id })
    }

    @Test
    fun `lebanon syria and iraq stay class D authority with jordan and palestine's margins never applied to them`() {
        // Every D_AUTHORITY entry here is checked on a thin sample, not a whole area's tables: measured
        // is false throughout, consistently (none of them claims an "at most" figure).
        for (id in listOf("lb.fatwa", "sy.awqaf", "iq.sunni", "ps.gaza.awqaf")) {
            val entry = requireNotNull(Registry.byId(id)) { id }
            assertEquals(EntryClass.D_AUTHORITY, entry.entryClass, id)
            assertTrue(!entry.measured, id)
        }
        assertOrdered("lb.fatwa", day("lb.fatwa", 33.894, 35.502, "Asia/Beirut", LocalDate(2026, 3, 20)))
        assertOrdered("sy.awqaf", day("sy.awqaf", 33.5138, 36.2765, "Asia/Damascus", LocalDate(2026, 9, 26)))
        assertOrdered("iq.sunni", day("iq.sunni", 33.341, 44.401, "Asia/Baghdad", LocalDate(2026, 9, 26)))
    }

    @Test
    fun `lebanon's late-limit exceptions cover exactly fajr and the two evening events`() {
        val entry = requireNotNull(Registry.byId("lb.fatwa"))
        val covered = entry.lateLimits.flatMap { it.events }.toSet()
        assertEquals(setOf(TimedEvent.FAJR, TimedEvent.MAGHRIB, TimedEvent.ISHA), covered)
        for (limit in entry.lateLimits) assertTrue(limit.reason.isNotBlank(), limit.events.toString())
    }

    @Test
    fun `yemen has no authority proven and stays calculated`() {
        val entry = requireNotNull(Registry.byId("ye.default"))
        assertEquals(EntryClass.D_NONE, entry.entryClass)
        assertEquals(AboutTemplate.CALCULATED, entry.about)
    }
}
