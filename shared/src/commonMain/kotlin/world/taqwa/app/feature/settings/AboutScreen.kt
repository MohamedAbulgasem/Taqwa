package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.about.AboutLinks
import world.taqwa.app.crash.ReportMail
import world.taqwa.app.crash.crashLogStore
import world.taqwa.app.crash.deviceInfoOrUnknown
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.LocalTaqwaDark
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.drawExternalLink
import world.taqwa.app.feature.onboarding.drawMihrab
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_icon_open_website
import world.taqwa.app.resources.about_licence
import world.taqwa.app.resources.about_licence_value
import world.taqwa.app.resources.about_links_label
import world.taqwa.app.resources.about_nothing_body
import world.taqwa.app.resources.about_nothing_title
import world.taqwa.app.resources.about_online_body
import world.taqwa.app.resources.about_online_title
import world.taqwa.app.resources.about_privacy_label
import world.taqwa.app.resources.about_privacy_policy
import world.taqwa.app.resources.about_report_problem
import world.taqwa.app.resources.about_source
import world.taqwa.app.resources.about_source_value
import world.taqwa.app.resources.about_stays_body
import world.taqwa.app.resources.about_stays_title
import world.taqwa.app.resources.about_tagline
import world.taqwa.app.resources.about_website
import world.taqwa.app.resources.about_website_value
import world.taqwa.app.resources.crash_mail_no_report
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
    val website = AboutLinks.website(arabic = isRtlLocale())
    val noReportLine = stringResource(Res.string.crash_mail_no_report)
    // A device with no browser fails silently rather than crashing (spec §6.3).
    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    SettingsScaffold(stringResource(Res.string.settings_about), onBack) {
        SettingsCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // The product name, in Latin script in both locales, as on the icon.
                    Text("Taqwa", style = TaqwaText.screenTitle, color = colors.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(Res.string.settings_version_value),
                        style = TaqwaText.caption,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.about_tagline), style = TaqwaText.rowLabel, color = colors.textPrimary)
                }
                Spacer(Modifier.width(16.dp))
                // The icon is a shortcut to the site; the Website row below is the visible way in.
                AppIconTile(stringResource(Res.string.about_icon_open_website)) { open(website) }
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
            LinkRow(stringResource(Res.string.about_website), stringResource(Res.string.about_website_value), forward) {
                open(website)
            }
            CardDivider()
            LinkRow(stringResource(Res.string.about_privacy_policy), null, forward) { open(AboutLinks.PRIVACY_POLICY) }
            CardDivider()
            LinkRow(stringResource(Res.string.about_source), stringResource(Res.string.about_source_value), forward) {
                open(AboutLinks.SOURCE)
            }
            CardDivider()
            LinkRow(stringResource(Res.string.about_licence), stringResource(Res.string.about_licence_value), forward) {
                open(AboutLinks.LICENCE)
            }
            CardDivider()
            // The support email (crash spec §3.2), carrying the last crash report if there is one.
            LinkRow(stringResource(Res.string.about_report_problem), null, forward) {
                val info = deviceInfoOrUnknown()
                val body = ReportMail.body(info, crashLogStore.read(), noReportLine)
                open(ReportMail.mailto(AboutLinks.SUPPORT_EMAIL, ReportMail.subject(info), body))
            }
            CardDivider()
            // TEMPORARY (crash plan Task 6): reverted before merge.
            LinkRow("Crash now", null, forward) {
                throw IllegalStateException("Deliberate test crash from About")
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

/**
 * The app icon as it sits on the home screen: a rounded tile with the mihrab in the icon's own
 * colours — a white tile with the near-black arch in light, and the same two swapped in dark,
 * the amber point unchanged. Literal colours on purpose: they are the launcher asset's, not the
 * theme's, and the tile has to read as the icon rather than as one more surface. A hairline
 * keeps its edge on a card of nearly the same colour in either theme. Tapping it opens the
 * website: the clip comes before the click so the ripple keeps the tile's corners, and the
 * description names the destination for screen readers, since the tile has no text of its own.
 */
@Composable
private fun AppIconTile(contentDescription: String, onClick: () -> Unit) {
    val dark = LocalTaqwaDark.current
    val hairline = LocalTaqwaColors.current.hairline
    val tile = if (dark) IconInk else IconPaper
    val arch = if (dark) IconPaper else IconInk
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(56.dp)
            .clip(shape)
            .background(tile)
            .border(1.dp, hairline, shape)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        // The launcher draws the mark at 78 % of its canvas so no mask clips it; the same here.
        Canvas(Modifier.fillMaxSize()) { scale(0.78f) { drawMihrab(arch, IconAmber) } }
    }
}

private val IconPaper = Color(0xFFFFFFFF)
private val IconInk = Color(0xFF16160F)
private val IconAmber = Color(0xFFE3A21C)

