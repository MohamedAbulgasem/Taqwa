package world.taqwa.app.quran

/**
 * Turns what the reader typed into what the two searches need (spec 2b §2.1). Pure, so the
 * token rule and the script decision are tested without a database.
 */
object SearchQuery {
    private val ARABIC_LETTER = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]")

    fun isArabic(raw: String): Boolean = ARABIC_LETTER.containsMatchIn(raw)

    fun isLongEnough(raw: String): Boolean = raw.count { it.isLetterOrDigit() } >= 2

    /**
     * The folded tokens of an Arabic query, in typing order; empty when nothing searchable
     * survives, so the caller runs no search at all. Every token is matched as a plain substring
     * of `ayah.text_search`, so no character needs escaping: there is no query syntax left to
     * break (search stopped going through FTS5, see [QuranRepository.searchArabic]).
     *
     * Anything that is neither a letter, a digit nor whitespace becomes a separator — quotation
     * marks, the Arabic comma and full stop, brackets, the parentheses a quoted ayah is usually
     * pasted inside. `ayah.text_search` holds bare words, so a token that kept its quote would be
     * searched as `"رب"` and match nothing at all; a separator rather than nothing keeps
     * «رب،العالمين» two words instead of welding them into one. The fold runs first, so the
     * harakat this would otherwise split a word on are already gone by the time it does.
     */
    fun arabicTokens(raw: String): List<String> =
        QuranText.normaliseForSearch(raw)
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
}
