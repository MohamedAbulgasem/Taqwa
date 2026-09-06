package world.taqwa.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import world.taqwa.app.R
import world.taqwa.app.domain.WidgetBackground

/**
 * One widget, drawn to fit whatever cell it is given.
 *
 * The two receivers differ only in the cell they ask for by default (2x2 and 4x2). Past that the
 * user can drag either to any size from a single cell up, and the layout is chosen from the
 * measured size rather than from which provider was placed: a small widget stretched to four
 * columns becomes the two-column card, a medium one squeezed to one row becomes the strip.
 *
 * [SizeMode.Exact] is what makes this honest. Under `Responsive` Glance reported the *declared*
 * size nearest to the cell rather than the cell itself, so a 4x2 that the launcher actually drew
 * at 267x150 dp laid itself out for 250x110 and left a band of dead space above and below. Every
 * number below is derived from the real width and height.
 */

/** Everything one draw of the widget needs, resolved at the moment it draws. */
internal class WidgetRender(
    val content: WidgetContent?,
    val remainingMinutes: Long?,
    val colors: WidgetPaletteColors,
    val languageTag: String,
)

private fun readWidgetRender(context: Context): WidgetRender {
    val store = createWidgetKeyValueStore()
    val snapshot = WidgetMirrorWriter.read(store)
    val background = store.getString("widget_background")
        ?.let { raw -> WidgetBackground.entries.firstOrNull { it.name == raw } }
        ?: WidgetBackground.FOLLOW_THEME
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val systemIsDark = nightMode == Configuration.UI_MODE_NIGHT_YES
    return WidgetRender(
        content = snapshot?.let(WidgetContentBuilder::build),
        // `provideGlance` runs on every re-render (the half-hourly APPWIDGET_UPDATE, the rolling
        // five-minute window alarm, the prayer-boundary alarm and the unlock receiver), so this
        // reads the clock as it is *now*, not as it was when the app last had Today open.
        remainingMinutes = snapshot?.let {
            WidgetCountdown.remainingMinutesAt(it, System.currentTimeMillis() / 1_000L)
        },
        colors = WidgetPalette.colorsFor(background, systemIsDark),
        languageTag = snapshot?.languageTag.orEmpty(),
    )
}

// -- Palette-derived colour roles -------------------------------------------------------------

private fun WidgetPaletteColors.cardBackground() = Color(backgroundArgb).copy(alpha = backgroundAlpha)
private fun WidgetPaletteColors.primaryText() = Color(textArgb)
private fun WidgetPaletteColors.secondaryText() = Color(textArgb).copy(alpha = 0.62f)
private fun WidgetPaletteColors.hairline() = Color(textArgb).copy(alpha = 0.14f)
private fun WidgetPaletteColors.accent() = Color(accentArgb)

// -- Layout selection --------------------------------------------------------------------------

/** Below this height the cell is a single launcher row and the card becomes a strip. */
private val STRIP_MAX_HEIGHT = 80.dp

/** Below this width a strip is a single cell and carries the countdown alone. */
private val TINY_MAX_WIDTH = 100.dp

/**
 * From this width the card has room for the prayer list beside the countdown. Derived, not
 * guessed: the smallest countdown block is 61 dp, the longest row needs 11.5 sp x 10.1 em beside
 * it, plus padding and gaps, which comes to about 230 dp. A Pixel's 2x2 is 190 dp and stays a
 * stacked card; a 3x2 or 4x2 on any launcher measured so far clears it.
 */
private val TWO_COLUMN_MIN_WIDTH = 230.dp

/** "12:34" in the widget face: two digits, a colon and two more, in em. */
private const val COUNTDOWN_EM = 2.51f

/** The longest prayer row, "Maghrib · المغرب" in bold and "18:32" beside it, in em. */
private const val LONGEST_ROW_EM = 10.1f

private fun Float.sp(min: Float, max: Float): TextUnit = coerceIn(min, max).sp

/**
 * Two blocks side by side, iOS's medium widget on Android: the countdown block on the start
 * side and the five prayers spread down the full height of the end side, current one in accent.
 */
@Composable
private fun TwoColumnCard(render: WidgetRender, size: DpSize) {
    val colors = render.colors
    val content = render.content
    val padH = 16.dp
    val padV = 14.dp
    val columnGap = 12.dp
    val available = size.width - padH * 2 - columnGap
    // The countdown block is exactly as wide as "12:34" needs at its size, no wider: every dp it
    // does not use goes to the prayer list, whose bilingual names are the longest text on the card.
    val countdown = minOf(size.height.value * 0.28f, available.value * 0.40f / COUNTDOWN_EM).sp(22f, 40f)
    val leftWidth = (countdown.value * COUNTDOWN_EM + 6f).dp
    val label = (countdown.value * 0.38f).sp(12f, 15f)
    val clock = (countdown.value * 0.36f).sp(12f, 14f)
    val innerHeight = size.height.value - padV.value * 2
    val rowByHeight = innerHeight / 5f * 0.58f
    // "Maghrib · المغرب" in bold plus its time is about ten em; a row is sized so that fits the
    // list column whole, and the height only ever makes it larger, never the width smaller.
    val rowByWidth = (available.value - leftWidth.value - 8f) / LONGEST_ROW_EM
    val rowText = minOf(rowByHeight, rowByWidth).sp(11f, 15f)

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.width(leftWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            NextPrayerBlock(render, label, countdown, clock, labelGap = 2.dp, clockGap = 2.dp, centered = false)
        }
        Spacer(GlanceModifier.width(columnGap))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            content?.rows?.forEach { row ->
                val rowColor = ColorProvider(if (row.isCurrent) colors.accent() else colors.primaryText())
                val rowWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal
                // Each row takes an equal share of the height, so five of them fill the card
                // however tall the launcher draws it instead of huddling in the middle.
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The name is the part that gives when the two do not fit: the time is the
                    // row's payload and stays whole.
                    Text(
                        text = row.displayName,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = rowText),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = row.clockTime,
                        style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = rowText),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The 2x2 and everything square-ish: label, countdown, clock time, stacked and centred. */
@Composable
private fun StackedCard(render: WidgetRender, size: DpSize) {
    val pad = 12.dp
    val countdown = minOf((size.width.value - pad.value * 2) / COUNTDOWN_EM, size.height.value * 0.34f).sp(24f, 56f)
    val label = (countdown.value * 0.30f).sp(11f, 16f)
    val clock = (countdown.value * 0.40f).sp(12f, 22f)
    val gap = (countdown.value * 0.16f).dp
    Box(modifier = GlanceModifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
        NextPrayerBlock(render, label, countdown, clock, labelGap = gap, clockGap = gap * 0.6f, centered = true)
    }
}

/** A single launcher row, two or more cells wide: label and clock on the start side, countdown
 * on the end side. */
@Composable
private fun StripCard(render: WidgetRender, size: DpSize) {
    val colors = render.colors
    val padH = 14.dp
    val countdown = minOf(size.height.value * 0.46f, (size.width.value * 0.5f) / COUNTDOWN_EM).sp(18f, 32f)
    val small = (countdown.value * 0.45f).sp(11f, 13f)
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = padH, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = labelText(render),
                style = TextStyle(color = ColorProvider(colors.accent()), fontWeight = FontWeight.Bold, fontSize = small),
                maxLines = 2,
            )
            render.content?.let {
                Text(
                    text = it.nextClockTime,
                    style = TextStyle(color = ColorProvider(colors.secondaryText()), fontSize = small),
                    maxLines = 1,
                )
            }
        }
        render.remainingMinutes?.let {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = countdownText(it, render.languageTag),
                style = TextStyle(color = ColorProvider(colors.primaryText()), fontWeight = FontWeight.Medium, fontSize = countdown),
                maxLines = 1,
            )
        }
    }
}

/** One cell: the prayer's short name and the countdown, nothing else fits. */
@Composable
private fun TinyCard(render: WidgetRender, size: DpSize) {
    val colors = render.colors
    val countdown = minOf((size.width.value - 12f) / COUNTDOWN_EM, size.height.value * 0.42f).sp(14f, 26f)
    val label = (countdown.value * 0.5f).sp(9f, 12f)
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shortName(render),
            style = TextStyle(color = ColorProvider(colors.accent()), fontWeight = FontWeight.Bold, fontSize = label, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        render.remainingMinutes?.let {
            Text(
                text = countdownText(it, render.languageTag),
                style = TextStyle(color = ColorProvider(colors.primaryText()), fontWeight = FontWeight.Medium, fontSize = countdown),
                maxLines = 1,
            )
        }
    }
}

// -- Shared pieces ------------------------------------------------------------------------------

/** The already-localised "next prayer in" phrase, or the plain name when no honest countdown
 * exists so that "in" never dangles; "Taqwa" before the app has ever written a snapshot. */
private fun labelText(render: WidgetRender): String {
    val content = render.content ?: return "Taqwa"
    return if (render.remainingMinutes == null) content.nextPrayerDisplayName else content.countdownLabel
}

/** "Maghrib · المغرب" -> "Maghrib": the bilingual name is two words too many for one cell. */
private fun shortName(render: WidgetRender): String =
    render.content?.nextPrayerDisplayName?.substringBefore(" · ")?.trim() ?: "Taqwa"

/** Built here by interpolation, so it needs its own pass through [WidgetDigits.localize] to match
 * the pre-formatted clock times beside it (I9). */
private fun countdownText(remainingMinutes: Long, languageTag: String): String {
    val hours = remainingMinutes / 60
    val minutes = remainingMinutes % 60
    return WidgetDigits.localize("$hours:${minutes.toString().padStart(2, '0')}", languageTag)
}

@Composable
private fun NextPrayerBlock(
    render: WidgetRender,
    label: TextUnit,
    countdown: TextUnit,
    clock: TextUnit,
    labelGap: Dp,
    clockGap: Dp,
    centered: Boolean,
) {
    val colors = render.colors
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = labelText(render),
            style = TextStyle(color = ColorProvider(colors.accent()), fontWeight = FontWeight.Bold, fontSize = label, textAlign = align),
            maxLines = 2,
        )
        val content = render.content ?: return@Column
        // Omitted entirely when the mirror is too stale to place the next prayer, rather than
        // shown as a number that was true hours ago (I8).
        render.remainingMinutes?.let {
            Spacer(GlanceModifier.height(labelGap))
            Text(
                text = countdownText(it, render.languageTag),
                style = TextStyle(color = ColorProvider(colors.primaryText()), fontWeight = FontWeight.Medium, fontSize = countdown, textAlign = align),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(clockGap))
        Text(
            text = content.nextClockTime,
            style = TextStyle(color = ColorProvider(colors.secondaryText()), fontWeight = FontWeight.Normal, fontSize = clock, textAlign = align),
            maxLines = 1,
        )
    }
}

/** Rounded card with a 1dp hairline border. The border is a tinted image because Glance 1.1.1
 * has no border modifier. */
@Composable
private fun WidgetCard(colors: WidgetPaletteColors, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(colors.cardBackground()))
            .cornerRadius(19.dp)
            .appWidgetBackground(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_card_hairline),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(ColorProvider(colors.hairline())),
        )
        content()
    }
}

/** The one layout decision, from the measured cell. */
@Composable
internal fun TaqwaWidgetContent(render: WidgetRender) {
    val size = LocalSize.current
    WidgetCard(render.colors) {
        when {
            size.height < STRIP_MAX_HEIGHT && size.width < TINY_MAX_WIDTH -> TinyCard(render, size)
            size.height < STRIP_MAX_HEIGHT -> StripCard(render, size)
            size.width >= TWO_COLUMN_MIN_WIDTH -> TwoColumnCard(render, size)
            else -> StackedCard(render, size)
        }
    }
}

/** Both receivers draw this; they differ only in the default cell declared in their XML. */
abstract class TaqwaGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // The mirror is read *inside* the content lambda, not before it. Glance keeps a session
        // alive per widget for a while after a draw, and an `update()` that lands on a live
        // session only recomposes this lambda; the code above `provideContent` does not run
        // again. Read out here, a widget drawn once before the app had written anything kept
        // showing the empty card through every refresh that followed, sitting beside a twin of
        // the same provider that had all five times.
        provideContent { TaqwaWidgetContent(readWidgetRender(context)) }
    }
}

class TaqwaSmallGlanceWidget : TaqwaGlanceWidget()

class TaqwaMediumGlanceWidget : TaqwaGlanceWidget()
