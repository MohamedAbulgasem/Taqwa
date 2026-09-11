package world.taqwa.app.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's one ring: a hairline track with an accent arc filling clockwise from the top, drawn
 * by the Prayer screen's countdown and by the tasbeeh's counter so the two are the same object
 * seen twice. Extracted from [CountdownRing] when the tasbeeh needed the identical track, stroke
 * and cap — a second hand-drawn ring would have drifted from this one on the first tweak.
 *
 * [diameter] is the ring's own size; the stroke keeps its share of it (9 dp at 196 dp), so a
 * shrunken ring reads as the same ring rather than as a thicker one.
 *
 * [ticks] are fractions of the whole ring, 0..1, at which a 1 dp mark in the *track* colour
 * crosses the stroke. Empty for a single-part counter and for the countdown. The mark is
 * invisible against the track it matches and appears only once the arc has swept past it, which
 * is exactly what it is for: on the post-prayer set the notches at 33 % and 66 % show which parts
 * are behind you without adding a colour the ring does not already have.
 */
@Composable
fun RingArc(
    progress: Float,
    diameter: Dp,
    modifier: Modifier = Modifier,
    ticks: List<Float> = emptyList(),
) {
    val colors = LocalTaqwaColors.current
    // `drawArc` takes literal angles, so this is one of the few things in the app that does not
    // mirror itself with the layout direction. Time is read the way text is read: an Arabic
    // reader's clock hand sweeps from the top towards the left, so the arc fills anticlockwise.
    val sweepSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Canvas(modifier.size(diameter)) {
        val stroke = (diameter * (9f / 196f)).toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = colors.hairline,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = colors.ring,
            startAngle = -90f,
            sweepAngle = sweepSign * 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (ticks.isEmpty()) return@Canvas
        val radius = (size.width - stroke) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val tick = 1.dp.toPx()
        ticks.forEach { fraction ->
            // The same -90° origin and the same mirrored sweep as the arc, so a tick sits under
            // the point of the arc that reaches it in either direction.
            val degrees = -90f + sweepSign * 360f * fraction.coerceIn(0f, 1f)
            val radians = degrees * PI.toFloat() / 180f
            val unit = Offset(cos(radians), sin(radians))
            drawLine(
                color = colors.hairline,
                start = centre + unit * (radius - stroke / 2f),
                end = centre + unit * (radius + stroke / 2f),
                strokeWidth = tick,
                cap = StrokeCap.Butt,
            )
        }
    }
}
