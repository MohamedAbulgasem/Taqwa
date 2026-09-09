package world.taqwa.app.qibla

import kotlinx.coroutines.flow.Flow
import world.taqwa.app.domain.GeoLocation

/**
 * One raw reading, as the sensor produced it. Heading is already true north: Android applies
 * `GeomagneticField` declination before emitting; iOS reports `CLHeading.trueHeading` directly.
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
    /** Only meaningful on Android, where declination must be computed from a location; a no-op
     * on iOS, where `CLHeading.trueHeading` is already true north. */
    fun updateLocation(location: GeoLocation?)
}

expect fun createCompassSource(): CompassSource
