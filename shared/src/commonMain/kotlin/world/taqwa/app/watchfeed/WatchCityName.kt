package world.taqwa.app.watchfeed

import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.i18n.PlatformFormat

/**
 * The city's name as the Prayer screen shows it, for the feed's `place.city` (Ruling 39 allows the
 * city, and the Lead's call 5 sends it): the path of `PrayerViewModel.resolveCityName`, with the
 * format's tag and not the interface language, so that the watch names the place as the app does.
 *
 * - With a city id, the bundle's name in the format's language (`setLanguage`, then
 *   `displayName`), which is the list's own name for a language the bundle has no file for;
 * - when the bundle has no such city, or the location no id, the name stored with the location;
 * - when there is none either, "".
 *
 * The first lookup in a process parses the bundled list, which the app's own comment puts at about
 * 0.4 s on a phone: it runs inside the ask's budget, on the sync's worker.
 */
internal suspend fun watchCityName(location: GeoLocation, format: PlatformFormat, cities: CityRepository): String {
    val bundled = location.cityId?.let { id ->
        cities.setLanguage(format.languageTag())
        cities.displayName(id)
    }
    return bundled ?: location.cityName ?: ""
}
