package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.timetables.Json
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * Spec §5 / ruling R86: `release.sh` and `ios-release.sh` refuse to build on a stale or red stamp.
 * This is that check, run before either script builds anything:
 *
 *     ./gradlew -p tools/timetables checkStamps [-Pramadan=<Hijri year>]
 *
 * or directly `--repo <repository root> [--ramadan <Hijri year>]`. It needs no archive: every table
 * read the gate would otherwise do is skipped, since a stamp's `engineHash` never depends on a
 * table's printed times, only on the resolution each gate row reaches (the entry, its point, its
 * unit and its method, [Stamps.fingerprint]) — the same resolution [Gate.setUp] computes whether or
 * not a row's table is held. So a stamp this finds fresh here is fresh wherever the gate next runs
 * with the archive restored, and the reverse: a stamp gone stale here is exactly what the next
 * `gate` run would refresh. [fingerprintsByEntry] is the one function both [Gate] (when a row's
 * table is held, so it can write a fresh stamp) and this (which never holds one) build an entry's
 * fingerprints with.
 *
 * Four ways a release must not go out:
 *
 * - **red**: the stamp's own `broken` is over 0 (an early start, a late end, a cell over its limit
 *   or a day out of order the last time `gate` ran — already the one figure the stamp keeps,
 *   [EntryStats.broken]).
 * - **stale**: the stamp's `engineHash` no longer matches what the current engine core and this
 *   entry's current methods give at the points the manifest's rows check it at.
 * - **missing**: the manifest has rows for an entry with no `official/stamps/<entry>.json` at all
 *   (a registry entry gated once, never stamped, or a stamp file removed by hand).
 * - **`ProofStamps.kt` stale**: what [ProofStampsGenerator] would write from the committed stamps
 *   differs from the committed file — the About-times screen would show numbers no stamp backs.
 *
 * And a fifth for the Ramadan build alone, opted into with `--ramadan` (spec §5's Ramadan release
 * rule, rulings R145, R146 and R148): **Ramadan not proven** — an A or B stamp with a unit whose
 * checked runs of Fajr, the end of eating or Maghrib ([Ramadan.EVENTS]) leave a day of that tabular
 * Ramadan ([Ramadan.of]) or the day either side of it ([Ramadan.guardFirst]..[Ramadan.guardLast])
 * unchecked. Demoted entries (the stamp's class no longer A or B) are not held to it, and a
 * Ramadan whose guarded days ended before today is refused as a typo ([pastRamadan]). Without
 * `--ramadan` the check only hints, inside [Ramadan.isNear]'s window, that the Ramadan build runs
 * with it; the release scripts pass it from `TAQWA_RAMADAN=<Hijri year>`.
 */
object CheckStamps {

    /** One entry's problems; an entry with none is fresh and green, and is left out of [Result.entries]. */
    data class EntryProblem(val entryId: String, val messages: List<String>)

    class Result(val entries: List<EntryProblem>, val proofStampsStale: Boolean, val ramadan: Ramadan? = null) {
        val ok: Boolean get() = entries.isEmpty() && !proofStampsStale

        /** One line per problem, the entry named first; empty when [ok]. */
        fun report(): String = buildString {
            for (e in entries) for (m in e.messages) appendLine("${e.entryId}: $m")
            if (proofStampsStale) {
                appendLine(
                    "ProofStamps.kt does not match the committed stamps — re-run " +
                        "./gradlew -p tools/timetables generateProofStamps and commit the result",
                )
            }
        }
    }

    /**
     * Every fingerprint [manifest]'s rows resolve to, per entry ([Gate.setUp]'s resolution, the
     * same one a `gate` run reaches whether or not [roots] holds the archive — only the table read,
     * which this never needs, depends on that). A row a bare manifest cannot resolve by itself (an
     * unknown entry, unit, zone or format) is the manifest's own mistake and fails loudly, exactly
     * as a `gate` run would refuse it, rather than silently leaving an entry's fingerprints short.
     */
    fun fingerprintsByEntry(
        manifest: GateManifest,
        roots: OfficialRoots,
        lookup: (String) -> RegistryEntry? = Registry::byId,
    ): Map<String, Set<String>> {
        val problems = mutableListOf<String>()
        val result = sortedMapOf<String, MutableSet<String>>()
        for (row in manifest.rows) {
            val setup = Gate.setUp(row, roots, null, problems, lookup) ?: continue
            result.getOrPut(setup.entry.id) { sortedSetOf() } += Stamps.fingerprint(setup.resolution)
        }
        if (problems.isNotEmpty()) throw GateError(problems)
        return result
    }

    /**
     * [manifest] against [stampsDir]'s committed stamps and [proofStampsFile]'s committed table,
     * reading no table ([roots] supplies [Gate.setUp] only the checkout's own `official/`, never an
     * archive — see the class comment). With [ramadan], every A/B unit's checked runs must hold each
     * of its days too.
     */
    fun check(
        repo: File,
        stampsDir: File,
        manifest: GateManifest,
        roots: OfficialRoots,
        proofStampsFile: File,
        lookup: (String) -> RegistryEntry? = Registry::byId,
        ramadan: Ramadan? = null,
    ): Result {
        val coreHash = Stamps.coreHash(repo)
        val fingerprints = fingerprintsByEntry(manifest, roots, lookup)
        val stampFiles = stampsDir.listFiles { f -> f.extension == "json" }?.associateBy { it.nameWithoutExtension }.orEmpty()

        val entries = fingerprints.entries.mapNotNull { (entryId, fp) ->
            val messages = mutableListOf<String>()
            val file = stampFiles[entryId]
            if (file == null) {
                messages += "has gate rows but no stamp (official/stamps/$entryId.json is missing — " +
                    "run ./gradlew -p tools/timetables gate)"
            } else {
                @Suppress("UNCHECKED_CAST")
                val stamp = Json.parse(file.readText()) as Map<String, Any?>
                val broken = (stamp["broken"] as? Long ?: 0L).toInt()
                if (broken > 0) {
                    messages += "stamp is red ($broken broken — run ./gradlew -p tools/timetables gate and see why)"
                }
                val stored = stamp["engineHash"] as? String
                val expected = Stamps.engineHash(coreHash, fp)
                if (stored != expected) {
                    messages += "stamp is stale (engineHash $stored, the current engine and methods give $expected — " +
                        "run ./gradlew -p tools/timetables gate then generateProofStamps)"
                }
                if (ramadan != null) {
                    val summary = StampSummary.of(stamp, entryId)
                    if (summary.isAOrB) {
                        val gaps = summary.ramadanGaps(ramadan)
                        if (gaps.isNotEmpty()) {
                            messages += "$ramadan is not proven: ${summary.describe(ramadan, gaps)} — fetch the authority's table for " +
                                "those dates (with its Fajr, end of eating and Maghrib: ruling R148), add gate rows (split test) and re-run " +
                                "the gate, or demote the entry's class in the registry and re-run the gate (spec §5)"
                        }
                    }
                }
            }
            if (messages.isEmpty()) null else EntryProblem(entryId, messages)
        }

        val proofStale = !proofStampsFile.isFile || ProofStampsGenerator.generate(stampsDir) != proofStampsFile.readText()
        return Result(entries, proofStale, ramadan)
    }

    /**
     * Why `--ramadan` refuses [ramadan] on [today], or null: a month whose guarded days ([Ramadan.guardLast]) ended
     * before today proves nothing about the build being cut, and is a typo for the next one (1447 for 1448 holds the
     * stamps to a Ramadan already past, and could pass).
     */
    fun pastRamadan(ramadan: Ramadan, today: LocalDate): String? {
        if (ramadan.guardLast >= today) return null
        val next = Ramadan.nextFrom(today)
        return "--ramadan ${ramadan.hijriYear}: $ramadan ended before today ($today); the next is ${next.hijriYear} " +
            "(${next.first}..${next.last}), so a Ramadan build runs with -Pramadan=${next.hijriYear} (TAQWA_RAMADAN=${next.hijriYear})"
    }

    /**
     * What a run without `--ramadan` says on [today], inside the eight weeks before a Ramadan and
     * through it: the Ramadan build runs with it. Null outside the window.
     */
    fun ramadanHint(today: LocalDate): String? {
        val ramadan = Ramadan.nextFrom(today)
        if (!ramadan.isNear(today)) return null
        return "checkStamps: $ramadan begins about ${ramadan.first}: the Ramadan build runs this check with " +
            "-Pramadan=${ramadan.hijriYear} (TAQWA_RAMADAN=${ramadan.hijriYear} for scripts/release.sh and scripts/ios-release.sh), " +
            "which also refuses an A/B unit whose Fajr, end of eating and Maghrib were not each checked on every day of it (spec §5)."
    }
}

fun main(args: Array<String>) {
    val options = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        options[key] = args.getOrNull(i + 1) ?: fail("$key needs a value")
        i += 2
    }
    val repo = File(options["--repo"] ?: fail("--repo is required"))
    val ramadan = options["--ramadan"]?.let { value ->
        val year = value.toIntOrNull() ?: fail("--ramadan needs a Hijri year like 1448, not '$value'")
        runCatching { Ramadan.of(year) }.getOrElse { fail(it.message ?: "--ramadan $value") }
    }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    ramadan?.let { CheckStamps.pastRamadan(it, today) }?.let(::fail)
    val checkout = repo.resolve("tools/timetables/official")
    // checkStamps never reads a table: the roots it resolves rows with never hold an archive, so an
    // archive restored on this machine cannot change what it finds (see the class comment above).
    val roots = OfficialRoots(repo.resolve(".checkStamps-never-reads-this-archive"), checkout)
    val manifest = GateManifest.load(checkout.resolve("gate"))
    val stampsDir = checkout.resolve("stamps")
    val proofStampsFile = repo.resolve(
        "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ProofStamps.kt",
    )

    val result = CheckStamps.check(repo, stampsDir, manifest, roots, proofStampsFile, ramadan = ramadan)
    if (!result.ok) {
        System.err.print(result.report())
        exitProcess(1)
    }
    println(
        "checkStamps: ${manifest.rows.map { it.entry }.distinct().size} entries — every stamp fresh and " +
            "green, ProofStamps.kt matches the committed stamps" +
            (if (ramadan == null) "." else ", and every A/B unit's Fajr, end of eating and Maghrib were checked on every day of $ramadan."),
    )
    if (ramadan == null) CheckStamps.ramadanHint(today)?.let(::println)
}

private fun fail(problem: String): Nothing {
    System.err.println("checkStamps: $problem")
    exitProcess(2)
}
