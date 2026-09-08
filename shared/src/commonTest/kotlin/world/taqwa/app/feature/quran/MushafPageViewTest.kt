package world.taqwa.app.feature.quran

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [annotateLine] (spec §5.2/§2.4) over a real line from [MUSHAF_PAGE_2] — page 2's third line,
 * which is exactly the shape that matters here: ayah 1 ("الٓمٓ") ends within the line on its own
 * single word, so it contributes exactly one trailing-digit accent span, and ayah 2 spans six
 * consecutive words with no digit of its own inside this line, so highlighting it contributes
 * exactly one background span covering that whole run rather than one per word.
 */
class MushafPageViewTest {

    private val line = MUSHAF_PAGE_2.lines[2]
    private val ranges = wordRanges(line)
    private val accent = Color(0xFFB5820B)

    @Test
    fun oneAccentSpanPerWordsTrailingDigitRun() {
        val annotated = annotateLine(line.text.orEmpty(), ranges, highlighted = null, accent = accent)
        val digitSpans = annotated.spanStyles.filter { it.item.color == accent }
        // Only the line's first word ("الٓمٓ ١") ends an ayah within this line; every other word's
        // ayah (2) ends on a later line, so no other word carries a trailing digit run here.
        assertEquals(1, digitSpans.size)
        val firstWord = ranges.first()
        // The span covers only the trailing digit run within the first word — "١" — not the rest
        // of that word and not the rest of the line.
        assertEquals(firstWord.start + firstWord.word.text.indexOf('١'), digitSpans.first().start)
        assertEquals(firstWord.end, digitSpans.first().end)
    }

    @Test
    fun oneBackgroundSpanPerContiguousHighlightedRunNotPerWord() {
        // Ayah 2 (surah 2) is six consecutive words in this line (indices 1..6 of `ranges`) with
        // no other ayah's word interleaved, so highlighting it must add exactly one background
        // span across the whole run, not six.
        val annotated = annotateLine(line.text.orEmpty(), ranges, highlighted = 2 to 2, accent = accent)
        val backgroundSpans = annotated.spanStyles.filter { it.item.background != Color.Unspecified }
        assertEquals(1, backgroundSpans.size)
        val ayah2Ranges = ranges.filter { it.word.ayah == 2 }
        assertEquals(ayah2Ranges.first().start, backgroundSpans.first().start)
        assertEquals(ayah2Ranges.last().end, backgroundSpans.first().end)

        // The digit accent span for ayah 1 is unaffected by highlighting a different ayah.
        val digitSpans = annotated.spanStyles.filter { it.item.color == accent }
        assertEquals(1, digitSpans.size)
    }
}
