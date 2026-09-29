package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TimetableTest {

    private fun city(
        slug: String,
        country: String,
        lat: Double,
        lon: Double,
        zone: String,
        languages: List<String> = listOf("en"),
    ) = City(
        slug = slug, id = 1, countryCode = country, region = "north-africa", latitude = lat, longitude = lon,
        timeZone = zone, languages = languages, featured = emptySet(),
        names = languages.associateWith { slug },
    )

    private val tripoli = city("tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli", languages = listOf("en", "ar"))
    private val timetable = Timetable()

    private fun clock(city: City, day: TimetableDay, prayer: Prayer): String {
        val local = day.times.getValue(prayer).toLocalDateTime(TimeZone.of(city.timeZone))
        return "${local.hour}:${local.minute.toString().padStart(2, '0')}"
    }

    @Test
    fun tripoliOnThe13thMatchesTheAppsOwnPrayerScreen() {
        // Libya's Awqaf method (18.5°/18.5°, Dhuhr + 4, Maghrib + 5), the engine's Automatic there;
        // the old engine's MWL gave 5:26, 13:04, 16:34, 19:16, 20:35.
        val day = timetable.day(tripoli, LocalDate(2026, 9, 13))
        assertEquals(
            listOf("5:25", "6:47", "13:08", "16:36", "19:21", "20:42"),
            listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).map { clock(tripoli, day, it) },
        )
    }

    @Test
    fun theMonthsAreTheCitysCurrentMonthAndTheNext() {
        val months = timetable.months(tripoli, Instant.parse("2026-09-25T00:07:00Z"))
        assertEquals(listOf(2026 to 9, 2026 to 10), months.map { it.year to it.month })
        assertEquals(30, months[0].days.size)
        assertEquals(31, months[1].days.size)
        assertEquals(LocalDate(2026, 9, 1), months[0].days.first().date)
    }

    @Test
    fun aCityBehindUtcKeepsItsOwnMonthOnTheFirstOfTheNext() {
        val newYork = city("new-york-usa", "US", 40.71427, -74.00597, "America/New_York")
        val months = timetable.months(newYork, Instant.parse("2026-11-01T00:07:00Z"))
        assertEquals(listOf(2026 to 10, 2026 to 11), months.map { it.year to it.month })
        assertEquals(LocalDate(2026, 10, 31), timetable.localToday(newYork, Instant.parse("2026-11-01T00:07:00Z")))
    }

    @Test
    fun londonTimesFollowTheClockChangeAtTheEndOfOctober() {
        val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London")
        val before = timetable.day(london, LocalDate(2026, 10, 24))
        val after = timetable.day(london, LocalDate(2026, 10, 26))
        assertEquals(3600, before.utcOffsetSeconds)
        assertEquals(0, after.utcOffsetSeconds)
        // Dhuhr stays near noon on the local clock on both sides of the change.
        assertTrue(clock(london, before, Prayer.DHUHR).startsWith("12:"), clock(london, before, Prayer.DHUHR))
        assertTrue(clock(london, after, Prayer.DHUHR).startsWith("11:"), clock(london, after, Prayer.DHUHR))
    }

    @Test
    fun hanafiAsrIsLaterThanStandardAsr() {
        val karachi = city("karachi-pakistan", "PK", 24.8608, 67.0104, "Asia/Karachi")
        val date = LocalDate(2026, 9, 25)
        val standard = Timetable(settings = PrayerSettings(school = "standard")).day(karachi, date)
        val hanafi = Timetable(settings = PrayerSettings(school = "hanafi")).day(karachi, date)
        assertTrue(hanafi.times.getValue(Prayer.ASR) > standard.times.getValue(Prayer.ASR))
        // Karachi's own school is Hanafi (spec §3.7), which is what a page shows.
        assertEquals(hanafi.times, timetable.day(karachi, date).times)
        assertEquals("HANAFI", timetable.source(karachi, date).school.name)
    }

    @Test
    fun fridaysAreMarked() {
        assertTrue(timetable.day(tripoli, LocalDate(2026, 9, 25)).friday)
        assertFalse(timetable.day(tripoli, LocalDate(2026, 9, 24)).friday)
    }

    @Test
    fun apiaKeepsEveryTimeOnItsOwnCivilDate() {
        // Apia's clock runs thirteen hours ahead of UTC at a longitude eleven hours behind it; the
        // old engine answered with the next day's times, and the page refused the city. The engine
        // keeps every time on the civil date asked for.
        val apia = city("apia-samoa", "WS", -13.83333, -171.76666, "Pacific/Apia")
        val date = LocalDate(2026, 9, 25)
        val day = timetable.day(apia, date)
        for (prayer in listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB)) {
            assertEquals(date, day.times.getValue(prayer).toLocalDateTime(TimeZone.of(apia.timeZone)).date, "$prayer")
        }
        assertTrue(clock(apia, day, Prayer.DHUHR).startsWith("12:"), clock(apia, day, Prayer.DHUHR))
    }

    @Test
    fun ummAlQuraIshaIsTwoHoursAfterMaghribInRamadan() {
        // Umm al-Qura's Isha is 90 minutes after Maghrib, and 120 in Ramadan: the engine applies
        // it on Umm al-Qura's own Ramadan dates, and the page says so.
        val makkah = city("makkah-saudi-arabia", "SA", 21.42664, 39.82563, "Asia/Riyadh")
        fun interval(day: TimetableDay) = day.times.getValue(Prayer.ISHA) - day.times.getValue(Prayer.MAGHRIB)
        val ramadan = timetable.day(makkah, LocalDate(2027, 2, 15))
        assertEquals(9, ramadan.hijri.month)
        assertEquals(120.minutes, interval(ramadan))
        assertTrue(ramadan.ramadanIsha)
        val ordinary = timetable.day(makkah, LocalDate(2026, 9, 25))
        assertEquals(90.minutes, interval(ordinary))
        assertFalse(ordinary.ramadanIsha)
        // Only Umm al-Qura has the rule: Cairo's Ramadan Isha is the Egyptian angle as ever.
        val cairo = city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo")
        assertFalse(timetable.day(cairo, LocalDate(2027, 2, 15)).ramadanIsha)
    }

    @Test
    fun aCautiousDayCarriesEachMembersOwnSevenInstants() {
        // Toronto combines three timetables: each prayer once all have begun it, so no member's
        // Fajr is after the one shown, and no member's sunrise before it.
        val toronto = city("toronto-canada", "CA", 43.70643, -79.39864, "America/Toronto")
        val day = timetable.day(toronto, LocalDate(2026, 10, 1))
        assertEquals(3, day.members.size)
        assertTrue(day.members.all { it.size == 7 }, day.members.map { it.size }.toString())
        val fajr = day.times.getValue(Prayer.FAJR)
        assertTrue(day.members.all { it[0] <= fajr }, "a member's Fajr after the shown ${day.members.map { it[0] }} > $fajr")
        assertTrue(day.members.all { it[1] >= day.times.getValue(Prayer.SUNRISE) })
        assertTrue(day.members.all { it[6] <= it[0] }, "a member's end of eating after its own Fajr")
        assertEquals(day.members.any { it[4] > day.times.getValue(Prayer.MAGHRIB) }, day.capped)
    }

    @Test
    fun aSingleMethodDayCarriesTheOtherSchoolsAsrAndTheDaysEnds() {
        val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London")
        val day = timetable.day(london, LocalDate(2026, 10, 1))
        assertTrue(day.members.isEmpty())
        assertFalse(day.capped)
        // London Unified leads with the Hanafi Asr (its school is not known), so the other is the earlier Standard one.
        assertTrue(day.asrOther < day.times.getValue(Prayer.ASR), "${day.asrOther} vs ${day.times.getValue(Prayer.ASR)}")
        assertTrue(day.endOfEating <= day.times.getValue(Prayer.FAJR))
        assertTrue(day.sunset <= day.times.getValue(Prayer.MAGHRIB))
        assertTrue(day.imsak == null || day.imsak!! <= day.times.getValue(Prayer.FAJR))
        assertEquals(emptySet(), day.setByRule)
        assertFalse(day.polar)
    }

    @Test
    fun theHijriDateIsTheAppsTabularOne() {
        val day = timetable.day(tripoli, LocalDate(2026, 9, 13))
        assertEquals(Triple(1448, 3, 30), Triple(day.hijri.year, day.hijri.month, day.hijri.day))
        val next = timetable.day(tripoli, LocalDate(2026, 9, 14))
        assertEquals(Triple(1448, 4, 1), Triple(next.hijri.year, next.hijri.month, next.hijri.day))
    }
}
