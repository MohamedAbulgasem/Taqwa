package world.taqwa.app.prayer.engine.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The second audit's G12: About's and the site's "who publishes" sentence names the place, and a unit's own name is the
 * authority's Latin spelling (Habous's, Oman's, Algeria's) or a zone code (JAKIM's), which would stand in an Arabic,
 * Urdu or Bengali sentence ("في Casablanca", "used in SBH01"). So these units carry no name of their own for it
 * ([AuthorityUnit.named] false, as Egypt's towns since F34) and the place is called by the app's own name for it in the
 * reader's language.
 */
class UnitNamesTest {
    @Test
    fun `habous oman algeria and jakim units leave the place's name to the app`() {
        for (id in listOf("ma.habous", "om.mara", "dz.marw", "my.jakim")) {
            val units = Units.of(id)!!.units
            assertTrue(units.isNotEmpty(), id)
            for (unit in units) assertEquals(false, unit.named, "$id ${unit.id}")
        }
    }

    @Test
    fun `casablanca muscat algiers and sandakan resolve without a unit label`() {
        for (place in listOf(
            Place(33.5731, -7.5898, "Africa/Casablanca", "MA"),
            Place(23.588, 58.3829, "Asia/Muscat", "OM"),
            Place(36.7538, 3.0588, "Africa/Algiers", "DZ"),
            Place(5.8394, 118.1172, "Asia/Kuching", "MY"),
        )) {
            val resolution = Registry.resolve(place)
            assertTrue(resolution.unitId != null, "${resolution.entry.id} at $place")
            assertNull(resolution.unitLabel, "${resolution.entry.id} ${resolution.unitId}")
        }
    }
}
