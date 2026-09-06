package world.taqwa.app.feature.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.settings_about
import world.taqwa.app.resources.settings_appearance
import world.taqwa.app.resources.settings_attribution
import world.taqwa.app.resources.settings_group_about
import world.taqwa.app.resources.settings_group_app
import world.taqwa.app.resources.settings_group_prayer
import world.taqwa.app.resources.settings_language
import world.taqwa.app.resources.settings_location
import world.taqwa.app.resources.settings_not_set
import world.taqwa.app.resources.settings_notifications
import world.taqwa.app.resources.settings_notifications_off
import world.taqwa.app.resources.settings_notifications_on_count
import world.taqwa.app.resources.settings_prayer_times
import world.taqwa.app.resources.settings_title
import world.taqwa.app.resources.settings_version_value
import world.taqwa.app.resources.language_name

/** The horizontal inset every settings screen shares with Today's cards. */
internal val SettingsGutter = 24.dp

/**
 * Back chevron, large title, scrolling body. Every settings screen is this shape, so it lives in
 * one place rather than being re-typed seven times with drifting paddings.
 *
 * [onBack] is null on the settings root, which is a tab root: there is nowhere above it, and the
 * chevron is replaced by the space it occupied so the title does not jump between this screen and
 * the sub-screens it opens.
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
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()),
    ) {
        if (onBack == null) {
            // 8 dp of top padding plus the 44 dp tap target the chevron would have occupied.
            Spacer(Modifier.height(52.dp))
        } else {
            BackChevron(onBack)
        }
        Text(
            title,
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = SettingsGutter, end = SettingsGutter, top = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        content()
        Spacer(Modifier.height(40.dp))
    }
}

/** Sub-screens only; the settings root is a tab root and has nothing above it. */
@Composable
private fun BackChevron(onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .padding(start = 8.dp, top = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onBack),
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
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = TaqwaText.sectionLabel,
        color = LocalTaqwaColors.current.textTertiary,
        modifier = Modifier.padding(start = SettingsGutter + 4.dp, bottom = 8.dp),
    )
}

/** Body copy beneath a card — privacy notes, rule explanations, live previews. */
@Composable
internal fun SettingsNote(text: String) {
    Text(
        text,
        style = TaqwaText.caption,
        color = LocalTaqwaColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = SettingsGutter + 4.dp),
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    TaqwaCard(Modifier.padding(horizontal = SettingsGutter), content = content)
}

@Composable
private fun GroupGap() = Spacer(Modifier.fillMaxWidth().height(28.dp))

/**
 * A switch in the app's own palette. Material3's would arrive with its own colour scheme and a
 * different silhouette from everything else on these screens.
 */
@Composable
internal fun TaqwaToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalTaqwaColors.current
    val knobStart by animateDpAsState(if (checked) 22.dp else 3.dp, label = "knob")
    // Spec §92: 44 pt minimum. The switch is still drawn 48×28 — the outer box only widens what
    // a finger has to hit, so nothing on the screen moves or changes shape.
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(if (checked) colors.accent else colors.hairline),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = knobStart)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.background else colors.surface),
            )
        }
    }
}

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
    onOpenAttribution: () -> Unit,
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
        SectionLabel(stringResource(Res.string.settings_group_app))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.settings_appearance),
                value = themeName,
                onClick = onOpenAppearance,
            )
            CardDivider()
            // The language is the device's, not a setting of ours — `language_name` is each
            // translation naming itself, so this row is right without any code to pick it.
            TaqwaRow(
                stringResource(Res.string.settings_language),
                value = stringResource(Res.string.language_name),
            )
        }

        GroupGap()
        SectionLabel(stringResource(Res.string.settings_group_about))
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.settings_about),
                value = stringResource(Res.string.settings_version_value),
            )
            CardDivider()
            TaqwaRow(stringResource(Res.string.settings_attribution), onClick = onOpenAttribution)
        }
    }
}
