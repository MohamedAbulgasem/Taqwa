package world.taqwa.app.feature.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** `kotlin.math` has no `toRadians` — that is `java.lang.Math`, JVM-only and unavailable from
 * commonMain on Kotlin/Native. */
private fun Double.toRadians(): Double = this * PI / 180.0

/**
 * The dial rotates under a needle that always points straight up — the same convention every
 * compass app uses — with the Kaaba marker fixed at [bearingDegrees] on the rim. [dimmed] is the
 * low-accuracy state: the whole dial fades to 28% and points at nothing, because "I don't know"
 * is the correct behaviour there.
 */
@Composable
fun QiblaDial(
    headingDegrees: Double,
    bearingDegrees: Double,
    aligned: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTaqwaColors.current
    Box(modifier.size(260.dp).alpha(if (dimmed) 0.28f else 1f)) {
        Canvas(Modifier.size(260.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            for (tick in 0 until 12) {
                val angle = ((tick * 30.0) - headingDegrees - 90.0).toRadians()
                val outer = Offset(
                    center.x + (radius - 4.dp.toPx()) * cos(angle).toFloat(),
                    center.y + (radius - 4.dp.toPx()) * sin(angle).toFloat(),
                )
                val inner = Offset(
                    center.x + (radius - 14.dp.toPx()) * cos(angle).toFloat(),
                    center.y + (radius - 14.dp.toPx()) * sin(angle).toFloat(),
                )
                drawLine(colors.hairline, inner, outer, strokeWidth = 2.dp.toPx())
            }

            val markerAngle = (bearingDegrees - headingDegrees - 90.0).toRadians()
            val markerCenter = Offset(
                center.x + (radius - 20.dp.toPx()) * cos(markerAngle).toFloat(),
                center.y + (radius - 20.dp.toPx()) * sin(markerAngle).toFloat(),
            )
            drawCircle(
                color = if (aligned) colors.accent else colors.textSecondary,
                radius = 8.dp.toPx(),
                center = markerCenter,
            )

            drawLine(
                color = colors.accent,
                start = center,
                end = Offset(center.x, center.y - radius + 30.dp.toPx()),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )

            if (aligned) {
                drawCircle(
                    color = colors.accent,
                    radius = radius - 2.dp.toPx(),
                    center = center,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }
    }
}
