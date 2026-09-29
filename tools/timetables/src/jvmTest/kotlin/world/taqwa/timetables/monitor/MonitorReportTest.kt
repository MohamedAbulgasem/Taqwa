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

/** The index, the catalogue, the report's words (ruling R93) and whole runs on an invented state folder. */
class MonitorReportTest {

    private val official: File = Files.createTempDirectory("monitor-run").toFile()
    private val monitorDir = official.resolve("monitor")
    private val stamps = official.resolve("stamps")

    /** No gate rows and no surveys: a full run's gate is "0 rows" and green, so the runs here stay fast and offline. */
    private val gateDir = official.resolve("gate").also { it.mkdirs() }

    @AfterTest
    fun cleanUp() {
        official.deleteRecursively()
    }

    @Test
    fun `the index is read with its optional cells empty`() {
        val text = MonitorIndex.HEADER.joinToString("\t") + "\n" +
            "sa-ummalqura\tmakkah-2026\tarchive/tables/monitor/sa-ummalqura/makkah-2026.txt\tMakkah 2026\t21.426666\t39.831666\tAsia/Riyadh\t\tSA\tsa.ummalqura\t\tF+E S D A M I\tdaily\tstandard\tabc\t2026-10-05\t\n" +
            "mawaqit\tsome-slug\tarchive/tables/monitor/mawaqit/some-slug.txt\tA mosque\t52.5\t-1.9\tEurope/London\t\tGB\t\tgb-cautious\tF S D A M I\tdaily\t-\tdef\t2026-10-05\tnote\n"
        val tables = MonitorIndex.parse(text)
        assertEquals(2, tables.size)
        assertEquals(null, tables[0].clock)
        assertEquals("sa.ummalqura", tables[0].entry)
        assertEquals(null, tables[0].survey)
        assertEquals("gb-cautious", tables[1].survey)
        assertEquals(null, tables[1].entry)
        assertTrue("abc" in tables[0].metadata && "def" !in tables[0].metadata)
        // The first monitor's index carried a per-run status column: still read, the column ignored.
        val old = (MonitorIndex.HEADER.dropLast(2) + "status" + MonitorIndex.HEADER.takeLast(2)).joinToString("\t") + "\n" +
            "sa-ummalqura\tmakkah-2026\tarchive/tables/monitor/sa-ummalqura/makkah-2026.txt\tMakkah 2026\t21.426666\t39.831666\tAsia/Riyadh\t\tSA\tsa.ummalqura\t\tF+E S D A M I\tdaily\tstandard\tabc\theld\t2026-10-05\t\n"
        assertEquals("2026-10-05", MonitorIndex.parse(old).single().fetched)
        assertTrue(runCatching { MonitorIndex.parse("source\tkey\nx\ty\n") }.isFailure, "a header lacking columns is refused")
    }

    @Test
    fun `the committed catalogue reads and names every fetcher or manual`() {
        val sources = Source.load(TestPaths.repoRoot.resolve("tools/timetables/official/monitor/sources.tsv"))
        assertTrue(sources.size >= 10)
        assertTrue(sources.all { it.cadence in setOf("weekly", "monthly", "month-start", "manual") }, sources.joinToString { it.cadence })
        assertTrue(sources.all { !it.manual || it.nextExpected != null || it.fetcher != "manual" }, "a source read by hand names when its next edition is expected")
        val fetchers = TestPaths.repoRoot.resolve("tools/timetables/monitor/fetchers").listFiles { f -> f.extension == "py" }!!.map { it.nameWithoutExtension }.toSet()
        for (s in sources.filter { it.fetcher != "manual" }) assertTrue(s.fetcher in fetchers, "${s.id}: no fetcher ${s.fetcher}.py")
        // IRN runs on its own (ruling R95); London waits for the owner's key; the MJC's page is due each new month.
        assertEquals("irn", sources.first { it.id == "no-irn" }.fetcher)
        assertTrue(!sources.first { it.id == "no-irn" }.manual)
        assertTrue(sources.first { it.id == "gb-london-lupt" }.manual)
        val mjc = sources.first { it.id == "za-mjc" }
        assertEquals("mjc" to "month-start", mjc.fetcher to mjc.cadence)
        assertTrue(!mjc.manual && mjc.nextExpected == null)
        // Metadata only (ruling R69): no printed time in the catalogue.
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        assertTrue(sources.none { time.containsMatchIn(it.points) || time.containsMatchIn(it.note) })
    }

    @Test
    fun `a month in next_expected is its first day and either field makes a source manual`() {
        val s = Source.parse(Source.HEADER.joinToString("\t") + "\nx\te\tmanual\tmanual\t2027-01\tp\tn\ny\te\tf\tweekly\t-\tp\tn\nz\te\tlondon\tmanual\t2026-12-01\tp\tn")
        assertEquals(LocalDate(2027, 1, 1), s[0].nextExpected)
        assertEquals(null, s[1].nextExpected)
        assertTrue(s[0].manual && !s[1].manual && s[2].manual)
    }

    @Test
    fun `the summary leads with the never-early count and the report lists tiers in order with an index line`() {
        val items = listOf(
            Item(Kind.GREEN, "the gate: fine"),
            Item(Kind.MANUAL_DUE, "ae-iacad: due", key = "due:ae-iacad"),
            Item(Kind.NEVER_EARLY, "x early", listOf("fajr: 3 early"), "fix it", key = "table:x:never-early", days = 3, worst = 2, label = "x (2026-10-01..2026-10-03)"),
            Item(Kind.NEVER_EARLY, "the gate: 1 rows", listOf("aa.one fajr: 1 early"), "fix it", key = "gate", days = 1, worst = 1, group = 1, label = "the gate (aa.one)"),
            Item(Kind.NEW_TABLE_FINE, "y new", listOf("gate row: `a\tb`")),
            Item(Kind.FETCH_BROKEN, "z broke", listOf("HTTP 503"), "look", key = "fetch:z"),
            Item(Kind.OWN_LATE, "w late", listOf("dhuhr: 2 over the late limit"), "refit", key = "table:w:late", days = 2, worst = 4),
            Item(Kind.FOR_THE_RECORD, "m: lateness on the member row", listOf("fr.cautious isha: 20 over the late limit"), key = "table:m:member"),
            Item(Kind.STILL_OPEN, "s late", listOf("asr: 1 over"), key = "table:s:late", since = LocalDate(2026, 9, 1)),
            Item(Kind.NOTE, "the workflow file differs", listOf("copy it"), key = "note:workflow-drift"),
            Item(Kind.CHECKED_FINE, "sg-muis/a"),
            Item(Kind.CHECKED_FINE, "sg-muis/b"),
            Item(Kind.CHECKED_FINE, "eg-esa/c"),
        )
        assertEquals(
            "2 never-early failures (x (2026-10-01..2026-10-03), the gate (aa.one)), 3 other items (1 fetch broken, 1 over the late limit, 1 manual source due).",
            Report.summary(items),
        )
        assertEquals("All green.", Report.summary(items.filter { !it.kind.attention }))
        assertEquals("0 never-early failures, 1 other item (1 fetch broken).", Report.summary(listOf(items[5])))
        assertEquals("Never early: 2 — x (2026-10-01..2026-10-03); the gate (aa.one)", Report.index(items))
        val text = Report.render(LocalDate(2026, 10, 5), items, "Backup: 3 files mirrored.", fetched = true, changed = true)
        val order = listOf(
            "## Needs attention", "Never early: 2 — x (2026-10-01..2026-10-03); the gate (aa.one)",
            "### 1. Never early — x early", "### 2. Never early — the gate: 1 rows", "### 3. Fetch broken — z broke",
            "### 4. Over the late limit — w late", "### 5. Manual source due — ae-iacad: due",
            "## Informational", "- Still open (since 2026-09-01): s late", "- Note: the workflow file differs", "- For the record: m: lateness on the member row",
            "## Green", "New table, fine: y new", "Checked again, fine: 3 held tables (sg-muis 2, eg-esa 1)", "## Backup",
        )
        val positions = order.map { text.indexOf(it) }
        assertTrue(positions.all { it >= 0 } && positions == positions.sorted(), text)
        assertTrue("**Next:** fix it" in text && "- fajr: 3 early" in text && "The attention set changed since the last notification." in text, text)
        assertTrue(text.trimEnd().endsWith("## Backup\nBackup: 3 files mirrored."), text)
        assertTrue("No fetch this run" in Report.render(LocalDate(2026, 10, 5), items, null, fetched = false))
        val partial = Report.render(LocalDate(2026, 10, 5), items, null, fetched = false, partial = "sg-muis")
        assertTrue(partial.startsWith("# Taqwa monitor — 2026-10-05 (partial: sg-muis)") && "Partial run (sg-muis)" in partial, partial)
        val issue = Report.issueBody(LocalDate(2026, 10, 5), items)
        assertTrue(issue.startsWith("**Taqwa monitor, 2026-10-05.** 2 never-early failures") && "Never early: 2" in issue && "5. Manual source due" in issue, issue)
        assertTrue(issue.trimEnd().endsWith("(restricted: it quotes dates, counts and minutes)."), issue)
    }

    @Test
    fun `the index line and the issue body are bounded however many tables fail`() {
        val many = (1..3000).map { n ->
            Item(Kind.NEVER_EARLY, "src-$n/table-$n (a name) against an.entry", listOf("fajr: 1 early"), "fix", key = "table:src-$n/table-$n:never-early", days = 1, worst = 1, label = "src-$n/table-$n (2026-10-01)")
        }
        val index = Report.index(many)!!
        assertTrue(index.startsWith("Never early: 3000 — src-1/table-1 (2026-10-01); ") && index.endsWith("; and 2960 more"), index)
        assertTrue(Report.summary(many).endsWith(", and 2960 more)."), Report.summary(many))
        val issue = Report.issueBody(LocalDate(2026, 10, 5), many)
        assertTrue(issue.length <= Report.ISSUE_LIMIT, "${issue.length}")
        assertTrue("… (cut: the rest is in the report)" in issue && issue.trimEnd().endsWith("(restricted: it quotes dates, counts and minutes)."), issue.takeLast(300))
    }

    private val tablePath = "archive/tables/monitor/sg-muis/singapore.txt"

    private fun index(hash: String, more: String = "") {
        official.resolve("archive/tables/monitor/index.tsv").writeText(
            MonitorIndex.HEADER.joinToString("\t") + "\n" +
                "sg-muis\tsingapore\t$tablePath\tSingapore\t\t\tAsia/Singapore\t\tSG\tsg.muis\t\tF+E S D A M I\tdaily\tstandard\t$hash\t2026-10-05\t\n" + more,
        )
    }

    private fun fetchLog(date: String, vararg sources: Pair<String, Map<String, Any?>>, error: String? = null) {
        monitorDir.resolve("fetch").mkdirs()
        monitorDir.resolve("fetch/latest.json").writeText(
            Json.pretty(linkedMapOf("date" to date, "sources" to linkedMapOf(*sources), "error" to error).filterValues { it != null }),
        )
    }

    private fun backup(date: String, copied: Int, error: String? = null, skipped: String? = null) {
        monitorDir.resolve("backup.json").writeText(
            Json.pretty(
                linkedMapOf("date" to date, "copied" to copied, "total" to 1701, "newest" to "2026-10-05", "target" to "/tmp/backup", "error" to error, "skipped" to skipped)
                    .filterValues { it != null },
            ),
        )
    }

    private fun invented() {
        // The open MUIS fixture as the one held table; invented stamps, so that the committed ones' horizons do not reach these runs.
        val fixture = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
        official.resolve(tablePath).parentFile.mkdirs()
        fixture.resolve("open/SG-MUIS/muis-2026-02-a.txt").copyTo(official.resolve(tablePath))
        index("h1")
        stamps.mkdirs()
        for (id in listOf("aa.one", "gb.london.lupt", "no.irn")) {
            stamps.resolve("$id.json").writeText(Json.pretty(linkedMapOf("entry" to id, "class" to "A", "broken" to 0, "first" to "2026-01-01", "last" to "2027-12-31")))
        }
    }

    private fun run(today: String, only: Set<String> = emptySet(), checkAll: Boolean = false, skipFull: Boolean = false) =
        Monitor(TestPaths.repoRoot, official, monitorDir, LocalDate.parse(today), only, checkAll, skipFull, stampsDir = stamps, gateDir = gateDir, surveys = emptyList()).run()

    private val ok = linkedMapOf<String, Any?>("status" to "ok", "message" to "", "requests" to 2, "tables" to linkedMapOf("singapore" to "new"))
    private val failed = linkedMapOf<String, Any?>("status" to "failed", "message" to "HTTP 503 from dar-alifta.org", "requests" to 1, "tables" to emptyMap<String, String>())
    private val notDue = linkedMapOf<String, Any?>("status" to "skipped", "message" to "not due", "requests" to 0, "tables" to emptyMap<String, String>())

    @Test
    fun `a whole run reads the fetch log and the index and writes the report and the exit code`() {
        invented()
        fetchLog("2026-10-05", "sg-muis" to ok, "eg-esa" to failed, "mawaqit" to notDue)
        backup("2026-10-05", 3)

        val run = run("2026-10-05")
        assertEquals(1, run.exitCode, run.report)
        assertTrue(!run.partial)
        assertTrue(run.summary.startsWith("0 never-early failures, 1 other item (1 fetch broken)."), run.summary)
        assertTrue("Plus the backup reminder." in run.summary, run.summary)
        assertTrue("### 1. Fetch broken — eg-esa (failed)" in run.report && "HTTP 503" in run.report, run.report)
        assertTrue("New table, fine: sg-muis/singapore" in run.report, run.report)
        assertTrue("the gate: 0 rows, 0 place-days, 0 entries, 0 broken" in run.report, run.report)
        assertTrue("1 new tables, 0 changed" in run.report && "not due: mawaqit" in run.report, run.report)
        assertTrue("upload the backup folder" in run.report, run.report)
        assertTrue(run.attentionChanged)
        assertEquals(run.report, monitorDir.resolve("latest.md").readText())
        assertEquals(run.report, monitorDir.resolve("reports/2026-10-05.md").readText())
        val lastRun = monitorDir.resolve("last-run.json").readText()
        assertTrue("\"exit\": 1" in lastRun && "\"attentionChanged\": true" in lastRun && "\"report\": \"monitor/reports/2026-10-05.md\"" in lastRun, lastRun)
        assertTrue("\"partial\": false" in lastRun && "\"issue\": \"**Taqwa monitor, 2026-10-05.**" in lastRun, lastRun)
        val state = MonitorState(monitorDir.resolve("state.json"))
        assertEquals(LocalDate(2026, 10, 5), state.lastReminder)
        assertEquals("h1", state.checked.getValue("sg-muis/singapore").hash)
        assertTrue(!state.checked.getValue("sg-muis/singapore").red)
        assertEquals(setOf("fetch:eg-esa"), state.notified.keys)

        // The next run, nothing fetched, nothing changed: green, the table not checked again, the reminder not repeated,
        // and the attention set changed (to nothing).
        backup("2026-10-12", 0)
        val again = run("2026-10-12")
        assertEquals(0, again.exitCode, again.report)
        assertEquals("All green.", again.summary)
        assertTrue("No fetch this run" in again.report && "Nothing new since the last reminder (2026-10-05)" in again.report, again.report)
        assertTrue("sg-muis/singapore" !in again.report, "a held table whose signature is unchanged is not checked again")
        assertTrue(again.attentionChanged)
        assertEquals(emptySet(), MonitorState(monitorDir.resolve("state.json")).notified.keys)

        // A table red last run is checked again although unchanged, and is news when green.
        val state3 = MonitorState(monitorDir.resolve("state.json"))
        state3.checked["sg-muis/singapore"] = state3.checked.getValue("sg-muis/singapore").copy(red = true)
        state3.save()
        val third = run("2026-10-19")
        assertTrue("Green again: sg-muis/singapore" in third.report, third.report)
        assertTrue(!MonitorState(monitorDir.resolve("state.json")).checked.getValue("sg-muis/singapore").red)
        assertTrue(!third.attentionChanged)

        // Its content changed (a new hash in the index): checked and labelled changed.
        index("h2")
        val fourth = run("2026-10-26")
        assertTrue("Changed table, fine: sg-muis/singapore" in fourth.report, fourth.report)

        // Nothing changed, --check-all: checked again, one line for all.
        val fifth = run("2026-11-02", checkAll = true)
        assertTrue("Checked again, fine: 1 held table (sg-muis 1)" in fifth.report, fifth.report)
        assertTrue("Changed table" !in fifth.report)
    }

    @Test
    fun `a table the state does not know but that was not fetched today is checked again and not new`() {
        invented()
        val first = run("2026-10-12")
        assertTrue("Checked again, fine: 1 held table (sg-muis 1)" in first.report, first.report)
        assertTrue("New table" !in first.report, first.report)
    }

    @Test
    fun `the old state's red tables are checked again and the notification is posted only when the attention set changes`() {
        invented()
        monitorDir.mkdirs()
        monitorDir.resolve("state.json").writeText(Json.pretty(linkedMapOf("redTables" to listOf("sg-muis/singapore"), "lastReminder" to "2026-09-28")))
        val unchanged = linkedMapOf<String, Any?>("status" to "ok", "message" to "", "requests" to 2, "tables" to linkedMapOf("singapore" to "unchanged"))
        fetchLog("2026-10-05", "sg-muis" to unchanged, "eg-esa" to failed)
        backup("2026-10-05", 0)
        val first = run("2026-10-05")
        assertTrue("Green again: sg-muis/singapore" in first.report, first.report)
        assertTrue(first.attentionChanged)
        assertTrue("redTables" !in monitorDir.resolve("state.json").readText())
        // The same failure a week later: the report lists it, the notification is not repeated.
        fetchLog("2026-10-12", "sg-muis" to unchanged, "eg-esa" to failed)
        backup("2026-10-12", 0)
        val second = run("2026-10-12")
        assertEquals(1, second.exitCode)
        assertTrue(!second.attentionChanged, second.report)
        assertTrue("Nothing new since the last notification." in second.report, second.report)
        assertTrue("\"attentionChanged\": false" in monitorDir.resolve("last-run.json").readText())
        // A driver that broke is a finding of its own.
        fetchLog("2026-10-19", "sg-muis" to unchanged, error = "Traceback\nKeyError: 'tables'")
        backup("2026-10-19", 0)
        val third = run("2026-10-19")
        assertTrue("Fetch broken — the fetch driver itself broke" in third.report && "KeyError" in third.report, third.report)
        assertTrue(third.attentionChanged)
    }

    @Test
    fun `a partial run never touches the notified set or the day's report and says so`() {
        invented()
        fetchLog("2026-10-05", "sg-muis" to ok, "eg-esa" to failed)
        backup("2026-10-05", 0)
        val full = run("2026-10-05")
        assertEquals(setOf("fetch:eg-esa"), MonitorState(monitorDir.resolve("state.json")).notified.keys)
        val latest = monitorDir.resolve("latest.md").readText()
        assertEquals(full.report, latest)
        // --only sg-muis a week later: its own report beside the day's, the notified set and latest.md as they were,
        // nothing "changed", and the record says partial (ruling N1).
        fetchLog("2026-10-12", "sg-muis" to ok, "eg-esa" to failed)
        backup("2026-10-12", 0)
        val partial = run("2026-10-12", only = setOf("sg-muis"))
        assertTrue(partial.partial)
        assertTrue(!partial.attentionChanged)
        assertEquals(0, partial.exitCode, partial.report)
        assertTrue(partial.report.startsWith("# Taqwa monitor — 2026-10-12 (partial: sg-muis)"), partial.report)
        assertTrue("Partial run (sg-muis): only what it covers is listed" in partial.report, partial.report)
        assertTrue("eg-esa" !in partial.report, "the other source's failure is not this run's to report")
        assertEquals(partial.report, monitorDir.resolve("reports/2026-10-12-partial-sg-muis.md").readText())
        assertTrue(!monitorDir.resolve("reports/2026-10-12.md").exists())
        assertEquals(latest, monitorDir.resolve("latest.md").readText(), "latest.md is the last full run's")
        assertEquals(setOf("fetch:eg-esa"), MonitorState(monitorDir.resolve("state.json")).notified.keys)
        val lastRun = monitorDir.resolve("last-run.json").readText()
        assertTrue("\"partial\": true" in lastRun && "\"partialOf\": \"sg-muis\"" in lastRun && "\"attentionChanged\": false" in lastRun, lastRun)
        assertTrue("\"report\": \"monitor/reports/2026-10-12-partial-sg-muis.md\"" in lastRun, lastRun)
        assertTrue("Partial run (sg-muis)" in lastRun, lastRun)
        // A run that skips the gate and the surveys is partial too.
        val quick = run("2026-10-19", skipFull = true)
        assertTrue(quick.partial)
        assertTrue(monitorDir.resolve("reports/2026-10-19-partial-skip-full.md").isFile)
        assertEquals(latest, monitorDir.resolve("latest.md").readText())
    }

    @Test
    fun `london unified falls due as a manual source once its date passes`() {
        invented()
        val run = run("2026-12-05", only = setOf("gb-london-lupt"))
        assertEquals(1, run.exitCode, run.report)
        assertTrue("Manual source due — gb-london-lupt: its next edition was expected by 2026-12-01" in run.report, run.report)
        assertTrue("Manual source due" !in run("2026-11-30", only = setOf("gb-london-lupt")).report, "not due before its date")
    }

    @Test
    fun `a backup that was configured and did not run is attention and one not configured is a line`() {
        invented()
        fetchLog("2026-10-05", "sg-muis" to ok)
        backup("2026-10-05", 0, error = "no backup archive at /Volumes/gone/archive")
        val broken = run("2026-10-05")
        assertEquals(1, broken.exitCode, broken.report)
        assertTrue("Backup did not run — the backup mirror was configured and did not run" in broken.report && "/Volumes/gone" in broken.report, broken.report)
        backup("2026-10-12", 0, skipped = "TAQWA_OFFICIAL_BACKUP is not set")
        val skipped = run("2026-10-12")
        assertEquals(0, skipped.exitCode, skipped.report)
        assertTrue("Backup: not mirrored (TAQWA_OFFICIAL_BACKUP is not set)." in skipped.report, skipped.report)
        // A fetch today with yesterday's backup record: the step was skipped.
        fetchLog("2026-10-19", "sg-muis" to ok)
        val stale = run("2026-10-19")
        assertTrue("Backup did not run — the backup step did not run this run" in stale.report, stale.report)
    }

    @Test
    fun `the shell's note about a drifting workflow file is an informational line for today only`() {
        invented()
        monitorDir.mkdirs()
        monitorDir.resolve("notes.json").writeText(Json.pretty(linkedMapOf("date" to "2026-10-05", "workflowDrift" to "the two files differ in 3 lines")))
        val run = run("2026-10-05")
        assertEquals(0, run.exitCode, run.report)
        assertTrue("- Note: the archive repository's workflow file differs from the public template" in run.report && "differ in 3 lines" in run.report, run.report)
        assertTrue("note:workflow-drift" !in MonitorState(monitorDir.resolve("state.json")).notified.keys, "a note is not attention")
        assertTrue("Note:" !in run("2026-10-12").report, "yesterday's note is not repeated")
    }

    @Test
    fun `a stricter checker or a new survey row re-checks the tables it touches`() {
        invented()
        val first = run("2026-10-05")
        assertTrue("sg-muis/singapore" in first.report)
        val signature = MonitorState(monitorDir.resolve("state.json")).checked.getValue("sg-muis/singapore").signature
        assertEquals(24, signature.length)
        // Nothing changed: not checked again.
        assertTrue("sg-muis/singapore" !in run("2026-10-12").report)
        // The checker's own code changed (the state remembers a signature made with another checker hash): checked again.
        val state = MonitorState(monitorDir.resolve("state.json"))
        val record = state.checked.getValue("sg-muis/singapore")
        state.checked["sg-muis/singapore"] = record.copy(signature = "made-by-an-older-checker")
        state.save()
        assertTrue("Checked again, fine: 1 held table (sg-muis 1)" in run("2026-10-19").report)
        assertEquals(signature, MonitorState(monitorDir.resolve("state.json")).checked.getValue("sg-muis/singapore").signature)
    }

    @Test
    fun `the signature moves with the checker's sources and a calendar's own survey rows and with nothing else`() {
        // An invented repository with the checker's folders, and an invented survey folder.
        val repo = official.resolve("repo")
        val kt = repo.resolve("tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/monitor/A.kt")
        kt.parentFile.mkdirs()
        kt.writeText("class A")
        val py = repo.resolve("tools/timetables/monitor/fetchers/x.py")
        py.parentFile.mkdirs()
        py.writeText("x = 1\n")
        val checker1 = Signatures.checkerHash(repo)
        assertEquals(16, checker1.length)
        assertEquals(checker1, Signatures.checkerHash(repo), "stable")
        py.writeText("x = 2\n")
        val checker2 = Signatures.checkerHash(repo)
        assertTrue(checker1 != checker2, "a fetcher's change moves the checker hash")
        kt.writeText("class A { }")
        assertTrue(checker2 != Signatures.checkerHash(repo), "a Kotlin change moves it too")
        repo.resolve("tools/timetables/monitor/notes.txt").writeText("not code")
        assertEquals(Signatures.checkerHash(repo), Signatures.checkerHash(repo))

        val officialDir = official.resolve("official-invented")
        val survey = officialDir.resolve("survey/gb-cautious")
        survey.mkdirs()
        survey.resolve("faults.tsv").writeText("calendar\tfrom\tto\tcolumns\treason\nmosque-a\t2026-10-01\t2026-10-02\tD\ta slip\nmosque-b\t2026-10-01\t2026-10-01\tS\tanother\n")
        survey.resolve("outliers.tsv").writeText("calendar\tevent\tfrom\tto\tdays\tworst\treason\n*\tisha\t2026-06-01\t2026-06-30\t2\t3\tthe threshold nights\n")
        fun table(key: String, survey: String? = "gb-cautious") = MonitorTable(
            "mawaqit", key, "archive/tables/monitor/mawaqit/$key.txt", key, 52.5, -1.9, "Europe/London", null, "GB", null, survey,
            "F S D A M I", "daily", "-", "hash", "2026-10-05", "",
        )
        val a = Signatures.surveyRows(officialDir, table("mosque-a"))
        assertTrue("mosque-a\t2026-10-01\t2026-10-02\tD" in a && "*\tisha" in a && "mosque-b" !in a, a)
        assertEquals("", Signatures.surveyRows(officialDir, table("mosque-a", survey = null)), "no survey, no rows")
        val before = Signatures.of(table("mosque-a"), "engine", "checker", a)
        assertEquals(24, before.length)
        assertEquals(before, Signatures.of(table("mosque-a"), "engine", "checker", a))
        // A new fault row for mosque-a moves its signature; mosque-b's rows do not.
        survey.resolve("faults.tsv").appendText("mosque-a\t2026-11-01\t2026-11-01\tM\tone more\n")
        val after = Signatures.of(table("mosque-a"), "engine", "checker", Signatures.surveyRows(officialDir, table("mosque-a")))
        assertTrue(before != after, "a new row for the calendar moves its signature")
        val b = Signatures.surveyRows(officialDir, table("mosque-b"))
        survey.resolve("faults.tsv").appendText("mosque-a\t2026-12-01\t2026-12-01\tM\tand another\n")
        assertEquals(b, Signatures.surveyRows(officialDir, table("mosque-b")), "another calendar's row is not this one's")
        assertTrue(Signatures.of(table("mosque-a"), "engine", "checker-2", a) != before, "the checker hash is part of it")
        assertTrue(Signatures.of(table("mosque-a"), "engine-2", "checker", a) != before, "the engine hash is part of it")
    }
}
