package world.taqwa.timetables.units

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EDGE_NEAR_FACTOR
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Egypt
import world.taqwa.app.prayer.engine.registry.authorities.Gulf
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.edgeRideKm
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Split
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Measures the ground past an authority's inner lines (the second trust audit of 14 Nov 2026, findings G1, G2 and G3;
 * ruling R260), and writes nothing into the repository:
 *
 *     ./gradlew -p tools/timetables edgeSweep -Pentry=kg.default|eg.esa|om.mara [-Pthreads=N] [-Pout=<file>]
 *
 * The inner line is a unit's reach (the Kyrgyz Muftiate's localities), its belt (ESA's towns: twice the reach, ruling
 * R213) or its ride (the Omani ministry's localities: half again the circle, ruling R210). On 16 bearings, at
 * [OFFSETS_KM] past that line and just past [edgeRideKm] (the end of R260's ride), wherever the place is on the edge (no
 * unit's reach holds it) and the unit is the nearest one, the unit's own tables (its rows at its own point) are read
 * against the place twice: as the engine shows it, and as it showed it without rulings R260 and R262 (the same method
 * with only the days alongside that the inner rules ride: none past every inner line, the farther units' whose own
 * belt or ride holds the place inside one, ruling R262). A ring inside another unit's inner line is reported apart
 * ("inside another unit's line"). And at every app city of the country on the edge past the nearest unit's inner
 * line, against every unit whose table rides there and the nearest. Each line: points, place-days, the cells
 * before the table (a start) or after it (an end) with and without the rule, what the rule moves (each event's days by
 * minutes moved: a start later, an end earlier) and how late the place is against the nearest table.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) ?: fail("$name needs a value") }
    val repo = File(option("--repo") ?: fail("--repo is required"))
    val official = File(option("--official") ?: fail("--official is required"))
    val entryId = option("--entry") ?: fail("--entry is required")
    val spec = ReachRows.SPECS.firstOrNull { it.entry == entryId } ?: fail("--entry $entryId: no such reach entry")
    val threads = option("--threads")?.toInt() ?: Runtime.getRuntime().availableProcessors()
    val roots = OfficialRoots(official, repo.resolve("tools/timetables/official"))
    if (!roots.held) fail("no official archive at $official")
    val cities = EnvelopeRows.cities(repo.resolve("shared/src/commonMain/composeResources/files/cities.csv")).filter { it.cc == spec.countryCode }
    val manifest = GateManifest.load(repo.resolve("tools/timetables/official/gate")).only(groups = setOf(spec.group))
    val started = System.nanoTime()
    val report = EdgeSweep.run(spec, manifest, roots, cities, threads)
    print(report.text)
    println("${(System.nanoTime() - started) / 1_000_000_000} s")
    option("--out")?.let { File(it).writeText(report.lines); println("every point: $it") }
}

private fun fail(problem: String): Nothing {
    System.err.println(problem)
    exitProcess(2)
}

object EdgeSweep {
    /** How far past the inner line the rings stand, in km. */
    val OFFSETS_KM = listOf(0.5, 5.0, 10.0, 20.0, 40.0, 60.0)
    val BEARINGS: List<Double> = (0 until 16).map { it * 22.5 }

    /** The inner line of [entry]'s [unit]: where a place stops being held by the rules before R260. */
    fun innerKm(entry: String, unit: AuthorityUnit): Double = when (entry) {
        "eg.esa" -> unit.radiusKm * Egypt.BESIDE_REACHES
        "om.mara" -> Gulf.omanRideKm(unit)
        else -> unit.radiusKm
    }

    /** One event's tally: cells, cells broken with and without the rule, days moved by minutes, lateness against the table. */
    class Tally {
        var cells = 0L
        var brokenBefore = 0L
        var brokenAfter = 0L
        val moved = LongArray(5)
        val late = LongArray(6)
        var worstLate = 0
        var worstMoved = 0
    }

    class Group(val name: String) {
        var points = 0
        var placeDays = 0L
        val events = sortedMapOf<Event, Tally>()
        val worstBrokenBefore = sortedMapOf<Event, Int>()
        fun add(o: Group) {
            points += o.points
            placeDays += o.placeDays
            for ((e, t) in o.events) {
                val m = events.getOrPut(e) { Tally() }
                m.cells += t.cells
                m.brokenBefore += t.brokenBefore
                m.brokenAfter += t.brokenAfter
                t.moved.forEachIndexed { i, n -> m.moved[i] += n }
                t.late.forEachIndexed { i, n -> m.late[i] += n }
                m.worstLate = maxOf(m.worstLate, t.worstLate)
                m.worstMoved = maxOf(m.worstMoved, t.worstMoved)
            }
            for ((e, w) in o.worstBrokenBefore) worstBrokenBefore[e] = maxOf(worstBrokenBefore[e] ?: 0, w)
        }
    }

    class Report(val text: String, val lines: String)

    /** [own] (a unit's rows at its own point) read at [at] as the engine shows it there and as it showed it before R260. */
    private fun read(own: List<world.taqwa.timetables.gate.GateRow>, at: GeoPoint, roots: OfficialRoots, into: Group, keptBefore: (GeoPoint) -> Boolean) {
        val days = HashSet<kotlinx.datetime.LocalDate>()
        for (row in own) {
            val moved = row.copy(lat = at.lat, lon = at.lon, unit = null, split = Split.TEST)
            val problems = mutableListOf<String>()
            val setup = Gate.setUp(moved, roots, null, problems) ?: error(problems.joinToString("\n"))
            val table = setup.table ?: continue
            val after = setup.resolution
            val before: Resolution = after.copy(
                method = after.method?.let { m ->
                    m.copy(alongside = m.alongside.filter { keptBefore(it.point) }, envelopePoints = m.envelopePoints.filter(keptBefore))
                },
            )
            val school = setup.entry.school
            for (official in table.days) {
                val date = official.date
                val next = date.plus(1, DateTimeUnit.DAY)
                val a = Ends.withNextDay(DayPipeline.unended(after, date, setup.zone), DayPipeline.unended(after, next, setup.zone))
                val b = Ends.withNextDay(DayPipeline.unended(before, date, setup.zone), DayPipeline.unended(before, next, setup.zone))
                days += date
                row.columns.forEachIndexed { column, events ->
                    val minutes = official.minutes[column] ?: return@forEachIndexed
                    for (event in events) {
                        val printed = Gate.officialInstant(date, minutes, event, setup.clock, setup.zone)
                        val shownA = Gate.shown(a, event, school) ?: continue
                        val shownB = Gate.shown(b, event, school) ?: continue
                        val sign = if (event.isStart) 1 else -1
                        val safeA = (shownA - printed).inWholeSeconds * sign
                        val safeB = (shownB - printed).inWholeSeconds * sign
                        val t = into.events.getOrPut(event) { Tally() }
                        t.cells++
                        if (safeB < 0) {
                            t.brokenBefore++
                            into.worstBrokenBefore[event] = maxOf(into.worstBrokenBefore[event] ?: 0, ((-safeB + 59) / 60).toInt())
                        }
                        if (safeA < 0) t.brokenAfter++
                        val move = (shownA - shownB).inWholeSeconds * sign
                        check(move >= 0) { "${at.lat}, ${at.lon} on $date: R260/R262 moved ${event.key} the wrong way by $move s" }
                        val movedMinutes = ((move + 59) / 60).toInt()
                        t.moved[movedMinutes.coerceAtMost(4)]++
                        t.worstMoved = maxOf(t.worstMoved, movedMinutes)
                        if (safeA >= 0) {
                            val late = (safeA / 60).toInt()
                            t.late[late.coerceAtMost(5)]++
                            t.worstLate = maxOf(t.worstLate, late)
                        }
                    }
                }
            }
        }
        into.points++
        into.placeDays += days.size
    }

    fun run(spec: ReachRows.Spec, manifest: GateManifest, roots: OfficialRoots, cities: List<EnvelopeRows.City>, threads: Int): Report {
        val set = requireNotNull(Units.of(spec.entry)) { "no units for ${spec.entry}" }
        val entry = requireNotNull(Registry.byId(spec.entry))
        val all = set.units
        fun nearest(at: GeoPoint) = all.minBy { distanceKm(at, it.point) }
        val unitPoints = all.map { it.point }.toSet()
        // What the inner rules alone ride at [at] (before R260 and R262): the units whose own inner line holds it, and
        // any envelope point that is no unit's.
        fun keptBefore(at: GeoPoint): (GeoPoint) -> Boolean {
            val inner = all.filter { distanceKm(at, it.point) <= innerKm(spec.entry, it) }.map { it.point }.toSet()
            return { p -> p in inner || p !in unitPoints }
        }
        fun insideAnother(at: GeoPoint) = all.any { distanceKm(at, it.point) <= innerKm(spec.entry, it) }
        val measured = all.filter { it.measured }
        val pool = Executors.newFixedThreadPool(threads)
        val lines = StringBuilder("unit\tring\tlat\tlon\tevent\tcells\tbrokenBefore\tbrokenAfter\tworstLate\n")
        val rings = sortedMapOf<String, Group>()
        try {
            val futures = measured.map { unit ->
                pool.submit<List<Pair<String, Group>>> {
                    val own = ReachRows.ownRows(manifest, spec, unit)
                    if (own.isEmpty()) return@submit emptyList()
                    val zone = own.first().zone
                    val inner = innerKm(spec.entry, unit)
                    val offsets = OFFSETS_KM.map { "${fmt(it)} km past the inner line" to inner + it }.filter { it.second < edgeRideKm(unit) } +
                        listOf("0.5 km past R260's ride (${fmt(edgeRideKm(unit))} km)" to edgeRideKm(unit) + 0.5)
                    val out = ArrayList<Pair<String, Group>>()
                    for ((ringName, km) in offsets) for (bearing in BEARINGS) {
                        val at = QmdbReach.moved(unit.point, km, bearing).let { GeoPoint(round6(it.lat), round6(it.lon)) }
                        if (nearest(at).id != unit.id) continue
                        if (Registry.resolveEntry(entry, Place(at.lat, at.lon, zone, spec.countryCode)).unitId != null) continue
                        // Ruling R262: a ring past this unit's line but inside a farther unit's is measured too, apart.
                        val ring = if (insideAnother(at)) "$ringName, inside another unit's line" else ringName
                        val kept = keptBefore(at)
                        val g = Group(ring)
                        read(own, at, roots, g, kept)
                        out += ring to g
                        // The second nearest unit, where its table could still ride: whether R260 rides it (no more than
                        // EDGE_NEAR_FACTOR times as far) or not, what the place is against it.
                        val second = all.filter { it.id != unit.id }.minBy { distanceKm(at, it.point) }
                        val secondOwn = ReachRows.ownRows(manifest, spec, second)
                        if (distanceKm(at, second.point) <= edgeRideKm(second) && secondOwn.isNotEmpty()) {
                            val rides = distanceKm(at, second.point) <= distanceKm(at, unit.point) * EDGE_NEAR_FACTOR
                            val g2 = Group(ring)
                            read(secondOwn, at, roots, g2, kept)
                            out += "$ring, the second nearest unit's table (${if (rides) "riding" else "not riding"})" to g2
                        }
                        synchronized(lines) {
                            for ((e, t) in g.events) {
                                lines.append("${unit.id}\t$ring\t${at.lat}\t${at.lon}\t${e.key}\t${t.cells}\t${t.brokenBefore}\t${t.brokenAfter}\t${t.worstLate}\n")
                            }
                        }
                    }
                    out
                }
            }
            for (f in futures) for ((ring, g) in f.get()) rings.getOrPut(ring.replace(Regex(" \\(\\d+\\.\\d km\\)"), "")) { Group(ring.replace(Regex(" \\(\\d+\\.\\d km\\)"), "")) }.add(g)
        } finally {
            pool.shutdown()
        }
        // The app's cities on the edge past the nearest unit's inner line: every unit whose table rides there, and the nearest.
        val cityLines = ArrayList<String>()
        val cityGroup = Group("the app's cities past the nearest unit's inner line")
        for (c in cities) {
            val r = Registry.resolveEntry(entry, Place(c.at.lat, c.at.lon, c.zone, c.cc))
            if (r.unitId != null) continue
            val near = nearest(c.at)
            if (distanceKm(c.at, near.point) <= innerKm(spec.entry, near)) continue
            val m = r.method ?: continue
            val riding = all.filter { u -> m.alongside.any { it.point == u.point } }
            for (unit in (riding + nearest(c.at)).distinct()) {
                val own = ReachRows.ownRows(manifest, spec, unit)
                if (own.isEmpty()) continue
                val g = Group(c.name)
                read(own, c.at, roots, g, keptBefore(c.at))
                cityGroup.add(g)
                val broken = g.events.filterValues { it.brokenBefore > 0 || it.brokenAfter > 0 }
                    .map { (e, t) -> "${e.key} ${t.brokenBefore}→${t.brokenAfter} (≤${g.worstBrokenBefore[e] ?: 0} min before)" }
                val moved = g.events.map { (e, t) -> "${e.key} ${t.moved.drop(1).sum()} d" }
                cityLines += "  ${c.name}${if (insideAnother(c.at)) " (inside another unit's line)" else ""}: ${unit.name}'s table, ${fmt(distanceKm(c.at, unit.point))} km" +
                    (if (unit in riding) ", rides" else ", the nearest, does not ride") + "; broken before→after: " +
                    (if (broken.isEmpty()) "none" else broken.joinToString(", ")) + "; moved: " + moved.joinToString(", ") +
                    "; worst late against it: " + g.events.map { (e, t) -> "${e.key} ${t.worstLate}" }.joinToString(" ")
            }
        }
        val text = buildString {
            appendLine("${spec.entry}: the ground past each unit's inner line where the unit is the nearest and the place on the edge (16 bearings)")
            for ((_, g) in rings) table(g)
            // Every ring inside R260's ride, the nearest unit's table: what the rule moves and how late the place is against it.
            val inside = Group("every ring inside R260's ride, the nearest unit's table")
            for ((name, g) in rings) if ("km past the inner line" in name && "second" !in name) inside.add(g)
            table(inside)
            table(cityGroup)
            cityLines.forEach { appendLine(it) }
        }
        return Report(text, lines.toString())
    }

    private fun StringBuilder.table(g: Group) {
        appendLine("${g.name}: ${g.points} points, ${g.placeDays} place-days")
        appendLine("  event         cells  broken before→after (worst before) | moved +0 +1 +2 +3 +4+ (median, worst) | late against the table +0 +1 +2 +3 +4 +5+ worst")
        for ((e, t) in g.events) {
            val median = median(t.moved)
            appendLine(
                String.format(
                    Locale.ROOT, "  %-12s %7d  %7d → %d (%d) | %d %d %d %d %d (%d, %d) | %d %d %d %d %d %d %d",
                    e.key, t.cells, t.brokenBefore, t.brokenAfter, g.worstBrokenBefore[e] ?: 0,
                    t.moved[0], t.moved[1], t.moved[2], t.moved[3], t.moved[4], median, t.worstMoved,
                    t.late[0], t.late[1], t.late[2], t.late[3], t.late[4], t.late[5], t.worstLate,
                ),
            )
        }
    }

    private fun median(counts: LongArray): Int {
        val total = counts.sum()
        var run = 0L
        counts.forEachIndexed { i, n -> run += n; if (run * 2 >= total && total > 0) return i }
        return 0
    }

    private fun round6(x: Double): Double = String.format(Locale.ROOT, "%.6f", x).toDouble()

    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.1f", x)
}
