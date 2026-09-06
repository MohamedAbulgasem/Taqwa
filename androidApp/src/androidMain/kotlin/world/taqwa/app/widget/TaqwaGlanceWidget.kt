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

private fun readSnapshotAndPalette(context: Context): Pair<WidgetContent?, WidgetPaletteColors> {
    val store = createWidgetKeyValueStore()
    val content = WidgetMirrorWriter.read(store)?.let(WidgetContentBuilder::build)
    val background = store.getString("widget_background")
        ?.let { raw -> WidgetBackground.entries.firstOrNull { it.name == raw } }
        ?: WidgetBackground.FOLLOW_THEME
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val systemIsDark = nightMode == Configuration.UI_MODE_NIGHT_YES
    return content to WidgetPalette.colorsFor(background, systemIsDark)
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

/** "Asr" -> "ASR IN". Glance text has no letter-spacing, so uppercase + Medium weight + a small
 * size stands in for the mockup's tracked-caps label. The literal "IN" is hardcoded English: the
 * content builder only exposes the already-localized prayer name, not a separate translatable
 * "next prayer in" phrase, and adding one is out of scope for the Glance-only layer this task
 * owns (see report). */
private fun nextPrayerLabel(content: WidgetContent) = "${content.nextPrayerDisplayName} in".uppercase()

private fun countdownText(content: WidgetContent): String {
    val hours = content.countdownMinutes / 60
    val minutes = content.countdownMinutes % 60
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
    colors: WidgetPaletteColors,
    labelSize: TextUnit,
    countdownSize: TextUnit,
    clockSize: TextUnit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (content != null) nextPrayerLabel(content) else "TAQWA",
            style = TextStyle(color = ColorProvider(colors.accent()), fontWeight = FontWeight.Medium, fontSize = labelSize),
        )
        if (content != null) {
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = countdownText(content),
                style = TextStyle(color = ColorProvider(colors.primaryText()), fontWeight = FontWeight.Normal, fontSize = countdownSize),
            )
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
        val (content, colors) = readSnapshotAndPalette(context)
        provideContent {
            WidgetCard(colors) {
                NextPrayerBlock(
                    content = content,
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
        val (content, colors) = readSnapshotAndPalette(context)
        provideContent {
            WidgetCard(colors) {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = GlanceModifier.padding(end = 12.dp)) {
                        NextPrayerBlock(
                            content = content,
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
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                Text(
                                    text = row.displayName,
                                    modifier = GlanceModifier.defaultWeight(),
                                    style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = 12.sp),
                                )
                                Text(
                                    text = row.clockTime,
                                    style = TextStyle(color = rowColor, fontWeight = rowWeight, fontSize = 12.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
