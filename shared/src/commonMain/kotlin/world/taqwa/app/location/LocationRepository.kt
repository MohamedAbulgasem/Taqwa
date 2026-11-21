package world.taqwa.app.location

import kotlinx.datetime.TimeZone
import world.taqwa.app.city.CityRepository
import world.taqwa.app.city.FixCities
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.FixCountry
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LocationRepository(
    private val provider: LocationProvider,
    private val currentZoneId: () -> String = { TimeZone.currentSystemDefault().id },
) {

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

        /**
         * A fix at [latitude], [longitude] under zone [zoneId] as a location, with the facts [cities] gives it
         * ([CityRepository.forFix], rulings R256 and R259): the nearest city's name, id, country and region, and the
         * second country (with its region, where its units are not chosen by the point) where the zone says the
         * place may be across a border from that city.
         */
        fun gpsLocation(latitude: Double, longitude: Double, zoneId: String, cities: FixCities?): GeoLocation {
            val city = cities?.city
            val beside = cities?.beside
            return GeoLocation(
                latitude = latitude,
                longitude = longitude,
                timeZoneId = zoneId,
                cityName = city?.name,
                countryCode = city?.countryCode,
                cityId = city?.id,
                region = city?.region?.ifEmpty { null },
                alsoCountryCode = beside?.countryCode,
                alsoRegion = beside?.let { FixCountry.besideRegion(it.countryCode, it.region) },
            )
        }
    }

    suspend fun permission(): LocationPermission = provider.permission()

    suspend fun requestPermission(): LocationPermission = provider.requestPermission()

    /**
     * Returns fresh coordinates, or null when the permission is absent or the fix failed.
     * The timezone is not resolved here — the caller keeps the previously known zone, or the
     * zone of the manually chosen city.
     */
    suspend fun currentCoordinates(): Pair<Double, Double>? = provider.currentCoordinates()

    /** The platform's cached position, no new fix asked for. See [LocationProvider.lastKnownCoordinates]. */
    suspend fun lastKnownCoordinates(): Pair<Double, Double>? = provider.lastKnownCoordinates()

    /**
     * Resolves a fresh GPS fix into a [GeoLocation] ready to persist. The coordinates are the
     * exact fix — prayer times must use where the user actually is, never a city's coordinates —
     * but the city name, country code and region (the country decides the timetable) come from the
     * nearest bundled city, since raw GPS carries none of them, and where the device's zone says the
     * fix may be in another country, that country too ([CityRepository.forFix], rulings R256 and
     * R259, the second trust audit's G4 and G5): the engine then holds the day to both.
     */
    suspend fun resolveGpsLocation(cityRepository: CityRepository): GeoLocation? {
        val (latitude, longitude) = currentCoordinates() ?: return null
        val zoneId = currentZoneId()
        return gpsLocation(latitude, longitude, zoneId, cityRepository.forFix(latitude, longitude, zoneId))
    }
}
