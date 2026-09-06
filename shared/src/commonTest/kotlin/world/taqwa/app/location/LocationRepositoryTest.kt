package world.taqwa.app.location

import world.taqwa.app.domain.GeoLocation
import kotlin.test.Test
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
}
