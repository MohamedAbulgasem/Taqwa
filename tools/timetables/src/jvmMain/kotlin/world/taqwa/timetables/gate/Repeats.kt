package world.taqwa.timetables.gate

/**
 * Ruling R299 (16 Nov 2026, the proof-honesty entry's open item (6), G29's second half): a day-for-day repeat of one
 * authority's table counts once, everywhere in the proof, and this is the one place that says which tables are repeats.
 *
 * A table **repeats** another when the same entry reads both at the same place (the same member, unit and point) with
 * the same columns, it prints at least [MIN_DAYS] days, and every day it prints is printed the same, column for column,
 * by the other on the same month and day of one earlier year: the Kyrgyz Muftiate's 2027 calendars are its 2026 ones under
 * 2027's dates (its own fetch, so no `DERIVED` head says so; ruling R272 marked them fitted by hand), as every perpetual
 * table mapped onto a later year is (Dubai's, the PA Iftaa's, Jamiat's, Dublin's IFI, Dar al-Fatwa's, Banuri Town's).
 * The table it repeats is its [original]: the first such table, followed back to one that repeats none.
 *
 * Counted once means:
 * - **Published tables** ([AcrossEntries.tables]): a publication every file of which is a repeat is not counted; its
 *   original is (once, where its own entry owns it). A year printed again is no second published table.
 * - **Held out** ([Counted]): where any table of a family (an original and its repeats, [families]) is a fit cell for an
 *   event, by its file or a `@fitted` line, every table of the family is: the margins were fitted on those very printed
 *   numbers, so counting the repeat as held out would credit the same publication twice as a test the fit never saw
 *   (the sun under the later dates is not the same, and its days are still compared against their own dates, below;
 *   only the claim of a held-out test goes). So Ramadan's held-out guard, which reads [Counted], never takes a repeated year
 *   as a held-out Ramadan, and the stamp's held-out days and cells leave it out.
 * - **Its days stay**: each is a date the engine was compared on against what the authority prints for that date, so
 *   it counts in the place-days and the checked runs as any day does (a stamp's `placeDays` never counted tables).
 *
 * [days] gives a row's printed days (null where its table is not held: then nothing is a repeat, as in a run without
 * the archive).
 */
class Repeats(rows: List<GateRow>, days: ((GateRow) -> List<OfficialDay>?)?) {
    /** By table (`entry|path`), the table it repeats, followed back to the first: its family's earliest table. */
    private val original = HashMap<String, String>()

    /**
     * By original (`entry|path`), it and every table joined to it by a repeat: one table, however many years print it (two
     * captures of the first year are both in it where a later year repeats each).
     */
    val families: Map<String, Set<String>>

    init {
        // Every pair (later, earlier) where the later repeats the earlier; a family is each set they join.
        val parent = HashMap<String, String>()
        fun root(x: String): String {
            var at = x
            while (true) {
                val up = parent[at] ?: return at
                if (up == at) return at
                at = up
            }
        }
        val firstDay = HashMap<String, Int>()
        val later = HashSet<String>()
        if (days != null) {
            val compared = HashMap<String, Boolean>()
            val byPlace = rows.filter { !it.promiseOnly }.groupBy { "${it.entry}|${it.member.orEmpty()}|${it.unit.orEmpty()}|${it.lat},${it.lon}|${it.format}|${it.columns}" }
            for (at in byPlace.values) {
                val paths = at.distinctBy { it.path }
                if (paths.size < 2) continue
                for (row in paths) {
                    val mine = days(row)?.takeIf { it.size >= MIN_DAYS } ?: continue
                    for (other in paths) {
                        if (other === row) continue
                        val pair = "${table(row)}>${table(other)}"
                        if (compared.getOrPut(pair) { repeats(mine, days(other).orEmpty()) }) {
                            later += table(row)
                            for (t in listOf(row, other)) firstDay.getOrPut(table(t)) { first(days(t)) }
                            val a = root(table(row))
                            val b = root(table(other))
                            if (a != b) parent[maxOf(a, b)] = minOf(a, b)
                        }
                    }
                }
            }
        }
        val members = firstDay.keys.groupBy(::root)
        families = members.values.associate { family ->
            // The original: the family's earliest table that repeats none, else its earliest (so the same in any order).
            val order = compareBy<String>({ firstDay.getValue(it) }, { it })
            val of = family.filter { it !in later }.minWithOrNull(order) ?: family.minWith(order)
            of to family.toSortedSet()
        }
        for ((of, family) in families) for (t in family) if (t in later) original[t] = of
    }

    /** Whether [row]'s table repeats another ([original]). */
    fun isRepeat(row: GateRow): Boolean = table(row) in original

    /** Whether the table [path] of [entry] repeats another. */
    fun isRepeat(entry: String, path: String): Boolean = "$entry|$path" in original

    /** The table [row]'s repeats (`entry|path`), followed back to one that repeats none; null where it repeats none. */
    fun original(row: GateRow): String? = original[table(row)]

    /** Every repeat, by table (`entry|path`), with its original. */
    val all: Map<String, String> get() = original

    companion object {
        /** The fewest days a table prints for its repeat of another to count: a month's (a lone week could repeat by chance). */
        const val MIN_DAYS = 28

        fun table(row: GateRow): String = "${row.entry}|${row.path}"

        private fun first(days: List<OfficialDay>?): Int = days?.minOfOrNull { it.date.toEpochDays().toInt() } ?: Int.MAX_VALUE

        /**
         * Whether every day of [mine] is printed the same by [other] on the same month and day of one year before its own,
         * for each year [mine] holds.
         */
        fun repeats(mine: List<OfficialDay>, other: List<OfficialDay>): Boolean {
            if (other.isEmpty()) return false
            val byDate = other.groupBy { Triple(it.date.year, it.date.monthNumber, it.date.dayOfMonth) }
            val otherYears = other.map { it.date.year }.toSortedSet()
            for ((year, ofYear) in mine.groupBy { it.date.year }) {
                val earlier = otherYears.filter { it < year }
                if (earlier.none { y -> ofYear.all { d -> byDate[Triple(y, d.date.monthNumber, d.date.dayOfMonth)]?.any { it.minutes == d.minutes } == true } }) {
                    return false
                }
            }
            return true
        }
    }
}
