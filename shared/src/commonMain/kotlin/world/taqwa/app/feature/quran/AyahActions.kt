package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.floor
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
 * The selected ayah card's action row (spec 2b §2.4): play, bookmark, copy and share as
 * glyph-and-label buttons in the caption style, laid across the card's width and each its own
 * 44 dp target. There is no confirmation on any of them — the bookmark glyph filling in, and
 * "Copied" replacing the copy label for a moment, are the whole of the feedback.
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

    val playLabel = stringResource(Res.string.recitation_play)
    val bookmark = stringResource(Res.string.quran_action_bookmark)
    val bookmarkedLabel = stringResource(Res.string.quran_action_bookmarked)
    val copy = stringResource(Res.string.quran_action_copy)
    val copiedLabel = stringResource(Res.string.quran_action_copied)
    val shareLabel = stringResource(Res.string.quran_action_share)

    // The row is laid out from the words themselves (see [rememberActionRow]): one type size for
    // all four labels, and a share of the width for each action that is exactly the room its own
    // label needs at that size.
    //
    // Two simpler layouts were tried on a 360 dp phone at font scale 1.3 and neither works. The
    // original — intrinsic widths with 20 dp between them — is what the S23 caught running past
    // the card and breaking "Share" into "Shar / e". Four equal quarters fix that but leave about
    // 45 dp beside each glyph, and "Bookmarked" needs 54 dp even at the 8 sp floor: it came out
    // clipped to "Bookmar", and shrinking the short labels to a quarter's worth of type while the
    // long one still overflowed made the row look broken in a new way. "Play" never wanted a
    // quarter; giving the words the width they ask for fits all four at about 10 sp with room
    // over, at one size, which is the only version that reads as a row rather than four labels.
    // `BoxWithConstraints` because the row has to know how wide it is *before* it can decide the
    // type size, and the card's padding is not something a `Row` can be asked for.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val row = rememberActionRow(
        maxWidth,
        playLabel,
        widerOf(bookmark, bookmarkedLabel),
        widerOf(copy, copiedLabel),
        shareLabel,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // First in the row, before bookmark (spec 3a §5.2): it is the action that does something
        // to the ayah rather than something with it, and it is the one the whole slice is for.
        AyahActionButton(
            glyph = { tint -> if (!playing) drawSpeaker(tint, pointsForward = !mirrored) },
            label = playLabel,
            onClick = onPlay,
            modifier = Modifier.weight(row.shares[0]),
            labelSize = row.fontSize,
            // The equaliser is a composable, not a path, so it is drawn over the glyph slot
            // rather than inside it; the slot itself stays empty while it shows.
            overlay = if (playing) ({ PlayingMark() }) else null,
        )
        AyahActionButton(
            glyph = { tint -> drawBookmark(tint, filled = bookmarked) },
            label = if (bookmarked) bookmarkedLabel else bookmark,
            onClick = onBookmark,
            modifier = Modifier.weight(row.shares[1]),
            labelSize = row.fontSize,
        )
        AyahActionButton(
            glyph = { tint -> drawCopy(tint) },
            label = if (copied) copiedLabel else copy,
            onClick = { if (onCopy()) copyTaps++ },
            modifier = Modifier.weight(row.shares[2]),
            labelSize = row.fontSize,
        )
        AyahActionButton(
            glyph = { tint -> drawShare(tint) },
            label = shareLabel,
            onClick = onShare,
            modifier = Modifier.weight(row.shares[3]),
            labelSize = row.fontSize,
        )
    }
    }
}

/** The glyph's box and the space between it and its label, which every share has to carry. */
private val GlyphSize = 18.dp
private val GlyphGap = 6.dp

/** The floor the labels may shrink to. Below this the row would be legible to nobody. */
private val MinLabelSize = 8.sp

/** Type sizes move in half points, so a row does not re-lay out over a fraction of one. */
private const val LABEL_STEP_SP = 0.5f

/** One type size for the whole row, and the fraction of the width each action takes at it. */
private data class ActionRow(val fontSize: TextUnit, val shares: List<Float>)

/**
 * The row's arithmetic: measure the labels at the caption's own size, see how much of the width is
 * left once the four glyphs and their gaps are paid for, and take the type down in half points
 * until the words fit.
 *
 * One size for all four rather than one each. Compose can shrink a label on its own — the
 * [TextAutoSize] in [AyahActionButton] does exactly that — but per-label shrinking answers a
 * question nobody asked: it makes "Play" small and "Bookmark" large in the same row, because the
 * glyph beside a short word eats a bigger fraction of its share. The eye reads that as four
 * different things. Sizing the row once, from its longest word, keeps it one row.
 *
 * Measured through [rememberTextMeasurer] rather than counted in characters, because the words
 * change with the language and Arabic's are nothing like Latin's in width. Four short strings,
 * measured only when the words, the type or the width change.
 */
@Composable
private fun rememberActionRow(width: Dp, vararg labels: String): ActionRow {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(TaqwaText.caption)
    val density = LocalDensity.current
    return remember(style, density, width, labels.joinToString(SEPARATOR)) {
        with(density) {
            val full = labels.map {
                measurer.measure(it, style, maxLines = 1, softWrap = false).size.width.toFloat()
            }
            val fixed = (GlyphSize + GlyphGap).toPx()
            val room = width.toPx() - fixed * labels.size
            val wanted = full.sum()
            val fit = if (wanted <= 0f || room >= wanted) 1f else (room / wanted).coerceAtLeast(0f)
            val maxSp = style.fontSize.value
            val stepped = floor(maxSp * fit / LABEL_STEP_SP) * LABEL_STEP_SP
            val size = stepped.coerceIn(MinLabelSize.value, maxSp)
            ActionRow(size.sp, full.map { fixed + it * size / maxSp })
        }
    }
}

/** The longer of two labels, so a share is reserved for the widest thing an action can say. */
private fun widerOf(a: String, b: String): String = if (b.length > a.length) b else a

private const val SEPARATOR = "\u0000"

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
    modifier: Modifier = Modifier,
    /** The size the whole row settled on; see [rememberActionRow]. Unset means the caption's own. */
    labelSize: TextUnit = TextUnit.Unspecified,
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
        modifier
            .height(44.dp)
            .then(if (label == null) Modifier.width(44.dp) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .then(if (description == null) Modifier else Modifier.semantics { this.contentDescription = description }),
        horizontalArrangement = if (label == null) {
            Arrangement.Center
        } else {
            Arrangement.spacedBy(GlyphGap, Alignment.CenterHorizontally)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (overlay != null) {
            Box(Modifier.size(GlyphSize), contentAlignment = Alignment.Center) { overlay() }
        } else {
            Canvas(Modifier.size(GlyphSize)) { glyph(colors.accent) }
        }
        if (label != null) {
            // `BasicText` with `autoSize` rather than `Text`. The row has already chosen a size
            // its longest word fits at ([rememberActionRow]); this is the safety net under that
            // arithmetic — a face whose metrics measure a shade wider than they draw, a label
            // that grows in a language nobody here reads — and it costs a label a half point
            // rather than a second line. 8 sp is the floor, so it can never shrink to something
            // unreadable, and one line is the whole point.
            //
            // `LocalTextStyle` is merged by hand because `BasicText`, unlike `Text`, inherits
            // nothing: without it the caption would lose the app's face and its Arabic tracking
            // rule with it.
            val base = LocalTextStyle.current.merge(TaqwaText.caption).copy(color = colors.textSecondary)
            val style = if (labelSize.isSpecified) base.copy(fontSize = labelSize) else base
            BasicText(
                text = label,
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = MinLabelSize,
                    maxFontSize = style.fontSize,
                    stepSize = LABEL_STEP_SP.sp,
                ),
            )
        }
    }
}
