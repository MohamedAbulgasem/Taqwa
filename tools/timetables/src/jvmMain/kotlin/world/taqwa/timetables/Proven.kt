package world.taqwa.timetables

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Member
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.ReachLimits
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.gate.ExcuseKind
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.units.ReachRows
import world.taqwa.app.prayer.engine.registry.lateReachKm
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The days the gate compared, as runs of consecutive dates (a stamp's `checked`, written by
 * gate/Stamps.kt: "2026-01-01..2026-12-31" or one date). Dates only, never a time (ruling R69).
 */
class Checked(runs: List<ClosedRange<LocalDate>>) {
    val runs: List<ClosedRange<LocalDate>> = runs.sortedBy { it.start }

    fun covers(date: LocalDate): Boolean = runs.any { date in it }

    /** The first date of [first]..[last] no run holds, or null when every day is checked. */
    fun firstUncovered(first: LocalDate, last: LocalDate): LocalDate? {
        var date = first
        while (date <= last) {
            val run = runs.firstOrNull { date in it } ?: return date
            date = run.endInclusive.plus(1, DateTimeUnit.DAY)
        }
        return null
    }

    /** The last date of the run holding [date], or null. */
    fun endOf(date: LocalDate): LocalDate? = runs.firstOrNull { date in it }?.endInclusive

    /** The first checked date after [date], or null. */
    fun nextStartAfter(date: LocalDate): LocalDate? = runs.map { it.start }.filter { it > date }.minOrNull()

    /** The days both hold. */
    fun intersect(other: Checked): Checked = union(
        listOf(
            Checked(
                runs.flatMap { a ->
                    other.runs.mapNotNull { b ->
                        val start = maxOf(a.start, b.start)
                        val end = minOf(a.endInclusive, b.endInclusive)
                        if (start <= end) start..end else null
                    }
                },
            ),
        ),
    )

    /** The days this holds and [other] does not. */
    fun minus(other: Checked): Checked {
        var left = runs
        for (cut in other.runs) {
            left = left.flatMap { run ->
                if (cut.endInclusive < run.start || cut.start > run.endInclusive) {
                    listOf(run)
                } else {
                    listOfNotNull(
                        if (cut.start > run.start) run.start..cut.start.minus(1, DateTimeUnit.DAY) else null,
                        if (cut.endInclusive < run.endInclusive) cut.endInclusive.plus(1, DateTimeUnit.DAY)..run.endInclusive else null,
                    )
                }
            }
        }
        return Checked(left)
    }

    /** How many days of [first]..[last] this holds. */
    fun daysIn(first: LocalDate, last: LocalDate): Int = runs.sumOf { run ->
        val start = maxOf(run.start, first)
        val end = minOf(run.endInclusive, last)
        if (start <= end) end.toEpochDays().toLong() - start.toEpochDays().toLong() + 1L else 0L
    }.toInt()

    companion object {
        /** Nothing checked. */
        val NONE = Checked(emptyList())

        /** A stamp's list of runs; absent, it reads as nothing checked (the safe side). */
        fun parse(value: Any?): Checked = Checked(
            (value as? List<*>).orEmpty().mapNotNull { run ->
                (run as? String)?.split("..")?.let { ends -> LocalDate.parse(ends.first())..LocalDate.parse(ends.last()) }
            },
        )

        /** [all] merged: touching and overlapping runs become one. */
        fun union(all: Collection<Checked>): Checked {
            val merged = mutableListOf<ClosedRange<LocalDate>>()
            for (run in all.flatMap { it.runs }.sortedBy { it.start }) {
                val last = merged.lastOrNull()
                if (last != null && run.start <= last.endInclusive.plus(1, DateTimeUnit.DAY)) {
                    if (run.endInclusive > last.endInclusive) merged[merged.lastIndex] = last.start..run.endInclusive
                } else {
                    merged += run
                }
            }
            return Checked(merged)
        }
    }
}

/**
 * One stamp (`official/stamps/<entry>.json`, written by gate/Stamps.kt): statistics only — places,
 * place-days, dates, minute counts — never a time. Read through the generator's own [Json] reader,
 * numbers arriving as `Long`.
 */
@Suppress("UNCHECKED_CAST")
class Stamp(private val root: Map<String, Any?>) {
    val entry = root["entry"] as String
    val broken = (root["broken"] as Long).toInt()
    val places = (root["places"] as Long).toInt()
    val placeDays = (root["placeDays"] as Long).toInt()
    val heldOutDays = (root["heldOutDays"] as? Long ?: 0L).toInt()

    /** Of [placeDays], the days another entry's stamp counts too, and the published tables counted here (AcrossEntries). */
    val sharedDays = (root["sharedDays"] as? Long ?: 0L).toInt()
    val tables = (root["tables"] as? Long ?: 0L).toInt()
    val ramadanDays = (root["ramadanDays"] as? Long ?: 0L).toInt()
    val first: LocalDate = LocalDate.parse(root["first"] as String)
    val last: LocalDate = LocalDate.parse(root["last"] as String)
    val events: Map<String, Any?> = root["events"] as Map<String, Any?>

    /** The days any row of the entry compared, at any place (ruling R115); nothing on a stamp from before the runs were written. */
    val checked: Checked = Checked.parse(root["checked"])

    /**
     * The monitor's standing demotions (ruling R185: a capture of the authority's own site broke a promise), unit to
     * the day it was found; the key is null for the entry as a whole. Empty on most.
     */
    val demoted: Map<String?, String> = (root["demoted"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        .associate { (it["unit"] as? String) to (it["since"] as? String).orEmpty() }

    /** The per-unit rows where the gate checks the entry by unit; null where it checks it whole (as the app's `worstLateByUnit.isEmpty()`). */
    val units: Map<String, Any?>? = (root["units"] as? Map<String, Any?>)?.takeIf { it.isNotEmpty() }

    /** A cautious entry's stamp (class C): a gate over every member's printed table (ruling R38). */
    val cautious: Boolean get() = (root["class"] as String).startsWith("C")

    fun unitEvents(unitId: String): Map<String, Any?>? =
        (units?.get(unitId) as? Map<String, Any?>)?.get("events") as? Map<String, Any?>

    fun unitBroken(unitId: String): Int = ((units?.get(unitId) as? Map<String, Any?>)?.get("broken") as? Long ?: 0L).toInt()

    /** The days the unit's own rows compared. */
    fun unitChecked(unitId: String): Checked = Checked.parse((units?.get(unitId) as? Map<String, Any?>)?.get("checked"))

    /** A cautious stamp: [memberId]'s checked days by the point its rows were read at (gate/Gate.kt's `pointKey`). */
    fun memberPoints(memberId: String): Map<GeoPoint, Checked> {
        val member = (root["members"] as? Map<String, Any?>)?.get(memberId) as? Map<String, Any?> ?: return emptyMap()
        val points = member["points"] as? Map<String, Any?> ?: return emptyMap()
        return points.entries.associate { (key, runs) ->
            val (lat, lon) = key.split(',')
            GeoPoint(lat.toDouble(), lon.toDouble()) to Checked.parse(runs)
        }
    }

    /**
     * A cautious stamp: the days [memberId]'s rows leave out for a reason the gate file records
     * (ruling R117), by the point its rows were read at and by the kind of reason ("fault",
     * "unpublished"); empty on a stamp without them.
     */
    fun memberExcused(memberId: String): Map<GeoPoint, Map<String, Checked>> {
        val member = (root["members"] as? Map<String, Any?>)?.get(memberId) as? Map<String, Any?> ?: return emptyMap()
        val points = member["excused"] as? Map<String, Any?> ?: return emptyMap()
        return points.entries.associate { (key, kinds) ->
            val (lat, lon) = key.split(',')
            GeoPoint(lat.toDouble(), lon.toDouble()) to (kinds as Map<String, Any?>).mapValues { (_, runs) -> Checked.parse(runs) }
        }
    }

    /** Early starts over every event: 0 on a green stamp. */
    fun early(): Int = events.values.sumOf { ((it as Map<String, Any?>)["early"] as? Long ?: 0L).toInt() }

    /** Late ends (sunrise, the end of eating, imsak) over every event: 0 on a green stamp. */
    fun lateEnds(): Int = events.values.sumOf { ((it as Map<String, Any?>)["lateEnd"] as? Long ?: 0L).toInt() }

    /**
     * The worst lateness over the starts of [events] (the app's `measuredStartsWorst`), or null
     * where [events] lacks a start [entryEvents] has: a unit row without Fajr and Maghrib (eastern
     * Libya, ruling R73) is no figure for the whole day. Never an end's lateness.
     */
    fun worstStarts(events: Map<String, Any?>, entryEvents: Map<String, Any?> = this.events): Int? {
        fun starts(keys: Set<String>) = keys.filter { it in STARTS }.map { if (it.startsWith("asr")) "asr" else it }.toSet()
        if (!starts(events.keys).containsAll(starts(entryEvents.keys))) return null
        return events.entries.filter { it.key in STARTS }
            .mapNotNull { ((it.value as Map<String, Any?>)["worstLate"] as? Long)?.toInt() }
            .maxOrNull()
    }

    /**
     * [event]'s late histogram in [events] as shares of its own checked count ("0", "1", "2", "3+"),
     * four decimals: the ruler's bars (spec §3.3.1), never an absolute day count.
     */
    fun shares(events: Map<String, Any?>, event: String): Map<String, Double> {
        val stats = events[event] as? Map<String, Any?> ?: return emptyMap()
        val late = stats["late"] as? Map<String, Any?> ?: return emptyMap()
        val total = late.values.sumOf { (it as Long).toDouble() }.takeIf { it > 0 } ?: return emptyMap()
        return late.mapValues { Math.round((it.value as Long) / total * 10_000) / 10_000.0 }
    }

    companion object {
        /** The stamp's event keys for the starts: the gate writes one Asr, or the two schools apart. */
        val STARTS = setOf("fajr", "dhuhr", "asr", "asrStandard", "asrHanafi", "maghrib", "isha")

        /** Every `*.json` in [dir] by its entry id; an absent directory holds none. */
        @Suppress("UNCHECKED_CAST")
        fun load(dir: File): Map<String, Stamp> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .sortedBy { it.name }
            .map { Stamp(Json.parse(it.readText()) as Map<String, Any?>) }
            .associateBy { it.entry }
    }
}

/** Whether a city has a page (spec §2), and which of its months the page shows (ruling R116). */
sealed interface Verdict {
    /**
     * [months] are the whole months the page shows: the current month, and the next only where
     * every day of it was checked too (ruling R116); where it was not, [nextUnchecked] names the
     * first timetable that leaves a day of it unchecked, and that day. [events] is the row the
     * page's figures come from: the unit's where the stamp has units, else the entry's. [atMost]
     * is the worst lateness over the starts there — null for a cautious place, which claims no
     * such figure (ruling R105: the entry-wide worst would be another place's). [through] is the
     * last date of the checked run the days shown sit in (ruling R115): for a cautious place the
     * last of the run of days covered there (ruling R117). [excused] names each member whose table
     * is excused on days shown, why, and on how many (ruling R117).
     */
    data class Published(
        val stamp: Stamp,
        val events: Map<String, Any?>,
        val atMost: Int?,
        val through: LocalDate,
        val months: List<ClosedRange<LocalDate>>,
        val nextUnchecked: Unchecked? = null,
        val excused: List<Excused> = emptyList(),
    ) : Verdict

    data class Held(val reason: String) : Verdict
}

/** A day the gate did not check, and the timetable it was not checked against (ruling R116). */
data class Unchecked(val timetable: String, val day: LocalDate)

/**
 * Ruling R117: a member [timetable] excused on [days] of the days a page shows, for [why] — its
 * kind of reason in words ("recorded faults", "days it does not publish"); the words themselves
 * stay in the gate file.
 */
data class Excused(val timetable: String, val why: String, val days: Int)

/**
 * Spec §2 (rulings R101, R115, R116, R117): a city has a page only where the stamps prove every
 * day it shows — class A, B or C at the city, measured there, a green stamp for the resolved entry
 * and its unit, and every shown date among the days the gate compared for every timetable the
 * page's times depend on: the place's unit (or the entry, where the stamp has no units), and for a
 * cautious place every member at that place — except a day a member's table does not publish or
 * publishes wrongly, recorded with its reason in the gate file, so long as another member's table
 * was checked on it (ruling R117). The page shows whole months only: the current one when every
 * day of it is checked (else the city is held), and the next as well only when every day of that
 * one is checked too.
 */
object Proven {
    /** "26 Oct 2026": plain English months (en-GB's CLDR data abbreviates September as "Sept"). */
    private val DAY = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)

    /**
     * [months] are the city's current month and the next, each from its first day to its last,
     * the current first. The verdict shows a month only after every one before it: whole months,
     * in order.
     */
    fun verdict(
        resolution: Resolution,
        place: Place,
        stamps: Map<String, Stamp>,
        months: List<ClosedRange<LocalDate>>,
        ruled: Map<String, Map<TimedEvent, Int>> = ReachRows.RULED,
        minutes: Map<String, Map<String, Map<TimedEvent, Int>>> = ReachLimits.MINUTES,
    ): Verdict {
        require(months.isNotEmpty()) { "a page shows at least its current month" }
        // A checked authority the app shows as its published method on a day no table was compared on (ruling R275) is
        // judged as checked here, so the held reason names that day, as it did before the app said so too.
        val cls = resolution.checkedClass ?: resolution.entryClass
        if (cls != EntryClass.A && cls != EntryClass.B && cls != EntryClass.C) return Verdict.Held("class ${cls.name} is not proven")
        if (!resolution.measured) return Verdict.Held("not measured at this place")
        val stamp = stamps[resolution.entry.id] ?: return Verdict.Held("no stamp for ${resolution.entry.id}")
        if (stamp.broken != 0) return Verdict.Held("${stamp.entry}'s stamp is red")
        // Ruling R185: a checked timetable the monitor demoted has no page until its failing days are proven.
        stamp.demoted[null]?.let { return Verdict.Held("${stamp.entry} was demoted by the monitor on $it") }
        val unitId = if (stamp.units != null) {
            resolution.unitId ?: return Verdict.Held("${stamp.entry} is checked by unit and this place is in none")
        } else {
            null
        }
        // ... and a unit it demoted likewise (wherever the stamp's units stand, the place's own unit is the one meant).
        (unitId ?: resolution.unitId)?.let { u -> stamp.demoted[u]?.let { return Verdict.Held("${stamp.entry}'s unit $u was demoted by the monitor on $it") } }
        // Ruling R270: a unit whose reach was measured over the 4-min ceiling has no page until the owner rules on the
        // figure ([ruled], ReachRows.RULED), as a release has none: the page would say "at most" a figure no ruling accepted.
        // An entry with no units (MUIS's) holds its area's figure under its own id (ruling R298).
        (unitId ?: resolution.unitId ?: stamp.entry.takeIf { Units.of(it) == null })?.let { u ->
            ReachRows.unruled(stamp.entry, u, minutes, ruled).takeIf { it.isNotEmpty() }?.let { over ->
                val who = if (Units.of(stamp.entry) == null) "${stamp.entry} (no units)" else "${stamp.entry}'s unit $u"
                return Verdict.Held(
                    "$who reads " + over.entries.joinToString(", ") { (e, m) -> "${e.name.lowercase()} $m min" } +
                        " across its reach, over the ${ReachRows.CEILING}-min ceiling, until the owner rules (R270)",
                )
            }
        }
        val events = if (unitId != null) {
            val unit = stamp.unitEvents(unitId) ?: return Verdict.Held("unit $unitId is not in ${stamp.entry}'s stamp")
            if (stamp.unitBroken(unitId) != 0) return Verdict.Held("unit $unitId is red")
            unit
        } else {
            stamp.events
        }
        if (cls == EntryClass.C) return cautious(resolution, place, stamp, events, months, stamps)
        // The timetable the page's times depend on, with the days it was checked on here.
        val proven: List<Pair<String, Checked>> = listOf(stamp.entry to (if (unitId != null) stamp.unitChecked(unitId) else stamp.checked))
        // The current month, whole, or no page.
        val current = months.first()
        for ((who, checked) in proven) {
            val hole = hole(checked, current.start, current.endInclusive) ?: continue
            return Verdict.Held("$who: no checked table day $hole")
        }
        // Each later month, whole, or the page ends before it (ruling R116): a partly checked
        // month is never shown, and the first timetable leaving a day of it unchecked is named.
        var shown = 1
        var nextUnchecked: Unchecked? = null
        for (month in months.drop(1)) {
            nextUnchecked = proven.firstNotNullOfOrNull { (who, checked) ->
                checked.firstUncovered(month.start, month.endInclusive)?.let { Unchecked(who, it) }
            }
            if (nextUnchecked != null) break
            shown++
        }
        val lastShown = months[shown - 1].endInclusive
        val through = proven.minOf { (_, checked) -> checked.endOf(lastShown)!! }
        val atMost = when (cls) {
            EntryClass.C -> null
            else -> stamp.worstStarts(events) ?: return Verdict.Held("no start is measured at this unit")
        }
        return Verdict.Published(stamp, events, atMost, through, months.take(shown), nextUnchecked)
    }

    /**
     * A cautious place (rulings R115, R117): a day is shown only where, for every member, the
     * member's table is checked there or excused there for a recorded reason, and at least one
     * member's table is checked there. A day every member is excused on is a hole; so is a day a
     * member's table simply lacks. The held reason and the next month's notice name the first day
     * not covered, and the first member neither checked nor excused on it.
     */
    private fun cautious(
        resolution: Resolution,
        place: Place,
        stamp: Stamp,
        events: Map<String, Any?>,
        months: List<ClosedRange<LocalDate>>,
        stamps: Map<String, Stamp>,
    ): Verdict {
        val here = resolution.members.map { member ->
            memberHere(stamp, member, resolution.point, place, stamps)
                ?: return Verdict.Held("${member.id} (a member of ${stamp.entry}): no checked table at this place")
        }
        val covered = here.fold(Checked.union(here.map { it.checked })) { days, member -> days.intersect(member.covered) }
        /** Who leaves [day] uncovered: the first member neither checked nor excused on it, else every member excused. */
        fun who(day: LocalDate): MemberHere? = here.firstOrNull { !it.covered.covers(day) }
        val current = months.first()
        covered.firstUncovered(current.start, current.endInclusive)?.let { day ->
            val member = who(day)
            return Verdict.Held(
                if (member != null) {
                    "${member.who}: no checked table day ${hole(member.covered, day, current.endInclusive)}"
                } else {
                    "${stamp.entry}: no member's table checked day ${hole(covered, day, current.endInclusive)} (every member's excused)"
                },
            )
        }
        var shown = 1
        var nextUnchecked: Unchecked? = null
        for (month in months.drop(1)) {
            val day = covered.firstUncovered(month.start, month.endInclusive)
            if (day != null) {
                nextUnchecked = Unchecked(who(day)?.who ?: "${stamp.entry} (every member's table excused)", day)
                break
            }
            shown++
        }
        val firstShown = current.start
        val lastShown = months[shown - 1].endInclusive
        val excused = here.flatMap { member ->
            member.excused.mapNotNull { (kind, days) ->
                days.daysIn(firstShown, lastShown).takeIf { it > 0 }?.let { Excused(member.who, ExcuseKind.byKey(kind)?.words ?: kind, it) }
            }
        }
        return Verdict.Published(stamp, events, null, covered.endOf(lastShown)!!, months.take(shown), nextUnchecked, excused)
    }

    /**
     * A cautious member at a place: the days its table is [checked] there, the days it is [checked]
     * or excused there ([covered]), and its excused days by the kind of reason ([excused]).
     */
    private class MemberHere(val who: String, val checked: Checked, val covered: Checked, val excused: Map<String, Checked>)

    /**
     * [member] at [point] (rulings R115, R117): its checked days are the cautious [stamp]'s rows for
     * it within class C's reach of the point (the registry's own rule for how far a member's proof
     * reaches, `memberMeasured`), and its excused days those the gate file excuses there. Where it
     * has no such rows, or on a day its rows there leave out for a recorded reason, its own green
     * unit covering [place] — where the member is its own entry — stands in with that unit's
     * checked days. Null where it has neither.
     */
    private fun memberHere(stamp: Stamp, member: Member, point: GeoPoint, place: Place, stamps: Map<String, Stamp>): MemberHere? {
        val who = "${member.id} (a member of ${stamp.entry})"
        fun near(at: GeoPoint) = distanceKm(point, at) <= lateReachKm(at.lat, EntryClass.C)
        val rows = stamp.memberPoints(member.id).filterKeys(::near).values
        val own = ownUnitChecked(member, place, stamps)
        if (rows.isEmpty()) return own?.let { MemberHere(who, it, it, emptyMap()) }
        val atRows = Checked.union(rows)
        val excusedHere = stamp.memberExcused(member.id).filterKeys(::near).values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, runs) -> Checked.union(runs).minus(atRows) }
        val anyExcused = Checked.union(excusedHere.values)
        val checked = if (own == null) atRows else Checked.union(listOf(atRows, own.intersect(anyExcused)))
        return MemberHere(who, checked, Checked.union(listOf(checked, anyExcused)), excusedHere.mapValues { (_, days) -> days.minus(checked) })
    }

    /** [member]'s own entry's checked days at the green unit covering [place], or null. */
    private fun ownUnitChecked(member: Member, place: Place, stamps: Map<String, Stamp>): Checked? {
        val own = stamps[member.id] ?: return null
        val unit = Units.of(member.id)?.unitFor(place) ?: return null
        if (own.unitEvents(unit.id) == null || own.unitBroken(unit.id) != 0) return null
        return own.unitChecked(unit.id)
    }

    /** The first stretch of [first]..[last] [checked] leaves unchecked — "26 Oct 2026 – 31 Dec 2026", or "from 1 Oct 2026" with nothing checked after — or null. */
    private fun hole(checked: Checked, first: LocalDate, last: LocalDate): String? {
        val from = checked.firstUncovered(first, last) ?: return null
        val next = checked.nextStartAfter(from) ?: return "from ${day(from)}"
        return "${day(from)} – ${day(next.minus(1, DateTimeUnit.DAY))}"
    }

    /** A date as the run's notices write it: "1 Nov 2026". */
    fun day(date: LocalDate): String = DAY.format(date.toJavaLocalDate())
}

/** The totals the "How Taqwa checks" page prints (spec §5), read from the files, never typed. */
object ProofTotals {
    /** The gate's rows: every `.tsv` under `gate`, read as `GateManifest.parse` reads it — `#` lines, `@` directives (ruling R117's `@excuse`) and blank lines skipped, the first other line the header. */
    fun gateRows(official: File): Int = (official.resolve("gate").listFiles { f -> f.isFile && f.extension == "tsv" } ?: emptyArray())
        .sumOf { file -> rows(file) }

    /**
     * The published tables the gate holds: the distinct tables its rows read ([tableKey] of each `path`), each counted
     * once however many rows replay it. A reach or envelope row replays a table the gate already reads, at a point of
     * the unit's reach or under another setting, so it adds place-days and no table: "{tables} published tables" on the
     * "How Taqwa checks" page is at most this figure ([publishedTables]), never the row count (the review of 13 Nov 2026: with Egypt's reach rows
     * the rows were 55,143 and the table files 3,632). Nor is it the count of files (the audit of 13 Nov 2026, finding
     * F32, ruling R214): a table the monitor captured again on a later date, the two halves a year's table was cut into
     * for the fit, and the side file that holds a table's imsak column are files of one publication, and counted as
     * files they made the figure some 330 more than the publications held (4,385 files, 4,052 tables on 13 November).
     * A file that prints several towns is one table here, and two publications whose files differ only by a capture
     * date are one: the figure can understate, never overstate. A table only promise-only rows read is not counted
     * (ruling R247): an envelope row replays a table an ordinary row reads already, and a hand-kept promise file's
     * table (a calculator's own, which no place is known to follow) is no published timetable.
     */
    fun gateTables(official: File): Int = gateTables(GateManifest.load(official.resolve("gate")))

    /**
     * The page's own two figures (the review round of 14 Nov 2026, AcrossEntries), read off the stamps: the published
     * tables, each counted at one entry and none the archive worked out, and the place-days less those two entries
     * both count. Never more than [gateTables] and the plain sum.
     */
    fun publishedTables(stamps: Collection<Stamp>): Int = stamps.sumOf { it.tables }
    fun placeDays(stamps: Collection<Stamp>): Int = stamps.sumOf { it.placeDays - it.sharedDays }

    /** [gateTables] of a manifest already read. */
    fun gateTables(manifest: GateManifest): Int = manifest.rows.filter { !it.promiseOnly }.mapNotNull { tableKey(it.path) }.distinct().size

    /**
     * The publication a gate row's table file belongs to, so that every file of one publication has one key; null for
     * a side file that is no table of its own (ruling R214, finding F32). Read off the archive's own layout, and only
     * ever merging: a directory named for a capture date (`…/2026-11-01/…`) and a capture date closing the file's name
     * (`adiyaman-2026-11-12.txt`) are dropped, so a later capture of the same address is the same table; a closing
     * `-h1` or `-h2` is dropped, so the two halves of a year cut in two for the fit are one; and a file under
     * `archive/tables/imsak/` is the imsak column of a table held beside it, and none.
     */
    fun tableKey(path: String): String? {
        if (path.startsWith(IMSAK_SIDE_FILES)) return null
        val parts = path.split('/').filterNot { CAPTURE_DATE.matches(it) }
        val name = parts.last()
        val stem = name.substringBeforeLast('.', name).replace(CLOSING_DATE, "").replace(CLOSING_HALF, "")
        val extension = if ('.' in name) "." + name.substringAfterLast('.') else ""
        return (parts.dropLast(1) + (stem + extension)).joinToString("/")
    }

    private const val IMSAK_SIDE_FILES = "archive/tables/imsak/"
    private val CAPTURE_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val CLOSING_DATE = Regex("""-\d{4}-\d{2}-\d{2}$""")
    private val CLOSING_HALF = Regex("""-h[12]$""")

    /** The surveyed mosque calendars: every `survey/<entry>/calendars.tsv`, read the same way. */
    fun surveyCalendars(official: File): Int = (official.resolve("survey").listFiles { f -> f.isDirectory } ?: emptyArray())
        .sumOf { dir -> dir.resolve("calendars.tsv").takeIf { it.isFile }?.let { rows(it) } ?: 0 }

    private fun rows(file: File): Int = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }.drop(1).size
}
