package world.taqwa.app.qibla

import kotlin.math.abs
import kotlin.math.roundToInt

/** Which way the wearer turns to face the Qibla: clockwise is right. */
enum class Turn { LEFT, RIGHT }

/** A turn and its size, in whole steps of [QiblaGuidance.STEP] degrees, for a screen reader. */
data class Guidance(val turn: Turn, val degrees: Int)

/**
 * Which way to turn, in 15° steps: the design of the phone's accessibility round 2 (its plan's
 * Task 9 Step 1), public here so that round 2 and both watches tell a screen reader one thing.
 */
object QiblaGuidance {
    const val STEP = 15

    /**
     * The guidance for [delta], which is `QiblaAngles.angleDelta(heading, bearing)`: RIGHT for a
     * delta of 0 or more, since a positive delta puts the bearing clockwise of the heading. The
     * degrees are |delta| rounded to a step, never under one step and never over a half turn.
     * [delta] must be finite: rounding NaN throws. [QiblaGuidanceTracker] answers null for one.
     */
    fun of(delta: Double): Guidance {
        val degrees = ((abs(delta) / STEP).roundToInt() * STEP).coerceIn(STEP, 180)
        return Guidance(if (delta >= 0) Turn.RIGHT else Turn.LEFT, degrees)
    }
}

/**
 * The guidance being spoken: it changes only when the turn changes or the delta is more than 3°
 * past its step's edge (10.5° from the degrees it speaks), and at most once every 1 500 ms, so a
 * hand that wobbles is not narrated. Null while there is no direction to give, and the next
 * direction after that is spoken at once. A delta that is not finite, from the NaN heading a
 * sensor that misbehaves can give, is no direction, as null is. Time is passed in, from the
 * readings' monotonic clock.
 */
class QiblaGuidanceTracker {
    private var spoken: Guidance? = null
    private var spokenAt = 0L

    fun update(nowMillis: Long, delta: Double?): Guidance? {
        if (delta == null || !delta.isFinite()) {
            spoken = null
            return null
        }
        val candidate = QiblaGuidance.of(delta)
        val current = spoken
        val take = when {
            current == null -> true
            candidate == current -> false
            nowMillis - spokenAt < MIN_INTERVAL_MS -> false
            candidate.turn != current.turn -> true
            else -> abs(abs(delta) - current.degrees) > QiblaGuidance.STEP / 2.0 + MARGIN_DEGREES
        }
        if (take) {
            spoken = candidate
            spokenAt = nowMillis
        }
        return spoken
    }

    /** Forgets what was spoken, so the next delta is taken at once. */
    fun reset() {
        spoken = null
        spokenAt = 0L
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1_500L
        const val MARGIN_DEGREES = 3.0
    }
}
