package world.taqwa.timetables.gate

import java.io.File
import kotlin.system.exitProcess

/**
 * The `gate` task:
 *
 *     --repo <repository root> --official <official root> [--group <gate file>]... [--entry <id>]...
 *     [--fit <id>] [--no-stamps]
 *
 * Reads every `tools/timetables/official/gate/<group>.tsv` file (or the named groups and entries),
 * checks the engine against each held table under the official root, prints the report and writes
 * a stamp for each entry with checked days into `tools/timetables/official/stamps/`. `--fit` also prints that
 * entry's fitted margins and their held-out table. Exits 1 when a promise is broken, 2 when the
 * inputs are wrong.
 */
fun main(args: Array<String>) {
    val options = mutableListOf<Pair<String, String>>()
    var stamps = true
    var i = 0
    while (i < args.size) {
        when (val key = args[i]) {
            "--no-stamps" -> stamps = false.also { i++ }
            "--repo", "--official", "--group", "--entry", "--fit" -> {
                options += key to (args.getOrNull(i + 1) ?: fail("$key needs a value"))
                i += 2
            }
            else -> fail("unknown option $key")
        }
    }
    fun all(key: String) = options.filter { it.first == key }.map { it.second }
    val repo = File(all("--repo").singleOrNull() ?: fail("--repo is required"))
    val official = File(all("--official").singleOrNull() ?: fail("--official is required"))
    val gateDir = repo.resolve("tools/timetables/official/gate")
    val roots = OfficialRoots(official, repo.resolve("tools/timetables/official"))

    try {
        val manifest = GateManifest.load(gateDir).only(all("--group").toSet(), all("--entry").toSet())
        val result = Gate(roots).evaluate(manifest)
        print(result.report())
        if (stamps) {
            val written = Stamps.write(result, repo.resolve("tools/timetables/official/stamps"), repo)
            if (written.isNotEmpty()) println("\nStamps: ${written.joinToString(", ") { it.name }}")
        }
        for (entry in all("--fit")) {
            println()
            print(Fitter(roots, GateManifest.load(gateDir)).fit(entry).report())
        }
        if (result.violations().isNotEmpty()) exitProcess(1)
    } catch (error: GateError) {
        System.err.println(error.message)
        exitProcess(2)
    }
}

private fun fail(problem: String): Nothing {
    System.err.println("gate: $problem")
    exitProcess(2)
}
