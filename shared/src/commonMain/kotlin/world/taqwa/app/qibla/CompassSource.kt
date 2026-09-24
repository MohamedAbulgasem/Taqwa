package world.taqwa.app.qibla

import kotlinx.coroutines.flow.Flow
import world.taqwa.app.domain.GeoLocation

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

interface CompassSource {
    /** Never emits when [hasSensor] is false — the "no magnetometer" edge case shows a numeric
     * bearing instead and never subscribes to this at all. */
    val readings: Flow<CompassReading>
    fun hasSensor(): Boolean
    /** The location whose declination turns magnetic north into true north, on both platforms;
     * without one every heading is marked low rather than passed off as true. */
    fun updateLocation(location: GeoLocation?)
}

expect fun createCompassSource(): CompassSource
