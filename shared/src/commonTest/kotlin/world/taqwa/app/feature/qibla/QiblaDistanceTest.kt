package world.taqwa.app.feature.qibla

import world.taqwa.app.i18n.DigitGrouping
import world.taqwa.app.i18n.PlatformFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/** The distance the Qibla card and the Qibla screen print: truncated, grouped for the reader. */
class QiblaDistanceTest {

    private class Format(private val tag: String, private val zero: Int = '0'.code) : PlatformFormat {
        override fun languageTag() = tag
        override fun localizedDigits(number: Int): String =
            number.toString().map { Char(zero + (it - '0')) }.joinToString("")
        override fun clockTime(hour: Int, minute: Int) = "${localizedDigits(hour)}:${localizedDigits(minute)}"
    }

    @Test
    fun theKilometresAreTruncatedNotRounded() {
        assertEquals("2,916", localizedGroupedKm(2916.9, Format("en-GB")))
    }

    @Test
    fun aTurkishOrIndonesianReaderSeesAPointNotADecimalComma() {
        assertEquals("2.916", localizedGroupedKm(2916.2, Format("tr-TR")))
        assertEquals("2.916", localizedGroupedKm(2916.2, Format("in-ID")))
    }

    @Test
    fun aFrenchReaderSeesANarrowNoBreakSpace() {
        assertEquals("2${DigitGrouping.NARROW_NO_BREAK_SPACE}916", localizedGroupedKm(2916.2, Format("fr-FR")))
    }

    @Test
    fun anEgyptianReaderSeesArabicIndicDigitsWithTheArabicMark() {
        assertEquals("٢${DigitGrouping.ARABIC_THOUSANDS_SEPARATOR}٩١٦", localizedGroupedKm(2916.2, Format("ar-EG", 0x0660)))
    }

    @Test
    fun aLibyanReaderKeepsTheComma() {
        assertEquals("2,916", localizedGroupedKm(2916.2, Format("ar-LY")))
    }
}
