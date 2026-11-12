package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.ClockChange
import world.taqwa.app.prayer.ClockChanges
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import world.taqwa.timetables.gate.Ramadan
import world.taqwa.timetables.gate.StampSummary
import world.taqwa.timetables.gate.lastDay

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
 * - any stamp whose `last` (ProofStamps' `lastChecked`) is older than twelve months, and any unit
 *   whose own last checked day is, while its entry's is not (ruling R145);
 * - from eight weeks before each Ramadan (the tabular calendar, [Ramadan]) to its end, the A/B
 *   entries with a unit whose checked runs of Fajr, the end of eating or Maghrib do not hold every
 *   day of it (spec §5's Ramadan release rule, `checkStamps -Pramadan=<year>`): the runs, unit by
 *   unit and event by event, never the entry's envelope `first`..`last`, which hid Muscat's hole of
 *   ten years (ruling R145), nor the unit's days as a whole, which hid Pretoria's missing end of
 *   eating (ruling R148).
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
                key = "horizon:ummalqura-dates",
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
                    key = "horizon:clock-change:${change.id}",
                )
            }
        }

        for (rule in rules) {
            // The current year is always wanted; from the rule's date the next one too (review I1: a
            // rule that only asked for next year went silent every 1 January).
            val from = LocalDate(today.year, rule.fromMonth, rule.fromDay)
            val needed = if (today >= from) LocalDate(today.year + 1, 12, 31) else LocalDate(today.year, 12, 31)
            val wantedBy = if (today >= from) from else LocalDate(today.year - 1, rule.fromMonth, rule.fromDay)
            val stamp = byId[rule.entryId]
            val last = stamp?.last
            if (last == null || last < needed) {
                items += Item(
                    Kind.HORIZON,
                    "${rule.what} is not held (${rule.entryId}${if (last == null) " has no stamp" else " is proven through $last"})",
                    listOf("Wanted by $wantedBy: a proof through $needed."),
                    rule.fix,
                    key = "horizon:${rule.entryId}:next-year",
                )
            }
        }

        val staleBefore = today.plus(-12, DateTimeUnit.MONTH)
        val fetchNewest = "fetch the authority's newest table (add a fetcher if it has none), add gate rows and re-run the gate; " +
            "where the authority prints one table a year, list it as a manual source in sources.tsv naming this entry, " +
            "with next_expected the month it is due: this line then waits for that date."
        for (stamp in stamps) {
            val last = stamp.last ?: continue
            val until = deferred[stamp.entryId]
            if (until != null && today < until) continue
            if (last < staleBefore) {
                // The whole entry is stale, its units with it: one line.
                items += Item(
                    Kind.HORIZON,
                    "${stamp.entryId}'s proof is older than twelve months (through $last)",
                    listOf("About states \"not yet checked after $last\" for it."),
                    fetchNewest,
                    key = "horizon:stale:${stamp.entryId}",
                )
                continue
            }
            // Ruling R145: a unit whose own rows stopped a year ago is stale though the entry's envelope is
            // fresh; About and its city page give a place the end of its unit's own last run (ruling R115).
            for (unit in stamp.units) {
                val unitLast = unit.checked.lastDay ?: continue
                if (unitLast < staleBefore) {
                    items += Item(
                        Kind.HORIZON,
                        "${stamp.entryId}'s unit ${unit.id} (${unit.name}) is older than twelve months (through $unitLast)",
                        listOf("The entry's proof runs through $last; About and the city page state \"not yet checked after $unitLast\" at this unit's places."),
                        fetchNewest.replace("the authority's newest table", "the authority's newest table for this unit's point"),
                        key = "horizon:stale:${stamp.entryId}/${unit.id}",
                    )
                }
            }
        }

        val ramadan = Ramadan.nextFrom(today)
        if (ramadan.isNear(today)) {
            val short = stamps.filter { it.isAOrB }.mapNotNull { s -> s.ramadanGaps(ramadan).takeIf { it.isNotEmpty() }?.let { s to it } }
            if (short.isNotEmpty()) {
                items += Item(
                    Kind.HORIZON,
                    "Ramadan ${ramadan.hijriYear} begins about ${ramadan.first} and ${short.size} A/B entr${if (short.size == 1) "y's proof does" else "ies' proofs do"} " +
                        "not cover it from ${ramadan.guardFirst} through ${ramadan.guardLast} (the month and a day each side)",
                    short.map { (s, gaps) -> "${s.entryId}: ${s.describe(ramadan, gaps)}" },
                    "the Ramadan build refuses to build unless every A/B unit's Fajr, end of eating and Maghrib were checked on every day " +
                        "of Ramadan or the gate has demoted the entry (./gradlew -p tools/timetables checkStamps -Pramadan=${ramadan.hijriYear}, " +
                        "spec §5): fetch each authority's table for those dates (most publish the Ramadan imsakiya a few weeks before), " +
                        "add gate rows (split test) and re-run the gate.",
                    key = "horizon:ramadan:${ramadan.first}",
                )
            }
        }
        return items
    }
}
