package world.taqwa.app.qibla

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import world.taqwa.app.domain.GeoLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything decidable about pointing at the Kaaba, decided without a device. The bearing comes
 * from adhan2; the distance is our own haversine, which adhan2 does not provide.
 *
 * The angle rules (the shortest delta and the aligned decision, B40's hysteresis included) live in
 * [QiblaAngles], in `:watchcore`, so both watches judge alignment with the phone's own code. They
 * are delegated here under their old names, so no caller changed. The bearing and the distance stay
 * here: they need adhan2 and a location, and a watch has neither.
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

    /** [QiblaAngles.angleDelta]: the signed shortest angle from [a] to [b], in (-180, 180]. */
    fun angleDelta(a: Double, b: Double): Double = QiblaAngles.angleDelta(a, b)

    /** [QiblaAngles.ALIGNED_ENTER_DEGREES]: a dial not yet aligned lights within this many degrees. */
    const val ALIGNED_ENTER_DEGREES = QiblaAngles.ALIGNED_ENTER_DEGREES

    /** [QiblaAngles.ALIGNED_EXIT_DEGREES]: an aligned dial goes out only beyond this many degrees. */
    const val ALIGNED_EXIT_DEGREES = QiblaAngles.ALIGNED_EXIT_DEGREES

    /** [QiblaAngles.isAligned]: one threshold, no memory. */
    fun isAligned(heading: Double, bearing: Double, thresholdDegrees: Double = ALIGNED_ENTER_DEGREES): Boolean =
        QiblaAngles.isAligned(heading, bearing, thresholdDegrees)

    /** [QiblaAngles.isAligned]: entered within 5°, left only beyond 7° (B40), [wasAligned] being the
     * previous reading's answer. */
    fun isAligned(heading: Double, bearing: Double, wasAligned: Boolean): Boolean =
        QiblaAngles.isAligned(heading, bearing, wasAligned)
}
