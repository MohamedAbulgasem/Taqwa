package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DocumentTest {

    private fun city(
        slug: String,
        country: String,
        lat: Double,
        lon: Double,
        zone: String,
        names: Map<String, String>,
        featured: Set<String> = emptySet(),
    ) = City(
        slug = slug, id = 1, countryCode = country, region = "north-africa", latitude = lat, longitude = lon,
        timeZone = zone, languages = names.keys.toList(), madhab = AsrMadhab.STANDARD, featured = featured,
        names = names,
    )

    private val tripoli = city(
        "tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli",
        linkedMapOf("en" to "Tripoli", "ar" to "طرابلس"), featured = setOf("ar", "en"),
    )
    private val cairo = city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo", linkedMapOf("en" to "Cairo", "ar" to "القاهرة"))
    private val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London", linkedMapOf("en" to "London"))
    private val newYork = city("new-york-usa", "US", 40.71427, -74.00597, "America/New_York", linkedMapOf("en" to "New York"))

    private val document = Document(AppStrings(TestPaths.appResources))
    private val friday = Instant.parse("2026-09-25T00:07:00Z")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String) = getValue(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = getValue(key) as List<Map<String, Any?>>

    private fun page(city: City, language: String, now: Instant = friday) =
        document.city(city, now).map("pages").map(language)

    private fun dayIndex(date: LocalDate) = date.day - 1

    @Test
    fun aLibyanArabicPageReadsTheAppsTimesInLatinDigits() {
        val page = page(tripoli, "ar")
        assertEquals("0123456789", page["digits"])
        val times = page.list("days")[dayIndex(LocalDate(2026, 9, 13))]["times"] as List<*>
        assertEquals(listOf("5:26", "13:04", "16:34", "19:16", "20:35"), listOf(0, 2, 3, 4, 5).map { times[it] })
    }

    @Test
    fun anEgyptianArabicPageReadsArabicIndicDigits() {
        val page = page(cairo, "ar")
        assertEquals("٠١٢٣٤٥٦٧٨٩", page["digits"])
        val dhuhr = (page.list("days")[0]["times"] as List<*>)[2] as String
        assertTrue(dhuhr.matches(Regex("[٠-٩]{1,2}:[٠-٩]{2}")), dhuhr)
    }

    @Test
    fun theEpochsAreTheInstantsTheTimesWereFormattedFrom() {
        val day = document.city(tripoli, friday).list("days")[dayIndex(LocalDate(2026, 9, 13))]
        val expected = Timetable().day(tripoli, LocalDate(2026, 9, 13))
        assertEquals(Prayer.entries.map { expected.times.getValue(it).epochSeconds }, day["epochs"])
        assertEquals("2026-09-13", day["date"])
    }

    @Test
    fun namesAndSentencesComeFromTheApp() {
        val page = page(tripoli, "en")
        assertEquals(listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"), page["prayers"])
        assertEquals("Asr in", (page["nextIn"] as List<*>)[3])
        assertEquals("Muslim World League", page["method"])
        assertEquals("Standard", page["madhab"])
        assertEquals("109° · 2,916 km to Makkah", page["qiblaDetail"])
        assertEquals("Tripoli", page["city"])
        assertEquals("Libya", page["country"])
        assertEquals("طرابلس", page(tripoli, "ar")["city"])
        assertEquals("ليبيا", page(tripoli, "ar")["country"])
    }

    @Test
    fun theTodayBlockIsTheCitysOwnDay() {
        val today = page(tripoli, "en").map("today")
        assertEquals("Friday", today["weekday"])
        assertEquals("25 September 2026", today["date"])
        assertEquals("12 Rabi’ al-Thani 1448", today["hijri"])
        val full = today["full"] as String
        assertTrue(full.startsWith("Friday") && full.endsWith("25 September 2026"), full)
        assertEquals("25 Eylül 2026 Cuma", page(city("istanbul", "TR", 41.01384, 28.94966, "Europe/Istanbul", linkedMapOf("en" to "Istanbul", "tr" to "İstanbul")), "tr").map("today")["full"])

        val november = Instant.parse("2026-11-01T00:07:00Z")
        assertEquals("2026-10-31", document.city(newYork, november)["today"])
        assertEquals("October 31, 2026", page(newYork, "en", november).map("today")["date"])
    }

    @Test
    fun theMonthsCarryTheirGregorianAndHijriTitles() {
        val months = page(tripoli, "en").list("months")
        assertEquals("September 2026", months[0]["title"])
        assertEquals("Rabi’ al-Awwal – Rabi’ al-Thani 1448", months[0]["hijri"])
        assertEquals("October 2026", months[1]["title"])
        val facts = document.city(tripoli, friday).list("months")
        assertEquals(listOf(30, 31), facts.map { it["days"] })
    }

    @Test
    fun aClockChangeIsReportedOnTheFirstDayOnTheNewClock() {
        val changes = page(london, "en", Instant.parse("2026-10-02T00:07:00Z")).list("clockChanges")
        assertEquals(1, changes.size)
        assertEquals(24, changes[0]["index"])
        assertEquals("25 October 2026", changes[0]["date"])
        assertEquals("UTC", changes[0]["offset"])
    }

    @Test
    fun theFeaturedLanguagesFollowThePageOrder() {
        assertEquals(listOf("en", "ar"), document.city(tripoli, friday)["featured"])
    }

    @Test
    fun fridaysAreMarkedInTheFacts() {
        val days = document.city(tripoli, friday).list("days")
        assertEquals(true, days[dayIndex(LocalDate(2026, 9, 25))]["friday"])
        assertEquals(false, days[dayIndex(LocalDate(2026, 9, 24))]["friday"])
    }
}
