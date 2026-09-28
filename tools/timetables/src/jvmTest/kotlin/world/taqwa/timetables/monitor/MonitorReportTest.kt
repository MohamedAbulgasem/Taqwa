package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.Json
import world.taqwa.timetables.TestPaths
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The index, the catalogue, the report's words and one whole run on an invented state folder. */
class MonitorReportTest {

    private val official: File = Files.createTempDirectory("monitor-run").toFile()

    @AfterTest
    fun cleanUp() {
        official.deleteRecursively()
    }

    @Test
    fun `the index is read with its optional cells empty`() {
        val text = MonitorIndex.HEADER.joinToString("\t") + "\n" +
            "sa-ummalqura\tmakkah-2026\tarchive/tables/monitor/sa-ummalqura/makkah-2026.txt\tMakkah 2026\t21.426666\t39.831666\tAsia/Riyadh\t\tSA\tsa.ummalqura\t\tF+E S D A M I\tdaily\tstandard\tabc\tnew\t2026-10-05\t\n" +
            "mawaqit\tsome-slug\tarchive/tables/monitor/mawaqit/some-slug.txt\tA mosque\t52.5\t-1.9\tEurope/London\t\tGB\t\tgb-cautious\tF S D A M I\tdaily\t-\tdef\tunchanged\t2026-10-05\tnote\n"
        val tables = MonitorIndex.parse(text)
        assertEquals(2, tables.size)
        assertEquals(null, tables[0].clock)
        assertEquals("sa.ummalqura", tables[0].entry)
        assertEquals(null, tables[0].survey)
        assertTrue(tables[0].isNewOrChanged)
        assertEquals("gb-cautious", tables[1].survey)
        assertEquals(null, tables[1].entry)
        assertTrue(!tables[1].isNewOrChanged)
    }

    @Test
    fun `the committed catalogue reads and names every fetcher or manual`() {
        val sources = Source.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/sources.tsv"))
        assertTrue(sources.size >= 10)
        assertTrue(sources.all { it.cadence in setOf("weekly", "monthly", "manual") }, sources.joinToString { it.cadence })
        assertTrue(sources.all { !it.manual || it.nextExpected != null }, "a manual source names when its next edition is expected")
        val fetchers = TestPaths.repoRoot.resolve("tools/timetables/monitor/fetchers").listFiles { f -> f.extension == "py" }!!.map { it.nameWithoutExtension }.toSet()
        for (s in sources.filter { !it.manual }) assertTrue(s.fetcher in fetchers, "${s.id}: no fetcher ${s.fetcher}.py")
        // Metadata only (ruling R69): no printed time in the catalogue.
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        assertTrue(sources.none { time.containsMatchIn(it.points) || time.containsMatchIn(it.note) })
    }

    @Test
    fun `a month in next_expected is its first day`() {
        val s = Source.parse(Source.HEADER.joinToString("\t") + "\nx\te\tmanual\tmanual\t2027-01\tp\tn\ny\te\tf\tweekly\t-\tp\tn")
        assertEquals(LocalDate(2027, 1, 1), s[0].nextExpected)
        assertEquals(null, s[1].nextExpected)
    }

    @Test
    fun `the summary counts what needs attention and the report lists it first with the low items last`() {
        val items = listOf(
            Item(Kind.GREEN, "the gate: fine"),
            Item(Kind.MANUAL_DUE, "ae-iacad: due", low = true),
            Item(Kind.EARLY_OR_LATE, "x early", listOf("fajr: 3 early"), "fix it"),
            Item(Kind.NEW_TABLE_FINE, "y new", listOf("gate row: `a\tb`")),
            Item(Kind.FETCH_BROKEN, "z broke", listOf("HTTP 503"), "look"),
        )
        assertEquals("3 items need attention: 1 early or late, 1 fetch broken, 1 manual source due.", Report.summary(items))
        assertEquals("All green.", Report.summary(items.filter { !it.kind.attention }))
        assertEquals("1 item needs attention: 1 fetch broken.", Report.summary(listOf(items[4])))
        val text = Report.render(LocalDate(2026, 10, 5), items, "Backup: 3 files mirrored.", fetched = true)
        val attention = text.indexOf("## Needs attention")
        val early = text.indexOf("### 1. Early or late — x early")
        val broke = text.indexOf("### 2. Fetch broken — z broke")
        val due = text.indexOf("### 3. Manual source due — ae-iacad: due")
        val green = text.indexOf("## Green")
        assertTrue(attention in 0 until early && early < broke && broke < due && due < green, text)
        assertTrue("**Next:** fix it" in text && "- fajr: 3 early" in text && "New table, fine: y new" in text, text)
        assertTrue(text.trimEnd().endsWith("## Backup\nBackup: 3 files mirrored."), text)
        assertTrue("No fetch this run" in Report.render(LocalDate(2026, 10, 5), items, null, fetched = false))
    }

    @Test
    fun `a whole run reads the fetch log and the index and writes the report and the exit code`() {
        // An invented state: one source failed, one table new and fine (the open MUIS fixture), a backup today.
        val fixture = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
        val tablePath = "archive/tables/monitor/sg-muis/singapore.txt"
        official.resolve(tablePath).parentFile.mkdirs()
        fixture.resolve("open/SG-MUIS/muis-2026-02-a.txt").copyTo(official.resolve(tablePath))
        official.resolve("archive/tables/monitor/index.tsv").writeText(
            MonitorIndex.HEADER.joinToString("\t") + "\n" +
                "sg-muis\tsingapore\t$tablePath\tSingapore\t\t\tAsia/Singapore\t\tSG\tsg.muis\t\tF+E S D A M I\tdaily\tstandard\th1\tnew\t2026-10-05\t\n",
        )
        val monitorDir = official.resolve("monitor")
        monitorDir.resolve("fetch").mkdirs()
        monitorDir.resolve("fetch/latest.json").writeText(
            Json.pretty(
                linkedMapOf(
                    "date" to "2026-10-05",
                    "sources" to linkedMapOf(
                        "sg-muis" to linkedMapOf("status" to "ok", "message" to "", "requests" to 2, "tables" to linkedMapOf("singapore" to "new")),
                        "eg-esa" to linkedMapOf("status" to "failed", "message" to "HTTP 503 from dar-alifta.org", "requests" to 1, "tables" to emptyMap<String, String>()),
                        "mawaqit" to linkedMapOf("status" to "skipped", "message" to "not due", "requests" to 0, "tables" to emptyMap<String, String>()),
                    ),
                ),
            ),
        )
        monitorDir.resolve("backup.json").writeText(Json.pretty(linkedMapOf("date" to "2026-10-05", "copied" to 3, "total" to 1701, "newest" to "2026-10-05", "target" to "/tmp/backup")))
        // Invented stamps, so that the committed ones' own horizons do not reach this run.
        val stamps = official.resolve("stamps")
        stamps.mkdirs()
        for (id in listOf("aa.one", "gb.london.lupt", "no.irn")) {
            stamps.resolve("$id.json").writeText(Json.pretty(linkedMapOf("entry" to id, "class" to "A", "broken" to 0, "first" to "2026-01-01", "last" to "2027-12-31")))
        }

        val run = Monitor(TestPaths.repoRoot, official, monitorDir, LocalDate(2026, 10, 5), emptySet(), checkAll = false, skipFull = true, stampsDir = stamps).run()
        assertEquals(1, run.exitCode, run.report)
        assertTrue(run.summary.startsWith("1 item needs attention: 1 fetch broken."), run.summary)
        assertTrue("Plus the backup reminder." in run.summary, run.summary)
        assertTrue("### 1. Fetch broken — eg-esa (failed)" in run.report && "HTTP 503" in run.report, run.report)
        assertTrue("New table, fine: sg-muis/singapore" in run.report, run.report)
        assertTrue("not due: mawaqit" in run.report, run.report)
        assertTrue("upload the backup folder" in run.report, run.report)
        assertEquals(run.report, monitorDir.resolve("latest.md").readText())
        assertEquals(run.report, monitorDir.resolve("reports/2026-10-05.md").readText())
        assertTrue(monitorDir.resolve("last-run.json").readText().contains("\"exit\": 1"))
        assertTrue(monitorDir.resolve("state.json").readText().contains("\"lastReminder\": \"2026-10-05\""))

        // The next run, nothing fetched and nothing new: green, and the reminder is not repeated.
        official.resolve("archive/tables/monitor/index.tsv").writeText(
            MonitorIndex.HEADER.joinToString("\t") + "\n" +
                "sg-muis\tsingapore\t$tablePath\tSingapore\t\t\tAsia/Singapore\t\tSG\tsg.muis\t\tF+E S D A M I\tdaily\tstandard\th1\theld\t2026-10-05\t\n",
        )
        monitorDir.resolve("backup.json").writeText(Json.pretty(linkedMapOf("date" to "2026-10-12", "copied" to 0, "total" to 1701, "newest" to "2026-10-05", "target" to "/tmp/backup")))
        val again = Monitor(TestPaths.repoRoot, official, monitorDir, LocalDate(2026, 10, 12), emptySet(), checkAll = false, skipFull = true, stampsDir = stamps).run()
        assertEquals(0, again.exitCode, again.report)
        assertEquals("All green.", again.summary)
        assertTrue("No fetch this run" in again.report && "Nothing new since the last reminder (2026-10-05)" in again.report, again.report)
        assertTrue("sg-muis/singapore" !in again.report, "a held table that was fine is not checked again")

        // A table red last run is checked again although unchanged, until it is green.
        val state = MonitorState(monitorDir.resolve("state.json"))
        state.redTables = setOf("sg-muis/singapore")
        state.save()
        val third = Monitor(TestPaths.repoRoot, official, monitorDir, LocalDate(2026, 10, 19), emptySet(), checkAll = false, skipFull = true, stampsDir = stamps).run()
        assertTrue("Changed table, fine: sg-muis/singapore" in third.report, third.report)
        assertEquals(emptySet(), MonitorState(monitorDir.resolve("state.json")).redTables)
    }
}
