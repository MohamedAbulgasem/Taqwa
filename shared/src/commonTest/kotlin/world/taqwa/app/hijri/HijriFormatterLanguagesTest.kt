package world.taqwa.app.hijri

import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HijriFormatterLanguagesTest {

    private class TagOnly(private val tag: String) : PlatformFormat {
        override fun languageTag() = tag
        override fun localizedDigits(number: Int) = number.toString()
        override fun clockTime(hour: Int, minute: Int) = "$hour:$minute"
    }

    @Test
    fun everyLanguageHasTwelveDistinctMonths() {
        for (language in UiLanguage.entries) {
            val months = (1..12).map { HijriFormatter.monthName(it, language) }
            assertEquals(12, months.toSet().size, language.code)
            assertTrue(months.all { it.isNotBlank() }, language.code)
        }
    }

    @Test
    fun theMonthFollowsTheInterfaceLanguageOfTheTag() {
        val ramadan = HijriDate(year = 1448, month = 9, day = 1)
        assertEquals("1 Ramadan 1448", HijriFormatter.format(ramadan, TagOnly("en-GB")))
        assertEquals("1 رمضان 1448", HijriFormatter.format(ramadan, TagOnly("ar-LY")))
        assertEquals("1 Ramazan 1448", HijriFormatter.format(ramadan, TagOnly("tr-TR")))
        assertEquals("1 Ramadan 1448", HijriFormatter.format(ramadan, TagOnly("id-ID")))
        assertEquals("1 رمضان 1448", HijriFormatter.format(ramadan, TagOnly("ur-PK")))
        assertEquals("1 রমজান 1448", HijriFormatter.format(ramadan, TagOnly("bn-BD")))
        assertEquals("1 Ramadan 1448", HijriFormatter.format(ramadan, TagOnly("de")), "unknown falls back to English")
    }

    /** A format writing Arabic-Indic digits, as an Egyptian or Saudi phone does. */
    private class ArabicIndic(private val tag: String) : PlatformFormat {
        override fun languageTag() = tag
        override fun localizedDigits(number: Int) =
            number.toString().map { Char(0x0660 + (it - '0')) }.joinToString("")
        override fun clockTime(hour: Int, minute: Int) = "$hour:$minute"
    }

    @Test
    fun formatDayMonthDropsTheYear() {
        val date = HijriDate(year = 1448, month = 5, day = 6)
        assertEquals("6 Jumada al-Ula", HijriFormatter.formatDayMonth(date))
        assertEquals("6 Jumada al-Ula", HijriFormatter.formatDayMonth(date, TagOnly("en-GB")))
        assertEquals("٦ جمادى الأولى", HijriFormatter.formatDayMonth(date, ArabicIndic("ar-SA"), UiLanguage.ARABIC))
        // The interface language names the month; the format gives only the digits.
        assertEquals("6 جمادى الأولى", HijriFormatter.formatDayMonth(date, TagOnly("en-US"), UiLanguage.ARABIC))
    }

    @Test
    fun theFullFormTakesTheInterfaceLanguageToo() {
        val date = HijriDate(year = 1448, month = 5, day = 6)
        assertEquals("6 جمادى الأولى 1448", HijriFormatter.format(date, TagOnly("en-US"), UiLanguage.ARABIC))
        assertEquals("٦ جمادى الأولى ١٤٤٨", HijriFormatter.format(date, ArabicIndic("ar-SA")))
        assertEquals(
            HijriFormatter.format(date, ArabicIndic("ar-SA")).substringBeforeLast(' '),
            HijriFormatter.formatDayMonth(date, ArabicIndic("ar-SA")),
        )
    }
}
