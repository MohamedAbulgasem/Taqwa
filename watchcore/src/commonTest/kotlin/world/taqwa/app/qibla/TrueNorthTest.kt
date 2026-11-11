package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrueNorthTest {

    @Test
    fun positiveDeclinationAddsToTheMagneticHeading() {
        assertEquals(15.0, TrueNorth.correct(10.0, 5.0), absoluteTolerance = 0.001)
    }

    @Test
    fun negativeDeclinationSubtractsFromTheMagneticHeading() {
        assertEquals(5.0, TrueNorth.correct(10.0, -5.0), absoluteTolerance = 0.001)
    }

    @Test
    fun theResultWrapsForwardPastThreeSixty() {
        assertEquals(5.0, TrueNorth.correct(355.0, 10.0), absoluteTolerance = 0.001)
    }

    @Test
    fun theResultWrapsBackwardBelowZero() {
        assertEquals(355.0, TrueNorth.correct(10.0, -15.0), absoluteTolerance = 0.001)
    }

    @Test
    fun zeroDeclinationLeavesTheHeadingUnchanged() {
        assertEquals(123.0, TrueNorth.correct(123.0, 0.0), absoluteTolerance = 0.001)
    }

    // --- iOS computes true north itself: Core Location's magnetic heading plus the declination the
    // World Magnetic Model gives for the saved city (Cape Town, September 2026: -26.79°).

    @Test
    fun iosTrueNorthIsTheMagneticHeadingCorrectedForTheSavedCity() {
        assertEquals(93.21, TrueNorth.fromMagneticOrInvalid(120.0, -26.79), absoluteTolerance = 0.001)
        assertEquals(343.21, TrueNorth.fromMagneticOrInvalid(10.0, -26.79), absoluteTolerance = 0.001)
    }

    @Test
    fun iosHasNoTrueNorthWithoutAMagneticHeadingOrADeclination() {
        // -1 is Core Location's own "could not determine", which the accuracy rules refuse.
        assertEquals(-1.0, TrueNorth.fromMagneticOrInvalid(-1.0, -26.79))
        assertEquals(-1.0, TrueNorth.fromMagneticOrInvalid(120.0, null))
        assertTrue(CompassAccuracyRules.iosSampleIsLow(TrueNorth.fromMagneticOrInvalid(120.0, null), 10.0))
    }
}
