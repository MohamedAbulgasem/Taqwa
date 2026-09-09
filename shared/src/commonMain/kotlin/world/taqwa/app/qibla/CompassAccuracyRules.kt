package world.taqwa.app.qibla

import kotlin.math.sqrt

/**
 * The spec's low-accuracy thresholds, one function per platform's own accuracy signal. Android
 * reports a coarse 0-3 enum; iOS reports a confidence angle in degrees.
 */
object CompassAccuracyRules {

    /** `SensorManager.SENSOR_STATUS_NO_CONTACT`: the sensor is not in contact with what it
     * measures. Not "uncalibrated" — but equally not a reading to point a needle with. */
    const val SENSOR_STATUS_NO_CONTACT = -1

    /** `SensorManager.SENSOR_STATUS_ACCURACY_LOW`. Everything at or below this is untrustworthy:
     * LOW (1), UNRELIABLE (0) and NO_CONTACT (-1). */
    const val SENSOR_STATUS_ACCURACY_LOW = 1

    /**
     * The Earth's field runs from about 22 µT near the equator to about 66 µT at the poles. This
     * band is that range with a little air either side.
     */
    const val MIN_PLAUSIBLE_FIELD_MICROTESLA = 20.0
    const val MAX_PLAUSIBLE_FIELD_MICROTESLA = 70.0

    /** LOW (1), UNRELIABLE (0) or NO_CONTACT (-1). */
    fun androidAccuracyIsLow(sensorAccuracy: Int): Boolean = sensorAccuracy <= SENSOR_STATUS_ACCURACY_LOW

    /** Above 20 degrees, or negative (iOS's "no fix yet" sentinel). */
    fun iosAccuracyIsLow(headingAccuracyDegrees: Double): Boolean =
        headingAccuracyDegrees < 0.0 || headingAccuracyDegrees > 20.0

    /**
     * Core Location returns a negative `trueHeading` — not an error, just `-1` — whenever it has
     * no location fix to compute declination from, which is the whole time if the manager
     * delivering headings never also delivers locations. Left unchecked it flows through the
     * smoothing filter as a heading of 359°: a needle that looks alive and points at nothing.
     */
    fun iosTrueHeadingIsInvalid(trueHeadingDegrees: Double): Boolean = trueHeadingDegrees < 0.0

    /** Magnitude of a magnetometer sample, in µT. Android hands the vector over as floats,
     * Core Location as doubles; the check is the same one either way. */
    fun fieldMagnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    fun fieldMagnitude(x: Float, y: Float, z: Float): Double =
        fieldMagnitude(x.toDouble(), y.toDouble(), z.toDouble())

    /**
     * True when a "calibrated" magnetometer reading cannot be the Earth's field. One phone in
     * testing reported 536 µT — twenty times the local field — because its HAL was subtracting a
     * 504 µT bias estimate from an 83 µT measurement. No figure of eight fixes that; the honest
     * advice is to move away from whatever is producing the field, which is why this is a
     * separate reason from plain low accuracy.
     */
    fun magneticFieldIsImplausible(magnitudeMicroTesla: Double): Boolean =
        magnitudeMicroTesla < MIN_PLAUSIBLE_FIELD_MICROTESLA ||
            magnitudeMicroTesla > MAX_PLAUSIBLE_FIELD_MICROTESLA

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
