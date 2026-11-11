package world.taqwa.app.i18n

import kotlinx.datetime.LocalDate
import java.text.DateFormatSymbols
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The first test of the real Android formatter: it renders 12-hour times from the platform's own
 * CLDR symbols (spec §1.2), so these expectations are what `DateFormatSymbols` and `NumberFormat`
 * give for each locale, not strings the app owns. The rule under test is "the platform's symbol,
 * joined by U+00A0"; if a JDK's CLDR data spells a marker differently, the expected value here
 * follows the JDK and the code does not change.
 *
 * It builds [AndroidPlatformFormat] for a given locale, which is why the class is `internal` and
 * takes one: `Locale.getDefault()` would make every expectation depend on the machine running the
 * test. The joining space is written as an escape because a literal U+00A0 in source is invisible.
 */
class AndroidPlatformFormatTest {
    private fun format(tag: String, clock: ClockFormat) = AndroidPlatformFormat(Locale.forLanguageTag(tag), clock)

    @Test
    fun `twenty four hour is unchanged`() {
        assertEquals("18:32", format("en-US", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
        assertEquals("3:07", format("en-GB", ClockFormat.TWENTY_FOUR_HOUR).clockTime(3, 7))
        // The digit-set guard: 24-hour mode must keep each locale's own numerals, with no marker.
        assertEquals("١٨:٣٢", format("ar-EG", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
        assertEquals("১৮:৩২", format("bn-BD", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
        assertEquals("18:32", format("tr-TR", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
    }

    @Test
    fun `american english uses upper case markers after the digits`() {
        assertEquals("6:32\u00A0PM", format("en-US", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
        assertEquals("12:05\u00A0AM", format("en-US", ClockFormat.TWELVE_HOUR).clockTime(0, 5))
        assertEquals("12:05\u00A0PM", format("en-US", ClockFormat.TWELVE_HOUR).clockTime(12, 5))
    }

    @Test
    fun `british english uses lower case markers`() {
        assertEquals("6:32\u00A0pm", format("en-GB", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }

    @Test
    fun `turkish puts the marker first`() {
        assertEquals("ÖS\u00A06:32", format("tr-TR", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }

    @Test
    fun `egyptian arabic keeps its digits and uses the arabic marker`() {
        val text = format("ar-EG", ClockFormat.TWELVE_HOUR).clockTime(18, 32)
        assertEquals("٦:٣٢\u00A0م", text)
        assertEquals("6:32\u00A0م", format("ar-LY", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }

    @Test
    fun `bengali indonesian and urdu use latin markers`() {
        assertEquals("৬:৩২\u00A0PM", format("bn-BD", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
        assertEquals("6:32\u00A0PM", format("id-ID", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
        assertEquals("6:32\u00A0PM", format("ur-PK", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }

    // The watch feed's words (plan 2, Task 1). The date words and the day markers are CLDR's in the
    // *words locale*: the format's own locale when the tag's own language is the interface
    // language, otherwise the bare interface language. The clock figures carry no day period.

    private val sunday = LocalDate(2026, 10, 18)

    @Test
    fun clockFiguresLeaveTheDayPeriodOut() {
        val twelve = format("en-US", ClockFormat.TWELVE_HOUR)
        assertEquals("4:10", twelve.clockFigures(16, 10))
        assertEquals("12:05", twelve.clockFigures(0, 5))
        assertEquals("16:10", format("en-US", ClockFormat.TWENTY_FOUR_HOUR).clockFigures(16, 10))
    }

    @Test
    fun arabicIndicFiguresInTwelveHours() {
        assertEquals("\u0664:\u0661\u0660", format("ar-SA", ClockFormat.TWELVE_HOUR).clockFigures(16, 10))
    }

    @Test
    fun dayPeriodsAreTheLocalesOwn() {
        assertEquals("AM" to "PM", format("en-US", ClockFormat.TWELVE_HOUR).dayPeriods(UiLanguage.ENGLISH))
        assertEquals("\u0635" to "\u0645", format("ar-SA", ClockFormat.TWELVE_HOUR).dayPeriods(UiLanguage.ARABIC))
    }

    @Test
    fun shortWeekdayAndMonthAreCldrsAbbreviations() {
        val cases = listOf(
            Triple("en-US", UiLanguage.ENGLISH, "Sun" to "Oct"),
            Triple("fr-FR", UiLanguage.FRENCH, "dim." to "oct."),
            Triple("tr-TR", UiLanguage.TURKISH, "Paz" to "Eki"),
            Triple("ar-SA", UiLanguage.ARABIC, "\u0627\u0644\u0623\u062D\u062F" to "\u0623\u0643\u062A\u0648\u0628\u0631"),
        )
        for ((tag, language, words) in cases) {
            val f = format(tag, ClockFormat.TWENTY_FOUR_HOUR)
            assertEquals(words, f.shortWeekday(sunday, language) to f.shortMonth(sunday, language), tag)
        }
    }

    @Test
    fun longWeekdayAndMonthAreCldrsFullForms() {
        val cases = listOf(
            Triple("en-US", UiLanguage.ENGLISH, "Sunday" to "October"),
            Triple("fr-FR", UiLanguage.FRENCH, "dimanche" to "octobre"),
            Triple("tr-TR", UiLanguage.TURKISH, "Pazar" to "Ekim"),
            Triple("ar-SA", UiLanguage.ARABIC, "\u0627\u0644\u0623\u062D\u062F" to "\u0623\u0643\u062A\u0648\u0628\u0631"),
            Triple("he-IL", UiLanguage.ENGLISH, "Sunday" to "October"),
        )
        for ((tag, language, words) in cases) {
            val f = format(tag, ClockFormat.TWENTY_FOUR_HOUR)
            assertEquals(words, f.longWeekday(sunday, language) to f.longMonth(sunday, language), tag)
        }
    }

    @Test
    fun outsideTheSevenTheDateWordsAreTheInterfaceLanguages() {
        for (tag in listOf("hi-IN", "he-IL", "fa-IR", "mr-IN", "zh-CN", "az-AZ")) {
            val f = format(tag, ClockFormat.TWENTY_FOUR_HOUR)
            assertEquals("Sun" to "Oct", f.shortWeekday(sunday, UiLanguage.ENGLISH) to f.shortMonth(sunday, UiLanguage.ENGLISH), tag)
            assertEquals("Sunday" to "October", f.longWeekday(sunday, UiLanguage.ENGLISH) to f.longMonth(sunday, UiLanguage.ENGLISH), tag)
            assertEquals("AM" to "PM", f.dayPeriods(UiLanguage.ENGLISH), tag)
            // The clock figures keep the format's own digits: Task 3's WatchDigits rewrites them.
            assertEquals("${f.localizedDigits(16)}:${f.localizedDigits(10)}", f.clockFigures(16, 10), tag)
            // The app's own longDate and clockTime are unchanged: still the locale's own words,
            // digits and day period, never the words locale's.
            val longDate = f.longDate(sunday)
            assertFalse(longDate.contains("October"), "$tag longDate: $longDate")
            assertTrue(longDate.contains(f.localizedDigits(2026)), "$tag longDate: $longDate")
            val twelve = format(tag, ClockFormat.TWELVE_HOUR)
            val ownPm = DateFormatSymbols.getInstance(Locale.forLanguageTag(tag)).amPmStrings[1]
            assertEquals(
                TwelveHourClock.render(twelve.localizedDigits(4), twelve.localizedDigits(10), ownPm, markerFirst = false),
                twelve.clockTime(16, 10),
                tag,
            )
        }
        // The runtime's own digits for two of them, so the line above checks something.
        assertNotEquals("16", format("fa-IR", ClockFormat.TWENTY_FOUR_HOUR).localizedDigits(16))
        assertNotEquals("16", format("mr-IN", ClockFormat.TWENTY_FOUR_HOUR).localizedDigits(16))
    }

    @Test
    fun theWordsFollowTheLanguageNotTheFormat() {
        val english = format("en-US", ClockFormat.TWELVE_HOUR)
        assertEquals("\u0627\u0644\u0623\u062D\u062F" to "\u0623\u0643\u062A\u0648\u0628\u0631", english.shortWeekday(sunday, UiLanguage.ARABIC) to english.shortMonth(sunday, UiLanguage.ARABIC))
        assertEquals("\u0635" to "\u0645", english.dayPeriods(UiLanguage.ARABIC))
        assertEquals("18", english.localizedDigits(18))
        val arabic = format("ar-SA", ClockFormat.TWELVE_HOUR)
        assertEquals("Sun" to "Oct", arabic.shortWeekday(sunday, UiLanguage.ENGLISH) to arabic.shortMonth(sunday, UiLanguage.ENGLISH))
        assertEquals("AM" to "PM", arabic.dayPeriods(UiLanguage.ENGLISH))
        assertEquals("\u0661\u0668", arabic.localizedDigits(18))
    }
}
