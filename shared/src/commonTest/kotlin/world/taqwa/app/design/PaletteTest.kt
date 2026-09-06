package world.taqwa.app.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
}
