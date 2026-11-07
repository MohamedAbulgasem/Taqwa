package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals

/** The rule on its own. The whole Quran against the font itself is HafsMarkPlacementDbTest, in `:shared`. */
class HafsSpellingTest {

    private fun text(vararg codePoints: Int) = codePoints.joinToString("") { Char(it).toString() }

    private val tatweel = Char(0x0640).toString()

    // 31:11's ضَلَـٰلٍ, the word a reader reported: a lam after the tatweel, the word's last letter.
    private val dalal = text(0x0636, 0x064E, 0x0644, 0x064E, 0x0640, 0x0670, 0x0644, 0x064D)

    @Test fun theReportedWordLosesOnlyTheTatweel() {
        assertEquals(text(0x0636, 0x064E, 0x0644, 0x064E, 0x0670, 0x0644, 0x064D), HafsSpelling.forDrawing(dalal))
    }

    @Test fun everyKashidaFinalLetterIsCaught() {
        for (letter in listOf(0x0628, 0x062A, 0x062B, 0x0634, 0x0641, 0x0642, 0x0643, 0x0644, 0x0646, 0x0649)) {
            val word = text(0x0645, 0x064E, 0x0640, 0x0670, letter, 0x064E)
            assertEquals(text(0x0645, 0x064E, 0x0670, letter, 0x064E), HafsSpelling.forDrawing(word), "U+${letter.toString(16)}")
        }
    }

    // ـٰه, ـٰم, ـٰر and the rest: the font places the alef properly on these, so they are left alone.
    @Test fun aFinalLetterTheFontHandlesIsLeftAlone() {
        for (letter in listOf(0x062D, 0x062F, 0x0631, 0x0639, 0x063A, 0x0645, 0x0647)) {
            val word = text(0x0645, 0x064E, 0x0640, 0x0670, letter, 0x064E)
            assertEquals(word, HafsSpelling.forDrawing(word), "U+${letter.toString(16)}")
        }
    }

    // ٱلصَّـٰلِحَـٰتِ: a word that qualifies loses every tatweel before an alef, not only the last, so
    // it never draws one stretched alef and one not, and equals Tanzil's own no-tatweel word.
    @Test fun aWordThatQualifiesLosesEveryTatweelBeforeAnAlef() {
        val saalihaat = text(0x0635, 0x0651, 0x064E, 0x0640, 0x0670, 0x0644, 0x0650, 0x062D, 0x064E, 0x0640, 0x0670, 0x062A, 0x0650)
        assertEquals(text(0x0635, 0x0651, 0x064E, 0x0670, 0x0644, 0x0650, 0x062D, 0x064E, 0x0670, 0x062A, 0x0650), HafsSpelling.forDrawing(saalihaat))
    }

    // One word qualifying says nothing about the next: the ayah's other words keep their tatweels.
    @Test fun itsNeighboursKeepTheirs() {
        val haadha = text(0x0647, 0x064E, 0x0640, 0x0670, 0x0630, 0x064E, 0x0627)
        val drawn = text(0x0636, 0x064E, 0x0644, 0x064E, 0x0670, 0x0644, 0x064D)
        assertEquals("$haadha $drawn $haadha", HafsSpelling.forDrawing("$haadha $dalal $haadha"))
    }

    // A hamza on a tatweel is not a tatweel before an alef: بِـَٔايَـٰتِ loses the one and keeps the other.
    @Test fun aHamzaSeatInAWordThatQualifiesStays() {
        val biAayaat = text(0x0628, 0x0650, 0x0640, 0x064E, 0x0654, 0x0627, 0x064A, 0x064E, 0x0640, 0x0670, 0x062A, 0x0650)
        assertEquals(text(0x0628, 0x0650, 0x0640, 0x064E, 0x0654, 0x0627, 0x064A, 0x064E, 0x0670, 0x062A, 0x0650), HafsSpelling.forDrawing(biAayaat))
    }

    // هَـٰذَا and ٱلظَّـٰلِمُونَ: the letter after the tatweel is not the word's last, so no kashida glyph forms.
    @Test fun aLetterInsideTheWordIsLeftAlone() {
        val haadha = text(0x0647, 0x064E, 0x0640, 0x0670, 0x0630, 0x064E, 0x0627)
        val zaalimoon = text(0x0638, 0x0651, 0x064E, 0x0640, 0x0670, 0x0644, 0x0650, 0x0645, 0x064F, 0x0648, 0x0646, 0x064E)
        assertEquals(haadha, HafsSpelling.forDrawing(haadha))
        assertEquals(zaalimoon, HafsSpelling.forDrawing(zaalimoon))
    }

    // بُهْتَـٰنٌ and ٱلرَّحْمَـٰنِ: a final noon goes wrong after ب, ت, ي on one glyph and after every
    // other letter on another, so it is caught whatever comes before it.
    @Test fun aFinalNoonIsCaughtAfterAnyLetter() {
        for (before in listOf(0x0628, 0x062A, 0x064A, 0x062B, 0x062D, 0x0633, 0x0637, 0x0645, 0x0647)) {
            val word = text(before, 0x064E, 0x0640, 0x0670, 0x0646, 0x0650)
            assertEquals(text(before, 0x064E, 0x0670, 0x0646, 0x0650), HafsSpelling.forDrawing(word), "U+${before.toString(16)}")
        }
    }

    // A word on a Mushaf row can carry a pause sign after a space, or the ayah's number after a
    // no-break space; an ayah holds many words. The word ends where the whitespace starts.
    @Test fun aWordEndsAtAnyWhitespace() {
        val next = text(0x0645, 0x0651)
        assertEquals(text(0x0636, 0x064E, 0x0644, 0x064E, 0x0670, 0x0644, 0x064D) + " " + next, HafsSpelling.forDrawing("$dalal $next"))
        assertEquals(text(0x0636, 0x064E, 0x0644, 0x064E, 0x0670, 0x0644, 0x064D) + Char(0x00A0) + text(0x0661), HafsSpelling.forDrawing(dalal + Char(0x00A0) + text(0x0661)))
    }

    @Test fun nothingButThatTatweelEverGoes() {
        val hamzaSeat = text(0x0628, 0x0650, 0x0640, 0x064E, 0x0654, 0x0627, 0x064A)
        assertEquals(hamzaSeat, HafsSpelling.forDrawing(hamzaSeat))
        // A tatweel that is not followed by the superscript alef, then one at the very end of the text.
        assertEquals("ب$tatweel", HafsSpelling.forDrawing("ب$tatweel"))
        assertEquals("", HafsSpelling.forDrawing(""))
        val once = HafsSpelling.forDrawing(dalal)
        assertEquals(once, HafsSpelling.forDrawing(once))
    }
}
