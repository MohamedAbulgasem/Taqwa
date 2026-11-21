package world.taqwa.timetables.units

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.OfficialTable
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Measures what QMDB's overlapping places cost (ruling R249: a place inside more than one QMDB place's reach waits for
 * every such place's table), and writes nothing into the repository:
 *
 *     ./gradlew -p tools/timetables kazakhRide -Pmode=time|cost|pairs [-Pstep=0.05] [-Pyear=2027] [-Pout=<file>] [-Pthreads=4]
 *         [-Pofficial=<dir>]
 *
 * `time`: how long a place takes to resolve and a day to compute, at the app's Kazakh cities and at QMDB's densest
 * ground (the south, where one point lies inside up to 183 reaches), on this machine's JVM.
 *
 * `cost`: how many minutes later each start is (and how many earlier each end) than with the nearest place alone, as
 * it was from 9 Oct to 13 Nov 2026: at the app's Kazakh cities on every day of `-Pyear`, at every point of QMDB's own
 * list on every fifth day, and on a grid of `-Pstep` degrees over all the ground more than one reach holds on every
 * tenth day. Every place a time moves more than five minutes is named with the place whose table moved it. `-Pout`
 * takes a line for every point that moved. Each set is also split by whose day does the moving: QMDB's printed table
 * is held for 87 of its 5,676 places ([QmdbPlaces.all]), and the day that rides for every other place is the engine's
 * own calculation of that place's day, so the split reads what the held tables' places move by themselves and what
 * is left to the places whose table was never seen.
 *
 * `pairs` (needs the archive): QMDB's own printed tables against each other, for every two places whose reaches share
 * ground and whose tables are both held. Day by day, how far one table's printed minute stands from the other's beyond
 * what the engine's one method gives between the two points (each place's own day, alone): a pair whose tables part
 * by more than rounding and the method's fit would be two tables that disagree, not two places under one sky.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val repo = File(option("--repo") ?: fail("--repo is required"))
    val cities = EnvelopeRows.cities(repo.resolve("shared/src/commonMain/composeResources/files/cities.csv")).filter { it.cc == "KZ" }
    when (val mode = option("--mode") ?: "time") {
        "time" -> KazakhRide.time(cities)
        "cost" -> KazakhRide.cost(
            cities, option("--step")?.toDouble() ?: 0.05, option("--year")?.toInt() ?: 2027, option("--threads")?.toInt() ?: 4,
            option("--out")?.let { File(it) },
        )
        "pairs" -> KazakhRide.pairs(
            GateManifest.load(repo.resolve("tools/timetables/official/gate")),
            OfficialRoots(File(option("--official") ?: fail("--official is required")), repo.resolve("tools/timetables/official")),
        )
        else -> fail("--mode $mode: time, cost or pairs")
    }
}

private fun fail(problem: String): Nothing {
    System.err.println(problem)
    exitProcess(2)
}

object KazakhRide {
    const val ENTRY = "kz.qmdb"
    const val ZONE = "Asia/Almaty"

    /** QMDB's densest ground: the points of its list that lie inside the most reaches (Maktaaral and Zhetysay's villages). */
    val DENSE = listOf(GeoPoint(40.686322, 68.285832), GeoPoint(40.901015, 68.453861))

    private fun place(at: GeoPoint, zone: String = ZONE) = Place(at.lat, at.lon, zone, "KZ")

    /** Resolve and day times, in microseconds: the first place of all (which builds the 5,676 units), then warm medians. */
    fun time(cities: List<EnvelopeRows.City>) {
        val entry = requireNotNull(Registry.byId(ENTRY))
        val first = System.nanoTime()
        Registry.resolveEntry(entry, place(GeoPoint(43.25, 76.95)))
        println("first place of all (builds the units): ${(System.nanoTime() - first) / 1000} µs")
        // Fresh points each round, a few hundred metres apart, so nothing is read back from a cache of places.
        fun nudged(at: GeoPoint, i: Int) = GeoPoint(at.lat + 0.0007 * (i % 17), at.lon + 0.0009 * (i % 13))
        val named = DENSE.mapIndexed { i, at -> "dense ${i + 1} (${at.lat}, ${at.lon})" to at } +
            cities.filter { it.name in setOf("Almaty", "Talghar", "Shymkent", "Astana", "Turkistan", "Zhetysay") }.map { it.name to it.at }
        fun measure(label: String, points: List<GeoPoint>) {
            val resolve = ArrayList<Long>()
            val day = ArrayList<Long>()
            var riders = 0
            repeat(6) { round ->
                for ((i, base) in points.withIndex()) {
                    val at = nudged(base, round * 31 + i)
                    val t0 = System.nanoTime()
                    val r = Registry.resolveEntry(entry, place(at))
                    val t1 = System.nanoTime()
                    val tz = TimeZone.of(ZONE)
                    var date = LocalDate(2027, 1, 1 + round)
                    repeat(30) {
                        DayPipeline.day(r, date, tz)
                        date = date.plus(12, DateTimeUnit.DAY)
                    }
                    val t2 = System.nanoTime()
                    if (round > 0) {
                        resolve += (t1 - t0) / 1000
                        day += (t2 - t1) / 30_000
                        riders = maxOf(riders, r.method?.alongside?.size ?: 0)
                    }
                }
            }
            resolve.sort()
            day.sort()
            println(String.format(Locale.ROOT, "%-44s resolve median %5d µs, worst %6d; a day median %4d µs, worst %5d; most days alongside %d",
                label, resolve[resolve.size / 2], resolve.last(), day[day.size / 2], day.last(), riders))
        }
        // Warm the JIT first on the cities, then read.
        repeat(3) { measure("(warming)", cities.map { it.at }) }
        measure("the app's ${cities.size} Kazakh cities", cities.map { it.at })
        for ((name, at) in named) measure(name, listOf(at, at, at, at, at))
    }

    private val EVENTS: List<Triple<String, Boolean, (PrayerDay) -> kotlin.time.Instant>> = listOf(
        Triple("fajr", true) { it.fajr }, Triple("sunrise", false) { it.sunrise }, Triple("dhuhr", true) { it.dhuhr },
        Triple("asr", true) { it.asr }, Triple("asrOther", true) { it.asrOther }, Triple("maghrib", true) { it.maghrib },
        Triple("isha", true) { it.isha }, Triple("endOfEating", false) { it.endOfEating },
    )

    /** One point's cost: per event, its days moved by 0, 1 … 5 and more minutes, the worst and the day of the worst. */
    private class Point(val label: String, val at: GeoPoint, val follows: AuthorityUnit, val riders: Int, val holders: Int) {
        var days = 0
        var movedDays = 0
        val moved = Array(EVENTS.size) { IntArray(7) }
        val worst = IntArray(EVENTS.size)
        val worstOn = arrayOfNulls<LocalDate>(EVENTS.size)
        /** For an event moved more than five minutes: the place whose table moved it the most on the worst day. */
        val because = arrayOfNulls<AuthorityUnit>(EVENTS.size)
        /** The same with only the places whose printed table is held riding (every one of them, none set aside). */
        val movedHeld = Array(EVENTS.size) { IntArray(7) }
        val worstHeld = IntArray(EVENTS.size)
        var movedDaysHeld = 0
        var heldRiders = 0
        val worstOfAll get() = worst.max()
    }

    private fun band(lat: Double) = if (lat >= 48) "north" else if (lat >= 46) "middle" else "south"

    fun cost(cities: List<EnvelopeRows.City>, step: Double, year: Int, threads: Int, out: File?) {
        val entry = requireNotNull(Registry.byId(ENTRY))
        val set = requireNotNull(Units.of(ENTRY))
        val byId = set.units.associateBy { it.id }
        // The places whose printed table the archive holds (87; Zhenis among them, checked against Zhetysay's times).
        val heldIds = QmdbPlaces.all.map { it.key }.toSet()

        fun measure(label: String, at: GeoPoint, zone: String, everyDays: Int, offset: Int): Point? {
            val r = Registry.resolveEntry(entry, place(at, zone))
            val m = r.method ?: return null
            val follows = byId[r.unitId] ?: return null
            val holders = set.holding(at)
            val p = Point(label, at, follows, m.alongside.size, holders.size)
            val alone = r.copy(method = m.copy(alongside = emptyList()))
            // Only the holders whose printed table is held, each as its own day (what the ride takes a holder as).
            val heldRiders = holders.filter { it.id != follows.id && it.id in heldIds }.map {
                Alongside(it.point, Registry.methodInUnit(ENTRY, entry.method!!, it, it.point, zone, alone = true).copy(fixedPoint = null))
            }
            p.heldRiders = heldRiders.size
            val heldOnly = if (heldRiders.isEmpty()) null else r.copy(method = m.copy(alongside = heldRiders))
            val tz = TimeZone.of(zone)
            var date = LocalDate(year, 1, 1).plus(offset % everyDays, DateTimeUnit.DAY)
            while (date.year == year) {
                p.days++
                if (m.alongside.isNotEmpty()) {
                    val shown = DayPipeline.unended(r, date, tz)
                    val nearest = DayPipeline.unended(alone, date, tz)
                    val withHeld = heldOnly?.let { DayPipeline.unended(it, date, tz) }
                    var any = false
                    var anyHeld = false
                    for ((i, event) in EVENTS.withIndex()) {
                        val (name, start, read) = event
                        val seconds = (read(shown) - read(nearest)).inWholeSeconds * (if (start) 1 else -1)
                        check(seconds >= 0) { "$at on $date: waiting for a neighbour moved $name the wrong way by $seconds s" }
                        val minutes = ((seconds + 59) / 60).toInt()
                        p.moved[i][minutes.coerceAtMost(6)]++
                        if (minutes > 0) any = true
                        if (minutes > p.worst[i]) { p.worst[i] = minutes; p.worstOn[i] = date }
                        val byHeld = if (withHeld == null) 0 else {
                            val s = (read(withHeld) - read(nearest)).inWholeSeconds * (if (start) 1 else -1)
                            check(s in 0..seconds) { "$at on $date: the held tables alone move $name by $s s, the whole ride by $seconds s" }
                            ((s + 59) / 60).toInt()
                        }
                        p.movedHeld[i][byHeld.coerceAtMost(6)]++
                        if (byHeld > 0) anyHeld = true
                        if (byHeld > p.worstHeld[i]) p.worstHeld[i] = byHeld
                    }
                    if (any) p.movedDays++
                    if (anyHeld) p.movedDaysHeld++
                } else {
                    for (i in EVENTS.indices) { p.moved[i][0]++; p.movedHeld[i][0]++ }
                }
                date = date.plus(everyDays, DateTimeUnit.DAY)
            }
            for ((i, event) in EVENTS.withIndex()) {
                if (p.worst[i] <= 5) continue
                // Which table: each day alongside by itself on the worst day, the one that reaches farthest.
                val (_, start, read) = event
                val date = p.worstOn[i]!!
                val nearest = read(DayPipeline.unended(alone, date, tz))
                val far = m.alongside.maxBy { a ->
                    (read(DayPipeline.unended(r.copy(method = m.copy(alongside = listOf(a))), date, tz)) - nearest).inWholeSeconds * (if (start) 1 else -1)
                }
                p.because[i] = holders.firstOrNull { it.point == far.point }
            }
            return p
        }

        fun run(title: String, points: List<Triple<String, GeoPoint, String>>, everyDays: Int): List<Point> {
            val pool = Executors.newFixedThreadPool(threads)
            val started = System.nanoTime()
            val measured = try {
                points.mapIndexed { i, (label, at, zone) -> pool.submit<Point?> { measure(label, at, zone, everyDays, i) } }.mapNotNull { it.get() }
            } finally {
                pool.shutdown()
            }
            println()
            println("$title: ${measured.size} places, every ${if (everyDays == 1) "day" else "${everyDays}th day"} of $year (${(System.nanoTime() - started) / 1_000_000_000} s)")
            val riding = measured.filter { it.riders > 0 }
            val days = measured.sumOf { it.days.toLong() }
            val riders = riding.map { it.riders }.sorted()
            println(
                "  ${riding.size} of them wait for another place's table (${measured.size - riding.size} lie in one reach alone); " +
                    "${measured.count { it.movedDays > 0 }} have any time moved; place-days ${days}, with any time moved ${measured.sumOf { it.movedDays.toLong() }} " +
                    String.format(Locale.ROOT, "(%.1f %%)", 100.0 * measured.sumOf { it.movedDays.toLong() } / days),
            )
            if (riders.isNotEmpty()) {
                println("  days computed beside the place's own: median ${riders[riders.size / 2]}, 9 in 10 at most ${riders[riders.size * 9 / 10]}, most ${riders.last()}; reaches holding a place: most ${measured.maxOf { it.holders }}")
            }
            println("  event          place-days      moved  share       +1       +2       +3       +4       +5      >5  median-of-moved worst where")
            for ((i, event) in EVENTS.withIndex()) {
                val sum = LongArray(7)
                for (p in measured) for (k in 0..6) sum[k] += p.moved[i][k].toLong()
                val moved = sum.drop(1).sum()
                var median = 0
                var seen = 0L
                for (k in 1..6) { seen += sum[k]; if (moved > 0 && seen * 2 >= moved) { median = k; break } }
                val worst = measured.maxBy { it.worst[i] }
                println(
                    String.format(
                        Locale.ROOT, "  %-12s %12d %10d %5.1f %% %8d %8d %8d %8d %8d %7d  %15s %5d %s",
                        event.first, sum.sum(), moved, 100.0 * moved / sum.sum(), sum[1], sum[2], sum[3], sum[4], sum[5], sum[6],
                        if (moved == 0L) "-" else if (median == 6) ">5" else "+$median", worst.worst[i],
                        if (worst.worst[i] == 0) "-" else "${worst.label} (${worst.worstOn[i]})",
                    ),
                )
            }
            // Whose day does the moving: the places with a printed table held, and the rest (the engine's own day for them).
            val withHeld = measured.count { it.heldRiders > 0 }
            val movedAll = measured.sumOf { it.movedDays.toLong() }
            val movedHeld = measured.sumOf { it.movedDaysHeld.toLong() }
            println(
                "  whose day moves it: $withHeld of the ${riding.size} stand inside the reach of another place whose printed table is held; " +
                    "with those tables' places alone riding a time moves on $movedHeld place-days " +
                    String.format(Locale.ROOT, "(%.1f %% of all, %.1f %% of the days moved)", 100.0 * movedHeld / days, if (movedAll == 0L) 0.0 else 100.0 * movedHeld / movedAll) +
                    "; the other ${movedAll - movedHeld} place-days move only by the day Taqwa calculates for a place whose table is not held",
            )
            println("  event         moved by held tables' places alone   +1       +2       +3      >3 worst | minutes of the whole wait: held tables' places, the rest")
            for ((i, event) in EVENTS.withIndex()) {
                val sum = LongArray(7)
                var minutesAll = 0L
                var minutesHeld = 0L
                for (p in measured) for (k in 0..6) {
                    sum[k] += p.movedHeld[i][k].toLong()
                    minutesAll += k.toLong() * p.moved[i][k]
                    minutesHeld += k.toLong() * p.movedHeld[i][k]
                }
                val moved = sum.drop(1).sum()
                println(
                    String.format(
                        Locale.ROOT, "  %-12s %12d (%5.1f %%)          %8d %8d %8d %7d %5d | %d min, %d of them the held tables' (%.1f %%), %d the rest",
                        event.first, moved, 100.0 * moved / sum.sum(), sum[1], sum[2], sum[3], sum[4] + sum[5] + sum[6], measured.maxOf { it.worstHeld[i] },
                        minutesAll, minutesHeld, if (minutesAll == 0L) 0.0 else 100.0 * minutesHeld / minutesAll, minutesAll - minutesHeld,
                    ),
                )
            }
            return measured
        }

        fun describe(p: Point, i: Int): String {
            val b = p.because[i]
            val why = if (b == null) "?" else String.format(
                Locale.ROOT, "%s %s (%.4f, %.4f; reach %.1f km, %s band, %s) %.1f km off, %+.3f° of latitude and %+.3f° of longitude from the place (%.1f min of sun to the west)",
                b.id, b.name, b.point.lat, b.point.lon, b.radiusKm, band(b.point.lat), if (b.measured) "checked" else "not checked",
                distanceKm(p.at, b.point), b.point.lat - p.at.lat, b.point.lon - p.at.lon, (p.at.lon - b.point.lon) * 4,
            )
            return String.format(
                Locale.ROOT, "%s (%.4f, %.4f; %s band) follows %s %s (reach %.1f km, %s), %d reaches hold it: %s +%d min on %s, by the table of %s",
                p.label, p.at.lat, p.at.lon, band(p.at.lat), p.follows.id, p.follows.name, p.follows.radiusKm, if (p.follows.measured) "checked" else "not checked",
                p.holders, EVENTS[i].first, p.worst[i], p.worstOn[i], why,
            )
        }

        fun overFive(title: String, measured: List<Point>, most: Int) {
            val over = measured.flatMap { p -> EVENTS.indices.filter { p.worst[it] > 5 }.map { p to it } }
            println("  $title with a time moved more than 5 minutes: ${over.map { it.first }.distinct().size}")
            over.sortedByDescending { (p, i) -> p.worst[i] }.take(most).forEach { (p, i) -> println("    " + describe(p, i)) }
            if (over.size > most) println("    … ${over.size - most} more lines")
            // The tables that do it, each with the places it moves so far and the worst.
            val by = over.groupBy { (p, i) -> p.because[i]?.id ?: "?" }
            if (by.size > 1) {
                println("  the tables that move a time more than 5 minutes: ${by.size}")
                by.entries.sortedByDescending { it.value.size }.take(most).forEach { (id, list) ->
                    val u = byId[id]
                    println(
                        "    $id ${u?.name} (reach ${u?.radiusKm} km, ${if (u?.measured == true) "checked" else "not checked"}): " +
                            "${list.map { it.first }.distinct().size} places, worst +${list.maxOf { (p, i) -> p.worst[i] }} min",
                    )
                }
            }
        }

        // 1. The app's cities.
        val inCities = run("The app's Kazakh cities", cities.map { Triple(it.name, it.at, it.zone) }, 1)
        println("  cities by the worst minute moved (each event's worst minute / days moved):")
        inCities.filter { it.movedDays > 0 }.sortedWith(compareByDescending<Point> { it.worstOfAll }.thenByDescending { it.movedDays }).forEach { p ->
            println(
                String.format(Locale.ROOT, "    %-18s follows %-28s %2d beside, any time moved on %3d days: ", p.label, p.follows.name.take(28), p.riders, p.movedDays) +
                    EVENTS.indices.filter { p.worst[it] > 0 }.joinToString(", ") { "${EVENTS[it].first} +${p.worst[it]} (${p.moved[it].drop(1).sum()} d)" },
            )
        }
        println("  cities that wait for another table and never move a minute: " + inCities.filter { it.riders > 0 && it.movedDays == 0 }.joinToString(", ") { it.label })
        println("  cities in one reach alone: " + inCities.filter { it.riders == 0 }.joinToString(", ") { it.label })
        overFive("cities", inCities, 40)

        // 2. Every point of QMDB's own list.
        val own = run("QMDB's own points", set.units.map { Triple("${it.id} ${it.name}", it.point, ZONE) }, 5)
        overFive("own points", own, 30)

        // 3. A grid over all the ground more than one reach holds.
        val south = set.units.minOf { it.point.lat } - 1
        val north = set.units.maxOf { it.point.lat } + 1
        val west = set.units.minOf { it.point.lon } - 1.5
        val east = set.units.maxOf { it.point.lon } + 1.5
        val grid = ArrayList<Triple<String, GeoPoint, String>>()
        var held = 0
        var lat = south
        while (lat <= north) {
            var lon = west
            while (lon <= east) {
                val at = GeoPoint(Math.round(lat * 1e6) / 1e6, Math.round(lon * 1e6) / 1e6)
                val n = set.holding(at).size
                if (n >= 1) held++
                if (n >= 2) grid += Triple("${at.lat}, ${at.lon}", at, ZONE)
                lon += step
            }
            lat += step
        }
        println()
        println("A grid of $step°: $held points inside any reach, ${grid.size} of them inside more than one (${String.format(Locale.ROOT, "%.1f", 100.0 * grid.size / held)} %)")
        val onGrid = run("The grid's points inside more than one reach", grid, 10)
        overFive("grid points", onGrid, 30)

        out?.let { file ->
            file.writeText(buildString {
                appendLine("set\tplace\tlat\tlon\tfollows\tbeside\tholders\tdays\tdaysMoved\t" + EVENTS.joinToString("\t") { it.first })
                for ((name, list) in listOf("city" to inCities, "own" to own, "grid" to onGrid)) for (p in list) {
                    if (p.movedDays == 0) continue
                    appendLine(listOf(name, p.label, p.at.lat, p.at.lon, p.follows.id, p.riders, p.holders, p.days, p.movedDays).joinToString("\t") + "\t" + p.worst.joinToString("\t"))
                }
            })
            println("every place moved: $file")
        }
    }

    /** The six printed columns of QMDB's year tables and what the day shows for each. */
    private val COLUMNS: List<Pair<String, (PrayerDay) -> kotlin.time.Instant>> = listOf(
        "fajr" to { it.fajr }, "sunrise" to { it.sunrise }, "dhuhr" to { it.dhuhr }, "asr" to { it.asr }, "maghrib" to { it.maghrib }, "isha" to { it.isha },
    )

    fun pairs(manifest: GateManifest, roots: OfficialRoots) {
        if (!roots.held) error("no official archive")
        val set = requireNotNull(Units.of(ENTRY))
        val tz = TimeZone.of(ZONE)
        // Each held table: the unit and its printed days (minutes of the day, by date).
        class Held(val unit: AuthorityUnit, val printed: Map<LocalDate, List<Int?>>)
        val held = set.units.mapNotNull { unit ->
            val rows = manifest.rows.filter {
                it.source == "kz-qmdb.tsv" && it.entry == ENTRY && it.unit == null && it.lat == unit.point.lat && it.lon == unit.point.lon && it.format == "daily"
            }
            if (rows.isEmpty()) return@mapNotNull null
            val days = rows.flatMap { OfficialTable.read(roots.file(it.path), it.format, it.columns.size).days }.associate { it.date to it.minutes }
            Held(unit, days)
        }
        fun minuteOfDay(at: kotlin.time.Instant): Int = at.toLocalDateTime(tz).let { it.hour * 60 + it.minute }
        val own = held.associate { h -> h.unit.id to QmdbReach.unitDays(h.unit, h.printed.keys.sorted()) }
        println("${held.size} places with a table held, ${held.sumOf { it.printed.size }} printed days")
        val spread = Array(COLUMNS.size) { sortedMapOf<Int, Long>() }
        class Line(val text: String, val worst: Int)
        val lines = ArrayList<Line>()
        var pairs = 0
        var days = 0L
        for ((i, a) in held.withIndex()) for (b in held.drop(i + 1)) {
            val km = distanceKm(a.unit.point, b.unit.point)
            if (km >= a.unit.radiusKm + b.unit.radiusKm) continue
            pairs++
            val least = IntArray(COLUMNS.size) { Int.MAX_VALUE }
            val most = IntArray(COLUMNS.size) { Int.MIN_VALUE }
            val printedMost = IntArray(COLUMNS.size) { Int.MIN_VALUE }
            val printedLeast = IntArray(COLUMNS.size) { Int.MAX_VALUE }
            for ((date, pa) in a.printed) {
                val pb = b.printed[date] ?: continue
                val ca = own.getValue(a.unit.id).getValue(date)
                val cb = own.getValue(b.unit.id).getValue(date)
                days++
                for ((k, column) in COLUMNS.withIndex()) {
                    val x = pa.getOrNull(k) ?: continue
                    val y = pb.getOrNull(k) ?: continue
                    val printed = x - y
                    val computed = minuteOfDay(column.second(ca)) - minuteOfDay(column.second(cb))
                    val apart = printed - computed
                    spread[k].merge(apart, 1L, Long::plus)
                    if (apart < least[k]) least[k] = apart
                    if (apart > most[k]) most[k] = apart
                    if (printed > printedMost[k]) printedMost[k] = printed
                    if (printed < printedLeast[k]) printedLeast[k] = printed
                }
            }
            val worst = COLUMNS.indices.maxOf { maxOf(kotlin.math.abs(least[it]), kotlin.math.abs(most[it])) }
            lines += Line(
                String.format(
                    Locale.ROOT, "  %-22s %-22s %5.1f km (reaches %.1f and %.1f; %+.3f° lat, %+.3f° lon = %+.1f min of sun): ", a.unit.name.take(22), b.unit.name.take(22),
                    km, a.unit.radiusKm, b.unit.radiusKm, a.unit.point.lat - b.unit.point.lat, a.unit.point.lon - b.unit.point.lon, (b.unit.point.lon - a.unit.point.lon) * 4,
                ) + COLUMNS.indices.joinToString(", ") { "${COLUMNS[it].first} printed ${printedLeast[it]}…${printedMost[it]}, apart ${least[it]}…${most[it]}" },
                worst,
            )
        }
        println("$pairs pairs of them share ground, $days days printed in both")
        println("How far the two printed minutes stand apart beyond the engine's own difference between the two points (minutes: days):")
        for ((k, column) in COLUMNS.withIndex()) println("  ${column.first.padEnd(8)} " + spread[k].entries.joinToString("  ") { "${if (it.key > 0) "+" else ""}${it.key}: ${it.value}" })
        println("Every pair, the widest apart first (first place's printed minute less the second's, then that less the engine's difference):")
        lines.sortedByDescending { it.worst }.forEach { println(it.text) }
    }
}
