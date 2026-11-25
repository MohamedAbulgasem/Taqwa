package world.taqwa.app.city

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.prayer.FixCountry
import kotlin.concurrent.Volatile

/**
 * Searches the bundled GeoNames extract. The CSV is pre-sorted by population descending, so
 * preserving file order within each of [search]'s tiers ranks the London everyone means above
 * the others.
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
    @Volatile
    private var wantedLanguage: String = ""

    /** The language [localizedNames] was built from; null while nothing has been loaded yet. */
    private var loadedLanguage: String? = null
    private var localizedNames: Map<Int, String> = emptyMap()

    /**
     * [localizedNames] folded, keyed the same way. Held alongside the display forms rather than
     * folded on demand for the same reason [City.foldedName] exists: a keystroke would otherwise
     * fold every translated name it walks past, and there are up to ~9k of them.
     */
    private var foldedNames: Map<Int, String> = emptyMap()

    /** What [data] last returned, with its language: read without the lock by [loadedDisplayName]. */
    @Volatile
    private var snapshot: Loaded? = null

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
     * The city list and the current language's names — display forms and folded forms both —
     * parsed on first use under one lock acquisition: [Mutex] is not reentrant, so these cannot
     * be two nested helpers.
     */
    private suspend fun data(): Loaded = cacheLock.withLock {
        val cities = cache ?: parse(loadCsv()).also { cache = it }
        val language = wantedLanguage
        if (loadedLanguage != language) {
            localizedNames = if (language.isEmpty()) emptyMap() else parseNames(loadNames(language))
            foldedNames = localizedNames.mapValues { (_, name) -> CityText.fold(name) }
            loadedLanguage = language
        }
        Loaded(cities, localizedNames, foldedNames, language).also { snapshot = it }
    }

    /** The three structures a search needs, resolved together under one lock acquisition. */
    private class Loaded(
        val cities: List<City>,
        val names: Map<Int, String>,
        val folded: Map<Int, String>,
        val language: String,
    )

    /**
     * A city matches when its English name **or** its name in the loaded language answers the
     * query, both folded by [CityText] so marks and harakat never stand between a reader and
     * their own city, in one of three tiers:
     *
     * 1. the name starts with the query: "york" finds York;
     * 2. a later word of the name does, the query following one of its spaces (a hyphen folds to
     *    one): "york" finds New York City, «عنتاب» finds «غازي عنتاب»;
     * 3. the name starts with the query once every space is left out of both: "georgetown" finds
     *    George Town, "george town" finds Georgetown, "kualalumpur" finds Kuala Lumpur.
     *
     * A city takes the better tier of its two names and is listed once. The results are every
     * tier-1 match in the list's order, then tier 2's, then tier 3's, at most [limit] in all, so a
     * later word or a name run together never pushes out a city whose name starts with what was
     * typed. The scan stops early only once it holds [limit] tier-1 matches: nothing further down
     * the list can come before those.
     */
    suspend fun search(query: String, limit: Int = 30): List<City> {
        val q = CityText.fold(query)
        if (q.isEmpty()) return emptyList()
        // Tier 3's query, its spaces taken out once per search; a name's own are skipped as it is read.
        val squeezed = q.replace(" ", "")
        return withContext(Dispatchers.Default) {
            val loaded = data()
            val nameStarts = ArrayList<City>()
            val wordStarts = ArrayList<City>()
            val spacelessStarts = ArrayList<City>()
            for (city in loaded.cities) {
                // Both sides are already folded — the query once, above; the names once, at parse
                // time — so the scan is comparisons, and makes no string for a city it passes.
                val english = tier(city.foldedName, q, squeezed)
                val local = loaded.folded[city.id]
                val best = when {
                    english == NAME_START || local == null -> english
                    else -> minOf(english, tier(local, q, squeezed))
                }
                when (best) {
                    NAME_START -> {
                        nameStarts += city
                        if (nameStarts.size >= limit) break
                    }
                    WORD_START -> if (wordStarts.size < limit) wordStarts += city
                    SPACELESS_START -> if (spacelessStarts.size < limit) spacelessStarts += city
                }
            }
            (nameStarts + wordStarts + spacelessStarts).take(limit)
                .map { it.copy(localizedName = loaded.names[it.id]) }
        }
    }

    /**
     * The tier of [search] in which the folded [name] answers the folded query [q]: [NAME_START],
     * [WORD_START], [SPACELESS_START] or [NO_MATCH]. [squeezed] is [q] without its spaces.
     * Comparisons only, with no new string: it runs for every city on every keystroke.
     */
    private fun tier(name: String, q: String, squeezed: String): Int {
        if (name.startsWith(q)) return NAME_START
        // Each later word follows exactly one space: CityText.fold collapses runs and trims both ends.
        var space = name.indexOf(' ')
        while (space >= 0) {
            if (name.startsWith(q, space + 1)) return WORD_START
            space = name.indexOf(' ', space + 1)
        }
        // The name read with its spaces skipped, against the query without its own.
        var i = 0
        for (ch in squeezed) {
            while (i < name.length && name[i] == ' ') i++
            if (i == name.length || name[i] != ch) return NO_MATCH
            i++
        }
        return SPACELESS_START
    }

    /**
     * [displayName] from what is already loaded, without suspending: null until a lookup has read the list in the
     * current language. For a screen's first frame, so that it never shows one name and then another (About's unit
     * city: by the time About opens, the app has read the list for the location's own name).
     */
    fun loadedDisplayName(cityId: Int): String? {
        val loaded = snapshot?.takeIf { it.language == wantedLanguage } ?: return null
        return loaded.names[cityId] ?: loaded.cities.firstOrNull { it.id == cityId }?.name
    }

    /**
     * The name of one city — the loaded language's, falling back to the English one — or null
     * when the bundle has no city with that id (an id from an older extract, say).
     *
     * The name map is consulted first, so the common case is one hash lookup; only a language
     * with no name for this city, or an English interface, pays the linear scan. That is
     * deliberate: an id→city index would be a second 34k-entry structure held for the life of
     * the process to serve a lookup that happens when the location or the language changes, and
     * both callers ([PrayerViewModel] and `App.kt`) cache the string they get back.
     */
    suspend fun displayName(cityId: Int): String? = withContext(Dispatchers.Default) {
        val loaded = data()
        loaded.names[cityId] ?: loaded.cities.firstOrNull { it.id == cityId }?.name
    }

    /**
     * The bundled city with GeoNames id [cityId], or null when the bundle has none (an id from an
     * older extract). A linear scan, like [displayName]'s fallback: it serves the one-time
     * backfill of a stored location, not a keystroke.
     */
    suspend fun byId(cityId: Int): City? = withContext(Dispatchers.Default) {
        val loaded = data()
        loaded.cities.firstOrNull { it.id == cityId }?.let { it.copy(localizedName = loaded.names[it.id]) }
    }

    /**
     * The nearest bundled city to a raw GPS fix, by great-circle distance. A linear scan over
     * ~34k rows is a few milliseconds — not worth a spatial index for a call that happens once
     * per location resolution. Returns null only when the database itself is empty. Not for a fix's country: across a
     * border the nearest city is often the neighbour's ([forFix], ruling R256).
     */
    suspend fun nearest(latitude: Double, longitude: Double): City? =
        withContext(Dispatchers.Default) {
            val loaded = data()
            loaded.cities.minByOrNull {
                LocationRepository.distanceMetres(latitude, longitude, it.latitude, it.longitude)
            }?.let { it.copy(localizedName = loaded.names[it.id]) }
        }

    /**
     * The bundled cities a raw GPS fix takes its facts from, under the device's time zone [zoneId] ([FixCountry],
     * rulings R256 and R259): the nearest city, whose name, country and region the place takes, and, where the zone
     * belongs to other countries than that city's and one of their cities stands near enough, that city too, whose
     * country the place may be in as well (the engine then holds the day to both). Null only when the database is
     * empty.
     */
    suspend fun forFix(latitude: Double, longitude: Double, zoneId: String?): FixCities? =
        withContext(Dispatchers.Default) {
            val names = data().names
            FixCountry.choice(latitude, longitude, zoneId, fixIndex())?.let { choice ->
                FixCities(
                    city = choice.city.copy(localizedName = names[choice.city.id]),
                    beside = choice.beside?.let { it.copy(localizedName = names[it.id]) },
                )
            }
        }

    /** The latitude-sorted index [forFix] searches, built once on first use (a sort of the ~34k rows). */
    private var fixIndex: FixCountry.Index<City>? = null

    private suspend fun fixIndex(): FixCountry.Index<City> {
        val cities = data().cities
        return cacheLock.withLock {
            fixIndex ?: FixCountry.Index(cities, { it.latitude }, { it.longitude }, { it.countryCode }, { it.timeZoneId })
                .also { fixIndex = it }
        }
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

        /** [search]'s tiers, best first, so the lower of a city's two is the one it is listed by. */
        const val NAME_START = 1
        const val WORD_START = 2
        const val SPACELESS_START = 3
        const val NO_MATCH = 4
    }
}

/**
 * What a GPS fix takes from the city list ([CityRepository.forFix], rulings R256 and R259): [city], the nearest, for
 * the name, country and region; [beside], where the device zone says the place may be in another country, that
 * country's nearest city, for that country and its region (`GeoLocation.alsoCountryCode`).
 */
data class FixCities(val city: City, val beside: City? = null)
