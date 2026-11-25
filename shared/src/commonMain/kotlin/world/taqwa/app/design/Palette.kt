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
    /**
     * Amber words on a card, 4.5:1 or better: the watch card's action. Light is a deeper amber
     * than [accent], which is 3.4:1 on white at 14 sp; dark's accent already reads at that.
     */
    val accentText: Color,
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
    accentText = Color(0xFF946A0A),
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
    accentText = Color(0xFFF0B429),
    ring = Color(0xFFF0B429),
)

/**
 * The stand-in wallpaper the widget previews in Settings › Widgets sit on — a flat, deliberately
 * uninteresting neutral, not a photo, so the eye reads the widget card rather than the backdrop.
 *
 * It lives here rather than inline at the one call site for a greppable reason: `Palette.kt` and
 * `widget/WidgetPalette.kt` are meant to be the only two files in the tree holding a colour
 * literal, which is what keeps "the app uses amber and nothing else" a claim you can verify with
 * a search instead of a reading. It is not part of [TaqwaColors] because it is not a theme role —
 * it stands in for the user's wallpaper, which no theme controls, and so is the same in light and
 * dark.
 */
val PreviewWallpaper = Color(0xFF9C8F73)

/**
 * The stand-in Lock Screen behind the iPhone lock-screen widget's preview in Settings › Widgets: a
 * dark, neutral slate, because the widget sits under the clock in white on a Lock Screen whichever
 * theme the phone is in. Not a theme role for the same reason [PreviewWallpaper] is not; the type
 * drawn on it is [DarkColors]' own, so it matches the dark theme's ink.
 */
val PreviewLockScreen = Color(0xFF22262A)

/**
 * The real watch in Home's watch card (its 52 dp drawing): the case, its black screen and the
 * mihrab's arch on the Taqwa face. Not theme roles, for the reason [PreviewWallpaper] is not: they
 * stand for a real object, a dark watch wearing the face, which is the same in light and dark. The
 * face's own ink and amber are [DarkColors]' (the face is dark on every watch).
 */
object WatchDrawing {
    val case = Color(0xFF2B2F2D)
    val screen = Color(0xFF000000)
    val arch = Color(0xFF6A716C)
}
