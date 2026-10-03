package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.OfficialRoots
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `prove` on invented data: the open MUIS fixture (`gate-fixture`, Singapore Open Data Licence) as a
 * held table and as a monitor capture, shifted where a test needs the engine early (never a
 * restricted printed time, ruling R69). The gate files and stamps are a temporary folder's.
 */
class ProveTest {

    private val fixture: File = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
    private val official: File = Files.createTempDirectory("prove-official").toFile()
    private val work: File = Files.createTempDirectory("prove-gate").toFile()
    private val gateDir = work.resolve("gate").apply { mkdirs() }
    private val stampsDir = work.resolve("stamps")
    private val date = LocalDate(2026, 10, 5)

    @AfterTest
    fun cleanUp() {
        official.deleteRecursively()
        work.deleteRecursively()
    }

    private fun lines(name: String) = fixture.resolve("open/SG-MUIS/$name").readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    /** Every time on [lines] moved by [shift] minutes. */
    private fun shifted(lines: List<String>, shift: Int) = lines.map { line ->
        val parts = line.split(' ')
        parts[0] + parts.drop(1).joinToString("") { t ->
            val (h, m) = t.split(':').map(String::toInt)
            val total = (h * 60 + m + shift + 24 * 60) % (24 * 60)
            " %02d:%02d".format(total / 60, total % 60)
        }
    }

    private fun write(path: String, lines: List<String>) {
        official.resolve(path).apply { parentFile.mkdirs() }.writeText("# an invented table for the test\n" + lines.joinToString("\n") + "\n")
    }

    /** A gate file holding the fixture's first five days, and a capture of all ten (moved by [shift]). */
    private fun setUp(shift: Int = 0): List<MonitorTable> {
        write("archive/tables/held/sg-a.txt", lines("muis-2026-02-a.txt"))
        gateDir.resolve("sg-muis.tsv").writeText(
            "# invented\npath\tentry\tlat\tlon\tzone\tcolumns\tformat\tschool\tsplit\tnote\n" +
                "archive/tables/held/sg-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\tthe first five days\n",
        )
        write("archive/tables/monitor/sg-muis/singapore.txt", shifted(lines("muis-2026-02-a.txt") + lines("muis-2026-02-b.txt"), shift))
        return listOf(
            MonitorTable(
                "sg-muis", "singapore", "archive/tables/monitor/sg-muis/singapore.txt", "Singapore (test)", null, null, "Asia/Singapore", null, "SG",
                "sg.muis", null, "F+E S D A M I", "daily", "standard", "hash", "2026-10-05", "",
            ),
        )
    }

    private val recipes = Recipes.parse(
        Recipes.HEADER.joinToString("\t") + "\n" + listOf("sg-muis", "*", "sg-muis.tsv", "index", "-", "index", "all", "-").joinToString("\t") + "\n",
    )

    private fun prove(index: List<MonitorTable>, write: Boolean = true) = Prove(
        TestPaths.repoRoot, official, date, recipes, index, gateDir = gateDir, stampsDir = stampsDir,
        roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official")),
    ).run(write)

    @Test
    fun `a capture with days the gate does not hold is pinned, its row appended with its family's columns, and the stamps written`() {
        val index = setUp()
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(1, outcome.added.size, outcome.leftOut.toString())
        val pinned = "archive/tables/pinned/sg-muis/2026-10-05/singapore.txt"
        assertEquals(listOf(pinned), outcome.pinned)
        assertEquals(official.resolve("archive/tables/monitor/sg-muis/singapore.txt").readText(), official.resolve(pinned).readText())
        val gate = gateDir.resolve("sg-muis.tsv").readText()
        assertTrue("$pinned\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\tSingapore (test), the monitor capture of 5 Oct 2026 (prove)" in gate, gate)
        assertTrue("# prove, 2026-10-05:" in gate, gate)
        assertTrue("sg-muis.tsv:3, whose table prints the same on every day both hold" in outcome.added.single().family, outcome.added.single().family)
        assertEquals(10, outcome.added.single().days.size)
        assertTrue(stampsDir.resolve("sg.muis.json").isFile)
        // Run again: every day is held now, nothing is added and nothing changes.
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val again = prove(index)
        assertNull(again.failure)
        assertEquals(0, again.added.size)
        assertFalse(again.changed)
        assertEquals(before, gateDir.resolve("sg-muis.tsv").readText())
    }

    @Test
    fun `a capture the engine is early against is left out and reported, never loosened, and nothing stays behind`() {
        val index = setUp(shift = +3)
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(0, outcome.added.size)
        assertEquals(1, outcome.leftOut.size)
        assertTrue("early" in outcome.leftOut.single().reason, outcome.leftOut.single().reason)
        assertEquals(before, gateDir.resolve("sg-muis.tsv").readText())
        assertFalse(official.resolve("archive/tables/pinned/sg-muis/2026-10-05/singapore.txt").exists())
        assertFalse(stampsDir.exists())
    }

    @Test
    fun `a dry run checks the rows but keeps nothing`() {
        val index = setUp()
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val outcome = prove(index, write = false)
        assertEquals(1, outcome.added.size)
        assertEquals(before, gateDir.resolve("sg-muis.tsv").readText())
        assertFalse(official.resolve("archive/tables/pinned/sg-muis/2026-10-05/singapore.txt").exists())
        // Not even the dated folder it was written in stays behind.
        assertFalse(official.resolve("archive/tables/pinned/sg-muis").exists())
        assertFalse(stampsDir.exists())
    }

    @Test
    fun `a pinned path that exists with other content is left out and never rewritten`() {
        val index = setUp()
        val pinned = official.resolve("archive/tables/pinned/sg-muis/2026-10-05/singapore.txt").apply { parentFile.mkdirs() }
        pinned.writeText("# another copy\n")
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(0, outcome.added.size)
        assertTrue("never rewritten" in outcome.leftOut.single().reason, outcome.leftOut.single().reason)
        assertEquals("# another copy\n", pinned.readText())
        assertEquals(before, gateDir.resolve("sg-muis.tsv").readText())
    }

    @Test
    fun `a row the gate's reader refuses is left out before any pinned copy is written`() {
        // A capture with no day in common with the held table and no family: its row would take the index's empty zone.
        val index = listOf(setUp().single().copy(zone = ""))
        write("archive/tables/monitor/sg-muis/singapore.txt", lines("muis-2026-02-b.txt").map { it.replaceFirst("2026-02-", "2026-03-") })
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(0, outcome.added.size)
        assertTrue("zone is empty" in outcome.leftOut.single().reason, outcome.leftOut.single().reason)
        assertFalse(official.resolve("archive/tables/pinned").exists())
    }

    @Test
    fun `an entry red before prove whose new rows do not repair it fails, and leaves no pinned copy`() {
        val index = setUp()
        write("archive/tables/held/sg-a.txt", shifted(lines("muis-2026-02-a.txt"), +3))
        val outcome = prove(index)
        assertTrue(outcome.failure != null && "red before prove" in outcome.failure!!, outcome.failure)
        assertFalse(official.resolve("archive/tables/pinned/sg-muis").exists())
        assertFalse(stampsDir.exists())
    }

    @Test
    fun `a gate red before prove that no new row repairs stops it and writes nothing`() {
        setUp()
        write("archive/tables/held/sg-a.txt", shifted(lines("muis-2026-02-a.txt"), +3))
        val outcome = prove(emptyList())
        assertTrue(outcome.failure != null && "red before prove" in outcome.failure!!, outcome.failure)
        assertFalse(stampsDir.exists())
    }

    @Test
    fun `recipes refuse a line that mixes a clone with a named entry, and a days rule they do not know`() {
        val header = Recipes.HEADER.joinToString("\t") + "\n"
        val clone = assertFailsWith<IllegalArgumentException> { Recipes.parse(header + "a\t*\tx.tsv\tsame\t-\tsame\tall\t-\n") }
        assertTrue("entry same goes with member * and point same" in clone.message!!, clone.message)
        val days = assertFailsWith<IllegalArgumentException> { Recipes.parse(header + "a\t*\tx.tsv\tindex\t-\tindex\tweek\t-\n") }
        assertTrue("days 'week'" in days.message!!, days.message)
        assertTrue(recipes.single().matches("singapore") && Recipes.parse(header + "a\tcape-town-*\tx.tsv\tindex\t-\tindex\tmonth\t-\n").single().let { it.matches("cape-town-2026-11") && !it.matches("cpt-2026") })
    }

    @Test
    fun `a bare star line gives way to a line naming the capture more specifically in the same gate file`() {
        val all = Recipes.parse(
            Recipes.HEADER.joinToString("\t") + "\n" +
                "id-kemenag\t*\tid-kemenag.tsv\tindex\t-\tindex\tall\t-\n" +
                "id-kemenag\tmedan-*\tid-kemenag.tsv\tid.kemenag/0228\t-\tunit\tall\t-\n" +
                "za-mjc\tcape-town-*\tza-cape.tsv\tindex\t-\tindex\tall\t-\n" +
                "za-mjc\tcape-town-*\tza-cape.tsv\tza.cape\tindex\tall\tall\t-\n",
        )
        val (star, medan, own, member) = all
        assertFalse(Recipes.applies(star, "medan-2026", all))
        assertTrue(Recipes.applies(medan, "medan-2026", all))
        assertTrue(Recipes.applies(star, "jakarta-2026", all))
        assertFalse(Recipes.applies(medan, "jakarta-2026", all))
        // Two lines with the same specific pattern both apply (a capture's own row and its cautious member row).
        assertTrue(Recipes.applies(own, "cape-town-2026-11", all) && Recipes.applies(member, "cape-town-2026-11", all))
    }

    @Test
    fun `the committed recipes read`() {
        val committed = Recipes.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/recipes.tsv"))
        assertTrue(committed.size > 20)
        assertTrue(committed.all { TestPaths.repoRoot.resolve("tools/timetables/official/gate/${it.gate}").isFile }, committed.toString())
    }
}
