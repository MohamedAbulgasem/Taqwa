package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rulings R44 and R45: tables printed for one point, near their point and beyond its reach. */
class PointTableTest {
    private val dates = (1..12).flatMap { m -> listOf(LocalDate(2026, m, 1), LocalDate(2026, m, 15)) }

    private fun at(entryId: String, lat: Double, lon: Double, zone: String) =
        Registry.resolveEntry(Registry.byId(entryId)!!, Place(lat, lon, zone, "XX"))

    private fun day(method: TimetableMethod, point: GeoPoint, date: LocalDate, zone: String, school: AsrSchool = AsrSchool.STANDARD): PrayerDay =
        DayComputer.compute(method, point, date, TimeZone.of(zone), school, Registry.ramadanCalendar())

    @Test
    fun `a user near a printed table carries its point both ways`() {
        class Case(val entry: String, val lat: Double, val lon: Double, val zone: String, val unit: String, val table: GeoPoint)
        val cases = listOf(
            Case("tr.diyanet.europe", 48.80359, 2.13424, "Europe/Paris", "Paris", GeoPoint(48.85341, 2.3488)), // Versailles
            Case("tr.diyanet.europe", 52.5511, 13.19921, "Europe/Berlin", "Berlin", GeoPoint(52.52437, 13.41053)), // Spandau
            Case("be.emb", 50.83619, 4.31454, "Europe/Brussels", "Brussels", GeoPoint(50.8467, 4.3525)), // Anderlecht
            Case("no.irn", 59.8910, 10.5229, "Europe/Oslo", "Oslo", GeoPoint(59.91273, 10.74609)), // Sandvika
            Case("no.irn", 59.9127, 10.6353, "Europe/Oslo", "Oslo", GeoPoint(59.91273, 10.74609)), // Lysaker
            Case("fr.gmp", 48.96014, 2.87885, "Europe/Paris", "Paris", GeoPoint(48.8418, 2.3556)), // Meaux
            Case("fr.gmp", 48.85064, 2.65078, "Europe/Paris", "Paris", GeoPoint(48.8418, 2.3556)), // Torcy, Marne-la-Vallée
        )
        for (c in cases) {
            val r = at(c.entry, c.lat, c.lon, c.zone)
            assertEquals(c.unit, r.unitName, "${c.entry} ${c.lat}")
            val method = r.method!!
            assertEquals(c.table, method.fixedPoint)
            assertEquals(FixedPointMode.BOTH, method.fixedPointMode)
            // Against the table's own point, on the same method: never an earlier start, never a later end.
            for (date in dates) {
                val shown = day(method, r.point, date, c.zone)
                val table = day(method, c.table, date, c.zone)
                listOf(shown.fajr to table.fajr, shown.dhuhr to table.dhuhr, shown.asr to table.asr, shown.maghrib to table.maghrib, shown.isha to table.isha)
                    .forEach { (s, t) -> assertTrue(s >= t, "${c.entry} ${c.lat} $date") }
                assertTrue(shown.sunrise <= table.sunrise && shown.endOfEating <= table.endOfEating, "${c.entry} ${c.lat} $date")
            }
        }
    }

    @Test
    fun `irn's three calendars each carry their own end curve and margin`() {
        val oslo = at("no.irn", 59.91273, 10.74609, "Europe/Oslo")
        val trondheim = at("no.irn", 63.43049, 10.39506, "Europe/Oslo")
        val tromso = at("no.irn", 69.6489, 18.95508, "Europe/Oslo")
        assertEquals(listOf("Oslo", "Trondheim", "Tromsø"), listOf(oslo, trondheim, tromso).map { it.unitName })
        val curves = listOf(oslo, trondheim, tromso).map { (it.method!!.endOfEating as EndOfEating.DawnAngle).bySlot!!.toList() }
        assertNotEquals(curves[0], curves[1])
        assertNotEquals(curves[1], curves[2])
        // Tromsø's winter Fajr is deeper than 18°.
        assertTrue(curves[2][10] > 18.0, "Tromsø 11 Jan ${curves[2][10]}")
        // Ruling R82: Tromsø's calendar alone carries IRN's Makkah-time rule, and is measured under it.
        assertEquals(listOf(false, false, true), listOf(oslo, trondheim, tromso).map { it.method!!.clockRule != null })
        assertTrue(tromso.measured)
    }

    @Test
    fun `a fitted end margin applies at its table's point only`() {
        // Beyond the reach: SAFE_END or lower, whatever the table's fitted margin (IRN +55, GMP +20, Diyanet +10).
        for ((id, lat, lon) in listOf(Triple("no.irn", 58.97005, 5.73332), Triple("fr.gmp", 47.21806, -1.55278), Triple("tr.diyanet.europe", 53.55073, 9.99302))) {
            val r = at(id, lat, lon, "Europe/Oslo")
            assertNull(r.unitName, id)
            assertTrue(r.method!!.endOfEatingMarginSeconds <= SAFE_END, "$id: ${r.method!!.endOfEatingMarginSeconds}")
        }
        // Habous's edge moves its end like its sunrise (3 km west of Imilchil).
        val imilchil = Registry.resolve(Place(32.155, -5.666, "Africa/Casablanca", "MA"))
        assertNull(imilchil.unitName)
        assertEquals(-40, imilchil.method!!.endOfEatingMarginSeconds)
    }

    @Test
    fun `beyond its reach a table's point still bounds the ends and not the starts`() {
        // Ruling R45: Milton is just beyond IFT's reach (60.3 km of 60.2).
        val ift = GeoPoint(43.7980, -79.2417)
        val milton = GeoPoint(43.51681, -79.88294)
        val r = at("ca.ift", milton.lat, milton.lon, "America/Toronto")
        assertNull(r.unitName)
        val method = r.method!!
        assertEquals(ift, method.fixedPoint)
        assertEquals(FixedPointMode.ENDS_ONLY, method.fixedPointMode)
        val iftMethod = at("ca.ift", ift.lat, ift.lon, "America/Toronto").method!!
        for (date in dates) {
            val shown = day(method, milton, date, "America/Toronto", AsrSchool.HANAFI)
            val table = day(iftMethod, ift, date, "America/Toronto", AsrSchool.HANAFI)
            assertTrue(shown.sunrise <= table.sunrise && shown.endOfEating <= table.endOfEating, "$date")
            val own = day(method.copy(fixedPoint = null), milton, date, "America/Toronto", AsrSchool.HANAFI)
            assertEquals(own.fajr, shown.fajr, "$date: starts at the user's point")
            assertEquals(own.maghrib, shown.maghrib, "$date")
        }
        // Three reaches away the table no longer bounds anything (Vancouver).
        assertNull(at("ca.ift", 49.2827, -123.1207, "America/Vancouver").method!!.fixedPoint)
    }

    /**
     * Ruling R89: within a point table's reach the sunrise, end of eating and imsak shown at the user's point are
     * never after those the table's point itself shows, after every step the day applies to an end (the end of
     * eating held to the Fajr shown, the rate limit, the clock rule, the repair). Before it Charleroi's end of
     * eating came a minute after Brussels's own on 8 days of 2027–2029: EMB's eating dawn at Brussels a minute past
     * its own Fajr, held to it there and not at Charleroi, whose Fajr shown is the later of the two points'.
     */
    @Test
    fun `within a table's reach the ends shown are never after the table point's own`() {
        class Case(val entry: String, val table: GeoPoint, val zone: String, val cc: String, val near: List<Pair<String, GeoPoint>>)
        val cases = listOf(
            Case(
                "be.emb", GeoPoint(50.8467, 4.3525), "Europe/Brussels", "BE",
                listOf(
                    "Charleroi" to GeoPoint(50.4108, 4.4446), "Antwerp" to GeoPoint(51.2194, 4.4025), "Leuven" to GeoPoint(50.8798, 4.7005),
                    "Aalst" to GeoPoint(50.9378, 4.0402), "Mechelen" to GeoPoint(51.0259, 4.4776),
                ),
            ),
            Case(
                "ch.fids", GeoPoint(47.36667, 8.55), "Europe/Zurich", "CH",
                listOf(
                    "Winterthur" to GeoPoint(47.4988, 8.7237), "Baden" to GeoPoint(47.4733, 8.3059), "Zug" to GeoPoint(47.1662, 8.5155),
                    "Rapperswil" to GeoPoint(47.2267, 8.8184), "Aarau" to GeoPoint(47.3911, 8.0446),
                ),
            ),
            Case(
                "no.irn", GeoPoint(59.91273, 10.74609), "Europe/Oslo", "NO",
                listOf(
                    "Sandvika" to GeoPoint(59.8910, 10.5229), "Lillestrøm" to GeoPoint(59.9560, 11.0490), "Drøbak" to GeoPoint(59.6633, 10.6303),
                    "Ski" to GeoPoint(59.7195, 10.8358),
                ),
            ),
            // Tromsø's calendar under IRN's Makkah-time rule (ruling R82): on the mornings its Fajr is declared not
            // followed (after the sun has risen) the fast ends at the Fajr shown, the unit's own dawn at the table's
            // point and the later of the two points' dawns at the user's, so the user's end of eating was 3–4 min
            // after the table's own (a golden point south-west of the city, 2027–2028).
            Case(
                "no.irn", GeoPoint(69.6489, 18.95508), "Europe/Oslo", "NO",
                listOf("Kvaløya" to GeoPoint(69.597, 18.685), "Tromsdalen" to GeoPoint(69.6450, 19.0000), "Kvaløysletta" to GeoPoint(69.6841, 18.8262)),
            ),
        )
        for (c in cases) {
            val zone = TimeZone.of(c.zone)
            val entry = Registry.byId(c.entry)!!
            val atTable = Registry.resolveEntry(entry, Place(c.table.lat, c.table.lon, c.zone, c.cc))
            assertNotNull(atTable.unitName, c.entry)
            val own = HashMap<LocalDate, PrayerDay>()
            for ((name, point) in c.near) {
                val r = Registry.resolveEntry(entry, Place(point.lat, point.lon, c.zone, c.cc))
                assertEquals(atTable.unitName, r.unitName, "$name within the ${c.entry} table's reach")
                assertEquals(c.table, r.method!!.fixedPoint, name)
                assertEquals(FixedPointMode.BOTH, r.method!!.fixedPointMode, name)
                var date = LocalDate(2026, 1, 1)
                while (date.year <= 2030) {
                    val shown = DayPipeline.unended(r, date, zone)
                    val table = own.getOrPut(date) { DayPipeline.unended(atTable, date, zone) }
                    assertTrue(shown.endOfEating <= table.endOfEating, "$name $date: the end of eating ${shown.endOfEating - table.endOfEating} after the table's")
                    assertTrue(shown.sunrise <= table.sunrise, "$name $date: sunrise ${shown.sunrise - table.sunrise} after the table's")
                    table.imsak?.let { assertTrue(shown.imsak!! <= it, "$name $date: imsak ${shown.imsak!! - it} after the table's") }
                    date = date.plus(1, DateTimeUnit.DAY)
                }
            }
        }
    }

    /**
     * Ruling R88 (R87's edge hook reverted with Belgium's Maghrib): beyond its table's reach a cautious member
     * built from its entry's method with a change of its own carries the entry's own edge, not one built on
     * its change. Cape Town's MJC member (Maghrib margin 110 s within the reach) is that member: in the NE and
     * SE corners of the Cape Town box, beyond the MJC table's class-D reach, its edge is the MJC's own again,
     * − 10 + 60 s, so za.cape's Maghrib there is what it was before R87.
     */
    @Test
    fun `beyond its table's reach a cautious member carries its entry's own edge`() {
        val capeTown = GeoPoint(-33.92584, 18.42322)
        val zone = TimeZone.of("Africa/Johannesburg")
        val inside = Registry.resolve(Place(capeTown.lat, capeTown.lon, zone.id, "ZA")).members.single { it.id == "za.mjc" }.method
        assertEquals(110, inside.margins.maghrib)
        val mjc = Registry.byId("za.mjc")!!
        for ((lat, lon) in listOf(-33.47 to 19.08, -34.38 to 19.08)) {
            val corner = Place(lat, lon, zone.id, "ZA")
            val r = Registry.resolve(corner)
            assertEquals("za.cape", r.entry.id)
            assertTrue(distanceKm(capeTown, GeoPoint(lat, lon)) > lateReachKm(capeTown.lat, EntryClass.D_AUTHORITY), "$lat $lon")
            val member = r.members.single { it.id == "za.mjc" }.method
            assertEquals("za.mjc.edge", member.id)
            assertEquals(50, member.margins.maghrib)
            assertEquals(Registry.resolveEntry(mjc, corner).method, member, "$lat $lon: the MJC's own edge")
            val date = LocalDate(2026, 6, 21)
            assertEquals(
                DayPipeline.day(Registry.resolveEntry(mjc, corner), date, zone).maghrib,
                DayPipeline.members(r, date, zone)[r.members.indexOfFirst { it.id == "za.mjc" }].maghrib,
                "$lat $lon: the member's Maghrib is the MJC entry's own there",
            )
        }
    }
}
