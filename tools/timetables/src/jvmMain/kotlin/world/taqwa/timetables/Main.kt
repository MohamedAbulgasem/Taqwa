package world.taqwa.timetables

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * `--cities site/cities.tsv --app shared/src/commonMain/composeResources --out _data/timetables.json
 * [--official tools/timetables/official] [--now 2026-09-25T00:07:00Z]`. Exits non-zero, listing
 * every problem, when the curated list does not hold up against the app's own city data.
 * `--official` is the folder holding the stamps, the gate rows and the surveys the proven rule
 * and the checks page read (spec §2, §5); relative to the working directory, the repository root
 * under `generate`.
 */
fun main(args: Array<String>) {
    val options = parse(args)
    val cities = File(options["--cities"] ?: usage("--cities is required"))
    val app = File(options["--app"] ?: usage("--app is required"))
    val out = File(options["--out"] ?: usage("--out is required"))
    val official = File(options["--official"] ?: "tools/timetables/official")
    val now = options["--now"]?.let(Instant::parse) ?: Clock.System.now()

    val catalog = try {
        Catalog.load(cities, app.resolve("files"))
    } catch (error: CatalogError) {
        System.err.println(error.message)
        exitProcess(1)
    }
    val document = try {
        Document(AppStrings(app), Stamp.load(official.resolve("stamps")), official).build(catalog, now)
    } catch (error: IllegalStateException) {
        System.err.println(error.message)
        exitProcess(1)
    }
    out.absoluteFile.parentFile.mkdirs()
    out.writeText(Json.write(document))
    notices(document).forEach(::println)
    println("${summary(document)} for $now to ${out.path}")
}

/**
 * The run's annotations (spec §2): `::notice::` per city the proven rule held, with its reason, and
 * `::warning::` per published city whose stamp ends before the last day of the month after the two
 * shown — a month's notice to run the gate on the authority's new table before the page is held.
 */
@Suppress("UNCHECKED_CAST")
internal fun notices(document: Map<String, Any?>): List<String> {
    val held = (document["held"] as List<Map<String, Any?>>).map { "::notice::${it["slug"]}: held — ${it["reason"]}" }
    val warnings = (document["cities"] as List<Map<String, Any?>>).mapNotNull { city ->
        val shown = (city["months"] as List<Map<String, Any?>>).last()
        val monthAfter = LocalDate(shown["year"] as Int, shown["month"] as Int, 1).plus(1, DateTimeUnit.MONTH)
        val lastDayAfter = monthAfter.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val through = LocalDate.parse((city["proof"] as Map<String, Any?>)["through"] as String)
        if (through >= lastDayAfter) return@mapNotNull null
        "::warning::${city["slug"]}: ${city["method"]}'s stamp ends $through; the page after next month would be held — run the gate on the new table"
    }
    return held + warnings
}

/** "Wrote N cities (M held) and P pages". */
@Suppress("UNCHECKED_CAST")
internal fun summary(document: Map<String, Any?>): String {
    val cities = document["cities"] as List<Map<String, Any?>>
    val held = document["held"] as List<*>
    val pages = cities.sumOf { (it["languages"] as List<*>).size }
    return "Wrote ${cities.size} cities (${held.size} held) and $pages pages"
}

private fun parse(args: Array<String>): Map<String, String> {
    if (args.size % 2 != 0) usage("every option takes a value")
    return args.toList().chunked(2).associate { (key, value) ->
        if (key !in setOf("--cities", "--app", "--out", "--official", "--now")) usage("unknown option $key")
        key to value
    }
}

private fun usage(problem: String): Nothing {
    System.err.println("timetables: $problem")
    System.err.println("usage: --cities <tsv> --app <composeResources> --out <json> [--official <dir>] [--now <ISO instant>]")
    exitProcess(2)
}
