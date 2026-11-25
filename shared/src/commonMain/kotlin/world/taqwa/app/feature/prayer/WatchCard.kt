package world.taqwa.app.feature.prayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.FocusOnceHeard
import world.taqwa.app.design.components.LocalReduceMotion
import world.taqwa.app.design.components.SettingsReveal
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.drawClose
import world.taqwa.app.design.components.drawExternalLink
import world.taqwa.app.design.reduceMotion
import world.taqwa.app.design.screenReaderRunning
import world.taqwa.app.i18n.isolated
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_watch_about
import world.taqwa.app.resources.prayer_watch_dismiss
import world.taqwa.app.resources.prayer_watch_dismissed
import world.taqwa.app.resources.prayer_watch_install_on
import world.taqwa.app.resources.prayer_watch_title_face
import world.taqwa.app.resources.prayer_watch_title_install
import world.taqwa.app.resources.prayer_watch_try_again
import world.taqwa.app.resources.prayer_watch_undo
import world.taqwa.app.resources.settings_watch_continue
import world.taqwa.app.resources.settings_watch_face
import world.taqwa.app.resources.settings_watch_install
import world.taqwa.app.resources.settings_watch_unreachable
import kotlin.time.Duration

// Home's watch card (1.7.0, Option B "Face preview"): the card, its x, the line with Undo that
// takes its place after the x, and the slot they come and go in, above the Qibla card.

/** The card's inner padding at its start, as the Qibla card's. */
private val CardStart = 16.dp

/** Between the watch and the words, as between the Qibla dial and its words. */
private val WatchGap = 12.dp

/** The x's square target at the card's top-end corner; the words stop this far from the end, so nothing sits under it. */
private val CloseTarget = 48.dp

/** The x glyph, the player bar's own. */
private val CloseGlyph = 16.dp

/** The words' column: what is left of the card's width after the watch, its gap and the x. */
private val TextInsets = CardStart + WatchPreviewSize + WatchGap + CloseTarget

private const val LINK_GLYPH = "link"

/**
 * One offer as a card in the Qibla card's metrics (Option B): the watch drawing in the dial's 52 dp
 * place, then two lines, what this is (17 sp SemiBold) over the one action (14 sp SemiBold in the
 * 4.5:1 amber, with the external-link glyph: it opens on the watch). A tap's answer takes both
 * lines' place, so the card does not jump: "Continue on your watch" over the watch's name, or
 * "Couldn't reach your watch" over "Try again".
 *
 * The whole card, x aside, is one button for a screen reader, with the whole card as its label
 * (the parts joined by ". ", the watch face's own sentence last) and the action as its click label;
 * an answer is said as it comes, and so is a step that changes in place on a card tapped since Home
 * came to the front ([WatchCardUi.Offer.tapped]): a polite live region on that one node, which is
 * what CMP 1.12.1's iOS mediator announces. The x is its own button, "Dismiss". No ripple, like
 * every surface on Home. The watch's name is isolated (FSI…PDI) in every sentence, and a blank one
 * is left out; a named install line that cannot fit one line falls back to Settings' own "Install on
 * your watch" where that one fits, and wraps where neither does, keeping the name; the lines are
 * judged at the default text size, so at a larger one a name that fitted stays and wraps. Nothing
 * is ever cut. [bodyFocus] goes on the card's button ahead of its click, for the slot to move a
 * screen reader's focus to it.
 */
@Composable
internal fun WatchCardView(
    offer: WatchCardUi.Offer,
    horizontalPadding: Dp,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    bodyFocus: Modifier = Modifier,
) {
    val colors = LocalTaqwaColors.current
    val name = offer.watchName.takeIf { it.isNotBlank() }
    val named = name?.let { stringResource(Res.string.prayer_watch_install_on, isolated(it)) }
    val generic = stringResource(Res.string.settings_watch_install)
    val about = stringResource(Res.string.prayer_watch_about)
    val dismiss = stringResource(Res.string.prayer_watch_dismiss)
    val title = when (offer.step) {
        WatchCardStep.INSTALL -> stringResource(Res.string.prayer_watch_title_install)
        WatchCardStep.ADD_FACE -> stringResource(Res.string.prayer_watch_title_face)
    }
    val faceAction = stringResource(Res.string.settings_watch_face)
    val continueOn = stringResource(Res.string.settings_watch_continue)
    val unreachable = stringResource(Res.string.settings_watch_unreachable)
    val tryAgain = stringResource(Res.string.prayer_watch_try_again)
    val actionStyle = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    TaqwaCard(Modifier.padding(horizontal = horizontalPadding)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // The step's action as drawn: the named install line where it fits one line at the
            // default text size (Option B's rule), so a name that fits there stays, and wraps, at a
            // larger one; else Settings' own words where they fit, and the name, wrapped, where
            // they would wrap as well, since the name is then lost for nothing (the review of 20
            // November: Bengali on a 360 dp phone).
            val column = with(density) { (maxWidth - TextInsets).toPx() }
            val atDefaultSize = Density(density.density, fontScale = 1f)
            val action = when (offer.step) {
                WatchCardStep.INSTALL -> named?.takeIf {
                    measurer.fitsOneLine(it, actionStyle, column, atDefaultSize) ||
                        !measurer.fitsOneLine(generic, actionStyle, column, atDefaultSize)
                } ?: generic
                WatchCardStep.ADD_FACE -> faceAction
            }
            val polite = offer.tapped || offer.answer == WatchCardAnswer.CONTINUE || offer.answer == WatchCardAnswer.UNREACHABLE
            val label = when (offer.answer) {
                WatchCardAnswer.CONTINUE -> listOfNotNull(continueOn, name?.let(::isolated)).joinToString(". ")
                WatchCardAnswer.UNREACHABLE -> "$unreachable. $tryAgain"
                null, WatchCardAnswer.ASKING -> when (offer.step) {
                    WatchCardStep.INSTALL -> "$title. ${named ?: generic}. $about"
                    WatchCardStep.ADD_FACE -> "$title. $faceAction. $about"
                }
            }
            val clickLabel = if (offer.answer == WatchCardAnswer.UNREACHABLE) tryAgain else action
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(bodyFocus)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = clickLabel,
                        onClick = onOpen,
                    )
                    // One stop that says the whole card; the lines' own reading is replaced by it.
                    .clearAndSetSemantics {
                        contentDescription = label
                        if (polite) liveRegion = LiveRegionMode.Polite
                    }
                    .padding(start = CardStart, top = 12.dp, bottom = 12.dp, end = CloseTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WatchPreview(offer.look, offer.ringed)
                Spacer(Modifier.width(WatchGap))
                Column(Modifier.weight(1f)) {
                    when (offer.answer) {
                        WatchCardAnswer.CONTINUE -> {
                            Text(continueOn, style = TaqwaText.rowLabel, color = colors.textPrimary)
                            if (name != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(name, style = TaqwaText.caption, color = colors.textSecondary)
                            }
                        }
                        WatchCardAnswer.UNREACHABLE -> {
                            Text(unreachable, style = TaqwaText.rowLabel, color = colors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            ActionLine(tryAgain, actionStyle, glyph = false)
                        }
                        null, WatchCardAnswer.ASKING -> {
                            Text(title, style = TaqwaText.rowLabel, color = colors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            ActionLine(action, actionStyle, glyph = true)
                        }
                    }
                }
            }
            CloseButton(dismiss, onDismiss, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Whether [text], a no-break space and the 1 em glyph fit one line [width] px wide in [style]. */
private fun TextMeasurer.fitsOneLine(text: String, style: TextStyle, width: Float, density: Density): Boolean {
    val line = measure("$text\u00A0", style, softWrap = false, maxLines = 1, density = density)
    return line.size.width + with(density) { style.fontSize.toPx() } <= width
}

/**
 * The card's action in the amber that reads at 4.5:1 ([world.taqwa.app.design.TaqwaColors.accentText]),
 * with the external-link glyph when [glyph]: 1 em, held to the last word by a no-break space so it
 * never sits alone on a line, its arrow leaving toward the trailing edge (the ⓘ's pattern on Home).
 */
@Composable
private fun ActionLine(text: String, style: TextStyle, glyph: Boolean) {
    val colors = LocalTaqwaColors.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Text(
        buildAnnotatedString {
            append(text)
            if (glyph) {
                append("\u00A0")
                appendInlineContent(LINK_GLYPH)
            }
        },
        style = style,
        color = colors.accentText,
        inlineContent = if (!glyph) emptyMap() else mapOf(
            LINK_GLYPH to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                Canvas(Modifier.fillMaxSize()) { drawExternalLink(colors.accentText, forward) }
            },
        ),
    )
}

/**
 * The card's x: the player bar's 16 dp cross in textSecondary, in a 48 dp target of its own at the
 * card's top-end corner (the left one under Arabic), a button of its own with no ripple.
 */
@Composable
private fun CloseButton(description: String, onDismiss: () -> Unit, modifier: Modifier) {
    val colors = LocalTaqwaColors.current
    Box(
        modifier
            .size(CloseTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onDismiss,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(CloseGlyph)) { drawClose(colors.textSecondary) }
    }
}

/**
 * What takes the card's place for six seconds after its x (the owner's Q4), or as long as the reader
 * asked ([undoWindow]): one quiet line, said as it comes, "Set up your watch any time in Settings.",
 * and Undo, a button of its own at least 56 by 44 dp, in the amber that reads at 4.5:1. Inline, in
 * the card's slot: Home has no snackbar.
 *
 * Undo sits at the line's end while it takes at most [UndoShare] of the width, and the words wrap
 * beside it; a longer Undo (Bengali's, or twice the text size on a narrow phone) goes under the
 * words, at the end, rather than leave them no room. Mirrored under Arabic and Urdu.
 *
 * Under a screen reader ([screenReader]) the focus moves to Undo once the bridge has heard of it
 * ([FocusOnceHeard]): the card the reader's focus was on has left the screen, and TalkBack would put
 * it at the top of Home, a page of swipes from Undo (the review of 20 November).
 */
@Composable
internal fun WatchCardDismissedLine(horizontalPadding: Dp, onUndo: () -> Unit, screenReader: Boolean = false) {
    val colors = LocalTaqwaColors.current
    val undoFocus = remember { FocusRequester() }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(start = CardStart, end = 4.dp),
        content = {
            Text(
                stringResource(Res.string.prayer_watch_dismissed),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = TaqwaText.caption.copy(fontSize = 13.sp),
                color = colors.textSecondary,
            )
            UndoButton(
                onUndo,
                // A clickable takes focus from a keyboard alone; for the screen reader, from here too.
                Modifier.focusRequester(undoFocus).focusProperties { if (screenReader) canFocus = true },
            )
        },
    ) { measurables, constraints ->
        val (words, undo) = measurables
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val least = 44.dp.roundToPx()
        if (undo.maxIntrinsicWidth(constraints.maxHeight) <= width * UndoShare) {
            val button = undo.measure(loose)
            val text = words.measure(loose.copy(maxWidth = width - button.width))
            val height = maxOf(text.height, button.height, least)
            layout(width, height) {
                text.placeRelative(0, (height - text.height) / 2)
                button.placeRelative(width - button.width, (height - button.height) / 2)
            }
        } else {
            val text = words.measure(loose)
            val button = undo.measure(loose)
            layout(width, text.height + button.height) {
                text.placeRelative(0, 0)
                button.placeRelative(width - button.width, text.height)
            }
        }
    }
    if (screenReader) FocusOnceHeard(undoFocus)
}

/** The most of the undo line's width Undo takes beside the words; past it, it goes under them. */
private const val UndoShare = 0.4f

/** Undo: a target of its own, 56 by 44 dp at least, no ripple, in the 4.5:1 amber; [modifier] ahead of its click. */
@Composable
private fun UndoButton(onUndo: () -> Unit, modifier: Modifier) {
    val colors = LocalTaqwaColors.current
    Box(
        modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onUndo,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.prayer_watch_undo),
            style = TaqwaText.caption.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.accentText,
        )
    }
}

/**
 * The watch card's slot, under the times card and above the Qibla card: the card while [card] is an
 * offer, the line with Undo while it is dismissed, and nothing otherwise, each with the one-time
 * card's gaps ([extraTop] more above it under a line, as the one-time card has 6 dp there, and 14 dp
 * below). They fade and fold in and out over 220 ms, still under Reduce Motion ([reducedMotion]),
 * and a card that folds keeps its words while it goes.
 *
 * The x asks for the line's time as the platform gives it for this reader ([undoWindow], from
 * [LocalAccessibilityManager]) and passes it to [onDismiss]. Under a screen reader the focus follows
 * the reader's own x and Undo: to Undo as the line comes, back to the card when Undo brings it
 * back. Never otherwise: a card that arrives takes no focus, and without a screen reader nothing
 * moves it, since moving focus scrolls the page.
 */
@Composable
internal fun WatchCardSlot(
    card: WatchCardUi,
    extraTop: Dp,
    horizontalPadding: Dp,
    onOpen: () -> Unit,
    onDismiss: (window: Duration) -> Unit,
    onUndo: () -> Unit,
    reducedMotion: Boolean = remember { reduceMotion() },
) {
    var last by remember { mutableStateOf<WatchCardUi.Offer?>(null) }
    if (card is WatchCardUi.Offer) SideEffect { last = card }
    val accessibility = LocalAccessibilityManager.current
    val screenReader = screenReaderRunning()
    val cardFocus = remember { FocusRequester() }
    // Set by a screen reader's Undo, spent once the focus is back on the card, or by a card that did
    // not come back (the watch set up meanwhile).
    var refocus by remember { mutableStateOf(false) }
    if (refocus && card !is WatchCardUi.Offer && card !is WatchCardUi.Dismissed) SideEffect { refocus = false }
    CompositionLocalProvider(LocalReduceMotion provides reducedMotion) {
        Column {
            SettingsReveal(visible = card is WatchCardUi.Offer) {
                val shown = card as? WatchCardUi.Offer ?: last
                if (shown != null) {
                    Column {
                        Spacer(Modifier.height(extraTop))
                        WatchCardView(
                            shown,
                            horizontalPadding,
                            onOpen,
                            onDismiss = { onDismiss(undoWindow(accessibility)) },
                            // A clickable takes focus from a keyboard alone; for the screen reader, from here too.
                            bodyFocus = Modifier.focusRequester(cardFocus).focusProperties { if (screenReader) canFocus = true },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (refocus && card is WatchCardUi.Offer) FocusOnceHeard(cardFocus) { refocus = false }
                }
            }
            SettingsReveal(visible = card is WatchCardUi.Dismissed) {
                Column {
                    Spacer(Modifier.height(extraTop))
                    WatchCardDismissedLine(
                        horizontalPadding,
                        onUndo = {
                            if (screenReader) refocus = true
                            onUndo()
                        },
                        screenReader = screenReader,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}
