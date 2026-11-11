package world.taqwa.app.wear.tasbeeh

import kotlin.math.sign

/**
 * The bezel, in whole detents (plan 3 Task 6). [detentPx] is one click of it in the pixels a
 * `RotaryScrollEvent` carries: `ViewConfiguration.scaledVerticalScrollFactor`, one `AXIS_SCROLL`
 * unit, which is one click of the Galaxy's bezel. Compose writes a clockwise turn as positive
 * pixels, and clockwise counts.
 *
 * The pixels are added up until they make a whole detent, so a click delivered in pieces is still
 * one count, and a fast spin is every count it turned. What is left over is dropped after
 * [idleResetMillis] without a scroll, or when the turn changes direction, so a nudge never adds to
 * a later turn, and a turn back never pays for the clockwise remainder first.
 */
internal class RotaryCounter(private val detentPx: Float, private val idleResetMillis: Long = 500) {

    private var remainder = 0f
    private var last = Long.MIN_VALUE

    /** The signed whole detents to apply for [pixels] scrolled at [uptimeMillis]: positive counts, negative takes away. */
    fun onScroll(pixels: Float, uptimeMillis: Long): Int {
        if (last != Long.MIN_VALUE && uptimeMillis - last > idleResetMillis) remainder = 0f
        if (remainder != 0f && pixels != 0f && pixels.sign != remainder.sign) remainder = 0f
        last = uptimeMillis
        remainder += pixels
        val detents = remainder / detentPx
        // A click in pieces may add up a hair short of whole in floating point.
        val whole = (detents + detents.sign * SLACK).toInt()
        remainder -= whole * detentPx
        // That hair is not a turn the other way.
        if (whole != 0 && remainder.sign == -whole.sign.toFloat()) remainder = 0f
        return whole
    }

    private companion object {
        /** How short of a whole detent still counts as one, in detents: only floating point's error. */
        const val SLACK = 1e-4f
    }
}
