package world.taqwa.app.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CountdownRing
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.domain.TimelineRow
import kotlin.time.Duration
import kotlin.time.Instant

@Composable
fun TodayScreen(
    state: TodayUiState,
    onOpenQibla: () -> Unit,
    onOpenSettings: () -> Unit,
    onChooseCity: () -> Unit,
    onAllowLocation: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (state) {
            TodayUiState.Loading -> Unit
            TodayUiState.NeedsLocation -> NeedsLocationBody(onChooseCity, onAllowLocation)
            is TodayUiState.Ready -> ReadyBody(state, onOpenQibla, onOpenSettings)
        }
    }
}

@Composable
private fun ReadyBody(
    state: TodayUiState.Ready,
    onOpenQibla: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val zone = rememberZone(state.location.timeZoneId)
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.location.cityName ?: "Current location",
                    style = TaqwaText.screenTitle,
                    color = colors.textPrimary,
                )
                Text(state.hijri, style = TaqwaText.caption, color = colors.textSecondary)
            }
            IconButton(onOpenQibla) { drawCompass(colors.textSecondary) }
            IconButton(onOpenSettings) { drawSliders(colors.textSecondary) }
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CountdownRing(
                progress = state.today.ringProgress,
                label = "${englishName(state.today.next.prayer)} in",
                countdown = formatCountdown(state.today.countdown),
                clockTime = formatClock(state.today.next.instant, zone),
            )
        }
        Spacer(Modifier.height(28.dp))

        PrayerTimeline(state.today.rows) { row: TimelineRow -> formatClock(row.instant, zone) }

        if (state.highLatitudeNote != null) {
            Spacer(Modifier.height(20.dp))
            TaqwaCard(Modifier.padding(horizontal = 24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "AT THIS LATITUDE",
                        style = TaqwaText.sectionLabel,
                        color = colors.accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.highLatitudeNote,
                        style = TaqwaText.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NeedsLocationBody(onChooseCity: () -> Unit, onAllowLocation: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No location yet", style = TaqwaText.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Prayer times depend on your exact position. Everything is calculated on your " +
                "device — your location never leaves your phone.",
            style = TaqwaText.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        TaqwaPrimaryButton("Choose a city", onChooseCity)
        Spacer(Modifier.height(4.dp))
        TaqwaTextLink("Allow location instead", onAllowLocation)
    }
}

@Composable
private fun IconButton(onClick: () -> Unit, draw: DrawScope.() -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { draw() }
    }
}

/** A ring with a needle. Line work only, to match [world.taqwa.app.design.components.CheckMark]. */
private fun DrawScope.drawCompass(tint: Color) {
    val w = size.width
    drawCircle(color = tint, radius = w * 0.46f, style = Stroke(width = w * 0.09f))
    // A rhombus along the north-east diagonal: tip, one flank, tail, the other flank.
    val needle = Path().apply {
        moveTo(w * 0.712f, w * 0.288f)
        lineTo(w * 0.575f, w * 0.575f)
        lineTo(w * 0.288f, w * 0.712f)
        lineTo(w * 0.425f, w * 0.425f)
        close()
    }
    drawPath(needle, tint)
}

/** Three sliders — the settings glyph, and less fussy than a gear at 22dp. */
private fun DrawScope.drawSliders(tint: Color) {
    val w = size.width
    val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
    listOf(0.24f to 0.66f, 0.5f to 0.34f, 0.76f to 0.58f).forEach { (y, knob) ->
        drawLine(
            color = tint,
            start = Offset(w * 0.1f, w * y),
            end = Offset(w * 0.9f, w * y),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawCircle(color = tint, radius = w * 0.13f, center = Offset(w * knob, w * y))
    }
}

/** `TimeZone.of` parses an IANA id; the zone only changes when the location does. */
@Composable
private fun rememberZone(id: String): TimeZone = remember(id) { TimeZone.of(id) }

internal fun formatClock(instant: Instant, zone: TimeZone): String {
    val t = instant.toLocalDateTime(zone)
    return "${t.hour}:${t.minute.toString().padStart(2, '0')}"
}

/** H:MM, per the spec — the ring counts down a duration, not a clock time. */
internal fun formatCountdown(duration: Duration): String {
    val total = if (duration.isNegative()) Duration.ZERO else duration
    val hours = total.inWholeHours
    val minutes = (total.inWholeMinutes % 60).toString().padStart(2, '0')
    return "$hours:$minutes"
}
