package world.taqwa.app.qibla

import kotlin.math.abs

/**
 * The angle rules of pointing at the Kaaba, which need neither a location nor a library: the signed
 * shortest delta, and the aligned decision. They are the phone's `QiblaMath` rules, moved here
 * unchanged (Dev 1's B40 included) so that the phone, the Galaxy watch and the Apple Watch light the
 * rim and buzz by one rule. `QiblaMath` in `:shared` keeps the bearing and the distance, which need
 * adhan2, and delegates these under their old names.
 */
object QiblaAngles {

    /** The spec's "aligned" (§4.3): a dial not yet aligned lights when the heading comes within
     * this many degrees of the bearing. */
    const val ALIGNED_ENTER_DEGREES = 5.0

    /**
     * An aligned dial goes out only when the heading is more than this many degrees off. The 2
     * degrees between this and [ALIGNED_ENTER_DEGREES] are hysteresis: a phone resting near the
     * 5-degree edge wanders across it with the sensor's noise, and with a single threshold the rim
     * flickered and the tick repeated on every crossing — about 43 ticks in 10 s, modelled, against
     * one with this gap.
     */
    const val ALIGNED_EXIT_DEGREES = 7.0

    /**
     * Signed shortest angular distance from [a] to [b], in the range (-180, 180]. Positive
     * means [b] lies clockwise of [a]. This is the one piece of arithmetic that makes alignment
     * correct across the 0/360 seam — a naive subtraction reports 349 instead of 11.
     */
    fun angleDelta(a: Double, b: Double): Double {
        var delta = (b - a) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta <= -180.0) delta += 360.0
        return delta
    }

    /** True when [heading] is within [thresholdDegrees] of [bearing]: one threshold, no memory.
     * The spec's "aligned" (§4.3, amended 24 Oct 2026) is the three-argument call below, which
     * enters at [ALIGNED_ENTER_DEGREES] and leaves beyond [ALIGNED_EXIT_DEGREES]. */
    fun isAligned(heading: Double, bearing: Double, thresholdDegrees: Double = ALIGNED_ENTER_DEGREES): Boolean =
        abs(angleDelta(heading, bearing)) <= thresholdDegrees

    /**
     * The aligned decision with memory: entered within [ALIGNED_ENTER_DEGREES], left only beyond
     * [ALIGNED_EXIT_DEGREES], [wasAligned] being the previous reading's answer. This is the rule
     * the phone's view model and the Watch dial share, for the lit rim and the tick alike, so it
     * lives here, pure and platform-free, rather than in either of them.
     */
    fun isAligned(heading: Double, bearing: Double, wasAligned: Boolean): Boolean =
        isAligned(heading, bearing, if (wasAligned) ALIGNED_EXIT_DEGREES else ALIGNED_ENTER_DEGREES)
}
