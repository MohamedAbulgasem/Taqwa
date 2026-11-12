package world.taqwa.timetables.sanity

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.Box
import world.taqwa.app.prayer.engine.registry.Circle
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.round
import kotlin.time.Instant

/**
 * The sun-sanity pass (engine survey of 11 Nov 2026, verifier priority 6): what no official table is needed for. At
 * every app city (`cities.csv`, the places the app and the site compute for), every registry unit's reference point
 * and every region rule's own point, on every day of a window and in both Asr schools, the day the engine shows
 * ([DayPipeline.unended] at the place rounded to 1e-3°, as `PrayerEngine` computes it) is held to the real sun at the
 * place ([sky]: the exact sun, sea level, standard refraction, [DayPipeline.REAL_HORIZON_DEG], as the engine's own
 * real sunset reads it; the engine knows no place's elevation):
 *
 * - no Dhuhr before the sun's transit;
 * - no Maghrib before the real sunset;
 * - no Fajr, and no end of eating, after the real sunrise;
 * - no Isha at or before Maghrib.
 *
 * A breach on a day the engine itself marks ([Excuse]: an authority time it declares it does not show, ruling R82; a
 * time a high-latitude or clock rule set; a polar day) is excused, and listed with its count, never hidden; a Dhuhr
 * before transit never is (the sun transits on a polar day too, and no rule may put noon before it). Every other
 * breach is a finding. Both are pinned: a finding must be listed in `open-findings.tsv` and an excused group in
 * `excused.tsv`, each with a ceiling on its worst seconds and its points ([Listed]), so a new one, or a listed one
 * that grows, fails the pass ([problems]). A day the real sun does not rise (or set) at the point checks no Fajr or
 * end of eating (or Maghrib), and is counted as such. Days
 * [Invariants][world.taqwa.app.prayer.engine.day.Invariants] had to repair are counted too.
 *
 * Automatic is what most users follow, so every app city is checked under it; the entries Settings offers are checked
 * too ([points], [Kind.CHOSEN]): each one at a spread sample of the app cities where it applies and Automatic follows
 * another entry, every Other method (`other.*`) among them, at the engine's default high-latitude setting.
 */
object SunSanity {

    enum class Check(val key: String, val words: String) {
        DHUHR("dhuhr-before-transit", "Dhuhr before the sun's transit"),
        MAGHRIB("maghrib-before-sunset", "Maghrib before the real sunset"),
        FAJR("fajr-after-sunrise", "Fajr after the real sunrise"),
        END_OF_EATING("end-of-eating-after-sunrise", "end of eating after the real sunrise"),
        ISHA("isha-not-after-maghrib", "Isha at or before Maghrib"),
        ;

        companion object {
            fun byKey(key: String): Check = entries.firstOrNull { it.key == key } ?: error("no check $key")
        }
    }

    /** What the engine's own day says about a breach: an excuse with its rule, never a silent one. */
    enum class Excuse(val key: String, val words: String) {
        DECLARED("declared", "the day declares it does not show this authority time (PrayerDay.notFollowed, ruling R82)"),
        SET_BY_RULE("set-by-rule", "a high-latitude or clock rule set this time (PrayerDay.setByRule)"),
        POLAR("polar", "a polar day: the sun does not rise or set and the times follow the nearest latitude where it does (PrayerDay.polar)"),
        ;

        companion object {
            fun byKey(key: String): Excuse = entries.firstOrNull { it.key == key } ?: error("no excuse $key")
        }
    }

    /** The real sun at a point on a date, in UTC epoch seconds: its transit, and its rise and set where it has them. */
    class Sky(val transit: Double, val sunrise: Double?, val sunset: Double?)

    /** The real sun at [point] on [date] in [zone]: the exact model, the zone's offset at local noon, as `PrayerEngine.sky`. */
    fun sky(point: GeoPoint, date: LocalDate, zone: TimeZone): Sky {
        val noonOffset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val clock = SunClock(point.lat, point.lon, date, noonOffset, SunModel.EXACT)
        return Sky(
            transit = clock.transit(),
            sunrise = clock.altitudeTime(DayPipeline.REAL_HORIZON_DEG, morning = true),
            sunset = clock.altitudeTime(DayPipeline.REAL_HORIZON_DEG, morning = false),
        )
    }

    /** One check failed on one day, by [seconds] on the wrong side, with the engine's own [excuse] for it or none. */
    class Breach(val check: Check, val seconds: Double, val excuse: Excuse?)

    /** Every check [day] fails against [sky]; a Dhuhr before transit is never excused. */
    fun breaches(day: PrayerDay, sky: Sky): List<Breach> {
        val out = ArrayList<Breach>(0)
        fun excuse(prayer: Prayer): Excuse? = when {
            prayer in day.notFollowed -> Excuse.DECLARED
            prayer in day.setByRule -> Excuse.SET_BY_RULE
            day.polar -> Excuse.POLAR
            else -> null
        }
        val dhuhr = seconds(day.dhuhr)
        if (dhuhr < sky.transit) out += Breach(Check.DHUHR, sky.transit - dhuhr, excuse = null)
        sky.sunset?.let { sunset ->
            val maghrib = seconds(day.maghrib)
            if (maghrib < sunset) out += Breach(Check.MAGHRIB, sunset - maghrib, excuse(Prayer.MAGHRIB))
        }
        sky.sunrise?.let { sunrise ->
            val fajr = seconds(day.fajr)
            if (fajr > sunrise) out += Breach(Check.FAJR, fajr - sunrise, excuse(Prayer.FAJR))
            val end = seconds(day.endOfEating)
            if (end > sunrise) out += Breach(Check.END_OF_EATING, end - sunrise, excuse(Prayer.FAJR))
        }
        if (day.isha <= day.maghrib) {
            out += Breach(Check.ISHA, seconds(day.maghrib) - seconds(day.isha), excuse(Prayer.ISHA))
        }
        return out
    }

    private fun seconds(instant: Instant): Double = instant.epochSeconds.toDouble()

    // ---- the points ----

    enum class Kind(val words: String) {
        CITY("app cities"),
        UNIT("unit points"),
        REGION("region points"),
        CHOSEN("Settings choices at app cities"),
    }

    /** A place the pass computes, under Automatic ([entryId] null) or under one entry chosen there. */
    data class Point(val label: String, val kind: Kind, val place: Place, val entryId: String? = null)

    /** An app city as `cities.csv` lists it. */
    data class City(val id: Int, val name: String, val region: String?, val cc: String, val lat: Double, val lon: Double, val zone: String)

    fun cities(file: File): List<City> = file.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
        val f = line.split(',')
        require(f.size == 8) { "cities.csv: not 8 fields: $line" }
        City(f[0].toInt(), f[1], f[2].ifEmpty { null }, f[4].uppercase(), f[5].toDouble(), f[6].toDouble(), f[7])
    }

    /** As `PrayerEngine` takes a place: to 1e-3°, the country upper case. */
    fun canonical(lat: Double, lon: Double, zone: String, cc: String, admin1: String?): Place =
        Place(round(lat * 1000.0) / 1000.0, round(lon * 1000.0) / 1000.0, zone, cc.uppercase(), admin1)

    /**
     * Every point the pass computes: each app city under Automatic; each unit of every registry entry at its own
     * point, under that entry (and under Automatic there where that is another entry); each region rule's circle
     * centre or box centre under Automatic. A unit or region point takes the zone, country and first-level region of
     * the nearest app city in its entry's (its rule's) countries, from the whole list. [everyCity] keeps every n-th app
     * city as a point (a sample; unit and region points are always all kept).
     *
     * And each entry Settings offers, as the app chooses it ([Kind.CHOSEN]): at the app cities where it is in scope
     * (`Registry.inScope`, as the engine reads a choice) and Automatic follows another entry (where Automatic follows it,
     * the city is already checked under it), at most [chosenPerEntry] of them spread evenly over the list's order
     * (countries and their cities), and [otherPerEntry] for each Other method, which applies at every city.
     */
    fun points(cities: List<City>, everyCity: Int = 1, chosenPerEntry: Int = 40, otherPerEntry: Int = 400): List<Point> {
        val out = ArrayList<Point>()
        val places = cities.map { c -> canonical(c.lat, c.lon, c.zone, c.cc, c.region) }
        fun cityLabel(c: City) = "${c.name}, ${c.cc} (city ${c.id})"
        for ((i, c) in cities.withIndex()) if (i % everyCity == 0) out += Point(cityLabel(c), Kind.CITY, places[i])
        out += chosenPoints(cities, places, chosenPerEntry, otherPerEntry, ::cityLabel)
        val byCountry = cities.groupBy { it.cc }
        fun nearest(p: GeoPoint, countries: Set<String>): City {
            val pool = countries.flatMap { byCountry[it].orEmpty() }.ifEmpty { cities }
            return pool.minBy { distanceKm(p, GeoPoint(it.lat, it.lon)) }
        }
        val seen = HashSet<Pair<String, GeoPoint>>()
        for (entry in Registry.entries) {
            val units = Units.of(entry.id)?.units ?: continue
            for (unit in units) {
                if (!seen.add(entry.id to unit.point)) continue
                val near = nearest(unit.point, entry.countries)
                val place = canonical(unit.point.lat, unit.point.lon, near.zone, near.cc, near.region)
                out += Point("${entry.id} unit ${unit.id} (${unit.name})", Kind.UNIT, place, entry.id)
                if (Registry.automaticEntry(place).id != entry.id) {
                    out += Point("${entry.id} unit ${unit.id} (${unit.name}), Automatic there", Kind.UNIT, place)
                }
            }
        }
        for (rule in Regions.rules) {
            val centre = when (val area = rule.area) {
                is Circle -> area.center
                is Box -> GeoPoint((area.south + area.north) / 2, (area.west + area.east) / 2)
                else -> null
            } ?: continue
            val near = nearest(centre, setOf(rule.countryCode))
            out += Point("region ${rule.name} (${rule.entryId})", Kind.REGION, canonical(centre.lat, centre.lon, near.zone, rule.countryCode, near.region))
        }
        return out
    }

    /** [points]' Settings choices: per entry, the cities it applies at besides Automatic's own, spread evenly. */
    private fun chosenPoints(
        cities: List<City>,
        places: List<Place>,
        chosenPerEntry: Int,
        otherPerEntry: Int,
        cityLabel: (City) -> String,
    ): List<Point> {
        val others = Registry.otherMethods.map { it.id }.toSet()
        val byCountry = Registry.entries.flatMap { e -> e.countries.map { it.uppercase() to e } }.groupBy({ it.first }, { it.second })
        val at = LinkedHashMap<String, MutableList<Int>>()
        for ((i, place) in places.withIndex()) {
            val automatic = Registry.automaticEntry(place)
            // inScope's own candidates: an Other method, an entry of the place's country, one Automatic lists nearby or as a member.
            val candidates = Registry.otherMethods + byCountry[place.countryCode].orEmpty() +
                automatic.nearby.mapNotNull { Registry.byId(it) } + automatic.members.mapNotNull { Registry.byId(it.id) }
            for (entry in candidates.distinctBy { it.id }) {
                if (entry.id != automatic.id && Registry.inScope(entry, place)) at.getOrPut(entry.id) { mutableListOf() } += i
            }
        }
        val out = ArrayList<Point>()
        for (entry in Registry.entries) {
            val indices = at[entry.id] ?: continue
            val take = minOf(indices.size, if (entry.id in others) otherPerEntry else chosenPerEntry)
            for (k in 0 until take) {
                val i = indices[(k.toLong() * indices.size / take).toInt()]
                out += Point("${cityLabel(cities[i])}, ${entry.id} chosen", Kind.CHOSEN, places[i], entry.id)
            }
        }
        return out
    }

    // ---- the run ----

    /** One check's breaches at one point: days, the first and last, the worst and when. */
    class PointTally(val label: String, val unit: String?) {
        var days = 0
        val schools = sortedSetOf<String>()
        var first: LocalDate? = null
        var last: LocalDate? = null
        var worst = 0.0
        var worstDate: LocalDate? = null

        fun add(date: LocalDate, school: AsrSchool, seconds: Double) {
            days++
            schools += school.name.lowercase()
            if (first == null || date < first!!) first = date
            if (last == null || date > last!!) last = date
            if (seconds > worst) {
                worst = seconds
                worstDate = date
            }
        }
    }

    /** A finding or an excuse: one entry's one check, and the excuse if any. */
    data class Key(val entryId: String, val check: Check, val excuse: Excuse?)

    class Result(val from: LocalDate, val to: LocalDate) {
        val points = sortedMapOf<Kind, Int>()
        var placeDays = 0L
        val groups = LinkedHashMap<Key, MutableList<PointTally>>()
        /** Days per entry the real sun did not rise, or did not set, at the point (checks skipped, not failed). */
        val noSunrise = sortedMapOf<String, Int>()
        val noSunset = sortedMapOf<String, Int>()
        /** Days per entry the invariants repaired (a start moved later, or an end earlier). */
        val repaired = sortedMapOf<String, Int>()

        val findings: Map<Key, List<PointTally>> get() = groups.filterKeys { it.excuse == null }
        val excused: Map<Key, List<PointTally>> get() = groups.filterKeys { it.excuse != null }
    }

    /**
     * The pass over [points] from [from] to [to] (every [everyDays]-th day), in both schools, on [threads] threads. [change]
     * rewrites each resolved method first, as the gate's own `change` does (a what-if: a margin raised to clear a finding).
     */
    fun run(
        points: List<Point>,
        from: LocalDate,
        to: LocalDate,
        everyDays: Int = 1,
        threads: Int = Runtime.getRuntime().availableProcessors(),
        change: ((TimetableMethod) -> TimetableMethod)? = null,
    ): Result {
        val dates = generateSequence(from) { it.plus(everyDays, DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()
        val result = Result(from, to)
        val pool = Executors.newFixedThreadPool(threads.coerceAtLeast(1))
        try {
            val futures = points.map { point -> pool.submit<Local> { one(point, dates, change) } }
            for ((i, f) in futures.withIndex()) {
                val local = f.get()
                val point = points[i]
                result.points.merge(point.kind, 1, Int::plus)
                result.placeDays += local.placeDays
                for ((key, tally) in local.tallies) result.groups.getOrPut(key) { mutableListOf() } += tally
                if (local.noSunrise > 0) result.noSunrise.merge(local.entryId, local.noSunrise, Int::plus)
                if (local.noSunset > 0) result.noSunset.merge(local.entryId, local.noSunset, Int::plus)
                if (local.repaired > 0) result.repaired.merge(local.entryId, local.repaired, Int::plus)
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.MINUTES)
        }
        return result
    }

    private class Local(val entryId: String) {
        var placeDays = 0L
        val tallies = LinkedHashMap<Key, PointTally>()
        var noSunrise = 0
        var noSunset = 0
        var repaired = 0
    }

    /** The resolution [point] is computed with: its entry's at the place, or Automatic's. */
    fun resolution(point: Point): Resolution = point.entryId
        ?.let { Registry.resolveEntry(requireNotNull(Registry.byId(it)) { "no entry $it" }, point.place) }
        ?: Registry.resolve(point.place)

    private fun one(point: Point, dates: List<LocalDate>, change: ((TimetableMethod) -> TimetableMethod)?): Local {
        val resolution = resolution(point).let { r -> if (change == null) r else r.copy(method = r.method?.let(change)) }
        val zone = TimeZone.of(point.place.zoneId)
        val at = GeoPoint(point.place.lat, point.place.lon)
        val local = Local(resolution.entry.id)
        for (date in dates) {
            val sky = sky(at, date, zone)
            if (sky.sunrise == null) local.noSunrise++
            if (sky.sunset == null) local.noSunset++
            for (school in AsrSchool.entries) {
                val day = DayPipeline.unended(resolution, date, zone, school)
                local.placeDays++
                if (day.repaired) local.repaired++
                for (b in breaches(day, sky)) {
                    local.tallies.getOrPut(Key(resolution.entry.id, b.check, b.excuse)) { PointTally(point.label, resolution.unitId) }
                        .add(date, school, b.seconds)
                }
            }
        }
        return local
    }

    // ---- the open findings and the report ----

    /**
     * A group the pass knows of: an open finding (`open-findings.tsv`, [excuse] null: not fixed yet, with the smallest
     * safe fix in its [note]) or an excused one (`excused.tsv`, its [excuse]), each with its ceilings: the most seconds
     * on the wrong side ([worstSeconds]) and the most points ([points]) it may reach before the pass fails on it.
     */
    data class Listed(
        val entryId: String,
        val check: Check,
        val excuse: Excuse?,
        val worstSeconds: Int,
        val points: Int,
        val note: String,
    ) {
        val key: Key get() = Key(entryId, check, excuse)
    }

    /** `open-findings.tsv`: entry, check key, worst seconds, points, note; `#` lines are comments. */
    fun openFindings(file: File): List<Listed> = listed(file, excused = false)

    /** `excused.tsv`: entry, check key, excuse key, worst seconds, points, note; `#` lines are comments. */
    fun excusedGroups(file: File): List<Listed> = listed(file, excused = true)

    private fun listed(file: File, excused: Boolean): List<Listed> = if (!file.isFile) emptyList() else file.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }
        .filter { it[0] != "entry" }
        .map { f ->
            val n = if (excused) 6 else 5
            require(f.size == n) { "${file.name}: not $n fields: ${f.joinToString("\t")}" }
            val rest = if (excused) f.drop(3) else f.drop(2)
            Listed(
                entryId = f[0],
                check = Check.byKey(f[1]),
                excuse = if (excused) Excuse.byKey(f[2]) else null,
                worstSeconds = rest[0].toInt(),
                points = rest[1].toInt(),
                note = rest[2],
            )
        }

    /**
     * What fails the pass, one line each: a finding not in [open], an excused group not in [excused], and a listed
     * group past either ceiling (worse than its worst seconds, or at more points).
     */
    fun problems(result: Result, open: List<Listed>, excused: List<Listed>): List<String> {
        val listed = (open + excused).associateBy { it.key }
        val out = mutableListOf<String>()
        for ((key, tallies) in result.groups) {
            val words = "${key.entryId} ${key.check.key}" + (key.excuse?.let { " (${it.key})" } ?: "")
            val l = listed[key]
            if (l == null) {
                out += "$words: not listed in ${if (key.excuse == null) "open-findings.tsv" else "excused.tsv"}"
                continue
            }
            val worst = tallies.maxOf { it.worst }
            if (worst > l.worstSeconds) out += "$words: worst ${String.format(Locale.ROOT, "%.1f", worst)} s, over its ceiling of ${l.worstSeconds} s"
            if (tallies.size > l.points) out += "$words: ${tallies.size} points, over its ceiling of ${l.points}"
        }
        return out
    }

    /** The listed groups the pass no longer finds: fixed, or gone (the list should drop them). */
    fun fixed(result: Result, listed: List<Listed>): List<Listed> = listed.filter { it.key !in result.groups }

    fun report(result: Result, open: List<Listed>, excusedListed: List<Listed> = emptyList(), placesShown: Int = 12): String = buildString {
        val total = result.points.values.sum()
        appendLine(
            "Sun sanity: ${fmt(total.toLong())} points (" + result.points.entries.joinToString(", ") { "${fmt(it.value.toLong())} ${it.key.words}" } +
                "), ${result.from}..${result.to}, both Asr schools: ${fmt(result.placeDays)} place-days.",
        )
        appendLine("Held to the real sun at the point (exact model, sea level, standard refraction -0.8333°): Dhuhr not before transit,")
        appendLine("Maghrib not before sunset, Fajr and the end of eating not after sunrise, Isha after Maghrib.")
        appendLine()
        val findings = result.findings
        appendLine("Findings (nothing in the day excuses them): ${findings.size}")
        for ((key, tallies) in findings.entries.sortedWith(compareBy({ it.key.entryId }, { it.key.check }))) {
            appendGroup(key, tallies, placesShown)
            appendListed(open.firstOrNull { it.key == key }, "open-findings.tsv")
        }
        appendLine()
        val excused = result.excused
        appendLine("Excused by the day itself (listed, not hidden; never a Dhuhr): ${excused.size}")
        for ((key, tallies) in excused.entries.sortedWith(compareBy({ it.key.entryId }, { it.key.check }, { it.key.excuse }))) {
            appendGroup(key, tallies, 5)
            appendListed(excusedListed.firstOrNull { it.key == key }, "excused.tsv")
        }
        appendLine()
        appendLine("Dates the real sun does not rise at a point (Fajr and end of eating not checked), counted per point:")
        for ((entry, n) in result.noSunrise) appendLine("  $entry: ${fmt(n.toLong())}")
        appendLine("Dates the real sun does not set at a point (Maghrib not checked), counted per point:")
        for ((entry, n) in result.noSunset) appendLine("  $entry: ${fmt(n.toLong())}")
        appendLine("Days the invariants repaired (Invariants.repair moved a start later or an end earlier), place-days in both schools:")
        for ((entry, n) in result.repaired) appendLine("  $entry: ${fmt(n.toLong())}")
        val problems = problems(result, open, excusedListed)
        val fixed = fixed(result, open + excusedListed)
        appendLine()
        if (problems.isEmpty()) {
            appendLine("Every finding is listed in open-findings.tsv and every excused group in excused.tsv, each within its ceilings.")
        } else {
            appendLine("Failing (${problems.size}):")
            for (p in problems) appendLine("  $p")
        }
        if (fixed.isNotEmpty()) {
            appendLine("Listed but not found in this run (drop them if the run is the whole pass): " +
                fixed.joinToString(", ") { "${it.entryId} ${it.check.key}" + (it.excuse?.let { e -> " (${e.key})" } ?: "") })
        }
    }

    private fun StringBuilder.appendListed(listed: Listed?, file: String) {
        if (listed == null) appendLine("    NOT LISTED in $file")
        else appendLine("    listed (ceilings ${listed.worstSeconds} s, ${listed.points} points): ${listed.note}")
    }

    private fun StringBuilder.appendGroup(key: Key, tallies: List<PointTally>, placesShown: Int) {
        val days = tallies.sumOf { it.days }
        val first = tallies.mapNotNull { it.first }.min()
        val last = tallies.mapNotNull { it.last }.max()
        val worst = tallies.maxBy { it.worst }
        val schools = tallies.flatMap { it.schools }.toSortedSet().joinToString("+")
        appendLine(
            "  ${key.entryId}: ${key.check.words}" + (key.excuse?.let { ", excused: ${it.words}" } ?: "") +
                " — ${fmt(tallies.size.toLong())} points, ${fmt(days.toLong())} place-days ($schools), $first..$last, worst ${secs(worst.worst)} at ${worst.label}" +
                (worst.unit?.let { " [unit $it]" } ?: "") + " on ${worst.worstDate}",
        )
        for (t in tallies.sortedByDescending { it.worst }.take(placesShown)) {
            appendLine("      ${t.label}${t.unit?.let { " [unit $it]" } ?: ""}: ${t.days} days, ${t.first}..${t.last}, worst ${secs(t.worst)} on ${t.worstDate}")
        }
        if (tallies.size > placesShown) appendLine("      … and ${tallies.size - placesShown} more points")
    }

    private fun secs(s: Double): String = if (s < 120) String.format(Locale.ROOT, "%.0f s", s) else String.format(Locale.ROOT, "%.1f min", s / 60)

    private fun fmt(n: Long): String = String.format(Locale.ROOT, "%,d", n)
}
