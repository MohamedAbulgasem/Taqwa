package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.feature.today.englishName

private fun soundLabel(sound: PrayerSound): String = when (sound) {
    PrayerSound.SILENT -> "Silent"
    PrayerSound.NOTIFICATION -> "Notification"
    PrayerSound.TAKBIR -> "Takbir"
    PrayerSound.ADHAN -> "Adhan"
}

private fun leadLabel(minutes: Int): String = if (minutes == 0) "Never" else "$minutes min"

/**
 * Master toggle, "Remind me before" (default Never) and one row per obligatory prayer — Sunrise
 * is never notified, so it gets no row. Every control writes through [onToggleEnabled],
 * [onPickLead] or [onPickSound] the moment it is touched; there is no save button, matching every
 * other settings screen in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    settings: NotificationSettings,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onPickLead: (Int) -> Unit,
    onPickSound: (Prayer, PrayerSound) -> Unit,
    onPreviewSound: (PrayerSound) -> Unit,
) {
    var soundSheetFor by remember { mutableStateOf<Prayer?>(null) }
    var remindSheetOpen by remember { mutableStateOf(false) }

    SettingsScaffold("Notifications", onBack) {
        SettingsCard {
            TaqwaRow(
                "Prayer notifications",
                trailing = { TaqwaToggle(checked = settings.enabled, onCheckedChange = onToggleEnabled) },
            )
        }

        Spacer(Modifier.height(28.dp))
        SettingsCard {
            TaqwaRow(
                "Remind me before",
                value = leadLabel(settings.remindBeforeMinutes),
                onClick = { remindSheetOpen = true },
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("PRAYERS")
        SettingsCard {
            ObligatoryPrayers.forEachIndexed { i, prayer ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = englishName(prayer),
                    value = soundLabel(settings.soundFor(prayer)),
                    onClick = { soundSheetFor = prayer },
                )
            }
        }
    }

    if (remindSheetOpen) {
        ModalBottomSheet(onDismissRequest = { remindSheetOpen = false }) {
            Text(
                "Remind me before",
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
                        trailing = { if (minutes == settings.remindBeforeMinutes) CheckMark() },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    soundSheetFor?.let { prayer ->
        ModalBottomSheet(onDismissRequest = { soundSheetFor = null }) {
            SoundSheet(
                current = settings.soundFor(prayer),
                onPick = { sound -> onPickSound(prayer, sound); soundSheetFor = null },
                onPreview = onPreviewSound,
            )
        }
    }
}
