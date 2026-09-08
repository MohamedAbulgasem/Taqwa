package world.taqwa.app.feature.quran

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchHighlightTest {
    private val style = SpanStyle(fontWeight = FontWeight.SemiBold)

    /** The styled ranges as the substrings they cover, which is what the row actually shows. */
    private fun highlighted(text: String, query: String): List<String> =
        highlightMatches(text, query, style).spanStyles.map { text.substring(it.start, it.end) }

    @Test
    fun everyOccurrenceIsStyled() {
        val text = "The Entirely Merciful, the Especially Merciful"
        val result = highlightMatches(text, "Merciful", style)
        assertEquals(text, result.text)
        assertEquals(listOf("Merciful", "Merciful"), highlighted(text, "Merciful"))
        assertTrue(result.spanStyles.all { it.item == style })
    }

    @Test
    fun matchingIgnoresCaseAndKeepsTheTextsOwn() {
        val text = "In the name of Allah, the Entirely Merciful"
        assertEquals(listOf("Merciful"), highlighted(text, "merciful"))
        assertEquals(text, highlightMatches(text, "MERCIFUL", style).text)
    }

    @Test
    fun surroundingSpaceIsIgnoredLikeTheSearchItself() {
        assertEquals(listOf("praise"), highlighted("All praise is due to Allah", "  praise "))
    }

    @Test
    fun emptyOrBlankQueryLeavesThePlainText() {
        val text = "All praise is due to Allah"
        assertEquals(emptyList(), highlightMatches(text, "", style).spanStyles)
        assertEquals(emptyList(), highlightMatches(text, "   ", style).spanStyles)
        assertEquals(text, highlightMatches(text, "", style).text)
    }

    @Test
    fun aQueryThatIsNotThereChangesNothing() {
        val text = "All praise is due to Allah"
        val result = highlightMatches(text, "merciful", style)
        assertEquals(text, result.text)
        assertEquals(emptyList(), result.spanStyles)
    }
}
