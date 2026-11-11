package world.taqwa.app.qibla

import kotlinx.coroutines.flow.Flow
import world.taqwa.app.domain.GeoLocation

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
