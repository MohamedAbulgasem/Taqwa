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
}
