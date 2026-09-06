package world.taqwa.app.qibla

/**
 * The spec's low-accuracy thresholds, one function per platform's own accuracy signal. Android
 * reports a coarse 0-3 enum; iOS reports a confidence angle in degrees.
 */
object CompassAccuracyRules {
    /** `SENSOR_STATUS_ACCURACY_LOW` (1) or `SENSOR_STATUS_ACCURACY_UNRELIABLE` (0). */
    fun androidAccuracyIsLow(sensorAccuracy: Int): Boolean = sensorAccuracy <= 1

    /** Above 20 degrees, or negative (iOS's "no fix yet" sentinel). */
    fun iosAccuracyIsLow(headingAccuracyDegrees: Double): Boolean =
        headingAccuracyDegrees < 0.0 || headingAccuracyDegrees > 20.0
}
