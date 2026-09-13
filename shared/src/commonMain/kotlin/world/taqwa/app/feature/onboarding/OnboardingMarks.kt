package world.taqwa.app.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors

/**
 * The onboarding illustrations: one line drawing per screen, in the same hand as the app icon.
 *
 * Each is drawn in the icon's own 1024-unit space and scaled to the canvas, with the icon's
 * 88-unit stroke, round caps and joins, and a single amber element, so the three read as a set
 * and the first of them *is* the icon. Line work in [TaqwaColors.textPrimary] rather than a
 * white tile, because a tile on a dark screen is a lamp and on a light one is invisible; the
 * glyph alone sits equally well on both.
 */
private const val ICON_SPACE = 1024f
private const val ICON_STROKE = 88f

private val MarkSize = 116.dp

private fun DrawScope.unit() = size.minDimension / ICON_SPACE

private fun DrawScope.lineStroke() = Stroke(width = ICON_STROKE * unit(), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The app icon's mihrab: the arch that marks the qibla in every mosque, with the direction
 * itself as an amber point at its heart. */
@Composable
fun MihrabMark(modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Canvas(modifier.size(MarkSize)) { drawMihrab(colors.textPrimary, colors.ring) }
}

/**
 * The icon's mihrab in this canvas's own space — the same path the launcher icon is cut from,
 * so the About screen's icon tile and the onboarding mark cannot drift from it. [line] is the
 * arch, [dot] the amber point at its heart.
 */
fun DrawScope.drawMihrab(line: Color, dot: Color) {
    val u = unit()
    val arch = Path().apply {
        moveTo(292f * u, 780f * u)
        lineTo(292f * u, 520f * u)
        cubicTo(292f * u, 372f * u, 380f * u, 268f * u, 512f * u, 216f * u)
        cubicTo(644f * u, 268f * u, 732f * u, 372f * u, 732f * u, 520f * u)
        lineTo(732f * u, 780f * u)
    }
    drawPath(arch, line, style = lineStroke())
    drawCircle(dot, radius = 46f * u, center = Offset(512f * u, 392f * u))
}

/** A map pin: the circle of a position with a point beneath it, and the position itself in
 * amber. Says "where" without a map, which would have to be somewhere in particular. */
@Composable
fun PinMark(modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Canvas(modifier.size(MarkSize)) {
        val u = unit()
        val centre = Offset(512f * u, 430f * u)
        val radius = 240f * u
        // The two tangents from the tip meet the circle 57 degrees either side of straight down.
        val pin = Path().apply {
            arcTo(
                rect = Rect(centre - Offset(radius, radius), Size(radius * 2, radius * 2)),
                startAngleDegrees = 33f,
                sweepAngleDegrees = -246f,
                forceMoveTo = true,
            )
            lineTo(512f * u, 870f * u)
            close()
        }
        drawPath(pin, colors.textPrimary, style = lineStroke())
        drawCircle(colors.ring, radius = 70f * u, center = centre)
    }
}

/** A bell, its clapper in amber: the sound is the point of the screen. */
@Composable
fun BellMark(modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Canvas(modifier.size(MarkSize)) {
        val u = unit()
        val body = Path().apply {
            moveTo(312f * u, 640f * u)
            lineTo(312f * u, 470f * u)
            arcTo(
                rect = Rect(Offset(312f * u, 270f * u), Size(400f * u, 400f * u)),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(712f * u, 640f * u)
            lineTo(776f * u, 724f * u)
            lineTo(248f * u, 724f * u)
            close()
        }
        drawPath(body, colors.textPrimary, style = lineStroke())
        drawCircle(colors.ring, radius = 52f * u, center = Offset(512f * u, 812f * u))
    }
}

