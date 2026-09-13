package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.about.AboutLinks
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.drawExternalLink
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_licence
import world.taqwa.app.resources.about_licence_value
import world.taqwa.app.resources.about_links_label
import world.taqwa.app.resources.about_nothing_body
import world.taqwa.app.resources.about_nothing_title
import world.taqwa.app.resources.about_online_body
import world.taqwa.app.resources.about_online_title
import world.taqwa.app.resources.about_privacy_label
import world.taqwa.app.resources.about_privacy_policy
import world.taqwa.app.resources.about_source
import world.taqwa.app.resources.about_source_value
import world.taqwa.app.resources.about_stays_body
import world.taqwa.app.resources.about_stays_title
import world.taqwa.app.resources.about_tagline
import world.taqwa.app.resources.settings_about
import world.taqwa.app.resources.settings_version_value

/**
 * Settings › About Taqwa (privacy spec §6): who made this, what it promises, and where the
 * promise can be checked. Three cards in the settings idiom; the credits stay on their own
 * screen and are not repeated here.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val uriHandler = LocalUriHandler.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    // A device with no browser fails silently rather than crashing (spec §6.3).
    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    SettingsScaffold(stringResource(Res.string.settings_about), onBack) {
        SettingsCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                // The product name, in Latin script in both locales, as on the icon.
                Text("Taqwa", style = TaqwaText.screenTitle, color = colors.textPrimary)
                Text(
                    stringResource(Res.string.settings_version_value),
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(Res.string.about_tagline), style = TaqwaText.rowLabel, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.about_privacy_label))
        SettingsCard {
            TaqwaRow(stringResource(Res.string.about_stays_title), subtitle = stringResource(Res.string.about_stays_body))
            CardDivider()
            TaqwaRow(stringResource(Res.string.about_online_title), subtitle = stringResource(Res.string.about_online_body))
            CardDivider()
            TaqwaRow(stringResource(Res.string.about_nothing_title), subtitle = stringResource(Res.string.about_nothing_body))
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.about_links_label))
        SettingsCard {
            LinkRow(stringResource(Res.string.about_privacy_policy), null, forward) { open(AboutLinks.PRIVACY_POLICY) }
            CardDivider()
            LinkRow(stringResource(Res.string.about_source), stringResource(Res.string.about_source_value), forward) {
                open(AboutLinks.SOURCE)
            }
            CardDivider()
            LinkRow(stringResource(Res.string.about_licence), stringResource(Res.string.about_licence_value), forward) {
                open(AboutLinks.LICENCE)
            }
        }
    }
}

/** A row that leaves the app: the external-link glyph in the trailing slot says so before the tap. */
@Composable
private fun LinkRow(label: String, value: String?, forward: Boolean, onClick: () -> Unit) {
    val tint = LocalTaqwaColors.current.textTertiary
    TaqwaRow(
        label,
        value = value,
        onClick = onClick,
        trailing = { Canvas(Modifier.size(16.dp)) { drawExternalLink(tint, forward) } },
    )
}
