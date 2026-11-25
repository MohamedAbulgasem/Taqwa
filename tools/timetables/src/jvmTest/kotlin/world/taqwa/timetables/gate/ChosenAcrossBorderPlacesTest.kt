package world.taqwa.timetables.gate

import world.taqwa.app.prayer.FixCountry
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.READ_IN_COUNTRY_ALONE_ENTRIES
import world.taqwa.app.prayer.engine.registry.READ_IN_COUNTRY_ALONE_UNITS
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.readInCountryAlone
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.units.ReachAreas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ruling R318 (the owner's ruling of 18 Nov 2026, the final audit's B1), the proof's side: the claims the app's
 * `readInCountryAlone` names are exactly the area claims read only where a fix is placed in the country alone, and the
 * places the app's test `ChosenAcrossBorderTest` uses are the ones FixCountry gives those fixes over the bundled cities,
 * none of them read by a claim of the entry chosen there.
 */
class ChosenAcrossBorderPlacesTest {

    private val cities = ReachAreas.Cities(TestPaths.appFiles.resolve("cities.csv"))

    /** [lat], [lon] under [zone] as the app places a GPS fix (LocationRepository.gpsLocation, PrayerTimesEngine). */
    private fun placed(lat: Double, lon: Double, zone: String): Place {
        val choice = requireNotNull(FixCountry.choice(lat, lon, zone, cities.index))
        val beside = choice.beside
        val besideCc = beside?.city?.cc?.uppercase()
        return Place(
            lat, lon, zone, choice.city.city.cc.uppercase(), choice.city.region,
            alsoCountryCode = besideCc,
            alsoAdmin1 = beside?.let { FixCountry.besideRegion(besideCc!!, it.region) },
        )
    }

    @Test
    fun `the claims read in the country alone are the area claims other than a shape`() {
        for (a in ReachAreas.AREAS) {
            assertEquals(a.claim !is ReachAreas.Shape, readInCountryAlone(a.entry, a.unit), a.tag)
        }
        val alone = ReachAreas.AREAS.filter { it.claim !is ReachAreas.Shape }
        assertEquals(
            READ_IN_COUNTRY_ALONE_UNITS,
            alone.filter { it.entry !in READ_IN_COUNTRY_ALONE_ENTRIES }.map { "${it.entry}/${it.unit ?: it.entry}" }.toSet(),
        )
        for (entry in READ_IN_COUNTRY_ALONE_ENTRIES) {
            val measured = requireNotNull(Units.of(entry)).units.filter { it.measured }.map { it.id }.toSet()
            assertEquals(measured, alone.filter { it.entry == entry }.mapNotNull { it.unit }.toSet(), entry)
        }
    }

    @Test
    fun `the border places are the app's, and no claim of the timetable chosen there reads them`() {
        val border = listOf(
            Triple(placed(1.8548, 102.9325, "Asia/Singapore"), Place(1.8548, 102.9325, "Asia/Singapore", "MY", "Johor", alsoCountryCode = "SG"), "sg.muis"),
            Triple(
                placed(26.4207, 50.0888, "Asia/Bahrain"),
                Place(26.4207, 50.0888, "Asia/Bahrain", "SA", "Eastern Province", alsoCountryCode = "BH", alsoAdmin1 = "Manama"),
                "bh.council",
            ),
            Triple(
                placed(1.43801, 103.78877, "Asia/Kuala_Lumpur"),
                Place(1.43801, 103.78877, "Asia/Kuala_Lumpur", "SG", null, alsoCountryCode = "MY", alsoAdmin1 = "Johor"),
                "my.jakim",
            ),
        )
        for ((app, used, entry) in border) {
            assertEquals(used, app, entry)
            val at = GeoPoint(app.lat, app.lon)
            assertFalse(ReachAreas.AREAS.filter { it.entry == entry }.any { ReachAreas.claimed(it, at, cities) }, "$entry at $at")
        }
    }

    @Test
    fun `the places inside the areas are the app's, and the chosen timetable's claim reads them`() {
        val inside = listOf(
            Triple(
                placed(1.28967, 103.85007, "Asia/Kuala_Lumpur"),
                Place(1.28967, 103.85007, "Asia/Kuala_Lumpur", "SG", null, alsoCountryCode = "MY", alsoAdmin1 = "Johor"),
                "sg.muis",
            ),
            Triple(
                placed(26.22787, 50.58565, "Asia/Riyadh"),
                Place(26.22787, 50.58565, "Asia/Riyadh", "BH", "Manama", alsoCountryCode = "SA", alsoAdmin1 = "Eastern Province"),
                "bh.council",
            ),
            Triple(placed(1.4655, 103.7578, "Asia/Singapore"), Place(1.4655, 103.7578, "Asia/Singapore", "MY", "Johor", alsoCountryCode = "SG"), "my.jakim"),
        )
        for ((app, used, entry) in inside) {
            assertEquals(used, app, entry)
            val at = GeoPoint(app.lat, app.lon)
            assertTrue(ReachAreas.AREAS.filter { it.entry == entry }.any { ReachAreas.claimed(it, at, cities) }, "$entry at $at")
        }
    }
}
