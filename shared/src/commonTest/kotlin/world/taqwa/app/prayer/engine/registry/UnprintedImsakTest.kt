package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * No promise of an imsak the authority does not print (ruling R188, the owner, 12 Nov 2026): Tunisia's INM prints none in
 * anything held, and the Singapore method outside Singapore has no table of its own that prints one, so the app shows
 * none there, whichever unit or edge the place resolves to, and the fast's one time is the end of eating, never after
 * the Fajr shown. An imsak that is printed stays: MUIS's own entry (its Imsakiah prints it, and the gate reads it) and
 * Brunei's MORA (its page's first column and its printed Taqwim, Suboh − 10, read by the gate) — the ruling's brief
 * took Brunei's for unprinted, and the print says otherwise.
 */
class UnprintedImsakTest {
    private fun TimetableMethod.withAlongside(): List<TimetableMethod> = listOf(this) + alongside.flatMap { it.method.withAlongside() }

    private val unprinted = listOf("tn.inm", "other.singapore")

    // One place at a unit's own point and one beyond the units, for each entry that has units.
    private val places = mapOf(
        "tn.inm" to listOf(Place(36.8065, 10.1815, "Africa/Tunis", "TN"), Place(33.9197, 8.1335, "Africa/Tunis", "TN")),
        "other.singapore" to listOf(Place(-33.9249, 18.4241, "Africa/Johannesburg", "ZA")),
    )

    @Test
    fun `no method of theirs counts an imsak`() {
        for (id in unprinted) {
            val entry = assertNotNull(Registry.byId(id), id)
            val methods = (listOfNotNull(entry.method) + Units.of(id)?.units.orEmpty().mapNotNull { it.method } +
                places.getValue(id).mapNotNull { Registry.resolveEntry(entry, it).method }).flatMap { it.withAlongside() }
            assertTrue(methods.isNotEmpty(), id)
            for (method in methods) assertNull(method.imsakMinutesBeforeFajr, "${method.id} counts an imsak no table prints")
        }
    }

    @Test
    fun `their days show no imsak and stop eating by the fajr shown through ramadan 1448`() {
        for (id in unprinted) {
            val entry = assertNotNull(Registry.byId(id), id)
            for (place in places.getValue(id)) {
                var date = LocalDate(2027, 2, 6)
                while (date <= LocalDate(2027, 3, 11)) {
                    val day = DayPipeline.day(entry, place, date)
                    assertNull(day.imsak, "$id at ${place.lat},${place.lon} on $date")
                    assertTrue(day.endOfEating <= day.fajr, "$id at ${place.lat},${place.lon} on $date: eating ends after Fajr")
                    date = date.plus(1, DateTimeUnit.DAY)
                }
            }
        }
    }

    @Test
    fun `muis's own entry keeps the imsak its imsakiah prints`() {
        assertNotNull(assertNotNull(Registry.byId("sg.muis")?.method).imsakMinutesBeforeFajr)
    }

    @Test
    fun `brunei keeps the imsak its ministry prints in every district and never after the fajr shown`() {
        val entry = assertNotNull(Registry.byId("bn.mora"))
        // Bandar Seri Begawan, Kuala Belait (the district with the largest minutes) and Bangar (Temburong).
        for (place in listOf(Place(4.89035, 114.94006, "Asia/Brunei", "BN"), Place(4.58361, 114.2312, "Asia/Brunei", "BN"), Place(4.7086, 115.0739, "Asia/Brunei", "BN"))) {
            var date = LocalDate(2027, 2, 6)
            while (date <= LocalDate(2027, 3, 11)) {
                val day = DayPipeline.day(entry, place, date)
                val imsak = assertNotNull(day.imsak, "bn.mora at ${place.lat},${place.lon} on $date")
                assertTrue(imsak < day.endOfEating && day.endOfEating <= day.fajr, "bn.mora at ${place.lat},${place.lon} on $date: imsak, end of eating and Fajr out of order")
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }
}
