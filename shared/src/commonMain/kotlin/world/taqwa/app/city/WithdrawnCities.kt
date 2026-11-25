package world.taqwa.app.city

import kotlin.math.abs

/**
 * Rows the city list once held and no longer does because the row's point was not its town's
 * (`DROPPED` in `tools/build-city-db.py`, the same rows; `WithdrawnCitiesTest` holds the two
 * together).
 *
 * Leaving a row out of the list stops anyone picking it. It does nothing for the person who
 * picked it before: their phone still holds the row's point under the town's name, and at that
 * point the engine finds whatever stands there, which for Batang, Central Java is Kab. Jepara's
 * checked Kemenag table, shown with no label under Batang's name. A stored location that was
 * picked from a withdrawn row is therefore read as no location at all
 * ([world.taqwa.app.settings.SettingsRepository.location]) and the app asks for the place again:
 * showing another regency's table as the town's own is the one thing it must not do, and nothing
 * the app holds says where the town really is.
 */
object WithdrawnCities {
    /** One withdrawn row: its GeoNames id, the English name it was stored under and the point it had. */
    class Row(val id: Int, val name: String, val latitude: Double, val longitude: Double)

    val rows: List<Row> = listOf(
        // Kab. Batang's town; GeoNames' point is in Kab. Jepara, inside its Kemenag circle (ruling R212).
        Row(id = 1649881, name = "Batang", latitude = -6.4846, longitude = 110.7083),
    )

    /**
     * Whether a stored location is one picked from a withdrawn row: it stands at the row's point
     * (as stored, to three decimals) and carries the row's id or its name. The name counts beside
     * the id because an install from before ids were stored has only the name, and the id it was
     * later given is its nearest neighbour's. A GPS fix carries no name and, with the row gone,
     * cannot be given its id, so a person really standing there keeps their location.
     */
    fun picked(cityId: Int?, cityName: String?, latitude: Double, longitude: Double): Boolean =
        rows.any { row ->
            (cityId == row.id || cityName == row.name) &&
                abs(latitude - row.latitude) <= STORED_STEP && abs(longitude - row.longitude) <= STORED_STEP
        }

    /** The stored point is the picked one rounded to three decimals; a step of slack on either side. */
    private const val STORED_STEP = 0.0011
}
