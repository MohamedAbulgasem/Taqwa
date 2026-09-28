package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.gate.EntryStats
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateError
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Survey
import world.taqwa.timetables.gate.Surveys
import world.taqwa.timetables.gate.dateRanges
import world.taqwa.timetables.gate.formats.DailyFormat
import java.io.File
import kotlin.math.abs
import kotlin.time.Clock

/**
 * One fetched table through the gate's machinery at its own point (brief P item 4, ruling R93).
 * An authority's table is checked as one or two gate rows:
 *
 * - where Automatic resolves the table's own entry at the point, one row of that entry;
 * - where Automatic resolves a cautious entry that lists the table's entry as a member, the member
 *   row (ruling R38: each start against the member's printed time, Maghrib under the cap) and a
 *   second row of the member's own entry at the point, so a drifting member model is still caught;
 * - where the table names a unit (`entry/unit`), the unit's own row at its reference point, and,
 *   where Automatic follows a cautious entry there that lists the unit's entry, the member row too
 *   (review I3: the gate files hold Diyanet's Amsterdam table under nl.cautious the same way);
 * - where Automatic resolves something else (Diyanet's London table inside the M25), the table's
 *   own entry alone, since no other entry claims to reproduce it.
 *
 * A mosque calendar goes through its survey's machinery ([Survey]) at the mosque's point, against
 * the survey's entry, with the faults and outliers its folder records for that calendar.
 *
 * Each finding line is classified on its own (ruling R93): an early start, a late end or a day out
 * of order is never early (attention, tier 1, every run); lateness over the limit on the entry's
 * own row is attention (low) the first time and whenever it worsens, then carried as still open; on
 * a member row it is for the record (the spread between members, not an engine error); capped
 * Maghribs left unchecked are for the record, naming the missing member. A table nothing is wrong
 * with is new, changed, green again, or checked again and fine.
 */
class TableCheck(
    private val roots: OfficialRoots,
    private val officialDir: File,
    private val lookup: (String) -> RegistryEntry? = Registry::byId,
) {

    /** One gate row the table is checked as; [own] is the table's own entry (not a cautious entry's member row). */
    data class PlannedRow(val entry: String, val lat: Double?, val lon: Double?, val member: String?, val why: String) {
        val own: Boolean get() = member == null
        val entryId: String get() = entry.substringBefore('/')
    }

    /** How a table is checked: as gate rows, or through a survey. */
    sealed class Plan {
        data class Rows(val rows: List<PlannedRow>) : Plan()
        data class Calendar(val survey: Survey, val why: String, val held: Survey.Calendar?, val heldFolder: String?) : Plan()
        data class Refused(val reason: String) : Plan()

        /** A calendar its survey leaves out whole (`use` is the reason): not checked, for the record. */
        data class LeftOut(val reason: String) : Plan()
    }

    /** What the monitor remembers of the table ([MonitorState]), as the check needs it. */
    data class Status(val isNew: Boolean, val contentChanged: Boolean, val wasRed: Boolean, val lateness: Lateness?) {
        companion object {
            val NEW = Status(isNew = true, contentChanged = false, wasRed = false, lateness = null)
        }
    }

    /** The items for one table, whether it stays red (checked again next run whatever changed), and its open lateness. */
    class Outcome(val items: List<Item>, val red: Boolean, val lateness: Lateness?) {
        val item: Item get() = items.first()
    }

    fun check(table: MonitorTable, status: Status = Status.NEW, today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Outcome =
        try {
            when (val plan = plan(table)) {
                is Plan.Refused -> broken(table, listOf(plan.reason))
                is Plan.LeftOut -> Outcome(listOf(Item(Kind.GREEN, "${table.id} (${table.name}): its survey leaves it out, not checked (${plan.reason})")), red = false, lateness = null)
                is Plan.Rows -> checkRows(table, plan, status, today)
                is Plan.Calendar -> checkCalendar(table, plan, status)
            }
        } catch (e: Exception) {
            // One bad table (a mistyped zone, a column list that does not fit) never sinks the run (review M1).
            broken(table, listOf("${e::class.simpleName}: ${e.message}"))
        }

    private fun broken(table: MonitorTable, reasons: List<String>): Outcome =
        Outcome(listOf(Item(Kind.FETCH_BROKEN, "${table.id} could not be checked", reasons, CHECK_FIX, key = "table:${table.id}")), red = true, lateness = null)

    fun plan(table: MonitorTable): Plan {
        if (table.survey != null) return calendarPlan(table)
        val hint = table.entry ?: return Plan.Refused("no entry named in the index (a mosque calendar names its survey instead)")
        val hintId = hint.substringBefore('/')
        val hintEntry = lookup(hintId) ?: return Plan.Refused("no registry entry '$hintId'")
        val unitId = hint.substringAfter('/', "").takeIf { it.isNotEmpty() }
        if (unitId != null) {
            val unit = Units.of(hintId)?.units?.firstOrNull { it.id == unitId } ?: return Plan.Refused("$hintId has no unit '$unitId'")
            val own = PlannedRow(hint, null, null, null, "checked at its unit's own point")
            val automatic = Registry.automaticEntry(Place(unit.point.lat, unit.point.lon, table.zone, table.cc))
            return Plan.Rows(listOfNotNull(memberRow(automatic, hintEntry, unit.point.lat, unit.point.lon, table.name), own))
        }
        val lat = table.lat
        val lon = table.lon
        if (lat == null || lon == null) {
            return if (hintEntry.method?.fixedPoint != null) {
                Plan.Rows(listOf(PlannedRow(hint, null, null, null, "checked at the method's fixed point")))
            } else {
                Plan.Refused("no point: give lat and lon, or an entry/unit")
            }
        }
        val automatic = Registry.automaticEntry(Place(lat, lon, table.zone, table.cc))
        val member = memberRow(automatic, hintEntry, lat, lon, table.name)
        return Plan.Rows(
            when {
                automatic.id == hintEntry.id -> listOf(PlannedRow(hint, lat, lon, null, "Automatic follows ${automatic.id} at ${table.name}"))
                member != null -> listOf(
                    member,
                    PlannedRow(hint, lat, lon, null, "the member's own entry ${hintEntry.id} at ${table.name}, so a drifting member model is still caught"),
                )
                else -> listOf(
                    PlannedRow(hint, lat, lon, null, "Automatic follows ${automatic.id} at ${table.name}, not this table's ${hintEntry.id}: the table's own entry is checked"),
                )
            },
        )
    }

    /** The member row where [automatic] is a cautious entry listing [hintEntry] as a member, else null. */
    private fun memberRow(automatic: RegistryEntry, hintEntry: RegistryEntry, lat: Double, lon: Double, name: String): PlannedRow? =
        if (automatic.members.any { it.id == hintEntry.id }) {
            PlannedRow(automatic.id, lat, lon, hintEntry.id, "Automatic follows the cautious ${automatic.id} at $name, whose member ${hintEntry.id} this table is")
        } else {
            null
        }

    private fun calendarPlan(table: MonitorTable): Plan {
        val folder = Surveys.named(table.survey!!) ?: return Plan.Refused("no survey folder '${table.survey}' (${Surveys.all.joinToString { it.folder }})")
        val whole = try {
            folder.load(officialDir)
        } catch (e: IllegalArgumentException) {
            return Plan.Refused("survey ${folder.folder}: ${e.message}")
        }
        val lat = table.lat ?: return Plan.Refused("a mosque calendar needs its mosque's lat and lon")
        val lon = table.lon ?: return Plan.Refused("a mosque calendar needs its mosque's lat and lon")
        val columns = table.columns.split(' ').filter { it.isNotEmpty() }
        val calendar = Survey.Calendar(
            id = table.key, path = table.path, name = table.name, lat = lat, lon = lon, columns = columns, use = "yes",
            zone = TimeZone.of(table.zone).takeIf { it != whole.zone },
        )
        val held = whole.calendars.firstOrNull { it.id == table.key }
        if (held != null && !held.used) return Plan.LeftOut(held.use)
        val why = if (held == null) {
            "a calendar the survey does not list yet: checked at the mosque's point with no recorded faults or outliers"
        } else {
            "checked as the survey checks it, with its recorded faults and outliers"
        }
        val survey = Survey(
            whole.entry, whole.zone, whole.countryCode, listOf(calendar),
            whole.faults.filter { it.calendar == table.key },
            whole.outliers.filter { it.calendar == "*" || it.calendar == table.key },
        )
        return Plan.Calendar(survey, why, held, whole.calendars.firstOrNull()?.path?.substringBeforeLast('/'))
    }

    private fun checkRows(table: MonitorTable, plan: Plan.Rows, status: Status, today: LocalDate): Outcome {
        val manifest = GateManifest.parse(
            "monitor:${table.id}",
            rowHeader() + "\n" + plan.rows.joinToString("\n") { rowText(table, it, table.path) },
        )
        val result = try {
            Gate(roots, lookup = lookup).evaluate(manifest)
        } catch (e: GateError) {
            return broken(table, e.problems)
        }
        val figures = mutableListOf<String>()
        val neverEarly = mutableListOf<String>()
        val ownLate = mutableListOf<String>()
        val memberLate = mutableListOf<String>()
        val unchecked = mutableListOf<String>()
        val failingDates = sortedSetOf<LocalDate>()
        var outOfOrder = 0
        var worstEarly = 0
        var lateCells = 0
        var lateWorst = 0
        for (row in plan.rows) {
            val s = result.entries[row.entryId] ?: return broken(table, listOf("the gate read no day from it for ${row.entry}"))
            figures += figures(s, row)
            neverEarly += result.neverEarly(s)
            for (e in s.events.values) {
                failingDates += e.brokenDates
                worstEarly = maxOf(worstEarly, e.worstEarly)
            }
            outOfOrder += s.outOfOrder
            val late = result.overLimit(s)
            if (row.own) {
                ownLate += late
                lateCells += s.overLimit
                lateWorst = maxOf(lateWorst, s.events.values.filter { it.over > 0 }.maxOfOrNull { it.worst } ?: 0)
            } else {
                memberLate += late
            }
            unchecked += result.unchecked(s).map {
                "$it\n    the most-followed member ${s.maghribUncheckedMember ?: "?"}'s table is not held at ${table.name} (a coverage gap, not a failure)"
            }
        }
        val against = plan.rows.joinToString(" and ") { it.entry + (it.member?.let { m -> " (member $m)" } ?: "") }
        val head = "${table.id} (${table.name}) against $against"
        val items = mutableListOf<Item>()
        if (neverEarly.isNotEmpty()) {
            val days = failingDates.size + outOfOrder
            items += Item(
                Kind.NEVER_EARLY, head, figures + neverEarly.map { it.replace("\n", "; ") } + gateRows(table, plan), EARLY_LATE_FIX,
                key = "table:${table.id}", days = days, worst = worstEarly, group = 0,
                label = "${table.id} (${dateRanges(failingDates).take(3).joinToString(", ")}${if (dateRanges(failingDates).size > 3) ", …" else ""})",
            )
        }
        var lateness: Lateness? = null
        if (ownLate.isNotEmpty()) {
            val was = status.lateness
            val worse = was == null || lateCells > was.days || lateWorst > was.worst
            lateness = Lateness(was?.since ?: today, lateCells, lateWorst)
            val title = "$head: $lateCells cell${if (lateCells == 1) "" else "s"} over the late limit, worst $lateWorst min"
            items += if (worse) {
                Item(Kind.OWN_LATE, title, figures + ownLate.map { it.replace("\n", "; ") } + gateRows(table, plan), LATE_FIX, key = "table:${table.id}:late", days = lateCells, worst = lateWorst)
            } else {
                Item(Kind.STILL_OPEN, title, ownLate.map { it.replace("\n", "; ") }, key = "table:${table.id}:late", since = was!!.since)
            }
        }
        if (memberLate.isNotEmpty()) {
            items += Item(
                Kind.FOR_THE_RECORD, "$head: lateness on the member row",
                memberLate.map { it.replace("\n", "; ") } +
                    "a cautious start waits for the latest member and a cautious end comes with the earliest: this is the spread between members, not an engine error; the member's own entry is checked beside it",
                key = "table:${table.id}:member",
            )
        }
        if (unchecked.isNotEmpty()) {
            items += Item(Kind.FOR_THE_RECORD, "$head: capped Maghrib unchecked", unchecked.map { it.replace("\n", "; ") }, key = "table:${table.id}:unchecked")
        }
        if (neverEarly.isEmpty() && ownLate.isEmpty()) {
            items += when {
                status.isNew -> Item(Kind.NEW_TABLE_FINE, head, figures + gateRows(table, plan))
                status.contentChanged -> Item(Kind.CHANGED_FINE, head, figures)
                status.wasRed -> Item(Kind.GREEN_AGAIN, head, figures)
                else -> Item(Kind.CHECKED_FINE, table.id, figures)
            }
        }
        return Outcome(items, red = neverEarly.isNotEmpty() || ownLate.isNotEmpty(), lateness = lateness)
    }

    private fun checkCalendar(table: MonitorTable, plan: Plan.Calendar, status: Status): Outcome {
        val result = try {
            plan.survey.evaluate(roots)
        } catch (e: IllegalArgumentException) {
            return broken(table, listOf(e.message ?: "unreadable"))
        }
        val broken = result.broken()
        val figures = "${result.placeDays} place-days at the mosque's point against ${plan.survey.entry.id} (${plan.why}); " +
            result.checked.entries.joinToString(", ") { (e, n) -> "${e.key} $n" } +
            (if (result.leftOut > 0) "; ${result.leftOut} cells left out as faulty" else "")
        val head = "${table.id} (${table.name}) against ${plan.survey.entry.id}"
        if (broken.isNotEmpty()) {
            val dates = result.unrecorded.mapTo(sortedSetOf()) { it.date }
            val days = dates.size + result.covered.filter { !it.within }.sumOf { it.days }
            val worst = maxOf(result.unrecorded.maxOfOrNull { it.minutes } ?: 0, result.covered.filter { !it.within }.maxOfOrNull { it.worst } ?: 0)
            val details = listOf(figures) + broken.take(MAX_LISTED) +
                (if (broken.size > MAX_LISTED) listOf("… ${broken.size - MAX_LISTED} more") else emptyList()) +
                suspectedFaults(table, plan, result.unrecorded) + reDated(table, plan)
            val ranges = dateRanges(dates)
            return Outcome(
                listOf(
                    Item(
                        Kind.NEVER_EARLY, head, details, CALENDAR_FIX, key = "table:${table.id}", days = days, worst = worst, group = 0,
                        label = "${table.id} (${ranges.take(3).joinToString(", ")}${if (ranges.size > 3) ", …" else ""})",
                    ),
                ),
                red = true, lateness = null,
            )
        }
        val item = when {
            status.isNew -> Item(Kind.NEW_TABLE_FINE, head, listOf(figures, calendarRow(table, plan)))
            status.contentChanged -> Item(Kind.CHANGED_FINE, head, listOf(figures))
            status.wasRed -> Item(Kind.GREEN_AGAIN, head, listOf(figures))
            else -> Item(Kind.CHECKED_FINE, table.id, listOf(figures))
        }
        return Outcome(listOf(item), red = false, lateness = null)
    }

    /** A calendar's cells by date, each column as minutes, as [DailyFormat] reads them. */
    private fun cells(file: File, columns: Int): Map<LocalDate, List<Int?>> =
        if (!file.isFile) emptyMap() else DailyFormat.read(file.readLines(), columns).days.associate { it.date to it.minutes }

    /**
     * The survey's own rule for printed cells that are the calendar's error, not ours (review Q2):
     * a run of days whose cells in one column stand 20 min or more off the days just before and
     * after the run, or cells exactly an hour or twelve hours off the survey's held capture of the
     * same calendar. Each such run gets a line with the evidence and a `faults.tsv` row ready to
     * paste; the finding stays attention, the owner decides.
     */
    private fun suspectedFaults(table: MonitorTable, plan: Plan.Calendar, unrecorded: List<Survey.Finding>): List<String> {
        val columns = table.columns.split(' ').filter { it.isNotEmpty() }
        val own = cells(roots.file(table.path), columns.size)
        val held = plan.held
        val heldCells = if (held == null) emptyMap() else cells(roots.file(held.path), held.columns.size)
        val lines = mutableListOf<String>()
        for ((column, findings) in unrecorded.groupBy { COLUMN_OF[it.kind] }.toSortedMap(compareBy { it ?: "" })) {
            if (column == null) continue
            val i = columns.indexOf(column)
            if (i < 0) continue
            val hi = held?.columns?.indexOf(column) ?: -1
            for (range in dateRanges(findings.map { it.date })) {
                val from = LocalDate.parse(range.substringBefore(".."))
                val to = LocalDate.parse(range.substringAfter(".."))
                val n = from.daysUntil(to) + 1
                val evidence = mutableListOf<String>()
                val atFrom = own[from]?.getOrNull(i)
                val atTo = own[to]?.getOrNull(i)
                val before = own[from.plus(-1, DateTimeUnit.DAY)]?.getOrNull(i)
                val after = own[to.plus(1, DateTimeUnit.DAY)]?.getOrNull(i)
                if (atFrom != null && atTo != null && before != null && after != null &&
                    abs(atFrom - before) >= NEIGHBOUR_MIN && abs(atTo - after) >= NEIGHBOUR_MIN
                ) {
                    evidence += "${signed(atFrom - before)} min against the day before the run and ${signed(atTo - after)} against the day after it"
                }
                if (hi >= 0) {
                    val slips = (0 until n).mapNotNull { k ->
                        val d = from.plus(k, DateTimeUnit.DAY)
                        val v = own[d]?.getOrNull(i) ?: return@mapNotNull null
                        val w = heldCells[d]?.getOrNull(hi) ?: return@mapNotNull null
                        (v - w).takeIf { abs(it) in CLOCK_SLIPS }
                    }
                    if (slips.isNotEmpty()) {
                        evidence += slips.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                            .joinToString(", ") { (slip, count) -> "${signed(slip)} min against the survey's held capture on $count of $n days" }
                    }
                }
                if (evidence.isEmpty()) continue
                val what = evidence.joinToString("; ")
                lines += "suspected calendar fault, not ours: column $column on $range ($n day${if (n == 1) "" else "s"}): $what"
                lines += "faults.tsv row to paste: `${table.key}\t$from\t$to\t$column\tsuspected from the monitor's capture of ${table.fetched}: " +
                    "${COLUMN_NAMES.getValue(column)} $what`"
            }
        }
        return lines
    }

    /**
     * A Mawaqit page carries no year: a calendar the mosque did not update is the held capture
     * re-dated (review M6), and its clock-change days then fall on the old year's dates. Said when
     * the fetched year differs from the held capture's and the cells agree by month and day.
     */
    private fun reDated(table: MonitorTable, plan: Plan.Calendar): List<String> {
        val held = plan.held ?: return emptyList()
        val columns = table.columns.split(' ').filter { it.isNotEmpty() }
        if (columns != held.columns) return emptyList()
        val own = cells(roots.file(table.path), columns.size)
        val was = cells(roots.file(held.path), held.columns.size)
        if (own.isEmpty() || was.isEmpty()) return emptyList()
        val ownYear = own.keys.first().year
        val wasYear = was.keys.first().year
        if (ownYear == wasYear) return emptyList()
        val byMonthDay = was.entries.associate { (d, m) -> (d.monthNumber to d.dayOfMonth) to m }
        val common = own.entries.filter { (d, _) -> (d.monthNumber to d.dayOfMonth) in byMonthDay }
        val same = common.count { (d, m) -> byMonthDay[d.monthNumber to d.dayOfMonth] == m }
        if (common.isEmpty() || same * 100 < common.size * RE_DATED_PERCENT) return emptyList()
        return listOf(
            "the mosque has not published a $ownYear calendar: the cells are the survey's $wasYear capture re-dated ($same of ${common.size} days " +
                "identical by month and day), so its clock-change days fall on $wasYear's dates; refresh the held copy when the mosque publishes",
        )
    }

    private fun signed(minutes: Int): String = if (minutes > 0) "+$minutes" else minutes.toString()

    /** "N place-days first..last" and, where the span has holes, "N of M days; missing …" (review M15). */
    private fun figures(s: EntryStats, row: PlannedRow): String {
        val dates = s.dates
        val first = s.first
        val last = s.last
        val span = if (first != null && last != null) first.daysUntil(last) + 1 else 0
        val coverage = if (first == null || last == null || span == dates.size) {
            "${dates.size} place-days $first..$last"
        } else {
            val missing = (0 until span).map { first.plus(it, DateTimeUnit.DAY) }.filter { it !in dates }
            val ranges = dateRanges(missing)
            "${dates.size} of $span days $first..$last; missing ${ranges.take(10).joinToString(", ")}${if (ranges.size > 10) ", …" else ""}"
        }
        val worst = s.events.entries.joinToString(", ") { (event, e) ->
            "${event.key} ${e.checked} days" + (if (event.isStart) ", ${e.early} early" else ", ${e.lateEnd} late ends") +
                ", worst ${e.worst} of limit ${s.effectiveLimit(event)}" + (if (e.over > 0) ", ${e.over} over" else "")
        }
        val rowName = if (row.own) row.entry else "${row.entry} member ${row.member}"
        return "$rowName: $coverage (class ${s.classes.joinToString("/")}): $worst"
    }

    private fun rowHeader(): String = (GateManifest.HEADER + GateManifest.OPTIONAL).joinToString("\t")

    /** A gate row in the header order of [rowHeader], `member` and `clock` last, naming [path]. */
    private fun rowText(table: MonitorTable, row: PlannedRow, path: String): String = listOf(
        path, row.entry, row.lat?.toString().orEmpty(), row.lon?.toString().orEmpty(), table.zone, table.columns, table.format,
        table.school, "test", "${table.name}, ${table.source} fetched ${table.fetched}", row.member.orEmpty(), table.clock.orEmpty(),
    ).joinToString("\t")

    /** The table's dated copy a gate row may name (the monitor rewrites the live file, review M13). */
    private fun datedPath(table: MonitorTable): String = "archive/tables/held/${table.source}/${table.key}-${table.fetched}.txt"

    /** The rows ready to paste, after copying the live table to a dated path the monitor never rewrites. */
    private fun gateRows(table: MonitorTable, plan: Plan.Rows): List<String> =
        listOf("to hold it, first copy `${table.path}` to `${datedPath(table)}` (the monitor rewrites the live file), then paste:") +
            plan.rows.map { "gate row (${it.why}): `${rowText(table, it, datedPath(table))}`" }

    private fun calendarRow(table: MonitorTable, plan: Plan.Calendar): String {
        val folder = plan.heldFolder ?: "archive/tables/${table.cc.lowercase()}-mawaqit"
        val path = "$folder/${table.key}.txt"
        return "to hold it, copy `${table.path}` to `$path`, then paste the calendars.tsv row: " +
            "`${listOf(path, table.name, table.lat, table.lon, table.columns, "yes").joinToString("\t")}`" +
            (if (table.survey == "ca-cautious") " plus its zone `${table.zone}`" else "")
    }

    private companion object {
        const val MAX_LISTED = 40

        /** A cell this far off both its neighbours in its column is the calendar's slip (research-uk's rule). */
        const val NEIGHBOUR_MIN = 20

        /** Exactly an hour or twelve hours off the held capture: a clock-change or 12-hour-clock slip. */
        val CLOCK_SLIPS = setOf(60, 720)

        /** A fetched calendar whose cells agree with the held capture this much, by month and day, is that capture re-dated. */
        const val RE_DATED_PERCENT = 95

        /** A survey finding's kind as the calendar column it was read from. */
        val COLUMN_OF = mapOf(
            "fajr" to "F", "endOfEating" to "F", "sunrise" to "S", "dhuhr" to "D", "asrStandard" to "A", "asrHanafi" to "A",
            "maghrib" to "M", Survey.MAGHRIB_CAP to "M", "isha" to "I",
        )
        val COLUMN_NAMES = mapOf("F" to "Fajr", "S" to "sunrise", "D" to "Dhuhr", "A" to "Asr", "M" to "Maghrib", "I" to "Isha")

        const val CHECK_FIX = "read the reason: a table the archive does not hold or that yields no day is a fetcher's mistake " +
            "(the raw response is under archive/raw/monitor/<source>/<date>/); an entry or unit the registry does not know is " +
            "the fetcher's metadata to fix (tools/timetables/monitor/fetchers/)."

        const val EARLY_LATE_FIX = "an early start or a late end is never excused: if the authority changed its method or its point, " +
            "rebuild it in the registry and refit the margins (add the table as a split=fit row, `./gradlew -p tools/timetables gate " +
            "-Pfit=<entry>`), then re-run the gate; if one stray day is the authority's own slip, record it with its reason (a LateLimit " +
            "covers lateness only, never an early start). Then add the gate rows above (split test), regenerate the stamps and " +
            "ProofStamps.kt, and commit."

        const val LATE_FIX = "lateness over the recorded limit on the table's own entry: refit the margins (`./gradlew -p tools/timetables " +
            "gate -Pfit=<entry>` with the table as a split=fit row) or record the exception in the registry with its reason (rulings R37, " +
            "R41); it is raised once and whenever it worsens, and carried as still open in between."

        const val CALENDAR_FIX = "a start before a mosque's own time, or a sunrise or end of eating after it: decide as the surveys did " +
            "(rulings R75, R87): a family of mosques becomes a member of the cautious entry, a single stray day is recorded in the survey's " +
            "outliers.tsv with its worst minutes, a wrong printed cell in faults.tsv with its reason (a suspected calendar fault above has " +
            "its row ready); then add the calendar to calendars.tsv (its file copied into the archive's survey folder) and re-run the survey test."
    }
}
