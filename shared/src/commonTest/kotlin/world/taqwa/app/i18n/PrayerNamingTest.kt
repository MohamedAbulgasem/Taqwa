package world.taqwa.app.i18n

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrayerNamingTest {

    @Test
    fun arabicLocaleShowsTheArabicNameAlone() {
        val display = PrayerNaming.display(Prayer.FAJR, "ar", "Fajr")
        assertEquals(PrayerNaming.arabicName(Prayer.FAJR), display)
        assertFalse(display.contains("Fajr"))
    }

    @Test
    fun regionalArabicVariantsStillShowTheArabicNameAlone() {
        listOf("ar-LY", "ar-EG", "ar-SA", "ar-MA").forEach { tag ->
            assertEquals(PrayerNaming.arabicName(Prayer.ISHA), PrayerNaming.display(Prayer.ISHA, tag, "Isha"), tag)
        }
    }

    @Test
    fun everyOtherLocalePairsTheLocalisedNameWithArabic() {
        val display = PrayerNaming.display(Prayer.DHUHR, "fr", "Dohr")
        assertTrue(display.contains("Dohr"))
        assertTrue(display.contains(PrayerNaming.arabicName(Prayer.DHUHR)))
    }

    @Test
    fun englishIsTreatedAsJustAnotherNonArabicLocale() {
        val display = PrayerNaming.display(Prayer.ASR, "en", "Asr")
        assertTrue(display.contains("Asr"))
        assertTrue(display.contains(PrayerNaming.arabicName(Prayer.ASR)))
    }

    @Test
    fun theLanguageTagCheckIsCaseInsensitive() {
        assertEquals(PrayerNaming.arabicName(Prayer.MAGHRIB), PrayerNaming.display(Prayer.MAGHRIB, "AR", "Maghrib"))
    }

    @Test
    fun nextInPatternKeepsThePlaceForTheName() {
        assertEquals("%1\$s in", PrayerNaming.nextInPattern("en"))
        assertEquals("متبقٍّ على %1\$s", PrayerNaming.nextInPattern("ar"))
        // The label the widgets render, with the name put in the pattern's place.
        for (tag in listOf("en", "ar", "fr", "tr", "id", "ur", "bn")) {
            assertEquals(
                PrayerNaming.countdownLabel(Prayer.ASR, tag),
                PrayerNaming.nextInPattern(tag).replace("%1\$s", PrayerNaming.name(Prayer.ASR, tag)),
                tag,
            )
        }
    }
}
