package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.registry.data.ProofStamp
import world.taqwa.app.prayer.engine.registry.data.ProofStamps

/**
 * The days the gate compared an authority's timetable on, by entry and unit, as the site's proven rule reads them
 * (`Proven.verdict` in tools/timetables, rulings R101, R115 and R116): the unit's own runs where the stamp checks the
 * entry by unit, the entry's runs otherwise. The second audit's G9 (ruling R275): a checked label is a claim about a
 * day, so the app asks this of the day it shows, and a day no table was compared on is not shown as checked, as the
 * site shows no page for it. Read once from [ProofStamps], which is generated from the committed stamps.
 *
 * A run is days in a row ([blocks]): a lone sampled day (Tunisia's every third day, Oman's 1st and 15th of 2029) is a
 * day the gate compared, but the days around it were not, so it neither makes the place checked on them nor flips the
 * label for one day in three; About's "checked through" date reads the same runs (ruling R277, the second audit's G13).
 */
object CheckedDays {
    /** The checked runs of each stamped entry, and of each unit of an entry checked by unit, keyed "entry" and "entry unit". */
    private val runs: Map<String, List<ClosedRange<LocalDate>>> by lazy {
        buildMap {
            for (stamp in ProofStamps.byEntry.values) {
                put(stamp.entryId, blocks(stamp.checked))
                for (unit in stamp.worstLateByUnit.keys) put(key(stamp.entryId, unit), blocks(stamp.checkedByUnit[unit] ?: stamp.checked))
            }
        }
    }

    /**
     * Whether [entryId]'s timetable was compared on [date] where [unitId] is the place's unit: never where the entry has
     * no stamp, and where its stamp checks it by unit, never outside a unit the stamp holds (the site's page is held
     * in both cases); otherwise whether one of the runs holds the date.
     */
    fun covers(entryId: String, unitId: String?, date: LocalDate): Boolean {
        val stamp: ProofStamp = ProofStamps.of(entryId) ?: return false
        val key = if (stamp.worstLateByUnit.isEmpty()) entryId else key(entryId, unitId ?: return false)
        return runs[key]?.any { date in it } ?: false
    }

    private fun key(entryId: String, unitId: String) = "$entryId $unitId"

    /**
     * [runs] as a stamp writes them ("2026-09-25..2026-11-01", or one date) read into runs of days in a row: runs that
     * touch are one, and a run of a single day is passed over.
     */
    fun blocks(runs: List<String>): List<ClosedRange<LocalDate>> {
        val spans = runs.map { LocalDate.parse(it.substringBefore("..")) to LocalDate.parse(it.substringAfter("..")) }.sortedBy { it.first }
        val merged = mutableListOf<Pair<LocalDate, LocalDate>>()
        for ((start, end) in spans) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.second.plus(1, DateTimeUnit.DAY)) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, end)
            } else {
                merged += start to end
            }
        }
        return merged.filter { it.first < it.second }.map { it.first..it.second }
    }
}
