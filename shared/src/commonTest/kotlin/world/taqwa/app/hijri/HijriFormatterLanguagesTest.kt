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
}
