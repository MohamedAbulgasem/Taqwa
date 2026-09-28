package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import world.taqwa.timetables.Json
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateError
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Surveys
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * The `monitor` task, the check half of `scripts/monitor.sh` (spec §5, brief P):
 *
 *     --repo <repository root> --official <official root> [--monitor <state dir>] [--today yyyy-mm-dd]
 *     [--only <source>]... [--check-all] [--skip-full]
 *
 * Reads what `fetch.py` wrote (the tables' index, the fetch log, the backup log), checks each new
 * or changed table at its own point ([TableCheck]), runs the whole gate and every survey, the
 * horizons ([Horizons]) and the manual sources' due dates, and writes the report to
 * `<state dir>/reports/<today>.md` and `latest.md`. `last-run.json` carries the exit code and the
 * summary line for the shell: 0 all green, 1 attention needed (2 when the monitor itself failed is
 * the shell's, when this never gets to write it).
 *
 * `--check-all` checks every held table, not only the new and changed ones; `--skip-full` leaves the
 * whole gate and the surveys out (a quick look at the fetched tables alone).
 */
fun main(args: Array<String>) {
    val options = mutableListOf<Pair<String, String>>()
    var checkAll = false
    var skipFull = false
    var i = 0
    while (i < args.size) {
        when (val key = args[i]) {
            "--check-all" -> checkAll = true.also { i++ }
            "--skip-full" -> skipFull = true.also { i++ }
            "--repo", "--official", "--monitor", "--today", "--only" -> {
                options += key to (args.getOrNull(i + 1) ?: fail("$key needs a value"))
                i += 2
            }
            else -> fail("unknown option $key")
        }
    }
    fun all(key: String) = options.filter { it.first == key }.map { it.second }
    val repo = File(all("--repo").singleOrNull() ?: fail("--repo is required"))
    val official = File(all("--official").singleOrNull() ?: fail("--official is required"))
    val monitorDir = File(all("--monitor").singleOrNull() ?: official.resolve("monitor").path)
    val today = all("--today").singleOrNull()?.let(LocalDate::parse) ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
    val only = all("--only").toSet()

    val run = Monitor(repo, official, monitorDir, today, only, checkAll, skipFull).run()
    println(run.report)
    println()
    println("SUMMARY: ${run.summary}")
    println("REPORT: ${run.reportFile.path}")
    exitProcess(run.exitCode)
}

private fun fail(problem: String): Nothing {
    System.err.println("monitor: $problem")
    exitProcess(2)
}

class MonitorRun(val exitCode: Int, val summary: String, val report: String, val reportFile: File)

class Monitor(
    private val repo: File,
    private val official: File,
    private val monitorDir: File,
    private val today: LocalDate,
    private val only: Set<String>,
    private val checkAll: Boolean,
    private val skipFull: Boolean,
    /** The committed stamps the horizons read; a test hands invented ones. */
    private val stampsDir: File = repo.resolve("tools/timetables/official/stamps"),
) {
    private val officialDir = repo.resolve("tools/timetables/official")
    private val roots = OfficialRoots(official, officialDir)

    fun run(): MonitorRun {
        val items = mutableListOf<Item>()
        val sources = Source.load(officialDir.resolve("monitor/sources.tsv"))
        val index = MonitorIndex.load(official.resolve("archive/tables/monitor/index.tsv"))
            .filter { only.isEmpty() || it.source in only }
        val log = FetchLog.load(monitorDir.resolve("fetch/latest.json"))
        val fetchedToday = log != null && log.date == today

        // 1. The fetch: what worked, what did not.
        if (fetchedToday) {
            val runs = log!!.sources.filter { only.isEmpty() || it.source in only }
            for (r in runs.filter { it.status == "failed" || it.status == "partial" }) {
                items += Item(
                    Kind.FETCH_BROKEN, "${r.source} (${r.status})", r.message.lines().filter { it.isNotBlank() },
                    "read the message: an endpoint moved or changed shape (fix the fetcher in tools/timetables/monitor/fetchers/, " +
                        "the raw response is under archive/raw/monitor/), a site refused the request (try by hand, respect its terms; " +
                        "if it stays closed, make the source manual in sources.tsv with the month its next edition is expected).",
                )
            }
            val ok = runs.filter { it.status == "ok" }
            val skipped = runs.filter { it.status == "skipped" }
            val counts = index.groupingBy { it.status }.eachCount()
            items += Item(
                Kind.GREEN,
                "fetched ${ok.size} source${if (ok.size == 1) "" else "s"} (${ok.sumOf { it.requests }} requests): " +
                    "${counts["new"] ?: 0} new tables, ${counts["changed"] ?: 0} changed, ${counts["unchanged"] ?: 0} unchanged" +
                    (if (skipped.isNotEmpty()) "; not due: ${skipped.joinToString { it.source }}" else ""),
                ok.map { "${it.source}: ${it.requests} requests, ${it.tables.size} tables" + (if (it.message.isNotBlank()) " — ${it.message}" else "") },
            )
        }

        // 2. Each new or changed table at its own point, and each table red last run until it is green.
        val state = MonitorState(monitorDir.resolve("state.json"))
        val red = state.redTables
        val toCheck = index.filter { checkAll || it.isNewOrChanged || it.id in red }
        val stillRed = mutableSetOf<String>()
        if (toCheck.isNotEmpty()) {
            val check = TableCheck(roots, officialDir)
            for (table in toCheck) {
                val item = check.check(table)
                items += item
                if (item.kind.attention) stillRed += table.id
            }
        }
        state.redTables = stillRed + (red - index.map { it.id }.toSet()).filter { only.isNotEmpty() }.toSet()

        // 3. The whole gate and every survey.
        if (!skipFull) {
            items += fullGate()
            for (folder in Surveys.all) items += survey(folder)
        }

        // 4. Horizons.
        val deferred = sources.filter { it.manual && it.nextExpected != null }
            .flatMap { s -> ENTRY_ID.findAll(s.entries).map { it.value to s.nextExpected!! } }.toMap()
        items += Horizons.check(today, StampSummary.load(stampsDir), deferred = deferred)

        // 5. Manual sources due.
        for (s in sources.filter { it.manual && (only.isEmpty() || it.id in only) }) {
            val due = s.nextExpected
            if (due != null && today >= due) {
                items += Item(
                    Kind.MANUAL_DUE, "${s.id}: its next edition was expected by $due", listOf("${s.points}; ${s.note}"),
                    "fetch it by hand (${s.entries}), normalise it into the archive, add its gate rows, re-run the gate, then move " +
                        "next_expected in official/monitor/sources.tsv to the edition after.",
                    low = true,
                )
            }
        }

        // 6. The backup reminder.
        val backup = BackupRun.load(monitorDir.resolve("backup.json"))?.takeIf { it.date == today }
        val reminder = backupReminder(backup, state)

        val summary = Report.summary(items) + (if (reminder != null && "upload" in reminder) " Plus the backup reminder." else "")
        val text = Report.render(today, items, reminder, fetchedToday)
        val reportFile = monitorDir.resolve("reports/$today.md")
        reportFile.parentFile.mkdirs()
        reportFile.writeText(text)
        monitorDir.resolve("latest.md").writeText(text)
        val exitCode = if (items.any { it.kind.attention }) 1 else 0
        state.note("lastRun", linkedMapOf("date" to today.toString(), "exit" to exitCode, "summary" to summary))
        state.save()
        monitorDir.resolve("last-run.json").writeText(
            Json.pretty(linkedMapOf("date" to today.toString(), "exit" to exitCode, "summary" to summary, "report" to reportFile.path)),
        )
        return MonitorRun(exitCode, summary, text, reportFile)
    }

    private fun fullGate(): Item {
        val manifest = try {
            GateManifest.load(officialDir.resolve("gate"))
        } catch (e: GateError) {
            return Item(Kind.FETCH_BROKEN, "the gate files could not be read", e.problems, "fix the gate file named")
        }
        val result = try {
            Gate(roots).evaluate(manifest)
        } catch (e: GateError) {
            return Item(Kind.FETCH_BROKEN, "the gate could not run", e.problems, "restore the archive file named, or fix the gate row")
        }
        val violations = result.violations()
        val what = "the gate: ${result.checkedRows} rows, ${result.placeDays} place-days, ${result.entries.size} entries"
        return if (violations.isEmpty()) {
            Item(Kind.GREEN, "$what, 0 broken")
        } else {
            Item(
                Kind.EARLY_OR_LATE, what, violations.map { it.replace("\n", "; ") },
                "the engine or the registry changed under a held table: `./gradlew -p tools/timetables gate -Pentry=<entry>` shows the " +
                    "days; fix the method or its margins (refit with -Pfit=<entry>), never the table; then regenerate the stamps and " +
                    "ProofStamps.kt.",
            )
        }
    }

    private fun survey(folder: world.taqwa.timetables.gate.SurveyFolder): Item {
        val result = try {
            folder.load(officialDir).evaluate(roots)
        } catch (e: IllegalArgumentException) {
            return Item(Kind.FETCH_BROKEN, "the ${folder.folder} survey could not run", listOf(e.message ?: ""), "restore the calendar named")
        }
        val broken = result.broken()
        val what = "the ${folder.folder} survey (${folder.entryId}): ${result.used} calendars, ${result.placeDays} place-days"
        return if (broken.isEmpty()) {
            Item(Kind.GREEN, "$what, 0 unrecorded")
        } else {
            Item(
                Kind.EARLY_OR_LATE, what, broken.take(40),
                "the engine or the registry changed under a surveyed calendar: run the survey test " +
                    "(UkMawaqitSurveyTest, ContinentalMawaqitSurveyTest) and decide as rulings R75 and R87 did: fix the member, or record " +
                    "the outlier with its worst minutes.",
            )
        }
    }

    private companion object {
        /** A registry entry id as the catalogue's `entries` column names it: `sa.ummalqura`, `ps.gaza.awqaf`. */
        val ENTRY_ID = Regex("""\b[a-z]{2}\.[a-z0-9]+(?:\.[a-z0-9]+)*\b""")
    }

    /** The backup paragraph, and the reminder to upload when files were added since the last one. */
    private fun backupReminder(backup: BackupRun?, state: MonitorState): String? {
        if (backup == null) return null
        val last = state.lastReminder
        val newSince = backup.copied > 0 || last == null || (backup.newest != null && backup.newest > last)
        val text = "Backup: ${backup.copied} file${if (backup.copied == 1) "" else "s"} mirrored to ${backup.target} today (${backup.total} files in all)."
        if (!newSince) return "$text Nothing new since the last reminder ($last)."
        state.lastReminder = today
        return "$text Files were added since the last reminder${if (last == null) "" else " ($last)"}: upload the backup folder to Google Drive by hand."
    }
}
