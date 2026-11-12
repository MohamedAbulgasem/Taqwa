package world.taqwa.timetables.sanity

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * The `sunSanity` task ([SunSanity]):
 *
 *     --repo <repository root> [--from yyyy-mm-dd] [--to yyyy-mm-dd] [--every-days n] [--every-city n]
 *     [--chosen-per-entry n] [--other-per-entry n] [--threads n] [--out <report file>]
 *
 * The window is today through [MONTHS_AHEAD] months on by default (every season once, and the next one's start).
 * Prints the report (and writes it to `--out`). Exits 1 on any [SunSanity.problems]: a finding not listed in
 * `tools/timetables/official/sun-sanity/open-findings.tsv`, an excused group not listed in `excused.tsv`, or a listed
 * one past its ceilings; 0 otherwise. The report says, too, which listed groups are no longer found. `--every-city`
 * keeps every n-th app city (unit and region points are always all kept); `--chosen-per-entry` and `--other-per-entry`
 * set how many app cities each Settings choice is checked at ([SunSanity.points]).
 */
fun main(args: Array<String>) {
    val options = mutableMapOf<String, String>()
    var i = 0
    val known = setOf("--repo", "--from", "--to", "--every-days", "--every-city", "--chosen-per-entry", "--other-per-entry", "--threads", "--out")
    while (i < args.size) {
        val key = args[i]
        require(key in known) { "unknown option $key" }
        options[key] = args.getOrNull(i + 1) ?: error("$key needs a value")
        i += 2
    }
    val repo = File(options["--repo"] ?: error("--repo is required"))
    val from = options["--from"]?.let(LocalDate::parse) ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
    val to = options["--to"]?.let(LocalDate::parse) ?: from.plus(MONTHS_AHEAD, DateTimeUnit.MONTH)
    val cities = SunSanity.cities(repo.resolve("shared/src/commonMain/composeResources/files/cities.csv"))
    val points = SunSanity.points(
        cities,
        everyCity = options["--every-city"]?.toInt() ?: 1,
        chosenPerEntry = options["--chosen-per-entry"]?.toInt() ?: 40,
        otherPerEntry = options["--other-per-entry"]?.toInt() ?: 400,
    )
    val started = System.nanoTime()
    val result = SunSanity.run(
        points, from, to,
        everyDays = options["--every-days"]?.toInt() ?: 1,
        threads = options["--threads"]?.toInt() ?: Runtime.getRuntime().availableProcessors(),
    )
    val open = SunSanity.openFindings(repo.resolve(OPEN_FINDINGS))
    val excused = SunSanity.excusedGroups(repo.resolve(EXCUSED))
    val report = SunSanity.report(result, open, excused) +
        "\n(${"%.0f".format((System.nanoTime() - started) / 1e9)} s)\n"
    print(report)
    options["--out"]?.let { File(it).apply { parentFile?.mkdirs() }.writeText(report) }
    if (SunSanity.problems(result, open, excused).isNotEmpty()) exitProcess(1)
}

const val OPEN_FINDINGS = "tools/timetables/official/sun-sanity/open-findings.tsv"
const val EXCUSED = "tools/timetables/official/sun-sanity/excused.tsv"

/** The default window's length: today through 13 months on. */
private const val MONTHS_AHEAD = 13
