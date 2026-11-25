package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.FixCountry
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.TestPaths
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every gate point resolved the way a GPS fix is (rulings R258 and R259; the second trust audit's G4 and G5, 14–15 Nov
 * 2026): the app's own rule, [FixCountry], over the app's whole city list. A fix is named after, and takes the country
 * and region of, the nearest app city; where the device zone belongs to other countries than that city's and one of
 * their cities is near enough, the place may be in that country too, and the engine holds the day to both countries'
 * Automatic entries ([Registry.border]: each start the later, each end the earlier). The gate checks each row under its
 * entry's own country alone (`Gate`: `Place(…, entry.countries.first())`), so it cannot see a GPS user at the same
 * point handed a neighbour's entry; this does, three ways:
 *
 * 1. Under the row's own zone, the point "keeps its country" when its entry's country (or one whose Automatic entry
 *    there is the same: Western Sahara and Morocco both follow Habous) is the nearest city's or the one beside it. The
 *    points that do not are listed, each with why, in `official/gps-country/elsewhere.tsv` (the PA Iftaa's tables of
 *    Arab towns inside Israel); a point not listed that loses its country fails, and so does a listed one that no
 *    longer does. The failure prints the lines to add or remove. This proves the rule under a correct zone only. A reach
 *    or envelope row's point is generated around a unit's table and may lie across a border or at sea on purpose, so
 *    such a point is held here only where its nearest app city is of its entry's own country (ruling R286, the
 *    re-audit's GPS-1 of 15 Nov 2026, narrowed by its review): one whose nearest city is foreign is left out, and test 3
 *    does not reach it either (it holds only points that keep their country), while test 2 still tries every point.
 * 2. Under every zone of another country with an app city within [FixCountry.PLAUSIBLE_KM] (a phone on a neighbour's
 *    network, a zone set by hand, a link zone's target): a point whose nearest city is its own country's keeps it,
 *    always (R256 did not: Johor Bahru under Singapore's zone took Singapore's entry alone). The points whose nearest
 *    city is foreign and whose zone is too lose their own country there; they are counted, and only a bundled border
 *    lookup would settle them.
 * 3. Wherever (1) holds the point to two countries, the day shown is never before the row's own entry's day, in both
 *    Asr schools, and never ends after it, on a date every two weeks of 2026; and how much later it is (the cost of
 *    not knowing the side) is printed, worst first. Rules only, never a figure from a table.
 */
class GpsCountryTest {

    private data class City(val name: String, val region: String, val cc: String, val lat: Double, val lon: Double, val zone: String)

    private val cities: List<City> by lazy {
        TestPaths.appFiles.resolve("cities.csv").readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val f = line.split(',')
            City(f[1], f[2], f[4].uppercase(), f[5].toDouble(), f[6].toDouble(), f[7])
        }
    }

    private val index by lazy { FixCountry.Index(cities, { it.lat }, { it.lon }, { it.cc }, { it.zone }) }

    private class Point(val at: GeoPoint, val zone: String, val entry: RegistryEntry, val source: String) {
        /**
         * Whether the point is one a generator placed around a unit's table rather than a place a table is printed for:
         * every row at it is a reach row (`*-reach.tsv`, a unit's own tables replayed across its reach, ruling R270) or
         * an envelope row (`*-envelope.tsv`, a table replayed at the cities and rings around it, promise only). Such a
         * point falls wherever the circle does, across a border or in the sea (the PA Iftaa's reach around Acre and
         * Tulkarm into Lebanon, Jordan and Syria; Egypt's ring past Taba into Jordan), on purpose: where it falls past
         * the border, what it proves is the unit's own table there, never which country a fix at it is in. Ruling R286.
         */
        val generated: Boolean get() = generatedSource(source)
    }

    /**
     * Every gate row's point, once per point, entry and zone; but not the rows of a calculated default (`default.*`) or
     * an Other method (`other.*`): those are checked against tables held across a region or the world, at places
     * outside the countries they are Automatic in, on purpose (`safe-default-*.tsv`, Diyanet Australia's mosques as
     * Other › Turkey). A place there follows its own country's entry, which its own rows check.
     */
    private val points: List<Point> by lazy {
        // A point a table is printed for is taken from its own row, before any reach or envelope row at the same point
        // (`ps-iftaa-reach.tsv` sorts before `ps-iftaa.tsv`), so [Point.generated] is true only where no own row stands.
        val rows = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")).rows.sortedBy { generatedSource(it.source) }
        val seen = HashSet<String>()
        rows.mapNotNull { row ->
            if (row.entry.startsWith("default.") || row.entry.startsWith("other.")) return@mapNotNull null
            val entry = Registry.byId(row.entry) ?: return@mapNotNull null
            val at = when {
                row.lat != null && row.lon != null -> GeoPoint(row.lat, row.lon)
                row.unit != null -> Units.of(entry.id)?.units?.firstOrNull { it.id == row.unit }?.point
                else -> entry.method?.fixedPoint
            } ?: return@mapNotNull null
            if (seen.add("${key(at, entry.id)} ${row.zone}")) Point(at, row.zone, entry, row.source) else null
        }
    }

    private fun key(at: GeoPoint, entryId: String) = String.format(Locale.ROOT, "%.6f\t%.6f\t%s", at.lat, at.lon, entryId)

    /** Whether a place at [p] in country [cc] keeps [p]'s entry's country (or one whose Automatic entry there is the same). */
    private fun keeps(p: Point, cc: String): Boolean {
        if (cc in p.entry.countries) return true
        val own = p.entry.countries.firstOrNull() ?: return false
        return Registry.automaticEntry(Place(p.at.lat, p.at.lon, p.zone, cc)).id ==
            Registry.automaticEntry(Place(p.at.lat, p.at.lon, p.zone, own)).id
    }

    /** Whether [choice] keeps [p]'s country: its nearest city's country or the one beside it (ruling R259). */
    private fun keepsEither(p: Point, choice: FixCountry.Choice<City>): Boolean =
        keeps(p, choice.city.cc) || choice.beside?.let { keeps(p, it.cc) } == true

    private val listed: Map<String, String> by lazy {
        TestPaths.repoRoot.resolve("tools/timetables/official/gps-country/elsewhere.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("lat\t") }
            .associate { line ->
                val f = line.split('\t')
                require(f.size == 5) { "elsewhere.tsv: lat, lon, entry, country, why: $line" }
                "${f[0]}\t${f[1]}\t${f[2]}" to f[3]
            }
    }

    @Test
    fun `every gate point resolved the GPS way keeps its entry's country unless listed`() {
        var foreignNearest = 0
        val changed = LinkedHashMap<String, String>()
        val byReason = sortedMapOf<String, Int>()
        // Ruling R286 (as narrowed by the lane's review): a reach or envelope point ([Point.generated]) is left out only
        // where its nearest app city is of another country: generated across a border or at sea, it is no place of the
        // entry's country. One whose nearest city is its own country's is real ground where people follow the table,
        // and between cities is where a nearest-city rule can go wrong, so it is held here like a printed point.
        var acrossBorder = 0
        val held = points.filter { p ->
            val nearest = requireNotNull(index.nearest(p.at.lat, p.at.lon)).first
            (!p.generated || keeps(p, nearest.cc)).also { if (!it) acrossBorder++ }
        }
        for (p in held) {
            val nearest = requireNotNull(index.nearest(p.at.lat, p.at.lon)).first
            if (!keeps(p, nearest.cc)) foreignNearest++
            val choice = requireNotNull(FixCountry.choice(p.at.lat, p.at.lon, p.zone, index))
            if (keepsEither(p, choice)) continue
            val k = key(p.at, p.entry.id)
            changed[k] = "${choice.city.cc}\t${p.source}: nearest app city ${choice.city.name} (${choice.city.cc}, " +
                String.format(Locale.ROOT, "%.1f", choice.km) + " km), ${choice.reason.name.lowercase()} under ${p.zone}"
            byReason.merge(choice.reason.name.lowercase(), 1, Int::plus)
        }
        println(
            "GPS country (R259, R286): ${held.size} gate points held (of ${points.size}; $acrossBorder reach or envelope points " +
                "with a foreign app city nearest left out); $foreignNearest have an app city of another country nearest " +
                "(the nearest-city rule alone would hand them that country); ${changed.size} lose their country " +
                "under the row's zone ($byReason); elsewhere.tsv lists ${listed.size}",
        )
        val unlisted = changed.filterKeys { it !in listed }
        val stale = listed.filterKeys { it !in changed }
        val wrongCountry = changed.filter { (k, v) -> k in listed && listed[k] != v.substringBefore('\t') }
        assertTrue(
            unlisted.isEmpty() && stale.isEmpty() && wrongCountry.isEmpty(),
            buildString {
                if (unlisted.isNotEmpty()) {
                    append("${unlisted.size} gate points change country the GPS way and are not listed in official/gps-country/elsewhere.tsv. ")
                    append("A point inside its entry's country must not (fix FixCountry); one that truly lies across the border is ")
                    append("listed with why (lat, lon, entry, country, why):\n")
                    unlisted.forEach { (k, v) -> append("$k\t$v\n") }
                }
                if (stale.isNotEmpty()) {
                    append("${stale.size} listed points no longer change country (or are no longer gate points); remove:\n")
                    stale.keys.forEach { append("$it\n") }
                }
                if (wrongCountry.isNotEmpty()) {
                    append("listed with another country:\n")
                    wrongCountry.forEach { (k, v) -> append("$k\t$v\n") }
                }
            },
        )
    }

    /** The zones of other countries' app cities within [FixCountry.PLAUSIBLE_KM] of [p], a handful at most. */
    private fun neighbourZones(p: Point): List<String> {
        val found = LinkedHashSet<String>()
        while (found.size < 8) {
            val (city, _) = index.nearest(p.at.lat, p.at.lon, withinKm = FixCountry.PLAUSIBLE_KM) { c ->
                c.zone !in found && c.zone != p.zone && !keeps(p, c.cc)
            } ?: break
            found += city.zone
        }
        return found.toList()
    }

    @Test
    fun `under a neighbour's zone a point whose nearest city is its own country's keeps it`() {
        var pairs = 0
        var residual = 0
        val residualBy = sortedMapOf<String, Int>()
        val wrong = ArrayList<String>()
        for (p in points) {
            val nearest = requireNotNull(index.nearest(p.at.lat, p.at.lon)).first
            for (zone in neighbourZones(p)) {
                pairs++
                val choice = requireNotNull(FixCountry.choice(p.at.lat, p.at.lon, zone, index))
                if (keepsEither(p, choice)) continue
                if (keeps(p, nearest.cc)) {
                    wrong += "${key(p.at, p.entry.id)} under $zone: ${choice.city.name} (${choice.city.cc}) beside ${choice.beside?.cc}"
                } else {
                    residual++
                    residualBy.merge("${p.entry.countries.firstOrNull()}<-${choice.city.cc}", 1, Int::plus)
                }
            }
        }
        println(
            "GPS country under neighbours' zones (R259): $pairs point-and-zone pairs; $residual lose their own country " +
                "(nearest app city and zone both the neighbour's: only a border lookup settles them), by own<-given: $residualBy",
        )
        assertTrue(wrong.isEmpty(), "points whose nearest app city is their own country's lose it under a neighbour's zone:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun `a point held to two countries is never before its own entry's day and the cost is printed`() {
        val dates = generateSequence(LocalDate(2026, 1, 1)) { it.plus(14, DateTimeUnit.DAY) }.takeWhile { it.year == 2026 }.toList()
        val events = listOf("Fajr", "Sunrise", "Dhuhr", "Asr standard", "Asr Hanafi", "Maghrib", "Isha", "End of eating")
        val worst = events.associateWith { 0L }.toMutableMap()
        val worstAt = HashMap<String, String>()
        val early = ArrayList<String>()
        var held = 0
        for (p in points) {
            val own = p.entry.countries.firstOrNull() ?: continue
            // Only rows of the entry Automatic follows in its country there: a named table is not what a GPS fix is held to.
            if (Registry.automaticEntry(Place(p.at.lat, p.at.lon, p.zone, own)).id != p.entry.id) continue
            val choice = requireNotNull(FixCountry.choice(p.at.lat, p.at.lon, p.zone, index))
            val beside = choice.beside ?: continue
            if (!keepsEither(p, choice)) continue
            val place = Place(
                p.at.lat, p.at.lon, p.zone, choice.city.cc, choice.city.region.ifEmpty { null },
                alsoCountryCode = beside.cc, alsoAdmin1 = FixCountry.besideRegion(beside.cc, beside.region),
            )
            val border = Registry.resolve(place)
            if (border.sides.isEmpty()) continue
            held++
            val alone = Registry.resolveEntry(p.entry, Place(p.at.lat, p.at.lon, p.zone, own))
            val zone = TimeZone.of(p.zone)
            for (date in dates) {
                val shown = DayPipeline.unended(border, date, zone)
                val its = DayPipeline.unended(alone, date, zone)
                fun asr(day: PrayerDay, dayIn: AsrSchool, school: AsrSchool) = if (dayIn == school) day.asr else day.asrOther
                val late = mapOf(
                    "Fajr" to shown.fajr - its.fajr,
                    "Sunrise" to its.sunrise - shown.sunrise,
                    "Dhuhr" to shown.dhuhr - its.dhuhr,
                    "Asr standard" to asr(shown, border.entry.school, AsrSchool.STANDARD) - asr(its, alone.entry.school, AsrSchool.STANDARD),
                    "Asr Hanafi" to asr(shown, border.entry.school, AsrSchool.HANAFI) - asr(its, alone.entry.school, AsrSchool.HANAFI),
                    "Maghrib" to shown.maghrib - its.maghrib,
                    "Isha" to shown.isha - its.isha,
                    "End of eating" to its.endOfEating - shown.endOfEating,
                )
                for ((event, by) in late) {
                    val minutes = by.inWholeMinutes
                    if (by.isNegative()) early += "${key(p.at, p.entry.id)} $date $event: ${border.entry.id}"
                    if (minutes > worst.getValue(event)) {
                        worst[event] = minutes
                        worstAt[event] = "${p.source} (${p.entry.id}, ${border.entry.id})"
                    }
                }
            }
        }
        println(
            "GPS border cost (R259): $held gate points held to two countries under their own zone; the most later than " +
                "the own entry's day (sunrise and end of eating: earlier), on ${dates.size} dates of 2026: " +
                events.joinToString("; ") { "$it ${worst[it]} min at ${worstAt[it] ?: "-"}" },
        )
        assertTrue(early.isEmpty(), "${early.size} border days start before (or end after) the own entry's day:\n" + early.take(40).joinToString("\n"))
    }

    private companion object {
        /** A reach or an envelope file: points generated around a unit's table (ruling R286). */
        fun generatedSource(source: String) = source.endsWith("-reach.tsv") || source.endsWith("-envelope.tsv")
    }
}
