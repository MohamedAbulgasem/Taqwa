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
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
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

    companion object {
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
    val ramadanDays = (root["ramadanDays"] as? Long ?: 0L).toInt()
    val first: LocalDate = LocalDate.parse(root["first"] as String)
    val last: LocalDate = LocalDate.parse(root["last"] as String)
    val events: Map<String, Any?> = root["events"] as Map<String, Any?>

    /** The days any row of the entry compared, at any place (ruling R115); nothing on a stamp from before the runs were written. */
    val checked: Checked = Checked.parse(root["checked"])

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
     * earliest such date over its members.
     */
    data class Published(
        val stamp: Stamp,
        val events: Map<String, Any?>,
        val atMost: Int?,
        val through: LocalDate,
        val months: List<ClosedRange<LocalDate>>,
        val nextUnchecked: Unchecked? = null,
    ) : Verdict

    data class Held(val reason: String) : Verdict
}

/** A day the gate did not check, and the timetable it was not checked against (ruling R116). */
data class Unchecked(val timetable: String, val day: LocalDate)

/**
 * Spec §2 (rulings R101, R115, R116): a city has a page only where the stamps prove every day it
 * shows — class A, B or C at the city, measured there, a green stamp for the resolved entry and
 * its unit, and every shown date among the days the gate compared for every timetable the page's
 * times depend on: the place's unit (or the entry, where the stamp has no units), and for a
 * cautious place every member at that place. The page shows whole months only: the current one
 * when every day of it is checked (else the city is held), and the next as well only when every
 * day of that one is checked too.
 */
object Proven {
    /** "26 Oct 2026": plain English months (en-GB's CLDR data abbreviates September as "Sept"). */
    private val DAY = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)

    /**
     * [months] are the city's current month and the next, each from its first day to its last,
     * the current first. The verdict shows a month only after every one before it: whole months,
     * in order.
     */
    fun verdict(resolution: Resolution, place: Place, stamps: Map<String, Stamp>, months: List<ClosedRange<LocalDate>>): Verdict {
        require(months.isNotEmpty()) { "a page shows at least its current month" }
        val cls = resolution.entryClass
        if (cls != EntryClass.A && cls != EntryClass.B && cls != EntryClass.C) return Verdict.Held("class ${cls.name} is not proven")
        if (!resolution.measured) return Verdict.Held("not measured at this place")
        val stamp = stamps[resolution.entry.id] ?: return Verdict.Held("no stamp for ${resolution.entry.id}")
        if (stamp.broken != 0) return Verdict.Held("${stamp.entry}'s stamp is red")
        val unitId = if (stamp.units != null) {
            resolution.unitId ?: return Verdict.Held("${stamp.entry} is checked by unit and this place is in none")
        } else {
            null
        }
        val events = if (unitId != null) {
            val unit = stamp.unitEvents(unitId) ?: return Verdict.Held("unit $unitId is not in ${stamp.entry}'s stamp")
            if (stamp.unitBroken(unitId) != 0) return Verdict.Held("unit $unitId is red")
            unit
        } else {
            stamp.events
        }
        // The timetables the page's times depend on, each with the days it was checked on here.
        val proven: List<Pair<String, Checked>> = if (cls == EntryClass.C) {
            resolution.members.map { member ->
                val here = memberChecked(stamp, member, resolution.point, place, stamps)
                    ?: return Verdict.Held("${member.id} (a member of ${stamp.entry}): no checked table at this place")
                "${member.id} (a member of ${stamp.entry})" to here
            }
        } else {
            listOf(stamp.entry to (if (unitId != null) stamp.unitChecked(unitId) else stamp.checked))
        }
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
     * [member]'s checked days at [point] (ruling R115): the cautious [stamp]'s rows for it within
     * class C's reach of the point (the registry's own rule for how far a member's proof reaches,
     * `memberMeasured`), else — where the member is its own entry with a green unit covering
     * [place] — that unit's rows; null where it has neither.
     */
    private fun memberChecked(stamp: Stamp, member: Member, point: GeoPoint, place: Place, stamps: Map<String, Stamp>): Checked? {
        val near = stamp.memberPoints(member.id).filter { (at, _) -> distanceKm(point, at) <= lateReachKm(at.lat, EntryClass.C) }.values
        if (near.isNotEmpty()) return Checked.union(near)
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
    /** The gate's rows: every `.tsv` under `gate`, read as `GateManifest.parse` reads it — `#` lines and blank lines skipped, the first other line the header. */
    fun gateRows(official: File): Int = (official.resolve("gate").listFiles { f -> f.isFile && f.extension == "tsv" } ?: emptyArray())
        .sumOf { file -> rows(file) }

    /** The surveyed mosque calendars: every `survey/<entry>/calendars.tsv`, read the same way. */
    fun surveyCalendars(official: File): Int = (official.resolve("survey").listFiles { f -> f.isDirectory } ?: emptyArray())
        .sumOf { dir -> dir.resolve("calendars.tsv").takeIf { it.isFile }?.let { rows(it) } ?: 0 }

    private fun rows(file: File): Int = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).size
}
