package world.taqwa.app.design.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

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
