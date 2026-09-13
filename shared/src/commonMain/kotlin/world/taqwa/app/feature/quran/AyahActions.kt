package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import world.taqwa.app.design.components.drawSpeaker
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.feature.recitation.PlayingMark
import world.taqwa.app.design.components.drawCopy
import world.taqwa.app.design.components.drawShare
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_action_bookmark
import world.taqwa.app.resources.quran_action_bookmarked
import world.taqwa.app.resources.quran_action_copied
import world.taqwa.app.resources.quran_action_copy
import world.taqwa.app.resources.quran_action_share
import world.taqwa.app.resources.recitation_play

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
 *
 * [onCopy] answers whether text actually reached the clipboard — there is none before the surah
 * has loaded, or for an ayah it does not hold — and only a true answer turns the label into
 * "Copied", so the one piece of feedback the row gives is never a lie.
 */
@Composable
fun AyahActions(
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onCopy: () -> Boolean,
    onShare: () -> Unit,
    /** True when this ayah is the one being recited, which turns Play into a pause (spec 3a §5.2). */
    playing: Boolean = false,
    onPlay: () -> Unit = {},
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

    // See ReaderHeader: the interface's direction, not the subtree's.
    val mirrored = isRtlLocale()
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // First in the row, before bookmark (spec 3a §5.2): it is the action that does something
        // to the ayah rather than something with it, and it is the one the whole slice is for.
        AyahActionButton(
            glyph = { tint -> if (!playing) drawSpeaker(tint, pointsForward = !mirrored) },
            label = stringResource(Res.string.recitation_play),
            onClick = onPlay,
            // The equaliser is a composable, not a path, so it is drawn over the glyph slot
            // rather than inside it; the slot itself stays empty while it shows.
            overlay = if (playing) ({ PlayingMark() }) else null,
        )
        val bookmarkLabel = stringResource(
            if (bookmarked) Res.string.quran_action_bookmarked else Res.string.quran_action_bookmark,
        )
        AyahActionButton(
            glyph = { tint -> drawBookmark(tint, filled = bookmarked) },
            label = bookmarkLabel,
            onClick = onBookmark,
        )
        val copyLabel = stringResource(if (copied) Res.string.quran_action_copied else Res.string.quran_action_copy)
        AyahActionButton(
            glyph = { tint -> drawCopy(tint) },
            label = copyLabel,
            onClick = { if (onCopy()) copyTaps++ },
        )
        val shareLabel = stringResource(Res.string.quran_action_share)
        AyahActionButton(
            glyph = { tint -> drawShare(tint) },
            label = shareLabel,
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
 * [contentDescription] instead of through a caption the pill has no room for. With a [label] the
 * Text already carries the same words, so [contentDescription] is not only unnecessary there but
 * would have a screen reader say them twice — hence it defaults to null and is required, loudly,
 * only on the glyph-only path.
 */
@Composable
internal fun AyahActionButton(
    glyph: DrawScope.(Color) -> Unit,
    label: String?,
    onClick: () -> Unit,
    contentDescription: String? = null,
    /** Drawn in the glyph's 18 dp box instead of a path, for the one action whose mark moves. */
    overlay: (@Composable () -> Unit)? = null,
) {
    val colors = LocalTaqwaColors.current
    // Read out here: inside a `semantics` block the name resolves to the write-only semantics
    // property, not to this parameter.
    val description =
        if (label != null) null else requireNotNull(contentDescription) { "a glyph-only action needs a contentDescription" }
    Row(
        Modifier
            .height(44.dp)
            .then(if (label == null) Modifier.width(44.dp) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .then(if (description == null) Modifier else Modifier.semantics { this.contentDescription = description }),
        horizontalArrangement = if (label == null) Arrangement.Center else Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (overlay != null) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { overlay() }
        } else {
            Canvas(Modifier.size(18.dp)) { glyph(colors.accent) }
        }
        if (label != null) Text(label, style = TaqwaText.caption, color = colors.textSecondary)
    }
}
