package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun androidNoContactIsLowToo() {
        // -1: the sensor is reporting that it is not measuring anything. Not "uncalibrated", but
        // certainly not a heading.
        assertTrue(CompassAccuracyRules.androidAccuracyIsLow(CompassAccuracyRules.SENSOR_STATUS_NO_CONTACT))
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

    // --- Regression: iOS reported trueHeading = -1 for the whole session, because the manager
    // delivering headings was never also delivering locations. Unchecked, the smoothing filter
    // turned that into a heading of 359 degrees — a needle that looked alive.

    @Test
    fun aNegativeIosTrueHeadingIsRejectedEvenThoughItIsAValidLookingNumber() {
        assertTrue(CompassAccuracyRules.iosTrueHeadingIsInvalid(-1.0))
        assertTrue(CompassAccuracyRules.iosTrueHeadingIsInvalid(-0.0001))
    }

    @Test
    fun anyHeadingOnTheCompassIsAValidIosHeading() {
        assertFalse(CompassAccuracyRules.iosTrueHeadingIsInvalid(0.0))
        assertFalse(CompassAccuracyRules.iosTrueHeadingIsInvalid(359.9))
    }

    // --- Field plausibility. The Earth's field is 22-66 uT everywhere on the surface; the phone
    // that started this investigation reported a "calibrated" 536 uT, having subtracted a 504 uT
    // bias estimate from an 83 uT measurement.

    @Test
    fun aFieldTooWeakToBeTheEarthsIsImplausible() {
        assertTrue(CompassAccuracyRules.magneticFieldIsImplausible(19.0))
    }

    @Test
    fun theEndsOfTheBandAreAccepted() {
        assertFalse(CompassAccuracyRules.magneticFieldIsImplausible(20.0))
        assertFalse(CompassAccuracyRules.magneticFieldIsImplausible(70.0))
    }

    @Test
    fun anOrdinaryTerrestrialFieldIsPlausible() {
        assertFalse(CompassAccuracyRules.magneticFieldIsImplausible(45.0))
    }

    @Test
    fun aFieldTooStrongToBeTheEarthsIsImplausible() {
        assertTrue(CompassAccuracyRules.magneticFieldIsImplausible(71.0))
        assertTrue(CompassAccuracyRules.magneticFieldIsImplausible(536.0))
    }

    @Test
    fun theMagnitudeIsTheEuclideanNormOfTheSample() {
        assertEquals(5.0, CompassAccuracyRules.fieldMagnitude(3f, 4f, 0f), absoluteTolerance = 1e-9)
        // The device's own reading: -11.8, 80.2, 20.2 raw, which is already implausible.
        assertTrue(CompassAccuracyRules.magneticFieldIsImplausible(CompassAccuracyRules.fieldMagnitude(-11.8f, 80.2f, 20.2f)))
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
