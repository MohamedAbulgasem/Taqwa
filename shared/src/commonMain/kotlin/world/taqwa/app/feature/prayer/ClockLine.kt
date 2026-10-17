package world.taqwa.app.feature.prayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isolated
import world.taqwa.app.prayer.ClockChange
import world.taqwa.app.prayer.ClockIssue
import world.taqwa.app.prayer.ClockWarning
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_card_ok
import world.taqwa.app.resources.prayer_clock_kept_manual_early
import world.taqwa.app.resources.prayer_clock_kept_manual_late
import world.taqwa.app.resources.prayer_clock_kept_stale_early
import world.taqwa.app.resources.prayer_clock_kept_stale_late
import world.taqwa.app.resources.prayer_clock_line
import world.taqwa.app.resources.prayer_clock_manual_early
import world.taqwa.app.resources.prayer_clock_manual_late
import world.taqwa.app.resources.prayer_clock_region_alberta
import world.taqwa.app.resources.prayer_clock_region_british_columbia
import world.taqwa.app.resources.prayer_clock_region_chihuahua
import world.taqwa.app.resources.prayer_clock_region_jordan
import world.taqwa.app.resources.prayer_clock_region_kazakhstan
import world.taqwa.app.resources.prayer_clock_region_manitoba
import world.taqwa.app.resources.prayer_clock_region_morocco
import world.taqwa.app.resources.prayer_clock_region_paraguay
import world.taqwa.app.resources.prayer_clock_region_syria
import world.taqwa.app.resources.prayer_clock_region_yukon
import world.taqwa.app.resources.prayer_clock_stale_early
import world.taqwa.app.resources.prayer_clock_stale_late

/**
 * True when the phone's clock is set by hand, false when it follows the network, null where the
 * platform cannot tell (spec §3.9): Android reads `Settings.Global.AUTO_TIME`; iOS has no such
 * setting an app can read, so it compares zone offsets only.
 */
expect fun clockSetByHand(): Boolean?

/**
 * The clock line (spec §3.9, mockup §2): one tinted line above the ring, "Prayer times may be an
 * hour off ›", opening [ClockSheet]. Sideways it heads the right pane. It speaks of the times, not
 * the phone's clock: a phone kept on a zone of the true offset with a city picked from the list has
 * a right clock and times drawn an hour early (BUILD-LOG, 17 October).
 */
@Composable
internal fun ClockLine(onClick: () -> Unit, horizontalPadding: Dp) {
    val colors = LocalTaqwaColors.current
    val pointsForward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.ring.copy(alpha = 0.16f).compositeOver(colors.surface))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoGlyph(colors.accent, Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(Res.string.prayer_clock_line),
            modifier = Modifier.weight(1f),
            style = TaqwaText.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.size(12.dp)) { drawChevron(colors.textTertiary, pointsForward) }
    }
}

/**
 * The fix, in the direction this phone is off: which place changed its clocks and when, or has
 * kept one time all year since when, whether the times may be an hour early or late, and that
 * alerts are right only when the clock is automatic (spec §3.9).
 */
@Composable
internal fun ClockSheet(warning: ClockWarning, onDismiss: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val body = clockBodyRes(warning.change, warning.issue, warning.timesEarly)
    val region = clockRegionRes(warning.change.id)
    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(Res.string.prayer_clock_line),
                style = TaqwaText.screenTitle.copy(fontSize = 20.sp),
                color = colors.textPrimary,
            )
            Text(
                stringResource(
                    body,
                    isolated(stringResource(region)),
                    isolated(format.longDate(warning.change.from)),
                ),
                style = TaqwaText.caption.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = colors.textSecondary,
            )
            TaqwaPrimaryButton(
                stringResource(Res.string.prayer_card_ok),
                onClick = onDismiss,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** The place [ClockSheet] names for the [world.taqwa.app.prayer.ClockChanges.table] entry [changeId]. */
internal fun clockRegionRes(changeId: String): StringResource = when (changeId) {
    "morocco" -> Res.string.prayer_clock_region_morocco
    "british_columbia" -> Res.string.prayer_clock_region_british_columbia
    "alberta" -> Res.string.prayer_clock_region_alberta
    "manitoba" -> Res.string.prayer_clock_region_manitoba
    "yukon" -> Res.string.prayer_clock_region_yukon
    "syria" -> Res.string.prayer_clock_region_syria
    "jordan" -> Res.string.prayer_clock_region_jordan
    "chihuahua" -> Res.string.prayer_clock_region_chihuahua
    "paraguay" -> Res.string.prayer_clock_region_paraguay
    else -> Res.string.prayer_clock_region_kazakhstan
}

/**
 * The sentence [ClockSheet] shows for [change], true to what the place did (spec §3.9): a place
 * whose clocks went back once (Morocco, Kazakhstan; [ClockChange.shiftHours] −1) "changed its
 * clocks on" the date; one that stopped turning them back (British Columbia, Syria, Paraguay; +1)
 * "has kept one time all year since" it, as its date is no clock movement. Each in the direction
 * the times are off ([timesEarly]) and for the fix the phone needs ([issue]).
 */
internal fun clockBodyRes(change: ClockChange, issue: ClockIssue, timesEarly: Boolean): StringResource = when {
    change.shiftHours > 0 -> when (issue) {
        ClockIssue.STALE_ZONE_DATA ->
            if (timesEarly) Res.string.prayer_clock_kept_stale_early else Res.string.prayer_clock_kept_stale_late
        ClockIssue.SET_BY_HAND ->
            if (timesEarly) Res.string.prayer_clock_kept_manual_early else Res.string.prayer_clock_kept_manual_late
    }
    else -> when (issue) {
        ClockIssue.STALE_ZONE_DATA ->
            if (timesEarly) Res.string.prayer_clock_stale_early else Res.string.prayer_clock_stale_late
        ClockIssue.SET_BY_HAND ->
            if (timesEarly) Res.string.prayer_clock_manual_early else Res.string.prayer_clock_manual_late
    }
}
