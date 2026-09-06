package world.taqwa.app.location

import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.settings.SettingsRepository

/**
 * Keeps the stored location honest after onboarding. Spec §261: times are recomputed when the
 * device has moved more than 5 km from the cached position, or when the IANA timezone changes.
 *
 * [LocationRepository.shouldRecompute] encoded the 5 km rule but nothing ever called it, so a
 * user who set up in Cape Town and flew to Istanbul kept seeing Cape Town's times, in Cape Town's
 * zone, with notifications scheduled against the same stale zone, until they thought to go to
 * Settings → Location → Use my location.
 *
 * The refresh is deliberately silent about permission: [LocationProvider.currentCoordinates]
 * returns null when location was never granted, which is exactly the case of a user who chose a
 * city by hand — their choice must not be overwritten by a fix they never asked for.
 */
class LocationRefresher(
    private val locationRepository: LocationRepository,
    private val cityRepository: CityRepository,
    private val settings: SettingsRepository,
    private val currentZoneId: () -> String = { TimeZone.currentSystemDefault().id },
) {

    /**
     * Refreshes on the two triggers that mean "the world may have moved under us": returning to
     * the foreground, and the system telling us the zone changed. Boot, a settings change or an
     * alarm firing are all about the schedule, not the position, and cost a GPS read for nothing.
     */
    suspend fun refreshFor(trigger: RescheduleTrigger): GeoLocation? = when (trigger) {
        RescheduleTrigger.APP_FOREGROUND, RescheduleTrigger.TIMEZONE_CHANGED -> refresh()
        else -> settings.location.first()
    }

    /** Returns the location now stored — the refreshed one when it moved, the old one otherwise. */
    suspend fun refresh(): GeoLocation? {
        val stored = settings.location.first() ?: return null
        val coordinates = locationRepository.currentCoordinates() ?: return stored
        val zoneId = currentZoneId()
        val moved = LocationRepository.shouldRecompute(stored, coordinates)
        val zoneChanged = zoneId != stored.timeZoneId
        if (!moved && !zoneChanged) return stored

        val (latitude, longitude) = coordinates
        val nearest = cityRepository.nearest(latitude, longitude)
        val next = GeoLocation(
            latitude = latitude,
            longitude = longitude,
            timeZoneId = zoneId,
            cityName = nearest?.name,
            countryCode = nearest?.countryCode,
        )
        settings.setLocation(next)
        // A move across a border is exactly when the country default becomes relevant; it is a
        // no-op once the user has picked a method themselves.
        settings.applyCountryDefaultMethod(next.countryCode)
        return next
    }
}
