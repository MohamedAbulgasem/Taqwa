package world.taqwa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.feature.today.englishName
import world.taqwa.app.feature.today.formatClock
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.UmmAlQuraCalendar
import world.taqwa.app.prayer.HighLatitudeSelector
import world.taqwa.app.prayer.PrayerTimesEngine

fun methodDisplayName(id: CalculationMethodId): String = when (id) {
    CalculationMethodId.MUSLIM_WORLD_LEAGUE -> "Muslim World League"
    CalculationMethodId.ISNA -> "ISNA (North America)"
    CalculationMethodId.EGYPTIAN -> "Egyptian General Authority"
    CalculationMethodId.UMM_AL_QURA -> "Umm al-Qura (Makkah)"
    CalculationMethodId.KARACHI -> "University of Islamic Sciences, Karachi"
    CalculationMethodId.TEHRAN -> "Institute of Geophysics, Tehran"
    CalculationMethodId.DUBAI -> "Dubai"
    CalculationMethodId.KUWAIT -> "Kuwait"
    CalculationMethodId.QATAR -> "Qatar"
    CalculationMethodId.SINGAPORE -> "Singapore"
    CalculationMethodId.TURKEY -> "Diyanet (Türkiye)"
    CalculationMethodId.MOONSIGHTING_COMMITTEE -> "Moonsighting Committee"
}

fun highLatitudeDisplayName(preference: HighLatitudePreference): String = when (preference) {
    HighLatitudePreference.AUTOMATIC -> "Automatic"
    HighLatitudePreference.MIDDLE_OF_NIGHT -> "Middle of the night"
    HighLatitudePreference.SEVENTH_OF_NIGHT -> "One-seventh of the night"
    HighLatitudePreference.TWILIGHT_ANGLE -> "Twilight angle"
}

/**
 * Two segmented controls, three pushed pickers and a toggle. Every control writes through
 * [onChange] on touch: there is no save button anywhere in settings, so a change the user can see
 * is a change that has already been stored.
 */
@Composable
fun PrayerTimesSettingsScreen(
    settings: PrayerSettings,
    today: LocalDate,
    onChange: (PrayerSettings) -> Unit,
    onBack: () -> Unit,
    onOpenMethodPicker: () -> Unit,
    onOpenHighLatitudePicker: () -> Unit,
    onOpenManualAdjustments: () -> Unit,
) {
    SettingsScaffold("Prayer times", onBack) {
        SettingsCard {
            TaqwaRow(
                "Calculation method",
                value = methodDisplayName(settings.method),
                onClick = onOpenMethodPicker,
            )
            CardDivider()
            TaqwaRow(
                "High latitude rule",
                value = highLatitudeDisplayName(settings.highLatitude),
                onClick = onOpenHighLatitudePicker,
            )
            CardDivider()
            TaqwaRow(
                "Manual adjustments",
                value = adjustmentsSummary(settings.minuteAdjustments),
                onClick = onOpenManualAdjustments,
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("ASR MADHAB")
        SegmentedControl(
            options = listOf(AsrMadhab.STANDARD to "Standard", AsrMadhab.HANAFI to "Hanafi"),
            selected = settings.madhab,
            onSelect = { onChange(settings.copy(madhab = it)) },
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel("HIJRI DATE")
        SegmentedControl(
            options = listOf(-1 to "−1 day", 0 to "Umm al-Qura", 1 to "+1 day"),
            selected = settings.hijriOffsetDays.coerceIn(-1, 1),
            onSelect = { onChange(settings.copy(hijriOffsetDays = it)) },
        )
        Spacer(Modifier.height(10.dp))
        // The offset shifts the Gregorian date before conversion, exactly as Today does it —
        // never the Hijri day number, which can produce a day 0 or a day 31.
        SettingsNote(
            HijriFormatter.format(
                UmmAlQuraCalendar.fromGregorian(today.plus(settings.hijriOffsetDays, DateTimeUnit.DAY)),
            ),
        )

        Spacer(Modifier.height(28.dp))
        SettingsCard {
            TaqwaRow(
                "Show sunrise",
                trailing = {
                    TaqwaToggle(settings.showSunrise) { onChange(settings.copy(showSunrise = it)) }
                },
            )
        }
    }
}

private fun adjustmentsSummary(adjustments: Map<Prayer, Int>): String {
    val active = adjustments.count { it.value != 0 }
    return when (active) {
        0 -> "None"
        1 -> "1 prayer"
        else -> "$active prayers"
    }
}

/** Amber is the only colour, so the selected segment carries it and everything else is quiet. */
@Composable
private fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .padding(horizontal = SettingsGutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) colors.accent else colors.surface)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TaqwaText.caption,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isSelected) colors.background else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun MethodPickerScreen(
    current: CalculationMethodId,
    onPick: (CalculationMethodId) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold("Calculation method", onBack) {
        SettingsCard {
            CalculationMethodId.entries.forEachIndexed { i, id ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = methodDisplayName(id),
                    onClick = { onPick(id) },
                    trailing = { if (id == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(
            "Authorities differ on how far below the horizon the sun must be for Fajr and Isha. " +
                "Pick the one your local mosque follows.",
        )
    }
}

@Composable
fun HighLatitudePickerScreen(
    current: HighLatitudePreference,
    latitude: Double,
    onPick: (HighLatitudePreference) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold("High latitude rule", onBack) {
        SettingsCard {
            HighLatitudePreference.entries.forEachIndexed { i, preference ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = highLatitudeDisplayName(preference),
                    onClick = { onPick(preference) },
                    trailing = { if (preference == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // "Automatic" on its own tells the user nothing, so name the rule it resolves to here.
        val note = if (current == HighLatitudePreference.AUTOMATIC) {
            val resolved = HighLatitudeSelector.select(current, latitude)
            "At your latitude, automatic uses " +
                highLatitudeDisplayName(resolved).lowercase() + "."
        } else {
            "Above roughly 48 degrees the sun stops dropping far enough below the horizon for a " +
                "true Fajr and Isha, so a substitution rule takes over."
        }
        SettingsNote(note)
    }
}

/**
 * A stepper per prayer with the resulting time beside it. Without the time the control is a
 * number with no meaning — the point of an offset is the clock time it produces.
 */
@Composable
fun ManualAdjustmentsScreen(
    settings: PrayerSettings,
    location: GeoLocation?,
    engine: PrayerTimesEngine,
    today: LocalDate,
    onChange: (PrayerSettings) -> Unit,
    onBack: () -> Unit,
) {
    val zone = location?.let { TimeZone.of(it.timeZoneId) }
    val times = location?.let { engine.timesFor(it, today, settings) }

    SettingsScaffold("Manual adjustments", onBack) {
        SettingsCard {
            ObligatoryPrayers.forEachIndexed { i, prayer ->
                if (i > 0) CardDivider()
                val minutes = settings.minuteAdjustments[prayer] ?: 0
                AdjustmentRow(
                    label = englishName(prayer),
                    clock = if (times != null && zone != null) formatClock(times.time(prayer), zone) else null,
                    minutes = minutes,
                    onChange = { next ->
                        val updated = settings.minuteAdjustments.toMutableMap()
                        if (next == 0) updated.remove(prayer) else updated[prayer] = next
                        onChange(settings.copy(minuteAdjustments = updated))
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(
            "Shifts each time by up to an hour, on top of the calculation method. Use it only " +
                "when your mosque's timetable differs from the calculated time.",
        )
    }
}

private const val ADJUSTMENT_LIMIT = 59

@Composable
private fun AdjustmentRow(
    label: String,
    clock: String?,
    minutes: Int,
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
            color = if (minutes == 0) colors.textTertiary else colors.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        StepperButton("+", enabled = minutes < ADJUSTMENT_LIMIT) {
            onChange((minutes + 1).coerceAtMost(ADJUSTMENT_LIMIT))
        }
    }
}

private fun formatOffset(minutes: Int): String = when {
    minutes == 0 -> "0 min"
    minutes > 0 -> "+$minutes min"
    else -> "−${-minutes} min"
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
