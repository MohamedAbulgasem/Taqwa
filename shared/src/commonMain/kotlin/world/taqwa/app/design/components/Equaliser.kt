package world.taqwa.app.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The three-bar equaliser, moving (spec §12.7). It is the *only* animated thing on a Quran screen,
 * which is the point: recitation is live, and nothing else on the page is.
 *
 * The three bars run on three tweens of different lengths (they are prime-ish multiples of each
 * other, 620/880/740 ms) so the pattern never settles into a visible loop; one shared clock with
 * three phase offsets would read as a wave travelling across the glyph, which looks like a
 * loading indicator rather than sound.
 *
 * [animated] draws the still frame instead when false. There is no reduced-motion signal in the
 * app to read yet — neither platform's accessibility flag is surfaced through Compose
 * Multiplatform, and this app has no expect/actual for it — so nothing passes false today; the
 * parameter exists so that the one place the app animates is the one place that has to change
 * when that signal arrives.
 */
@Composable
fun Equaliser(tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp, animated: Boolean = true) {
    if (!animated) {
        Canvas(modifier.size(size)) { drawEqualiser(tint, REST) }
        return
    }
    val transition = rememberInfiniteTransition(label = "equaliser")
    val bars = BAR_MILLIS.mapIndexed { index, millis ->
        transition.animateFloat(
            initialValue = if (index == 1) 0.95f else 0.2f,
            targetValue = if (index == 1) 0.25f else 1f,
            animationSpec = infiniteRepeatable(tween(millis), RepeatMode.Reverse),
            label = "equaliserBar$index",
        )
    }
    Canvas(modifier.size(size)) { drawEqualiser(tint, bars.map { it.value }) }
}

/** The still frame: the middle bar tall, the outer two short, so a frozen glyph still reads. */
private val REST = listOf(0.35f, 0.9f, 0.5f)

private val BAR_MILLIS = listOf(620, 880, 740)
