package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.adhanVoiceDisplayName
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.i18n.soundDisplayName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhan_voice_row
import world.taqwa.app.resources.minutes_count
import world.taqwa.app.resources.notifications_exact_alarms_off
import world.taqwa.app.resources.notifications_master_toggle
import world.taqwa.app.resources.notifications_prayers_label
import world.taqwa.app.resources.notifications_remind_before
import world.taqwa.app.resources.notifications_remind_never
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
) {
    var soundSheetFor by remember { mutableStateOf<Prayer?>(null) }
    var remindSheetOpen by remember { mutableStateOf(false) }
    var voiceSheetOpen by remember { mutableStateOf(false) }

    SettingsScaffold(stringResource(Res.string.settings_notifications), onBack) {
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.notifications_master_toggle),
                trailing = { TaqwaToggle(checked = settings.enabled, onCheckedChange = onToggleEnabled) },
            )
        }

        if (exactAlarmsUnavailable) {
            Spacer(Modifier.height(10.dp))
            SettingsNote(stringResource(Res.string.notifications_exact_alarms_off))
        }

        Spacer(Modifier.height(28.dp))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.notifications_remind_before),
                value = leadLabel(settings.remindBeforeMinutes),
                onClick = { remindSheetOpen = true },
            )
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
                TaqwaRow(
                    // The name alone here, not the Arabic-alone/paired display rule: this is a
                    // settings row, not one of the places the spec names (timeline, widget,
                    // notification), and a doubled name would crowd the sound value beside it.
                    label = localizedPrayerName(prayer),
                    value = soundDisplayName(settings.soundFor(prayer)),
                    onClick = { soundSheetFor = prayer },
                )
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
            SettingsCard {
                NotificationSettings.LeadOptions.forEachIndexed { i, minutes ->
                    if (i > 0) CardDivider()
                    TaqwaRow(
                        label = leadLabel(minutes),
                        onClick = { onPickLead(minutes); remindSheetOpen = false },
                        selectable = true,
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

    soundSheetFor?.let { prayer ->
        // Covers every exit at once: swipe, scrim tap, a pick, back, and leaving the screen.
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        TaqwaBottomSheet(onDismissRequest = { soundSheetFor = null }) {
            SoundSheet(
                current = settings.soundFor(prayer),
                voice = settings.voice,
                onPick = { sound -> onPickSound(prayer, sound); soundSheetFor = null },
                onPreview = onPreviewSound,
            )
        }
    }
}
