package world.taqwa.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.compose.ui.unit.dp
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import world.taqwa.app.domain.WidgetBackground

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

class TaqwaSmallGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (content, colors) = readSnapshotAndPalette(context)
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(Color(colors.backgroundArgb)))
                    .padding(12.dp),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(
                    content?.nextPrayerDisplayName ?: "Taqwa",
                    style = TextStyle(color = ColorProvider(Color(colors.accentArgb)), fontWeight = FontWeight.Bold),
                )
                if (content != null) {
                    Text(
                        "${content.countdownMinutes / 60}:${(content.countdownMinutes % 60).toString().padStart(2, '0')}",
                        style = TextStyle(color = ColorProvider(Color(colors.textArgb))),
                    )
                    Text(content.nextClockTime, style = TextStyle(color = ColorProvider(Color(colors.textArgb))))
                }
            }
        }
    }
}

class TaqwaMediumGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (content, colors) = readSnapshotAndPalette(context)
        provideContent {
            Row(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(Color(colors.backgroundArgb)))
                    .padding(12.dp),
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        content?.nextPrayerDisplayName ?: "Taqwa",
                        style = TextStyle(color = ColorProvider(Color(colors.accentArgb)), fontWeight = FontWeight.Bold),
                    )
                    if (content != null) {
                        Text(content.nextClockTime, style = TextStyle(color = ColorProvider(Color(colors.textArgb))))
                    }
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    content?.rows?.forEach { row ->
                        Text(
                            "${row.displayName}  ${row.clockTime}",
                            style = TextStyle(
                                color = ColorProvider(Color(if (row.isCurrent) colors.accentArgb else colors.textArgb)),
                                fontWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}
