package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.about.AboutLinks
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.notifications.canOpenAppLanguageSettings
import world.taqwa.app.notifications.openAppLanguageSettings
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.contentWidth
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.drawExternalLink
import world.taqwa.app.design.components.drawShare
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.settings_about
import world.taqwa.app.resources.settings_appearance
import world.taqwa.app.resources.settings_attribution
import world.taqwa.app.resources.settings_group_about
import world.taqwa.app.resources.settings_group_app
import world.taqwa.app.resources.settings_group_prayer
import world.taqwa.app.resources.settings_group_quran
import world.taqwa.app.resources.settings_recitation
import world.taqwa.app.resources.settings_language
import world.taqwa.app.resources.settings_location
import world.taqwa.app.resources.settings_not_set
import world.taqwa.app.resources.settings_notifications
import world.taqwa.app.resources.settings_notifications_off
import world.taqwa.app.resources.settings_notifications_on_count
import world.taqwa.app.resources.settings_prayer_times
import world.taqwa.app.resources.settings_rate
import world.taqwa.app.resources.settings_share
import world.taqwa.app.resources.settings_share_message
import world.taqwa.app.resources.settings_title
import world.taqwa.app.resources.settings_version_value
import world.taqwa.app.resources.language_name
import world.taqwa.app.share.shareText
import world.taqwa.app.widget.isIosPlatform
import world.taqwa.app.resources.nav_a11y_back

/** The horizontal inset every settings screen shares with the Prayer screen's cards. */
internal val SettingsGutter = 24.dp

/**
 * Back chevron, large title, scrolling body. Every settings screen is this shape, so it lives in
 * one place rather than being re-typed seven times with drifting paddings.
 *
 * [onBack] is null on the settings root, which is a tab root: there is nowhere above it, so it
 * shows a short spacer instead of the chevron rather than reserving the chevron's full height. A
 * tab root is reached by switching tabs, not by pushing, so the title it must line up with is the
 * Prayer tab's — the sub-screens it pushes are allowed to sit their titles lower.
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // safeDrawing, not systemBars: held sideways the navigation bar and the camera cutout
            // move to the edges, and only safeDrawing reports those horizontal insets.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        // The scroller stays full-bleed so the background reaches both edges; only the column of
        // chrome and cards inside it is capped, chevron and title included — a title pinned to
        // the far left of a 900 dp landscape screen would sit half a hand away from its cards.
        Column(Modifier.contentWidth()) {
            if (onBack == null) {
                // With the title's own 4 dp on top, this puts the title `TabRootTitleTop` below the
                // status bar, level with the Prayer and Quran titles — the tabs the user switches
                // between.
                Spacer(Modifier.height(20.dp))
            } else {
                BackChevron(onBack)
            }
            Text(
                title,
                style = TaqwaText.screenTitle,
                color = colors.textPrimary,
                // Headings, so TalkBack's heading navigation and the VoiceOver rotor can jump from
                // section to section instead of walking every row (audit sr-headings).
                modifier = Modifier
                    .padding(start = SettingsGutter, end = SettingsGutter, top = 4.dp)
                    .semantics { heading() },
            )
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * Pushed screens only; a tab root has nothing above it. Shared with the Qibla screen, which is
 * pushed from the Prayer screen's card and needs the same way back.
 */
@Composable
internal fun BackChevron(onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val back = stringResource(Res.string.nav_a11y_back)
    Box(
        Modifier
            .padding(start = 8.dp, top = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack)
            // A drawn chevron has no words of its own, and it is the first thing a screen reader
            // meets on every pushed screen: "Back", not "Unlabelled".
            .semantics { contentDescription = back },
        contentAlignment = Alignment.Center,
    ) {
        // Drawn from literal coordinates, so unlike every `Row` and `padding(start=)` on these
        // screens it does not mirror itself: under Arabic "back" is to the right, and a chevron
        // still pointing left would send the eye the wrong way.
        val pointsLeft = LocalLayoutDirection.current == LayoutDirection.Ltr
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            fun x(fraction: Float) = if (pointsLeft) w * fraction else w * (1f - fraction)
            val path = Path().apply {
                moveTo(x(0.62f), w * 0.14f)
                lineTo(x(0.30f), w * 0.50f)
                lineTo(x(0.62f), w * 0.86f)
            }
            drawPath(
                path = path,
                color = colors.textPrimary,
                style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String, gutter: Dp = SettingsGutter) {
    Text(
        text,
        style = TaqwaText.sectionLabel,
        color = LocalTaqwaColors.current.textTertiary,
        modifier = Modifier.padding(start = gutter + 4.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Body copy beneath a card — privacy notes, rule explanations, live previews. */
@Composable
internal fun SettingsNote(text: String, gutter: Dp = SettingsGutter) {
    Text(
        text,
        style = TaqwaText.caption,
        color = LocalTaqwaColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = gutter + 4.dp),
    )
}

@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    TaqwaCard(modifier.padding(horizontal = SettingsGutter), content = content)
}

/**
 * A row that leaves the app: the external-link glyph in the trailing slot says so before the tap.
 * About's links and the root's Rate row share it, so a row that opens a browser or a store looks
 * the same wherever it sits.
 */
@Composable
internal fun LinkRow(label: String, value: String?, forward: Boolean, onClick: () -> Unit) {
    val tint = LocalTaqwaColors.current.textTertiary
    TaqwaRow(
        label,
        value = value,
        onClick = onClick,
        trailing = { Canvas(Modifier.size(16.dp)) { drawExternalLink(tint, forward) } },
    )
}

/**
 * Opens the first of [links] that something on the phone answers. Compose's Android handler
 * throws when no app takes a link, which is how a phone without the Play Store falls through to
 * the web listing; a phone with nothing for any of them stays where it is, as About's links do
 * (privacy spec §6.3).
 */
private fun UriHandler.openFirst(links: List<String>) {
    for (link in links) if (runCatching { openUri(link) }.isSuccess) return
}

@Composable
private fun GroupGap() = Spacer(Modifier.fillMaxWidth().height(28.dp))

@Composable
fun SettingsRootScreen(
    cityName: String?,
    methodName: String,
    themeName: String,
    notificationSettings: NotificationSettings,
    onOpenLocation: () -> Unit,
    onOpenPrayerTimes: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAttribution: () -> Unit,
    /** The voice the reader listens in, for the Recitation row's value; blank until the
     * catalogue has loaded, which is a frame. */
    reciterName: String,
    onOpenRecitation: () -> Unit,
) {
    // "Off" once the master toggle is off; otherwise how many of the five obligatory prayers
    // still carry a sound, so the row means something before the screen behind it is even open.
    val format = LocalPlatformFormat.current
    val notificationValue = if (!notificationSettings.enabled) {
        stringResource(Res.string.settings_notifications_off)
    } else {
        val on = ObligatoryPrayers.count { notificationSettings.soundFor(it) != PrayerSound.SILENT }
        stringResource(Res.string.settings_notifications_on_count, format.localizedDigits(on))
    }
    // The Rate and Share rows read what they need from the composition and the platform: nothing
    // about them is state the caller holds, so nothing is passed in for them.
    val colors = LocalTaqwaColors.current
    val uriHandler = LocalUriHandler.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    val shareLine = stringResource(Res.string.settings_share_message)
    val languageCode = uiLanguage().code

    SettingsScaffold(stringResource(Res.string.settings_title), onBack = null) {
        SectionLabel(stringResource(Res.string.settings_group_prayer))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.settings_location),
                value = cityName ?: stringResource(Res.string.settings_not_set),
                onClick = onOpenLocation,
            )
            CardDivider()
            TaqwaRow(
                stringResource(Res.string.settings_prayer_times),
                value = methodName,
                onClick = onOpenPrayerTimes,
            )
            CardDivider()
            TaqwaRow(
                stringResource(Res.string.settings_notifications),
                value = notificationValue,
                onClick = onOpenNotifications,
            )
        }

        GroupGap()
        // Quran sits between Prayer and App: it is a section about content, like Prayer above it,
        // and not about the app itself. One row today; the reading settings that would join it
        // live in the reader's own sheet, where they are next to the text they change.
        SectionLabel(stringResource(Res.string.settings_group_quran))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.settings_recitation),
                value = reciterName,
                onClick = onOpenRecitation,
            )
        }

        GroupGap()
        SectionLabel(stringResource(Res.string.settings_group_app))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.settings_appearance),
                value = themeName,
                onClick = onOpenAppearance,
            )
            CardDivider()
            // The language is the device's, not a setting of ours — `language_name` is each
            // translation naming itself. Where the system has a per-app language page (Android
            // 13 and later, iOS) the row opens it, so a reader on an English phone can put
            // Taqwa in Urdu without knowing where that page lives; older Android only states it.
            TaqwaRow(
                stringResource(Res.string.settings_language),
                value = stringResource(Res.string.language_name),
                onClick = if (canOpenAppLanguageSettings()) ({ openAppLanguageSettings() }) else null,
            )
        }

        GroupGap()
        SectionLabel(stringResource(Res.string.settings_group_about))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.settings_about),
                value = stringResource(Res.string.settings_version_value),
                onClick = onOpenAbout,
            )
            CardDivider()
            TaqwaRow(stringResource(Res.string.settings_attribution), onClick = onOpenAttribution)
            // Rating and sharing are rows the reader comes to, never a prompt that comes to the
            // reader: an in-app review prompt breaks the premise of a quiet app. So there is no
            // review API, no launch counter deciding when to ask, and nothing stored; until one
            // of these two rows is tapped, nothing happens (privacy spec §6.5).
            CardDivider()
            // The value names the store the row opens. Its glyph is the external link's, because
            // the tap leaves the app for the store's own page.
            LinkRow(stringResource(Res.string.settings_rate), AboutLinks.storeName(isIosPlatform), forward) {
                uriHandler.openFirst(AboutLinks.rateLinks(isIosPlatform))
            }
            CardDivider()
            // The share glyph is a tray with an arrow rising out of it, the same in both reading
            // directions, so unlike the link glyph it is not mirrored.
            TaqwaRow(
                stringResource(Res.string.settings_share),
                onClick = { shareText(AboutLinks.shareMessage(shareLine, languageCode)) },
                trailing = { Canvas(Modifier.size(16.dp)) { drawShare(colors.textTertiary) } },
            )
        }
    }
}
