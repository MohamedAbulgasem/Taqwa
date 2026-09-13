package world.taqwa.app.i18n

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiLanguageTest {

    @Test
    fun aTagResolvesByItsLanguageAloneAndUnknownOnesFallBackToEnglish() {
        assertEquals(UiLanguage.URDU, UiLanguage.of("ur-PK"))
        assertEquals(UiLanguage.BENGALI, UiLanguage.of("bn_BD"))
        assertEquals(UiLanguage.FRENCH, UiLanguage.of("FR"))
        assertEquals(UiLanguage.ARABIC, UiLanguage.of("ar-LY"))
        assertEquals(UiLanguage.ENGLISH, UiLanguage.of("de-DE"))
        assertEquals(UiLanguage.ENGLISH, UiLanguage.of(null))
        assertEquals(UiLanguage.ENGLISH, UiLanguage.of(""))
    }

    @Test
    fun javasLegacyIndonesianCodeIsIndonesian() {
        // Locale.getLanguage() still answers "in" on Android; the tag form answers "id".
        assertEquals(UiLanguage.INDONESIAN, UiLanguage.of("in-ID"))
        assertEquals(UiLanguage.INDONESIAN, UiLanguage.of("id"))
    }

    @Test
    fun directionAndScriptAreSeparateQuestions() {
        assertTrue(UiLanguage.URDU.rtl)
        assertFalse(UiLanguage.URDU.latinScript)
        assertTrue(UiLanguage.URDU.arabicScript)
        assertFalse(UiLanguage.BENGALI.rtl)
        assertFalse(UiLanguage.BENGALI.latinScript)
        assertFalse(UiLanguage.BENGALI.arabicScript)
        assertTrue(UiLanguage.TURKISH.latinScript)
        assertFalse(UiLanguage.TURKISH.rtl)
        assertEquals(listOf("en", "ar", "fr", "tr", "id", "ur", "bn"), UiLanguage.codes)
    }

    @Test
    fun everyLanguageNamesEveryPrayerAndPhrasesTheCountdown() {
        for (language in UiLanguage.entries) {
            for (prayer in Prayer.entries) {
                val name = PrayerNaming.name(prayer, language.code)
                assertTrue(name.isNotBlank(), "${language.code} has no name for $prayer")
                val label = PrayerNaming.countdownLabel(prayer, language.code)
                assertTrue(label.contains(name) && !label.contains("{prayer}"), "${language.code}: $label")
            }
        }
    }

    @Test
    fun arabicScriptInterfacesShowTheirOwnNameAloneAndTheRestPairItWithTheArabic() {
        assertEquals("فجر", PrayerNaming.display(Prayer.FAJR, "ur-PK", "Fajr"))
        assertEquals("الفجر", PrayerNaming.display(Prayer.FAJR, "ar", "Fajr"))
        assertEquals("ফজর · الفجر", PrayerNaming.display(Prayer.FAJR, "bn", "ফজর"))
        assertEquals("Sabah · الفجر", PrayerNaming.display(Prayer.FAJR, "tr", "Sabah"))
        assertEquals("Fajr · الفجر", PrayerNaming.display(Prayer.FAJR, "en-GB", "Fajr"))
    }
}
