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

    // --- Regression: the qibla needle froze on Android because the device's TYPE_ROTATION_VECTOR
    // HAL reported the identity quaternion (0, 0, 0, 1) on every event, at accuracy HIGH.

    @Test
    fun identityRotationVectorIsDegenerate() {
        assertTrue(CompassAccuracyRules.androidRotationVectorIsDegenerate(0f, 0f, 0f))
    }

    @Test
    fun aRestingButRealRotationVectorIsNotDegenerate() {
        // A phone lying flat and very nearly facing north: tiny, but orders of magnitude above
        // the tolerance. This must keep using the fused sensor rather than falling back.
        assertFalse(CompassAccuracyRules.androidRotationVectorIsDegenerate(1e-4f, -2e-4f, 3e-5f))
    }

    @Test
    fun aRotationAboutAnySingleAxisIsNotDegenerate() {
        assertFalse(CompassAccuracyRules.androidRotationVectorIsDegenerate(0.5f, 0f, 0f))
        assertFalse(CompassAccuracyRules.androidRotationVectorIsDegenerate(0f, -0.5f, 0f))
        assertFalse(CompassAccuracyRules.androidRotationVectorIsDegenerate(0f, 0f, 0.7071f))
    }
}
