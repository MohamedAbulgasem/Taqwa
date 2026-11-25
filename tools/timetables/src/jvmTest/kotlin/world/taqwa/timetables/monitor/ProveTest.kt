package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.StampDemotion
import world.taqwa.timetables.gate.StampSummary
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
    fun `a capture the engine is early against is left out and reported, never loosened, and its checked timetable demoted by itself`() {
        // Ruling R185: sg.muis is a checked timetable (class B), the row its own and the capture its authority's — so
        // the first run that finds a day early in it writes the @demoted line and the stamps, with no row and no pinned copy.
        val index = setUp(shift = +3)
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(0, outcome.added.size)
        assertEquals(1, outcome.leftOut.size)
        assertTrue("early" in outcome.leftOut.single().reason, outcome.leftOut.single().reason)
        assertFalse(official.resolve("archive/tables/pinned/sg-muis/2026-10-05/singapore.txt").exists())
        val demoted = outcome.demoted.single()
        assertEquals(listOf("sg-muis.tsv", "sg.muis", null, date), listOf(demoted.gate, demoted.entry, demoted.unit, demoted.since))
        assertEquals("sg.muis", demoted.target)
        assertTrue(demoted.reason.startsWith("the monitor's capture sg-muis/singapore, at "), demoted.reason)
        assertTrue(" early on " in demoted.reason && " place-days, by up to 3 min (2026-02-" in demoted.reason, demoted.reason)
        // Dates, counts and minutes only: no printed time (ruling R69).
        assertFalse(Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(demoted.reason), demoted.reason)
        assertTrue(outcome.changed)
        assertEquals(listOf(mapOf("gate" to "sg-muis.tsv", "entry" to "sg.muis", "since" to "2026-10-05", "reason" to demoted.reason)), outcome.json()["demoted"])
        val gate = gateDir.resolve("sg-muis.tsv").readText()
        assertTrue(gate.startsWith(before), gate)
        val added = gate.removePrefix(before).lines().filter { it.isNotEmpty() }
        assertEquals(2, added.size, gate)
        assertTrue(added[0].startsWith("# prove, 2026-10-05: sg.muis demoted by itself (ruling R185)"), added[0])
        assertEquals("@demoted\tsg.muis\t2026-10-05\t${demoted.reason}", added[1])
        // The gate reads it back, and the stamp carries it: green on the days held, and demoted.
        val line = GateManifest.parse("sg-muis.tsv", gate).demotions.single()
        assertEquals(listOf("sg.muis", null, date), listOf(line.entry, line.unit, line.since))
        val stamp = StampSummary.load(stampsDir).single()
        assertEquals(0, stamp.broken)
        assertEquals(listOf(StampDemotion(null, date, demoted.reason)), stamp.demoted)
        assertTrue(stamp.demotedStanding)
        // The next run finds the same days early again, leaves the row out again, and writes no second line.
        val again = prove(index)
        assertNull(again.failure)
        assertEquals(1, again.leftOut.size)
        assertTrue(again.demoted.isEmpty())
        assertFalse(again.changed)
        assertEquals(gate, gateDir.resolve("sg-muis.tsv").readText())
    }

    @Test
    fun `a dry run that finds a failing day demotes nothing on disk`() {
        val index = setUp(shift = +3)
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val outcome = prove(index, write = false)
        assertEquals(1, outcome.demoted.size)
        assertEquals(before, gateDir.resolve("sg-muis.tsv").readText())
        assertFalse(stampsDir.exists())
        assertFalse(official.resolve("archive/tables/pinned/sg-muis").exists())
    }

    @Test
    fun `a capture that is late, never early, is left out over its limit and demotes its checked timetable too`() {
        // A failing day is any promise broken (spec §2): over the late limit is the entry's "as little late as possible".
        val index = setUp(shift = -30)
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(0, outcome.added.size)
        assertEquals(1, outcome.demoted.size, outcome.leftOut.toString())
        assertTrue("over its late limit on" in outcome.demoted.single().reason, outcome.demoted.single().reason)
    }

    @Test
    fun `a republisher's mirror never demotes - its row is left out saying so, marked for the issue`() {
        // The review of 13 Nov 2026: JAKIM's 2027 zones are held from a mirror (the index names them "... (mirror)"), and
        // a mirror's mistake is not the authority's. The same failing capture as above, named a mirror: no line, no stamp.
        val index = setUp(shift = +3).map { it.copy(name = "Singapore 2027 (mirror)") }
        assertTrue(index.single().mirror)
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertTrue(outcome.demoted.isEmpty(), outcome.demoted.toString())
        assertFalse(outcome.changed)
        val left = outcome.leftOut.single()
        assertTrue("early" in left.reason && "a republisher's mirror, not the authority's own table: sg.muis is not demoted on it (ruling R185)" in left.reason, left.reason)
        assertTrue(left.checked && left.mirror)
        assertEquals(listOf(true, true), (outcome.json()["leftOut"] as List<*>).map { it as Map<*, *> }.single().let { listOf(it["checked"], it["mirror"]) })
        assertEquals(before, gateDir.resolve("sg-muis.tsv").readText())
        assertFalse(stampsDir.exists())
        // The authority's own capture of the same days does demote, and its row left out is marked checked, not a mirror.
        val own = prove(setUp(shift = +3))
        assertEquals(1, own.demoted.size)
        assertEquals(listOf(true, false), own.leftOut.single().let { listOf(it.checked, it.mirror) })
        assertFalse("mirror" in own.leftOut.single().reason)
    }

    @Test
    fun `an entry that claims no checked timetable is left out without a demotion`() {
        // other.singapore is a published method (class D): there is no class to take from it.
        val index = setUp(shift = +3).map { it.copy(entry = "other.singapore", lat = 1.3521, lon = 103.8198) }
        gateDir.resolve("sg-muis.tsv").writeText(gateDir.resolve("sg-muis.tsv").readText().replace("\tsg.muis\t\t\t", "\tother.singapore\t1.3521\t103.8198\t"))
        val before = gateDir.resolve("sg-muis.tsv").readText()
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(1, outcome.leftOut.size)
        assertTrue(outcome.demoted.isEmpty(), outcome.demoted.toString())
        assertFalse(outcome.leftOut.single().checked)
        assertFalse(outcome.changed)
        assertEquals(before, gateDir.resolve("sg-muis.tsv").readText())
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
        // No demotion (a table the reader cannot take is not a promise broken), but the row was a checked timetable's
        // own, so its days went unchecked: marked for the issue's first tier.
        assertTrue(outcome.demoted.isEmpty())
        assertTrue(outcome.leftOut.single().checked)
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

    private fun planner(index: List<MonitorTable>, with: List<Recipe> = recipes) = Prove(
        TestPaths.repoRoot, official, date, with, index, gateDir = gateDir, stampsDir = stampsDir,
        roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official")),
    )

    /** [lines] with [column]'s time (1 Fajr .. 6 Isha) on [day] moved by [shift] minutes. */
    private fun moved(lines: List<String>, day: String, column: Int, shift: Int) = lines.map { line ->
        if (!line.startsWith(day)) return@map line
        val parts = line.split(' ').toMutableList()
        val (h, m) = parts[column].split(':').map(String::toInt)
        val total = h * 60 + m + shift
        parts[column] = "%02d:%02d".format(total / 60, total % 60)
        parts.joinToString(" ")
    }

    @Test
    fun `a held day the capture re-reads otherwise becomes a row of those days alone (ruling R283)`() {
        val index = setUp()
        assertEquals(1, prove(index).added.size)
        // Every day is held now; a re-read moves one held day's Maghrib a minute earlier (an authority's correction).
        val all = lines("muis-2026-02-a.txt") + lines("muis-2026-02-b.txt")
        write("archive/tables/monitor/sg-muis/singapore.txt", moved(all, "2026-02-16", 5, -1))
        val (planned, left) = planner(index).plan()
        assertTrue(left.isEmpty(), left.toString())
        val p = planned.single()
        assertEquals(listOf(LocalDate(2026, 2, 16)), p.days)
        assertEquals("archive/tables/pinned/sg-muis/2026-10-05/singapore.changed-2026-02-16-1.txt", p.path)
        val dated = p.content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(1, dated.size)
        assertTrue(dated.single().startsWith("2026-02-16 "))
        assertTrue("1 held day re-read otherwise, held as now printed (ruling R283)" in p.cells.getValue("note"), p.cells.getValue("note"))
        assertFalse(Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(p.cells.getValue("note")), "no printed time in a note (ruling R69)")
        // A cell the capture leaves empty is no correction: nothing to add.
        write("archive/tables/monitor/sg-muis/singapore.txt", all.map { if (it.startsWith("2026-02-16")) it.split(' ').let { c -> (c.take(5) + "-" + c.drop(6)).joinToString(" ") } else it })
        assertTrue(planner(index).plan().first.isEmpty())
    }

    @Test
    fun `a correction the engine is early against demotes its checked timetable though every day was held (ruling R283)`() {
        val index = setUp()
        assertEquals(1, prove(index).added.size)
        val all = lines("muis-2026-02-a.txt") + lines("muis-2026-02-b.txt")
        write("archive/tables/monitor/sg-muis/singapore.txt", moved(all, "2026-02-16", 5, +3))
        val outcome = prove(index)
        assertNull(outcome.failure)
        assertEquals(0, outcome.added.size)
        val left = outcome.leftOut.single()
        assertTrue("early" in left.reason && left.checked && !left.mirror, left.reason)
        val demoted = outcome.demoted.single()
        assertEquals("sg.muis", demoted.target)
        assertTrue("(2026-02-16)" in demoted.reason, demoted.reason)
        assertFalse(official.resolve("archive/tables/pinned/sg-muis/2026-10-05/singapore.changed-2026-02-16-1.txt").exists())
        assertTrue(StampSummary.load(stampsDir).single().demotedStanding)
    }

    @Test
    fun `a mirror's rows hold no day of the authority's own capture and the row says whose it is (ruling R282)`() {
        val own = setUp()
        // The held row is a republisher's copy; the authority's own capture prints the same five days.
        gateDir.resolve("sg-muis.tsv").writeText(gateDir.resolve("sg-muis.tsv").readText().replace("the first five days", "Singapore 2026 (mirror), the first five days"))
        write("archive/tables/monitor/sg-muis/singapore.txt", lines("muis-2026-02-a.txt"))
        val p = planner(own).plan().first.single()
        assertEquals(5, p.days.size)
        assertFalse(p.mirror)
        assertFalse(MIRROR_MARK in p.cells.getValue("note"), p.cells.getValue("note"))
        // A mirror's capture of the same days is held by the mirror's row: nothing to add.
        assertTrue(planner(own.map { it.copy(name = "Singapore 2026 (mirror)") }).plan().first.isEmpty())
        // And the authority's row lands: the gate is green on it and holds both.
        val outcome = prove(own)
        assertNull(outcome.failure)
        assertEquals(1, outcome.added.size, outcome.leftOut.toString())
    }

    @Test
    fun `a printed imsak is read by its own recipe lines alone and held by imsak rows alone (ruling R284)`() {
        setUp()
        val imsak = (lines("muis-2026-02-a.txt") + lines("muis-2026-02-b.txt")).map { line ->
            val c = line.split(' ')
            val (h, m) = c[1].split(':').map(String::toInt)
            val total = h * 60 + m - 10
            "${c[0]} ${c[1]} %02d:%02d".format(total / 60, total % 60)
        }
        write("archive/tables/monitor/sg-muis/singapore-imsak.txt", imsak)
        val capture = MonitorTable(
            "sg-muis", "singapore-imsak", "archive/tables/monitor/sg-muis/singapore-imsak.txt", "Singapore imsak (test)", null, null, "Asia/Singapore", null,
            "SG", "sg.muis", null, "- Im", "daily", "standard", "hash", "2026-10-05", "",
        )
        // The day's recipe line (`*`) never reads it, and says nothing of it either.
        val (none, quiet) = planner(listOf(capture)).plan()
        assertTrue(none.isEmpty() && quiet.isEmpty(), "$none $quiet")
        val both = Recipes.parse(
            Recipes.HEADER.joinToString("\t") + "\n" + "sg-muis\t*\tsg-muis.tsv\tindex\t-\tindex\tall\t-\n" +
                "sg-muis\t*-imsak\tsg-muis.tsv\tindex\t-\tindex\tall\t-\n",
        )
        // The day's row holds the first five days' Fajr and end of eating, but no imsak: every day is new to the imsak.
        val p = planner(listOf(capture), both).plan().first.single()
        assertEquals(10, p.days.size)
        assertEquals("- Im", p.cells.getValue("columns"))
        assertEquals("archive/tables/pinned/sg-muis/2026-10-05/singapore-imsak.txt", p.path)
        // A hand read-back of the same imsak (archive/tables/imsak/<source>/<capture>/<key>.txt) is the capture's family
        // and holds its days.
        write("archive/tables/imsak/sg-muis/2026-09-28/singapore.txt", imsak.take(5))
        gateDir.resolve("sg-muis.tsv").appendText("archive/tables/imsak/sg-muis/2026-09-28/singapore.txt\tsg.muis\t\t\tAsia/Singapore\t- Im\tdaily\tstandard\ttest\tthe imsak read back\n")
        val again = planner(listOf(capture), both).plan().first.single()
        assertEquals(10, again.days.size)
        assertTrue("the earlier capture's row sg-muis.tsv:4" in again.family, again.family)
        write("archive/tables/monitor/sg-muis/singapore-imsak.txt", imsak.take(5))
        assertTrue(planner(listOf(capture), both).plan().first.isEmpty(), "every imsak day held as printed")
    }

    @Test
    fun `a recipe line for the day's tables never matches a printed imsak's key`() {
        val header = Recipes.HEADER.joinToString("\t") + "\n"
        val (star, reach, imsak) = Recipes.parse(
            header + "a\t*\tx.tsv\tindex\t-\tindex\tall\t-\n" + "a\tjakarta-2*\tx.tsv\tindex\t-\tall\tall\t-\n" +
                "a\tjakarta-*-imsak\tx.tsv\tindex\t-\tindex\tall\t-\n",
        )
        assertFalse(star.matches("jakarta-2027-imsak"))
        assertFalse(reach.matches("jakarta-2027-imsak"))
        assertTrue(reach.matches("jakarta-2027"))
        assertTrue(imsak.matches("jakarta-2027-imsak"))
        assertFalse(imsak.matches("jakarta-2027"))
    }

    @Test
    fun `the committed recipes read`() {
        val committed = Recipes.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/recipes.tsv"))
        assertTrue(committed.size > 20)
        assertTrue(committed.all { TestPaths.repoRoot.resolve("tools/timetables/official/gate/${it.gate}").isFile }, committed.toString())
    }
}
