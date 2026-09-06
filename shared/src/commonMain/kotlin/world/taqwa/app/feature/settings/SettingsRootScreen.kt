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
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow

/** The horizontal inset every settings screen shares with Today's cards. */
internal val SettingsGutter = 24.dp

/**
 * Back chevron, large title, scrolling body. Every settings screen is this shape, so it lives in
 * one place rather than being re-typed seven times with drifting paddings.
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
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
        Box(
            Modifier
                .padding(start = 8.dp, top = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val w = size.width
                val path = Path().apply {
                    moveTo(w * 0.62f, w * 0.14f)
                    lineTo(w * 0.30f, w * 0.50f)
                    lineTo(w * 0.62f, w * 0.86f)
                }
                drawPath(
                    path = path,
                    color = colors.textPrimary,
                    style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
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
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) colors.accent else colors.hairline)
            .clickable { onCheckedChange(!checked) },
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

@Composable
fun SettingsRootScreen(
    cityName: String?,
    methodName: String,
    themeName: String,
    onBack: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenPrayerTimes: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAttribution: () -> Unit,
) {
    SettingsScaffold("Settings", onBack) {
        SectionLabel("PRAYER")
        SettingsCard {
            TaqwaRow("Location", value = cityName ?: "Not set", onClick = onOpenLocation)
            CardDivider()
            TaqwaRow("Prayer times", value = methodName, onClick = onOpenPrayerTimes)
            CardDivider()
            // Deliberately inert: Task 19 wires the notification screen. A row that opens an
            // empty screen is worse than one that says what it is waiting for.
            TaqwaRow("Notifications", value = "Coming soon")
        }

        GroupGap()
        SectionLabel("APP")
        SettingsCard {
            TaqwaRow("Appearance", value = themeName, onClick = onOpenAppearance)
            CardDivider()
            TaqwaRow("Language", value = "English")
        }

        GroupGap()
        SectionLabel("ABOUT")
        SettingsCard {
            TaqwaRow("About Taqwa", value = "Version 1.0")
            CardDivider()
            TaqwaRow("Attribution & licences", onClick = onOpenAttribution)
        }
    }
}
