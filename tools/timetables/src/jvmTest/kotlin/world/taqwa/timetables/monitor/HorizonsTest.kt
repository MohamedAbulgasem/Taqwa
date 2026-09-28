package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.ClockChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec §5's monitor, the horizons (brief P, item 5): attention only inside each window, on invented
 * dates and invented stamps. Nothing here reads the archive or the committed stamps.
 */
class HorizonsTest {

    private fun stamp(id: String, classes: String, first: String, last: String, broken: Int = 0) =
        StampSummary(id, classes.split('/').toSet(), LocalDate.parse(first), LocalDate.parse(last), broken)

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
        assertTrue("aa.one" in items.single().details.joinToString(), items.toString())
        assertTrue("bb.two" !in items.single().details.joinToString(), items.toString())
        assertTrue("cc.three" !in items.single().details.joinToString(), items.toString())
        assertTrue("dd.four" !in items.single().details.joinToString(), items.toString())
        // Inside Ramadan itself it still stands; after it, it is gone.
        assertEquals(1, Horizons.check(LocalDate(2027, 2, 20), stamps, uqLast, changes).count { "Ramadan" in it.title })
        assertTrue(Horizons.check(LocalDate(2027, 3, 15), stamps, uqLast, changes).none { "Ramadan" in it.title })
    }

    @Test
    fun `the next tabular ramadan is found from any date`() {
        assertEquals(LocalDate(2027, 2, 8), Horizons.nextRamadan(LocalDate(2026, 10, 1)))
        // Inside Ramadan the current one is returned, not the next.
        assertEquals(LocalDate(2027, 2, 8), Horizons.nextRamadan(LocalDate(2027, 2, 20)))
    }
}
