package world.taqwa.app.location

enum class LocationPermission { NOT_REQUESTED, GRANTED, DENIED }

interface LocationProvider {
    suspend fun permission(): LocationPermission
    suspend fun requestPermission(): LocationPermission
    /** Latitude to longitude, or null when unavailable. */
    suspend fun currentCoordinates(): Pair<Double, Double>?

    /**
     * The platform's last known position without asking for a new fix — nothing lights up, nothing
     * waits. For the background wake-ups (spec §16.5): a background task may not be allowed a
     * fresh fix at all, and a cached one from the last time anything on the phone asked is enough
     * to notice a move of hundreds of kilometres. Null without permission or without a cache.
     */
    suspend fun lastKnownCoordinates(): Pair<Double, Double>?
}

expect fun createLocationProvider(): LocationProvider
