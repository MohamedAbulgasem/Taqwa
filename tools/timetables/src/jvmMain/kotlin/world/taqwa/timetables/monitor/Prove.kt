package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
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
import java.io.File

/**
 * `prove` (docs/MONITOR.md): the monitor's new captures turned into proof with no human. For each
 * capture a recipe ([Recipes]) names whose days the gate rows do not all hold yet, the capture is
 * copied as it is to `archive/tables/pinned/<source>/<date>/` (a member row's days cut as its
 * recipe says, in a file of its own beside it), the recipe's rows are appended to their gate files,
 * and the WHOLE gate runs. A new row that breaks a promise (an early start, a late end, lateness
 * over its limit, a capped Maghrib unchecked, a day out of order) or that the gate refuses is
 * removed again and reported, never loosened, and the gate runs again until it is green. Then the
 * gate files and the stamps are written. A gate that is red before anything is added stops it
 * (nothing is written). Running it again adds nothing: every capture's days are then held.
 *
 * Every row takes what it reads from its family, never from the recipe: the latest row of the same
 * gate file, entry, member and point that reads an earlier capture of the same source and key;
 * else such a row reading any table that prints the same minutes as the capture on every day both
 * hold (the same layout, proven by the files themselves); else the fetcher's own metadata in the
 * monitor's index.
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
    ) {
        val path: String get() = cells.getValue("path")
        val entry: String get() = cells.getValue("entry")
        val member: String get() = cells["member"].orEmpty()
        val point: String get() = listOf(cells["lat"].orEmpty(), cells["lon"].orEmpty()).joinToString(",").let { if (it == ",") "unit" else it }
        val label: String get() = "$gate $entry" + (if (member.isNotEmpty()) " member $member" else "") + " at $point from $capture"
    }

    data class LeftOut(val label: String, val reason: String)

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
    ) {
        val changed: Boolean get() = failure == null && added.isNotEmpty()

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
            "leftOut" to leftOut.map { linkedMapOf("row" to it.label, "reason" to it.reason) },
            "pinned" to pinned,
        )
    }

    private val tables = HashMap<String, Set<LocalDate>>()
    private var texts: Map<String, String> = emptyMap()

    fun run(write: Boolean = true): Outcome {
        val texts = gateDir.listFiles { f -> f.isFile && f.name.endsWith(".tsv") }!!.sortedBy { it.name }.associate { it.name to it.readText() }
        this.texts = texts
        val baseManifest = manifest(texts)
        val base = try {
            gate.evaluate(baseManifest)
        } catch (e: GateError) {
            return fail("the gate's inputs are refused before prove adds anything: ${e.problems.take(5).joinToString("; ")}", baseManifest)
        }
        // An entry red before anything is added (a hand-read member row committed ahead of the capture it leans on) can
        // only be made green by its new rows together; if they do not, prove fails and writes nothing.
        val baseRed = base.entries.values.filter { base.violations(it).isNotEmpty() }.associate { it.entry.id to base.violations(it).first().lineSequence().first() }
        val leftOut = mutableListOf<LeftOut>()
        var planned = plan(texts, baseManifest, leftOut)
        // Pinned copies: a path that exists with other content is never overwritten.
        val created = mutableListOf<File>()
        planned = planned.filter { p ->
            val file = official.resolve(p.path)
            when {
                !file.exists() -> true
                file.readText() == p.content -> true
                else -> false.also { leftOut += LeftOut(p.label, "${p.path} already exists with other content; a pinned copy is never rewritten") }
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
        while (planned.isNotEmpty()) {
            val (withRows, lines) = append(texts, planned)
            val manifest = manifest(withRows)
            val rowOf = planned.associateWith { p -> manifest.rows.first { it.source == p.gate && it.line == lines.getValue(p) } }
            val evaluated = try {
                gate.evaluate(manifest)
            } catch (e: GateError) {
                val dropped = refused(e, planned, rowOf, manifest.excuses)
                for ((p, why) in dropped) leftOut += LeftOut(p.label, why)
                planned = planned - dropped.keys
                continue
            }
            val failing = evaluated.entries.values.filter { evaluated.violations(it).isNotEmpty() }
            if (failing.isEmpty()) {
                result = evaluated
                break
            }
            val drop = LinkedHashMap<Planned, String>()
            for (s in failing) {
                val own = planned.filter { it.entry.substringBefore('/') == s.entry.id }
                if (s.entry.id in baseRed) {
                    if (own.isEmpty()) {
                        created.forEach { it.delete() }
                        return fail("the gate is red before prove at ${s.entry.id} and no new row repairs it: ${baseRed.getValue(s.entry.id)}", baseManifest, base)
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
                    val why = alone(baseRows + rowOf.getValue(p), excuses, manifest.sources)
                    if (why != null) {
                        drop[p] = why
                        any = true
                    }
                }
                if (!any) {
                    val why = evaluated.violations(s).first().lines().take(3).joinToString(" ")
                    for (p in own) drop.putIfAbsent(p, "breaks a promise only with the other new rows of ${s.entry.id}: $why")
                }
            }
            for ((p, why) in drop) leftOut += LeftOut(p.label, why)
            planned = planned - drop.keys
        }
        if (planned.isEmpty()) {
            // Nothing kept: the pinned copies this run wrote are removed again, the gate files left alone.
            created.forEach { it.delete() }
            if (baseRed.isNotEmpty()) {
                return fail("the gate is red before prove and nothing added repairs it: ${baseRed.entries.take(5).joinToString("; ") { it.value }}", baseManifest, base)
            }
            return Outcome(null, emptyList(), leftOut, emptyList(), baseManifest.rows.size, baseManifest.rows.size, base.placeDays, base.placeDays, base)
        }
        val kept = planned.map { it.path }.toSet()
        for (file in created) if (!write || file.relativeTo(official).invariantSeparatorsPath !in kept) file.delete()
        val (finalTexts, _) = append(texts, planned)
        val final = result!!
        if (write) {
            for ((name, text) in finalTexts) if (text != texts[name]) gateDir.resolve(name).writeText(text)
            Stamps.write(final, stampsDir, repo)
        }
        return Outcome(
            null, planned, leftOut, if (!write) emptyList() else created.map { it.relativeTo(official).invariantSeparatorsPath }.filter { it in kept }.sorted(),
            baseManifest.rows.size, baseManifest.rows.size + planned.size, base.placeDays, final.placeDays, final,
        )
    }

    private fun fail(why: String, manifest: GateManifest, result: GateResult? = null) =
        Outcome(why, emptyList(), emptyList(), emptyList(), manifest.rows.size, manifest.rows.size, result?.placeDays ?: 0, result?.placeDays ?: 0, result)

    private fun GateResult.violations(s: world.taqwa.timetables.gate.EntryStats): List<String> = neverEarly(s) + overLimit(s) + unchecked(s)

    /** Why [rows] of one entry break a promise on their own (the new row with the entry's committed rows), or null. */
    private fun alone(rows: List<GateRow>, excuses: List<Excuse>, sources: List<String>): String? = try {
        val r = gate.evaluate(GateManifest(rows, sources, excuses))
        r.entries.values.flatMap { r.violations(it) }.firstOrNull()?.lines()?.take(3)?.joinToString(" ")
    } catch (e: GateError) {
        "refused by the gate: ${e.problems.first()}"
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
        return GateManifest(parsed.flatMap { it.rows }, texts.keys.toList(), parsed.flatMap { it.excuses })
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
        for (recipe in recipes) {
            if (only.isNotEmpty() && recipe.source !in only) continue
            val text = texts[recipe.gate]
            if (text == null) {
                leftOut += LeftOut(recipe.where, "no gate file ${recipe.gate}")
                continue
            }
            val header = header(text)
            val rows = manifest.rows.filter { it.source == recipe.gate }
            for (capture in index.filter { it.source == recipe.source && it.survey == null && recipe.matches(it.key) }.sortedBy { it.key }) {
                val table = if (recipe.from == "-") capture else from(recipe, capture) ?: run {
                    leftOut += LeftOut("${recipe.where} ${capture.id}", "no capture ${expand(recipe.from, capture.key)} in the monitor's index")
                    null
                } ?: continue
                for (target in targets(recipe, capture, table, rows)) {
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
    private fun readsCapture(row: GateRow, table: MonitorTable): Boolean {
        val m = Regex("""archive/tables/(?:pinned/([^/]+)/\d{4}-\d{2}-\d{2}|monitor/([^/]+))/([^/]+)\.txt""").matchEntire(row.path) ?: return false
        val source = m.groupValues[1].ifEmpty { m.groupValues[2] }
        val key = m.groupValues[3].substringBefore('.')
        return source == table.source && key == table.key
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
        val family = sameKey ?: sameLayout
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
        val derived = kept.size != captureLines.size
        val days = OfficialTable.from(world.taqwa.timetables.gate.OfficialFormats.named(format).read(kept, count)).days.map { it.date }.distinct().sorted()
        if (days.isEmpty()) return null
        val held = sameTarget.flatMap { held(it) }.toSet()
        if (held.containsAll(days)) return null
        val suffix = when {
            !derived -> ""
            recipe.days == "month" -> ".month-" + capture.key.takeLast(7)
            else -> ".excused-" + (target.member ?: target.entry.substringBefore('/'))
        }
        val path = "archive/tables/pinned/${table.source}/$date/${table.key}$suffix.txt"
        val content = if (!derived) source.readText() else {
            val note = "# prove, $date: ${table.path} as the monitor captured it, " +
                (if (recipe.days == "month") "the days of ${capture.key.takeLast(7)} alone" else "without the days ${recipe.gate}'s @excuse lines excuse for ${target.member}") +
                " (a row's days cut as its recipe says)"
            (listOf(note) + kept).joinToString("\n").trimEnd('\n') + "\n"
        }
        val dated = "the monitor capture of ${words(date)}"
        val note = family?.note?.let { n ->
            val re = Regex("""the monitor('s)? capture of \d{1,2} [A-Z][a-z]{2} \d{4}""")
            if (re.containsMatchIn(n)) re.replace(n, dated) else null
        } ?: "${table.name}, $dated (prove)"
        val cells = linkedMapOf(
            "path" to path, "entry" to target.entry, "lat" to (target.lat?.let(::num) ?: ""), "lon" to (target.lon?.let(::num) ?: ""), "zone" to zone,
            "columns" to columns, "format" to format, "school" to school, "split" to "test", "note" to note,
            "member" to target.member.orEmpty(), "clock" to clock.orEmpty(),
        )
        require(target.member == null || "member" in header) { "${recipe.gate} has no member column" }
        require(clock == null || "clock" in header) { "${recipe.gate} has no clock column" }
        val familyWhy = when {
            sameKey != null -> "the earlier capture's row ${sameKey.where}"
            sameLayout != null -> "${sameLayout.where}, whose table prints the same on every day both hold"
            else -> "the fetcher's metadata (no row of this target reads the same layout)"
        }
        return Planned(recipe.gate, cells, content, table.id, recipe.where, days, familyWhy)
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

}
