package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        val bookmarkLabel = stringResource(
            if (bookmarked) Res.string.quran_action_bookmarked else Res.string.quran_action_bookmark,
        )
        AyahActionButton(
            glyph = { tint -> drawBookmark(tint, filled = bookmarked) },
            label = bookmarkLabel,
            contentDescription = bookmarkLabel,
            onClick = onBookmark,
        )
        val copyLabel = stringResource(if (copied) Res.string.quran_action_copied else Res.string.quran_action_copy)
        AyahActionButton(
            glyph = { tint -> drawCopy(tint) },
            label = copyLabel,
            contentDescription = copyLabel,
            onClick = {
                onCopy()
                copyTaps++
            },
        )
        val shareLabel = stringResource(Res.string.quran_action_share)
        AyahActionButton(
            glyph = { tint -> drawShare(tint) },
            label = shareLabel,
            contentDescription = shareLabel,
            onClick = onShare,
        )
    }
}

/**
 * One action: the glyph in the accent, its [label] beside it when there is one, and the whole
 * thing as the target — 44 dp and no ripple, like every other tap target in the app.
 *
 * Shared with the Mushaf's reference pill (spec 2b §2.5), which passes `label = null`: there the
 * button is a 44 dp square holding the glyph alone, so it says what it is through
 * [contentDescription] instead of through a caption the pill has no room for.
 */
@Composable
internal fun AyahActionButton(
    glyph: DrawScope.(Color) -> Unit,
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    // Read out here: inside a `semantics` block the name resolves to the write-only semantics
    // property, not to this parameter.
    val description = contentDescription
    Row(
        Modifier
            .height(44.dp)
            .then(if (label == null) Modifier.width(44.dp) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // Only without a label: with one, the Text already carries the same words and a
            // description here would have a screen reader say them twice.
            .then(if (label == null) Modifier.semantics { this.contentDescription = description } else Modifier),
        horizontalArrangement = if (label == null) Arrangement.Center else Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) { glyph(colors.accent) }
        if (label != null) Text(label, style = TaqwaText.caption, color = colors.textSecondary)
    }
}
