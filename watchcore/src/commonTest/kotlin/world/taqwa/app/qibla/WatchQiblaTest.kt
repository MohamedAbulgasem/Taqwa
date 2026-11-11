package world.taqwa.app.qibla

import world.taqwa.app.qibla.Turn.LEFT
import world.taqwa.app.qibla.Turn.RIGHT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dial's states over the feed's bearing, as the phone's view model reads its compass, with the
 * watch's default filter and gate: Cape Town's bearing, and a reading every 50 ms.
 */
class WatchQiblaTest {

    private val bearing = 23.35
    private var at = 0L

    private fun good(heading: Double) = CompassReading(heading, timestampMillis = next(), isLowAccuracy = false)

    /** A low sample, pointing north as an untrusted sensor might. */
    private fun low(reason: CompassLowReason) = lowAt(0.0, reason)

    private fun lowAt(heading: Double, reason: CompassLowReason) =
        CompassReading(heading, timestampMillis = next(), isLowAccuracy = true, lowReason = reason)

    private fun next(): Long = at.also { at += STEP_MS }

    /** The steps of [millis] of readings made by [reading], one every 50 ms. */
    private fun WatchQibla.during(millis: Long, reading: () -> CompassReading): List<DialStep> =
        List((millis / STEP_MS).toInt()) { onReading(reading()) }

    /** The turn a searching step tells, which never buzzes. */
    private fun DialStep.turn(): Guidance? {
        assertFalse(buzz)
        return assertIs<DialState.Searching>(state).guidance
    }

    @Test
    fun aGoodHeadingWithinFiveDegreesIsAlignedAndBuzzesOnce() {
        val dial = WatchQibla(bearing)
        val steps = listOf(21.0, 22.0, 24.0).map { dial.onReading(good(it)) }
        steps.forEach { assertIs<DialState.Aligned>(it.state) }
        assertEquals(listOf(true, false, false), steps.map { it.buzz })
        // The first reading passes the filter as it is.
        assertEquals(DialState.Aligned(21.0), steps.first().state)
    }

    @Test
    fun leavingAndComingBackBuzzesAgain() {
        val dial = WatchQibla(bearing)
        val first = dial.onReading(good(21.0))
        val away = dial.during(2_000) { good(60.0) }
        val back = dial.during(2_000) { good(23.0) }
        assertIs<DialState.Searching>(away.last().state)
        assertIs<DialState.Aligned>(back.last().state)
        assertEquals(2, (listOf(first) + away + back).count { it.buzz })
    }

    @Test
    fun onceAlignedItHoldsToSevenDegrees() {
        val dial = WatchQibla(bearing)
        val settled = dial.during(2_000) { good(23.0) }
        assertIs<DialState.Aligned>(settled.last().state)
        // 6.15 degrees off: past the five that let it in, inside the seven that let it out.
        val held = dial.during(3_000) { good(29.5) }
        held.forEach { assertIs<DialState.Aligned>(it.state) }
        // 7.65 degrees off.
        val gone = dial.during(3_000) { good(31.0) }
        assertIs<DialState.Searching>(gone.last().state)
        assertEquals(1, (settled + held + gone).count { it.buzz })
    }

    @Test
    fun searchingSaysWhichWayToTurn() {
        // 340 is 43.35 degrees anticlockwise of the bearing, so the turn is to the right.
        val west = WatchQibla(bearing).during(1_000) { good(340.0) }
        assertEquals(DialStep(DialState.Searching(340.0, Guidance(RIGHT, 45)), buzz = false), west.last())
        // 60 is 36.65 degrees clockwise of it, so the turn is to the left.
        val east = WatchQibla(bearing).during(1_000) { good(60.0) }
        assertEquals(DialStep(DialState.Searching(60.0, Guidance(LEFT, 30)), buzz = false), east.last())
    }

    @Test
    fun searchingTellsANewTurnOnceInOneAndAHalfSecondsAndOnlyPastItsMargin() {
        // One spell of searching, on a boot clock well past 0, with a filter that does not smooth,
        // so each heading is the dial's at once. The turn told comes from the tracker on the
        // readings' own clock: never one turn per reading, and never one turn for the whole spell.
        at = 10_000L
        val dial = WatchQibla(bearing, filter = HeadingFilter(smoothing = 1.0))
        // 43.35 degrees anticlockwise of the bearing: "right 45", told at once and held while the
        // heading holds (10 000 to 10 450 ms).
        dial.during(500) { good(340.0) }.forEach { assertEquals(Guidance(RIGHT, 45), it.turn()) }
        // 83.35 degrees: "right 90" is due, but "right 45" holds for 1 500 ms from its telling...
        dial.during(950) { good(300.0) }.forEach { assertEquals(Guidance(RIGHT, 45), it.turn()) }
        assertEquals(Guidance(RIGHT, 45), dial.onReading(good(300.0)).turn(), "11 450 ms")
        // ...and the reading at 11 500 ms tells it.
        assertEquals(Guidance(RIGHT, 90), dial.onReading(good(300.0)).turn(), "11 500 ms")
        // 20 degrees: "right 15" waits 1 500 ms from the second telling, not from the first.
        dial.during(1_450) { good(3.35) }.forEach { assertEquals(Guidance(RIGHT, 90), it.turn()) }
        assertEquals(Guidance(RIGHT, 15), dial.onReading(good(3.35)).turn(), "13 000 ms")
        // 25 degrees: within 10.5 of the 15 it speaks, so "right 15" holds however long it lasts,
        // though 25 alone would round to 30.
        dial.during(3_000) { good(358.35) }.forEach { assertEquals(Guidance(RIGHT, 15), it.turn()) }
    }

    @Test
    fun searchingTellsALeftTurnByTheSameClockAndMarginAsARightOne() {
        // The run above mirrored about the bearing, so the Qibla is to the wearer's left and each
        // delta is the negative of the one there.
        at = 10_000L
        val dial = WatchQibla(bearing, filter = HeadingFilter(smoothing = 1.0))
        // 43.35 degrees clockwise of the bearing: "left 45", told at once (10 000 to 10 450 ms).
        dial.during(500) { good(66.7) }.forEach { assertEquals(Guidance(LEFT, 45), it.turn()) }
        // 83.35 degrees: "left 90" waits 1 500 ms from the telling of "left 45".
        dial.during(950) { good(106.7) }.forEach { assertEquals(Guidance(LEFT, 45), it.turn()) }
        assertEquals(Guidance(LEFT, 45), dial.onReading(good(106.7)).turn(), "11 450 ms")
        assertEquals(Guidance(LEFT, 90), dial.onReading(good(106.7)).turn(), "11 500 ms")
        // 20 degrees: "left 15" waits 1 500 ms from the second telling.
        dial.during(1_450) { good(43.35) }.forEach { assertEquals(Guidance(LEFT, 90), it.turn()) }
        assertEquals(Guidance(LEFT, 15), dial.onReading(good(43.35)).turn(), "13 000 ms")
        // 25 degrees: within 10.5 of the 15 it speaks, so "left 15" holds however long it lasts,
        // though 25 alone would round to 30.
        dial.during(3_000) { good(48.35) }.forEach { assertEquals(Guidance(LEFT, 15), it.turn()) }
    }

    @Test
    fun anAlignedDialTellsTheTurnAfreshWhenItSearchesAgain() {
        // A filter that does not smooth, so three readings can say three things in 100 ms.
        val dial = WatchQibla(bearing, filter = HeadingFilter(smoothing = 1.0))
        assertEquals(Guidance(RIGHT, 45), (dial.onReading(good(340.0)).state as DialState.Searching).guidance)
        assertIs<DialState.Aligned>(dial.onReading(good(23.0)).state)
        // 50 ms later, well inside the 1 500 ms the guidance holds a turn for: the aligned dial
        // forgot the old turn, so the new one is told at once.
        assertEquals(DialStep(DialState.Searching(60.0, Guidance(LEFT, 30)), buzz = false), dial.onReading(good(60.0)))
    }

    @Test
    fun aLowDialTellsTheTurnAfreshWhenItSearchesAgain() {
        val dial = WatchQibla(bearing, filter = HeadingFilter(smoothing = 1.0))
        assertEquals(Guidance(RIGHT, 45), (dial.onReading(good(340.0)).state as DialState.Searching).guidance)
        assertIs<DialState.Low>(dial.during(1_050) { low(CompassLowReason.CALIBRATION) }.last().state)
        // 36 degrees anticlockwise of the bearing: a held "right 45" would be kept, 9 degrees from
        // it, but the low dial forgot it, so the turn is told afresh as "right 30".
        val back = dial.during(1_550) { good(347.35) }
        assertEquals(Guidance(RIGHT, 30), (back.last().state as DialState.Searching).guidance)
    }

    @Test
    fun aSpellOfLowForgetsTheAlignment() {
        val dial = WatchQibla(bearing)
        assertTrue(dial.onReading(good(23.0)).buzz)
        // A second of low samples at the same heading: aligned while the gate still trusts them,
        // then Low.
        assertIs<DialState.Low>(dial.during(1_050) { lowAt(23.0, CompassLowReason.CALIBRATION) }.last().state)
        // Trusted again, at the same heading: the dial came back from not knowing, so it buzzes again.
        val back = dial.during(1_550) { good(23.0) }
        assertEquals(DialStep(DialState.Aligned(23.0), buzz = true), back.last())
    }

    @Test
    fun aSecondOfLowSamplesIsLowWithItsReason() {
        val dial = WatchQibla(bearing)
        val steps = dial.during(1_050) { low(CompassLowReason.INTERFERENCE) }
        // The last sample is the one 1 000 ms after the first; the one before it is still a wobble.
        assertEquals(DialStep(DialState.Low(CompassLowReason.INTERFERENCE), buzz = false), steps.last())
        assertFalse(steps.dropLast(1).any { it.state is DialState.Low }, "Low before a full second")
    }

    @Test
    fun twentySecondsLowIsBestEffortAndStaysUntilGood() {
        val dial = WatchQibla(bearing)
        // Low samples pointing north, far from the bearing: the gate's first second, before it
        // judges them, shows them searching, never aligned.
        val low = dial.during(20_050) { low(CompassLowReason.CALIBRATION) }
        assertEquals(DialState.Low(CompassLowReason.CALIBRATION), low[low.size - 2].state)
        assertEquals(DialState.BestEffort(CompassLowReason.CALIBRATION), low.last().state)
        // A good sample, then two seconds of interference: given up on, it does not ask again.
        val flapping = listOf(dial.onReading(good(23.0))) +
            dial.during(2_000) { low(CompassLowReason.INTERFERENCE) }
        flapping.forEach { assertIs<DialState.BestEffort>(it.state) }
        assertEquals(DialState.BestEffort(CompassLowReason.INTERFERENCE), flapping.last().state)
        // Only 1 500 ms of good samples bring the needle back: the 31st, 1 500 ms after the first.
        val recovering = dial.during(1_550) { good(23.0) }
        recovering.dropLast(1).forEach { assertIs<DialState.BestEffort>(it.state) }
        assertIs<DialState.Aligned>(recovering.last().state)
        assertTrue(recovering.last().buzz)
        assertEquals(1, (low + flapping + recovering).count { it.buzz })
    }

    @Test
    fun aHeadingThatIsNotANumberSearchesWithNoTurnUntilReset() {
        // A good 23, then one NaN from a sensor that misbehaves, then good 23 again. The filter
        // keeps the NaN, as the phone's does, so the dial searches with no turn to tell, as the
        // phone shows Searching(NaN), and never throws.
        val dial = WatchQibla(bearing)
        assertEquals(DialStep(DialState.Aligned(23.0), buzz = true), dial.onReading(good(23.0)))
        listOf(dial.onReading(good(Double.NaN)), dial.onReading(good(23.0))).forEach { step ->
            val searching = assertIs<DialState.Searching>(step.state)
            assertTrue(searching.heading.isNaN())
            assertNull(searching.guidance)
            assertFalse(step.buzz)
        }
        dial.reset()
        assertEquals(DialStep(DialState.Aligned(23.0), buzz = true), dial.onReading(good(23.0)))

        // A low NaN in the gate's first second, which it still trusts.
        val first = WatchQibla(bearing).onReading(lowAt(Double.NaN, CompassLowReason.CALIBRATION))
        val searching = assertIs<DialState.Searching>(first.state)
        assertTrue(searching.heading.isNaN())
        assertNull(searching.guidance)
    }

    @Test
    fun resetForgetsTheHeadingTheGateAndTheGuidance() {
        // The filter and the guidance: a dial said "turn right 45" moments ago takes the next
        // heading as it is, and tells the turn it needs at once, not 1 500 ms later.
        val searching = WatchQibla(bearing)
        searching.during(500) { good(340.0) }
        searching.reset()
        assertEquals(
            DialStep(DialState.Searching(300.0, Guidance(RIGHT, 90)), buzz = false),
            searching.onReading(good(300.0)),
        )

        // The alignment: a dial aligned before the reset buzzes again on its first aligned reading.
        val aligned = WatchQibla(bearing)
        assertTrue(aligned.onReading(good(23.0)).buzz)
        aligned.reset()
        assertEquals(DialStep(DialState.Aligned(23.0), buzz = true), aligned.onReading(good(23.0)))

        // The gate: a dial that was Low trusts the next good sample, with no 1 500 ms to wait.
        val wasLow = WatchQibla(bearing)
        assertIs<DialState.Low>(wasLow.during(1_050) { low(CompassLowReason.CALIBRATION) }.last().state)
        wasLow.reset()
        assertEquals(DialStep(DialState.Aligned(23.0), buzz = true), wasLow.onReading(good(23.0)))
    }

    private companion object {
        const val STEP_MS = 50L
    }
}
