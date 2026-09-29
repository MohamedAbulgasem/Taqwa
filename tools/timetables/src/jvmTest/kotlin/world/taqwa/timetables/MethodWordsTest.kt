package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The method description is the app's own sentence (`AboutTimesScreen.methodDescription`), word for word. */
class MethodWordsTest {
    private val strings = AppStrings(TestPaths.appResources)
    private val timetable = Timetable()
    private val date = LocalDate(2026, 9, 29)

    private fun city(slug: String, cc: String, lat: Double, lon: Double, zone: String, admin1: String? = null) = City(
        slug = slug, id = 1, countryCode = cc, region = "europe", latitude = lat, longitude = lon, timeZone = zone,
        languages = listOf("en"), featured = emptySet(), names = mapOf("en" to slug), admin1 = admin1,
    )

    private fun describe(city: City, language: String): String {
        val method = timetable.source(city, date).effective.method!!
        return MethodWords.describe(strings, language, method, Formats(language, city.countryCode))
    }

    @Test fun londonUnifiedIsDescribedWithItsCurvesAndMinutes() {
        val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London", "England")
        assertEquals(
            "dawn (Fajr) at a twilight angle that changes through the year, between 11.9° and 16.3° below the horizon, " +
                "nightfall (Isha) at a twilight angle that changes through the year, between 8.6° and 15.6° below the horizon, " +
                "its own minutes (Sunrise −3, Dhuhr +5, Maghrib +3), " +
                "and each start rounded up to the next minute (sunrise and the end of eating down)",
            describe(london, "en"),
        )
    }

    @Test fun diyanetIsDescribedWithItsDailySunAndAngles() {
        val istanbul = city("istanbul-turkiye", "TR", 41.01384, 28.94966, "Europe/Istanbul", "Istanbul")
        assertEquals(
            "the sun’s position taken once a day, dawn (Fajr) at 18° below the horizon, nightfall (Isha) at 17° below the horizon, " +
                "its own minutes (Sunrise −7, Dhuhr +5, Asr +4, Maghrib +7), " +
                "and each start rounded up to the next minute (sunrise and the end of eating down)",
            describe(istanbul, "en"),
        )
    }

    @Test fun aHalfDegreeAngleKeepsItsDecimal() {
        val cairo = city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo")
        val words = describe(cairo, "en")
        assertTrue(words.contains("dawn (Fajr) at 19.5° below the horizon"), words)
    }

    @Test fun arabicJoinsThePartsWithTheArabicComma() {
        assertEquals("، ", MethodWords.listComma("ar"))
        assertEquals("، ", MethodWords.listComma("ur"))
        assertEquals(", ", MethodWords.listComma("bn"))
        val istanbul = city("istanbul-turkiye", "TR", 41.01384, 28.94966, "Europe/Istanbul", "Istanbul")
        val words = describe(istanbul, "ar")
        assertTrue(words.count { it == '،' } >= 4 && !words.contains(", "), words)
        // The digits are the page's own (Turkish Arabic pages keep Western digits, as the app does).
        assertTrue(words.contains("١٨°") || words.contains("18°"), words)
    }
}
