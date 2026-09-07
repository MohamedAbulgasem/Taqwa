package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingSettingsTest {

    @Test
    fun englishDefaultsToSaheehAndTranslationMode() {
        val d = ReadingSettings.defaultsFor("en-GB")
        assertEquals("en.sahih", d.translationId)
        assertEquals(ReadingMode.TRANSLATION, d.mode)
    }

    @Test
    fun arabicDefaultsToMuyassarAndMushaf() {
        val d = ReadingSettings.defaultsFor("ar-LY")
        assertEquals("ar.muyassar", d.translationId)
        assertEquals(ReadingMode.MUSHAF, d.mode)
    }

    @Test
    fun bundledLanguagesGetTheirOwnTranslationOthersFallBackToEnglish() {
        assertEquals("tr.diyanet", ReadingSettings.defaultsFor("tr-TR").translationId)
        assertEquals("en.sahih", ReadingSettings.defaultsFor("de-DE").translationId)
    }

    @Test
    fun sizeIsClampedToTheSheetRange() {
        assertEquals(22, ReadingSettings(arabicSizeSp = 10).clamped().arabicSizeSp)
        assertEquals(40, ReadingSettings(arabicSizeSp = 99).clamped().arabicSizeSp)
    }
}
