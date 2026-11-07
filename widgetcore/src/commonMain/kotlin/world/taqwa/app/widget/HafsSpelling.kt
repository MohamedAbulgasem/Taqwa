package world.taqwa.app.widget

/**
 * What to hand the Hafs face so it draws a word's superscript alef where it belongs. A rendering
 * choice, only ever applied on the way to the screen: nothing stored changes, and copy and share
 * still hand out what is stored, which is Tanzil's text as the pipeline keeps it (U+06DF as U+0652,
 * and no basmala on the first ayah of a surah).
 *
 * Tanzil writes a superscript alef on a tatweel (U+0640 U+0670). When the tatweel runs straight
 * into some of a word's last letters, the font merges the two into one kashida glyph, and its table
 * has no right place for the alef on that glyph. On most of them the anchor is (0,0), so every
 * shaper, HarfBuzz and CoreText alike, leaves the mark on the baseline to the left of the whole
 * word. A reader reported it at 31:11, where ضَلَـٰلٍ read as ضَلَلٍ; the basmala's ٱلرَّحْمَـٰنِ is
 * one of the 1,333 words. On a noon after ب, ت or ي and on a final ى it stays above the letters but
 * hangs over the last one or runs into its vowel, and at 24:16 and 61:4 into an unreadable blot.
 *
 * Such a word is drawn with every tatweel before an alef gone, so no kashida glyph forms and it
 * never shows one stretched alef and one not. That is how Tanzil's own download without the tatweel
 * option spells those 322 words, apart from the pipeline's U+06DF. The King Fahd Complex's text for
 * this font has no tatweel there either, though it differs from them in other marks.
 *
 * Every other word stays as stored: its tatweels draw correctly, and many would change shape
 * without them. `HafsMarkPlacementDbTest`, in `:shared`, holds this rule to the font itself over the
 * whole Quran, in both directions.
 *
 * Here and not in `:shared` because the iOS widget extension links only this module.
 */
object HafsSpelling {

    private val TATWEEL = Char(0x0640)
    private val SUPERSCRIPT_ALEF = Char(0x0670)
    private val TATWEEL_BEFORE_ALEF = "$TATWEEL$SUPERSCRIPT_ALEF"

    /** The last letters the font merges with a tatweel into a glyph with no right place for the alef. */
    private const val KASHIDA_FINALS = "بتثشفقكلنى"

    fun forDrawing(text: String): String {
        if (TATWEEL_BEFORE_ALEF !in text) return text
        val drawn = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val start = i
            val space = text[i].isWhitespace()
            while (i < text.length && text[i].isWhitespace() == space) i++
            val run = text.substring(start, i)
            drawn.append(if (!space && endsOnAKashida(run)) run.replace(TATWEEL_BEFORE_ALEF, SUPERSCRIPT_ALEF.toString()) else run)
        }
        return drawn.toString()
    }

    /** Whether the word's last letter comes straight after a tatweel and alef and is one the font merges. */
    private fun endsOnAKashida(word: String): Boolean {
        var last = word.lastIndex
        while (last >= 0 && isMark(word[last])) last--
        return last >= 2 && word[last] in KASHIDA_FINALS && word[last - 1] == SUPERSCRIPT_ALEF && word[last - 2] == TATWEEL
    }

    /** Harakat and the Quranic annotation signs, everything that can sit on a letter. */
    private fun isMark(c: Char) = c == SUPERSCRIPT_ALEF || c in Char(0x064B)..Char(0x065F) || c in Char(0x06D6)..Char(0x06ED)
}
