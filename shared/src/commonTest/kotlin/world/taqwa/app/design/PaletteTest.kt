package world.taqwa.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PaletteTest {
    @Test
    fun lightAccentIsDeepenedForContrastOnPaper() {
        assertEquals(Color(0xFFB5820B), LightColors.accent)
    }

    @Test
    fun darkAccentIsFullStrength() {
        assertEquals(Color(0xFFF0B429), DarkColors.accent)
    }

    @Test
    fun accentDiffersBetweenModes() {
        assertNotEquals(LightColors.accent, DarkColors.accent)
    }

    @Test
    fun darkIsNotAnInvertedLightMode() {
        assertEquals(Color(0xFF0B0D0C), DarkColors.background)
        assertEquals(Color(0xFF131614), DarkColors.surface)
        assertNotEquals(DarkColors.background, DarkColors.surface)
    }

    /** WCAG's contrast ratio of two colours, from their relative luminance. */
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * The watch card's action is 14 sp amber words: WCAG AA asks 4.5:1 of text that size, on the
     * card and on the page it sits on. The accent itself is 3.4:1 on white, so the words need a
     * deeper amber in light; dark's accent already passes.
     */
    @Test
    fun accentTextReadsAtFourAndAHalfToOneOnTheCardAndThePage() {
        for (c in listOf(LightColors, DarkColors)) {
            assertTrue(contrast(c.accentText, c.surface) >= 4.5, "on the card: ${contrast(c.accentText, c.surface)}")
            assertTrue(contrast(c.accentText, c.background) >= 4.5, "on the page: ${contrast(c.accentText, c.background)}")
        }
    }

    @Test
    fun lightAccentTextIsTheDeeperAmber() = assertEquals(Color(0xFF946A0A), LightColors.accentText)

    @Test
    fun darkAccentTextIsTheAccent() = assertEquals(DarkColors.accent, DarkColors.accentText)
}
