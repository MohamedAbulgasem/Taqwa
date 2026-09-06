package world.taqwa.app.city

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

    private fun parse(csv: String): List<City> =
        csv.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val f = line.split(",")
                if (f.size < 6) return@mapNotNull null
                City(
                    name = f[0],
                    region = f[1],
                    countryCode = f[2],
                    latitude = f[3].toDoubleOrNull() ?: return@mapNotNull null,
                    longitude = f[4].toDoubleOrNull() ?: return@mapNotNull null,
                    timeZoneId = f[5],
                )
            }
            .toList()
}
