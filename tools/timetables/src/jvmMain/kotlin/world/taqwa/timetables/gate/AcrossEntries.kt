package world.taqwa.timetables.gate

import world.taqwa.timetables.ProofTotals
import java.io.File
import java.util.BitSet

/**
 * Ruling R214 (the review round of 14 Nov 2026): the two totals the "How Taqwa checks" page prints over every
 * timetable, counted once across the entries. A stamp's `placeDays` are the entry's own (each of its tables' days once),
 * and true of the entry; summed over the stamps they counted a printed day once for every entry that reads its table: a
 * cautious entry reads its members' tables, a region's default the tables of the authorities inside it, and on 14
 * November some 79,000 of the 909,501 place-days summed were such second readings. And the count of "published tables"
 * took every table file's publication, the tables the archive worked out among them.
 *
 * - **A publication belongs to one entry** ([owner]): of the entries whose rows read it ([ProofTotals.tableKey], so a
 *   later capture is the same publication), the first by [ORDER]: the authority's own entry before a cautious entry
 *   reading it as a member's, and either before a region's default; then by id.
 * - **A day another entry counts is shared** ([shared]): a day of a row whose publication an entry earlier in that order
 *   also reads, on a date one of that entry's rows holds. The stamp writes them as `sharedDays`, and the page's total
 *   is the sum of `placeDays - sharedDays`. The days a later reader holds beyond the earlier one's stay its own.
 * - **A published table is one an authority printed** ([tables], the stamp's `tables`): the publications an entry
 *   owns, less those every file of which says in its head that it was worked out ([DERIVED]: a town's table made of the
 *   capital's plus the town's printed minutes, a perpetual table assigned to a year's dates), and less those every
 *   printed line of which another publication prints too (one table held under two folders, a month a year's table
 *   holds), and less those every file of which repeats another table day for day under a later year's dates ([Repeats],
 *   ruling R299: the Kyrgyz Muftiate's 2027 calendars, its 2026 ones again). A perpetual table held only as its mappings
 *   onto years is so counted nowhere, and two towns an authority prints the same minutes for are one: the figure can
 *   understate, never overstate.
 *
 * Read off the whole gate and the archive, never the rows of one run: a gate over one entry writes the same figures
 * for it as the whole gate does. [days] and [file] are null without the archive, and then nothing is shared or counted.
 */
class AcrossEntries(
    rows: List<GateRow>,
    days: ((GateRow) -> List<BitSet>?)? = null,
    file: ((String) -> File)? = null,
    /** Ruling R299: the tables that repeat another day for day ([Repeats]), each counted once, as its original. */
    repeats: Repeats? = null,
) {
    /** By publication, the entries reading it in [ORDER], each with the days its rows hold. */
    private val readers = HashMap<String, List<Pair<String, BitSet>>>()
    private val tables = HashMap<String, Int>()

    init {
        val byKey = LinkedHashMap<String, MutableList<GateRow>>()
        for (row in rows) {
            if (row.promiseOnly || row.endsOnly) continue
            byKey.getOrPut(ProofTotals.tableKey(row.path) ?: continue) { mutableListOf() } += row
        }
        val owners = byKey.mapValues { (_, of) -> of.minWith(ORDER).entry }
        if (days != null) {
            for ((key, of) in byKey) {
                val entries = of.sortedWith(ORDER).map { it.entry }.distinct()
                if (entries.size < 2) continue
                readers[key] = entries.map { entry ->
                    entry to BitSet().also { on -> for (row in of) if (row.entry == entry) days(row)?.forEach(on::or) }
                }
            }
        }
        if (file != null) {
            val lines = HashMap<String, Set<Long>>()
            val derived = HashSet<String>()
            for ((key, of) in byKey) {
                val printed = HashSet<Long>()
                var worked = true
                for (path in of.map { it.path }.distinct()) {
                    val text = runCatching { file(path).readLines() }.getOrDefault(emptyList())
                    if (text.none { it.startsWith("#") && DERIVED.containsMatchIn(it) }) worked = false
                    for (line in text) if (line.isNotBlank() && !line.startsWith("#")) printed += fingerprint(line)
                }
                lines[key] = printed
                if (worked) derived += key
            }
            // By printed line, the publications holding it: a publication is another's when every line of it is there too.
            val holding = HashMap<Long, MutableList<String>>()
            for ((key, printed) in lines) if (key !in derived) printed.firstOrNull()?.let { holding.getOrPut(it) { mutableListOf() } }
            for ((key, printed) in lines) if (key !in derived) for (line in printed) holding[line]?.add(key)
            // Ruling R299: a publication every file of which repeats another table day for day is that table again.
            val repeated = if (repeats == null) emptySet() else byKey.filterValues { of ->
                val mine = of.filter { it.entry == owners.getValue(ProofTotals.tableKey(it.path)!!) }
                mine.isNotEmpty() && mine.all(repeats::isRepeat)
            }.keys
            for ((key, printed) in lines) {
                if (key in derived || key in repeated || printed.isEmpty()) continue
                val within = holding[printed.first()].orEmpty().any { other ->
                    val theirs = lines.getValue(other)
                    other != key && theirs.containsAll(printed) && (theirs.size > printed.size || other < key)
                }
                if (!within) tables.merge(owners.getValue(key), 1, Int::plus)
            }
        }
    }

    /** The entry [row]'s publication is counted at; null for a row that counts none (promise-only, ends alone, a side file). */
    fun owner(row: GateRow): String? = if (row.promiseOnly || row.endsOnly) null else ProofTotals.tableKey(row.path)?.let { key ->
        readers[key]?.first()?.first ?: row.entry
    }

    /** Whether [row]'s day [epochDay] is one an entry before the row's own already counts ([ORDER]). */
    fun shared(row: GateRow, epochDay: Int): Boolean {
        val of = readers[ProofTotals.tableKey(row.path) ?: return false] ?: return false
        return of.takeWhile { it.first != row.entry }.any { it.second.get(epochDay) }
    }

    /** The published tables counted at [entry]: an authority's own, each once across the entries. */
    fun tables(entry: String): Int = tables[entry] ?: 0

    companion object {
        /**
         * The order an entry takes a publication in: the entry whose own table it is, then a cautious entry reading it as
         * a member's, then a region's default (`default.…`); and by id, so the same whatever order the files are read in.
         */
        val ORDER: Comparator<GateRow> = compareBy<GateRow> {
            when {
                it.entry.startsWith("default.") -> 2
                it.member != null -> 1
                else -> 0
            }
        }.thenBy { it.entry }

        /** What a worked-out table's head says of it: the archive's own word, or that a perpetual table was laid on a year. */
        val DERIVED = Regex("""\bDERIVED\b|: derived, |mapped onto \d{4}|assigned to \d{4}""")

        /** A printed line as 64 bits: two hashes of it, so that two lines are one only when they are the same. */
        private fun fingerprint(line: String): Long {
            val text = line.trim()
            var other = 1125899906842597L
            for (c in text) other = 31 * other + c.code
            return (text.hashCode().toLong() shl 32) xor other
        }
    }
}
