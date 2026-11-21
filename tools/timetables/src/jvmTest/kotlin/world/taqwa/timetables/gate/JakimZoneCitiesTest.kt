package world.taqwa.timetables.gate

import world.taqwa.timetables.units.EnvelopeRows
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.JakimZoneCities
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import world.taqwa.timetables.TestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import world.taqwa.timetables.units.ReachAreas

/**
 * JAKIM's zones and the app's Malaysian cities (the zone round of 13 Nov 2026, rulings R225–R226): a city is one of a
 * zone's towns only as the app's city list holds it, each town is its zone's unit and nowhere else's, every table a
 * zone is read against at its unit is read at each of its towns too, and the monitor re-reads every zone and grows
 * every town's rows. Rules only: no printed time is held here, and nothing is read from the archive.
 */
class JakimZoneCitiesTest {

    private class City(val id: Int, val name: String, val lat: Double, val lon: Double, val tz: String)

    private val malaysia: Map<Int, City> = TestPaths.appFiles.resolve("cities.csv").readLines().drop(1)
        .map { it.split(',') }.filter { it[4] == "MY" }
        .associate { it[0].toInt() to City(it[0].toInt(), it[1], it[5].toDouble(), it[6].toDouble(), it[7]) }

    private val towns = JakimZoneCities.byZone.flatMap { (zone, cities) -> cities.map { zone to it } }

    private val gateDir = TestPaths.repoRoot.resolve("tools/timetables/official/gate")

    @Test
    fun `every town of a zone is a malaysian city of the app's list at its own point and in one zone only`() {
        val units = Units.of("my.jakim")!!.units.map { it.id }.toSet()
        assertEquals(JakimZones.zones.map { it.id }.toSet(), units)
        assertEquals(units.size, JakimZones.zones.size, "no zone twice")
        for ((zone, town) in towns) {
            assertTrue(zone in units, "$zone is not a unit")
            val city = assertNotNull(malaysia[town.id], "$zone: ${town.id} is not a Malaysian city of cities.csv")
            assertEquals(city.lat, town.lat, "${city.name} lat")
            assertEquals(city.lon, town.lon, "${city.name} lon")
        }
        assertEquals(towns.size, towns.map { it.second.id }.toSet().size, "a city is a town of one zone only")
    }

    @Test
    fun `every malaysian city is a zone's town or named as unattached`() {
        val attached = towns.map { it.second.id }.toSet()
        assertTrue(JakimZoneCities.unattached.none { it in attached }, "an unattached city is on no zone's list")
        assertTrue(JakimZoneCities.unattached.all { it in malaysia }, "an unattached city is a Malaysian city of the list")
        val neither = malaysia.values.filter { it.id !in attached && it.id !in JakimZoneCities.unattached }
        assertTrue(neither.isEmpty(), "neither a town nor named unattached: ${neither.map { "${it.id} ${it.name}" }}")
    }

    @Test
    fun `every town resolves to its own zone as a checked timetable`() {
        for ((zone, town) in towns) {
            val city = malaysia.getValue(town.id)
            val r = Registry.resolve(Place(city.lat, city.lon, city.tz, "MY"))
            assertEquals("my.jakim", r.entry.id, city.name)
            assertEquals(zone, r.unitId, city.name)
            assertTrue(r.measured, city.name)
            assertEquals(EntryClass.B, r.entryClass, city.name)
        }
    }

    @Test
    fun `an unattached city is never given a zone's table as its own without one`() {
        // No zone's list names it: it takes the zone of a listed town within reach (measured there by that zone's own
        // table and its neighbours', ruling R226), else the safe edge, which claims no checked timetable.
        for (id in JakimZoneCities.unattached) {
            val city = malaysia.getValue(id)
            val r = Registry.resolve(Place(city.lat, city.lon, city.tz, "MY"))
            assertEquals("my.jakim", r.entry.id, city.name)
            if (r.unitId == null) {
                assertFalse(r.measured, city.name)
                assertEquals(EntryClass.D_AUTHORITY, r.entryClass, city.name)
            }
        }
    }

    @Test
    fun `every table a zone is read against at its unit is read at each of its towns`() {
        val manifest = GateManifest.load(gateDir)
        val atUnit = manifest.only(setOf("my-jakim")).rows.filter { it.entry == "my.jakim" && it.lat == null }
        val atTowns = manifest.only(setOf("my-jakim-cities")).rows
        assertTrue(atTowns.all { it.entry == "my.jakim" && it.split == Split.TEST && it.lat != null }, "the town rows are all split test, at a point")
        val held = atTowns.map { Triple(it.path, it.unit, it.lat to it.lon) }.toSet()
        assertEquals(atTowns.size, held.size, "no town row twice")
        var expected = 0
        for (zone in JakimZones.zones) {
            val rows = atUnit.filter { it.unit == zone.id }
            assertTrue(rows.any { it.split == Split.FIT } && rows.any { it.split == Split.TEST }, "${zone.id}: a fit row and a test row at its unit")
            for (town in JakimZoneCities.byZone[zone.id].orEmpty()) {
                if (town.lat == zone.point.lat && town.lon == zone.point.lon) continue   // the unit's own point: read in my-jakim.tsv
                for (row in rows) {
                    expected++
                    assertTrue(Triple(row.path, row.unit, town.lat to town.lon) in held, "${zone.id}: ${row.path} is not read at town ${town.id}")
                }
            }
        }
        // The second trust audit's G28 (ruling R274): beside the towns, the places of the app no zone's list holds read the
        // zone they take, every table of it, at their own points, and only that zone.
        val listed = JakimZones.zones.flatMap { z -> JakimZoneCities.byZone[z.id].orEmpty().map { it.lat to it.lon } }.toSet()
        val taken = atTowns.filter { (it.lat to it.lon) !in listed }
        for ((at, rows) in taken.groupBy { it.lat!! to it.lon!! }) {
            val zone = EnvelopeRows.jakimAt(GeoPoint(at.first, at.second), rows.first().zone).taken
            assertTrue(zone != null && rows.all { it.unit == zone.id }, "$at: its rows are not the zone it takes")
            assertEquals(atUnit.filter { it.unit == zone!!.id }.map { it.path }.toSet(), rows.map { it.path }.toSet(), "$at: not every table of ${zone!!.id}")
        }
        assertEquals(expected, atTowns.size - taken.size, "no town row beyond the zones' towns and tables")
    }

    @Test
    fun `the monitor re-reads every zone and grows every town's rows`() {
        val fetcher = TestPaths.repoRoot.resolve("tools/timetables/monitor/fetchers/jakim.py").readText()
        val listed = Regex("""\("([A-Z]{3}\d\d)", "[^"]+", "Asia/(Kuala_Lumpur|Kuching)"\)""")
            .findAll(fetcher.substringAfter("\nZONES = [").substringBefore("\n]\n")).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(JakimZones.zones.map { it.id }.sorted(), listed.map { it.first }.sorted(), "the fetcher's ZONES are the units, each once")
        for ((zone, tz) in listed) {
            assertEquals(zone.startsWith("SBH") || zone.startsWith("SWK") || zone == "WLY02", tz == "Kuching", "$zone: its clock")
        }
        val recipes = TestPaths.repoRoot.resolve("tools/timetables/official/monitor/recipes.tsv").readLines()
            .filter { !it.startsWith("#") }.map { it.split('\t') }.filter { it[0] == "my-jakim" }
        assertTrue(recipes.any { it.subList(1, 6) == listOf("*", "my-jakim.tsv", "index", "-", "index") }, "every capture at its zone's unit")
        // And the printed imsak kept beside each table (ruling R284): its own line at the units and at each zone's towns.
        assertTrue(recipes.any { it.subList(1, 6) == listOf("*-imsak", "my-jakim.tsv", "index", "-", "index") }, "every imsak capture at its zone's unit")
        val withTowns = GateManifest.load(gateDir).only(setOf("my-jakim-cities")).rows.mapNotNull { it.unit }.toSet()
        for (zone in withTowns) {
            assertTrue(
                recipes.any { it.subList(1, 6) == listOf("$zone-2*", "my-jakim-cities.tsv", "my.jakim/$zone", "-", "all") },
                "$zone: no recipe grows its towns' rows",
            )
            assertTrue(
                recipes.any { it.subList(1, 6) == listOf("$zone-2*-imsak", "my-jakim-cities.tsv", "my.jakim/$zone", "-", "all") },
                "$zone: no recipe grows its towns' imsak rows",
            )
        }
        // The reach files' own lines (my-jakim-reach.tsv, ruling R270, and the zones' area file, R310) are CityUnitsTest's to hold.
        assertEquals(
            2 * (withTowns.size + 1), recipes.count { it[2] != "my-jakim-reach.tsv" && it[2] != ReachAreas.JAKIM_FILE },
            "a line for the units and one for each zone with towns, each for the day's table and its imsak, no more",
        )
    }
}
