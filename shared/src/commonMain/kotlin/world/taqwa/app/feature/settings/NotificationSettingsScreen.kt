package world.taqwa.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.RadioMark
import world.taqwa.app.design.components.LocalReduceMotion
import world.taqwa.app.design.components.RowToggle
import world.taqwa.app.design.components.SettingsReveal
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.reduceMotion
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.DuhaTiming
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.adhanVoiceDisplayName
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.i18n.soundDisplayName
import world.taqwa.app.notifications.rememberNotificationPermissionRequester
import world.taqwa.app.prayer.DuhaTimes
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhan_voice_row
import world.taqwa.app.resources.minutes_count
import world.taqwa.app.resources.notifications_adhkar
import world.taqwa.app.resources.notifications_adhkar_subtitle
import world.taqwa.app.resources.notifications_allow_exact_alarms
import world.taqwa.app.resources.notifications_blocked
import world.taqwa.app.resources.notifications_duha
import world.taqwa.app.resources.notifications_duha_best
import world.taqwa.app.resources.notifications_duha_best_detail
import world.taqwa.app.resources.notifications_duha_start
import world.taqwa.app.resources.notifications_duha_start_detail
import world.taqwa.app.resources.notifications_duha_when
import world.taqwa.app.resources.notifications_exact_alarms_off
import world.taqwa.app.resources.notifications_iqama
import world.taqwa.app.resources.notifications_iqama_after
import world.taqwa.app.resources.notifications_iqama_not_friday
import world.taqwa.app.resources.notifications_iqama_note
import world.taqwa.app.resources.notifications_iqama_off
import world.taqwa.app.resources.notifications_iqama_subtitle
import world.taqwa.app.resources.notifications_open_settings
import world.taqwa.app.resources.notifications_master_toggle
import world.taqwa.app.resources.notifications_prayers_label
import world.taqwa.app.resources.notifications_remind_before
import world.taqwa.app.resources.notifications_remind_never
import world.taqwa.app.resources.notifications_section_iqama
import world.taqwa.app.resources.notifications_section_sound
import world.taqwa.app.resources.notifications_section_when
import world.taqwa.app.resources.notifications_tahajjud
import world.taqwa.app.resources.notifications_tahajjud_sound
import world.taqwa.app.resources.notifications_tahajjud_subtitle
import world.taqwa.app.resources.settings_notifications
import world.taqwa.app.design.components.TaqwaBottomSheet

// A plural, not a formatted string: Arabic reads "5 دقائق" but "15 دقيقة", and only the
// quantity rules can tell those apart. The digits are localized before they go in, so the
// resource never sees a Western numeral under an Arabic UI.
@Composable
private fun leadLabel(minutes: Int): String = if (minutes == 0) {
    stringResource(Res.string.notifications_remind_never)
} else {
    pluralStringResource(
        Res.plurals.minutes_count,
        minutes,
        LocalPlatformFormat.current.localizedDigits(minutes),
    )
}

/** The line under a prayer's row while Iqama is on, a plural for Arabic's counting as [leadLabel]. */
@Composable
private fun iqamaLine(minutes: Int): String = pluralStringResource(
    Res.plurals.notifications_iqama_after,
    minutes,
    LocalPlatformFormat.current.localizedDigits(minutes),
)

/** An option in a prayer's iqama list: Off, or its minutes as the Remind-me-before sheet writes them. */
@Composable
private fun iqamaOption(minutes: Int): String = if (minutes == 0) {
    stringResource(Res.string.notifications_iqama_off)
} else {
    pluralStringResource(Res.plurals.minutes_count, minutes, LocalPlatformFormat.current.localizedDigits(minutes))
}

@Composable
private fun duhaTimingName(timing: DuhaTiming): String = stringResource(
    when (timing) {
        DuhaTiming.BEST -> Res.string.notifications_duha_best
        DuhaTiming.START -> Res.string.notifications_duha_start
    },
)

/** The timing's one-line detail: the Duha card's subtitle and the line under each option. */
@Composable
private fun duhaTimingDetail(timing: DuhaTiming): String = when (timing) {
    DuhaTiming.BEST -> stringResource(Res.string.notifications_duha_best_detail)
    DuhaTiming.START -> stringResource(
        Res.string.notifications_duha_start_detail,
        LocalPlatformFormat.current.localizedDigits(DuhaTimes.AFTER_SUNRISE.inWholeMinutes.toInt()),
    )
}

/**
 * Master toggle, "Remind me before" (default Never), the adhan voice and one row per obligatory
 * prayer — Sunrise is never notified, so it gets no row. Every control writes through
 * [onToggleEnabled], [onPickLead], [onPickVoice] or [onPickSound] the moment it is touched; there
 * is no save button, matching every other settings screen in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    settings: NotificationSettings,
    // Android only, and only when the user has revoked "Alarms & reminders": the scheduler then
    // falls back to an inexact window rather than crashing, and this is where that trade-off is
    // admitted. Always false on iOS.
    exactAlarmsUnavailable: Boolean = false,
    /** Whether the OS currently lets Taqwa post notifications at all. Re-read by the caller each
     * time the app comes to the front, so a switch flipped in system settings is reflected here. */
    notificationsGranted: Boolean = true,
    /** The app's page in system settings, for when [notificationsGranted] is false. */
    onOpenNotificationSettings: () -> Unit = {},
    /** Android's "Alarms & reminders" access for this app, for when [exactAlarmsUnavailable]. */
    onRequestExactAlarms: () -> Unit = {},
    /** The permission request has answered; the caller re-reads [notificationsGranted]. */
    onPermissionChecked: () -> Unit = {},
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onPickLead: (Int) -> Unit,
    onPickSound: (Prayer, PrayerSound) -> Unit,
    onPreviewSound: (PrayerSound) -> Unit,
    /** One voice for all five prayers. Like a sound change, it has to reschedule: the channel a
     * notification posts into carries the voice, and a channel's sound is immutable. */
    onPickVoice: (AdhanVoice) -> Unit = {},
    /** Auditions the complete adhan in that voice, not the clip a notification would play. */
    onPreviewVoice: (AdhanVoice) -> Unit = {},
    /** Called whenever either audio sheet goes away, however it goes: the complete adhan previews
     * for minutes, and it must not carry on under a closed sheet. */
    onStopPreview: () -> Unit = {},
    /** Tahajjud on or off, and what it sounds like (spec §17.6). Both reschedule. */
    onToggleTahajjud: (Boolean) -> Unit = {},
    onPickTahajjudSound: (PrayerSound) -> Unit = {},
    /** The morning and evening adhkar reminder on or off, and its sound (adhkar spec §5). Both
     * reschedule. */
    onToggleAdhkar: (Boolean) -> Unit = {},
    onPickAdhkarSound: (PrayerSound) -> Unit = {},
    /** Duha on or off, when it rings and its sound (Duha and Iqama spec §4.2). Each reschedules. */
    onToggleDuha: (Boolean) -> Unit = {},
    onPickDuhaTiming: (DuhaTiming) -> Unit = {},
    onPickDuhaSound: (PrayerSound) -> Unit = {},
    /** Iqama on or off, a prayer's minutes after its adhan (0 is Off) and its sound. Each
     * reschedules. */
    onToggleIqama: (Boolean) -> Unit = {},
    onPickIqamaMinutes: (Prayer, Int) -> Unit = { _, _ -> },
    onPickIqamaSound: (PrayerSound) -> Unit = {},
) {
    var soundSheetFor by remember { mutableStateOf<Prayer?>(null) }
    var tahajjudSheetOpen by remember { mutableStateOf(false) }
    var adhkarSheetOpen by remember { mutableStateOf(false) }
    var remindSheetOpen by remember { mutableStateOf(false) }
    var voiceSheetOpen by remember { mutableStateOf(false) }
    var duhaWhenSheetOpen by remember { mutableStateOf(false) }
    var duhaSoundSheetOpen by remember { mutableStateOf(false) }
    var iqamaSoundSheetOpen by remember { mutableStateOf(false) }
    // Read once for the screen (spec §4.3): every reveal on it stands still under reduced motion.
    val reducedMotion = remember { reduceMotion() }

    // Turning the master switch on while the OS permission is missing asks for it first: on
    // Android 13+ that is the system dialog (or an immediate refusal once it has been declined
    // twice), on iOS a permission refused before answers at once. The switch goes on either way,
    // because notifications are what the person asked for; the note below then says what still
    // stands in the way and where to fix it.
    val requestPermission = rememberNotificationPermissionRequester { _ ->
        onToggleEnabled(true)
        onPermissionChecked()
    }

    CompositionLocalProvider(LocalReduceMotion provides reducedMotion) {
        SettingsScaffold(stringResource(Res.string.settings_notifications), onBack) {
            SettingsCard {
                TaqwaRow(
                    stringResource(Res.string.notifications_master_toggle),
                    toggle = RowToggle(settings.enabled) { enabled ->
                        if (enabled && !notificationsGranted) requestPermission() else onToggleEnabled(enabled)
                    },
                )
            }

            if (settings.enabled && !notificationsGranted) {
                Spacer(Modifier.height(10.dp))
                SettingsNote(stringResource(Res.string.notifications_blocked))
                SettingsLink(stringResource(Res.string.notifications_open_settings), onOpenNotificationSettings)
            }
            if (exactAlarmsUnavailable) {
                Spacer(Modifier.height(10.dp))
                SettingsNote(stringResource(Res.string.notifications_exact_alarms_off))
                SettingsLink(stringResource(Res.string.notifications_allow_exact_alarms), onRequestExactAlarms)
            }

            Spacer(Modifier.height(28.dp))
            SettingsCard {
                TaqwaRow(
                    stringResource(Res.string.notifications_remind_before),
                    value = leadLabel(settings.remindBeforeMinutes),
                    onClick = { remindSheetOpen = true },
                )
            }

            // Off, one quiet row and nothing anywhere else (Duha and Iqama spec §4.2). On, its sound,
            // the note under the card, a line under each prayer and the minutes in each prayer's
            // sheet: the minutes belong to the prayers, so they are set there, not here.
            Spacer(Modifier.height(28.dp))
            SettingsCard {
                TaqwaRow(
                    stringResource(Res.string.notifications_iqama),
                    subtitle = stringResource(Res.string.notifications_iqama_subtitle),
                    revealSubtitle = settings.iqama,
                    toggle = RowToggle(settings.iqama, onToggleIqama),
                )
                SettingsReveal(settings.iqama) {
                    Column {
                        CardDivider()
                        TaqwaRow(
                            stringResource(Res.string.notifications_tahajjud_sound),
                            value = soundDisplayName(settings.iqamaSound),
                            onClick = { iqamaSoundSheetOpen = true },
                        )
                    }
                }
            }
            SettingsReveal(settings.iqama) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    SettingsNote(stringResource(Res.string.notifications_iqama_note))
                }
            }

            Spacer(Modifier.height(28.dp))
            SettingsCard {
                // Its own card rather than a row inside the per-prayer one: it is the single choice
                // that applies to all five, and sitting among them would read as a sixth prayer.
                TaqwaRow(
                    stringResource(Res.string.adhan_voice_row),
                    value = adhanVoiceDisplayName(settings.voice),
                    onClick = { voiceSheetOpen = true },
                )
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(Res.string.notifications_prayers_label))
            SettingsCard {
                ObligatoryPrayers.forEachIndexed { i, prayer ->
                    if (i > 0) CardDivider()
                    val iqamaMinutes = settings.iqamaMinutesFor(prayer)
                    TaqwaRow(
                        // The name alone here, not the Arabic-alone/paired display rule: this is a
                        // settings row, not one of the places the spec names (timeline, widget,
                        // notification), and a doubled name would crowd the sound value beside it.
                        label = localizedPrayerName(prayer),
                        value = soundDisplayName(settings.soundFor(prayer)),
                        // While Iqama is on, the prayer's gap under its name; none for an Off prayer.
                        // One element to a screen reader, as the row always was: name, line, sound.
                        subtitle = if (iqamaMinutes > 0) iqamaLine(iqamaMinutes) else null,
                        revealSubtitle = settings.iqama && iqamaMinutes > 0,
                        onClick = { soundSheetFor = prayer },
                    )
                }
            }

            // A card of its own, below the five and off until asked for (spec §17.6): a voluntary
            // prayer is offered, not assumed, and it is not one of the prayers the card above is
            // about. Its sound row exists only while it is on, so the screen a person who never
            // wants it sees has grown by exactly one quiet row.
            Spacer(Modifier.height(28.dp))
            SettingsCard {
                TaqwaRow(
                    stringResource(Res.string.notifications_tahajjud),
                    subtitle = stringResource(Res.string.notifications_tahajjud_subtitle),
                    toggle = RowToggle(checked = settings.tahajjud, onCheckedChange = onToggleTahajjud),
                )
                SettingsReveal(settings.tahajjud) {
                    Column {
                        CardDivider()
                        TaqwaRow(
                            stringResource(Res.string.notifications_tahajjud_sound),
                            value = soundDisplayName(settings.tahajjudSound),
                            onClick = { tahajjudSheetOpen = true },
                        )
                    }
                }
            }

            // Duha (Duha and Iqama spec §4.2), built as Tahajjud's card is: off, the row and its switch
            // and nothing else; on, the chosen timing's detail, When and Sound.
            Spacer(Modifier.height(28.dp))
            SettingsCard {
                TaqwaRow(
                    stringResource(Res.string.notifications_duha),
                    subtitle = duhaTimingDetail(settings.duhaTiming),
                    revealSubtitle = settings.duha,
                    toggle = RowToggle(settings.duha, onToggleDuha),
                )
                SettingsReveal(settings.duha) {
                    Column {
                        CardDivider()
                        TaqwaRow(
                            stringResource(Res.string.notifications_duha_when),
                            value = duhaTimingName(settings.duhaTiming),
                            onClick = { duhaWhenSheetOpen = true },
                        )
                        CardDivider()
                        TaqwaRow(
                            stringResource(Res.string.notifications_tahajjud_sound),
                            value = soundDisplayName(settings.duhaSound),
                            onClick = { duhaSoundSheetOpen = true },
                        )
                    }
                }
            }

            // The adhkar reminder (adhkar spec §5), built as the Tahajjud card is and for the same
            // reasons: off until asked for, and its sound row there only while it is on.
            Spacer(Modifier.height(28.dp))
            SettingsCard {
                TaqwaRow(
                    stringResource(Res.string.notifications_adhkar),
                    subtitle = stringResource(Res.string.notifications_adhkar_subtitle),
                    toggle = RowToggle(settings.adhkar, onToggleAdhkar),
                )
                SettingsReveal(settings.adhkar) {
                    Column {
                        CardDivider()
                        TaqwaRow(
                            stringResource(Res.string.notifications_tahajjud_sound),
                            value = soundDisplayName(settings.adhkarSound),
                            onClick = { adhkarSheetOpen = true },
                        )
                    }
                }
            }
        }
    }

    if (remindSheetOpen) {
        TaqwaBottomSheet(onDismissRequest = { remindSheetOpen = false }) {
            Text(
                stringResource(Res.string.notifications_remind_before),
                style = TaqwaText.screenTitle,
                modifier = Modifier.padding(horizontal = SettingsGutter),
            )
            Spacer(Modifier.height(16.dp))
            SettingsCard(Modifier.selectableGroup()) {
                NotificationSettings.LeadOptions.forEachIndexed { i, minutes ->
                    if (i > 0) CardDivider()
                    TaqwaRow(
                        label = leadLabel(minutes),
                        onClick = { onPickLead(minutes); remindSheetOpen = false },
                        selectable = true,
                        selected = minutes == settings.remindBeforeMinutes,
                        trailing = { if (minutes == settings.remindBeforeMinutes) CheckMark() },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (voiceSheetOpen) {
        // Same guarantee as the sound sheet below, and it matters more here: this sheet's play
        // button starts a four-minute recording.
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        TaqwaBottomSheet(onDismissRequest = { voiceSheetOpen = false }) {
            AdhanVoiceSheet(
                current = settings.voice,
                onPick = { voice -> onPickVoice(voice); voiceSheetOpen = false },
                onPreview = onPreviewVoice,
            )
        }
    }

    if (tahajjudSheetOpen) {
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        TaqwaBottomSheet(onDismissRequest = { tahajjudSheetOpen = false }) {
            SoundSheet(
                current = settings.tahajjudSound,
                voice = settings.voice,
                onPick = { sound -> onPickTahajjudSound(sound); tahajjudSheetOpen = false },
                onPreview = onPreviewSound,
                options = NotificationSettings.TahajjudSounds,
            )
        }
    }

    if (duhaWhenSheetOpen) {
        TaqwaBottomSheet(onDismissRequest = { duhaWhenSheetOpen = false }) {
            Text(
                stringResource(Res.string.notifications_duha),
                style = TaqwaText.screenTitle,
                modifier = Modifier.padding(horizontal = SettingsGutter),
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(Res.string.notifications_section_when))
            SettingsCard(Modifier.selectableGroup()) {
                DuhaTiming.entries.forEachIndexed { i, timing ->
                    if (i > 0) CardDivider()
                    TaqwaRow(
                        label = duhaTimingName(timing),
                        subtitle = duhaTimingDetail(timing),
                        onClick = { onPickDuhaTiming(timing); duhaWhenSheetOpen = false },
                        selectable = true,
                        selected = timing == settings.duhaTiming,
                        trailing = { if (timing == settings.duhaTiming) CheckMark() },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (duhaSoundSheetOpen) {
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        TaqwaBottomSheet(onDismissRequest = { duhaSoundSheetOpen = false }) {
            SoundSheet(
                current = settings.duhaSound,
                voice = settings.voice,
                onPick = { sound -> onPickDuhaSound(sound); duhaSoundSheetOpen = false },
                onPreview = onPreviewSound,
                options = NotificationSettings.DuhaSounds,
            )
        }
    }

    if (iqamaSoundSheetOpen) {
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        TaqwaBottomSheet(onDismissRequest = { iqamaSoundSheetOpen = false }) {
            SoundSheet(
                current = settings.iqamaSound,
                voice = settings.voice,
                onPick = { sound -> onPickIqamaSound(sound); iqamaSoundSheetOpen = false },
                onPreview = onPreviewSound,
                options = NotificationSettings.IqamaSounds,
            )
        }
    }

    if (adhkarSheetOpen) {
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        TaqwaBottomSheet(onDismissRequest = { adhkarSheetOpen = false }) {
            SoundSheet(
                current = settings.adhkarSound,
                voice = settings.voice,
                onPick = { sound -> onPickAdhkarSound(sound); adhkarSheetOpen = false },
                onPreview = onPreviewSound,
                options = NotificationSettings.AdhkarSounds,
            )
        }
    }

    soundSheetFor?.let { prayer ->
        // Covers every exit at once: swipe, scrim tap, a pick, back, and leaving the screen.
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        TaqwaBottomSheet(onDismissRequest = { soundSheetFor = null }, skipPartiallyExpanded = settings.iqama) {
            SoundSheet(
                current = settings.soundFor(prayer),
                voice = settings.voice,
                onPick = { sound -> onPickSound(prayer, sound); soundSheetFor = null },
                onPreview = onPreviewSound,
                // While Iqama is on the sheet also holds the prayer's minutes, so it takes the
                // prayer's name and labels its two sections (spec §4.2). Off, it is unchanged.
                title = if (settings.iqama) localizedPrayerName(prayer) else null,
                sectionLabel = if (settings.iqama) stringResource(Res.string.notifications_section_sound) else null,
                extra = if (settings.iqama) {
                    {
                        IqamaMinutes(
                            prayer = prayer,
                            current = settings.iqamaMinutesFor(prayer),
                            onPick = { minutes -> onPickIqamaMinutes(prayer, minutes); soundSheetFor = null },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * A prayer's iqama minutes, the second section of its sound sheet while Iqama is on: Off and
 * 5 to 30 as radio rows. Dhuhr's adds that there is no iqama reminder on a Friday.
 */
@Composable
private fun IqamaMinutes(prayer: Prayer, current: Int, onPick: (Int) -> Unit) {
    SectionLabel(stringResource(Res.string.notifications_section_iqama), gutter = 0.dp)
    TaqwaCard(Modifier.selectableGroup()) {
        NotificationSettings.IqamaOptions.forEachIndexed { i, minutes ->
            if (i > 0) CardDivider()
            TaqwaRow(
                label = iqamaOption(minutes),
                onClick = { onPick(minutes) },
                selectable = true,
                selected = minutes == current,
                // The sheet's one selection style: the same radio as its SOUND rows above.
                trailing = { RadioMark(selected = minutes == current) },
            )
        }
    }
    if (prayer == Prayer.DHUHR) {
        Spacer(Modifier.height(10.dp))
        SettingsNote(stringResource(Res.string.notifications_iqama_not_friday), gutter = 0.dp)
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * The one action a note can offer: a system screen the app cannot stand in for. Left-aligned
 * under its note rather than the centred [world.taqwa.app.design.components.TaqwaTextLink], and
 * a 44 dp row so it is a target, not a line of text.
 */
@Composable
private fun SettingsLink(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = SettingsGutter)
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold),
            color = LocalTaqwaColors.current.accent,
        )
    }
}
