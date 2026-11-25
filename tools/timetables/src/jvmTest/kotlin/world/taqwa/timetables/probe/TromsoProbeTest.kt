package world.taqwa.timetables.probe

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.day.ClockedProbe
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.data.ProofStamps
import world.taqwa.app.prayer.engine.registry.data.ReachLimits
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.units.QmdbReach
import world.taqwa.timetables.units.ReachRows
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The Tromsø probe (ruling R321; kept since the final review of 20 Nov 2026): IRN's Tromsø table replayed densely across
 * `no.irn.tromso`'s circle through the real engine, where the gate's reach rows read a few hundred points. For every probe
 * point (Automatic must resolve to `no.irn` / `no.irn.tromso` there) and every date of the table: shown − printed for each
 * start, printed − shown for each end, as `Gate.evaluate` measures them (the same `setUp`, `officialInstant`, `shown` and
 * declared skips), split by (a) all days, (b) the nights ruling R292 pulls (observed through [ClockedProbe] in
 * `DayComputer.clocked`, which changes no time), (c) the nights ruling R302 calls too short
 * ([DayPipeline.nightTooShortForIsha] at the point, as the gate recomputes it), with (c) split into pulled and not pulled.
 * Cross-checks: `Gate(roots).evaluate` on the same replayed rows (per-event worst, early, late ends, out of order) at every
 * `gateEvery`-th point; `PrayerEngine.dayTimes` (the app's entry point, Automatic, which rounds a place to three decimals)
 * against the day used here at every `engineEvery`-th point on the app's grid.
 *
 * Ruling R319's sentence (`about_not_followed_irn_tromso`), clause by clause, on every night (b) or (c) holds: pulled and
 * not too short, Maghrib and Isha both before the next Fajr (so both alerts fire: `NotificationPlanner` skips one only at
 * or after it); too short and pulled, Isha on the next Fajr and Maghrib before it; too short and not pulled, both after
 * the next sunrise. Any other night with Isha at or after the next Fajr is counted as an exception.
 *
 * Outside the default suite (`jvmTest` excludes it). Run it with the `tromsoProbe` task, which hands these on:
 * - `-PprobeOut=<dir>` (required): the summary, the per-point worst and the details are written there;
 * - `-PprobeGrids=<grids>`: `;`-separated, each `reach` (the unit point and the reach rows), `lattice:sLat:sLon`, `polar:n:s`,
 *   `line:lon:s`, `box:la0:la1:lo0:lo1:sLat:sLon` and `edges:w` (the app's own thousandth-of-a-degree places), or
 *   `mbox:la0:la1:lo0:lo1:sLat:sLon` (in 1e-5°: the continuum between them); the four grids of ruling R321 and MONITOR.md's
 *   December step are `refine`, `dense`, `edges` and `micro` ([NAMED]);
 *   `rows:<file>` rebuilds `refine`'s latitude rows from another run's `points-*.tsv` (a new year's `dense` run);
 * - `-PprobeYear`: the year of the unit's own row replayed (the latest held where not given);
 * - `-PprobeTag`, `-PprobeThreads`, `-PprobeGateEvery`, `-PprobeEngineEvery`, `-PprobeLimit` (points, for timing),
 *   `-PprobeDetail="lat,lon,date;..."` (each point's days around a date, in full);
 * - `-PprobeDump=<file>` writes every place-day's shown times; `-PprobeCompare=<file>` reads such a file written for the same
 *   grid and reports every place-day that moved, and whether any start moved earlier or any end later.
 */
class TromsoProbeTest {
    private val events = listOf(
        Event.FAJR, Event.SUNRISE, Event.DHUHR, Event.ASR_STANDARD, Event.ASR_HANAFI, Event.MAGHRIB, Event.ISHA, Event.END_OF_EATING,
    )
    private val categories = listOf("a_all", "b_pulled", "c_tooShort", "c1_tooShort_notPulled", "c2_tooShort_pulled")

    private class Worst(var seconds: Long = Long.MIN_VALUE, var at: String = "", var date: LocalDate? = null, var cells: Long = 0, var early: Long = 0)

    private class PointResult(
        val at: GeoPoint,
        val label: String,
        val worst: Map<String, Map<Event, Worst>>,
        val placeDays: Map<String, Int>,
        val outOfOrder: Int,
        val shortNightDeclared: Int,
        val declared: Map<Event, Int>,
        val gateMismatch: List<String>,
        val engineMismatch: Int,
        val engineChecked: Int,
        val gateChecked: Boolean,
    )

    /** Ruling R319's clauses, night by night, across one run. */
    private class Clauses {
        val nights = ConcurrentHashMap<String, AtomicLong>()
        val maghribAfterSunrise = AtomicLong(Long.MIN_VALUE)
        val samples = ConcurrentLinkedQueue<String>()

        fun count(what: String) {
            nights.computeIfAbsent(what) { AtomicLong() }.incrementAndGet()
        }

        fun sample(what: String) {
            if (samples.size < 40) samples.add(what)
        }

        fun of(what: String): Long = nights[what]?.get() ?: 0
    }

    private fun setting(name: String, env: String): String? =
        System.getProperty("probe.$name")?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

    @Test
    fun probe() {
        val out = File(setting("out", "PROBE_OUT") ?: error("probeOut not set: run the tromsoProbe task with -PprobeOut=<dir>"))
        out.mkdirs()
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        require(roots.held) { "no archive at $official" }
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")).only(groups = setOf("no-irn"))
        val spec = ReachRows.SPECS.first { it.entry == "no.irn" }
        val unit = ReachRows.units(spec).first { it.id == "no.irn.tromso" }
        // The unit's own row of one year: -PprobeYear, else the latest held.
        val ownAll = ReachRows.ownRows(manifest, spec, unit).sortedBy { it.path }
        val year = setting("year", "PROBE_YEAR")
        val own = if (year != null) ownAll.filter { year in it.path } else ownAll.takeLast(1)
        require(own.size == 1) { "expected one own row${year?.let { " of $it" }.orEmpty()}, got ${own.size}: ${ownAll.map { it.path }}" }
        val entry = requireNotNull(Registry.byId("no.irn"))
        val zoneId = own.first().zone
        val center = unit.point
        val radius = unit.radiusKm
        val tag = setting("tag", "PROBE_TAG") ?: "run"

        // ---- points ----
        val asked = setting("grids", "PROBE_GRIDS") ?: "reach"
        val grids = (NAMED[asked] ?: asked).split(';').filter { it.isNotBlank() }
        val pts = LinkedHashMap<GeoPoint, String>()
        fun r6(x: Double) = String.format(Locale.ROOT, "%.6f", x).toDouble()
        for (g in grids) {
            val parts = g.split(':')
            when (parts[0]) {
                "lattice" -> {
                    // every parts[1] thousandths of a degree of latitude, every parts[2] thousandths of longitude: the app's own 1e-3° places.
                    val sLat = parts[1].toInt()
                    val sLon = parts[2].toInt()
                    val dLat = radius / 111.19 + 0.002
                    val dLon = radius / (111.19 * cos(Math.toRadians(center.lat + dLat))) + 0.002
                    val la0 = ceil((center.lat - dLat) * 1000).toInt()
                    val la1 = floor((center.lat + dLat) * 1000).toInt()
                    val lo0 = ceil((center.lon - dLon) * 1000).toInt()
                    val lo1 = floor((center.lon + dLon) * 1000).toInt()
                    var la = la0 - Math.floorMod(la0, sLat)
                    while (la <= la1) {
                        var lo = lo0 - Math.floorMod(lo0, sLon)
                        while (lo <= lo1) {
                            val p = GeoPoint(la / 1000.0, lo / 1000.0)
                            if (distanceKm(center, p) <= radius) pts.putIfAbsent(p, "lattice")
                            lo += sLon
                        }
                        la += sLat
                    }
                }
                "polar" -> {
                    val nb = parts[1].toInt()
                    val ns = parts[2].toInt()
                    for (b in 0 until nb) for (share in (0..ns).map { it.toDouble() / ns } + listOf(0.995, 0.999)) {
                        val at = QmdbReach.moved(center, radius * share, 360.0 * b / nb)
                        pts.putIfAbsent(GeoPoint(r6(at.lat), r6(at.lon)), "polar b=${360.0 * b / nb} s=$share")
                    }
                }
                "reach" -> {
                    pts.putIfAbsent(center, "unit point")
                    val reach = TestPaths.repoRoot.resolve("tools/timetables/official/gate/no-irn-reach.tsv").readLines()
                        .filter { !it.startsWith("#") && it.contains("no.irn/no.irn.tromso") }
                    for (l in reach) {
                        val c = l.split('\t')
                        pts.putIfAbsent(GeoPoint(c[2].toDouble(), c[3].toDouble()), "reach row")
                    }
                }
                "line" -> {
                    // a N-S line at longitude parts[1] (thousandths), every parts[2] thousandths of latitude across the circle
                    val lo = parts[1].toInt()
                    val s = parts[2].toInt()
                    val dLat = radius / 111.19 + 0.002
                    var la = ceil((center.lat - dLat) * 1000).toInt()
                    while (la <= floor((center.lat + dLat) * 1000).toInt()) {
                        val p = GeoPoint(la / 1000.0, lo / 1000.0)
                        if (distanceKm(center, p) <= radius) pts.putIfAbsent(p, "line")
                        la += s
                    }
                }
                "box" -> {
                    // box:la0:la1:lo0:lo1:sLat:sLon in thousandths
                    val v = parts.drop(1).map { it.toInt() }
                    var la = v[0]
                    while (la <= v[1]) {
                        var lo = v[2]
                        while (lo <= v[3]) {
                            val p = GeoPoint(la / 1000.0, lo / 1000.0)
                            if (distanceKm(center, p) <= radius) pts.putIfAbsent(p, "box")
                            lo += v[5]
                        }
                        la += v[4]
                    }
                }
                "edges" -> {
                    // edges:W: on every 1e-3 latitude row, the W westernmost and W easternmost 1e-3 points inside the circle
                    val w = parts[1].toInt()
                    val dLat = radius / 111.19 + 0.002
                    val dLon = radius / (111.19 * cos(Math.toRadians(center.lat + dLat))) + 0.002
                    for (la in ceil((center.lat - dLat) * 1000).toInt()..floor((center.lat + dLat) * 1000).toInt()) {
                        val row = (ceil((center.lon - dLon) * 1000).toInt()..floor((center.lon + dLon) * 1000).toInt())
                            .map { GeoPoint(la / 1000.0, it / 1000.0) }.filter { distanceKm(center, it) <= radius }
                        for (p in row.take(w) + row.takeLast(w)) pts.putIfAbsent(p, "edges")
                    }
                }
                "rows" -> {
                    // rows:<an earlier run's points-*.tsv>: every 1e-3° point of the latitude rows within three thousandths of a
                    // row where sunrise, Maghrib, Isha or the end of eating came within a minute of that event's worst in that
                    // run: ruling R321's refine grid, rebuilt from any year's dense run.
                    val lines = File(parts.drop(1).joinToString(":")).readLines()
                    val head = lines.first().split('\t')
                    val cells = lines.drop(1).map { it.split('\t') }
                    val rows = sortedSetOf<Int>()
                    for (event in listOf("sunrise", "maghrib", "isha", "endOfEating")) {
                        val k = head.indexOf(event)
                        val worstHere = cells.mapNotNull { it[k].toLongOrNull() }.maxOrNull() ?: continue
                        for (c in cells) {
                            val v = c[k].toLongOrNull() ?: continue
                            if (v >= worstHere - 60) {
                                val la = Math.round(c[0].toDouble() * 1000).toInt()
                                for (d in -3..3) rows.add(la + d)
                            }
                        }
                    }
                    val dLon = radius / (111.19 * cos(Math.toRadians(center.lat + radius / 111.19 + 0.002))) + 0.002
                    for (la in rows) {
                        for (lo in ceil((center.lon - dLon) * 1000).toInt()..floor((center.lon + dLon) * 1000).toInt()) {
                            val p = GeoPoint(la / 1000.0, lo / 1000.0)
                            if (distanceKm(center, p) <= radius) pts.putIfAbsent(p, "rows")
                        }
                    }
                    println("PROBE: rows from ${parts.drop(1).joinToString(":")}: $rows")
                }
                "mbox" -> {
                    // mbox:la0:la1:lo0:lo1:sLat:sLon in 1e-5 degrees (off the app's 1e-3 lattice: the gate's continuum)
                    val v = parts.drop(1).map { it.toInt() }
                    var la = v[0]
                    while (la <= v[1]) {
                        var lo = v[2]
                        while (lo <= v[3]) {
                            val p = GeoPoint(la / 100000.0, lo / 100000.0)
                            if (distanceKm(center, p) <= radius) pts.putIfAbsent(p, "mbox")
                            lo += v[5]
                        }
                        la += v[4]
                    }
                }
                else -> error("unknown grid $g")
            }
        }
        val limit = setting("limit", "PROBE_LIMIT")?.toInt() ?: Int.MAX_VALUE
        val points = pts.entries.take(limit).map { it.key to it.value }
        val threads = setting("threads", "PROBE_THREADS")?.toInt() ?: 6
        val gateEvery = setting("gateEvery", "PROBE_GATE_EVERY")?.toInt() ?: 1
        val engineEvery = setting("engineEvery", "PROBE_ENGINE_EVERY")?.toInt() ?: 25
        val daysInTable = requireNotNull(Gate.setUp(own.first(), roots, null, mutableListOf())?.table).days.size
        val dump = setting("dump", "PROBE_DUMP")?.let { Shown(File(it), points.size, daysInTable, write = true) }
        val compare = setting("compare", "PROBE_COMPARE")?.let { Shown(File(it), points.size, daysInTable, write = false) }
        println("PROBE: ${points.size} points (radius ${"%.3f".format(radius)} km, centre $center), grids $grids, threads $threads")

        // ---- hook: R292 pulled / R302 too short per (point, date), from clocked() at the user's own point ----
        // Each point is computed on one thread: the first night checked there for a date is the user's own day's (a table's
        // day at its own point, computed beside it, checks its own nights there, and another point's thread its own).
        val current = ThreadLocal<Pair<GeoPoint, HashMap<LocalDate, Int>>?>()
        ClockedProbe.listener = { p, d, pulled, tooShort ->
            val (at, flags) = current.get() ?: (null to null)
            if (p == at) flags?.putIfAbsent(d, (if (pulled) 1 else 0) or (if (tooShort) 2 else 0))
        }

        val limits = events.associateWith { Gate.lateLimit(it, unit, entry, entry.entryClass) }
        val stampRow = requireNotNull(ProofStamps.of("no.irn")).worstLateByUnit["no.irn.tromso"]
        val reachFig = ReachLimits.MINUTES["no.irn"]?.get("no.irn.tromso")
        val ruled = ReachRows.RULED["no.irn/no.irn.tromso"]
        val clauses = Clauses()

        val done = AtomicInteger()
        val edgeSkipped = ConcurrentLinkedQueue<String>()
        val started = System.nanoTime()
        val pool = Executors.newFixedThreadPool(threads)
        val results = try {
            points.mapIndexed { index, (at, label) ->
                pool.submit<PointResult?> {
                    val place = Place(at.lat, at.lon, zoneId, "NO")
                    val auto = Registry.resolve(place)
                    if (!(auto.entry.id == "no.irn" && auto.unitId == "no.irn.tromso") && distanceKm(center, at) > radius - 0.01) {
                        edgeSkipped.add("$at ${distanceKm(center, at)} km -> ${auto.entry.id}/${auto.unitId}")
                        dump?.put(index, at, null)
                        compare?.against(index, at, null)
                        return@submit null
                    }
                    check(auto.entry.id == "no.irn" && auto.unitId == "no.irn.tromso") {
                        "Automatic at $at resolves to ${auto.entry.id}/${auto.unitId}, not no.irn/no.irn.tromso (${distanceKm(center, at)} km)"
                    }
                    check(auto.entryClass != EntryClass.C)
                    val row = ReachRows.rowsAt(own, unit, at).single()
                    val problems = mutableListOf<String>()
                    val setup = Gate.setUp(row, roots, null, problems) ?: error("setUp failed at $at: $problems")
                    check(setup.resolution.unitId == auto.unitId && setup.resolution.entry.id == auto.entry.id) { "gate's resolution differs from Automatic's at $at" }
                    val table = requireNotNull(setup.table)
                    val zone = setup.zone
                    val school = auto.entry.school
                    val flags = HashMap<LocalDate, Int>()
                    current.set(at to flags)
                    val unended = HashMap<LocalDate, PrayerDay>()
                    fun u(d: LocalDate) = unended.getOrPut(d) { DayPipeline.unended(auto, d, zone, school) }
                    val worst = categories.associateWith { events.associateWith { Worst() } }
                    val placeDays = categories.associateWith { 0 }.toMutableMap()
                    val declared = HashMap<Event, Int>()
                    var outOfOrder = 0
                    var shortDeclared = 0
                    val days = HashMap<LocalDate, PrayerDay>()
                    val shown = if (dump != null || compare != null) ShortArray(table.days.size * SHOWN.size) else null
                    for ((dayIndex, officialDay) in table.days.withIndex()) {
                        val date = officialDay.date
                        val day = Ends.withNextDay(u(date), u(date.plus(1, DateTimeUnit.DAY)))
                        days[date] = day
                        if (shown != null) SHOWN.forEachIndexed { k, (_, pick) -> shown[dayIndex * SHOWN.size + k] = minutesInto(date, pick(day)) }
                        val f = flags[date] ?: error("no hook record at $at $date")
                        val pulled = f and 1 != 0
                        val tooShort = DayPipeline.nightTooShortForIsha(at, date, zone)
                        // Ruling R319's clauses on this night: the next day's Fajr and sunrise as shown.
                        if (pulled || tooShort) {
                            val next = Ends.withNextDay(u(date.plus(1, DateTimeUnit.DAY)), u(date.plus(2, DateTimeUnit.DAY)))
                            clauses.count("short nights")
                            when {
                                pulled && !tooShort -> if (day.maghrib < next.fajr && day.isha < next.fajr) {
                                    clauses.count("1 pulled: Maghrib and Isha before the next Fajr")
                                } else {
                                    clauses.count("x pulled: not both before the next Fajr")
                                    clauses.sample("pulled $at $date m=${day.maghrib} i=${day.isha} nf=${next.fajr}")
                                }
                                pulled && tooShort -> if (day.isha == next.fajr && day.maghrib < next.fajr) {
                                    clauses.count("2 shortest: Isha on the next Fajr, Maghrib before it")
                                } else {
                                    clauses.count("x shortest: Isha not on the next Fajr")
                                    clauses.sample("shortest $at $date m=${day.maghrib} i=${day.isha} nf=${next.fajr}")
                                }
                                else -> if (day.maghrib > next.sunrise && day.isha > next.sunrise) {
                                    clauses.count("3 very shortest: Maghrib and Isha after the next sunrise")
                                    clauses.maghribAfterSunrise.accumulateAndGet((day.maghrib - next.sunrise).inWholeSeconds) { a, b -> maxOf(a, b) }
                                } else {
                                    clauses.count("x very shortest: not both after the next sunrise")
                                    clauses.sample("very shortest $at $date m=${day.maghrib} i=${day.isha} nf=${next.fajr} ns=${next.sunrise}")
                                }
                            }
                        } else {
                            val next = Ends.withNextDay(u(date.plus(1, DateTimeUnit.DAY)), u(date.plus(2, DateTimeUnit.DAY)))
                            if (day.isha >= next.fajr) {
                                clauses.count("x other night: Isha at or after the next Fajr")
                                clauses.sample("other $at $date m=${day.maghrib} i=${day.isha} nf=${next.fajr} ns=${next.sunrise}")
                            }
                        }
                        val cats = buildList {
                            add("a_all")
                            if (pulled) add("b_pulled")
                            if (tooShort) add("c_tooShort")
                            if (tooShort && !pulled) add("c1_tooShort_notPulled")
                            if (tooShort && pulled) add("c2_tooShort_pulled")
                        }
                        for (c in cats) placeDays[c] = placeDays.getValue(c) + 1
                        if (day.repaired || Prayer.ISHA !in day.ends) {
                            if (!day.repaired && tooShort) shortDeclared++ else outOfOrder++
                        }
                        row.columns.forEachIndexed { column, evs ->
                            val minutes = officialDay.minutes[column] ?: return@forEachIndexed
                            for (event in evs) {
                                val printed = Gate.officialInstant(date, minutes, event, setup.clock, zone)
                                val shownAt = Gate.shown(day, event, entry.school) ?: continue
                                val secs = (shownAt - printed).inWholeSeconds
                                val safe = if (event.isStart) secs else -secs
                                val decl = with(Gate) { event.declarable } in day.notFollowed
                                if (decl) {
                                    declared.merge(event, 1, Int::plus)
                                    if (event == Event.ISHA && shownAt < printed) {
                                        for (c in cats) worst.getValue(c).getValue(event).early++
                                    }
                                    continue
                                }
                                for (c in cats) {
                                    val w = worst.getValue(c).getValue(event)
                                    w.cells++
                                    if (safe < 0) w.early++
                                    if (safe > w.seconds) {
                                        w.seconds = safe; w.at = "${at.lat},${at.lon}"; w.date = date
                                    }
                                }
                            }
                        }
                    }
                    current.remove()
                    dump?.put(index, at, shown)
                    compare?.against(index, at, shown)
                    // ---- the gate itself on the same replayed row ----
                    val gateMismatch = mutableListOf<String>()
                    val gateChecked = index % gateEvery == 0
                    if (gateChecked) {
                        val s = Gate(roots).evaluate(GateManifest(listOf(row))).entries.getValue("no.irn")
                        for (e in events) {
                            val g = s.events[e]
                            val mine = worst.getValue("a_all").getValue(e)
                            val gw = g?.worst ?: -1
                            val mw = if (mine.cells == 0L) -1 else maxOf(0L, mine.seconds / 60).toInt()
                            val gEarly = (g?.early ?: 0) + (g?.lateEnd ?: 0)
                            if (g != null && (gw != mw || gEarly.toLong() != mine.early)) gateMismatch += "$e gate worst $gw early $gEarly, probe $mw early ${mine.early}"
                        }
                        if (s.outOfOrder != outOfOrder) gateMismatch += "outOfOrder gate ${s.outOfOrder} probe $outOfOrder"
                    }
                    // ---- the app's entry point (Automatic), lattice points only (it rounds to 1e-3°) ----
                    var engineMismatch = 0
                    var engineChecked = 0
                    val onLattice = Math.abs(at.lat * 1000 - Math.round(at.lat * 1000)) < 1e-6 && Math.abs(at.lon * 1000 - Math.round(at.lon * 1000)) < 1e-6
                    if (onLattice && index % engineEvery == 0) {
                        for ((date, mine) in days) {
                            val ed = PrayerEngine.dayTimes(place, date, EngineSettings())
                            check(ed.resolution.unitId == "no.irn.tromso" && ed.effective.entry.id == "no.irn")
                            val d = ed.day
                            engineChecked++
                            if (d.fajr != mine.fajr || d.sunrise != mine.sunrise || d.dhuhr != mine.dhuhr || d.asr != mine.asr ||
                                d.asrOther != mine.asrOther || d.maghrib != mine.maghrib || d.isha != mine.isha ||
                                d.endOfEating != mine.endOfEating || d.ends != mine.ends || d.notFollowed != mine.notFollowed
                            ) engineMismatch++
                        }
                    }
                    val n = done.incrementAndGet()
                    if (n % 200 == 0) {
                        println("PROBE: $n / ${points.size} points, ${"%.0f".format((System.nanoTime() - started) / 1e9)} s")
                    }
                    PointResult(at, label, worst, placeDays, outOfOrder, shortDeclared, declared, gateMismatch, engineMismatch, engineChecked, gateChecked)
                }
            }.mapNotNull { it.get() }
        } finally {
            pool.shutdown()
            ClockedProbe.listener = null
        }

        // ---- details: probeDetail="lat,lon,date;..." ----
        val detail = StringBuilder()
        for (d in (setting("detail", "PROBE_DETAIL") ?: "").split(';').filter { it.isNotBlank() }) {
            val (la, lo, ds) = d.split(',')
            val at = GeoPoint(la.toDouble(), lo.toDouble())
            val date = LocalDate.parse(ds)
            val row = ReachRows.rowsAt(own, unit, at).single()
            val setup = Gate.setUp(row, roots, null, mutableListOf())!!
            val auto = Registry.resolve(Place(at.lat, at.lon, zoneId, "NO"))
            val z = setup.zone
            val flags = HashMap<LocalDate, Int>()
            current.set(at to flags)
            ClockedProbe.listener = { p, dd, pulled, tooShort ->
                if (p == at) flags.putIfAbsent(dd, (if (pulled) 1 else 0) or (if (tooShort) 2 else 0))
            }
            for (dd in listOf(date.plus(-1, DateTimeUnit.DAY), date, date.plus(1, DateTimeUnit.DAY))) {
                val day = DayPipeline.day(auto, dd, z)
                val od = setup.table!!.days.first { it.date == dd }
                detail.append("$at $dd unit=${auto.unitId} km=${"%.3f".format(distanceKm(center, at))} hook=${flags[dd]} tooShort=${DayPipeline.nightTooShortForIsha(at, dd, z)} ")
                detail.append("realSunset=${DayPipeline.realSunset(at, dd, z)} realSunriseNext=${DayPipeline.realSunrise(at, dd.plus(1, DateTimeUnit.DAY), z)} ")
                detail.append("tromsoSunset=${DayPipeline.realSunset(center, dd, z)}\n")
                detail.append("   shown fajr=${day.fajr} sunrise=${day.sunrise} dhuhr=${day.dhuhr} asr=${day.asr} maghrib=${day.maghrib} isha=${day.isha} endOfEating(next)=${day.endOfEating} ends=${day.ends} notFollowed=${day.notFollowed} setByRule=${day.setByRule}\n")
                detail.append("   printed ")
                row.columns.forEachIndexed { c, evs -> od.minutes[c]?.let { m -> evs.forEach { e -> detail.append("${e.key}=${Gate.officialInstant(dd, m, e, setup.clock, z)} ") } } }
                val engineDay = PrayerEngine.dayTimes(Place(at.lat, at.lon, zoneId, "NO"), dd, EngineSettings()).day
                detail.append("\n   PrayerEngine.dayTimes maghrib=${engineDay.maghrib} isha=${engineDay.isha}\n")
            }
            current.remove()
            ClockedProbe.listener = null
            val s = Gate(roots).evaluate(GateManifest(listOf(row))).entries.getValue("no.irn")
            detail.append("   GATE at $at: broken=${s.broken} neverEarlyBroken=${s.neverEarlyBroken} outOfOrder=${s.outOfOrder} ")
            detail.append(s.events.entries.joinToString(" ") { (e, st) -> "${e.key}:worst=${st.worst},over=${st.over},early=${st.early},lateEnd=${st.lateEnd}" })
            detail.append("\n   overLimit samples: ${s.events.values.flatMap { it.overLimit }.take(6)}\n")
        }
        if (detail.isNotEmpty()) {
            println(detail)
            out.resolve("detail-$tag.txt").writeText(detail.toString())
        }

        // ---- the shown times, written for a later run or compared with an earlier one's ----
        dump?.close()
        val moved = compare?.let { c -> c.report().also { c.close() } }

        // ---- summary ----
        val sb = StringBuilder()
        sb.append("TROMSO PROBE ${java.time.Instant.now()}\n")
        sb.append("points ${results.size} (grids $grids), radius ${"%.4f".format(radius)} km, centre $center, zone $zoneId\n")
        sb.append("edge points skipped (rounded past the circle, Automatic not Tromsø there): ${edgeSkipped.size} ${edgeSkipped.take(5)}\n")
        sb.append("table ${own.first().path}, columns ${own.first().columns}\n")
        for (c in categories) sb.append("place-days $c: ${results.sumOf { it.placeDays.getValue(c) }}\n")
        sb.append("points by label: ${results.groupingBy { it.label.substringBefore(' ') }.eachCount()}\n")
        sb.append("gate cross-checked points ${results.count { it.gateChecked }}, mismatches ${results.sumOf { it.gateMismatch.size }}\n")
        results.filter { it.gateMismatch.isNotEmpty() }.take(20).forEach { sb.append("  GATE MISMATCH ${it.at}: ${it.gateMismatch}\n") }
        sb.append("engine (PrayerEngine.dayTimes) checked place-days ${results.sumOf { it.engineChecked }} at ${results.count { it.engineChecked > 0 }} points, mismatching days ${results.sumOf { it.engineMismatch }}\n")
        val shortNights = clauses.of("short nights")
        val first = clauses.of("1 pulled: Maghrib and Isha before the next Fajr")
        sb.append("R319 clauses: ${clauses.nights.toSortedMap().mapValues { it.value.get() }}\n")
        sb.append(
            String.format(
                Locale.ROOT, "R319 'most': Maghrib and Isha before the next Fajr, with their alerts, on %d of %d short nights (%.1f %%); " +
                    "Maghrib at most %s s after the next sunrise on the very shortest; samples %s\n",
                first, shortNights, if (shortNights == 0L) 0.0 else 100.0 * first / shortNights,
                clauses.maghribAfterSunrise.get().takeIf { it != Long.MIN_VALUE }?.toString() ?: "-", clauses.samples.take(12),
            ),
        )
        sb.append("out of order (gate's rule) ${results.sumOf { it.outOfOrder }}, R302 short-night declared days ${results.sumOf { it.shortNightDeclared }}\n")
        sb.append("declared cells: ${events.associateWith { e -> results.sumOf { it.declared[e] ?: 0 } }}\n")
        sb.append("stamp row (About's figures) $stampRow\nReachLimits $reachFig\nRULED $ruled\n")
        sb.append("effective limits: ${limits.mapValues { "${it.value.minutes} (${it.value.source})" }}\n")
        if (moved != null) sb.append(moved)
        val aboutN = stampRow?.filterKeys { it in setOf("fajr", "dhuhr", "asrStandard", "asrHanafi", "maghrib", "isha") }?.values?.maxOrNull()
        sb.append("About's 'at most N min after' N = $aboutN\n\n")
        sb.append(String.format(Locale.ROOT, "%-22s %-13s %8s %6s %7s %7s %7s %7s  %-24s %s\n", "category", "event", "worstSec", "min", "stamp", "limit", "ruled", "early", "point", "date"))
        for (c in categories) {
            for (e in events) {
                var best = Worst()
                var cells = 0L
                var early = 0L
                for (r in results) {
                    val w = r.worst.getValue(c).getValue(e)
                    cells += w.cells
                    early += w.early
                    if (w.cells > 0 && w.seconds > best.seconds) best = w
                }
                val min = if (cells == 0L) "-" else (best.seconds / 60).toString()
                val stamp = stampRow?.get(e.key)
                val lim = limits.getValue(e).minutes
                val rule = ruled?.get(e.timed)
                val verdict = when {
                    cells == 0L -> "no cells"
                    early > 0 -> "FAIL (early/late end)"
                    rule != null && best.seconds / 60 > rule -> "OVER RULED"
                    best.seconds / 60 > lim -> "over limit"
                    stamp != null && best.seconds / 60 > stamp -> "over stamp"
                    else -> "PASS"
                }
                sb.append(
                    String.format(
                        Locale.ROOT, "%-22s %-13s %8s %6s %7s %7s %7s %7s  %-24s %s  cells %d  %s\n",
                        c, e.key, if (cells == 0L) "-" else best.seconds.toString(), min, stamp?.toString() ?: "-", lim, rule?.toString() ?: "-", early,
                        best.at, best.date?.toString() ?: "-", cells, verdict,
                    ),
                )
            }
            sb.append('\n')
        }
        val startsWorst = results.maxOf { r -> events.filter { it.isStart }.maxOf { e -> r.worst.getValue("a_all").getValue(e).let { if (it.cells > 0) it.seconds else Long.MIN_VALUE } } }
        sb.append("worst start over all events and points: $startsWorst s = ${startsWorst / 60} min vs About N $aboutN -> ${if (aboutN != null && startsWorst / 60 <= aboutN) "PASS" else "FAIL"}\n")
        val text = sb.toString()
        println(text)
        out.resolve("summary-$tag.txt").writeText(text)
        // per-point worst minutes (seconds) per event, all days
        out.resolve("points-$tag.tsv").bufferedWriter().use { w ->
            w.write("lat\tlon\tlabel\tkm\t" + events.joinToString("\t") { it.key } + "\tmaghrib_pulled\tmaghrib_tooShortNotPulled\tisha_tooShortNotPulled\toutOfOrder\n")
            for (r in results) {
                w.write("${r.at.lat}\t${r.at.lon}\t${r.label}\t${"%.3f".format(Locale.ROOT, distanceKm(center, r.at))}\t")
                w.write(events.joinToString("\t") { e -> r.worst.getValue("a_all").getValue(e).let { if (it.cells > 0) it.seconds.toString() else "" } })
                fun cell(c: String, e: Event) = r.worst.getValue(c).getValue(e).let { if (it.cells > 0) it.seconds.toString() else "" }
                w.write("\t${cell("b_pulled", Event.MAGHRIB)}\t${cell("c1_tooShort_notPulled", Event.MAGHRIB)}\t${cell("c1_tooShort_notPulled", Event.ISHA)}\t${r.outOfOrder}\n")
            }
        }
    }

    /**
     * Every place-day's shown times ([SHOWN]) in [file], one fixed record a point at its index in the run's points, so a later
     * run of the same grid can compare each place-day with this one's ([write] false): how many moved, each time by how much
     * and which way, and every start that moved earlier or end that moved later (none should, where a change only takes each
     * start's later moment and each end's earlier one). Minutes from the date's UTC midnight, in a short.
     */
    private class Shown(file: File, private val points: Int, private val days: Int, private val write: Boolean) {
        private val raf = RandomAccessFile(file, if (write) "rw" else "r")
        private val record = 8 + 8 + 1 + days * SHOWN.size * 2
        private val placeDays = AtomicLong()
        private val movedDays = AtomicLong()
        private val wrongWay = AtomicLong()
        private val later = AtomicLongArray(SHOWN.size)
        private val earlier = AtomicLongArray(SHOWN.size)
        private val most = AtomicLongArray(SHOWN.size)
        private val samples = Array(SHOWN.size) { ConcurrentLinkedQueue<String>() }
        private val wrong = Array(SHOWN.size) { ConcurrentLinkedQueue<String>() }

        init {
            if (write) {
                raf.setLength(0)
                raf.writeInt(MAGIC); raf.writeInt(points); raf.writeInt(days); raf.writeInt(SHOWN.size)
            } else {
                check(raf.readInt() == MAGIC && raf.readInt() == points && raf.readInt() == days && raf.readInt() == SHOWN.size) {
                    "$file is not this grid's shown times ($points points, $days days)"
                }
            }
        }

        fun put(index: Int, at: GeoPoint, shown: ShortArray?) {
            val buffer = ByteBuffer.allocate(record).putDouble(at.lat).putDouble(at.lon).put(if (shown == null) 0 else 1)
            shown?.forEach { buffer.putShort(it) }
            synchronized(raf) {
                raf.seek(HEADER + index.toLong() * record)
                raf.write(buffer.array())
            }
        }

        fun against(index: Int, at: GeoPoint, shown: ShortArray?) {
            val bytes = ByteArray(record)
            synchronized(raf) {
                raf.seek(HEADER + index.toLong() * record)
                raf.readFully(bytes)
            }
            val buffer = ByteBuffer.wrap(bytes)
            check(buffer.getDouble() == at.lat && buffer.getDouble() == at.lon) { "point $index is not $at in the compared run" }
            val held = buffer.get() == 1.toByte()
            check(held == (shown != null)) { "$at: computed in one run and not in the other" }
            if (shown == null) return
            for (d in 0 until days) {
                var movedHere = false
                for (k in SHOWN.indices) {
                    val before = buffer.getShort()
                    val now = shown[d * SHOWN.size + k]
                    if (before == now) continue
                    movedHere = true
                    val name = SHOWN[k].first
                    val line = "$at day ${d + 1} $name ${before.minutes()} -> ${now.minutes()}"
                    if (before == NONE || now == NONE) {
                        wrongWay.incrementAndGet()
                        if (wrong[k].size < 8) wrong[k].add("$line (appears or goes)")
                        continue
                    }
                    val by = (now - before).toLong()
                    if (by > 0) later.incrementAndGet(k) else earlier.incrementAndGet(k)
                    most.accumulateAndGet(k, kotlin.math.abs(by)) { a, b -> maxOf(a, b) }
                    if ((name in STARTS && by < 0) || (name !in STARTS && by > 0)) {
                        wrongWay.incrementAndGet()
                        if (wrong[k].size < 8) wrong[k].add(line)
                    } else if (samples[k].size < 3) {
                        samples[k].add(line)
                    }
                }
                placeDays.incrementAndGet()
                if (movedHere) movedDays.incrementAndGet()
            }
        }

        fun report(): String = buildString {
            append("compared place-days ${placeDays.get()}, moved ${movedDays.get()}; a start earlier or an end later: ${wrongWay.get()}\n")
            for (k in SHOWN.indices) {
                if (later.get(k) + earlier.get(k) == 0L && wrong[k].isEmpty()) continue
                append("  ${SHOWN[k].first}: ${later.get(k)} later, ${earlier.get(k)} earlier, at most ${most.get(k)} min\n")
                if (samples[k].isNotEmpty()) append("    moved, e.g.: ${samples[k].toList()}\n")
                if (wrong[k].isNotEmpty()) append("    WRONG WAY: ${wrong[k].toList()}\n")
            }
        }

        fun close() = raf.close()

        private fun Short.minutes(): String = if (this == NONE) "none" else toString()

        private companion object {
            const val MAGIC = 0x54505231
            const val HEADER = 16L
        }
    }

    private companion object {
        /** The four grids of ruling R321's probe, by name. */
        val NAMED = mapOf(
            "refine" to "box:69388:69395:18200:19710:1:1;box:69668:69674:18200:19710:1:1;box:69679:69685:18200:19710:1:1;" +
                "box:69828:69835:18200:19710:1:1;box:69892:69898:18200:19710:1:1",
            "dense" to "lattice:1:20;polar:16:10;reach",
            "edges" to "edges:25",
            "micro" to "mbox:6989400:6989600:1880000:1895000:10:100;mbox:6967000:6967200:1870000:1895000:10:100;" +
                "mbox:6983000:6983300:1930000:1972000:10:200",
        )

        /** The times a place-day shows, as [Shown] keeps them (minutes from the date's UTC midnight, [NONE] where there is none). */
        val SHOWN: List<Pair<String, (PrayerDay) -> Instant?>> = listOf(
            "fajr" to { d -> d.fajr }, "sunrise" to { d -> d.sunrise }, "dhuhr" to { d -> d.dhuhr }, "asr" to { d -> d.asr },
            "asrOther" to { d -> d.asrOther }, "maghrib" to { d -> d.maghrib }, "isha" to { d -> d.isha }, "sunset" to { d -> d.sunset },
            "endOfEating" to { d -> d.endOfEating }, "imsak" to { d -> d.imsak },
            "fajrEnd" to { d -> d.ends[Prayer.FAJR] }, "dhuhrEnd" to { d -> d.ends[Prayer.DHUHR] }, "asrEnd" to { d -> d.ends[Prayer.ASR] },
            "maghribEnd" to { d -> d.ends[Prayer.MAGHRIB] }, "ishaEnd" to { d -> d.ends[Prayer.ISHA] },
        )

        /** The starts among [SHOWN]; every other is an end. */
        val STARTS = setOf("fajr", "dhuhr", "asr", "asrOther", "maghrib", "isha")

        const val NONE = Short.MIN_VALUE

        fun minutesInto(date: LocalDate, at: Instant?): Short {
            if (at == null) return NONE
            val minutes = Math.floorDiv(at.epochSeconds, 60L) - date.toEpochDays() * 1440L
            check(minutes > NONE && minutes <= Short.MAX_VALUE) { "$at is too far from $date" }
            return minutes.toInt().toShort()
        }
    }
}
