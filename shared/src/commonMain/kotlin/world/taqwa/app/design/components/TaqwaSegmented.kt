package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors

/**
 * A two-or-more-way pill switch (spec §2.1 point 3, reused by the reading-settings sheet's
 * Translation | Mushaf switch): a hairline pill on the surface colour, the selected option filled
 * with the accent. The selected label uses `colors.background` rather than a literal white — the
 * same choice the prayer-times method switch already makes — so it stays legible against the
 * accent in both themes rather than only in light mode.
 *
 * 44 dp tall, and taller only when a label needs it — a 200 % font, or a label that wraps — so
 * the text is never cut. The height is the tallest label's own (`IntrinsicSize.Min`) with 44 dp as
 * the floor, never "as tall as allowed": the adhkar screen hands the switch a bounded height, and
 * the options' `fillMaxHeight` would stretch a pill sized only by a minimum to fill it.
 */
@Composable
fun TaqwaSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(percent = 50))
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // The selectable takes the full 44 dp; only the drawn pill is inset by 3 dp. Putting
            // the inset on the Row instead would leave each option a 38 dp target.
            Box(
                Modifier
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(3.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) colors.accent else colors.surface)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.background else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
