package world.taqwa.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.i18n.PrayerNaming
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.today_jumuah
import world.taqwa.app.resources.today_set_by_rule

/** Gutter holding the pips; the rail runs down its centre. */
private val GutterWidth = 26.dp

/**
 * Air between the pip column and the name. The current prayer's halo is 24 dp inside a 26 dp
 * gutter, so without this the name sits about a dp from it and the two read as one blob. Added
 * here rather than by widening the gutter, which would carry the rail off the pips' centres.
 */
private val PipNameGap = 4.dp

/**
 * Half a row's height. Rows are a ~23dp text line plus 10dp of padding each side, so this puts
 * the rail's ends on the centres of the first and last pips rather than overshooting past them.
 */
private val RailInset = 22.dp

/**
 * Nothing here mirrors by hand. The gutter is the first child of a `Row` and the rail is inset
 * with `padding(start = …)`, both of which resolve against `LocalLayoutDirection` — so under
 * Arabic the pip column and the rail move to the right edge on their own. Only geometry drawn
 * into a `Canvas` with literal coordinates needs help, and this file draws none.
 *
 * [setByRule] are the prayers a high-latitude rule set, marked "Set by rule" after the name in the
 * Jumuʿah pill's style (spec §2.1); [subLines] are the quiet lines under a row, the other Asr and
 * where timetables differ, adding height only where they are shown.
 */
@Composable
fun PrayerTimeline(
    rows: List<TimelineRow>,
    // 24 dp was the page gutter when the timeline sat on the page; inside a card it is the
    // card's own inset, and the card already carries the gutter.
    horizontalPadding: Dp = 24.dp,
    setByRule: Set<Prayer> = emptySet(),
    subLines: Map<Prayer, List<String>> = emptyMap(),
    formatTime: (TimelineRow) -> String,
) {
    val colors = LocalTaqwaColors.current
    val arabicAlone = uiLanguage().arabicScript
    // The last row's lines under it, so the rail still ends on the last pip's centre.
    var lastRowExtra by remember { mutableIntStateOf(0) }
    val lastHasLines = subLines[rows.lastOrNull()?.prayer].orEmpty().isNotEmpty()
    val lastRowExtraDp = if (lastHasLines) with(LocalDensity.current) { lastRowExtra.toDp() } else 0.dp
    Box(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        // The rail is drawn behind the pips. matchParentSize takes its height from the column of
        // rows, so this stays correct however many prayers are visible.
        Box(
            Modifier
                .matchParentSize()
                .padding(start = GutterWidth / 2, top = RailInset, bottom = RailInset + lastRowExtraDp),
        ) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
        }
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                val lines = subLines[row.prayer].orEmpty()
                val last = index == rows.lastIndex
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        .alpha(if (row.status == PrayerStatus.PASSED) 0.44f else 1f),
                ) {
                    PrayerRow(row, arabicAlone, row.prayer in setByRule, formatTime)
                    if (lines.isNotEmpty()) {
                        Column(
                            Modifier
                                .padding(start = GutterWidth + PipNameGap)
                                .then(if (last) Modifier.onSizeChanged { lastRowExtra = it.height } else Modifier),
                        ) {
                            lines.forEach { line ->
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    line,
                                    style = TaqwaText.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One prayer's line: its pip, its names and pills, and its time. */
@Composable
private fun PrayerRow(
    row: TimelineRow,
    arabicAlone: Boolean,
    setByRule: Boolean,
    formatTime: (TimelineRow) -> String,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier.fillMaxWidth(),
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
        Spacer(Modifier.width(PipNameGap))
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Weighted so the time is measured first and always keeps its line. With a
            // large font on a narrow phone the name and what follows it stop fitting
            // beside the time — Friday's Dhuhr with its pill from 150 % on a 360 dp
            // screen, Maghrib from 200 % — and a plain Row squeezed the time into a
            // column of single digits. Here what follows the name wraps under it.
            FlowRow(
                Modifier.weight(1f, fill = false),
                // Spacing between items rather than a start padding on each, so a
                // wrapped item starts flush under the name.
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (arabicAlone) {
                    // The spec's naming rule: in an Arabic-script interface the
                    // interface's own name stands alone — الفجر in Arabic, فجر in
                    // Urdu — never doubled beside a second Arabic-script name. It
                    // takes the row's full weight and size here — it is the name,
                    // not a secondary label.
                    Text(
                        localizedPrayerName(row.prayer),
                        fontFamily = FontFamily.Default,
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
                } else {
                    Text(
                        localizedPrayerName(row.prayer),
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
                        PrayerNaming.arabicName(row.prayer),
                        // Arabic always uses the OS face, never Manrope — Manrope
                        // ships no Arabic glyphs, so forcing it produces tofu.
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        color = colors.textTertiary,
                    )
                }
                if (row.isJumuah) OutlinedPill(stringResource(Res.string.today_jumuah))
                if (setByRule) OutlinedPill(stringResource(Res.string.today_set_by_rule))
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

/**
 * The pill after a prayer's name: Friday's Dhuhr is Jumuʿah, and a time a high-latitude rule set
 * is "Set by rule" (spec §2.1), both in this one style. An outline, not a fill: the current prayer
 * is lit by its text colour and the pip's halo, never by a background, so the pill sits on the
 * card either way and reads the same beside an amber name as beside a plain one.
 */
@Composable
private fun OutlinedPill(text: String, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text,
        modifier = modifier
            .border(1.dp, colors.accent.copy(alpha = 0.4f), shape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        color = colors.accent,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        // The type scale's 11 sp label, as the name and time beside it take theirs, so the three
        // share a face; without the wide tracking it carries for all-caps labels, as this one is
        // set in mixed case.
        style = TaqwaText.sectionLabel.copy(letterSpacing = TextUnit.Unspecified),
    )
}
