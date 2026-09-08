package world.taqwa.app.feature.quran

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * [text] with every occurrence of [query] wearing [style] (spec 2b §2.1: the matched substring in
 * the primary colour and semibold, the rest secondary). Matching is case-insensitive and follows
 * the same rule the translation search itself uses — a plain substring, since that search is a
 * substring match too, so what is highlighted is exactly what was matched.
 *
 * Pure and free of Compose's runtime — no `@Composable`, no colours of its own — so the rule is
 * unit-tested in `commonTest` rather than on a device. Occurrences never overlap: the scan resumes
 * after each match. An empty or blank [query] returns the text unstyled, which is what a search
 * field mid-clear hands us.
 */
internal fun highlightMatches(text: String, query: String, style: SpanStyle): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        while (cursor <= text.length) {
            val match = text.indexOf(needle, cursor, ignoreCase = true)
            if (match < 0) break
            append(text.substring(cursor, match))
            // The text's own casing, not the query's: the ayah is quoted as it is written.
            withStyle(style) { append(text.substring(match, match + needle.length)) }
            cursor = match + needle.length
        }
        append(text.substring(cursor))
    }
}
