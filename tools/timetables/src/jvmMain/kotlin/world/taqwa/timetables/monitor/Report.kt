package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate

/**
 * What one run of the monitor found, classified (brief P, item 4): a kind that needs attention, or
 * one that is green. [attention] decides the exit code and the notification; [low] marks an item
 * the report lists last (a manual source whose next edition is due).
 */
enum class Kind(val attention: Boolean, val heading: String) {
    /** An early start, a late end or a cell over its late limit: in a fetched table, the gate or a survey. */
    EARLY_OR_LATE(true, "Early or late"),

    /** A fetcher failed, or a table could not be read or checked. */
    FETCH_BROKEN(true, "Fetch broken"),

    /** A built-in data horizon inside its window (spec §5, brief P item 5). */
    HORIZON(true, "Horizon"),

    /** A source fetched by hand whose next edition was expected by now. */
    MANUAL_DUE(true, "Manual source due"),

    /** A table not held before, checked and fine: a gate row is ready to paste. */
    NEW_TABLE_FINE(false, "New table, fine"),

    /** A held table whose content changed, checked and fine. */
    CHANGED_FINE(false, "Changed table, fine"),

    /** Nothing to do: a line for the record. */
    GREEN(false, "Green"),
}

/** One finding: its [kind], a one-line [title], the [details] under it and what to do [next]. */
data class Item(
    val kind: Kind,
    val title: String,
    val details: List<String> = emptyList(),
    val next: String? = null,
    val low: Boolean = false,
)

/**
 * The report (brief P, item 6): first a one-paragraph summary, then each item that needs attention
 * with what to do next, then the green lines, then the backup reminder.
 */
object Report {

    /** The summary paragraph's first sentence, also the line the shell prints. */
    fun summary(items: List<Item>): String {
        val attention = items.filter { it.kind.attention }
        if (attention.isEmpty()) return "All green."
        val counts = attention.groupingBy { it.kind }.eachCount()
        val parts = Kind.entries.filter { it.attention && counts.containsKey(it) }
            .joinToString(", ") { "${counts.getValue(it)} ${it.heading.lowercase()}" }
        return "${attention.size} item${if (attention.size == 1) "" else "s"} need${if (attention.size == 1) "s" else ""} attention: $parts."
    }

    fun render(date: LocalDate, items: List<Item>, backup: String?, fetched: Boolean): String = buildString {
        appendLine("# Taqwa monitor — $date")
        appendLine()
        append("**Summary.** ").append(summary(items))
        if (!fetched) append(" No fetch this run: the tables checked are the ones already held.")
        if (backup != null) append(" $backup")
        appendLine()
        val attention = items.filter { it.kind.attention }.sortedBy { it.low }
        if (attention.isNotEmpty()) {
            appendLine()
            appendLine("## Needs attention")
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
        val green = items.filter { !it.kind.attention }
        appendLine()
        appendLine("## Green")
        if (green.isEmpty()) appendLine("- nothing else to report")
        for (item in green) {
            val head = if (item.kind == Kind.GREEN) item.title else "${item.kind.heading}: ${item.title}"
            appendLine("- $head")
            for (line in item.details) appendLine("    - $line")
        }
        if (backup != null) {
            appendLine()
            appendLine("## Backup")
            appendLine(backup)
        }
    }
}
