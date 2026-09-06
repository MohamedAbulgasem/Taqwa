package world.taqwa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.PreviewWallpaper
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetPalette
import world.taqwa.app.widget.WidgetPrayerRow

/**
 * The medium widget, rendered with the same [WidgetPalette] colours `TaqwaMediumGlanceWidget`
 * (Android) and the iOS widget extension resolve, over a neutral wallpaper swatch. This is the
 * spec's live preview: it replaces the explanatory footnote entirely, and updates the moment
 * [background] changes.
 */
@Composable
fun WidgetPreview(
    background: WidgetBackground,
    systemIsDark: Boolean,
    content: WidgetContent?,
    modifier: Modifier = Modifier,
) {
    val colors = WidgetPalette.colorsFor(background, systemIsDark)
    Column(
        modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PreviewWallpaper)
            .padding(16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(colors.backgroundArgb).copy(alpha = colors.backgroundAlpha))
                .padding(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(content?.nextPrayerDisplayName ?: "Asr", color = Color(colors.accentArgb))
                Text(content?.nextClockTime ?: "15:47", color = Color(colors.textArgb))
            }
            Column(Modifier.weight(1f)) {
                (content?.rows ?: sampleRows()).forEach { row ->
                    Text(
                        "${row.displayName}  ${row.clockTime}",
                        color = Color(if (row.isCurrent) colors.accentArgb else colors.textArgb),
                    )
                }
            }
        }
    }
}

/** Shown before the mirror has ever been written — e.g. a fresh install that has not opened
 * Today yet — so the preview is never blank. */
private fun sampleRows() = listOf(
    WidgetPrayerRow(Prayer.FAJR, "Fajr", "05:12", false),
    WidgetPrayerRow(Prayer.DHUHR, "Dhuhr", "12:34", false),
    WidgetPrayerRow(Prayer.ASR, "Asr", "15:47", true),
    WidgetPrayerRow(Prayer.MAGHRIB, "Maghrib", "18:20", false),
    WidgetPrayerRow(Prayer.ISHA, "Isha", "19:50", false),
)
