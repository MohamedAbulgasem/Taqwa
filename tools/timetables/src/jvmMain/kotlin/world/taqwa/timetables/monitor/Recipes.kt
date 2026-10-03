package world.taqwa.timetables.monitor

import java.io.File

/**
 * One line of `official/monitor/recipes.tsv`: how a monitor capture becomes gate rows (`prove`,
 * docs/MONITOR.md). Metadata only: source ids, capture keys, gate files, entries, members and
 * points, never a printed time. What a row reads (its columns, zone, format, school, clock) is not
 * the recipe's to say: [Prove] takes it from the family's own rows in the gate file, so a convention
 * changed there is inherited by the next capture.
 *
 * - [source]: a source id of `sources.tsv`; [capture]: the capture keys it applies to (`*` any
 *   run of characters: `*`, `cape-town-*`).
 * - [gate]: the gate file the rows go into (`eg-esa.tsv`).
 * - [entry]: `index` (the capture's own entry as the monitor's index names it, `id` or `id/unit`),
 *   `same` (every row of the gate file that reads an earlier capture of the same source and key,
 *   cloned: its entry, member and point), or an entry (`id` or `id/unit`).
 * - [member]: `-` (none), `index` (the index's entry is the member: a cautious entry's member row),
 *   an id, or `*` with `same`.
 * - [point]: `index` (the capture's point, or its unit's), `unit` (empty: the unit's own point),
 *   `all` (every point the gate file's rows of this entry and member are read at), `same` (with
 *   `same`), or `lat,lon`.
 * - [days]: `all`; `month` (only the month the capture's key ends with, `…-yyyy-mm`: a cautious
 *   entry's members for the month a member published); `excused` (without the days the gate file's
 *   `@excuse` lines excuse for this entry's member there, ruling R117: a member row never holds a
 *   day its own excuse names).
 * - [from]: `-` (the capture itself) or `<source>/<key>` of another capture to read for the row,
 *   `{yyyy}` and `{mm}` taken from the capture's key (za.cape's Jamiat member for the MJC's month).
 */
data class Recipe(
    val line: Int,
    val source: String,
    val capture: String,
    val gate: String,
    val entry: String,
    val member: String,
    val point: String,
    val days: String,
    val from: String,
) {
    val where: String get() = "recipes.tsv:$line"

    fun matches(key: String): Boolean = Regex(capture.split('*').joinToString(".*") { Regex.escape(it) }).matches(key)
}

object Recipes {
    val HEADER = listOf("source", "capture", "gate", "entry", "member", "point", "days", "from")
    private val DAYS = setOf("all", "month", "excused")
    private val POINT = Regex("""-?\d+(\.\d+)?,-?\d+(\.\d+)?""")
    private val ID = Regex("""[a-z0-9][a-z0-9.\-]*(/[A-Za-z0-9.\-]+)*""")

    fun load(file: File): List<Recipe> = parse(file.readText())

    /** Every line, or every problem at once (with its line). */
    fun parse(text: String): List<Recipe> {
        val problems = mutableListOf<String>()
        val recipes = mutableListOf<Recipe>()
        var header: List<String>? = null
        text.lines().forEachIndexed { index, raw ->
            val line = index + 1
            if (raw.isBlank() || raw.startsWith("#")) return@forEachIndexed
            val cells = raw.split('\t').map { it.trim() }
            if (header == null) {
                if (cells != HEADER) problems += "recipes.tsv:$line: the header must be ${HEADER.joinToString(" ")}"
                header = cells
                return@forEachIndexed
            }
            if (cells.size != HEADER.size || cells.any { it.isEmpty() }) {
                problems += "recipes.tsv:$line: ${HEADER.size} cells, none empty"
                return@forEachIndexed
            }
            val r = Recipe(line, cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6], cells[7])
            val before = problems.size
            if (!r.gate.endsWith(".tsv") || '/' in r.gate) problems += "${r.where}: gate '${r.gate}' (a file name in official/gate/)"
            if (r.entry != "index" && r.entry != "same" && !ID.matches(r.entry)) problems += "${r.where}: entry '${r.entry}'"
            val same = listOf(r.entry == "same", r.member == "*", r.point == "same")
            if (same.any { it } && !same.all { it }) problems += "${r.where}: entry same goes with member * and point same"
            if (r.member !in setOf("-", "index", "*") && !ID.matches(r.member)) problems += "${r.where}: member '${r.member}'"
            if (r.point !in setOf("index", "unit", "all", "same") && !POINT.matches(r.point)) problems += "${r.where}: point '${r.point}'"
            if (r.days !in DAYS) problems += "${r.where}: days '${r.days}' (${DAYS.joinToString(" ")})"
            if (r.from != "-" && !Regex("""[a-z0-9\-]+/[A-Za-z0-9.\-{}]+""").matches(r.from)) problems += "${r.where}: from '${r.from}' (- or source/key)"
            if (problems.size == before) recipes += r
        }
        require(header != null) { "recipes.tsv: no header" }
        require(problems.isEmpty()) { problems.joinToString("\n") }
        return recipes
    }
}
