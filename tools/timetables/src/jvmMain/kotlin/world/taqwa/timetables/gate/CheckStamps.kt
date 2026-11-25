package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import world.taqwa.app.prayer.engine.registry.CheckedDays
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.data.ProofStamp
import world.taqwa.app.prayer.engine.registry.data.ProofStamps
import world.taqwa.timetables.Json
import world.taqwa.timetables.monitor.MonthByMonth
import world.taqwa.timetables.units.ReachLimitsFile
import world.taqwa.timetables.units.ReachRows
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * Ruling R185 (the owner's O4): whether an A/B entry whose units leave [gaps] in a Ramadan is
 * covered all the same — its method is proven on the months held and on a Ramadan before, the
 * monitor fetches the authority's own publication and has every fetched day in the gate, and prove
 * demotes the entry on the first day that fails (`MonthByMonth`).
 */
fun interface RamadanCover {
    fun verdict(stamp: StampSummary, ramadan: Ramadan, gaps: List<RamadanGap>): Cover
}

sealed interface Cover {
    /** Covered under O4; [words] say on what grounds. */
    data class Covered(val words: String) : Cover

    /** Still named by the guard: every condition the entry fails, in words. */
    data class Named(val reasons: List<String>) : Cover
}

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
 *
 * Ruling R185 (the owner's O4, 12 Nov 2026) adds two things. An entry short of the Ramadan is
 * **covered under O4**, and not a problem, where a [RamadanCover] says so (the monitor's
 * `MonthByMonth`: its method proven on the months held and on a Ramadan before, the authority's
 * own publication fetched on the monitor's cadence with every fetched day in the gate, demoted
 * automatically on the first failing day); the run says so entry
 * by entry, and names every other with the conditions it fails. That rule reads the monitor's index
 * and the tables it names, so the Ramadan build passes `--official <root>` (`-Pofficial`, or
 * `TAQWA_OFFICIAL`); without it no entry is covered. And a sixth way a release must not go out,
 * Ramadan or not: **demoted** — the stamp carries the monitor's standing demotion ([Demotion]) while
 * the registry still calls the entry a checked timetable. `--allow-demoted true` lets the monitor's
 * own run through, so that it can publish the record (`scripts/prove.sh`); a release never passes it.
 *
 * And a seventh, for a release alone (`--release true`, which `scripts/release.sh` and
 * `scripts/ios-release.sh` pass): **a reach proof is owed** — a unit left out of its entry's reach
 * rows, or an entry whose reach rows are pending ([ReachRows.outstanding]). There About's figure is
 * the unit's own point's, and a place across the unit's reach was measured later than it (never
 * early), so a release would say a figure known to be too low ([reachHold]). Every other run says
 * what is owed and passes: the gate and the monitor go on while the limit is being settled. Nothing
 * is owed since 14 Nov 2026 (Bahla, ruling R254; Türkiye's il centres, ruling R255): the hold stays
 * for the next unit left out or entry pending, and passes while there is none.
 *
 * Ruling R246 (13 Nov 2026) makes the Ramadan check say everything it knows, with or without the
 * flag, so that a green line never hides it:
 *
 * - **held out, not only checked**: an A/B unit's guarded days must each be held by a held-out
 *   cell of every event (the stamp's `heldOutByEvent`, ruling R214: a row marked test on a table no
 *   fitted row of the entry reads that event from). A unit whose days are all in the gate, some of
 *   them only in tables its method was fitted to, is named apart ("in fitted rows only"): the engine
 *   was not early on those very days, and that says nothing of a day the fit did not see. `--ramadan`
 *   refuses it like the other; no O4 cover applies (its days are published and in the gate already).
 * - **the imsak too**, at a unit whose method shows one and whose tables print one
 *   ([UnitSummary.ramadanEvents]); a unit that shows an imsak no table of it prints is named in a
 *   notice ([Result.imsakUnheld]) and refuses nothing, since no row exists to add a day to.
 * - **Published method** entries (class D (authority) and no A or B) are read the same way and named
 *   in a notice ([Result.publishedMethod]); they refuse nothing: the label claims the authority's
 *   method, not its table, and most of these authorities print no table for a year ahead.
 * - **without `--ramadan`** the run reads the coming Ramadan ([Ramadan.nextFrom]) all the same and
 *   prints, never failing, one line naming the A/B entries not yet proven for it
 *   ([Result.notProven]), on every run and not only inside [Ramadan.isNear]'s window.
 */
object CheckStamps {

    /** One entry's problems; an entry with none is fresh and green, and is left out of [Result.entries]. */
    data class EntryProblem(val entryId: String, val messages: List<String>)

    class Result(
        val entries: List<EntryProblem>,
        val proofStampsStale: Boolean,
        val ramadan: Ramadan? = null,
        /** Ruling R185: the entries short of [ramadan] that are covered under O4, each with the rule's words. */
        val covered: List<Pair<String, String>> = emptyList(),
        /** The Ramadan the lists below were read for: [ramadan], or the coming one on a run without it (ruling R246). */
        val readFor: Ramadan? = ramadan,
        /**
         * Ruling R246, a run without `--ramadan`: the A/B entries not yet proven for [readFor], each
         * with what it lacks. With `--ramadan` these are [entries]' own problems, and this is empty.
         */
        val notProven: List<Pair<String, String>> = emptyList(),
        /** Ruling R246: the Published-method entries (class D (authority)) short of [readFor]; named, never refused. */
        val publishedMethod: List<Pair<String, String>> = emptyList(),
        /**
         * Ruling R273 (the second trust audit's G30): the cautious entries (class C) whose members' tables leave days of
         * [readFor] unchecked or checked in fitted rows only; named, never refused while [CAUTIOUS_REFUSES] is off.
         */
        val cautious: List<Pair<String, String>> = emptyList(),
        /** Ruling R246: the units that show an imsak no table of theirs prints, as `entry` or `entry/unit`; named, never refused. */
        val imsakUnheld: List<String> = emptyList(),
        /**
         * Ruling R246, only where [FITTED_ONLY_REFUSES] is off: the A/B entries `--ramadan` let through with
         * guarded days checked in fitted rows only, each with the units and events; named, not refused.
         */
        val fittedOnly: List<Pair<String, String>> = emptyList(),
    ) {
        val ok: Boolean get() = entries.isEmpty() && !proofStampsStale

        /**
         * What the run says beside its verdict, green or red (ruling R246): one line a list that is
         * not empty, the entries named; empty when nothing is short. Never a reason to fail.
         */
        fun notices(): List<String> {
            val month = readFor ?: return emptyList()
            return listOfNotNull(
                notProven.takeIf { it.isNotEmpty() }?.let { list ->
                    "checkStamps: NOT YET PROVEN for $month: ${counted(list.size, "checked timetable")} (class A or B) — " +
                        "${list.joinToString("; ") { "${it.first}: ${it.second}" }}. This run does not fail for it; the Ramadan build runs " +
                        "with -Pramadan=${month.hijriYear} (TAQWA_RAMADAN=${month.hijriYear}), which refuses each of them unless it is covered under O4 (ruling R185)."
                },
                publishedMethod.takeIf { it.isNotEmpty() }?.let { list ->
                    "checkStamps: notice: ${counted(list.size, "Published-method entry", "Published-method entries")} (class D (authority)) " +
                        "not checked against the authority's own table on every day of $month, and no build is refused for it (ruling R246) — " +
                        "${list.joinToString("; ") { "${it.first}: ${it.second}" }}."
                },
                cautious.takeIf { it.isNotEmpty() }?.let { list ->
                    "checkStamps: notice: ${counted(list.size, "Cautious-times entry", "Cautious-times entries")} (class C) whose members' " +
                        "printed tables are not checked, held out, on every day of $month, so \"never before any member's table\" rests on " +
                        "the months held and not on the month itself; no build is refused for it (ruling R273, the owner's to decide) — " +
                        "${list.joinToString("; ") { "${it.first}: ${it.second}" }}."
                },
                fittedOnly.takeIf { it.isNotEmpty() }?.let { list ->
                    "checkStamps: notice: ${counted(list.size, "checked timetable")} (class A or B) hold days of $month in fitted rows only " +
                        "(checked and not early, not held out; ruling R246) — ${list.joinToString("; ") { "${it.first}: ${it.second}" }}."
                },
                imsakUnheld.takeIf { it.isNotEmpty() }?.let { list ->
                    "checkStamps: notice: ${counted(list.size, "unit")} show an imsak that no table held for them prints, so it is checked on " +
                        "no day, of $month or any other (ruling R246) — ${list.joinToString(", ")}."
                },
            )
        }

        private fun counted(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

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
        /** Ruling R185: who says an entry short of [ramadan] is covered under O4; null and none is. */
        cover: RamadanCover? = null,
        /** The monitor's own run: a standing demotion is its news to publish, not a release to stop. */
        allowDemoted: Boolean = false,
        /**
         * Ruling R246, a run without [ramadan]: the coming Ramadan, read the same way and reported in
         * [Result.notProven] without a problem raised. Null and nothing is read (and with [ramadan], unused).
         */
        coming: Ramadan? = null,
    ): Result {
        val coreHash = Stamps.coreHash(repo)
        val fingerprints = fingerprintsByEntry(manifest, roots, lookup)
        val stampFiles = stampsDir.listFiles { f -> f.extension == "json" }?.associateBy { it.nameWithoutExtension }.orEmpty()
        val covered = mutableListOf<Pair<String, String>>()
        val readFor = ramadan ?: coming
        val notProven = mutableListOf<Pair<String, String>>()
        val publishedMethod = mutableListOf<Pair<String, String>>()
        val imsakUnheld = mutableListOf<String>()
        val fittedOnly = mutableListOf<Pair<String, String>>()
        val cautious = mutableListOf<Pair<String, String>>()

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
                val summary = StampSummary.of(stamp, entryId)
                if (summary.demotedStanding && !allowDemoted) {
                    messages += "the monitor demoted ${summary.demoted.joinToString("; ") { it.describe(entryId) }} and the registry still calls " +
                        "it a checked timetable (class ${summary.classes.joinToString("/")}) — prove the failing days (refit the method, or record " +
                        "the authority's own misprint and add its corrected table; their rows in the gate and green) and remove the " +
                        "$DEMOTED_DIRECTIVE line, or lower the class in the registry, and re-run the gate (ruling R185)"
                }
                if (readFor != null && summary.isCautious) {
                    // Ruling R273: a cautious entry's stamp read the same way (its members' days, the stamp's own runs).
                    val gaps = summary.ramadanGaps(readFor)
                    val fitted = summary.fittedOnly(readFor)
                    val short = listOfNotNull(
                        gaps.takeIf { it.isNotEmpty() }?.let { summary.describe(readFor, it) },
                        fitted.takeIf { it.isNotEmpty() }?.let { "checked in fitted rows only at ${summary.describeFitted(it)}" },
                    ).joinToString("; and ")
                    if (short.isNotEmpty()) {
                        if (ramadan != null && CAUTIOUS_REFUSES) {
                            messages += "$ramadan is not proven for a cautious entry: $short — add the members' tables for those dates (held " +
                                "out, split test) and re-run the gate, or change the entry in the registry (ruling R273)"
                        } else {
                            cautious += entryId to short
                        }
                    }
                }
                if (readFor != null && (summary.isAOrB || summary.isPublishedMethod)) {
                    val gaps = summary.ramadanGaps(readFor)
                    val fitted = summary.fittedOnly(readFor)
                    if (summary.isAOrB) {
                        imsakUnheld += summary.imsakUnheld.map { if (summary.units.isEmpty()) entryId else "$entryId/${it.id}" }
                    }
                    val short = listOfNotNull(
                        gaps.takeIf { it.isNotEmpty() }?.let { summary.describe(readFor, it) },
                        fitted.takeIf { it.isNotEmpty() }?.let { "checked in fitted rows only at ${summary.describeFitted(it)}" },
                    ).joinToString("; and ")
                    when {
                        short.isEmpty() -> Unit
                        // Ruling R246: a Published-method entry is named, never refused.
                        !summary.isAOrB -> publishedMethod += entryId to short
                        ramadan == null -> notProven += entryId to short
                        else -> {
                            if (gaps.isNotEmpty()) {
                                val fix = "fetch the authority's table for those dates (with its Fajr, end of eating and Maghrib, and its imsak " +
                                    "where the unit shows one: rulings R148 and R246), add gate rows held out of the fit (split test, on a table no " +
                                    "fitted row reads) and re-run the gate, or demote the entry's class in the registry and re-run the gate (spec §5)"
                                when (val verdict = cover?.verdict(summary, ramadan, gaps)) {
                                    is Cover.Covered -> covered += entryId to verdict.words
                                    is Cover.Named -> messages += "$ramadan is not proven: ${summary.describe(ramadan, gaps)}; not covered under O4 " +
                                        "(ruling R185): ${verdict.reasons.joinToString("; ")} — $fix"
                                    null -> messages += "$ramadan is not proven: ${summary.describe(ramadan, gaps)} — $fix"
                                }
                            }
                            if (fitted.isNotEmpty() && FITTED_ONLY_REFUSES) {
                                messages += "$ramadan is checked and not held out: ${summary.describeFitted(fitted)} — every guarded day is in the " +
                                    "gate and was not early, some of them only in tables the method was fitted to (ruling R246); hold those tables " +
                                    "out (refit the method without them and mark their rows split test, the gate green), or add a held-out table " +
                                    "for those dates, or demote the entry's class in the registry and re-run the gate"
                            } else if (fitted.isNotEmpty()) {
                                fittedOnly += entryId to summary.describeFitted(fitted)
                            }
                        }
                    }
                }
            }
            if (messages.isEmpty()) null else EntryProblem(entryId, messages)
        }

        val proofStale = !proofStampsFile.isFile || ProofStampsGenerator.generate(stampsDir) != proofStampsFile.readText()
        return Result(
            entries, proofStale, ramadan, covered, readFor, notProven, publishedMethod,
            cautious = cautious, imsakUnheld = imsakUnheld, fittedOnly = fittedOnly,
        )
    }

    private const val DEMOTED_DIRECTIVE = GateManifest.DEMOTED

    /**
     * Ruling R246's one open question, held in one line. True (the strict reading, "the guard counts only
     * held-out rows"): `--ramadan` refuses an A/B entry whose guarded days are all in the gate but some of them only
     * in tables its method was fitted to. False: the same entry is named in a notice ([Result.fittedOnly]) and let
     * through, on the argument that the engine was compared with the authority's own figures on those very days and
     * was not early on any. Under true, an entry whose only table of the coming Ramadan is a fit row (Kemenag's
     * 2026 and 2027 books, MARA, JAKIM's imsak) passes only once its method is refitted with that table held out.
     * The owner rules; until then the stricter one stands.
     */
    const val FITTED_ONLY_REFUSES = true

    /**
     * Ruling R273's open question, held in one line (the second trust audit's G30, 14 Nov 2026). False: a cautious entry
     * (class C) whose members' tables do not hold every guarded day of the Ramadan is named in a notice
     * ([Result.cautious]) and let through, as R246 treats the Published-method entries, since most members print no table
     * a year ahead and the times are each member's method, never before any member's on the days held. True: `--ramadan`
     * refuses it like an A/B entry. The owner rules; until then the notice stands.
     */
    const val CAUTIOUS_REFUSES = false

    /**
     * Why `--ramadan` refuses [ramadan] on [today], or null: a month whose guarded days ([Ramadan.guardLast]) ended
     * before today proves nothing about the build being cut, and is a typo for the next one (1447 for 1448 holds the
     * stamps to a Ramadan already past, and could pass).
     */
    /**
     * The release cut's notice of labels about to change (ruling R278, the review of ruling R275): each A/B entry with
     * units whose checked run holding [today] ends within [days] days, or that no run holds today. From the day after
     * that run the app shows those places as the published method, and only a build carrying a later stamp shows them
     * checked again, while the site's page comes back as soon as the monitor proves the month. A notice, never a
     * failure: read the next tables before the cut where they are out. Reads the generated stamps, as the app does.
     */
    fun labelEnds(today: LocalDate, days: Int = 45, stamps: Collection<ProofStamp> = ProofStamps.byEntry.values): List<String> {
        val horizon = today.plus(days, DateTimeUnit.DAY)
        val lines = mutableListOf<String>()
        for (stamp in stamps.sortedBy { it.entryId }) {
            val cls = Registry.byId(stamp.entryId)?.entryClass ?: continue
            if (cls != EntryClass.A && cls != EntryClass.B) continue
            val keys: List<String?> = stamp.worstLateByUnit.keys.sorted().ifEmpty { listOf(null) }
            val ends = keys.map { unit ->
                val runs = unit?.let { stamp.checkedByUnit[it] } ?: stamp.checked
                CheckedDays.blocks(runs).firstOrNull { today in it }?.endInclusive
            }
            val already = ends.count { it == null }
            val soon = ends.filterNotNull().filter { it <= horizon }
            if (already == 0 && soon.isEmpty()) continue
            val of = if (stamp.worstLateByUnit.isEmpty()) "" else " of ${keys.size} units"
            val parts = buildList {
                if (already > 0) add("$already$of not checked today")
                soon.groupingBy { it }.eachCount().toSortedMap().forEach { (end, n) -> add("$n$of checked through $end") }
            }
            lines += "checkStamps: label (ruling R275): ${stamp.entryId}: ${parts.joinToString("; ")} — the published method " +
                "the day after, until a build carries a later stamp"
        }
        return lines
    }

    fun pastRamadan(ramadan: Ramadan, today: LocalDate): String? {
        if (ramadan.guardLast >= today) return null
        val next = Ramadan.nextFrom(today)
        return "--ramadan ${ramadan.hijriYear}: $ramadan ended before today ($today); the next is ${next.hijriYear} " +
            "(${next.first}..${next.last}), so a Ramadan build runs with -Pramadan=${next.hijriYear} (TAQWA_RAMADAN=${next.hijriYear})"
    }

    /**
     * The reach proofs owed ([ReachRows.outstanding]) as this run takes them: problems for a release
     * (first), which they stop, and notices for any other run (second), which says them and passes.
     */
    fun reachHold(release: Boolean, outstanding: List<String> = ReachRows.outstanding()): Pair<List<String>, List<String>> {
        val lines = outstanding.map {
            "$it — About's figure there is not settled across the unit's reach (measured later than said, or past what a ruling accepts; never early)"
        }
        return if (release) lines to emptyList() else emptyList<String>() to lines
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
            "which also refuses an A/B unit whose Fajr, end of eating and Maghrib were not each held out and checked on every day of it (spec §5, ruling R246)."
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
    (options.keys - setOf("--repo", "--ramadan", "--official", "--allow-demoted", "--release")).firstOrNull()?.let { fail("unknown option $it") }
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

    // Ruling R185: the month-by-month rule does read the monitor's index and the tables it names, from --official.
    val cover = ramadan?.let { MonthByMonth.load(repo, options["--official"]?.let(::File), manifest, today) }
    val allowDemoted = when (val value = options["--allow-demoted"]) {
        null, "false" -> false
        "true" -> true
        else -> fail("--allow-demoted true|false, not '$value'")
    }
    val release = when (val value = options["--release"]) {
        null, "false" -> false
        "true" -> true
        else -> fail("--release true|false, not '$value'")
    }
    val result = CheckStamps.check(
        repo, stampsDir, manifest, roots, proofStampsFile, ramadan = ramadan, cover = cover, allowDemoted = allowDemoted,
        coming = Ramadan.nextFrom(today),
    )
    for ((entry, words) in result.covered) println("checkStamps: $entry: covered under O4 (ruling R185) — $words")
    // Ruling R246: said on every run, green or red, with the flag or without.
    result.notices().forEach(::println)
    val gated = manifest.rows.map { it.entry }.toSet()
    val specs = ReachRows.SPECS + ReachRows.PENDING
    val (owed, notices) = CheckStamps.reachHold(
        release,
        ReachRows.outstanding(
            unspecified = ReachRows.unspecified(specs, gated),
            unswept = ReachRows.unswept(gated),
            beyondArea = ReachRows.beyondArea(gated),
        ),
    )
    for (notice in notices) println("checkStamps: a reach proof is owed, and a release (-Prelease=true) is refused until it is settled: $notice")
    if (!result.ok) System.err.print(result.report())
    // Ruling R270: About's reach figures are generated from the stamps; a file the stamps no longer give says a stale figure.
    val reachLimitsFile = repo.resolve(ReachLimitsFile.PATH)
    val reachLimitsStale = !reachLimitsFile.isFile || ReachLimitsFile.render(stampsDir) != reachLimitsFile.readText()
    if (reachLimitsStale) {
        System.err.println(
            "checkStamps: ${ReachLimitsFile.PATH} is not what the committed stamps give — run ./gradlew -p tools/timetables " +
                "generateReachLimits, report every figure it raises, then the gate and generateProofStamps",
        )
    }
    if (owed.isNotEmpty()) {
        System.err.println(
            "checkStamps: a release is refused while a reach proof is owed (settle the unit's limit or cut its reach, then " +
                "generateReachRows and the gate; GenerateReachRows.kt's ReachRows says what was measured):",
        )
        for (line in owed) System.err.println("  $line")
    }
    if (!result.ok || owed.isNotEmpty() || reachLimitsStale) exitProcess(1)
    val o4 = when (result.covered.size) {
        0 -> ""
        1 -> ", or (1 entry: ${result.covered.single().first}) is covered under O4 (ruling R185)"
        else -> ", or (${result.covered.size} entries: ${result.covered.joinToString(", ") { it.first }}) are covered under O4 (ruling R185)"
    }
    println(
        "checkStamps: ${manifest.rows.map { it.entry }.distinct().size} entries — every stamp fresh and " +
            "green, ProofStamps.kt and ReachLimits.kt match the committed stamps" +
            (if (ramadan == null) {
                if (result.notProven.isEmpty()) "." else ". ${result.notProven.size} of them not yet proven for Ramadan ${result.readFor?.hijriYear}: see above."
            } else {
                ", and every A/B unit's Fajr, end of eating and Maghrib (and its imsak where a table prints one) were held out and checked on every day of $ramadan$o4."
            }),
    )
    if (ramadan == null) CheckStamps.ramadanHint(today)?.let(::println)
    CheckStamps.labelEnds(today).forEach(::println)
}

private fun fail(problem: String): Nothing {
    System.err.println("checkStamps: $problem")
    exitProcess(2)
}
