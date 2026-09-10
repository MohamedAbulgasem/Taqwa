package world.taqwa.app.city

/**
 * The folding applied to both a search query and every candidate city name (design spec §4), so
 * that a name typed without its marks still finds the city: "zurich" finds Zürich, "istanbul"
 * finds İstanbul, "thane" finds Thāne, «مكه» finds «مكة».
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
     * letters and the Arabic letter variants to their bare forms, drops apostrophes, turns
     * hyphens into spaces and collapses whitespace.
     */
    fun fold(s: String): String {
        val out = StringBuilder(s.length)
        var pendingSpace = false
        for (raw in s.lowercase()) {
            // Combining diacritics, including the U+0307 that `İ`.lowercase() produces.
            if (raw in '\u0300'..'\u036F') continue
            // Harakat, the superscript alef, and tatweel — invisible to a searcher.
            if (raw in '\u064B'..'\u0652' || raw == '\u0670' || raw == '\u0640') continue
            // Nobody types the apostrophe in Xi'an, and the bundle spells it with four different
            // characters. Dropped rather than spaced, so "xian" finds «Xi’an».
            if (APOSTROPHES.indexOf(raw) >= 0) continue
            // A hyphen is a word break to a searcher: "saint denis" has to find Saint-Denis.
            val ch = if (raw == '-' || raw == '\u2013') ' ' else raw
            if (ch.isWhitespace()) {
                if (out.isNotEmpty()) pendingSpace = true
                continue
            }
            if (pendingSpace) {
                out.append(' ')
                pendingSpace = false
            }
            // The overwhelming majority of a Latin name is already bare ASCII, and nothing below
            // this line can match one — so a 34k-name parse never walks the table for them.
            if (ch.code < 0x80) {
                out.append(ch)
                continue
            }
            val marked = MARKED.indexOf(ch)
            if (marked >= 0) {
                out.append(BARE[marked])
                continue
            }
            when (ch) {
                // Letters formed by a stroke, a bar or a shape of their own rather than by a mark:
                // NFD leaves them undecomposed, so no generated table can hold them.
                'ø' -> out.append('o')
                'ı' -> out.append('i')
                'ł' -> out.append('l')
                'đ', 'ð' -> out.append('d')
                'ħ' -> out.append('h')
                'ə' -> out.append('e')
                'ŧ' -> out.append('t')
                'ŋ' -> out.append('n')
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

    /**
     * Every lowercase Latin letter in U+00C0–U+024F and U+1E00–U+1EFF whose Unicode NFD
     * decomposition is one bare ASCII letter plus combining marks, and — at the same index in
     * [BARE] — that bare letter. Generated from the Unicode data rather than curated by hand:
     * the curated list this replaced covered 28 of the 111 marked letters that actually occur in
     * the bundled city names, so "thane" did not find Thāne, "ota" did not find Ōta and "nis" did
     * not find Niš. Grouped one bare letter per line, which is what makes 246 pairs reviewable.
     */
    private const val MARKED =
        "àáâãäåāăąǎǟǡǻȁȃȧḁạảấầẩẫậắằẳẵặ" +
        "çćĉċčḉ" +
        "èéêëēĕėęěȅȇȩḕḗḙḛḝẹẻẽếềểễệ" +
        "ìíîïĩīĭįǐȉȋḭḯỉị" +
        "ñńņňǹṅṇṉṋ" +
        "òóôõöōŏőơǒǫǭȍȏȫȭȯȱṍṏṑṓọỏốồổỗộớờởỡợ" +
        "ùúûüũūŭůűųưǔǖǘǚǜȕȗṳṵṷṹṻụủứừửữự" +
        "ýÿŷȳẏẙỳỵỷỹ" +
        "ďḋḍḏḑḓ" +
        "ĝğġģǧǵḡ" +
        "ĥȟḣḥḧḩḫẖ" +
        "ĵǰ" +
        "ķǩḱḳḵ" +
        "ĺļľḷḹḻḽ" +
        "ŕŗřȑȓṙṛṝṟ" +
        "śŝşšșṡṣṥṧṩ" +
        "ţťțṫṭṯṱẗ" +
        "ŵẁẃẅẇẉẘ" +
        "źżžẑẓẕ" +
        "ḃḅḇ" +
        "ḟ" +
        "ḿṁṃ" +
        "ṕṗ" +
        "ṽṿ" +
        "ẋẍ"

    /** [MARKED]'s bare letters, one per index. Same length, same order, by construction. */
    private const val BARE =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
        "cccccc" +
        "eeeeeeeeeeeeeeeeeeeeeeeee" +
        "iiiiiiiiiiiiiii" +
        "nnnnnnnnn" +
        "oooooooooooooooooooooooooooooooooo" +
        "uuuuuuuuuuuuuuuuuuuuuuuuuuuuuu" +
        "yyyyyyyyyy" +
        "dddddd" +
        "ggggggg" +
        "hhhhhhhh" +
        "jj" +
        "kkkkk" +
        "lllllll" +
        "rrrrrrrrr" +
        "ssssssssss" +
        "tttttttt" +
        "wwwwwww" +
        "zzzzzz" +
        "bbb" +
        "f" +
        "mmm" +
        "pp" +
        "vv" +
        "xx"

    /** ASCII, right single quote, left single quote, ʻokina, modifier apostrophe. */
    private const val APOSTROPHES = "'\u2019\u2018\u02BB\u02BC"
}
