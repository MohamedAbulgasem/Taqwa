package world.taqwa.app.city

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the six bundled `city-names-<lang>.csv` files against `cities.csv` itself (design spec
 * §7): every translated name must join onto a city that is actually in the bundle, no id may
 * appear twice, and the handful of cities the design was argued over must resolve to the names
 * it quotes.
 *
 * Lives in `androidUnitTest` — the module's JVM unit test source set, run by
 * `:shared:testDebugUnitTest` — because it reads the real resource files. Compose resources are
 * not on a plain JVM unit test's classpath, so it reads them from the file system relative to the
 * module, exactly as [world.taqwa.app.quran.QuranRepositoryDbTest] reads the bundled `quran.db`.
 */
class CityNamesBundleTest {

    private val languages = listOf("ar", "id", "ur", "bn", "tr", "fr")

    private fun resource(name: String): File {
        val moduleRelative = File("src/commonMain/composeResources/files/$name")
        if (moduleRelative.exists()) return moduleRelative
        val rootRelative = File("shared/src/commonMain/composeResources/files/$name")
        if (rootRelative.exists()) return rootRelative
        error("$name not found at either ${moduleRelative.absolutePath} or ${rootRelative.absolutePath}")
    }

    /** `id,name` — the name may itself contain an Arabic comma (U+060C), so split on the first only. */
    private fun namePairs(language: String): List<Pair<Int, String>> =
        resource("city-names-$language.csv").readText().lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val comma = line.indexOf(',')
                assertTrue(comma > 0, "malformed line in city-names-$language.csv: $line")
                val id = line.substring(0, comma).toIntOrNull()
                assertTrue(id != null, "non-numeric id in city-names-$language.csv: $line")
                id!! to line.substring(comma + 1)
            }
            .toList()

    private val cityIds: Set<Int> by lazy {
        resource("cities.csv").readText().lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.substringBefore(',').toInt() }
            .toSet()
    }

    private fun repository() = CityRepository(
        loadCsv = { resource("cities.csv").readText() },
        loadNames = { language -> resource("city-names-$language.csv").readText() },
    )

    @Test
    fun citiesCsvLeadsWithNumericIdsAndHasNoDuplicates() {
        val ids = resource("cities.csv").readText().lineSequence()
            .drop(1).filter { it.isNotBlank() }
            .map { it.substringBefore(',').toInt() }
            .toList()
        assertTrue(ids.size > 30_000, "the bundle should still hold the full cities15000 extract, got ${ids.size}")
        assertEquals(ids.size, ids.toSet().size, "cities.csv has duplicate ids")
    }

    @Test
    fun everyTranslatedNameJoinsOntoACityInTheBundle() {
        for (language in languages) {
            val pairs = namePairs(language)
            assertTrue(pairs.isNotEmpty(), "city-names-$language.csv is empty")
            val orphans = pairs.map { it.first }.filterNot { it in cityIds }
            assertTrue(orphans.isEmpty(), "city-names-$language.csv has ids not in cities.csv: ${orphans.take(5)}")
        }
    }

    @Test
    fun noNameFileRepeatsAnId() {
        for (language in languages) {
            val ids = namePairs(language).map { it.first }
            assertEquals(ids.size, ids.toSet().size, "city-names-$language.csv has duplicate ids")
        }
    }

    @Test
    fun noTranslatedNameIsBlank() {
        for (language in languages) {
            val blanks = namePairs(language).filter { it.second.isBlank() }
            assertTrue(blanks.isEmpty(), "city-names-$language.csv has blank names: ${blanks.take(5)}")
        }
    }

    @Test
    fun theCitiesTheDesignArguesOverResolveToTheirExpectedNames() = runTest {
        val expected = mapOf(
            "ar" to mapOf(
                3369157 to "كيب تاون",
                2643743 to "لندن",
                2210247 to "طرابلس",
                745044 to "اسطنبول",
                1642911 to "جاكارتا",
            ),
            "tr" to mapOf(2643743 to "Londra", 2210247 to "Trablus", 745044 to "İstanbul"),
            "fr" to mapOf(3369157 to "Le Cap", 2643743 to "Londres", 2210247 to "Tripoli"),
            "bn" to mapOf(745044 to "ইস্তাম্বুল", 1642911 to "জাকার্তা"),
            "id" to mapOf(1642911 to "Jakarta", 745044 to "Istanbul"),
        )
        for ((language, names) in expected) {
            val file = namePairs(language).toMap()
            for ((id, name) in names) {
                assertEquals(name, file[id], "city-names-$language.csv, id $id")
            }
        }
    }

    @Test
    fun arabicTripoliCarriesNoHarakat() {
        val tripoli = namePairs("ar").toMap().getValue(2210247)
        assertEquals("طرابلس", tripoli)
        assertEquals(tripoli, CityText.fold(tripoli), "the stored Arabic name should already be bare")
    }

    @Test
    fun theRealBundleSearchesInArabic() = runTest {
        val repo = repository()
        repo.setLanguage("ar-LY")

        val capeTown = repo.search("كيب").first { it.name == "Cape Town" }
        assertEquals("كيب تاون", capeTown.displayName)

        val tripoli = repo.search("طرابلس").first { it.countryCode == "LY" }
        assertEquals("Tripoli", tripoli.name)
        assertEquals("طرابلس", tripoli.displayName)

        // English keeps working under an Arabic interface, and shows the Arabic name.
        assertEquals("لندن", repo.search("london").first().displayName)
    }

    @Test
    fun theRealBundleFoldsTurkishAndFrenchNames() = runTest {
        val repo = repository()
        repo.setLanguage("tr-TR")
        assertEquals("İstanbul", repo.search("istanbul").first().displayName)

        repo.setLanguage("fr-FR")
        assertEquals("Le Cap", repo.search("le cap").first { it.name == "Cape Town" }.displayName)
        assertEquals("Londres", repo.search("londres").first().displayName)
    }

    @Test
    fun anEnglishInterfaceShowsTheEnglishNames() = runTest {
        val repo = repository()
        assertEquals("London", repo.search("london").first().displayName)
        assertEquals("Cape Town", repo.search("cape town").first().displayName)
    }
}
