package world.taqwa.app.qibla

/** Why a reading is not trustworthy. The two cases want opposite advice from the user. */
enum class CompassLowReason {
    /** The magnetometer's own calibration has drifted; a figure of eight can fix it. */
    CALIBRATION,

    /** The field around the device is not the Earth's; no amount of waving helps, moving does. */
    INTERFERENCE,
}

/** What the gate believes about the compass right now. */
sealed interface CompassAccuracyState {
    /** Point the needle. */
    data object Good : CompassAccuracyState

    /** Do not point; ask for the fix that matches [reason]. */
    data class Low(val reason: CompassLowReason) : CompassAccuracyState

    /** Long enough in [Low] that asking again is not honest: show what needs no sensor. */
    data class BestEffort(val reason: CompassLowReason) : CompassAccuracyState
}

/**
 * The one place that decides whether the compass is believable, kept pure so it can be tested
 * against synthetic sequences rather than against a phone on a desk.
 *
 * Two devices motivated every rule here. On one, the magnetometer HAL reported
 * `SENSOR_STATUS_ACCURACY_LOW` once, at registration, and never spoke again — so a state that
 * flips on a single sample latched the calibration prompt for the life of the screen, with no
 * escape and no explanation. On another, the accuracy callback never arrived at all, so the app
 * showed a confident needle it had not earned. Accuracy is now read from every sample, which
 * means the state can flip 100 times a second unless something damps it; that is what the two
 * dwell times below are for.
 *
 * - A run of low samples lasting [enterLowAfterMillis] enters [CompassAccuracyState.Low]. Shorter
 *   than that is a wobble, not a state worth telling the user about.
 * - A run of good samples lasting [recoverAfterMillis] — deliberately longer — returns to
 *   [CompassAccuracyState.Good], from `Low` and from `BestEffort` alike. Asymmetric on purpose:
 *   pointing wrongly is worse than declining to point.
 * - A single unbroken low run reaching [bestEffortAfterMillis] means the device is not going to
 *   recover by being asked nicely; [CompassAccuracyState.BestEffort] stops asking and shows the
 *   bearing and distance, which need no sensor at all.
 *
 * Reasons within one low run are kept at their worst: [CompassLowReason.INTERFERENCE] wins over
 * [CompassLowReason.CALIBRATION], because a field that reads 500 µT makes the accuracy flag
 * meaningless and the figure of eight futile. The worst reason is forgotten when a good sample
 * breaks the run, not when the state changes.
 *
 * Time is passed in rather than read: the caller supplies a monotonic clock (Android's
 * `SystemClock.elapsedRealtime`, iOS's `NSProcessInfo.systemUptime`), and tests supply a counter.
 */
class CompassAccuracyGate(
    private val enterLowAfterMillis: Long = 1_000L,
    private val recoverAfterMillis: Long = 1_500L,
    private val bestEffortAfterMillis: Long = 20_000L,
) {

    private var state: CompassAccuracyState = CompassAccuracyState.Good
    private var lowRunStartedAt: Long? = null
    private var goodRunStartedAt: Long? = null
    private var worstReasonThisRun: CompassLowReason? = null

    /** The gate's current belief, without feeding it a sample. */
    fun state(): CompassAccuracyState = state

    /**
     * Feeds one sample and returns the state it produces. [reason] is only read when [low] is
     * true; a low sample that names no reason is treated as [CompassLowReason.CALIBRATION].
     */
    fun update(
        timestampMillis: Long,
        low: Boolean,
        reason: CompassLowReason? = null,
    ): CompassAccuracyState {
        if (low) onLowSample(timestampMillis, reason ?: CompassLowReason.CALIBRATION)
        else onGoodSample(timestampMillis)
        return state
    }

    fun reset() {
        state = CompassAccuracyState.Good
        lowRunStartedAt = null
        goodRunStartedAt = null
        worstReasonThisRun = null
    }

    private fun onLowSample(now: Long, reason: CompassLowReason) {
        goodRunStartedAt = null
        val runStart = lowRunStartedAt ?: now.also { lowRunStartedAt = it }
        worstReasonThisRun = worseOf(worstReasonThisRun, reason)
        val worst = worstReasonThisRun ?: reason
        val elapsed = now - runStart
        state = when {
            elapsed >= bestEffortAfterMillis -> CompassAccuracyState.BestEffort(worst)
            elapsed >= enterLowAfterMillis -> CompassAccuracyState.Low(worst)
            // Below the entry dwell the state stands: Good stays Good, and a Low or BestEffort
            // that a single good sample interrupted does not drop back a level for one sample.
            else -> state
        }
    }

    private fun onGoodSample(now: Long) {
        lowRunStartedAt = null
        worstReasonThisRun = null
        if (state == CompassAccuracyState.Good) {
            goodRunStartedAt = null
            return
        }
        val runStart = goodRunStartedAt ?: now.also { goodRunStartedAt = it }
        if (now - runStart >= recoverAfterMillis) {
            state = CompassAccuracyState.Good
            goodRunStartedAt = null
        }
    }

    private fun worseOf(a: CompassLowReason?, b: CompassLowReason): CompassLowReason =
        if (a == CompassLowReason.INTERFERENCE || b == CompassLowReason.INTERFERENCE) {
            CompassLowReason.INTERFERENCE
        } else {
            CompassLowReason.CALIBRATION
        }
}
