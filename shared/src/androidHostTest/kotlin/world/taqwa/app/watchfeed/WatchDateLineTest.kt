package world.taqwa.app.watchfeed

import kotlinx.datetime.LocalDate
import world.taqwa.app.i18n.AndroidPlatformFormat
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.UiLanguage
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The face's date line, built on the real Android formats: CLDR's abbreviations in the words
 * locale, without their trailing dot and in the interface language's capitals, around the phone's
 * own day number; and the screen reader's date in full words, in the phone's case.
 */
class WatchDateLineTest {
    private val sunday = LocalDate(2026, 10, 18)

    private fun line(tag: String, language: UiLanguage) =
        WatchDateLine.of(sunday, AndroidPlatformFormat(Locale.forLanguageTag(tag), ClockFormat.TWENTY_FOUR_HOUR), language)

    private val english = WatchDateLine(gregorian = "SUN 18 OCT", weekday = "SUN", dayNumber = "18", spoken = "Sunday 18 October")

    @Test
    fun englishIsTheSamplesLine() {
        assertEquals(english, line("en", UiLanguage.ENGLISH))
    }

    @Test
    fun frenchLosesItsAbbreviationDots() {
        val french = line("fr-FR", UiLanguage.FRENCH)
        assertEquals("DIM 18 OCT", french.gregorian)
        assertEquals("dimanche 18 octobre", french.spoken)
    }

    @Test
    fun turkishCapitalisesWithTheDottedI() {
        val turkish = line("tr-TR", UiLanguage.TURKISH)
        assertEquals("PAZ 18 EKİ", turkish.gregorian)
        assertEquals("Pazar 18 Ekim", turkish.spoken)
    }

    @Test
    fun saudiArabicIsInArabicIndicDigits() {
        assertEquals(
            WatchDateLine(gregorian = "الأحد ١٨ أكتوبر", weekday = "الأحد", dayNumber = "١٨", spoken = "الأحد ١٨ أكتوبر"),
            line("ar-SA", UiLanguage.ARABIC),
        )
    }

    @Test
    fun outsideTheSevenTheLineIsTheInterfaceLanguages() {
        for (tag in listOf("he-IL", "hi-IN", "az-AZ")) {
            assertEquals(english, line(tag, UiLanguage.ENGLISH), tag)
        }
    }
}
