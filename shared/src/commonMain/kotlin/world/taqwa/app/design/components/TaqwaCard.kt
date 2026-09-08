package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TaqwaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
fun CardDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalTaqwaColors.current.hairline))
}

private const val LABEL_ID = "label"
private const val VALUE_ID = "value"
private const val TRAILING_ID = "trailing"

/** Space between the label block and whatever sits to its end side. */
private val RowGap = 12.dp

/**
 * When the label and the value cannot both fit on one line, the label keeps at least this share of
 * the row and the value ellipsises in what is left. The label is the row's identity; the value is
 * detail the next screen will repeat in full.
 */
private const val LABEL_MIN_SHARE = 0.6f

/**
 * [subtitle] is for a caveat the row itself cannot express, a known limitation of the option,
 * not a restatement of it. Left null, the row is exactly as it was.
 *
 * Measured by hand rather than with a weighted `Row` because weights split the width before
 * anyone has asked what the children need. Two `weight(1f)` slots gave the label at most half the
 * row, so "Choose a city instead" wrapped onto two lines beside an empty end slot and "Use the
 * system widget material." broke under a lone check mark. Here the label is measured at its
 * natural width first, the value takes the remainder, and only a genuinely long pair shares the
 * line, with the label winning the larger part.
 */
@Composable
fun TaqwaRow(
    label: String,
    value: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    /**
     * True for a row that *is* an option (a check mark moves to it when tapped). Those rows do
     * not ripple: the check arriving is the feedback, and a grey wash sweeping across the row a
     * beat later reads as a second, unrelated event. Navigation rows keep the ripple, because
     * nothing else acknowledges the tap before the next screen arrives.
     */
    selectable: Boolean = false,
    /**
     * False for a row whose tap is acknowledged by something else on the same screen (a list
     * expanding beneath it, a sheet section changing) rather than by a new screen or a check
     * mark. Independent of [selectable], which says what the row *is*; this says only how it
     * responds.
     */
    ripple: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalTaqwaColors.current
    val clickModifier = when {
        onClick == null -> Modifier
        selectable || !ripple -> Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
        else -> Modifier.clickable(onClick = onClick)
    }
    Layout(
        content = {
            if (subtitle == null) {
                Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary, modifier = Modifier.layoutId(LABEL_ID))
            } else {
                Column(Modifier.layoutId(LABEL_ID)) {
                    Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary)
                    Text(subtitle, style = TaqwaText.caption, color = colors.textTertiary)
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.layoutId(VALUE_ID),
                )
            }
            if (trailing != null) {
                Box(Modifier.layoutId(TRAILING_ID)) { trailing() }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = RowGap.roundToPx()
        val loose = Constraints()

        val trailingPlaceable = measurables.firstOrNull { it.layoutId == TRAILING_ID }?.measure(loose)
        val trailingSpan = trailingPlaceable?.let { it.width + gap } ?: 0
        val available = max(width - trailingSpan, 0)

        val labelMeasurable = measurables.first { it.layoutId == LABEL_ID }
        val valueMeasurable = measurables.firstOrNull { it.layoutId == VALUE_ID }
        val labelWanted = labelMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        val valueWanted = valueMeasurable?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val valueGap = if (valueMeasurable != null) gap else 0

        // A label that fits is measured against everything the value leaves it, not against its
        // own intrinsic width: shaped Arabic can lay out a pixel wider than it measures, and a
        // constraint cut to the exact intrinsic width then wrapped the last letter onto its own line.
        val labelWidth = if (labelWanted + valueGap + valueWanted <= available) {
            available - valueGap - valueWanted
        } else {
            val floor = (available * LABEL_MIN_SHARE).roundToInt()
            min(labelWanted, max(available - valueGap - valueWanted, floor))
        }.coerceIn(0, available)
        val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = labelWidth))
        val valuePlaceable = valueMeasurable?.measure(
            Constraints(maxWidth = max(available - labelPlaceable.width - valueGap, 0)),
        )

        val height = max(
            constraints.minHeight,
            maxOf(labelPlaceable.height, valuePlaceable?.height ?: 0, trailingPlaceable?.height ?: 0),
        )
        layout(width, height) {
            // placeRelative mirrors every x under RTL, so the label leads and the value and
            // trailing slot trail in both directions without a LayoutDirection check.
            labelPlaceable.placeRelative(0, (height - labelPlaceable.height) / 2)
            trailingPlaceable?.placeRelative(width - trailingPlaceable.width, (height - trailingPlaceable.height) / 2)
            valuePlaceable?.placeRelative(width - trailingSpan - valuePlaceable.width, (height - valuePlaceable.height) / 2)
        }
    }
}
