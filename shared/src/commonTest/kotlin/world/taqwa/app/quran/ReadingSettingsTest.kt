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


    @Test
    fun everyInterfaceLanguageOpensItsOwnTranslation() {
        assertEquals("ur.junagarhi", ReadingSettings.defaultsFor("ur-PK").translationId)
        assertEquals("bn.bengali", ReadingSettings.defaultsFor("bn-BD").translationId)
        assertEquals("id.indonesian", ReadingSettings.defaultsFor("id-ID").translationId)
        assertEquals("fr.hamidullah", ReadingSettings.defaultsFor("fr-FR").translationId)
        // Java still spells Indonesian "in" in Locale.getLanguage().
        assertEquals("id.indonesian", ReadingSettings.defaultsFor("in-ID").translationId)
        assertEquals(ReadingMode.TRANSLATION, ReadingSettings.defaultsFor("ur-PK").mode)
    }
}
