package world.taqwa.app.city

import world.taqwa.app.domain.GeoLocation

/**
 * One row of the bundled GeoNames extract. [id] is the GeoNames id — the stable key the
 * per-language name files join on — and [localizedName] is this city's name in the language the
 * repository currently has loaded, null when that language has no name for it or when the
 * interface is in English.
 */
data class City(
    val id: Int,
    val name: String,
    val region: String,
    val countryName: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val localizedName: String? = null,
) {
    /** What the reader sees: their language's name, falling back to the English one. */
    val displayName: String get() = localizedName ?: name

    /**
     * `cityName` stays the English snapshot deliberately: everything that already reads it keeps
     * working, and a saved location whose city later leaves the bundle still has something to show.
     */
    fun toGeoLocation() = GeoLocation(
        latitude = latitude,
        longitude = longitude,
        timeZoneId = timeZoneId,
        cityName = name,
        countryCode = countryCode,
    )
}
