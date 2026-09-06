package world.taqwa.app.hijri

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UmmAlQuraCalendarTest {

    @Test
    fun theHijraEpochMapsToTheFirstOfMuharramYearOne() {
        val h = UmmAlQuraCalendar.fromGregorian(LocalDate(622, 7, 19))
        assertEquals(1, h.year)
        assertEquals(1, h.month)
    }

    @Test
    fun aKnownModernDateConverts() {
        // 2026-09-06 falls in Rabi' al-Awwal 1448 by the tabular calendar.
        // Verified independently via hijri-converter and hijridate (Python):
        // Gregorian(2026, 9, 6).to_hijri() -> 1448-03-24.
        val h = UmmAlQuraCalendar.fromGregorian(LocalDate(2026, 9, 6))
        assertEquals(1448, h.year)
        assertEquals(3, h.month)
    }

    @Test
    fun monthIsAlwaysOneToTwelve() {
        var d = LocalDate(2024, 1, 1)
        repeat(1200) {
            val h = UmmAlQuraCalendar.fromGregorian(d)
            assertTrue(h.month in 1..12, "month ${h.month} for $d")
            assertTrue(h.day in 1..30, "day ${h.day} for $d")
            d = d.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
        }
    }

    @Test
    fun conversionIsMonotonic() {
        var previous = UmmAlQuraCalendar.fromGregorian(LocalDate(2026, 1, 1))
        var d = LocalDate(2026, 1, 2)
        repeat(500) {
            val h = UmmAlQuraCalendar.fromGregorian(d)
            val a = previous.year * 10000 + previous.month * 100 + previous.day
            val b = h.year * 10000 + h.month * 100 + h.day
            assertTrue(b >= a, "went backwards at $d")
            previous = h
            d = d.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
        }
    }

    @Test
    fun monthNamesCoverAllTwelve() {
        (1..12).forEach { assertTrue(HijriFormatter.monthNameEnglish(it).isNotBlank()) }
    }
}
