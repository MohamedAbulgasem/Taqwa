package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.timetables.gate.formats.DailyFormat
import java.io.File
import kotlin.time.Instant

/**
 * A survey: mosque calendars checked against one registry entry, each at its own mosque's point
 * (research-uk, ruling R75). It is not a gate: a calendar is no member's printed table, so ruling R38's
 * cautious check (every member's table at one place and date) cannot take it. Each calendar is held to
 * the promise on its own instead, day by day, as the app computes the entry there ([DayPipeline]):
 *
 * - no Fajr, Dhuhr, Maghrib or Isha shown before the calendar's (a Maghrib before it that spec §3.6
 *   capped, the members spreading and the most-followed member's own shown, is kind `maghribCap`, apart
 *   from any other early Maghrib);
 * - no Asr before it: the entry's own school against every printed Asr where that school is the later
 *   (Hanafi), else against the prints nearer its time; the other school against the prints nearer its
 *   own time than the entry's (the one tap away in Settings);
 * - no sunrise, and no end of eating, shown after the calendar's sunrise and Fajr (a mosque's Fajr is
 *   when its fast begins);
 * - and no day put back in order ([PrayerDay.repaired], kind `order`) or whose Isha has no end, the
 *   next end of eating at or before it (ruling R26; kind `ishaWindow`, by the minutes it is before).
 *
 * What a calendar gets wrong is left out with its reason ([faults]: dates and columns only, never a
 * time); what no member absorbs at a fair cost for everyone is recorded ([outliers]: the days and the
 * worst minutes it may reach). Anything else fails. Files, in the survey's folder:
 *
 * - `calendars.tsv`: `path` (under the official root, like a gate row's), `name`, `lat`, `lon`,
 *   `columns` (`F S D A M I` in order, `-` for a column not read), `use` (`yes`, or why the whole
 *   calendar is left out), and, where the survey's calendars are not all on one clock (Canada's),
 *   a last column `zone`, the mosque's own civil zone;
 * - `faults.tsv`: `calendar` (its file's name without `.txt`), `from`, `to`, `columns` (letters as
 *   above; `F` also leaves the end of eating out), `reason`;
 * - `outliers.tsv`: `calendar` (or `*` for every one), `event` (an [Event] key, `maghribCap`,
 *   `ishaWindow` or `order`), `from`, `to`, `days` (the most days it may cover, at each calendar for
 *   `*`; `-` for no bound), `worst` (minutes), `reason`.
 */
class Survey(
    val entry: RegistryEntry,
    val zone: TimeZone,
    val countryCode: String,
    val calendars: List<Calendar>,
    val faults: List<Fault>,
    val outliers: List<Outlier>,
) {
    class Calendar(
        val id: String,
        val path: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val columns: List<String>,
        val use: String,
        /** The mosque's own civil zone where it is not the survey's ([Survey.zone]). */
        val zone: TimeZone? = null,
    ) {
        val used: Boolean get() = use == "yes"
    }

    class Fault(val calendar: String, val from: LocalDate, val to: LocalDate, val columns: Set<String>, val reason: String) {
        fun covers(calendar: String, date: LocalDate, column: String) = calendar == this.calendar && date in from..to && column in columns
    }

    class Outlier(
        val calendar: String,
        val kind: String,
        val from: LocalDate,
        val to: LocalDate,
        val days: Int?,
        val worst: Int,
        val reason: String,
    ) {
        fun covers(f: Finding) = (calendar == "*" || calendar == f.calendar) && kind == f.kind && f.date in from..to

        val label: String get() = "$calendar $kind $from..$to"
    }

    /**
     * What breaks the promise at one calendar and date: a start shown before its time or an end after it
     * ([kind] an [Event] key), an Isha with no end ([ISHA_WINDOW]) or a day put back in order ([ORDER]),
     * by [minutes].
     */
    data class Finding(val calendar: String, val kind: String, val date: LocalDate, val minutes: Int, val what: String) {
        override fun toString() = "$calendar $date $kind $what by $minutes min"
    }

    /**
     * What an outlier covered: its [days] (for `*`, the most at any one calendar; [allDays] over them all)
     * and [worst] minutes, against what it records.
     */
    class Covered(val outlier: Outlier, val days: Int, val allDays: Int, val worst: Int) {
        val within: Boolean get() = (outlier.days == null || days <= outlier.days) && worst <= outlier.worst
    }

    class Result(
        val held: Boolean,
        val used: Int,
        val placeDays: Int,
        val checked: Map<Event, Int>,
        val leftOut: Int,
        val unrecorded: List<Finding>,
        val covered: List<Covered>,
    ) {
        /** Everything that breaks the survey: a finding no outlier covers, an outlier past what it records. */
        fun broken(): List<String> =
            unrecorded.map { "not recorded: $it" } +
                covered.filter { !it.within }.map {
                    "${it.outlier.label}: ${it.days} days, worst ${it.worst} min; records ${it.outlier.days ?: "-"} days, worst ${it.outlier.worst}"
                }

        fun report(): String = buildString {
            if (!held) {
                append("no archive: the survey's calendars are not held, nothing checked\n")
                return@buildString
            }
            append("$used calendars used, $placeDays place-days, $leftOut cells left out as faulty\n")
            for ((event, n) in checked) append("  ${event.key.padEnd(12)} $n days checked\n")
            append("recorded outliers (days, worst min; recorded):\n")
            for (c in covered) {
                val all = if (c.outlier.calendar == "*") " (${c.allDays} calendar-days)" else ""
                append("  ${c.outlier.label}: ${c.days} days$all, worst ${c.worst}; records ${c.outlier.days ?: "-"}, ${c.outlier.worst}\n")
            }
            append("not recorded: ${unrecorded.size}\n")
            for (f in unrecorded.take(MAX_LISTED)) append("  $f\n")
        }
    }

    /** Every used calendar against [entry] at its own point, read from [roots] (none when not held). */
    fun evaluate(roots: OfficialRoots): Result {
        if (!roots.held) return Result(false, 0, 0, emptyMap(), 0, emptyList(), emptyList())
        val findings = mutableListOf<Finding>()
        val checked = linkedMapOf<Event, Int>()
        var placeDays = 0
        var leftOut = 0
        val used = calendars.filter { it.used }
        for (calendar in used) {
            val file = roots.file(calendar.path)
            require(file.isFile) { "${calendar.path} is not held (${file.path})" }
            val read = DailyFormat.read(file.readLines(), calendar.columns.size)
            require(read.days.isNotEmpty() && read.unreadable == 0) { "${calendar.path}: ${read.unreadable} unreadable lines" }
            val zone = calendar.zone ?: zone
            val resolution = Registry.resolveEntry(entry, Place(calendar.lat, calendar.lon, zone.id, countryCode))
            val unended = HashMap<LocalDate, PrayerDay>()
            fun unended(date: LocalDate) = unended.getOrPut(date) { DayPipeline.unended(resolution, date, zone) }
            for (official in read.days) {
                val date = official.date
                val next = unended(date.plus(1, DateTimeUnit.DAY))
                val day = Ends.withNextDay(unended(date), next)
                placeDays++
                if (day.repaired) findings += Finding(calendar.id, ORDER, date, 0, "put back in order")
                if (day.ends[Prayer.ISHA] == null) {
                    val before = (day.isha - next.endOfEating).inWholeMinutes.toInt()
                    findings += Finding(calendar.id, ISHA_WINDOW, date, before, "the next end of eating at or before Isha")
                }
                val printed = HashMap<String, Instant>()
                calendar.columns.forEachIndexed { i, column ->
                    val minutes = official.minutes[i] ?: return@forEachIndexed
                    val event = EVENTS[column] ?: return@forEachIndexed
                    if (faults.any { it.covers(calendar.id, date, column) }) {
                        leftOut++
                        return@forEachIndexed
                    }
                    printed[column] = Gate.officialInstant(date, minutes, event, zone, zone)
                }
                fun check(event: Event, shown: Instant, official: Instant, kind: () -> String = { event.key }) {
                    checked[event] = (checked[event] ?: 0) + 1
                    val bad = if (event.isStart) official - shown else shown - official
                    if (bad.isPositive()) {
                        findings += Finding(calendar.id, kind(), date, bad.inWholeMinutes.toInt(), if (event.isStart) "early" else "late")
                    }
                }

                // Spec §3.6's cap: the members' Maghribs spread past Cautious.MAGHRIB_AGREEMENT and the
                // Maghrib shown is the most-followed member's own. A Maghrib before the calendar's for any
                // other reason is `maghrib`.
                fun maghribKind(): String {
                    if (resolution.members.isEmpty()) return Event.MAGHRIB.key
                    val maghribs = DayPipeline.members(resolution, date, zone).map { it.maghrib }
                    val mostFollowed = resolution.members.indices.minBy { resolution.members[it].shareRank }
                    val capped = maghribs.max() - maghribs.min() > Cautious.MAGHRIB_AGREEMENT && day.maghrib == maghribs[mostFollowed]
                    return if (capped) MAGHRIB_CAP else Event.MAGHRIB.key
                }
                printed["F"]?.let { check(Event.FAJR, day.fajr, it); check(Event.END_OF_EATING, day.endOfEating, it) }
                printed["S"]?.let { check(Event.SUNRISE, day.sunrise, it) }
                printed["D"]?.let { check(Event.DHUHR, day.dhuhr, it) }
                printed["A"]?.let { asr ->
                    val hanafi = if (entry.school == AsrSchool.HANAFI) day.asr else day.asrOther
                    val standard = if (entry.school == AsrSchool.STANDARD) day.asr else day.asrOther
                    val nearerStandard = asr < standard + (hanafi - standard) / 2
                    if (entry.school == AsrSchool.HANAFI || !nearerStandard) check(Event.ASR_HANAFI, hanafi, asr)
                    if (nearerStandard) check(Event.ASR_STANDARD, standard, asr)
                }
                printed["M"]?.let { check(Event.MAGHRIB, day.maghrib, it) { maghribKind() } }
                printed["I"]?.let { check(Event.ISHA, day.isha, it) }
            }
        }
        val unrecorded = findings.filter { f -> outliers.none { it.covers(f) } }
        val covered = outliers.map { o ->
            val mine = findings.filter { o.covers(it) }
            val perCalendar = mine.groupBy { it.calendar }.values.maxOfOrNull { f -> f.map { it.date }.distinct().size } ?: 0
            Covered(o, perCalendar, mine.map { it.calendar to it.date }.distinct().size, mine.maxOfOrNull { it.minutes } ?: 0)
        }
        return Result(true, used.size, placeDays, checked, leftOut, unrecorded, covered)
    }

    companion object {
        /** A day whose Isha has no end: the next end of eating at or before it (ruling R26). */
        const val ISHA_WINDOW = "ishaWindow"

        /** A day the pipeline put back in order. */
        const val ORDER = "order"

        /**
         * A Maghrib before a calendar's that spec §3.6 capped: the members' Maghribs spread past
         * [Cautious.MAGHRIB_AGREEMENT] and the most-followed member's own is shown.
         */
        const val MAGHRIB_CAP = "maghribCap"

        private const val MAX_LISTED = 200

        /** A calendar's columns, read as the gate reads them ([Gate.officialInstant]'s civil windows). */
        private val EVENTS = mapOf(
            "F" to Event.FAJR, "S" to Event.SUNRISE, "D" to Event.DHUHR, "A" to Event.ASR_STANDARD,
            "M" to Event.MAGHRIB, "I" to Event.ISHA,
        )

        /** The survey in [dir] of [entryId], computed in [zoneId] for places in [countryCode]. */
        fun load(dir: File, entryId: String, zoneId: String, countryCode: String): Survey {
            val entry = requireNotNull(Registry.byId(entryId)) { "no registry entry $entryId" }
            val calendars = rows(dir.resolve("calendars.tsv"), listOf("path", "name", "lat", "lon", "columns", "use"), optional = "zone").map { r ->
                val columns = r.getValue("columns").split(' ').filter { it.isNotEmpty() }
                require(columns.all { it == "-" || it in EVENTS }) { "${r.getValue("path")}: columns ${r.getValue("columns")}" }
                val path = r.getValue("path")
                Calendar(
                    id = path.substringAfterLast('/').removeSuffix(".txt"), path = path, name = r.getValue("name"),
                    lat = r.getValue("lat").toDouble(), lon = r.getValue("lon").toDouble(), columns = columns, use = r.getValue("use"),
                    zone = r["zone"]?.let { TimeZone.of(it) },
                )
            }
            val ids = calendars.map { it.id }.toSet()
            require(ids.size == calendars.size) { "a calendar listed twice" }
            val faults = rows(dir.resolve("faults.tsv"), listOf("calendar", "from", "to", "columns", "reason")).map { r ->
                Fault(
                    r.getValue("calendar"), LocalDate.parse(r.getValue("from")), LocalDate.parse(r.getValue("to")),
                    r.getValue("columns").split(' ').filter { it.isNotEmpty() }.toSet(), r.getValue("reason"),
                ).also {
                    require(it.calendar in ids) { "faults.tsv: no calendar ${it.calendar}" }
                    require(it.from <= it.to && it.columns.isNotEmpty() && it.columns.all { c -> c in EVENTS }) { "faults.tsv: ${it.calendar} ${it.from}" }
                    require(it.reason.isNotBlank()) { "faults.tsv: ${it.calendar} ${it.from} has no reason" }
                }
            }
            val outliers = rows(dir.resolve("outliers.tsv"), listOf("calendar", "event", "from", "to", "days", "worst", "reason")).map { r ->
                val kind = r.getValue("event")
                require(Event.byKey(kind) != null || kind in setOf(ISHA_WINDOW, ORDER, MAGHRIB_CAP)) { "outliers.tsv: event $kind" }
                Outlier(
                    r.getValue("calendar"),
                    kind,
                    LocalDate.parse(r.getValue("from")), LocalDate.parse(r.getValue("to")),
                    r.getValue("days").takeIf { it != "-" }?.toInt(), r.getValue("worst").toInt(), r.getValue("reason"),
                ).also {
                    require(it.calendar == "*" || it.calendar in ids) { "outliers.tsv: no calendar ${it.calendar}" }
                    require(it.reason.isNotBlank()) { "outliers.tsv: ${it.label} has no reason" }
                }
            }
            return Survey(entry, TimeZone.of(zoneId), countryCode, calendars, faults, outliers)
        }

        /**
         * A tab-separated file: `#` lines are comments, the first other line the header naming [columns],
         * with [optional] allowed as one more, last column.
         */
        private fun rows(file: File, columns: List<String>, optional: String? = null): List<Map<String, String>> {
            val lines = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            val header = lines.first().split('\t')
            val expected = if (optional != null && header == columns + optional) columns + optional else columns
            require(header == expected) {
                val last = optional?.let { ", with an optional last column $it" }.orEmpty()
                "${file.name}: the header must be ${columns.joinToString(" ")}$last"
            }
            return lines.drop(1).map { line ->
                val cells = line.split('\t')
                require(cells.size == expected.size) { "${file.name}: '$line' has ${cells.size} cells" }
                expected.zip(cells.map { it.trim() }).toMap()
            }
        }
    }
}
