package world.taqwa.timetables

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.registry.CheckedDays
import world.taqwa.app.prayer.engine.registry.data.ProofStamps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * App = site, day by day (rulings R275 and R277, the review of the second audit's G9): the app's [CheckedDays] reads the
 * generated `ProofStamps.kt`, the site's [Proven] the committed stamp files, and both must call the same days checked at
 * the same unit. The site reads a unit's own runs ([Stamp.unitChecked]) where the stamp checks the entry by unit, the
 * entry's otherwise; the app the same runs as generated, with one difference by design: a lone checked day is no run of
 * days in a row, so the app does not show it as checked (R277). So the app's day is the site's checked day that has a
 * checked day beside it. Both sides change only at a run's ends, so every end and the days around it are compared.
 */
class CheckedDaysParityTest {
    private val stamps = Stamp.load(TestPaths.repoRoot.resolve("tools/timetables/official/stamps"))

    @Test
    fun `the app's checked days are the site's checked days of two or more in a row at every unit of every stamp`() {
        var compared = 0
        for ((entry, site) in stamps) {
            val app = ProofStamps.of(entry) ?: continue
            val units = site.units
            assertEquals(units != null, app.worstLateByUnit.isNotEmpty(), "$entry: checked by unit on both sides")
            val keys: List<Pair<String?, Checked>> = units?.keys?.map { it to site.unitChecked(it) } ?: listOf(null to site.checked)
            if (units != null) assertEquals(units.keys, app.worstLateByUnit.keys, "$entry: the same units")
            for ((unit, checked) in keys) {
                val days = checked.runs.flatMap { run ->
                    listOf(run.start, run.endInclusive).flatMap { listOf(it.minus(1, DateTimeUnit.DAY), it, it.plus(1, DateTimeUnit.DAY)) }
                }.toSortedSet()
                for (day in days) {
                    val siteDay = checked.covers(day) &&
                        (checked.covers(day.minus(1, DateTimeUnit.DAY)) || checked.covers(day.plus(1, DateTimeUnit.DAY)))
                    assertEquals(siteDay, CheckedDays.covers(entry, unit, day), "$entry ${unit ?: "(whole)"} on $day")
                    compared++
                }
            }
            // A unit the stamp does not hold is checked on no day, on either side (the site holds its page).
            if (units != null) assertEquals(false, CheckedDays.covers(entry, "not-a-unit-of-$entry", site.last), entry)
        }
        assertTrue(compared > 10_000, "$compared unit-days compared")
    }
}
