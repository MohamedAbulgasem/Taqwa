package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.TestPaths
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Habous unit's name is shown as the place whose table the times follow, and most are read from Habous's Arabic
 * name alone. So no unit may read as a town of the app's that it is not: a unit that shares its name with cities the
 * app lists in Morocco or Western Sahara holds one of them within its own reach, or it carries a name that tells it
 * apart (the review of 13 November 2026: "Ras El Ma" was Habous's place on the Nador coast, 281 km from the app's).
 */
class HabousNamesTest {
    private val cities = EnvelopeRows.cities(TestPaths.appFiles.resolve("cities.csv")).filter { it.cc == "MA" || it.cc == "EH" }
    private val units = Units.of(EnvelopeRows.HABOUS)!!.units

    /** A name as a reader takes it: its letters, without accents, case or what stands between the words. */
    private fun plain(name: String) = Normalizer.normalize(name, Normalizer.Form.NFKD).filter { it.isLetterOrDigit() }.lowercase()

    @Test
    fun `a habous unit that shares an app city's name holds a city of that name within its reach`() {
        assertTrue(cities.size > 100, "${cities.size}")
        var shared = 0
        val strangers = units.filter { unit ->
            val namesakes = cities.filter { plain(it.name) == plain(unit.name) }
            if (namesakes.isNotEmpty()) shared++
            namesakes.isNotEmpty() && namesakes.none { distanceKm(it.at, unit.point) <= unit.radiusKm }
        }
        assertEquals(emptyList(), strangers.map { it.name })
        // The rule has something to hold: most of the app's Moroccan towns are units by their own name.
        assertTrue(shared > units.size / 3, "$shared of ${units.size}")
    }

    @Test
    fun `no two habous units share a name`() {
        assertEquals(units.size, units.map { plain(it.name) }.toSet().size)
    }
}
