package world.taqwa.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TimelineRow

internal fun englishName(p: Prayer) = when (p) {
    Prayer.FAJR -> "Fajr"
    Prayer.SUNRISE -> "Sunrise"
    Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"
    Prayer.MAGHRIB -> "Maghrib"
    Prayer.ISHA -> "Isha"
}

internal fun arabicName(p: Prayer) = when (p) {
    Prayer.FAJR -> "الفجر"
    Prayer.SUNRISE -> "الشروق"
    Prayer.DHUHR -> "الظهر"
    Prayer.ASR -> "العصر"
    Prayer.MAGHRIB -> "المغرب"
    Prayer.ISHA -> "العشاء"
}

/** Gutter holding the pips; the rail runs down its centre. */
private val GutterWidth = 26.dp

/**
 * Half a row's height. Rows are a ~23dp text line plus 10dp of padding each side, so this puts
 * the rail's ends on the centres of the first and last pips rather than overshooting past them.
 */
private val RailInset = 22.dp

@Composable
fun PrayerTimeline(rows: List<TimelineRow>, formatTime: (TimelineRow) -> String) {
    val colors = LocalTaqwaColors.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        // The rail is drawn behind the pips. matchParentSize takes its height from the column of
        // rows, so this stays correct however many prayers are visible.
        Box(
            Modifier
                .matchParentSize()
                .padding(start = GutterWidth / 2, top = RailInset, bottom = RailInset),
        ) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
        }
        Column(Modifier.fillMaxWidth()) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        .alpha(if (row.status == PrayerStatus.PASSED) 0.44f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(GutterWidth), contentAlignment = Alignment.Center) {
                        when (row.status) {
                            // The halo is a translucent accent disc behind the larger pip, so the
                            // current prayer reads at a glance without adding a second colour.
                            PrayerStatus.CURRENT -> Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(14.dp).clip(CircleShape).background(colors.accent),
                                )
                            }
                            PrayerStatus.PASSED -> Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(colors.textTertiary),
                            )
                            PrayerStatus.UPCOMING -> Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(colors.background)
                                    .border(1.5.dp, colors.hairline, CircleShape),
                            )
                        }
                    }
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                englishName(row.prayer),
                                style = TaqwaText.rowLabel,
                                color = if (row.status == PrayerStatus.CURRENT) {
                                    colors.accent
                                } else {
                                    colors.textPrimary
                                },
                                fontWeight = if (row.status == PrayerStatus.CURRENT) {
                                    FontWeight.ExtraBold
                                } else {
                                    FontWeight.SemiBold
                                },
                            )
                            Text(
                                arabicName(row.prayer),
                                // Arabic always uses the OS face, never Manrope — Manrope ships
                                // no Arabic glyphs, so forcing it produces tofu.
                                fontFamily = FontFamily.Default,
                                fontSize = 14.sp,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Text(
                            formatTime(row),
                            style = TaqwaText.rowTime,
                            color = if (row.status == PrayerStatus.CURRENT) {
                                colors.accent
                            } else {
                                colors.textSecondary
                            },
                            fontWeight = if (row.status == PrayerStatus.CURRENT) {
                                FontWeight.ExtraBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
            }
        }
    }
}
