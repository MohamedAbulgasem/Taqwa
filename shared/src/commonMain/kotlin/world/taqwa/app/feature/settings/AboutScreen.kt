package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.about.AboutLinks
import world.taqwa.app.crash.ReportMail
import world.taqwa.app.crash.crashLogStore
import world.taqwa.app.crash.deviceInfoOrUnknown
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.LocalTaqwaDark
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.contentWidth
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.design.components.drawCloudDown
import world.taqwa.app.design.components.drawNoEntry
import world.taqwa.app.design.components.drawPhone
import world.taqwa.app.design.components.drawShare
import world.taqwa.app.design.components.drawStar
import world.taqwa.app.feature.onboarding.drawMihrab
import world.taqwa.app.i18n.isLatinScriptUi
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_icon_open_website
import world.taqwa.app.resources.about_licence
import world.taqwa.app.resources.about_licence_value
import world.taqwa.app.resources.about_nothing_body
import world.taqwa.app.resources.about_nothing_title
import world.taqwa.app.resources.about_online_body
import world.taqwa.app.resources.about_online_title
import world.taqwa.app.resources.about_privacy_policy
import world.taqwa.app.resources.about_report_problem
import world.taqwa.app.resources.about_source
import world.taqwa.app.resources.about_source_value
import world.taqwa.app.resources.about_stays_body
import world.taqwa.app.resources.about_stays_title
import world.taqwa.app.resources.about_tagline
import world.taqwa.app.resources.about_version_chip
import world.taqwa.app.resources.about_website
import world.taqwa.app.resources.about_website_value
import world.taqwa.app.resources.crash_mail_no_report
import world.taqwa.app.resources.settings_about
import world.taqwa.app.resources.settings_attribution
import world.taqwa.app.resources.settings_rate
import world.taqwa.app.resources.settings_share
import world.taqwa.app.resources.settings_share_message
import world.taqwa.app.resources.settings_version_value
import world.taqwa.app.share.shareText
import world.taqwa.app.widget.isIosPlatform

/**
 * Settings › About Taqwa (privacy spec §6; settings redesign spec, approved 30 Oct 2026): the app's
 * profile page. The icon, the name and the version first; then the two things a reader can do for
 * the app, rate it and share it; then what it promises and where the promise can be checked, its
 * source and its credits, and how to reach a person. The credits stay on their own screen, one
 * tap away, and are not repeated here.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenAttribution: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val uriHandler = LocalUriHandler.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    val languageCode = uiLanguage().code
    val website = AboutLinks.website(languageCode)
    val policy = AboutLinks.privacyPolicy(languageCode)
    val noReportLine = stringResource(Res.string.crash_mail_no_report)
    // Rate and Share read what they need from the composition and the platform: nothing about
    // them is state the caller holds, so nothing is passed in for them.
    val shareLine = stringResource(Res.string.settings_share_message)
    // A device with no browser fails silently rather than crashing (spec §6.3).
    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    AboutFrame(stringResource(Res.string.settings_about), onBack) {
        AboutHero(onOpenWebsite = { open(website) })

        // Rating and sharing are buttons the reader comes to, never a prompt that comes to the
        // reader: an in-app review prompt breaks the premise of a quiet app. So there is no review
        // API, no launch counter deciding when to ask, and nothing stored; until one of these two
        // buttons is tapped, nothing happens (privacy spec §6.5).
        ActionPills(Modifier.padding(start = SettingsGutter, end = SettingsGutter, top = 24.dp)) {
            // The platform's own store, at its page for Taqwa: the first of the rate links that
            // something on the phone answers, so a phone without the store app gets the web listing.
            ActionPill(
                stringResource(Res.string.settings_rate),
                glyph = { drawStar(it) },
                onClick = { uriHandler.openFirst(AboutLinks.rateLinks(isIosPlatform)) },
            )
            // The share glyph is a tray with an arrow rising out of it, the same in both reading
            // directions, so unlike the link glyph it is not mirrored.
            ActionPill(
                stringResource(Res.string.settings_share),
                glyph = { drawShare(it) },
                onClick = { shareText(AboutLinks.shareMessage(shareLine, languageCode)) },
            )
        }

        AboutGroups.forEach { group ->
            Spacer(Modifier.height(AboutGroupGap))
            SectionLabel(stringResource(group.label))
            SettingsCard {
                // The promises head the card that holds the policy, which spells them out.
                if (AboutRow.PRIVACY_POLICY in group.rows) {
                    Promises()
                    CardDivider()
                }
                group.rows.forEachIndexed { index, row ->
                    if (index > 0) CardDivider()
                    when (row) {
                        AboutRow.PRIVACY_POLICY ->
                            LinkRow(stringResource(Res.string.about_privacy_policy), null, forward) { open(policy) }
                        AboutRow.SOURCE ->
                            LinkRow(stringResource(Res.string.about_source), stringResource(Res.string.about_source_value), forward) {
                                open(AboutLinks.SOURCE)
                            }
                        AboutRow.LICENCE ->
                            LinkRow(stringResource(Res.string.about_licence), stringResource(Res.string.about_licence_value), forward) {
                                open(AboutLinks.LICENCE)
                            }
                        // The one row here that stays in the app, so the disclosure chevron rather
                        // than the external-link glyph: the trailing glyphs tell the reader which
                        // rows leave Taqwa before the tap.
                        AboutRow.ATTRIBUTION -> TaqwaRow(
                            stringResource(Res.string.settings_attribution),
                            onClick = onOpenAttribution,
                            trailing = {
                                Canvas(Modifier.size(16.dp)) { drawChevron(colors.textTertiary, pointsForward = forward) }
                            },
                        )
                        // The support email (crash spec §3.2), carrying the last crash report if there is one.
                        AboutRow.REPORT -> LinkRow(stringResource(Res.string.about_report_problem), null, forward) {
                            val info = deviceInfoOrUnknown()
                            val body = ReportMail.body(info, crashLogStore.read(), noReportLine)
                            open(ReportMail.mailto(AboutLinks.SUPPORT_EMAIL, ReportMail.subject(info), body))
                        }
                        AboutRow.WEBSITE ->
                            LinkRow(stringResource(Res.string.about_website), stringResource(Res.string.about_website_value), forward) {
                                open(website)
                            }
                    }
                }
            }
        }
    }
}

/** The air above each card's label: a little more than the settings root's, as the canvas has it. */
private val AboutGroupGap = 32.dp

/**
 * [SettingsScaffold]'s frame without its large title, because the hero's name is this screen's
 * heading: the same background, insets, width cap, scroll and back chevron, so About sits exactly
 * where every other settings screen does. Its own frame rather than an option on the scaffold,
 * whose signature other work builds on. With no visible title, the pane carries [title] for
 * TalkBack and VoiceOver, so the screen is still named "About Taqwa" when it opens.
 */
@Composable
private fun AboutFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .semantics { paneTitle = title }
            // safeDrawing, as the scaffold has it: held sideways, the navigation bar and the camera
            // cutout move to the edges, and only safeDrawing reports those horizontal insets.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        // Full-bleed scroller, capped column, as in the scaffold.
        Column(Modifier.contentWidth()) {
            BackChevron(onBack)
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** The icon, the name, the tagline and the version, centred, as an app's page in a store has them. */
@Composable
private fun AboutHero(onOpenWebsite: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val pill = RoundedCornerShape(percent = 50)
    Column(
        Modifier.fillMaxWidth().padding(start = SettingsGutter, end = SettingsGutter, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The icon is a shortcut to the site; the Website row below is the visible way in.
        AppIconTile(stringResource(Res.string.about_icon_open_website), size = 88.dp, onClick = onOpenWebsite)
        Spacer(Modifier.height(16.dp))
        // The product name, in Latin script in every locale, as on the icon. A heading, and the
        // screen's first: with no title above it, heading navigation starts here.
        Text(
            "Taqwa",
            style = TaqwaText.screenTitle.copy(fontSize = 30.sp),
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.about_tagline),
            style = TaqwaText.caption.copy(fontSize = 16.sp),
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.about_version_chip, stringResource(Res.string.settings_version_value)),
            // Tracked like a label in Latin script only, for the reason TaqwaText.forScript gives.
            style = TaqwaText.forScript(
                TaqwaText.Latin.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
                arabic = !isLatinScriptUi(),
            ),
            color = colors.textSecondary,
            modifier = Modifier
                .clip(pill)
                .background(colors.surface)
                .border(1.dp, colors.hairline, pill)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The space between the two pills, across the row or down the column. */
private val PillGap = 12.dp

/**
 * The two pills under the hero, laid out by hand so each label stays on one line beside its glyph,
 * as the canvas has them: side by side in two equal halves when both labels fit half the row on one
 * line, otherwise one above the other at the full width, where every label in the seven languages
 * fits on one line at the usual font sizes. Only a label too long even for the whole row, at the
 * largest font sizes, wraps, and its pill grows with it. Rate stays first, at the start of the row
 * or on top.
 */
@Composable
private fun ActionPills(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = PillGap.roundToPx()
        val half = (width - gap) / 2
        val sideBySide = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half }
        if (sideBySide) {
            // Both as tall as the taller, so the row reads as one pair.
            val height = measurables.maxOf { it.minIntrinsicHeight(half) }
            val placeables = measurables.map { it.measure(Constraints.fixed(half, height)) }
            layout(width, height) {
                placeables.forEachIndexed { i, pill -> pill.placeRelative(i * (width - half), 0) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            layout(width, placeables.sumOf { it.height } + gap * (placeables.size - 1)) {
                var y = 0
                placeables.forEach { pill ->
                    pill.placeRelative(0, y)
                    y += pill.height + gap
                }
            }
        }
    }
}

/**
 * One of the two buttons under the hero: an outlined pill on the card colour, its accent glyph
 * before its words, the two centred together. A button to TalkBack and VoiceOver, named by those
 * words, which say the action; the glyph is drawn and has nothing to add. A label that has to wrap
 * ([ActionPills] avoids it wherever one line fits) starts its lines beside the glyph rather than
 * centring them away from it, and never ellipsises.
 */
@Composable
private fun ActionPill(
    label: String,
    glyph: DrawScope.(Color) -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) { glyph(colors.accent) }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = TaqwaText.rowLabel.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            textAlign = TextAlign.Start,
        )
    }
}

/** The privacy card's three promises, each with its mark (privacy spec §6). */
@Composable
private fun Promises() {
    PromiseRow(
        stringResource(Res.string.about_stays_title),
        stringResource(Res.string.about_stays_body),
        mark = { drawPhone(it) },
    )
    CardDivider()
    // TODO(watch-companion merge): aboutOnlineBody(isIosPlatform)
    PromiseRow(
        stringResource(Res.string.about_online_title),
        stringResource(Res.string.about_online_body),
        mark = { drawCloudDown(it) },
    )
    CardDivider()
    PromiseRow(
        stringResource(Res.string.about_nothing_title),
        stringResource(Res.string.about_nothing_body),
        mark = { drawNoEntry(it) },
    )
}

/**
 * A promise: its mark in a rounded square of the accent at a low alpha, then its title and the
 * sentence that makes it concrete. Read as one item, title then sentence; the mark is decoration
 * the words already carry.
 */
@Composable
private fun PromiseRow(title: String, body: String, mark: DrawScope.(Color) -> Unit) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = PromiseMarkTint)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) { mark(colors.accent) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TaqwaText.rowLabel.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(body, style = TaqwaText.caption, color = colors.textSecondary)
        }
    }
}

/** The promise marks' square: the accent faint enough to read as a tint of the card in either theme. */
private const val PromiseMarkTint = 0.12f

/**
 * The app icon as it sits on the home screen: a rounded tile with the mihrab in the icon's own
 * colours — a white tile with the near-black arch in light, and the same two swapped in dark,
 * the amber point unchanged. Literal colours on purpose: they are the launcher asset's, not the
 * theme's, and the tile has to read as the icon rather than as one more surface. A hairline
 * keeps its edge on a card of nearly the same colour in either theme. Tapping it opens the
 * website: the clip comes before the click so the ripple keeps the tile's corners, and the
 * description names the destination for screen readers, since the tile has no text of its own.
 *
 * [size] is the tile's side; its corners are a quarter of it at any size, as the 56 dp tile's
 * 14 dp were, so the hero's larger tile is the same shape.
 */
@Composable
private fun AppIconTile(contentDescription: String, size: Dp, onClick: () -> Unit) {
    val dark = LocalTaqwaDark.current
    val hairline = LocalTaqwaColors.current.hairline
    val tile = if (dark) IconInk else IconPaper
    val arch = if (dark) IconPaper else IconInk
    val shape = RoundedCornerShape(size / 4)
    Box(
        Modifier
            .size(size)
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
