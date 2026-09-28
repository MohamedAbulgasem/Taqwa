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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.highLatitudeDisplayName
import world.taqwa.app.i18n.schoolDisplayName
import world.taqwa.app.i18n.timetableDisplayName
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adjustments_many
import world.taqwa.app.resources.adjustments_none
import world.taqwa.app.resources.adjustments_one
import world.taqwa.app.resources.asr_note_automatic_authority
import world.taqwa.app.resources.asr_note_automatic_majority
import world.taqwa.app.resources.asr_note_automatic_unknown
import world.taqwa.app.resources.asr_note_automatic_unknown_here
import world.taqwa.app.resources.asr_note_hanafi
import world.taqwa.app.resources.asr_note_standard
import world.taqwa.app.resources.asr_shadow_hanafi
import world.taqwa.app.resources.asr_shadow_standard
import world.taqwa.app.resources.hijri_day_after
import world.taqwa.app.resources.hijri_day_before
import world.taqwa.app.resources.hijri_method_note
import world.taqwa.app.resources.hijri_tabular
import world.taqwa.app.resources.high_lat_picker_note
import world.taqwa.app.resources.prayer_times_about_row
import world.taqwa.app.resources.prayer_times_asr_label
import world.taqwa.app.resources.prayer_times_hijri_label
import world.taqwa.app.resources.prayer_times_high_latitude
import world.taqwa.app.resources.prayer_times_manual
import world.taqwa.app.resources.prayer_times_paused_value
import world.taqwa.app.resources.prayer_times_show_sunrise
import world.taqwa.app.resources.prayer_times_timetable
import world.taqwa.app.resources.saudi_fajr_later
import world.taqwa.app.resources.saudi_fajr_later_detail
import world.taqwa.app.resources.settings_prayer_times
import world.taqwa.app.resources.show_both_asr
import world.taqwa.app.resources.show_where_differ
import world.taqwa.app.resources.show_where_differ_detail
import world.taqwa.app.resources.show_where_differ_detail_here
import world.taqwa.app.resources.timetable_automatic_named
import world.taqwa.app.resources.timetable_checking
import world.taqwa.app.resources.timetable_keep_current
import world.taqwa.app.resources.timetable_out_of_scope
import world.taqwa.app.resources.timetable_paused_note

/** Roughly where the sun stops dropping far enough below the horizon for a true Fajr and Isha. */
private const val HIGH_LATITUDE_DEGREES = 48

/** The Asr control's choices, as stored: the place's own school, then the two. */
private val SCHOOLS = listOf("automatic", "standard", "hanafi")

/**
 * Settings › **Prayer times** (spec §2.2, mockup section 3). Every control writes its own field on
 * touch: there is no save button anywhere in settings, so a change the user can see is a change
 * that has already been stored.
 *
 * - The Timetable card: the timetable ("Automatic · London Unified"), Saudi Arabia's "Pray Fajr 5
 *   minutes later" while Umm al-Qura applies there, the high-latitude rule for an Other method
 *   only, and Manual adjustments. Under it, one line when the stored timetable does not apply here
 *   (ruling R50) or waits to be confirmed (ruling R52).
 * - Asr: Automatic, Standard, Hanafi, with a note naming the school by its shadow; the switches
 *   for both Asr times and, where mosques differ, where their timetables differ.
 * - Hijri date and Show sunrise, unchanged, and About these times at the foot.
 *
 * [status] is null without a location: the rows then name the stored choice alone.
 */
@Composable
fun PrayerTimesSettingsScreen(
    settings: PrayerSettings,
    status: TimetableStatus?,
    adjustmentsPaused: Boolean,
    placeName: String?,
    today: LocalDate,
    onSchool: (String) -> Unit,
    onHijriOffset: (Int) -> Unit,
    onShowSunrise: (Boolean) -> Unit,
    onShowBothAsr: (Boolean) -> Unit,
    onShowWhereDiffer: (Boolean) -> Unit,
    onSaudiFajrLater: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenHighLatitudePicker: () -> Unit,
    onOpenManualAdjustments: () -> Unit,
    onOpenAboutTimes: () -> Unit,
) {
    SettingsScaffold(stringResource(Res.string.settings_prayer_times), onBack) {
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.prayer_times_timetable),
                value = timetableValue(settings, status),
                onClick = onOpenTimetable,
            )
            if (status?.saudiFajr == true) {
                CardDivider()
                TaqwaRow(
                    stringResource(Res.string.saudi_fajr_later),
                    subtitle = stringResource(Res.string.saudi_fajr_later_detail),
                    trailing = { TaqwaToggle(settings.saudiFajrLater, onSaudiFajrLater) },
                )
            }
            if (status?.highLatitude ?: (settings.timetable.startsWith("other."))) {
                CardDivider()
                TaqwaRow(
                    stringResource(Res.string.prayer_times_high_latitude),
                    value = highLatitudeDisplayName(settings.legacyHighLatitude ?: HighLatitudePreference.AUTOMATIC),
                    onClick = onOpenHighLatitudePicker,
                )
            }
            CardDivider()
            TaqwaRow(
                stringResource(Res.string.prayer_times_manual),
                value = adjustmentsSummary(settings.minuteAdjustments, adjustmentsPaused),
                onClick = onOpenManualAdjustments,
            )
        }
        if (status != null && (status.outOfScope || status.paused)) {
            Spacer(Modifier.height(10.dp))
            SettingsNote(
                stringResource(
                    if (status.outOfScope) Res.string.timetable_out_of_scope else Res.string.timetable_paused_note,
                    chosenName(status),
                ),
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.prayer_times_asr_label))
        SegmentedControl(
            options = SCHOOLS.map { it to schoolDisplayName(it) },
            selected = settings.school,
            onSelect = onSchool,
        )
        asrNote(settings.school, status, placeName)?.let {
            Spacer(Modifier.height(10.dp))
            SettingsNote(it)
        }
        Spacer(Modifier.height(14.dp))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.show_both_asr),
                trailing = { TaqwaToggle(settings.showBothAsr, onShowBothAsr) },
            )
            if (status?.whereDiffer == true) {
                CardDivider()
                TaqwaRow(
                    stringResource(Res.string.show_where_differ),
                    subtitle = if (placeName != null) {
                        stringResource(Res.string.show_where_differ_detail, placeName)
                    } else {
                        stringResource(Res.string.show_where_differ_detail_here)
                    },
                    trailing = { TaqwaToggle(settings.showWhereDiffer, onShowWhereDiffer) },
                )
            }
        }

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
            onSelect = onHijriOffset,
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
                trailing = { TaqwaToggle(settings.showSunrise, onShowSunrise) },
            )
            CardDivider()
            ChevronRow(stringResource(Res.string.prayer_times_about_row), onClick = onOpenAboutTimes)
        }
    }
}

/**
 * The Timetable row's value: "Automatic · London Unified" while Automatic applies (chosen, or in
 * place of a timetable that does not apply here), the timetable's name when one applies, and
 * "· paused" after it until it is confirmed.
 */
@Composable
private fun timetableValue(settings: PrayerSettings, status: TimetableStatus?): String {
    if (status == null) return timetableDisplayName(settings.timetable)
    if (status.selectedId == AUTOMATIC_TIMETABLE) {
        return stringResource(Res.string.timetable_automatic_named, timetableNameText(status.automatic))
    }
    val name = chosenName(status)
    return if (status.paused) stringResource(Res.string.prayer_times_paused_value, name) else name
}

/**
 * The Asr note names the school by its shadow (spec §2.2, §3.7): "Automatic: twice the shadow,
 * the later time, because Toronto's majority school isn't known", "Automatic: the shadow equal to
 * its length, as Umm al-Qura", "Hanafi: twice the shadow". Null for Automatic without a place.
 */
@Composable
private fun asrNote(school: String, status: TimetableStatus?, placeName: String?): String? {
    val standard = stringResource(Res.string.asr_shadow_standard)
    val hanafi = stringResource(Res.string.asr_shadow_hanafi)
    return when (school) {
        "standard" -> stringResource(Res.string.asr_note_standard, standard)
        "hanafi" -> stringResource(Res.string.asr_note_hanafi, hanafi)
        else -> {
            status ?: return null
            val shadow = if (status.school == AsrSchool.HANAFI) hanafi else standard
            when {
                !status.schoolKnown && placeName != null -> stringResource(Res.string.asr_note_automatic_unknown, shadow, placeName)
                !status.schoolKnown -> stringResource(Res.string.asr_note_automatic_unknown_here, shadow)
                status.automatic.source == SourceKind.AUTHORITY ->
                    stringResource(Res.string.asr_note_automatic_authority, shadow, timetableNameText(status.automatic))
                else -> stringResource(Res.string.asr_note_automatic_majority, shadow)
            }
        }
    }
}

@Composable
private fun adjustmentsSummary(adjustments: Map<Prayer, Int>, paused: Boolean): String {
    val active = adjustments.count { it.value != 0 }
    val summary = when (active) {
        0 -> stringResource(Res.string.adjustments_none)
        1 -> stringResource(Res.string.adjustments_one)
        else -> stringResource(
            Res.string.adjustments_many,
            LocalPlatformFormat.current.localizedDigits(active),
        )
    }
    return if (paused && active > 0) stringResource(Res.string.prayer_times_paused_value, summary) else summary
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

/**
 * The high-latitude rule an Other method follows (spec §2.2); Automatic is the engine's own: the
 * real time whenever the sign occurs, else the MWL Fiqh Council's proportion (spec §3.8).
 *
 * A confirmed Other method applies as it is, so a new rule is checked against Automatic first,
 * as a new timetable is (review I1): [checking] while the check runs, then, if the new rule
 * begins a prayer earlier, [warning] with "Follow it" and a button that keeps the rule in use
 * ([keep]).
 */
@Composable
fun HighLatitudePickerScreen(
    current: HighLatitudePreference,
    onPick: (HighLatitudePreference) -> Unit,
    onBack: () -> Unit,
    checking: Float? = null,
    warning: PendingWarning? = null,
    automatic: TimetableName? = null,
    onKeep: () -> Unit = {},
    onFollow: () -> Unit = {},
) {
    val colors = LocalTaqwaColors.current
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
        if (checking != null) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = SettingsGutter + 4.dp)) {
                Text(stringResource(Res.string.timetable_checking), style = TaqwaText.caption, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                CheckProgressLine(checking)
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(
            stringResource(
                Res.string.high_lat_picker_note,
                LocalPlatformFormat.current.localizedDigits(HIGH_LATITUDE_DEGREES),
            ),
        )
    }
    if (warning != null && automatic != null) {
        EarlierWarningSheet(
            warning = warning,
            automatic = automatic,
            keep = stringResource(Res.string.timetable_keep_current, highLatitudeDisplayName(current)),
            onKeep = onKeep,
            onFollow = onFollow,
        )
    }
}
