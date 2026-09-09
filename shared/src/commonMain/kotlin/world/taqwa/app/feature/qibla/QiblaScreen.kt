package world.taqwa.app.feature.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.contentWidth
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.qibla_bearing
import world.taqwa.app.resources.qibla_best_effort_calibration
import world.taqwa.app.resources.qibla_best_effort_interference
import world.taqwa.app.resources.qibla_best_effort_title
import world.taqwa.app.resources.qibla_calibration_help
import world.taqwa.app.resources.qibla_distance
import world.taqwa.app.resources.qibla_facing_qibla
import world.taqwa.app.resources.qibla_hold_flat
import world.taqwa.app.resources.qibla_interference_help
import world.taqwa.app.resources.qibla_interference_title
import world.taqwa.app.resources.qibla_needs_calibrating
import world.taqwa.app.resources.qibla_no_sensor_body
import world.taqwa.app.resources.qibla_no_sensor_title
import world.taqwa.app.resources.qibla_title

/**
 * Thousands separators without `java.text` — commonMain has no number formatter — with digits run
 * through [PlatformFormat] one at a time (the same trick `CountdownFormatter.padTwo` uses) so
 * ar-EG reads Arabic-Indic and ar-LY reads Western, matching how Today formats every other number.
 */
internal fun localizedGroupedKm(km: Double, format: PlatformFormat): String {
    val digits = km.toLong().toString()
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) append(',')
            append(format.localizedDigits(c - '0'))
        }
    }
}

@Composable
fun QiblaScreen(state: QiblaUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    // The background stays full-bleed; the dial, its readout and the chevron sit in a capped,
    // centred column inside it, so held sideways the compass does not drift to one edge.
    // safeDrawing, not systemBars: sideways the navigation bar and the cutout are on the sides.
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // The chevron, title and subtitle above the dial take about 120 dp, and the readout below
        // it another 90; on a sideways phone that leaves less than the dial's full 312 dp, so the
        // dial shrinks to what is actually there rather than running off the bottom of the screen.
        val dialSize = minOf(DialSize, maxHeight - 210.dp, maxWidth - 32.dp)
        Column(
            Modifier.fillMaxSize().contentWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Pushed from the Prayer screen's Qibla card (iteration 8), so it gets the same chevron
            // every other pushed screen has, in the same place.
            Box(Modifier.fillMaxWidth()) { BackChevron(onBack) }
            Text(stringResource(Res.string.qibla_title), style = TaqwaText.screenTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            when (state) {
                QiblaUiState.NoSensor ->
                    Subtitle(stringResource(Res.string.qibla_no_sensor_title), colors.textSecondary)
                is QiblaUiState.Searching ->
                    Subtitle(stringResource(Res.string.qibla_hold_flat), colors.textSecondary)
                is QiblaUiState.Aligned ->
                    Subtitle(stringResource(Res.string.qibla_facing_qibla), colors.accent, FontWeight.SemiBold)
                // Amber, never red: a compass that wants a wiggle is not an error state. Which
                // wiggle depends on the reason — a phone sitting in a 500 µT field cannot be
                // waved back into calibration, and telling it to try is the older bug.
                is QiblaUiState.LowAccuracy -> Subtitle(
                    stringResource(
                        when (state.reason) {
                            CompassLowReason.CALIBRATION -> Res.string.qibla_needs_calibrating
                            CompassLowReason.INTERFERENCE -> Res.string.qibla_interference_title
                        },
                    ),
                    colors.accent,
                    FontWeight.SemiBold,
                )
                // Not amber: nothing here is going to be fixed by doing something, so this is a
                // statement of fact in the secondary colour rather than a call to action.
                is QiblaUiState.BestEffort ->
                    Subtitle(stringResource(Res.string.qibla_best_effort_title), colors.textSecondary)
            }

            // The dial and its readout sit as one block in the middle of what is left, rather than
            // riding at the top of a mostly empty screen.
            Spacer(Modifier.weight(1f))
            when (state) {
                QiblaUiState.NoSensor -> Text(
                    stringResource(Res.string.qibla_no_sensor_body),
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )

                is QiblaUiState.Searching -> {
                    QiblaDial(state.headingDegrees, state.bearingDegrees, aligned = false, dimmed = false, diameter = dialSize)
                    Readout(state.bearingDegrees, state.distanceKm)
                }

                is QiblaUiState.Aligned -> {
                    QiblaDial(state.headingDegrees, state.bearingDegrees, aligned = true, dimmed = false, diameter = dialSize)
                    Readout(state.bearingDegrees, state.distanceKm)
                }

                is QiblaUiState.LowAccuracy -> {
                    QiblaDial(0.0, state.bearingDegrees, aligned = false, dimmed = true, diameter = dialSize)
                    Spacer(Modifier.height(12.dp))
                    // The figure of eight is drawn only when it is the actual remedy. Under
                    // interference it would be an instruction that cannot work.
                    if (state.reason == CompassLowReason.CALIBRATION) {
                        FigureOfEight()
                        Spacer(Modifier.height(15.dp))
                    }
                    Help(
                        stringResource(
                            when (state.reason) {
                                CompassLowReason.CALIBRATION -> Res.string.qibla_calibration_help
                                CompassLowReason.INTERFERENCE -> Res.string.qibla_interference_help
                            },
                        ),
                    )
                }

                // Twenty seconds of an unusable compass. The dial stays, so the screen is still
                // the same screen, but with no needle and no marker: there is nothing to point
                // at. The bearing and the distance are arithmetic on the location and are as
                // correct here as in any other state, so they are shown — under a caveat that
                // does not go away, saying which of the two problems this device has.
                is QiblaUiState.BestEffort -> {
                    QiblaDial(
                        0.0,
                        state.bearingDegrees,
                        aligned = false,
                        dimmed = true,
                        needle = false,
                        diameter = dialSize,
                    )
                    Readout(state.bearingDegrees, state.distanceKm)
                    Spacer(Modifier.height(15.dp))
                    Help(
                        stringResource(
                            when (state.reason) {
                                CompassLowReason.CALIBRATION -> Res.string.qibla_best_effort_calibration
                                CompassLowReason.INTERFERENCE -> Res.string.qibla_best_effort_interference
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The one paragraph of body copy the non-pointing states share, so they stay identical. */
@Composable
private fun Help(text: String) {
    Text(
        text,
        style = TaqwaText.caption.copy(fontSize = 15.sp, lineHeight = 24.sp),
        color = LocalTaqwaColors.current.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 36.dp),
    )
}

@Composable
private fun Subtitle(text: String, color: Color, weight: FontWeight? = null) {
    Text(
        text,
        style = TaqwaText.caption.let { if (weight != null) it.copy(fontWeight = weight) else it },
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** The bearing is the one number worth reading across the room; the distance is context. */
@Composable
private fun Readout(bearingDegrees: Double, distanceKm: Double) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    Spacer(Modifier.height(24.dp))
    // The mockup sets the bearing at 27px against Today's 29px countdown — the same tier, which
    // in this app's type scale is `countdown` itself.
    Text(
        stringResource(Res.string.qibla_bearing, format.localizedDigits(bearingDegrees.toInt())),
        style = TaqwaText.countdown,
        color = colors.accent,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(Res.string.qibla_distance, localizedGroupedKm(distanceKm, format)),
        style = TaqwaText.caption,
        color = colors.textSecondary,
    )
}

/** Two overlapping ovals — the motion being asked for, drawn rather than described. */
@Composable
private fun FigureOfEight() {
    val accent = LocalTaqwaColors.current.accent
    Canvas(Modifier.size(width = 78.dp, height = 45.dp)) {
        val stroke = Stroke(width = 2.4.dp.toPx())
        val ovalWidth = size.width * (18f / 52f)
        val ovalHeight = size.height * (20f / 30f)
        val top = size.height * (5f / 30f)
        listOf(8f / 52f, 26f / 52f).forEach { left ->
            drawOval(
                color = accent,
                topLeft = Offset(size.width * left, top),
                size = Size(ovalWidth, ovalHeight),
                style = stroke,
            )
        }
    }
}
