package world.taqwa.app.wear.qibla

import kotlin.math.acos

/**
 * How far the watch's screen tilts from level, and when to ask for a level wrist (open question 4's
 * default: "Hold your wrist level" takes the caption's place while the screen tilts more than 40°
 * from level, until it is back under 30°). The needle keeps pointing meanwhile: the heading is
 * tilt-compensated, so this is advice, not a bad state.
 */
internal object Tilt {

    /**
     * The angle, in degrees, between the screen's normal and up: the arc cosine of the rotation
     * matrix's last element, the device's z axis read in the world's (0 flat, 90 on its edge,
     * 180 face down). The element is held to [-1, 1] first, because float rounding can put it a
     * hair past 1, where the arc cosine is not a number.
     */
    fun degrees(rotationMatrix: FloatArray): Double =
        Math.toDegrees(acos(rotationMatrix[8].coerceIn(-1f, 1f).toDouble()))

    /**
     * Whether to ask for a level wrist: from a tilt of more than [enterAbove] until one under
     * [leaveBelow], so a wrist near the edge does not make the caption flicker. A tilt that is not
     * known (not finite: the lying vector's notice has none) keeps the answer as it was.
     */
    class Level(private val enterAbove: Double = 40.0, private val leaveBelow: Double = 30.0) {
        private var tilted = false

        fun tilted(tiltDegrees: Double): Boolean {
            if (tiltDegrees.isFinite()) {
                tilted = if (tilted) tiltDegrees >= leaveBelow else tiltDegrees > enterAbove
            }
            return tilted
        }
    }
}
