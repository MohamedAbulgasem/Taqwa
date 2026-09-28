package world.taqwa.timetables.monitor

import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.timetables.gate.EntryStats
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateError
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Survey
import world.taqwa.timetables.gate.Surveys
import java.io.File

/**
 * One fetched table through the gate's machinery at its own point (brief P, item 4). An
 * authority's table is checked as a gate row against the entry it is a table of, where the entry
 * Automatic resolves at the point agrees; where Automatic resolves a cautious entry that lists the
 * table's entry as a member, the row names that member (ruling R38: each start against the member's
 * printed time, Maghrib under the cap); where Automatic resolves something else (Diyanet's London
 * table inside the M25), the table's own entry is checked, since no other entry claims to reproduce
 * it. A mosque calendar goes through its survey's machinery ([Survey]) at the mosque's point,
 * against the survey's entry, with the faults and outliers its folder records for that calendar.
 *
 * Counts early starts, late ends and cells over the recorded late limits, and classifies: early or
 * late (attention, with the figures), a new table that is fine (with a gate row ready to paste), a
 * changed table that is fine, or a table that could not be checked (attention).
 */
class TableCheck(
    private val roots: OfficialRoots,
    private val officialDir: File,
    private val lookup: (String) -> RegistryEntry? = Registry::byId,
) {

    /** How a table is checked: the gate row's text (ready to paste), or the survey it goes through. */
    sealed class Plan {
        data class Row(val manifest: GateManifest, val text: String, val why: String) : Plan()
        data class Calendar(val survey: Survey, val why: String) : Plan()
        data class Refused(val reason: String) : Plan()

        /** A calendar its survey leaves out whole (`use` is the reason): not checked, for the record. */
        data class LeftOut(val reason: String) : Plan()
    }

    fun check(table: MonitorTable): Item = when (val plan = plan(table)) {
        is Plan.Refused -> Item(Kind.FETCH_BROKEN, "${table.id} could not be checked", listOf(plan.reason), CHECK_FIX)
        is Plan.LeftOut -> Item(Kind.GREEN, "${table.id} (${table.name}): its survey leaves it out, not checked (${plan.reason})")
        is Plan.Row -> checkRow(table, plan)
        is Plan.Calendar -> checkCalendar(table, plan)
    }

    fun plan(table: MonitorTable): Plan {
        if (table.survey != null) return calendarPlan(table)
        val hint = table.entry ?: return Plan.Refused("no entry named in the index (a mosque calendar names its survey instead)")
        val hintId = hint.substringBefore('/')
        val hintEntry = lookup(hintId) ?: return Plan.Refused("no registry entry '$hintId'")
        val lat = table.lat
        val lon = table.lon
        if ('/' in hint) return Plan.Row(rowManifest(table, hint, null, null, null), rowText(table, hint, null, null, null), "checked at its unit's own point")
        if (lat == null || lon == null) {
            return if (hintEntry.method?.fixedPoint != null) {
                Plan.Row(rowManifest(table, hint, null, null, null), rowText(table, hint, null, null, null), "checked at the method's fixed point")
            } else {
                Plan.Refused("no point: give lat and lon, or an entry/unit")
            }
        }
        val automatic = Registry.automaticEntry(Place(lat, lon, table.zone, table.cc))
        return when {
            automatic.id == hintEntry.id -> Plan.Row(
                rowManifest(table, hint, lat, lon, null), rowText(table, hint, lat, lon, null),
                "Automatic follows ${automatic.id} at ${table.name}",
            )
            automatic.members.any { it.id == hintEntry.id } -> Plan.Row(
                rowManifest(table, automatic.id, lat, lon, hintEntry.id), rowText(table, automatic.id, lat, lon, hintEntry.id),
                "Automatic follows the cautious ${automatic.id} at ${table.name}, whose member ${hintEntry.id} this table is",
            )
            else -> Plan.Row(
                rowManifest(table, hint, lat, lon, null), rowText(table, hint, lat, lon, null),
                "Automatic follows ${automatic.id} at ${table.name}, not this table's ${hintEntry.id}: the table's own entry is checked",
            )
        }
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
        return Plan.Calendar(survey, why)
    }

    private fun checkRow(table: MonitorTable, plan: Plan.Row): Item {
        val result = try {
            Gate(roots, lookup = lookup).evaluate(plan.manifest)
        } catch (e: GateError) {
            return Item(Kind.FETCH_BROKEN, "${table.id} could not be checked", e.problems, CHECK_FIX)
        }
        val stats = result.entries.values.singleOrNull()
            ?: return Item(Kind.FETCH_BROKEN, "${table.id} could not be checked", listOf("the gate read no day from it"), CHECK_FIX)
        val violations = result.violations()
        val figures = figures(stats)
        val row = "gate row (${plan.why}): `${plan.text}`"
        return if (violations.isNotEmpty()) {
            Item(
                Kind.EARLY_OR_LATE, "${table.id} (${table.name}) against ${stats.entry.id}",
                listOf(figures) + violations.map { it.replace("\n", "; ") } + row,
                EARLY_LATE_FIX,
            )
        } else if (table.status == "new") {
            Item(Kind.NEW_TABLE_FINE, "${table.id} (${table.name}) against ${stats.entry.id}", listOf(figures, row))
        } else {
            Item(Kind.CHANGED_FINE, "${table.id} (${table.name}) against ${stats.entry.id}", listOf(figures, row))
        }
    }

    private fun checkCalendar(table: MonitorTable, plan: Plan.Calendar): Item {
        val result = try {
            plan.survey.evaluate(roots)
        } catch (e: IllegalArgumentException) {
            return Item(Kind.FETCH_BROKEN, "${table.id} could not be checked", listOf(e.message ?: "unreadable"), CHECK_FIX)
        }
        val broken = result.broken()
        val figures = "${result.placeDays} place-days at the mosque's point against ${plan.survey.entry.id} (${plan.why}); " +
            result.checked.entries.joinToString(", ") { (e, n) -> "${e.key} $n" } +
            (if (result.leftOut > 0) "; ${result.leftOut} cells left out as faulty" else "")
        return if (broken.isNotEmpty()) {
            Item(
                Kind.EARLY_OR_LATE, "${table.id} (${table.name}) against ${plan.survey.entry.id}",
                listOf(figures) + broken.take(MAX_LISTED) + (if (broken.size > MAX_LISTED) listOf("… ${broken.size - MAX_LISTED} more") else emptyList()),
                CALENDAR_FIX,
            )
        } else if (table.status == "new") {
            Item(Kind.NEW_TABLE_FINE, "${table.id} (${table.name}) against ${plan.survey.entry.id}", listOf(figures, calendarRow(table)))
        } else {
            Item(Kind.CHANGED_FINE, "${table.id} (${table.name}) against ${plan.survey.entry.id}", listOf(figures))
        }
    }

    private fun figures(s: EntryStats): String {
        val worst = s.events.entries.joinToString(", ") { (event, e) ->
            "${event.key} ${e.checked} days" + (if (event.isStart) ", ${e.early} early" else ", ${e.lateEnd} late ends") +
                ", worst ${e.worst} of limit ${s.effectiveLimit(event)}" + (if (e.over > 0) ", ${e.over} over" else "")
        }
        return "${s.placeDayCount} place-days ${s.first}..${s.last} (class ${s.classes.joinToString("/")}): $worst"
    }

    private fun rowManifest(table: MonitorTable, entry: String, lat: Double?, lon: Double?, member: String?): GateManifest =
        GateManifest.parse("monitor:${table.id}", rowHeader() + "\n" + rowText(table, entry, lat, lon, member))

    private fun rowHeader(): String = (GateManifest.HEADER + GateManifest.OPTIONAL).joinToString("\t")

    /** A gate row in the header order of [rowHeader], `member` and `clock` last: ready to paste into a gate file. */
    private fun rowText(table: MonitorTable, entry: String, lat: Double?, lon: Double?, member: String?): String = listOf(
        table.path, entry, lat?.toString().orEmpty(), lon?.toString().orEmpty(), table.zone, table.columns, table.format,
        table.school, "test", "${table.name}, ${table.source} fetched ${table.fetched}", member.orEmpty(), table.clock.orEmpty(),
    ).joinToString("\t")

    private fun calendarRow(table: MonitorTable): String =
        "calendars.tsv row: `${listOf(table.path, table.name, table.lat, table.lon, table.columns, "yes").joinToString("\t")}`" +
            (if (table.survey == "ca-cautious") " plus its zone `${table.zone}`" else "")

    private companion object {
        const val MAX_LISTED = 40

        const val CHECK_FIX = "read the reason: a table the archive does not hold or that yields no day is a fetcher's mistake " +
            "(the raw response is under archive/raw/monitor/<source>/<date>/); an entry or unit the registry does not know is " +
            "the fetcher's metadata to fix (tools/timetables/monitor/fetchers/)."

        const val EARLY_LATE_FIX = "an early start or a late end is never excused: if the authority changed its method or its point, " +
            "rebuild it in the registry and refit the margins (add the table as a split=fit row, `./gradlew -p tools/timetables gate " +
            "-Pfit=<entry>`), then re-run the gate; if one stray day is the authority's own slip, record it with its reason (a LateLimit " +
            "covers lateness only, never an early start). Lateness over the limit: refit, or record the exception in the registry " +
            "(rulings R37, R41). Then add the gate row above (split test), regenerate the stamps and ProofStamps.kt, and commit."

        const val CALENDAR_FIX = "a start before a mosque's own time, or a sunrise or end of eating after it: decide as the surveys did " +
            "(rulings R75, R87): a family of mosques becomes a member of the cautious entry, a single stray day is recorded in the survey's " +
            "outliers.tsv with its worst minutes, a wrong printed cell in faults.tsv with its reason; then add the calendar to " +
            "calendars.tsv (its file copied into the archive's survey folder) and re-run the survey test."
    }
}
