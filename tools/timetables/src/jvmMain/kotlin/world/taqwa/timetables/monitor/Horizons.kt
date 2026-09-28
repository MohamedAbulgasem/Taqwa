package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.prayer.ClockChange
import world.taqwa.app.prayer.ClockChanges
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import world.taqwa.timetables.Json
import java.io.File

/** One entry's stamp as the horizons read it: statistics only (`official/stamps/<entry>.json`). */
data class StampSummary(
    val entryId: String,
    /** The stamp's `class` split on `/`: "A", "B", "C", "D (authority)", "D (none)". */
    val classes: Set<String>,
    val first: LocalDate?,
    val last: LocalDate?,
    val broken: Int,
) {
    val isAOrB: Boolean get() = "A" in classes || "B" in classes

    companion object {
        /** Every `*.json` stamp in [dir]. */
        fun load(dir: File): List<StampSummary> =
            dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.map { file ->
                @Suppress("UNCHECKED_CAST")
                val stamp = Json.parse(file.readText()) as Map<String, Any?>
                StampSummary(
                    entryId = stamp["entry"] as? String ?: file.nameWithoutExtension,
                    classes = (stamp["class"] as? String).orEmpty().split('/').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    first = (stamp["first"] as? String)?.let(LocalDate::parse),
                    last = (stamp["last"] as? String)?.let(LocalDate::parse),
                    broken = (stamp["broken"] as? Long ?: 0L).toInt(),
                )
            }.orEmpty()
    }
}

/**
 * A published calendar the engine leans on for a year at a time: on and after [fromMonth]/[fromDay]
 * of a year Y, [entryId]'s proof must run through the end of Y + 1, else the next year's table is
 * wanted ([what], and the usual fix [fix]).
 */
data class NextYearRule(val entryId: String, val fromMonth: Int, val fromDay: Int, val what: String, val fix: String)

/**
 * Every built-in data horizon (spec §5, brief P item 5), attention only inside its window:
 *
 * - Umm al-Qura's built-in dates ([UmmAlQuraDates.lastDate]) from six months before they end;
 * - each clock change's `until` ([ClockChanges.table]) from eight weeks before it until it passes;
 * - London Unified's next year not held by 1 December, IRN's (Tromsø's 2027 calendar, ruling R82)
 *   by 1 November ([NEXT_YEAR]);
 * - any stamp whose `last` (About's `provenThrough`) is older than twelve months;
 * - from eight weeks before each Ramadan (the tabular calendar) to its end, the A/B entries whose
 *   proof does not cover it (spec §5's Ramadan release rule).
 */
object Horizons {

    val NEXT_YEAR: List<NextYearRule> = listOf(
        NextYearRule(
            "gb.london.lupt", 12, 1, "London Unified's next year",
            "fetch the East London Mosque's table for the coming year as London Prayer Times republishes it " +
                "(free for all use, ruling R33), add gate rows for it in official/gate/gb-london-lupt.tsv " +
                "(split test), re-run the gate; where its gaps changed, rederive LondonUnifiedCurve.",
        ),
        NextYearRule(
            "no.irn", 11, 1, "IRN's next-year calendars (Tromsø's rule, ruling R82)",
            "fetch Islamsk Råd Norge's calendars for the coming year (Oslo, Trondheim, Tromsø; bonnetid.info, " +
                "restricted: archive only), add gate rows in official/gate/no-irn.tsv, re-run the gate; " +
                "check Tromsø's rule dates against IrnArctic and narrow the widened window (ruling R82).",
        ),
    )

    private const val WEEKS_8 = 56
    private const val RAMADAN_DAYS = 29

    fun check(
        today: LocalDate,
        stamps: List<StampSummary>,
        uqLast: LocalDate = UmmAlQuraDates.lastDate,
        changes: List<ClockChange> = ClockChanges.table,
        rules: List<NextYearRule> = NEXT_YEAR,
        /**
         * Entries a manual source in `sources.tsv` watches, with the date its next edition is
         * expected: a proof older than twelve months stays quiet for them until that date passes
         * (the manual-due item takes over), so an authority that prints one table a year is not
         * nagged about every week.
         */
        deferred: Map<String, LocalDate> = emptyMap(),
    ): List<Item> {
        val items = mutableListOf<Item>()
        val byId = stamps.associateBy { it.entryId }

        if (today >= uqLast.plus(-6, DateTimeUnit.MONTH)) {
            items += Item(
                Kind.HORIZON,
                "Umm al-Qura's built-in dates end on $uqLast",
                listOf("UmmAlQuraDates covers Ramadan and the lag dates through $uqLast; after it every day is taken as a lag date (ruling R71)."),
                "fetch GetPrayerByYear for the years after $uqLast (the sa-ummalqura fetcher's raw JSON holds the Hijri month of every " +
                    "day), regenerate UmmAlQuraDates (Ramadan dates: Hijri month 9; lag dates: rows equal to the day before at every point), " +
                    "run the gate on the new years and commit.",
            )
        }

        for (change in changes) {
            if (today >= change.until.plus(-WEEKS_8, DateTimeUnit.DAY) && today < change.until) {
                items += Item(
                    Kind.HORIZON,
                    "The clock-change table's entry '${change.id}' expires on ${change.until}",
                    listOf("ClockChanges.table stops checking phones' zone data for ${change.zones.joinToString()} on that date."),
                    "decide whether the entry is still needed (is old zone data still around?) and either extend `until` or leave it to expire; " +
                        "check tzdata for any new change to add.",
                )
            }
        }

        for (rule in rules) {
            val from = LocalDate(today.year, rule.fromMonth, rule.fromDay)
            if (today < from) continue
            val needed = LocalDate(today.year + 1, 12, 31)
            val stamp = byId[rule.entryId]
            val last = stamp?.last
            if (last == null || last < needed) {
                items += Item(
                    Kind.HORIZON,
                    "${rule.what} is not held (${rule.entryId}${if (last == null) " has no stamp" else " is proven through $last"})",
                    listOf("Wanted by $from: a proof through $needed."),
                    rule.fix,
                )
            }
        }

        val staleBefore = today.plus(-12, DateTimeUnit.MONTH)
        for (stamp in stamps) {
            val last = stamp.last ?: continue
            val until = deferred[stamp.entryId]
            if (last < staleBefore && (until == null || today >= until)) {
                items += Item(
                    Kind.HORIZON,
                    "${stamp.entryId}'s proof is older than twelve months (through $last)",
                    listOf("About states \"not yet checked after $last\" for it."),
                    "fetch the authority's newest table (add a fetcher if it has none), add gate rows and re-run the gate; " +
                        "where the authority prints one table a year, list it as a manual source in sources.tsv naming this entry, " +
                        "with next_expected the month it is due: this line then waits for that date.",
                )
            }
        }

        val ramadan = nextRamadan(today)
        val ramadanEnd = ramadan.plus(RAMADAN_DAYS, DateTimeUnit.DAY)
        if (today >= ramadan.plus(-WEEKS_8, DateTimeUnit.DAY) && today <= ramadanEnd) {
            val short = stamps.filter { it.isAOrB }.filter { s ->
                val first = s.first
                val last = s.last
                first == null || last == null || first > ramadan || last < ramadanEnd
            }
            if (short.isNotEmpty()) {
                items += Item(
                    Kind.HORIZON,
                    "Ramadan begins about $ramadan and ${short.size} A/B entr${if (short.size == 1) "y's proof does" else "ies' proofs do"} not cover it",
                    short.map { "${it.entryId}: proven ${it.first ?: "?"}..${it.last ?: "?"}" },
                    "the Ramadan release refuses to build unless every A/B entry's proof covers Ramadan or the gate has demoted it (spec §5): " +
                        "fetch each authority's table for those dates (most publish the Ramadan imsakiya a few weeks before), add gate rows " +
                        "(split test) and re-run the gate.",
                )
            }
        }
        return items
    }

    /**
     * The first day of the tabular Ramadan that [today] is in or before: the current one when
     * [today] falls inside it (within [RAMADAN_DAYS] of its start), else the next.
     */
    fun nextRamadan(today: LocalDate): LocalDate {
        var date = today.plus(-RAMADAN_DAYS, DateTimeUnit.DAY)
        repeat(400) {
            val hijri = TabularHijriCalendar.fromGregorian(date)
            if (hijri.month == 9 && hijri.day == 1) return date
            date = date.plus(1, DateTimeUnit.DAY)
        }
        error("no Ramadan within 400 days of $today")
    }
}
