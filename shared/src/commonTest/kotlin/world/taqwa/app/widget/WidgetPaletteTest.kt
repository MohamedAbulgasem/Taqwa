package world.taqwa.app.widget

import world.taqwa.app.domain.WidgetBackground
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WidgetPaletteTest {

    @Test
    fun followThemeTracksTheSystemSetting() {
        val light = WidgetPalette.colorsFor(WidgetBackground.FOLLOW_THEME, systemIsDark = false)
        val dark = WidgetPalette.colorsFor(WidgetBackground.FOLLOW_THEME, systemIsDark = true)
        assertNotEquals(light.backgroundArgb, dark.backgroundArgb)
    }

    @Test
    fun lightIsAlwaysLightRegardlessOfSystemSetting() {
        val a = WidgetPalette.colorsFor(WidgetBackground.LIGHT, systemIsDark = false)
        val b = WidgetPalette.colorsFor(WidgetBackground.LIGHT, systemIsDark = true)
        assertEquals(a.backgroundArgb, b.backgroundArgb)
    }

    @Test
    fun darkIsAlwaysDarkRegardlessOfSystemSetting() {
        val a = WidgetPalette.colorsFor(WidgetBackground.DARK, systemIsDark = false)
        val b = WidgetPalette.colorsFor(WidgetBackground.DARK, systemIsDark = true)
        assertEquals(a.backgroundArgb, b.backgroundArgb)
    }

    @Test
    fun onlyTranslucentOrFrostedHasReducedAlpha() {
        assertEquals(1.0f, WidgetPalette.colorsFor(WidgetBackground.LIGHT, false).backgroundAlpha)
        assertEquals(1.0f, WidgetPalette.colorsFor(WidgetBackground.DARK, false).backgroundAlpha)
        val translucent = WidgetPalette.colorsFor(WidgetBackground.TRANSLUCENT_OR_FROSTED, false).backgroundAlpha
        kotlin.test.assertTrue(translucent < 1.0f)
    }
}
