package world.taqwa.app.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
