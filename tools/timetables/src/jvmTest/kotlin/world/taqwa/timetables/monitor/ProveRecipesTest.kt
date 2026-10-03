package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.OfficialRoots
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How `prove` plans each recipe kind, on invented tables and invented entries (no official time, ruling
 * R69; the gate does not run here, so the entries need no registry): a `same` clone, a `month` cut read
 * `from` another source, an `excused` member row, `all` points, the family across a new year's key, and
 * the captures no recipe target picks up.
 */
class ProveRecipesTest {

    private val official: File = Files.createTempDirectory("prove-plan-official").toFile()
    private val work: File = Files.createTempDirectory("prove-plan-gate").toFile()
    private val gateDir = work.resolve("gate").apply { mkdirs() }
    private val date = LocalDate(2026, 10, 5)
    private val header6 = "path\tentry\tlat\tlon\tzone\tcolumns\tformat\tschool\tsplit\tnote\tmember\n"

    @AfterTest
    fun cleanUp() {
        official.deleteRecursively()
        work.deleteRecursively()
    }

    /** An invented daily table: [days] days from [from], six invented times a day. */
    private fun table(path: String, from: LocalDate, days: Int, comment: String = "# invented for the test") {
        val lines = (0 until days).map { i ->
            val d = from.plus(i, DateTimeUnit.DAY)
            "$d 05:${10 + i % 40} 06:30 12:40 15:50 18:45 20:00"
        }
        official.resolve(path).apply { parentFile.mkdirs() }.writeText(comment + "\n" + lines.joinToString("\n") + "\n")
    }

    private fun capture(source: String, key: String, columns: String = "F S D As Ah I", entry: String? = null) = MonitorTable(
        source, key, "archive/tables/monitor/$source/$key.txt", "$key (invented)", null, null, "Africa/Johannesburg", null, "ZA",
        entry, null, columns, "daily", "standard", "hash", "2026-10-05", "",
    )

    private fun recipes(vararg lines: String) = Recipes.parse(Recipes.HEADER.joinToString("\t") + "\n" + lines.joinToString("\n") + "\n")

    private fun plan(recipes: List<Recipe>, index: List<MonitorTable>) = Prove(
        TestPaths.repoRoot, official, date, recipes, index, gateDir = gateDir, stampsDir = work.resolve("stamps"),
        roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official")),
    ).plan()

    @Test
    fun `a same clone keeps its family's columns, member and point, and is pinned under the run's date`() {
        table("archive/tables/pinned/src-e/2026-09-01/paris.txt", LocalDate(2026, 9, 1), 5)
        table("archive/tables/monitor/src-e/paris.txt", LocalDate(2026, 10, 1), 10)
        gateDir.resolve("x-cautious.tsv").writeText(
            header6 + "archive/tables/pinned/src-e/2026-09-01/paris.txt\tx.c\t48.85\t2.35\tEurope/Paris\tF+E S D A - I\tdaily\tstandard\ttest\tParis, the monitor capture of 1 Sep 2026\tsrc.e\n",
        )
        val (planned, left) = plan(recipes("src-e\t*\tx-cautious.tsv\tsame\t*\tsame\tall\t-"), listOf(capture("src-e", "paris", columns = "F S D A M I")))
        val p = planned.single()
        assertEquals("archive/tables/pinned/src-e/2026-10-05/paris.txt", p.path)
        assertEquals("x.c", p.entry)
        assertEquals("src.e", p.member)
        assertEquals("48.85,2.35", p.point)
        assertEquals("F+E S D A - I", p.cells["columns"])
        assertEquals("Europe/Paris", p.cells["zone"])
        assertEquals("test", p.cells["split"])
        assertEquals("Paris, the monitor capture of 5 Oct 2026", p.cells["note"])
        assertEquals(10, p.days.size)
        assertTrue(left.isEmpty(), left.toString())
    }

    @Test
    fun `a month member row read from another source is cut to the key's month, named for it, and planned at all its points`() {
        // The member's rows read a slice of the from-source's table by the gate file's own convention (a `-` column).
        table("archive/tables/split/j-sep.txt", LocalDate(2026, 9, 1), 30)
        table("archive/tables/monitor/src-j/cpt-2026.txt", LocalDate(2026, 9, 1), 61)
        table("archive/tables/monitor/src-m/cape-2026-10.txt", LocalDate(2026, 10, 1), 31)
        gateDir.resolve("z.tsv").writeText(
            header6 +
                "archive/tables/split/j-sep.txt\tz.c\t-33.9\t18.4\tAfrica/Johannesburg\tF S D A - I\tdaily\tstandard\ttest\tCape Town, the monitor capture of 1 Sep 2026 (September 2026)\tz.j\n" +
                "archive/tables/split/j-sep.txt\tz.c\t-33.95\t18.45\tAfrica/Johannesburg\tF S D A - I\tdaily\tstandard\ttest\tCape Town, the monitor capture of 1 Sep 2026 (September 2026)\tz.j\n",
        )
        val (planned, left) = plan(
            recipes("src-m\tcape-*\tz.tsv\tz.c\tz.j\tall\tmonth\tsrc-j/cpt-{yyyy}"),
            listOf(capture("src-m", "cape-2026-10"), capture("src-j", "cpt-2026")),
        )
        assertEquals(listOf("-33.9,18.4", "-33.95,18.45"), planned.map { it.point }, left.toString())
        for (p in planned) {
            assertEquals("archive/tables/pinned/src-j/2026-10-05/cpt-2026.month-2026-10.txt", p.path)
            assertEquals("z.j", p.member)
            assertEquals("F S D A - I", p.cells["columns"], p.family)
            assertEquals(31, p.days.size)
            assertTrue(p.days.all { it.monthNumber == 10 && it.year == 2026 })
            assertTrue(p.content.startsWith("# prove, 2026-10-05: archive/tables/monitor/src-j/cpt-2026.txt"), p.content.lines().first())
            assertTrue(p.content.lines().none { it.startsWith("2026-09-") || it.startsWith("2026-11-") })
            // The family's note gives a date range of its own days, so it is not copied.
            assertEquals("cpt-2026 (invented), the monitor capture of 5 Oct 2026 (prove)", p.cells["note"])
        }
    }

    @Test
    fun `a new year's key inherits the member's columns from the row reading the old key, never the index's`() {
        // The member's November row reads the from-source's 2026 key; January 2027 shares no date with it.
        table("archive/tables/pinned/src-j/2026-11-02/cpt-2026.month-2026-11.txt", LocalDate(2026, 11, 1), 30)
        table("archive/tables/monitor/src-j/cpt-2027.txt", LocalDate(2027, 1, 1), 59)
        table("archive/tables/monitor/src-m/cape-2027-01.txt", LocalDate(2027, 1, 1), 31)
        gateDir.resolve("z.tsv").writeText(
            header6 + "archive/tables/pinned/src-j/2026-11-02/cpt-2026.month-2026-11.txt\tz.c\t-33.9\t18.4\tAfrica/Johannesburg\tF S D A - I\tdaily\tstandard\ttest\tthe member\tz.j\n",
        )
        val (planned, left) = plan(
            recipes("src-m\tcape-*\tz.tsv\tz.c\tz.j\tall\tmonth\tsrc-j/cpt-{yyyy}"),
            listOf(capture("src-m", "cape-2027-01"), capture("src-j", "cpt-2026"), capture("src-j", "cpt-2027")),
        )
        val p = planned.single()
        assertEquals("F S D A - I", p.cells["columns"], p.family)
        assertTrue("another capture of the same source" in p.family, p.family)
        assertEquals("archive/tables/pinned/src-j/2026-10-05/cpt-2027.month-2027-01.txt", p.path)
        assertEquals(31, p.days.size)
        assertTrue(left.isEmpty(), left.toString())
    }

    @Test
    fun `a member row whose only family reads another layout's slice takes the member's latest row of the same width`() {
        table("archive/tables/split/j-2026-q4.txt", LocalDate(2026, 10, 1), 10)
        table("archive/tables/monitor/src-j/cpt-2027.txt", LocalDate(2027, 1, 1), 59)
        table("archive/tables/monitor/src-m/cape-2027-01.txt", LocalDate(2027, 1, 1), 31)
        gateDir.resolve("z.tsv").writeText(
            header6 + "archive/tables/split/j-2026-q4.txt\tz.c\t-33.9\t18.4\tAfrica/Johannesburg\tF S D A - I\tdaily\tstandard\ttest\tthe member\tz.j\n",
        )
        val (planned, _) = plan(
            recipes("src-m\tcape-*\tz.tsv\tz.c\tz.j\tall\tmonth\tsrc-j/cpt-{yyyy}"),
            listOf(capture("src-m", "cape-2027-01"), capture("src-j", "cpt-2027")),
        )
        val p = planned.single()
        assertEquals("F S D A - I", p.cells["columns"], p.family)
        assertTrue("the member's latest row of the same width" in p.family, p.family)
    }

    @Test
    fun `an excused member row leaves out the days its @excuse lines name there, and keeps the rest`() {
        table("archive/tables/pinned/src-t/2026-09-01/t.txt", LocalDate(2026, 9, 1), 5)
        table("archive/tables/monitor/src-t/t.txt", LocalDate(2026, 10, 1), 6)
        gateDir.resolve("t.tsv").writeText(
            header6 +
                "@excuse\tt.c\tt.i\t*\t2026-10-03..2026-10-04\tfault\tthe member's table prints these two days wrongly (invented)\n" +
                "archive/tables/pinned/src-t/2026-09-01/t.txt\tt.c\t43.65\t-79.38\tAmerica/Toronto\tF S D A M I\tdaily\tstandard\ttest\tthe member\tt.i\n" +
                "archive/tables/pinned/src-t/2026-09-01/t.txt\tt.c\t43.7\t-79.4\tAmerica/Toronto\tF S D A M I\tdaily\tstandard\ttest\tthe member\tt.i\n",
        )
        val (planned, left) = plan(recipes("src-t\t*\tt.tsv\tt.c\tindex\tall\texcused\t-"), listOf(capture("src-t", "t", columns = "F S D A M I", entry = "t.i")))
        assertEquals(2, planned.size, left.toString())
        for (p in planned) {
            assertEquals("archive/tables/pinned/src-t/2026-10-05/t.excused-t.i.txt", p.path)
            assertEquals("t.i", p.member)
            assertEquals(listOf(1, 2, 5, 6), p.days.map { it.dayOfMonth })
            assertTrue(p.content.lines().none { it.startsWith("2026-10-03") || it.startsWith("2026-10-04") })
        }
    }

    @Test
    fun `a capture no recipe target picks up is reported, not skipped in silence`() {
        table("archive/tables/monitor/src-e/oslo.txt", LocalDate(2026, 10, 1), 3)
        gateDir.resolve("x-cautious.tsv").writeText(header6)
        val (planned, left) = plan(recipes("src-e\t*\tx-cautious.tsv\tsame\t*\tsame\tall\t-"), listOf(capture("src-e", "oslo")))
        assertTrue(planned.isEmpty())
        assertEquals("src-e/oslo", left.single().label)
        assertTrue("no recipe line reads it" in left.single().reason, left.single().reason)
    }
}
