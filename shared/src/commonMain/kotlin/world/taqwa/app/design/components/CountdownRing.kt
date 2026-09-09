package world.taqwa.app.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText

/** The ring's own diameter on a phone held upright — the size everything below is drawn against. */
val CountdownRingSize = 196.dp

@Composable
fun CountdownRing(
    progress: Float,
    label: String,
    countdown: String,
    clockTime: String,
    modifier: Modifier = Modifier,
    diameter: Dp = CountdownRingSize,
) {
    val colors = LocalTaqwaColors.current
    // `drawArc` takes literal angles, so this is one of the few things in the app that does not
    // mirror itself with the layout direction. Time is read the way text is read: an Arabic
    // reader's clock hand sweeps from the top towards the left, so the arc fills anticlockwise.
    val sweepSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    // [diameter] is only ever *smaller* than the default, when a sideways screen gives the ring
    // less than its own half-page to sit in; the stroke keeps its share of the diameter, so a
    // shrunken ring reads as the same ring rather than as a thicker one.
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = (diameter * (9f / 196f)).toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.hairline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.ring,
                startAngle = -90f,
                sweepAngle = sweepSign * 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.uppercase(),
                style = TaqwaText.sectionLabel.copy(fontSize = 11.sp),
                color = colors.accent,
                textAlign = TextAlign.Center,
            )
            // 36sp is the largest size at which the longest string a ring can show, "10:00:00" in
            // tabular Manrope Light, still clears the stroke on both sides (149dp inside 178dp);
            // scaled with the diameter so a landscape-shrunken ring keeps the same proportions.
            Text(
                text = countdown,
                style = TaqwaText.countdown.copy(fontSize = 36.sp * (diameter / CountdownRingSize)),
                color = colors.textPrimary,
            )
            Text(text = clockTime, style = TaqwaText.caption, color = colors.textSecondary)
        }
    }
}
