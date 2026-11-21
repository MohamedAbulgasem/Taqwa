package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.FixCountry
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import world.taqwa.app.prayer.engine.registry.data.ProofStamps
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaceList
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import world.taqwa.app.prayer.engine.registry.data.ReachLimits
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.monitor.Recipes
import world.taqwa.timetables.units.OwnPointWorst
import world.taqwa.timetables.units.QmdbReach
import world.taqwa.timetables.units.ReachAreas
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
 * - with the archive: across every unit, on a grid offset from the reach rows', the engine is never early, no end
 *   is late, nothing passes its late limit (the unit's own, ruling R41, which the reach rows measured across the
 *   unit) and no start passes the unit's own figure, against the unit's own tables; and at each unit's own point and
 *   the app's city point the limits from before the reach rows still hold. And so on rings around 24 of the larger
 *   Kazakh cities, out to 15 km, against the city's own tables.
 *
 * Since the city-point audit of 10–11 Oct 2026 the same reach and own-point checks cover the units of the tables printed
 * for their own points (`tablesAtTheirPoints`): ISNA's mosques in the United States and Canada and BIK's base across
 * their reach ([ReachRows.SPECS]), and every such group's own points and app points ([OwnPointWorst.GROUPS]).
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

    @Test
    fun `every diyanet unit stands at the app's point for its city or names it as its town`() {
        // Ruling R203: a neighbouring district's table stops 2 km short of a unit's own point, the city's own town
        // (R171). A unit that stands elsewhere (Diyanet's computing point) names the app's point for the city, and the
        // same 2 km are kept clear around it; so no unit may stand off the app's list without naming its town.
        val cities = TestPaths.appFiles.resolve("cities.csv").readLines().drop(1).map { it.split(",") }
            .filter { it[4] == "TR" }.map { GeoPoint(it[5].toDouble(), it[6].toDouble()) }.toSet()
        val units = requireNotNull(Units.of("tr.diyanet")).units
        for (unit in units) {
            val town = Diyanet.towns[unit.id]
            if (town == null) assertTrue(unit.point in cities, "${unit.name} stands at no city of the app's list and names no town")
            else assertTrue(town in cities && unit.point !in cities, "${unit.name}'s town")
        }
        assertTrue(Diyanet.towns.keys.all { id -> units.any { it.id == id } })
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

    /**
     * With the archive, the reviews of 10 Oct 2026: every measured unit's own tables on the review's two grids
     * ([GRIDS], one offset from the reach rows' grid and every sweep), a point another unit takes left out. Never early, no
     * end late, nothing over its limit, and no start beyond the unit's own figure (its committed stamp row, which
     * About reads): the reach rows found each unit's true worst.
     */
    @Test
    fun `across every unit off the reach rows no start is early or beyond its figure and none over its limit`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        class Checked(val entry: String, val unit: String, val points: Int, val tally: Tally, val beyond: List<String>)
        val pool = Executors.newFixedThreadPool(4)
        val checked = try {
            ReachRows.SPECS.flatMap { spec ->
                val figures = requireNotNull(ProofStamps.of(spec.entry)).worstLateByUnit
                ReachRows.units(spec).map { unit ->
                    pool.submit<Checked> {
                        val own = ReachRows.ownRows(manifest, spec, unit)
                        val points = GRIDS.flatMap { (bearings, shares) -> ReachRows.points(spec, unit, own.first().zone, shares, bearings) }
                            .distinctBy { it.at }
                        val tally = Tally()
                        val beyond = mutableListOf<String>()
                        if (roots.held) {
                            val figure = figures[unit.id]?.filterKeys { it in STARTS }?.values?.maxOrNull()
                            for (p in points) {
                                val result = Gate(roots).evaluate(GateManifest(ReachRows.rowsAt(own, unit, p.at)))
                                tally.add(result, spec.entry)
                                val s = result.entries[spec.entry] ?: continue
                                val worst = s.events.filterKeys { it.isStart }.values.maxOfOrNull { it.worst } ?: continue
                                if (figure == null || worst > figure) beyond += "${spec.entry}/${unit.id} at ${p.at}: a start $worst min after, its figure $figure"
                            }
                        }
                        Checked(spec.entry, unit.id, points.size, tally, beyond)
                    }
                }
            }.map { it.get() }
        } finally {
            pool.shutdown()
        }
        if (!roots.held) return
        for (entry in ReachRows.SPECS.map { it.entry }) {
            val mine = checked.filter { it.entry == entry }
            val all = Tally().also { t -> mine.forEach { t.add(it.tally) } }
            println(
                "$entry off the reach rows: ${mine.sumOf { it.points }} points of ${mine.size} units, ${all.placeDays} place-days; worst " +
                    all.worst.entries.joinToString { "${it.key.key} ${it.value}" },
            )
        }
        val broken = checked.flatMap { it.tally.broken.map { b -> "${it.entry}/${it.unit}: $b" } } + checked.flatMap { it.beyond }
        if (broken.isNotEmpty()) fail("${broken.size} broken off the reach rows:\n" + broken.take(40).joinToString("\n"))
    }

    /**
     * The reviews of 10 Oct 2026: the reach's limits are wider than a table's own point needs (Umm al-Qura's starts 2
     * and ends 3 for the reach, against class A's minute; QMDB's per unit), so every row at a unit's own point and at
     * the app's point for its city (the group files, without the reach files) is held here, each on its own, to its own
     * worst per event as `generateOwnPointWorst` measured it (official/own-points-worst.tsv): a one-minute regression
     * at a city's own point fails, at Almaty as at Petropavl. Each row also stays within the limits the own points kept
     * before the reach rows (the documented exceptions, [pointLimit]); a row `prove` adds since is held to those alone.
     * Not so for Oman's localities and Türkiye's districts ([HELD_BY_PLACE], the review of 14 Nov 2026), nor since ruling
     * R270 for the entries `ReachLimits.kt` raises: since rulings R254, R255 and R270 their limits are their whole
     * reach's worst (Bahla's 3, an il centre's 2 to 4, Kazan's 135), which a row new since the last
     * `generateOwnPointWorst` would otherwise be free to reach at the own point itself. Such a row is held to the worst of the rows already measured at its place ([overItsPlace]), a table's own point being a place of
     * a minute or two; only a row at a place with no measured row yet stands on the limits alone.
     * A Kyrgyz row whose point stands inside another locality's reach too, where that locality's table rides beside its
     * own (ruling R205), is held to the gate's own limit there; every Kyrgyz row no other reach holds keeps class B's 2.
     * So with QMDB's since ruling R249 (14 Nov 2026): a Kazakh row whose point another place's reach holds too waits for
     * that place's table as well, and is held to the gate's own limit for its unit (the reach's, ruling R252) and to its
     * own worst in the file; every Kazakh row no other reach holds keeps the entry's Fajr 8, Isha 6, end of eating 8
     * and class D's 3, as before the ride.
     */
    @Test
    fun `at each unit's own point and the app's city point every row keeps its own worst`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val baseline = OwnPointWorst.read(TestPaths.repoRoot.resolve(OwnPointWorst.FILE))
        if (!roots.held) return
        val now = OwnPointWorst.measure(roots, manifest)
        val over = mutableListOf<String>()
        var unbaselined = 0
        for ((key, events) in now) {
            val before = baseline[key]
            if (before == null) unbaselined++
            for ((event, worst) in events) {
                val was = before?.get(event)
                if (was != null && worst > was) over += "$key $event: $worst min, its own worst $was"
            }
        }
        val besidePlace = overItsPlace(now, baseline) { entry -> if (entry in HELD_BY_PLACE) Gate.classLimit(requireNotNull(Registry.byId(entry)).entryClass) else null }
        over += besidePlace.over
        // R205: the Kyrgyz rows whose point another locality's reach holds too, and the units they belong to.
        val kyrgyz = requireNotNull(Units.of(KYRGYZ)).units
        val kyrgyzRows = OwnPointWorst.rows(manifest).filter { it.entry == KYRGYZ }
        val shared = kyrgyzRows.filter { row ->
            val at = GeoPoint(requireNotNull(row.lat), requireNotNull(row.lon))
            kyrgyz.count { distanceKm(at, it.point) <= it.radiusKm } > 1
        }
        val sharedUnits = shared.mapNotNull { it.unit }.toSet()
        for (row in kyrgyzRows - shared.toSet()) {
            val unit = requireNotNull(row.unit) { "${row.source}:${row.line} names no unit" }
            for ((event, worst) in now.getValue(OwnPointWorst.key(row))) {
                val limit = kyrgyzOwnLimit(unit, event)
                if (worst > limit) over += "$KYRGYZ/$unit at ${row.lat}, ${row.lon} $event: $worst min, in no other locality's reach, limit $limit"
            }
        }
        // R249: the Kazakh rows whose point another place's reach holds too, and the units they resolve to.
        val kazakh = requireNotNull(Units.of(KAZAKH)).units
        val kazakhRows = OwnPointWorst.rows(manifest).filter { it.entry == KAZAKH }
        val kazakhShared = kazakhRows.filter { row ->
            val at = GeoPoint(requireNotNull(row.lat), requireNotNull(row.lon))
            kazakh.count { distanceKm(at, it.point) <= it.radiusKm } > 1
        }
        val kazakhSharedUnits = kazakhShared.mapNotNull { kazakhUnitAt(GeoPoint(requireNotNull(it.lat), requireNotNull(it.lon)), it.zone) }.toSet()
        val kazakhAlone = kazakhRows - kazakhShared.toSet()
        assertTrue(kazakhAlone.isNotEmpty(), "no Kazakh row stands outside every other place's reach")
        for (row in kazakhAlone) {
            for ((event, worst) in now.getValue(OwnPointWorst.key(row))) {
                val limit = kazakhOwnLimit(event)
                if (worst > limit) over += "$KAZAKH ${row.path} at ${row.lat}, ${row.lon} $event: $worst min, in no other place's reach, limit $limit"
            }
        }
        val result = Gate(roots).evaluate(manifest.only(groups = OwnPointWorst.GROUPS.toSet()))
        for ((entry, s) in result.entries) {
            for ((unit, u) in s.units) {
                for ((event, e) in u.events) {
                    val limit = pointLimit(entry, unit, event.key, sharedUnits + kazakhSharedUnits)
                    if (e.worst > limit) over += "$entry/$unit ${event.key}: ${e.worst} min, its own point's limit $limit"
                }
            }
        }
        println(
            "own points: ${now.size} rows, ${now.size - unbaselined} held to their own worst, ${besidePlace.held} new ones to their place's worst, " +
                "${unbaselined - besidePlace.held} to the limits alone; ${kazakhShared.size} of ${kazakhRows.size} Kazakh rows stand in another place's reach too",
        )
        if (over.isNotEmpty()) fail("${over.size} over an own point's worst or limit (./gradlew -p tools/timetables generateOwnPointWorst where meant):\n" + over.joinToString("\n"))
    }

    /**
     * The entries whose own-point rows, new since the baseline, are held to their place's measured worst ([overItsPlace]):
     * Oman's and Türkiye's (R254, R255), and since ruling R270 every entry whose units `ReachLimits.kt` raised to their
     * reach's worst (the review of 15 Nov 2026): their recorded limit is the reach's now, which [pointLimit] would let a
     * row `prove` adds at the own point reach.
     */
    private val HELD_BY_PLACE = setOf("om.mara", "tr.diyanet") + ReachLimits.MINUTES.keys

    /** What [overItsPlace] found: how many new rows it held to their place's worst, and each event of theirs beyond it. */
    private class BesidePlace(val held: Int, val over: List<String>)

    /**
     * Each row of [now] with no line in [baseline] whose place has one (the same gate file, entry and unit, and point: the
     * captures of one table before it), held per event to the worst any of those reads there, and to [floor] of its entry
     * where that is larger (the class's own minutes: a new capture may round a day the other way). An entry [floor]
     * gives nothing for is not held here, nor a row at a place with no line yet.
     */
    private fun overItsPlace(now: Map<String, Map<String, Int>>, baseline: Map<String, Map<String, Int>>, floor: (String) -> Int?): BesidePlace {
        fun place(key: String) = key.split('\t').let { listOf(it[0], it[2], it[3], it[4]) }
        val measured = baseline.entries.groupBy({ place(it.key) }, { it.value })
        val over = mutableListOf<String>()
        var held = 0
        for ((key, events) in now) {
            if (key in baseline) continue
            val least = floor(key.split('\t')[2].substringBefore('/')) ?: continue
            val beside = measured[place(key)] ?: continue
            held++
            for ((event, worst) in events) {
                val limit = maxOf(least, beside.maxOf { it[event] ?: 0 })
                if (worst > limit) over += "$key $event: $worst min, new since the baseline, its place's worst $limit"
            }
        }
        return BesidePlace(held, over)
    }

    /** [overItsPlace]'s rule on invented figures: no archive needed. */
    @Test
    fun `a new row at a measured place is held to that place's worst and one at a new place is not`() {
        fun key(path: String, entry: String, lat: String = "null", lon: String = "null") = listOf("xx.tsv", path, entry, lat, lon).joinToString("\t")
        val baseline = mapOf(
            key("a/2026-01-01/t.txt", "xx.one/7") to mapOf("fajr" to 1, "isha" to 0),
            key("a/2026-02-01/t.txt", "xx.one/7") to mapOf("fajr" to 0, "isha" to 2),
            key("a/2026-01-01/u.txt", "xx.one", "1.000000", "2.000000") to mapOf("fajr" to 0),
        )
        val now = baseline + mapOf(
            // A third capture of the first table: Fajr at the place's worst, Isha a minute beyond it, Maghrib read by no row before.
            key("a/2026-03-01/t.txt", "xx.one/7") to mapOf("fajr" to 1, "isha" to 3, "maghrib" to 1),
            // The second table's next capture: within the class's minute, though the row before read 0.
            key("a/2026-03-01/u.txt", "xx.one", "1.000000", "2.000000") to mapOf("fajr" to 1),
            // A place with no row yet, and an entry not held this way: the limits alone.
            key("a/2026-03-01/v.txt", "xx.one/8") to mapOf("fajr" to 4),
            key("a/2026-03-01/t.txt", "xx.two/7") to mapOf("fajr" to 4),
        )
        val found = overItsPlace(now, baseline) { if (it == "xx.one") 1 else null }
        assertEquals(2, found.held)
        assertEquals(1, found.over.size, found.over.joinToString("\n"))
        assertTrue(found.over.single().contains("isha: 3 min") && found.over.single().contains("worst 2"), found.over.single())
        assertEquals(0, overItsPlace(baseline, baseline) { 1 }.held, "a measured row is held to its own worst, not here")
    }

    /**
     * Ruling R255, the review of 14 Nov 2026: a Turkish il centre's limits are generated from the gate's own measure
     * across its reach (the archive's code/manual/tr-diyanet/limits.py from the stamp), and nothing else looked at
     * them. So each is held here to what it was measured as, from the committed stamp: an event's limit is the worst the
     * stamp measured across the unit's reach where that is over class A's minute, and class A's minute where it is not;
     * no limit stands above its measure (slack a later table could grow into unseen) or below it. And none is over
     * [TURKISH_CEILING] min, the most the ruling's measure found: a larger figure is a new ruling's to allow, not a
     * regeneration's. Class A's own minute holds at each il centre's own point (the own-point rows, each to its own
     * worst); across a reach only the events the stamp read no later than a minute keep it.
     */
    @Test
    fun `a turkish limit is its reach's measured worst and none is over the ruling's ceiling`() {
        val entry = "tr.diyanet"
        val registry = requireNotNull(Registry.byId(entry))
        val figures = requireNotNull(ProofStamps.of(entry)).worstLateByUnit
        val units = requireNotNull(Units.of(entry)).units.filter { it.measured }
        assertEquals(units.map { it.id }.toSet(), figures.keys, "$entry: the stamp's units are the measured units")
        val wrong = mutableListOf<String>()
        var own = 0
        var classes = 0
        for (unit in units) {
            val least = Gate.classLimit(unit.entryClass ?: registry.entryClass)
            for ((event, worst) in figures.getValue(unit.id)) {
                val limit = recordedLimit(entry, unit.id, event)
                if (limit != maxOf(worst, least)) wrong += "$entry/${unit.id} $event: limit $limit min, measured $worst across its reach"
                if (limit > TURKISH_CEILING) wrong += "$entry/${unit.id} $event: limit $limit min, over the ruling's $TURKISH_CEILING"
                if (limit > least) own++ else classes++
            }
        }
        println("$entry: ${own + classes} unit-events of ${units.size} units, $own on a limit of their own equal to the reach's measured worst, $classes on the class's")
        if (wrong.isNotEmpty()) fail("${wrong.size} Turkish limits not their reach's measure (docs/MONITOR.md, \"Türkiye's limits\"):\n" + wrong.joinToString("\n"))
    }

    /** Ruling R255's measure of 14 Nov 2026 found no Turkish unit-event later than this across a reach (Ankara's end of eating and Fajr). */
    private val TURKISH_CEILING = 4

    /**
     * Ruling R254: an Omani locality's own limit (Bahla's, Al Hamra's) is what the gate measured across its reach, no
     * more: each event such a limit names reads exactly that at its worst in the committed stamp.
     */
    @Test
    fun `an omani locality's own limit is its reach's measured worst`() {
        val entry = "om.mara"
        val figures = requireNotNull(ProofStamps.of(entry)).worstLateByUnit
        val withOwn = requireNotNull(Units.of(entry)).units.filter { it.lateLimits.isNotEmpty() }
        assertTrue(withOwn.any { it.id == "bahla" }, "Bahla's reach limit (ruling R254)")
        for (unit in withOwn) {
            val measured = requireNotNull(figures[unit.id]) { "$entry/${unit.id}: a limit of its own and no figure" }
            for (limit in unit.lateLimits) {
                val read = Event.entries.filter { it.timed in limit.events }.mapNotNull { measured[it.key] }
                assertTrue(read.isNotEmpty(), "$entry/${unit.id}: a limit for events the gate reads none of")
                assertEquals(limit.minutes, read.max(), "$entry/${unit.id}: ${limit.events} limit against the reach's measured worst")
            }
        }
    }

    /**
     * The review of 14 Nov 2026 (ruling R255): Türkiye's reach limits were measured on every day the tables hold, and
     * the 62 il centres read by hand hold 2026 alone, so how far a second year moves a reach's worst is measured here,
     * on the units whose tables hold both 2026 and 2027 whole: each unit's own tables cut to one year (copies in a
     * temporary folder; the archive is not touched) and read at its own point and every point its reach rows stand at,
     * the two years side by side. Held: neither year is early, late at an end or over its limit. Printed, not held:
     * how many unit-events read later in 2027 than in 2026, the measure behind docs/MONITOR.md's "a red to expect".
     */
    @Test
    fun `a turkish reach read one year at a time is never early and the second year's move is measured`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        if (!roots.held) return
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val spec = ReachRows.SPECS.single { it.entry == "tr.diyanet" }
        val reach = manifest.rows.filter { it.source == "${spec.file}" && it.entry == spec.entry }
        val years = listOf(2026, 2027)
        val date = Regex("""(\d{4})-(\d{2})-(\d{2})\b.*""")
        class Years(val unit: AuthorityUnit, val points: Int, val worst: Map<Int, Map<Event, Int>>, val broken: List<String>)
        val temp = kotlin.io.path.createTempDirectory("taqwa-year-split").toFile()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val splits = ReachRows.units(spec).mapNotNull { unit ->
                val own = ReachRows.ownRows(manifest, spec, unit)
                val lines = own.associate { it.path to roots.file(it.path).readLines() }
                fun days(year: Int) = lines.values.flatten().mapNotNull { date.matchEntire(it)?.takeIf { m -> m.groupValues[1].toInt() == year }?.value?.take(10) }.toSet()
                // Both years whole (neither is a leap year); a unit with less is no measure of a year's move.
                if (years.any { days(it).size != 365 }) return@mapNotNull null
                val points = (listOf(unit.point) + reach.filter { it.unit == unit.id }.map { GeoPoint(it.lat!!, it.lon!!) }).distinct()
                pool.submit<Years> {
                    val worst = mutableMapOf<Int, MutableMap<Event, Int>>()
                    val broken = mutableListOf<String>()
                    for (year in years) {
                        val root = temp.resolve("${unit.id}/$year")
                        val held = own.filter { row ->
                            val kept = lines.getValue(row.path).filter { it.startsWith("#") || date.matchEntire(it)?.groupValues?.get(1)?.toInt() == year }
                            val some = kept.any { !it.startsWith("#") }
                            if (some) root.resolve(row.path).also { it.parentFile.mkdirs() }.writeText(kept.joinToString("\n", postfix = "\n"))
                            some
                        }
                        val yearRoots = OfficialRoots(root, roots.checkout)
                        for (p in points) {
                            val result = Gate(yearRoots).evaluate(GateManifest(ReachRows.rowsAt(held, unit, p)))
                            val s = result.entries[spec.entry] ?: continue
                            for ((event, e) in s.events) worst.getOrPut(year) { mutableMapOf() }.merge(event, e.worst) { a, b -> maxOf(a, b) }
                            broken += (result.neverEarly(s) + result.overLimit(s)).map { "${spec.entry}/${unit.id} $year at $p: $it" }
                        }
                        root.deleteRecursively()
                    }
                    Years(unit, points.size, worst, broken)
                }
            }.map { it.get() }
            assertTrue(splits.isNotEmpty(), "no Turkish unit's tables hold 2026 and 2027 whole")
            val moved = splits.flatMap { s ->
                s.worst.getValue(2026).mapNotNull { (event, first) ->
                    val second = s.worst.getValue(2027)[event] ?: return@mapNotNull null
                    Triple(s.unit, event, first to second)
                }
            }
            val later = moved.filter { it.third.second > it.third.first }
            val earlier = moved.filter { it.third.second < it.third.first }
            println(
                "tr.diyanet a year at a time: ${splits.size} units whose tables hold 2026 and 2027 whole, ${splits.sumOf { it.points }} points, " +
                    "${moved.size} unit-events; ${later.size} read later in 2027 than in 2026, in ${later.map { it.first.id }.distinct().size} units " +
                    "(by at most ${later.maxOfOrNull { it.third.second - it.third.first } ?: 0} min), ${earlier.size} less late",
            )
            for ((unit, event, w) in later) println("  ${unit.name} ${event.key}: ${w.first} min in 2026, ${w.second} in 2027")
            val broken = splits.flatMap { it.broken }
            if (broken.isNotEmpty()) fail("${broken.size} broken a year at a time:\n" + broken.take(40).joinToString("\n"))
        } finally {
            pool.shutdown()
            temp.deleteRecursively()
        }
    }

    /**
     * Ruling R249's ride only ever moves a start later and an end earlier, and since it every gate row at a Kazakh
     * place's own point is read with the neighbouring places' days riding: no row reads a table's own day alone any
     * more, and a table met early by its own place's day would pass behind a neighbour's wait (the review of 14 Nov
     * 2026). Here every Kazakh own-point and city-point row is read with nothing riding beside the place followed,
     * the day as it was from 9 Oct to 13 Nov 2026 and the one a reach is measured with (ruling R250): no start
     * early and no end late, for the tables held now and for those `prove` adds later.
     */
    @Test
    fun `each kazakh table is never early at its own point with nothing riding beside it`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        if (!roots.held) return
        val rows = OwnPointWorst.rows(manifest).filter { it.entry == KAZAKH }
        assertTrue(rows.isNotEmpty(), "no Kazakh own-point row")
        val result = Gate(roots, change = { it.copy(alongside = emptyList()) }).evaluate(GateManifest(rows))
        assertEquals(rows.size, result.checkedRows, "every Kazakh own-point row read")
        val s = result.entries.getValue(KAZAKH)
        assertEquals(emptyList(), result.neverEarly(s), "a Kazakh table before its own place's day alone")
        println("kz.qmdb own points with nothing riding: ${rows.size} rows, ${s.placeDayCount} place-days, none early, no end late")

        // The second trust audit's G14 (14 Nov 2026): a held table whose own point follows another place (Zhenis's point is
        // inside Zhetysay's city reach) was read above as the city's day beside the user's, never as its own place's day
        // alone, which is the day that rides for everyone else inside its reach (CentralAsia.kt's Alongside). Each such row
        // is read again as its own place's unit, tagged so, with nothing riding: no start early, no end late.
        val entry = requireNotNull(Registry.byId(KAZAKH))
        val units = requireNotNull(Units.of(KAZAKH)).units
        val byPoint = units.groupBy { it.point }
        // A row at no place's own point is an app city's row, read above as the day of the place it follows; a point two
        // places share would leave "its own place" unsaid, so it fails here rather than pass unread (the review of 15 Nov).
        val cityRows = rows.count { byPoint[GeoPoint(requireNotNull(it.lat), requireNotNull(it.lon))] == null }
        val elsewhere = rows.mapNotNull { row ->
            val at = GeoPoint(requireNotNull(row.lat), requireNotNull(row.lon))
            val owners = byPoint[at] ?: return@mapNotNull null
            val own = requireNotNull(owners.singleOrNull()) { "${row.source}:${row.line}: ${owners.size} places share the point ${row.lat}, ${row.lon}" }
            val follows = Registry.resolveEntry(entry, Place(at.lat, at.lon, row.zone, "KZ")).unitId
            if (follows == own.id) null else row.copy(unit = own.id)
        }
        assertTrue(elsewhere.any { it.lat == 40.599543 && it.lon == 68.50476 }, "Zhenis's own point no longer follows another place: look again")
        val alone = Gate(roots, change = { it.copy(alongside = emptyList()) }).evaluate(GateManifest(elsewhere))
        assertEquals(elsewhere.size, alone.checkedRows, "every such row read as its own place")
        val t = alone.entries.getValue(KAZAKH)
        assertEquals(emptyList(), alone.neverEarly(t), "a Kazakh table before its own place's lone day, where its point follows another place")
        println(
            "kz.qmdb held tables whose own point follows another place, read as their own place's day alone: ${elsewhere.size} rows " +
                "(${elsewhere.map { it.unit }.distinct().size} places), ${t.placeDayCount} place-days, none early, no end late; " +
                "$cityRows rows at an app city's point, read above",
        )
    }

    /**
     * Ruling R205's wide limits are the strip's alone (the review of 13 Nov 2026). Kara-Suu's and Osh's units carry
     * limits of their own (Fajr, Asr, Maghrib) for the one strip of their reaches that Aravan town's reach holds too,
     * where the Aravan table rides, and a unit's limit is its whole reach's to the gate; so a regression in central
     * Osh would pass under the strip's number. Here every row of those two units whose point Aravan town's reach
     * does not hold is read on its own, and stays within the entry's limits, as every other locality's rows do.
     */
    @Test
    fun `outside aravan town's reach kara-suu's and osh's rows keep the entry's limits`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        if (!roots.held) return
        val registry = requireNotNull(Registry.byId(KYRGYZ))
        val units = requireNotNull(Units.of(KYRGYZ)).units
        val town = units.single { it.id == "aravan-town" }
        val beside = units.filter { it.id != town.id && it.lateLimits.isNotEmpty() }
        assertEquals(setOf("kara-suu", "osh"), beside.map { it.id }.toSet(), "the localities with limits of their own beside Aravan town")
        val rows = manifest.rows.filter { it.entry == KYRGYZ && it.unit in beside.map { u -> u.id } }
        val (inside, outside) = rows.partition { distanceKm(GeoPoint(requireNotNull(it.lat), requireNotNull(it.lon)), town.point) <= town.radiusKm }
        for (unit in beside) {
            assertTrue(inside.any { it.unit == unit.id }, "${unit.id}: a row inside Aravan town's reach, where its own limits are meant")
            assertTrue(outside.count { it.unit == unit.id } >= 4, "${unit.id}: rows outside Aravan town's reach")
        }
        val over = mutableListOf<String>()
        for (row in outside) {
            val s = Gate(roots).evaluate(GateManifest(listOf(row))).entries.values.single()
            for ((event, e) in s.events) {
                val limit = Gate.lateLimit(event, null, registry, registry.entryClass).minutes
                if (e.worst > limit) over += "${row.where} $KYRGYZ/${row.unit} at ${row.lat}, ${row.lon} ${event.key}: ${e.worst} min, the entry's limit $limit"
            }
        }
        println("beside aravan town: ${outside.size} rows of kara-suu's and osh's outside its reach held to the entry's limits, ${inside.size} inside to the units' own")
        if (over.isNotEmpty()) fail("${over.size} over the entry's limit outside Aravan town's reach:\n" + over.joinToString("\n"))
    }

    /**
     * The limits the tables' own points and the app's city points kept before the reach rows: Umm al-Qura class A's
     * minute, the lag dates' 2 at Fajr, sunrise and the end of eating, the three app points west and south of KACST's
     * (Ash Shafa at Taif, Sultanah at Madinah, Al Hufuf), and Tayma's own; QMDB the entry's Fajr 8, Isha 6, end of
     * eating 8 and class D's 3 ([kazakhOwnLimit]), and for a unit one of whose rows stands in ground another place's
     * reach holds too ([shared], ruling R249) the gate's own limit, each row outside such ground still held to the
     * entry's by the test itself; the Kyrgyz Muftiate class B's 2, Aravan town 4 at Isha and the end of eating
     * ([kyrgyzOwnLimit]), and for a Kyrgyz unit one of whose rows stands in ground it shares ([shared], ruling
     * R205) the gate's own limit, each row outside that ground still held to class B's by the test itself; ESA's towns
     * class B's 2, Taba 3 at sunrise and the end of eating.
     */
    private fun pointLimit(entry: String, unit: String, event: String, shared: Set<String>): Int = when (entry) {
        "sa.ummalqura" -> when {
            unit == "tayma" -> if (event == "fajr") 5 else 3
            event in setOf("fajr", "sunrise", "endOfEating") -> 2
            unit == "taif" && event in setOf("asrStandard", "maghrib", "isha") -> 2
            unit in setOf("madinah", "al-hofuf") && event in setOf("maghrib", "isha") -> 2
            else -> 1
        }
        KAZAKH -> if (unit in shared) recordedLimit(entry, unit, event) else kazakhOwnLimit(event)
        KYRGYZ -> if (unit in shared) recordedLimit(entry, unit, event) else kyrgyzOwnLimit(unit, event)
        // ESA's towns' own points keep class B's 2, and Taba's own earlier sunrise and dawn their 3; the towns' 3 and
        // Taba's 4 are the reach's (eg-esa-reach.tsv, ruling R181 of 13 Nov 2026).
        "eg.esa" -> if (unit == "taba" && event in setOf("sunrise", "endOfEating")) 3 else 2
        // The city-point audit's units (tablesAtTheirPoints): their recorded limits, the per-row worst above holding the rest.
        else -> recordedLimit(entry, unit, event)
    }

    /** The limit the gate itself holds [unit]'s [event] to: the unit's own where it lists the event, else its entry's. */
    private fun recordedLimit(entry: String, unit: String, event: String): Int {
        val registry = requireNotNull(Registry.byId(entry))
        val own = Units.of(entry)?.units?.firstOrNull { it.id == unit }
        return Gate.lateLimit(requireNotNull(Event.byKey(event)), own, registry, own?.entryClass ?: registry.entryClass).minutes
    }

    /**
     * The Kyrgyz localities' own points and the app's points, where no other locality's reach holds them, keep class B's
     * 2 (Aravan town its own sun's 4 at Isha and the end of eating); the entry's 3 and 4 and the units' own are the
     * reach's and the shared ground's (kg-default-reach.tsv, ruling R205).
     */
    private fun kyrgyzOwnLimit(unit: String, event: String): Int = if (unit == "aravan-town" && event in setOf("isha", "endOfEating")) 4 else 2

    private val KYRGYZ = "kg.default"

    /**
     * QMDB's own points and the app's points, where no other place's reach holds them, keep the entry's Fajr 8, Isha 6
     * and end of eating 8 and class D's 3; a unit's own limits beyond them are the shared ground's (ruling R252).
     */
    private fun kazakhOwnLimit(event: String): Int = when (event) {
        "fajr", "endOfEating" -> 8
        "isha" -> 6
        else -> 3
    }

    private val KAZAKH = "kz.qmdb"

    private val STARTS = setOf("fajr", "dhuhr", "asrStandard", "asrHanafi", "maghrib", "isha")

    /**
     * The review's two grids (10 Oct 2026): 16 bearings from 11.25° by 22.5° at a quarter, three quarters, 90 % and
     * 99 % of the radius, offset from the reach rows' grid and from every sweep of generateReachRows; and 24 bearings
     * from 7.5° by 15° at 40, 85 and 99.5 %, which the sweep now includes; both out to 99.9 % and the edge itself, which
     * a reach holds (the review's last check, 10 Oct 2026).
     */
    private val GRIDS: List<Pair<List<Double>, List<Double>>> = listOf(
        (0 until 16).map { 11.25 + 22.5 * it } to listOf(0.25, 0.75, 0.9, 0.99, 0.999, 1.0),
        (0 until 24).map { 7.5 + 15.0 * it } to listOf(0.4, 0.85, 0.995, 0.999, 1.0),
    )

    /**
     * The review of 9 Oct 2026 ("prove it across the unit"): every measured Umm al-Qura and QMDB unit's own tables are
     * replayed across its reach (the reach files, `generateReachRows`), every own table at every point kept, each point
     * inside the unit's reach, and the monitor's recipes extend each unit's rows with each new year of its table (and
     * with each capture of a table the fetcher grows under one key).
     */
    @Test
    fun `every measured unit's own tables are replayed across its reach`() {
        val gate = TestPaths.repoRoot.resolve("tools/timetables/official/gate")
        val manifest = GateManifest.load(gate)
        val recipes = Recipes.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/recipes.tsv"))
        for (spec in ReachRows.SPECS) {
            val reach = manifest.rows.filter { it.source == "${spec.file}" }
            assertTrue(reach.none { it.unit in spec.leftOut }, "${spec.file}: a row of a unit left out")
            for (unit in ReachRows.units(spec)) {
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
                        recipes.any { it.source == source && it.gate == "${spec.file}" && it.entry == "${spec.entry}/${unit.id}" && it.matches("$place-2030") },
                        "${spec.entry}/${unit.id}: no recipe grows its reach rows from $source/$place",
                    )
                }
                // A table the fetcher keeps under one key and grows (no year in its name: Diyanet's districts, ruling R255):
                // each capture that adds days to the own point's rows must add them at every point of the reach too. By a
                // `same` line, which clones the reach rows that read the key's earlier capture at each point they stand
                // at, or by a line naming the unit at `all` the reach's points. A line reading the unit from the monitor's
                // index is not enough for such a key (the review of 14 Nov 2026): the index names nine of Diyanet's older
                // keys without a unit until the fetcher's next run, and such a capture would grow the own point alone.
                val reachRecipes = recipes.filter { it.gate == "${spec.file}" }
                val growing = own.mapNotNull { Regex("""archive/tables/pinned/([a-z-]+)/[0-9-]+/([^./]+)(\.[^/]*)?\.txt""").matchEntire(it.path) }
                    .map { it.groupValues[1] to it.groupValues[2] }.filterNot { (_, key) -> Regex(""".*-\d{4}""").matches(key) }.distinct()
                for ((source, key) in growing) {
                    assertTrue(
                        reachRecipes.any {
                            it.source == source && it.matches(key) &&
                                (it.entry == "same" || (it.entry == "${spec.entry}/${unit.id}" && it.point == "all"))
                        },
                        "${spec.entry}/${unit.id}: no recipe grows its reach rows from $source/$key whatever the index says",
                    )
                }
                // A unit whose own tables were all read by hand from the pages the monitor fetches (archive/tables/held/
                // <source>/: Diyanet's 62 il centres of 12–13 Nov 2026) has no earlier capture to clone: its first capture
                // reaches the reach only by a line that reads the unit from the index, at `all` the reach's points.
                val byHand = own.mapNotNull { Regex("""archive/tables/held/([a-z-]+)/[^/]+\.txt""").matchEntire(it.path) }
                    .map { it.groupValues[1] }.distinct().filter { source -> growing.none { it.first == source } }
                for (source in byHand) {
                    assertTrue(
                        reachRecipes.any { it.source == source && it.capture == "*" && it.entry == "index" && it.point == "all" },
                        "${spec.entry}/${unit.id}: read by hand from $source, and no recipe reads its first capture across its reach",
                    )
                }
            }
        }
    }

    /**
     * Ruling R297: every figure claimed across an area ([ReachAreas.AREAS]) has its reach rows, each at a point the app
     * claims the figure at, each reading every one of the claim's own tables there, and every recipe line that grows the
     * own rows has its twin on the claim's file, so that a new year is read across the claim too. A country's cell holds
     * every app city of the country (ruling R307), and every claim's box holds it whole. Claims may share a file (JAKIM's
     * zones, ruling R310): every row of such a file is one claim's, and a set read zone by zone ([ReachAreas.Handed]) has
     * a claim for every measured unit.
     */
    @Test
    fun `every figure claimed across an area is read across it and grown with it`() {
        val gate = TestPaths.repoRoot.resolve("tools/timetables/official/gate")
        val manifest = GateManifest.load(gate)
        val recipes = Recipes.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/recipes.tsv"))
        val cities = ReachAreas.Cities(TestPaths.appFiles.resolve("cities.csv"))
        for ((file, claims) in ReachAreas.AREAS.groupBy { it.file }) {
            val rows = manifest.rows.filter { it.source == file }
            assertTrue(rows.isNotEmpty(), "$file: no rows")
            assertTrue(
                rows.all { r -> r.split == Split.TEST && claims.any { r.entry == it.entry && r.unit == it.unit } },
                "$file: a row no claim of the file's, or not held out",
            )
        }
        for (entry in ReachAreas.AREAS.filter { it.claim is ReachAreas.Handed }.map { it.entry }.distinct()) {
            val units = requireNotNull(Units.of(entry)).units.filter { it.measured }.map { it.id }.toSet()
            assertEquals(units, ReachAreas.AREAS.filter { it.entry == entry && it.claim is ReachAreas.Handed }.mapNotNull { it.unit }.toSet(), "$entry: a measured unit with no claim")
        }
        for (a in ReachAreas.AREAS) {
            val own = ReachAreas.ownRows(manifest, a)
            assertTrue(own.isNotEmpty(), "${a.tag}: no own row")
            val mine = manifest.rows.filter { it.source == a.file && it.entry == a.entry && it.unit == a.unit }
            val points = mine.map { GeoPoint(it.lat!!, it.lon!!) }.distinct()
            assertTrue(points.size >= 4, "${a.file} ${a.tag}: fewer points than the claim's four extremes")
            for (p in points) {
                assertTrue(ReachAreas.claimed(a, p, cities), "${a.file} ${a.tag}: $p is no point of the claim")
                assertEquals(own.map { it.path }.toSet(), mine.filter { it.lat == p.lat && it.lon == p.lon }.map { it.path }.toSet(), "${a.file} ${a.tag} at $p: not its own tables")
            }
            // A unit's claim (rulings R300 and R310) grows as its unit's reach rows grow (the radius file's lines for the
            // unit: none for Brunei's book, read by hand like Bahrain's); a claim of the whole group's table, as the group
            // file grows.
            val grows = if (a.claim is ReachAreas.Chosen || a.claim is ReachAreas.Handed) {
                recipes.filter { it.gate == "${a.group}-reach.tsv" && it.entry == a.tag }
            } else {
                recipes.filter { it.gate == "${a.group}.tsv" }
            }
            for (line in grows) {
                assertTrue(
                    recipes.any { it.gate == a.file && it.source == line.source && it.capture == line.capture && it.entry == a.tag && it.point == "all" },
                    "${a.tag}: ${line.where} grows ${line.gate}, and no line grows ${a.file} from ${line.source} ${line.capture}",
                )
            }
            // Ruling R307: a country's claim is the app's whole nearest-city cell of it, so every app city of the country is
            // a point of the claim (a fix there, under the country's own zone, is its own nearest city).
            if (a.claim is ReachAreas.Cell) {
                val cityRows = TestPaths.appFiles.resolve("cities.csv").readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
                    .filter { it[4].uppercase() == a.country }
                assertTrue(cityRows.isNotEmpty(), "${a.entry}: no app city of ${a.country}")
                val held = cityRows.count { ReachAreas.claimed(a, GeoPoint(it[5].toDouble(), it[6].toDouble()), cities) }
                assertEquals(cityRows.size, held, "${a.entry}: ${cityRows.size - held} of ${a.country}'s app cities outside its cell")
            }
            // The box holds the claim whole: no point of its outermost grid lines is claimed (the sweep stops otherwise;
            // held here too, so a cities.csv change that grows a cell past its box is caught without regenerating the rows).
            assertEquals(emptyList<GeoPoint>(), edgeClaimed(a, cities), "${a.entry}: the claim reaches its box's edge: widen ReachAreas.AREAS' box")
            // The rule bites: a country's cell on a box shrunk to its middle has claimed points on its edge.
            if (a.claim is ReachAreas.Cell) {
                val b = a.box
                val shrunk = ReachAreas.AreaClaim(
                    a.entry, a.group, a.unit, a.country, a.zone,
                    b.copy(south = (3 * b.south + b.north) / 4, north = (b.south + 3 * b.north) / 4, west = (3 * b.west + b.east) / 4, east = (b.west + 3 * b.east) / 4),
                    a.stepKm, a.claim, a.what, a.file,
                )
                assertTrue(edgeClaimed(shrunk, cities).isNotEmpty(), "${a.entry}: a box shrunk to its middle found no claimed point on its edge")
            }
        }
    }

    /** The points of [a]'s grid on its box's outermost lines that the app claims [a]'s figure at (none, for a box that holds the claim). */
    private fun edgeClaimed(a: ReachAreas.AreaClaim, cities: ReachAreas.Cities): List<GeoPoint> {
        val rows = ReachAreas.grid(a)
        val edge = rows.first() + rows.last() + rows.drop(1).dropLast(1).flatMap { listOf(it.first(), it.last()) }
        return edge.filter { ReachAreas.claimed(a, it, cities) }
    }

    /**
     * Ruling R300: a unit set whose `choose` hands a measured unit to a place beyond the unit's own circle claims the
     * unit's figure there, past what the radius sweep reads. Among the app's cities of each entry's countries (with their
     * region, as the app places them), JAKIM's named places, and a grid of [GRID_DEGREES] over each such entry's countries
     * (their cities' box widened by half a degree, each point placed as its nearest city is: country, region and zone, and
     * kept where that is one of the entry's countries), the entries where that happens are exactly
     * [ReachAreas.CHOSEN_BEYOND]'s, so the release hold ([ReachAreas.areaClaims]) names each; and where [ReachAreas.AREAS]
     * reads such an entry, every place found is a point one of its claims reads, for the unit chosen there. The grid is
     * coarse (about 5.5 km): it finds a set that chooses beyond its circles over a town's worth of ground, not a sliver.
     */
    /** The spacing, in degrees, of the grid the chosen-beyond test reads (ruling R300). */
    private val GRID_DEGREES = 0.05

    @Test
    fun `every measured unit chosen beyond its own circle is named and read where its entry is read`() {
        val rows = TestPaths.appFiles.resolve("cities.csv").readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
        val cities = rows.map { Place(it[5].toDouble(), it[6].toDouble(), it[7], it[4].uppercase(), it[2].ifBlank { null }) }
        val named = JakimZones.zones.flatMap { z -> z.places.map { Place(it.lat, it.lon, "Asia/Kuala_Lumpur", "MY") } }
        val areaCities = ReachAreas.Cities(TestPaths.appFiles.resolve("cities.csv"))
        val index = FixCountry.Index(cities, { it.lat }, { it.lon }, { it.countryCode }, { it.zoneId })
        fun grid(countries: Set<String>): List<Place> {
            val own = cities.filter { it.countryCode in countries }.takeIf { it.isNotEmpty() } ?: return emptyList()
            val (south, north) = own.minOf { it.lat } - 0.5 to own.maxOf { it.lat } + 0.5
            val (west, east) = own.minOf { it.lon } - 0.5 to own.maxOf { it.lon } + 0.5
            val out = ArrayList<Place>()
            for (i in 0..((north - south) / GRID_DEGREES).toInt()) {
                for (j in 0..((east - west) / GRID_DEGREES).toInt()) {
                    val (lat, lon) = south + i * GRID_DEGREES to west + j * GRID_DEGREES
                    val city = index.nearest(lat, lon)?.first ?: continue
                    if (city.countryCode in countries) out += Place(lat, lon, city.zoneId, city.countryCode, city.admin1)
                }
            }
            return out
        }
        val found = sortedMapOf<String, List<Pair<Place, AuthorityUnit>>>()
        var gridded = 0
        for (entry in Registry.entries) {
            val set = Units.of(entry.id) ?: continue
            val grid = if (set.choose != null && set.units.any { it.measured && it.radiusKm > 0.0 }) grid(entry.countries) else emptyList()
            gridded += grid.size
            val places = cities.filter { it.countryCode in entry.countries } + (if (entry.id == "my.jakim") named else emptyList()) + grid
            ReachAreas.chosenBeyond(set, places).takeIf { it.isNotEmpty() }?.let { found[entry.id] = it }
        }
        println("chosen beyond their circles, on ${cities.size} cities, ${named.size} named places and $gridded grid points: " +
            found.mapValues { (_, v) -> "${v.size} places, units ${v.map { it.second.id }.distinct().sorted()}" })
        assertEquals(ReachAreas.CHOSEN_BEYOND.keys.sorted(), found.keys.toList(), found.mapValues { (_, v) -> v.take(3).map { "${it.second.id} at ${it.first.lat},${it.first.lon}" } }.toString())
        for ((entry, hits) in found) {
            val claims = ReachAreas.AREAS.filter { it.entry == entry }
            if (claims.isEmpty()) continue // owed: ReachRows.unswept names the entry, and a release waits
            for ((place, unit) in hits) {
                assertTrue(
                    claims.any { it.unit == unit.id && ReachAreas.claimed(it, GeoPoint(place.lat, place.lon), areaCities) },
                    "$entry: ${unit.id} is chosen at ${place.lat},${place.lon}, ${"%.1f".format(distanceKm(GeoPoint(place.lat, place.lon), unit.point))} km " +
                        "from its point, past its ${unit.radiusKm} km, and no area claim reads it",
                )
            }
        }
    }

    /**
     * Ruling R297, with the archive: every figure claimed across an area read on a grid offset from the sweep's two
     * ([ReachAreas.sweep]: twice its step, shifted by 0.6 of it). Never early, no end late, nothing over its limit, and no
     * event beyond the claim's own figure in its committed stamp (the unit's row, or the entry's where it has no units):
     * the sweep found the claim's true worst.
     */
    @Test
    fun `across every area claim off its reach rows nothing is early or beyond its figure`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        if (!roots.held) return
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val cities = ReachAreas.Cities(TestPaths.appFiles.resolve("cities.csv"))
        val pool = Executors.newFixedThreadPool(4)
        val broken = mutableListOf<String>()
        try {
            for (a in ReachAreas.AREAS) {
                val stamp = requireNotNull(ProofStamps.of(a.entry))
                val figure = if (a.unit == null) stamp.worstLateMinutes else stamp.worstLateByUnit.getValue(a.unit!!)
                val own = ReachAreas.ownRows(manifest, a)
                val points = ReachAreas.grid(a, a.stepKm * 2, shift = 0.6).flatten().filter { ReachAreas.claimed(a, it, cities) }
                val tally = Tally()
                val beyond = mutableListOf<String>()
                val results = points.map { p -> pool.submit<Pair<GeoPoint, GateResult>> { p to Gate(roots).evaluate(GateManifest(ReachAreas.rowsAt(own, a, p))) } }
                for (f in results) {
                    val (p, result) = f.get()
                    tally.add(result, a.entry)
                    val s = result.entries[a.entry] ?: continue
                    for ((event, e) in s.events) {
                        val key = if (event.key.startsWith("asr") && "asr" in figure) "asr" else event.key
                        val most = figure[key] ?: figure[event.key]
                        if (most == null || e.worst > most) beyond += "${a.entry} at $p: ${event.key} ${e.worst} min, its figure $most"
                    }
                }
                println("${a.entry} off its reach rows: ${points.size} points, ${tally.placeDays} place-days; worst " + tally.worst.entries.joinToString { "${it.key.key} ${it.value}" })
                broken += tally.broken.map { "${a.entry}: $it" } + beyond
            }
        } finally {
            pool.shutdown()
        }
        if (broken.isNotEmpty()) fail("${broken.size} broken off the area claims' reach rows:\n" + broken.take(40).joinToString("\n"))
    }

    /**
     * Rulings R287 (the re-audit's F1, F2, C4 and C6, 15 Nov 2026): a reach file's rows must grow with every capture its
     * unit's own rows grow with, or the first such capture adds an own row the reach does not have, the test above fails
     * and `prove` halts. Before any capture arrives, for every unit of every reach file:
     *
     * - each printed imsak its own point reads in a fetcher's own layout (`archive/tables/imsak/<source>/<date>/<key>.txt`,
     *   the source's `<key>-imsak` capture, ruling R284) has an `-imsak` line on the reach file that reads the next
     *   year's capture: only a pattern so named reads an imsak (`Recipe.matches`), so a day's line is no twin;
     * - each table read by hand from a source the monitor fetches (`archive/tables/manual/<source>/<date>/<key>-<yyyy>.txt`
     *   where some recipe line reads that source: JAKIM's 51 zones of 13 Nov 2026) has a line on the reach file that reads
     *   the next year's capture of it.
     *
     * A line reads it when it names the unit at `all` the reach's points, or reads the unit from the monitor's index at
     * `all` of them; a `same` line never does (it clones rows that read an earlier capture, so it cannot read a first
     * one), and a capture of a source no recipe reads (the PA Iftaa's, read by hand only) owes nothing here.
     */
    @Test
    fun `every reach row a capture can grow has a recipe line, the printed imsak too`() {
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val recipes = Recipes.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/recipes.tsv"))
        val fetchedSources = recipes.map { it.source }.toSet()
        val missing = ArrayList<String>()
        var imsakUnits = 0
        for (spec in ReachRows.SPECS) {
            if (manifest.rows.none { it.source == "${spec.file}" }) continue
            val onReach = recipes.filter { it.gate == "${spec.file}" }
            for (unit in ReachRows.units(spec)) {
                val own = ReachRows.ownRows(manifest, spec, unit)
                fun reads(source: String, key: String) = onReach.any {
                    it.source == source && it.matches(key) && it.point == "all" &&
                        (it.entry == "${spec.entry}/${unit.id}" || it.entry == "index")
                }
                val imsak = own.mapNotNull { Regex("""archive/tables/imsak/([a-z-]+)/[0-9-]+/(.+)-\d{4}\.txt""").matchEntire(it.path) }
                    .map { it.groupValues[1] to it.groupValues[2] }.distinct()
                if (imsak.isNotEmpty()) imsakUnits++
                for ((source, place) in imsak) {
                    if (!reads(source, "$place-2030-imsak")) missing += "${spec.file} ${spec.entry}/${unit.id}: no -imsak line reads $source/$place-2030-imsak across its reach"
                }
                val byHand = own.mapNotNull { Regex("""archive/tables/manual/([a-z-]+)/[0-9-]+/(.+)-\d{4}\.txt""").matchEntire(it.path) }
                    .map { it.groupValues[1] to it.groupValues[2] }.distinct().filter { (source, _) -> source in fetchedSources }
                for ((source, place) in byHand) {
                    if (!reads(source, "$place-2030")) missing += "${spec.file} ${spec.entry}/${unit.id}: no line reads $source/$place-2030 across its reach"
                }
            }
        }
        println("Reach recipes (R287): $imsakUnits reach units hold a printed imsak; every such unit and every hand-read table of a fetched source has its line")
        assertTrue(imsakUnits > 0, "no reach unit holds a printed imsak: the check would hold nothing")
        assertTrue(missing.isEmpty(), "${missing.size} reach rows no recipe line would grow (official/monitor/recipes.tsv):\n" + missing.joinToString("\n"))
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

        fun add(result: GateResult?, entry: String = "kz.qmdb") {
            val s = result?.entries?.get(entry) ?: return
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
