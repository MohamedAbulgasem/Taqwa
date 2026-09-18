package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.RadioMark
import world.taqwa.app.design.components.RowSubtitleGap
import world.taqwa.app.i18n.PlatformFormat
import kotlin.math.roundToInt
import kotlin.time.Duration

/** Spec §92: all tap targets ≥44 pt. */
private val MIN_TAP_TARGET = 44.dp

/** A drawn triangle, matching `CheckMark`'s "never a text glyph for a control" rule. */
@Composable
private fun PlayTriangle(modifier: Modifier = Modifier) {
    val color = LocalTaqwaColors.current.textSecondary
    Canvas(modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            lineTo(size.width * 0.86f, size.height * 0.5f)
            close()
        }
        drawPath(path, color = color)
    }
}

/**
 * One auditionable choice: a name, a caption, an optional play button and a radio. Both audio
 * sheets — the four sound levels and the three adhan voices — are the same row with different
 * words in it, so it lives here rather than twice.
 *
 * [onPreview] is null for a choice with nothing to hear (Silent): that row simply has no play
 * button, and the press itself is the reassurance that nothing plays. Selection uses a radio
 * rather than [world.taqwa.app.design.components.CheckMark]: seeing the unselected targets — not
 * just the chosen one — helps a listener compare them.
 */
@Composable
internal fun AudioOptionRow(
    label: String,
    caption: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: (() -> Unit)? = null,
    /** What a screen reader calls the preview triangle. The two prayer-sound sheets leave it
     * unset and keep the label they have always had. */
    previewDescription: String? = null,
    /** Drawn before the label, with 12 dp after it. The reciter picker passes a monogram disc
     * (spec 3a §5.5); the two prayer-sound sheets pass nothing and are unchanged. */
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            // No ripple: the radio moving is the feedback, as on every option row.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSelect() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary)
            Spacer(Modifier.height(RowSubtitleGap))
            Text(caption, style = TaqwaText.caption, color = colors.textSecondary)
        }
        if (onPreview != null) {
            // Spec §92's 44 pt floor is a hit area, not a drawn size: the triangle stays 18 dp
            // and the box around it carries the click. It matters more here than anywhere else
            // in the app, because this is a nested clickable inside an already-clickable row —
            // a near-miss used to select the option instead of previewing it.
            Box(
                // Clipped first, so the press ripple is a disc the size of the hit area rather
                // than a square: the target is round in the mind, so it should be round when lit.
                Modifier
                    .size(MIN_TAP_TARGET)
                    .clip(CircleShape)
                    .clickable { onPreview() }
                    .then(
                        if (previewDescription == null) {
                            Modifier
                        } else {
                            Modifier.semantics { contentDescription = previewDescription }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PlayTriangle()
            }
        }
        RadioMark(selected = selected, modifier = Modifier.padding(start = 12.dp))
    }
}

/** A clip's length in whole seconds, in the locale's own digits. */
internal fun PlatformFormat.localizedSeconds(duration: Duration): String =
    localizedDigits(duration.inWholeMilliseconds.toDouble().div(1000).roundToInt())

/**
 * A clip's length as `m:ss` in the locale's own digits — "2:34", "٢:٣٤". The seconds are padded
 * digit by digit rather than with `padStart('0')`, because a literal Western zero beside an
 * Arabic-Indic digit is exactly the mixed-numeral bug the app formats everything to avoid.
 */
internal fun PlatformFormat.localizedClipLength(duration: Duration): String {
    val total = duration.inWholeMilliseconds.toDouble().div(1000).roundToInt()
    val minutes = total / 60
    val seconds = total % 60
    return "${localizedDigits(minutes)}:${localizedDigits(seconds / 10)}${localizedDigits(seconds % 10)}"
}
