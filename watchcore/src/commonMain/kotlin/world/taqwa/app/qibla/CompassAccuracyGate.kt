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
 * [CompassAccuracyState.BestEffort] is one-way: its only exit is `Good`, after a full
 * [recoverAfterMillis] of continuous good samples. It must never fall back to `Low`, because the
 * two states ask opposite things of the user — `Low` says "fix your compass", `BestEffort` says
 * "your compass is not going to be fixed, here is the bearing instead" — and a device that
 * emits one good sample every second or so (the flapping HAL, again) would otherwise oscillate
 * between the two screens forever, having given up and un-given up alternately.
 *
 * The reason shown is recomputed on every sample from a trailing [reasonWindowMillis] of the
 * current low run, rather than being the worst thing seen since the run began.
 * [CompassLowReason.INTERFERENCE] wins over [CompassLowReason.CALIBRATION] *within that window*,
 * because a field that reads 500 µT makes the accuracy flag meaningless and the figure of eight
 * futile — but a user who walks away from the magnet gets the figure-of-eight advice back a
 * second later instead of being told for the next twenty seconds to keep walking. Changing the
 * reason does not restart the run: the clock towards `BestEffort` measures how long the compass
 * has been untrustworthy, not how long it has been untrustworthy *for one particular reason*.
 * The window is emptied when a good sample breaks the run.
 *
 * Time is passed in rather than read: the caller supplies a monotonic clock (Android's
 * `SystemClock.elapsedRealtime`, iOS's `NSProcessInfo.systemUptime`), and tests supply a counter.
 * A timestamp older than the run it belongs to means that clock did something no monotonic clock
 * should; the run restarts at the new timestamp rather than being measured against a start in the
 * future, which fails towards the safe side in both directions — a longer wait before trusting
 * the compass again, never an early promotion to `Good`.
 */
class CompassAccuracyGate(
    private val enterLowAfterMillis: Long = 1_000L,
    private val recoverAfterMillis: Long = 1_500L,
    private val bestEffortAfterMillis: Long = 20_000L,
    private val reasonWindowMillis: Long = 1_000L,
) {

    private var state: CompassAccuracyState = CompassAccuracyState.Good
    private var lowRunStartedAt: Long? = null
    private var goodRunStartedAt: Long? = null

    /** Timestamps of the low samples in the current run that are still inside the reason window,
     * oldest first, each paired with the reason that sample carried. */
    private val recentLowSamples = ArrayDeque<Pair<Long, CompassLowReason>>()

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
        recentLowSamples.clear()
    }

    private fun onLowSample(now: Long, reason: CompassLowReason) {
        goodRunStartedAt = null
        val previousStart = lowRunStartedAt
        if (previousStart == null || now < previousStart) {
            // Either the run starts here, or the clock went backwards: a run measured from a
            // start in the future would read as negative elapsed time and could never enter Low,
            // so it restarts here. The window goes with it — its timestamps are from the same
            // untrustworthy clock and are no longer comparable to `now`.
            lowRunStartedAt = now
            recentLowSamples.clear()
        }
        val runStart = lowRunStartedAt ?: now
        recentLowSamples.addLast(now to reason)
        // Drop everything a full window or more old: a second without a single interference
        // sample is what turns the advice back into the figure of eight.
        while (recentLowSamples.isNotEmpty() && recentLowSamples.first().first <= now - reasonWindowMillis) {
            recentLowSamples.removeFirst()
        }
        val windowReason =
            if (recentLowSamples.any { it.second == CompassLowReason.INTERFERENCE }) {
                CompassLowReason.INTERFERENCE
            } else {
                CompassLowReason.CALIBRATION
            }
        val elapsed = now - runStart
        state = when {
            // Once given up on, stay given up on: only a full recovery dwell of good samples
            // leaves BestEffort, and it leaves for Good. The reason still tracks the window, so
            // the best-effort screen says which thing is wrong even as that changes.
            elapsed >= bestEffortAfterMillis || state is CompassAccuracyState.BestEffort ->
                CompassAccuracyState.BestEffort(windowReason)
            elapsed >= enterLowAfterMillis -> CompassAccuracyState.Low(windowReason)
            // Below the entry dwell the state stands: Good stays Good, and a Low that a single
            // good sample interrupted does not drop back a level for one sample.
            else -> state
        }
    }

    private fun onGoodSample(now: Long) {
        lowRunStartedAt = null
        recentLowSamples.clear()
        if (state == CompassAccuracyState.Good) {
            goodRunStartedAt = null
            return
        }
        val previousStart = goodRunStartedAt
        // Same clamp as the low run, and the same direction of failure: a backwards clock costs
        // the user another recovery dwell, it does not hand them a needle early.
        val runStart = if (previousStart == null || now < previousStart) now.also { goodRunStartedAt = it } else previousStart
        if (now - runStart >= recoverAfterMillis) {
            state = CompassAccuracyState.Good
            goodRunStartedAt = null
        }
    }
}
