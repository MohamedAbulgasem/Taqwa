package world.taqwa.app.location

import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
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
     *
     * Neither trigger touches GPS when [SettingsRepository.locationSource] reads
     * [LocationSource.MANUAL]: the user picked a city on purpose, and a fix landing behind their
     * back would flip "Use my location" back on and quietly move them elsewhere — the exact
     * silent-overwrite this method exists to prevent (review finding I3). The stored location and
     * source are returned untouched rather than as a hard `null`, matching every other trigger
     * below and [refresh]'s own permission-denied fallback: this method never has the side effect
     * of cancelling a correctly-scheduled notification just because GPS was skipped on purpose.
     *
     * That includes [RescheduleTrigger.TIMEZONE_CHANGED], which is the judgment call: a traveller
     * who picked a city then flew elsewhere keeps the picked city — and so its timezone — until
     * they change it by hand. A manual pick is a statement about *place*, not a snapshot of the
     * device's position when it was made, so a moving IANA zone alone isn't reason enough to
     * override it.
     */
    suspend fun refreshFor(trigger: RescheduleTrigger): GeoLocation? = when (trigger) {
        RescheduleTrigger.APP_FOREGROUND, RescheduleTrigger.TIMEZONE_CHANGED ->
            if (settings.locationSource.first() == LocationSource.MANUAL) {
                settings.location.first()
            } else {
                refresh()
            }
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
        // A silent re-resolve is still a fix, and it has just overwritten whatever city the user
        // picked, so "Use my location" has to say so. Only reached when a fix arrived at all —
        // the early return above is the manually-chosen-city case.
        settings.setLocationSource(LocationSource.GPS)
        // A move across a border is exactly when the country default becomes relevant; it is a
        // no-op once the user has picked a method themselves.
        settings.applyCountryDefaultMethod(next.countryCode)
        return next
    }
}
