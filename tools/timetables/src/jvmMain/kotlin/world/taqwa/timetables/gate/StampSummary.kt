package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.timetables.Json
import java.io.File

/** A run of checked days as a stamp writes it ([dateRanges]): `from..to`, both days in it, or one date. */
data class DateRun(val from: LocalDate, val to: LocalDate) {
    init {
        require(to >= from) { "a run that ends before it starts: $from..$to" }
    }

    operator fun contains(day: LocalDate): Boolean = day >= from && day <= to

    override fun toString(): String = if (from == to) "$from" else "$from..$to"

    companion object {
        fun parse(text: String): DateRun {
            val ends = text.split("..")
            require(ends.size in 1..2 && ends.none { it.isBlank() }) { "not a run of days: '$text'" }
            return DateRun(LocalDate.parse(ends.first()), LocalDate.parse(ends.last()))
        }

        /** A stamp's `checked` value: a list of runs, or nothing (an older stamp, read as no day checked). */
        fun parseAll(value: Any?): List<DateRun> = (value as? List<*>).orEmpty().map { parse(it as String) }
    }
}

/** The last day any run of this list holds; null when it holds none. */
val List<DateRun>.lastDay: LocalDate? get() = maxOfOrNull { it.to }

/** The last day any run holds before [day]; null when none comes before it. */
fun List<DateRun>.lastDayBefore(day: LocalDate): LocalDate? =
    mapNotNull { run -> if (run.from >= day) null else minOf(run.to, day.plus(-1, DateTimeUnit.DAY)) }.maxOrNull()

/** The days of [from]..[to] (both in it) that no run of this list holds, in order. */
fun List<DateRun>.unchecked(from: LocalDate, to: LocalDate): List<LocalDate> {
    val days = mutableListOf<LocalDate>()
    var day = from
    while (day <= to) {
        if (none { day in it }) days += day
        day = day.plus(1, DateTimeUnit.DAY)
    }
    return days
}

/**
 * One authority unit of a stamp (`units.<id>`): the days its own rows were checked on, and by
 * event ([checkedByEvent], the stamp's `checkedByEvent`, event key to runs: ruling R148). A stamp
 * from before the per-event runs has none, and is read as no event checked on any day.
 */
data class UnitSummary(
    val id: String,
    val name: String,
    val checked: List<DateRun>,
    val checkedByEvent: Map<String, List<DateRun>> = emptyMap(),
)

/**
 * One entry's stamp as the horizons and `checkStamps` read it: statistics only, from
 * `official/stamps/<entry>.json` ([Stamps.stamp] writes it). The days are the stamp's runs, the
 * entry's and each unit's, never the envelope `first`..`last` alone: an entry proven in two years
 * with a hole between them covers no day of the hole (ruling R145). And for a Ramadan the runs of
 * each event the fast turns on, never the unit's days as a whole (ruling R148): Jamiat's Pretoria
 * tables print no Suhoor column, so its Fajr and Maghrib are checked on every day of 2026 and 2027
 * and its end of eating on none, and Jamiat's end is its Fajr less five minutes, which a proven Fajr
 * does not bound (a later Fajr makes a later end).
 */
data class StampSummary(
    val entryId: String,
    /** The stamp's `class` split on `/`: "A", "B", "C", "D (authority)", "D (none)". */
    val classes: Set<String>,
    val first: LocalDate?,
    val last: LocalDate?,
    val broken: Int,
    /** The days any row compared, as runs. */
    val checked: List<DateRun> = emptyList(),
    /** Each authority unit's own checked days; empty where the gate checks the entry without units. */
    val units: List<UnitSummary> = emptyList(),
    /** The days any row compared each event on, event key to runs (ruling R148). */
    val checkedByEvent: Map<String, List<DateRun>> = emptyMap(),
) {
    val isAOrB: Boolean get() = "A" in classes || "B" in classes

    /**
     * The units a proof is read by: the stamp's own, or the entry itself as one unit where the gate
     * checks it without any (its id for the unit's, its own runs).
     */
    val proofUnits: List<UnitSummary> get() = units.ifEmpty { listOf(UnitSummary(entryId, entryId, checked, checkedByEvent)) }

    /**
     * The proof units short of [ramadan]: one of [Ramadan.EVENTS] was not checked on some day of its
     * guarded days ([Ramadan.guardFirst]..[Ramadan.guardLast], the month and a day each side; rulings
     * R146 and R148). Empty when every unit covers every such day for each of them.
     */
    fun ramadanGaps(ramadan: Ramadan): List<RamadanGap> = proofUnits.mapNotNull { unit ->
        val unchecked = Ramadan.EVENTS.associateWith { event ->
            unit.checkedByEvent[event].orEmpty().unchecked(ramadan.guardFirst, ramadan.guardLast)
        }
        if (unchecked.values.all { it.isEmpty() }) null else RamadanGap(unit, unchecked)
    }

    /**
     * One line on how [gaps] fall short of [ramadan], for a report or a refusal: the days checked at
     * each unit (and which event is short where they differ), at most [named] units named, and the
     * last day checked before the guarded days, all dates and counts (ruling R69): the entry's own
     * where it has no units, else the short units' own (the earliest to the latest of them, and how
     * many have none), never the entry's, which a unit that is not short can carry past them (review
     * of 12 Nov 2026: eg.esa's runs reach 31 Dec 2026 while 28 short units stop on 30 Nov).
     */
    fun describe(ramadan: Ramadan, gaps: List<RamadanGap>, named: Int = 5): String = buildString {
        if (units.isEmpty()) {
            append("${gaps.single().describe()} checked")
        } else {
            append("${gaps.size} of ${units.size} unit${if (units.size == 1) "" else "s"} short (")
            append(gaps.take(named).joinToString(", ") { "${it.unit.id} ${it.describe()}" })
            if (gaps.size > named) append(" and ${gaps.size - named} more")
            append(")")
        }
        if (units.isEmpty()) {
            val before = checked.lastDayBefore(ramadan.guardFirst)
            append(if (before == null) "; no day checked before it" else "; checked through $before before it")
            return@buildString
        }
        val before = gaps.map { it.unit.checked.lastDayBefore(ramadan.guardFirst) }
        val days = before.filterNotNull()
        if (days.isEmpty()) {
            append("; no short unit checked before it")
            return@buildString
        }
        val range = if (days.min() == days.max()) "${days.max()}" else "${days.min()} to ${days.max()}"
        append("; the short units checked through $range before it")
        val none = before.count { it == null }
        if (none > 0) append(" ($none with no day before it)")
    }

    companion object {
        /** Every `*.json` stamp in [dir], in name order. */
        fun load(dir: File): List<StampSummary> =
            dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.map { file ->
                @Suppress("UNCHECKED_CAST")
                of(Json.parse(file.readText()) as Map<String, Any?>, file.nameWithoutExtension)
            }.orEmpty()

        /** A parsed stamp; [fallbackId] names the entry where the stamp does not. */
        fun of(stamp: Map<String, Any?>, fallbackId: String): StampSummary {
            @Suppress("UNCHECKED_CAST")
            val units = (stamp["units"] as? Map<String, Any?>).orEmpty().map { (id, value) ->
                val unit = value as? Map<String, Any?>
                UnitSummary(id, unit?.get("name") as? String ?: id, DateRun.parseAll(unit?.get("checked")), byEvent(unit?.get("checkedByEvent")))
            }
            return StampSummary(
                entryId = stamp["entry"] as? String ?: fallbackId,
                classes = (stamp["class"] as? String).orEmpty().split('/').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                first = (stamp["first"] as? String)?.let(LocalDate::parse),
                last = (stamp["last"] as? String)?.let(LocalDate::parse),
                broken = (stamp["broken"] as? Long ?: 0L).toInt(),
                checked = DateRun.parseAll(stamp["checked"]),
                units = units,
                checkedByEvent = byEvent(stamp["checkedByEvent"]),
            )
        }

        /** A stamp's `checkedByEvent` value: event key to runs; nothing on a stamp from before them. */
        private fun byEvent(value: Any?): Map<String, List<DateRun>> =
            (value as? Map<*, *>).orEmpty().entries.associate { (key, runs) -> key as String to DateRun.parseAll(runs) }
    }
}

/**
 * One proof unit short of a Ramadan: for each of [Ramadan.EVENTS], the days of the month no run of
 * that event holds (ruling R148).
 */
data class RamadanGap(val unit: UnitSummary, val uncheckedByEvent: Map<String, List<LocalDate>>) {
    /** The guarded days every one of the events was checked on: those days less the shortest event's holes. */
    val checkedDays: Int get() = Ramadan.GUARD_DAYS - uncheckedByEvent.values.maxOf { it.size }

    /**
     * "N of 32 days" (the guarded days, [Ramadan.GUARD_DAYS]), and where the events differ, which
     * fall short and by how much: "0 of 32 days (endOfEating 0)" for a unit whose tables print no end
     * of eating.
     */
    fun describe(): String {
        val checked = uncheckedByEvent.mapValues { Ramadan.GUARD_DAYS - it.value.size }
        val differ = checked.values.toSet().size > 1
        return "$checkedDays of ${Ramadan.GUARD_DAYS} days" +
            if (differ) " (${checked.filterValues { it < Ramadan.GUARD_DAYS }.entries.joinToString(", ") { "${it.key} ${it.value}" }})" else ""
    }
}

/**
 * One Ramadan of the tabular calendar ([TabularHijriCalendar]): its [DAYS] days from [first], both
 * ends in it. The tabular month, not Umm al-Qura's, as the horizons have always read it: a sighting
 * can move the real month a day either way, and a table proven over the whole tabular month was
 * printed for a whole month or year and holds those days with it. Spec §5's Ramadan release rule
 * and the monitor's Ramadan horizon both read this (ruling R145), each day for each of [EVENTS]
 * (ruling R148), over the month widened by [TOLERANCE_DAYS] each side ([guardFirst]..[guardLast];
 * ruling R146, widened on 12 Nov 2026): the engine's own Ramadan for every authority but Umm
 * al-Qura is every date within a day of a month (`Registry.ramadanCalendarFor`), so its Ramadan rules
 * already apply on the eve of the first tarawih and on the last fast of a country that begins the
 * month a day late. That month is Umm al-Qura's own from 2024 to 2030 (the tabular one outside it,
 * moved by the user's Hijri offset only there, HijriOffsetTest): for 1448, Umm al-Qura's 29 days
 * from 8 Feb 2027, so the engine's Ramadan is 7 Feb–9 Mar, inside this guard's 7 Feb–10 Mar.
 */
data class Ramadan(val hijriYear: Int, val first: LocalDate) {
    val last: LocalDate get() = first.plus(DAYS - 1, DateTimeUnit.DAY)

    /** The first day a proof must hold: the eve of 1 Ramadan, the first tarawih night's day (ruling R146). */
    val guardFirst: LocalDate get() = first.plus(-TOLERANCE_DAYS, DateTimeUnit.DAY)

    /** The last day a proof must hold: the last fast of a country that begins the month a day late (ruling R146). */
    val guardLast: LocalDate get() = last.plus(TOLERANCE_DAYS, DateTimeUnit.DAY)

    /** From [WEEKS_8] weeks before [first] to [guardLast]: when the horizon speaks and `checkStamps` hints. */
    fun isNear(today: LocalDate): Boolean = today >= first.plus(-WEEKS_8, DateTimeUnit.DAY) && today <= guardLast

    override fun toString(): String = "Ramadan $hijriYear ($first..$last, guarded $guardFirst..$guardLast)"

    companion object {
        const val DAYS = 30

        /** The days the guard adds on each side of the tabular month, as `Registry.ramadanCalendarFor` does (R146). */
        const val TOLERANCE_DAYS = 1

        /** The days a proof must hold: the month and [TOLERANCE_DAYS] each side. */
        const val GUARD_DAYS = DAYS + 2 * TOLERANCE_DAYS
        private const val WEEKS_8 = 56

        /**
         * The events a day of fasting turns on, by their stamp keys: when eating stops (an end, which
         * a proven Fajr does not bound), the day's first prayer and its iftar. A unit covers a day of
         * Ramadan only when each was checked on it (ruling R148).
         */
        val EVENTS: List<String> = listOf(Event.FAJR.key, Event.END_OF_EATING.key, Event.MAGHRIB.key)

        /** Ramadan of [hijriYear] (1400..1600, a Hijri year like 1448: never a Gregorian one by mistake). */
        fun of(hijriYear: Int): Ramadan {
            require(hijriYear in 1400..1600) { "a Hijri year is wanted, like 1448, not $hijriYear" }
            // A Hijri year begins about 0.97 Gregorian years after the one before: start the year before.
            var date = LocalDate((hijriYear * 0.970229 + 621.57).toInt() - 1, 1, 1)
            repeat(1500) {
                val hijri = TabularHijriCalendar.fromGregorian(date)
                if (hijri.year == hijriYear && hijri.month == 9 && hijri.day == 1) return Ramadan(hijriYear, date)
                date = date.plus(1, DateTimeUnit.DAY)
            }
            error("no 1 Ramadan $hijriYear within 1500 days of $date")
        }

        /** The Ramadan whose guarded days [today] is in or before (through its [guardLast]), else the next. */
        fun nextFrom(today: LocalDate): Ramadan {
            var date = today.plus(-(DAYS - 1 + TOLERANCE_DAYS), DateTimeUnit.DAY)
            repeat(400) {
                val hijri = TabularHijriCalendar.fromGregorian(date)
                if (hijri.month == 9 && hijri.day == 1) return Ramadan(hijri.year, date)
                date = date.plus(1, DateTimeUnit.DAY)
            }
            error("no Ramadan within 400 days of $today")
        }
    }
}
