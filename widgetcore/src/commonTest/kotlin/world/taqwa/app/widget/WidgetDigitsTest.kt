package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetDigitsTest {

    @Test
    fun egyptianArabicGetsArabicIndicDigits() {
        assertEquals("١:٠٥", WidgetDigits.localize("1:05", "ar-EG"))
    }

    @Test
    fun libyanArabicStaysWesternByCldrDefault() {
        assertEquals("1:05", WidgetDigits.localize("1:05", "ar-LY"))
    }

    @Test
    fun englishStaysWestern() {
        assertEquals("1:05", WidgetDigits.localize("1:05", "en"))
    }

    @Test
    fun nonDigitCharactersPassThroughUnchanged() {
        assertEquals("h:mm ٠١٢٣", WidgetDigits.localize("h:mm 0123", "ar-EG"))
        assertEquals("h:mm 0123", WidgetDigits.localize("h:mm 0123", "ar-LY"))
    }

    @Test
    fun otherMaghrebAdjacentRegionsAlsoStayWestern() {
        listOf("ar-MA", "ar-TN", "ar-DZ", "ar-EH", "ar-MR").forEach { tag ->
            assertEquals("1:05", WidgetDigits.localize("1:05", tag), tag)
        }
    }

    @Test
    fun saudiArabicAndBareArabicGetArabicIndicDigits() {
        assertEquals("١:٠٥", WidgetDigits.localize("1:05", "ar-SA"))
        assertEquals("١:٠٥", WidgetDigits.localize("1:05", "ar"))
    }

    @Test
    fun matchIsCaseInsensitiveOnTheTag() {
        assertEquals("1:05", WidgetDigits.localize("1:05", "AR-ly"))
        assertEquals("١:٠٥", WidgetDigits.localize("1:05", "AR-eg"))
    }
}
