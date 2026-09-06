package world.taqwa.app.qibla

/**
 * A low-pass filter on a circular quantity (0-360 degrees). A plain exponential average breaks
 * across the 0/360 seam — averaging 359 and 1 naively yields 180, the wrong side of the compass
 * entirely. This averages via the shortest angular delta instead, reusing [QiblaMath.angleDelta].
 */
class HeadingFilter(private val smoothing: Double = 0.15) {

    private var current: Double? = null

    fun update(rawHeadingDegrees: Double): Double {
        val previous = current
        val next = if (previous == null) {
            rawHeadingDegrees
        } else {
            val delta = QiblaMath.angleDelta(previous, rawHeadingDegrees)
            (previous + smoothing * delta + 360.0) % 360.0
        }
        current = next
        return next
    }

    fun reset() { current = null }
}
