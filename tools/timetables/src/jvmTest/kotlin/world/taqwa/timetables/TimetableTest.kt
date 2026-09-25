package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimetableTest {

    private fun city(
        slug: String,
        country: String,
        lat: Double,
        lon: Double,
        zone: String,
        madhab: AsrMadhab = AsrMadhab.STANDARD,
        languages: List<String> = listOf("en"),
    ) = City(
        slug = slug, id = 1, countryCode = country, region = "north-africa", latitude = lat, longitude = lon,
        timeZone = zone, languages = languages, madhab = madhab, featured = emptySet(),
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
        val day = timetable.day(tripoli, LocalDate(2026, 9, 13))
        assertEquals("5:26", clock(tripoli, day, Prayer.FAJR))
        assertEquals("13:04", clock(tripoli, day, Prayer.DHUHR))
        assertEquals("16:34", clock(tripoli, day, Prayer.ASR))
        assertEquals("19:16", clock(tripoli, day, Prayer.MAGHRIB))
        assertEquals("20:35", clock(tripoli, day, Prayer.ISHA))
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
        val standard = city("karachi-pakistan", "PK", 24.8608, 67.0104, "Asia/Karachi", AsrMadhab.STANDARD)
        val hanafi = standard.copy(madhab = AsrMadhab.HANAFI)
        val date = LocalDate(2026, 9, 25)
        assertTrue(timetable.day(hanafi, date).times.getValue(Prayer.ASR) > timetable.day(standard, date).times.getValue(Prayer.ASR))
    }

    @Test
    fun fridaysAreMarked() {
        assertTrue(timetable.day(tripoli, LocalDate(2026, 9, 25)).friday)
        assertFalse(timetable.day(tripoli, LocalDate(2026, 9, 24)).friday)
    }

    @Test
    fun aCityWhoseTimesTheEngineWouldPutOnAnotherDayIsRefused() {
        // Apia's clock runs thirteen hours ahead of UTC at a longitude eleven hours behind it,
        // and the app's engine then answers with the next day's times. No page may say so.
        val apia = city("apia-samoa", "WS", -13.83333, -171.76666, "Pacific/Apia")
        val error = assertFailsWith<IllegalStateException> { timetable.day(apia, LocalDate(2026, 9, 25)) }
        assertTrue(error.message!!.contains("apia-samoa"), error.message)
    }

    @Test
    fun anUmmAlQuraRamadanIsRefusedUntilTheAppAddsItsHalfHour() {
        // Umm al-Qura's Isha is 120 minutes after Maghrib in Ramadan, not 90; the app's engine
        // keeps 90, so a Ramadan month in Makkah would show Isha half an hour early.
        val makkah = city("makkah-saudi-arabia", "SA", 21.42664, 39.82563, "Asia/Riyadh")
        val error = assertFailsWith<IllegalStateException> {
            timetable.months(makkah, Instant.parse("2027-01-15T00:07:00Z"))
        }
        assertTrue(error.message!!.contains("Ramadan"), error.message)
        assertEquals(2, timetable.months(makkah, Instant.parse("2026-09-25T00:07:00Z")).size)
    }

    @Test
    fun theHijriDateIsTheAppsTabularOne() {
        val day = timetable.day(tripoli, LocalDate(2026, 9, 13))
        assertEquals(Triple(1448, 3, 30), Triple(day.hijri.year, day.hijri.month, day.hijri.day))
        val next = timetable.day(tripoli, LocalDate(2026, 9, 14))
        assertEquals(Triple(1448, 4, 1), Triple(next.hijri.year, next.hijri.month, next.hijri.day))
    }
}
