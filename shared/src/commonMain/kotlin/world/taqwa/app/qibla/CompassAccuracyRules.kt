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

    /**
     * True when the vector part of an Android `TYPE_ROTATION_VECTOR` sample is the zero vector,
     * i.e. the sample encodes the identity rotation and carries no orientation at all.
     *
     * A working fused sensor never reports this. Even a phone lying perfectly flat and facing
     * exactly true north jitters in the low float digits, so `x`, `y` and `z` are never all zero
     * twice running. A stubbed vendor HAL, on the other hand, reports `(0, 0, 0, 1)` forever —
     * while still claiming `SENSOR_STATUS_ACCURACY_HIGH` — and every such sample decodes to
     * azimuth 0, which pins the qibla needle to north and never moves it. Devices that do this
     * have to be driven from the raw accelerometer and magnetometer instead, so the Android
     * source drops these samples rather than trusting a sensor that merely exists.
     *
     * The comparison is against a tolerance rather than exact zero so that a HAL that quantises
     * its output to a fixed-point grid, rather than emitting literal zeroes, is caught too;
     * `1e-6` is several orders of magnitude below any real device's resting jitter.
     */
    fun androidRotationVectorIsDegenerate(x: Float, y: Float, z: Float): Boolean {
        val squaredNorm = x.toDouble() * x + y.toDouble() * y + z.toDouble() * z
        return squaredNorm < 1e-12
    }
}
