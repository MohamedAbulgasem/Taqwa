package world.taqwa.app.city

import world.taqwa.app.domain.GeoLocation

data class City(
    val name: String,
    val region: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
) {
    fun toGeoLocation() = GeoLocation(
        latitude = latitude,
        longitude = longitude,
        timeZoneId = timeZoneId,
        cityName = name,
        countryCode = countryCode,
    )
}
