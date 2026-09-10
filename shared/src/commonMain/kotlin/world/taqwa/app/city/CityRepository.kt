package world.taqwa.app.city

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import world.taqwa.app.location.LocationRepository

/**
 * Searches the bundled GeoNames extract. The CSV is pre-sorted by population descending, so
 * preserving file order in the results ranks the London everyone means above the others.
 *
 * Alongside the English list it holds **at most one** per-language name map — the one for the
 * language last given to [setLanguage] — loaded lazily and dropped when the language changes
 * (design spec §3). A reader in Arabic pays for Arabic; nobody pays for the other five.
 *
 * Every public method hops to [Dispatchers.Default] before touching the data. Both callers —
 * `App.kt`'s `useGpsFix()` and `CitySearchScreen` — launch from `rememberCoroutineScope()`, i.e.
 * Main, and a `suspend` function inherits its caller's dispatcher: parsing 1.6 MB into ~34k
 * objects, or scanning all of them, on the UI thread is a visible freeze on the first location
 * resolution and on the first keystroke of a search, and an ANR on a slow device.
 *
 * [loadNames] is given a bare language code and returns that file's text, or null when the app
 * bundles nothing for it. It defaults to "no translations", which is what the tests that only
 * care about the English list want.
 */
class CityRepository(
    private val loadCsv: suspend () -> String,
    private val loadNames: suspend (String) -> String? = { null },
) {

    private var cache: List<City>? = null

    /** The language asked for, "" for English or anything the bundle has no file for. */
    private var wantedLanguage: String = ""

    /** The language [localizedNames] was built from; null while nothing has been loaded yet. */
    private var loadedLanguage: String? = null
    private var localizedNames: Map<Int, String> = emptyMap()

    /** Guards the one-time parse so two concurrent callers cannot each build the 34k-row list. */
    private val cacheLock = Mutex()

    /**
     * Points the repository at the interface language, e.g. "ar-LY" or "en-GB". Only the part
     * before the `-` matters, and only the six languages the bundle ships names for load anything.
     * Cheap and idempotent: the file itself is read on the next [search] or [nearest].
     */
    suspend fun setLanguage(languageTag: String) {
        val language = languageTag.substringBefore('-').lowercase()
            .takeIf { it in SUPPORTED_LANGUAGES } ?: ""
        cacheLock.withLock { wantedLanguage = language }
    }

    /**
     * The city list and the name map for the current language, both parsed on first use, under
     * one lock acquisition — [Mutex] is not reentrant, so these cannot be two nested helpers.
     */
    private suspend fun data(): Pair<List<City>, Map<Int, String>> = cacheLock.withLock {
        val cities = cache ?: parse(loadCsv()).also { cache = it }
        val language = wantedLanguage
        if (loadedLanguage != language) {
            localizedNames = if (language.isEmpty()) emptyMap() else parseNames(loadNames(language))
            loadedLanguage = language
        }
        cities to localizedNames
    }

    /**
     * A city matches when its English name **or** its name in the loaded language starts with the
     * query, both folded by [CityText] so marks and harakat never stand between a reader and
     * their own city.
     */
    suspend fun search(query: String, limit: Int = 30): List<City> {
        val q = CityText.fold(query)
        if (q.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            val (cities, names) = data()
            cities.asSequence()
                .mapNotNull { city ->
                    val localized = names[city.id]
                    val matches = CityText.fold(city.name).startsWith(q) ||
                        (localized != null && CityText.fold(localized).startsWith(q))
                    if (matches) city.copy(localizedName = localized) else null
                }
                .take(limit)
                .toList()
        }
    }

    /**
     * The name of one city — the loaded language's, falling back to the English one — or null
     * when the bundle has no city with that id (an id from an older extract, say).
     *
     * The name map is consulted first, so the common case is one hash lookup; only a language
     * with no name for this city, or an English interface, pays the linear scan. That is
     * deliberate: an id→city index would be a second 34k-entry structure held for the life of
     * the process to serve a lookup that happens when the location or the language changes, and
     * both callers ([TodayViewModel] and `App.kt`) cache the string they get back.
     */
    suspend fun displayName(cityId: Int): String? = withContext(Dispatchers.Default) {
        val (cities, names) = data()
        names[cityId] ?: cities.firstOrNull { it.id == cityId }?.name
    }

    /**
     * The nearest bundled city to a raw GPS fix, by great-circle distance. A linear scan over
     * ~34k rows is a few milliseconds — not worth a spatial index for a call that happens once
     * per location resolution. Returns null only when the database itself is empty.
     */
    suspend fun nearest(latitude: Double, longitude: Double): City? =
        withContext(Dispatchers.Default) {
            val (cities, names) = data()
            cities.minByOrNull {
                LocationRepository.distanceMetres(latitude, longitude, it.latitude, it.longitude)
            }?.let { it.copy(localizedName = names[it.id]) }
        }

    private suspend fun parse(csv: String): List<City> = withContext(Dispatchers.Default) {
        csv.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val f = line.split(",")
                if (f.size < 8) return@mapNotNull null
                City(
                    id = f[0].toIntOrNull() ?: return@mapNotNull null,
                    name = f[1],
                    region = f[2],
                    countryName = f[3],
                    countryCode = f[4],
                    latitude = f[5].toDoubleOrNull() ?: return@mapNotNull null,
                    longitude = f[6].toDoubleOrNull() ?: return@mapNotNull null,
                    timeZoneId = f[7],
                )
            }
            .toList()
    }

    /** `id,name`, one line each, commas already stripped from the names by the build script. */
    private suspend fun parseNames(csv: String?): Map<Int, String> {
        if (csv == null) return emptyMap()
        return withContext(Dispatchers.Default) {
            csv.lineSequence()
                .drop(1)
                .mapNotNull { line ->
                    if (line.isBlank()) return@mapNotNull null
                    val comma = line.indexOf(',')
                    if (comma <= 0) return@mapNotNull null
                    val id = line.substring(0, comma).trim().toIntOrNull() ?: return@mapNotNull null
                    val name = line.substring(comma + 1).trim()
                    if (name.isEmpty()) null else id to name
                }
                .toMap()
        }
    }

    private companion object {
        /** The languages `files/city-names-<lang>.csv` exists for. */
        val SUPPORTED_LANGUAGES = setOf("ar", "id", "ur", "bn", "tr", "fr")
    }
}
