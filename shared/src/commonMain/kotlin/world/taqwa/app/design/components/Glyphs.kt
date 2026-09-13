package world.taqwa.app.design.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn glyph paths shared by more than one component — originally private to
 * [TaqwaTabBar], moved here once the reader header (spec §2.2) needed the same book mark for its
 * mode-toggle button and the tab bar's own "Quran" tab icon. Kept `internal`: these are drawn from
 * literal 16-unit coordinates tuned for a specific `Canvas` size, not a general-purpose icon API.
 */

/** Line weight of every glyph here: the mockup's 1.4 px in a 16 px box. */
internal fun DrawScope.glyphStroke() = Stroke(width = size.width * 0.0875f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** An open book: two facing pages and the spine between them. Reads as "Quran" the way the
 * mihrab reads as "prayer" — a shape borrowed from the design mockups, not a generic glyph. */
internal fun DrawScope.drawBook(tint: Color) {
    val u = size.width / 16f
    val pages = Path().apply {
        moveTo(8f * u, 3.2f * u)
        cubicTo(6.6f * u, 2.1f * u, 4.4f * u, 1.9f * u, 1.8f * u, 2.4f * u)
        lineTo(1.8f * u, 12.8f * u)
        cubicTo(4.4f * u, 12.3f * u, 6.6f * u, 12.5f * u, 8f * u, 13.7f * u)
        cubicTo(9.4f * u, 12.5f * u, 11.6f * u, 12.3f * u, 14.2f * u, 12.8f * u)
        lineTo(14.2f * u, 2.4f * u)
        cubicTo(11.6f * u, 1.9f * u, 9.4f * u, 2.1f * u, 8f * u, 3.2f * u)
        close()
    }
    drawPath(pages, tint, style = glyphStroke())
    drawLine(tint, Offset(8f * u, 3.2f * u), Offset(8f * u, 13.7f * u), strokeWidth = size.width * 0.0875f, cap = StrokeCap.Round)
}

/** A bookmark: a pennant with a notch at the foot. Filled when the ayah is kept. */
internal fun DrawScope.drawBookmark(tint: Color, filled: Boolean) {
    val u = size.width / 16f
    val path = Path().apply {
        moveTo(3.5f * u, 2f * u)
        lineTo(12.5f * u, 2f * u)
        lineTo(12.5f * u, 14f * u)
        lineTo(8f * u, 10.6f * u)
        lineTo(3.5f * u, 14f * u)
        close()
    }
    drawPath(path, tint, style = if (filled) Fill else glyphStroke())
}

/** Copy: two overlapping rounded rectangles, the back one peeking out top-start. */
internal fun DrawScope.drawCopy(tint: Color) {
    val u = size.width / 16f
    val corner = CornerRadius(1.2f * u)
    drawRoundRect(tint, topLeft = Offset(5.5f * u, 5.5f * u), size = Size(8f * u, 8f * u), cornerRadius = corner, style = glyphStroke())
    val back = Path().apply {
        moveTo(3.2f * u, 10.5f * u)
        lineTo(3.2f * u, 3.4f * u)
        quadraticTo(3.2f * u, 2.5f * u, 4.1f * u, 2.5f * u)
        lineTo(10.5f * u, 2.5f * u)
    }
    drawPath(back, tint, style = glyphStroke())
}

/** Share: a tray with an arrow rising out of it. */
internal fun DrawScope.drawShare(tint: Color) {
    val u = size.width / 16f
    val tray = Path().apply {
        moveTo(5f * u, 7.5f * u); lineTo(3.2f * u, 7.5f * u); lineTo(3.2f * u, 14f * u)
        lineTo(12.8f * u, 14f * u); lineTo(12.8f * u, 7.5f * u); lineTo(11f * u, 7.5f * u)
    }
    drawPath(tray, tint, style = glyphStroke())
    drawLine(tint, Offset(8f * u, 10.5f * u), Offset(8f * u, 2.2f * u), strokeWidth = size.width * 0.0875f, cap = StrokeCap.Round)
    val head = Path().apply { moveTo(5.4f * u, 4.8f * u); lineTo(8f * u, 2.2f * u); lineTo(10.6f * u, 4.8f * u) }
    drawPath(head, tint, style = glyphStroke())
}

/**
 * A misbaha: a loop of beads with the larger head bead at the foot and a tassel under it — the
 * entry point to the tasbeeh (spec Tasbeeh §5), picked in the design round as the most
 * recognisable silhouette of the object itself.
 *
 * Eight bead positions sit 45° apart on a 5.2-unit radius around (8, 7.2); seven of them are
 * drawn as filled 1.05-unit circles, and the eighth — the one at the foot — is the head bead,
 * drawn larger at 1.45 and pushed a little further out to (8, 12.9) so it reads as the knot the
 * loop is tied at rather than as one more bead. Filled rather than stroked for the reason the
 * bookmark badge is: at 22 dp a stroked bead is a smudge.
 *
 * The tassel is two strokes at the mockup's thinner 1.1-unit weight — a stem and a flare — and
 * stops short of the grid's foot so its round caps stay inside the canvas.
 */
internal fun DrawScope.drawMisbaha(tint: Color) {
    val u = size.width / 16f
    // -90° is the top of the loop; +90° is the foot, which the head bead takes instead.
    listOf(-90f, -45f, 0f, 45f, 135f, 180f, 225f).forEach { degrees ->
        val radians = degrees * PI.toFloat() / 180f
        drawCircle(
            tint,
            radius = 1.05f * u,
            center = Offset((8f + 5.2f * cos(radians)) * u, (7.2f + 5.2f * sin(radians)) * u),
        )
    }
    drawCircle(tint, radius = 1.45f * u, center = Offset(8f * u, 12.9f * u))
    val tassel = size.width * 0.069f
    drawLine(tint, Offset(8f * u, 14.2f * u), Offset(8f * u, 15.35f * u), strokeWidth = tassel, cap = StrokeCap.Round)
    val flare = Path().apply {
        moveTo(6.95f * u, 15.35f * u)
        lineTo(8f * u, 14.25f * u)
        lineTo(9.05f * u, 15.35f * u)
    }
    drawPath(flare, tint, style = Stroke(width = tassel, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A plus in a ring, as the tasbeeh mockup's top-corner button draws it: add a dhikr of your own. */
internal fun DrawScope.drawPlus(tint: Color) {
    val u = size.width / 16f
    val stroke = size.width * 0.0875f
    drawCircle(tint, radius = 6f * u, center = Offset(8f * u, 8f * u), style = Stroke(width = stroke))
    drawLine(tint, Offset(5.5f * u, 8f * u), Offset(10.5f * u, 8f * u), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(tint, Offset(8f * u, 5.5f * u), Offset(8f * u, 10.5f * u), strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * A speaker: the cone, and two waves leaving it. Recitation's idle mark (spec §12.7) — headphones
 * were the original pick and read wrong when nobody is wearing any, so the glyph is the sound
 * itself rather than the thing you hear it through.
 *
 * The waves are drawn as quadratic curves rather than arc sweeps so they keep the same rounded
 * stroke as every other glyph here, and the outer one is longer than the inner one, which is what
 * makes the pair read as sound rather than as brackets.
 *
 * **[pointsForward] mirrors it**, for the reason [drawSkip] and [drawChevron] take the same
 * parameter: the shape is drawn from literal coordinates, a `DrawScope` has no direction to
 * consult, and a speaker is one of the glyphs both platforms' guidance mirrors under a
 * right-to-left interface — the cone should open the way the reading does. The equaliser beside
 * it is not mirrored: three bars have no direction to be wrong about.
 */
internal fun DrawScope.drawSpeaker(tint: Color, pointsForward: Boolean = true) {
    val u = size.width / 16f
    fun x(value: Float) = (if (pointsForward) value else 16f - value) * u
    val cone = Path().apply {
        moveTo(x(2.4f), 6.2f * u)
        lineTo(x(5f), 6.2f * u)
        lineTo(x(8.3f), 3.1f * u)
        lineTo(x(8.3f), 12.9f * u)
        lineTo(x(5f), 9.8f * u)
        lineTo(x(2.4f), 9.8f * u)
        close()
    }
    drawPath(cone, tint, style = glyphStroke())
    val inner = Path().apply {
        moveTo(x(10.7f), 6.1f * u)
        quadraticTo(x(12.1f), 8f * u, x(10.7f), 9.9f * u)
    }
    drawPath(inner, tint, style = glyphStroke())
    val outer = Path().apply {
        moveTo(x(12.7f), 4.2f * u)
        quadraticTo(x(15.2f), 8f * u, x(12.7f), 11.8f * u)
    }
    drawPath(outer, tint, style = glyphStroke())
}

/**
 * Three bars rising and falling: the one cue on a Quran screen that recitation is live (spec
 * §12.7). [levels] are fractions of the full 11-unit height, one per bar; [Equaliser] animates
 * them, and a still frame at rest draws the same shape.
 *
 * Rounded caps and a bar width matched to [glyphStroke]'s weight would leave three hairlines, so
 * the bars are deliberately heavier — 1.9 u — which is what lets them read at 18 dp beside a
 * speaker glyph of the same box.
 */
internal fun DrawScope.drawEqualiser(tint: Color, levels: List<Float>) {
    val u = size.width / 16f
    val width = 1.9f * u
    val centres = listOf(3.6f, 8f, 12.4f)
    centres.forEachIndexed { i, x ->
        val fraction = levels.getOrElse(i) { 0.5f }.coerceIn(0f, 1f)
        val half = (1.3f + fraction * 4.6f) * u
        drawLine(
            color = tint,
            start = Offset(x * u, 8f * u - half),
            end = Offset(x * u, 8f * u + half),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Previous- or next-ayah: a solid triangle against a bar, the transport control every media
 * player draws. [forward] is resolved by the caller from `LocalLayoutDirection` rather than read
 * here, because a `DrawScope` has no direction of its own — under an Arabic UI the row of
 * controls mirrors itself and the glyphs have to follow it, or "next" would point back at the
 * button before it.
 */
internal fun DrawScope.drawSkip(tint: Color, forward: Boolean) {
    val u = size.width / 16f
    fun x(value: Float) = (if (forward) value else 16f - value) * u
    val triangle = Path().apply {
        moveTo(x(3.2f), 3.4f * u)
        lineTo(x(10.6f), 8f * u)
        lineTo(x(3.2f), 12.6f * u)
        close()
    }
    drawPath(triangle, tint, style = Fill)
    drawLine(
        tint,
        Offset(x(12.3f), 3.4f * u),
        Offset(x(12.3f), 12.6f * u),
        strokeWidth = size.width * 0.115f,
        cap = StrokeCap.Round,
    )
}

/** A solid play triangle, optically centred: a triangle drawn on the true centre reads as sitting
 * left of it, so the ink is nudged a third of a unit along. */
internal fun DrawScope.drawPlayTriangle(tint: Color) {
    val u = size.width / 16f
    val path = Path().apply {
        moveTo(4.6f * u, 3f * u)
        lineTo(12.6f * u, 8f * u)
        lineTo(4.6f * u, 13f * u)
        close()
    }
    drawPath(path, tint, style = Fill)
}

/** Two bars. Rounded ends, like every other filled mark in the set. */
internal fun DrawScope.drawPause(tint: Color) {
    val u = size.width / 16f
    val stroke = size.width * 0.155f
    listOf(5.6f, 10.4f).forEach { x ->
        drawLine(tint, Offset(x * u, 3.6f * u), Offset(x * u, 12.4f * u), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** A plain cross: the player bar's dismiss. */
internal fun DrawScope.drawClose(tint: Color) {
    val u = size.width / 16f
    val stroke = size.width * 0.0875f
    drawLine(tint, Offset(4.5f * u, 4.5f * u), Offset(11.5f * u, 11.5f * u), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(tint, Offset(11.5f * u, 4.5f * u), Offset(4.5f * u, 11.5f * u), strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * A disclosure chevron. [pointsForward] is the caller's own reading of `LocalLayoutDirection`,
 * for the same reason [drawSkip] takes one: the shape is drawn from literal coordinates and a
 * `DrawScope` has no direction to consult.
 */
internal fun DrawScope.drawChevron(tint: Color, pointsForward: Boolean) {
    val u = size.width / 16f
    fun x(value: Float) = (if (pointsForward) value else 16f - value) * u
    val path = Path().apply {
        moveTo(x(6.2f), 2.6f * u)
        lineTo(x(11.2f), 8f * u)
        lineTo(x(6.2f), 13.4f * u)
    }
    drawPath(path, tint, style = glyphStroke())
}

/**
 * An external link: a box with its top-trailing corner open and an arrow leaving through it.
 * [pointsForward] is the caller's reading of `LocalLayoutDirection`, as for [drawChevron]: the
 * arrow leaves toward the trailing edge in both directions.
 */
internal fun DrawScope.drawExternalLink(tint: Color, pointsForward: Boolean) {
    val u = size.width / 16f
    fun x(value: Float) = (if (pointsForward) value else 16f - value) * u
    val box = Path().apply {
        moveTo(x(8.5f), 3.2f * u); lineTo(x(3.2f), 3.2f * u); lineTo(x(3.2f), 12.8f * u)
        lineTo(x(12.8f), 12.8f * u); lineTo(x(12.8f), 7.5f * u)
    }
    drawPath(box, tint, style = glyphStroke())
    drawLine(tint, Offset(x(7.2f), 8.8f * u), Offset(x(13.2f), 2.8f * u), strokeWidth = size.width * 0.0875f, cap = StrokeCap.Round)
    val head = Path().apply { moveTo(x(9.6f), 2.8f * u); lineTo(x(13.2f), 2.8f * u); lineTo(x(13.2f), 6.4f * u) }
    drawPath(head, tint, style = glyphStroke())
}
