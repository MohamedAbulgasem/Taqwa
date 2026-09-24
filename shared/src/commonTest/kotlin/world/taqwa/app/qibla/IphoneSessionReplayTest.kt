package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Replays [IphoneCapeTownSession] — a real iPhone, in ordinary use, whose own compass points without
 * complaint — through the iOS per-sample verdict and the gate, exactly as the view model does.
 */
class IphoneSessionReplayTest {

    @Test
    fun aRecordedSessionOfOrdinaryUseNeverHidesTheNeedle() {
        val gate = CompassAccuracyGate()
        for ((timestamp, accuracy, heading) in IphoneCapeTownSession.samples) {
            val state = gate.update(
                timestamp,
                CompassAccuracyRules.iosSampleIsLow(trueHeadingDegrees = heading, headingAccuracyDegrees = accuracy),
                CompassLowReason.CALIBRATION,
            )
            assertEquals(CompassAccuracyState.Good, state, "at $timestamp ms, headingAccuracy $accuracy°")
        }
    }
}
