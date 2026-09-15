package world.taqwa.app.location

import kotlinx.coroutines.test.runTest
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationRepositoryTest {

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")

    @Test
    fun walkingAcrossTownDoesNotTriggerRecomputation() {
        // ~2 km east
        assertFalse(LocationRepository.shouldRecompute(london, 51.5074 to -0.0990))
    }

    @Test
    fun travellingBeyondFiveKilometresTriggersRecomputation() {
        // ~15 km east
        assertTrue(LocationRepository.shouldRecompute(london, 51.5074 to 0.0885))
    }

    @Test
    fun crossingContinentsAlwaysTriggersRecomputation() {
        assertTrue(LocationRepository.shouldRecompute(london, 21.4225 to 39.8262))
    }

    @Test
    fun theSamePointNeverTriggersRecomputation() {
        assertFalse(LocationRepository.shouldRecompute(london, 51.5074 to -0.1278))
    }

    private class FakeLocationProvider(
        private val coordinates: Pair<Double, Double>?,
    ) : LocationProvider {
        override suspend fun permission(): LocationPermission = LocationPermission.GRANTED
        override suspend fun requestPermission(): LocationPermission = LocationPermission.GRANTED
        override suspend fun currentCoordinates(): Pair<Double, Double>? = coordinates
    override suspend fun lastKnownCoordinates(): Pair<Double, Double>? = currentCoordinates()
    }

    private val citiesCsv = """
        id,name,region,country,countryCode,lat,lon,tz
        2643743,London,England,United Kingdom,GB,51.50853,-0.12574,Europe/London
        6058560,London,Ontario,Canada,CA,42.98339,-81.23304,America/Toronto
        360630,Cairo,Cairo Governorate,Egypt,EG,30.06263,31.24967,Africa/Cairo
    """.trimIndent()

    @Test
    fun resolveGpsLocationKeepsExactCoordinatesButBorrowsNearestCityNameAndCode() = runTest {
        // A GPS fix a short distance from central London, UK — not exactly on the bundled
        // city's own coordinates, which is the point: the fix itself must be preserved exactly.
        val fixLatitude = 51.5
        val fixLongitude = -0.12
        val repo = LocationRepository(FakeLocationProvider(fixLatitude to fixLongitude))
        val cityRepository = CityRepository(loadCsv = { citiesCsv })

        val resolved = repo.resolveGpsLocation(cityRepository)

        assertEquals(fixLatitude, resolved?.latitude)
        assertEquals(fixLongitude, resolved?.longitude)
        assertEquals("London", resolved?.cityName)
        assertEquals("GB", resolved?.countryCode)
    }

    @Test
    fun resolveGpsLocationReturnsNullWhenNoFixIsAvailable() = runTest {
        val repo = LocationRepository(FakeLocationProvider(null))
        val cityRepository = CityRepository(loadCsv = { citiesCsv })

        assertEquals(null, repo.resolveGpsLocation(cityRepository))
    }
}
