package world.taqwa.app.domain

/**
 * Where the stored [GeoLocation] came from, and so what "Use my location" reads back as.
 *
 * It cannot be inferred from the location itself. Task 14 tried — a GPS fix carries no city name,
 * so "cityName == null" stood in for "GPS" — and it was wrong in both directions: a fix taken
 * near a bundled city gets that city's name attached (`LocationRepository.resolveGpsLocation`),
 * and a user who granted the permission during onboarding and then chose a city by hand still
 * satisfied the old test. The toggle therefore showed OFF for most people who had it on, which is
 * what the owner saw on his phone.
 *
 * [MANUAL] is the default: before anything is stored there is no fix, and a user who has never
 * been asked has not turned GPS on.
 */
enum class LocationSource { GPS, MANUAL }
