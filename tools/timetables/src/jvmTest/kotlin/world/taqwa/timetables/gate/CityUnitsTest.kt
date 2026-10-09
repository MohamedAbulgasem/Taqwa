package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.units.QmdbReach
import java.io.File
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Ruling R44's city units for the two authorities that print one table per place of their own city lists, Umm
 * al-Qura (KACST's 173) and QMDB (every one of its 5,694 places since the owner's decision of 9 Oct 2026, 87 of
 * them measured), the city-points round of 9 Oct 2026:
 *
 * - every measured unit is a point the monitor fetches (the fetchers' own lists), and every point it fetches is a
 *   unit, so no unit goes unchecked when a new year's table appears and no capture lands outside its unit;
 * - QMDB's reaches, measured by the engine's own rule (QmdbReach), hold at the edges of a large sample of units;
 * - every Saudi and Kazakh city of the app's list is inside a unit but the few no place of the authority's list
 *   is near, which this names;
 * - with the archive: at the four edges of every unit's reach (north, east, south and west, 95 % of the radius),
 *   the engine is never early and no end is late against the unit's own city tables, the tables the gate reads at
 *   the unit's point. The gate's late limit is not held there (ruling R40: near a radius's edge the start is the
 *   user's own sun, up to the class's minute later than the city's table); the worst lateness is printed.
 */
class CityUnitsTest {

    private val monitor = TestPaths.repoRoot.resolve("tools/timetables/monitor/fetchers")

    /** A fetcher's POINTS: (key, lat, lon) of each tuple, the coordinates as the file writes them. */
    private fun points(file: String): List<Triple<String, Double, Double>> {
        val text = monitor.resolve(file).readText()
        val block = text.substringAfter("POINTS = [").substringBefore("\n]")
        val tuple = Regex("""\(\s*"([^"]+)",\s*"[^"]*",\s*"?(-?[0-9.]+)"?,\s*"?(-?[0-9.]+)"?""")
        return tuple.findAll(block).map { Triple(it.groupValues[1], it.groupValues[2].toDouble(), it.groupValues[3].toDouble()) }.toList()
    }

    private fun assertSame(entry: String, fetched: List<Triple<String, Double, Double>>) {
        // A unit the authority serves no table for (QMDB's five doubled points) is not measured, and not fetched.
        val units = requireNotNull(Units.of(entry)) { "$entry has no units" }.units.filter { it.measured }
        assertEquals(units.size, fetched.size, "$entry: as many measured units as fetched points")
        assertEquals(units.map { it.id }.toSet(), fetched.map { it.first }.toSet(), "$entry: each unit is the monitor's capture key")
        for ((key, lat, lon) in fetched) {
            val unit = units.single { it.id == key }
            assertEquals(GeoPoint(lat, lon), unit.point, "$entry/$key: the unit's point is the point fetched")
        }
    }

    @Test
    fun `every unit is a point the monitor fetches and every point it fetches a unit`() {
        assertSame("sa.ummalqura", points("ummalqura.py") + points("ummalqura_cities.py"))
        assertSame("kz.qmdb", points("qmdb.py"))
    }

    @Test
    fun `every saudi and kazakh city of the app's list is inside a unit but the few no place of the list is near`() {
        val cities = TestPaths.appFiles.resolve("cities.csv").readLines().drop(1).map { it.split(",") }
            .filter { it[4] == "SA" || it[4] == "KZ" }
        val outside = cities.filter { c ->
            Registry.resolve(Place(c[5].toDouble(), c[6].toDouble(), c[7], c[4])).unitId == null
        }.map { it[0] }.toSet()
        // GeoNames ids: Turubah, King Khalid Military City, Al Mashʿilīyah and one of the two Al ʿAqīq (no KACST place
        // within a minute's reach), and Shalkar in the Atyrau region (QMDB's nearest place 16 km off, another village).
        assertEquals(setOf("393275", "8449855", "12513572", "110059", "608362"), outside)
        assertEquals(98 + 84, cities.size, "the app's Saudi and Kazakh cities")
        // Tayma takes KACST's own Tayma table, which KACST's list computes a degree north of the town.
        val tayma = Registry.resolve(Place(27.62233, 38.53882, "Asia/Riyadh", "SA"))
        assertEquals("tayma", tayma.unitId)
        assertEquals(GeoPoint(28.63, 38.55), tayma.method?.fixedPoint)
        // Shchuchinsk is its own QMDB place's unit, which QMDB's API serves no table for: no figure there, nor
        // Makinsk's, 37 km off.
        val shchuchinsk = Registry.resolve(Place(52.93592, 70.18895, "Asia/Almaty", "KZ"))
        assertEquals("shchuchinsk", shchuchinsk.unitId)
        assertTrue(!shchuchinsk.measured)
    }

    /** [from] moved [km] along [bearingDeg] on the sphere the registry measures with. */
    private fun moved(from: GeoPoint, km: Double, bearingDeg: Double): GeoPoint {
        val r = 6371.0
        val d = km / r
        val b = bearingDeg * PI / 180
        val la1 = from.lat * PI / 180
        val lo1 = from.lon * PI / 180
        val la2 = asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
        val lo2 = lo1 + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
        return GeoPoint(la2 * 180 / PI, lo2 * 180 / PI)
    }

    /**
     * The owner's decision of 9 Oct 2026 (every QMDB place a unit): a unit reaches as far as every time shown stays
     * within three minutes of the unit's own day and of the user's own point alone (QmdbReach). Checked here on every
     * day of 2026 at eight bearings, 98 % of the reach, for all 87 checked places and some 300 more: the north from
     * 54.5° N, the bands either side of 46° N and 48° N, the west's zones, the far south, the sparsest steppe (the
     * places farthest from any other) and a stride through the rest. No archive needed: the engine against itself.
     */
    @Test
    fun `at the reach's edge of a large sample of qmdb units every time stays within three minutes`() {
        val units = requireNotNull(Units.of("kz.qmdb")).units
        val others = units.filter { !it.measured }
        fun nearestOther(u: AuthorityUnit) = units.asSequence().filter { it !== u }.minOf { distanceKm(it.point, u.point) }
        val sample = (
            units.filter { it.measured } +
                others.filter { it.point.lat >= 54.5 }.take(40) +
                others.filter { kotlin.math.abs(it.point.lat - 48.0) < 0.3 }.take(40) +
                others.filter { kotlin.math.abs(it.point.lat - 46.0) < 0.3 }.take(40) +
                others.filter { it.point.lon < 56.0 }.take(40) +
                others.filter { it.point.lat < 41.5 }.take(30) +
                others.sortedByDescending { nearestOther(it) }.take(40) +
                others.filterIndexed { i, _ -> i % 60 == 0 }
            ).distinctBy { it.id }
        assertTrue(sample.size >= 350, "only ${sample.size} units sampled")
        val results = sample.parallelStream().map { it to QmdbReach.edges(it) }.toList()
        val worst = IntArray(QmdbReach.events.size)
        for ((_, e) in results) for (i in worst.indices) worst[i] = maxOf(worst[i], e.extra[i])
        println(
            "kz.qmdb: ${sample.size} units, 8 edges each, every day of 2026; worst minutes beyond the unit's own day or " +
                "the user's own point: " + QmdbReach.events.indices.joinToString { "${QmdbReach.events[it].first} ${worst[it]}" },
        )
        val broken = results.flatMap { (u, e) -> e.broken.map { "${u.id}: $it" } }
        if (broken.isNotEmpty()) fail("${broken.size} broken at the edges:\n" + broken.take(20).joinToString("\n"))
        val over = results.filter { (_, e) -> e.extra.any { it > QmdbReach.LIMIT_MINUTES } }
        if (over.isNotEmpty()) {
            fail("${over.size} units over ${QmdbReach.LIMIT_MINUTES} min at their edge:\n" + over.take(20).joinToString("\n") { (u, e) ->
                "${u.id} (${u.point.lat}, ${u.point.lon}, ${u.radiusKm} km): " + e.extra.joinToString(" ")
            })
        }
    }

    @Test
    fun `at the four edges of every city unit no start is early and no end late against its city's tables`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
            .only(groups = setOf("sa-ummalqura", "kz-qmdb"))
        val edges = mutableListOf<GateRow>()
        val points = mutableMapOf<String, Int>()
        for (entry in listOf("sa.ummalqura", "kz.qmdb")) {
            for (unit in requireNotNull(Units.of(entry)).units.filter { it.measured }) {
                // The unit's own city tables: the rows read at its point.
                val own = manifest.rows.filter {
                    it.entry.substringBefore('/') == entry && it.lat == unit.point.lat && it.lon == unit.point.lon
                }
                assertTrue(own.isNotEmpty(), "$entry/${unit.id}: no gate row at the unit's own point")
                for (bearing in listOf(0.0, 90.0, 180.0, 270.0)) {
                    val at = moved(unit.point, unit.radiusKm * 0.95, bearing)
                    assertTrue(distanceKm(at, unit.point) <= unit.radiusKm)
                    val here = Registry.resolveEntry(Registry.byId(entry)!!, Place(at.lat, at.lon, own.first().zone, entry.take(2).uppercase()))
                    // Where a nearer unit takes the point, that unit's own reach is the one checked.
                    if (here.unitId != unit.id) continue
                    points[entry] = (points[entry] ?: 0) + 1
                    edges += own.map { it.copy(lat = at.lat, lon = at.lon, unit = unit.id, split = Split.TEST, note = "${unit.id} edge $bearing") }
                }
            }
        }
        val result = Gate(roots).evaluate(GateManifest(edges))
        if (!roots.held) return
        for ((id, s) in result.entries) {
            val starts = s.events.filterKeys { it.isStart }.values.maxOfOrNull { it.worst }
            val ends = s.events.filterKeys { !it.isStart }.values.maxOfOrNull { it.worst }
            println("$id: ${points[id]} edge points, ${s.placeDayCount} place-days; worst start $starts min after the city's table, worst end $ends min before it")
            for ((event, e) in s.events) println("  ${event.key}: worst ${e.worst} min, ${e.late.joinToString("/")} days at 0/1/2/3+ min")
        }
        val broken = result.entries.values.flatMap { result.neverEarly(it) }
        if (broken.isNotEmpty()) fail("${broken.size} broken at the edges:\n" + broken.joinToString("\n"))
    }
}
