package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The user's Hijri offset (Settings, −1, 0 or +1 day, `EngineSettings.hijriOffsetDays`) and the Ramadan rules (engine
 * survey of 11 Nov 2026, "what the survey missed": the gate measures offset 0 alone). The rules: Umm al-Qura's Isha
 * Maghrib + 120 instead of + 90, MUIS's Maghrib + 60 s, Qatar's + 8 s, Sudan's Isha floor of Maghrib + 90 (ruling R74),
 * wherever an entry, a cautious entry's member or a unit carries one ([ruled]).
 *
 * The engine's Ramadan ([Registry.ramadanCalendarFor]) is Umm al-Qura's own dates from 2024 to 2030 (± a day for every
 * authority but Umm al-Qura) and reads the offset only outside them, where it falls back to the tabular month ± a day.
 * So from 2024 to 2030 ±1 moves nothing, and there the offset-0 proof is the proof at ±1 (held on the calendar itself).
 * Every held table of a ruled entry is also run through the gate at −1 and +1: none early and none over its limit, the
 * figures that move printed with the held dates that move them (only tables outside 2024–2030 can: Sudan's 2022
 * imsakiya). Beyond 2030 the offset moves the window a day; each offset's window still holds the user's own tabular
 * month (the month the app shows with that offset), and the dates it gains and loses against offset 0 are printed.
 */
class HijriOffsetTest {

    @Test
    fun `the entries with a Ramadan rule are the four authorities and what carries their methods`() {
        val ids = ruled.map { it.id }
        println("Entries with a Ramadan rule: ${ids.joinToString(", ")}")
        for (expected in listOf("sa.ummalqura", "sg.muis", "qa.calendarhouse", "sd.ministry")) {
            assertTrue(expected in ids, "$expected has no Ramadan rule any more: $ids")
        }
    }

    @Test
    fun `from 2024 to 2030 the user's Hijri offset moves no entry's Ramadan by a day`() {
        val moved = mutableListOf<String>()
        for (entry in ruled) {
            val calendars = OFFSETS.associateWith { Registry.ramadanCalendarFor(entry, it) }
            for (date in dates(LocalDate(2024, 1, 1), LocalDate(2030, 12, 31))) {
                val zero = calendars.getValue(0).isRamadan(date)
                for (offset in OFFSETS) if (calendars.getValue(offset).isRamadan(date) != zero) moved += "${entry.id} $date at $offset"
            }
        }
        assertTrue(moved.isEmpty(), "the offset moves a Ramadan rule inside Umm al-Qura's range: ${moved.take(20)}")
    }

    @Test
    fun `beyond 2030 each offset's Ramadan still holds the user's own tabular month, and what it moves is reported`() {
        val missing = mutableListOf<String>()
        val report = StringBuilder()
        for (entry in ruled) {
            val zero = Registry.ramadanCalendarFor(entry, 0)
            for (offset in listOf(-1, 1)) {
                val calendar = Registry.ramadanCalendarFor(entry, offset)
                val lost = mutableListOf<LocalDate>()
                val gained = mutableListOf<LocalDate>()
                for (date in dates(LocalDate(2031, 1, 1), LocalDate(2033, 12, 31))) {
                    val own = TabularHijriCalendar.fromGregorian(date.plus(offset, DateTimeUnit.DAY)).month == RAMADAN
                    val here = calendar.isRamadan(date)
                    if (own && !here) missing += "${entry.id} $date at $offset"
                    if (zero.isRamadan(date) && !here) lost += date
                    if (!zero.isRamadan(date) && here) gained += date
                }
                report.appendLine("  ${entry.id} at ${if (offset > 0) "+" else ""}$offset: loses ${lost.joinToString()} ; gains ${gained.joinToString()}")
            }
        }
        println("Beyond Umm al-Qura's range (2031–2033), the Ramadan rules' dates against offset 0:\n$report")
        assertTrue(missing.isEmpty(), "a day of the user's own tabular Ramadan outside the engine's: ${missing.take(20)}")
    }

    @Test
    fun `at offset -1 and +1 no held table of an entry with a Ramadan rule shows a start early, and what moves is reported`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        if (!roots.held) return
        val ids = ruled.map { it.id }.toSet()
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val rows = manifest.rows.filter { it.entry.substringBefore('/') in ids || it.member in ids }
        val only = GateManifest(rows, rows.map { it.source }.distinct(), manifest.excuses.filter { it.entry in ids })
        val results = OFFSETS.associateWith { Gate(roots, hijriOffsetDays = it).evaluate(only) }
        val base = counts(results.getValue(0))
        println("Held rows of entries with a Ramadan rule: ${rows.size} (${results.getValue(0).checkedRows} read), entries ${base.keys}")
        for ((offset, result) in results) {
            assertTrue(result.violations().isEmpty(), "offset $offset: ${result.violations()}")
            for ((id, line) in counts(result)) if (line != base[id]) println("  at $offset, $id's figures move:\n    0: ${base[id]}\n    $offset: $line")
        }
        // The held dates whose Ramadan the offset moves: only those outside Umm al-Qura's range can be.
        val moved = sortedSetOf<String>()
        for (row in rows.distinctBy { it.entry to it.path }) {
            val entry = Registry.byId(row.member ?: row.entry.substringBefore('/')) ?: continue
            val calendars = OFFSETS.associateWith { Registry.ramadanCalendarFor(entry, it) }
            for (day in OfficialTable.read(roots.file(row.path), row.format, row.columns.size).days) {
                val zero = calendars.getValue(0).isRamadan(day.date)
                for (offset in listOf(-1, 1)) {
                    if (calendars.getValue(offset).isRamadan(day.date) != zero) {
                        moved += "${entry.id} ${day.date} at ${if (offset > 0) "+" else ""}$offset: Ramadan rule ${if (zero) "dropped" else "added"}"
                        assertTrue(day.date.year !in 2024..2030, "${entry.id} ${day.date}: moved inside Umm al-Qura's range")
                    }
                }
            }
        }
        println("Held dates whose Ramadan rule the offset moves (none early, above): ${moved.ifEmpty { setOf("none") }}")
    }

    private fun counts(result: GateResult): Map<String, String> = result.entries.mapValues { (_, s) ->
        s.events.entries.joinToString("; ") { (event, e) ->
            "${event.key} checked ${e.checked} early ${e.early} lateEnd ${e.lateEnd} late ${e.late.joinToString("/")} worst ${e.worst} over ${e.over}"
        } + " outOfOrder ${s.outOfOrder} ramadan ${s.ramadanDays}"
    }

    private companion object {
        const val RAMADAN = 9
        val OFFSETS = listOf(-1, 0, 1)

        fun hasRule(m: TimetableMethod): Boolean =
            m.ramadan != null || (m.isha as? IshaRule.AfterMaghrib)?.let { it.ramadanMinutes != it.minutes } == true

        /** Every entry whose own method, a member's or a unit's carries a Ramadan rule. */
        val ruled: List<RegistryEntry> = Registry.entries.filter { e ->
            (listOfNotNull(e.method) + e.members.map { it.method } + Units.of(e.id)?.units?.mapNotNull { it.method }.orEmpty())
                .any(::hasRule)
        }

        fun dates(from: LocalDate, to: LocalDate): Sequence<LocalDate> =
            generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }
    }
}
