package world.taqwa.timetables.gate

import world.taqwa.timetables.Json
import java.io.File
import kotlin.system.exitProcess

/**
 * Task 11's "About these times" screen cannot read `tools/` at runtime (the app ships no stamp
 * files, and the engine module takes no resources), so it needs the stamps' headline numbers as
 * plain Kotlin: [world.taqwa.app.prayer.engine.registry.data.ProofStamps]. This writes that file
 * from every `tools/timetables/official/stamps/<entry>.json` (statistics only, so committing the
 * generated table is safe — see `Stamps.kt`'s own comment).
 *
 * Re-run after every stamp refresh (a `gate` run that changes a stamp):
 *
 *     ./gradlew -p tools/timetables generateProofStamps
 *
 * or directly:
 *
 *     --repo <repository root> [--stamps <stamps dir>] [--out <output .kt file>]
 *
 * Deterministic: the entries are sorted by id, so an unrelated stamp refresh never reorders lines
 * that did not change.
 */
fun main(args: Array<String>) {
    val options = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        options[key] = args.getOrNull(i + 1) ?: fail("$key needs a value")
        i += 2
    }
    val repo = File(options["--repo"] ?: fail("--repo is required"))
    val stampsDir = File(options["--stamps"] ?: repo.resolve("tools/timetables/official/stamps").path)
    val outFile = File(
        options["--out"] ?: repo.resolve(
            "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ProofStamps.kt",
        ).path,
    )

    val stampCount = stampsDir.listFiles { f -> f.extension == "json" }?.size ?: fail("no stamps directory at $stampsDir")
    outFile.parentFile.mkdirs()
    outFile.writeText(ProofStampsGenerator.generate(stampsDir))
    println("Wrote $stampCount proof stamps to ${outFile.relativeTo(repo).invariantSeparatorsPath}")
}

private fun fail(problem: String): Nothing {
    System.err.println("generateProofStamps: $problem")
    exitProcess(2)
}

/**
 * The generator proper, apart from `main`'s argument parsing, so a test can re-run it in memory
 * against the committed stamps and fail if `ProofStamps.kt` has drifted (a staleness guard: the
 * generated file is committed, so nothing re-runs the generator on a plain build).
 */
object ProofStampsGenerator {
    /** [stampsDir]'s `*.json` stamps, rendered as `ProofStamps.kt`'s exact source text. */
    fun generate(stampsDir: File): String {
        val files = stampsDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }
            ?: error("no stamps directory at $stampsDir")
        val stamps = files.map { readStamp(it) }.sortedBy { it.entryId }
        return render(stamps)
    }
}

/** One entry's headline numbers, read back from its stamp (see `Stamps.stamp` for the writer). */
private class ReadStamp(
    val entryId: String,
    val places: Int,
    val placeDays: Int,
    val ramadanDays: Int,
    val provenThrough: String?,
    val worstLateMinutes: Map<String, Int>,
    val worstLateByUnit: Map<String, Map<String, Int>>,
    val lateLimits: List<Triple<Int, String?, List<String>>>,
)

@Suppress("UNCHECKED_CAST")
private fun readStamp(file: File): ReadStamp {
    val root = Json.parse(file.readText()) as Map<String, Any?>
    fun worstOf(events: Map<String, Any?>): Map<String, Int> = events.entries.associate { (event, stats) ->
        event to ((stats as Map<String, Any?>)["worstLate"] as? Long ?: 0L).toInt()
    }
    val worstLate = worstOf(root["events"] as? Map<String, Any?> ?: emptyMap())
    // Per authority unit (the stamp's `units`, keyed by the unit's id): its own worst per event.
    val worstByUnit = (root["units"] as? Map<String, Any?> ?: emptyMap()).entries.associate { (unit, stats) ->
        unit to worstOf((stats as Map<String, Any?>)["events"] as? Map<String, Any?> ?: emptyMap())
    }
    val lateLimitsRaw = root["lateLimits"] as? Map<String, Any?> ?: emptyMap()
    // A trailing " (event)" disambiguates two limits that share a source and minutes but differ
    // in reason (Stamps.limits); the reason and events come from the entry's own fields below, so
    // it is only skipped over here, never parsed out.
    val lateLimitPattern = Regex("^(.*): (\\d+) min(?: \\(.+\\))?$")
    val lateLimits = lateLimitsRaw.entries.mapNotNull { (label, value) ->
        val match = lateLimitPattern.matchEntire(label) ?: return@mapNotNull null
        val minutes = match.groupValues[2].toInt()
        val entry = value as Map<String, Any?>
        val reason = entry["reason"] as? String
        val events2 = (entry["events"] as? List<Any?>)?.map { it as String } ?: emptyList()
        Triple(minutes, reason, events2)
    }
    return ReadStamp(
        entryId = root["entry"] as String,
        places = (root["places"] as? Long ?: 0L).toInt(),
        placeDays = (root["placeDays"] as? Long ?: 0L).toInt(),
        ramadanDays = (root["ramadanDays"] as? Long ?: 0L).toInt(),
        provenThrough = root["last"] as? String,
        worstLateMinutes = worstLate,
        worstLateByUnit = worstByUnit,
        lateLimits = lateLimits,
    )
}

private fun render(stamps: List<ReadStamp>): String = buildString {
    append(HEADER)
    append("object ProofStamps {\n")
    append("    /** One row per stamped entry (`tools/timetables/official/stamps/<id>.json`), sorted by id. */\n")
    append("    val byEntry: Map<String, ProofStamp> = listOf(\n")
    for (s in stamps) {
        append("        ProofStamp(\n")
        append("            entryId = ${literal(s.entryId)},\n")
        append("            places = ${s.places},\n")
        append("            placeDays = ${s.placeDays},\n")
        append("            ramadanDays = ${s.ramadanDays},\n")
        append("            provenThrough = ${s.provenThrough?.let(::literal) ?: "null"},\n")
        append("            worstLateMinutes = ${worstLiteral(s.worstLateMinutes)},\n")
        if (s.worstLateByUnit.isEmpty()) {
            append("            worstLateByUnit = emptyMap(),\n")
        } else {
            append("            worstLateByUnit = mapOf(\n")
            for ((unit, worst) in s.worstLateByUnit.entries.sortedBy { it.key }) {
                append("                ${literal(unit)} to ${worstLiteral(worst)},\n")
            }
            append("            ),\n")
        }
        val limits = s.lateLimits.sortedWith(compareBy({ it.first }, { it.third.firstOrNull() ?: "" }))
        if (limits.isEmpty()) {
            append("            lateLimits = emptyList(),\n")
        } else {
            append("            lateLimits = listOf(\n")
            for ((minutes, reason, events) in limits) {
                val eventsLiteral = events.sorted().joinToString(", ") { literal(it) }
                append("                ProofLateLimit(minutes = $minutes, reason = ${reason?.let(::literal) ?: "null"}, events = listOf($eventsLiteral)),\n")
            }
            append("            ),\n")
        }
        append("        ),\n")
    }
    append("    ).associateBy { it.entryId }\n\n")
    append("    /** The stamp for [entryId], or null where the gate has not checked it (a class D unit still awaiting data). */\n")
    append("    fun of(entryId: String): ProofStamp? = byEntry[entryId]\n")
    append("}\n")
}

private fun worstLiteral(worst: Map<String, Int>): String =
    "mapOf(" + worst.entries.sortedBy { it.key }.joinToString(", ") { (k, v) -> "${literal(k)} to $v" } + ")"

private fun literal(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private val HEADER = """
    // GENERATED FILE — do not edit by hand.
    //
    // Written by `./gradlew -p tools/timetables generateProofStamps` (GenerateProofStamps.kt) from
    // every `tools/timetables/official/stamps/<entry>.json`. Re-run it after every stamp refresh (a
    // `gate` run that changes a stamp) and commit the result: stamps are statistics only — place-days,
    // dates, minute counts — never an official time, so this file is safe to publish (spec §5).
    package world.taqwa.app.prayer.engine.registry.data

    /**
     * One registry entry's proof, as the About-times screen shows it (spec §2.3, task 11): how many
     * place-days and places the gate has checked it on, the last date it holds (`provenThrough`, null
     * before any stamp exists), the worst lateness seen per event over the whole entry
     * (`worstLateMinutes`, event keys as the gate writes them: "fajr", "sunrise", "dhuhr", "asr" or
     * "asrStandard"/"asrHanafi", "maghrib", "isha", "endOfEating", "imsak") and per authority unit
     * (`worstLateByUnit`, keyed by the unit's id; empty for an entry the gate checks without units),
     * and the late limits the gate held the entry to when the stamp was written ([ProofLateLimit],
     * ruling R41): the class's own (A 1, B 2, C 1 after the latest member, D 3), listed with a null
     * reason, and each recorded exception with its reason, each with the events it covered.
     *
     * A unit or entry with no stamp file (an authority not yet gated, or a class with no official
     * days) has no row: [ProofStamps.of] returns null, and the screen reads that as "not yet compared"
     * (spec §2.3's without-data wording), never as zero.
     */
    data class ProofStamp(
        val entryId: String,
        val places: Int,
        val placeDays: Int,
        val ramadanDays: Int,
        val provenThrough: String?,
        val worstLateMinutes: Map<String, Int>,
        val worstLateByUnit: Map<String, Map<String, Int>>,
        val lateLimits: List<ProofLateLimit>,
    )

    /**
     * One late limit the gate held this entry to and the events it covered (ruling R41): the class's
     * own with a null reason, or a recorded exception with its reason.
     */
    data class ProofLateLimit(val minutes: Int, val reason: String?, val events: List<String>)


""".trimIndent() + "\n"
