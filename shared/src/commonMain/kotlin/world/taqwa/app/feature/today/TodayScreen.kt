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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import world.taqwa.app.feature.qibla.QiblaMiniDial
import world.taqwa.app.feature.qibla.localizedGroupedKm
import world.taqwa.app.i18n.CountdownFormatter
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.onboarding_location_body
import world.taqwa.app.resources.qibla_title
import world.taqwa.app.resources.today_allow_location
import world.taqwa.app.resources.today_choose_city
import world.taqwa.app.resources.today_current_location
import world.taqwa.app.resources.today_latitude_label
import world.taqwa.app.resources.today_next_in
import world.taqwa.app.resources.today_no_location_title
import world.taqwa.app.resources.today_qibla_detail
import kotlin.time.Instant

/** The page gutter, shared with every settings screen. */
private val Gutter = 24.dp

/**
 * The Prayer tab (iteration 8; still `Today` in code, see `Tab`). City and both dates, the
 * countdown ring free on the page, the timeline in a card, and beneath it the Qibla card that
 * opens the compass — the compass stopped being a tab so that the bar could stay small.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onChooseCity: () -> Unit,
    onAllowLocation: () -> Unit,
    onOpenQibla: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (state) {
            TodayUiState.Loading -> Unit
            TodayUiState.NeedsLocation -> NeedsLocationBody(onChooseCity, onAllowLocation)
            is TodayUiState.Ready -> ReadyBody(state, onOpenQibla)
        }
    }
}

@Composable
private fun ReadyBody(state: TodayUiState.Ready, onOpenQibla: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val zone = rememberZone(state.location.timeZoneId)
    val format = LocalPlatformFormat.current
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 12.dp, bottom = 4.dp)) {
            Text(
                state.location.cityName ?: stringResource(Res.string.today_current_location),
                style = TaqwaText.screenTitle,
                color = colors.textPrimary,
            )
            // Hijri first, then the Gregorian a step quieter: one line, the two calendars read
            // as a pair. The separator is punctuation, not a translated string, so it is the
            // same in both languages; the bidi algorithm orders the halves under Arabic.
            Text(
                buildAnnotatedString {
                    append(state.hijri)
                    withStyle(SpanStyle(color = colors.textTertiary)) {
                        append(" · ")
                        append(state.gregorian)
                    }
                },
                style = TaqwaText.caption,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(44.dp))
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
        Spacer(Modifier.height(36.dp))

        TaqwaCard(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(4.dp))
            PrayerTimeline(state.today.rows, horizontalPadding = 16.dp) { row: TimelineRow ->
                formatClock(row.instant, zone, format)
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(14.dp))
        QiblaCard(state.qiblaBearingDegrees, state.qiblaDistanceKm, format, onOpenQibla)

        if (state.highLatitudeNote != null) {
            Spacer(Modifier.height(14.dp))
            TaqwaCard(Modifier.padding(horizontal = Gutter)) {
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

/**
 * The way to the compass, where the thumb rests: a still north-up dial, the bearing and the
 * distance, and a chevron. The whole card is the tap target; no ripple, like every other
 * surface in the app — the pushed screen is the feedback.
 */
@Composable
private fun QiblaCard(bearingDegrees: Double, distanceKm: Double, format: PlatformFormat, onOpen: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaCard(Modifier.padding(horizontal = Gutter)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QiblaMiniDial(bearingDegrees)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.qibla_title), style = TaqwaText.rowLabel, color = colors.textPrimary)
                Text(
                    stringResource(
                        Res.string.today_qibla_detail,
                        format.localizedDigits(bearingDegrees.toInt()),
                        localizedGroupedKm(distanceKm, format),
                    ),
                    style = TaqwaText.caption.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            ForwardChevron()
        }
    }
}

/** The settings back chevron's mirror image: "onwards", so it points the way the text runs. */
@Composable
private fun ForwardChevron() {
    val colors = LocalTaqwaColors.current
    val pointsRight = LocalLayoutDirection.current == LayoutDirection.Ltr
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        fun x(fraction: Float) = if (pointsRight) w * fraction else w * (1f - fraction)
        val path = Path().apply {
            moveTo(x(0.36f), w * 0.14f)
            lineTo(x(0.68f), w * 0.50f)
            lineTo(x(0.36f), w * 0.86f)
        }
        drawPath(
            path = path,
            color = colors.textTertiary,
            style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
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
