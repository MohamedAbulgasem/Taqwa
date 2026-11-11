package world.taqwa.app.wear.app

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sqrt
import world.taqwa.app.wear.R

/** The palette of the drawings (`faces.js`'s `C`, and its amber): the app's accent is amber alone (call 11). */
internal object WatchColors {
    val black = Color(0xFF000000)
    val ink = Color(0xFFF1F3F1)
    val mist = Color(0xFF8C948F)
    val slate = Color(0xFF5A625D)
    val hairline = Color(0xFF232825)
    val surface = Color(0xFF131614)
    val accent = Color(0xFFF0B429)
}

/**
 * Manrope, the phone's, in the four weights the screens draw (copies of the phone's files, the
 * Lead's call 2). The dhikr and his phrases use the system font, as the phone does.
 */
internal object WatchFonts {
    val manrope = FontFamily(
        Font(R.font.manrope_light, FontWeight.Light),
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    )

    /**
     * The weight a drawing's `font-weight` is drawn in: Light for 300 (the count and the countdown),
     * SemiBold for 600 and 700, ExtraBold for 800. 700 is named here, because left to Compose's
     * font matching it would come out ExtraBold, the nearest heavier weight the family holds.
     */
    fun weight(drawn: Int): FontWeight = when {
        drawn <= 300 -> FontWeight.Light
        drawn < 600 -> FontWeight.Normal
        drawn < 800 -> FontWeight.SemiBold
        else -> FontWeight.ExtraBold
    }
}

/**
 * The drawings' units: `faces.js` draws the round screen 200 units across, so a unit is [dp] of the
 * screen's shorter side over 200 (call 15).
 */
internal data class WatchUnits(val dp: Float) {

    /** [units] as the drawing gives them: the size every text size is tested against. */
    fun x(units: Float): Dp = (units * dp).dp

    /**
     * [units] as a font size. The size goes through the screen's own [density], whose `toSp`
     * inverts the font-scale converter text layout applies (from a scale of 1.03 it is not linear),
     * so a size is drawn at exactly that many units at every font scale. The font scale is applied
     * once, by [Line.range]; dividing by it here would shrink the big lines (call 15).
     */
    fun sp(units: Float, density: Density): TextUnit = with(density) { x(units).toSp() }

    companion object {
        const val ACROSS = 200f

        fun of(widthDp: Int, heightDp: Int): WatchUnits = WatchUnits(min(widthDp, heightDp) / ACROSS)

        /**
         * How wide, in units, a line at height [y] may be: the round screen's chord there, less
         * [inset] at each end, or the square's width less the insets. Never below 0, above or
         * below the screen included.
         */
        fun chordUnits(y: Float, round: Boolean, inset: Float = 6f): Float {
            if (!round) return (ACROSS - 2 * inset).coerceAtLeast(0f)
            val radius = ACROSS / 2
            val dy = y - radius
            val half = radius * radius - dy * dy
            if (half <= 0f) return 0f
            return (2 * sqrt(half) - 2 * inset).coerceAtLeast(0f)
        }
    }
}

/** The drawings' units of this screen ([WatchTheme]). */
internal val LocalWatchUnits = staticCompositionLocalOf { WatchUnits(1f) }

/** Whether this screen is round, for a line's chord ([WatchUnits.chordUnits]). */
internal val LocalRoundScreen = staticCompositionLocalOf { true }

/**
 * The direction a page is laid out in: the phone's, from the feed, whatever the watch is set to;
 * with no feed ([rtl] null), the watch's own, [system].
 */
internal fun layoutDirectionOf(rtl: Boolean?, system: LayoutDirection): LayoutDirection = when (rtl) {
    true -> LayoutDirection.Rtl
    false -> LayoutDirection.Ltr
    null -> system
}

/** The watch's own direction, from its configuration's `layoutDirection` (`View.LAYOUT_DIRECTION_*`). */
internal fun systemDirectionOf(layoutDirection: Int): LayoutDirection =
    if (layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr

/**
 * Wraps each page's content, and never the pager (call 4): the page is laid out in
 * [layoutDirectionOf] the feed's direction, and is given the screen's units and shape. The colours
 * and the fonts are [WatchColors] and [WatchFonts].
 *
 * The watch's own direction is read from the configuration and never from `LocalLayoutDirection`,
 * which the frame has set to Ltr so that the pager never mirrors.
 */
@Composable
internal fun WatchTheme(rtl: Boolean?, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    // The window's size, or the screen's while the window has none yet: a page is never given
    // units of nothing, which would size its text at 0 sp.
    val units = if (window.width > 0 && window.height > 0) {
        WatchUnits.of((window.width / density.density).toInt(), (window.height / density.density).toInt())
    } else {
        @Suppress("ConfigurationScreenWidthHeight") // Only before the window has its own size.
        WatchUnits.of(configuration.screenWidthDp, configuration.screenHeightDp)
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirectionOf(rtl, systemDirectionOf(configuration.layoutDirection)),
        LocalWatchUnits provides units,
        LocalRoundScreen provides configuration.isScreenRound,
        content = content,
    )
}
