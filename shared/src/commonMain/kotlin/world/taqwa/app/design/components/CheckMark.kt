package world.taqwa.app.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors

@Composable
fun CheckMark(modifier: Modifier = Modifier) {
    val accent = LocalTaqwaColors.current.accent
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.17f, w * 0.52f)
            lineTo(w * 0.40f, w * 0.75f)
            lineTo(w * 0.83f, w * 0.27f)
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = w * 0.115f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
