package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class AyahShareTextTest {
    // Not Quran text: a stand-in string is fine here because the formatter never inspects it.
    private val arabic = "نص"

    @Test
    fun withTranslationThreeBlocksSeparatedByBlankLines() {
        val text = AyahShareText.format(
            arabic = arabic, ayahNumber = 255,
            translation = "Allah - there is no deity except Him" to "Saheeh International",
            surahName = "Al-Baqarah", surah = 2, ayah = 255, digits = { it.toString() },
        )
        assertEquals(
            "نص ﴿٢٥٥﴾\n\nAllah - there is no deity except Him (Saheeh International)\n\nAl-Baqarah 2:255",
            text,
        )
    }

    @Test
    fun withoutTranslationTwoBlocks() {
        val text = AyahShareText.format(arabic, 1, null, "Al-Fatihah", 1, 1) { it.toString() }
        assertEquals("نص ﴿١﴾\n\nAl-Fatihah 1:1", text)
    }

    @Test
    fun referenceDigitsFollowTheUiButTheOrnateNumberIsAlwaysArabicIndic() {
        val text = AyahShareText.format(arabic, 7, null, "الفاتحة", 1, 7) { QuranText.arabicIndic(it) }
        assertEquals("نص ﴿٧﴾\n\nالفاتحة ١:٧", text)
    }
}
