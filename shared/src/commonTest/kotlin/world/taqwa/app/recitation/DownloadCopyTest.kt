package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadCopyTest {

    private val nine = (9.3 * MEGABYTE).toLong()
    private val whole = (58.2 * MEGABYTE).toLong()

    @Test
    fun progressReadsAsOneLineWithTheUnitOnce() {
        assertEquals(
            "Al-Baqarah · 9.3 of 58.2 MB",
            DownloadCopy.progress("Al-Baqarah", nine, whole, "en-GB", NumberStyle.WESTERN),
        )
    }

    @Test
    fun arabicProgressUsesArabicIndicDigitsAndSeparator() {
        assertEquals(
            "البقرة · ٩٫٣ من ٥٨٫٢ م.ب",
            DownloadCopy.progress("البقرة", nine, whole, "ar-EG", NumberStyle.ARABIC_INDIC),
        )
    }

    @Test
    fun aLibyanReaderKeepsWesternDigitsUnderArabicWords() {
        // The style is the platform's answer, not the language's: ar-LY formats with 0-9.
        assertEquals(
            "البقرة · 9.3 من 58.2 م.ب",
            DownloadCopy.progress("البقرة", nine, whole, "ar-LY", NumberStyle.WESTERN),
        )
    }

    @Test
    fun theBatchLineCountsSurahsInEveryLanguage() {
        assertEquals(
            "Mishary Rashid Alafasy · 12 of 114 surahs",
            DownloadCopy.batch("Mishary Rashid Alafasy", 12, 114, "en", NumberStyle.WESTERN),
        )
        assertEquals("العفاسي · ١٢ من ١١٤ سورة", DownloadCopy.batch("العفاسي", 12, 114, "ar", NumberStyle.ARABIC_INDIC))
        assertEquals("Alafasy · 12 sur 114 sourates", DownloadCopy.batch("Alafasy", 12, 114, "fr", NumberStyle.WESTERN_COMMA))
        assertEquals("Alafasy · 114 sureden 12", DownloadCopy.batch("Alafasy", 12, 114, "tr-TR", NumberStyle.WESTERN_COMMA))
        assertEquals("Alafasy · 12 dari 114 surah", DownloadCopy.batch("Alafasy", 12, 114, "id", NumberStyle.WESTERN_COMMA))
        assertEquals("العفاسی · 114 میں سے 12 سورتیں", DownloadCopy.batch("العفاسی", 12, 114, "ur-PK", NumberStyle.WESTERN))
        assertEquals("আল-আফাসি · ১১৪টির মধ্যে ১২টি সূরা", DownloadCopy.batch("আল-আফাসি", 12, 114, "bn-BD", NumberStyle.BENGALI))
    }

    @Test
    fun theChannelIsNamedInTheInterfaceLanguage() {
        assertEquals("Downloads", DownloadCopy.channelName("en"))
        assertEquals("التنزيلات", DownloadCopy.channelName("ar-LY"))
        assertEquals("Téléchargements", DownloadCopy.channelName("fr-FR"))
        assertEquals("İndirmeler", DownloadCopy.channelName("tr"))
        assertEquals("Unduhan", DownloadCopy.channelName("in-ID"))
        assertEquals("Downloads", DownloadCopy.channelName("de"), "an unknown language falls back to English")
    }

    @Test
    fun megabytesRoundToOneDecimalInTheGivenStyle() {
        assertEquals("0.0", DownloadCopy.megabytes(0L, NumberStyle.WESTERN))
        assertEquals("1.0", DownloadCopy.megabytes(1024L * 1024L, NumberStyle.WESTERN))
        assertEquals("0.5", DownloadCopy.megabytes(512L * 1024L, NumberStyle.WESTERN))
        assertEquals("58,2", DownloadCopy.megabytes(whole, NumberStyle.WESTERN_COMMA))
        assertEquals("৫৮.২", DownloadCopy.megabytes(whole, NumberStyle.BENGALI))
    }

    @Test
    fun gigabytesRoundToOneDecimal() {
        assertEquals("1.0", DownloadCopy.gigabytes(GIGABYTE, NumberStyle.WESTERN))
        assertEquals("0.9", DownloadCopy.gigabytes(902L * MEGABYTE, NumberStyle.WESTERN))
        assertEquals("١٫٦", DownloadCopy.gigabytes(1625L * MEGABYTE, NumberStyle.ARABIC_INDIC))
    }

    @Test
    fun theUnitSwitchesAtAThousandRoundedMegabytes() {
        assertEquals(false, DownloadCopy.useGigabytes(999L * MEGABYTE))
        assertEquals(true, DownloadCopy.useGigabytes(1000L * MEGABYTE))
    }

    @Test
    fun theNumberStyleFollowsThePlatformDigitsFirstAndTheLanguageSecond() {
        assertEquals(NumberStyle.ARABIC_INDIC, NumberStyle.of("ar-EG", "٠"))
        assertEquals(NumberStyle.WESTERN, NumberStyle.of("ar-LY", "0"))
        assertEquals(NumberStyle.BENGALI, NumberStyle.of("bn-BD", "০"))
        assertEquals(NumberStyle.WESTERN_COMMA, NumberStyle.of("fr-FR", "0"))
        assertEquals(NumberStyle.WESTERN_COMMA, NumberStyle.of("tr-TR", "0"))
        assertEquals(NumberStyle.WESTERN_COMMA, NumberStyle.of("id-ID", "0"))
        assertEquals(NumberStyle.WESTERN, NumberStyle.of("ur-PK", "0"))
        assertEquals(NumberStyle.WESTERN, NumberStyle.of("en-GB", "0"))
        assertTrue(NumberStyle.entries.all { it.apply("9.3").length == 3 })
    }

    private companion object {
        const val MEGABYTE = 1024L * 1024L
        const val GIGABYTE = 1024L * 1024L * 1024L
    }
}
