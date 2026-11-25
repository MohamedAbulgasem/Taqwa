package world.taqwa.timetables.units

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Split
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Measures a unit set's overlaps (the audit of 12 Nov 2026, findings F1 and F8; ruling R205), and writes nothing into
 * the repository:
 *
 *     ./gradlew -p tools/timetables overlapSweep -Pentry=kg.default [-Pstep=1.5] [-Ppaths=-2027] [-Pyear=2027]
 *         [-Pout=<file>] [-Pthreads=N] [-Pofficial=<dir>]
 *
 * Every measured unit's own tables (its rows at its own point, [ReachRows.ownRows]) are replayed on a lattice of
 * `step` km across its whole reach, on a ring just inside its edge, at the middle of the ground it shares with each
 * neighbour (so a pair whose reaches barely touch is read too, and the report counts the pairs read) and at the
 * app's cities inside it, wherever another unit's reach holds the point too. Two questions, kept apart:
 *
 *  - a point that follows ANOTHER unit: is it ever before this unit's printed table (a start early, an end late)?
 *    Where every unit whose reach holds a user rides beside the nearest (Kyrgyzstan, R205; QMDB's places since the
 *    owner's ruling of 13 Nov 2026, R249) the answer must be never; where a user takes the nearest alone (QMDB's
 *    places until then) this is the figure of how far and how often a place runs before its neighbour's table.
 *  - a point that follows THIS unit inside a neighbour's reach: how late against its own table (the rider's cost).
 *
 * And where the neighbours ride (the place's method carries envelope points), what riding them costs by itself: each
 * such point's day as shown beside its day with the nearest unit alone, every day of `-Pyear` (2027), no table read.
 *
 * `-Ppaths` keeps only the rows whose table path contains the text (a year). `-Pout` takes every pair's line.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) ?: fail("$name needs a value") }
    val repo = File(option("--repo") ?: fail("--repo is required"))
    val official = File(option("--official") ?: fail("--official is required"))
    val entryId = option("--entry") ?: fail("--entry is required")
    val spec = ReachRows.SPECS.firstOrNull { it.entry == entryId } ?: fail("--entry $entryId: no such reach entry")
    val step = option("--step")?.toDouble() ?: 1.5
    val paths = option("--paths")
    val threads = option("--threads")?.toInt() ?: Runtime.getRuntime().availableProcessors()
    val roots = OfficialRoots(official, repo.resolve("tools/timetables/official"))
    if (!roots.held) fail("no official archive at $official")
    val cities = EnvelopeRows.cities(repo.resolve("shared/src/commonMain/composeResources/files/cities.csv")).filter { it.cc == spec.countryCode }
    val manifest = GateManifest.load(repo.resolve("tools/timetables/official/gate")).only(groups = setOf(spec.group))
    val started = System.nanoTime()
    val year = option("--year")?.toInt() ?: 2027
    val result = OverlapSweep.run(spec, manifest, roots, cities, step, paths, threads, year)
    print(OverlapSweep.report(spec, result, step, paths))
    println("${(System.nanoTime() - started) / 1_000_000_000} s")
    option("--out")?.let { File(it).writeText(OverlapSweep.lines(result)); println("every pair: $it") }
}

private fun fail(problem: String): Nothing {
    System.err.println(problem)
    exitProcess(2)
}

object OverlapSweep {
    /** The ring just inside a reach's edge: its share of the radius and its bearings. */
    const val EDGE_SHARE = 0.995
    val EDGE_BEARINGS: List<Double> = (0 until 72).map { it * 5.0 }

    /** One event's tally over the points of a pair (or of everything): cells, the worst, and where the worst stood. */
    class Tally {
        var checked = 0L
        var before = 0L
        var worstBefore = 0
        var worstBeforeAt: String? = null
        var ramadanBefore = 0L
        /** The most days one point ran before the table, the Ramadan days among them, and the point. */
        var mostDays = 0
        var mostDaysRamadan = 0
        var mostDaysAt: String? = null
        var worstLate = 0
        var worstLateAt: String? = null
        val late = LongArray(4)
    }

    /** A table's unit and the unit its point follows (the same where the point is its own inside a neighbour's reach). */
    data class Pair(val table: String, val follows: String)

    class PairStats {
        var points = 0
        val events = sortedMapOf<Event, Tally>()
    }

    /** What riding the neighbours moves at the points that ride any: minutes later (a start) or earlier (an end) than the nearest alone. */
    class Ride {
        var points = 0
        var days = 0L
        val moved = sortedMapOf<String, LongArray>()
        val worst = sortedMapOf<String, Int>()
        val worstAt = sortedMapOf<String, String>()
        val worstRamadan = sortedMapOf<String, Int>()
    }

    private val RIDE_EVENTS: List<Triple<String, Boolean, (PrayerDay) -> kotlin.time.Instant>> = listOf(
        Triple("fajr", true) { it.fajr }, Triple("sunrise", false) { it.sunrise }, Triple("dhuhr", true) { it.dhuhr },
        Triple("asr", true) { it.asr }, Triple("asrOther", true) { it.asrOther }, Triple("maghrib", true) { it.maghrib },
        Triple("isha", true) { it.isha }, Triple("endOfEating", false) { it.endOfEating },
    )

    class Result(
        val ride: Ride,
        /** The same where a table rides that is computed farther from the place than any reach holding it is wide (Aravan's). */
        val rideFar: Ride,
        val units: Int,
        val overlapping: Int,
        /** Of [overlapping], the pairs with each measured side's table replayed at a point inside the other's reach. */
        val read: Int,
        val unread: List<String>,
        val swept: Int,
        val shared: Int,
        val pairs: Map<Pair, PairStats>,
        val cities: Map<String, List<String>>,
    )

    /** The lattice of [stepKm] across [unit]'s reach, the ring just inside its edge, and its own point. */
    fun lattice(unit: AuthorityUnit, stepKm: Double): List<GeoPoint> {
        val dLat = stepKm / 111.195
        val dLon = stepKm / (111.195 * cos(unit.point.lat * PI / 180))
        val n = (unit.radiusKm / stepKm).toInt() + 1
        val grid = (-n..n).flatMap { i -> (-n..n).map { j -> GeoPoint(round6(unit.point.lat + i * dLat), round6(unit.point.lon + j * dLon)) } }
        val ring = EDGE_BEARINGS.map { b -> QmdbReach.moved(unit.point, unit.radiusKm * EDGE_SHARE, b).let { GeoPoint(round6(it.lat), round6(it.lon)) } }
        return (grid + ring).distinct().filter { distanceKm(it, unit.point) <= unit.radiusKm }
    }

    /**
     * The middle of the ground [unit]'s reach shares with [other]'s, on the line between their points (its own point
     * where [other]'s reach holds it and reaches as far behind it); null where they share none.
     */
    fun middle(unit: AuthorityUnit, other: AuthorityUnit): GeoPoint? {
        val d = distanceKm(unit.point, other.point)
        if (d >= unit.radiusKm + other.radiusKm) return null
        val along = (max(-unit.radiusKm, d - other.radiusKm) + min(unit.radiusKm, d + other.radiusKm)) / 2
        val la1 = unit.point.lat * PI / 180
        val la2 = other.point.lat * PI / 180
        val dLon = (other.point.lon - unit.point.lon) * PI / 180
        val bearing = atan2(sin(dLon) * cos(la2), cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)) * 180 / PI
        val at = if (d == 0.0) unit.point else QmdbReach.moved(unit.point, along, bearing)
        return GeoPoint(round6(at.lat), round6(at.lon)).takeIf { distanceKm(it, unit.point) <= unit.radiusKm && distanceKm(it, other.point) <= other.radiusKm }
    }

    private fun round6(x: Double): Double = String.format(Locale.ROOT, "%.6f", x).toDouble()

    fun run(
        spec: ReachRows.Spec, manifest: GateManifest, roots: OfficialRoots, cities: List<EnvelopeRows.City>, stepKm: Double,
        paths: String?, threads: Int, year: Int = 2027,
    ): Result {
        val set = requireNotNull(Units.of(spec.entry)) { "no units for ${spec.entry}" }
        val entry = requireNotNull(Registry.byId(spec.entry))
        val ramadan = Registry.ramadanCalendar()
        val all = set.units
        val measured = all.filter { it.measured }
        // Coarse cells of a degree, so a point's holders are found among the units near it (QMDB's list is 5,676).
        val cells = HashMap<Long, MutableList<AuthorityUnit>>()
        fun cell(lat: Int, lon: Int): Long = lat.toLong() * 1000 + lon
        for (u in all) cells.getOrPut(cell(Math.floorDiv((u.point.lat * 2).toInt(), 1), Math.floorDiv((u.point.lon * 2).toInt(), 1))) { mutableListOf() } += u
        fun near(at: GeoPoint): List<AuthorityUnit> {
            val la = (at.lat * 2).toInt()
            val lo = (at.lon * 2).toInt()
            return (-4..4).flatMap { i -> (-6..6).flatMap { j -> cells[cell(la + i, lo + j)].orEmpty() } }
        }
        fun holders(at: GeoPoint): List<AuthorityUnit> = near(at).filter { distanceKm(at, it.point) <= it.radiusKm }
        // Unordered pairs of units whose reaches share ground, a measured unit's table on at least one side.
        fun neighbours(u: AuthorityUnit): List<AuthorityUnit> =
            near(u.point).distinctBy { it.id }.filter { it.id != u.id && distanceKm(u.point, it.point) < u.radiusKm + it.radiusKm }
        val overlapping = all.flatMap { u -> neighbours(u).filter { it.id > u.id && (u.measured || it.measured) }.map { u to it } }

        class Found(
            val pair: Pair, val at: String, val events: Map<Event, IntArray>, val late: Map<Event, IntArray>, val city: String?,
            val others: List<String>,
        )
        val ride = Ride()
        val rideFar = Ride()
        val ridden = ConcurrentHashMap.newKeySet<String>()
        fun rideCost(at: GeoPoint, zone: String) {
            val r = Registry.resolveEntry(entry, Place(at.lat, at.lon, zone, spec.countryCode))
            val m = r.method ?: return
            if (m.envelopePoints.isEmpty() || !ridden.add("${at.lat}, ${at.lon}")) return
            val alone = r.copy(method = m.copy(envelopePoints = emptyList()))
            val widest = holders(at).maxOf { it.radiusKm }
            val into = if ((listOfNotNull(m.fixedPoint) + m.envelopePoints).any { distanceKm(at, it) > widest }) rideFar else ride
            val tz = TimeZone.of(zone)
            val local = Ride()
            var date = LocalDate(year, 1, 1)
            while (date.year == year) {
                val shown = DayPipeline.unended(r, date, tz)
                val nearest = DayPipeline.unended(alone, date, tz)
                for ((name, start, read) in RIDE_EVENTS) {
                    val seconds = (read(shown) - read(nearest)).inWholeSeconds * (if (start) 1 else -1)
                    check(seconds >= 0) { "$at on $date: riding a neighbour moved $name the wrong way by $seconds s" }
                    val minutes = ((seconds + 59) / 60).toInt()
                    local.moved.getOrPut(name) { LongArray(4) }[minutes.coerceAtMost(3)]++
                    if (minutes > (local.worst[name] ?: -1)) local.worst[name] = minutes
                    if (ramadan.isRamadan(date) && minutes > (local.worstRamadan[name] ?: -1)) local.worstRamadan[name] = minutes
                }
                local.days++
                date = date.plus(1, DateTimeUnit.DAY)
            }
            synchronized(into) {
                into.points++
                into.days += local.days
                for ((name, counts) in local.moved) {
                    val sum = into.moved.getOrPut(name) { LongArray(4) }
                    counts.forEachIndexed { i, n -> sum[i] += n }
                    if (local.worst.getValue(name) > (into.worst[name] ?: -1)) { into.worst[name] = local.worst.getValue(name); into.worstAt[name] = "${at.lat}, ${at.lon}" }
                    if ((local.worstRamadan[name] ?: -1) > (into.worstRamadan[name] ?: -1)) into.worstRamadan[name] = local.worstRamadan.getValue(name)
                }
            }
        }
        val pool = Executors.newFixedThreadPool(threads)
        var swept = 0
        val found = try {
            measured.map { unit ->
                pool.submit<List<Found>> {
                    val own = ReachRows.ownRows(manifest, spec, unit).filter { paths == null || paths in it.path }
                    if (own.isEmpty()) return@submit emptyList()
                    val zone = own.first().zone
                    val points = (lattice(unit, stepKm) + neighbours(unit).mapNotNull { middle(unit, it) }).distinct().map { it to null as String? } +
                        cities.filter { distanceKm(it.at, unit.point) <= unit.radiusKm }.map { it.at to it.name }
                    synchronized(this) { swept += points.size }
                    points.mapNotNull { (at, city) ->
                        val others = holders(at).filter { it.id != unit.id }.map { it.id }
                        if (others.isEmpty()) return@mapNotNull null
                        rideCost(at, zone)
                        val follows = Registry.resolveEntry(entry, Place(at.lat, at.lon, zone, spec.countryCode)).unitId ?: return@mapNotNull null
                        val rows = own.map { it.copy(lat = at.lat, lon = at.lon, unit = null, split = Split.TEST) }
                        val s = Gate(roots).evaluate(GateManifest(rows)).entries[spec.entry] ?: return@mapNotNull null
                        val events = s.events.mapValues { (_, e) ->
                            intArrayOf(e.checked, e.early + e.lateEnd, e.worstEarly, e.brokenDates.count { ramadan.isRamadan(it) }, e.worst)
                        }
                        Found(Pair(unit.id, follows), "${at.lat}, ${at.lon}", events, s.events.mapValues { it.value.late.copyOf() }, city, others)
                    }
                }
            }.flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        val pairs = sortedMapOf<Pair, PairStats>(compareBy({ it.table }, { it.follows }))
        val inCities = sortedMapOf<String, MutableList<String>>()
        for (f in found) {
            val p = pairs.getOrPut(f.pair) { PairStats() }
            p.points++
            for ((event, v) in f.events) {
                val t = p.events.getOrPut(event) { Tally() }
                t.checked += v[0]
                t.before += v[1]
                if (v[2] > t.worstBefore) { t.worstBefore = v[2]; t.worstBeforeAt = f.at }
                t.ramadanBefore += v[3]
                if (v[1] > t.mostDays) { t.mostDays = v[1]; t.mostDaysRamadan = v[3]; t.mostDaysAt = f.at }
                if (v[4] > t.worstLate) { t.worstLate = v[4]; t.worstLateAt = f.at }
                f.late.getValue(event).forEachIndexed { i, n -> t.late[i] += n.toLong() }
            }
            if (f.city != null) {
                val before = f.events.filterValues { it[1] > 0 }.map { (e, v) -> "${e.key} ${v[1]} d ≤${v[2]} min (${v[3]} in Ramadan)" }
                val late = f.events.maxOf { it.value[4] }
                inCities.getOrPut(f.city) { mutableListOf() } +=
                    "${f.pair.table}'s table, follows ${f.pair.follows}: " + (if (before.isEmpty()) "never before" else "before: " + before.joinToString(", ")) + "; late ≤$late"
            }
        }
        // A pair is read when each measured side's own table was replayed at a point the other side's reach holds.
        val replayed = found.flatMapTo(HashSet()) { f -> f.others.map { f.pair.table to it } }
        val unread = overlapping.filterNot { (a, b) -> (!a.measured || (a.id to b.id) in replayed) && (!b.measured || (b.id to a.id) in replayed) }
        return Result(ride, rideFar, measured.size, overlapping.size, overlapping.size - unread.size, unread.map { (a, b) -> "${a.id}/${b.id}" }, swept, found.size, pairs, inCities)
    }

    private fun total(pairs: Collection<PairStats>): Map<Event, Tally> {
        val out = sortedMapOf<Event, Tally>()
        for (p in pairs) for ((event, t) in p.events) {
            val o = out.getOrPut(event) { Tally() }
            o.checked += t.checked
            o.before += t.before
            o.ramadanBefore += t.ramadanBefore
            if (t.worstBefore > o.worstBefore) { o.worstBefore = t.worstBefore; o.worstBeforeAt = t.worstBeforeAt }
            if (t.mostDays > o.mostDays) { o.mostDays = t.mostDays; o.mostDaysRamadan = t.mostDaysRamadan; o.mostDaysAt = t.mostDaysAt }
            if (t.worstLate > o.worstLate) { o.worstLate = t.worstLate; o.worstLateAt = t.worstLateAt }
            t.late.forEachIndexed { i, n -> o.late[i] += n }
        }
        return out
    }

    private fun table(title: String, pairs: Map<Pair, PairStats>): String = buildString {
        val t = total(pairs.values)
        appendLine("$title: ${pairs.size} pairs, ${pairs.values.sumOf { it.points }} points, ${t.values.maxOfOrNull { it.checked } ?: 0} place-days")
        appendLine("  event         checked   before worst  inRamadan  mostDaysAtOnePoint(inRamadan) where                 |     +0     +1     +2    +3+ worstLate where")
        for ((event, e) in t) {
            appendLine(
                String.format(
                    Locale.ROOT, "  %-12s %8d %8d %5d %10d  %8d (%d) %-28s | %6d %6d %6d %6d %5d %s",
                    event.key, e.checked, e.before, e.worstBefore, e.ramadanBefore, e.mostDays, e.mostDaysRamadan, e.mostDaysAt ?: "-",
                    e.late[0], e.late[1], e.late[2], e.late[3], e.worstLate, e.worstLateAt ?: "-",
                ),
            )
        }
    }

    fun report(spec: ReachRows.Spec, r: Result, stepKm: Double, paths: String?): String = buildString {
        appendLine(
            "${spec.entry}: ${r.units} measured units, ${r.overlapping} pairs of units whose reaches share ground (a measured one on a side), " +
                "${r.read} of them read (each measured side's table replayed inside the other's reach); ${r.swept} points on a " +
                "$stepKm km lattice, the edge ring, the middle of each pair's shared ground and the app's cities" +
                (if (paths != null) ", tables \"$paths\"" else "") + "; ${r.shared} inside another reach too",
        )
        if (r.unread.isNotEmpty()) appendLine("  pairs not read: ${r.unread.take(40).joinToString(", ")}" + if (r.unread.size > 40) " …" else "")
        val other = r.pairs.filterKeys { it.table != it.follows }
        val own = r.pairs.filterKeys { it.table == it.follows }
        append(table("A table replayed where the place follows ANOTHER unit", other))
        val unordered = other.keys.map { setOf(it.table, it.follows) }.toSet().size
        appendLine("  unordered pairs of units: $unordered; pairs with any cell before the table: ${other.count { (_, p) -> p.events.values.any { it.before > 0 } }}")
        other.entries.filter { (_, p) -> p.events.values.any { it.before > 0 } }
            .sortedByDescending { (_, p) -> p.events.values.maxOf { it.worstBefore } * 100_000L + p.events.values.maxOf { it.mostDays } }.take(12)
            .forEach { (pair, p) ->
                appendLine(
                    "    ${pair.table}'s table where the place follows ${pair.follows} (${p.points} points): " +
                        p.events.filterValues { it.before > 0 }.map { (e, t) -> "${e.key} ≤${t.worstBefore} min, ${t.mostDays} d at one point (${t.mostDaysRamadan} in Ramadan) at ${t.mostDaysAt}" }.joinToString("; "),
                )
            }
        append(table("A unit's own table where a neighbour's reach holds the place too", own))
        own.entries.sortedByDescending { (_, p) -> p.events.values.maxOf { it.worstLate } }.take(12).forEach { (pair, p) ->
            appendLine("    ${pair.table} (${p.points} points): " + p.events.map { (e, t) -> "${e.key} ${t.worstLate}" }.joinToString(" "))
        }
        if (spec.waits) {
            // QMDB's places ride as days alongside (ruling R249), not as envelope points, and a year of them at every
            // point swept is hours: an empty table here would read as "no cost".
            appendLine(
                "What riding the neighbours moves, beside the nearest unit alone: not read here for ${spec.entry}, whose neighbours ride " +
                    "as days alongside; kazakhRide -Pmode=cost measures it (the app's cities, QMDB's points and a grid)",
            )
        }
        for ((title, ride) in listOf(
            "What riding the neighbours moves, beside the nearest unit alone" to r.ride,
            "The same where a table rides that is computed farther off than any reach holding the place is wide" to r.rideFar,
        )) {
            if (spec.waits && ride.points == 0) continue
            appendLine("$title: ${ride.points} points, ${ride.days} place-days")
            if (ride.points == 0) continue
            appendLine("  event              +0       +1       +2      +3+ worst inRamadan where")
            for ((name, m) in ride.moved) {
                appendLine(
                    String.format(
                        Locale.ROOT, "  %-12s %9d %8d %8d %8d %5d %9d %s", name, m[0], m[1], m[2], m[3], ride.worst[name], ride.worstRamadan[name] ?: 0,
                        ride.worstAt[name],
                    ),
                )
            }
        }
        appendLine("The app's cities inside two reaches: ${r.cities.size}")
        r.cities.forEach { (city, lines) -> lines.forEach { appendLine("    $city: $it") } }
    }

    /** Every pair, a line each: the table's unit, the unit followed, points, and each event's cells, worst and lateness. */
    fun lines(r: Result): String = buildString {
        appendLine("table\tfollows\tpoints\tevent\tchecked\tbefore\tworstBefore\tramadanBefore\tmostDays\tmostDaysRamadan\tmostDaysAt\tworstLate\tworstLateAt")
        for ((pair, p) in r.pairs) for ((event, t) in p.events) {
            appendLine(
                listOf(
                    pair.table, pair.follows, p.points, event.key, t.checked, t.before, t.worstBefore, t.ramadanBefore, t.mostDays,
                    t.mostDaysRamadan, t.mostDaysAt ?: "", t.worstLate, t.worstLateAt ?: "",
                ).joinToString("\t"),
            )
        }
    }
}
