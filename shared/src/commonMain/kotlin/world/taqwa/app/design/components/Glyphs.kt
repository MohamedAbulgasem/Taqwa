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
