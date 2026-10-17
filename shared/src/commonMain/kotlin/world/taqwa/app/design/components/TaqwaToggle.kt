package world.taqwa.app.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors

/**
 * A switch drawn at the end of a [TaqwaRow], spoken as the row itself: the row becomes one switch
 * node carrying its label, its on/off state and a double-tap that flips it, while touch stays on
 * the drawn switch alone (see [TaqwaRow]'s `toggle`).
 */
class RowToggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit)

/**
 * A switch in the app's own palette. Material3's would arrive with its own colour scheme and a
 * different silhouette from everything else on these screens.
 *
 * On its own it is a toggleable with the switch role, so a screen reader at least says "switch,
 * off"; inside a [TaqwaRow] it is hidden and the row speaks for it.
 */
@Composable
fun TaqwaToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalTaqwaColors.current
    val knobStart by animateDpAsState(if (checked) 22.dp else 3.dp, label = "knob")
    // Spec §92: 44 pt minimum. The switch is still drawn 48×28 — the outer box only widens what
    // a finger has to hit, so nothing on the screen moves or changes shape.
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            // The knob sliding is the feedback; a rectangular ripple around a pill looked like a
            // box lighting up behind it.
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(if (checked) colors.accent else colors.hairline),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = knobStart)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.background else colors.surface),
            )
        }
    }
}
