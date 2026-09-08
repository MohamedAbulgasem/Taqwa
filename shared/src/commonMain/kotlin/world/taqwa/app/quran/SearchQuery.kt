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
     */
    fun arabicTokens(raw: String): List<String> =
        QuranText.normaliseForSearch(raw)
            .split(' ')
            .filter { it.isNotBlank() }
}
