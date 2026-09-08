package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors

/**
 * Every bottom sheet in the app: the reading settings, the notification sound picker, the
 * remind-before picker. One place for the chrome so no sheet falls back to material3's own
 * defaults — a sheet left on those takes its container colour from the Material colour scheme's
 * tinted surface and shows up faintly mauve against everything else, which is exactly what the
 * sound picker did until it was routed through here.
 *
 * The container is the screen [world.taqwa.app.design.TaqwaColors.background], not the card
 * surface: the sheets hold cards, and a card only reads as a card when the ground behind it is
 * the same ground the screen's cards sit on. Tonal elevation is zero for the same reason — it is
 * the mechanism that tints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaqwaBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTaqwaColors.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { SheetDragHandle() },
        content = content,
    )
}

/** The sheets' grab handle: a plain 36×4 dp pill in the hairline colour, matching the rest of the
 * app's hairline-drawn chrome rather than material3's default grey. */
@Composable
fun SheetDragHandle() {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .padding(vertical = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.hairline),
    )
}
