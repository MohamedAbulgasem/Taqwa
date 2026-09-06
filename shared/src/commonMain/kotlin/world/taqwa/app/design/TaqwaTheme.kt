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

@Composable
fun TaqwaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalTaqwaColors provides colors) {
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
