package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Bangladesh
import world.taqwa.app.prayer.engine.registry.authorities.SouthAsia
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Bangladesh lane of 13 Nov 2026 (rulings R220–R224): the Islamic Foundation's 64 districts as units. Its pages are
 * restricted (the private archive), so no printed time is here: these tests pin the rules the gate checked (the units,
 * the hold for a district's page and for its town under the 1993 rule, the kind) and the Foundation's own two district
 * rules as the engine's sun gives them, on days the Foundation has not printed yet.
 */
class BangladeshDistrictsTest {

    private val zone = TimeZone.of("Asia/Dhaka")
    private val entry = assertNotNull(Registry.byId("bd.ifb"))

    private fun place(at: GeoPoint) = Place(at.lat, at.lon, "Asia/Dhaka", "BD")

    /** The Sundarbans, more than 20 km from every district town. */
    private val sundarbans = GeoPoint(22.0, 89.5)

    /** Each district's page edges at its town's latitude, the town itself, and the middle of the page. */
    private fun across(d: Bangladesh.District): List<GeoPoint> =
        listOf(d.west, d.town.lon, (d.west + d.east) / 2, d.east).map { GeoPoint(d.town.lat, it) }

    /** A quarter of a degree north and south of the town's latitude, at both edges and at the town's longitude. */
    private fun offLatitude(d: Bangladesh.District): List<GeoPoint> =
        listOf(-OFF_LATITUDE, OFF_LATITUDE).flatMap { by -> listOf(d.west, d.town.lon, d.east).map { GeoPoint(d.town.lat + by, it) } }

    /** Just past the fitted east edge, where the fit may have fallen short of the district. */
    private fun pastEast(d: Bangladesh.District) = GeoPoint(d.town.lat, d.east + PAST_EAST)

    /** Ramadan 1448 as the calendars expect it (8 Feb to 9 Mar 2027) every third day, and a day of every month. */
    private val days: List<LocalDate> =
        generateSequence(LocalDate(2027, 2, 8)) { it.plus(3, DateTimeUnit.DAY) }.takeWhile { it <= LocalDate(2027, 3, 9) }.toList() +
            (1..12).map { LocalDate(2027, it, 21) }

    private val ramadan = days.filter { it <= LocalDate(2027, 3, 9) }

    @Test
    fun `the foundation's 64 districts are units at their towns and every place is its published method`() {
        val units = assertNotNull(Units.of("bd.ifb"))
        assertEquals(64, units.units.size)
        assertEquals(64, units.units.map { it.id }.toSet().size)
        assertEquals(64, Bangladesh.districts.size)
        assertEquals(AsrSchool.HANAFI, entry.school)
        for (d in Bangladesh.districts) {
            val unit = units.unit(d.id)
            assertEquals(d.town, unit.point, d.id)
            assertEquals(20.0, unit.radiusKm, d.id)
            // No kind of its own (R224): a district's page is held for Ramadan only, so a unit is no checked timetable.
            assertNull(unit.entryClass, d.id)
            assertTrue(unit.lateLimits.isEmpty(), d.id)
            assertNull(unit.method, d.id)
            // The town lies on its own page, edge to edge.
            assertTrue(d.west < d.town.lon && d.town.lon < d.east, d.id)
            val r = Registry.resolve(place(d.town))
            assertEquals("bd.ifb", r.entry.id, d.id)
            assertEquals(d.id, r.unitId, d.id)
            assertEquals(d.name, r.unitName, d.id)
            assertEquals(EntryClass.D_AUTHORITY, r.entryClass, d.id)
            assertTrue(r.measured, d.id)
            // A page is printed for its district's edges, never for its town: the unit carries no point of its own.
            assertNull(assertNotNull(r.method).fixedPoint, d.id)
        }
        assertEquals("coxs-bazar", units.units.first { it.name == "Cox's Bazar" }.id)
        assertEquals("chapai-nawabganj", units.units.first { it.name == "Chapai Nawabganj" }.id)
        val beyond = Registry.resolve(place(sundarbans))
        assertEquals("bd.ifb", beyond.entry.id)
        assertNull(beyond.unitId)
        assertEquals(EntryClass.D_AUTHORITY, beyond.entryClass)
        assertTrue(Bangladesh.districts.all { distanceKm(sundarbans, it.town) > 20.0 })
    }

    @Test
    fun `the hold only ever moves fajr and maghrib later and the end of eating earlier`() {
        val base = SouthAsia.bangladeshMethod
        for (at in Bangladesh.districts.flatMap { across(it) + offLatitude(it) + pastEast(it) } + sundarbans) {
            val held = Bangladesh.held(base, at)
            val where = "${at.lat},${at.lon}"
            assertTrue(held.margins.fajr >= base.margins.fajr, where)
            assertTrue(held.margins.maghrib >= base.margins.maghrib, where)
            assertTrue(held.endOfEatingMarginSeconds <= base.endOfEatingMarginSeconds, where)
            assertEquals(base.margins.copy(fajr = held.margins.fajr, maghrib = held.margins.maghrib), held.margins, where)
            assertEquals(base.authorityMinutes, held.authorityMinutes, where)
            // What a place is given is this hold, in a unit or beyond every unit.
            val shown = assertNotNull(Registry.resolve(place(at)).method)
            assertEquals(held.margins, shown.margins, where)
            assertEquals(held.endOfEatingMarginSeconds, shown.endOfEatingMarginSeconds, where)
        }
    }

    @Test
    fun `every place on a district's page waits for its west edge at dawn and sunset and stops eating by its east edge`() {
        // Four minutes of time a degree of longitude, and half a minute to spare for the latitudes of the edge.
        fun seconds(degrees: Double) = ceil(degrees * 240.0).toInt() + 30
        for (d in Bangladesh.districts) {
            for (at in across(d) + offLatitude(d) + pastEast(d)) {
                val method = assertNotNull(Registry.resolve(place(at)).method)
                val where = "${d.id} at ${at.lat},${at.lon}"
                assertTrue(method.authorityMinutes.fajr * 60 + method.margins.fajr >= seconds(at.lon - d.west), "fajr, $where")
                assertTrue(method.authorityMinutes.maghrib * 60 + method.margins.maghrib >= seconds(at.lon - d.west), "maghrib, $where")
                assertTrue(-method.endOfEatingMarginSeconds >= seconds(d.east - at.lon), "end of eating, $where")
            }
        }
        // The 1993 rule's own minutes already hold iftar and the end of sehri across the widest page.
        val base = SouthAsia.bangladeshMethod
        val widest = Bangladesh.districts.maxOf { it.east - it.west }
        assertTrue(base.authorityMinutes.maghrib * 60 + base.margins.maghrib >= seconds(widest))
        assertTrue(-base.endOfEatingMarginSeconds >= seconds(widest))
    }

    @Test
    fun `under the 1993 rule every place on a district's page waits for its town at dawn and sunset and stops eating by its town`() {
        // A district's day under the 1993 rule is its town's: the method's own minutes at the town, and on top of them
        // the seconds dawn and sunset take from the place to the town, or the town's dawn to the place.
        fun seconds(degrees: Double) = ceil(degrees * 240.0).toInt()
        val base = SouthAsia.bangladeshMethod
        for (d in Bangladesh.districts) {
            for (at in across(d) + offLatitude(d)) {
                val method = assertNotNull(Registry.resolve(place(at)).method)
                val where = "${d.id} at ${at.lat},${at.lon}"
                assertTrue(method.margins.fajr - base.margins.fajr >= seconds(at.lon - d.town.lon), "fajr, $where")
                assertTrue(method.margins.maghrib - base.margins.maghrib >= seconds(at.lon - d.town.lon), "maghrib, $where")
                assertTrue(base.endOfEatingMarginSeconds - method.endOfEatingMarginSeconds >= seconds(d.town.lon - at.lon), "end of eating, $where")
            }
        }
        // The town's own day, as the engine's sun gives it, is never later than the day shown anywhere on its page.
        for (d in Bangladesh.districts) {
            for (date in days) {
                val town = DayComputer.compute(base, d.town, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                for (at in across(d)) {
                    val shown = DayPipeline.unended(Registry.resolve(place(at)), date, zone)
                    val where = "${d.id} at ${at.lon} on $date"
                    assertTrue(shown.fajr >= town.fajr, "fajr, $where")
                    assertTrue(shown.maghrib >= town.maghrib, "maghrib, $where")
                    assertTrue(shown.endOfEating <= town.endOfEating, "end of eating, $where")
                }
            }
        }
    }

    @Test
    fun `the foundation's district rule holds on ramadan 1448 and across 2027 before it is printed`() {
        // The Foundation's rule since Ramadan 1446 (2025): the Fajr azan is the 18° dawn at the district's west edge
        // and iftar the sunset there, each made a full minute; sehri ends at the dawn of its east edge, seconds dropped.
        val exact = SouthAsia.bangladeshMethod.copy(authorityMinutes = EventOffsets(), margins = EventOffsets(), endOfEatingMarginSeconds = 0)
        val calendar = Registry.ramadanCalendarFor(entry)
        for (d in Bangladesh.districts) {
            for (date in days) {
                val west = DayComputer.compute(exact, GeoPoint(d.town.lat, d.west), date, zone, entry.school, calendar)
                val east = DayComputer.compute(exact, GeoPoint(d.town.lat, d.east), date, zone, entry.school, calendar)
                // Off the town's latitude only in Ramadan: a page is printed for Ramadan, and the hold's spare for the
                // latitude is a Ramadan one.
                for (at in across(d) + pastEast(d) + if (date in ramadan) offLatitude(d) else emptyList()) {
                    val shown = DayPipeline.unended(Registry.resolve(place(at)), date, zone)
                    val where = "${d.id} at ${at.lat},${at.lon} on $date"
                    assertTrue(shown.fajr >= west.fajr, "fajr, $where")
                    assertTrue(shown.maghrib >= west.maghrib, "maghrib, $where")
                    assertTrue(shown.endOfEating <= east.endOfEating, "end of eating, $where")
                }
            }
        }
    }

    @Test
    fun `a place beyond every unit is held to the pages that may be its own`() {
        // Between Sylhet's and Moulvibazar's towns, more than 20 km from both: both pages may hold it.
        val between = GeoPoint(24.68, 91.95)
        assertNull(Registry.resolve(place(between)).unitId)
        val near = Bangladesh.districts.filter { it.id == "sylhet" || it.id == "moulvibazar" }
        assertTrue(near.size == 2 && near.all { between.lon in it.west..it.east && distanceKm(between, it.town) > 20.0 })
        val method = assertNotNull(Registry.resolve(place(between)).method)
        val west = near.minOf { it.west }
        val east = near.maxOf { it.east }
        assertTrue(method.authorityMinutes.fajr * 60 + method.margins.fajr >= ceil((between.lon - west) * 240.0).toInt() + 30)
        assertTrue(-method.endOfEatingMarginSeconds >= ceil((east - between.lon) * 240.0).toInt() + 30)
        // Far from every page (the sea south of the coast), the method as it is.
        assertEquals(SouthAsia.bangladeshMethod.margins, Bangladesh.held(SouthAsia.bangladeshMethod, GeoPoint(18.0, 90.0)).margins)
    }

    private companion object {
        const val OFF_LATITUDE = 0.25
        const val PAST_EAST = 0.10
    }
}
