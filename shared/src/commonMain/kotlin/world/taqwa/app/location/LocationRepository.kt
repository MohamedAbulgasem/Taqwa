package world.taqwa.app.location

import kotlinx.datetime.TimeZone
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LocationRepository(private val provider: LocationProvider) {

    companion object {
        private const val EARTH_RADIUS_METRES = 6_371_000.0
        private const val RECOMPUTE_THRESHOLD_METRES = 5_000.0

        fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            fun rad(d: Double) = d * kotlin.math.PI / 180.0
            val dLat = rad(lat2 - lat1)
            val dLon = rad(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).pow(2)
            return EARTH_RADIUS_METRES * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        fun shouldRecompute(old: GeoLocation, new: Pair<Double, Double>): Boolean =
            distanceMetres(old.latitude, old.longitude, new.first, new.second) > RECOMPUTE_THRESHOLD_METRES
    }

    suspend fun permission(): LocationPermission = provider.permission()

    suspend fun requestPermission(): LocationPermission = provider.requestPermission()

    /**
     * Returns fresh coordinates, or null when the permission is absent or the fix failed.
     * The timezone is not resolved here — the caller keeps the previously known zone, or the
     * zone of the manually chosen city.
     */
    suspend fun currentCoordinates(): Pair<Double, Double>? = provider.currentCoordinates()

    /**
     * Resolves a fresh GPS fix into a [GeoLocation] ready to persist. The coordinates are the
     * exact fix — prayer times must use where the user actually is, never a city's coordinates —
     * but the city name and country code (the latter needed for calculation-method
     * auto-detection) come from the nearest bundled city, since raw GPS carries neither.
     */
    suspend fun resolveGpsLocation(cityRepository: CityRepository): GeoLocation? {
        val (latitude, longitude) = currentCoordinates() ?: return null
        val nearest = cityRepository.nearest(latitude, longitude)
        return GeoLocation(
            latitude = latitude,
            longitude = longitude,
            timeZoneId = TimeZone.currentSystemDefault().id,
            cityName = nearest?.name,
            countryCode = nearest?.countryCode,
        )
    }
}
