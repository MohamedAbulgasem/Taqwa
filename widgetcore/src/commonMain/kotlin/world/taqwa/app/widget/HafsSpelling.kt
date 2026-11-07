package world.taqwa.app.widget

/**
 * Where a text handed to [HafsSpelling.forDrawing] begins in the Quran: the surah, the ayah, and which
 * of the ayah's words is the text's first. Words are counted from 1 in the stored ayah text, a pause
 * sign, ۞ or ۩ standing apart belonging to no word; an ayah number inside the text (a Mushaf line's)
 * starts the next ayah at its word 1. This is the numbering of quran.db's `line_word` in every ayah
 * but four, where the Mushaf takes two of these words as one (بَعْدَ مَا in 2:181, 8:6 and 13:37,
 * إِلْ يَاسِينَ in 37:130), so from there on `line_word` counts one less. [HafsSpelling]'s table names
 * none of those four; an entry there would fire in one of the two counts only, which
 * `HafsMarkPlacementDbTest`'s Mushaf-against-ayah check would catch. The basmala drawn above a surah
 * is Al-Fatihah's first ayah, 1:1.
 */
data class QuranPlace(val surah: Int, val ayah: Int, val word: Int = 1)

/**
 * What to hand the Hafs face so it draws the 1405 AH Madinah Mushaf with that print's own signs.
 *
 * The stored text is Tanzil's Uthmani text, byte for byte apart from the basmala taken off the first
 * ayah of a surah, and copy and share hand it out unchanged. The face is the King Fahd Complex's
 * KFGQPC HAFS Uthmanic Script v2.2, made for the Complex's own encoding of the same Mushaf. The two
 * encodings differ in a few systematic ways that are the same in every Madinah edition, and this
 * converts them on the way to the screen, so the face draws the sign the print has. Everything that
 * is an edition choice stays Tanzil's, which follows the 1405 print the app's pages are laid out
 * from: the pause signs, the word division and the page layout.
 *
 * First, at the 44 places of [RESPELLINGS], a word is taken in the Complex's spelling: at 2:72, 2:97,
 * 52:37 and the 14 words with the hamza below its seat, the 1405 print writes the word as the Complex
 * does and the face cannot draw Tanzil's spelling that way; at 12:11 the face has no glyph for
 * Tanzil's sign, and the Complex's draws the solid dot of its current print, not the 1405 print's
 * hollow lozenge, which no Hafs face has; and on the 26 words of the 15 sajdahs it adds the line the
 * print draws over them, which Tanzil's text does not mark. Then the rules below run over the whole
 * text in one pass, R5 before R4. Counts are over the bundled Quran, each ayah drawn with what is
 * read after it; `HafsMarkPlacementDbTest` pins them, and `tools/quran-drawn-check.py` holds the
 * result against the Complex's own text word by word.
 *
 * - **R2, the superscript alef.** Every tatweel directly before a superscript alef (U+0640 U+0670)
 *   goes: all 5,924. The face merges a tatweel and some final letters into a kashida glyph with no
 *   place for the alef, and draws it on the baseline outside the word (31:11's ضَلَـٰلٍ read as
 *   ضَلَلٍ). The Complex's text has no tatweel there, and Tanzil's own site drops it on screen.
 *   1.5.1 (42) dropped 1,448 of them, in the 1,333 words where the alef went wrong; this
 *   generalises that.
 * - **R6, the lam-alef with hamza and madda.** After a lam, whatever marks it carries, a hamza on a
 *   tatweel with a fatha and an alef (U+0640 U+0654 U+064E U+0627) is drawn as an alef with hamza
 *   and madda (U+0623 U+0653), as the Complex writes it: 277 words, ٱلْـَٔاخِرَةِ among them. The face
 *   then draws the lam-alef ligature with the hamza and madda over it, the 1405 print's form, where
 *   Tanzil's spelling draws a lam, a stroke carrying the hamza and a loose alef with no madda.
 *   These are the only tatweels besides R2's that go.
 * - **R5, iqlab.** A tanwin straight before the small meem (U+06E2 high, U+06ED low) is drawn as its
 *   short vowel (U+064B → U+064E, U+064C → U+064F, U+064D → U+0650): the print shows one vowel and the
 *   meem, and a full tanwin stacked under or over the meem overprints it: 339 tanwins (240 before
 *   the high meem, 99 before the low one). It runs before R4, which would otherwise stagger them.
 * - **R4, the staggered tanwin.** Every other tanwin is drawn staggered (U+064B → U+0657,
 *   U+064C → U+065E, U+064D → U+0656, the code points the Complex writes), unless the next letter
 *   read is a throat letter (ء أ إ ؤ ئ ه ع ح غ خ) or ٱ, where the tanwin is said clearly and the print
 *   keeps it stacked. The next letter read is the first letter of the next word, whatever marks,
 *   pause signs, ۞, ۩, spaces and ayah numbers lie between; past the end of [forDrawing]'s text it is
 *   the first letter of `followedBy`. At an ayah's end that is the next ayah's first letter, at a
 *   surah's end the next surah's basmala (ب), and after surah 8 the first letter of 9:1, which has no
 *   basmala ([afterLastAyahOf]). Of the 8,893 tanwins, 6,643 are drawn staggered and 1,911 stay
 *   stacked.
 * - **R1, the sukun.** Tanzil writes a sounded sukun as U+0652 and a silent letter's sign as U+06DF.
 *   The face draws U+0652 as the ring and U+06E1 as the head of kha, which is what the print puts on
 *   a sounded letter, so U+0652 is drawn as U+06E1 and U+06DF as U+0652: 37,148 heads of kha and
 *   3,988 rings. Before 1.5.1's marks hotfix the pipeline stored every U+06DF as U+0652, so both
 *   kinds drew the ring.
 * - **The sakta's seen.** Where Tanzil writes the small seen of a sakta (U+06DC) apart, after a
 *   space (مَنْ ۜ رَاقٍ), the space goes: five places, 18:1, 36:52, 69:28, 75:27 and 83:14. After a
 *   space the face hangs the seen on the space, past the word's end and over the gap or the next
 *   word; the print, and the Complex's text, set it on the word's last letter. This is the one rule
 *   that takes anything from between two words, so a screen that lights words by their place in the
 *   stored text passes those places through [drawnWordPositions]. The other pause signs stay where
 *   Tanzil writes them.
 *
 * Not applied: ى for ي where the Complex writes ي (invisible in every word as the app lays words
 * out), and 17:7's لِيَسُـۥٓـُٔوا۟, which the face draws with the print's signs from Tanzil's spelling.
 *
 * The conversion is not idempotent (R1 turns the ring it writes into a head of kha on a second
 * pass), so it is applied exactly once, to stored text, at the point of drawing.
 *
 * Here and not in `:shared` because the iOS widget extension links only this module.
 */
object HafsSpelling {

    private const val TATWEEL = 'ـ'
    private const val SUPERSCRIPT_ALEF = 'ٰ'
    private const val SUKUN = 'ْ'
    private const val SILENT_LETTER_SIGN = '۟'
    private const val HEAD_OF_KHA = 'ۡ'
    private const val FATHATAN = 'ً'
    private const val DAMMATAN = 'ٌ'
    private const val KASRATAN = 'ٍ'
    private const val SMALL_HIGH_MEEM = 'ۢ'
    private const val SMALL_LOW_MEEM = 'ۭ'
    private const val BEH = 'ب'
    private const val LAM = 'ل'
    private const val ALEF_WASLA = 'ٱ'
    private const val SAKTA = 'ۜ'
    private const val NO_BREAK_SPACE = '\u00A0'

    /** R6: what follows the lam's marks in Tanzil's spelling, and what the face is handed instead. */
    private const val HAMZA_ON_TATWEEL_ALEF = "\u0640\u0654\u064E\u0627"
    private const val ALEF_HAMZA_MADDA = "\u0623\u0653"

    /** U+06E4, the small high madda, which the face draws as a line over the word. */
    private const val SAJDAH_LINE = "\u06E4"

    /** The throat letters, before which a tanwin is said clearly and keeps its stacked form. */
    private const val THROAT = "ءأإؤئهعحغخ"

    /**
     * [text] as the face should draw it: one word, a Mushaf line, an ayah, several ayahs or the
     * basmala. [followedBy] is what is read straight after it — the next word, the next line, the
     * next ayah, or what [afterLastAyahOf] gives at a surah's end — and only its first letter is
     * used, for R4. Null means nothing is read after it, which is true only after 114:6; with
     * nothing after it a final tanwin is staggered. [startsAt] is where [text] begins in the Quran,
     * which says whether one of its words is in [RESPELLINGS]; every screen knows it. Null only for a
     * text that is not at a place in the Quran (the rules' own tests).
     */
    fun forDrawing(text: String, followedBy: String?, startsAt: QuranPlace?): String {
        val source = if (startsAt == null) text else respelled(text, startsAt)
        val drawn = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when (c) {
                TATWEEL -> when {
                    source.getOrNull(i + 1) == SUPERSCRIPT_ALEF -> Unit
                    isLamHamzaAlef(source, i) -> {
                        drawn.append(ALEF_HAMZA_MADDA)
                        i += HAMZA_ON_TATWEEL_ALEF.length - 1
                    }
                    else -> drawn.append(c)
                }
                FATHATAN, DAMMATAN, KASRATAN -> drawn.append(tanwin(source, i, followedBy))
                SUKUN -> drawn.append(HEAD_OF_KHA)
                SILENT_LETTER_SIGN -> drawn.append(SUKUN)
                ' ' -> if (!isSpaceBeforeSakta(source, i)) drawn.append(c)
                else -> drawn.append(c)
            }
            i++
        }
        return drawn.toString()
    }

    /**
     * Where the words of [text] at [positions] stand in what [forDrawing] makes of [text]. Both are
     * positions among the whitespace-separated words, counted from 0, as `ArabicWordAlignment`
     * reports a search's matches in the stored text. They are the same except after a sakta's seen
     * that [forDrawing] joins to the word before it: the seen is then part of that word, and each
     * later word stands one place earlier. Search lights the drawn text by these.
     */
    fun drawnWordPositions(text: String, positions: Set<Int>): Set<Int> {
        if (positions.isEmpty() || text.indexOf(SAKTA) < 0) return positions
        val drawn = HashSet<Int>(positions.size)
        var stored = -1
        var drawnAt = -1
        var inWord = false
        var joined = false
        for (i in text.indices) {
            if (text[i].isWhitespace()) {
                if (isSpaceBeforeSakta(text, i)) joined = true
                inWord = false
            } else if (!inWord) {
                inWord = true
                stored++
                if (!joined) drawnAt++
                joined = false
                if (stored in positions) drawn += drawnAt
            }
        }
        return drawn
    }

    /**
     * Whether the space at [at] stands between a word and the sakta's small seen written apart after
     * it, as Tanzil writes مَنْ ۜ رَاقٍ: a word's last letter or mark before it, the seen after it,
     * and nothing more of that token.
     */
    private fun isSpaceBeforeSakta(text: String, at: Int): Boolean =
        text[at] == ' ' && at > 0 && (isLetter(text[at - 1]) || isMark(text[at - 1])) &&
            text.getOrNull(at + 1) == SAKTA &&
            (at + 2 == text.length || text[at + 2] == ' ' || text[at + 2] == NO_BREAK_SPACE)

    /**
     * What is read straight after the last ayah of [surah], as `followedBy` for [forDrawing]: the
     * next surah's basmala, or after surah 8 the first ayah of At-Tawbah, which has none. Both begin
     * with ب, and R4 reads nothing more, so this is that letter. Null after An-Nas, the Quran's end.
     */
    fun afterLastAyahOf(surah: Int): String? = if (surah in 1..113) BEH.toString() else null

    /** R6 at the tatweel at [at]: a hamza on it, a fatha and an alef, after a lam and its marks. */
    private fun isLamHamzaAlef(text: String, at: Int): Boolean {
        if (!text.startsWith(HAMZA_ON_TATWEEL_ALEF, at)) return false
        var j = at - 1
        while (j >= 0 && isMark(text[j])) j--
        return j >= 0 && text[j] == LAM
    }

    /** R5, then R4, for the tanwin at [at]. */
    private fun tanwin(text: String, at: Int, followedBy: String?): Char {
        val c = text[at]
        val next = text.getOrNull(at + 1)
        if (next == SMALL_HIGH_MEEM || next == SMALL_LOW_MEEM) {
            return when (c) {
                FATHATAN -> 'َ'
                DAMMATAN -> 'ُ'
                else -> 'ِ'
            }
        }
        val read = letterReadAfterWord(text, at) ?: followedBy?.let { firstLetter(it, 0) }
        if (read != null && (read in THROAT || read == ALEF_WASLA)) return c
        return when (c) {
            FATHATAN -> 'ٗ'
            DAMMATAN -> 'ٞ'
            else -> 'ٖ'
        }
    }

    /**
     * The first letter of the word after the one holding [at], or null when [text] ends first. The
     * rest of the tanwin's own word is passed over, since what follows a tanwin there (the alef of
     * رِزْقًا, the ى of هُدًى) is not read.
     */
    private fun letterReadAfterWord(text: String, at: Int): Char? {
        var i = at + 1
        while (i < text.length && (isLetter(text[i]) || isMark(text[i]) || text[i] == TATWEEL)) i++
        return firstLetter(text, i)
    }

    /** The first letter in [text] from [from] on: marks, signs, digits and spaces are not read. */
    private fun firstLetter(text: String, from: Int): Char? {
        for (i in from until text.length) if (isLetter(text[i])) return text[i]
        return null
    }

    /** The Arabic letters the Quran text is written in: hamza to yeh, without the tatweel, and ٱ. */
    private fun isLetter(c: Char) = c in 'ء'..'غ' || c in 'ف'..'ي' || c == ALEF_WASLA

    /** Harakat and the Quranic annotation signs, everything that can sit on a letter. */
    private fun isMark(c: Char) = c == SUPERSCRIPT_ALEF || c in 'ً'..'ٟ' || c in 'ۖ'..'ۭ'

    /** The Arabic-Indic digits of an ayah number. */
    private fun isDigit(c: Char) = c in '٠'..'٩'

    // --- the table ---

    /**
     * One word at one place drawn from the Complex's spelling: [stored] is Tanzil's word as quran.db
     * holds it, [respelled] the same word as the Complex spells it, still in Tanzil's encoding, so the
     * rules then run over it like any other word. A word that is not [stored] at its place, which
     * would mean the place was counted wrongly, is left as it is.
     */
    private class Respelling(val surah: Int, val ayah: Int, val word: Int, val stored: String, val respelled: String)

    private fun overlined(surah: Int, ayah: Int, word: Int, stored: String) = Respelling(surah, ayah, word, stored, stored + SAJDAH_LINE)

    /**
     * The words the face cannot draw from Tanzil's spelling as the print has them, each checked
     * against the print (the print-match ground truth of 7 November 2026, which viewed every one
     * beside the 1405 page font's own glyph). The 1405 print writes 43 of them as the Complex does;
     * 12:11 is the exception, drawn with the Complex's current sign (its entry says why), and the
     * sajdah words add a line Tanzil's text does not mark. None changes what is read. Copy and share
     * still give Tanzil's word.
     */
    private val RESPELLINGS: List<Respelling> = listOf(
        // 2:72, word 4, فَٱدَّٰرَْٰٔتُمْ:
        // the 1405 print writes this hamza on the line (ء), after the raa's small alef, as the
        // Complex does. The face draws Tanzil's hamza above (U+0654) raised over the raa.
        // U+0654 → U+0621.
        Respelling(2, 72, 4, "\u0641\u064E\u0671\u062F\u0651\u064E\u0670\u0631\u064E\u0670\u0654\u0652\u062A\u064F\u0645\u0652", "\u0641\u064E\u0671\u062F\u0651\u064E\u0670\u0631\u064E\u0670\u0621\u0652\u062A\u064F\u0645\u0652"),
        // 2:97, word 5, لِّجِبْرِيلَ:
        // the print joins the lam to the jeem at the baseline, the jeem's head to its left. Without a
        // connecting stroke the face draws its own lam-jeem ligature, the jeem's bar crossing back over
        // the lam, which the print does not have; the Complex writes the stroke (U+0640) after the lam's
        // shadda and kasra, and so does this.
        Respelling(2, 97, 5, "\u0644\u0651\u0650\u062C\u0650\u0628\u0652\u0631\u0650\u064A\u0644\u064E", "\u0644\u0651\u0650\u0640\u062C\u0650\u0628\u0652\u0631\u0650\u064A\u0644\u064E"),
        // 12:11, word 6, تَأْمَ۫نَّا:
        // the ishmam sign over the meem. The face has no glyph for Tanzil's U+06EB and draws a dotted
        // circle in its place. The 1405 print's hollow lozenge is in no Hafs face; U+06EC, the sign the
        // Complex writes here, draws the solid dot of the Complex's current print. The word, its reading
        // and the sign's place are unchanged: U+06EB → U+06EC.
        Respelling(12, 11, 6, "\u062A\u064E\u0623\u0652\u0645\u064E\u06EB\u0646\u0651\u064E\u0627", "\u062A\u064E\u0623\u0652\u0645\u064E\u06EC\u0646\u0651\u064E\u0627"),
        // 52:37, word 7, ٱلْمُصَۣيْطِرُونَ:
        // the small seen under the saad (the saad reading preferred). The face has no glyph for Tanzil's
        // small low seen (U+06E3) and draws a dotted circle. The Complex writes U+06DC, which the face,
        // before the fatha, draws below the saad with the fatha above it: the 1405 print's form.
        // U+06E3 → U+06DC.
        Respelling(52, 37, 7, "\u0671\u0644\u0652\u0645\u064F\u0635\u06E3\u064E\u064A\u0652\u0637\u0650\u0631\u064F\u0648\u0646\u064E", "\u0671\u0644\u0652\u0645\u064F\u0635\u06DC\u064E\u064A\u0652\u0637\u0650\u0631\u064F\u0648\u0646\u064E"),
        // The hamza below its seat, 14 words. The 1405 print draws a bare seat, a yeh's tooth (a waw at
        // 56:23), with the hamza below it and the kasra (or kasratan) below the hamza. Tanzil writes ئ (ؤ),
        // which the face draws with the hamza above the tooth and the kasra on the letter beside it; the
        // Complex writes the seat and U+0655, hamza below: U+0626 → U+064A U+0655 (U+0624 → U+0648 U+0655).
        // 10:15, word 24, تِلْقَآئِ
        Respelling(10, 15, 24, "\u062A\u0650\u0644\u0652\u0642\u064E\u0627\u0653\u0626\u0650", "\u062A\u0650\u0644\u0652\u0642\u064E\u0627\u0653\u064A\u0655\u0650"),
        // 16:90, word 6, وَإِيتَآئِ
        Respelling(16, 90, 6, "\u0648\u064E\u0625\u0650\u064A\u062A\u064E\u0627\u0653\u0626\u0650", "\u0648\u064E\u0625\u0650\u064A\u062A\u064E\u0627\u0653\u064A\u0655\u0650"),
        // 20:130, word 14, ءَانَآئِ
        Respelling(20, 130, 14, "\u0621\u064E\u0627\u0646\u064E\u0627\u0653\u0626\u0650", "\u0621\u064E\u0627\u0646\u064E\u0627\u0653\u064A\u0655\u0650"),
        // 24:11, word 16, ٱمْرِئٍ
        Respelling(24, 11, 16, "\u0671\u0645\u0652\u0631\u0650\u0626\u064D", "\u0671\u0645\u0652\u0631\u0650\u064A\u0655\u064D"),
        // 28:30, word 5, شَـٰطِئِ
        Respelling(28, 30, 5, "\u0634\u064E\u0640\u0670\u0637\u0650\u0626\u0650", "\u0634\u064E\u0640\u0670\u0637\u0650\u064A\u0655\u0650"),
        // 30:8, word 20, بِلِقَآئِ
        Respelling(30, 8, 20, "\u0628\u0650\u0644\u0650\u0642\u064E\u0627\u0653\u0626\u0650", "\u0628\u0650\u0644\u0650\u0642\u064E\u0627\u0653\u064A\u0655\u0650"),
        // 30:16, word 6, وَلِقَآئِ
        Respelling(30, 16, 6, "\u0648\u064E\u0644\u0650\u0642\u064E\u0627\u0653\u0626\u0650", "\u0648\u064E\u0644\u0650\u0642\u064E\u0627\u0653\u064A\u0655\u0650"),
        // 35:43, word 5, ٱلسَّيِّئِ
        Respelling(35, 43, 5, "\u0671\u0644\u0633\u0651\u064E\u064A\u0651\u0650\u0626\u0650", "\u0671\u0644\u0633\u0651\u064E\u064A\u0651\u0650\u064A\u0655\u0650"),
        // 42:51, word 11, وَرَآئِ
        Respelling(42, 51, 11, "\u0648\u064E\u0631\u064E\u0627\u0653\u0626\u0650", "\u0648\u064E\u0631\u064E\u0627\u0653\u064A\u0655\u0650"),
        // 52:21, word 16, ٱمْرِئٍۭ
        Respelling(52, 21, 16, "\u0671\u0645\u0652\u0631\u0650\u0626\u064D\u06ED", "\u0671\u0645\u0652\u0631\u0650\u064A\u0655\u064D\u06ED"),
        // 56:23, word 2, ٱللُّؤْلُؤِ
        Respelling(56, 23, 2, "\u0671\u0644\u0644\u0651\u064F\u0624\u0652\u0644\u064F\u0624\u0650", "\u0671\u0644\u0644\u0651\u064F\u0624\u0652\u0644\u064F\u0648\u0655\u0650"),
        // 70:38, word 3, ٱمْرِئٍ
        Respelling(70, 38, 3, "\u0671\u0645\u0652\u0631\u0650\u0626\u064D", "\u0671\u0645\u0652\u0631\u0650\u064A\u0655\u064D"),
        // 74:52, word 4, ٱمْرِئٍ
        Respelling(74, 52, 4, "\u0671\u0645\u0652\u0631\u0650\u0626\u064D", "\u0671\u0645\u0652\u0631\u0650\u064A\u0655\u064D"),
        // 80:37, word 2, ٱمْرِئٍ
        Respelling(80, 37, 2, "\u0671\u0645\u0652\u0631\u0650\u0626\u064D", "\u0671\u0645\u0652\u0631\u0650\u064A\u0655\u064D"),
        // The line over the words of a sajdah, 26 words at the 15 sajdahs: the 1405 print draws it over these
        // words, one for one the words the Complex marks with U+06E4, which the face draws as a line over the
        // word. Two more words carry it in the 1405 print only, 27:25's لِلَّهِ and 84:21's لَا; they are left
        // without it (the face cannot draw it over لَا, and 27:25 waits on the owner). ۩ marks every sajdah.
        // 7:206, word 11, يَسْجُدُونَ
        overlined(7, 206, 11, "\u064A\u064E\u0633\u0652\u062C\u064F\u062F\u064F\u0648\u0646\u064E"),
        // 13:15, word 1, وَلِلَّهِ
        overlined(13, 15, 1, "\u0648\u064E\u0644\u0650\u0644\u0651\u064E\u0647\u0650"),
        // 13:15, word 2, يَسْجُدُ
        overlined(13, 15, 2, "\u064A\u064E\u0633\u0652\u062C\u064F\u062F\u064F"),
        // 16:49, word 1, وَلِلَّهِ
        overlined(16, 49, 1, "\u0648\u064E\u0644\u0650\u0644\u0651\u064E\u0647\u0650"),
        // 16:49, word 2, يَسْجُدُ
        overlined(16, 49, 2, "\u064A\u064E\u0633\u0652\u062C\u064F\u062F\u064F"),
        // 17:107, word 16, يَخِرُّونَ
        overlined(17, 107, 16, "\u064A\u064E\u062E\u0650\u0631\u0651\u064F\u0648\u0646\u064E"),
        // 17:107, word 17, لِلْأَذْقَانِ
        overlined(17, 107, 17, "\u0644\u0650\u0644\u0652\u0623\u064E\u0630\u0652\u0642\u064E\u0627\u0646\u0650"),
        // 17:107, word 18, سُجَّدًا
        overlined(17, 107, 18, "\u0633\u064F\u062C\u0651\u064E\u062F\u064B\u0627"),
        // 19:58, word 27, خَرُّوا۟
        overlined(19, 58, 27, "\u062E\u064E\u0631\u0651\u064F\u0648\u0627\u06DF"),
        // 19:58, word 28, سُجَّدًا
        overlined(19, 58, 28, "\u0633\u064F\u062C\u0651\u064E\u062F\u064B\u0627"),
        // 22:18, word 5, يَسْجُدُ
        overlined(22, 18, 5, "\u064A\u064E\u0633\u0652\u062C\u064F\u062F\u064F"),
        // 22:18, word 6, لَهُۥ
        overlined(22, 18, 6, "\u0644\u064E\u0647\u064F\u06E5"),
        // 22:77, word 5, وَٱسْجُدُوا۟
        overlined(22, 77, 5, "\u0648\u064E\u0671\u0633\u0652\u062C\u064F\u062F\u064F\u0648\u0627\u06DF"),
        // 25:60, word 4, ٱسْجُدُوا۟
        overlined(25, 60, 4, "\u0671\u0633\u0652\u062C\u064F\u062F\u064F\u0648\u0627\u06DF"),
        // 27:25, word 1, أَلَّا
        overlined(27, 25, 1, "\u0623\u064E\u0644\u0651\u064E\u0627"),
        // 27:25, word 2, يَسْجُدُوا۟
        overlined(27, 25, 2, "\u064A\u064E\u0633\u0652\u062C\u064F\u062F\u064F\u0648\u0627\u06DF"),
        // 32:15, word 8, خَرُّوا۟
        overlined(32, 15, 8, "\u062E\u064E\u0631\u0651\u064F\u0648\u0627\u06DF"),
        // 32:15, word 9, سُجَّدًا
        overlined(32, 15, 9, "\u0633\u064F\u062C\u0651\u064E\u062F\u064B\u0627"),
        // 38:24, word 30, وَخَرَّ
        overlined(38, 24, 30, "\u0648\u064E\u062E\u064E\u0631\u0651\u064E"),
        // 38:24, word 31, رَاكِعًا
        overlined(38, 24, 31, "\u0631\u064E\u0627\u0643\u0650\u0639\u064B\u0627"),
        // 41:37, word 12, وَٱسْجُدُوا۟
        overlined(41, 37, 12, "\u0648\u064E\u0671\u0633\u0652\u062C\u064F\u062F\u064F\u0648\u0627\u06DF"),
        // 41:37, word 13, لِلَّهِ
        overlined(41, 37, 13, "\u0644\u0650\u0644\u0651\u064E\u0647\u0650"),
        // 53:62, word 1, فَٱسْجُدُوا۟
        overlined(53, 62, 1, "\u0641\u064E\u0671\u0633\u0652\u062C\u064F\u062F\u064F\u0648\u0627\u06DF"),
        // 53:62, word 2, لِلَّهِ
        overlined(53, 62, 2, "\u0644\u0650\u0644\u0651\u064E\u0647\u0650"),
        // 84:21, word 6, يَسْجُدُونَ
        overlined(84, 21, 6, "\u064A\u064E\u0633\u0652\u062C\u064F\u062F\u064F\u0648\u0646\u064E"),
        // 96:19, word 4, وَٱسْجُدْ
        overlined(96, 19, 4, "\u0648\u064E\u0671\u0633\u0652\u062C\u064F\u062F\u0652"),
    )

    private fun key(surah: Int, ayah: Int, word: Int) = (surah * 1_000 + ayah) * 1_000 + word

    private val BY_PLACE: Map<Int, Respelling> = RESPELLINGS.associateBy { key(it.surah, it.ayah, it.word) }
    private val SURAHS: Set<Int> = RESPELLINGS.mapTo(HashSet()) { it.surah }

    /**
     * [text] with each word [RESPELLINGS] names at its place in the Complex's spelling, counting
     * words from [start] ([QuranPlace]). The same string when none is in it, which is nearly always.
     */
    private fun respelled(text: String, start: QuranPlace): String {
        if (start.surah !in SURAHS) return text
        var ayah = start.ayah
        var word = start.word - 1
        var out: StringBuilder? = null
        var from = 0
        while (from <= text.length) {
            var to = from
            while (to < text.length && text[to] != ' ' && text[to] != NO_BREAK_SPACE) to++
            var letters = false
            var digits = false
            for (k in from until to) {
                if (isLetter(text[k])) letters = true else if (isDigit(text[k])) digits = true
            }
            val entry = if (letters) BY_PLACE[key(start.surah, ayah, ++word)] else null
            if (digits && !letters) {
                ayah++
                word = 0
            }
            if (entry != null && to - from == entry.stored.length && text.startsWith(entry.stored, from)) {
                if (out == null) out = StringBuilder(text.length + 4).append(text, 0, from)
                out.append(entry.respelled)
            } else {
                out?.append(text, from, to)
            }
            if (to < text.length) out?.append(text[to])
            from = to + 1
        }
        return out?.toString() ?: text
    }
}
