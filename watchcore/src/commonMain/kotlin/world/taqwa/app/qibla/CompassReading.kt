package world.taqwa.app.qibla

/**
 * One raw reading, as the sensor produced it. Heading is already true north: both sources correct
 * the magnetic heading by the declination at the Qibla screen's location before emitting — Android
 * with `GeomagneticField`, iOS with [WorldMagneticModel].
 *
 * [isLowAccuracy] and [lowReason] are *per sample* facts, not a state — a single untrustworthy
 * sample means nothing on its own. [CompassAccuracyGate], driven by the view model, is what turns
 * a run of them into something worth showing. [timestampMillis] comes from the platform's
 * monotonic clock, so the gate's dwell times survive the wall clock moving.
 */
data class CompassReading(
    val trueHeadingDegrees: Double,
    val timestampMillis: Long,
    val isLowAccuracy: Boolean,
    val lowReason: CompassLowReason? = null,
)
