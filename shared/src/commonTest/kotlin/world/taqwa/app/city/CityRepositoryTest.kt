package world.taqwa.app.city

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CityRepositoryTest {

    private val csv = """
        id,name,region,country,countryCode,lat,lon,tz
        2643743,London,England,United Kingdom,GB,51.50853,-0.12574,Europe/London
        3458449,Londrina,Parana,Brazil,BR,-23.31028,-51.16278,America/Sao_Paulo
        6058560,London,Ontario,Canada,CA,42.98339,-81.23304,America/Toronto
        2643736,Londonderry,Northern Ireland,United Kingdom,GB,54.99721,-7.30917,Europe/London
        360630,Cairo,Cairo Governorate,Egypt,EG,30.06263,31.24967,Africa/Cairo
        999999,Nowhereton,,Freedonia,FD,10.0,10.0,Etc/UTC
    """.trimIndent()

    /** Cairo and the London everyone means have Arabic names; the rest deliberately do not. */
    private val arabicNames = """
        id,name
        360630,القاهرة
        2643743,لندن
    """.trimIndent()

    private val frenchNames = """
        id,name
        360630,Le Caire
    """.trimIndent()

    private var namesLoaded = 0

    private fun repository() = CityRepository(
        loadCsv = { csv },
        loadNames = { language ->
            namesLoaded++
            when (language) {
                "ar" -> arabicNames
                "fr" -> frenchNames
                else -> null
            }
        },
    )

    private val repo = repository()

    @Test
    fun searchIsCaseInsensitiveAndPrefixMatched() = runTest {
        val results = repo.search("lond")
        assertEquals(4, results.size)
    }

    @Test
    fun mostPopulousMatchComesFirst() = runTest {
        val first = repo.search("lond").first()
        assertEquals("London", first.name)
        assertEquals("GB", first.countryCode)
    }

    @Test
    fun regionDistinguishesTheElevenLondons() = runTest {
        val londons = repo.search("london").filter { it.name == "London" }
        assertEquals(setOf("England", "Ontario"), londons.map { it.region }.toSet())
    }

    @Test
    fun timezoneSurvivesIntoTheGeoLocation() = runTest {
        val g = repo.search("cairo").first().toGeoLocation()
        assertEquals("Africa/Cairo", g.timeZoneId)
        assertEquals("EG", g.countryCode)
    }

    @Test
    fun blankQueryReturnsNothingRatherThanEverything() = runTest {
        assertTrue(repo.search("   ").isEmpty())
    }

    @Test
    fun limitIsRespected() = runTest {
        assertEquals(2, repo.search("lond", limit = 2).size)
    }

    @Test
    fun unmappedAdmin1CodeYieldsEmptyRegionButStillParses() = runTest {
        val results = repo.search("nowhereton")
        assertEquals(1, results.size)
        val city = results.first()
        assertEquals("", city.region)
        assertEquals("Freedonia", city.countryName)
        assertEquals("FD", city.countryCode)
    }

    @Test
    fun nearestPicksLondonUkOverLondonOntario() = runTest {
        // A GPS fix in central London, UK — much closer to 51.50853,-0.12574 than to the
        // Ontario London on the other side of the Atlantic.
        val nearest = repo.nearest(51.5, -0.12)
        assertEquals("London", nearest?.name)
        assertEquals("GB", nearest?.countryCode)
    }

    @Test
    fun nearestPicksCairoForAPointCloserToCairo() = runTest {
        val nearest = repo.nearest(30.1, 31.3)
        assertEquals("Cairo", nearest?.name)
        assertEquals("EG", nearest?.countryCode)
    }

    @Test
    fun nearestOnEmptyDatabaseReturnsNull() = runTest {
        val empty = CityRepository(loadCsv = { "id,name,region,country,countryCode,lat,lon,tz" })
        assertEquals(null, empty.nearest(51.5, -0.12))
    }

    @Test
    fun anArabicQueryFindsTheCityByItsArabicName() = runTest {
        repo.setLanguage("ar-LY")
        val results = repo.search("القاه")
        assertEquals(1, results.size)
        assertEquals("Cairo", results.first().name)
        assertEquals("القاهرة", results.first().localizedName)
    }

    @Test
    fun anArabicQueryFoldsHarakatAndAlefVariants() = runTest {
        repo.setLanguage("ar")
        // Typed without the taa marbuta and with a hamza-less alef — still Cairo.
        assertEquals("Cairo", repo.search("القاهره").firstOrNull()?.name)
    }

    @Test
    fun englishStillMatchesUnderAnArabicInterfaceAndShowsTheArabicName() = runTest {
        repo.setLanguage("ar")
        val cairo = repo.search("cairo").single()
        assertEquals("Cairo", cairo.name)
        assertEquals("القاهرة", cairo.displayName)
    }

    @Test
    fun aCityWithNoTranslatedNameFallsBackToEnglish() = runTest {
        repo.setLanguage("ar")
        val londrina = repo.search("londrina").single()
        assertEquals(null, londrina.localizedName)
        assertEquals("Londrina", londrina.displayName)
    }

    @Test
    fun anEnglishInterfaceLoadsNoNamesAtAll() = runTest {
        repo.setLanguage("en-GB")
        assertEquals("Cairo", repo.search("cairo").single().displayName)
        assertEquals(0, namesLoaded)
    }

    @Test
    fun aLanguageWithNoBundledFileSimplyHasNoTranslations() = runTest {
        repo.setLanguage("de-DE")
        assertEquals("Cairo", repo.search("cairo").single().displayName)
        assertEquals(0, namesLoaded, "German is not one of the six, so no file should be read")
    }

    @Test
    fun rankingAndLimitAreUnchangedByTheTranslations() = runTest {
        repo.setLanguage("ar")
        val results = repo.search("lond")
        assertEquals(4, results.size)
        assertEquals(listOf("England", "Parana", "Ontario", "Northern Ireland"), results.map { it.region })
        assertEquals("لندن", results.first().displayName)
        assertEquals(2, repo.search("lond", limit = 2).size)
    }

    @Test
    fun changingLanguageDropsAndReloadsTheNameMap() = runTest {
        repo.setLanguage("ar")
        assertEquals("القاهرة", repo.search("cairo").single().displayName)
        assertEquals(1, namesLoaded)

        // The same language again must not re-read the file...
        repo.setLanguage("ar-EG")
        assertEquals("القاهرة", repo.search("cairo").single().displayName)
        assertEquals(1, namesLoaded)

        // ...but a different one must, and the Arabic map must be gone.
        repo.setLanguage("fr")
        val cairo = repo.search("cairo").single()
        assertEquals("Le Caire", cairo.displayName)
        assertEquals(2, namesLoaded)
        assertEquals(null, repo.search("lond").first().localizedName)
    }

    @Test
    fun searchFoldsMarksInTheEnglishNamesToo() = runTest {
        val marked = CityRepository(loadCsv = {
            """
                id,name,region,country,countryCode,lat,lon,tz
                745044,İstanbul,Istanbul,Turkey,TR,41.01384,28.94966,Europe/Istanbul
                2657896,Zürich,Zurich,Switzerland,CH,47.36667,8.55,Europe/Zurich
            """.trimIndent()
        })
        assertEquals("İstanbul", marked.search("istanbul").single().name)
        assertEquals("Zürich", marked.search("zurich").single().name)
    }

    @Test
    fun nearestCarriesTheLocalizedNameToo() = runTest {
        repo.setLanguage("ar")
        val nearest = repo.nearest(30.1, 31.3)
        assertEquals("Cairo", nearest?.name)
        assertEquals("القاهرة", nearest?.displayName)
    }

    @Test
    fun aPunctuationOnlyQueryMatchesNothingWithoutBecomingABlankQuery() = runTest {
        assertTrue(repo.search("...").isEmpty())
    }
}
