package world.taqwa.app.widget

import world.taqwa.app.domain.WidgetBackground

/**
 * background -> colour and alpha, decided once here so the live preview in Settings and both
 * real widgets can never disagree about what a choice looks like. Plain ARGB longs rather than
 * a UI-framework colour type, so this stays usable from Glance and from a value bridged into
 * Swift without either platform's widget target needing a Compose dependency.
 */
data class WidgetPaletteColors(
    val backgroundArgb: Long,
    val textArgb: Long,
    val accentArgb: Long,
    val backgroundAlpha: Float,
)

object WidgetPalette {
    fun colorsFor(background: WidgetBackground, systemIsDark: Boolean): WidgetPaletteColors {
        val dark = when (background) {
            WidgetBackground.FOLLOW_THEME, WidgetBackground.TRANSLUCENT_OR_FROSTED -> systemIsDark
            WidgetBackground.LIGHT -> false
            WidgetBackground.DARK -> true
        }
        val alpha = if (background == WidgetBackground.TRANSLUCENT_OR_FROSTED) 0.55f else 1.0f
        // Dark widgets sit on the dark theme's *card* colour, not its page colour: on a home
        // screen the near-black page tone read as a hole in the wallpaper, and the card tone is
        // what the in-app ayah card and prayer rows are drawn on (Mohamed, 10 September 2026).
        return if (dark) {
            WidgetPaletteColors(0xFF131614L, 0xFFF1F3F1L, 0xFFF0B429L, alpha)
        } else {
            WidgetPaletteColors(0xFFFBFAF7L, 0xFF16160FL, 0xFFB5820BL, alpha)
        }
    }
}
