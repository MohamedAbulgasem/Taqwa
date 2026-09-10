package world.taqwa.app.city

/**
 * The folding applied to both a search query and every candidate city name (design spec §4), so
 * that a name typed without its marks still finds the city: "zurich" finds Zürich, "istanbul"
 * finds İstanbul, «مكه» finds «مكة».
 *
 * Pure Kotlin with no platform APIs — it has to fold identically on Android and iOS, and a
 * platform collator would not. It deliberately does not reuse `QuranText.normaliseForSearch`:
 * that one answers to the Mushaf's own annotation marks, this one to place names in seven
 * languages, and the two rulesets have no reason to move together.
 */
object CityText {

    /**
     * Lowercases (locale-invariantly, which is what turns Turkish `İ` into `i` plus a combining
     * dot), drops the combining marks that lowercasing leaves behind, maps the marked Latin
     * letters and the Arabic letter variants to their bare forms, and collapses whitespace.
     */
    fun fold(s: String): String {
        val out = StringBuilder(s.length)
        var pendingSpace = false
        for (ch in s.lowercase()) {
            // Combining diacritics, including the U+0307 that `İ`.lowercase() produces.
            if (ch in '\u0300'..'\u036F') continue
            // Harakat, the superscript alef, and tatweel — invisible to a searcher.
            if (ch in '\u064B'..'\u0652' || ch == '\u0670' || ch == '\u0640') continue
            if (ch.isWhitespace()) {
                if (out.isNotEmpty()) pendingSpace = true
                continue
            }
            if (pendingSpace) {
                out.append(' ')
                pendingSpace = false
            }
            when (ch) {
                'à', 'â', 'ä', 'á', 'ã', 'å' -> out.append('a')
                'ç', 'ć', 'č' -> out.append('c')
                'è', 'é', 'ê', 'ë' -> out.append('e')
                'ì', 'í', 'î', 'ï', 'ı' -> out.append('i')
                'ñ' -> out.append('n')
                'ò', 'ó', 'ô', 'ö', 'õ', 'ø' -> out.append('o')
                'ù', 'ú', 'û', 'ü' -> out.append('u')
                'ý', 'ÿ' -> out.append('y')
                'ş' -> out.append('s')
                'ğ' -> out.append('g')
                'ž' -> out.append('z')
                'đ' -> out.append('d')
                'ł' -> out.append('l')
                'ß' -> out.append("ss")
                'æ' -> out.append("ae")
                'œ' -> out.append("oe")
                'ٱ', 'أ', 'إ', 'آ' -> out.append('ا')
                'ى' -> out.append('ي')
                'ة' -> out.append('ه')
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
