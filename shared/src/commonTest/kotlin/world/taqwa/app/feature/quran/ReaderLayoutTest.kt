package world.taqwa.app.feature.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderLayoutTest {

    @Test
    fun withoutABasmalaTheFirstItemIsAyahIndexZero() {
        assertEquals(0, firstVisibleAyahIndex(firstVisibleItemIndex = 0, hasBasmala = false))
        assertEquals(1, firstVisibleAyahIndex(firstVisibleItemIndex = 1, hasBasmala = false))
    }

    @Test
    fun withABasmalaTheBasmalaItemStillMapsToAyahIndexZeroNotNegativeOne() {
        // Item 0 is the basmala itself: the regression this guards against mapped it to -1 and the
        // caller's ayah lookup silently skipped it, so the very top of the surah was never
        // reported as the first visible ayah.
        assertEquals(0, firstVisibleAyahIndex(firstVisibleItemIndex = 0, hasBasmala = true))
        // Item 1 is ayah 1 itself, so it also maps to index 0.
        assertEquals(0, firstVisibleAyahIndex(firstVisibleItemIndex = 1, hasBasmala = true))
    }
}
