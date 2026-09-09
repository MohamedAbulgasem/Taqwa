package world.taqwa.app.qibla

import kotlin.math.abs
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
    fun crossingTheSeamTheOtherWayNeverJumpsToTheOppositeSideEither() {
        val f = HeadingFilter(smoothing = 0.3)
        f.update(1.0)
        val next = f.update(359.0)
        // The short way from 1 to 359 is -2 degrees.
        assertTrue(next in 359.0..360.0 || next in 0.0..1.0, "was $next")
    }

    @Test
    fun itConvergesAcrossTheSeamOneStepAtATimeWithoutEverReversing() {
        // Every step must shorten the remaining distance to the target and keep the same sign of
        // travel: a filter that broke at the seam would show up as one step going the long way.
        listOf(350.0 to 10.0, 10.0 to 350.0).forEach { (from, to) ->
            val f = HeadingFilter(smoothing = 0.3)
            f.update(from)
            var remaining = QiblaMath.angleDelta(from, to)
            val direction = if (remaining > 0) 1.0 else -1.0
            repeat(40) {
                val next = f.update(to)
                val nextRemaining = QiblaMath.angleDelta(next, to)
                assertTrue(
                    nextRemaining * direction >= -1e-9,
                    "overshot or reversed: $nextRemaining remaining from $from to $to",
                )
                assertTrue(
                    abs(nextRemaining) < abs(remaining) + 1e-9,
                    "did not converge: $remaining -> $nextRemaining",
                )
                remaining = nextRemaining
            }
            assertTrue(abs(remaining) < 0.1, "was $remaining short of $to")
        }
    }

    @Test
    fun resetForgetsThePreviousReadingSoTheNextOneAgainPassesThroughUnfiltered() {
        val f = HeadingFilter()
        f.update(90.0)
        f.reset()
        assertEquals(10.0, f.update(10.0), absoluteTolerance = 0.001)
    }
}
