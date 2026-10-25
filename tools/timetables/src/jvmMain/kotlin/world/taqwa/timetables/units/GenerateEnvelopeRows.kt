package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.TableEnvelope
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Americas
import world.taqwa.app.prayer.engine.registry.authorities.Europe
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
 * reach, three minutes of longitude), whichever unit the city follows, or none; and past every bound reach, the nearest
 * table's rows at every app city within its ride (`TableEnvelope.rideKm`, five minutes of longitude: the owner's decision
 * of 17 Oct 2026; Wifaqul's where it is chosen on its own), as Rabita's Helsinki calendar's at the app's Finnish cities
 * and on rings around Helsinki within its ride (`Europe.rabitaRide`), and ISNA's Montréal and Ottawa mosques' rows under
 * Canada's cautious times wherever those are followed and a mosque rides beside them ([EnvelopeRows.CROSS],
 * `Units.crossRide`). And by name ([EnvelopeRows.NAMED]): the Takvim at the app's
 * Kosovo cities, Kénitra's Habous table at Sidi Taibi, Lakemba's tables at Sydney's west, London's Unified Timetable where
 * it is chosen outside the M25 and still applies (within its area, about 104 km), IACAD's tables where they are chosen
 * outside its zones, and Diyanet's Sydney table where Diyanet's preset is chosen around Sydney (the owner's decision of
 * 17 Oct 2026).
 *
 * The gate holds these rows to the promise alone ([GateRow.promiseOnly]): an early start or a late end breaks it as
 * anywhere, but their lateness, against a table the city may not follow, is no unit's figure and no limit's. A row the
 * group files already hold at that city (the app's own point rows) is not written again.
 *
 *     ./gradlew -p tools/timetables generateEnvelopeRows
 *
 * Re-run after a table printed for its own point, its rows or its reach change, or after `prove` adds a year to a table's
 * rows, then run the gate and commit. `EnvelopeCoverageTest` fails while the committed files miss a place the generator
 * would write now: where the generator asks the engine where a table rides ([EnvelopeRows.Named.rides]), a ride changed
 * in the engine cannot leave the files behind it unnoticed. Coordinates are the app's own, as `cities.csv` gives them;
 * no printed time is written.
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

    /**
     * A named check: [source]'s rows matched by [rows], replayed at the app's [cities] of [cc] (by name, or the point given),
     * and at every app city of [cc] where [rides] holds. [rides] asks the engine where it rides the table ([ridesAny]: the
     * app's own resolution), never a reach of the generator's own, so a ride widened or narrowed in the engine moves these
     * rows with it the next time they are written, and `EnvelopeCoverageTest` fails until they are (the Q4 review of 17 Oct
     * 2026). [chosen], for a table checked where a user chooses it, is its entry: every city must be one where that choice
     * applies (`Registry.inScope`), or the row would test a choice the app does not honour there. [why] ends each row's
     * note: the claim it stands for.
     */
    class Named(
        val what: String,
        val source: String,
        val rows: (GateRow) -> Boolean,
        val cc: String,
        val cities: List<String>,
        val chosen: String? = null,
        val rides: ((City) -> Boolean)? = null,
        val why: String = "the round's named claim (round 2's envelope)",
    )

    /**
     * Whether entry [entryId], chosen at [city], rides any of [points] there: the app's own resolution of the entry at the
     * city ([Registry.resolveEntry]) carries one of them as its fixed point, as a start, end or envelope point, or as a
     * day alongside (`TimetableMethod.alongside`, the nearest table's ride past its bound, the owner's decision of 17 Oct
     * 2026, question 1).
     */
    fun ridesAny(entryId: String, city: City, points: List<GeoPoint>): Boolean {
        val entry = requireNotNull(Registry.byId(entryId)) { "no entry $entryId" }
        val m = Registry.resolveEntry(entry, Place(city.at.lat, city.at.lon, city.zone, city.cc)).method ?: return false
        val ridden = listOfNotNull(m.fixedPoint) + m.startPoints + m.endPoints + m.envelopePoints + m.alongside.map { it.point }
        return ridden.any { it in points }
    }

    private fun at(row: GateRow, lat: Double, lon: Double) = row.lat != null && row.lon != null && abs(row.lat - lat) < 1e-6 && abs(row.lon - lon) < 1e-6

    /**
     * The round's named claims (the round 2 review): BIK's Takvim (its base point's rows) at every app city in Kosovo; Kénitra's
     * Habous table at Sidi Taibi, in Rabat's unit but nearer Kénitra; Lakemba's tables (LMA's and Diyanet's Sydney table) at
     * Sydney's west; LUPT where it is chosen outside the M25, within its area (R45's three reaches, about 104 km: beyond, the
     * owner's decision of 17 Oct 2026 put it out of scope, so Ipswich, Margate, Dover, Norwich, Peterborough, Southampton,
     * Birmingham, Bristol, Manchester, Leeds, Glasgow and Edinburgh, which round 2's fix held, are held no more); IACAD's
     * Dubai table where it is chosen in the other emirates, Hatta's at Fujairah and Dubai Rural's at Al Ain. And the owner's
     * decision of 17 Oct 2026 (question 4): Diyanet's Sydney table where Diyanet's preset is chosen, at every app city where
     * the chosen preset rides one of the table's plausible points (`Americas.sydneyDiyanetPoints`; today class D's reach of
     * them, 69 km).
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
            listOf("Basildon", "Sevenoaks", "St Albans", "Luton", "Reading", "Folkestone"),
            chosen = "gb.london.lupt",
        ),
        Named(
            "IACAD's Dubai table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "dubai" }, "AE",
            listOf("Sharjah", "Ajman", "Fujairah", "Al Ain City"), chosen = "ae.iacad.dubai",
        ),
        Named(
            "IACAD's Hatta table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "hatta" }, "AE", listOf("Fujairah"),
            chosen = "ae.iacad.dubai",
        ),
        Named(
            "IACAD's Dubai Rural table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "dubai-rural" }, "AE",
            listOf("Al Ain City"), chosen = "ae.iacad.dubai",
        ),
        Named(
            "Diyanet's Sydney table, its preset chosen", "au-diyanet.tsv", { it.entry == "other.turkey" && at(it, -33.92, 151.0756) }, "AU",
            emptyList(), chosen = "other.turkey", rides = { c -> ridesAny("other.turkey", c, Americas.sydneyDiyanetPoints.map { it.point }) },
            why = "the owner's decision of 17 Oct 2026 (question 4)",
        ),
    )

    fun cities(file: File): List<City> = file.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
        .map { City(it[1], it[4], GeoPoint(it[5].toDouble(), it[6].toDouble()), it[7]) }

    /** One written file: its text, rows, cities and the rows left out because the group file holds them. */
    class Written(val text: String, val rows: Int, val cities: Int, val skipped: Int)

    /** A row to write: its source row, the city and why, and the entry and member it is checked under where not its own. */
    private class Out(val row: GateRow, val city: City, val note: String, val entry: String = row.entry, val member: String? = row.member)

    /**
     * A cautious [entry] that rides another entry's [tables] (Units.crossRide), checked under [member], its most-followed
     * member: a table riding beside the place is shown on the safe side whatever the far-north guard says (DayComputer.safeSide),
     * and the most-followed member's Maghrib is the one the guard always reads as printed (ruling R156), so its Maghrib is
     * checked as printed on every day: ISNA's Montréal and Ottawa mosques beside Canada's cautious times.
     */
    class Cross(val entry: String, val tables: String, val member: String)

    val CROSS = listOf(Cross("ca.cautious", "ca.isna", "ca.eighteen"))

    /** Rabita's ring around Helsinki: every [RABITA_RING_BEARINGS] degrees, at each of [RABITA_RINGS_KM], inside its ride. */
    fun rabitaRing(): List<City> = RABITA_RINGS_KM.flatMap { km ->
        (0 until 360 step RABITA_RING_BEARINGS).map { bearing ->
            City("a point ${fmt1(km)} km from Helsinki at $bearing°", "FI", destination(Europe.RABITA_HELSINKI, km, bearing.toDouble()), "Europe/Helsinki")
        }
    }.also { ring -> require(ring.all { Europe.rabitaRides(it.at) }) { "a ring point outside Rabita's ride" } }

    /**
     * The rings' distances from Helsinki: just past a minute's reach (13.8 km), across Diyanet's Helsinki unit (41.5 km) and
     * the region's 45 km edge, out to the ride's end.
     */
    private val RABITA_RINGS_KM = listOf(14.0, 20.0, 30.0, 38.0, 41.0, 42.0, 44.9, 45.1, 49.0, 55.0, 62.0, 69.0)
    private const val RABITA_RING_BEARINGS = 15

    /** The point [km] from [from] at [bearing] degrees (great circle, the earth radius `distanceKm` uses). */
    private fun destination(from: GeoPoint, km: Double, bearing: Double): GeoPoint {
        val d = km / 6371.0
        val b = Math.toRadians(bearing)
        val la1 = Math.toRadians(from.lat)
        val lo1 = Math.toRadians(from.lon)
        val la2 = Math.asin(Math.sin(la1) * Math.cos(d) + Math.cos(la1) * Math.sin(d) * Math.cos(b))
        val lo2 = lo1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(la1), Math.cos(d) - Math.sin(la1) * Math.sin(la2))
        return GeoPoint(Math.toDegrees(la2), Math.toDegrees(lo2))
    }

    fun render(gate: File, cities: List<City>): Map<String, Written> {
        val manifest = GateManifest.load(gate)
        val base = manifest.rows.filter { !it.promiseOnly }
        val own = base.filter { !it.source.endsWith("-reach.tsv") }
        val held = base.map { key(it.source, it.path, it.entry, it.member, it.lat, it.lon) }.toSet()
        val out = LinkedHashMap<String, MutableList<Out>>()

        for (setId in SETS) {
            val set = Units.of(setId) ?: error("no units for $setId")
            val rowsOf = set.units.associateWith { unit ->
                own.filter { (it.entry == setId && it.member == null || it.member == setId) && it.unit == null && at(it, unit.point.lat, unit.point.lon) }
                    .also { require(it.isNotEmpty()) { "$setId/${unit.id}: no gate row at the table's own point" } }
            }
            val envelope = requireNotNull(set.envelope) { "$setId rides no envelope" }
            for (c in cities) {
                for ((unit, why) in riders(envelope, c.at)) {
                    val rows = rowsOf.getValue(unit)
                    val countries = rows.flatMap { requireNotNull(Registry.byId(it.entry)).countries }.toSet()
                    if (c.cc !in countries) continue
                    val note = "${unit.name}'s own table at the app's ${c.name}, ${fmt1(distanceKm(c.at, unit.point))} km, $why"
                    for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
                }
            }
        }
        // Rabita's Helsinki calendar from a minute's reach of Helsinki out to its ride (Europe.rabitaRide, the owner's decision
        // of 17 Oct 2026): at the app's Finnish cities there, and at points on rings around Helsinki (rabitaRing), where the
        // app's own cities had all been clean while points between them were not (the reviews of 17 Oct 2026).
        val rabita = own.filter { it.entry == "se.cautious" && it.member == "se.rabita" && at(it, Europe.RABITA_HELSINKI.lat, Europe.RABITA_HELSINKI.lon) }
        require(rabita.isNotEmpty()) { "no row of Rabita's calendar at Helsinki in se-cautious.tsv" }
        val ride = "within its ${fmt1(Europe.RABITA_RIDE_FROM_KM)} to ${fmt1(Europe.RABITA_RIDE_KM)} km ride (the owner's decision of 17 Oct 2026)"
        for (c in cities) {
            if (c.cc != "FI" || !Europe.rabitaRides(c.at)) continue
            val note = "Rabita's Helsinki calendar at the app's ${c.name}, ${fmt1(distanceKm(c.at, Europe.RABITA_HELSINKI))} km, $ride"
            for (r in rabita) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
        }
        for (c in rabitaRing()) {
            val note = "Rabita's Helsinki calendar at ${c.name}, $ride"
            for (r in rabita) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
        }
        // Another entry's tables riding beside a cautious place (Units.crossRide, the owner's decision of 17 Oct 2026): ISNA's
        // Montréal and Ottawa mosques' own rows at every app city of Canada where Canada's cautious times are followed and a
        // mosque's bound or ride holds it, checked under Automatic (CROSS).
        for (cross in CROSS) {
            val set = Units.of(cross.tables) ?: error("no units for ${cross.tables}")
            val envelope = requireNotNull(set.envelope) { "${cross.tables} rides no envelope" }
            val entry = requireNotNull(Registry.byId(cross.entry)) { "no entry ${cross.entry}" }
            require(Units.crossRide(cross.entry)?.tablesEntryId == cross.tables) { "${cross.entry} rides no ${cross.tables} tables" }
            require(entry.members.minBy { it.shareRank }.id == cross.member) { "${cross.member} is not ${cross.entry}'s most-followed member, whose Maghrib the gate always reads as printed (ruling R156)" }

            for (c in cities) {
                if (c.cc !in entry.countries) continue
                if (Registry.automaticEntry(Place(c.at.lat, c.at.lon, c.zone, c.cc)).id != cross.entry) continue
                for ((unit, why) in riders(envelope, c.at)) {
                    val rows = own.filter { it.entry == cross.tables && it.member == null && it.unit == null && at(it, unit.point.lat, unit.point.lon) }
                    require(rows.isNotEmpty()) { "${cross.tables}/${unit.id}: no gate row at the table's own point" }
                    val note = "${unit.name}'s own table beside ${cross.entry} at the app's ${c.name}, ${fmt1(distanceKm(c.at, unit.point))} km, " +
                        "$why, ridden by Automatic there (the owner's decision of 17 Oct 2026)"
                    for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note, cross.entry, cross.member)
                }
            }
        }
        for (n in NAMED) {
            val rows = own.filter { it.source == n.source && n.rows(it) }
            require(rows.isNotEmpty()) { "${n.what}: no row in ${n.source}" }
            val named = n.cities.flatMap { name ->
                when {
                    name == "*" -> cities.filter { it.cc == n.cc }
                    name.startsWith("@") -> {
                        val (lat, lon) = name.drop(1).split(",").map { it.toDouble() }
                        listOf(cities.filter { it.cc == n.cc }.minBy { distanceKm(it.at, GeoPoint(lat, lon)) })
                    }
                    else -> listOf(cities.firstOrNull { it.name == name && it.cc == n.cc } ?: error("${n.what}: no app city $name, ${n.cc}"))
                }
            }
            val near = n.rides?.let { rides -> cities.filter { c -> c.cc == n.cc && rides(c) } }.orEmpty()
            require(n.rides == null || near.isNotEmpty()) { "${n.what}: the engine rides the table at no app city of ${n.cc}" }
            val places = (named + near).distinct()
            n.chosen?.let { id ->
                val entry = requireNotNull(Registry.byId(id)) { "${n.what}: no entry $id" }
                for (c in places) {
                    require(Registry.inScope(entry, Place(c.at.lat, c.at.lon, c.zone, c.cc))) { "${n.what}: $id does not apply at the app's ${c.name}" }
                }
            }
            for (c in places) for (r in rows) {
                val from = r.lat?.let { lat -> GeoPoint(lat, r.lon!!) } ?: r.unit?.let { u -> Units.of(r.entry)?.units?.firstOrNull { it.id == u }?.point }
                val km = from?.let { " ${fmt1(distanceKm(c.at, it))} km," }.orEmpty()
                out.getOrPut(r.source) { mutableListOf() } += Out(r, c, "${n.what} at the app's ${c.name},$km ${n.why}")
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
            // A row checked under another entry's member (CROSS) needs the member column, which a single entry's file lacks.
            val addMember = "member" !in names && list.any { it.member != null }
            val iMember = if (addMember) names.size else names.indexOf("member")
            val sb = StringBuilder()
            val group = source.removeSuffix(".tsv")
            sb.append("# GENERATED by `./gradlew -p tools/timetables generateEnvelopeRows` (units/GenerateEnvelopeRows.kt): promise-only rows\n")
            sb.append("# (the city-point round 2 review, 11 Oct 2026). ").append(group).append(".tsv's tables printed for their own points, each\n")
            sb.append("# replayed at every app city of its entry's countries within its bound reach (R40's class D reach), whichever table\n")
            sb.append("# the city follows, past every bound the nearest table within its ride (five reaches, the owner's decision of\n")
            sb.append("# 17 Oct 2026), and the round's named claims: never before any of them, nor any end after one. The gate checks\n")
            sb.append("# the promise alone here; the lateness is no unit's figure and no limit's. Coordinates and the tables' own paths only,\n")
            sb.append("# never a time.\n")
            sb.append(header).append(if (addMember) "\tmember" else "").append('\n')
            var rows = 0
            var skipped = 0
            val places = HashSet<String>()
            val sorted = list.sortedWith(compareBy<Out>({ it.entry }, { it.city.at.lat }, { it.city.at.lon }, { it.row.line }))
            val seen = HashSet<String>()
            for (o in sorted) {
                val lat = fmt6(o.city.at.lat)
                val lon = fmt6(o.city.at.lon)
                val k = key(o.row.source, o.row.path, o.entry, o.member, lat.toDouble(), lon.toDouble())
                if (k in held) {
                    skipped++
                    continue
                }
                if (!seen.add(k + "|" + o.row.line)) continue
                val cells = lines[o.row.line - 1].split('\t').toMutableList()
                if (addMember) cells += ""
                cells[iEntry] = o.entry
                cells[iLat] = lat
                cells[iLon] = lon
                cells[iSplit] = "test"
                cells[iNote] = "${o.note}: ${cells[iNote]}"
                if (iMember >= 0 && iMember < cells.size) cells[iMember] = o.member.orEmpty()
                sb.append(cells.joinToString("\t")).append('\n')
                rows++
                places += "${o.entry}@$lat,$lon"
            }
            written["$group${GateRow.PROMISE_ONLY_SUFFIX}"] = Written(sb.toString(), rows, places.size, skipped)
        }
        return written
    }

    /**
     * The tables that ride beside [at] ([TableEnvelope.riding]), each with why: every one whose bound reach (R40's class D
     * reach) holds it, and the nearest within its ride ([TableEnvelope.rideKm], the owner's decision of 17 Oct 2026) where
     * no bound holds it.
     */
    fun riders(envelope: TableEnvelope, at: GeoPoint): List<Pair<AuthorityUnit, String>> {
        val held = envelope.holding(at)
        return envelope.riding(at).map { unit ->
            unit to if (unit in held) {
                "within its ${fmt1(lateReachKm(unit.point.lat, EntryClass.D_AUTHORITY))} km bound reach (round 2's envelope)"
            } else {
                "the nearest table, within its ${fmt1(envelope.rideKm(unit))} km ride past its bound (the owner's decision of 17 Oct 2026)"
            }
        }
    }

    private fun key(source: String, path: String, entry: String, member: String?, lat: Double?, lon: Double?) =
        listOf(source, path, entry, member ?: "", lat?.let(::fmt6) ?: "", lon?.let(::fmt6) ?: "").joinToString("|")

    private fun fmt6(x: Double) = String.format(Locale.ROOT, "%.6f", x)

    private fun fmt1(x: Double) = String.format(Locale.ROOT, "%.1f", x)
}
