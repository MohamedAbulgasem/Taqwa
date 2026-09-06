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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.nav.Tab
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.tab_qibla
import world.taqwa.app.resources.tab_settings
import world.taqwa.app.resources.tab_today

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
 * Today · Qibla · Settings, as the mockup draws them: a hairline top border, the page background
 * beneath it (not a raised surface — the bar is the page's own edge, not a card), a 16 dp line
 * icon over a small semibold label, active in accent and inactive in tertiary.
 *
 * Nothing mirrors by hand. The items sit in a `Row`, which resolves against
 * `LocalLayoutDirection`, so under Arabic they run Today · Qibla · Settings from the right. The
 * one exception is the ring mark's arc, drawn from literal angles into a `Canvas` — it follows
 * `CountdownRing`'s rule and sweeps the other way, because an Arabic reader's clock hand does.
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
                Tab.TODAY -> drawRingMark(tint, colors.hairline, rtl)
                Tab.QIBLA -> drawCompass(tint)
                Tab.SETTINGS -> drawSliders(tint)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                when (tab) {
                    Tab.TODAY -> Res.string.tab_today
                    Tab.QIBLA -> Res.string.tab_qibla
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

/**
 * The app's mark, the same one onboarding shows at 88 dp: a hairline ring with a short arc off
 * the top. The arc carries the tint, so the mark lights amber when Today is the current tab and
 * goes quiet when it is not — the ring behind it stays hairline in both states, which is what
 * keeps a 16 dp circle legible as a circle.
 */
private fun DrawScope.drawRingMark(tint: Color, ring: Color, rtl: Boolean) {
    val stroke = size.width * 0.13f
    drawCircle(color = ring, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke))
    drawArc(
        color = tint,
        startAngle = -90f,
        sweepAngle = if (rtl) -108f else 108f,
        useCenter = false,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/**
 * A ring with a needle — the glyph Today's header used for the qibla button before qibla became
 * a tab. Line work only, to match [CheckMark].
 */
internal fun DrawScope.drawCompass(tint: Color) {
    val w = size.width
    drawCircle(color = tint, radius = w * 0.46f, style = Stroke(width = w * 0.09f))
    // A rhombus along the north-east diagonal: tip, one flank, tail, the other flank.
    val needle = Path().apply {
        moveTo(w * 0.712f, w * 0.288f)
        lineTo(w * 0.575f, w * 0.575f)
        lineTo(w * 0.288f, w * 0.712f)
        lineTo(w * 0.425f, w * 0.425f)
        close()
    }
    drawPath(needle, tint)
}

/** Three sliders — the settings glyph, and less fussy than a gear at this size. */
internal fun DrawScope.drawSliders(tint: Color) {
    val w = size.width
    listOf(0.24f to 0.66f, 0.5f to 0.34f, 0.76f to 0.58f).forEach { (y, knob) ->
        drawLine(
            color = tint,
            start = Offset(w * 0.1f, w * y),
            end = Offset(w * 0.9f, w * y),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = tint, radius = w * 0.13f, center = Offset(w * knob, w * y))
    }
}
