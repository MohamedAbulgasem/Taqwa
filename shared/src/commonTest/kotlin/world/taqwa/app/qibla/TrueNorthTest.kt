package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
