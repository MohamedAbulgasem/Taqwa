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
    /**
     * [name] folded once, when the row is parsed, rather than on every keystroke: a search
     * compares the folded query against all ~34k of these, and folding them per keystroke was
     * 34k `fold` calls and 34k `StringBuilder`s per character typed.
     *
     * Outside the constructor deliberately — it is derived from [name], so it stays out of
     * `equals`, `hashCode`, `toString` and `copy`'s parameter list, and `copy` recomputes it.
     */
    val foldedName: String = CityText.fold(name)

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
        cityId = id,
    )
}
