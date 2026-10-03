package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import world.taqwa.timetables.Json
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * The `prove` task ([Prove], docs/MONITOR.md):
 *
 *     --repo <repository root> --official <root holding archive/> [--date yyyy-mm-dd] [--only <source>]...
 *     [--recipes <file>] [--index <file>] [--report <file>] [--dry-run]
 *
 * `--date` names the pinned folder (default today, UTC); `--report` writes the outcome as JSON (rows
 * added and left out with their reasons: dates, counts and minutes, never a printed time) for the
 * workflow's issue; `--dry-run` plans and checks without writing anything. Exits 0 when the gate is
 * green afterwards (whether or not anything was added), 2 when prove could not run: the gate red or
 * refused before anything was added, or its inputs unreadable.
 */
fun main(args: Array<String>) {
    val options = mutableListOf<Pair<String, String>>()
    var dryRun = false
    var i = 0
    while (i < args.size) {
        when (val key = args[i]) {
            "--dry-run" -> dryRun = true.also { i++ }
            "--repo", "--official", "--date", "--only", "--recipes", "--index", "--report" -> {
                options += key to (args.getOrNull(i + 1) ?: fail("$key needs a value"))
                i += 2
            }
            else -> fail("unknown option $key")
        }
    }
    fun all(key: String) = options.filter { it.first == key }.map { it.second }.filter { it.isNotBlank() }
    fun one(key: String) = all(key).lastOrNull()
    val repo = File(one("--repo") ?: fail("--repo is required"))
    val official = File(one("--official") ?: fail("--official is required"))
    if (!official.resolve("archive").isDirectory) fail("no archive/ under ${official.path}")
    val date = one("--date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: fail("--date $it (yyyy-mm-dd)") }
        ?: Clock.System.todayIn(TimeZone.UTC)
    val recipes = try {
        Recipes.load(File(one("--recipes") ?: repo.resolve("tools/timetables/official/monitor/recipes.tsv").path))
    } catch (e: IllegalArgumentException) {
        fail(e.message ?: "recipes.tsv unreadable")
    }
    val index = MonitorIndex.load(File(one("--index") ?: official.resolve("archive/tables/monitor/index.tsv").path))
    val only = all("--only").toSet()
    val unknown = only - recipes.map { it.source }.toSet()
    if (unknown.isNotEmpty()) fail("no recipe for ${unknown.sorted().joinToString(" ")}")
    val outcome = Prove(repo, official, date, recipes, index, only).run(write = !dryRun)
    one("--report")?.let { File(it).apply { parentFile?.mkdirs() }.writeText(Json.pretty(outcome.json() + ("date" to date.toString()) + ("dryRun" to dryRun))) }
    if (outcome.failure != null) {
        System.err.println("prove: ${outcome.failure}")
        exitProcess(2)
    }
    println("prove ${date}${if (dryRun) " (dry run)" else ""}: ${outcome.added.size} rows added, ${outcome.leftOut.size} left out; " +
        "gate ${outcome.rowsBefore} -> ${outcome.rowsAfter} rows, ${outcome.placeDaysBefore} -> ${outcome.placeDaysAfter} place-days, green")
    for (p in outcome.added) println("  added: ${p.label} (${p.days.size} days; columns from ${p.family})")
    for (l in outcome.leftOut) println("  left out: ${l.label}: ${l.reason.lineSequence().first()}")
}

private fun fail(problem: String): Nothing {
    System.err.println("prove: $problem")
    exitProcess(2)
}
