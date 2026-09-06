package world.taqwa.app.prayer

import world.taqwa.app.domain.HighLatitudePreference
import kotlin.test.Test
import kotlin.test.assertEquals

class HighLatitudeSelectorTest {

    @Test
    fun explicitChoiceIsAlwaysHonoured() {
        assertEquals(
            HighLatitudePreference.TWILIGHT_ANGLE,
            HighLatitudeSelector.select(HighLatitudePreference.TWILIGHT_ANGLE, 71.0),
        )
    }

    @Test
    fun automaticUsesMiddleOfNightInTheTropics() {
        assertEquals(
            HighLatitudePreference.MIDDLE_OF_NIGHT,
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 21.4),
        )
    }

    @Test
    fun automaticEngagesSeventhRuleAtLondonLatitude() {
        assertEquals(
            HighLatitudePreference.SEVENTH_OF_NIGHT,
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 51.5),
        )
    }

    @Test
    fun automaticUsesTwilightAngleAtTromso() {
        assertEquals(
            HighLatitudePreference.TWILIGHT_ANGLE,
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 69.65),
        )
    }

    @Test
    fun southernLatitudesMirrorNorthern() {
        assertEquals(
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 55.0),
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, -55.0),
        )
    }

    @Test
    fun selectNeverReturnsAutomatic() {
        (-90..90 step 5).forEach { lat ->
            val r = HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, lat.toDouble())
            kotlin.test.assertNotEquals(HighLatitudePreference.AUTOMATIC, r)
        }
    }
}
