package world.taqwa.app.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.contentWidth
import world.taqwa.app.nav.Tab
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.tab_prayer
import world.taqwa.app.resources.tab_quran
import world.taqwa.app.resources.tab_settings

/**
 * [content] with the tab bar beneath it when [current] names a tab root, and [content] alone —
 * the whole screen — when it is null.
 *
 * The screens inside pad themselves for `safeDrawing`. Once the bar is there, the bar is the thing
 * sitting over the gesture area, so that inset is consumed here and the content is left padding
 * for the status bar only. Without this the two would both pad for it and leave a visible gap.
 *
 * Only the *bottom* is consumed. Held sideways the navigation bar and the camera cutout move to
 * the left and right edges, where this bar — which spans the foot of the screen — covers nothing
 * at all, so the screen above must still pad for them itself.
 *
 * The bar itself pads for [BarInsets] rather than `safeDrawing`, because safeDrawing also carries
 * the keyboard: the bar would climb to sit on top of it. The keyboard covering the bar, and the
 * list under it, is what a tab root has always done — which is also why the *whole* bottom of
 * safeDrawing is consumed for the content.
 */
@Composable
fun TaqwaTabScaffold(current: Tab?, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    if (current == null) {
        content()
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                // The whole bottom of safeDrawing, keyboard included: the bar covers the bottom
                // edge, and a tab root has always let the keyboard cover the bar and the list
                // under it rather than reflowing around it.
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) { content() }
        TaqwaTabBar(current, onSelect)
    }
}

/**
 * What the bar and the space it occupies have to clear: the navigation bar, plus the camera cutout,
 * which sideways is a notch down one side rather than a band along the top. Deliberately not
 * `safeDrawing` — see [TaqwaTabScaffold].
 */
private val BarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets.displayCutout)

/** 22 dp square above the label — bigger than the mockup's original 16 px now that the bar
 * carries three glyphs instead of two and each needs to read clearly on its own. */
private val IconSize = 22.dp

/** Content height. The navigation-bar inset is added beneath it, never subtracted from it. */
private val BarHeight = 60.dp

/**
 * Prayer · Quran · Settings, as the mockup draws them: a hairline top border, the page background
 * beneath it (not a raised surface — the bar is the page's own edge, not a card), a 22 dp line
 * icon over a small semibold label, active in accent and inactive in secondary.
 *
 * Nothing mirrors by hand. The items sit in a `Row`, which resolves against
 * `LocalLayoutDirection`, so under Arabic they run from the right; the glyphs themselves are
 * not mirrored: the arch and the book are symmetric, and a sliders icon reads the same in either
 * direction, so the `Canvas` needs no help either.
 */
@Composable
fun TaqwaTabBar(current: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            Modifier
                // Capped and centred like every screen's content: three items spread across a
                // 900 dp landscape screen would put Prayer and Settings a hand apart, with the
                // thumb able to reach neither.
                .contentWidth()
                // Outside the height, so the bar grows for the gesture bar rather than losing
                // its own 56 dp to it. Sideways it is the horizontal insets that matter — the
                // navigation bar and the cutout are then at the ends of this row, not under it.
                .windowInsetsPadding(
                    BarInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .height(BarHeight),
        ) {
            Tab.entries.forEach { tab ->
                TabItem(tab, selected = tab == current, onSelect = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, selected: Boolean, onSelect: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val tint = if (selected) colors.accent else colors.textSecondary
    Column(
        // No ripple: a tab switch repaints the whole screen, which is feedback enough, and a
        // grey rectangle flashing across a third of the bar is louder than anything else here.
        Modifier.weight(1f).fillMaxHeight().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onSelect,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(IconSize)) {
            when (tab) {
                Tab.PRAYER -> drawMihrab(tint)
                Tab.QURAN -> drawBook(tint)
                Tab.SETTINGS -> drawSliders(tint)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(
                when (tab) {
                    Tab.PRAYER -> Res.string.tab_prayer
                    Tab.QURAN -> Res.string.tab_quran
                    Tab.SETTINGS -> Res.string.tab_settings
                },
            ),
            style = TaqwaText.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = tint,
        )
    }
}

/**
 * The app's own mark, the one onboarding draws at 116 dp: a prayer niche open at the foot, with
 * the dot near its crown. The same 1024-unit geometry as `MihrabMark`, scaled to 16. The dot
 * takes the tint too — an amber dot on a grey arch would keep the inactive tab lit.
 */
private fun DrawScope.drawMihrab(tint: Color) {
    val u = size.width / 16f
    val arch = Path().apply {
        moveTo(4.56f * u, 12.6f * u)
        lineTo(4.56f * u, 8.13f * u)
        cubicTo(4.56f * u, 5.81f * u, 5.94f * u, 4.19f * u, 8f * u, 3.38f * u)
        cubicTo(10.06f * u, 4.19f * u, 11.44f * u, 5.81f * u, 11.44f * u, 8.13f * u)
        lineTo(11.44f * u, 12.6f * u)
    }
    drawPath(arch, tint, style = glyphStroke())
    drawCircle(tint, radius = 0.95f * u, center = Offset(8f * u, 6.3f * u))
}

/**
 * Three sliders: full-width tracks with a knob riding each one. The gear it replaces was a busy
 * radial shape next to two calm line drawings; this is the same handful of strokes as the book —
 * straight lines and one filled dot per row, on the same 16-unit grid — so the three tabs read as
 * one set. The knobs sit at different points along their tracks (a gear's eight-fold symmetry was
 * part of what made it look mechanical rather than drawn); each is painted over its track, which
 * at 22 dp reads as a knob on the line rather than a gap in it.
 *
 * The knob radius is 1.5 u against a 0.7 u half-stroke. Anything under about 1.3 u leaves less
 * than half a pixel of bulge either side at 22 dp and the glyph collapses into three plain rules;
 * 1.5 u is also what brings its ink close to the book's, which is the denser of the other two.
 * With the knobs it spans y 2.5–13.5, near enough the book's 1.9–13.7 to sit level with it.
 */
private fun DrawScope.drawSliders(tint: Color) {
    val u = size.width / 16f
    val stroke = size.width * 0.0875f
    listOf(4f to 10.5f, 8f to 5.5f, 12f to 9f).forEach { (y, knobX) ->
        drawLine(
            color = tint,
            start = Offset(2.2f * u, y * u),
            end = Offset(13.8f * u, y * u),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(tint, radius = 1.5f * u, center = Offset(knobX * u, y * u))
    }
}
