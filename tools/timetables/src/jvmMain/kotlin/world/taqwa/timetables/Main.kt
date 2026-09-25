package world.taqwa.timetables

import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * `--cities site/cities.tsv --app shared/src/commonMain/composeResources --out _data/timetables.json
 * [--now 2026-09-25T00:07:00Z]`. Exits non-zero, listing every problem, when the curated list
 * does not hold up against the app's own city data.
 */
fun main(args: Array<String>) {
    val options = parse(args)
    val cities = File(options["--cities"] ?: usage("--cities is required"))
    val app = File(options["--app"] ?: usage("--app is required"))
    val out = File(options["--out"] ?: usage("--out is required"))
    val now = options["--now"]?.let(Instant::parse) ?: Clock.System.now()

    val catalog = try {
        Catalog.load(cities, app.resolve("files"))
    } catch (error: CatalogError) {
        System.err.println(error.message)
        exitProcess(1)
    }
    val document = try {
        Document(AppStrings(app)).build(catalog, now)
    } catch (error: IllegalStateException) {
        System.err.println(error.message)
        exitProcess(1)
    }
    out.absoluteFile.parentFile.mkdirs()
    out.writeText(Json.write(document))
    val pages = catalog.sumOf { it.languages.size }
    println("Wrote ${catalog.size} cities and $pages pages for $now to ${out.path}")
}

private fun parse(args: Array<String>): Map<String, String> {
    if (args.size % 2 != 0) usage("every option takes a value")
    return args.toList().chunked(2).associate { (key, value) ->
        if (key !in setOf("--cities", "--app", "--out", "--now")) usage("unknown option $key")
        key to value
    }
}

private fun usage(problem: String): Nothing {
    System.err.println("timetables: $problem")
    System.err.println("usage: --cities <tsv> --app <composeResources> --out <json> [--now <ISO instant>]")
    exitProcess(2)
}
