package world.taqwa.app.location

enum class LocationPermission { NOT_REQUESTED, GRANTED, DENIED }

interface LocationProvider {
    suspend fun permission(): LocationPermission
    suspend fun requestPermission(): LocationPermission
    /** Latitude to longitude, or null when unavailable. */
    suspend fun currentCoordinates(): Pair<Double, Double>?
}

expect fun createLocationProvider(): LocationProvider
