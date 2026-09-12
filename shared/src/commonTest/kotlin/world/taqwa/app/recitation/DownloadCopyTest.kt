package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals

/** The words baked into a download notification while the app still knows what language it is in. */
class DownloadCopyTest {

    @Test
    fun aProgressLineReadsTheWayTheSpecPrintsIt() {
        val nine = 9_751_756L
        val whole = 61_026_304L
        assertEquals("Al-Baqarah · 9.3 of 58.2 MB", DownloadCopy.progress("Al-Baqarah", nine, whole, false))
    }

    @Test
    fun anArabicProgressLineUsesArabicDigitsAndSeparator() {
        val nine = 9_751_756L
        val whole = 61_026_304L
        assertEquals("البقرة · ٩٫٣ من ٥٨٫٢ م.ب", DownloadCopy.progress("البقرة", nine, whole, true))
    }

    @Test
    fun aBatchLineCountsSurahs() {
        assertEquals(
            "Mishary Rashid Alafasy · 12 of 114 surahs",
            DownloadCopy.batch("Mishary Rashid Alafasy", 12, 114, false),
        )
        assertEquals("العفاسي · ١٢ من ١١٤ سورة", DownloadCopy.batch("العفاسي", 12, 114, true))
    }

    @Test
    fun theChannelIsNamedInBothLanguages() {
        assertEquals("Downloads", DownloadCopy.channelName(false))
        assertEquals("التنزيلات", DownloadCopy.channelName(true))
    }

    @Test
    fun megabytesRoundRatherThanTruncate() {
        assertEquals("0.0", DownloadCopy.megabytes(0L, false))
        assertEquals("1.0", DownloadCopy.megabytes(1024L * 1024L, false))
        assertEquals("0.5", DownloadCopy.megabytes(512L * 1024L, false))
    }
}
