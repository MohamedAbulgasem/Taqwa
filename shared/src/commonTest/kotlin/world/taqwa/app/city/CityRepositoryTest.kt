package world.taqwa.app.city

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CityRepositoryTest {

    private val csv = """
        name,region,country,countryCode,lat,lon,tz
        London,England,United Kingdom,GB,51.50853,-0.12574,Europe/London
        Londrina,Parana,Brazil,BR,-23.31028,-51.16278,America/Sao_Paulo
        London,Ontario,Canada,CA,42.98339,-81.23304,America/Toronto
        Londonderry,Northern Ireland,United Kingdom,GB,54.99721,-7.30917,Europe/London
        Cairo,Cairo Governorate,Egypt,EG,30.06263,31.24967,Africa/Cairo
        Nowhereton,,Freedonia,FD,10.0,10.0,Etc/UTC
    """.trimIndent()

    private val repo = CityRepository { csv }

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
}
