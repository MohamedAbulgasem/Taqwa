package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.method.ImsakFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Kemenag's imsak at the kab/kota whose printed imsak the gate does not hold (ruling R246, the review of 14 Nov 2026).
 * The gate reads a printed imsak column at the few kab/kota a table with one is held for; at every other unit the
 * imsak shown is checked against no print, on any day (`checkStamps` names them in a notice and refuses nothing, since
 * no row exists to add a day to). What can be held without a print is the rule, and that it is one rule: every unit
 * counts its imsak the way the units the gate reads do (Kemenag's own: Subuh less 10, counted back from the
 * authority's dawn as an end), so a unit that is not read differs from one that is by its point and its margins, never
 * by how the imsak is made.
 *
 * This is not a printed imsakiyah for those units and does not stand in for one: it holds how the imsak is counted,
 * not that Kemenag's print agrees at each of them.
 */
class KemenagImsakTest {
    private val entry = assertNotNull(Registry.byId("id.kemenag"))
    private val units = assertNotNull(Units.of("id.kemenag")).units

    // Any of Indonesia's three clocks gives the same instants; the nearest one keeps the local date the unit's own.
    private fun AuthorityUnit.place() = Place(
        point.lat,
        point.lon,
        if (point.lon >= 127.5) "Asia/Jayapura" else if (point.lon >= 114.7) "Asia/Makassar" else "Asia/Jakarta",
        "ID",
    )

    @Test
    fun `every unit counts its imsak one way and as an end`() {
        assertTrue(units.isNotEmpty())
        for (unit in units) {
            val method = assertNotNull(unit.method, unit.id)
            assertEquals(10, method.imsakMinutesBeforeFajr, "${unit.id}: Kemenag's imsak is its Subuh less 10")
            assertEquals(ImsakFrom.Dawn, method.imsakFrom, "${unit.id}: counted back from the authority's own dawn")
            assertTrue(method.endOfEatingMarginSeconds <= 0, "${unit.id}: an end of eating only ever moves earlier")
        }
    }

    @Test
    fun `through ramadan 1448 every unit shows an imsak ten minutes before its end of eating and never near the fajr shown`() {
        for (unit in units) {
            val place = unit.place()
            var date = LocalDate(2027, 2, 6)
            while (date <= LocalDate(2027, 3, 11)) {
                val day = DayPipeline.day(entry, place, date)
                val imsak = assertNotNull(day.imsak, "${unit.id} on $date shows no imsak")
                assertTrue(day.endOfEating <= day.fajr, "${unit.id} on $date: eating ends after the Fajr shown")
                assertEquals(day.endOfEating - 10.minutes, imsak, "${unit.id} on $date: the imsak is not the end of eating less 10")
                // The Fajr shown carries Kemenag's ihtiyat and the unit's margin; the imsak never borrows from them.
                assertTrue(day.fajr - imsak >= 10.minutes, "${unit.id} on $date: the imsak is under 10 min before the Fajr shown")
                assertTrue(day.fajr - imsak < 15.minutes, "${unit.id} on $date: the imsak is a quarter of an hour or more before the Fajr shown")
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }
}
