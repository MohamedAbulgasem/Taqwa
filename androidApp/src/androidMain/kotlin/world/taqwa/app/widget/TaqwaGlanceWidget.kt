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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import world.taqwa.app.R
import world.taqwa.app.domain.WidgetBackground

/**
 * Design reference: ".superpowers/brainstorm/11962-1788645017/content/slice1-screens.html",
 * section "5 · Widgets". Both sizes share one visual language: a tiny uppercase tracked label in
 * accent ("ASR IN"), a large light-weight countdown as the visual anchor, and the clock time in a
 * secondary tone underneath — nothing else competes for attention.
 *
 * [WidgetPaletteColors] only carries background/text/accent, not a dedicated secondary or
 * hairline colour, so both are approximated here by lowering the alpha of the primary text colour
 * (a standard on-surface-at-N% technique) rather than editing the shared palette model, which is
 * out of scope for this pass (see the widget polish task notes).
 */

/**
 * Everything one draw of the widget needs, resolved at the moment it draws.
 *
 * [remainingMinutes] is the point of the class. It is derived here, from the device clock, and is
 * `null` whenever no honest countdown exists — see [WidgetCountdown].
 */
private class WidgetRender(
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
        // `provideGlance` runs on every re-render — the half-hourly APPWIDGET_UPDATE, the rolling
        // five-minute window alarm, the prayer-boundary alarm and the unlock receiver all hand
        // back the very same mirror — so this reads the clock as it is *now*, not as it was when
        // the app last had the Today screen open. See `WidgetRefreshScheduler` for the cadence
        // that makes "now" mean something.
        remainingMinutes = snapshot?.let {
            WidgetCountdown.remainingMinutesAt(it, System.currentTimeMillis() / 1_000L)
        },
        colors = WidgetPalette.colorsFor(background, systemIsDark),
        // Defaults to Western digits (matching `WidgetDigits.localize`'s own fallback) when there
        // is no snapshot yet — the placeholder card shows no numbers anyway.
        languageTag = snapshot?.languageTag.orEmpty(),
    )
}

// -- Palette-derived colour roles -------------------------------------------------------------
// The card background already honours WidgetPaletteColors.backgroundAlpha, which is how the
// translucent/frosted background option (wired by another agent) reaches the widget: no TODO
// hook needed, it's a plain alpha multiply below.

private fun WidgetPaletteColors.cardBackground() = Color(backgroundArgb).copy(alpha = backgroundAlpha)
private fun WidgetPaletteColors.primaryText() = Color(textArgb)
private fun WidgetPaletteColors.secondaryText() = Color(textArgb).copy(alpha = 0.62f)
private fun WidgetPaletteColors.hairline() = Color(textArgb).copy(alpha = 0.14f)
private fun WidgetPaletteColors.accent() = Color(accentArgb)

// -- Responsive sizing ------------------------------------------------------------------------
// A launcher does not always give a widget the cell it asked for. This one declares 2x2 and was
// handed 2x3 on the owner's phone, which left a fixed-size card marooned in a tall well of empty
// space — "text in the middle and most of the rest is empty". Glance builds one layout per
// declared size and picks the largest that fits, so declaring the three shapes these widgets are
// actually placed at is what lets the type grow into the cell instead of floating in it.
// Sizes follow the launcher's 70n-30dp cell formula.

private val SMALL_2X2 = DpSize(110.dp, 110.dp)
private val TALL_2X3 = DpSize(110.dp, 180.dp)
private val MEDIUM_4X2 = DpSize(250.dp, 110.dp)

private val TAQWA_WIDGET_SIZES = setOf(SMALL_2X2, TALL_2X3, MEDIUM_4X2)

/**
 * The mockup's proportions held constant while the absolute sizes move: the label stays the
 * smallest thing on the card, the countdown stays the anchor at roughly three times it, and the
 * clock time sits between the two.
 */
private class WidgetTypeScale(
    val label: TextUnit,
    val countdown: TextUnit,
    val clock: TextUnit,
    val labelGap: Dp,
    val clockGap: Dp,
    val cardPadding: Dp,
)

/**
 * [LocalSize] under [SizeMode.Responsive] reports the *declared* size Glance chose rather than the
 * raw cell, so this is a three-way match on known shapes rather than arithmetic on an arbitrary
 * number.
 *
 * The countdown is capped by width, never by height. It reads "12:34" when the next prayer is most
 * of a day away — about 2.5 em of digits and colon — so at 38 sp it needs ~95 dp inside a 110 dp
 * card. Glance text clips rather than shrinking, so there is no room above that in a two-cell-wide
 * card however tall it gets; the extra height goes into the label, the clock time and the gaps.
 */
private fun typeScaleFor(size: DpSize): WidgetTypeScale = when (size) {
    // Two cells wide, three tall: take all the width allows and open the gaps up, so the card
    // reads as a deliberately airy layout rather than a small label adrift in one.
    TALL_2X3 -> WidgetTypeScale(
        label = 13.sp, countdown = 38.sp, clock = 17.sp,
        labelGap = 10.dp, clockGap = 8.dp, cardPadding = 12.dp,
    )
    // Four cells wide, two tall — the small widget stretched sideways. Width is no longer the
    // constraint; height is.
    MEDIUM_4X2 -> WidgetTypeScale(
        label = 12.sp, countdown = 40.sp, clock = 15.sp,
        labelGap = 5.dp, clockGap = 3.dp, cardPadding = 8.dp,
    )
    // The design's home ground, unchanged.
    else -> WidgetTypeScale(
        label = 11.sp, countdown = 34.sp, clock = 13.sp,
        labelGap = 3.dp, clockGap = 2.dp, cardPadding = 6.dp,
    )
}

/**
 * The medium widget's countdown block lives in roughly a third of the card, so it is sized against
 * that column rather than against the whole cell — [typeScaleFor]'s wide scale would clip a
 * two-digit-hour countdown here. It still grows when the card is given extra height.
 */
private fun mediumBlockScaleFor(size: DpSize): WidgetTypeScale =
    if (size.height >= TALL_2X3.height) {
        WidgetTypeScale(
            label = 12.sp, countdown = 32.sp, clock = 13.sp,
            labelGap = 6.dp, clockGap = 4.dp, cardPadding = 4.dp,
        )
    } else {
        WidgetTypeScale(
            label = 10.sp, countdown = 28.sp, clock = 11.sp,
            labelGap = 3.dp, clockGap = 2.dp, cardPadding = 4.dp,
        )
    }

/** "Dhuhr in" -> "DHUHR IN" / "متبقٍ على الظهر" -> "متبقٍ على الظهر". Glance text has no
 * letter-spacing, so uppercase + Medium weight + a small size stands in for the mockup's
 * tracked-caps label. [WidgetContent.countdownLabel] is already the fully-localised "next prayer
 * in" phrase (written by `WidgetMirrorWriter` in `shared`), so uppercasing it here is purely a
 * Latin-script stylistic touch — `String.uppercase()` is a no-op on Arabic text.
 *
 * When no countdown can be derived the "in" phrase would be dangling ("ASR IN" with nothing after
 * it), so the plain prayer name is used instead. The card then reads "ASR / 15:47": less, but
 * nothing in it is untrue. */
private fun nextPrayerLabel(content: WidgetContent, remainingMinutes: Long?) =
    if (remainingMinutes == null) content.nextPrayerDisplayName.uppercase() else content.countdownLabel.uppercase()

/**
 * Unlike [WidgetContent.nextClockTime] and [WidgetPrayerRow.clockTime], which `PlatformFormat`
 * already pre-formats with the locale's own digits before the snapshot is written, this string is
 * built here by raw interpolation — so it needs its own pass through [WidgetDigits.localize], or
 * an `ar-EG` user sees a Western countdown sitting directly above an Arabic-Indic clock time in
 * the same card (I9).
 */
private fun countdownText(remainingMinutes: Long, languageTag: String): String {
    val hours = remainingMinutes / 60
    val minutes = remainingMinutes % 60
    return WidgetDigits.localize("$hours:${minutes.toString().padStart(2, '0')}", languageTag)
}

/** Rounded card (≈19dp) with a 1dp hairline border, matching the mockup's `.wg` treatment. The
 * border is drawn as a tinted image because Glance 1.1.1 has no border/stroke modifier. */
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

@Composable
private fun NextPrayerBlock(
    content: WidgetContent?,
    remainingMinutes: Long?,
    colors: WidgetPaletteColors,
    languageTag: String,
    scale: WidgetTypeScale,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (content != null) nextPrayerLabel(content, remainingMinutes) else "TAQWA",
            style = TextStyle(color = ColorProvider(colors.accent()), fontWeight = FontWeight.Medium, fontSize = scale.label),
            maxLines = 1,
        )
        if (content != null) {
            // Omitted entirely when the mirror is too stale to place the next prayer, rather than
            // shown as a number that was true hours ago (I8).
            if (remainingMinutes != null) {
                Spacer(GlanceModifier.height(scale.labelGap))
                Text(
                    text = countdownText(remainingMinutes, languageTag),
                    style = TextStyle(color = ColorProvider(colors.primaryText()), fontWeight = FontWeight.Normal, fontSize = scale.countdown),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(scale.clockGap))
            Text(
                text = content.nextClockTime,
                style = TextStyle(color = ColorProvider(colors.secondaryText()), fontWeight = FontWeight.Normal, fontSize = scale.clock),
                maxLines = 1,
            )
        }
    }
}

class TaqwaSmallGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(TAQWA_WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val render = readWidgetRender(context)
        val colors = render.colors
        provideContent {
            val scale = typeScaleFor(LocalSize.current)
            WidgetCard(colors) {
                Box(modifier = GlanceModifier.padding(scale.cardPadding)) {
                    NextPrayerBlock(
                        content = render.content,
                        remainingMinutes = render.remainingMinutes,
                        colors = colors,
                        languageTag = render.languageTag,
                        scale = scale,
                    )
                }
            }
        }
    }
}

class TaqwaMediumGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(TAQWA_WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val render = readWidgetRender(context)
        val content = render.content
        val colors = render.colors
        provideContent {
            val size = LocalSize.current
            val scale = mediumBlockScaleFor(size)
            // A taller card can afford a bigger prayer list; the five rows are what extra height
            // is for here, the same way the countdown is what it is for on the small widget.
            val rowSize = if (size.height >= TALL_2X3.height) 14.sp else 12.sp
            WidgetCard(colors) {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = GlanceModifier.padding(end = 12.dp)) {
                        NextPrayerBlock(
                            content = content,
                            remainingMinutes = render.remainingMinutes,
                            colors = colors,
                            languageTag = render.languageTag,
                            scale = scale,
                        )
                    }
                    Box(
                        modifier = GlanceModifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(ColorProvider(colors.hairline())),
                    ) {}
                    Column(
                        modifier = GlanceModifier.defaultWeight().padding(start = 12.dp),
                    ) {
                        content?.rows?.forEachIndexed { index, row ->
                            if (index > 0) {
                                Spacer(GlanceModifier.height(3.dp))
                            }
                            val rowColor = if (row.isCurrent) ColorProvider(colors.accent()) else ColorProvider(colors.secondaryText())
                            val rowWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal
                            // `maxLines = 1` on both, and it is the name that needs it: in a
                            // non-Arabic locale `PrayerNaming` produces "Maghrib · المغرب", which
                            // at 12 sp in a weighted column inside a 4x2 cell has no room to wrap.
                            // Without this the row grew to two lines and five of them stopped
                            // fitting the widget's height, pushing the last prayers out of view.
                            // Clipping the tail of a name the reader can still recognise costs
                            // less than losing whole rows — and the time, which is the row's
                            // actual payload, is unweighted and so is never the part that gives.
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                Text(
                                    text = row.displayName,
                                    modifier = GlanceModifier.defaultWeight(),
                                    style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = rowSize),
                                    maxLines = 1,
                                )
                                Text(
                                    text = row.clockTime,
                                    style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = rowSize),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
