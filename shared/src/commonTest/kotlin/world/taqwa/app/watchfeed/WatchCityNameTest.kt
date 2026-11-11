package world.taqwa.app.watchfeed

import kotlinx.coroutines.test.runTest
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The city the feed names: the Prayer screen's own name for it (`PrayerViewModel.resolveCityName`),
 * looked up in the format's language, so that the watch names the place as the app does.
 */
class WatchCityNameTest {

    private val csv = """
        id,name,region,country,countryCode,lat,lon,tz
        1,Cape Town,Western Cape,South Africa,ZA,-33.92584,18.42322,Africa/Johannesburg
        2,London,England,United Kingdom,GB,51.50853,-0.12574,Europe/London
    """.trimIndent()

    private val arabicNames = """
        id,name
        1,كيب تاون
    """.trimIndent()

    private fun cities() = CityRepository(
        loadCsv = { csv },
        loadNames = { language -> if (language == "ar") arabicNames else null },
    )

    private fun format(tag: String) = DigitSetFormat(DigitSets.WESTERN, tag)

    private val capeTown = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", cityName = "Cape Town", cityId = 1)

    @Test
    fun theCityIsThePrayerScreensName() = runTest {
        assertEquals("كيب تاون", watchCityName(capeTown, format("ar-SA"), cities()))
        assertEquals("Cape Town", watchCityName(capeTown, format("en-US"), cities()))
        // One repository, asked in one language and then another, as the app's single one is.
        val shared = cities()
        assertEquals("كيب تاون", watchCityName(capeTown, format("ar-SA"), shared))
        assertEquals("Cape Town", watchCityName(capeTown, format("en-US"), shared))
    }

    @Test
    fun aTagTheBundleHasNoNamesForGivesTheListsName() = runTest {
        assertEquals("Cape Town", watchCityName(capeTown, format("he-IL"), cities()))
    }

    @Test
    fun aCityTheListLacksFallsBackToTheStoredName() = runTest {
        val gone = GeoLocation(10.0, 10.0, "Etc/UTC", cityName = "Somewhere", cityId = 99)
        assertEquals("Somewhere", watchCityName(gone, format("ar-SA"), cities()))
    }

    @Test
    fun withoutAnIdTheStoredNameIsSent() = runTest {
        val london = GeoLocation(51.5074, -0.1278, "Europe/London", cityName = "London")
        assertEquals("London", watchCityName(london, format("ar-SA"), cities()))
        assertEquals("", watchCityName(GeoLocation(51.5074, -0.1278, "Europe/London"), format("en-US"), cities()))
        assertEquals("", watchCityName(GeoLocation(10.0, 10.0, "Etc/UTC", cityId = 99), format("en-US"), cities()))
    }
}
