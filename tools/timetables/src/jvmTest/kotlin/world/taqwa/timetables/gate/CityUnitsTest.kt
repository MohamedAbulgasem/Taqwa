package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaceList
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.monitor.Recipes
import world.taqwa.timetables.units.QmdbReach
import world.taqwa.timetables.units.ReachRows
import java.io.File
import java.util.concurrent.Executors
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
 * - every checked unit (the measured, and Zhenis, which is Zhetysay's) is a point the monitor fetches (the fetchers'
 *   own lists), and every point it fetches is a unit, so no table goes unchecked when a new year's appears and no
 *   capture lands outside its unit;
 * - QMDB's reaches, measured by the engine's own rule (QmdbReach), hold at the edges of a large sample of units,
 *   and were measured with kz.qmdb as it stands (QmdbPlaceList.FINGERPRINT);
 * - inside a Kazakh city's reach the city's table wins (the owner's decision of 9 Oct 2026): a village's user there
 *   stays within three minutes of the village's own day and is never before their own point;
 * - every Saudi and Kazakh city of the app's list is inside a unit but the few no place of the authority's list
 *   is near, which this names;
 * - with the archive: at the four edges of every unit's reach (north, east, south and west, 95 % of the radius),
 *   the engine is never early, no end is late and nothing passes its late limit (the unit's own, ruling R41, which
 *   the reach rows of 9 Oct 2026 measured across the unit) against the unit's own city tables, the tables the gate
 *   reads at the unit's point; the worst lateness is printed. And so on rings around 24 of the larger Kazakh
 *   cities, out to 15 km, against the city's own tables.
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

    /** The units whose tables the gate reads: the measured, and Zhenis, a checked place inside Zhetysay's reach. */
    private fun checked(entry: String): List<AuthorityUnit> = requireNotNull(Units.of(entry)) { "$entry has no units" }.units.filter {
        it.measured || (entry == "kz.qmdb" && QmdbPlaces.all.any { p -> p.key == it.id })
    }

    private fun assertSame(entry: String, fetched: List<Triple<String, Double, Double>>) {
        // A unit the authority serves no table for (QMDB's five doubled points) is not measured, and not fetched.
        val units = checked(entry)
        assertEquals(units.size, fetched.size, "$entry: as many checked units as fetched points")
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
    fun `at the four edges of every city unit no start is early no end late and none over its limit against its city's tables`() {
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
        val broken = result.entries.values.flatMap { result.neverEarly(it) + result.overLimit(it) }
        if (broken.isNotEmpty()) fail("${broken.size} broken at the edges:\n" + broken.joinToString("\n"))
    }

    /**
     * The review of 9 Oct 2026 ("prove it across the unit"): every measured Umm al-Qura and QMDB unit's own tables are
     * replayed across its reach (the reach files, `generateReachRows`), every own table at every point kept, each point
     * inside the unit's reach, and the monitor's recipes extend each unit's rows with each new year of its table.
     */
    @Test
    fun `every measured unit's own tables are replayed across its reach`() {
        val gate = TestPaths.repoRoot.resolve("tools/timetables/official/gate")
        val manifest = GateManifest.load(gate)
        val recipes = Recipes.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/recipes.tsv"))
        for (spec in ReachRows.SPECS) {
            val reach = manifest.rows.filter { it.source == "${spec.group}-reach.tsv" }
            for (unit in requireNotNull(Units.of(spec.entry)).units.filter { it.measured }) {
                val own = ReachRows.ownRows(manifest, spec, unit)
                val mine = reach.filter { it.entry == spec.entry && it.unit == unit.id }
                val points = mine.map { GeoPoint(it.lat!!, it.lon!!) }.distinct()
                assertTrue(points.isNotEmpty(), "${spec.entry}/${unit.id}: no reach row")
                for (p in points) {
                    assertTrue(distanceKm(p, unit.point) <= unit.radiusKm, "${spec.entry}/${unit.id}: $p beyond its reach")
                    val paths = mine.filter { it.lat == p.lat && it.lon == p.lon }.map { it.path }.toSet()
                    assertEquals(own.map { it.path }.toSet(), paths, "${spec.entry}/${unit.id} at $p: not its own tables")
                }
                val fetched = own.mapNotNull { Regex("""archive/tables/pinned/([a-z-]+)/[0-9-]+/(.+)-\d{4}\.txt""").matchEntire(it.path) }
                    .map { it.groupValues[1] to it.groupValues[2] }.distinct()
                for ((source, place) in fetched) {
                    assertTrue(
                        recipes.any { it.source == source && it.gate == "${spec.group}-reach.tsv" && it.entry == "${spec.entry}/${unit.id}" && it.matches("$place-2030") },
                        "${spec.entry}/${unit.id}: no recipe grows its reach rows from $source/$place",
                    )
                }
            }
        }
    }

    @Test
    fun `the qmdb reaches were measured with kz qmdb as it stands`() {
        assertEquals(
            QmdbPlaceList.FINGERPRINT,
            QmdbReach.fingerprint(),
            "kz.qmdb's method, bands or curves, QmdbReach's rule or QmdbPlaceCodec changed since QmdbPlaceList.kt was " +
                "generated, so its reaches are stale: run ./gradlew -p tools/timetables generateQmdbPlaces, then the gate, " +
                "and commit the new file",
        )
    }

    private val kazakhstan get() = requireNotNull(Registry.byId("kz.qmdb"))

    private fun kazakhUnitAt(at: GeoPoint, zone: String = "Asia/Almaty"): String? =
        Registry.resolveEntry(kazakhstan, Place(at.lat, at.lon, zone, "KZ")).unitId

    /**
     * The owner's decision of 9 Oct 2026 (the city's table): inside a city's reach the city's place wins over a
     * nearer village's. At a village inside a city's reach, every day of 2026, the time shown stays within three
     * minutes of the village's own day, of the city's own day and of the user's own point alone, and nothing comes
     * before any of them (the engine before units is the user's own point alone). A village with its own table
     * (Zhenis, in Zhetysay's reach) is held against that table by its gate rows as well.
     */
    @Test
    fun `inside a city's reach a village stays within three minutes of its own day and never before its own point`() {
        val units = requireNotNull(Units.of("kz.qmdb")).units
        val cities = units.filter { it.cityId != null }.associateBy { it.id }
        val year = generateSequence(LocalDate(2026, 1, 1)) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it.year == 2026 }.toList()
        val villages = units.filter { it.cityId == null }.mapNotNull { v -> cities[kazakhUnitAt(v.point)]?.let { v to it } }
        assertTrue(villages.size >= 300, "only ${villages.size} villages inside a city's reach")
        val cityDays = villages.map { it.second }.distinct().parallelStream().map { it.id to QmdbReach.unitDays(it, year) }.toList().toMap()
        val results = villages.parallelStream().map { (v, city) ->
            Triple(v, city, QmdbReach.extraMinutes(city, v.point, cityDays.getValue(city.id), year, also = QmdbReach.unitDays(v, year)))
        }.toList()
        val worst = IntArray(QmdbReach.events.size)
        for ((_, _, e) in results) for (i in worst.indices) worst[i] = maxOf(worst[i], e.extra[i])
        println(
            "kz.qmdb: ${villages.size} places (${villages.count { v -> QmdbPlaces.all.any { it.key == v.first.id } }} checked) inside ${villages.map { it.second }.distinct().size} " +
                "cities' reaches, every day of 2026; worst minutes beyond the place's own day, the city's or the user's own point: " +
                QmdbReach.events.indices.joinToString { "${QmdbReach.events[it].first} ${worst[it]}" },
        )
        val broken = results.flatMap { (v, c, e) -> e.broken.map { "${v.id} in ${c.id}: $it" } }
        if (broken.isNotEmpty()) fail("${broken.size} before a reference:\n" + broken.take(20).joinToString("\n"))
        val over = results.filter { (_, _, e) -> e.extra.any { it > QmdbReach.LIMIT_MINUTES } }
        if (over.isNotEmpty()) {
            fail("${over.size} places over ${QmdbReach.LIMIT_MINUTES} min:\n" + over.take(20).joinToString("\n") { (v, c, e) ->
                "${v.id} in ${c.id} (${"%.1f".format(distanceKm(v.point, c.point))} km): " + e.extra.joinToString(" ")
            })
        }
    }

    /** The 24 larger cities of the ring check, each a measured city unit. */
    private val ringCities = listOf(
        "almaty", "astana", "shymkent", "karaganda", "aktobe", "taraz", "pavlodar", "oskemen", "semey", "oral",
        "kostanay", "petropavl", "atyrau", "kyzylorda", "aktau", "turkistan", "temirtau", "kokshetau", "taldykorgan",
        "ekibastuz", "rudny", "zhezkazgan", "balkhash", "kentau",
    )

    /**
     * The owner's decision of 9 Oct 2026 (the city's table), with the archive: on 10 rings out to 15 km and 16
     * bearings around 24 of the larger cities, every point inside the city's reach follows the city's place, or the
     * nearer city's where two reach, and against that city's own tables no start comes before them and no end after.
     */
    @Test
    fun `on rings inside a city's reach no start comes before the city's table no end after it and none over its limit`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")).only(groups = setOf("kz-qmdb"))
        val units = requireNotNull(Units.of("kz.qmdb")).units.associateBy { it.id }
        fun ownRows(unit: AuthorityUnit) = manifest.rows.filter { it.entry == "kz.qmdb" && it.lat == unit.point.lat && it.lon == unit.point.lon }
        // One city at a time, four at once: the gate keeps every place-day it checks, and the 24 cities' rings hold
        // some three million.
        val pool = Executors.newFixedThreadPool(4)
        val rings = try {
            ringCities.map { id -> pool.submit<Ring> { ring(roots, units, units.getValue(id), ::ownRows, roots.held) } }.map { it.get() }
        } finally {
            pool.shutdown()
        }
        if (!roots.held) return
        val held = Tally()
        val farther = Tally()
        for (r in rings) {
            held.add(r.held)
            farther.add(r.farther)
        }
        println(
            "kz.qmdb rings: ${rings.sumOf { it.points }} points around ${ringCities.size} cities (${rings.sumOf { it.nearer }} in a nearer " +
                "city's reach, ${rings.sumOf { it.unchecked }} in a city with no table), ${held.placeDays} place-days; worst start " +
                "${held.worst.filterKeys { it.isStart }.values.maxOrNull()} min after the city's table, worst end " +
                "${held.worst.filterKeys { !it.isStart }.values.maxOrNull()} min before it",
        )
        for ((event, worst) in held.worst) println("  ${event.key}: worst $worst min, ${held.late.getValue(event).joinToString("/")} days at 0/1/2/3+ min")
        println(
            "  in a nearer city's reach, against the farther ring city's own tables (for the record, not held): " +
                "${rings.sumOf { it.nearer }} points, ${farther.placeDays} place-days, ${farther.cells.values.sum()} cells before or after " +
                "them (worst ${farther.worstEarly} min" + farther.cells.entries.filter { it.value > 0 }.joinToString("") { "; ${it.key.key} ${it.value}" } + ")",
        )
        for (r in rings.filter { it.nearer > 0 }) println("    ${r.city}: ${r.nearer} points in ${r.takers.joinToString()}")
        val broken = held.broken
        if (broken.isNotEmpty()) fail("${broken.size} broken on the rings:\n" + broken.joinToString("\n"))
    }

    /** One ring city's sample: its points, those a nearer city took (and which), and the gate's results. */
    private class Ring(
        val city: String,
        val points: Int,
        val nearer: Int,
        val unchecked: Int,
        val takers: List<String>,
        val held: Tally,
        val farther: Tally,
    )

    /**
     * The rings around [city]: each point's unit checked (the city's, or a nearer city's where two reach), then, when
     * [evaluate], the tables of the city it follows gated there, and where a nearer city took it, [city]'s own too.
     */
    private fun ring(
        roots: OfficialRoots,
        units: Map<String, AuthorityUnit>,
        city: AuthorityUnit,
        ownRows: (AuthorityUnit) -> List<GateRow>,
        evaluate: Boolean,
    ): Ring {
        val zone = ownRows(city).first().zone
        var points = 0
        var nearer = 0
        var unchecked = 0
        val takers = sortedMapOf<String, Int>()
        val rows = mutableListOf<GateRow>()
        val farther = mutableListOf<GateRow>()
        for (km in listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0)) {
            for (bearing in (0 until 16).map { it * 22.5 }) {
                val at = moved(city.point, km, bearing)
                val where = "${city.id} ring $km km $bearing"
                assertTrue(distanceKm(at, city.point) <= city.radiusKm, "$where: beyond the city's reach")
                val holder = units.getValue(requireNotNull(kazakhUnitAt(at, zone)) { "$where: in no unit" })
                assertTrue(holder.cityId != null, "$where: ${holder.id}, not a city, took a point inside the city's reach")
                points++
                if (holder.id != city.id) {
                    nearer++
                    takers.merge(holder.id, 1, Int::plus)
                    assertTrue(distanceKm(at, holder.point) <= distanceKm(at, city.point), "$where: ${holder.id} is not nearer")
                    farther += ownRows(city).map { it.copy(lat = at.lat, lon = at.lon, unit = holder.id, split = Split.TEST, note = "$where in ${holder.id}") }
                }
                val own = ownRows(holder)
                if (own.isEmpty()) unchecked++
                rows += own.map { it.copy(lat = at.lat, lon = at.lon, unit = holder.id, split = Split.TEST, note = where) }
            }
        }
        // Each result is tallied at once and let go: the gate keeps every place-day it checked.
        fun gate(r: List<GateRow>) = Tally().also { if (evaluate && r.isNotEmpty()) it.add(Gate(roots).evaluate(GateManifest(r))) }
        return Ring(city.id, points, nearer, unchecked, takers.map { "${it.key} ${it.value}" }, gate(rows), gate(farther))
    }

    /** The gate's counts over several results, by event. */
    private class Tally {
        var placeDays = 0
        val worst = sortedMapOf<Event, Int>()
        val late = sortedMapOf<Event, IntArray>()
        val cells = sortedMapOf<Event, Int>()
        var worstEarly = 0
        val broken = mutableListOf<String>()

        fun add(result: GateResult?) {
            val s = result?.entries?.get("kz.qmdb") ?: return
            placeDays += s.placeDayCount
            for ((event, e) in s.events) {
                worst.merge(event, e.worst) { a, b -> maxOf(a, b) }
                val hist = late.getOrPut(event) { IntArray(4) }
                for (i in hist.indices) hist[i] += e.late[i]
                cells.merge(event, e.early + e.lateEnd, Int::plus)
                worstEarly = maxOf(worstEarly, e.worstEarly)
            }
            broken += result.neverEarly(s) + result.overLimit(s)
        }

        fun add(other: Tally) {
            placeDays += other.placeDays
            for ((event, w) in other.worst) worst.merge(event, w) { a, b -> maxOf(a, b) }
            for ((event, hist) in other.late) {
                val mine = late.getOrPut(event) { IntArray(4) }
                for (i in mine.indices) mine[i] += hist[i]
            }
            for ((event, n) in other.cells) cells.merge(event, n, Int::plus)
            worstEarly = maxOf(worstEarly, other.worstEarly)
            broken += other.broken
        }
    }
}
