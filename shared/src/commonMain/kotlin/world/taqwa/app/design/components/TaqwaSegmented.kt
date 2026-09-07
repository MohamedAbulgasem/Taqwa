package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 */
@Composable
fun TaqwaSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(percent = 50))
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) colors.accent else colors.surface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    )
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
