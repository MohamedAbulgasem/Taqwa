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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.nav.Tab
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.tab_prayer
import world.taqwa.app.resources.tab_settings

/**
 * [content] with the tab bar beneath it when [current] names a tab root, and [content] alone —
 * the whole screen — when it is null.
 *
 * The screens inside pad themselves for `systemBars`. Once the bar is there, the bar is the thing
 * sitting over the gesture area, so that inset is consumed here and the content is left padding
 * for the status bar only. Without this the two would both pad for it and leave a visible gap.
 */
@Composable
fun TaqwaTabScaffold(current: Tab?, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    if (current == null) {
        content()
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) { content() }
        TaqwaTabBar(current, onSelect)
    }
}

/** The mockup's `.tab .ic`: 16 px square above the label, in a phone drawn 288 px wide. */
private val IconSize = 16.dp

/** Content height. The navigation-bar inset is added beneath it, never subtracted from it. */
private val BarHeight = 56.dp

/**
 * Prayer · Settings, as the mockup draws them: a hairline top border, the page background
 * beneath it (not a raised surface — the bar is the page's own edge, not a card), a 16 dp line
 * icon over a small semibold label, active in accent and inactive in tertiary.
 *
 * Nothing mirrors by hand. The items sit in a `Row`, which resolves against
 * `LocalLayoutDirection`, so under Arabic they run from the right; both glyphs are symmetric, so
 * the `Canvas` needs no help either.
 */
@Composable
fun TaqwaTabBar(current: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            Modifier
                .fillMaxWidth()
                // Outside the height, so the bar grows for the gesture bar rather than losing
                // its own 56 dp to it.
                .windowInsetsPadding(WindowInsets.navigationBars)
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
    val tint = if (selected) colors.accent else colors.textTertiary
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
                Tab.SETTINGS -> drawGear(tint)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                when (tab) {
                    Tab.PRAYER -> Res.string.tab_prayer
                    Tab.SETTINGS -> Res.string.tab_settings
                },
            ),
            // The mockup's 8.5 px of a 288 px phone is 11.5 dp on a real one; 11 sp is the app's
            // existing smallest size, and unlike `sectionLabel` this one carries no tracking —
            // these are words, not a legend.
            style = TaqwaText.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = tint,
        )
    }
}

/** Line weight of every glyph here: the mockup's 1.4 px in a 16 px box. */
private fun DrawScope.glyphStroke() = Stroke(width = size.width * 0.0875f, cap = StrokeCap.Round, join = StrokeJoin.Round)

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

/** A gear: hub, rim, eight teeth. Reads as "settings" with or without its label. */
private fun DrawScope.drawGear(tint: Color) {
    val u = size.width / 16f
    val c = Offset(8f * u, 8f * u)
    drawCircle(tint, radius = 2.1f * u, center = c, style = glyphStroke())
    drawCircle(tint, radius = 4.6f * u, center = c, style = glyphStroke())
    repeat(8) { i ->
        val a = i * (PI / 4)
        val (dx, dy) = cos(a).toFloat() to sin(a).toFloat()
        drawLine(
            color = tint,
            start = Offset(c.x + dx * 4.5f * u, c.y + dy * 4.5f * u),
            end = Offset(c.x + dx * 6.4f * u, c.y + dy * 6.4f * u),
            strokeWidth = size.width * 0.0875f,
            cap = StrokeCap.Round,
        )
    }
}
