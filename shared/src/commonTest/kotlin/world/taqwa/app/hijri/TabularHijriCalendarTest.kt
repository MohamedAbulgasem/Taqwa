package world.taqwa.app.hijri

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabularHijriCalendarTest {

    @Test
    fun theHijraEpochMapsToTheFirstOfMuharramYearOne() {
        val h = TabularHijriCalendar.fromGregorian(LocalDate(622, 7, 19))
        assertEquals(1, h.year)
        assertEquals(1, h.month)
        assertEquals(1, h.day)
    }

    @Test
    fun aKnownModernDateConverts() {
        // Reference: an independent implementation of the *tabular* civil calendar — the
        // standard 30-year intercalation cycle (leap years 2,5,7,10,13,16,18,21,24,26,29) with
        // the civil epoch 1 Muharram 1 AH = JD 1948439.5 — converting via Julian Day, which is a
        // different route through the arithmetic from the closed form in TabularHijriCalendar.
        // It gives 2026-09-06 -> 1448-03-23, and 09-05/09-07 -> 03-22/03-24 either side.
        //
        // The previous comment here cited hijri-converter/hijridate, which implement the
        // table-driven *Umm al-Qura* calendar and answer 1448-03-24; the assertion was scoped to
        // year and month, the only two fields on which the two calendars agree. That is the
        // wrong authority for this class, and the day is now asserted against the right one.
        val h = TabularHijriCalendar.fromGregorian(LocalDate(2026, 9, 6))
        assertEquals(1448, h.year)
        assertEquals(3, h.month)
        assertEquals(23, h.day)
    }

    @Test
    fun theDaysEitherSideOfTheGoldenDateAlsoMatchTheTabularReference() {
        val before = TabularHijriCalendar.fromGregorian(LocalDate(2026, 9, 5))
        assertEquals(HijriDate(1448, 3, 22), before)
        val after = TabularHijriCalendar.fromGregorian(LocalDate(2026, 9, 7))
        assertEquals(HijriDate(1448, 3, 24), after)
    }

    @Test
    fun monthIsAlwaysOneToTwelve() {
        var d = LocalDate(2024, 1, 1)
        repeat(1200) {
            val h = TabularHijriCalendar.fromGregorian(d)
            assertTrue(h.month in 1..12, "month ${h.month} for $d")
            assertTrue(h.day in 1..30, "day ${h.day} for $d")
            d = d.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
        }
    }

    @Test
    fun conversionIsMonotonic() {
        var previous = TabularHijriCalendar.fromGregorian(LocalDate(2026, 1, 1))
        var d = LocalDate(2026, 1, 2)
        repeat(500) {
            val h = TabularHijriCalendar.fromGregorian(d)
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
