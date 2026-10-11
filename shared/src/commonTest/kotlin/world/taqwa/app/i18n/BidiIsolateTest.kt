package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

// First-strong isolate and pop-directional isolate, written as escapes: in source they are invisible.
private const val FSI = "\u2068"
private const val PDI = "\u2069"
private const val NBSP = "\u00A0"

/**
 * The one isolate every spliced clock time goes through. Its promise to the default setting is the
 * first test: a 24-hour time, in any of the digit sets the app writes, is returned untouched, so a
 * sentence built with it is byte for byte the sentence built without it.
 */
class BidiIsolateTest {

    @Test
    fun `a twenty four hour time comes back byte for byte`() {
        for (time in listOf("18:32", "3:07", "0:05", "١٨:٣٢", "٣:٠٧", "১৮:৩২", "۱۸:۳۲")) {
            assertEquals(time, isolatedClock(time))
        }
    }

    @Test
    fun `a time that carries a day period marker is isolated`() {
        // The marker after (English and Urdu, Latin), after in its own script (Arabic), and before (Turkish).
        for (time in listOf("6:32${NBSP}PM", "6:32${NBSP}pm", "٦:٣٢${NBSP}م", "ÖS${NBSP}6:32", "৬:৩২${NBSP}PM")) {
            assertEquals("$FSI$time$PDI", isolatedClock(time))
        }
    }

    @Test
    fun `isolated wraps any run in a first strong isolate and its pop`() {
        assertEquals("${FSI}Umm al-Qura$PDI", isolated("Umm al-Qura"))
        assertEquals("${FSI}أم القرى$PDI", isolated("أم القرى"))
    }
}
