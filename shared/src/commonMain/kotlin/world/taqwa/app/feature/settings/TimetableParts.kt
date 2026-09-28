package world.taqwa.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.RadioMark
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.feature.today.formatClock
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.madhab_hanafi
import world.taqwa.app.resources.madhab_standard
import world.taqwa.app.resources.timetable_checking
import world.taqwa.app.resources.timetable_earlier_asr_other
import world.taqwa.app.resources.timetable_earlier_start
import world.taqwa.app.resources.timetable_follow_it
import world.taqwa.app.resources.timetable_later_fast
import world.taqwa.app.resources.timetable_later_sunrise
import world.taqwa.app.resources.timetable_minutes
import world.taqwa.app.resources.timetable_of_authority
import world.taqwa.app.resources.timetable_of_cautious
import world.taqwa.app.resources.timetable_of_taqwa
import world.taqwa.app.resources.timetable_source_authority
import world.taqwa.app.resources.timetable_source_cautious
import world.taqwa.app.resources.timetable_source_taqwa
import world.taqwa.app.resources.timetable_times_today

/** A timetable choice being checked against Automatic: which, and how far through the year. */
data class ChoiceInProgress(val id: String, val progress: Float)

/** A choice the check found earlier than Automatic, waiting for its keep button or "Follow it". */
data class PendingWarning(val id: String, val nameKey: String, val finding: EarlierFinding)

/** The name a timetable goes by: an authority's short name, "Cautious times" or "Calculated by Taqwa". */
@Composable
internal fun timetableNameText(name: TimetableName): String = authorityShortName(name.nameKey)

/** "Diyanet’s time", "the cautious time" or "Taqwa’s time" (spec §2.2's {source}). */
@Composable
internal fun sourceTime(name: TimetableName): String = when (name.source) {
    SourceKind.AUTHORITY -> stringResource(Res.string.timetable_source_authority, authorityShortName(name.nameKey))
    SourceKind.CAUTIOUS -> stringResource(Res.string.timetable_source_cautious)
    SourceKind.TAQWA -> stringResource(Res.string.timetable_source_taqwa)
}

/** "London Unified’s", "the cautious times" or "Taqwa’s": whose start a warning compares with. */
@Composable
private fun sourceOf(name: TimetableName): String = when (name.source) {
    SourceKind.AUTHORITY -> stringResource(Res.string.timetable_of_authority, authorityShortName(name.nameKey))
    SourceKind.CAUTIOUS -> stringResource(Res.string.timetable_of_cautious)
    SourceKind.TAQWA -> stringResource(Res.string.timetable_of_taqwa)
}

/** "21 minutes", in the interface's digits and plural. */
@Composable
internal fun minutesLong(minutes: Int): String =
    pluralStringResource(Res.plurals.timetable_minutes, minutes, LocalPlatformFormat.current.localizedDigits(minutes))

/**
 * The earlier-than warning (spec §2.2, ruling R50): "Its Fajr begins 21 minutes before London
 * Unified’s on some days. Follow it only if your mosque does.", "Its Hanafi Asr begins…" for the
 * other school's Asr, or its sunrise or fast worded the same way.
 */
@Composable
internal fun earlierSentence(finding: EarlierFinding, automatic: TimetableName): String {
    val n = minutesLong(finding.minutes)
    val whose = sourceOf(automatic)
    val prayer = finding.time.prayer()
    val otherSchool = finding.otherSchool
    return when {
        finding.time == CheckedTime.ASR_OTHER && otherSchool != null -> stringResource(
            Res.string.timetable_earlier_asr_other,
            stringResource(if (otherSchool == AsrSchool.HANAFI) Res.string.madhab_hanafi else Res.string.madhab_standard),
            n,
            whose,
        )
        prayer != null -> stringResource(Res.string.timetable_earlier_start, localizedPrayerName(prayer), n, whose)
        finding.time == CheckedTime.SUNRISE -> stringResource(Res.string.timetable_later_sunrise, n, whose)
        else -> stringResource(Res.string.timetable_later_fast, n, whose)
    }
}

/** "Fajr 5:30, Maghrib 18:42, Isha 20:00 today", in the place's zone. */
@Composable
internal fun timesToday(times: OwnTimes, zone: TimeZone): String {
    val format = LocalPlatformFormat.current
    return stringResource(
        Res.string.timetable_times_today,
        formatClock(times.fajr, zone, format),
        formatClock(times.maghrib, zone, format),
        formatClock(times.isha, zone, format),
    )
}

/**
 * One timetable to choose: a radio at the start (the mockup's), its name and a line under it, and
 * while it is being checked, "Checking the next 12 months…" over a thin line that fills.
 */
@Composable
internal fun TimetableChoiceRow(
    label: String,
    detail: String?,
    selected: Boolean,
    checking: Float?,
    enabled: Boolean = true,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            // The radio filling is the acknowledgement; a ripple across the row would be a second one.
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioMark(selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary)
            if (checking != null) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.timetable_checking), style = TaqwaText.caption, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                CheckProgressLine(checking)
            } else if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = TaqwaText.caption, color = colors.textSecondary)
            }
        }
        if (chevron) {
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.size(14.dp)) { drawChevron(colors.textTertiary, pointsForward = forward) }
        }
    }
}

/** A row that opens another screen: optional leading glyph, label, a line under it, chevron. */
@Composable
internal fun ChevronRow(
    label: String,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary)
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = TaqwaText.caption, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.size(14.dp)) { drawChevron(colors.textTertiary, pointsForward = forward) }
    }
}

/**
 * The check's progress: 3 dp of accent over the hairline, filling from the reading edge, as the
 * whole-Quran download's line does.
 */
@Composable
internal fun CheckProgressLine(fraction: Float) {
    val colors = LocalTaqwaColors.current
    val width by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(250), label = "checkProgress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.hairline),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(width).height(3.dp).background(colors.accent))
    }
}

/**
 * A tinted block of amber: Match my mosque's result, and the warning under it (the mockup's
 * `.fit` and `.inl-warn`). [outlined] adds the accent hairline the result card carries.
 */
@Composable
internal fun AmberBlock(outlined: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTaqwaColors.current
    val shape = RoundedCornerShape(if (outlined) 16.dp else 14.dp)
    Column(
        Modifier
            .padding(horizontal = SettingsGutter)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = 0.12f))
            .then(if (outlined) Modifier.border(1.dp, colors.accent.copy(alpha = 0.35f), shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

/**
 * A question with a safe answer and a quiet one, in the app's confirm sheet (the recitation
 * delete's and the tasbeeh reset's shape): [title], [body], the safe choice as the one button and
 * the other as a text link under it.
 */
@Composable
internal fun ConfirmSheet(
    title: String?,
    body: String,
    safe: String,
    other: String,
    onSafe: () -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit,
) {
    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        ConfirmSheetContent(title, body, safe, other, onSafe, onOther)
    }
}

/** The sheet's content alone, so a preview can show it without a window to slide it over. */
@Composable
internal fun ConfirmSheetContent(
    title: String?,
    body: String,
    safe: String,
    other: String,
    onSafe: () -> Unit,
    onOther: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(title, style = TaqwaText.screenTitle.copy(fontSize = 20.sp), color = colors.textPrimary)
        }
        Text(body, style = TaqwaText.caption.copy(fontWeight = FontWeight.Normal), color = colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        TaqwaPrimaryButton(safe, onSafe)
        TaqwaTextLink(other, onOther)
    }
}

/**
 * The earlier-than warning for a timetable choice: the button keeps what is followed now and
 * says so ([keep]: "Keep Automatic", "Keep London Unified"), and "Follow it" is the link.
 */
@Composable
internal fun EarlierWarningSheet(
    warning: PendingWarning,
    automatic: TimetableName,
    keep: String,
    onKeep: () -> Unit,
    onFollow: () -> Unit,
) {
    ConfirmSheet(
        title = authorityShortName(warning.nameKey),
        body = earlierSentence(warning.finding, automatic),
        safe = keep,
        other = stringResource(Res.string.timetable_follow_it),
        onSafe = onKeep,
        onOther = onFollow,
        onDismiss = onKeep,
    )
}
