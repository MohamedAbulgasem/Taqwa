package world.taqwa.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TaqwaColors(
    val background: Color,
    val surface: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val ring: Color,
)

val LightColors = TaqwaColors(
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFFFFFF),
    hairline = Color(0xFFE7E5DD),
    textPrimary = Color(0xFF16160F),
    textSecondary = Color(0xFF6F6E62),
    textTertiary = Color(0xFFA5A498),
    accent = Color(0xFFB5820B),
    ring = Color(0xFFE3A21C),
)

val DarkColors = TaqwaColors(
    background = Color(0xFF0B0D0C),
    surface = Color(0xFF131614),
    hairline = Color(0xFF232825),
    textPrimary = Color(0xFFF1F3F1),
    textSecondary = Color(0xFF8C948F),
    textTertiary = Color(0xFF5A625D),
    accent = Color(0xFFF0B429),
    ring = Color(0xFFF0B429),
)
