package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.timetables.gate.EntryStats
import world.taqwa.timetables.gate.Excuse
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateError
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.GateResult
import world.taqwa.timetables.gate.GateRow
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.OfficialTable
import world.taqwa.timetables.gate.Stamps
import world.taqwa.timetables.gate.dateRanges
import world.taqwa.timetables.gate.standing
import java.io.File

/**
 * `prove` (docs/MONITOR.md): the monitor's new captures turned into proof with no human. For each
 * capture a recipe ([Recipes]) names whose days the gate rows do not all hold yet, the capture is
 * copied as it is to `archive/tables/pinned/<source>/<date>/` (a member row's days cut as its
 * recipe says, in a file of its own beside it), the recipe's rows are appended to their gate files,
 * and the WHOLE gate runs. A new row that breaks a promise (an early start, a late end, lateness
 * over its limit, a day out of order) or that the gate refuses is removed again and reported,
 * never loosened, and the gate runs again until it is green. Then the gate files and the stamps
 * are written. A gate that is red before anything is added stops it (nothing is written). Running
 * it again adds nothing: every capture's days are then held.
 *
 * Ruling R185, the automatic demotion: when the row left out is a checked timetable's own (class A
 * or B, no member's), reads a capture of the authority's own site, and what it broke is the promise
 * itself (an early start, a late end, a day out of order or lateness over its limit; not an entry
 * red before prove), prove writes the entry's `@demoted` line ([Demotion]) into that gate file by
 * itself, on the first run that finds it, and the stamps with it: the website then holds the
 * entry's pages, a Ramadan no longer counts it as covered, and no release is cut while the registry
 * still calls it a checked timetable (`CheckStamps`). One line an entry: a demotion that stands is
 * not written again. The capture is whatever the monitor holds and the gate does not — most often
 * days the authority newly published, but as well a table re-fetched, or one fetched long ago and
 * only now given a recipe — so the line names the capture and never calls its days new.
 *
 * What never demotes: a republisher's mirror ([MonitorTable.mirror]) — a mirror's mistake is not
 * the authority's, so its row is left out saying so and a person compares the authority's own
 * table; and a row the reader or the gate refuses (an empty zone, a bad point), which is a table
 * we could not take, not a promise broken. Both are marked on the row left out ([LeftOut.checked],
 * [LeftOut.mirror]) and are tier 1 items of the prove issue (`proof.py`): a checked timetable's own
 * row that did not land is a day not checked.
 *
 * Every row takes what it reads from its family, never from the recipe: the latest row of the same
 * gate file, entry, member and point that reads an earlier capture of the same source and key;
 * else such a row reading any table that prints the same minutes as the capture on every day both
 * hold (the same layout, proven by the files themselves); else such a row reading another capture
 * of the same source (a new year's key: the fetcher writes every key alike, and the index agrees
 * when it still lists the old key); else, for a cautious member's row, the member's latest row of
 * the same width (the gate file's convention for that member); else the fetcher's own metadata in
 * the monitor's index. A row the reader refuses, or a capture no recipe target picks up, is
 * reported as left out; whatever stops prove midway removes the pinned copies it wrote.
 */
class Prove(
    private val repo: File,
    private val official: File,
    private val date: LocalDate,
    private val recipes: List<Recipe>,
    private val index: List<MonitorTable>,
    private val only: Set<String> = emptySet(),
    private val gateDir: File = repo.resolve("tools/timetables/official/gate"),
    private val stampsDir: File = repo.resolve("tools/timetables/official/stamps"),
    private val roots: OfficialRoots = OfficialRoots(official, repo.resolve("tools/timetables/official")),
    private val gate: Gate = Gate(roots),
) {

    /** A row prove means to add: its gate file, its cells by header name, and the file it reads. */
    data class Planned(
        val gate: String,
        val cells: Map<String, String>,
        val content: String,
        val capture: String,
        val recipe: String,
        val days: List<LocalDate>,
        val family: String,
        /** The capture is a republisher's mirror of the authority's table ([MonitorTable.mirror]): it never demotes. */
        val mirror: Boolean = false,
    ) {
        val path: String get() = cells.getValue("path")
        val entry: String get() = cells.getValue("entry")
        val member: String get() = cells["member"].orEmpty()
        val point: String get() = listOf(cells["lat"].orEmpty(), cells["lon"].orEmpty()).joinToString(",").let { if (it == ",") "unit" else it }
        val label: String get() = "$gate $entry" + (if (member.isNotEmpty()) " member $member" else "") + " at $point from $capture"
    }

    /**
     * A row prove did not add. [checked]: it was the own row (no member's) of an entry the gate holds as a checked
     * timetable (class A or B), so days of a checked timetable went unchecked whatever the reason. [mirror]: the capture
     * was a republisher's mirror, which may be left out but never demotes.
     */
    data class LeftOut(val label: String, val reason: String, val checked: Boolean = false, val mirror: Boolean = false)

    /** An entry prove demoted (ruling R185): the gate file its line went into, and why (dates, counts and minutes only). */
    data class Demoted(val gate: String, val entry: String, val unit: String?, val since: LocalDate, val reason: String) {
        val target: String get() = if (unit == null) entry else "$entry/$unit"
    }

    class Outcome(
        val failure: String?,
        val added: List<Planned>,
        val leftOut: List<LeftOut>,
        val pinned: List<String>,
        val rowsBefore: Int,
        val rowsAfter: Int,
        val placeDaysBefore: Int,
        val placeDaysAfter: Int,
        val result: GateResult?,
        val demoted: List<Demoted> = emptyList(),
    ) {
        /** Rows were added, or an entry was demoted (ruling R185): either way the gate files and stamps moved. */
        val changed: Boolean get() = failure == null && (added.isNotEmpty() || demoted.isNotEmpty())

        fun json(): Map<String, Any?> = linkedMapOf(
            "failure" to failure,
            "changed" to changed,
            "rowsBefore" to rowsBefore,
            "rowsAfter" to rowsAfter,
            "placeDaysBefore" to placeDaysBefore,
            "placeDaysAfter" to placeDaysAfter,
            "added" to added.map {
                linkedMapOf(
                    "gate" to it.gate, "entry" to it.entry, "member" to it.member.ifEmpty { null }, "point" to it.point, "path" to it.path,
                    "capture" to it.capture, "days" to dateRanges(it.days), "dayCount" to it.days.size, "recipe" to it.recipe, "family" to it.family,
                )
            },
            "leftOut" to leftOut.map { linkedMapOf("row" to it.label, "reason" to it.reason, "checked" to it.checked, "mirror" to it.mirror) },
            "demoted" to demoted.map { linkedMapOf("gate" to it.gate, "entry" to it.target, "since" to it.since.toString(), "reason" to it.reason) },
            "pinned" to pinned,
        )
    }

    private val tables = HashMap<String, Set<LocalDate>>()

    /** The held tables' cells read while planning (ruling R283), let go when the plan is made. */
    private val printed = HashMap<String, Map<LocalDate, List<Int?>>>()
    private var texts: Map<String, String> = emptyMap()

    fun run(write: Boolean = true): Outcome {
        val created = mutableListOf<File>()
        try {
            return run(write, created)
        } catch (e: Throwable) {
            // Whatever stops prove midway, the pinned copies it wrote are removed again (never left as orphans).
            discard(created)
            throw e
        }
    }

    /** The rows prove would add and those it leaves out while planning, before any file is written or the gate runs (tests). */
    fun plan(): Pair<List<Planned>, List<LeftOut>> {
        val texts = gateDir.listFiles { f -> f.isFile && f.name.endsWith(".tsv") }!!.sortedBy { it.name }.associate { it.name to it.readText() }
        this.texts = texts
        val leftOut = mutableListOf<LeftOut>()
        return plan(texts, manifest(texts), leftOut) to leftOut
    }

    private fun run(write: Boolean, created: MutableList<File>): Outcome {
        val texts = gateDir.listFiles { f -> f.isFile && f.name.endsWith(".tsv") }!!.sortedBy { it.name }.associate { it.name to it.readText() }
        this.texts = texts
        val baseManifest = manifest(texts)
        // What prove needs of the base result is kept, and the result itself let go before the gate runs again (ruling
        // R280, the second audit's G17): a whole result holds every entry's place-days as sets, over a gigabyte on the
        // line of 14 Nov 2026, and two at once ran out of prove's heap. It is made and read inside [summarise] alone, so
        // no local of this function keeps it; the one path that writes stamps from the base (a demotion with no row
        // kept) evaluates it again.
        val summary = try {
            summarise(gate.evaluate(baseManifest))
        } catch (e: GateError) {
            return fail("the gate's inputs are refused before prove adds anything: ${e.problems.take(5).joinToString("; ")}", baseManifest)
        }
        // An entry red before anything is added (a hand-read member row committed ahead of the capture it leans on) can
        // only be made green by its new rows together; if they do not, prove fails and writes nothing.
        val baseRed = summary.red
        val checkedTimetables = summary.checked
        val basePlaceDays = summary.placeDays
        val leftOut = mutableListOf<LeftOut>()
        var planned = plan(texts, baseManifest, leftOut)
        // Mirror captures whose row broke its checked timetable's promise: left out, never a demotion (ruling R185).
        val heldBack = HashSet<Planned>()
        fun left(p: Planned, why: String): LeftOut {
            val own = p.member.isEmpty() && p.entry.substringBefore('/') in checkedTimetables
            val note = if (p in heldBack) {
                " — the capture is a republisher's mirror, not the authority's own table: ${p.entry} is not demoted on it (ruling R185); " +
                    "compare the authority's own table for these days by hand"
            } else {
                ""
            }
            return LeftOut(p.label, why + note, checked = own, mirror = p.mirror)
        }
        // A row the gate file's reader refuses on its own (an empty zone, a bad point) is left out before anything is written.
        planned = planned.filter { p ->
            val problem = readable(p)
            if (problem != null) leftOut += left(p, "refused by the gate's reader: $problem")
            problem == null
        }
        // Pinned copies: a path that exists with other content is never overwritten.
        planned = planned.filter { p ->
            val file = official.resolve(p.path)
            when {
                !file.exists() -> true
                file.readText() == p.content -> true
                else -> false.also { leftOut += left(p, "${p.path} already exists with other content; a pinned copy is never rewritten") }
            }
        }
        // The gate reads the pinned copies, so they are written first (a dry run removes them again at the end).
        for (p in planned.distinctBy { it.path }) {
            val file = official.resolve(p.path)
            if (!file.exists()) {
                file.parentFile.mkdirs()
                file.writeText(p.content)
                created += file
            }
        }
        var result: GateResult? = null
        // Ruling R185: the checked timetables a capture of their authority's own site broke a promise of, by entry and
        // unit (the first row found for each). The unit is the one the failing cells fell in; the entry as a whole where they fell in none.
        val demoted = LinkedHashMap<String, Demoted>()
        fun demote(p: Planned, s: EntryStats) {
            val id = s.entry.id
            if (p.member.isNotEmpty()) return
            if (s.classes.none { it == EntryClass.A || it == EntryClass.B }) return
            val broke = broke(s) ?: return
            if (p.mirror) {
                heldBack += p
                return
            }
            val units: List<String?> = s.units.filterValues { u -> u.events.values.any { it.early + it.lateEnd + it.over > 0 } }.keys.toList()
            for (unit in units.ifEmpty { listOf(null) }) {
                val target = if (unit == null) id else "$id/$unit"
                if (target in demoted || id in demoted || baseManifest.demotions.any { it.covers(id, unit) }) continue
                demoted[target] = Demoted(p.gate, id, unit, date, "the monitor's capture ${p.capture}, at ${p.point}: $broke")
            }
        }
        // One whole result at a time (ruling R280): the last round's is let go before the gate runs again.
        var evaluated: GateResult? = null
        var failing: List<EntryStats> = emptyList()
        while (planned.isNotEmpty()) {
            val (withRows, lines) = append(texts, planned)
            val manifest = manifest(withRows)
            val rowOf = planned.associateWith { p -> manifest.rows.first { it.source == p.gate && it.line == lines.getValue(p) } }
            evaluated = null
            failing = emptyList()
            evaluated = try {
                gate.evaluate(manifest)
            } catch (e: GateError) {
                val dropped = refused(e, planned, rowOf, manifest.excuses)
                for ((p, why) in dropped) leftOut += left(p, why)
                planned = planned - dropped.keys
                continue
            }
            failing = evaluated.entries.values.filter { evaluated.violations(it).isNotEmpty() }
            if (failing.isEmpty()) {
                result = evaluated
                break
            }
            val drop = LinkedHashMap<Planned, String>()
            for (s in failing) {
                val own = planned.filter { it.entry.substringBefore('/') == s.entry.id }
                if (s.entry.id in baseRed) {
                    if (own.isEmpty()) {
                        discard(created)
                        return fail("the gate is red before prove at ${s.entry.id} and no new row repairs it: ${baseRed.getValue(s.entry.id)}", baseManifest, basePlaceDays)
                    }
                    for (p in own) drop.putIfAbsent(p, "${s.entry.id} was red before prove and its new rows together do not make it green: ${evaluated.violations(s).first().lines().take(3).joinToString(" ")}")
                    continue
                }
                if (own.isEmpty()) {
                    // Red where nothing was added: another entry's new rows moved it (a member's entry); drop every new row then.
                    for (p in planned) drop.putIfAbsent(p, "the gate went red at ${s.entry.id}, where nothing was added: ${evaluated.violations(s).first().lineSequence().first()}")
                    continue
                }
                val baseRows = manifest.rows.filter { r -> r.entry == s.entry.id && planned.none { rowOf[it] == r } }
                val excuses = manifest.excuses.filter { it.entry == s.entry.id }
                var any = false
                for (p in own) {
                    val (why, stats) = alone(baseRows + rowOf.getValue(p), excuses, manifest.sources, s.entry.id)
                    if (why != null) {
                        drop[p] = why
                        any = true
                        if (stats != null) demote(p, stats)
                    }
                }
                if (!any) {
                    val why = evaluated.violations(s).first().lines().take(3).joinToString(" ")
                    for (p in own) drop.putIfAbsent(p, "breaks a promise only with the other new rows of ${s.entry.id}: $why")
                    // Together: where a mirror's row is among them the break cannot be laid on the authority's own table.
                    val ownRows = own.filter { it.member.isEmpty() }
                    if (ownRows.any { it.mirror }) ownRows.filter { it.mirror }.forEach { demote(it, s) } else ownRows.firstOrNull()?.let { demote(it, s) }
                }
            }
            for ((p, why) in drop) leftOut += left(p, why)
            planned = planned - drop.keys
        }
        if (planned.isEmpty()) {
            // The last round's whole result is let go here, before the base gate may run again below (ruling R280).
            evaluated = null
            failing = emptyList()
            // Nothing kept: the pinned copies this run wrote are removed again, the gate files left alone.
            discard(created)
            if (baseRed.isNotEmpty()) {
                return fail("the gate is red before prove and nothing added repairs it: ${baseRed.entries.take(5).joinToString("; ") { it.value }}", baseManifest, basePlaceDays)
            }
            // ... but for a demotion (ruling R185): its line and the stamps are written though no row was kept, from the
            // base gate evaluated again (its first result was let go, ruling R280).
            var again: GateResult? = null
            if (demoted.isNotEmpty() && write) {
                val withLines = demotions(texts, demoted.values)
                val rerun = gate.evaluate(baseManifest)
                record(rerun, withLines)
                for ((name, text) in withLines) if (text != texts[name]) gateDir.resolve(name).writeText(text)
                Stamps.write(rerun, stampsDir, repo)
                again = rerun
            }
            return Outcome(
                null, emptyList(), leftOut, emptyList(), baseManifest.rows.size, baseManifest.rows.size, basePlaceDays, basePlaceDays, again,
                demoted.values.toList(),
            )
        }
        val kept = planned.map { it.path }.toSet()
        discard(created.filter { !write || it.relativeTo(official).invariantSeparatorsPath !in kept })
        val finalTexts = demotions(append(texts, planned).first, demoted.values)
        val final = result!!
        record(final, finalTexts)
        if (write) {
            for ((name, text) in finalTexts) if (text != texts[name]) gateDir.resolve(name).writeText(text)
            Stamps.write(final, stampsDir, repo)
        }
        return Outcome(
            null, planned, leftOut, if (!write) emptyList() else created.map { it.relativeTo(official).invariantSeparatorsPath }.filter { it in kept }.sorted(),
            baseManifest.rows.size, baseManifest.rows.size + planned.size, basePlaceDays, final.placeDays, final,
            demoted.values.toList(),
        )
    }

    /** The gate files with each of [demoted]'s lines at the end of its file, under a comment saying what it is. */
    private fun demotions(texts: Map<String, String>, demoted: Collection<Demoted>): Map<String, String> {
        val out = LinkedHashMap(texts)
        for (d in demoted) {
            out[d.gate] = out.getValue(d.gate).trimEnd('\n') + "\n" +
                "# prove, $date: ${d.target} demoted by itself (ruling R185): a capture of its authority's own site that the monitor holds " +
                "broke a promise, and its row was left out. The line goes only with those days proven (the method refitted or the authority's misprint recorded, their " +
                "rows here and green), or stays with the registry's class lowered\n" +
                GateManifest.demotedLine(d.entry, d.unit, d.since, d.reason) + "\n"
        }
        return out
    }

    /** Sets on [result] the demotions [texts] hold, as the gate would have read them (the stamps are written from it). */
    private fun record(result: GateResult, texts: Map<String, String>) {
        val all = manifest(texts).demotions
        for (s in result.entries.values) s.demoted = standing(all, s.entry.id)
    }

    /**
     * What [s] broke, in words a public gate file may hold (ruling R69: dates, counts and minutes, never a printed
     * time), or null when it broke nothing.
     */
    private fun broke(s: EntryStats): String? = (
        s.events.entries.flatMap { (event, e) ->
            listOfNotNull(
                "${event.key} early on ${e.early} place-days, by up to ${e.worstEarly} min (${dateRanges(e.brokenDates).joinToString(", ")})".takeIf { e.early > 0 },
                "${event.key} ended late on ${e.lateEnd} place-days, by up to ${e.worstEarly} min (${dateRanges(e.brokenDates).joinToString(", ")})".takeIf { e.lateEnd > 0 },
                "${event.key} over its late limit on ${e.over} place-days".takeIf { e.over > 0 },
            )
        } + listOfNotNull("${s.outOfOrder} days out of order".takeIf { s.outOfOrder > 0 })
        ).joinToString("; ").ifEmpty { null }

    /** Deletes [files] (pinned copies this run wrote) and every folder under pinned/ they leave empty. */
    private fun discard(files: List<File>) {
        val stop = official.resolve("archive/tables/pinned").canonicalFile
        for (file in files) {
            val parent = file.canonicalFile.parentFile
            file.delete()
            var dir = parent
            while (dir != null && dir != stop && dir.startsWith(stop) && dir.list()?.isEmpty() == true) {
                dir.delete()
                dir = dir.parentFile
            }
        }
    }

    /** The problem the gate file's reader finds in [p]'s row on its own (under its file's header), or null. */
    private fun readable(p: Planned): String? {
        val header = header(texts.getValue(p.gate))
        val row = header.joinToString("\t") { p.cells[it].orEmpty() }.trimEnd('\t')
        return try {
            GateManifest.parse(p.gate, header.joinToString("\t") + "\n" + row + "\n")
            null
        } catch (e: GateError) {
            e.problems.joinToString("; ")
        }
    }

    /** What prove keeps of the base gate's result (ruling R280): the entries red, the checked timetables (A or B), the place-days. */
    private class Summary(val red: Map<String, String>, val checked: Set<String>, val placeDays: Int)

    private fun summarise(r: GateResult) = Summary(
        r.entries.values.filter { r.violations(it).isNotEmpty() }.associate { it.entry.id to r.violations(it).first().lineSequence().first() },
        r.entries.values.filter { s -> s.classes.any { it == EntryClass.A || it == EntryClass.B } }.mapTo(HashSet()) { it.entry.id },
        r.placeDays,
    )

    private fun fail(why: String, manifest: GateManifest, placeDays: Int = 0) =
        Outcome(why, emptyList(), emptyList(), emptyList(), manifest.rows.size, manifest.rows.size, placeDays, placeDays, null)

    private fun GateResult.violations(s: EntryStats): List<String> = neverEarly(s) + overLimit(s)

    /**
     * Why [rows] of one entry break a promise on their own (the new row with the entry's committed rows), or null;
     * and [entry]'s tally then, when it is the promise they break (not a refusal).
     */
    private fun alone(rows: List<GateRow>, excuses: List<Excuse>, sources: List<String>, entry: String): Pair<String?, EntryStats?> = try {
        val r = gate.evaluate(GateManifest(rows, sources, excuses))
        val why = r.entries.values.flatMap { r.violations(it) }.firstOrNull()?.lines()?.take(3)?.joinToString(" ")
        why to r.entries[entry]?.takeIf { why != null && r.violations(it).isNotEmpty() }
    } catch (e: GateError) {
        "refused by the gate: ${e.problems.first()}" to null
    }

    /** The new rows a refusal names (by their line, or the `@excuse` line they contradict), with the reason. */
    private fun refused(e: GateError, planned: List<Planned>, rowOf: Map<Planned, GateRow>, excuses: List<Excuse>): Map<Planned, String> {
        val drop = LinkedHashMap<Planned, String>()
        for (problem in e.problems) {
            for (p in planned) if (Regex("""\b${Regex.escape(rowOf.getValue(p).where)}\b""").containsMatchIn(problem)) drop.putIfAbsent(p, "refused by the gate: $problem")
            for (x in excuses) {
                if (!Regex("""\b${Regex.escape(x.where)}\b""").containsMatchIn(problem)) continue
                for (p in planned) if (p.entry.substringBefore('/') == x.entry && p.member == x.member) drop.putIfAbsent(p, "refused by the gate: $problem")
            }
        }
        if (drop.isEmpty()) for (p in planned) drop[p] = "refused by the gate (no row named): ${e.problems.first()}"
        return drop
    }

    private fun manifest(texts: Map<String, String>): GateManifest {
        val parsed = texts.map { (name, text) -> GateManifest.parse(name, text) }
        return GateManifest(
            parsed.flatMap { it.rows }, texts.keys.toList(), parsed.flatMap { it.excuses }, parsed.flatMap { it.demotions },
            parsed.flatMap { it.fitted },
        )
    }

    /** The gate files with [planned] appended under a comment each, and each new row's line. */
    private fun append(texts: Map<String, String>, planned: List<Planned>): Pair<Map<String, String>, Map<Planned, Int>> {
        val lines = HashMap<Planned, Int>()
        val out = LinkedHashMap(texts)
        for ((name, rows) in planned.groupBy { it.gate }) {
            val header = header(texts.getValue(name))
            val sb = StringBuilder(texts.getValue(name).trimEnd('\n')).append('\n')
            val sources = rows.map { it.path.split('/').getOrNull(3) ?: "?" }.distinct().sorted()
            sb.append("# prove, $date: the monitor's captures pinned as they were under archive/tables/pinned/{${sources.joinToString(",")}}/$date/, ")
                .append("each row as its recipe (official/monitor/recipes.tsv) and family say, added with the whole gate green\n")
            var n = sb.lines().size - 1
            for (p in rows) {
                n++
                lines[p] = n
                sb.append(header.joinToString("\t") { p.cells[it].orEmpty() }.trimEnd('\t')).append('\n')
            }
            out[name] = sb.toString()
        }
        return out to lines
    }

    private fun header(text: String): List<String> =
        text.lines().first { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }.split('\t').map { it.trim() }

    /** Every row the recipes call for whose days the gate does not hold yet. */
    private fun plan(texts: Map<String, String>, manifest: GateManifest, leftOut: MutableList<LeftOut>): List<Planned> {
        val out = LinkedHashMap<String, Planned>()
        // Each capture a recipe applies to, and whether any of its recipes found a row to add or hold it at (a capture none
        // picks up, a city new to a fetcher or an index line naming no entry, is reported, never skipped in silence).
        val picked = LinkedHashMap<String, Boolean>()
        for (recipe in recipes) {
            if (only.isNotEmpty() && recipe.source !in only) continue
            val text = texts[recipe.gate]
            if (text == null) {
                leftOut += LeftOut(recipe.where, "no gate file ${recipe.gate}")
                continue
            }
            val header = header(text)
            val rows = manifest.rows.filter { it.source == recipe.gate }
            for (capture in index.filter { it.source == recipe.source && it.survey == null && Recipes.applies(recipe, it.key, recipes) }.sortedBy { it.key }) {
                picked.putIfAbsent(capture.id, false)
                val table = if (recipe.from == "-") capture else from(recipe, capture) ?: run {
                    leftOut += LeftOut("${recipe.where} ${capture.id}", "no capture ${expand(recipe.from, capture.key)} in the monitor's index")
                    picked[capture.id] = true
                    null
                } ?: continue
                val targets = targets(recipe, capture, table, rows)
                if (targets.isNotEmpty()) picked[capture.id] = true
                for (target in targets) {
                    val label = "${recipe.gate} ${target.entry}" + (target.member?.let { " member $it" } ?: "") + " at ${target.pointText} from ${table.id}"
                    try {
                        val p = planRow(recipe, header, rows, manifest, capture, table, target) ?: continue
                        out.putIfAbsent("${p.gate}|${p.path}|${p.entry}|${p.member}|${p.point}", p)
                    } catch (e: IllegalArgumentException) {
                        leftOut += LeftOut(label, e.message ?: "unreadable")
                    }
                }
            }
        }
        printed.clear()
        for ((id, any) in picked) {
            if (any) continue
            val capture = index.first { it.id == id }
            leftOut += LeftOut(
                id,
                if (capture.entry == null) "no recipe line reads it: the monitor's index names no entry for it, and no gate row of this source and key exists to clone"
                else "no recipe line reads it: no gate row of this source and key exists to clone (a capture new to its fetcher is added by hand once)",
            )
        }
        return out.values.toList()
    }

    private data class Target(val entry: String, val member: String?, val lat: Double?, val lon: Double?, val family: GateRow? = null) {
        val pointText: String get() = if (lat == null) "unit" else "$lat,$lon"
    }

    private fun from(recipe: Recipe, capture: MonitorTable): MonitorTable? {
        val (source, key) = expand(recipe.from, capture.key).split('/', limit = 2)
        return index.firstOrNull { it.source == source && it.key == key }
    }

    private fun expand(template: String, key: String): String {
        val month = Regex("""(\d{4})-(\d{2})$""").find(key)
        val year = month?.groupValues?.get(1) ?: Regex("""(\d{4})""").findAll(key).lastOrNull()?.value
        var out = template
        if (year != null) out = out.replace("{yyyy}", year)
        if (month != null) out = out.replace("{mm}", month.groupValues[2])
        return out
    }

    /** Rows of [rows] that read an earlier capture of [table]'s source and key (pinned or live). */
    private fun readsCapture(row: GateRow, table: MonitorTable): Boolean = captureOf(row) == table.source to table.key

    /**
     * Whether [row] reads a capture of [table]'s source under another key, in the layout the index gives [table]
     * (when the index still lists that key; the fetcher writes every key of a source alike).
     */
    private fun readsSource(row: GateRow, table: MonitorTable): Boolean {
        val (source, key) = captureOf(row) ?: return false
        if (source != table.source || key == table.key) return false
        // A day's table and a printed imsak are two layouts of one source (ruling R284): neither is the other's family.
        if (key.endsWith(IMSAK_KEY) != table.key.endsWith(IMSAK_KEY)) return false
        val listed = index.firstOrNull { it.source == source && it.key == key } ?: return true
        return listed.format == table.format && listed.columns == table.columns
    }

    /** The source and key of the monitor capture [row] reads (pinned or live), or null. */
    private fun captureOf(row: GateRow): Pair<String, String>? {
        // A printed imsak read back by hand from the monitor's raw answers (archive/tables/imsak/<source>/<capture>/<key>.txt)
        // is that source's `<key>-imsak` (ruling R284): the fetcher now keeps the same two columns under that key.
        Regex("""archive/tables/imsak/([^/]+)/\d{4}-\d{2}-\d{2}/([^/]+)\.txt""").matchEntire(row.path)?.let { m ->
            return m.groupValues[1] to m.groupValues[2].substringBefore('.') + IMSAK_KEY
        }
        val m = Regex("""archive/tables/(?:pinned/([^/]+)/\d{4}-\d{2}-\d{2}|monitor/([^/]+))/([^/]+)\.txt""").matchEntire(row.path) ?: return null
        return m.groupValues[1].ifEmpty { m.groupValues[2] } to m.groupValues[3].substringBefore('.')
    }

    private fun entryText(row: GateRow): String = row.entry + (row.unit?.let { "/$it" } ?: "")

    private fun targets(recipe: Recipe, capture: MonitorTable, table: MonitorTable, rows: List<GateRow>): List<Target> {
        if (recipe.entry == "same") {
            return rows.filter { readsCapture(it, table) }.groupBy { Triple(entryText(it), it.member, it.lat to it.lon) }
                .map { (k, family) -> Target(k.first, k.second, k.third.first, k.third.second, family.last()) }
        }
        val entry = if (recipe.entry == "index") capture.entry ?: return emptyList() else recipe.entry
        val member = when (recipe.member) {
            "-" -> null
            "index" -> capture.entry?.substringBefore('/') ?: return emptyList()
            else -> recipe.member
        }
        val points: List<Pair<Double?, Double?>> = when (recipe.point) {
            // A capture naming a unit is read at the unit's own point, as the monitor's check reads it (TableCheck).
            "index" -> listOf(if (capture.entry?.contains('/') == true || capture.lat == null) null to null else capture.lat to capture.lon)
            "unit" -> listOf(null to null)
            "all" -> rows.filter { entryText(it) == entry && it.member == member }.map { it.lat to it.lon }.distinct()
            else -> recipe.point.split(',').map { it.toDouble() }.let { listOf(it[0] to it[1]) }
        }
        return points.map { (lat, lon) -> Target(entry, member, lat, lon) }
    }

    private fun planRow(
        recipe: Recipe, header: List<String>, rows: List<GateRow>, manifest: GateManifest, capture: MonitorTable, table: MonitorTable, target: Target,
    ): Planned? {
        val sameTarget = rows.filter { entryText(it) == target.entry && it.member == target.member && it.lat == target.lat && it.lon == target.lon }
        val source = official.resolve(table.path)
        require(source.isFile) { "the capture ${table.path} is not in the archive" }
        val captureLines = source.readLines()
        // The family: the same key's earlier capture read at this target, else a row whose table prints the same as the capture.
        val sameKey = target.family ?: sameTarget.lastOrNull { readsCapture(it, table) }
        val sameLayout = if (sameKey != null) null else sameTarget.lastOrNull { agrees(it, captureLines, table) }
        // A key no row of this target has read and no date in common (a new year's table): the latest row of the target
        // that reads another capture of the same source, which its fetcher writes in the same layout (the index says so
        // when it still lists that key) — za.cape's Jamiat member for January from the December slice.
        val sameSource = if (sameKey != null || sameLayout != null) null else sameTarget.lastOrNull { readsSource(it, table) }
        // A cautious member's row reads its member's table by the gate file's own convention for that member (a Shafi'i
        // reading of a two-school table), which the fetcher's metadata does not know: the target's latest row of the same
        // width, before the index. A row of the source's own entry falls back to the fetcher's description of its file.
        val sameWidth = if (sameKey != null || sameLayout != null || sameSource != null || target.member == null) null
        else sameTarget.lastOrNull { it.columns.size == table.columns.split(' ').count { c -> c.isNotEmpty() } }
        val family = sameKey ?: sameLayout ?: sameSource ?: sameWidth
        val columns = family?.let { columnsText(it) } ?: table.columns
        val format = family?.format ?: table.format
        val school = family?.let { schoolText(it) } ?: table.school
        val zone = family?.zone ?: table.zone
        val clock = family?.clock ?: table.clock
        val count = columns.split(' ').count { it.isNotEmpty() }
        require(format == "daily" || recipe.days == "all") { "days ${recipe.days} needs the daily format, not $format" }
        val keep: (LocalDate) -> Boolean = when (recipe.days) {
            "month" -> {
                val m = Regex("""(\d{4})-(\d{2})$""").find(capture.key) ?: throw IllegalArgumentException("days month: the key ${capture.key} ends with no yyyy-mm")
                val y = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt();
                { d -> d.year == y && d.monthNumber == mo }
            }
            "excused" -> {
                val excused = excusedDays(manifest.excuses.filter { it.source == recipe.gate }, target)
                ({ d -> d !in excused })
            }
            else -> ({ _ -> true })
        }
        val dateOf = Regex("""^\s*(\d{4}-\d{2}-\d{2})\b""")
        val kept = captureLines.filter { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) return@filter true
            val d = dateOf.find(line)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@filter true
            keep(d)
        }
        val days = OfficialTable.from(world.taqwa.timetables.gate.OfficialFormats.named(format).read(kept, count)).days.map { it.date }.distinct().sorted()
        if (days.isEmpty()) return null
        require(target.member == null || "member" in header) { "${recipe.gate} has no member column" }
        require(clock == null || "clock" in header) { "${recipe.gate} has no clock column" }
        fun cellsOf(path: String, note: String) = linkedMapOf(
            "path" to path, "entry" to target.entry, "lat" to (target.lat?.let(::num) ?: ""), "lon" to (target.lon?.let(::num) ?: ""), "zone" to zone,
            "columns" to columns, "format" to format, "school" to school, "split" to "test", "note" to note,
            "member" to target.member.orEmpty(), "clock" to clock.orEmpty(),
        )
        // The row as the gate file's reader will take it, before its path and note are known: the events it reads.
        val probe = probe(recipe.gate, header, cellsOf(table.path, "probe"))
        val events = probe?.columns?.flatten()?.toSet().orEmpty()
        // The rows of the target that hold the capture's days. Ruling R282 (the second audit's G18): a republisher's
        // mirror holds no day of the authority's own capture, so e-solat's own table is proven where the mirror's is
        // gated (a mirror's capture is held by any row). Ruling R284 (G25): a row holds the capture's days only where it
        // reads one of the same events and, like it, ends alone or not (a printed imsak is held by imsak rows alone; an
        // imsak row holds no day's starts).
        val holding = sameTarget.filter { r ->
            (table.mirror || !mirrored(r)) && (probe == null || (r.endsOnly == probe.endsOnly && r.columns.flatten().any { it in events }))
        }
        val held = holding.flatMap { held(it) }.toSet()
        // Ruling R283 (the second audit's G20): a capture whose every day is held may still re-read a held day otherwise
        // than the held table prints it (an authority's correction, e-solat's every-run re-read, Kemenag's re-read month,
        // Habous's re-posted month). Those days alone become a row, so the gate judges what is printed now and R185 can
        // demote on it; a capture that prints what is held adds nothing, as before.
        val whole = held.containsAll(days)
        val changed = if (!whole || probe == null) emptyList() else changedDays(probe, kept, count, format, holding, days)
        if (whole && changed.isEmpty()) return null
        val cut = changed.isNotEmpty()
        val changedSet = changed.toSet()
        val rowLines = if (!cut) kept else kept.filter { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) return@filter true
            val d = dateOf.find(line)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@filter false
            d in changedSet
        }
        val derived = rowLines.size != captureLines.size
        val suffix = when {
            kept.size == captureLines.size -> ""
            recipe.days == "month" -> ".month-" + capture.key.takeLast(7)
            else -> ".excused-" + (target.member ?: target.entry.substringBefore('/'))
        } + (if (cut) ".changed-${changed.first()}-${changed.size}" else "")
        val path = "archive/tables/pinned/${table.source}/$date/${table.key}$suffix.txt"
        val content = if (!derived) source.readText() else {
            val what = listOfNotNull(
                when {
                    kept.size == captureLines.size -> null
                    recipe.days == "month" -> "the days of ${capture.key.takeLast(7)} alone"
                    else -> "without the days ${recipe.gate}'s @excuse lines excuse for ${target.member}"
                },
                if (cut) "the ${changed.size} held day${if (changed.size == 1) "" else "s"} it prints otherwise than the held table alone (ruling R283)" else null,
            ).joinToString(", ")
            val note = "# prove, $date: ${table.path} as the monitor captured it, $what (a row's days cut as its recipe says)"
            (listOf(note) + rowLines).joinToString("\n").trimEnd('\n') + "\n"
        }
        val dated = "the monitor capture of ${words(date)}"
        // The family's note, its capture date replaced, only when nothing else in it names a month or a date (a range it
        // gives would describe the family's days, not this row's); else the capture's name and date.
        val copied = family?.note?.let { n ->
            val re = Regex("""the monitor('s)? capture of \d{1,2} [A-Z][a-z]{2} \d{4}""")
            if (re.containsMatchIn(n) && !DATED.containsMatchIn(re.replace(n, ""))) re.replace(n, dated) else null
        }
        // A family's note says whether its table was a mirror's; this row says what its own capture is (ruling R282).
        val named = when {
            copied == null -> "${table.name}, $dated (prove)"
            !table.mirror -> copied.replace(" $MIRROR_MARK", "")
            MIRROR_MARK in copied -> copied
            else -> "$copied $MIRROR_MARK"
        }
        val note = named + if (cut) "; ${changed.size} held day${if (changed.size == 1) "" else "s"} re-read otherwise, held as now printed (ruling R283)" else ""
        // Defence in depth (ruling R69): a note is public, and a name the fetcher gave that reads like a time never goes in.
        require(!TIME_SHAPE.containsMatchIn(note)) { "the note for this row would carry a time-shaped text (from the index's name); not written to a public file" }
        val cells = cellsOf(path, note)
        val familyWhy = when {
            sameKey != null -> "the earlier capture's row ${sameKey.where}"
            sameLayout != null -> "${sameLayout.where}, whose table prints the same on every day both hold"
            sameSource != null -> "${sameSource.where}, which reads another capture of the same source (the fetcher's one layout)"
            sameWidth != null -> "${sameWidth.where}, the member's latest row of the same width (the gate file's convention for the member)"
            else -> "the fetcher's metadata (no row of this target reads the same layout)"
        } + (if (cut) "; the held days it prints otherwise alone (ruling R283)" else "")
        return Planned(recipe.gate, cells, content, table.id, recipe.where, if (cut) changed else days, familyWhy, table.mirror)
    }

    /** [cells] as one row of [gate] under [header], read by the gate file's own reader; null where it refuses it. */
    private fun probe(gate: String, header: List<String>, cells: Map<String, String>): GateRow? = try {
        GateManifest.parse(gate, header.joinToString("\t") + "\n" + header.joinToString("\t") { cells[it].orEmpty() }.trimEnd('\t') + "\n").rows.singleOrNull()
    } catch (e: GateError) {
        null
    }

    /** Whether [row] reads a republisher's mirror (ruling R282): its note names the mark, or its file is a mirror's copy. */
    private fun mirrored(row: GateRow): Boolean = MIRROR_MARK in row.note || "-mirror" in row.path.substringAfterLast('/')

    /**
     * Ruling R283: the days of [days] on which the capture ([lines], read as [probe] reads it) prints an event otherwise
     * than every row of [holding] that prints that event on that day (a row on another clock is not compared). A cell the
     * capture leaves empty, or one no held row prints, is no change: only a re-read that differs from what is held is.
     */
    private fun changedDays(probe: GateRow, lines: List<String>, count: Int, format: String, holding: List<GateRow>, days: List<LocalDate>): List<LocalDate> {
        val mine = OfficialTable.from(world.taqwa.timetables.gate.OfficialFormats.named(format).read(lines, count)).days.associate { it.date to it.minutes }
        val readings = holding.filter { it.clockZone == probe.clockZone }.map { it to cells(it) }
        val out = mutableListOf<LocalDate>()
        for (d in days) {
            val now = mine[d] ?: continue
            val differs = probe.columns.withIndex().any { (i, events) ->
                val v = now.getOrNull(i) ?: return@any false
                events.any { e ->
                    val was = readings.mapNotNull { (r, byDate) ->
                        val column = r.columns.indexOfFirst { e in it }
                        if (column < 0) null else byDate[d]?.getOrNull(column)
                    }
                    was.isNotEmpty() && v !in was
                }
            }
            if (differs) out += d
        }
        return out
    }

    /** The cells [row]'s table prints, by date, read as the gate reads it (cached for one plan by table and reading). */
    private fun cells(row: GateRow): Map<LocalDate, List<Int?>> = printed.getOrPut("${row.path}|${row.format}|${row.columns.size}") {
        val file = roots.file(row.path)
        if (!file.isFile) emptyMap() else OfficialTable.read(file, row.format, row.columns.size).days.associate { it.date to it.minutes }
    }

    private fun num(d: Double): String = d.toString().removeSuffix(".0")

    private fun words(d: LocalDate): String =
        "${d.dayOfMonth} ${listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[d.monthNumber - 1]} ${d.year}"

    /** A family row's own cell as its gate file writes it (the columns and school exactly as written there). */
    private fun raw(row: GateRow, name: String): String {
        val text = texts.getValue(row.source)
        val names = header(text)
        val cells = text.lines()[row.line - 1].split('\t').map { it.trim() }
        return cells.getOrNull(names.indexOf(name)).orEmpty()
    }

    private fun columnsText(row: GateRow): String = raw(row, "columns")

    private fun schoolText(row: GateRow): String = raw(row, "school")

    /** The dates [row]'s table holds, read as the gate reads it (cached by table and reading). */
    private fun held(row: GateRow): Set<LocalDate> = tables.getOrPut("${row.path}|${row.format}|${row.columns.size}") {
        val file = roots.file(row.path)
        if (!file.isFile) emptySet() else OfficialTable.read(file, row.format, row.columns.size).days.mapTo(HashSet()) { it.date }
    }

    /** Whether [row]'s table prints the same tokens as the capture on every date both hold (at least one), so its columns read the capture alike. */
    private fun agrees(row: GateRow, capture: List<String>, table: MonitorTable): Boolean {
        if (row.format != "daily" || table.format != "daily") return false
        val n = row.columns.size
        if (n != table.columns.split(' ').count { it.isNotEmpty() }) return false
        fun byDate(lines: List<String>) = lines.asSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(Regex("\\s+")) }.filter { it.size > n }.associate { it[0] to it.subList(1, n + 1) }
        val file = roots.file(row.path)
        if (!file.isFile) return false
        val theirs = byDate(file.readLines())
        val ours = byDate(capture)
        val common = theirs.keys.intersect(ours.keys)
        return common.isNotEmpty() && common.all { theirs[it] == ours[it] }
    }

    private fun excusedDays(excuses: List<Excuse>, target: Target): Set<LocalDate> {
        val out = HashSet<LocalDate>()
        for (x in excuses) {
            if (x.entry != target.entry.substringBefore('/') || x.member != target.member) continue
            if (x.at != null && (x.at.lat != target.lat || x.at.lon != target.lon)) continue
            var d = x.days.start
            while (d <= x.days.endInclusive) {
                out += d
                d = d.plus(1, DateTimeUnit.DAY)
            }
        }
        return out
    }

    companion object {
        private const val MONTHS = "Jan(uary)?|Feb(ruary)?|Mar(ch)?|Apr(il)?|May|June?|July?|Aug(ust)?|Sep(t|tember)?|Oct(ober)?|Nov(ember)?|Dec(ember)?"

        /** A month's name or a date in a note (what a copied note must not keep: it would describe another row's days). */
        private val DATED = Regex("""\b($MONTHS)\b|\b\d{4}-\d{2}(-\d{2})?\b""")

        /** A clock time's shape, h:mm or hh:mm (ruling R69: never in a public file). */
        val TIME_SHAPE = Regex("""(^|[^0-9+\-])[0-2]?[0-9]:[0-5][0-9]([^0-9]|$)""")
    }
}
