package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The distance to Makkah's thousands mark, per language and per the digits the platform draws.
 * The website's generator runs this file too (tools/timetables), so a city page is held to it.
 */
class DigitGroupingTest {

    private val western: (Int) -> String = { it.toString() }
    private val arabicIndic: (Int) -> String = { n -> n.toString().map { Char(0x0660 + (it - '0')) }.joinToString("") }
    private val extendedArabicIndic: (Int) -> String = { n -> n.toString().map { Char(0x06F0 + (it - '0')) }.joinToString("") }
    private val bengali: (Int) -> String = { n -> n.toString().map { Char(0x09E6 + (it - '0')) }.joinToString("") }

    private val nnbsp = DigitGrouping.NARROW_NO_BREAK_SPACE
    private val arabicMark = DigitGrouping.ARABIC_THOUSANDS_SEPARATOR

    @Test
    fun englishGroupsWithAComma() {
        assertEquals("2,916", DigitGrouping.group(2916, "en-GB", western))
        assertEquals("12,345", DigitGrouping.group(12345, "en", western))
    }

    @Test
    fun turkishAndIndonesianGroupWithAPointBecauseTheirDecimalIsTheComma() {
        assertEquals("2.916", DigitGrouping.group(2916, "tr-TR", western))
        assertEquals("2.916", DigitGrouping.group(2916, "id-ID", western))
        // Java still spells Indonesian "in" in Locale.getLanguage().
        assertEquals("2.916", DigitGrouping.group(2916, "in-ID", western))
        assertEquals("12.345", DigitGrouping.group(12345, "tr", western))
    }

    @Test
    fun frenchGroupsWithANarrowNoBreakSpace() {
        assertEquals("2${nnbsp}916", DigitGrouping.group(2916, "fr-FR", western))
        assertEquals("12${nnbsp}345", DigitGrouping.group(12345, "fr-MA", western))
        assertEquals(0x202F, nnbsp.code)
    }

    @Test
    fun arabicIndicDigitsTakeTheArabicThousandsSeparator() {
        assertEquals("٢${arabicMark}٩١٦", DigitGrouping.group(2916, "ar-EG", arabicIndic))
        assertEquals(0x066C, arabicMark.code)
    }

    @Test
    fun arabicInWesternDigitsKeepsTheCommaAsTheAppWritesItsDecimalsWithAPoint() {
        assertEquals("2,916", DigitGrouping.group(2916, "ar-LY", western))
        assertEquals("4,936", DigitGrouping.group(4936, "ar-MA", western))
    }

    @Test
    fun theDigitsDecideBeforeTheLanguage() {
        // An ar-LY phone whose own ICU draws Arabic-Indic digits takes the Arabic mark with them.
        assertEquals("٢${arabicMark}٩١٦", DigitGrouping.group(2916, "ar-LY", arabicIndic))
        // An English interface on a Persian phone, whose digits are the extended set.
        assertEquals("۲${arabicMark}۹۱۶", DigitGrouping.group(2916, "fa-IR", extendedArabicIndic))
    }

    @Test
    fun urduGroupsWithACommaInWesternDigitsAndTheArabicMarkInItsOwn() {
        assertEquals("2,916", DigitGrouping.group(2916, "ur-PK", western))
        assertEquals("۲${arabicMark}۹۱۶", DigitGrouping.group(2916, "ur-IN", extendedArabicIndic))
    }

    @Test
    fun bengaliGroupsWithACommaInEitherDigitSet() {
        assertEquals("২,৯১৬", DigitGrouping.group(2916, "bn-BD", bengali))
        assertEquals("2,916", DigitGrouping.group(2916, "bn-BD", western))
    }

    @Test
    fun aLanguageTheAppDoesNotSpeakFollowsItsEnglishInterface() {
        assertEquals("2,916", DigitGrouping.group(2916, "de-DE", western))
    }

    @Test
    fun shortNumbersAreNeverGrouped() {
        assertEquals("0", DigitGrouping.group(0, "tr", western))
        assertEquals("987", DigitGrouping.group(987, "fr", western))
        assertEquals("৯৮৭", DigitGrouping.group(987, "bn", bengali))
    }

    @Test
    fun theFarthestPlaceOnEarthStillGroupsOnce() {
        // Half the Earth's circumference is about 20,000 km: no distance to Makkah has six digits.
        assertEquals("20.015", DigitGrouping.group(20015, "id", western))
        assertEquals("1,234,567", DigitGrouping.group(1234567, "en", western))
    }

    @Test
    fun aNegativeNumberIsRefused() {
        assertFailsWith<IllegalArgumentException> { DigitGrouping.group(-1, "en", western) }
    }

    @Test
    fun theSeparatorIsReadFromThePlatformZeroEvenWithAMarkBesideIt() {
        // A formatter that put a bidi mark beside the digit still names the digit set.
        assertEquals(arabicMark, DigitGrouping.separator("ar-SA", "${Char(0x061C)}٠"))
        assertEquals('.', DigitGrouping.separator("tr", "0"))
    }
}
