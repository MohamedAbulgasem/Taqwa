package world.taqwa.app.qibla

/** What the watch's Qibla dial shows. */
sealed interface DialState {
    /** No magnetometer: the bearing and the distance, with no needle, as the phone shows them. */
    data object NoSensor : DialState

    /** A trusted heading away from the bearing, and the turn a screen reader is told, if any. */
    data class Searching(val heading: Double, val guidance: Guidance?) : DialState

    /** A trusted heading facing the bearing: the rim lights. */
    data class Aligned(val heading: Double) : DialState

    /** The compass can probably be fixed: no needle, and the fix that suits [reason]. */
    data class Low(val reason: CompassLowReason) : DialState

    /** The compass has been low too long to ask again: the bearing and the distance, no needle. */
    data class BestEffort(val reason: CompassLowReason) : DialState
}

/** One reading's answer: the state to draw, and whether the watch buzzes for it. */
data class DialStep(val state: DialState, val buzz: Boolean)

/**
 * The watch's Qibla dial over the feed's bearing: the phone's `QiblaViewModel.collectWhileActive`
 * body, restated over a bearing, so the watch judges its compass with the phone's own rules.
 * `WatchDialParityTest` in `:shared` fails the day the two part.
 *
 * - The gate first, then the filter on every sample: a filter fed only the samples the gate trusts
 *   would jump when it reopens, having missed everything in between.
 * - BestEffort and Low clear the alignment.
 * - Good is Aligned while [QiblaAngles.isAligned] holds with the last step's answer as its memory
 *   (B40: in within 5°, out only beyond 7°), and buzzes only on entry. Otherwise it is Searching,
 *   with the turn from the smoothed heading to the bearing.
 * - Every state but Searching gives the guidance null, so the turn is told afresh when the dial
 *   searches again.
 * - A trusted NaN heading is Searching at NaN with no turn, as the phone shows Searching at NaN.
 *   The filter keeps it, as the phone's does, until [reset].
 *
 * Time is the readings' own monotonic clock ([CompassReading.timestampMillis]).
 * [DialState.NoSensor] is the screen's to choose, before any reading: this never answers it.
 */
class WatchQibla(
    private val bearing: Double,
    private val filter: HeadingFilter = HeadingFilter(),
    private val gate: CompassAccuracyGate = CompassAccuracyGate(),
    private val guidance: QiblaGuidanceTracker = QiblaGuidanceTracker(),
) {
    private var wasAligned = false

    /**
     * Forgets the heading, the gate's runs, the guidance and the alignment. Called on every entry to
     * the page, as the phone resets on every `collectWhileActive`: after a spell away the last heading
     * is stale, and a compass mid-calibration is judged on what it does now.
     */
    fun reset() {
        filter.reset()
        gate.reset()
        guidance.reset()
        wasAligned = false
    }

    fun onReading(reading: CompassReading): DialStep {
        val accuracy = gate.update(reading.timestampMillis, reading.isLowAccuracy, reading.lowReason)
        val smoothed = filter.update(reading.trueHeadingDegrees)
        return when (accuracy) {
            is CompassAccuracyState.BestEffort -> notPointing(reading, DialState.BestEffort(accuracy.reason))
            is CompassAccuracyState.Low -> notPointing(reading, DialState.Low(accuracy.reason))
            CompassAccuracyState.Good ->
                if (QiblaAngles.isAligned(smoothed, bearing, wasAligned)) {
                    val buzz = !wasAligned
                    wasAligned = true
                    guidance.update(reading.timestampMillis, null)
                    DialStep(DialState.Aligned(smoothed), buzz)
                } else {
                    wasAligned = false
                    val turn = guidance.update(reading.timestampMillis, QiblaAngles.angleDelta(smoothed, bearing))
                    DialStep(DialState.Searching(smoothed, turn), buzz = false)
                }
        }
    }

    private fun notPointing(reading: CompassReading, state: DialState): DialStep {
        wasAligned = false
        guidance.update(reading.timestampMillis, null)
        return DialStep(state, buzz = false)
    }
}
