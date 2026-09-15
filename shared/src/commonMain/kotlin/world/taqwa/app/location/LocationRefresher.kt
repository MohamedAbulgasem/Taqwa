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
     * Asks for a fresh fix on the two triggers that mean "the world may have moved under us":
     * returning to the foreground, and the system telling us the zone changed. The background
     * wake-ups read the phone's cached position instead (spec §16.5). Boot, a settings change and
     * a time change are about the schedule, not the position, and cost nothing.
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
        // The wake-ups the app gets while closed (spec §16.5): iOS's refresh task, Android's
        // prayer alarm and top-up. No fix is asked for — a closed app may not be allowed one —
        // but the position the phone last knew is enough to notice a journey, and the
        // notifications then move with it instead of waiting for the app to be opened.
        RescheduleTrigger.BACKGROUND_REFRESH, RescheduleTrigger.ALARM_FIRED ->
            if (settings.locationSource.first() == LocationSource.MANUAL) {
                settings.location.first()
            } else {
                refresh(fresh = false)
            }
        else -> settings.location.first()
    }

    /**
     * Whether the phone's last known position is more than the recompute distance from the
     * stored one, without asking for a fix. False under a manual city, without permission, or
     * with nothing cached. For a wake-up that would otherwise leave the schedule alone (Android's
     * prayer alarm reschedules only when the window has drained), this is the reason to rebuild.
     */
    suspend fun hasMoved(): Boolean {
        if (settings.locationSource.first() == LocationSource.MANUAL) return false
        val stored = settings.location.first() ?: return false
        val cached = locationRepository.lastKnownCoordinates() ?: return false
        return LocationRepository.shouldRecompute(stored, cached)
    }

    /**
     * Returns the location now stored — the refreshed one when it moved, the old one otherwise.
     * [fresh] asks the platform for a new fix; false reads only what it last knew.
     */
    suspend fun refresh(fresh: Boolean = true): GeoLocation? {
        val stored = settings.location.first() ?: return null
        val coordinates = (if (fresh) locationRepository.currentCoordinates() else locationRepository.lastKnownCoordinates())
            ?: return stored
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
            cityId = nearest?.id,
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
