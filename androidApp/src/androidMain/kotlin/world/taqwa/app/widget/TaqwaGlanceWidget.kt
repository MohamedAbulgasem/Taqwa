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
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
    val nowEpochSeconds = System.currentTimeMillis() / 1_000L
    return WidgetRender(
        // Worked out for *now* from the mirror's two-day schedule (next prayer, current prayer,
        // the rows), not read as it stood when the app last had Today open: the render runs on
        // the half-hourly APPWIDGET_UPDATE, the rolling five-minute alarm, the prayer alarm and
        // the unlock receiver, and after Isha it has to say "Fajr in" without the app's help.
        content = snapshot?.let { WidgetContentBuilder.build(it, nowEpochSeconds) },
        remainingMinutes = snapshot?.let { WidgetCountdown.remainingMinutesAt(it, nowEpochSeconds) },
        colors = WidgetPalette.colorsFor(background, systemIsDark),
        languageTag = snapshot?.languageTag.orEmpty(),
    )
}

// -- Palette-derived colour roles -------------------------------------------------------------

private fun WidgetPaletteColors.cardBackground() = Color(backgroundArgb).copy(alpha = backgroundAlpha)
private fun WidgetPaletteColors.primaryText() = Color(textArgb)
private fun WidgetPaletteColors.secondaryText() = Color(textArgb).copy(alpha = 0.62f)
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

/** "12:34" in the widget face: two digits, a colon and two more, in em. The two-column card
 * sizes its countdown block to this worst case so the prayer list beside it never moves. */
private const val COUNTDOWN_EM = 2.51f

/**
 * The width of the countdown actually on screen, in em: a digit is about 0.56 em and the colon
 * 0.28 em in the system face. "0:08" is 1.96 em, "12:34" is 2.52. The single-block layouts size
 * the number for the string they have rather than for the worst case, which is why the 2x2 no
 * longer draws a 56 sp number inside a card that could hold 70.
 */
private fun countdownEm(text: String): Float =
    text.count { it.isDigit() } * 0.56f + text.count { !it.isDigit() } * 0.28f

/** The longest prayer row, "Maghrib · المغرب" in bold and "18:32" beside it, in em. */
private const val LONGEST_ROW_EM = 10.1f

/** One prayer row at the smallest row size (11 sp) with its line height, in dp. */
private const val MIN_ROW_HEIGHT_DP = 15f

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
    // A single launcher row on a tall-celled phone still clears the strip threshold, and there
    // the card cannot spare its two-row padding: the rows were clipped at the baseline.
    val padV = if (size.height < 110.dp) 8.dp else 14.dp
    val columnGap = 12.dp
    val available = size.width - padH * 2 - columnGap
    // The countdown block is exactly as wide as "12:34" needs at its size, no wider: every dp it
    // does not use goes to the prayer list, whose bilingual names are the longest text on the card.
    val countdown = minOf(size.height.value * 0.28f, available.value * 0.40f / COUNTDOWN_EM).sp(22f, 40f)
    val leftWidth = (countdown.value * COUNTDOWN_EM + 6f).dp
    val label = (countdown.value * 0.38f).sp(12f, 15f)
    val clock = (countdown.value * 0.36f).sp(12f, 14f)
    // The list sits inside its own vertical inset, so the five rows gather slightly toward the
    // middle of the card rather than touching the top and bottom padding, as iOS's do. The inset
    // is only what the height can spare: five rows at the smallest size need about 15 dp each,
    // and on a one-row cell that leaves nothing, so the rows keep the full height they had.
    val listHeight = size.height.value - padV.value * 2
    val listInset = ((listHeight - 5f * MIN_ROW_HEIGHT_DP) / 2f).coerceIn(0f, 8f).dp
    val innerHeight = listHeight - listInset.value * 2
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
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(vertical = listInset)) {
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
    val pad = 10.dp
    val em = render.remainingMinutes?.let { countdownEm(countdownText(it, render.languageTag)) } ?: COUNTDOWN_EM
    // The label and clock line take about 1.3 em of the countdown between them, so the number is
    // capped at 44% of the height, and by whatever the width allows for its own digits.
    val countdown = minOf((size.width.value - pad.value * 2) / em, size.height.value * 0.44f).sp(24f, 72f)
    val label = (countdown.value * 0.27f).sp(11f, 17f)
    val clock = (countdown.value * 0.34f).sp(12f, 22f)
    val gap = (countdown.value * 0.10f).dp
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
    val em = render.remainingMinutes?.let { countdownEm(countdownText(it, render.languageTag)) } ?: COUNTDOWN_EM
    val countdown = minOf(size.height.value * 0.50f, (size.width.value * 0.5f) / em).sp(18f, 36f)
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
    val em = render.remainingMinutes?.let { countdownEm(countdownText(it, render.languageTag)) } ?: COUNTDOWN_EM
    val countdown = minOf((size.width.value - 12f) / em, size.height.value * 0.46f).sp(14f, 30f)
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

/**
 * Rounded card, no border. The in-app cards carry a hairline, but on a home screen the launcher
 * clips the widget to its own corner radius and the stretched 1dp stroke image showed up as a
 * bright rim around the dark card; iOS never had one, and the card reads as a card without it.
 */
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
