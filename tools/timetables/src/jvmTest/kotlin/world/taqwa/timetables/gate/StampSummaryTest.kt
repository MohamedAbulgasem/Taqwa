package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [StampSummary] and [Ramadan] on invented stamps and dates: nothing here reads the committed stamps. */
class StampSummaryTest {

    @Test
    fun `ramadan 1448 of the tabular calendar runs from the eighth of february to the ninth of march 2027`() {
        val ramadan = Ramadan.of(1448)
        assertEquals(LocalDate(2027, 2, 8), ramadan.first)
        assertEquals(LocalDate(2027, 3, 9), ramadan.last)
        // The guard holds a day each side (ruling R146, as the engine's own Ramadan for every authority but Umm al-Qura):
        // the first tarawih's eve, 7 February, and the last fast of a country that begins a day late, 10 March.
        assertEquals(LocalDate(2027, 2, 7), ramadan.guardFirst)
        assertEquals(LocalDate(2027, 3, 10), ramadan.guardLast)
        assertEquals(32, Ramadan.GUARD_DAYS)
        assertEquals("Ramadan 1448 (2027-02-08..2027-03-09, guarded 2027-02-07..2027-03-10)", ramadan.toString())
        assertEquals(Ramadan.nextFrom(LocalDate(2026, 10, 18)), ramadan)
        // Eight weeks before its first day is 14 December 2026; the window closes after the guard's last day.
        assertTrue(!ramadan.isNear(LocalDate(2026, 12, 13)) && ramadan.isNear(LocalDate(2026, 12, 14)))
        assertTrue(ramadan.isNear(LocalDate(2027, 3, 10)) && !ramadan.isNear(LocalDate(2027, 3, 11)))
        // The years around it, and a Gregorian year given by mistake.
        assertEquals(LocalDate(2026, 2, 18), Ramadan.of(1447).first)
        assertEquals(LocalDate(2028, 1, 28), Ramadan.of(1449).first)
        assertFailsWith<IllegalArgumentException> { Ramadan.of(2027) }
    }

    @Test
    fun `runs are read as the stamp writes them and the days outside them are found`() {
        val runs = listOf("2026-01-01..2026-01-31", "2026-03-05").map(DateRun::parse)
        assertEquals(DateRun(LocalDate(2026, 3, 5), LocalDate(2026, 3, 5)), runs[1])
        assertEquals("2026-03-05", runs[1].toString())
        assertEquals(LocalDate(2026, 3, 5), runs.lastDay)
        assertEquals(LocalDate(2026, 1, 31), runs.lastDayBefore(LocalDate(2026, 3, 5)))
        assertEquals(LocalDate(2026, 1, 20), runs.lastDayBefore(LocalDate(2026, 1, 21)))
        assertNull(runs.lastDayBefore(LocalDate(2026, 1, 1)))
        assertEquals(
            listOf(LocalDate(2026, 2, 28), LocalDate(2026, 3, 1), LocalDate(2026, 3, 2), LocalDate(2026, 3, 3), LocalDate(2026, 3, 4), LocalDate(2026, 3, 6)),
            runs.unchecked(LocalDate(2026, 2, 28), LocalDate(2026, 3, 6)),
        )
        assertEquals(emptyList(), runs.unchecked(LocalDate(2026, 1, 10), LocalDate(2026, 1, 20)))
        assertNull(emptyList<DateRun>().lastDay)
        assertFailsWith<IllegalArgumentException> { DateRun.parse("2026-01-31..2026-01-01") }
        assertFailsWith<IllegalArgumentException> { DateRun.parse("2026-01-01..") }
    }

    @Test
    fun `a stamp file is read with its runs and its units' runs`() {
        val dir = Files.createTempDirectory("stamp-summary").toFile()
        try {
            val byEvent = mapOf("fajr" to listOf("2026-01-01..2026-12-31"), "endOfEating" to listOf("2026-01-01..2026-12-31"), "maghrib" to listOf("2026-01-01..2026-12-31"))
            dir.resolve("xx.one.json").writeText(
                Json.pretty(
                    mapOf(
                        "entry" to "xx.one", "class" to "A/D (authority)", "engineHash" to "abc", "broken" to 2,
                        "first" to "2021-01-01", "last" to "2031-09-30",
                        "checked" to listOf("2021-01-01..2021-01-31", "2026-01-01..2026-12-31", "2031-09-30"),
                        "checkedByEvent" to byEvent + ("sunrise" to listOf("2021-01-01..2021-01-31")),
                        "units" to mapOf(
                            "muscat" to mapOf("name" to "Muscat", "checked" to listOf("2026-01-01..2026-12-31"), "checkedByEvent" to byEvent),
                            "sur" to mapOf("name" to "Sur", "checked" to listOf("2031-09-30"), "checkedByEvent" to mapOf("fajr" to listOf("2031-09-30"))),
                        ),
                    ),
                ),
            )
            // An older stamp, without runs: it covers no day, and the entry is its own unit.
            dir.resolve("yy.two.json").writeText(Json.pretty(mapOf("class" to "B", "first" to "2026-01-01", "last" to "2026-12-31")))
            val stamps = StampSummary.load(dir)
            assertEquals(listOf("xx.one", "yy.two"), stamps.map { it.entryId })
            val one = stamps[0]
            assertEquals(setOf("A", "D (authority)"), one.classes)
            assertTrue(one.isAOrB)
            assertEquals(2, one.broken)
            assertEquals(LocalDate(2021, 1, 1), one.first)
            assertEquals(3, one.checked.size)
            assertEquals(setOf("fajr", "endOfEating", "maghrib", "sunrise"), one.checkedByEvent.keys)
            assertEquals(listOf(DateRun.parse("2021-01-01..2021-01-31")), one.checkedByEvent["sunrise"])
            assertEquals(listOf("muscat", "sur"), one.units.map { it.id })
            assertEquals("Sur", one.units[1].name)
            assertEquals(listOf(DateRun.parse("2031-09-30")), one.units[1].checkedByEvent["fajr"])
            assertEquals(one.units, one.proofUnits)
            val ramadan = Ramadan.of(1448)
            assertEquals(listOf("muscat", "sur"), one.ramadanGaps(ramadan).map { it.unit.id })
            assertEquals(0, one.ramadanGaps(ramadan).first().checkedDays)
            assertEquals("2 of 2 units short (muscat 0 of 32 days, sur 0 of 32 days); the short units checked through 2026-12-31 before it (1 with no day before it)", one.describe(ramadan, one.ramadanGaps(ramadan)))
            val two = stamps[1]
            assertEquals(emptyList(), two.checked)
            assertEquals(listOf(UnitSummary("yy.two", "yy.two", emptyList())), two.proofUnits)
            assertEquals("0 of 32 days checked; no day checked before it", two.describe(ramadan, two.ramadanGaps(ramadan)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a ramadan is covered only where each of its events was checked on every day`() {
        // Ruling R148: Pretoria's shape — every day checked, the end of eating on none of them.
        val year = listOf(DateRun.parse("2026-01-01..2027-12-31"))
        val ramadan = Ramadan.of(1448)
        assertEquals(listOf("fajr", "endOfEating", "maghrib"), Ramadan.EVENTS)
        val whole = UnitSummary("pretoria", "Pretoria", year, Ramadan.EVENTS.associateWith { year })
        val noEnd = whole.copy(checkedByEvent = mapOf("fajr" to year, "maghrib" to year, "sunrise" to year))
        val stamp = StampSummary("za.x", setOf("B"), LocalDate(2026, 1, 1), LocalDate(2027, 12, 31), 0, year, listOf(whole))
        assertEquals(emptyList(), stamp.ramadanGaps(ramadan))
        val gap = stamp.copy(units = listOf(noEnd)).ramadanGaps(ramadan).single()
        assertEquals(0, gap.checkedDays)
        assertEquals(32, gap.uncheckedByEvent.getValue("endOfEating").size)
        assertEquals(0, gap.uncheckedByEvent.getValue("fajr").size)
        assertEquals("0 of 32 days (endOfEating 0)", gap.describe())
        assertEquals(
            "1 of 1 unit short (pretoria 0 of 32 days (endOfEating 0)); the short units checked through 2027-02-06 before it",
            stamp.copy(units = listOf(noEnd)).describe(ramadan, listOf(gap)),
        )
        // Two events short by different counts: both named with their own; all three equally short: the count alone.
        val mixed = whole.copy(checkedByEvent = mapOf("fajr" to year, "endOfEating" to listOf(DateRun.parse("2026-01-01..2027-02-20")), "maghrib" to listOf(DateRun.parse("2026-01-01..2027-03-05"))))
        assertEquals("14 of 32 days (endOfEating 14, maghrib 27)", stamp.copy(units = listOf(mixed)).ramadanGaps(ramadan).single().describe())
        val none = whole.copy(checkedByEvent = emptyMap())
        assertEquals("0 of 32 days", stamp.copy(units = listOf(none)).ramadanGaps(ramadan).single().describe())
        // A unit-less entry is read by its own per-event runs.
        val bare = StampSummary("sg.x", setOf("A"), LocalDate(2026, 1, 1), LocalDate(2027, 12, 31), 0, year, emptyList(), mapOf("fajr" to year, "maghrib" to year))
        assertEquals("0 of 32 days (endOfEating 0) checked; checked through 2027-02-06 before it", bare.describe(ramadan, bare.ramadanGaps(ramadan)))
    }

    @Test
    fun `the description names at most five short units`() {
        val units = ('a'..'h').map { UnitSummary("$it", "$it", listOf(DateRun.parse("2026-01-01..2026-12-31"))) }
        val stamp = StampSummary("zz", setOf("B"), LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), 0, listOf(DateRun.parse("2026-01-01..2026-12-31")), units)
        val ramadan = Ramadan.of(1448)
        assertEquals(
            "8 of 8 units short (a 0 of 32 days, b 0 of 32 days, c 0 of 32 days, d 0 of 32 days, e 0 of 32 days and 3 more); the short units checked through 2026-12-31 before it",
            stamp.describe(ramadan, stamp.ramadanGaps(ramadan)),
        )
    }

    @Test
    fun `the last day checked before it is the short units' own, never the entry's`() {
        // Review of 12 Nov 2026: eg.esa's own runs reach 31 Dec 2026 while 28 short units stop on 30 Nov.
        val ramadan = Ramadan.of(1448)
        val whole = listOf(DateRun.parse("2026-01-01..2027-12-31"))
        val covered = UnitSummary("cairo", "Cairo", whole, Ramadan.EVENTS.associateWith { whole })
        val nov = UnitSummary("aswan", "Aswan", listOf(DateRun.parse("2026-01-01..2026-11-30")))
        val dec = UnitSummary("luxor", "Luxor", listOf(DateRun.parse("2026-01-01..2026-12-15")))
        val stamp = StampSummary("eg.x", setOf("B"), LocalDate(2026, 1, 1), LocalDate(2027, 12, 31), 0, whole, listOf(covered, nov, dec))
        assertEquals(
            "2 of 3 units short (aswan 0 of 32 days, luxor 0 of 32 days); the short units checked through 2026-11-30 to 2026-12-15 before it",
            stamp.describe(ramadan, stamp.ramadanGaps(ramadan)),
        )
        val never = UnitSummary("siwa", "Siwa", emptyList())
        val alone = stamp.copy(units = listOf(covered, never))
        assertEquals("1 of 2 units short (siwa 0 of 32 days); no short unit checked before it", alone.describe(ramadan, alone.ramadanGaps(ramadan)))
    }
}
