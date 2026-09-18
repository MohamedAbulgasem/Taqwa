package world.taqwa.app.feature.quran

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * The Arabic line of a search hit with the words the query found wearing [style], opened near
 * the first of them (spec §17.4). [matchedWords] are positions among the whitespace-separated
 * words of [text], as [world.taqwa.app.quran.ArabicWordAlignment] reports them.
 *
 * **Whole words, never part of one.** The search matches a token as a substring, so «رحمن»
 * finds «ٱلرَّحْمَـٰنِ» — but the span goes round the whole written word, harakat and all. A style
 * boundary inside an Arabic word is a shaping boundary on some text engines: the letters either
 * side of it stop joining, and a Quran line with a broken word is worse than one with no
 * highlight. A boundary at a space can break nothing.
 *
 * **Opened near the match.** The row shows one line, cut at its end. A match in the fortieth
 * word of Ayat al-Kursi would be lit somewhere the reader never sees, so when the first match is
 * more than [LEAD_WORDS] words in, the line starts [LEAD_WORDS] words before it behind an
 * ellipsis. Cut at word boundaries only, and only ever for this one-line pointer to the ayah.
 *
 * Pure: no `@Composable`, no colours of its own, unit-tested in `commonTest`. No matched words —
 * a translation hit — returns the text untouched.
 */
internal fun highlightArabicWords(text: String, matchedWords: Set<Int>, style: SpanStyle): AnnotatedString {
    val words = wordRanges(text)
    val firstMatch = words.indices.firstOrNull { it in matchedWords } ?: return AnnotatedString(text)
    val firstShown = if (firstMatch > LEAD_WORDS) firstMatch - LEAD_WORDS else 0
    return buildAnnotatedString {
        if (firstShown > 0) append(ELLIPSIS)
        var cursor = words[firstShown].first
        for (i in firstShown until words.size) {
            val range = words[i]
            append(text.substring(cursor, range.first))
            val word = text.substring(range.first, range.last + 1)
            if (i in matchedWords) withStyle(style) { append(word) } else append(word)
            cursor = range.last + 1
        }
        append(text.substring(cursor))
    }
}

/** The runs of non-whitespace in [text], in order. */
private fun wordRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start = -1
    for (i in text.indices) {
        val space = text[i].isWhitespace()
        if (!space && start < 0) start = i
        if (space && start >= 0) {
            ranges += start until i
            start = -1
        }
    }
    if (start >= 0) ranges += start until text.length
    return ranges
}

/** How many words of context stay in front of the first match when the line is opened late. */
private const val LEAD_WORDS = 2

/** The ellipsis and a space that must not wrap away from the word it introduces. */
private const val ELLIPSIS = "…\u00A0"
