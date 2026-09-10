package world.taqwa.app.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_ayah_widget_preview_label
import world.taqwa.app.resources.appearance_note
import world.taqwa.app.resources.appearance_theme_label
import world.taqwa.app.resources.appearance_widget_background_label
import world.taqwa.app.resources.appearance_prayer_widget_preview_label
import world.taqwa.app.resources.settings_appearance
import world.taqwa.app.resources.theme_dark
import world.taqwa.app.resources.theme_light
import world.taqwa.app.resources.theme_system
import world.taqwa.app.resources.widget_background_dark
import world.taqwa.app.resources.widget_background_follow_theme
import world.taqwa.app.resources.widget_background_light
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetContentBuilder
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.isIosPlatform
import world.taqwa.app.widget.translucentOrFrostedSubtitleKey
import world.taqwa.app.widget.translucentOrFrostedTitleKey

@Composable
internal fun themeDisplayName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> Res.string.theme_system
        ThemeMode.LIGHT -> Res.string.theme_light
        ThemeMode.DARK -> Res.string.theme_dark
    },
)

/**
 * Four rows rather than a segmented control: a row with a check reads correctly to a screen
 * reader as a selected option, and it matches every other choice in settings. Only the fourth
 * option carries a [widgetBackgroundSubtitle] — its per-platform wording is long enough that it
 * needs its own line rather than crowding the check mark off the row.
 */
@Composable
internal fun widgetBackgroundTitle(value: WidgetBackground): String = when (value) {
    WidgetBackground.FOLLOW_THEME -> stringResource(Res.string.widget_background_follow_theme)
    WidgetBackground.LIGHT -> stringResource(Res.string.widget_background_light)
    WidgetBackground.DARK -> stringResource(Res.string.widget_background_dark)
    WidgetBackground.TRANSLUCENT_OR_FROSTED ->
        stringResource(translucentOrFrostedTitleKey(isIosPlatform))
}

@Composable
internal fun widgetBackgroundSubtitle(value: WidgetBackground): String? = when (value) {
    WidgetBackground.TRANSLUCENT_OR_FROSTED ->
        stringResource(translucentOrFrostedSubtitleKey(isIosPlatform))
    else -> null
}

@Composable
fun AppearanceSettingsScreen(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    widgetBackground: WidgetBackground,
    onPickWidgetBackground: (WidgetBackground) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(Res.string.settings_appearance), onBack) {
        SectionLabel(stringResource(Res.string.appearance_theme_label))
        SettingsCard {
            ThemeMode.entries.forEachIndexed { i, mode ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = themeDisplayName(mode),
                    onClick = { onPick(mode) },
                    selectable = true,
                    trailing = { if (mode == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(stringResource(Res.string.appearance_note))

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.appearance_widget_background_label))
        SettingsCard {
            WidgetBackground.entries.forEachIndexed { i, value ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = widgetBackgroundTitle(value),
                    subtitle = widgetBackgroundSubtitle(value),
                    onClick = { onPickWidgetBackground(value) },
                    selectable = true,
                    trailing = { if (value == widgetBackground) CheckMark() },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel(stringResource(Res.string.appearance_prayer_widget_preview_label))
        val systemIsDark = isSystemInDarkTheme()
        // Read once per composition rather than observed live: the mirror only changes when
        // Today refreshes (once a second while it's open), and re-reading it here on every
        // recomposition would be wasted work for a preview whose only job is to react to
        // [widgetBackground] changing.
        val mirrorContent: WidgetContent? = remember {
            WidgetMirrorWriter.read(createWidgetKeyValueStore())?.let(WidgetContentBuilder::build)
        }
        WidgetPreview(
            background = widgetBackground,
            systemIsDark = systemIsDark,
            content = mirrorContent,
            modifier = Modifier.padding(horizontal = SettingsGutter),
        )

        Spacer(Modifier.height(14.dp))
        SectionLabel(stringResource(Res.string.appearance_ayah_widget_preview_label))
        AyahWidgetPreview(
            background = widgetBackground,
            systemIsDark = systemIsDark,
            modifier = Modifier.padding(horizontal = SettingsGutter),
        )
    }
}
