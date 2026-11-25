package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.gate.Cover
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialFormats
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Ramadan
import world.taqwa.timetables.gate.RamadanCover
import world.taqwa.timetables.gate.RamadanGap
import world.taqwa.timetables.gate.StampSummary
import world.taqwa.timetables.gate.dateRanges
import world.taqwa.timetables.gate.daysWithin
import java.io.File

/**
 * Ruling R185 (the owner's O4, 12 Nov 2026): a timetable its authority publishes month by month
 * counts as covered for the Ramadan release guard (`checkStamps -Pramadan=<year>`, rulings
 * R145–R149) though its Ramadan days are not printed yet, when all three of these hold — each read
 * from committed files, the monitor's index and its fetch records, none taken on trust. The rule
 * does not ask how the authority publishes (a month at a time, or a year at a time later than the
 * build): it asks only for this evidence, and what it accepts is said in the verdict's own words.
 *
 * - **(a) the method is proven on the months held, a Ramadan among them**: the stamp is green,
 *   nothing of the entry is demoted, and at every unit short of the month each of Fajr, the end of
 *   eating and Maghrib ([Ramadan.EVENTS]) was checked on at least [MIN_DAYS] days within the twelve
 *   months before the guarded days begin (days older than that do not count towards them), and on
 *   at least [MIN_RAMADAN_DAYS] days of one earlier Ramadan (its guarded days, the best of the
 *   [EARLIER_RAMADANS] before): a method never seen in a fasting month is not proven for one. And
 *   the entry holds at least [MIN_RAMADAN_DAYS] Ramadan place-days in rows held out of the fit (the
 *   stamp's `heldOut.ramadanDays`; a stamp keeps that tally by entry, never by unit, so this is the
 *   entry's, not each unit's). A unit whose tables print no end of eating has no such proof and
 *   stays named (Pretoria, ruling R148). Where a unit shows an imsak and a table of it prints one,
 *   the imsak is read beside the three ([UnitSummary.ramadanEvents], ruling R246).
 * - **(b) the monitor fetches the authority's own publication on its cadence, and prove has put
 *   every day fetched into the gate**: an automatic source of `sources.tsv` (fetched on its cadence,
 *   never by hand) holds a table of the unit in the monitor's index, read from the authority's own
 *   site (a republisher's mirror, [MonitorTable.mirror], is not the authority being watched), and a
 *   line of `recipes.tsv` turns that capture into the entry's own gate row (member `-`) in a gate
 *   file that already holds the entry. Every day those captures print is in the unit's proof for
 *   each of the three events the unit is checked on at all: a day fetched and not in the gate was
 *   left out by `prove` (it failed, or prove has not run since the fetch), whether it is a day of
 *   this Ramadan or any other. The imsak is read on its own (ruling R246): where the month's imsak
 *   is unchecked, the unit's captures must be seen printing one (on every day of the twelve months
 *   before that they print and the unit's Fajr is checked on, its imsak is checked too), or the
 *   imsak waits for a table read by hand, which no source fetches, and the entry stays named
 *   (Singapore: the year's table prints no imsak, the Imsakiah does). And the fetching is seen to work, not supposed to: the newest fetch
 *   on record that ran each feeding source (`monitor/fetch/<date>.json` of the archive's checkout)
 *   ended `ok`, no longer ago than [freshDays] of its cadence (no missed run), and the captures
 *   print days up to that fetch at least — a fetch that ends well and brings no current day checks
 *   nothing. This is a check on the monitor's cadence, weekly at its most frequent: a day is
 *   compared when the next run fetches it, not on the day the authority publishes it. So the
 *   captures must hold the month, not sample it: of the [SAMPLE_WINDOW] days up to the last a
 *   unit's captures of a source print, they print at least [MIN_RAMADAN_DAYS]. A table that grows
 *   by the one day each run reads (ESA's daily page, a day a week for the 48 cities it alone
 *   prints: the audit of 12 Nov 2026, finding F25) leaves six days in seven never compared, and is
 *   no watch over a fasting month; a table its authority prints every third day (INM's far
 *   delegations) holds all there is.
 * - **(c) the entry is demoted automatically on the first failing day**: `prove` writes the
 *   `@demoted` line itself ([Prove], [world.taqwa.timetables.gate.Demotion]) for any row of a class
 *   A or B entry that is not a member row and reads the authority's own capture — which is what
 *   (b)'s recipe line yields — and a demoted entry is no longer covered here, has no city page, and
 *   blocks the release.
 *
 * Without the archive there is no index, (b) cannot be established, and every entry stays named.
 */
class MonthByMonth(
    private val sources: List<Source>,
    private val recipes: List<Recipe>,
    /** The monitor's index (`archive/tables/monitor/index.tsv`); null where the archive is not here. */
    private val index: List<MonitorTable>?,
    private val manifest: GateManifest,
    /** The days [MonitorTable]'s file prints; null when it cannot be read. */
    private val daysOf: (MonitorTable) -> Set<LocalDate>?,
    /** The id of the unit of the entry (first) that the point (lat, lon, in the zone) lies in; null in none. */
    private val unitAt: (String, Double, Double, String) -> String? = ::registryUnitAt,
    /** The newest fetch on record that ran a source (skipped runs apart); null when there is none. */
    private val lastFetch: (String) -> FetchRun?,
    /** The day the guard runs: a fetch is fresh or old against it. */
    private val today: LocalDate,
) : RamadanCover {

    /** A source's run in a dated fetch record: the day, and how it ended (`ok`, `partial` or `failed`). */
    data class FetchRun(val date: LocalDate, val status: String)

    /** A capture of an automatic source and the recipe line that makes it the entry's own gate row. */
    private data class Feed(val table: MonitorTable, val recipe: Recipe)

    private val printedDays = HashMap<String, Set<LocalDate>?>()

    /** [daysOf], read once a table. */
    private fun printed(table: MonitorTable): Set<LocalDate>? = printedDays.getOrPut(table.id) { daysOf(table) }

    override fun verdict(stamp: StampSummary, ramadan: Ramadan, gaps: List<RamadanGap>): Cover {
        val reasons = mutableListOf<String>()
        val entry = stamp.entryId

        // (a) The method is proven on the months held, a Ramadan among them.
        if (stamp.broken > 0) reasons += "(a) its stamp is red"
        if (stamp.demoted.isNotEmpty()) {
            reasons += "(c) the monitor demoted ${stamp.demoted.joinToString("; ") { it.describe(entry) }}"
        }
        val recentFrom = ramadan.guardFirst.plus(-12, DateTimeUnit.MONTH)
        val recentTo = ramadan.guardFirst.plus(-1, DateTimeUnit.DAY)
        val earlier = (1..EARLIER_RAMADANS).map { Ramadan.of(ramadan.hijriYear - it) }
        val thin = mutableListOf<String>()
        for (gap in gaps) {
            // The events the month is read by at the unit: its imsak too where it shows one a table prints (ruling R246).
            val events = gap.unit.ramadanEvents
            val short = events.mapNotNull { event ->
                val days = gap.unit.checkedByEvent[event].orEmpty().daysWithin(recentFrom, recentTo)
                if (days < MIN_DAYS) "$event on $days days" else null
            }.toMutableList()
            // One earlier Ramadan with each event on enough of its days: the best of them, by its thinnest event.
            val (year, most) = earlier.map { r ->
                r.hijriYear to events.minOf { gap.unit.checkedByEvent[it].orEmpty().daysWithin(r.guardFirst, r.guardLast) }
            }.maxBy { it.second }
            if (most < MIN_RAMADAN_DAYS) {
                short += if (most == 0) "no earlier Ramadan checked" else "an earlier Ramadan on $most of its days at most (Ramadan $year)"
            }
            if (short.isNotEmpty()) thin += "${gap.unit.id} (${short.joinToString(", ")})"
        }
        if (thin.isNotEmpty()) {
            reasons += "(a) not proven on a month's days ($MIN_DAYS of each of Fajr, the end of eating and Maghrib in $recentFrom..$recentTo, and " +
                "$MIN_RAMADAN_DAYS of each in one of the $EARLIER_RAMADANS Ramadans before) at ${thin.size} of its units: ${listed(thin)}"
        }
        if (stamp.heldOutRamadanDays < MIN_RAMADAN_DAYS) {
            reasons += "(a) its method was never tested on a Ramadan it was not fitted to: ${stamp.heldOutRamadanDays} Ramadan place-days in rows held " +
                "out of the fit, fewer than $MIN_RAMADAN_DAYS (the stamp's heldOut.ramadanDays, the entry's as a whole)"
        }

        // (b) The monitor fetches the authority's own publication, and prove has put every day fetched into the gate.
        val feeds = mutableMapOf<String, List<Feed>>()
        // The last day each feeding source's captures print, at the unit where they stop soonest.
        val printedThrough = mutableMapOf<String, LocalDate>()
        if (index == null) {
            reasons += "(b) the monitor's index is not here, so what the monitor fetches for it cannot be read " +
                "(run with -Pofficial=<the archive's checkout>, or TAQWA_OFFICIAL)"
        } else {
            val unfed = mutableListOf<String>()
            val published = mutableListOf<String>()
            val waiting = mutableListOf<String>()
            val byHand = mutableListOf<String>()
            val sampled = mutableListOf<String>()
            for (gap in gaps) {
                val all = feedsOf(entry, gap.unit.id.takeIf { stamp.units.isNotEmpty() })
                val own = all.filter { !it.table.mirror }
                if (own.isEmpty()) {
                    unfed += gap.unit.id + if (all.isEmpty()) "" else " (${all.joinToString(", ") { it.table.id }} only, a republisher's mirror)"
                } else {
                    feeds[gap.unit.id] = own
                }
                // The guarded days one of the three is unchecked on (ruling R246): a day the gate holds on all three is not
                // left out by prove for an imsak the capture does not print; the imsak's own days are read in its block below.
                val unchecked = Ramadan.EVENTS.flatMap { gap.uncheckedByEvent[it].orEmpty() }.toSortedSet()
                // The events the unit is checked on at all: one it never is, (a) has named already. The three alone
                // (ruling R246): an imsak is printed by a Ramadan timetable and by few of a year's tables, so a day a
                // feed prints without one waits for nothing; the imsak is read on its own below.
                val proven = Ramadan.EVENTS.map { gap.unit.checkedByEvent[it].orEmpty() }.filter { it.isNotEmpty() }
                for (feed in all) {
                    val days = printed(feed.table)
                    if (days == null) {
                        published += "${feed.table.id} could not be read"
                        continue
                    }
                    // A mirror's days count here too: what a republisher prints, the authority has published.
                    val held = unchecked.filter { it in days }
                    if (held.isNotEmpty()) {
                        published += "${feed.table.id} already prints ${held.size} of ${gap.unit.id}'s unchecked days (${dateRanges(held).joinToString(", ")})"
                    }
                    if (feed.table.mirror) continue
                    val left = days.filter { day -> (day < ramadan.guardFirst || day > ramadan.guardLast) && proven.any { runs -> runs.none { day in it } } }.sorted()
                    if (left.isNotEmpty()) {
                        waiting += "${left.size} of the days ${feed.table.id} prints (${dateRanges(left).joinToString(", ")}) at ${gap.unit.id}"
                    }
                }
                // The imsak (ruling R246). O4 covers a month the monitor will fetch, and an imsak that only a table read by
                // hand prints is fetched by no source. Read by the days alone, since a stamp does not say which table a day's
                // imsak came from: on a day the unit's own captures print and its Fajr is checked, its imsak is checked too,
                // or the captures are not seen printing one.
                if (Event.IMSAK.key in gap.uncheckedByEvent.filterValues { it.isNotEmpty() } && own.isNotEmpty()) {
                    val fed = own.flatMap { printed(it.table).orEmpty() }.filter { it in recentFrom..recentTo }.toSet()
                    val fajr = fed.filter { day -> gap.unit.checkedByEvent[Event.FAJR.key].orEmpty().any { day in it } }
                    val imsak = fajr.count { day -> gap.unit.checkedByEvent[Event.IMSAK.key].orEmpty().any { day in it } }
                    if (imsak < fajr.size) {
                        byHand += "${gap.unit.id} (of the ${fajr.size} days of $recentFrom..$recentTo that " +
                            "${own.map { it.table.id }.distinct().sorted().joinToString(", ")} print${if (own.size == 1) "s" else ""} " +
                            "and its Fajr is checked on, the imsak is checked on $imsak)"
                    }
                }
                // How far the unit's captures of each source print (the newest of them); the unit that stops soonest is the source's.
                for ((source, tables) in own.groupBy { it.table.source }) {
                    val days = tables.flatMap { printed(it.table).orEmpty() }.toSet()
                    val last = days.maxOrNull() ?: continue
                    printedThrough.merge(source, last) { x, y -> minOf(x, y) }
                    // The captures hold the month or sample it: the days they print of the window up to their last.
                    val from = last.plus(-(SAMPLE_WINDOW - 1), DateTimeUnit.DAY)
                    val held = days.count { it >= from }
                    if (held < MIN_RAMADAN_DAYS) sampled += "$source prints $held of the $SAMPLE_WINDOW days up to $last at ${gap.unit.id}"
                }
            }
            if (unfed.isNotEmpty()) {
                reasons += "(b) no automatic source of sources.tsv holds a table of the authority's own site that the recipes turn into this entry's own rows at " +
                    "${unfed.size} of its units: ${listed(unfed)}"
            }
            if (published.isNotEmpty()) {
                reasons += "(b) days its authority has already published are not in the gate (prove left their row out, or has not run since): " +
                    listed(published)
            }
            if (waiting.isNotEmpty()) {
                reasons += "(b) prove has not put every day the monitor fetched into the gate (it left their row out, or has not run since the fetch): " +
                    listed(waiting)
            }
            if (byHand.isNotEmpty()) {
                reasons += "(b) the tables the monitor fetches are not seen printing its imsak, so the imsak of $ramadan waits for a table read by " +
                    "hand at ${byHand.size} of its units: ${listed(byHand)}"
            }
            if (sampled.isNotEmpty()) {
                reasons += "(b) the monitor's captures sample its days and do not hold them (fewer than $MIN_RAMADAN_DAYS of the $SAMPLE_WINDOW days up to " +
                    "the last they print: the days between are never compared, so a fasting month would not be watched): ${listed(sampled)}"
            }
            val unfetched = feeds.values.flatten().map { it.table.source }.distinct().sorted().mapNotNull { id ->
                val run = lastFetch(id)
                val fresh = freshDays(sources.first { it.id == id }.cadence)
                val through = printedThrough[id]
                when {
                    run == null -> "$id has no fetch on record"
                    run.status != "ok" -> "$id's last fetch (${run.date}) ended ${run.status}"
                    run.date.plus(fresh, DateTimeUnit.DAY) < today -> "$id was last fetched on ${run.date}, over $fresh days before $today"
                    through != null && through < run.date -> "$id's fetch of ${run.date} brought no current day: its captures print through $through"
                    else -> null
                }
            }
            if (unfetched.isNotEmpty()) {
                reasons += "(b) the monitor is not seen fetching it (a fetch that ended ok, recent for its cadence, in monitor/fetch): " +
                    listed(unfetched)
            }
        }
        if (reasons.isNotEmpty()) return Cover.Named(reasons)

        val bySource = feeds.values.flatten().map { it.table.source }.distinct().sorted()
        val cadences = bySource.joinToString(", ") { id ->
            "$id ${sources.first { it.id == id }.cadence}, last ok ${lastFetch(id)?.date}, its captures printing through ${printedThrough[id]}"
        }
        return Cover.Covered(
            "${gaps.size} unit${if (gaps.size == 1) "" else "s"} short of $ramadan (${listed(gaps.map { "${it.unit.id} ${it.describe()}" })}): " +
                "(a) each proven on at least $MIN_DAYS days of Fajr, the end of eating and Maghrib in $recentFrom..$recentTo and on at least " +
                "$MIN_RAMADAN_DAYS of each in an earlier Ramadan, the entry on ${stamp.heldOutRamadanDays} Ramadan place-days held out of the fit; " +
                "(b) fetched on the monitor's cadence, not on the day of publication ($cadences; a missed run leaves its days unchecked until " +
                "the next), every day fetched in the gate, none of the unchecked days published yet; " +
                "(c) demoted by prove on the first failing day of the authority's own capture",
        )
    }

    /**
     * The captures that feed [entry] at [unit] (null: an entry the gate checks whole): tables of the
     * index from an automatic source of the entry, at that unit, with a recipe line that makes each
     * the entry's own row (member `-`) in a gate file that holds the entry.
     */
    private fun feedsOf(entry: String, unit: String?): List<Feed> {
        val automatic = sources.filter { !it.manual && entry in entriesOf(it) }.map { it.id }.toSet()
        return index.orEmpty().filter { it.source in automatic }.mapNotNull { table ->
            val recipe = recipes.firstOrNull { r ->
                r.source == table.source && r.member == "-" && Recipes.applies(r, table.key, recipes) &&
                    manifest.rows.any { it.source == r.gate && it.entry == entry && it.member == null } &&
                    target(r, table)?.let { (id, at) -> id == entry && (unit == null || at == unit) } == true
            }
            recipe?.let { Feed(table, it) }
        }
    }

    /** The entry and unit a recipe line's row checks for a capture; null where it names neither (`same`). */
    private fun target(recipe: Recipe, table: MonitorTable): Pair<String, String?>? {
        val named = when (recipe.entry) {
            "same" -> return null
            "index" -> table.entry ?: return null
            else -> recipe.entry
        }
        val id = named.substringBefore('/')
        named.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let { return id to it }
        val point = when (recipe.point) {
            "index" -> table.lat?.let { lat -> table.lon?.let { lon -> lat to lon } }
            "unit", "all", "same" -> null
            else -> recipe.point.split(',').let { it[0].toDouble() to it[1].toDouble() }
        }
        return id to point?.let { (lat, lon) -> unitAt(id, lat, lon, table.zone) }
    }

    companion object {
        /** A month's days: the fewest of each event a unit must hold, within the twelve months before the guarded days, for its method to count as proven. */
        const val MIN_DAYS = 28

        /**
         * A third of a fasting month: the fewest days of each event a unit must hold in one earlier
         * Ramadan, and the fewest Ramadan place-days the entry must hold in rows held out of the fit.
         * A table read every third day through the month reaches it (INM's far delegations); a stray
         * day or two of a February does not (Amman's two).
         */
        const val MIN_RAMADAN_DAYS = 10

        /**
         * The days up to the last a unit's captures print, of which they must print [MIN_RAMADAN_DAYS] to count as
         * holding the authority's month ((b)): a month, as the fasting month the rule stands in for.
         */
        const val SAMPLE_WINDOW = 30

        /** How many Ramadans back an earlier month's proof may lie. */
        const val EARLIER_RAMADANS = 5

        /** The registry entries a source of `sources.tsv` proves: its `entries` cell before any note in brackets. */
        fun entriesOf(source: Source): Set<String> =
            source.entries.substringBefore('(').split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        /**
         * How many days old the newest good fetch of a source may be: one run and a day of a cadence the
         * monitor fetches every week (`weekly`, and `hijri-month`, which it asks weekly for the month to
         * come), a month and a day of one it fetches once a month. No missed run is let pass: one missed
         * weekly run is seven days nothing compared.
         */
        fun freshDays(cadence: String): Int = if (cadence == "weekly" || cadence == "hijri-month") 8 else 32

        private val DATED = Regex("\\d{4}-\\d{2}-\\d{2}\\.json")

        /**
         * The dated fetch records of [official] (`monitor/fetch/<yyyy-mm-dd>.json`), newest first: beside
         * `archive/` in the archive's own checkout, or beside what the app checkout's `archive` link
         * points at. A record that cannot be read is no record.
         */
        fun fetchLogs(official: File?): List<FetchLog> {
            if (official == null) return emptyList()
            val dirs = listOf(official.resolve("monitor/fetch")) +
                listOfNotNull(runCatching { official.resolve("archive").canonicalFile.parentFile?.resolve("monitor/fetch") }.getOrNull())
            val dir = dirs.firstOrNull { it.isDirectory } ?: return emptyList()
            return dir.listFiles().orEmpty().filter { DATED.matches(it.name) }
                .mapNotNull { runCatching { FetchLog.load(it) }.getOrNull() }
                .sortedByDescending { it.date }
        }

        /** The newest of [logs] (newest first) that ran [source], a run it skipped being no run. */
        fun lastRun(logs: List<FetchLog>, source: String): FetchRun? = logs.firstNotNullOfOrNull { log ->
            log.sources.firstOrNull { it.source == source && it.status != "skipped" }?.let { FetchRun(log.date, it.status) }
        }

        /** The rule over this checkout's committed sources, recipes and gate files, and [official]'s index, tables and fetch records. */
        fun load(repo: File, official: File?, manifest: GateManifest, today: LocalDate): MonthByMonth {
            val checkout = repo.resolve("tools/timetables/official")
            val indexFile = official?.resolve("archive/tables/monitor/index.tsv")
            val roots = official?.let { OfficialRoots(it, checkout) }
            val logs = fetchLogs(official)
            return MonthByMonth(
                Source.load(checkout.resolve("monitor/sources.tsv")),
                Recipes.load(checkout.resolve("monitor/recipes.tsv")),
                indexFile?.takeIf { it.isFile }?.let(MonitorIndex::load),
                manifest,
                daysOf = { table ->
                    val file = roots?.file(table.path)
                    if (file == null || !file.isFile) {
                        null
                    } else {
                        runCatching {
                            val columns = table.columns.split(' ').count { it.isNotEmpty() }
                            OfficialFormats.named(table.format).read(file.readLines(), columns).days.map { it.date }.toSet()
                        }.getOrNull()
                    }
                },
                lastFetch = { source -> lastRun(logs, source) },
                today = today,
            )
        }

        /** The unit of the registry's entry [id] the point resolves to, as the gate resolves a row there ([world.taqwa.timetables.gate.Gate.setUp]). */
        fun registryUnitAt(id: String, lat: Double, lon: Double, zone: String): String? {
            val entry = Registry.byId(id) ?: return null
            val resolution = Registry.resolveEntry(entry, Place(lat, lon, zone, entry.countries.firstOrNull() ?: ""))
            return Units.of(id)?.units?.firstOrNull { it.name == resolution.unitName && it.point == resolution.unitPoint }?.id
        }

        private fun listed(items: List<String>, most: Int = 6): String =
            items.take(most).joinToString("; ") + if (items.size > most) "; and ${items.size - most} more" else ""
    }
}
