package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateLimitFor
import world.taqwa.app.prayer.engine.registry.reachLateLimit
import java.io.File
import java.util.Locale
import java.util.SortedMap
import java.util.SortedSet
import kotlin.time.Instant

/**
 * The engine against every official day held locally (spec §5). For each gate row: the entry by
 * its id (never by place), the table's point (or its unit's reference point, or the method's fixed
 * point), the day through [DayPipeline] exactly as the app computes it, and each mapped column:
 *
 * - a start shown before the official minute is **early**, otherwise it is so many minutes late;
 * - an end (sunrise, the end of eating, imsak) shown after it is a **late end**, otherwise it is so
 *   many minutes early, which counts as lateness too;
 * - lateness beyond the event's limit is **over** the limit: the `LateLimit` its unit, else its
 *   entry, records for that event, else its class's (A 1, B 2, C 1 after the latest member, D 3)
 *   (rulings R37, R41);
 * - a day the pipeline had to put back in order, or whose Isha ends no later than the next day's end
 *   of eating, is **out of order** (the invariants hold on the unadjusted day, spec §3.3);
 * - a cell whose authority time the day itself declares it does not show (`PrayerDay.notFollowed`,
 *   ruling R82: no day in order can) is **declared**, neither early nor late, and counted with its
 *   dates in the report and the stamp. Nothing else excuses an early start or a late end.
 *
 * A cautious (class C) entry's rows are its members' printed tables, each naming its `member`
 * (ruling R38). Rows at the same point and date are one place-day. Each start is checked against
 * every member's printed time and is late by its minutes after the latest; each end against every
 * member's, early by its minutes before the earliest. Maghrib too (ruling R155: the latest time any
 * member gives from the sun), but for the far-north guard (ruling R156, [MaghribBar]): on a day a
 * member that is not the most followed has its Maghrib set by its own day-length rule (Diyanet's
 * five-hour winter day: [PrayerDay.maghribHeld]), the shown Maghrib is checked against that
 * member's Maghrib from the sun instead of its printed one; where a rule sets it outright (a polar
 * day's borrowed time, a clock rule's estimate) or the real sun does not set at the point, against
 * the other members' alone. Each such cell is recorded, with the gap to the member's printed
 * Maghrib, in the report and the stamp. A gate file's `@excuse` lines ([Excuse], ruling R117)
 * record the days a member's rows leave out because its table does not publish them or publishes
 * them wrongly; they excuse no cell, only say why a day is missing.
 *
 * Nothing unread passes. Without the archive root (CI) every row is skipped and counted; with it,
 * a table that is not there, or one that yields no day, is a mistake in the gate file, and a date a
 * table repeats is checked on every line ([OfficialRoots], [OfficialTable]). [change] rewrites
 * each resolved method before it is computed (the [Fitter]'s holdout run); by default the
 * registry's own methods are checked.
 */
class Gate(
    private val roots: OfficialRoots,
    private val change: ((TimetableMethod) -> TimetableMethod)? = null,
    /** Registry entries by id; a test may add its own. */
    private val lookup: (String) -> RegistryEntry? = Registry::byId,
    /**
     * The user's Hijri offset every day is computed with (`EngineSettings.hijriOffsetDays`); 0, the gate's own,
     * unless a test asks what ±1 does to the Ramadan rules (HijriOffsetTest).
     */
    private val hijriOffsetDays: Int = 0,
) {

    /**
     * [manifest]'s rows checked. [whole] is the gate they were taken from, where they are fewer (a run of one entry or
     * one file): what an entry shares with another is read off every row there is ([AcrossEntries]), so that a stamp
     * written by a part run says what the whole gate's would.
     */
    fun evaluate(manifest: GateManifest, whole: GateManifest = manifest): GateResult {
        val problems = mutableListOf<String>()
        val stats = sortedMapOf<String, EntryStats>()
        val skipped = mutableListOf<GateRow>()
        var checked = 0
        val cells = LinkedHashMap<String, CautiousCell>()
        // A promise-only file's cautious cells ([GateRow.promiseOnly]), accounted as soon as its rows move to another point:
        // its rows stand together by entry and point, and a gate over every app city could not hold them all at once.
        val promiseCells = LinkedHashMap<String, CautiousCell>()
        var promiseGroup: String? = null
        // Rows at the same point of the same entry (a year per file, a table in two files) share days. Only the last
        // few points are kept: each holds every day it computed, and the reach rows (sa-ummalqura-reach.tsv,
        // kz-qmdb-reach.tsv) read some three thousand points, which the whole gate could not hold at once. A point
        // read again later is computed again, to the same days.
        val computed = object : LinkedHashMap<String, Days>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Days>): Boolean = size > DAYS_KEPT
        }

        // Ruling R214: which row counts each table's days, and which cells the margins never saw (by table file and, with
        // the archive held, by the days each table prints: a fitted table fetched again is no held-out table).
        val columnDays = if (roots.held) columnDays(roots) else null
        // Ruling R299: a table that repeats another day for day is that table again, read off every row there is.
        val repeats = if (roots.held) Repeats(whole.rows, printedDays(roots)) else null
        val counted = Counted(manifest, columnDays, unitOf(lookup), repeats)
        // And across the entries (the review round of 14 Nov 2026): a day another entry counts, the tables an entry owns.
        val across = AcrossEntries(whole.rows, columnDays, if (roots.held) roots::file else null, repeats)

        for (row in manifest.rows) {
            // Every row is checked for its entry, unit, point and zone, held or not, so that CI
            // (which holds no archive) still refuses a gate file with a mistake in it.
            val setup = setUp(row, roots, change, problems, lookup) ?: continue
            val table = setup.table
            if (table == null) {
                skipped += row
                continue
            }
            val (entry, point, zone, resolution) = setup
            checked++
            val s = stats.getOrPut(entry.id) { EntryStats(entry) }
            val limits = row.columns.flatten().associateWith { lateLimit(it, setup.unit, resolution.entry, resolution.entryClass) }
            val promiseOnly = row.promiseOnly
            // A row that reads ends alone (a printed imsak, an end of eating) checks those ends on days a row reading a
            // start holds, and adds no place-day of its own: no day counts as checked, nor as held out, by its ends alone.
            val endsOnly = row.endsOnly
            // Ruling R214: a table's days are counted at one row; any other row reading it is a replay, a check alone.
            val own = counted.isOwn(row)
            val scope = counted.scope(row)
            val unit = if (promiseOnly) null else setup.unit?.let { UnitKey(it.id, it.name) }
            s.rows += row.where
            s.unreadable += table.unreadable
            s.duplicates += table.duplicates
            s.fingerprints += Stamps.fingerprint(resolution)
            // A promise-only row's place is no place the entry's proof names (its stamp's places and days).
            if (!promiseOnly) s.places += point
            val cautious = resolution.entryClass == EntryClass.C
            val mostFollowed = resolution.members.minByOrNull { it.shareRank }?.id
            val days = computed.getOrPut("${entry.id}|${row.unit}|${point.lat},${point.lon}|${row.zone}") { Days(resolution, zone, hijriOffsetDays) }
            // Days actually in Ramadan (Umm al-Qura's dates), not the day either side authorities are given.
            val ramadan = Registry.ramadanCalendar()

            for (official in table.days) {
                val day = days.day(official.date)
                val epochDay = official.date.toEpochDays().toInt()
                // A place-day is the entry at one point on one date, however many tables hold it.
                val group = "${entry.id}@${pointKey(point)}"
                if (promiseOnly && group != promiseGroup) {
                    for (cell in promiseCells.values) cell.account()
                    promiseCells.clear()
                    promiseGroup = group
                }
                // The entry shows an imsak here, so a Ramadan is proven here only with its imsak too (ruling R246).
                if (!promiseOnly && day.imsak != null) s.imsakShown(unit)
                // Ruling R302: a day with no Isha end on a night the real sun at the row's point makes too short for one is
                // declared, never out of order; recomputed here from the sun alone, never read off the day.
                val shortNight = { DayPipeline.nightTooShortForIsha(point, official.date, zone) }
                if (promiseOnly) {
                    s.promiseDay(group, official.date, day, shortNight)
                } else if (endsOnly) {
                    s.endsOnlyDay(group, official.date, unit, row.where)
                } else {
                    s.placeDay(group, official.date, row.split, ramadan.isRamadan(official.date), day, unit, row.member, point, own, counted.heldOut(row, epochDay), across.shared(row, epochDay), shortNight)
                }
                row.columns.forEachIndexed { column, events ->
                    val minutes = official.minutes[column] ?: return@forEachIndexed
                    for (event in events) {
                        val at = officialInstant(official.date, minutes, event, setup.clock, zone)
                        val shown = shown(day, event, entry.school)
                        if (shown == null) {
                            if (!promiseOnly) s.event(event).absent++
                            continue
                        }
                        // Ruling R214. A promise-only row is in no stamp: its breaks alone are tallied, under its own split as ever.
                        val heldOut = if (promiseOnly) row.split == Split.TEST else counted.heldOut(row, event, epochDay)
                        val observation = Observation(
                            official.date, event, shown, at, resolution.entryClass, row, limits.getValue(event), unit,
                            heldOut, replay = !promiseOnly && !counted.counts(row, event), scope = scope,
                        )
                        if (event.declarable in day.notFollowed) {
                            if (!promiseOnly) s.declare(event, official.date, counted.heldOut(row, event, epochDay) && counted.counts(row, event), unit)
                            // Ruling R294: a declared Isha is only ever moved later, to the minute after a Maghrib past the
                            // authority's (DayComputer's point 9, and the real-sun floor's R293), so it is never before the printed
                            // Isha; it is held to that as a start, here or at a promise-only row. A declared Fajr is earlier than
                            // the printed one by design (point 8) and a declared sunrise is an end; neither is checked.
                            if (event == Event.ISHA && shown < at) s.declaredEarly(observation)
                            continue
                        }
                        if (cautious) {
                            (if (promiseOnly) promiseCells else cells).getOrPut("$group|${official.date}|${event.key}") {
                                CautiousCell(s, mostFollowed!!, promiseOnly) { days.maghribBars(official.date) }
                            }.add(observation)
                        } else {
                            s.account(observation)
                        }
                    }
                }
            }
        }
        for (cell in cells.values) cell.account()
        for (cell in promiseCells.values) cell.account()
        for (s in stats.values) {
            for ((where, dates) in s.endsOnlyUnheld()) {
                problems += "$where: reads ends alone on ${dateRanges(dates).joinToString(", ")}, which no row reading a start holds at its point"
            }
        }
        for (excuse in manifest.excuses) excuse(excuse, stats, problems)
        // Ruling R185: an entry's standing demotions go into its stamp.
        for (s in stats.values) s.demoted = standing(manifest.demotions, s.entry.id)
        for (s in stats.values) s.tables = across.tables(s.entry.id)
        if (problems.isNotEmpty()) throw GateError(problems)
        return GateResult(roots, checked, skipped, stats)
    }

    /**
     * Ruling R117: [excuse]'s days recorded for its member at each point its rows were read at (or
     * at its one point), where those rows do not hold them. Checked like a row, archive or not: its
     * entry must be cautious and the member one of its own. With the archive, a line that matches
     * no row of the member, or excuses a day the gate compared for the member there, is a mistake
     * in the gate file: a day is checked or excused, never both.
     */
    private fun excuse(excuse: Excuse, stats: Map<String, EntryStats>, problems: MutableList<String>) {
        val entry = lookup(excuse.entry) ?: return Unit.also { problems += "${excuse.where}: no registry entry '${excuse.entry}'" }
        val members = entry.members.map { it.id }
        if (members.isEmpty()) return Unit.also { problems += "${excuse.where}: ${entry.id} is not cautious; only a member's days are excused" }
        if (excuse.member !in members) {
            return Unit.also { problems += "${excuse.where}: ${excuse.member} is not a member of ${entry.id} (${members.joinToString(" ")})" }
        }
        if (!roots.held) return
        val s = stats[entry.id]
        val points = s?.memberDays?.get(excuse.member).orEmpty().filterKeys { excuse.at == null || it == pointKey(excuse.at) }
        if (s == null || points.isEmpty()) {
            return Unit.also { problems += "${excuse.where}: no row of ${excuse.member} for ${entry.id}${excuse.at?.let { " at ${pointKey(it)}" } ?: ""}" }
        }
        for ((point, dates) in points) {
            val both = dates.filter { it in excuse.days }
            if (both.isNotEmpty()) {
                problems += "${excuse.where}: ${excuse.member} is checked at $point on ${dateRanges(both).joinToString(", ")}, which this line excuses"
                continue
            }
            s.excuse(excuse.member, point, excuse.kind, excuse.days)
        }
    }

    /**
     * What a row is checked with: its entry, point, the engine's civil zone, resolution, table
     * (null without the archive), the authority unit the point resolved to (null outside every
     * unit) and the zone its times are printed in.
     */
    data class Setup(
        val entry: RegistryEntry,
        val point: GeoPoint,
        val zone: TimeZone,
        val resolution: Resolution,
        val table: OfficialTable?,
        val unit: AuthorityUnit? = null,
        val clock: TimeZone = zone,
    )

    /** One resolution's days, each computed once ([DayPipeline], R34). */
    private class Days(private val resolution: Resolution, private val zone: TimeZone, private val hijriOffsetDays: Int) {
        private val unended = HashMap<LocalDate, PrayerDay>()

        private fun unended(date: LocalDate): PrayerDay =
            unended.getOrPut(date) { DayPipeline.unended(resolution, date, zone, hijriOffsetDays = hijriOffsetDays) }

        fun day(date: LocalDate): PrayerDay = Ends.withNextDay(unended(date), unended(date.plus(1, DateTimeUnit.DAY)))

        /**
         * A cautious resolution's members on [date], by id: what each one's printed Maghrib is checked as
         * (ruling R156, [maghribBar]), from the engine's own members' days and the real sunset at the point,
         * as [world.taqwa.app.prayer.engine.day.Cautious.combine] reads them.
         */
        fun maghribBars(date: LocalDate): Map<String, MaghribBar> {
            val members = resolution.members
            val mostFollowed = members.minBy { it.shareRank }.id
            val sunSets = DayPipeline.realSunset(resolution.point, date, zone) != null
            return members.zip(DayPipeline.members(resolution, date, zone, hijriOffsetDays = hijriOffsetDays)).associate { (member, day) ->
                member.id to maghribBar(day, member.id == mostFollowed, sunSets)
            }
        }
    }

    /**
     * One event of a cautious place-day, with each member's printed time (ruling R38): a start is
     * measured against the latest member's, an end against the earliest's. Maghrib is a start like
     * any other (ruling R155), each member's printed time read as its [MaghribBar] says (ruling R156):
     * its printed time where its Maghrib counts, its Maghrib from the sun where its own day-length
     * rule held the printed one later, nothing where a rule sets it. Each member read otherwise than
     * as printed is recorded with its gap ([EntryStats.maghribGuards]); a date no member's time
     * checks is recorded too ([EntryStats.maghribGuardUnchecked]), never early against a time the
     * engine does not follow.
     */
    private class CautiousCell(
        private val stats: EntryStats,
        private val mostFollowed: String,
        /** A promise-only file's cell ([GateRow.promiseOnly]): no guard recorded, a date no member's time checks not counted. */
        private val promiseOnly: Boolean,
        private val maghribBars: () -> Map<String, MaghribBar>,
    ) {
        /** Each member's observation; a member printing the date twice keeps its stricter line. */
        private val byMember = LinkedHashMap<String, Observation>()

        fun add(o: Observation) {
            val member = o.row.member!!
            val had = byMember[member]
            val stricter = had == null || if (o.event.isStart) o.official > had.official else o.official < had.official
            if (stricter) byMember[member] = o
        }

        fun account() {
            val all = byMember.values
            val first = all.first()
            if (first.event != Event.MAGHRIB) {
                stats.account(if (first.event.isStart) all.maxBy { it.official } else all.minBy { it.official })
                return
            }
            // The most-followed member's Maghrib always counts: the engine's members are computed only where another's is held.
            val bars = if (byMember.keys.all { it == mostFollowed }) emptyMap() else maghribBars()
            val checked = byMember.mapNotNull { (member, o) ->
                when (val bar = bars[member] ?: MaghribBar.Printed) {
                    MaghribBar.Printed -> o
                    is MaghribBar.Sun -> {
                        if (!promiseOnly) stats.maghribGuard(member, o, atSun = true)
                        Observation(o.date, o.event, o.shown, bar.at, o.entryClass, o.row, o.limit, o.unit, o.heldOut, o.replay, o.scope)
                    }
                    MaghribBar.None -> {
                        if (!promiseOnly) stats.maghribGuard(member, o, atSun = false)
                        null
                    }
                }
            }
            when {
                checked.isNotEmpty() -> stats.account(checked.maxBy { it.official })
                // A table the place does not follow: nothing of the entry's own proof.
                promiseOnly -> Unit
                else -> stats.maghribGuardUnchecked(first)
            }
        }
    }

    companion object {
        /** How many points' computed days [evaluate] keeps at once (rows of one point stand together). */
        private const val DAYS_KEPT = 64

        /**
         * For [Repeats] (ruling R299): the days a row's table prints, each table read once per reader and column count.
         * Null where the table is not there or does not read.
         */
        fun printedDays(roots: OfficialRoots): (GateRow) -> List<OfficialDay>? {
            val read = HashMap<String, List<OfficialDay>?>()
            return { row ->
                read.getOrPut("${row.path}|${row.format}|${row.columns.size}") {
                    runCatching { OfficialFormats.named(row.format).read(roots.file(row.path).readLines(), row.columns.size).days }.getOrNull()
                }
            }
        }

        /**
         * For [Counted]'s rule by date: the days each column of a row's table holds a time on, as epoch days, each table
         * read once per reader and column count. Null where the table is not there or does not read: [setUp] says so.
         */
        fun columnDays(roots: OfficialRoots): (GateRow) -> List<java.util.BitSet>? {
            val read = HashMap<String, List<java.util.BitSet>>()
            return { row ->
                read.getOrPut("${row.path}|${row.format}|${row.columns.size}") {
                    val file = roots.file(row.path)
                    val days = runCatching { OfficialFormats.named(row.format).read(file.readLines(), row.columns.size).days }.getOrDefault(emptyList())
                    List(row.columns.size) { column ->
                        java.util.BitSet().also { on -> for (day in days) if (day.minutes[column] != null) on.set(day.date.toEpochDays().toInt()) }
                    }
                }
            }
        }

        /**
         * [row] made ready to check: the entry by id, the table's point (its own, else its unit's
         * reference point, else the method's fixed point), the entry resolved there (the unit logic
         * of the registry, checked against a named unit) with [change] applied to its method, and
         * the table read when the archive is held ([OfficialRoots]). Null, with the reason in
         * [problems], when any of it fails: a table the archive does not hold, or one that yields no
         * day (a column list that does not fit its lines), is a mistake in the gate file, and so is
         * an end read from a start's column of a table whose minutes were rounded up ([roundedUp],
         * ruling R149).
         */
        fun setUp(
            row: GateRow,
            roots: OfficialRoots,
            change: ((TimetableMethod) -> TimetableMethod)?,
            problems: MutableList<String>,
            lookup: (String) -> RegistryEntry? = Registry::byId,
        ): Setup? {
            val entry = lookup(row.entry) ?: return null.also { problems += "${row.where}: no registry entry '${row.entry}'" }
            val members = entry.members.map { it.id }
            when {
                members.isEmpty() && row.member != null ->
                    return null.also { problems += "${row.where}: ${entry.id} is not cautious; its rows name no member" }
                members.isNotEmpty() && row.member !in members ->
                    return null.also {
                        problems += "${row.where}: a cautious row names the member whose table it is (${entry.id}: ${members.joinToString(" ")})"
                    }
            }
            val unit = row.unit?.let { id ->
                Units.of(entry.id)?.units?.firstOrNull { it.id == id }
                    ?: return null.also { problems += "${row.where}: ${entry.id} has no unit '$id'" }
            }
            val point = when {
                row.lat != null && row.lon != null -> GeoPoint(row.lat, row.lon)
                unit != null -> unit.point
                else -> entry.method?.fixedPoint ?: return null.also {
                    problems += "${row.where}: give lat and lon (${entry.id} has no fixed point) or a unit as ${entry.id}/<unit>"
                }
            }
            val zone = runCatching { TimeZone.of(row.zone) }.getOrNull()
                ?: return null.also { problems += "${row.where}: unknown zone '${row.zone}'" }
            val clock = runCatching { TimeZone.of(row.clockZone) }.getOrNull()
                ?: return null.also { problems += "${row.where}: unknown clock '${row.clockZone}'" }
            val format = try {
                OfficialFormats.named(row.format)
            } catch (e: IllegalArgumentException) {
                return null.also { problems += "${row.where}: ${e.message}" }
            }
            val table = if (!roots.held) {
                null
            } else {
                val file = roots.file(row.path)
                if (!file.isFile) return null.also { problems += "${row.where}: ${row.path} is not held (${file.path})" }
                val lines = file.readLines()
                val shared = row.columns.firstOrNull { column -> column.any { it.isStart } && column.any { !it.isStart } }
                if (shared != null && roundedUp(lines)) {
                    return null.also {
                        problems += "${row.where}: ${row.path} says its times were rounded up to the minute, so a column that holds a start " +
                            "may not also hold an end (${shared.joinToString("+") { it.key }}): the printed minute could stand up to 59 s " +
                            "after the authority's moment, and a day at +0 would pass while the end is late (ruling R149). Read the end " +
                            "from a column of its own, floored from the source's seconds or the printed minute less one " +
                            "(archive/code/moral-auth-world/southasia/di_floor_e.py)"
                    }
                }
                val read = OfficialTable.from(format.read(lines, row.columns.size))
                if (read.days.isEmpty()) {
                    return null.also {
                        problems += "${row.where}: no day read from ${row.path} (${read.unreadable} unreadable lines for " +
                            "${row.columns.size} columns in format ${row.format})"
                    }
                }
                read
            }
            val place = Place(point.lat, point.lon, row.zone, entry.countries.firstOrNull() ?: "")
            val found = Registry.resolveEntry(entry, place)
            // A row names its unit, and a row's place has no region. Where that unit shares its point with the one the
            // point resolves to (a seat's unit whose table is held for it, a printed or a fitted one, ruling R217: Oran;
            // a wilaya's own unit, which a place without a region is never handed, ruling R219), or is one a place
            // reaches by its region alone and holds the row's point within its own reach (Dellys, in Boumerdès), the
            // row's own unit is the one checked, as itself.
            val asItself = unit != null && entry.method != null && found.unitId != unit.id && (
                found.unitPoint == unit.point ||
                    (found.unitName != unit.name && Units.of(entry.id)?.choose != null && distanceKm(point, unit.point) <= unit.radiusKm)
                )
            if (unit != null && !asItself && found.unitName != unit.name) {
                return null.also { problems += "${row.where}: the point resolves to ${found.unitName ?: "no unit"}, not ${unit.name}" }
            }
            val resolution = if (unit != null && asItself) Registry.resolveEntry(entry, place, inUnit = unit) else found
            val changed = if (change == null) resolution else resolution.copy(method = resolution.method?.let(change))
            val resolved = unit ?: Units.of(entry.id)?.units?.firstOrNull { it.name == resolution.unitName && it.point == resolution.unitPoint }
            return Setup(entry, point, zone, changed, table, resolved, clock)
        }

        /**
         * The words a table's `#` lines carry where its minutes were rounded up from the source's seconds ([roundedUp]):
         * "rounded up" as the archive writes it, and the other ways to say it ("rounds them up", "ceil", "to the next
         * minute"), so a table worded differently is not let through (review of 12 Nov 2026). None of the 1,435 tables
         * read with a shared column on 12 Nov 2026 carries any of them.
         */
        private val ROUNDED_UP = Regex(
            "\\bround(?:ed|s|ing)?\\s+(?:\\w+\\s+)?up\\b|\\bceil(?:ing|ed)?\\b|\\bto the next (?:whole |full )?minute\\b",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Ruling R149: whether a table's comment lines say its times were rounded up to the minute
         * (Dawat-e-Islami's months, whose AJAX prints seconds; Dar al-Iftaa's derived starts). Right
         * for a start, which is then never shown early; wrong as an end's reference, which could stand
         * up to 59 s after the authority's moment. [setUp] refuses such a table on a row whose column
         * holds a start and an end together (`F+E`), the shape that hid a late end of under a minute
         * at Karachi until 18 Oct 2026 (E16, ruling R147).
         */
        fun roundedUp(lines: List<String>): Boolean =
            lines.any { line -> line.trimStart().startsWith("#") && ROUNDED_UP.containsMatchIn(line) }

        /**
         * The unit a row that names none stands in ([Counted.scope], the second trust audit's G27): its point resolved as
         * [setUp] resolves it, once per entry, point and zone. Null for a row with a unit or no point, an unknown entry,
         * or a point no unit holds.
         */
        fun unitOf(lookup: (String) -> RegistryEntry? = Registry::byId): (GateRow) -> String? {
            val known = java.util.concurrent.ConcurrentHashMap<String, String>()
            return { row ->
                if (row.unit != null || row.lat == null || row.lon == null) {
                    null
                } else {
                    known.getOrPut("${row.entry}|${row.lat},${row.lon}|${row.zone}") {
                        val entry = lookup(row.entry)
                        if (entry == null || Units.of(entry.id) == null) {
                            ""
                        } else {
                            Registry.resolveEntry(entry, Place(row.lat, row.lon, row.zone, entry.countries.firstOrNull() ?: "")).unitId.orEmpty()
                        }
                    }.ifEmpty { null }
                }
            }
        }

        /**
         * [event]'s late limit on a row (rulings R37, R41): the `LateLimit` [lateLimitFor] finds for it
         * (the unit's own, else the entry's), else the class default. An exception covers only the
         * events it names.
         */
        fun lateLimit(event: Event, unit: AuthorityUnit?, entry: RegistryEntry, entryClass: EntryClass): Limit {
            val found = lateLimitFor(event.timed, unit, entry)
                ?: return Limit(classLimit(entryClass), "class ${entryClass.label()}", null)
            val reach = unit != null && found == reachLateLimit(entry.id, unit.id, event.timed)
            val owner = if (unit != null && (reach || unit.lateLimits.any { it === found })) "unit ${unit.id}" else "entry ${entry.id}"
            return Limit(found.minutes, owner, found.reason)
        }

        /** The most minutes late each class allows (spec §5). */
        fun classLimit(entryClass: EntryClass): Int = when (entryClass) {
            EntryClass.A -> 1
            EntryClass.B -> 2
            EntryClass.C -> 1
            EntryClass.D_AUTHORITY, EntryClass.D_NONE -> 3
        }

        /**
         * A printed time as an instant: [minutes] on the table's [clock] (the row's `clock`, else
         * its civil [zone]), on whichever of the day before, the row's own [date] and the day after
         * puts the event in its civil window at the place:
         *
         * - a Fajr, end of eating or imsak from noon the day before to noon (DUM RT's Kazan prints
         *   23:54 on its 5 May 2026 row: the evening before, where the sun only just reaches 18°);
         * - sunrise, Dhuhr and Asr on the date itself;
         * - Maghrib and Isha from noon to noon the next day (an Isha printed 00:19 or 24:19).
         *
         * A table printed in UTC far from its place (Singapore's Fajr at 21:58 the day before) is
         * read the same way.
         */
        fun officialInstant(date: LocalDate, minutes: Int, event: Event, clock: TimeZone, zone: TimeZone = clock): Instant {
            val base = date.plus(minutes / (24 * 60), DateTimeUnit.DAY)
            val time = LocalTime((minutes % (24 * 60)) / 60, minutes % 60)
            val noon = LocalTime(12, 0)
            val (from, to) = when (event) {
                Event.FAJR, Event.END_OF_EATING, Event.IMSAK -> LocalDateTime(date.plus(-1, DateTimeUnit.DAY), noon) to LocalDateTime(date, noon)
                Event.SUNRISE, Event.DHUHR, Event.ASR_STANDARD, Event.ASR_HANAFI ->
                    LocalDateTime(date, LocalTime(0, 0)) to LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0))
                Event.MAGHRIB, Event.ISHA -> LocalDateTime(date, noon) to LocalDateTime(date.plus(1, DateTimeUnit.DAY), noon)
            }
            val candidates = listOf(0, -1, 1).map { LocalDateTime(base.plus(it, DateTimeUnit.DAY), time).toInstant(clock) }
            return candidates.firstOrNull { it.toLocalDateTime(zone).let { civil -> civil >= from && civil < to } } ?: candidates.first()
        }

        /** The prayer a day may declare [this] event not followed for (`PrayerDay.notFollowed`); ends never are. */
        val Event.declarable: Prayer?
            get() = when (this) {
                Event.FAJR -> Prayer.FAJR
                Event.SUNRISE -> Prayer.SUNRISE
                Event.DHUHR -> Prayer.DHUHR
                Event.ASR_STANDARD, Event.ASR_HANAFI -> Prayer.ASR
                Event.MAGHRIB -> Prayer.MAGHRIB
                Event.ISHA -> Prayer.ISHA
                Event.END_OF_EATING, Event.IMSAK -> null
            }

        /** What the day shows for [event]; the day's Asr is its entry's [school], asrOther the other. */
        fun shown(day: PrayerDay, event: Event, school: AsrSchool): Instant? = when (event) {
            Event.FAJR -> day.fajr
            Event.SUNRISE -> day.sunrise
            Event.DHUHR -> day.dhuhr
            Event.ASR_STANDARD -> if (school == AsrSchool.STANDARD) day.asr else day.asrOther
            Event.ASR_HANAFI -> if (school == AsrSchool.HANAFI) day.asr else day.asrOther
            Event.MAGHRIB -> day.maghrib
            Event.ISHA -> day.isha
            Event.END_OF_EATING -> day.endOfEating
            Event.IMSAK -> day.imsak
        }
    }
}

/** A late limit, where it comes from ("unit kazan", "entry sg.muis", "class A") and its reason. */
data class Limit(val minutes: Int, val source: String, val reason: String?) {
    val isException: Boolean get() = reason != null
}

/** An authority unit a row resolved to. */
data class UnitKey(val id: String, val name: String)

/** One compared cell: what the engine shows against what the table prints, and the limit it is held to. */
class Observation(
    val date: LocalDate,
    val event: Event,
    val shown: Instant,
    val official: Instant,
    val entryClass: EntryClass,
    val row: GateRow,
    val limit: Limit,
    val unit: UnitKey? = null,
    /** Held out of the fit (ruling R214, [Counted.heldOut]): a test row's cell no fit row reads from the same table. */
    val heldOut: Boolean = row.split == Split.TEST,
    /** Read again at a row that does not count the table's cells of this event (ruling R214, [Counted.counts]). */
    val replay: Boolean = false,
    /** Where the cell stands ([Counted.scope]): a held-out cell two captures print is tallied once there. */
    val scope: String = "",
)

/** One event's tally for one entry. [late] counts minutes on the safe side: 0, 1, 2, 3 or more. */
class EventStats {
    var checked = 0
    var early = 0
    var lateEnd = 0
    val late = IntArray(4)
    var worst = 0
    var over = 0
    var absent = 0

    /**
     * The days a cell of this event was compared on, as epoch days (ruling R148: a Ramadan is proven
     * event by event, so the stamp writes each event's own runs; a bit a day, since an entry of many
     * units holds one of these per unit and event).
     */
    private val days = java.util.BitSet()

    /** Those days, in order. */
    val dates: List<LocalDate> get() = days.stream().mapToObj { LocalDate.fromEpochDays(it) }.toList()

    internal fun day(date: LocalDate) {
        val epochDay = date.toEpochDays()
        require(epochDay in 0..Int.MAX_VALUE) { "a day before 1970: $date" }
        days.set(epochDay.toInt())
    }

    /** Cells the day declared not followed (ruling R82): not checked, and the dates they fell on. */
    var declared = 0
    val declaredDates = sortedSetOf<LocalDate>()
    /** The limits its cells were held to, by where each came from and its minutes. */
    val applied = sortedMapOf<String, Limit>()

    /** The minutes of those limits. */
    val limits: Set<Int> get() = applied.values.map { it.minutes }.toSortedSet()
    /** A few early starts and late ends, and a few days over the limit, for the failure message. */
    val broken = mutableListOf<String>()
    val overLimit = mutableListOf<String>()

    /** The most minutes an early start or a late end reached, and every date one fell on (the monitor's figures). */
    var worstEarly = 0
    val brokenDates = sortedSetOf<LocalDate>()

    val exact: Double get() = if (checked == 0) 0.0 else late[0].toDouble() / checked
}

/**
 * Ruling R185: [entry]'s standing demotions among a manifest's `@demoted` lines, one for the entry
 * as a whole or for each unit (the earliest, where several lines say the same), the whole entry
 * first; a unit's line under a line for the whole entry says nothing more and is left out.
 */
fun standing(demotions: List<Demotion>, entry: String): List<Demotion> {
    val own = demotions.filter { it.entry == entry }.sortedWith(compareBy({ it.since }, { it.source }, { it.line }))
    val whole = own.firstOrNull { it.unit == null }
    if (whole != null) return listOf(whole)
    return own.distinctBy { it.unit }.sortedBy { it.unit }
}

/** Everything the gate found for one registry entry. */
class EntryStats(val entry: RegistryEntry) {
    val rows = mutableListOf<String>()
    val places = LinkedHashSet<GeoPoint>()
    val fingerprints = sortedSetOf<String>()
    val classes = sortedSetOf<EntryClass>()
    val events = sortedMapOf<Event, EventStats>()

    /** The entry's standing `@demoted` lines ([standing], ruling R185); empty on most. */
    var demoted: List<Demotion> = emptyList()

    /** The same tally over the held-out cells alone (ruling R214: a test row's, of a table no fit row reads the event from). */
    val heldOut = sortedMapOf<Event, EventStats>()
    var unreadable = 0
    var duplicates = 0
    /**
     * Each place-day checked, once: its group's index (below) in the high half, its epoch day in the low. Every row
     * reading a start adds its days here, a replay too (ruling R214): the days the gate compared, not the figure shown.
     */
    private val placeDays = HashSet<Long>()
    private val groups = HashMap<String, Int>()

    /**
     * Ruling R214, the figures a reader is shown: the place-days of the rows that count their tables' days
     * ([Counted.isOwn]), each once, keyed as [placeDays]; those of them a fit table holds ([fitDays], so a day two tables
     * print is held out only when neither was fitted, in whatever order the rows are read); the points they were read
     * at ([tablePlaces]); and how many of each fall in Ramadan.
     */
    private val tableDays = HashSet<Long>()
    private val fitDays = HashSet<Long>()
    val tablePlaces = LinkedHashSet<GeoPoint>()

    /** Of [tableDays], those no entry before this one counts too ([AcrossEntries.shared]). */
    private val unsharedDays = HashSet<Long>()

    /** The entry's own place-days another entry counts as well: the checks page's total leaves them out here. */
    val sharedDayCount: Int get() = tableDays.size - unsharedDays.size

    /** The published tables counted at this entry, each once across the entries ([AcrossEntries.tables]). */
    var tables = 0
    private var fitRamadanDays = 0

    /** The published tables' own place-days (ruling R214): the stamp's `placeDays`, what About and the site show. */
    val tableDayCount: Int get() = tableDays.size

    /** Place-days checked at a replay alone (ruling R214): in the gate as checks, in no figure shown. */
    val replayDayCount: Int get() = placeDays.size - tableDays.size

    /** The published tables' own place-days in Ramadan (Umm al-Qura's dates). */
    var ramadanDays = 0
        private set

    /** Of [tableDayCount], the days no fitted table holds: the held-out days. */
    val testDays: Int get() = tableDays.size - fitDays.size
    val testRamadanDays: Int get() = ramadanDays - fitRamadanDays
    var outOfOrder = 0
        private set
    val outOfOrderSamples = mutableListOf<String>()

    /**
     * Ruling R302 (the owner's (a′) of 17 Nov 2026): the place-days, own or promise-only, whose Isha shows no end on a
     * night the real sun at the row's point makes too short for one ([DayPipeline.nightTooShortForIsha], recomputed by the
     * gate, never read off the day), and their dates. Declared, never out of order: no day in order fits such a night.
     * Only for an entry the ruling names ([SHORT_NIGHT_ENTRIES]); any other entry's such day is out of order as before.
     */
    var shortNights = 0
        private set
    val shortNightDates = sortedSetOf<LocalDate>()

    /** Whether the owner's R302 declaration covers this entry ([SHORT_NIGHT_ENTRIES]). */
    private val shortNightsRuled = entry.id in SHORT_NIGHT_ENTRIES

    private fun declareShortNight(date: LocalDate) {
        shortNights++
        shortNightDates += date
    }

    /**
     * Cautious entries, ruling R156: by member, the Maghrib cells its printed time was not the bar for (the
     * far-north guard, [MaghribBar]), with the shown Maghrib's gap to that printed time.
     */
    val maghribGuards = sortedMapOf<String, MaghribGuard>()

    /** Cautious Maghrib cells no member's time checked (every member held there guarded, none at its sun), and their dates. */
    var maghribGuardUnchecked = 0
        private set
    val maghribGuardUncheckedDates = sortedSetOf<LocalDate>()

    /**
     * Cautious entries, ruling R158: the entry's own place-days that show no Maghrib end, as no end in play is after the
     * Maghrib shown (a member's Isha has begun by then), and their dates. Recorded, never broken.
     */
    var maghribNoEnd = 0
        private set
    val maghribNoEndDates = sortedSetOf<LocalDate>()
    var first: LocalDate? = null
        private set
    var last: LocalDate? = null
        private set

    val placeDayCount: Int get() = placeDays.size

    /** Place-days of promise-only rows alone ([GateRow.promiseOnly], [promiseDay]): checked for the promise, in no stamp. */
    private val promiseDays = HashSet<Long>()
    val promisePlaceDayCount: Int get() = promiseDays.size

    /** Every date checked, whatever the point (a one-row manifest's coverage; the monitor names the holes). */
    val dates: Set<LocalDate> get() = placeDays.mapTo(sortedSetOf()) { LocalDate.fromEpochDays((it and 0xFFFFFFFFL).toInt()) }

    /** Early starts, late ends, cells over the limit and days out of order: 0 when the entry is green. */
    val broken: Int get() = events.values.sumOf { it.early + it.lateEnd + it.over } + outOfOrder

    /** Early starts, late ends and days out of order: the promise itself, never excused. */
    val neverEarlyBroken: Int get() = events.values.sumOf { it.early + it.lateEnd } + outOfOrder

    /** Cells over the late limit. */
    val overLimit: Int get() = events.values.sumOf { it.over }

    /** Ruling R156: [member]'s printed Maghrib [o] was read as its Maghrib from the sun ([atSun]) or not at all. */
    internal fun maghribGuard(member: String, o: Observation, atSun: Boolean) {
        maghribGuards.getOrPut(member) { MaghribGuard() }.add(o.date, (o.shown - o.official).inWholeMinutes.toInt(), atSun)
    }

    /** Ruling R156: a cautious Maghrib cell on [o]'s date that no member's time checked. */
    internal fun maghribGuardUnchecked(o: Observation) {
        maghribGuardUnchecked++
        maghribGuardUncheckedDates += o.date
    }

    fun event(event: Event): EventStats = events.getOrPut(event) { EventStats() }

    /** Per authority unit (rows outside every unit are not listed): its place-days and tally. */
    val units = sortedMapOf<String, UnitStats>()

    /**
     * A cautious entry's members (ruling R115): each member's checked dates by the point its rows
     * were read at ([pointKey]), so a page can hold every day some member's table was not checked
     * on at that place.
     */
    val memberDays = sortedMapOf<String, SortedMap<String, SortedSet<LocalDate>>>()

    /**
     * A cautious entry's excused member days (ruling R117): by member, by point ([pointKey]) and by
     * why ([ExcuseKind]), the days a gate file's `@excuse` lines leave out of the member's rows there.
     */
    val memberExcused = sortedMapOf<String, SortedMap<String, SortedMap<ExcuseKind, SortedSet<LocalDate>>>>()

    internal fun excuse(member: String, point: String, kind: ExcuseKind, days: ClosedRange<LocalDate>) {
        val into = memberExcused.getOrPut(member) { sortedMapOf() }.getOrPut(point) { sortedMapOf() }.getOrPut(kind) { sortedSetOf() }
        var date = days.start
        while (date <= days.endInclusive) {
            into += date
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }

    /** The limits [event] was held to: "1", or "1/3" where its rows' units or classes differ. */
    fun effectiveLimit(event: Event): String = events[event]?.limits.orEmpty().joinToString("/")

    internal fun placeDay(
        group: String,
        date: LocalDate,
        split: Split,
        ramadan: Boolean,
        day: PrayerDay,
        unit: UnitKey?,
        member: String? = null,
        point: GeoPoint? = null,
        own: Boolean = true,
        heldOut: Boolean = split == Split.TEST,
        shared: Boolean = false,
        shortNight: () -> Boolean = { false },
    ) {
        if (unit != null) units.getOrPut(unit.id) { UnitStats(unit.name) }.placeDays += date
        // Recorded before the place-day check: a second member's table at the same point and date is
        // the same place-day, but its own checked day all the same.
        if (member != null && point != null) {
            memberDays.getOrPut(member) { sortedMapOf() }.getOrPut(pointKey(point)) { sortedSetOf() } += date
        }
        val key = (groups.getOrPut(group) { groups.size }.toLong() shl 32) or (date.toEpochDays().toLong() and 0xFFFFFFFFL)
        // Ruling R214: the day counts in the figures shown only at the row that counts its table's days, and is held
        // out only while no fitted table holds the same place-day.
        if (own) {
            if (point != null) tablePlaces += point
            if (tableDays.add(key) && ramadan) ramadanDays++
            if (!heldOut && fitDays.add(key) && ramadan) fitRamadanDays++
            if (!shared) unsharedDays += key
        }
        if (!placeDays.add(key)) return
        promiseDays.remove(key)
        if (first == null || date < first!!) first = date
        if (last == null || date > last!!) last = date
        if (day.repaired || Prayer.ISHA !in day.ends) {
            if (!day.repaired && shortNightsRuled && shortNight()) {
                declareShortNight(date)
            } else {
                outOfOrder++
                if (outOfOrderSamples.size < SAMPLES) {
                    outOfOrderSamples += "$date: " + (if (day.repaired) "repaired into order" else "Isha not before the next end of eating") + " ($group)"
                }
            }
        }
        // Ruling R158: a cautious day whose Maghrib no end in play follows shows none.
        if (entry.members.isNotEmpty() && Prayer.MAGHRIB !in day.ends) {
            maghribNoEnd++
            maghribNoEndDates += date
        }
    }

    /** By row, the days an ends-only row read ([endsOnlyDay]): its group's index and epoch day, as [placeDays]. */
    private val endsOnlyDays = LinkedHashMap<String, MutableList<Long>>()

    /**
     * A day of a row that reads ends alone (a printed imsak, an end of eating): its ends are checked, but the day is no
     * place-day of its own (nor its unit's, nor held out), since none of its starts was read. [endsOnlyUnheld] names the
     * days no row reading a start holds at the same point.
     */
    internal fun endsOnlyDay(group: String, date: LocalDate, unit: UnitKey?, where: String) {
        if (unit != null) units.getOrPut(unit.id) { UnitStats(unit.name) }
        val key = (groups.getOrPut(group) { groups.size }.toLong() shl 32) or (date.toEpochDays().toLong() and 0xFFFFFFFFL)
        endsOnlyDays.getOrPut(where) { mutableListOf() } += key
    }

    /** By row, the dates an ends-only row read that no place-day holds: a mistake in the gate file. */
    internal fun endsOnlyUnheld(): Map<String, List<LocalDate>> = endsOnlyDays.mapNotNull { (where, keys) ->
        val unheld = keys.filter { it !in placeDays }.map { LocalDate.fromEpochDays((it and 0xFFFFFFFFL).toInt()) }.distinct().sorted()
        unheld.takeIf { it.isNotEmpty() }?.let { where to it }
    }.toMap()

    /**
     * A place-day of a promise-only row ([GateRow.promiseOnly]): counted apart from the entry's own place-days (its
     * stamp's), where no row of the entry's own files holds it, and a day out of order breaks the promise as anywhere.
     */
    internal fun promiseDay(group: String, date: LocalDate, day: PrayerDay, shortNight: () -> Boolean = { false }) {
        val key = (groups.getOrPut(group) { groups.size }.toLong() shl 32) or (date.toEpochDays().toLong() and 0xFFFFFFFFL)
        if (key in placeDays || !promiseDays.add(key)) return
        if (!day.repaired && Prayer.ISHA !in day.ends && shortNightsRuled && shortNight()) {
            declareShortNight(date)
        } else if (day.repaired || Prayer.ISHA !in day.ends) {
            outOfOrder++
            if (outOfOrderSamples.size < SAMPLES) {
                outOfOrderSamples += "$date: " + (if (day.repaired) "repaired into order" else "Isha not before the next end of eating") + " ($group, promise-only)"
            }
        }
    }

    /** A cell of [event] on [date] the day declared not followed: counted, never checked. */
    /** Whether the entry showed an imsak on a day checked (ruling R246): at some point of it, and by unit in [UnitStats.showsImsak]. */
    var showsImsak = false
        private set

    internal fun imsakShown(unit: UnitKey?) {
        showsImsak = true
        if (unit != null) units.getOrPut(unit.id) { UnitStats(unit.name) }.showsImsak = true
    }

    internal fun declare(event: Event, date: LocalDate, heldOut: Boolean, unit: UnitKey?) {
        fun EventStats.add() {
            declared++
            declaredDates += date
        }
        event(event).add()
        if (heldOut) this.heldOut.getOrPut(event) { EventStats() }.add()
        unit?.let { units.getValue(it.id).events.getOrPut(event) { EventStats() }.add() }
    }

    /**
     * Ruling R294: a cell the day declared not followed whose start is shown before the printed one (a declared Isha,
     * which no declaration may move earlier): early as any start, in the entry's tally and its unit's, never checked.
     */
    internal fun declaredEarly(o: Observation) {
        val minutes = (o.official - o.shown).inWholeSeconds / 60
        fun EventStats.early() {
            early++
            worstEarly = maxOf(worstEarly, minutes.toInt())
            brokenDates += o.date
            sample(broken, o, "$minutes min early (a cell declared not followed)")
        }
        event(o.event).early()
        o.unit?.let { units.getValue(it.id).events.getOrPut(o.event) { EventStats() }.early() }
    }

    internal fun account(o: Observation) {
        // A promise-only row's class (a place beyond the entry's own area) is none of the entry's proof.
        if (!o.row.promiseOnly) classes += o.entryClass
        tally(event(o.event), o)
        // Ruling R214: the held-out tally takes each held-out cell once, where its table is counted, and once however
        // many captures of the table print the day; a later capture of the same day still gives its breaks and its worst.
        if (o.heldOut && !o.replay) tally(heldOut.getOrPut(o.event) { EventStats() }, o, again = !heldOutSeen(o))
        o.unit?.let { unit ->
            val u = units.getValue(unit.id)
            tally(u.events.getOrPut(o.event) { EventStats() }, o)
            // The unit's own held-out days by event (ruling R214), at whichever of its rows read them: the Ramadan guard's reading.
            if (o.heldOut && !o.row.promiseOnly) u.heldOut.getOrPut(o.event) { java.util.BitSet() }.set(o.date.toEpochDays().toInt())
        }
    }

    /** By [Observation.scope], by event, the days a held-out cell was tallied on, as epoch days. */
    private val heldOutCells = HashMap<String, Array<java.util.BitSet?>>()

    /** Whether [o]'s held-out cell is tallied for the first time: the same place, event and day in no capture before. */
    private fun heldOutSeen(o: Observation): Boolean {
        val byEvent = heldOutCells.getOrPut(o.scope) { arrayOfNulls(Event.entries.size) }
        val days = byEvent[o.event.ordinal] ?: java.util.BitSet().also { byEvent[o.event.ordinal] = it }
        val day = o.date.toEpochDays().toInt()
        return !days.get(day).also { days.set(day) }
    }

    /** [again]: a cell tallied before from another capture (the held-out tally alone): its breaks and its worst, no count. */
    private fun tally(e: EventStats, o: Observation, again: Boolean = false) {
        // A promise-only row ([GateRow.promiseOnly]) is held to the promise alone: an early start or a late end counts as
        // anywhere; no cell checked, no limit applied and no lateness recorded in the entry's tally (its stamp's).
        val promiseOnly = o.row.promiseOnly || again
        if (!promiseOnly) {
            e.checked++
            e.day(o.date)
            e.applied["${o.limit.source} ${o.limit.minutes}"] = o.limit
        }
        val seconds = (o.shown - o.official).inWholeSeconds
        // Minutes on the safe side: late for a start, early for an end. Both are whole minutes.
        val safe = if (o.event.isStart) seconds else -seconds
        if (safe < 0) {
            if (o.event.isStart) e.early++ else e.lateEnd++
            e.worstEarly = maxOf(e.worstEarly, (-safe / 60).toInt())
            e.brokenDates += o.date
            sample(e.broken, o, if (o.event.isStart) "${-safe / 60} min early" else "${-safe / 60} min late (an end)")
            return
        }
        if (o.row.promiseOnly) return
        val minutes = (safe / 60).toInt()
        if (!again) e.late[minOf(minutes, 3)]++
        if (minutes > e.worst) e.worst = minutes
        if (minutes > o.limit.minutes) {
            e.over++
            sample(e.overLimit, o, "$minutes min ${if (o.event.isStart) "late" else "early (an end)"}, over the ${o.limit.source} limit ${o.limit.minutes}")
        }
    }

    private fun sample(into: MutableList<String>, o: Observation, what: String) {
        if (into.size < SAMPLES) into += "${o.date} ${o.event.key}: $what (${o.row.path})"
    }

    companion object {
        private const val SAMPLES = 5

        /**
         * Ruling R302: the entries whose days without an Isha end on a night too short for one are declared. The owner
         * ruled on Norway's IRN (the far-north entry's question (1), 17 Nov 2026), and by ruling R314 (17 Nov 2026, the
         * rulings entry's For the owner (1), option (a)) on Norway's Cautious times (no.cautious, IRN a member), which meets
         * the same nights at Tromsø's reach and shows no Isha end on them too; its About says those nights in its own
         * sentence within Tromsø's reach (beyond it, Alta to Kirkenes and Svalbard, no sentence says them; no gate row meets
         * such a night today). Any other entry's such day is out of order.
         */
        val SHORT_NIGHT_ENTRIES: Set<String> = setOf("no.irn", "no.cautious")
    }
}

/** One authority unit's share of an entry: its place-days and its tally (each event with its limits). */
class UnitStats(val name: String) {
    val placeDays = sortedSetOf<LocalDate>()
    val events = sortedMapOf<Event, EventStats>()

    /** Whether the unit showed an imsak on a day checked (ruling R246). */
    var showsImsak = false

    /** By event, the days a held-out cell of the unit was compared on, as epoch days (ruling R214, [Counted.heldOut]). */
    val heldOut = sortedMapOf<Event, java.util.BitSet>()

    /** Those days by event key, in order. */
    val heldOutDates: Map<String, List<LocalDate>>
        get() = heldOut.entries.associate { (event, days) -> event.key to days.stream().mapToObj { LocalDate.fromEpochDays(it) }.toList() }

    val broken: Int get() = events.values.sumOf { it.early + it.lateEnd + it.over }
}

/**
 * The exceptions [events] were held to (rulings R37, R41): each limit that is not a class's, with
 * the events it covered.
 */
fun exceptionsIn(events: Map<Event, EventStats>): Map<Limit, List<Event>> {
    val found = LinkedHashMap<Limit, MutableList<Event>>()
    for ((event, e) in events) for (limit in e.applied.values) if (limit.isException) found.getOrPut(limit) { mutableListOf() } += event
    return found
}

/** "dhuhr 14 from unit kazan, …": the exceptions in [events], for the report. */
private fun describeExceptions(events: Map<Event, EventStats>): String =
    exceptionsIn(events).entries.joinToString(", ") { (limit, evs) -> "${evs.joinToString("/") { it.key }} ${limit.minutes} from ${limit.source}" }

/**
 * Ruling R156, the far-north guard as the gate reads a cautious member's printed Maghrib on one date (ruling R155:
 * a Maghrib a member's own far-north or winter rule sets, rather than the sun, counts only when that member is
 * the most followed):
 *
 * - [Printed]: as printed, like any start: its Maghrib counts (it follows the sun there, or it is the most followed);
 * - [Sun]: its own day-length rule held its Maghrib later than the sun gives it ([PrayerDay.maghribHeld], Diyanet's
 *   five-hour winter day), and it is not the most followed: the shown Maghrib is checked against [at], its Maghrib
 *   from the sun as the engine gives it ([PrayerDay.maghribBySun]; no table prints it);
 * - [None]: a rule sets its Maghrib outright (a polar day's borrowed time, a clock rule's estimate) or the real
 *   sun does not set at the point, and it is not the most followed: no time of its own to check against.
 */
sealed interface MaghribBar {
    data object Printed : MaghribBar

    data class Sun(val at: Instant) : MaghribBar

    data object None : MaghribBar
}

/**
 * [member]'s [MaghribBar] on its date (ruling R156): [mostFollowed] when it is the cautious entry's most-followed
 * member, [sunSets] when the real sun sets at the point that day ([DayPipeline.realSunset]), as
 * [world.taqwa.app.prayer.engine.day.Cautious.combine] counts each member.
 */
fun maghribBar(member: PrayerDay, mostFollowed: Boolean, sunSets: Boolean): MaghribBar {
    val bySun = member.maghribBySun
    return when {
        mostFollowed -> MaghribBar.Printed
        sunSets && bySun == member.maghrib -> MaghribBar.Printed
        sunSets && member.maghribHeld && bySun != null -> MaghribBar.Sun(bySun)
        else -> MaghribBar.None
    }
}

/**
 * Ruling R156: one member's Maghrib cells the far-north guard read otherwise than as printed ([MaghribBar.Sun] or
 * [MaghribBar.None]): how many, on which dates, how many checked against its Maghrib from the sun, and the shown
 * Maghrib less its printed one, in whole minutes, least and most (below zero: shown before its printed time).
 */
class MaghribGuard {
    var days = 0
        private set
    val dates = sortedSetOf<LocalDate>()
    var atSun = 0
        private set
    var least = Int.MAX_VALUE
        private set
    var most = Int.MIN_VALUE
        private set

    internal fun add(date: LocalDate, gap: Int, atSun: Boolean) {
        days++
        dates += date
        if (atSun) this.atSun++
        least = minOf(least, gap)
        most = maxOf(most, gap)
    }
}

/** The gate's result over one manifest. */
class GateResult(
    val roots: OfficialRoots,
    val checkedRows: Int,
    val skippedRows: List<GateRow>,
    val entries: Map<String, EntryStats>,
) {
    val placeDays: Int get() = entries.values.sumOf { it.placeDayCount }

    /** The published tables' own place-days (ruling R214): what the stamps count, a replay's days left out. */
    val tableDays: Int get() = entries.values.sumOf { it.tableDayCount }

    /** Place-days checked by promise-only rows alone ([GateRow.promiseOnly]): the envelope's, in no entry's stamp. */
    val promisePlaceDays: Int get() = entries.values.sumOf { it.promisePlaceDayCount }

    /**
     * Every broken promise: early starts, late ends and days out of order ([neverEarly]), then
     * lateness over the limit ([overLimit]).
     */
    fun violations(): List<String> = entries.values.flatMap { s -> neverEarly(s) + overLimit(s) }

    /** The promise itself, for one entry: early starts, late ends, days out of order (never excused). */
    fun neverEarly(s: EntryStats): List<String> = s.events.flatMap { (event, e) ->
        listOfNotNull(
            ("${s.entry.id} ${event.key}: ${e.early} early" + lines(e.broken)).takeIf { e.early > 0 },
            ("${s.entry.id} ${event.key}: ${e.lateEnd} late ends" + lines(e.broken)).takeIf { e.lateEnd > 0 },
        )
    } + listOfNotNull(
        "${s.entry.id}: ${s.outOfOrder} days out of order${lines(s.outOfOrderSamples)}".takeIf { s.outOfOrder > 0 },
    )

    /** Lateness over the recorded limit, for one entry. */
    fun overLimit(s: EntryStats): List<String> = s.events.mapNotNull { (event, e) ->
        ("${s.entry.id} ${event.key}: ${e.over} over the late limit" + lines(e.overLimit)).takeIf { e.over > 0 }
    }

    /**
     * Ruling R156, for one entry: each member whose printed Maghrib the far-north guard read otherwise, and the cells
     * no member's time checked. Recorded, never broken.
     */
    fun guarded(s: EntryStats): List<String> = s.maghribGuards.map { (member, g) ->
        "${s.entry.id} maghrib: $member's printed Maghrib not its own from the sun on ${g.days} days " +
            "(${dateRanges(g.dates).joinToString(", ")}; its far-north or winter rule, or no real sunset there): checked " +
            "against its Maghrib from the sun on ${g.atSun}, shown ${g.least}..${g.most} min after its printed time (ruling R156)"
    } + listOfNotNull(
        ("${s.entry.id} maghrib: ${s.maghribGuardUnchecked} cells no member's time checks " +
            "(${dateRanges(s.maghribGuardUncheckedDates).joinToString(", ")}; ruling R156)").takeIf { s.maghribGuardUnchecked > 0 },
    )

    /** Ruling R158, for one entry: its place-days that show no Maghrib end, and their dates. Recorded, never broken. */
    fun maghribNoEnd(s: EntryStats): String? =
        ("${s.entry.id} maghrib: no end shown on ${s.maghribNoEnd} place-days " +
            "(${dateRanges(s.maghribNoEndDates).joinToString(", ")}; ruling R158: no end in play after the Maghrib shown)")
            .takeIf { s.maghribNoEnd > 0 }

    private fun lines(samples: List<String>) = samples.joinToString("") { "\n    $it" }

    private fun StringBuilder.table(s: EntryStats, events: Map<Event, EventStats>) {
        append(
            String.format(
                Locale.ROOT, "  %-12s %6s %6s %8s %6s %6s %6s %6s %7s %6s %5s %5s\n",
                "event", "days", "early", "late-end", "+0", "+1", "+2", "+3+", "exact", "worst", "limit", "over",
            ),
        )
        for ((event, e) in events) {
            append(
                String.format(
                    Locale.ROOT,
                    "  %-12s %6d %6s %8s %6d %6d %6d %6d %6.1f%% %6d %5s %5d\n",
                    event.key, e.checked, if (event.isStart) e.early.toString() else "-",
                    if (event.isStart) "-" else e.lateEnd.toString(), e.late[0], e.late[1], e.late[2], e.late[3],
                    100.0 * e.exact, e.worst, s.effectiveLimit(event), e.over,
                ),
            )
        }
    }

    /** A table per entry and event: days, early, late ends, 0/1/2/3+ minutes late, exact share. */
    fun report(): String = buildString {
        append("Gate over ${roots.archive.path}: $checkedRows rows checked ($placeDays place-days")
        // Ruling R214: the days checked, and of them the published tables' own (what the stamps count) and the replays.
        if (placeDays > tableDays) append(", $tableDays of them the published tables' own and ${placeDays - tableDays} the same tables replayed at other points")
        append(")")
        if (promisePlaceDays > 0) append(" and $promisePlaceDays more place-days held to the promise alone (the envelope's rows and the hand-kept promise files')")
        append(if (roots.held) "\n" else ", ${skippedRows.size} rows skipped (no archive there)\n")
        for (s in entries.values) {
            append('\n')
            val classes = s.classes.joinToString("/") { it.label() }
            val classLimits = s.events.values.flatMap { it.applied.values }.filter { !it.isException }.map { it.minutes }.distinct().sorted()
            append("${s.entry.id} — class $classes, late limit")
            if (classLimits.isNotEmpty()) append(" ${classLimits.joinToString("/")}")
            if (exceptionsIn(s.events).isNotEmpty()) {
                append(if (classLimits.isEmpty()) " by exception: ${describeExceptions(s.events)}" else " (by exception: ${describeExceptions(s.events)})")
            }
            append(" · ${s.tablePlaces.size} place${if (s.tablePlaces.size == 1) "" else "s"} · ${s.first}..${s.last}")
            append(" · ${s.tableDayCount} place-days (${s.testDays} held out, ${s.ramadanDays} in Ramadan)")
            if (s.replayDayCount > 0) append(" and ${s.replayDayCount} more replayed at ${s.places.size - s.tablePlaces.size} other points")
            append(" · ${s.rows.size} rows\n")
            table(s, s.events)
            for ((event, e) in s.events) if (e.absent > 0) append("    ${event.key}: the engine shows none on ${e.absent} days\n")
            for ((event, e) in s.events) {
                if (e.declared > 0) {
                    append("    ${event.key}: ${e.declared} cells declared not followed (${dateRanges(e.declaredDates).joinToString(", ")})\n")
                }
            }
            if (s.heldOut.isNotEmpty() && s.testDays < s.tableDayCount) {
                append("  held out only (${s.testDays} place-days, ${s.testRamadanDays} in Ramadan):\n")
                table(s, s.heldOut)
            }
            for ((x, evs) in exceptionsIn(s.events)) {
                append("  exception: ${evs.joinToString("/") { it.key }} up to ${x.minutes} min from ${x.source} — ${x.reason}\n")
            }
            for ((id, u) in s.units) {
                val worst = u.events.values.maxOfOrNull { it.worst } ?: 0
                val limits = u.events.values.flatMap { it.limits }.distinct().sorted().joinToString("/")
                append("  unit $id (${u.name}): ${u.placeDays.size} place-days, limit $limits")
                if (exceptionsIn(u.events).isNotEmpty()) append(" (by exception: ${describeExceptions(u.events)})")
                append(", worst $worst, ${u.broken} broken\n")
            }
            if (s.unreadable > 0 || s.duplicates > 0) {
                append("  lines: ${s.unreadable} unreadable (skipped), ${s.duplicates} repeating a date (each checked)\n")
            }
            if (s.outOfOrder > 0) append("  out of order: ${s.outOfOrder} days\n")
            if (s.shortNights > 0) {
                append("  nights too short for Isha's end (declared, ruling R302): ${s.shortNights} days, ${dateRanges(s.shortNightDates).joinToString(", ")}\n")
            }
            for (line in guarded(s)) append("  ${line.removePrefix("${s.entry.id} ")}\n")
            maghribNoEnd(s)?.let { append("  ${it.removePrefix("${s.entry.id} ")}\n") }
            for ((member, points) in s.memberExcused) {
                for (kind in ExcuseKind.entries) {
                    val at = points.values.mapNotNull { it[kind] }
                    if (at.isEmpty()) continue
                    val dates = dateRanges(at.flatten()).joinToString(", ")
                    append("  excused for $member (${kind.words}, ruling R117): $dates, at ${at.size} point${if (at.size == 1) "" else "s"}\n")
                }
            }
        }
        val violations = violations()
        if (violations.isNotEmpty()) {
            append("\nBROKEN (${violations.size}):\n")
            violations.forEach { append("  $it\n") }
        }
    }
}

/** A point as the stamps key it: "lat,lon" as the gate file gives them (read back by `Stamp.memberPoints`). */
fun pointKey(point: GeoPoint): String = "${point.lat},${point.lon}"

/** [dates] as runs of consecutive days, "2026-04-24..2026-05-18", or a single date, in order. */
fun dateRanges(dates: Collection<LocalDate>): List<String> {
    val sorted = dates.toSortedSet().toList()
    val runs = mutableListOf<String>()
    var i = 0
    while (i < sorted.size) {
        var j = i
        while (j + 1 < sorted.size && sorted[j + 1] == sorted[j].plus(1, DateTimeUnit.DAY)) j++
        runs += if (i == j) sorted[i].toString() else "${sorted[i]}..${sorted[j]}"
        i = j + 1
    }
    return runs
}

internal fun EntryClass.label(): String = when (this) {
    EntryClass.A -> "A"
    EntryClass.B -> "B"
    EntryClass.C -> "C"
    EntryClass.D_AUTHORITY -> "D (authority)"
    EntryClass.D_NONE -> "D (none)"
}
