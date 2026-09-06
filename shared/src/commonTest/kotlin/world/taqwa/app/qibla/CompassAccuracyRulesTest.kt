package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompassAccuracyRulesTest {

    @Test
    fun androidHighAndMediumAreNotLow() {
        assertFalse(CompassAccuracyRules.androidAccuracyIsLow(3)) // SENSOR_STATUS_ACCURACY_HIGH
        assertFalse(CompassAccuracyRules.androidAccuracyIsLow(2)) // SENSOR_STATUS_ACCURACY_MEDIUM
    }

    @Test
    fun androidLowAndUnreliableAreLow() {
        assertTrue(CompassAccuracyRules.androidAccuracyIsLow(1)) // SENSOR_STATUS_ACCURACY_LOW
        assertTrue(CompassAccuracyRules.androidAccuracyIsLow(0)) // SENSOR_STATUS_UNRELIABLE
    }

    @Test
    fun iosAtOrBelowTwentyDegreesIsNotLow() {
        assertFalse(CompassAccuracyRules.iosAccuracyIsLow(0.0))
        assertFalse(CompassAccuracyRules.iosAccuracyIsLow(20.0))
    }

    @Test
    fun iosAboveTwentyDegreesIsLow() {
        assertTrue(CompassAccuracyRules.iosAccuracyIsLow(20.01))
    }

    @Test
    fun iosNegativeIsLowRegardlessOfMagnitude() {
        assertTrue(CompassAccuracyRules.iosAccuracyIsLow(-1.0))
    }
}
