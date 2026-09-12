package world.taqwa.app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalTaqwaColors = staticCompositionLocalOf { LightColors }

/**
 * Whether the theme in force is the dark one. Every colour a screen needs is already in
 * [LocalTaqwaColors]; this exists for the one thing that is *not* a theme role — a reciter's
 * monogram hue (`world.taqwa.app.recitation.ReciterHue`), which is per-reciter data from the
 * manifest and carries its own light and dark pair. Comparing the palette instance against
 * `DarkColors` would work and would break the day a third palette exists.
 */
val LocalTaqwaDark = staticCompositionLocalOf { false }

@Composable
fun TaqwaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    SystemBarsAppearance(mode, dark)
    CompositionLocalProvider(LocalTaqwaColors provides colors, LocalTaqwaDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    background = colors.background,
                    surface = colors.surface,
                    onBackground = colors.textPrimary,
                    onSurface = colors.textPrimary,
                    primary = colors.accent,
                )
            } else {
                lightColorScheme(
                    background = colors.background,
                    surface = colors.surface,
                    onBackground = colors.textPrimary,
                    onSurface = colors.textPrimary,
                    primary = colors.accent,
                )
            },
            typography = TaqwaTypography(),
            content = content,
        )
    }
}
