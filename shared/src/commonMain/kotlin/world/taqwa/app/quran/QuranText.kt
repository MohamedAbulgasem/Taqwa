package world.taqwa.app.quran

/**
 * Ayah-marker and search-normalisation rules for Quran text (spec §5.2).
 *
 * The end-of-ayah roundel is drawn by the Hafs font itself: a bare Arabic-Indic digit run,
 * preceded by a non-breaking space so line-wrapping never splits the ayah text from its number.
 * The ornate ayah-end glyph (U+06DD) and the word joiner (U+2060) are deliberately never used —
 * the Hafs font's own digit glyphs already render as the traditional roundel.
 */
object QuranText {

    private const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"

    /** U+00A0 non-breaking space — keeps the ayah number glued to its text when wrapping. Exposed
     * so [world.taqwa.app.feature.quran.AyahCard] and [world.taqwa.app.feature.quran.ReadingSheet]
     * share this one definition instead of each repeating the literal. */
    const val MARKER_SEPARATOR = ' '

    fun arabicIndic(n: Int): String = n.toString().map { ARABIC_INDIC[it - '0'] }.joinToString("")

    /** Spec §5.2: the Hafs font draws bare Arabic-Indic digits as the Mushaf roundel. */
    fun withMarker(text: String, ayah: Int): String = text + MARKER_SEPARATOR + arabicIndic(ayah)

    /** Splits text produced by [withMarker] back into the ayah's own text and its trailing
     * Arabic-Indic digit run (the roundel), for a caller — such as the reading-settings sheet's
     * live preview — that draws the roundel in a different colour from the rest of the line. */
    fun splitMarker(textWithMarker: String): Pair<String, String> {
        val marker = textWithMarker.takeLastWhile { it in ARABIC_INDIC }
        return textWithMarker.dropLast(marker.length) to marker
    }

    // Harakat, quranic annotation marks, and tatweel (U+0640) — all stripped for search.
    private val HARAKAT = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

    /**
     * Folds diacritics and alef variants so a search for "الحمد" also matches "ٱلْحَمْدُ": strips
     * harakat/tatweel, then normalises alef wasla (U+0671) and the hamza-bearing alef forms to
     * plain alef, and alef maksura to yaa.
     */
    fun normaliseForSearch(s: String): String = HARAKAT.replace(s, "")
        .replace('ٱ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        .replace(Regex("\\s+"), " ").trim()
}
