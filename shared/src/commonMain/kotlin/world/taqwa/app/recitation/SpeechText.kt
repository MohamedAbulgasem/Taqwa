package world.taqwa.app.recitation

import world.taqwa.app.quran.TextKind

/**
 * What a phone voice reads after an ayah (read-aloud spec §3): the text the reader shows under
 * it, cleaned of what a voice should not say, and — for Tafsir al-Muyassar — without the Quran
 * words it quotes, because a phone voice never recites the Quran. Pure, so every rule is held to
 * the whole bundled database by `SpeechTextDbTest`.
 */
object SpeechText {

    /** Android's input limit is 4,000 characters; the longest bundled text is about 1,700. */
    const val MAX_CHARS = 3_900

    /** The shortest slot a translation is given on the surah's clock. */
    const val MIN_SPEECH_MS = 1_000L

    /**
     * What to read after which ayah of [surah], keyed by that ayah. Consecutive ayahs that carry
     * the same text — Diyanet and Muyassar give one text to a run of up to fourteen — are read
     * once, after the run's last ayah. An ayah whose text prepares to nothing gets no entry.
     */
    fun prepare(kind: TextKind, language: String, surah: Int, texts: Map<Int, String>): Map<Int, String> {
        if (kind == TextKind.TRANSLITERATION) return emptyMap()
        val ordered = texts.entries.sortedBy { it.key }
        val prepared = LinkedHashMap<Int, String>()
        var start = 0
        while (start < ordered.size) {
            var end = start
            while (
                end + 1 < ordered.size &&
                ordered[end + 1].key == ordered[end].key + 1 &&
                ordered[end + 1].value == ordered[start].value
            ) {
                end++
            }
            val spoken = speakable(kind, language, surah, ordered[start].key, ordered[start].value)
            if (spoken.isNotEmpty()) prepared[ordered[end].key] = spoken
            start = end + 1
        }
        return prepared
    }

    /** One ayah's text as a voice should read it; empty when nothing is left to read. */
    fun speakable(kind: TextKind, language: String, surah: Int, ayah: Int, text: String): String {
        if (kind == TextKind.TRANSLITERATION) return ""
        var s = text
        if (kind == TextKind.TAFSIR) s = dropQuotations(surah, ayah, s)
        if (language == "bn") s = repairBengali(s)
        s = STRIPPED.replace(s, "")
        return clip(tidy(s))
    }

    /**
     * How long [text] takes to read at a phone's default rate, for the surah's clock (read-aloud
     * spec §5.2). An estimate: the player scales the real reading into this slot.
     */
    fun estimateMs(text: String, language: String): Long {
        val perSecond = when (language) {
            "en" -> 16
            "fr" -> 15
            "tr", "id" -> 14
            "ar", "ur" -> 13
            "bn" -> 12
            else -> 14
        }
        return (text.length * 1_000L / perSecond).coerceAtLeast(MIN_SPEECH_MS)
    }

    // ── Tafsir al-Muyassar's quotations (spec §3.2) ──────────────────────────────────────

    private fun dropQuotations(surah: Int, ayah: Int, text: String): String {
        val opening = OPENING_LETTERS[surah]?.takeIf { ayah <= 2 }
        var s = QUOTED_VERSES.replace(text, " ")
        s = PARENTHESES.replace(s) { match ->
            val inside = match.groupValues[1]
            if (isVoweled(inside) || (opening != null && isOpening(inside, opening))) " " else match.value
        }
        if (opening != null) {
            s = LEADING_LETTERS.replace(s) { match ->
                if (isOpening(match.groupValues[1], opening)) "" else match.value
            }
        }
        return s
    }

    /** A quotation of the ayah: at least one vowel mark for every two Arabic letters. */
    private fun isVoweled(inside: String): Boolean {
        val letters = inside.count { it in 'ء'..'ي' }
        if (letters == 0) return false
        val marks = inside.count { it in 'ً'..'ْ' || it == 'ٰ' }
        return marks * 2 >= letters
    }

    /** Only the letters a surah opens with (spaces and Muyassar's `*` between them allowed). */
    private fun isOpening(inside: String, accepted: Set<String>): Boolean {
        if (inside.any { !it.isWhitespace() && it != '*' && it !in 'ء'..'ي' }) return false
        return inside.filter { it in 'ء'..'ي' } in accepted
    }

    // ── Bengali's mangled character references (spec §3.1) ──────────────────────────────

    private fun repairBengali(text: String): String = MANGLED_REFERENCE.replace(text) { match ->
        val code = match.groupValues[1].toInt()
        if (code in 0x0980..0x09FF) code.toChar().toString() else ""
    }

    // ── Tidying ──────────────────────────────────────────────────────────────────────────

    private fun tidy(text: String): String {
        var s = WHITESPACE.replace(text, " ")
        s = SPACE_BEFORE_MARK.replace(s, "$1")
        s = STACKED_MARKS.replace(s, "$1")
        s = LEADING_MARKS.replace(s, "")
        s = TRAILING_DASH.replace(s, "")
        return s.trim()
    }

    private fun clip(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val cut = text.substring(0, MAX_CHARS)
        val end = cut.indexOfLast { it in ".!?؟۔।" }
        return if (end > MAX_CHARS / 2) cut.substring(0, end + 1) else cut
    }

    /** The 29 surahs that open with disjoined letters, as Muyassar writes them. */
    private val OPENING_LETTERS: Map<Int, Set<String>> = buildMap {
        listOf(2, 3, 29, 30, 31, 32).forEach { put(it, setOf("الم")) }
        put(7, setOf("المص"))
        listOf(10, 11, 12, 14, 15).forEach { put(it, setOf("الر")) }
        put(13, setOf("المر"))
        put(19, setOf("كهيعص"))
        put(20, setOf("طه"))
        listOf(26, 28).forEach { put(it, setOf("طسم")) }
        put(27, setOf("طس"))
        put(36, setOf("يس"))
        put(38, setOf("ص"))
        listOf(40, 41, 43, 44, 45, 46).forEach { put(it, setOf("حم")) }
        put(42, setOf("حمعسق", "حم", "عسق"))
        put(50, setOf("ق"))
        put(68, setOf("ن"))
    }

    private val QUOTED_VERSES = Regex("\\{[^{}]*\\}|\\uFD3F[^\\uFD3E]*\\uFD3E")
    private val PARENTHESES = Regex("\\(([^()]*)\\)")
    private val LEADING_LETTERS = Regex("^\\s*([\\u0621-\\u064A\\s*]{1,12}?)\\s*:")
    private val MANGLED_REFERENCE = Regex("[^\\s\\u0980-\\u09FF]?#?(\\d{3,4});")
    private val STRIPPED = Regex("[\\[\\]{}\\uFD3E\\uFD3F`*]")
    private val WHITESPACE = Regex("\\s+")
    private val SPACE_BEFORE_MARK = Regex(" ([\\u060C,\\u061B;:.!?\\u061F])")
    private val STACKED_MARKS = Regex("([\\u060C,\\u061B;:.!?\\u061F])(?: ?[\\u060C,\\u061B;])+")
    private val LEADING_MARKS = Regex("^[\\s\\u060C,\\u061B;:.!?\\u061F\\-\\u2013\\u2014]+")
    private val TRAILING_DASH = Regex("\\s*[\\-\\u2013\\u2014]+\\s*$")
}
