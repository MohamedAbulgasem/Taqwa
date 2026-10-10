package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.GateRow
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Split
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Writes the reach rows of the two authorities whose every place is a unit, Umm al-Qura's (KACST's 173 places) and
 * QMDB's (its 86 measured places): `official/gate/sa-ummalqura-reach.tsv` and `kz-qmdb-reach.tsv` (the reviews of
 * 9 and 10 Oct 2026, "prove it across the unit", London's precedent). A unit's figure was proven at its own point and
 * at the app's point for its city only; inside the unit a user can be up to its reach from both. So each measured
 * unit's own tables (the rows its group file reads at the unit's point) are replayed, held out (split=test) and tagged
 * with the unit (`<entry>/<unit>`, so the gate checks the point resolves there):
 *
 * - at [BEARINGS] bearings and [SHARES] of its radius, a fixed grid the monitor's recipes extend each new year;
 * - and at each event's worst point of a dense sweep ([sweep]: 392 points a unit, and a finer sweep of 1,560 more
 *   where fewer than [MIN_SWEPT] of them fall inside the unit, against the unit's own tables in the archive): the
 *   lateness that decides the unit's figure and limits sits near the edge and between bearings, and in a unit that
 *   other units hem in (Fayfa: 15 of a 72-point grid inside it), where a sparse grid misses it by a minute.
 *
 * A point another unit takes (a nearer place, or a city whose reach holds it) is left out: its user follows that
 * unit's table. The unit's stamp row then holds the lateness across its area, which About reads. `CityUnitsTest`
 * checks the result on a third grid, offset from both.
 *
 *     ./gradlew -p tools/timetables generateReachRows [-Pthreads=N]
 *
 * Needs the archive. Re-run after a unit, a reach, a unit's table or the rule that picks a unit changes, then run the
 * gate and commit. Coordinates are rounded to six decimals, the point then resolved again; no printed time is written.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) ?: fail("$name needs a value") }
    val repo = File(option("--repo") ?: fail("--repo is required"))
    val official = File(option("--official") ?: fail("--official is required"))
    val threads = option("--threads")?.toInt() ?: Runtime.getRuntime().availableProcessors()
    val roots = OfficialRoots(official, repo.resolve("tools/timetables/official"))
    if (!roots.held) fail("no archive at $official: the sweep reads each unit's own tables")
    val gate = repo.resolve("tools/timetables/official/gate")
    for (spec in ReachRows.SPECS) {
        val started = System.nanoTime()
        val text = ReachRows.render(spec, gate, roots, threads)
        repo.resolve("tools/timetables/official/gate/${spec.group}-reach.tsv").writeText(text.file)
        println(
            "${spec.entry}: ${text.units} units, ${text.points} points kept of ${text.tried} on the grid and ${text.worst} " +
                "worst points of the sweep (${text.swept} swept, ${text.fine} units finely), ${text.rows} rows, ${text.broken} cells early or late in the " +
                "sweep, ${"%.0f".format((System.nanoTime() - started) / 1e9)} s",
        )
        if (text.broken > 0) fail("${spec.entry}: the sweep found ${text.broken} cells early or late")
    }
}

private fun fail(problem: String): Nothing {
    System.err.println("generateReachRows: $problem")
    exitProcess(2)
}

/** The reach rows' layout and the points they are read at. */
object ReachRows {
    /** One authority: its entry, its group file (the unit's own rows) and its country. */
    class Spec(val entry: String, val group: String, val country: String)

    val SPECS = listOf(Spec("sa.ummalqura", "sa-ummalqura", "SA"), Spec("kz.qmdb", "kz-qmdb", "KZ"))

    /** The fixed grid. */
    val BEARINGS: List<Double> = (0 until 8).map { it * 45.0 }
    val SHARES: List<Double> = listOf(0.5, 0.95)

    /**
     * The sweep's grids, as (bearings, shares): 40 bearings from 4.5° by 9° at eight shares out to the edge itself
     * (a reach holds its edge; the review found Isha's worst at 99.9 %, 10 Oct 2026),
     * and the review's fourth grid, 24 bearings from 7.5° by 15° at 40, 85 and 99.5 % (10 Oct 2026). `CityUnitsTest`
     * checks the rows on that grid and on another, offset from all of these.
     */
    val SWEEP: List<Pair<List<Double>, List<Double>>> = listOf(
        (0 until 40).map { 4.5 + 9.0 * it } to listOf(0.35, 0.6, 0.8, 0.93, 0.97, 0.995, 0.999, 1.0),
        (0 until 24).map { 7.5 + 15.0 * it } to listOf(0.4, 0.85, 0.995),
    )

    /** The finer sweep where fewer than [MIN_SWEPT] points of [SWEEP] fall inside the unit: 120 bearings from 1.5° by 3°. */
    val FINE: Pair<List<Double>, List<Double>> =
        (0 until 120).map { 1.5 + 3.0 * it } to listOf(0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.92, 0.97, 0.995, 0.999, 1.0)

    /** Half of [SWEEP]'s 392 points. */
    const val MIN_SWEPT = 196

    /** The points of the sweep around [unit] inside it, the finer sweep's too where [SWEEP]'s are few. */
    fun sweep(spec: Spec, unit: AuthorityUnit, zone: String): Pair<List<Point>, Boolean> {
        val coarse = SWEEP.flatMap { (bearings, shares) -> points(spec, unit, zone, shares, bearings) }
        val fine = coarse.size < MIN_SWEPT
        val all = if (fine) coarse + points(spec, unit, zone, FINE.second, FINE.first) else coarse
        return all.distinctBy { it.at } to fine
    }

    /** A unit's own rows: its group file's rows of the entry itself (no unit named) at the unit's own point. */
    fun ownRows(manifest: GateManifest, spec: Spec, unit: AuthorityUnit): List<GateRow> = manifest.rows.filter {
        it.source == "${spec.group}.tsv" && it.entry == spec.entry && it.unit == null && it.member == null &&
            it.lat == unit.point.lat && it.lon == unit.point.lon
    }

    /** A point of a grid around a unit: where, at what share of its radius and bearing. */
    class Point(val at: GeoPoint, val share: Double, val bearing: Double)

    /** The points of [shares] × [bearings] around [unit] that resolve to [unit] once rounded. */
    fun points(spec: Spec, unit: AuthorityUnit, zone: String, shares: List<Double> = SHARES, bearings: List<Double> = BEARINGS): List<Point> {
        val entry = requireNotNull(Registry.byId(spec.entry))
        return shares.flatMap { share -> bearings.map { b -> Triple(share, b, QmdbReach.moved(unit.point, unit.radiusKm * share, b)) } }
            .map { (share, b, at) -> Point(GeoPoint(round6(at.lat), round6(at.lon)), share, b) }
            .filter { Registry.resolveEntry(entry, Place(it.at.lat, it.at.lon, zone, spec.country)).unitId == unit.id }
    }

    private fun round6(x: Double): Double = String.format(Locale.ROOT, "%.6f", x).toDouble()

    /** [own] read at [at] for [unit], held out and tagged. */
    fun rowsAt(own: List<GateRow>, unit: AuthorityUnit, at: GeoPoint): List<GateRow> =
        own.map { it.copy(lat = at.lat, lon = at.lon, unit = unit.id, split = Split.TEST) }

    /** Each event's worst point of the sweep around [unit] (the first reached where several tie), and the sweep's broken cells. */
    class Worst(val points: Map<Point, List<Event>>, val swept: Int, val broken: Int, val fine: Boolean)

    fun worst(spec: Spec, unit: AuthorityUnit, own: List<GateRow>, roots: OfficialRoots): Worst {
        val (swept, fine) = sweep(spec, unit, own.first().zone)
        val best = LinkedHashMap<Event, Pair<Point, Int>>()
        var broken = 0
        for (p in swept) {
            val s = Gate(roots).evaluate(GateManifest(rowsAt(own, unit, p.at))).entries[spec.entry] ?: continue
            for ((event, e) in s.events) {
                broken += e.early + e.lateEnd
                if (e.worst > (best[event]?.second ?: -1)) best[event] = p to e.worst
            }
        }
        val points = LinkedHashMap<Point, MutableList<Event>>()
        for ((event, pw) in best) points.getOrPut(pw.first) { mutableListOf() } += event
        return Worst(points, swept.size, broken, fine)
    }

    class Rendered(
        val file: String,
        val units: Int,
        val points: Int,
        val tried: Int,
        val worst: Int,
        val swept: Int,
        val rows: Int,
        val broken: Int,
        val fine: Int,
    )

    fun render(spec: Spec, gate: File, roots: OfficialRoots, threads: Int): Rendered {
        val manifest = GateManifest.load(gate).only(groups = setOf(spec.group))
        val source = gate.resolve("${spec.group}.tsv").readLines()
        val header = source.first { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }
        val names = header.split('\t').map { it.trim() }
        fun col(name: String) = names.indexOf(name).also { require(it >= 0) { "${spec.group}.tsv has no $name column" } }
        val (iEntry, iLat, iLon, iSplit, iNote) = listOf(col("entry"), col("lat"), col("lon"), col("split"), col("note"))
        val units = requireNotNull(Units.of(spec.entry)).units.filter { it.measured }
        val pool = Executors.newFixedThreadPool(threads)
        val worsts = try {
            units.map { unit ->
                pool.submit<Worst> {
                    val own = ownRows(manifest, spec, unit)
                    require(own.isNotEmpty()) { "${spec.entry}/${unit.id}: no row of ${spec.group}.tsv at the unit's own point" }
                    worst(spec, unit, own, roots)
                }
            }.map { it.get() }
        } finally {
            pool.shutdown()
        }
        val out = StringBuilder()
        out.append("# GENERATED by `./gradlew -p tools/timetables generateReachRows` (units/GenerateReachRows.kt); prove extends the\n")
        out.append("# grid's rows each new year through official/monitor/recipes.tsv. ").append(spec.entry).append("'s measured units, each\n")
        out.append("# unit's own tables (").append(spec.group).append(".tsv's rows at its point) replayed across its area: at ")
        out.append(BEARINGS.size).append(" bearings and ").append(SHARES.joinToString(" and ") { "${(it * 100).toInt()} %" })
        out.append(" of its radius, and at\n# each event's worst point of a sweep of ").append(SWEEP.sumOf { it.first.size * it.second.size })
        out.append(" points a unit (and a finer one where few fall inside it); held out and tagged with the unit, a point another\n")
        out.append("# unit takes left out\n")
        out.append("# (the reviews of 9 and 10 Oct 2026, \"prove it across the unit\"). Coordinates and the tables' own paths only,\n")
        out.append("# never a time.\n")
        out.append(header).append('\n')
        var points = 0
        var tried = 0
        var worstPoints = 0
        var swept = 0
        var rows = 0
        var withRows = 0
        var broken = 0
        for ((unit, worst) in units.zip(worsts)) {
            val own = ownRows(manifest, spec, unit)
            val grid = points(spec, unit, own.first().zone)
            tried += SHARES.size * BEARINGS.size
            points += grid.size
            swept += worst.swept
            broken += worst.broken
            val onGrid = grid.map { it.at }.toSet()
            val extra = worst.points.filterKeys { it.at !in onGrid }
            worstPoints += extra.size
            if (grid.isNotEmpty() || extra.isNotEmpty()) withRows++
            val radius = String.format(Locale.ROOT, "%.1f", unit.radiusKm)
            fun emit(p: Point, what: String) {
                for (row in own) {
                    val cells = source[row.line - 1].split('\t').toMutableList()
                    cells[iEntry] = "${spec.entry}/${unit.id}"
                    cells[iLat] = String.format(Locale.ROOT, "%.6f", p.at.lat)
                    cells[iLon] = String.format(Locale.ROOT, "%.6f", p.at.lon)
                    cells[iSplit] = "test"
                    cells[iNote] = "${unit.name}'s own table $what at ${String.format(Locale.ROOT, "%.1f", p.share * 100)} % of its " +
                        "$radius km reach, bearing ${String.format(Locale.ROOT, "%.1f", p.bearing)}: ${cells[iNote]}"
                    out.append(cells.joinToString("\t")).append('\n')
                    rows++
                }
            }
            for (p in grid) emit(p, "on the grid")
            for ((p, events) in extra) emit(p, "at the sweep's worst for ${events.joinToString("/") { it.key }}")
        }
        return Rendered(out.toString(), withRows, points, tried, worstPoints, swept, rows, broken, worsts.count { it.fine })
    }
}
