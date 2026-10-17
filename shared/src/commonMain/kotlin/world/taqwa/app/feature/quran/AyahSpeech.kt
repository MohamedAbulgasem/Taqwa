package world.taqwa.app.feature.quran

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle

/**
 * What a screen reader gets for an ayah (accessibility round 1, design §5): its number, then the
 * translation in its own language — and never the Arabic. The Quran is recited by one of the
 * app's reciters or not at all; a phone voice reading Uthmani text is not a recitation, so the
 * Arabic is hidden from semantics wherever it is drawn and the card offers Play instead.
 *
 * The translation's run is tagged with [translationLanguage] so that a voice for that language can
 * read it. On iOS, CMP maps the tag to `UIAccessibilitySpeechAttributeLanguage`, which VoiceOver
 * honours. On Android, Compose passes it on as a `LocaleSpan` in the node's text (ui-text-android
 * 1.12.1, `AndroidAccessibilitySpannableString`). Whether TalkBack then switches voice is something
 * to observe on a device; this code cannot promise it.
 */
internal fun spokenAyah(ayahLabel: String, translation: String?, translationLanguage: String): AnnotatedString =
    buildAnnotatedString {
        append(ayahLabel)
        if (!translation.isNullOrBlank()) {
            append(". ")
            withStyle(SpanStyle(localeList = LocaleList(translationLanguage))) { append(translation) }
        }
    }
