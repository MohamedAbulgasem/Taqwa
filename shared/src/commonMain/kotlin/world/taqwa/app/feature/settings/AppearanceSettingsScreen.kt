package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.notifications.canOpenAppLanguageSettings
import world.taqwa.app.notifications.openAppLanguageSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_language_label
import world.taqwa.app.resources.appearance_note
import world.taqwa.app.resources.language_name
import world.taqwa.app.resources.appearance_theme_label
import world.taqwa.app.resources.settings_appearance
import world.taqwa.app.resources.theme_dark
import world.taqwa.app.resources.theme_light
import world.taqwa.app.resources.theme_system

@Composable
internal fun themeDisplayName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> Res.string.theme_system
        ThemeMode.LIGHT -> Res.string.theme_light
        ThemeMode.DARK -> Res.string.theme_dark
    },
)

/**
 * The app's theme and its language: the widgets, their background and the ways to add them have
 * their own screen, Settings › Widgets, where a reader who skipped onboarding's widget step can
 * find them again.
 */
@Composable
fun AppearanceSettingsScreen(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(Res.string.settings_appearance), onBack) {
        SectionLabel(stringResource(Res.string.appearance_theme_label))
        SettingsCard(Modifier.selectableGroup()) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = themeDisplayName(mode),
                    onClick = { onPick(mode) },
                    selectable = true,
                    selected = mode == current,
                    trailing = { if (mode == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(stringResource(Res.string.appearance_note))

        // The language is the device's, not a setting of ours — `language_name` is each
        // translation naming itself. Where the system has a per-app language page (Android 13
        // and later, iOS) the row opens it, so a reader on an English phone can put Taqwa in Urdu
        // without knowing where that page lives; older Android only states it.
        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.appearance_language_label))
        SettingsCard {
            TaqwaRow(
                label = stringResource(Res.string.language_name),
                onClick = if (canOpenAppLanguageSettings()) ({ openAppLanguageSettings() }) else null,
            )
        }
    }
}
