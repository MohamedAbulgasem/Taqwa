package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow

internal fun themeDisplayName(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

/**
 * Three rows rather than a segmented control: a row with a check reads correctly to a screen
 * reader as a selected option, and it matches every other choice in settings.
 */
@Composable
fun AppearanceSettingsScreen(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold("Appearance", onBack) {
        SectionLabel("THEME")
        SettingsCard {
            ThemeMode.entries.forEachIndexed { i, mode ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = themeDisplayName(mode),
                    onClick = { onPick(mode) },
                    trailing = { if (mode == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote("System follows your device's light and dark setting.")
    }
}
