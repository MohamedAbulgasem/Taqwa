package world.taqwa.app.quran

/**
 * Finds, in the Uthmani text the reader sees, the words an Arabic search matched in the plain
 * text it actually searched (spec §17.4).
 *
 * The two are different spellings of the same ayah, not one text with and without its marks:
 * «ٱلْعَـٰلَمِينَ» is searched as «العالمين», «ٱلصَّلَوٰةَ» as «الصلاة», «يَـٰٓأَيُّهَا» as the two
 * words «يا أيها». No fold of the Uthmani word gives the plain one back, so the words are paired
 * by walking both texts together instead, comparing each pair on a *skeleton* — the letters left
 * once the marks, every alef, hamza, waw and yaa, and doubled letters are gone — which is the
 * part of a word the two orthographies agree on. Where one text writes as one word what the
 * other writes as two or three, the skeletons of the run are joined and compared.
 *
 * `ArabicSearchDbTest` walks all 6,236 ayahs of the bundled database and holds every one of them
 * to ending both texts together. An ayah that did not would return no words at all: a hit with
 * nothing lit is a smaller fault than a hit with the wrong word lit.
 */
object ArabicWordAlignment {

    /**
     * The indices, among the whitespace-separated words of [uthmani], of the words that spell a
     * word of [plain] containing any of [tokens] (folded, as [SearchQuery.arabicTokens] gives them).
     */
    fun matchedWords(uthmani: String, plain: String, tokens: List<String>): Set<Int> {
        if (tokens.isEmpty()) return emptySet()
        val u = words(uthmani)
        val p = words(plain)
        val pairs = align(u.map(::skeleton), p.map(::skeleton)) ?: return emptySet()
        val matched = mutableSetOf<Int>()
        p.forEachIndexed { i, word ->
            val folded = QuranText.normaliseForSearch(word)
            if (folded.isNotEmpty() && tokens.any { folded.contains(it) }) matched += pairs[i]
        }
        return matched
    }

    /** Whether the two spellings of one ayah can be walked to their ends together. */
    internal fun aligns(uthmani: String, plain: String): Boolean =
        align(words(uthmani).map(::skeleton), words(plain).map(::skeleton)) != null

    private fun words(text: String): List<String> = text.split(WHITESPACE).filter { it.isNotEmpty() }

    /** For each plain word, the Uthmani words that spell it; null when the walk does not end together. */
    private fun align(u: List<String>, p: List<String>): List<List<Int>>? {
        val pairs = ArrayList<List<Int>>(p.size)
        var i = 0
        var j = 0
        while (i < p.size && j < u.size) {
            val plainRun = (1..MAX_RUN).firstOrNull { n -> i + n <= p.size && n > 1 && p.joined(i, n) == u[j] }
            val uthmaniRun = (2..MAX_RUN).firstOrNull { n -> j + n <= u.size && u.joined(j, n) == p[i] }
            when {
                p[i] == u[j] -> { pairs += listOf(j); i++; j++ }
                plainRun != null -> { repeat(plainRun) { pairs += listOf(j) }; i += plainRun; j++ }
                uthmaniRun != null -> { pairs += (j until j + uthmaniRun).toList(); i++; j += uthmaniRun }
                // The skeletons disagree — «وَيَبْصُۜطُ» for «ويبسط», «وَأَلَّوِ» for «وأن لو». Look one
                // word ahead for where the texts meet again, and pair what lies before it.
                i + 2 < p.size && j + 1 < u.size && p[i + 1] != u[j + 1] && p[i + 2] == u[j + 1] -> {
                    repeat(2) { pairs += listOf(j) }; i += 2; j++
                }
                else -> { pairs += listOf(j); i++; j++ }
            }
        }
        return if (i == p.size && j == u.size) pairs else null
    }

    private fun List<String>.joined(from: Int, count: Int): String =
        squeeze(subList(from, from + count).joinToString(""))

    private fun skeleton(word: String): String =
        squeeze(word.filterNot { it in WEAK || MARKS.matches(it.toString()) })

    /** Doubled letters once: a shadda in one text is a letter written twice in the other. */
    private fun squeeze(s: String): String = buildString {
        for (c in s) if (isEmpty() || last() != c) append(c)
    }

    private val WHITESPACE = Regex("\\s+")
    private val MARKS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private const val WEAK = "اٱأإآءؤئىيو"
    private const val MAX_RUN = 3
}
