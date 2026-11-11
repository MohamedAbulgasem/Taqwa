package world.taqwa.app.qibla

import world.taqwa.app.qibla.Turn.LEFT
import world.taqwa.app.qibla.Turn.RIGHT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The turn a screen reader is told, the design of the phone's accessibility round 2 (its plan's
 * Task 9 Step 1), here so that round 2 and both watches use one rule. Its cases come first,
 * verbatim; the boundaries and the reset after them.
 */
class QiblaGuidanceTest {

    @Test
    fun theTurnIsTheDeltasSignAndItsDegreesAreRoundedToFifteenFromFifteenToOneEighty() {
        assertEquals(Guidance(RIGHT, 30), QiblaGuidance.of(28.0))
        assertEquals(Guidance(LEFT, 45), QiblaGuidance.of(-44.0))
        assertEquals(Guidance(RIGHT, 15), QiblaGuidance.of(6.0))
        assertEquals(Guidance(RIGHT, 180), QiblaGuidance.of(179.9))
        assertEquals(Guidance(LEFT, 180), QiblaGuidance.of(-179.9))
    }

    @Test
    fun aDeltaOfZeroTurnsRight() {
        assertEquals(Guidance(RIGHT, 15), QiblaGuidance.of(0.0))
    }

    @Test
    fun theTrackerHoldsWhatItSaysAgainstAWobblingHand() {
        val tracker = QiblaGuidanceTracker()
        assertEquals(Guidance(RIGHT, 30), tracker.update(0, 31.0))
        assertEquals(Guidance(RIGHT, 30), tracker.update(100, 34.0))
        assertEquals(Guidance(RIGHT, 30), tracker.update(200, 27.0))
        // A new step, but inside 1 500 ms of the last one taken.
        assertEquals(Guidance(RIGHT, 30), tracker.update(500, 46.0))
        assertEquals(Guidance(RIGHT, 45), tracker.update(1_600, 46.0))
        // |36 - 45| = 9, inside the 10.5 degrees that hold a step.
        assertEquals(Guidance(RIGHT, 45), tracker.update(3_200, 36.0))
        // |33 - 45| = 12, past them.
        assertEquals(Guidance(RIGHT, 30), tracker.update(3_300, 33.0))
    }

    @Test
    fun theTrackerHoldsALeftTurnAgainstAWobblingHandAsItDoesARightOne() {
        // The sequence above with the Qibla to the left: the margin is measured on |delta|.
        val tracker = QiblaGuidanceTracker()
        assertEquals(Guidance(LEFT, 30), tracker.update(0, -31.0))
        assertEquals(Guidance(LEFT, 30), tracker.update(100, -34.0))
        assertEquals(Guidance(LEFT, 30), tracker.update(200, -27.0))
        assertEquals(Guidance(LEFT, 30), tracker.update(500, -46.0))
        assertEquals(Guidance(LEFT, 45), tracker.update(1_600, -46.0))
        // |-36| is 9 from 45, inside the 10.5 degrees that hold a step.
        assertEquals(Guidance(LEFT, 45), tracker.update(3_200, -36.0))
        // |-33| is 12 from it, past them.
        assertEquals(Guidance(LEFT, 30), tracker.update(3_300, -33.0))
    }

    @Test
    fun nullClearsItAndTheNextDeltaIsTakenAtOnce() {
        val tracker = QiblaGuidanceTracker()
        tracker.update(0, 31.0)
        assertNull(tracker.update(100, null))
        assertEquals(Guidance(LEFT, 90), tracker.update(200, -88.0))
    }

    @Test
    fun aDeltaThatIsNotANumberIsNoDirectionAsNullIs() {
        // A sensor that misbehaves can give a heading of NaN, and so a delta of NaN: no direction,
        // never a throw, from the first update or after a turn was said.
        val tracker = QiblaGuidanceTracker()
        assertNull(tracker.update(0, Double.NaN))
        assertEquals(Guidance(RIGHT, 30), tracker.update(100, 31.0))
        assertNull(tracker.update(200, Double.NaN))
        // Cleared as null clears it, so the next delta is taken at once.
        assertEquals(Guidance(LEFT, 90), tracker.update(300, -88.0))
        assertNull(tracker.update(400, Double.POSITIVE_INFINITY))
        assertNull(tracker.update(500, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun aSignChangeAfterTheIntervalTurnsLeft() {
        val tracker = QiblaGuidanceTracker()
        tracker.update(0, 31.0)
        assertEquals(Guidance(RIGHT, 30), tracker.update(1_499, -31.0))
        assertEquals(Guidance(LEFT, 30), tracker.update(1_500, -31.0))
    }

    @Test
    fun aStepMovesOnlyMoreThanTenAndAHalfDegreesFromTheOneItSays() {
        val tracker = QiblaGuidanceTracker()
        tracker.update(0, 31.0)
        // 40.5 rounds to 45, but is exactly 10.5 from the 30 being said.
        assertEquals(Guidance(RIGHT, 30), tracker.update(1_500, 40.5))
        assertEquals(Guidance(RIGHT, 45), tracker.update(1_550, 40.6))
    }

    @Test
    fun aLeftStepMovesOnlyMoreThanTenAndAHalfDegreesFromTheOneItSaysToo() {
        val tracker = QiblaGuidanceTracker()
        tracker.update(0, -31.0)
        assertEquals(Guidance(LEFT, 30), tracker.update(1_500, -40.5))
        assertEquals(Guidance(LEFT, 45), tracker.update(1_550, -40.6))
    }

    @Test
    fun theIntervalRunsFromTheLastTakeNotFromZero() {
        val tracker = QiblaGuidanceTracker()
        tracker.update(0, 31.0)
        assertEquals(Guidance(RIGHT, 45), tracker.update(1_600, 46.0))
        // RIGHT 15 is 25 degrees from the 45 being said, but only 400 ms after 45 was taken.
        assertEquals(Guidance(RIGHT, 45), tracker.update(2_000, 20.0))
        assertEquals(Guidance(RIGHT, 45), tracker.update(3_099, 20.0))
        assertEquals(Guidance(RIGHT, 15), tracker.update(3_100, 20.0))
    }

    @Test
    fun aHeadingWobblingAcrossTheBackIsSaidOnceInTheInterval() {
        // The readings' clock counts from boot, so the first take is far from 0.
        val tracker = QiblaGuidanceTracker()
        assertEquals(Guidance(RIGHT, 180), tracker.update(10_000, 179.0))
        assertEquals(Guidance(RIGHT, 180), tracker.update(10_050, -179.0))
        assertEquals(Guidance(RIGHT, 180), tracker.update(10_100, 179.0))
        assertEquals(Guidance(RIGHT, 180), tracker.update(11_499, -179.0))
        assertEquals(Guidance(LEFT, 180), tracker.update(11_500, -179.0))
        assertEquals(Guidance(LEFT, 180), tracker.update(11_550, 179.0))
    }

    @Test
    fun resetForgetsWhatItSaidSoTheNextDeltaIsTakenAtOnce() {
        val tracker = QiblaGuidanceTracker()
        tracker.update(0, 31.0)
        tracker.reset()
        assertEquals(Guidance(RIGHT, 45), tracker.update(100, 46.0))
    }
}
