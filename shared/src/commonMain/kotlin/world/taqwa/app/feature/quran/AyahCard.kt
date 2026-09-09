package world.taqwa.app.feature.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.drawBookmark
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.design.quran
import world.taqwa.app.quran.QuranText
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_action_bookmarked

/** Languages whose bundled translation text itself reads right-to-left (spec §5.1) — Arabic (the
 * Muyassar tafsir), Urdu and Farsi. Everything else, including the UI's own direction, is
 * irrelevant here: an English translation must read left-to-right even under an Arabic UI, and an
 * Urdu one right-to-left even under an English UI. Shared with the root's search hits (spec 2b
 * §2.1), whose translation snippet reads in the same direction. */
internal val RTL_TRANSLATION_LANGUAGES = setOf("ar", "ur", "fa")

/**
 * One ayah's card (spec §2.3): the Uthmani Arabic with the ayah roundel inline, an optional
 * transliteration line, then the translation. [text] is the ayah's own database row — never
 * retyped — and the roundel is added here at render time via [QuranText.arabicIndic], per spec
 * §5.2, rather than stored in the text itself.
 *
 * [translationLanguage] is [translation]'s own language (spec §5.1) — the transliteration is
 * always Latin script and so always left-to-right, but the translation paragraph must follow
 * whatever language it is actually written in, never the surrounding UI's direction: an Arabic UI
 * showing an English translation still reads that paragraph left-to-right, full stop at the end.
 *
 * A tap toggles a subtle selected background and, through [actions], reveals the ayah's own
 * action row below the content (spec 2b §2.4). The row is passed in rather than built here so the
 * card stays a pure rendering of one ayah: the screen owns the selection and the clipboard, share
 * sheet and bookmark calls those actions make.
 *
 * [bookmarked] draws the badge (spec 2b §2.2) whether or not the card is selected — that is what
 * makes a kept ayah findable by scrolling — while [actions] appears only under the selected one.
 * The badge is an overlay, not a row of its own, so bookmarking an ayah does not make its card
 * taller and the list never shifts under a tap on the bookmark action.
 */
@Composable
fun AyahCard(
    text: String,
    ayahNumber: Int,
    transliteration: String?,
    translation: String?,
    translationLanguage: String,
    sizeSp: Int,
    selected: Boolean,
    bookmarked: Boolean,
    actions: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val bookmarkedLabel = stringResource(Res.string.quran_action_bookmarked)
    // Selection is a state change, not a page change, so nothing about it snaps: the tint fades in
    // and the action row unfolds from under the translation, and both reverse on deselection.
    val selectedTint by animateColorAsState(
        if (selected) colors.accent.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(SELECT_MILLIS),
        label = "ayahSelectedTint",
    )
    // The screen hands in a null row the moment the card is deselected (it builds the row for the
    // selected card only), but the row has to stay composed while it shrinks away, so the last
    // non-null one is kept for the exit.
    var lastActions by remember { mutableStateOf(actions) }
    if (actions != null) lastActions = actions
    TaqwaCard(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        // The selected background belongs to the whole card, so it is this Box — not the padded
        // column inside it — that carries it.
        Box(
            Modifier
                .fillMaxWidth()
                .background(selectedTint),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        buildAnnotatedString {
                            append(text)
                            // QuranText.MARKER_SEPARATOR, not a plain space: keeps the ayah number
                            // glued to its text when wrapping, matching QuranText.withMarker's own
                            // separator (spec §5.2) from the one shared definition.
                            append(QuranText.MARKER_SEPARATOR)
                            withStyle(SpanStyle(color = colors.accent)) { append(QuranText.arabicIndic(ayahNumber)) }
                        },
                        style = TaqwaText.quran(sizeSp),
                        fontFamily = mushafFamily(),
                        color = colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (transliteration != null) {
                    Spacer(Modifier.height(6.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            transliteration,
                            style = TaqwaText.caption.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic),
                            color = colors.textTertiary,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (translation != null) {
                    Spacer(Modifier.height(8.dp))
                    val translationDirection =
                        if (translationLanguage in RTL_TRANSLATION_LANGUAGES) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides translationDirection) {
                        Text(
                            translation,
                            style = TaqwaText.caption,
                            color = colors.textSecondary,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = selected && actions != null,
                    enter = fadeIn(tween(SELECT_MILLIS)) + expandVertically(tween(SELECT_MILLIS)),
                    exit = fadeOut(tween(SELECT_MILLIS / 2)) + shrinkVertically(tween(SELECT_MILLIS)),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        CardDivider()
                        lastActions?.invoke()
                    }
                }
            }
            if (bookmarked) {
                // An overlay inside the card's own 14 dp top padding band rather than an item above
                // the Arabic: no text occupies that band (the Arabic's line box starts at y = 14 dp),
                // so the badge costs the card no height at all and a bookmarked card sits exactly as
                // tall as the same card unbookmarked. Top-*start*, so it follows the UI's direction
                // (right under an Arabic UI) rather than the Arabic text's own.
                Canvas(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 1.dp)
                        .size(14.dp)
                        .semantics { contentDescription = bookmarkedLabel },
                ) { drawBookmark(colors.accent, filled = true) }
            }
        }
    }
}

/** One duration for the selection tint and the action row, so the two read as a single motion. */
private const val SELECT_MILLIS = 220
