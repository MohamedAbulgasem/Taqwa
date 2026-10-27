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
    val lastChecked: String?,
    val worstLateMinutes: Map<String, Int>,
    val worstLateByUnit: Map<String, Map<String, Int>>,
    val lateLimits: List<Triple<Int, String?, List<String>>>,
    /** The entry's checked days as the stamp writes its runs ("2026-09-25..2026-11-01", or one date). */
    val checked: List<String> = emptyList(),
    /** Each unit's own runs, where they are not the entry's. */
    val checkedByUnit: Map<String, List<String>> = emptyMap(),
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
    // Ruling R115's checked runs ("2026-09-25..2026-11-01"), the entry's and each unit's where they differ: About's
    // date is the end of the run holding the day it is read on, so it never runs past a day no table was checked on
    // (ch.fids's and Diyanet's November and December 2026), as the city page's date never does.
    fun runsOf(value: Any?): List<String> = (value as? List<Any?>).orEmpty().map { it as String }
    val checked = runsOf(root["checked"])
    val checkedByUnit = (root["units"] as? Map<String, Any?> ?: emptyMap()).entries.mapNotNull { (unit, stats) ->
        val runs = runsOf((stats as Map<String, Any?>)["checked"])
        (unit to runs).takeIf { runs.isNotEmpty() && runs != checked }
    }.toMap()
    val lateLimitsRaw = root["lateLimits"] as? Map<String, Any?> ?: emptyMap()
    // A trailing " (event)" disambiguates two limits that share a source and minutes but differ
    // in reason (Stamps.limits); the reason and events come from the entry's own fields below, so
    // it is only skipped over here, never parsed out.
    val lateLimitPattern = Regex("^(.*): (\\d+) min(?: \\(.+\\))?$")
    val lateLimits = lateLimitsRaw.entries.mapNotNull { (label, value) ->
        val match = lateLimitPattern.matchEntire(label) ?: return@mapNotNull null
        val minutes = match.groupValues[2].toIntOrNull()?.takeIf { it <= 9999 } ?: error("a late limit of ${match.groupValues[2]} min")
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
        lastChecked = root["last"] as? String,
        worstLateMinutes = worstLate,
        worstLateByUnit = worstByUnit,
        lateLimits = lateLimits,
        checked = checked,
        checkedByUnit = checkedByUnit,
    )
}

/**
 * Each entry's row is built in a function of its own, and an entry's units in functions of at most [UNITS_PER_FUNCTION]
 * each: one initializer holding every row passes the JVM's 64 KB limit for a method once an entry has hundreds of
 * units (Umm al-Qura's 173 and QMDB's 92 city units, the city-points round of 9 Oct 2026).
 */
private fun render(stamps: List<ReadStamp>): String = buildString {
    val names = stamps.map { functionName(it.entryId) }
    require(names.toSet().size == names.size) { "two entry ids share a function name: $names" }
    append(HEADER)
    append("object ProofStamps {\n")
    append("    /** One row per stamped entry (`tools/timetables/official/stamps/<id>.json`), sorted by id. */\n")
    append("    val byEntry: Map<String, ProofStamp> = listOf(\n")
    for (name in names) append("        $name(),\n")
    append("    ).associateBy { it.entryId }\n\n")
    append("    /** The stamp for [entryId], or null where the gate has not checked it (a class D unit still awaiting data). */\n")
    append("    fun of(entryId: String): ProofStamp? = byEntry[entryId]\n")
    for ((s, name) in stamps.zip(names)) {
        val chunks = s.worstLateByUnit.entries.sortedBy { it.key }.chunked(UNITS_PER_FUNCTION)
        append("\n")
        append("    private fun $name() = ProofStamp(\n")
        append("        entryId = ${literal(s.entryId)},\n")
        append("        places = ${s.places},\n")
        append("        placeDays = ${s.placeDays},\n")
        append("        ramadanDays = ${s.ramadanDays},\n")
        append("        lastChecked = ${s.lastChecked?.let(::literal) ?: "null"},\n")
        append("        worstLateMinutes = ${worstLiteral(s.worstLateMinutes)},\n")
        when (chunks.size) {
            0 -> append("        worstLateByUnit = emptyMap(),\n")
            1 -> append("        worstLateByUnit = ${name}Units(),\n")
            else -> append("        worstLateByUnit = ${chunks.indices.joinToString(" + ") { "${name}Units${it + 1}()" }},\n")
        }
        val limits = s.lateLimits.sortedWith(compareBy({ it.first }, { it.third.firstOrNull() ?: "" }))
        if (limits.isEmpty()) {
            append("        lateLimits = emptyList(),\n")
        } else {
            append("        lateLimits = listOf(\n")
            for ((minutes, reason, events) in limits) {
                val eventsLiteral = events.sorted().joinToString(", ") { literal(it) }
                append("            ProofLateLimit(minutes = $minutes, reason = ${reason?.let(::literal) ?: "null"}, events = listOf($eventsLiteral)),\n")
            }
            append("        ),\n")
        }
        append("        checked = ${runsLiteral(s.checked)},\n")
        val runChunks = s.checkedByUnit.entries.sortedBy { it.key }.chunked(UNITS_PER_FUNCTION)
        when (runChunks.size) {
            0 -> {}
            1 -> append("        checkedByUnit = ${name}Checked(),\n")
            else -> append("        checkedByUnit = ${runChunks.indices.joinToString(" + ") { "${name}Checked${it + 1}()" }},\n")
        }
        append("    )\n")
        runChunks.forEachIndexed { index, chunk ->
            val suffix = if (runChunks.size == 1) "" else "${index + 1}"
            append("\n")
            append("    private fun ${name}Checked$suffix(): Map<String, List<String>> = mapOf(\n")
            for ((unit, runs) in chunk) append("        ${literal(unit)} to ${runsLiteral(runs)},\n")
            append("    )\n")
        }
        chunks.forEachIndexed { index, chunk ->
            val suffix = if (chunks.size == 1) "" else "${index + 1}"
            append("\n")
            append("    private fun ${name}Units$suffix(): Map<String, Map<String, Int>> = mapOf(\n")
            for ((unit, worst) in chunk) append("        ${literal(unit)} to ${worstLiteral(worst)},\n")
            append("    )\n")
        }
    }
    append("}\n")
}

/** At most this many units' rows in one generated function (see [render]). */
private const val UNITS_PER_FUNCTION = 40

/** The only entry ids a function name is made from: anything else in a stamp is refused, never written as code. */
private val ENTRY_ID = Regex("[a-z][a-z0-9]*([._-][a-z0-9]+)*")

/** Names an entry's function may not take: Kotlin's hard keywords, and another entry's helpers (`…Units`, `…Checked`). */
private val KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
    "when", "while",
)
private val HELPER = Regex(".*(Units|Checked)[0-9]*")

/** An entry id as a function name: "ae.iacad.dubai" → "aeIacadDubai". */
private fun functionName(entryId: String): String {
    require(ENTRY_ID.matches(entryId)) { "an entry id that is not lower-case letters, digits and . - _: ${literal(entryId)}" }
    val words = entryId.split('.', '-', '_').filter { it.isNotEmpty() }
    val name = words.first() + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    require(name !in KEYWORDS && !HELPER.matches(name)) { "an entry id whose function name is a Kotlin keyword or a helper's: ${literal(entryId)}" }
    return name
}

private fun worstLiteral(worst: Map<String, Int>): String =
    "mapOf(" + worst.entries.sortedBy { it.key }.joinToString(", ") { (k, v) -> "${literal(k)} to $v" } + ")"

private fun runsLiteral(runs: List<String>): String =
    if (runs.isEmpty()) "emptyList()" else "listOf(" + runs.joinToString(", ") { literal(it) } + ")"

/**
 * A Kotlin string literal for any text a stamp holds: the backslash, the quote, the dollar (a template otherwise) and
 * every control character escaped, so a stamp's text can never end the literal or run as code (finding D9). The
 * monitor's publish job writes the same bytes with tools/timetables/monitor/ci/proof_stamps.py (ProofStampsPortTest).
 */
private fun literal(s: String): String = buildString {
    append('"')
    for (ch in s) {
        when {
            ch == '\\' -> append("\\\\")
            ch == '"' -> append("\\\"")
            ch == '$' -> append("\\$")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch.code < 0x20 || ch == ' ' || ch == ' ' -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
    }
    append('"')
}

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
     * place-days and places the gate has checked it on, the last date any row holds (`lastChecked`, the
     * stamp's `last`, null before any stamp exists: the end of the envelope, never a "checked through"
     * claim, since the days before it may have holes), the worst lateness seen per event over the whole entry
     * (`worstLateMinutes`, event keys as the gate writes them: "fajr", "sunrise", "dhuhr", "asr" or
     * "asrStandard"/"asrHanafi", "maghrib", "isha", "endOfEating", "imsak") and per authority unit
     * (`worstLateByUnit`, keyed by the unit's id; empty for an entry the gate checks without units),
     * and the late limits the gate held the entry to when the stamp was written ([ProofLateLimit],
     * ruling R41): the class's own (A 1, B 2, C 1 after the latest member, D 3), listed with a null
     * reason, and each recorded exception with its reason, each with the events it covered.
     * `checked` is the days any row compared, as the stamp's runs ("2026-09-25..2026-11-01", or one
     * date), and `checkedByUnit` a unit's own runs where they are not the entry's (ruling R115): About's
     * "checked through" date is the end of the run holding the day it is read on, so it never runs past a
     * day the place's own table was not checked on, as its city page's date never does.
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
        val lastChecked: String?,
        val worstLateMinutes: Map<String, Int>,
        val worstLateByUnit: Map<String, Map<String, Int>>,
        val lateLimits: List<ProofLateLimit>,
        val checked: List<String> = emptyList(),
        val checkedByUnit: Map<String, List<String>> = emptyMap(),
    )

    /**
     * One late limit the gate held this entry to and the events it covered (ruling R41): the class's
     * own with a null reason, or a recorded exception with its reason.
     */
    data class ProofLateLimit(val minutes: Int, val reason: String?, val events: List<String>)


""".trimIndent() + "\n"
