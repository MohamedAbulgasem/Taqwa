package world.taqwa.app.city

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The city search's three tiers on the real bundle (the owner's decision of 25 November 2026): a city is found by
 * the start of its name, then by the start of a later word of it, then by its start with every space left out, each
 * tier in the list's own order. Two searches broke that day when city names were put right — Georgetown under
 * Indonesian, «عنتاب» under Arabic — and outside Indonesian "georgetown" had never found George Town.
 *
 * Reads `cities.csv` and the name files from the module's Compose resources, as [CityNamesBundleTest] does.
 */
class CitySearchBundleTest {

    private fun files(): File {
        val moduleRelative = File("src/commonMain/composeResources/files")
        return if (moduleRelative.isDirectory) moduleRelative else File("shared/src/commonMain/composeResources/files")
    }

    private val repo = CityRepository(
        loadCsv = { File(files(), "cities.csv").readText() },
        loadNames = { language -> File(files(), "city-names-$language.csv").readText() },
    )

    /** Each bundled city's place in the list (population descending), by id: the order inside a tier. */
    private val rowOf: Map<Int, Int> by lazy {
        File(files(), "cities.csv").readLines().drop(1).filter { it.isNotBlank() }
            .mapIndexed { row, line -> line.substringBefore(',').toInt() to row }
            .toMap()
    }

    private suspend fun ids(languageTag: String, query: String, limit: Int = 30): List<Int> {
        repo.setLanguage(languageTag)
        return repo.search(query, limit).map { it.id }
    }

    @Test
    fun georgetownFindsGeorgeTownPenangInIndonesian() = runTest {
        // Penang's capital has Indonesian's own spelling, George Town, since 25 November, where GeoNames had
        // Georgetown: a reader who typed it as before found other Georgetowns but not Penang's.
        val found = ids("id-ID", "Georgetown")
        assertTrue(GEORGE_TOWN_PENANG in found, "$found")
    }

    @Test
    fun aLaterWordFindsGaziantepInArabic() = runTest {
        // Gaziantep is «غازي عنتاب» since 25 November, where GeoNames had «عنتاب» alone.
        val found = ids("ar-LY", "عنتاب")
        assertTrue(GAZIANTEP in found, "$found")
    }

    @Test
    fun yorkListsYorkBeforeNewYorkCity() = runTest {
        val found = ids("en", "york")
        val york = found.indexOf(YORK)
        // New York City is thousands of rows above York in the list, but only a later word of its name matches.
        val newYork = found.indexOf(NEW_YORK_CITY)
        assertTrue(york >= 0 && newYork >= 0, "both found: $found")
        assertTrue(york < newYork, "York first: $found")
    }

    @Test
    fun aNameTypedWithoutItsSpacesIsFound() = runTest {
        for ((city, languageTag, query) in listOf(
            Triple(KUALA_LUMPUR, "en", "kualalumpur"),
            Triple(GEORGE_TOWN_PENANG, "en", "georgetown"),
            Triple(CAPE_TOWN, "ar-LY", "كيبتاون"),
        )) {
            val found = ids(languageTag, query)
            assertTrue(city in found, "$languageTag $query: $found")
        }
    }

    @Test
    fun aNameTypedWithItsSpaceStillFindsIt() = runTest {
        val found = ids("en", "george town")
        assertEquals(GEORGE_TOWN_PENANG, found.first())
        // The Georgetowns written as one word follow it, Guyana's capital among them.
        assertTrue(found.indexOf(GEORGETOWN_GUYANA) > 0, "$found")
    }

    /**
     * The order the KDoc promises, checked result by result against the rule itself: each result's tier from its
     * two names, then its place in the list, must rise from one result to the next. A prefix match below a word or
     * spaceless one, a tier out of the list's order or a city listed twice each break the rise.
     */
    @Test
    fun aPrefixMatchIsNeverPushedBelowAWordOrSpacelessMatch() = runTest {
        for ((languageTag, query) in SPANNING) {
            repo.setLanguage(languageTag)
            val q = CityText.fold(query)
            val order = repo.search(query, limit = 300).map { city ->
                val local = city.localizedName?.let { tier(CityText.fold(it), q) } ?: NO_MATCH
                minOf(tier(city.foldedName, q), local) to rowOf.getValue(city.id)
            }
            assertTrue(order.none { it.first == NO_MATCH }, "$languageTag $query: a result that does not match: $order")
            assertEquals(order.sortedWith(compareBy({ it.first }, { it.second })).distinct(), order, "$languageTag $query")
            // And every match is there: the whole list worked out from the files by the rule alone.
            assertEquals(expected(languageTag, query, 300), repo.search(query, limit = 300).map { it.id }, "$languageTag $query")
        }
        // Hand-checked on the bundle: a search in the list's order alone would put San Jose and Cape Town first.
        assertEquals(SANJO, ids("en", "sanjo").first())
        val town = ids("en", "town")
        assertTrue(town.indexOf(TOWNSVILLE) in 0 until town.indexOf(CAPE_TOWN), "Townsville before Cape Town: $town")
    }

    @Test
    fun aBengaliQueryFindsCairoWhicheverWayItsYaIsWritten() = runTest {
        // GeoNames wrote Cairo's ya-nukta precomposed; the bundle holds Unicode's composed form (NFC), and a keyboard
        // may type either.
        for (query in listOf("\u0995\u09BE\u09DF\u09B0\u09CB", "\u0995\u09BE\u09AF\u09BC\u09B0\u09CB")) {
            assertTrue(CAIRO in ids("bn-BD", query), query)
        }
    }

    @Test
    fun noCityAppearsTwiceAndTheLimitHolds() = runTest {
        for ((languageTag, query) in SPANNING) {
            val all = ids(languageTag, query, limit = 300)
            assertEquals(all.distinct(), all, "$languageTag $query")
            // Each limit takes the head of the whole tiered list: no early stop drops a match a later one would show.
            for (limit in listOf(1, 5, 30)) {
                assertEquals(all.take(limit), ids(languageTag, query, limit), "$languageTag $query, limit $limit")
            }
        }
        // Under Indonesian New York City matches by both its names, its English one's start and its Indonesian one's
        // (Kota New York) later word: it is listed once, at the first tier.
        val found = ids("id-ID", "new york")
        assertEquals(NEW_YORK_CITY, found.first())
        assertEquals(1, found.count { it == NEW_YORK_CITY })
    }

    /** The rule applied by brute force to the bundle's files: every match by its better tier, then its row. */
    private fun expected(languageTag: String, query: String, limit: Int): List<Int> {
        val q = CityText.fold(query)
        val language = languageTag.substringBefore('-').lowercase()
        val local: Map<Int, String> = if (language == "en") emptyMap() else
            File(files(), "city-names-$language.csv").readLines().drop(1).filter { it.isNotBlank() }
                .associate { it.substringBefore(',').toInt() to CityText.fold(it.substringAfter(',')) }
        return File(files(), "cities.csv").readLines().drop(1).filter { it.isNotBlank() }
            .mapIndexedNotNull { row, line ->
                val id = line.substringBefore(',').toInt()
                val best = minOf(tier(CityText.fold(line.split(',')[1]), q), local[id]?.let { tier(it, q) } ?: NO_MATCH)
                if (best == NO_MATCH) null else Triple(best, row, id)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .take(limit)
            .map { it.third }
    }

    /**
     * The tier the rule gives a folded name for a folded query, worked out with plain string functions: 1 the
     * name's start, 2 a later word's (the query after a space), 3 the name's start with no spaces in either.
     */
    private fun tier(name: String, query: String): Int = when {
        name.startsWith(query) -> 1
        " $query" in name -> 2
        name.replace(" ", "").startsWith(query.replace(" ", "")) -> 3
        else -> NO_MATCH
    }

    private companion object {
        const val NO_MATCH = 4

        const val GEORGE_TOWN_PENANG = 1735106
        const val GEORGETOWN_GUYANA = 3378644
        const val GAZIANTEP = 314830
        const val YORK = 2633352
        const val NEW_YORK_CITY = 5128581
        const val KUALA_LUMPUR = 1735161
        const val CAPE_TOWN = 3369157
        const val SANJO = 1852984
        const val TOWNSVILLE = 2146142
        const val CAIRO = 360630

        /** Queries whose matches span the tiers, in the bundle's scripts; "san jo" has more than the default 30. */
        val SPANNING = listOf(
            "en" to "sanjo",
            "en" to "san jo",
            "en" to "town",
            "en" to "santa",
            "en" to "york",
            "id-ID" to "new york",
            "id-ID" to "kota",
            "ar-LY" to "تاون",
            "ar-LY" to "سان",
            "fr-FR" to "saint",
            "tr-TR" to "kent",
            // A city whose local name starts with the query while its English one has it only as a later word.
            "fr-FR" to "novgorod",
            "tr-TR" to "kut",
        )
    }
}
