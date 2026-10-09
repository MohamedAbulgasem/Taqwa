package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.GateRow
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Writes the reach rows of the two authorities whose every place is a unit, Umm al-Qura's (KACST's 173 places) and
 * QMDB's (its 86 measured places): `official/gate/sa-ummalqura-reach.tsv` and `kz-qmdb-reach.tsv` (the review of
 * 9 Oct 2026, "prove it across the unit", London's precedent). A unit's figure was proven at its own point and at the
 * app's point for its city only; inside the unit a user can be up to its reach from both. So each measured unit's own
 * tables (the rows its group file reads at the unit's point) are replayed at [BEARINGS] bearings and [SHARES] of its
 * radius, held out (split=test) and tagged with the unit (`<entry>/<unit>`, so the gate checks the point resolves
 * there). A point another unit takes (a nearer place, or a city whose reach holds it) is left out: its user follows
 * that unit's table. The unit's stamp row then holds the lateness across its area, which About reads.
 *
 *     ./gradlew -p tools/timetables generateReachRows
 *
 * Re-run after a unit, a reach or the rule that picks a unit changes, and after `prove` adds a unit's own row the
 * monitor's recipes did not extend (each new year of a unit's own table is added here by the recipes,
 * official/monitor/recipes.tsv), then run the gate and commit. Coordinates are rounded to six decimals, the point
 * then resolved again; no printed time is read or written.
 */
fun main(args: Array<String>) {
    val at = args.indexOf("--repo")
    val repo = File(if (at >= 0) args.getOrNull(at + 1) ?: fail("--repo needs a value") else fail("--repo is required"))
    val gate = repo.resolve("tools/timetables/official/gate")
    for (spec in ReachRows.SPECS) {
        val text = ReachRows.render(spec, gate)
        repo.resolve("tools/timetables/official/gate/${spec.group}-reach.tsv").writeText(text.file)
        println("${spec.entry}: ${text.units} units, ${text.points} points kept of ${text.tried}, ${text.rows} rows")
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

    val BEARINGS: List<Double> = (0 until 8).map { it * 45.0 }
    val SHARES: List<Double> = listOf(0.5, 0.95)

    /** A unit's own rows: its group file's rows of the entry itself (no unit named) at the unit's own point. */
    fun ownRows(manifest: GateManifest, spec: Spec, unit: AuthorityUnit): List<GateRow> = manifest.rows.filter {
        it.source == "${spec.group}.tsv" && it.entry == spec.entry && it.unit == null && it.member == null &&
            it.lat == unit.point.lat && it.lon == unit.point.lon
    }

    /** The points around [unit] its reach rows are read at: those that resolve to [unit] once rounded. */
    fun points(spec: Spec, unit: AuthorityUnit, zone: String): List<Triple<GeoPoint, Double, Double>> {
        val entry = requireNotNull(Registry.byId(spec.entry))
        return SHARES.flatMap { share -> BEARINGS.map { b -> Triple(share, b, QmdbReach.moved(unit.point, unit.radiusKm * share, b)) } }
            .map { (share, b, at) -> Triple(GeoPoint(round6(at.lat), round6(at.lon)), share, b) }
            .filter { (at, _, _) -> Registry.resolveEntry(entry, Place(at.lat, at.lon, zone, spec.country)).unitId == unit.id }
    }

    private fun round6(x: Double): Double = String.format(Locale.ROOT, "%.6f", x).toDouble()

    class Rendered(val file: String, val units: Int, val points: Int, val tried: Int, val rows: Int)

    fun render(spec: Spec, gate: File): Rendered {
        val manifest = GateManifest.load(gate).only(groups = setOf(spec.group))
        val source = gate.resolve("${spec.group}.tsv").readLines()
        val header = source.first { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }
        val names = header.split('\t').map { it.trim() }
        fun col(name: String) = names.indexOf(name).also { require(it >= 0) { "${spec.group}.tsv has no $name column" } }
        val (iEntry, iLat, iLon, iSplit, iNote) = listOf(col("entry"), col("lat"), col("lon"), col("split"), col("note"))
        val units = requireNotNull(Units.of(spec.entry)).units.filter { it.measured }
        val out = StringBuilder()
        out.append("# GENERATED by `./gradlew -p tools/timetables generateReachRows` (units/GenerateReachRows.kt); prove extends it\n")
        out.append("# each new year through official/monitor/recipes.tsv. ").append(spec.entry).append("'s measured units, each\n")
        out.append("# unit's own tables (").append(spec.group).append(".tsv's rows at its point) replayed across its area: at ")
        out.append(BEARINGS.size).append(" bearings and ").append(SHARES.joinToString(" and ") { "${(it * 100).toInt()} %" })
        out.append(" of its radius,\n# held out and tagged with the unit, a point another unit takes left out (the review of 9 Oct 2026,\n")
        out.append("# \"prove it across the unit\"). Coordinates and the tables' own paths only, never a time.\n")
        out.append(header).append('\n')
        var points = 0
        var tried = 0
        var rows = 0
        var withRows = 0
        for (unit in units) {
            val own = ownRows(manifest, spec, unit)
            require(own.isNotEmpty()) { "${spec.entry}/${unit.id}: no row of ${spec.group}.tsv at the unit's own point" }
            val kept = points(spec, unit, own.first().zone)
            tried += SHARES.size * BEARINGS.size
            points += kept.size
            if (kept.isNotEmpty()) withRows++
            for ((at, share, bearing) in kept) {
                for (row in own) {
                    val cells = source[row.line - 1].split('\t').toMutableList()
                    cells[iEntry] = "${spec.entry}/${unit.id}"
                    cells[iLat] = String.format(Locale.ROOT, "%.6f", at.lat)
                    cells[iLon] = String.format(Locale.ROOT, "%.6f", at.lon)
                    cells[iSplit] = "test"
                    cells[iNote] = "${unit.name}'s own table at ${(share * 100).toInt()} % of its ${String.format(Locale.ROOT, "%.1f", unit.radiusKm)} km reach, " +
                        "bearing ${bearing.toInt()}: ${cells[iNote]}"
                    out.append(cells.joinToString("\t")).append('\n')
                    rows++
                }
            }
        }
        return Rendered(out.toString(), withRows, points, tried, rows)
    }
}
