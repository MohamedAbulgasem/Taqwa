package world.taqwa.app.feature.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.DarkColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.manropeFamily
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.qibla_cardinal_east
import world.taqwa.app.resources.qibla_cardinal_north
import world.taqwa.app.resources.qibla_cardinal_south
import world.taqwa.app.resources.qibla_cardinal_west
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** `kotlin.math` has no `toRadians` — that is `java.lang.Math`, JVM-only and unavailable from
 * commonMain on Kotlin/Native. */
private fun Double.toRadians(): Double = this * PI / 180.0

/**
 * The mockup's 186px dial on its 250px phone is 74% of the screen's width; on a 420dp screen that
 * is this box, which keeps the dial the same size relative to the screen as the mockup draws it.
 */
internal val DialSize = 312.dp

// Radii as fractions of the dial's outer radius, read off the mockup SVG (viewBox 186, centre 93).
private const val OuterCircleR = 82f / 93f
private const val TickRingR = 75f / 93f
private const val MarkerR = 68f / 93f
private const val CentreDotR = 3.5f / 93f
private const val LabelR = 64.5f / 93f

/** `stroke-dasharray="2 17.6"` around r=75 is 24 slots; this is the inked fraction of each. */
private const val TickCount = 24
private const val TickInkedFraction = 2f / 19.6f

/**
 * The dial rotates under a needle that points at the Kaaba marker, with the marker fixed at
 * [bearingDegrees] on the rim. [aligned] lights the outer rim amber; [dimmed] is the low-accuracy
 * state, where the whole dial fades to 28% and drops the marker and the cardinals, because "I
 * don't know" is the correct behaviour there.
 *
 * [needle] false drops the needle and the centre pin as well, leaving the ring and its ticks: the
 * best-effort state, where the compass is known to be unusable and even a dimmed needle would be
 * a direction the app does not have. The numbers below the dial are still correct — they are
 * computed from the location, never measured — so the screen keeps them.
 */
@Composable
fun QiblaDial(
    headingDegrees: Double,
    bearingDegrees: Double,
    aligned: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    needle: Boolean = true,
    diameter: Dp = DialSize,
) {
    val colors = LocalTaqwaColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = manropeFamily(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = colors.textTertiary,
    )
    // The amber halo reads as a glow on a near-black ground; on the light ground it would read as
    // a smudge, so it is pulled well back there. The ticks go the other way: the same alpha that
    // lands on the mockup's tick grey over black is too faint over the light ground.
    val dark = colors == DarkColors
    val glow = if (dark) 1f else 0.35f
    val tickAlpha = if (dark) 0.45f else 0.55f

    // Resolved here, in composition, because stringResource needs a @Composable context that the
    // Canvas draw lambda below does not have.
    val cardinals = listOf(
        stringResource(Res.string.qibla_cardinal_north) to 0.0,
        stringResource(Res.string.qibla_cardinal_east) to 90.0,
        stringResource(Res.string.qibla_cardinal_south) to 180.0,
        stringResource(Res.string.qibla_cardinal_west) to 270.0,
    )

    // This Canvas draws every position (ticks, cardinal labels, needle, Kaaba marker) from literal
    // x/y coordinates built with sin/cos on the raw compass bearing — it never reads
    // LocalLayoutDirection, so it is not mirrored under RTL. North stays up and west stays left in
    // every locale, which is correct: a compass does not flip because the surrounding text does.
    // Every position below is a fraction of the box, so the dial is the same drawing at any
    // diameter — [diameter] is only ever smaller than [DialSize], when a sideways screen is too
    // short to give the full dial its room.
    Canvas(modifier.size(diameter).alpha(if (dimmed) 0.28f else 1f)) {
        val r = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        // 1 — outer hairline, which becomes the lit rim when aligned.
        if (aligned) {
            haloCircle(colors.accent, r * OuterCircleR, 3.75.dp.toPx(), glow)
            drawCircle(
                color = colors.accent.copy(alpha = 0.9f),
                radius = r * OuterCircleR,
                center = centre,
                style = Stroke(width = 3.75.dp.toPx()),
            )
        } else {
            drawCircle(
                color = colors.hairline,
                radius = r * OuterCircleR,
                center = centre,
                style = Stroke(width = 1.8.dp.toPx()),
            )
        }

        // 2 — the 24 ticks: one dashed circle stroke, as the mockup's stroke-dasharray, not 24
        // separate lines. The whole dial spins under the fixed needle.
        rotate(degrees = -headingDegrees.toFloat(), pivot = centre) {
            val ringRadius = r * TickRingR
            val slot = (2f * PI.toFloat() * ringRadius) / TickCount
            val inked = slot * TickInkedFraction
            drawCircle(
                color = if (aligned) {
                    colors.accent.copy(alpha = 0.30f)
                } else {
                    colors.textTertiary.copy(alpha = tickAlpha)
                },
                radius = ringRadius,
                center = centre,
                style = Stroke(
                    width = 7.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(inked, slot - inked)),
                ),
            )

            // 3 — N/E/S/W ride the dial, so they tilt with it exactly as a physical card does.
            // All four always draw — dropping the one nearest the Kaaba marker previously made
            // that letter disappear at bearings common to many real locations (e.g. E). Drawing
            // the labels here, before the needle and marker below, means a coincidental overlap
            // simply sits under them instead of hiding the letter.
            if (!dimmed) {
                cardinals.forEach { (letter, onDial) ->
                    val a = (onDial - 90.0).toRadians()
                    drawCardinal(measurer, letter, labelStyle, centre, r * LabelR, a)
                }
            }
        }

        // 4 — needle and marker share one angle: the needle points at the Kaaba, not at north.
        val markerAngle = (bearingDegrees - headingDegrees - 90.0).toRadians()
        val tip = Offset(
            centre.x + r * MarkerR * cos(markerAngle).toFloat(),
            centre.y + r * MarkerR * sin(markerAngle).toFloat(),
        )
        val needleColor = if (dimmed) colors.textSecondary else colors.accent
        val needleWidth = (if (aligned) 3.9 else 3.3).dp.toPx()
        if (needle) {
            if (!dimmed) haloLine(needleColor, centre, tip, needleWidth, glow)
            drawLine(needleColor, centre, tip, strokeWidth = needleWidth, cap = StrokeCap.Round)

            // 5 — centre pin.
            drawCircle(color = needleColor, radius = r * CentreDotR, center = centre)
        }

        // 6 — the Kaaba: an amber rounded square with its dark band, sitting on the rim. It stays
        // upright at every bearing, as in the mockup.
        if (!dimmed) drawKaaba(colors.accent, tip)
    }
}

/** Compose has no CSS `drop-shadow`, so the halo is a few progressively wider, fainter passes. */
private fun DrawScope.haloCircle(color: Color, radius: Float, width: Float, strength: Float) {
    listOf(4.5f to 0.05f, 3f to 0.08f, 1.9f to 0.13f).forEach { (scale, alpha) ->
        drawCircle(
            color = color.copy(alpha = alpha * strength),
            radius = radius,
            center = center,
            style = Stroke(width = width * scale),
        )
    }
}

private fun DrawScope.haloLine(color: Color, from: Offset, to: Offset, width: Float, strength: Float) {
    listOf(4f to 0.06f, 2.5f to 0.10f, 1.7f to 0.15f).forEach { (scale, alpha) ->
        drawLine(
            color = color.copy(alpha = alpha * strength),
            start = from,
            end = to,
            strokeWidth = width * scale,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawKaaba(accent: Color, at: Offset) {
    val side = 24.dp.toPx()
    val half = side / 2f
    drawRoundRect(
        color = accent,
        topLeft = Offset(at.x - half, at.y - half),
        size = Size(side, side),
        cornerRadius = CornerRadius(5.25.dp.toPx()),
    )
    // The kiswah band: black rather than the background token, so it stays dark in both themes.
    val bandHeight = 4.5.dp.toPx()
    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(at.x - half, at.y - 3.75.dp.toPx()),
        size = Size(side, bandHeight),
    )
}

private fun DrawScope.drawCardinal(
    measurer: TextMeasurer,
    letter: String,
    style: TextStyle,
    centre: Offset,
    radius: Float,
    angleRadians: Double,
) {
    val measured = measurer.measure(letter, style)
    val x = centre.x + radius * cos(angleRadians).toFloat()
    val y = centre.y + radius * sin(angleRadians).toFloat()
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f),
    )
}
