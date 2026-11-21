package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.KyrgyzLocalities
import world.taqwa.timetables.TestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Kyrgyz Muftiate's localities as kg.default's units (12 Nov 2026), as CityUnitsTest holds QMDB's: every locality
 * the monitor fetches (`fetchers/muftiyat_kg.py`'s POINTS) is a unit at the same point and every unit but Aravan town
 * is fetched, kg-default.tsv reads each locality's 2026 and 2027 tables at its unit's point, and each of the app's
 * Kyrgyz cities is inside a unit and read there against that unit's tables.
 */
class KyrgyzUnitsTest {

    /** The fetcher's POINTS: (id, key, lat, lon), the coordinates as the file writes them. */
    private val fetched: List<Pair<String, GeoPoint>> by lazy {
        val text = TestPaths.repoRoot.resolve("tools/timetables/monitor/fetchers/muftiyat_kg.py").readText()
        val block = text.substringAfter("POINTS = [").substringBefore("\n]")
        Regex("""\(\s*\d+,\s*"([^"]+)",\s*"[^"]*",\s*"(-?[0-9.]+)",\s*"(-?[0-9.]+)"\)""").findAll(block)
            .map { it.groupValues[1] to GeoPoint(it.groupValues[2].toDouble(), it.groupValues[3].toDouble()) }.toList()
    }

    private val rows: List<GateRow> by lazy {
        GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")).only(groups = setOf("kg-default")).rows
    }

    @Test
    fun `every locality the monitor fetches is a unit at the same point and every unit but aravan town is fetched`() {
        val units = requireNotNull(Units.of("kg.default")).units
        assertEquals(115, fetched.size)
        assertEquals(units.map { it.id }.toSet() - KyrgyzLocalities.aravanTown.key, fetched.map { it.first }.toSet())
        for ((key, point) in fetched) {
            assertEquals(point, units.single { it.id == key }.point, "kg.default/$key: the unit's point is the point fetched")
        }
    }

    @Test
    fun `the gate reads every locality's 2026 and 2027 tables at its unit's point`() {
        val units = requireNotNull(Units.of("kg.default")).units.associateBy { it.id }
        for ((key, point) in fetched) {
            for (year in listOf(2026, 2027)) {
                val at = rows.filter { it.unit == key && it.path.contains("/$key-$year") && it.lat == point.lat && it.lon == point.lon }
                assertEquals(1, at.size, "kg.default/$key: one $year row at its own point")
            }
            assertTrue(units.getValue(key).measured, key)
        }
        assertEquals(116, rows.count { it.split == Split.FIT }, "58 localities' 2026 and 2027 tables fit, the rest held out")
    }

    @Test
    fun `every kyrgyz city of the app's list is inside a unit and read there against its tables`() {
        val cities = TestPaths.appFiles.resolve("cities.csv").readLines().drop(1).map { it.split(",") }.filter { it[4] == "KG" }
        assertEquals(32, cities.size)
        val units = requireNotNull(Units.of("kg.default")).units.associateBy { it.id }
        for (c in cities) {
            val lat = c[5].toDouble()
            val lon = c[6].toDouble()
            val unit = Registry.resolve(Place(lat, lon, c[7], "KG")).unitId
            assertTrue(unit != null, "${c[1]} is inside a unit")
            // The unit's own tables (its rows at its own point); a neighbour's table read at the same place (Kara-Kol's
            // at Kara-Kul', the named places of 12 Nov 2026) is another row.
            val point = units.getValue(unit).point
            val own = rows.filter { it.unit == unit && it.lat == point.lat && it.lon == point.lon }.map { it.path }.toSet()
            val read = rows.count { it.unit == unit && it.lat == lat && it.lon == lon && it.path in own && it.path.contains("pinned/kg-muftiyat/") }
            assertEquals(2, read, "${c[1]}: its 2026 and 2027 rows in $unit")
        }
    }
}
