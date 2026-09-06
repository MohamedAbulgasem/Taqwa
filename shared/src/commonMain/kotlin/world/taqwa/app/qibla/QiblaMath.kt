package world.taqwa.app.qibla

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import world.taqwa.app.domain.GeoLocation
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything decidable about pointing at the Kaaba, decided without a device. The bearing comes
 * from adhan2; the distance is our own haversine, which adhan2 does not provide.
 */
object QiblaMath {

    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle bearing to the Kaaba, degrees clockwise from true north. */
    fun bearing(from: GeoLocation): Double =
        Qibla(Coordinates(from.latitude, from.longitude)).direction

    /** Haversine distance to the Kaaba, in kilometres. */
    fun distanceKm(from: GeoLocation): Double {
        fun rad(d: Double) = d * kotlin.math.PI / 180.0
        val dLat = rad(KAABA_LATITUDE - from.latitude)
        val dLon = rad(KAABA_LONGITUDE - from.longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(rad(from.latitude)) * cos(rad(KAABA_LATITUDE)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Signed shortest angular distance from [a] to [b], in the range (-180, 180]. Positive
     * means [b] lies clockwise of [a]. This is the one piece of arithmetic that makes alignment
     * correct across the 0/360 seam — a naive subtraction reports 349 instead of 11.
     */
    fun angleDelta(a: Double, b: Double): Double {
        var delta = (b - a) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta <= -180.0) delta += 360.0
        return delta
    }

    /** True when [heading] is within [thresholdDegrees] of [bearing]. The spec's "aligned" is
     * exactly this call with the default 5-degree threshold. */
    fun isAligned(heading: Double, bearing: Double, thresholdDegrees: Double = 5.0): Boolean =
        abs(angleDelta(heading, bearing)) <= thresholdDegrees
}
