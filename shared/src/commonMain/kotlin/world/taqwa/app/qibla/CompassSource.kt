package world.taqwa.app.qibla

import kotlinx.coroutines.flow.Flow
import world.taqwa.app.domain.GeoLocation

/** One reading. Heading is already true north: Android applies `GeomagneticField` declination
 * before emitting; iOS reports `CLHeading.trueHeading` directly. */
data class CompassReading(val trueHeadingDegrees: Double, val isLowAccuracy: Boolean)

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
