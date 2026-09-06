package world.taqwa.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
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
        // `provideGlance` runs on every re-render, including the half-hourly APPWIDGET_UPDATE that
        // hands back the very same mirror — so this reads the clock as it is *now*, not as it was
        // when the app last had the Today screen open.
        remainingMinutes = snapshot?.let {
            WidgetCountdown.remainingMinutesAt(it, System.currentTimeMillis() / 1_000L)
        },
        colors = WidgetPalette.colorsFor(background, systemIsDark),
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

private fun countdownText(remainingMinutes: Long): String {
    val hours = remainingMinutes / 60
    val minutes = remainingMinutes % 60
    return "$hours:${minutes.toString().padStart(2, '0')}"
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
    labelSize: TextUnit,
    countdownSize: TextUnit,
    clockSize: TextUnit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (content != null) nextPrayerLabel(content, remainingMinutes) else "TAQWA",
            style = TextStyle(color = ColorProvider(colors.accent()), fontWeight = FontWeight.Medium, fontSize = labelSize),
        )
        if (content != null) {
            // Omitted entirely when the mirror is too stale to place the next prayer, rather than
            // shown as a number that was true hours ago (I8).
            if (remainingMinutes != null) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = countdownText(remainingMinutes),
                    style = TextStyle(color = ColorProvider(colors.primaryText()), fontWeight = FontWeight.Normal, fontSize = countdownSize),
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = content.nextClockTime,
                style = TextStyle(color = ColorProvider(colors.secondaryText()), fontWeight = FontWeight.Normal, fontSize = clockSize),
            )
        }
    }
}

class TaqwaSmallGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val render = readWidgetRender(context)
        val colors = render.colors
        provideContent {
            WidgetCard(colors) {
                NextPrayerBlock(
                    content = render.content,
                    remainingMinutes = render.remainingMinutes,
                    colors = colors,
                    labelSize = 11.sp,
                    countdownSize = 34.sp,
                    clockSize = 13.sp,
                )
            }
        }
    }
}

class TaqwaMediumGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val render = readWidgetRender(context)
        val content = render.content
        val colors = render.colors
        provideContent {
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
                            labelSize = 10.sp,
                            countdownSize = 28.sp,
                            clockSize = 11.sp,
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
                                    style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = 12.sp),
                                    maxLines = 1,
                                )
                                Text(
                                    text = row.clockTime,
                                    style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = 12.sp),
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
