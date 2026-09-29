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
import world.taqwa.app.prayer.engine.day.Cautious
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
import world.taqwa.app.prayer.engine.registry.lateLimitFor
import java.io.File
import java.util.Locale
import java.util.SortedMap
import java.util.SortedSet
import kotlin.time.Duration
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
 * member's, early by its minutes before the earliest. Maghrib is the exception: where the members'
 * printed Maghribs that date spread beyond [Cautious.MAGHRIB_AGREEMENT], the engine caps it at the
 * most-followed member's, so it is checked against that member's printed time alone, and a spread
 * date without the most-followed member's table is unchecked, which breaks the gate. With a single
 * member's table held, the engine's own members' Maghribs decide whether the date is a spread one.
 * The cap's gap to each member's printed Maghrib is recorded (spec §3.6).
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
) {

    fun evaluate(manifest: GateManifest): GateResult {
        val problems = mutableListOf<String>()
        val stats = sortedMapOf<String, EntryStats>()
        val skipped = mutableListOf<GateRow>()
        var checked = 0
        val cells = LinkedHashMap<String, CautiousCell>()
        // Rows at the same point of the same entry (a year per file, a table in two files) share days.
        val computed = HashMap<String, Days>()

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
            val unit = setup.unit?.let { UnitKey(it.id, it.name) }
            s.rows += row.where
            s.unreadable += table.unreadable
            s.duplicates += table.duplicates
            s.fingerprints += Stamps.fingerprint(resolution)
            s.places += point
            val cautious = resolution.entryClass == EntryClass.C
            val mostFollowed = resolution.members.minByOrNull { it.shareRank }?.id
            val days = computed.getOrPut("${entry.id}|${row.unit}|${point.lat},${point.lon}|${row.zone}") { Days(resolution, zone) }
            // Days actually in Ramadan (Umm al-Qura's dates), not the day either side authorities are given.
            val ramadan = Registry.ramadanCalendar()

            for (official in table.days) {
                val day = days.day(official.date)
                // A place-day is the entry at one point on one date, however many tables hold it.
                val group = "${entry.id}@${pointKey(point)}"
                s.placeDay(group, official.date, row.split, ramadan.isRamadan(official.date), day, unit, row.member, point)
                row.columns.forEachIndexed { column, events ->
                    val minutes = official.minutes[column] ?: return@forEachIndexed
                    for (event in events) {
                        val at = officialInstant(official.date, minutes, event, setup.clock, zone)
                        val shown = shown(day, event, entry.school)
                        if (shown == null) {
                            s.event(event).absent++
                            continue
                        }
                        if (event.declarable in day.notFollowed) {
                            s.declare(event, official.date, row.split, unit)
                            continue
                        }
                        val observation = Observation(official.date, event, shown, at, resolution.entryClass, row, limits.getValue(event), unit)
                        if (cautious) {
                            cells.getOrPut("$group|${official.date}|${event.key}") {
                                CautiousCell(s, mostFollowed!!) { days.memberSpread(official.date) }
                            }.add(observation)
                        } else {
                            s.account(observation)
                        }
                    }
                }
            }
        }
        for (cell in cells.values) cell.account()
        if (problems.isNotEmpty()) throw GateError(problems)
        return GateResult(roots, checked, skipped, stats)
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
    private class Days(private val resolution: Resolution, private val zone: TimeZone) {
        private val unended = HashMap<LocalDate, PrayerDay>()

        private fun unended(date: LocalDate): PrayerDay =
            unended.getOrPut(date) { DayPipeline.unended(resolution, date, zone) }

        fun day(date: LocalDate): PrayerDay = Ends.withNextDay(unended(date), unended(date.plus(1, DateTimeUnit.DAY)))

        /** A cautious resolution's own members' Maghribs on [date]: how far apart the engine found them. */
        fun memberSpread(date: LocalDate): Duration =
            DayPipeline.members(resolution, date, zone).map { it.maghrib }.let { it.max() - it.min() }
    }

    /**
     * One event of a cautious place-day, with each member's printed time (ruling R38): a start is
     * measured against the latest member's, an end against the earliest's, and a Maghrib whose
     * members spread beyond the agreement against the most-followed member's alone. Where a single
     * member's table is held, nothing printed shows the spread, and where the most-followed member's
     * is not held, members that agree in print do not show the engine left Maghrib uncapped: then
     * the engine's own members' Maghribs ([engineSpread]) tell whether it capped too. A capped date
     * without the most-followed member's table is unchecked, never early against a member the cap
     * does not follow.
     */
    private class CautiousCell(
        private val stats: EntryStats,
        private val mostFollowed: String,
        private val engineSpread: () -> Duration,
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
            val printed = if (all.size >= 2) all.maxOf { it.official } - all.minOf { it.official } else null
            val capped = if (mostFollowed in byMember) {
                (printed ?: engineSpread()) > Cautious.MAGHRIB_AGREEMENT
            } else {
                // Without the most-followed member's table, members that agree in print do not show
                // the engine left Maghrib uncapped: its own members' spread may still have capped it.
                (printed != null && printed > Cautious.MAGHRIB_AGREEMENT) || engineSpread() > Cautious.MAGHRIB_AGREEMENT
            }
            for ((member, o) in byMember) stats.maghribGap(member, (o.shown - o.official).inWholeMinutes.toInt(), capped)
            when {
                !capped -> stats.account(all.maxBy { it.official })
                mostFollowed in byMember -> stats.account(byMember.getValue(mostFollowed))
                else -> stats.maghribUnchecked(first, mostFollowed)
            }
        }
    }

    companion object {
        /**
         * [row] made ready to check: the entry by id, the table's point (its own, else its unit's
         * reference point, else the method's fixed point), the entry resolved there (the unit logic
         * of the registry, checked against a named unit) with [change] applied to its method, and
         * the table read when the archive is held ([OfficialRoots]). Null, with the reason in
         * [problems], when any of it fails: a table the archive does not hold, or one that yields no
         * day (a column list that does not fit its lines), is a mistake in the gate file.
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
                val read = OfficialTable.from(format.read(file.readLines(), row.columns.size))
                if (read.days.isEmpty()) {
                    return null.also {
                        problems += "${row.where}: no day read from ${row.path} (${read.unreadable} unreadable lines for " +
                            "${row.columns.size} columns in format ${row.format})"
                    }
                }
                read
            }
            val resolution = Registry.resolveEntry(entry, Place(point.lat, point.lon, row.zone, entry.countries.firstOrNull() ?: ""))
            if (unit != null && resolution.unitName != unit.name) {
                return null.also { problems += "${row.where}: the point resolves to ${resolution.unitName ?: "no unit"}, not ${unit.name}" }
            }
            val changed = if (change == null) resolution else resolution.copy(method = resolution.method?.let(change))
            val resolved = unit ?: Units.of(entry.id)?.units?.firstOrNull { it.name == resolution.unitName && it.point == resolution.unitPoint }
            return Setup(entry, point, zone, changed, table, resolved, clock)
        }

        /**
         * [event]'s late limit on a row (rulings R37, R41): the `LateLimit` [lateLimitFor] finds for it
         * (the unit's own, else the entry's), else the class default. An exception covers only the
         * events it names.
         */
        fun lateLimit(event: Event, unit: AuthorityUnit?, entry: RegistryEntry, entryClass: EntryClass): Limit {
            val found = lateLimitFor(event.timed, unit, entry)
                ?: return Limit(classLimit(entryClass), "class ${entryClass.label()}", null)
            val owner = if (unit != null && unit.lateLimits.any { it === found }) "unit ${unit.id}" else "entry ${entry.id}"
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

/** Everything the gate found for one registry entry. */
class EntryStats(val entry: RegistryEntry) {
    val rows = mutableListOf<String>()
    val places = LinkedHashSet<GeoPoint>()
    val fingerprints = sortedSetOf<String>()
    val classes = sortedSetOf<EntryClass>()
    val events = sortedMapOf<Event, EventStats>()

    /** The same tally over the held-out (`split=test`) rows alone. */
    val heldOut = sortedMapOf<Event, EventStats>()
    var unreadable = 0
    var duplicates = 0
    private val placeDays = HashSet<String>()
    var ramadanDays = 0
        private set
    var testDays = 0
        private set
    var testRamadanDays = 0
        private set
    var outOfOrder = 0
        private set
    val outOfOrderSamples = mutableListOf<String>()

    /** Cautious entries: the Maghrib cap's gap to each member's printed Maghrib, and spread dates left unchecked. */
    val maghribGaps = sortedMapOf<String, MaghribGap>()
    var maghribUnchecked = 0
        private set
    val maghribUncheckedSamples = mutableListOf<String>()

    /** The most-followed member whose table was missing on the unchecked days (the monitor names it). */
    var maghribUncheckedMember: String? = null
        private set
    var first: LocalDate? = null
        private set
    var last: LocalDate? = null
        private set

    val placeDayCount: Int get() = placeDays.size

    /** Every date checked, whatever the point (a one-row manifest's coverage; the monitor names the holes). */
    val dates: Set<LocalDate> get() = placeDays.mapTo(sortedSetOf()) { LocalDate.parse(it.substringAfter('|')) }

    /**
     * Early starts, late ends, cells over the limit, days out of order and cautious Maghribs that
     * could not be checked: 0 when the entry is green.
     */
    val broken: Int get() = events.values.sumOf { it.early + it.lateEnd + it.over } + outOfOrder + maghribUnchecked

    /** Early starts, late ends and days out of order: the promise itself, never excused. */
    val neverEarlyBroken: Int get() = events.values.sumOf { it.early + it.lateEnd } + outOfOrder

    /** Cells over the late limit. */
    val overLimit: Int get() = events.values.sumOf { it.over }

    internal fun maghribGap(member: String, minutes: Int, capped: Boolean) {
        maghribGaps.getOrPut(member) { MaghribGap() }.add(minutes, capped)
    }

    internal fun maghribUnchecked(o: Observation, mostFollowed: String) {
        maghribUnchecked++
        maghribUncheckedMember = mostFollowed
        if (maghribUncheckedSamples.size < SAMPLES) {
            maghribUncheckedSamples += "${o.date}: members spread beyond the agreement and $mostFollowed's table is not held (${o.row.path})"
        }
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
    ) {
        if (unit != null) units.getOrPut(unit.id) { UnitStats(unit.name) }.placeDays += date
        // Recorded before the place-day check: a second member's table at the same point and date is
        // the same place-day, but its own checked day all the same.
        if (member != null && point != null) {
            memberDays.getOrPut(member) { sortedMapOf() }.getOrPut(pointKey(point)) { sortedSetOf() } += date
        }
        if (!placeDays.add("$group|$date")) return
        if (ramadan) ramadanDays++
        if (split == Split.TEST) testDays++
        if (split == Split.TEST && ramadan) testRamadanDays++
        if (first == null || date < first!!) first = date
        if (last == null || date > last!!) last = date
        if (day.repaired || Prayer.ISHA !in day.ends) {
            outOfOrder++
            if (outOfOrderSamples.size < SAMPLES) {
                outOfOrderSamples += "$date: " + (if (day.repaired) "repaired into order" else "Isha not before the next end of eating") + " ($group)"
            }
        }
    }

    /** A cell of [event] on [date] the day declared not followed: counted, never checked. */
    internal fun declare(event: Event, date: LocalDate, split: Split, unit: UnitKey?) {
        fun EventStats.add() {
            declared++
            declaredDates += date
        }
        event(event).add()
        if (split == Split.TEST) heldOut.getOrPut(event) { EventStats() }.add()
        unit?.let { units.getValue(it.id).events.getOrPut(event) { EventStats() }.add() }
    }

    internal fun account(o: Observation) {
        classes += o.entryClass
        tally(event(o.event), o)
        if (o.row.split == Split.TEST) tally(heldOut.getOrPut(o.event) { EventStats() }, o)
        o.unit?.let { unit -> tally(units.getValue(unit.id).events.getOrPut(o.event) { EventStats() }, o) }
    }

    private fun tally(e: EventStats, o: Observation) {
        e.checked++
        e.applied["${o.limit.source} ${o.limit.minutes}"] = o.limit
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
        val minutes = (safe / 60).toInt()
        e.late[minOf(minutes, 3)]++
        if (minutes > e.worst) e.worst = minutes
        if (minutes > o.limit.minutes) {
            e.over++
            sample(e.overLimit, o, "$minutes min ${if (o.event.isStart) "late" else "early (an end)"}, over the ${o.limit.source} limit ${o.limit.minutes}")
        }
    }

    private fun sample(into: MutableList<String>, o: Observation, what: String) {
        if (into.size < SAMPLES) into += "${o.date} ${o.event.key}: $what (${o.row.path})"
    }

    private companion object {
        const val SAMPLES = 5
    }
}

/** One authority unit's share of an entry: its place-days and its tally (each event with its limits). */
class UnitStats(val name: String) {
    val placeDays = sortedSetOf<LocalDate>()
    val events = sortedMapOf<Event, EventStats>()

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
 * The shown (capped) Maghrib less one member's printed Maghrib, in whole minutes: over how many
 * days, how many of them capped (the members spread beyond the agreement), and the least and most.
 */
class MaghribGap {
    var days = 0
        private set
    var capped = 0
        private set
    var least = Int.MAX_VALUE
        private set
    var most = Int.MIN_VALUE
        private set

    internal fun add(minutes: Int, isCapped: Boolean) {
        days++
        if (isCapped) capped++
        least = minOf(least, minutes)
        most = maxOf(most, minutes)
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

    /**
     * Every broken promise: early starts, late ends and days out of order ([neverEarly]), lateness
     * over the limit ([overLimit]) and cautious Maghribs left unchecked ([unchecked]), in that order.
     */
    fun violations(): List<String> = entries.values.flatMap { s -> neverEarly(s) + overLimit(s) + unchecked(s) }

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

    /** Capped Maghribs left unchecked (the most-followed member's table not held), for one entry. */
    fun unchecked(s: EntryStats): List<String> = listOfNotNull(
        "${s.entry.id} maghrib: ${s.maghribUnchecked} capped days unchecked${lines(s.maghribUncheckedSamples)}".takeIf { s.maghribUnchecked > 0 },
    )

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
        append("Gate over ${roots.archive.path}: $checkedRows rows checked ($placeDays place-days)")
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
            append(" · ${s.places.size} place${if (s.places.size == 1) "" else "s"} · ${s.first}..${s.last}")
            append(" · ${s.placeDayCount} place-days (${s.testDays} held out, ${s.ramadanDays} in Ramadan) · ${s.rows.size} rows\n")
            table(s, s.events)
            for ((event, e) in s.events) if (e.absent > 0) append("    ${event.key}: the engine shows none on ${e.absent} days\n")
            for ((event, e) in s.events) {
                if (e.declared > 0) {
                    append("    ${event.key}: ${e.declared} cells declared not followed (${dateRanges(e.declaredDates).joinToString(", ")})\n")
                }
            }
            if (s.testDays in 1 until s.placeDayCount) {
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
            for ((member, g) in s.maghribGaps) {
                append("  Maghrib cap against $member: ${g.days} days (${g.capped} capped), shown ${g.least}..${g.most} min after its printed time\n")
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
