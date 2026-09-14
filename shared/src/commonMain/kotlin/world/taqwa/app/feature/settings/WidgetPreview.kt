package world.taqwa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.PreviewWallpaper
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PrayerNaming
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetDigits
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.WidgetPalette
import world.taqwa.app.widget.WidgetPaletteColors
import world.taqwa.app.widget.WidgetPrayerRow
import world.taqwa.app.widget.usesNativeDigits

/** The preview cards' height. Both cards share it; the wide one is what is left of the row. */
private val CardHeight = 100.dp

/**
 * Both home-screen widgets, small and wide, side by side over a neutral wallpaper swatch, drawn
 * with the same [WidgetPalette] colours the real widgets resolve. Onboarding shows it to say
 * "this exists"; Appearance shows it to say "this is what your background choice looks like".
 * The cards are Compose stand-ins, not the widgets themselves, so they only need to be faithful
 * in proportion, colour and content.
 */
@Composable
fun WidgetPreview(
    background: WidgetBackground,
    systemIsDark: Boolean,
    content: WidgetContent?,
    modifier: Modifier = Modifier,
) {
    val colors = WidgetPalette.colorsFor(background, systemIsDark)
    val shown = content ?: sampleContent()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PreviewWallpaper)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallCard(shown, colors, Modifier.size(CardHeight))
        WideCard(shown, colors, Modifier.weight(1f).height(CardHeight))
    }
}

@Composable
private fun WidgetSurface(colors: WidgetPaletteColors, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(colors.backgroundArgb).copy(alpha = colors.backgroundAlpha)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SmallCard(content: WidgetContent, colors: WidgetPaletteColors, modifier: Modifier) {
    WidgetSurface(colors, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
            Text(
                content.countdownLabel,
                style = TaqwaText.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = Color(colors.accentArgb),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                countdown(content.countdownMinutes),
                style = TaqwaText.countdown.copy(fontSize = 30.sp, fontWeight = FontWeight.Normal),
                color = Color(colors.textArgb),
                maxLines = 1,
            )
            Text(
                content.nextClockTime,
                style = TaqwaText.caption.copy(fontSize = 11.sp),
                color = Color(colors.textArgb).copy(alpha = 0.62f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WideCard(content: WidgetContent, colors: WidgetPaletteColors, modifier: Modifier) {
    WidgetSurface(colors, modifier) {
        Row(
            Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The countdown column is sized to its own text rather than to a share of the row:
            // "5:36" is one unbreakable word, so its minimum intrinsic width is exactly what it
            // needs, whatever face the UI language resolves — the system Arabic digits on iOS are
            // wider than Manrope's and a fixed share clipped them to "5:3". The list keeps the
            // rest, which is still the larger share and still shows its bilingual names whole.
            Column(Modifier.width(IntrinsicSize.Min)) {
                Text(
                    content.countdownLabel,
                    style = TaqwaText.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color(colors.accentArgb),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    countdown(content.countdownMinutes),
                    style = TaqwaText.countdown.copy(fontSize = 26.sp, fontWeight = FontWeight.Normal),
                    color = Color(colors.textArgb),
                    maxLines = 1,
                )
                Text(
                    content.nextClockTime,
                    style = TaqwaText.caption.copy(fontSize = 10.sp),
                    color = Color(colors.textArgb).copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                content.rows.forEach { row ->
                    val color = Color(if (row.isCurrent) colors.accentArgb else colors.textArgb)
                    val weight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.displayName,
                            style = TaqwaText.caption.copy(fontSize = 9.sp, fontWeight = weight),
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            row.clockTime,
                            style = TaqwaText.caption.copy(fontSize = 9.sp, fontWeight = weight),
                            color = color,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The preview's countdown, in the digits the app itself draws — asked of the platform, not derived
 * from the language tag, which is the rule the real widgets now follow through the mirror's
 * `arabicIndicDigits` (D2, S23 round). A preview showing "5:36" beside a widget showing "٥:٣٦"
 * would be a new disagreement in the place whose whole job is to show what the widget looks like.
 */
@Composable
private fun countdown(minutes: Long): String = WidgetDigits.localize(
    "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}",
    LocalPlatformFormat.current.usesNativeDigits(),
    LocalPlatformFormat.current.languageTag(),
)

private val SampleTimes = mapOf(
    Prayer.FAJR to "5:35",
    Prayer.DHUHR to "12:46",
    Prayer.ASR to "16:03",
    Prayer.MAGHRIB to "18:32",
    Prayer.ISHA to "19:50",
)

/**
 * A representative afternoon, in the UI's own language, for when the mirror has not been written
 * yet: onboarding runs before Today has ever been shown, and Appearance can be opened on a fresh
 * install before a location exists. Names go through the same [PrayerNaming] rule as the real
 * widget, so an Arabic UI previews Arabic-alone names and an English one the bilingual pair.
 */
@Composable
private fun sampleContent(): WidgetContent {
    val format = LocalPlatformFormat.current
    val languageTag = format.languageTag()
    val nativeDigits = format.usesNativeDigits()
    val rows = ObligatoryPrayers.map { prayer ->
        WidgetPrayerRow(
            prayer = prayer,
            displayName = PrayerNaming.display(prayer, languageTag, localizedPrayerName(prayer)),
            clockTime = WidgetDigits.localize(SampleTimes.getValue(prayer), nativeDigits, languageTag),
            isCurrent = prayer == Prayer.ASR,
        )
    }
    return WidgetContent(
        nextPrayerDisplayName = PrayerNaming.display(Prayer.MAGHRIB, languageTag, localizedPrayerName(Prayer.MAGHRIB)),
        countdownMinutes = 21,
        nextClockTime = WidgetDigits.localize(SampleTimes.getValue(Prayer.MAGHRIB), nativeDigits, languageTag),
        rows = rows,
        ringProgress = 0.86f,
        countdownLabel = WidgetMirrorWriter.countdownLabel(Prayer.MAGHRIB, languageTag),
    )
}
