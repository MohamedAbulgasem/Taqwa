package world.taqwa.app.qibla

/**
 * Arithmetic only: given a magnetic heading and a declination in degrees (east-positive, the
 * convention `GeomagneticField.getDeclination()` and [WorldMagneticModel] both return), produces
 * the true-north heading. Android takes its declination from `GeomagneticField`; iOS from
 * [WorldMagneticModel], for the city on the Qibla screen, since Core Location's own true heading
 * needs a location the user may never have given.
 */
object TrueNorth {
    fun correct(magneticHeadingDegrees: Double, declinationDegrees: Double): Double {
        val corrected = (magneticHeadingDegrees + declinationDegrees) % 360.0
        return if (corrected < 0.0) corrected + 360.0 else corrected
    }

    /**
     * iOS's true heading: Core Location's magnetic heading corrected for the saved city's
     * declination — or -1, Core Location's own "could not determine", when there is no magnetic
     * heading or no declination yet, so that [CompassAccuracyRules.iosSampleIsLow] refuses it rather
     * than passing magnetic north off as true north.
     */
    fun fromMagneticOrInvalid(magneticHeadingDegrees: Double, declinationDegrees: Double?): Double =
        if (declinationDegrees == null || magneticHeadingDegrees < 0.0) -1.0
        else correct(magneticHeadingDegrees, declinationDegrees)
}
