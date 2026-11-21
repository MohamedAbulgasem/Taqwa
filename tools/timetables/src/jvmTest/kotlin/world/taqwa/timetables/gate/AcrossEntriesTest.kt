package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The review round of 14 Nov 2026 ([AcrossEntries]) on an invented archive: a printed day two entries read is counted
 * at one of them, and a published table is one an authority printed, counted once. Rules only.
 */
class AcrossEntriesTest {

    private val dir: File = createTempDirectory("across-entries").toFile().also { it.deleteOnExit() }
    private val roots = OfficialRoots.of(dir)
    private val header = (GateManifest.HEADER + "member").joinToString("\t")

    private fun table(path: String, days: IntRange, head: String = "# A town's table, as its authority prints it.", shift: Int = 0) {
        val file = dir.resolve(path).also { it.parentFile.mkdirs() }
        file.writeText((listOf(head) + days.map { "2026-03-%02d 05:%02d 06:20 12:%02d 15:40 18:30 19:45".format(it, 10 + it, 20 + shift) }).joinToString("\n"))
    }

    private fun row(path: String, entry: String, member: String = "") = "$path\t$entry\t1.3\t103.8\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t\t$member"

    private fun rows(source: String, vararg lines: String) = GateManifest.parse(source, (listOf(header) + lines).joinToString("\n")).rows

    private fun day(of: Int) = LocalDate(2026, 3, of).toEpochDays().toInt()

    private val first = "pinned/xx-auth/2026-11-01/town.txt"
    private val again = "pinned/xx-auth/2026-11-08/town.txt"
    private val copy = "round/xx/town-copy.txt"
    private val village = "manual/xx-auth/village.txt"
    private val mapped = "manual/xx-auth/perpetual-2027.txt"
    private val other = "manual/xx-auth/other.txt"

    init {
        table(first, 1..5)
        table(again, 3..8)
        // The same lines under another folder, a town worked out from the first, a perpetual table laid on a year.
        table(copy, 1..5)
        table(village, 1..5, "# Village: DERIVED, the town's times + 2 min on every time, as the footnote prints.", shift = 2)
        table(mapped, 1..5, "# The authority's perpetual table, mapped onto 2027 (the same month and day).", shift = 4)
        table(other, 1..5, shift = 6)
    }

    private val own = rows("xx-auth.tsv", row(first, "xx.auth"), row(again, "xx.auth"), row(village, "xx.auth"), row(mapped, "xx.auth"))
    private val cautious = rows("xx-cautious.tsv", row(again, "xx.cautious", "xx.auth"))
    private val region = rows("safe-default-xx.tsv", row(first, "default.safe.xx"), row(copy, "default.safe.xx"), row(other, "default.safe.xx"))

    private fun across(rows: List<GateRow>) = AcrossEntries(rows, Gate.columnDays(roots), roots::file)

    @Test
    fun `a publication is counted at the authority's own entry, in whatever order the files are read`() {
        for (all in listOf(own + cautious + region, region + cautious + own)) {
            val across = across(all)
            for (reader in listOf(own[0], own[1], cautious[0], region[0])) assertEquals("xx.auth", across.owner(reader))
            assertEquals("default.safe.xx", across.owner(region[2]))
            // A cautious entry before a region's default, where the authority's own entry does not read the table.
            assertEquals("xx.cautious", across(cautious + rows("safe-default-xx.tsv", row(again, "default.safe.xx"))).owner(cautious[0]))
        }
    }

    @Test
    fun `a day another entry counts is shared, and a day the later reader holds alone is its own`() {
        val across = across(own + cautious + region)
        // The authority's own rows share nothing: they are where the days are counted.
        for (row in own) for (d in 1..8) assertFalse(across.shared(row, day(d)))
        // The cautious entry reads the second capture (3..8): the authority holds every one of those days.
        for (d in 3..8) assertTrue(across.shared(cautious[0], day(d)))
        // The region reads the first capture: shared where the authority's rows hold the day, in either capture.
        for (d in 1..8) assertTrue(across.shared(region[0], day(d)))
        assertFalse(across.shared(region[0], day(9)))
        // A table no other entry reads is its own, and without the archive nothing is shared.
        assertFalse(across.shared(region[2], day(2)))
        assertFalse(AcrossEntries(own + cautious + region).shared(cautious[0], day(4)))
        // Where the authority's entry holds fewer days than the later reader, the rest are the reader's own.
        val short = rows("xx-auth.tsv", row(first, "xx.auth"))
        val later = rows("xx-cautious.tsv", row(again, "xx.cautious", "xx.auth"))
        val few = across(short + later)
        assertTrue(few.shared(later[0], day(5)))
        assertFalse(few.shared(later[0], day(6)))
    }

    @Test
    fun `a published table is one an authority printed, counted once`() {
        val across = across(own + cautious + region)
        // The town's table, captured twice: one. The village and the mapped year were worked out: none.
        assertEquals(1, across.tables("xx.auth"))
        assertEquals(0, across.tables("xx.cautious"))
        // The region's copy prints no line the town's table does not: none. Its other table is its own.
        assertEquals(1, across.tables("default.safe.xx"))
        // Two publications printing the same lines are one, whichever is read first.
        assertEquals(1, listOf(region.take(2), region.take(2).reversed()).map { across(it).tables("default.safe.xx") }.distinct().single())
        // Without the archive no table is counted: a stamp is written only where the tables are held.
        assertEquals(0, AcrossEntries(own).tables("xx.auth"))
    }

    @Test
    fun `the gate writes an entry's shared days and tables the same over a part of the gate as over the whole`() {
        val a = "open/SG-MUIS/muis-2026-02-a.txt"
        val fixture = OfficialRoots.of(File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI()))
        val plain = GateManifest.HEADER.joinToString("\t")
        fun file(source: String, entry: String) = GateManifest.parse(source, "$plain\n$a\t$entry\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\t")
        val muis = file("sg-muis.tsv", "sg.muis")
        val stats = Gate(fixture).evaluate(muis).entries.getValue("sg.muis")
        assertEquals(listOf(5, 0, 1), listOf(stats.tableDayCount, stats.sharedDayCount, stats.tables))
        val stamp = Stamps.stamp(stats, "core")
        assertEquals(1, stamp["tables"])
        assertFalse("sharedDays" in stamp, "written only where there are some")
    }
}
