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

    @Test
    fun splitMarkerReversesWithMarkerExactly() {
        val marked = QuranText.withMarker("نص", 255)
        val (base, marker) = QuranText.splitMarker(marked)
        assertEquals("نص" + QuranText.MARKER_SEPARATOR, base)
        assertEquals("٢٥٥", marker)
        assertEquals(marked, base + marker)
    }
}
