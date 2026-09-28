package world.taqwa.app.domain

/**
 * Where prayer times are computed for. [cityName] is deliberately the **English** snapshot taken
 * when the location was stored, so everything that already reads it keeps working and a location
 * whose city is not (or no longer) in the bundle still has something to show.
 *
 * [cityId] is the GeoNames id of the bundled city this location came from, and is what lets the
 * name be re-rendered in the reader's own language on every language change. It is null for a
 * location stored by a build older than this one (resolved once from the coordinates and written
 * back — see `TodayViewModel.migrateCityId`) and for a fix whose coordinates match no bundled
 * city at all, which simply keeps showing [cityName].
 *
 * [region] is that city's first-level region (admin-1) as the bundled city list spells it
 * ("Béjaïa", "Punjab"): where an authority's units are its regions, as Algeria's wilayas are, the
 * prayer engine decides by it (ruling R32). Null for a location stored before regions were, until
 * the Prayer screen backfills it from the nearest city; [countryCode] likewise, and the engine
 * then reads the country from the zone.
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val cityName: String? = null,
    val countryCode: String? = null,
    val cityId: Int? = null,
    val region: String? = null,
)
