package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate

/**
 * How a run's findings are classified (ruling R93): each finding line, not each table. [tier]
 * orders the "Needs attention" section; [attention] decides the exit code; the informational
 * kinds (tier 6) are listed after it and never raise it; the green kinds close the report.
 */
enum class Kind(val tier: Int, val attention: Boolean, val heading: String) {
    /**
     * The promise itself: an early start, an end after the authority's, a day out of order, a survey
     * finding not recorded or past its record. Attention on every run until fixed or recorded.
     */
    NEVER_EARLY(1, true, "Never early"),

    /** A fetcher failed, the fetch driver broke, or a table could not be read or checked. */
    FETCH_BROKEN(2, true, "Fetch broken"),

    /** The backup mirror was configured and did not run. */
    BACKUP_FAILED(2, true, "Backup did not run"),

    /** A built-in data horizon inside its window (spec §5, brief P item 5). */
    HORIZON(3, true, "Horizon"),

    /** Lateness over the recorded limit in the full gate: the stamp is red. */
    GATE_LATE(3, true, "Over the late limit in the gate"),

    /**
     * Lateness over the recorded limit on a fetched table's own entry: attention (low) the first
     * time and whenever it worsens; afterwards carried as [STILL_OPEN].
     */
    OWN_LATE(4, true, "Over the late limit"),

    /** A source fetched by hand whose next edition was expected by now. */
    MANUAL_DUE(5, true, "Manual source due"),

    /** Own-table lateness already raised and not worse: carried, without raising the exit code. */
    STILL_OPEN(6, false, "Still open"),

    /** Something the run noticed about its own setup (the private repository's workflow file differs from the template). */
    NOTE(6, false, "Note"),

    /**
     * Lateness measured on a member row (the spread between a cautious entry's members, not an
     * engine error) and capped Maghribs left unchecked (the most-followed member's table not held).
     */
    FOR_THE_RECORD(6, false, "For the record"),

    /** A table not held before, checked and fine: a gate row is ready to paste. */
    NEW_TABLE_FINE(7, false, "New table, fine"),

    /** A held table whose content changed, checked and fine. */
    CHANGED_FINE(7, false, "Changed table, fine"),

    /** A table red last run, checked and fine now. */
    GREEN_AGAIN(7, false, "Green again"),

    /** A held table checked again (the engine changed, or `--check-all`) and fine: one line for all. */
    CHECKED_FINE(7, false, "Checked again, fine"),

    /** Nothing to do: a line for the record. */
    GREEN(7, false, "Green"),
}

/**
 * One finding: its [kind], a one-line [title], the [details] under it and what to do [next].
 * [key] is its identity across runs (the attention set the notification watches); for a tier-1
 * item [days] and [worst] are the failing days and the worst minutes (the order within the tier, and
 * what counts as worse); [group] orders tier 1 (fetched tables 0, the gate 1, the surveys 2);
 * [label] is its short name in the index line; [since] dates a still-open item.
 */
data class Item(
    val kind: Kind,
    val title: String,
    val details: List<String> = emptyList(),
    val next: String? = null,
    val key: String = "${kind.name}:$title",
    val days: Int = 0,
    val worst: Int = 0,
    val group: Int = 0,
    val label: String = title,
    val since: LocalDate? = null,
)

/**
 * The report (brief P item 6, ruling R93): first a summary that leads with the never-early count,
 * then "Needs attention" opened by a one-line index of the never-early items, each item with what
 * to do next, in tier order; then the informational lines; then the green lines; then the backup.
 */
object Report {

    /** The items that need attention, in the order the report lists them. */
    fun attention(items: List<Item>): List<Item> =
        items.filter { it.kind.attention }.sortedWith(compareBy({ it.kind.tier }, { it.group }, { -it.days }, { it.title }))

    /** The most items the index line and the summary name; the rest is "and N more". */
    private const val NAMED = 40

    private fun named(items: List<Item>, separator: String): String =
        items.take(NAMED).joinToString(separator) { it.label } + (if (items.size > NAMED) "${separator}and ${items.size - NAMED} more" else "")

    /** "Never early: N — a; b; c", or null when there is none. */
    fun index(items: List<Item>): String? {
        val never = attention(items).filter { it.kind == Kind.NEVER_EARLY }
        if (never.isEmpty()) return null
        return "Never early: ${never.size} — ${named(never, "; ")}"
    }

    /** The summary's first sentence, also the line the shell prints. */
    fun summary(items: List<Item>): String {
        val attention = attention(items)
        if (attention.isEmpty()) return "All green."
        val never = attention.filter { it.kind == Kind.NEVER_EARLY }
        val others = attention.filter { it.kind != Kind.NEVER_EARLY }
        val parts = mutableListOf<String>()
        parts += "${never.size} never-early failure${if (never.size == 1) "" else "s"}" +
            (if (never.isEmpty()) "" else " (${named(never, ", ")})")
        if (others.isNotEmpty()) {
            val counts = others.groupingBy { it.kind }.eachCount()
            val kinds = Kind.entries.filter { counts.containsKey(it) }.joinToString(", ") { "${counts.getValue(it)} ${it.heading.lowercase()}" }
            parts += "${others.size} other item${if (others.size == 1) "" else "s"} ($kinds)"
        }
        return parts.joinToString(", ") + "."
    }

    /**
     * The whole report. [fetched] says whether a fetch ran today; [changed] whether the attention
     * set differs from the one last notified (null when nothing needs attention); [partial] names
     * what a partial run was limited to (its report stands beside the day's, never as it).
     */
    fun render(date: LocalDate, items: List<Item>, backup: String?, fetched: Boolean, changed: Boolean? = null, partial: String? = null): String = buildString {
        appendLine("# Taqwa monitor — $date${if (partial != null) " (partial: $partial)" else ""}")
        appendLine()
        append("**Summary.** ")
        if (partial != null) append("Partial run ($partial): only what it covers is listed, the notified attention set and latest.md are untouched. ")
        append(summary(items))
        if (changed == true) append(" The attention set changed since the last notification.")
        if (changed == false) append(" Nothing new since the last notification.")
        if (!fetched) append(" No fetch this run: the tables checked are the ones already held.")
        if (backup != null) append(" $backup")
        appendLine()
        val attention = attention(items)
        if (attention.isNotEmpty()) {
            appendLine()
            appendLine("## Needs attention")
            index(items)?.let {
                appendLine()
                appendLine(it)
            }
            attention.forEachIndexed { i, item ->
                appendLine()
                appendLine("### ${i + 1}. ${item.kind.heading} — ${item.title}")
                for (line in item.details) appendLine("- $line")
                if (item.next != null) {
                    appendLine()
                    appendLine("**Next:** ${item.next}")
                }
            }
        }
        val informational = items.filter { it.kind.tier == 6 }.sortedWith(compareBy({ it.kind }, { it.title }))
        if (informational.isNotEmpty()) {
            appendLine()
            appendLine("## Informational")
            for (item in informational) {
                val since = item.since?.let { " (since $it)" }.orEmpty()
                appendLine("- ${item.kind.heading}$since: ${item.title}")
                for (line in item.details) appendLine("    - $line")
            }
        }
        val green = items.filter { it.kind.tier == 7 }
        appendLine()
        appendLine("## Green")
        if (green.isEmpty()) appendLine("- nothing else to report")
        for (item in green.filter { it.kind != Kind.CHECKED_FINE }) {
            val head = if (item.kind == Kind.GREEN) item.title else "${item.kind.heading}: ${item.title}"
            appendLine("- $head")
            for (line in item.details) appendLine("    - $line")
        }
        val checked = green.filter { it.kind == Kind.CHECKED_FINE }
        if (checked.isNotEmpty()) {
            val bySource = checked.groupingBy { it.title.substringBefore('/') }.eachCount()
            appendLine(
                "- ${Kind.CHECKED_FINE.heading}: ${checked.size} held table${if (checked.size == 1) "" else "s"} " +
                    "(${bySource.entries.joinToString(", ") { (source, n) -> "$source $n" }})",
            )
        }
        if (backup != null) {
            appendLine()
            appendLine("## Backup")
            appendLine(backup)
        }
    }

    /** The most characters an issue body (GitHub refuses 65,536) or a step summary may hold, the pointer included. */
    const val ISSUE_LIMIT = 60_000

    /**
     * The issue's body (the cloud run): the summary and the never-early index, then each attention
     * item's title, cut to [ISSUE_LIMIT] characters and always ending with the pointer to the full
     * report file in the archive repository.
     */
    fun issueBody(date: LocalDate, items: List<Item>, partial: String? = null): String {
        val pointer = "\nThe full report is `monitor/latest.md` in the archive repository (restricted: it quotes dates, counts and minutes)."
        val body = buildString {
            appendLine("**Taqwa monitor, $date.** ${if (partial != null) "Partial run ($partial): " else ""}${summary(items)}")
            index(items)?.let {
                appendLine()
                appendLine(it)
            }
            val attention = attention(items)
            if (attention.isNotEmpty()) {
                appendLine()
                attention.forEachIndexed { i, item -> appendLine("${i + 1}. ${item.kind.heading} — ${item.title}") }
            }
        }
        val room = ISSUE_LIMIT - pointer.length - 2
        val cut = if (body.length <= room) body else body.take(room).substringBeforeLast('\n') + "\n… (cut: the rest is in the report)\n"
        return cut + pointer + "\n"
    }
}
