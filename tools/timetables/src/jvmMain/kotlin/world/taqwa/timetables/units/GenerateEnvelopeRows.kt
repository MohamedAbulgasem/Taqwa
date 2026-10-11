package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.GateRow
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Writes the envelope's promise-only rows, `official/gate/<group>-envelope.tsv` (the city-point round 2 review of 11 Oct
 * 2026, "nothing in the gate guards the round's main claim"). Under the owner's rule "rather late, never early" a place is
 * never before any table printed for its own point whose bound reach holds it, nor any end after one, not only the table
 * it follows (`TableEnvelope`). So every such table's own rows (its group file's rows at the table's point) are replayed
 * at every app city (`cities.csv`) of its entry's countries within its bound reach ([EnvelopeRows.SETS]: R40's class D
 * reach, three minutes of longitude), whichever unit the city follows, or none. And by name ([EnvelopeRows.NAMED]): the
 * Takvim at the app's Kosovo cities, Kénitra's Habous table at Sidi Taibi, Lakemba's tables at Sydney's west, London's
 * Unified Timetable where it is chosen outside the M25, and IACAD's tables where they are chosen outside its zones.
 *
 * The gate holds these rows to the promise alone ([GateRow.promiseOnly]): an early start or a late end breaks it as
 * anywhere, but their lateness, against a table the city may not follow, is no unit's figure and no limit's. A row the
 * group files already hold at that city (the app's own point rows) is not written again.
 *
 *     ./gradlew -p tools/timetables generateEnvelopeRows
 *
 * Re-run after a table printed for its own point, its rows or its reach change, or after `prove` adds a year to a table's
 * rows, then run the gate and commit. Coordinates are the app's own, as `cities.csv` gives them; no printed time is written.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) ?: fail("$name needs a value") }
    val repo = File(option("--repo") ?: fail("--repo is required"))
    val gate = repo.resolve("tools/timetables/official/gate")
    val cities = EnvelopeRows.cities(repo.resolve("shared/src/commonMain/composeResources/files/cities.csv"))
    val written = EnvelopeRows.render(gate, cities)
    val stale = gate.listFiles { f -> f.isFile && f.name.endsWith(GateRow.PROMISE_ONLY_SUFFIX) }.orEmpty().map { it.name }.toSet() - written.keys
    for (name in stale) gate.resolve(name).delete()
    for ((name, file) in written) {
        gate.resolve(name).writeText(file.text)
        println("$name: ${file.rows} rows at ${file.cities} cities (${file.skipped} the group file already holds)")
    }
}

private fun fail(problem: String): Nothing {
    System.err.println("generateEnvelopeRows: $problem")
    exitProcess(2)
}

/** The envelope's rows and the tables and places they are read at. */
object EnvelopeRows {
    /** One app city: its name, country, point and zone. */
    class City(val name: String, val cc: String, val at: GeoPoint, val zone: String)

    /**
     * The sets of tables printed for their own points (`tablesAtTheirPoints`), by the entry or cautious member whose units
     * they are: each table's bound reach is R40's class D reach, the envelope's own (`TableEnvelope`).
     */
    val SETS = listOf(
        "us.isna", "ca.isna", "ca.isha90", "ca.fifteen", "ca.eighteen", "us.chicago.eighteen", "gb.wifaqul", "in.karachi",
        "pk.karachi", "uz.board", "kg.default", "ps.gaza.awqaf", "lb.fatwa", "sy.awqaf", "iq.sunni",
    )

    /** A named check: [source]'s rows matched by [rows], replayed at the app's [cities] of [cc] (by name, or the point given). */
    class Named(val what: String, val source: String, val rows: (GateRow) -> Boolean, val cc: String, val cities: List<String>)

    private fun at(row: GateRow, lat: Double, lon: Double) = row.lat != null && row.lon != null && abs(row.lat - lat) < 1e-6 && abs(row.lon - lon) < 1e-6

    /**
     * The round's named claims (the round 2 review): BIK's Takvim (its base point's rows) at every app city in Kosovo; Kénitra's
     * Habous table at Sidi Taibi, in Rabat's unit but nearer Kénitra; Lakemba's tables (LMA's and Diyanet's Sydney table) at
     * Sydney's west; LUPT where it is chosen outside the M25, near London and far from it; IACAD's Dubai table where it is
     * chosen in the other emirates, Hatta's at Fujairah and Dubai Rural's at Al Ain.
     */
    val NAMED = listOf(
        Named("BIK's Takvim (the base point's table)", "xk-bik.tsv", { it.entry == "xk.bik" && it.unit == null && at(it, 42.5, 21.0) }, "XK", listOf("*")),
        Named("Kénitra's Habous table", "ma-habous.tsv", { it.entry == "ma.habous" && it.path.endsWith("/kenitra.txt") }, "MA", listOf("@34.18698,-6.68176")),
        Named(
            "Lakemba's tables", "au-cautious.tsv", { it.entry == "au.cautious" && at(it, -33.92, 151.0756) }, "AU",
            listOf("Fairfield", "Liverpool", "Blacktown", "Parramatta"),
        ),
        Named(
            "London's Unified Timetable, chosen", "gb-london-lupt.tsv", { it.entry == "gb.london.lupt" && it.unit == "gb.london.lupt" }, "GB",
            listOf(
                "Basildon", "Sevenoaks", "St Albans", "Luton", "Reading", "Ipswich", "Margate", "Dover", "Folkestone", "Norwich",
                "Peterborough", "Southampton", "Birmingham", "Bristol", "Manchester", "Leeds", "Glasgow", "Edinburgh",
            ),
        ),
        Named("IACAD's Dubai table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "dubai" }, "AE", listOf("Sharjah", "Ajman", "Fujairah", "Al Ain City")),
        Named("IACAD's Hatta table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "hatta" }, "AE", listOf("Fujairah")),
        Named("IACAD's Dubai Rural table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "dubai-rural" }, "AE", listOf("Al Ain City")),
    )

    fun cities(file: File): List<City> = file.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
        .map { City(it[1], it[4], GeoPoint(it[5].toDouble(), it[6].toDouble()), it[7]) }

    /** One written file: its text, rows, cities and the rows left out because the group file holds them. */
    class Written(val text: String, val rows: Int, val cities: Int, val skipped: Int)

    /** A row to write: its source row, the city and why. */
    private class Out(val row: GateRow, val city: City, val note: String)

    fun render(gate: File, cities: List<City>): Map<String, Written> {
        val manifest = GateManifest.load(gate)
        val base = manifest.rows.filter { !it.promiseOnly }
        val own = base.filter { !it.source.endsWith("-reach.tsv") }
        val held = base.map { key(it.source, it.path, it.entry, it.member, it.lat, it.lon) }.toSet()
        val out = LinkedHashMap<String, MutableList<Out>>()

        for (setId in SETS) {
            val set = Units.of(setId) ?: error("no units for $setId")
            for (unit in set.units) {
                val rows = own.filter { (it.entry == setId && it.member == null || it.member == setId) && it.unit == null && at(it, unit.point.lat, unit.point.lon) }
                require(rows.isNotEmpty()) { "$setId/${unit.id}: no gate row at the table's own point" }
                val countries = rows.flatMap { requireNotNull(Registry.byId(it.entry)).countries }.toSet()
                val bound = lateReachKm(unit.point.lat, EntryClass.D_AUTHORITY)
                for (c in cities) {
                    if (c.cc !in countries) continue
                    val km = distanceKm(c.at, unit.point)
                    if (km > bound) continue
                    val note = "${unit.name}'s own table at the app's ${c.name}, ${fmt1(km)} km, within its ${fmt1(bound)} km bound reach (round 2's envelope)"
                    for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
                }
            }
        }
        for (n in NAMED) {
            val rows = own.filter { it.source == n.source && n.rows(it) }
            require(rows.isNotEmpty()) { "${n.what}: no row in ${n.source}" }
            val places = n.cities.flatMap { name ->
                when {
                    name == "*" -> cities.filter { it.cc == n.cc }
                    name.startsWith("@") -> {
                        val (lat, lon) = name.drop(1).split(",").map { it.toDouble() }
                        listOf(cities.filter { it.cc == n.cc }.minBy { distanceKm(it.at, GeoPoint(lat, lon)) })
                    }
                    else -> listOf(cities.firstOrNull { it.name == name && it.cc == n.cc } ?: error("${n.what}: no app city $name, ${n.cc}"))
                }
            }
            for (c in places) for (r in rows) {
                val from = r.lat?.let { lat -> GeoPoint(lat, r.lon!!) } ?: r.unit?.let { u -> Units.of(r.entry)?.units?.firstOrNull { it.id == u }?.point }
                val km = from?.let { " ${fmt1(distanceKm(c.at, it))} km," }.orEmpty()
                out.getOrPut(r.source) { mutableListOf() } += Out(r, c, "${n.what} at the app's ${c.name},$km the round's named claim (round 2's envelope)")
            }
        }

        val written = LinkedHashMap<String, Written>()
        for ((source, list) in out.toSortedMap()) {
            val lines = gate.resolve(source).readLines()
            val header = lines.first { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }
            val names = header.split('\t').map { it.trim() }
            fun col(name: String) = names.indexOf(name).also { require(it >= 0) { "$source has no $name column" } }
            val iEntry = col("entry")
            val iLat = col("lat")
            val iLon = col("lon")
            val iSplit = col("split")
            val iNote = col("note")
            val sb = StringBuilder()
            val group = source.removeSuffix(".tsv")
            sb.append("# GENERATED by `./gradlew -p tools/timetables generateEnvelopeRows` (units/GenerateEnvelopeRows.kt): promise-only rows\n")
            sb.append("# (the city-point round 2 review, 11 Oct 2026). ").append(group).append(".tsv's tables printed for their own points, each\n")
            sb.append("# replayed at every app city of its entry's countries within its bound reach (R40's class D reach), whichever table\n")
            sb.append("# the city follows, and the round's named claims: never before any of them, nor any end after one. The gate checks\n")
            sb.append("# the promise alone here; the lateness is no unit's figure and no limit's. Coordinates and the tables' own paths only,\n")
            sb.append("# never a time.\n")
            sb.append(header).append('\n')
            var rows = 0
            var skipped = 0
            val places = HashSet<String>()
            val sorted = list.sortedWith(compareBy<Out>({ it.row.entry }, { it.city.at.lat }, { it.city.at.lon }, { it.row.line }))
            val seen = HashSet<String>()
            for (o in sorted) {
                val lat = fmt6(o.city.at.lat)
                val lon = fmt6(o.city.at.lon)
                val k = key(o.row.source, o.row.path, o.row.entry, o.row.member, lat.toDouble(), lon.toDouble())
                if (k in held) {
                    skipped++
                    continue
                }
                if (!seen.add(k + "|" + o.row.line)) continue
                val cells = lines[o.row.line - 1].split('\t').toMutableList()
                cells[iEntry] = o.row.entry
                cells[iLat] = lat
                cells[iLon] = lon
                cells[iSplit] = "test"
                cells[iNote] = "${o.note}: ${cells[iNote]}"
                sb.append(cells.joinToString("\t")).append('\n')
                rows++
                places += "${o.row.entry}@$lat,$lon"
            }
            written["$group${GateRow.PROMISE_ONLY_SUFFIX}"] = Written(sb.toString(), rows, places.size, skipped)
        }
        return written
    }

    private fun key(source: String, path: String, entry: String, member: String?, lat: Double?, lon: Double?) =
        listOf(source, path, entry, member ?: "", lat?.let(::fmt6) ?: "", lon?.let(::fmt6) ?: "").joinToString("|")

    private fun fmt6(x: Double) = String.format(Locale.ROOT, "%.6f", x)

    private fun fmt1(x: Double) = String.format(Locale.ROOT, "%.1f", x)
}
