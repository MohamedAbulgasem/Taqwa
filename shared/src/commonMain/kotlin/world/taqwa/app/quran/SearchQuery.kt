package world.taqwa.app.quran

/**
 * Turns what the reader typed into what the two searches need (spec 2b §2.1). Pure, so the
 * quoting rule and the script decision are tested without a database.
 */
object SearchQuery {
    private val ARABIC_LETTER = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]")
    // Anything FTS5 gives syntax meaning to inside or around a term. A term is wrapped in double
    // quotes below, so a quote inside it is the one character that must not survive.
    private val FTS_SYNTAX = Regex("[\"*():^{}\\-+]")

    fun isArabic(raw: String): Boolean = ARABIC_LETTER.containsMatchIn(raw)

    fun isLongEnough(raw: String): Boolean = raw.count { it.isLetterOrDigit() } >= 2

    /** The FTS5 MATCH expression: every folded token as a quoted prefix term, AND-ed by
     * adjacency. Null when no token survives, so the caller runs no query at all. */
    fun fts(raw: String): String? {
        val tokens = QuranText.normaliseForSearch(FTS_SYNTAX.replace(raw, " "))
            .split(' ')
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "\"$it\"*" }
    }
}
