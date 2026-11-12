package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.ClockChange
import world.taqwa.timetables.gate.DateRun
import world.taqwa.timetables.gate.Ramadan
import world.taqwa.timetables.gate.StampSummary
import world.taqwa.timetables.gate.UnitSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec §5's monitor, the horizons (brief P, item 5): attention only inside each window, on invented
 * dates and invented stamps. Nothing here reads the archive or the committed stamps.
 */
class HorizonsTest {

    private fun runs(vararg text: String) = text.map(DateRun::parse)

    /** [runs] as every Ramadan event's own runs (ruling R148). */
    private fun byEvent(runs: List<DateRun>) = Ramadan.EVENTS.associateWith { runs }

    /** A unit checked on [runs] for every Ramadan event. */
    private fun unit(id: String, name: String, runs: List<DateRun>) = UnitSummary(id, name, runs, byEvent(runs))

    /** A stamp checked on every day of `first`..`last` unless [checked] says otherwise, with [units] of their own runs. */
    private fun stamp(
        id: String,
        classes: String,
        first: String,
        last: String,
        broken: Int = 0,
        checked: List<DateRun> = runs("$first..$last"),
        units: List<UnitSummary> = emptyList(),
    ) = StampSummary(id, classes.split('/').toSet(), LocalDate.parse(first), LocalDate.parse(last), broken, checked, units, byEvent(checked))

    /** A green world: every proof runs a year ahead, nothing ends soon. */
    private val fresh = listOf(
        stamp("aa.one", "A", "2025-01-01", "2028-12-31"),
        stamp("bb.two", "B", "2025-01-01", "2028-12-31"),
        stamp("cc.three", "C", "2025-01-01", "2028-12-31"),
        stamp("gb.london.lupt", "B", "2026-01-01", "2027-12-31"),
        stamp("no.irn", "D (authority)", "2026-01-01", "2027-12-31"),
    )
    private val uqLast = LocalDate(2030, 12, 31)
    private val changes = listOf(
        ClockChange("morocco", setOf("Africa/Casablanca"), LocalDate(2026, 9, 20), 0, -1, LocalDate(2029, 9, 20)),
    )

    private fun check(today: String, stamps: List<StampSummary> = fresh) =
        Horizons.check(LocalDate.parse(today), stamps, uqLast, changes)

    @Test
    fun `a quiet day far from every horizon raises nothing`() {
        assertEquals(emptyList(), check("2026-10-05"))
    }

    @Test
    fun `umm al qura dates raise attention from six months before they end and not before`() {
        // Six months before 31 December is 30 June.
        assertTrue(check("2030-06-29").none { "Umm al-Qura" in it.title })
        val items = check("2030-06-30")
        assertEquals(1, items.count { "Umm al-Qura" in it.title }, items.toString())
        assertTrue(items.first { "Umm al-Qura" in it.title }.kind == Kind.HORIZON)
    }

    @Test
    fun `a clock change raises attention eight weeks before its table entry expires and stops after`() {
        assertTrue(check("2029-07-25").none { "clock" in it.title.lowercase() })
        assertEquals(1, check("2029-07-26").count { "clock" in it.title.lowercase() })
        assertEquals(1, check("2029-09-19").count { "clock" in it.title.lowercase() })
        assertTrue(check("2029-09-20").none { "clock" in it.title.lowercase() })
    }

    @Test
    fun `london unified's next year is wanted from the first of december`() {
        val stamps = fresh.map { if (it.entryId == "gb.london.lupt") it.copy(last = LocalDate(2026, 12, 31)) else it }
        assertTrue(Horizons.check(LocalDate(2026, 11, 30), stamps, uqLast, changes).none { "London" in it.title })
        val items = Horizons.check(LocalDate(2026, 12, 1), stamps, uqLast, changes)
        assertEquals(1, items.count { "London" in it.title }, items.toString())
        // Held: nothing.
        assertTrue(Horizons.check(LocalDate(2026, 12, 1), fresh, uqLast, changes).none { "London" in it.title })
    }

    @Test
    fun `irn's next year is wanted from the first of november`() {
        val stamps = fresh.map { if (it.entryId == "no.irn") it.copy(last = LocalDate(2026, 12, 31)) else it }
        assertTrue(Horizons.check(LocalDate(2026, 10, 31), stamps, uqLast, changes).none { "IRN" in it.title })
        assertEquals(1, Horizons.check(LocalDate(2026, 11, 1), stamps, uqLast, changes).count { "IRN" in it.title })
    }

    @Test
    fun `a next-year rule keeps asking for the current year after the first of january`() {
        // Review I1: proven through 2026 only, on 5 January 2027 the 2027 table is still wanted.
        val lastYear = fresh.map { if (it.entryId == "gb.london.lupt" || it.entryId == "no.irn") it.copy(last = LocalDate(2026, 12, 31)) else it }
        val items = Horizons.check(LocalDate(2027, 1, 5), lastYear, uqLast, changes)
        assertEquals(1, items.count { "London" in it.title }, items.toString())
        assertEquals(1, items.count { "IRN" in it.title }, items.toString())
        assertTrue(items.first { "London" in it.title }.details.any { "a proof through 2027-12-31" in it }, items.toString())
        // Proven through 2027: quiet until 1 December 2027, when 2028 is wanted.
        assertTrue(Horizons.check(LocalDate(2027, 1, 5), fresh, uqLast, changes).none { "London" in it.title || "IRN" in it.title })
        assertTrue(Horizons.check(LocalDate(2027, 11, 30), fresh, uqLast, changes).none { "London" in it.title })
        val december = Horizons.check(LocalDate(2027, 12, 1), fresh, uqLast, changes)
        assertEquals(1, december.count { "London" in it.title }, december.toString())
        assertTrue(december.first { "London" in it.title }.details.any { "a proof through 2028-12-31" in it }, december.toString())
    }

    @Test
    fun `a missing stamp for a next-year rule is reported too`() {
        val stamps = fresh.filter { it.entryId != "gb.london.lupt" }
        assertEquals(1, Horizons.check(LocalDate(2026, 12, 15), stamps, uqLast, changes).count { "London" in it.title })
    }

    @Test
    fun `a proof older than twelve months is stale`() {
        val stamps = fresh + stamp("dd.old", "D (authority)", "2024-01-01", "2025-09-30")
        assertTrue(check("2026-09-30", stamps).none { "dd.old" in it.title })
        val items = check("2026-10-01", stamps)
        assertEquals(1, items.count { "dd.old" in it.title }, items.toString())
    }

    @Test
    fun `a stale proof waits while a manual source names the entry with a due date still to come`() {
        val stamps = fresh + stamp("dd.old", "D (authority)", "2024-01-01", "2025-09-30")
        val deferred = mapOf("dd.old" to LocalDate(2027, 2, 1))
        assertTrue(Horizons.check(LocalDate(2026, 10, 1), stamps, uqLast, changes, deferred = deferred).none { "dd.old" in it.title })
        assertEquals(1, Horizons.check(LocalDate(2027, 2, 1), stamps, uqLast, changes, deferred = deferred).count { "dd.old" in it.title })
    }

    @Test
    fun `a unit whose own rows stopped a year ago is stale while its entry's envelope is fresh`() {
        // Ruling R145: the entry runs to 2026-12-31 through its first unit; the second was last checked in 2025.
        val twoUnits = stamp(
            "ee.units", "B", "2024-01-01", "2026-12-31",
            units = listOf(
                unit("north", "North", runs("2024-01-01..2026-12-31")),
                unit("south", "South", runs("2024-01-01..2025-09-30")),
            ),
        )
        val stamps = fresh + twoUnits
        assertTrue(check("2026-09-30", stamps).none { "ee.units" in it.title })
        val items = check("2026-10-01", stamps).filter { "ee.units" in it.title }
        assertEquals(1, items.size, items.toString())
        val item = items.single()
        assertTrue("south" in item.title && "2025-09-30" in item.title, item.toString())
        assertTrue("north" !in item.title, item.toString())
        assertEquals("horizon:stale:ee.units/south", item.key)
        assertTrue(item.details.any { "2026-12-31" in it }, item.toString())
        // The entry stale as a whole: one line for it, none per unit.
        val whole = stamp("ff.old", "B", "2024-01-01", "2025-06-30", units = listOf(unit("u", "U", runs("2024-01-01..2025-06-30"))))
        val old = check("2026-10-01", fresh + whole).filter { "ff.old" in it.title }
        assertEquals(listOf("horizon:stale:ff.old"), old.map { it.key }, old.toString())
        // A manual source's due date still to come quiets the unit too.
        assertTrue(Horizons.check(LocalDate(2026, 10, 1), stamps, uqLast, changes, deferred = mapOf("ee.units" to LocalDate(2027, 2, 1))).none { "ee.units" in it.title })
    }

    @Test
    fun `eight weeks before a tabular ramadan the a and b entries whose proof stops short are listed`() {
        // Tabular 1 Ramadan 1448 is 8 February 2027 (spec §7); the window opens 56 days before.
        val stamps = listOf(
            stamp("aa.one", "A", "2025-01-01", "2027-01-31"),
            stamp("bb.two", "B", "2025-01-01", "2027-12-31"),
            stamp("cc.three", "C", "2025-01-01", "2027-01-31"),
            stamp("dd.four", "D (authority)", "2025-01-01", "2027-01-31"),
            stamp("gb.london.lupt", "B", "2026-01-01", "2027-12-31"),
            stamp("no.irn", "D (authority)", "2026-01-01", "2027-12-31"),
        )
        assertTrue(Horizons.check(LocalDate(2026, 12, 13), stamps, uqLast, changes).none { "Ramadan" in it.title })
        val items = Horizons.check(LocalDate(2026, 12, 14), stamps, uqLast, changes).filter { "Ramadan" in it.title }
        assertEquals(1, items.size, items.toString())
        assertTrue("Ramadan 1448" in items.single().title && "2027-02-08" in items.single().title, items.toString())
        assertTrue("from 2027-02-07 through 2027-03-10" in items.single().title, items.toString())
        assertTrue("aa.one" in items.single().details.joinToString(), items.toString())
        assertTrue("bb.two" !in items.single().details.joinToString(), items.toString())
        assertTrue("cc.three" !in items.single().details.joinToString(), items.toString())
        assertTrue("dd.four" !in items.single().details.joinToString(), items.toString())
        assertTrue(items.single().next!!.contains("-Pramadan=1448"), items.toString())
        // Inside Ramadan itself it still stands; after it, it is gone.
        assertEquals(1, Horizons.check(LocalDate(2027, 2, 20), stamps, uqLast, changes).count { "Ramadan" in it.title })
        assertTrue(Horizons.check(LocalDate(2027, 3, 15), stamps, uqLast, changes).none { "Ramadan" in it.title })
    }

    @Test
    fun `the ramadan horizon reads the checked runs and not the envelope that spans them`() {
        // Muscat's shape (ruling R145): first 2021, last 2031, and no run holds a day of February or March 2027.
        val muscat = stamp(
            "om.mara", "B", "2021-01-01", "2031-09-30",
            checked = runs("2021-01-01..2021-01-31", "2021-07-01..2021-07-31", "2026-01-01..2026-12-31", "2031-03-01..2031-03-31", "2031-09-01..2031-09-30"),
            units = listOf(unit("muscat", "Muscat", runs("2021-01-01..2021-01-31", "2021-07-01..2021-07-31", "2026-01-01..2026-12-31", "2031-03-01..2031-03-31", "2031-09-01..2031-09-30"))),
        )
        val items = Horizons.check(LocalDate(2027, 1, 10), fresh + muscat, uqLast, changes).filter { "Ramadan" in it.title }
        assertEquals(1, items.size, items.toString())
        val line = items.single().details.single { it.startsWith("om.mara:") }
        assertEquals("om.mara: 1 of 1 unit short (muscat 0 of 32 days); the short units checked through 2026-12-31 before it", line)
        // A run that holds every day of the month, with a hole elsewhere, covers it.
        val covered = muscat.copy(
            checked = runs("2026-01-01..2026-12-31", "2027-02-01..2027-03-31"),
            units = listOf(unit("muscat", "Muscat", runs("2026-01-01..2026-12-31", "2027-02-01..2027-03-31"))),
        )
        assertTrue(Horizons.check(LocalDate(2027, 1, 10), fresh + covered, uqLast, changes).none { "Ramadan" in it.title })
        // One day of the month missing at the unit is a hole.
        val holed = covered.copy(units = listOf(unit("muscat", "Muscat", runs("2026-01-01..2026-12-31", "2027-02-01..2027-02-20", "2027-02-22..2027-03-31"))))
        val hole = Horizons.check(LocalDate(2027, 1, 10), fresh + holed, uqLast, changes).filter { "Ramadan" in it.title }
        assertEquals("om.mara: 1 of 1 unit short (muscat 31 of 32 days); the short units checked through 2027-02-06 before it", hole.single().details.single { it.startsWith("om.mara:") })
    }

    @Test
    fun `a unit short of ramadan is listed while the entry's own runs cover it`() {
        // The entry's runs are the union of its rows' days: north was checked in Ramadan, south was not.
        val units = stamp(
            "ee.units", "A", "2025-01-01", "2027-12-31",
            units = listOf(
                unit("north", "North", runs("2025-01-01..2027-12-31")),
                unit("south", "South", runs("2025-01-01..2026-12-31")),
                unit("west", "West", runs("2025-01-01..2026-12-31")),
            ),
        )
        val items = Horizons.check(LocalDate(2027, 1, 10), fresh + units, uqLast, changes).filter { "Ramadan" in it.title }
        assertEquals(1, items.size, items.toString())
        assertEquals(
            "ee.units: 2 of 3 units short (south 0 of 32 days, west 0 of 32 days); the short units checked through 2026-12-31 before it",
            items.single().details.single { it.startsWith("ee.units:") },
        )
        // Every unit covering it: nothing, whatever the envelope says.
        val all = units.copy(units = units.units.map { unit(it.id, it.name, runs("2025-01-01..2027-12-31")) })
        assertTrue(Horizons.check(LocalDate(2027, 1, 10), fresh + all, uqLast, changes).none { "Ramadan" in it.title })
        // Ruling R148: a unit whose days all hold Ramadan while its end of eating was checked on none of them (Pretoria's
        // tables print no Suhoor column) is short, and the line says which event.
        val year = runs("2025-01-01..2027-12-31")
        val pretoria = units.copy(units = listOf(unit("north", "North", year), UnitSummary("pretoria", "Pretoria", year, mapOf("fajr" to year, "maghrib" to year))))
        val shape = Horizons.check(LocalDate(2027, 1, 10), fresh + pretoria, uqLast, changes).filter { "Ramadan" in it.title }
        assertEquals(
            "ee.units: 1 of 2 units short (pretoria 0 of 32 days (endOfEating 0)); the short units checked through 2027-02-06 before it",
            shape.single().details.single { it.startsWith("ee.units:") },
        )
        assertTrue(shape.single().next!!.contains("end of eating"), shape.toString())
        // An entry without units is read by its own runs, and a stamp without any runs covers nothing.
        val none = stamp("gg.none", "A", "2025-01-01", "2027-12-31", checked = emptyList())
        val bare = Horizons.check(LocalDate(2027, 1, 10), fresh + none, uqLast, changes).filter { "Ramadan" in it.title }
        assertEquals("gg.none: 0 of 32 days checked; no day checked before it", bare.single().details.single { it.startsWith("gg.none:") })
    }

    @Test
    fun `the next tabular ramadan is found from any date`() {
        assertEquals(LocalDate(2027, 2, 8), Ramadan.nextFrom(LocalDate(2026, 10, 1)).first)
        assertEquals(1448, Ramadan.nextFrom(LocalDate(2026, 10, 1)).hijriYear)
        // Inside Ramadan the current one is returned, not the next.
        assertEquals(LocalDate(2027, 2, 8), Ramadan.nextFrom(LocalDate(2027, 2, 20)).first)
        assertEquals(LocalDate(2027, 2, 8), Ramadan.nextFrom(LocalDate(2027, 3, 9)).first)
        // Its guard's last day (ruling R146) still belongs to it; the day after, the next.
        assertEquals(1448, Ramadan.nextFrom(LocalDate(2027, 3, 10)).hijriYear)
        assertEquals(1449, Ramadan.nextFrom(LocalDate(2027, 3, 11)).hijriYear)
    }
}
