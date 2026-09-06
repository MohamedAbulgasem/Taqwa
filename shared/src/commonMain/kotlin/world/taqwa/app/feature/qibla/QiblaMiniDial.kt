package world.taqwa.app.feature.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.manropeFamily
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.qibla_cardinal_north
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Prayer screen's Qibla card glyph: [QiblaDial] reduced to a north-up map. No sensor drives
 * it — the needle sits at [bearingDegrees] from north, which is pure geometry from the location,
 * so the card costs nothing while the Prayer screen is open. The live compass is one tap away.
 *
 * Same rules as the big dial: drawn from literal coordinates, so it does not mirror under RTL.
 * North is up in every language.
 */
@Composable
fun QiblaMiniDial(bearingDegrees: Double, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val colors = LocalTaqwaColors.current
    val measurer = rememberTextMeasurer()
    val north = stringResource(Res.string.qibla_cardinal_north)
    val northStyle = TextStyle(
        fontFamily = manropeFamily(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 7.sp,
        color = colors.textTertiary,
    )
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)

        // Outer hairline, as the big dial's, at the same fraction of the radius.
        drawCircle(colors.hairline, radius = r * OUTER, center = centre, style = Stroke(1.5.dp.toPx()))

        // 24 ticks as one dashed stroke; the same construction as QiblaDial, thinner.
        val tickRadius = r * TICKS
        val slot = (2f * PI.toFloat() * tickRadius) / 24
        val inked = 1.2.dp.toPx()
        drawCircle(
            color = colors.textTertiary.copy(alpha = 0.55f),
            radius = tickRadius,
            center = centre,
            style = Stroke(
                width = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(inked, slot - inked)),
            ),
        )

        val n = measurer.measure(north, northStyle)
        drawText(n, topLeft = Offset(centre.x - n.size.width / 2f, centre.y - r * LABEL - n.size.height / 2f))

        // Needle to the Kaaba at the bearing; the marker keeps the big dial's proportions.
        val angle = (bearingDegrees - 90.0) * PI / 180.0
        val tip = Offset(
            centre.x + r * MARKER * cos(angle).toFloat(),
            centre.y + r * MARKER * sin(angle).toFloat(),
        )
        drawLine(colors.accent, centre, tip, strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(colors.accent, radius = 2.dp.toPx(), center = centre)

        val side = r * KAABA
        drawRoundRect(
            color = colors.accent,
            topLeft = Offset(tip.x - side / 2f, tip.y - side / 2f),
            size = Size(side, side),
            cornerRadius = CornerRadius(side * 0.22f),
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(tip.x - side / 2f, tip.y - side * 0.16f),
            size = Size(side, side * 0.2f),
        )
    }
}

private const val OUTER = 0.88f
private const val TICKS = 0.78f
private const val LABEL = 0.56f
private const val MARKER = 0.70f
private const val KAABA = 0.30f
