package world.taqwa.app.feature.quran

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicSearchHighlightTest {
    private val style = SpanStyle(fontWeight = FontWeight.Bold)
    private val basmala = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"

    private fun AnnotatedString.lit(): List<String> =
        spanStyles.map { text.substring(it.start, it.end) }

    @Test
    fun `a matched word is lit whole with its harakat`() {
        val result = highlightArabicWords(basmala, setOf(2), style)
        assertEquals(basmala, result.text)
        assertEquals(listOf("ٱلرَّحْمَـٰنِ"), result.lit())
    }

    @Test
    fun `every matched word is lit`() {
        assertEquals(listOf("ٱللَّهِ", "ٱلرَّحِيمِ"), highlightArabicWords(basmala, setOf(1, 3), style).lit())
    }

    @Test
    fun `a span never starts or ends inside a word`() {
        val result = highlightArabicWords(basmala, setOf(0, 2), style)
        result.spanStyles.forEach { span ->
            assertTrue(span.start == 0 || result.text[span.start - 1].isWhitespace())
            assertTrue(span.end == result.text.length || result.text[span.end].isWhitespace())
        }
    }

    @Test
    fun `a late match opens the line two words before it`() {
        val text = "one two three four five six ٱلْقَيُّومُ eight"
        val result = highlightArabicWords(text, setOf(6), style)
        assertEquals("…\u00A0five six ٱلْقَيُّومُ eight", result.text)
        assertEquals(listOf("ٱلْقَيُّومُ"), result.lit())
    }

    @Test
    fun `a match within the lead keeps the line whole`() {
        val text = "one two ٱلْقَيُّومُ four five"
        assertEquals(text, highlightArabicWords(text, setOf(2), style).text)
    }

    @Test
    fun `no matched words leaves the line untouched`() {
        val result = highlightArabicWords(basmala, emptySet(), style)
        assertEquals(basmala, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `a position past the end of the line is ignored`() {
        val result = highlightArabicWords(basmala, setOf(9), style)
        assertEquals(basmala, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }
}
