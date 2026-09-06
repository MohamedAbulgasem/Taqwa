package world.taqwa.app.feature.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText

/**
 * Every user-facing string on this screen, in one place: a concurrent task is moving the app onto
 * string resources, and a single object makes that a mechanical change.
 */
internal object QiblaStrings {
    const val Title = "Qibla"
    const val Back = "Today"
    const val HoldFlat = "Hold your phone flat"
    const val FacingQibla = "Facing qibla"
    const val NeedsCalibrating = "Compass needs calibrating"
    const val NoSensorTitle = "No compass sensor"
    const val NoSensorBody =
        "This device has no magnetometer. Use a physical compass together with the bearing below."
    const val CalibrationHelp =
        "Move your phone in a figure of eight a few times. Magnetic interference from metal, " +
            "cases and speakers can throw the reading off."

    fun bearing(degrees: Double): String = "${degrees.toInt()}°"

    fun distance(km: Double): String = "Makkah · ${grouped(km)} km away"
}

/** Thousands separators without `java.text` — commonMain has no number formatter. */
internal fun grouped(km: Double): String {
    val digits = km.toLong().toString()
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) append(',')
            append(c)
        }
    }
}

@Composable
fun QiblaScreen(state: QiblaUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackLink(onBack, Modifier.align(Alignment.Start))

        Text(QiblaStrings.Title, style = TaqwaText.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        when (state) {
            QiblaUiState.NoSensor -> Subtitle(QiblaStrings.NoSensorTitle, colors.textSecondary)
            is QiblaUiState.Searching -> Subtitle(QiblaStrings.HoldFlat, colors.textSecondary)
            is QiblaUiState.Aligned ->
                Subtitle(QiblaStrings.FacingQibla, colors.accent, FontWeight.SemiBold)
            // Amber, never red: a compass that wants a wiggle is not an error state.
            is QiblaUiState.LowAccuracy ->
                Subtitle(QiblaStrings.NeedsCalibrating, colors.accent, FontWeight.SemiBold)
        }

        // The dial and its readout sit as one block in the middle of what is left, rather than
        // riding at the top of a mostly empty screen.
        Spacer(Modifier.weight(1f))
        when (state) {
            QiblaUiState.NoSensor -> Text(
                QiblaStrings.NoSensorBody,
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
                    QiblaStrings.CalibrationHelp,
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
    Spacer(Modifier.height(24.dp))
    // The mockup sets the bearing at 27px against Today's 29px countdown — the same tier, which
    // in this app's type scale is `countdown` itself.
    Text(
        QiblaStrings.bearing(bearingDegrees),
        style = TaqwaText.countdown,
        color = colors.accent,
    )
    Spacer(Modifier.height(4.dp))
    Text(QiblaStrings.distance(distanceKm), style = TaqwaText.caption, color = colors.textSecondary)
}

/** A text back link rather than the settings chevron: the mockup names the destination. */
@Composable
private fun BackLink(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Row(
        modifier
            .height(44.dp)
            .clickable(onClick = onBack)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 6.dp, height = 12.dp)) {
            drawLine(
                color = colors.textSecondary,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height / 2f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.textSecondary,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            QiblaStrings.Back,
            style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            color = colors.textSecondary,
        )
    }
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
