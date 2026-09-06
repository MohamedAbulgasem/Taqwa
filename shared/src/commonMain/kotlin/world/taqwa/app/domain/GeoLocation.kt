package world.taqwa.app.domain

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val cityName: String? = null,
    val countryCode: String? = null,
)
