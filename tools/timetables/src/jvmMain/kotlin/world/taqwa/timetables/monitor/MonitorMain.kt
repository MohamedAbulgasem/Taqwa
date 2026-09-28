package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import world.taqwa.timetables.Json
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateError
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Stamps
import world.taqwa.timetables.gate.SurveyFolder
import world.taqwa.timetables.gate.Surveys
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * The `monitor` task, the check half of `scripts/monitor.sh` (spec §5, brief P, ruling R93):
 *
 *     --repo <repository root> --official <official root> [--monitor <state dir>] [--today yyyy-mm-dd]
 *     [--only <source>]... [--check-all] [--skip-full]
 *
 * Reads what `fetch.py` wrote (the tables' index, the fetch log) and what the shell wrote about the
 * backup, checks every table whose signature (content, metadata, engine) differs from the one it was
 * last checked with and every table red last run ([TableCheck]), runs the whole gate and every
 * survey, the horizons ([Horizons]) and the manual sources' due dates, and writes the report to
 * `<state dir>/reports/<today>.md` and `latest.md`. `last-run.json` carries the exit code, the
 * summary line, whether the attention set changed since the last notification (the shell notifies,
 * the cloud run opens or updates its issue, only then), and the issue's body: 0 all green, 1
 * attention needed (2, the monitor itself failed, is the shell's, when this never gets to write it).
 *
 * `--check-all` checks every held table; `--skip-full` leaves the whole gate and the surveys out
 * (a quick look at the fetched tables alone).
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

class MonitorRun(val exitCode: Int, val summary: String, val report: String, val reportFile: File, val attentionChanged: Boolean)

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
        val state = MonitorState(monitorDir.resolve("state.json"))

        // 1. The fetch: what worked, what did not.
        if (fetchedToday) {
            val runs = log!!.sources.filter { only.isEmpty() || it.source in only }
            if (log.error != null) {
                items += Item(
                    Kind.FETCH_BROKEN, "the fetch driver itself broke", log.error.lines().filter { it.isNotBlank() }.takeLast(12),
                    "read the fetch log under monitor/fetch/: the sources after the break were not fetched this run; fix fetch.py or the state file it choked on.",
                    key = "fetch:driver",
                )
            }
            for (r in runs.filter { it.status == "failed" || it.status == "partial" }) {
                items += Item(
                    Kind.FETCH_BROKEN, "${r.source} (${r.status})", r.message.lines().filter { it.isNotBlank() },
                    "read the message: an endpoint moved or changed shape (fix the fetcher in tools/timetables/monitor/fetchers/, " +
                        "the raw response is under archive/raw/monitor/), a site refused the request (try by hand, respect its terms; " +
                        "if it stays closed, make the source manual in sources.tsv with the month its next edition is expected), or the " +
                        "run's time budget was spent (a host that never answers trips the breaker after three failures).",
                    key = "fetch:${r.source}",
                )
            }
            val ok = runs.filter { it.status == "ok" }
            val skipped = runs.filter { it.status == "skipped" }.groupBy { it.message }
            val statuses = runs.flatMap { it.tables.values }
            fun count(s: String) = statuses.count { it == s }
            items += Item(
                Kind.GREEN,
                "fetched ${ok.size} source${if (ok.size == 1) "" else "s"} (${ok.sumOf { it.requests }} requests): " +
                    "${count("new")} new tables, ${count("changed")} changed, ${count("unchanged")} unchanged" +
                    skipped.entries.joinToString("") { (why, list) -> "; $why: ${list.joinToString { it.source }}" },
                ok.map { "${it.source}: ${it.requests} requests, ${it.tables.size} tables" + (if (it.message.isNotBlank()) " — ${it.message}" else "") },
            )
        }

        // 2. Each table whose signature differs from the one it was last checked with (its content,
        //    its metadata, the engine), and each table red last run, until it is green (review I8).
        val engineHash = Stamps.wholeEngineHash(repo)
        val check = TableCheck(roots, officialDir)
        val records = state.checked
        for (table in index) {
            val record = records[table.id]
            val signature = signature(table, engineHash)
            if (!checkAll && record != null && record.signature == signature && !record.red) continue
            val status = TableCheck.Status(
                isNew = record == null, contentChanged = record != null && record.hash.isNotEmpty() && record.hash != table.hash,
                wasRed = record?.red == true, lateness = record?.lateness,
            )
            val outcome = check.check(table, status, today)
            items += outcome.items
            records[table.id] = CheckRecord(signature, table.hash, today, outcome.red, outcome.lateness)
        }
        if (only.isEmpty()) records.keys.retainAll(index.map { it.id }.toSet())

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
                    key = "due:${s.id}",
                )
            }
        }

        // 6. The backup: its reminder, or that it did not run (review I2).
        val (backupItem, reminder) = backupStatus(BackupRun.load(monitorDir.resolve("backup.json")), state, fetchedToday)
        if (backupItem != null) items += backupItem

        // 7. The attention set against the one last notified (ruling R93: notify on change only).
        val attention = Report.attention(items)
        val current = attention.associate { it.key to Notified(it.days, it.worst) }
        val before = state.notified
        val changed = current.keys != before.keys ||
            current.any { (key, now) -> before[key]?.let { now.days > it.days || now.worst > it.worst } == true }
        if (changed) state.notified = current
        val exitCode = if (attention.isNotEmpty()) 1 else 0

        val summary = Report.summary(items) + (if (reminder != null && "upload" in reminder) " Plus the backup reminder." else "")
        val text = Report.render(today, items, reminder, fetchedToday, changed = if (attention.isEmpty() && before.isEmpty()) null else changed)
        val reportFile = monitorDir.resolve("reports/$today.md")
        writeAtomic(reportFile, text)
        writeAtomic(monitorDir.resolve("latest.md"), text)
        state.note("lastRun", linkedMapOf("date" to today.toString(), "exit" to exitCode, "summary" to summary))
        state.save()
        val reportPath = reportFile.absoluteFile.relativeToOrNull(official.absoluteFile)?.invariantSeparatorsPath ?: reportFile.path
        writeAtomic(
            monitorDir.resolve("last-run.json"),
            Json.pretty(
                linkedMapOf(
                    "date" to today.toString(), "exit" to exitCode, "summary" to summary, "report" to reportPath,
                    "attentionChanged" to changed, "neverEarly" to attention.count { it.kind == Kind.NEVER_EARLY },
                    "attention" to attention.size, "issue" to Report.issueBody(today, items),
                ),
            ),
        )
        return MonitorRun(exitCode, summary, text, reportFile, changed)
    }

    /** What a check depends on: the table's content and metadata, and the whole engine. */
    private fun signature(table: MonitorTable, engineHash: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(table.metadata.toByteArray())
        digest.update(0)
        digest.update(engineHash.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun fullGate(): List<Item> {
        val manifest = try {
            GateManifest.load(officialDir.resolve("gate"))
        } catch (e: GateError) {
            return listOf(Item(Kind.FETCH_BROKEN, "the gate files could not be read", e.problems, "fix the gate file named", key = "gate:files"))
        }
        val result = try {
            Gate(roots).evaluate(manifest)
        } catch (e: GateError) {
            return listOf(Item(Kind.FETCH_BROKEN, "the gate could not run", e.problems, "restore the archive file named, or fix the gate row", key = "gate:run"))
        }
        val what = "the gate: ${result.checkedRows} rows, ${result.placeDays} place-days, ${result.entries.size} entries"
        val never = result.entries.values.flatMap { result.neverEarly(it) }
        val late = result.entries.values.flatMap { result.overLimit(it) + result.unchecked(it) }
        if (never.isEmpty() && late.isEmpty()) return listOf(Item(Kind.GREEN, "$what, 0 broken"))
        val items = mutableListOf<Item>()
        if (never.isNotEmpty()) {
            val broken = result.entries.values.filter { it.neverEarlyBroken > 0 }
            items += Item(
                Kind.NEVER_EARLY, what, never.map { it.replace("\n", "; ") },
                "the engine or the registry changed under a held table: `./gradlew -p tools/timetables gate -Pentry=<entry>` shows the " +
                    "days; fix the method or its margins (refit with -Pfit=<entry>), never the table; then regenerate the stamps and " +
                    "ProofStamps.kt.",
                key = "gate", group = 1,
                days = broken.sumOf { s -> s.events.values.sumOf { it.brokenDates.size } + s.outOfOrder },
                worst = broken.maxOfOrNull { s -> s.events.values.maxOfOrNull { it.worstEarly } ?: 0 } ?: 0,
                label = "the gate (${broken.joinToString(", ") { it.entry.id }})",
            )
        }
        if (late.isNotEmpty()) {
            items += Item(
                Kind.GATE_LATE, what, late.map { it.replace("\n", "; ") },
                "the stamp is red: refit the margins (`./gradlew -p tools/timetables gate -Pfit=<entry>`) or record the exception in the " +
                    "registry (rulings R37, R41); a capped Maghrib left unchecked wants the most-followed member's table held at that point.",
                key = "gate:late",
            )
        }
        return items
    }

    private fun survey(folder: SurveyFolder): Item {
        val result = try {
            folder.load(officialDir).evaluate(roots)
        } catch (e: IllegalArgumentException) {
            return Item(Kind.FETCH_BROKEN, "the ${folder.folder} survey could not run", listOf(e.message ?: ""), "restore the calendar named", key = "survey:${folder.folder}:run")
        }
        val broken = result.broken()
        val what = "the ${folder.folder} survey (${folder.entryId}): ${result.used} calendars, ${result.placeDays} place-days"
        return if (broken.isEmpty()) {
            Item(Kind.GREEN, "$what, 0 unrecorded")
        } else {
            val past = result.covered.filter { !it.within }
            Item(
                Kind.NEVER_EARLY, what, broken.take(40),
                "the engine or the registry changed under a surveyed calendar: run the survey test " +
                    "(UkMawaqitSurveyTest, ContinentalMawaqitSurveyTest) and decide as rulings R75 and R87 did: fix the member, or record " +
                    "the outlier with its worst minutes.",
                key = "survey:${folder.folder}", group = 2,
                days = result.unrecorded.map { it.calendar to it.date }.distinct().size + past.sumOf { it.allDays },
                worst = maxOf(result.unrecorded.maxOfOrNull { it.minutes } ?: 0, past.maxOfOrNull { it.worst } ?: 0),
                label = "the ${folder.folder} survey (${result.unrecorded.map { it.calendar }.distinct().size} calendars)",
            )
        }
    }

    private companion object {
        /** A registry entry id as the catalogue's `entries` column names it: `sa.ummalqura`, `ps.gaza.awqaf`. */
        val ENTRY_ID = Regex("""\b[a-z]{2}\.[a-z0-9]+(?:\.[a-z0-9]+)*\b""")

        const val BACKUP_FIX = "the mirror is TAQWA_OFFICIAL_BACKUP's archive/ folder: point it at the folder, or unset it where the private " +
            "repository is the backup (ruling R94); a run that fetched new captures left them in one place only until it runs."
    }

    /**
     * The backup paragraph (with the reminder to upload when files were added since the last one),
     * or the item that says the mirror did not run: configured and failed, or stale after a fetch.
     */
    private fun backupStatus(backup: BackupRun?, state: MonitorState, fetchedToday: Boolean): Pair<Item?, String?> {
        if (backup == null) return null to "Backup: no mirror has run here yet."
        if (backup.date != today) {
            return if (fetchedToday) {
                Item(Kind.BACKUP_FAILED, "the backup step did not run this run", listOf("monitor/backup.json is from ${backup.date}, and a fetch ran today"), BACKUP_FIX, key = "backup") to null
            } else {
                null to null
            }
        }
        if (backup.error != null) {
            return Item(Kind.BACKUP_FAILED, "the backup mirror was configured and did not run", listOf(backup.error), BACKUP_FIX, key = "backup") to null
        }
        if (backup.skipped != null) return null to "Backup: not mirrored (${backup.skipped})."
        val last = state.lastReminder
        val newSince = backup.copied > 0 || last == null || (backup.newest != null && backup.newest > last)
        val text = "Backup: ${backup.copied} file${if (backup.copied == 1) "" else "s"} mirrored to ${backup.target} today (${backup.total} files in all)."
        if (!newSince) return null to "$text Nothing new since the last reminder ($last)."
        state.lastReminder = today
        return null to "$text Files were added since the last reminder${if (last == null) "" else " ($last)"}: upload the backup folder to Google Drive by hand."
    }
}
