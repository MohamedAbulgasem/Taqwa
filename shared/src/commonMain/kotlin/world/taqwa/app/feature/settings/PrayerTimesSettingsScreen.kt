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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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
import world.taqwa.app.feature.today.formatClock
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.highLatitudeDisplayName
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.i18n.madhabDisplayName
import world.taqwa.app.i18n.methodDisplayName
import world.taqwa.app.prayer.HighLatitudeSelector
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.minutes_count
import world.taqwa.app.resources.adjustments_many
import world.taqwa.app.resources.adjustments_none
import world.taqwa.app.resources.adjustments_one
import world.taqwa.app.resources.high_lat_auto_note
import world.taqwa.app.resources.hijri_method_note
import world.taqwa.app.resources.high_lat_picker_note
import world.taqwa.app.resources.hijri_day_after
import world.taqwa.app.resources.hijri_day_before
import world.taqwa.app.resources.hijri_tabular
import world.taqwa.app.resources.manual_note
import world.taqwa.app.resources.method_picker_note
import world.taqwa.app.resources.method_tehran_maghrib_note
import world.taqwa.app.resources.prayer_times_high_latitude
import world.taqwa.app.resources.prayer_times_hijri_label
import world.taqwa.app.resources.prayer_times_madhab_label
import world.taqwa.app.resources.prayer_times_manual
import world.taqwa.app.resources.prayer_times_method
import world.taqwa.app.resources.prayer_times_show_sunrise
import world.taqwa.app.resources.settings_prayer_times

/** Roughly where the sun stops dropping far enough below the horizon for a true Fajr and Isha. */
private const val HIGH_LATITUDE_DEGREES = 48

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
    SettingsScaffold(stringResource(Res.string.settings_prayer_times), onBack) {
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.prayer_times_method),
                value = methodDisplayName(settings.method),
                onClick = onOpenMethodPicker,
            )
            CardDivider()
            TaqwaRow(
                stringResource(Res.string.prayer_times_high_latitude),
                value = highLatitudeDisplayName(settings.highLatitude),
                onClick = onOpenHighLatitudePicker,
            )
            CardDivider()
            TaqwaRow(
                stringResource(Res.string.prayer_times_manual),
                value = adjustmentsSummary(settings.minuteAdjustments),
                onClick = onOpenManualAdjustments,
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.prayer_times_madhab_label))
        SegmentedControl(
            options = AsrMadhab.entries.map { it to madhabDisplayName(it) },
            selected = settings.madhab,
            onSelect = { onChange(settings.copy(madhab = it)) },
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.prayer_times_hijri_label))
        // "A day earlier" rather than "−1 day": a signed numeral inside a segmented control is
        // the one place a minus sign would have to be mirrored by hand under RTL, and the words
        // say the same thing without the problem.
        SegmentedControl(
            options = listOf(
                -1 to stringResource(Res.string.hijri_day_before),
                0 to stringResource(Res.string.hijri_tabular),
                1 to stringResource(Res.string.hijri_day_after),
            ),
            selected = settings.hijriOffsetDays.coerceIn(-1, 1),
            onSelect = { onChange(settings.copy(hijriOffsetDays = it)) },
        )
        Spacer(Modifier.height(10.dp))
        // The offset shifts the Gregorian date before conversion, exactly as Today does it —
        // never the Hijri day number, which can produce a day 0 or a day 31.
        SettingsNote(
            HijriFormatter.format(
                TabularHijriCalendar.fromGregorian(today.plus(settings.hijriOffsetDays, DateTimeUnit.DAY)),
                LocalPlatformFormat.current,
            ),
        )
        Spacer(Modifier.height(6.dp))
        // Says what the date is made of. A reader whose mosque is a day off and who finds no
        // explanation concludes the app is wrong, not that it uses a different convention.
        SettingsNote(stringResource(Res.string.hijri_method_note))

        Spacer(Modifier.height(28.dp))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.prayer_times_show_sunrise),
                trailing = {
                    TaqwaToggle(settings.showSunrise) { onChange(settings.copy(showSunrise = it)) }
                },
            )
        }
    }
}

@Composable
private fun adjustmentsSummary(adjustments: Map<Prayer, Int>): String {
    val active = adjustments.count { it.value != 0 }
    return when (active) {
        0 -> stringResource(Res.string.adjustments_none)
        1 -> stringResource(Res.string.adjustments_one)
        else -> stringResource(
            Res.string.adjustments_many,
            LocalPlatformFormat.current.localizedDigits(active),
        )
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
    SettingsScaffold(stringResource(Res.string.prayer_times_method), onBack) {
        SettingsCard {
            CalculationMethodId.entries.forEachIndexed { i, id ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = methodDisplayName(id),
                    // Tehran is reconstructed from its Fajr/Isha angles on top of adhan2's OTHER
                    // method, which carries no Maghrib angle, so its 4.5° Maghrib is not applied.
                    // A Shia user choosing this method gets a sunset-based Maghrib and deserves
                    // to be told here rather than only in a code comment.
                    subtitle = if (id == CalculationMethodId.TEHRAN) {
                        stringResource(Res.string.method_tehran_maghrib_note)
                    } else {
                        null
                    },
                    onClick = { onPick(id) },
                    selectable = true,
                    trailing = { if (id == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(stringResource(Res.string.method_picker_note))
    }
}

@Composable
fun HighLatitudePickerScreen(
    current: HighLatitudePreference,
    latitude: Double,
    onPick: (HighLatitudePreference) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(Res.string.prayer_times_high_latitude), onBack) {
        SettingsCard {
            HighLatitudePreference.entries.forEachIndexed { i, preference ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = highLatitudeDisplayName(preference),
                    onClick = { onPick(preference) },
                    selectable = true,
                    trailing = { if (preference == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // "Automatic" on its own tells the user nothing, so name the rule it resolves to here.
        val note = if (current == HighLatitudePreference.AUTOMATIC) {
            val resolved = HighLatitudeSelector.select(current, latitude)
            // `lowercase()` is a no-op on Arabic, which has no letter case — the sentence reads
            // correctly there without a second template.
            stringResource(Res.string.high_lat_auto_note, highLatitudeDisplayName(resolved).lowercase())
        } else {
            stringResource(
                Res.string.high_lat_picker_note,
                LocalPlatformFormat.current.localizedDigits(HIGH_LATITUDE_DEGREES),
            )
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

    val format = LocalPlatformFormat.current
    SettingsScaffold(stringResource(Res.string.prayer_times_manual), onBack) {
        SettingsCard {
            ObligatoryPrayers.forEachIndexed { i, prayer ->
                if (i > 0) CardDivider()
                val minutes = settings.minuteAdjustments[prayer] ?: 0
                AdjustmentRow(
                    label = localizedPrayerName(prayer),
                    clock = if (times != null && zone != null) {
                        formatClock(times.time(prayer), zone, format)
                    } else {
                        null
                    },
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
        SettingsNote(stringResource(Res.string.manual_note))
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
private fun formatOffset(minutes: Int): String {
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
