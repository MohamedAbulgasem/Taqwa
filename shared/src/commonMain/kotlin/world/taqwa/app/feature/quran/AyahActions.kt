package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.drawBookmark
import world.taqwa.app.design.components.drawCopy
import world.taqwa.app.design.components.drawShare
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_action_bookmark
import world.taqwa.app.resources.quran_action_bookmarked
import world.taqwa.app.resources.quran_action_copied
import world.taqwa.app.resources.quran_action_copy
import world.taqwa.app.resources.quran_action_share

/** How long "Copied" stands in for the copy action's own label (spec 2b §2.3). */
private const val COPIED_LABEL_MS = 1_500L

/**
 * The selected ayah card's action row (spec 2b §2.4): bookmark, copy, share as glyph-and-label
 * buttons in the caption style, start-aligned, each its own 44 dp target. There is no confirmation
 * on any of them — the bookmark glyph filling in, and "Copied" replacing the copy label for a
 * moment, are the whole of the feedback.
 *
 * [bookmarked] comes from [ReaderUiState.Ready.bookmarked], i.e. from the store, so the glyph and
 * the label follow what was actually written rather than an optimistic local flip.
 */
@Composable
fun AyahActions(
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    // Keyed on a tap counter rather than on `copied` itself: a second tap while the label still
    // reads "Copied" has to restart the 1.5 s, and a state that is already true would not
    // re-launch the effect.
    var copyTaps by remember { mutableStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copyTaps) {
        if (copyTaps == 0) return@LaunchedEffect
        copied = true
        delay(COPIED_LABEL_MS)
        copied = false
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            glyph = { tint -> drawBookmark(tint, filled = bookmarked) },
            label = stringResource(if (bookmarked) Res.string.quran_action_bookmarked else Res.string.quran_action_bookmark),
            onClick = onBookmark,
        )
        ActionButton(
            glyph = { tint -> drawCopy(tint) },
            label = stringResource(if (copied) Res.string.quran_action_copied else Res.string.quran_action_copy),
            onClick = {
                onCopy()
                copyTaps++
            },
        )
        ActionButton(
            glyph = { tint -> drawShare(tint) },
            label = stringResource(Res.string.quran_action_share),
            onClick = onShare,
        )
    }
}

/** One action: the glyph in the accent, its label beside it, and the whole pair as the target —
 * 44 dp tall and no ripple, like every other tap target in the app. */
@Composable
private fun ActionButton(
    glyph: DrawScope.(Color) -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) { glyph(colors.accent) }
        Text(label, style = TaqwaText.caption, color = colors.textSecondary)
    }
}
