package world.taqwa.timetables.gate

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ruling R299 ([Repeats]) on an invented archive: a table that prints, day for day, what another of the same entry and
 * place printed on the same month and day of an earlier year is that table again. It is no second published table, and
 * where either is a fit cell both are; its days still count. Rules only, never a figure from a real table.
 */
class RepeatsTest {

    private val dir: File = createTempDirectory("repeats").toFile().also { it.deleteOnExit() }
    private val roots = OfficialRoots.of(dir)
    private val header = GateManifest.HEADER.joinToString("\t")

    /** A month of an invented town's table under [year]'s dates; [change] moves one day's Dhuhr a minute. */
    private fun table(path: String, year: Int, days: IntRange = 1..31, change: Int? = null) {
        val file = dir.resolve(path).also { it.parentFile.mkdirs() }
        file.writeText(
            (listOf("# An invented town's table.") + days.map { d ->
                "%d-03-%02d 05:%02d 06:20 12:%02d 15:40 18:30 19:45".format(year, d, 10 + d % 40, if (d == change) 21 else 20)
            }).joinToString("\n"),
        )
    }

    private fun row(path: String, split: String = "test", lat: String = "1.3") =
        "$path\txx.auth\t$lat\t103.8\tAsia/Singapore\tF S D A M I\tdaily\tstandard\t$split\t"

    private fun manifest(vararg lines: String) = GateManifest.parse("xx-auth.tsv", (listOf(header) + lines).joinToString("\n"))

    private val y2026 = "pinned/xx-auth/2026-11-12/town-2026.txt"
    private val y2027 = "pinned/xx-auth/2026-11-12/town-2027.txt"
    private val y2028 = "pinned/xx-auth/2026-11-12/town-2028.txt"
    private val changed = "pinned/xx-auth/2026-11-12/changed-2027.txt"
    private val week = "pinned/xx-auth/2026-11-12/week-2027.txt"
    private val base = "pinned/xx-auth/2026-11-12/changed-2026.txt"
    private val weekBase = "pinned/xx-auth/2026-11-12/week-2026.txt"

    init {
        table(y2026, 2026)
        table(y2027, 2027)
        table(y2028, 2028)
        table(base, 2026, change = 5)
        table(changed, 2027, change = 17)
        table(weekBase, 2026, 1..10)
        table(week, 2027, 1..10)
    }

    private fun repeats(m: GateManifest) = Repeats(m.rows, Gate.printedDays(roots))

    @Test
    fun `a later year printed day for day is a repeat of the first, in whatever order the files are read`() {
        val m = manifest(row(y2026, "fit"), row(y2027), row(y2028), row(base, "fit"), row(changed), row(weekBase, "fit"), row(week))
        for (rows in listOf(m.rows, m.rows.reversed())) {
            val r = Repeats(rows, Gate.printedDays(roots))
            assertEquals(mapOf("xx.auth|$y2027" to "xx.auth|$y2026", "xx.auth|$y2028" to "xx.auth|$y2026"), r.all)
            assertEquals(setOf("xx.auth|$y2026", "xx.auth|$y2027", "xx.auth|$y2028"), r.families.getValue("xx.auth|$y2026"))
        }
        val r = repeats(m)
        // One day printed otherwise is a new table; a week alone is too short to tell.
        assertFalse(r.isRepeat(m.rows.single { it.path == changed }))
        assertFalse(r.isRepeat(m.rows.single { it.path == week }))
        // Nor at another place: a repeat is the same entry's table at the same point.
        val apart = manifest(row(y2026, "fit"), row(y2027, lat = "1.4"))
        assertTrue(repeats(apart).all.isEmpty())
        // Two captures of the first year are one family with the year that repeats them, the earliest path its original.
        val recapture = "pinned/xx-auth/2026-11-19/town-2026.txt"
        table(recapture, 2026)
        val both = manifest(row(y2026), row(recapture), row(y2027))
        assertEquals(setOf("xx.auth|$y2026", "xx.auth|$recapture", "xx.auth|$y2027"), repeats(both).families.values.single())
        assertEquals(mapOf("xx.auth|$y2027" to "xx.auth|$y2026"), repeats(both).all)
        // Without the archive nothing is a repeat.
        assertNull(Repeats(m.rows, null).original(m.rows.single { it.path == y2027 }))
    }

    @Test
    fun `a repeat of a fitted table is fitted, and a fitted repeat fits its original`() {
        val m = manifest(row(y2026, "fit"), row(y2027), row(base, "fit"), row(changed))
        val counted = Counted(m, Gate.columnDays(roots), repeats = repeats(m))
        val dhuhr = Event.byKey("dhuhr")!!
        assertFalse(counted.heldOut(m.rows.single { it.path == y2027 }, dhuhr))
        assertFalse(counted.heldOut(m.rows.single { it.path == y2027 }))
        // A new table beside a fitted one stays held out; without the rule the repeat would have been.
        assertTrue(counted.heldOut(m.rows.single { it.path == changed }, dhuhr))
        assertTrue(Counted(m, Gate.columnDays(roots)).heldOut(m.rows.single { it.path == y2027 }, dhuhr))
        // A later year fitted by hand (a `@fitted` line) makes the year it repeats fitted too: the margins saw those numbers.
        val byHand = GateManifest.parse(
            "xx-auth.tsv",
            (listOf("@fitted\txx.auth\t$y2027\t*\tthe 2027 table the margins were widened to", header, row(y2026), row(y2027))).joinToString("\n"),
        )
        val fitted = Counted(byHand, Gate.columnDays(roots), repeats = repeats(byHand))
        assertFalse(fitted.heldOut(byHand.rows.single { it.path == y2026 }, dhuhr))
        assertTrue(Counted(byHand, Gate.columnDays(roots)).heldOut(byHand.rows.single { it.path == y2026 }, dhuhr))
    }

    @Test
    fun `a repeated year is no second published table, and its days still count`() {
        val m = manifest(row(y2026), row(y2027), row(y2028), row(changed))
        val across = AcrossEntries(m.rows, Gate.columnDays(roots), roots::file, repeats(m))
        // The town's table once, whatever years print it, and the changed table: two.
        assertEquals(2, across.tables("xx.auth"))
        assertEquals(4, AcrossEntries(m.rows, Gate.columnDays(roots), roots::file).tables("xx.auth"))
        // Every date compared still counts: each year's row is the one its own table's days are counted at.
        val counted = Counted(m, Gate.columnDays(roots), repeats = repeats(m))
        assertTrue(m.rows.all { counted.isOwn(it) })
        assertEquals(2, counted.tables)
    }
}
