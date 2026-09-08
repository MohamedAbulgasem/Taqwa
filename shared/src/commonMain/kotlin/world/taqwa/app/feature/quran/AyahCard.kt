package world.taqwa.app.feature.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.design.quran
import world.taqwa.app.quran.QuranText

/** Languages whose bundled translation text itself reads right-to-left (spec §5.1) — Arabic (the
 * Muyassar tafsir), Urdu and Farsi. Everything else, including the UI's own direction, is
 * irrelevant here: an English translation must read left-to-right even under an Arabic UI, and an
 * Urdu one right-to-left even under an English UI. */
private val RTL_TRANSLATION_LANGUAGES = setOf("ar", "ur", "fa")

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
 * A tap toggles a subtle selected background and nothing else yet — 2b adds the action row
 * (bookmark, share, copy) that a real selection is for; the state lives here, locally, so this
 * task's screen does not have to plumb a selection model through for a row that does not exist
 * yet.
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
    onClick: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    TaqwaCard(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (selected) colors.accent.copy(alpha = 0.08f) else Color.Transparent)
                .padding(14.dp),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    buildAnnotatedString {
                        append(text)
                        // U+00A0, not a plain space: keeps the ayah number glued to its text when
                        // wrapping, matching QuranText.withMarker's own separator (spec §5.2).
                        append(' ')
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
        }
    }
}
