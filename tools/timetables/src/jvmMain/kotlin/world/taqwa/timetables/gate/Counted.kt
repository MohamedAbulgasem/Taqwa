package world.taqwa.timetables.gate

import java.util.BitSet

/**
 * Ruling R214 (the audit of 13 Nov 2026, findings F17 and F20): what a gate row adds to the figures a
 * reader is shown (a stamp's `places`, `placeDays`, `ramadanDays` and held-out days, and so About's
 * "days at places" and the site's checks page), read off the gate files and the days their tables hold, and the same in
 * whatever order they are read.
 *
 * - **A published table's days are counted once, where it is first read.** The row that counts a
 *   table's days is the first row of the entry that reads its starts, a row of a `-reach.tsv` file only
 *   where no other file reads the table ([isOwn]). Every other row reading the same table (at a point of
 *   the unit's reach, at the app's own point, in a town the one national table also serves) is a
 *   **replay**: checked like any row, early starts and late ends breaking the gate as anywhere, and
 *   adding no place, no place-day and no Ramadan day to a figure shown.
 * - **A table the margins saw is a fit table wherever it is read.** A cell is a fit cell when a `split=fit` row of the
 *   entry reads the same event from the same table file, or a gate file's `@fitted` line names it ([FittedOn]: the cells
 *   of a test row that a margin was widened to, or read off); a reach row replaying a fitted table is in-sample, whatever
 *   its own split column says. By event, since a row reading ends alone (JAKIM's imsak) can be fitted while the table's
 *   starts are held out, and the other way round.
 * - **A day the margins saw is a fit day in whichever file it is printed** (the review round of 14 Nov 2026). A table
 *   fetched again lies at a new path (the monitor pins every capture under its own date), and under the rule above alone
 *   the second copy of a fitted table read as held out: 7,524 Fajr days of Diyanet's European cities and 399 days of
 *   FIDS's Zürich were stamped so. So a cell is held out only when, besides the rule above, no fit cell of the same
 *   event stands on the same date at the same place ([scope]: the entry, a cautious row's member, and the row's unit or,
 *   where it names none, its point). The days a later capture adds beyond the fitted one stay held out, which is why this
 *   is by date and not by the table's name.
 * - **A year printed again is the same table** (ruling R299, [Repeats]): where a table repeats another day for day, a fit
 *   cell of either is a fit cell of both.
 * - **A cautious entry's member tables are fit where the member's are.** A cautious row reads a member's table, and the
 *   day it is checked on is built from the member's margins: its cell is a fit cell where the member entry's own cell of
 *   the same table file is (a `split=fit` row of the member's, or a `@fitted` line naming the member).
 *
 * And as a table's days, so its cells: the stamp's held-out tally counts a held-out cell once, at the first row reading
 * that event from the table ([counts]), and once however many captures print the day (`EntryStats`); a unit's own
 * held-out days (what the Ramadan guard reads) take it at whichever of the unit's rows read it.
 *
 * [days] gives, for a row, the days each of its columns holds a time on, as epoch days (null where the table is not
 * held: without the archive nothing is compared, and the rule by file stands alone, as in a test of the gate files).
 *
 * Promise-only rows ([GateRow.promiseOnly]) are in no stamp and so in no rule here.
 */
class Counted(
    rows: List<GateRow>,
    fitted: List<FittedOn> = emptyList(),
    days: ((GateRow) -> List<BitSet>?)? = null,
    /**
     * The unit a row that names none stands in, where its point resolves to one ([scope]); null for none. The second
     * trust audit's G27 (14 Nov 2026): İstanbul's, Ankara's and Tekirdağ's tables re-read at the app's point with no unit
     * named were keyed by their point, apart from the fit rows of their units, and passed the Ramadan guard as held out
     * on the very days their margins were fitted on.
     */
    private val unitOf: ((GateRow) -> String?)? = null,
    /** Ruling R299: the tables that repeat another day for day ([Repeats]); a fit cell of one is a fit cell of each. */
    private val repeats: Repeats? = null,
) {
    constructor(
        manifest: GateManifest,
        days: ((GateRow) -> List<BitSet>?)? = null,
        unitOf: ((GateRow) -> String?)? = null,
        repeats: Repeats? = null,
    ) : this(manifest.rows, manifest.fitted, days, unitOf, repeats)

    private val own = HashMap<String, GateRow>()
    private val ownCells = HashMap<String, GateRow>()
    private val fit = HashSet<String>()

    /** By [scope] and event, the days a fit cell stands on: each a column of a table a fit row reads, shared, never copied. */
    private val fitDays = HashMap<String, MutableList<BitSet>>()

    init {
        for (row in rows) {
            if (row.promiseOnly) continue
            val table = table(row)
            for (event in row.columns.flatten()) {
                val cell = "$table|${event.key}"
                if (row.split == Split.FIT || fitted.any { event in it.events && it.names(row) }) fit += cell
                if (first(ownCells[cell], row)) ownCells[cell] = row
            }
            if (!row.endsOnly && first(own[table], row)) own[table] = row
        }
        // Ruling R299: one table, however many years print it. Where any table of a family is fit for an event, all are.
        if (repeats != null) {
            for (family in repeats.families.values) {
                val events = fit.mapNotNullTo(HashSet()) { cell -> cell.substringAfterLast('|').takeIf { cell.substringBeforeLast('|') in family } }
                for (table in family) for (event in events) fit += "$table|$event"
            }
        }
        if (days != null) {
            for (row in rows) {
                if (row.promiseOnly) continue
                val events = row.columns.map { column -> column.filter { fitByFile(row, it) } }
                if (events.all { it.isEmpty() }) continue
                val held = days(row) ?: continue
                events.forEachIndexed { column, fitEvents ->
                    val on = held.getOrNull(column) ?: return@forEachIndexed
                    for (event in fitEvents) fitDays.getOrPut("${scope(row)}|${event.key}") { mutableListOf() }.let { if (it.none { had -> had === on }) it += on }
                }
            }
        }
    }

    /** Whether [row] takes [held]'s place: nothing is held yet, or a reach row is and [row] is of another file. */
    private fun first(held: GateRow?, row: GateRow): Boolean = held == null || (held.reach && !row.reach)

    /** Whether [row] is the one its table's days are counted at; false for a replay, an ends-only or a promise-only row. */
    fun isOwn(row: GateRow): Boolean = own[table(row)] === row

    /** Whether [row] is the one its table's cells of [event] are counted at in the held-out tally; false for a replay. */
    fun counts(row: GateRow, event: Event): Boolean = ownCells["${table(row)}|${event.key}"] === row

    /**
     * Whether [row]'s cell of [event] on [epochDay] is held out of the fit: a test row's, of a table no fit row reads that
     * event from, on a day no fit cell of the event stands on at the row's place. Without a day, by the table file alone.
     */
    fun heldOut(row: GateRow, event: Event, epochDay: Int? = null): Boolean =
        row.split == Split.TEST && !fitByFile(row, event) && !(epochDay != null && fitOn(row, event, epochDay))

    /** Whether [row]'s place-day on [epochDay] is held out: a test row none of whose starts is a fit cell that day. */
    fun heldOut(row: GateRow, epochDay: Int? = null): Boolean =
        row.split == Split.TEST && row.columns.flatten().none { it.isStart && (fitByFile(row, it) || (epochDay != null && fitOn(row, it, epochDay))) }

    /** The published tables the rows read, by entry: each (entry, table file) once, a repeated year not again (R299). */
    val tables: Int get() = own.keys.count { key -> repeats?.all?.containsKey(key) != true }

    /**
     * Where a row's cells stand, for the rule by date: the entry, the member whose table a cautious row reads, and the
     * row's unit or, where it names none, the unit its point resolves to ([unitOf]), else its point (empty: the entry's
     * one fixed point). A town of a unit read at its own point is the unit's: its printed day is the unit's table, and a
     * day the unit's margins were fitted on is a fit day there whatever the row's file or split (G27). Where the row reads
     * another table at such a point (a district's beside its unit), a fit day of the unit's makes its cell no held-out
     * cell either: the safe side, fewer held-out days and never more.
     */
    fun scope(row: GateRow): String {
        val where = row.unit ?: unitOf?.invoke(row) ?: if (row.lat != null && row.lon != null) "${row.lat},${row.lon}" else ""
        return "${row.entry}|${row.member.orEmpty()}|$where"
    }

    /** A fit cell by the table file: a fit row of the entry's, a `@fitted` line, or for a cautious row its member's own. */
    private fun fitByFile(row: GateRow, event: Event): Boolean =
        "${table(row)}|${event.key}" in fit || (row.member != null && "${row.member}|${row.path}|${event.key}" in fit)

    private fun fitOn(row: GateRow, event: Event, epochDay: Int): Boolean =
        fitDays["${scope(row)}|${event.key}"]?.any { it.get(epochDay) } == true

    private fun table(row: GateRow): String = "${row.entry}|${row.path}"
}
