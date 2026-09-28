package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.feature.today.formatClock
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adjust_confirm_body
import world.taqwa.app.resources.adjust_confirm_body_maghrib
import world.taqwa.app.resources.adjust_confirm_title
import world.taqwa.app.resources.adjust_keep
import world.taqwa.app.resources.adjust_note
import world.taqwa.app.resources.adjust_paused_for
import world.taqwa.app.resources.adjust_strong_body
import world.taqwa.app.resources.adjust_strong_body_maghrib
import world.taqwa.app.resources.adjust_timetable_paused_note
import world.taqwa.app.resources.adjust_use
import world.taqwa.app.resources.minutes_count
import world.taqwa.app.resources.prayer_times_manual
import world.taqwa.app.resources.prayer_times_paused_value
import world.taqwa.app.resources.timetable_source_latest
import kotlin.time.Instant

/**
 * What Manual adjustments shows beside the steppers, from today's engine day (spec §2.2):
 *
 * - [clocks], [zone]: each prayer's time today with every adjustment the engine applied, floors
 *   included; null without a location.
 * - [paused]: earlier adjustments the engine did not apply (confirmed under another timetable,
 *   never confirmed, or the timetable itself paused); [timetablePaused] for the last, when the
 *   timetable [pausedTimetableNameKey] has to be confirmed first.
 * - [source], [effectiveId]: the timetable the day follows, for "Keep Diyanet’s time" and for the
 *   confirmation a "Use −2 min" writes.
 * - [margins]: per prayer, the timetable's own margin in minutes, where one is claimed.
 * - [confirmedNameKeys]: per prayer, the name of the timetable its earlier adjustment was set for.
 */
data class AdjustmentsState(
    val clocks: Map<Prayer, Instant>? = null,
    val zone: TimeZone? = null,
    val paused: Set<Prayer> = emptySet(),
    val timetablePaused: Boolean = false,
    val pausedTimetableNameKey: String? = null,
    val source: TimetableName? = null,
    val effectiveId: String? = null,
    val margins: Map<Prayer, Int> = emptyMap(),
    val confirmedNameKeys: Map<Prayer, String> = emptyMap(),
) {
    companion object {
        /** The screen's facts from today's engine [day] under [settings], drawn in [zone]. */
        fun of(day: EngineDay, settings: PrayerSettings, zone: TimeZone): AdjustmentsState {
            val d = day.day
            return AdjustmentsState(
                clocks = mapOf(Prayer.FAJR to d.fajr, Prayer.DHUHR to d.dhuhr, Prayer.ASR to d.asr, Prayer.MAGHRIB to d.maghrib, Prayer.ISHA to d.isha),
                zone = zone,
                paused = day.pausedAdjustments,
                timetablePaused = day.timetablePaused,
                pausedTimetableNameKey = Registry.byId(settings.timetable)?.shortNameKey,
                source = day.effective.timetableName(),
                effectiveId = day.effective.entry.id,
                margins = ObligatoryPrayers.mapNotNull { p -> marginMinutes(day.effective, p)?.let { p to it } }.toMap(),
                confirmedNameKeys = settings.confirmedAdjustments.mapNotNull { (p, id) -> Registry.byId(id)?.let { p to it.shortNameKey } }.toMap(),
            )
        }
    }
}

/** A question waiting on the sheet: [minutes] for [prayer], in its stronger words when [strong]. */
private data class PendingAsk(val prayer: Prayer, val minutes: Int, val strong: Boolean)

/**
 * A stepper per prayer with today's resulting time beside it (spec §2.2). The first step below the
 * timetable asks once, in one template for every prayer and place ("Show Maghrib before Diyanet’s
 * time?"), with the fast sentence for Maghrib only; past the timetable's own margin it asks again
 * in stronger words. An earlier adjustment confirmed under another timetable, or never, is paused:
 * it keeps its stepper, whose steps back towards the timetable never ask, and a line under it reads
 * "−2 min · paused (set for Diyanet) ›"; a tap on that line asks the same question again, and
 * keeping the timetable's time only closes it: the value stays stored with the timetable it was
 * set for. The floors (Maghrib after sunset, Dhuhr after the transit, Asr at the Standard shadow)
 * are the engine's; the time shown is always the one it will use.
 */
@Composable
fun ManualAdjustmentsScreen(
    adjustments: Map<Prayer, Int>,
    confirmed: Map<Prayer, String>,
    state: AdjustmentsState,
    onSet: (prayer: Prayer, minutes: Int, confirmUnder: String?) -> Unit,
    onOpenTimetable: () -> Unit,
    onBack: () -> Unit,
) {
    var ask by remember { mutableStateOf<PendingAsk?>(null) }
    val format = LocalPlatformFormat.current
    SettingsScaffold(stringResource(Res.string.prayer_times_manual), onBack) {
        SettingsCard {
            ObligatoryPrayers.forEachIndexed { i, prayer ->
                if (i > 0) CardDivider()
                val minutes = adjustments[prayer] ?: 0
                val clock = state.clocks?.get(prayer)?.let { t -> state.zone?.let { formatClock(t, it, format) } }
                val paused = prayer in state.paused && minutes < 0
                AdjustmentRow(
                    label = localizedPrayerName(prayer),
                    clock = clock,
                    minutes = minutes,
                    applied = !paused,
                    onChange = { next ->
                        val effective = state.effectiveId
                        val step = if (effective == null) {
                            AdjustStep.Apply(next)
                        } else {
                            adjustStep(minutes, next, confirmed[prayer], effective, state.margins[prayer])
                        }
                        when (step) {
                            is AdjustStep.Apply -> onSet(prayer, step.minutes, null)
                            is AdjustStep.Ask -> ask = PendingAsk(prayer, step.minutes, step.strong)
                        }
                    },
                )
                if (paused) {
                    val setFor = state.confirmedNameKeys[prayer]
                    PausedLine(
                        text = if (setFor != null) {
                            stringResource(Res.string.adjust_paused_for, formatOffset(minutes), authorityShortName(setFor))
                        } else {
                            stringResource(Res.string.prayer_times_paused_value, formatOffset(minutes))
                        },
                        onClick = {
                            if (state.timetablePaused || state.effectiveId == null) {
                                onOpenTimetable()
                            } else {
                                ask = PendingAsk(prayer, minutes, reconfirmStep(minutes, state.margins[prayer]).strong)
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (state.timetablePaused && state.paused.isNotEmpty()) {
            SettingsNote(
                stringResource(
                    Res.string.adjust_timetable_paused_note,
                    state.pausedTimetableNameKey?.let { authorityShortName(it) }.orEmpty(),
                ),
            )
            Spacer(Modifier.height(8.dp))
        }
        SettingsNote(stringResource(Res.string.adjust_note))
    }

    val pending = ask
    val source = state.source
    if (pending != null && source != null) {
        ConfirmSheet(
            title = stringResource(Res.string.adjust_confirm_title, localizedPrayerName(pending.prayer), sourceTime(source)),
            body = confirmBody(pending, source, state.margins[pending.prayer]),
            safe = stringResource(Res.string.adjust_keep, sourceTime(source)),
            other = stringResource(Res.string.adjust_use, formatOffset(pending.minutes)),
            // Keeping the timetable's time changes nothing: a paused value stays stored, and
            // applies again under the timetable it was set for (spec §2.2).
            onSafe = { ask = null },
            onOther = {
                ask = null
                onSet(pending.prayer, pending.minutes, state.effectiveId)
            },
            onDismiss = { ask = null },
        )
    }
}

/** The template's body: the plain one, or past the margin the stronger one; the fast sentence for Maghrib. */
@Composable
private fun confirmBody(ask: PendingAsk, source: TimetableName, margin: Int?): String {
    val maghrib = ask.prayer == Prayer.MAGHRIB
    if (!ask.strong || margin == null) {
        return stringResource(if (maghrib) Res.string.adjust_confirm_body_maghrib else Res.string.adjust_confirm_body)
    }
    // Where mosques differ Taqwa's time is the latest timetable's, so that is what the margin is measured from.
    val reference = if (source.source == SourceKind.CAUTIOUS) stringResource(Res.string.timetable_source_latest) else sourceTime(source)
    return stringResource(
        if (maghrib) Res.string.adjust_strong_body_maghrib else Res.string.adjust_strong_body,
        localizedPrayerName(ask.prayer),
        pluralStringResource(Res.plurals.minutes_count, margin, LocalPlatformFormat.current.localizedDigits(margin)),
        reference,
        formatOffset(ask.minutes),
    )
}

/** Under a paused row: "−2 min · paused (set for Diyanet) ›", which asks the question again. */
@Composable
private fun PausedLine(text: String, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TaqwaText.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.size(14.dp)) { drawChevron(colors.textTertiary, pointsForward = forward) }
    }
}

internal const val ADJUSTMENT_LIMIT = 59

@Composable
private fun AdjustmentRow(
    label: String,
    clock: String?,
    minutes: Int,
    applied: Boolean,
    onChange: (Int) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary)
            if (clock != null) {
                Text(clock, style = TaqwaText.caption, color = colors.textSecondary)
            }
        }
        StepperButton("−", enabled = minutes > -ADJUSTMENT_LIMIT) {
            onChange((minutes - 1).coerceAtLeast(-ADJUSTMENT_LIMIT))
        }
        Text(
            formatOffset(minutes),
            style = TaqwaText.rowTime,
            fontWeight = FontWeight.SemiBold,
            // A paused value is stored but not applied, so it is not drawn as one that is.
            color = if (minutes == 0 || !applied) colors.textTertiary else colors.accent,
            textAlign = TextAlign.Center,
            // Wide enough for the longest form the unit takes: Arabic's dual, "+٢ دقيقتان",
            // which wrapped to two lines in the 64.dp this column used to be.
            modifier = Modifier.width(78.dp),
        )
        StepperButton("+", enabled = minutes < ADJUSTMENT_LIMIT) {
            onChange((minutes + 1).coerceAtMost(ADJUSTMENT_LIMIT))
        }
    }
}

/**
 * The sign is a prefix on the numeral, not on the phrase, so under RTL the bidi algorithm keeps
 * it glued to the digits it belongs to rather than flipping it to the far side of the unit.
 */
@Composable
internal fun formatOffset(minutes: Int): String {
    val digits = LocalPlatformFormat.current.localizedDigits(
        if (minutes < 0) -minutes else minutes,
    )
    val signed = when {
        minutes == 0 -> digits
        minutes > 0 -> "+$digits"
        else -> "−$digits"
    }
    // The unit follows the magnitude's own plural rules — Arabic says "دقائق" for 3-10 — so the
    // sign never reaches the quantity, only the text.
    return pluralStringResource(
        Res.plurals.minutes_count,
        if (minutes < 0) -minutes else minutes,
        signed,
    )
}

@Composable
private fun StepperButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colors.background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}
