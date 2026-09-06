package world.taqwa.app.feature.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.qibla_bearing
import world.taqwa.app.resources.qibla_calibration_help
import world.taqwa.app.resources.qibla_distance
import world.taqwa.app.resources.qibla_facing_qibla
import world.taqwa.app.resources.qibla_hold_flat
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
fun QiblaScreen(state: QiblaUiState, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A tab root, so there is nowhere to go "back" to: the bar below is the way out. The
        // 20 dp stands in for the height the back link used to give the title.
        Spacer(Modifier.height(20.dp))
        Text(stringResource(Res.string.qibla_title), style = TaqwaText.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        when (state) {
            QiblaUiState.NoSensor ->
                Subtitle(stringResource(Res.string.qibla_no_sensor_title), colors.textSecondary)
            is QiblaUiState.Searching ->
                Subtitle(stringResource(Res.string.qibla_hold_flat), colors.textSecondary)
            is QiblaUiState.Aligned ->
                Subtitle(stringResource(Res.string.qibla_facing_qibla), colors.accent, FontWeight.SemiBold)
            // Amber, never red: a compass that wants a wiggle is not an error state.
            is QiblaUiState.LowAccuracy ->
                Subtitle(stringResource(Res.string.qibla_needs_calibrating), colors.accent, FontWeight.SemiBold)
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
                QiblaDial(state.headingDegrees, state.bearingDegrees, aligned = false, dimmed = false)
                Readout(state.bearingDegrees, state.distanceKm)
            }

            is QiblaUiState.Aligned -> {
                QiblaDial(state.headingDegrees, state.bearingDegrees, aligned = true, dimmed = false)
                Readout(state.bearingDegrees, state.distanceKm)
            }

            is QiblaUiState.LowAccuracy -> {
                QiblaDial(0.0, state.bearingDegrees, aligned = false, dimmed = true)
                Spacer(Modifier.height(12.dp))
                FigureOfEight()
                Spacer(Modifier.height(15.dp))
                Text(
                    stringResource(Res.string.qibla_calibration_help),
                    style = TaqwaText.caption.copy(fontSize = 15.sp, lineHeight = 24.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
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
