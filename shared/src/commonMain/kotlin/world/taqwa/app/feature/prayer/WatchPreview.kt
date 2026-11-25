package world.taqwa.app.feature.prayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.DarkColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.LocalTaqwaDark
import world.taqwa.app.design.TaqwaColors
import world.taqwa.app.design.WatchDrawing

/** The drawing's side: the Qibla card's dial's. */
internal val WatchPreviewSize = 52.dp

/**
 * The user's own round watch wearing the Taqwa face (Option B), in the Qibla dial's 52 dp place on
 * Home's watch card. It tells the step with no words: [WatchLook.GHOST], a dashed outline with the
 * face in ghost ink, before Taqwa is installed; [WatchLook.FAINT], the real dark watch with the face
 * faint on its black screen, before the face is added; and a thin amber ring round it while the
 * watch answers a tap ([ring]). Decorative: the card's words say all of it to a screen reader.
 */
@Composable
internal fun WatchPreview(look: WatchLook, ring: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    val dark = LocalTaqwaDark.current
    Canvas(modifier.size(WatchPreviewSize)) { drawWatchPreview(look, ring, colors, dark) }
}

/**
 * [WatchPreview]'s drawing, in absolute coordinates and never mirrored: it is a picture of a real
 * object, whose pushers are on its right in any language. Units: `u`, a 52nd of the side (1 dp at
 * 52 dp). The face is drawn in its own 200-unit square, scaled to the screen's circle and clipped
 * to it; its date, time and countdown are rounded bars, never digits, so Home shows no made-up time
 * beside its live countdown, and at this size the face's words would be 3 px tall anyway.
 */
internal fun DrawScope.drawWatchPreview(look: WatchLook, ring: Boolean, colors: TaqwaColors, dark: Boolean) {
    val u = size.width / 52f
    val centre = Offset(25f * u, 26f * u)
    val ghostStroke = colors.textTertiary.copy(alpha = 0.6f).compositeOver(colors.surface)
    when (look) {
        WatchLook.GHOST -> {
            for ((top, height) in Pushers) {
                drawRoundRect(
                    ghostStroke,
                    topLeft = Offset(47.2f * u, top * u),
                    size = Size(2.6f * u, height * u),
                    cornerRadius = CornerRadius(1.2f * u),
                    style = Stroke(width = 1f * u),
                )
            }
            drawCircle(colors.background, radius = 23.2f * u, center = centre)
            drawCircle(
                ghostStroke,
                radius = 23.2f * u,
                center = centre,
                style = Stroke(width = 1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f * u, 2.2f * u))),
            )
            drawFace(u, centre) {
                val ink = colors.textTertiary.copy(alpha = 0.45f).compositeOver(colors.surface)
                drawPath(archPath(), ink, style = Stroke(width = 4f))
                drawTime(ink)
                bar(ink, 62f, 138f, 142f, 151f, radius = 4.5f)
            }
        }
        WatchLook.FAINT -> {
            for ((top, height) in Pushers) {
                drawRoundRect(
                    WatchDrawing.case,
                    topLeft = Offset(46.6f * u, top * u),
                    size = Size(3f * u, height * u),
                    cornerRadius = CornerRadius(1.3f * u),
                )
            }
            drawCircle(WatchDrawing.case, radius = 23.2f * u, center = centre)
            // In dark the case would melt into the card: a hairline of the ghost's grey holds it.
            if (dark) drawCircle(ghostStroke, radius = 23.2f * u, center = centre, style = Stroke(width = 0.8f * u))
            drawCircle(WatchDrawing.screen, radius = ScreenRadius * u, center = centre)
            drawFace(u, centre) {
                // The face is dark on every watch: its own ink, whatever the phone's theme.
                fun faint(c: Color) = c.copy(alpha = c.alpha * FaintAlpha)
                val ink = faint(DarkColors.textPrimary)
                val secondary = faint(DarkColors.textSecondary)
                val amber = faint(DarkColors.accent)
                drawPath(archPath(), faint(WatchDrawing.arch), style = Stroke(width = 3f))
                drawPath(
                    Path().apply {
                        moveTo(70f, 28f)
                        lineTo(100f, 10f)
                        lineTo(130f, 28f)
                    },
                    amber,
                    style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawCircle(amber, radius = 6f, center = Offset(100f, 34f))
                bar(secondary, 66f, 134f, 51f, 57f)
                bar(ink, 70f, 130f, 66f, 75f)
                drawTime(ink)
                bar(amber, 62f, 112f, 142f, 152f)
                bar(ink, 120f, 140f, 142f, 152f)
                bar(secondary, 72f, 128f, 162f, 168f)
            }
        }
    }
    if (ring) drawCircle(colors.accent, radius = 25.4f * u, center = centre, style = Stroke(width = 1.3f * u))
}

/** The pushers on the case's right: each one's top and height, in `u`. */
private val Pushers = listOf(20.5f to 5f, 28f to 4f)

/** The screen's radius, in `u`: the face's square is its bounding box. */
private const val ScreenRadius = 20.6f

/** How strongly the face shows before it is added: faint, not off. */
private const val FaintAlpha = 0.34f

/**
 * [draw] in the face's own 200-unit square, which sits on the screen's circle (its origin at
 * (4.4, 5.4) `u`, 0.206 `u` to a unit, so 200 units are the circle's 41.2 `u`), clipped to it.
 */
private fun DrawScope.drawFace(u: Float, centre: Offset, draw: DrawScope.() -> Unit) {
    val screen = Path().apply { addOval(Rect(centre, ScreenRadius * u)) }
    clipPath(screen) {
        withTransform({
            translate(4.4f * u, 5.4f * u)
            scale(0.206f * u, 0.206f * u, pivot = Offset.Zero)
        }) { draw() }
    }
}

/** The mihrab's arch, the face's frame, in face units. */
private fun archPath() = Path().apply {
    moveTo(22f, 168f)
    lineTo(22f, 118f)
    cubicTo(22f, 74f, 58f, 38f, 100f, 10f)
    cubicTo(142f, 38f, 178f, 74f, 178f, 118f)
    lineTo(178f, 168f)
}

/**
 * The big time as four digit cells and a colon, in face units: a time, without saying one. (Two
 * blocks, one for each pair, read as a pair of glasses at 52 dp on the emulator.)
 */
private fun DrawScope.drawTime(color: Color) {
    for (left in listOf(50f, 74f, 106f, 130f)) {
        drawRoundRect(color, topLeft = Offset(left, 94f), size = Size(20f, 32f), cornerRadius = CornerRadius(5f))
    }
    drawCircle(color, radius = 3.5f, center = Offset(100f, 104f))
    drawCircle(color, radius = 3.5f, center = Offset(100f, 117f))
}

/** A line of the face's words as a bar from ([left], [top]) to ([right], [bottom]), fully rounded unless [radius] says. */
private fun DrawScope.bar(color: Color, left: Float, right: Float, top: Float, bottom: Float, radius: Float = (bottom - top) / 2f) {
    drawRoundRect(color, topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = CornerRadius(radius))
}
