package world.taqwa.app.i18n

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LongDateTest {

    @Test
    fun englishSpellsTheMonthOutWithNoWeekdayAndNoPadding() {
        assertEquals("6 September 2026", EnglishPlatformFormat.longDate(LocalDate(2026, 9, 6)))
        assertEquals("1 January 2027", EnglishPlatformFormat.longDate(LocalDate(2027, 1, 1)))
        assertEquals("31 December 2026", EnglishPlatformFormat.longDate(LocalDate(2026, 12, 31)))
    }

    // The interface default exists so test fakes need not implement it; it must be the English
    // rendering, not empty, so a fake that reaches a date still shows one.
    @Test
    fun theInterfaceDefaultIsTheEnglishRendering() {
        val fake = object : PlatformFormat {
            override fun languageTag() = "xx"
            override fun localizedDigits(number: Int) = number.toString()
            override fun clockTime(hour: Int, minute: Int) = "$hour:$minute"
        }
        assertEquals("6 September 2026", fake.longDate(LocalDate(2026, 9, 6)))
    }
}
