package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Resolution
import java.io.File

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

    /** The per-unit rows where the gate checks the entry by unit; null where it checks it whole (as the app's `worstLateByUnit.isEmpty()`). */
    val units: Map<String, Any?>? = (root["units"] as? Map<String, Any?>)?.takeIf { it.isNotEmpty() }

    /** A cautious entry's stamp (class C): a gate over every member's printed table (ruling R38). */
    val cautious: Boolean get() = (root["class"] as String).startsWith("C")

    fun unitEvents(unitId: String): Map<String, Any?>? =
        (units?.get(unitId) as? Map<String, Any?>)?.get("events") as? Map<String, Any?>

    fun unitBroken(unitId: String): Int = ((units?.get(unitId) as? Map<String, Any?>)?.get("broken") as? Long ?: 0L).toInt()

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

/** Whether a city has a page (spec §2). */
sealed interface Verdict {
    /**
     * [events] is the row the page's figures come from: the unit's where the stamp has units, else
     * the entry's. [atMost] is the worst lateness over the starts there — null for a cautious place,
     * which claims no such figure (ruling R105: the entry-wide worst would be another place's).
     */
    data class Published(val stamp: Stamp, val events: Map<String, Any?>, val atMost: Int?) : Verdict

    data class Held(val reason: String) : Verdict
}

/**
 * Spec §2 (ruling R101): a city has a page only where the stamps prove every day it shows — class
 * A, B or C at the city, measured there, a green stamp for the resolved entry and its unit, covering
 * the first day shown through the last.
 */
object Proven {
    fun verdict(resolution: Resolution, stamps: Map<String, Stamp>, firstShown: LocalDate, lastShown: LocalDate): Verdict {
        val cls = resolution.entryClass
        if (cls != EntryClass.A && cls != EntryClass.B && cls != EntryClass.C) return Verdict.Held("class ${cls.name} is not proven")
        if (!resolution.measured) return Verdict.Held("not measured at this place")
        val stamp = stamps[resolution.entry.id] ?: return Verdict.Held("no stamp for ${resolution.entry.id}")
        if (stamp.broken != 0) return Verdict.Held("${stamp.entry}'s stamp is red")
        val events = if (stamp.units != null) {
            val unitId = resolution.unitId ?: return Verdict.Held("${stamp.entry} is checked by unit and this place is in none")
            val unit = stamp.unitEvents(unitId) ?: return Verdict.Held("unit $unitId is not in ${stamp.entry}'s stamp")
            if (stamp.unitBroken(unitId) != 0) return Verdict.Held("unit $unitId is red")
            unit
        } else {
            stamp.events
        }
        if (stamp.first > firstShown || stamp.last < lastShown) {
            return Verdict.Held("${stamp.entry}'s stamp covers ${stamp.first}..${stamp.last}; the page shows $firstShown..$lastShown")
        }
        if (cls == EntryClass.C) return Verdict.Published(stamp, events, atMost = null)
        val atMost = stamp.worstStarts(events) ?: return Verdict.Held("no start is measured at this unit")
        return Verdict.Published(stamp, events, atMost)
    }
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
