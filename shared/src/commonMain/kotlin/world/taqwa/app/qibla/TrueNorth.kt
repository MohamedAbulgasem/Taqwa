package world.taqwa.app.qibla

/**
 * Arithmetic only: given a magnetic heading and a declination in degrees (east-positive, the
 * convention `GeomagneticField.getDeclination()` already returns), produces the true-north
 * heading. Android supplies both readings separately and calls this; iOS's `CLHeading.trueHeading`
 * is already true north and never touches this function.
 */
object TrueNorth {
    fun correct(magneticHeadingDegrees: Double, declinationDegrees: Double): Double {
        val corrected = (magneticHeadingDegrees + declinationDegrees) % 360.0
        return if (corrected < 0.0) corrected + 360.0 else corrected
    }
}
