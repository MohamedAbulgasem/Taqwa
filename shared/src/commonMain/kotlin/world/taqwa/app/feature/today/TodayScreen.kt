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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CountdownRing
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.i18n.CountdownFormatter
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.onboarding_location_body
import world.taqwa.app.resources.today_allow_location
import world.taqwa.app.resources.today_choose_city
import world.taqwa.app.resources.today_current_location
import world.taqwa.app.resources.today_latitude_label
import world.taqwa.app.resources.today_next_in
import world.taqwa.app.resources.today_no_location_title
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
    val format = LocalPlatformFormat.current
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
                    state.location.cityName ?: stringResource(Res.string.today_current_location),
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
                label = stringResource(
                    Res.string.today_next_in,
                    localizedPrayerName(state.today.next.prayer),
                ),
                countdown = CountdownFormatter.countdown(
                    state.today.countdown,
                    format,
                    CountdownFormatter.ARABIC_INDIC_DIGITS_VERIFIED_TABULAR,
                ),
                clockTime = formatClock(state.today.next.instant, zone, format),
            )
        }
        Spacer(Modifier.height(28.dp))

        PrayerTimeline(state.today.rows) { row: TimelineRow -> formatClock(row.instant, zone, format) }

        if (state.highLatitudeNote != null) {
            Spacer(Modifier.height(20.dp))
            TaqwaCard(Modifier.padding(horizontal = 24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.today_latitude_label),
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
        Text(
            stringResource(Res.string.today_no_location_title),
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            // The same sentence onboarding used to ask the question, because it is the same
            // question — a user who declined there is reading it a second time, not a new one.
            stringResource(Res.string.onboarding_location_body),
            style = TaqwaText.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        TaqwaPrimaryButton(stringResource(Res.string.today_choose_city), onChooseCity)
        Spacer(Modifier.height(4.dp))
        TaqwaTextLink(stringResource(Res.string.today_allow_location), onAllowLocation)
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

/**
 * A clock time in the location's zone and the device locale's own digits — "3:42" for a Libyan
 * reader, "٣:٤٢" for an Egyptian one, with no setting between them (spec §4.2). Unlike the
 * countdown, a clock time never falls back to Western digits.
 */
internal fun formatClock(instant: Instant, zone: TimeZone, format: PlatformFormat): String {
    val t = instant.toLocalDateTime(zone)
    return format.clockTime(t.hour, t.minute)
}
