package world.taqwa.app.city

import world.taqwa.app.location.LocationRepository

/**
 * Searches the bundled GeoNames extract. The CSV is pre-sorted by population descending, so
 * preserving file order in the results ranks the London everyone means above the others.
 */
class CityRepository(private val loadCsv: suspend () -> String) {

    private var cache: List<City>? = null

    private suspend fun cities(): List<City> = cache ?: parse(loadCsv()).also { cache = it }

    suspend fun search(query: String, limit: Int = 30): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return cities().asSequence()
            .filter { it.name.lowercase().startsWith(q) }
            .take(limit)
            .toList()
    }

    /**
     * The nearest bundled city to a raw GPS fix, by great-circle distance. A linear scan over
     * ~34k rows is a few milliseconds — not worth a spatial index for a call that happens once
     * per location resolution. Returns null only when the database itself is empty.
     */
    suspend fun nearest(latitude: Double, longitude: Double): City? =
        cities().minByOrNull {
            LocationRepository.distanceMetres(latitude, longitude, it.latitude, it.longitude)
        }

    private fun parse(csv: String): List<City> =
        csv.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val f = line.split(",")
                if (f.size < 7) return@mapNotNull null
                City(
                    name = f[0],
                    region = f[1],
                    countryName = f[2],
                    countryCode = f[3],
                    latitude = f[4].toDoubleOrNull() ?: return@mapNotNull null,
                    longitude = f[5].toDoubleOrNull() ?: return@mapNotNull null,
                    timeZoneId = f[6],
                )
            }
            .toList()
}
