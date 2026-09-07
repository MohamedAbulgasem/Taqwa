package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class QuranTextTest {

    @Test
    fun roundelIsArabicIndicDigitsAfterANonBreakingSpace() {
        assertEquals("نص ٢٥٥", QuranText.withMarker("نص", 255))
        assertEquals("نص ٧", QuranText.withMarker("نص", 7))
    }

    @Test
    fun roundelNeverUsesTheOrnamentOrWordJoiner() {
        val s = QuranText.withMarker("نص", 12)
        assertFalse('۝' in s)
        assertFalse('⁠' in s)
    }

    @Test
    fun searchNormaliserDropsHarakatAndFoldsAlef() {
        assertEquals("الحمد لله", QuranText.normaliseForSearch("ٱلْحَمْدُ لِلَّهِ"))
        assertEquals("ايمان", QuranText.normaliseForSearch("إيمَان"))
    }
}
