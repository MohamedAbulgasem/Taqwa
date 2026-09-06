package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadingFilterTest {

    @Test
    fun theFirstReadingPassesThroughUnfiltered() {
        val f = HeadingFilter()
        assertEquals(90.0, f.update(90.0), absoluteTolerance = 0.001)
    }

    @Test
    fun itSmoothsTowardANewReadingRatherThanJumpingToIt() {
        val f = HeadingFilter(smoothing = 0.2)
        f.update(0.0)
        val next = f.update(100.0)
        assertTrue(next in 15.0..25.0, "was $next")
    }

    @Test
    fun itConvergesToASteadyReadingOverRepeatedUpdates() {
        val f = HeadingFilter(smoothing = 0.3)
        f.update(0.0)
        repeat(30) { f.update(90.0) }
        val result = f.update(90.0)
        assertTrue(result in 89.0..91.0, "was $result")
    }

    @Test
    fun crossingTheZeroThreeSixtySeamNeverJumpsToTheOppositeSide() {
        val f = HeadingFilter(smoothing = 0.3)
        f.update(359.0)
        val next = f.update(1.0)
        // The short way from 359 to 1 is +2 degrees, not the long way through 180.
        assertTrue(next in 359.0..360.0 || next in 0.0..1.0, "was $next")
    }

    @Test
    fun resetForgetsThePreviousReadingSoTheNextOneAgainPassesThroughUnfiltered() {
        val f = HeadingFilter()
        f.update(90.0)
        f.reset()
        assertEquals(10.0, f.update(10.0), absoluteTolerance = 0.001)
    }
}
