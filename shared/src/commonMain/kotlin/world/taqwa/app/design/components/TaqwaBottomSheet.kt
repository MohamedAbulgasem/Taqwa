package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.sheet_a11y_drag_handle

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
fun TaqwaBottomSheet(
    onDismissRequest: () -> Unit,
    /** True opens a tall sheet whole rather than half way, for a sheet whose second section would
     * otherwise start below the fold (a prayer's sound sheet with its iqama minutes). A sheet
     * shorter than half the screen opens whole either way. */
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalTaqwaColors.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { SheetDragHandle() },
        content = content,
    )
}

/** The sheets' grab handle: a plain 36×4 dp pill in the hairline colour, matching the rest of the
 * app's hairline-drawn chrome rather than material3's default grey.
 *
 * material3 wraps the handle in a clickable node that carries the sheet's dismiss and expand
 * actions and takes its name from the handle. So this handle gets the name material3's default
 * one has; without it, every sheet began with a clickable node that had no name. The name is
 * spoken only, and nothing drawn changes. */
@Composable
fun SheetDragHandle() {
    val colors = LocalTaqwaColors.current
    val name = stringResource(Res.string.sheet_a11y_drag_handle)
    Box(
        Modifier
            .padding(vertical = 12.dp)
            .semantics { contentDescription = name }
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.hairline),
    )
}
