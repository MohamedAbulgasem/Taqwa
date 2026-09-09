package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The state machine the whole calibration prompt hangs on, driven with synthetic sequences —
 * this is where the latched prompt and the flickering prompt both live, and neither is reachable
 * from a test that only checks one sample at a time.
 */
class CompassAccuracyGateTest {

    private fun gate() = CompassAccuracyGate()

    @Test
    fun aFreshGateBelievesTheCompassUntilToldOtherwise() {
        assertEquals(CompassAccuracyState.Good, gate().state())
    }

    @Test
    fun aShortBurstOfLowSamplesIsNotWorthATellingOff() {
        val g = gate()
        g.update(0, low = true, reason = CompassLowReason.CALIBRATION)
        assertEquals(CompassAccuracyState.Good, g.update(500, true, CompassLowReason.CALIBRATION))
        assertEquals(CompassAccuracyState.Good, g.update(999, true, CompassLowReason.CALIBRATION))
    }

    @Test
    fun aFullSecondOfLowSamplesEntersLow() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.CALIBRATION),
            g.update(1_000, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun oneGoodSampleRestartsTheLowRunRatherThanCountingTowardsIt() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        g.update(900, false)
        g.update(950, true, CompassLowReason.CALIBRATION)
        // 1 000 ms after the first low sample, but only 50 ms into the current run.
        assertEquals(CompassAccuracyState.Good, g.update(1_000, true, CompassLowReason.CALIBRATION))
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.CALIBRATION),
            g.update(1_950, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun leavingLowTakesLongerThanEnteringItAndASingleGoodSampleDoesNotDoIt() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        g.update(1_000, true, CompassLowReason.CALIBRATION)
        assertEquals(CompassAccuracyState.Low(CompassLowReason.CALIBRATION), g.update(1_100, false))
        assertEquals(CompassAccuracyState.Low(CompassLowReason.CALIBRATION), g.update(2_500, false))
        assertEquals(CompassAccuracyState.Good, g.update(2_600, false))
    }

    @Test
    fun aLowSampleDuringTheRecoveryDwellRestartsIt() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        g.update(1_000, true, CompassLowReason.CALIBRATION)
        g.update(1_100, false)
        g.update(2_000, true, CompassLowReason.CALIBRATION) // 900 ms in: not enough
        g.update(2_100, false)
        assertEquals(CompassAccuracyState.Low(CompassLowReason.CALIBRATION), g.update(3_400, false))
        assertEquals(CompassAccuracyState.Good, g.update(3_600, false))
    }

    @Test
    fun interferenceOutranksCalibrationWithinOneLowRun() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        g.update(500, true, CompassLowReason.INTERFERENCE)
        // Back to a merely-uncalibrated sample: the run is still the one that saw 500 µT.
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.INTERFERENCE),
            g.update(1_000, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun aReasonIsForgottenWhenAGoodSampleEndsTheRun() {
        val g = gate()
        g.update(0, true, CompassLowReason.INTERFERENCE)
        g.update(1_000, true, CompassLowReason.INTERFERENCE)
        repeat(2) { g.update(1_100L + it * 1_600L, false) }
        assertEquals(CompassAccuracyState.Good, g.state())
        g.update(5_000, true, CompassLowReason.CALIBRATION)
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.CALIBRATION),
            g.update(6_000, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun twentySecondsOfUnbrokenLowSamplesGivesUpAndFallsThroughToBestEffort() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.CALIBRATION),
            g.update(19_999, true, CompassLowReason.CALIBRATION),
        )
        assertEquals(
            CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION),
            g.update(20_000, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun bestEffortCarriesTheReasonSoTheScreenCanSayWhich() {
        val g = gate()
        g.update(0, true, CompassLowReason.INTERFERENCE)
        assertEquals(
            CompassAccuracyState.BestEffort(CompassLowReason.INTERFERENCE),
            g.update(25_000, true, CompassLowReason.INTERFERENCE),
        )
    }

    @Test
    fun aRecoveredCompassLeavesBestEffortUnderTheSameRuleAsLow() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        g.update(20_000, true, CompassLowReason.CALIBRATION)
        assertEquals(CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION), g.update(21_000, false))
        assertEquals(CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION), g.update(22_400, false))
        assertEquals(CompassAccuracyState.Good, g.update(22_600, false))
    }

    @Test
    fun aBrokenLowRunNeverReachesBestEffortHoweverLongItLasts() {
        val g = gate()
        var t = 0L
        // Half a minute of low samples, interrupted by one good sample every five seconds. Never
        // twenty unbroken seconds, so it stays in the state that can still be recovered from.
        repeat(6) {
            repeat(5) { g.update(t, true, CompassLowReason.CALIBRATION); t += 1_000 }
            g.update(t, false); t += 1_000
        }
        assertEquals(CompassAccuracyState.Low(CompassLowReason.CALIBRATION), g.state())
    }

    @Test
    fun resetForgetsEverything() {
        val g = gate()
        g.update(0, true, CompassLowReason.INTERFERENCE)
        g.update(20_000, true, CompassLowReason.INTERFERENCE)
        g.reset()
        assertEquals(CompassAccuracyState.Good, g.state())
        assertEquals(CompassAccuracyState.Good, g.update(20_100, true, CompassLowReason.INTERFERENCE))
    }

    @Test
    fun givingUpIsOneWayAndOneGoodSampleDoesNotUndoIt() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        assertEquals(
            CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION),
            g.update(20_000, true, CompassLowReason.CALIBRATION),
        )
        // The flapping HAL: one good sample, nowhere near the 1 500 ms recovery dwell, then a
        // fresh second of low ones. A second is enough to enter Low from Good; it must not be
        // enough to walk back the decision that this compass is not going to be fixed, or the
        // screen alternates between "wave your phone" and "we have stopped asking".
        g.update(20_100, false)
        g.update(20_200, true, CompassLowReason.CALIBRATION)
        assertEquals(
            CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION),
            g.update(21_200, true, CompassLowReason.CALIBRATION),
        )
        assertEquals(
            CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION),
            g.update(22_500, true, CompassLowReason.CALIBRATION),
        )
        // The one exit is still open.
        g.update(23_000, false)
        assertEquals(CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION), g.update(24_400, false))
        assertEquals(CompassAccuracyState.Good, g.update(24_600, false))
    }

    @Test
    fun theReasonDeEscalatesWhenTheInterferenceStopsWithoutRestartingTheRun() {
        val g = gate()
        g.update(0, true, CompassLowReason.INTERFERENCE)
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.INTERFERENCE),
            g.update(1_000, true, CompassLowReason.INTERFERENCE),
        )
        // The user walks away from the magnet. The field is the Earth's again; the calibration
        // still is not, so the advice should go back to the figure of eight it can act on.
        g.update(1_100, true, CompassLowReason.CALIBRATION)
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.INTERFERENCE),
            g.update(1_900, true, CompassLowReason.CALIBRATION),
        )
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.CALIBRATION),
            g.update(2_000, true, CompassLowReason.CALIBRATION),
        )
        // The reason changed; the run did not. Twenty seconds of low is twenty seconds of low.
        assertEquals(
            CompassAccuracyState.BestEffort(CompassLowReason.CALIBRATION),
            g.update(20_000, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun theReasonEscalatesTheMomentInterferenceAppearsMidRun() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.CALIBRATION),
            g.update(1_000, true, CompassLowReason.CALIBRATION),
        )
        // A magnet arrives mid-run: no figure of eight will help until it leaves.
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.INTERFERENCE),
            g.update(1_500, true, CompassLowReason.INTERFERENCE),
        )
        // And it stays for the width of the window, not just for the one sample that saw it.
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.INTERFERENCE),
            g.update(2_400, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun aTimestampFromBeforeTheRunStartedRestartsTheRun() {
        val g = gate()
        g.update(1_000, true, CompassLowReason.CALIBRATION)
        g.update(1_500, true, CompassLowReason.CALIBRATION)
        // The clock has gone back past the start of the run. Measured against a start in the
        // future this sample is 100 ms of *negative* elapsed time, and the run could never enter
        // Low at all; measured from here it costs the user another second of waiting, which is
        // the safe way to be wrong.
        assertEquals(CompassAccuracyState.Good, g.update(900, true, CompassLowReason.CALIBRATION))
        assertEquals(CompassAccuracyState.Good, g.update(1_800, true, CompassLowReason.CALIBRATION))
        assertEquals(
            CompassAccuracyState.Low(CompassLowReason.CALIBRATION),
            g.update(1_900, true, CompassLowReason.CALIBRATION),
        )
    }

    @Test
    fun aBackwardsClockDuringRecoveryCostsAnotherDwellRatherThanGrantingOneEarly() {
        val g = gate()
        g.update(0, true, CompassLowReason.CALIBRATION)
        g.update(1_000, true, CompassLowReason.CALIBRATION)
        g.update(3_000, false)
        assertEquals(CompassAccuracyState.Low(CompassLowReason.CALIBRATION), g.update(1_500, false))
        assertEquals(CompassAccuracyState.Low(CompassLowReason.CALIBRATION), g.update(2_900, false))
        assertEquals(CompassAccuracyState.Good, g.update(3_100, false))
    }
}
