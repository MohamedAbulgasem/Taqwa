package world.taqwa.app.i18n

import kotlinx.datetime.LocalDate
import platform.Foundation.NSLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The real iOS formatter on the simulator, the iOS twin of `AndroidPlatformFormatTest`, and the only
 * check of the 12-hour markers iOS itself supplies. The rule under test is the same: the platform's
 * own day-period symbol (`NSDateFormatter`'s `AMSymbol` and `PMSymbol` for the language), joined to
 * the digits by U+00A0, before them only in Turkish. Where the expectation is a whole string it is
 * what Foundation's CLDR data gives; for English the shape is checked and the marker is whatever
 * Foundation returns, so a Foundation that spells it differently does not fail the test.
 *
 * It builds [IosPlatformFormat] for a given locale and language, never the simulator's own, so the
 * expectations do not depend on how the simulator is set up. The joining space is written as an
 * escape because a literal U+00A0 in source is invisible.
 */
class IosPlatformFormatTest {
    private fun format(identifier: String, language: String, clock: ClockFormat) =
        IosPlatformFormat(clock, NSLocale(localeIdentifier = identifier), language)

    @Test
    fun `twenty four hour is unchanged`() {
        assertEquals("18:32", format("en_US", "en-US", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
        assertEquals("3:07", format("en_GB", "en-GB", ClockFormat.TWENTY_FOUR_HOUR).clockTime(3, 7))
        assertEquals("18:32", format("tr_TR", "tr-TR", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
        // The digit-set guard: 24-hour keeps each locale's own numerals and has no marker.
        assertEquals("١٨:٣٢", format("ar_EG", "ar-EG", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
    }

    @Test
    fun `turkish puts the marker first`() {
        assertEquals("ÖS\u00A06:32", format("tr_TR", "tr-TR", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
        assertEquals("ÖÖ\u00A05:44", format("tr_TR", "tr-TR", ClockFormat.TWELVE_HOUR).clockTime(5, 44))
    }

    @Test
    fun `egyptian arabic keeps its digits and puts its own marker after them`() {
        val arabic = format("ar_EG", "ar-EG", ClockFormat.TWELVE_HOUR)
        assertEquals("٦:٣٢\u00A0م", arabic.clockTime(18, 32))
        assertEquals("٥:٤٤\u00A0ص", arabic.clockTime(5, 44))
    }

    @Test
    fun `english puts the platform's marker after the digits`() {
        for ((identifier, language) in listOf("en_US" to "en-US", "en_GB" to "en-GB")) {
            val english = format(identifier, language, ClockFormat.TWELVE_HOUR)
            val evening = english.clockTime(18, 32)
            val night = english.clockTime(0, 5)
            val noon = english.clockTime(12, 5)
            assertTrue(evening.startsWith("6:32\u00A0"), "$language evening: $evening")
            assertTrue(night.startsWith("12:05\u00A0"), "$language just after midnight: $night")
            assertTrue(noon.startsWith("12:05\u00A0"), "$language just after noon: $noon")
            val pm = evening.substringAfter('\u00A0')
            val am = night.substringAfter('\u00A0')
            // A real marker, not a fallback: letters only, and morning and evening told apart.
            assertTrue(pm.isNotEmpty() && pm.all { it.isLetter() || it == '.' }, "$language pm marker: $pm")
            assertTrue(am.isNotEmpty() && am.all { it.isLetter() || it == '.' }, "$language am marker: $am")
            assertNotEquals(am, pm, language)
            assertEquals(pm, noon.substringAfter('\u00A0'), "$language noon is after noon")
        }
    }

    // The watch feed's words (plan 2, Task 1), the twins of AndroidPlatformFormatTest's: the date
    // words and the day markers in the words locale, and the clock figures without a day period.

    private val sunday = LocalDate(2026, 10, 18)

    @Test
    fun clockFiguresLeaveTheDayPeriodOut() {
        val twelve = format("en_US", "en-US", ClockFormat.TWELVE_HOUR)
        assertEquals("4:10", twelve.clockFigures(16, 10))
        assertEquals("12:05", twelve.clockFigures(0, 5))
        assertEquals("16:10", format("en_US", "en-US", ClockFormat.TWENTY_FOUR_HOUR).clockFigures(16, 10))
    }

    @Test
    fun arabicIndicFiguresInTwelveHours() {
        assertEquals("٤:١٠", format("ar_SA", "ar-SA", ClockFormat.TWELVE_HOUR).clockFigures(16, 10))
    }

    @Test
    fun dayPeriodsAreTheLocalesOwn() {
        assertEquals("AM" to "PM", format("en_US", "en-US", ClockFormat.TWELVE_HOUR).dayPeriods(UiLanguage.ENGLISH))
        assertEquals("ص" to "م", format("ar_SA", "ar-SA", ClockFormat.TWELVE_HOUR).dayPeriods(UiLanguage.ARABIC))
    }

    @Test
    fun shortWeekdayAndMonthAreCldrsAbbreviations() {
        val english = format("en_US", "en-US", ClockFormat.TWENTY_FOUR_HOUR)
        assertEquals("Sun" to "Oct", english.shortWeekday(sunday, UiLanguage.ENGLISH) to english.shortMonth(sunday, UiLanguage.ENGLISH))
        // Foundation's CLDR abbreviates the Arabic weekday without its article, where the JVM's
        // (AndroidPlatformFormatTest) writes it in full: the rule is the platform's own `EEE`, so
        // the expectation follows Foundation and the code does not change. The month is the same.
        val arabic = format("ar_SA", "ar-SA", ClockFormat.TWENTY_FOUR_HOUR)
        assertEquals("أحد" to "أكتوبر", arabic.shortWeekday(sunday, UiLanguage.ARABIC) to arabic.shortMonth(sunday, UiLanguage.ARABIC))
    }

    @Test
    fun longWeekdayAndMonthAreCldrsFullForms() {
        val english = format("en_US", "en-US", ClockFormat.TWENTY_FOUR_HOUR)
        assertEquals("Sunday" to "October", english.longWeekday(sunday, UiLanguage.ENGLISH) to english.longMonth(sunday, UiLanguage.ENGLISH))
        val arabic = format("ar_SA", "ar-SA", ClockFormat.TWENTY_FOUR_HOUR)
        assertEquals("الأحد" to "أكتوبر", arabic.longWeekday(sunday, UiLanguage.ARABIC) to arabic.longMonth(sunday, UiLanguage.ARABIC))
        val hebrew = format("he_IL", "he-IL", ClockFormat.TWENTY_FOUR_HOUR)
        assertEquals("Sunday" to "October", hebrew.longWeekday(sunday, UiLanguage.ENGLISH) to hebrew.longMonth(sunday, UiLanguage.ENGLISH))
    }

    @Test
    fun outsideTheSevenTheDateWordsAreTheInterfaceLanguages() {
        val f = format("he_IL", "he-IL", ClockFormat.TWENTY_FOUR_HOUR)
        assertEquals("Sun" to "Oct", f.shortWeekday(sunday, UiLanguage.ENGLISH) to f.shortMonth(sunday, UiLanguage.ENGLISH))
        assertEquals("Sunday" to "October", f.longWeekday(sunday, UiLanguage.ENGLISH) to f.longMonth(sunday, UiLanguage.ENGLISH))
        assertEquals("AM" to "PM", f.dayPeriods(UiLanguage.ENGLISH))
        assertEquals("${f.localizedDigits(16)}:${f.localizedDigits(10)}", f.clockFigures(16, 10))
        // The app's own longDate and clockTime are unchanged: still the locale's own words and day
        // period, never the words locale's.
        val longDate = f.longDate(sunday)
        assertFalse(longDate.contains("October"), "longDate: $longDate")
        assertTrue(longDate.contains(f.localizedDigits(2026)), "longDate: $longDate")
        val twelve = format("he_IL", "he-IL", ClockFormat.TWELVE_HOUR).clockTime(16, 10)
        assertTrue(twelve.startsWith("4:10"), "clockTime: $twelve")
        assertFalse(twelve.endsWith("PM"), "clockTime keeps the locale's own marker: $twelve")
    }

    @Test
    fun theWordsFollowTheLanguageNotTheFormat() {
        val english = format("en_US", "en-US", ClockFormat.TWELVE_HOUR)
        assertEquals("أحد" to "أكتوبر", english.shortWeekday(sunday, UiLanguage.ARABIC) to english.shortMonth(sunday, UiLanguage.ARABIC))
        assertEquals("ص" to "م", english.dayPeriods(UiLanguage.ARABIC))
        assertEquals("18", english.localizedDigits(18))
        val arabic = format("ar_SA", "ar-SA", ClockFormat.TWELVE_HOUR)
        assertEquals("Sun" to "Oct", arabic.shortWeekday(sunday, UiLanguage.ENGLISH) to arabic.shortMonth(sunday, UiLanguage.ENGLISH))
        assertEquals("AM" to "PM", arabic.dayPeriods(UiLanguage.ENGLISH))
        assertEquals("١٨", arabic.localizedDigits(18))
    }
}
