package world.taqwa.app.wear.qibla

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The compass's log (plan 5 Task 12 Step 0): a debug build logs `TaqwaWearQibla` once a second as
 * plan 3 built it, and a store build only once the tag is enabled on the watch
 * (`adb shell setprop log.tag.TaqwaWearQibla DEBUG`, then a fresh open of Qibla), for the Galaxy's
 * pass on the signed candidate. The line holds the sensors' word alone: no Qibla bearing, no
 * declination and no place, so the log says nothing of where the wearer is (Ruling 39).
 */
class QiblaLogTest {

    @Test
    fun theQiblaLogIsOnInADebugBuildOrWhenItsTagIsEnabled() {
        assertFalse(qiblaLogOn(debugBuild = false, tagEnabled = false))
        assertTrue(qiblaLogOn(debugBuild = true, tagEnabled = false))
        assertTrue(qiblaLogOn(debugBuild = false, tagEnabled = true))
        assertTrue(qiblaLogOn(debugBuild = true, tagEnabled = true))
        // The name the setprop command and the Galaxy pass's logcat give it.
        assertEquals("TaqwaWearQibla", QIBLA_TAG)
    }

    @Test
    fun theQiblaLogLineIsTheExample() {
        assertEquals(
            "rv 3, mag 2, field 46.1 µT, trusted, raw pair off, tilt 12°, rotation 0, heading 231°",
            qiblaLogLine(3, 2, 46.1, trusted = true, rawPairActive = false, tiltDegrees = 12.0, displayRotation = 0, headingDegrees = 231.0),
        )
        // Before a sensor has said anything: no accuracy, no field, no tilt and no heading.
        assertEquals(
            "rv none, mag none, field none, not trusted, raw pair on, tilt none, rotation 2, heading none",
            qiblaLogLine(null, null, null, trusted = false, rawPairActive = true, tiltDegrees = Double.NaN, displayRotation = 2, headingDegrees = null),
        )
    }

    @Test
    fun theLineIsInWholeDegreesAndWesternDigitsWhateverTheWatchsLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals(
                "rv 1, mag 0, field 179.0 µT, trusted, raw pair off, tilt 40°, rotation 1, heading 0°",
                qiblaLogLine(1, 0, 178.96, trusted = true, rawPairActive = false, tiltDegrees = 39.5, displayRotation = 1, headingDegrees = 359.6),
            )
            // A heading that is not a number is no heading (Ruling 58 item 1).
            assertEquals(
                "rv 3, mag 3, field 46.1 µT, trusted, raw pair off, tilt 12°, rotation 0, heading none",
                qiblaLogLine(3, 3, 46.1, trusted = true, rawPairActive = false, tiltDegrees = 12.0, displayRotation = 0, headingDegrees = Double.NaN),
            )
        } finally {
            Locale.setDefault(before)
        }
    }
}
