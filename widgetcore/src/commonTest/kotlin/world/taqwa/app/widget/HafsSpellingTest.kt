package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** The rules on their own. The whole Quran against the font itself is HafsMarkPlacementDbTest, in `:shared`. */
class HafsSpellingTest {

    private fun text(vararg codePoints: Int) = codePoints.joinToString("") { Char(it).toString() }

    /** The rules alone, on a text at no place in the Quran, so the table takes no part. */
    private fun draw(text: String, followedBy: String?) = HafsSpelling.forDrawing(text, followedBy, startsAt = null)

    private val tatweel = Char(0x0640).toString()

    // 31:11's ضَلَـٰلٍ, the word a reader reported.
    private val dalal = text(0x0636, 0x064E, 0x0644, 0x064E, 0x0640, 0x0670, 0x0644, 0x064D)

    // A word read after another, by its first letter: مِن, a letter that is not a throat letter.
    private val min = text(0x0645, 0x0650, 0x0646)

    // --- R2: the tatweel before a superscript alef ---

    @Test fun theReportedWordLosesItsTatweelAndItsTanwinStaggers() {
        // The tatweel goes (R2); the kasratan before مِن is staggered (R4).
        assertEquals(text(0x0636, 0x064E, 0x0644, 0x064E, 0x0670, 0x0644, 0x0656), draw(dalal, min))
    }

    // هَـٰذَا: 1.5.1 dropped the tatweel only before a final letter the font merges; every tatweel
    // directly before a superscript alef goes now, as in the Complex's text and on tanzil.net.
    @Test fun everyTatweelBeforeASuperscriptAlefGoes() {
        val haadha = text(0x0647, 0x064E, 0x0640, 0x0670, 0x0630, 0x064E, 0x0627)
        assertEquals(text(0x0647, 0x064E, 0x0670, 0x0630, 0x064E, 0x0627), draw(haadha, null))
        val saalihaat = text(0x0635, 0x0651, 0x064E, 0x0640, 0x0670, 0x0644, 0x0650, 0x062D, 0x064E, 0x0640, 0x0670, 0x062A, 0x0650)
        assertEquals(text(0x0635, 0x0651, 0x064E, 0x0670, 0x0644, 0x0650, 0x062D, 0x064E, 0x0670, 0x062A, 0x0650), draw(saalihaat, null))
    }

    // بِـَٔايَـٰتِ: a hamza on a tatweel is not a tatweel before an alef.
    @Test fun aTatweelCarryingAHamzaStays() {
        val biAayaat = text(0x0628, 0x0650, 0x0640, 0x064E, 0x0654, 0x0627, 0x064A, 0x064E, 0x0640, 0x0670, 0x062A, 0x0650)
        assertEquals(text(0x0628, 0x0650, 0x0640, 0x064E, 0x0654, 0x0627, 0x064A, 0x064E, 0x0670, 0x062A, 0x0650), draw(biAayaat, null))
        assertEquals("ب$tatweel", draw("ب$tatweel", null))
    }

    // --- R1: the sukun ---

    // ٱعْبُدُوا۟: the sounded sukun on the ع becomes the head of kha, the silent alef's sign the ring.
    @Test fun theSoundedSukunIsTheHeadOfKhaAndTheSilentSignIsTheRing() {
        val ubuduu = text(0x0671, 0x0639, 0x0652, 0x0628, 0x064F, 0x062F, 0x064F, 0x0648, 0x0627, 0x06DF)
        assertEquals(text(0x0671, 0x0639, 0x06E1, 0x0628, 0x064F, 0x062F, 0x064F, 0x0648, 0x0627, 0x0652), draw(ubuduu, null))
    }

    // بِسْمِ, the basmala's first word.
    @Test fun theBasmalasSukunIsSounded() {
        assertEquals(text(0x0628, 0x0650, 0x0633, 0x06E1, 0x0645, 0x0650), draw(text(0x0628, 0x0650, 0x0633, 0x0652, 0x0645, 0x0650), null))
    }

    // The swap is simultaneous: a silent sign becomes the ring and stays the ring.
    @Test fun aRingWrittenForTheSilentSignIsNotTurnedAgain() {
        assertEquals(text(0x0652, 0x06E1), draw(text(0x06DF, 0x0652), null))
    }

    // --- R5: iqlab ---

    // أَلِيمٌۢ, قَوْمٍۭ, سَمِيعًۢا: a tanwin straight before a small meem is its short vowel.
    @Test fun aTanwinBeforeTheSmallMeemIsItsShortVowel() {
        assertEquals(text(0x0645, 0x064F, 0x06E2), draw(text(0x0645, 0x064C, 0x06E2), min))
        assertEquals(text(0x0645, 0x0650, 0x06ED), draw(text(0x0645, 0x064D, 0x06ED), min))
        assertEquals(text(0x0639, 0x064E, 0x06E2, 0x0627), draw(text(0x0639, 0x064B, 0x06E2, 0x0627), min))
    }

    // R5 runs first: even before a throat letter, where R4 would leave a tanwin stacked, and before
    // any other letter, where R4 would stagger it, an iqlab tanwin is the short vowel.
    @Test fun iqlabWinsOverEitherFormOfTheTanwin() {
        val hamza = text(0x0623, 0x064E)
        assertEquals(text(0x0645, 0x064F, 0x06E2), draw(text(0x0645, 0x064C, 0x06E2), hamza))
        assertEquals(text(0x0645, 0x064F, 0x06E2), draw(text(0x0645, 0x064C, 0x06E2), min))
        assertEquals(text(0x0645, 0x064F, 0x06E2), draw(text(0x0645, 0x064C, 0x06E2), null))
    }

    // --- R4: the staggered tanwin ---

    @Test fun eachTanwinHasItsStaggeredForm() {
        assertEquals(text(0x062F, 0x0657, 0x0627), draw(text(0x062F, 0x064B, 0x0627), min))
        assertEquals(text(0x062F, 0x065E), draw(text(0x062F, 0x064C), min))
        assertEquals(text(0x062F, 0x0656), draw(text(0x062F, 0x064D), min))
    }

    @Test fun beforeEachThroatLetterAndAlefWaslaTheTanwinStaysStacked() {
        for (letter in listOf(0x0621, 0x0623, 0x0625, 0x0624, 0x0626, 0x0647, 0x0639, 0x062D, 0x063A, 0x062E, 0x0671)) {
            for (tanwin in listOf(0x064B, 0x064C, 0x064D)) {
                val word = text(0x062F, tanwin)
                assertEquals(word, draw("$word ${text(letter, 0x064E)}", null).substringBefore(' '), "U+${letter.toString(16)} after U+${tanwin.toString(16)}")
                assertEquals(word, draw(word, text(letter, 0x064E)), "followed by U+${letter.toString(16)}")
            }
        }
    }

    // ب, ت, ق, م, ن, ي, و, ل, ر: every other letter staggers it, ى and ا included.
    @Test fun beforeAnyOtherLetterTheTanwinStaggers() {
        for (letter in listOf(0x0628, 0x062A, 0x0642, 0x0645, 0x0646, 0x064A, 0x0648, 0x0644, 0x0631, 0x0627, 0x0649, 0x062C, 0x0633)) {
            assertEquals(text(0x062F, 0x065E), draw(text(0x062F, 0x064C), text(letter, 0x064E)), "U+${letter.toString(16)}")
        }
    }

    // رِزْقًا and هُدًى: the alef or ى after a fathatan is part of its word and not read; the
    // letter that decides is the next word's first.
    @Test fun theRestOfTheTanwinsOwnWordIsNotRead() {
        val rizqan = text(0x0631, 0x0650, 0x0632, 0x0652, 0x0642, 0x064B, 0x0627)
        assertEquals(text(0x0631, 0x0650, 0x0632, 0x06E1, 0x0642, 0x064B, 0x0627), draw(rizqan, text(0x0647, 0x064F)))
        val hudan = text(0x0647, 0x064F, 0x062F, 0x064B, 0x0649)
        assertEquals(text(0x0647, 0x064F, 0x062F, 0x0657, 0x0649), draw(hudan, min))
    }

    // Inside a text the next word decides, past whatever lies between: a pause sign after a space
    // (as the Mushaf's words carry it), ۞ and ۩, the ayah number after a no-break space.
    @Test fun marksSignsAndAyahNumbersBetweenTheWordsAreNotRead() {
        val dalun = text(0x062F, 0x064C)
        val hamza = text(0x0623, 0x064E)
        val pause = Char(0x06D6).toString()
        val hizb = Char(0x06DE).toString()
        val sajdah = Char(0x06E9).toString()
        val number = Char(0x00A0) + text(0x0661, 0x0662)
        for (between in listOf(" $pause ", " $hizb ", " $sajdah ", "$number ", " $pause$number $hizb ")) {
            assertEquals("$dalun$between$hamza", draw("$dalun$between$hamza", min), "between: $between")
            assertEquals(text(0x062F, 0x065E) + between + min, draw("$dalun$between$min", hamza), "between: $between")
        }
    }

    // Past the end of the text the first letter of followedBy decides, whatever it starts with.
    @Test fun atTheEndOfTheTextTheFollowingTextDecides() {
        val dalun = text(0x062F, 0x064C)
        assertEquals(dalun, draw(dalun, text(0x06DE, 0x0020, 0x0623, 0x064E)))
        assertEquals(text(0x062F, 0x065E), draw(dalun, text(0x06DE, 0x0020, 0x0648, 0x064E)))
        val numbered = dalun + Char(0x00A0) + text(0x0663)
        assertEquals(numbered, draw(numbered, text(0x0639, 0x064E)))
    }

    // An ayah's end: the next ayah's first letter. 2:7 ends عَظِيمٌ, and 2:8 begins وَمِنَ.
    @Test fun atAnAyahsEndTheNextAyahDecides() {
        val aziimun = text(0x0639, 0x064E, 0x0638, 0x0650, 0x064A, 0x0645, 0x064C)
        val wamina = text(0x0648, 0x064E, 0x0645, 0x0650, 0x0646, 0x064E)
        assertEquals(text(0x0639, 0x064E, 0x0638, 0x0650, 0x064A, 0x0645, 0x065E), draw(aziimun, wamina))
        val inna = text(0x0625, 0x0650, 0x0646, 0x0651, 0x064E)
        assertEquals(aziimun, draw(aziimun, inna))
    }

    // A surah's end: the next surah's basmala, which begins with ب.
    @Test fun atASurahsEndTheNextBasmalaDecides() {
        assertEquals("ب", HafsSpelling.afterLastAyahOf(2))
        assertEquals("ب", HafsSpelling.afterLastAyahOf(1))
        assertEquals("ب", HafsSpelling.afterLastAyahOf(113))
        assertEquals(text(0x062F, 0x065E), draw(text(0x062F, 0x064C), HafsSpelling.afterLastAyahOf(2)))
    }

    // Before surah 9, which has no basmala, its first ayah decides: بَرَآءَةٌ, which also begins with ب.
    @Test fun beforeAtTawbahItsFirstAyahDecides() {
        assertEquals(Char(0x0628).toString(), HafsSpelling.afterLastAyahOf(8))
        val baraa = text(0x0628, 0x064E, 0x0631, 0x064E, 0x0622)
        assertEquals(draw(text(0x062F, 0x064C), baraa), draw(text(0x062F, 0x064C), HafsSpelling.afterLastAyahOf(8)))
    }

    // 114:6 is the Quran's last ayah: nothing is read after it.
    @Test fun afterAnNasNothingIsRead() {
        assertNull(HafsSpelling.afterLastAyahOf(114))
        assertNull(HafsSpelling.afterLastAyahOf(0))
        assertEquals(text(0x062F, 0x065E), draw(text(0x062F, 0x064C), null))
    }

    // A word drawn on its own with the next word as followedBy is the same word as in its ayah:
    // the Mushaf draws word by word, the Reader ayah by ayah.
    @Test fun aWordDrawnAloneEqualsTheSameWordInItsAyah() {
        val words = listOf(
            text(0x0648, 0x064E, 0x0644, 0x064E, 0x0647, 0x064F, 0x0645, 0x0652),
            text(0x0639, 0x064E, 0x0630, 0x064E, 0x0627, 0x0628, 0x064C),
            text(0x0639, 0x064E, 0x0638, 0x0650, 0x064A, 0x0645, 0x064C) + Char(0x00A0) + text(0x0667),
        )
        val next = text(0x0648, 0x064E, 0x0645, 0x0650, 0x0646, 0x064E)
        val ayah = draw(words.joinToString(" "), next)
        val alone = words.mapIndexed { i, w -> draw(w, words.getOrNull(i + 1) ?: next) }.joinToString(" ")
        assertEquals(ayah, alone)
    }

    // --- R6: the lam-alef with hamza and madda ---

    // 2:4's وَبِٱلْـَٔاخِرَةِ: after the lam and its sukun, ـٔ َا is drawn أٓ (and the sukun is the head of kha).
    @Test fun afterALamTheHamzaOnATatweelAndItsAlefAreTheAlefWithHamzaAndMadda() {
        val wabilAakhirati = text(0x0648, 0x064E, 0x0628, 0x0650, 0x0671, 0x0644, 0x0652, 0x0640, 0x0654, 0x064E, 0x0627, 0x062E, 0x0650, 0x0631, 0x064E, 0x0629, 0x0650)
        assertEquals(
            text(0x0648, 0x064E, 0x0628, 0x0650, 0x0671, 0x0644, 0x06E1, 0x0623, 0x0653, 0x062E, 0x0650, 0x0631, 0x064E, 0x0629, 0x0650),
            draw(wabilAakhirati, min),
        )
    }

    // 5:2's شَنَـَٔانُ: the same letters after a noon are not a lam-alef, and stay.
    @Test fun theSameLettersAfterAnyOtherLetterStay() {
        val shanaanu = text(0x0634, 0x064E, 0x0646, 0x064E, 0x0640, 0x0654, 0x064E, 0x0627, 0x0646, 0x064F)
        assertEquals(shanaanu, draw(shanaanu, min))
    }

    // --- the table: words at their places taken in the Complex's spelling ---

    // 2:72's فَٱدَّٰرَْٰٔتُمْ: the hamza on the line (ء), and R1's head of kha on both sounded letters.
    private val faddaaratum = text(0x0641, 0x064E, 0x0671, 0x062F, 0x0651, 0x064E, 0x0670, 0x0631, 0x064E, 0x0670, 0x0654, 0x0652, 0x062A, 0x064F, 0x0645, 0x0652)

    @Test fun aWordAtItsPlaceIsDrawnFromTheComplexsSpelling() {
        assertEquals(
            text(0x0641, 0x064E, 0x0671, 0x062F, 0x0651, 0x064E, 0x0670, 0x0631, 0x064E, 0x0670, 0x0621, 0x06E1, 0x062A, 0x064F, 0x0645, 0x06E1),
            HafsSpelling.forDrawing(faddaaratum, min, QuranPlace(2, 72, 4)),
        )
        // 12:11's تَأْمَ۫نَّا: the ishmam sign U+06EC, which the face draws, for U+06EB, which it cannot.
        val tamannaa = text(0x062A, 0x064E, 0x0623, 0x0652, 0x0645, 0x064E, 0x06EB, 0x0646, 0x0651, 0x064E, 0x0627)
        assertEquals(text(0x062A, 0x064E, 0x0623, 0x06E1, 0x0645, 0x064E, 0x06EC, 0x0646, 0x0651, 0x064E, 0x0627), HafsSpelling.forDrawing(tamannaa, min, QuranPlace(12, 11, 6)))
        // 24:11's ٱمْرِئٍ: the seat, the hamza below it, then the rules (R4 staggers the kasratan before مِّنْهُم).
        val imri = text(0x0671, 0x0645, 0x0652, 0x0631, 0x0650, 0x0626, 0x064D)
        assertEquals(text(0x0671, 0x0645, 0x06E1, 0x0631, 0x0650, 0x064A, 0x0655, 0x0656), HafsSpelling.forDrawing(imri, min, QuranPlace(24, 11, 16)))
    }

    // The same word anywhere else, or with no place, is Tanzil's word under the rules alone.
    @Test fun theSameWordAtAnotherPlaceIsNotRespelled() {
        val rulesAlone = draw(faddaaratum, min)
        assertEquals(rulesAlone, HafsSpelling.forDrawing(faddaaratum, min, QuranPlace(2, 72, 5)))
        assertEquals(rulesAlone, HafsSpelling.forDrawing(faddaaratum, min, QuranPlace(2, 73, 4)))
        assertEquals(rulesAlone, HafsSpelling.forDrawing(faddaaratum, min, QuranPlace(3, 72, 4)))
        assertEquals(Char(0x0654).toString(), rulesAlone.filter { it.code == 0x0654 })
    }

    // A place whose word is not the table's word is a miscounted place: the word is left alone.
    @Test fun aDifferentWordAtATablePlaceIsLeftAlone() {
        assertEquals(draw(min, null), HafsSpelling.forDrawing(min, null, QuranPlace(2, 72, 4)))
    }

    // 22:18 has لَهُۥ twice; the 1405 print draws the sajdah's line over the first only (word 6).
    @Test fun theSajdahLineGoesOverItsOwnWordOnly() {
        val lahu = text(0x0644, 0x064E, 0x0647, 0x064F, 0x06E5)
        assertEquals(lahu + Char(0x06E4), HafsSpelling.forDrawing(lahu, min, QuranPlace(22, 18, 6)))
        assertEquals(lahu, HafsSpelling.forDrawing(lahu, min, QuranPlace(22, 18, 30)))
    }

    // Words are counted from the place given, a pause sign standing apart is no word, and an ayah
    // number starts the next ayah: a Mushaf line running from 13:14's last word into 13:15.
    @Test fun wordsAreCountedAcrossPauseSignsAndAyahNumbers() {
        val dalal = text(0x0636, 0x064E, 0x0644, 0x064E, 0x0640, 0x0670, 0x0644, 0x064D)
        val walillahi = text(0x0648, 0x064E, 0x0644, 0x0650, 0x0644, 0x0651, 0x064E, 0x0647, 0x0650)
        val line = dalal + Char(0x00A0) + text(0x0661, 0x0664) + " " + walillahi
        val drawn = HafsSpelling.forDrawing(line, min, QuranPlace(13, 14, 27))
        assertEquals(draw(walillahi, min) + Char(0x06E4), drawn.substringAfterLast(' '))
        // A pause sign standing before it is no word, so it is still 13:15's first.
        val afterPause = Char(0x06D6) + " " + walillahi
        assertEquals(Char(0x06D6) + " " + draw(walillahi, min) + Char(0x06E4), HafsSpelling.forDrawing(afterPause, min, QuranPlace(13, 15, 1)))
        // After another word it is the second, a place whose word is يَسْجُدُ: left alone.
        val afterWord = "$min $walillahi"
        assertEquals(draw(afterWord, min), HafsSpelling.forDrawing(afterWord, min, QuranPlace(13, 15, 1)))
    }

    // --- the sakta's seen ---

    // 75:27's مَنْ ۜ رَاقٍ and 83:14's كَلَّا ۖ بَلْ ۜ رَانَ, as Tanzil writes them.
    private val sakta = Char(0x06DC).toString()
    private val man = text(0x0645, 0x064E, 0x0646, 0x0652)
    private val raaqin = text(0x0631, 0x064E, 0x0627, 0x0642, 0x064D)
    private val kalla = text(0x0643, 0x064E, 0x0644, 0x0651, 0x064E, 0x0627)
    private val bal = text(0x0628, 0x064E, 0x0644, 0x0652)
    private val raana = text(0x0631, 0x064E, 0x0627, 0x0646, 0x064E)

    // The space before the seen goes, so the face sets it on the word's last letter, as the print
    // and the Complex's text do; the space after it stays.
    @Test fun theSaktasSeenWrittenApartJoinsTheWordBeforeIt() {
        val manJoined = text(0x0645, 0x064E, 0x0646, 0x06E1, 0x06DC)
        assertEquals(manJoined + " " + draw(raaqin, null), draw("$man $sakta $raaqin", null))
        // A Mushaf word ending an ayah, before its number (69:28's مَالِيَهْ ۜ ٢٨).
        val number = Char(0x00A0) + text(0x0662, 0x0668)
        assertEquals(manJoined + number, draw("$man $sakta$number", null))
        // Before another pause sign standing apart (36:52's مَّرْقَدِنَا ۜ ۗ), which keeps its space.
        assertEquals(manJoined + " " + Char(0x06D7), draw("$man $sakta " + Char(0x06D7), null))
    }

    @Test fun onlyTheSaktasSeenStandingApartAfterAWordIsJoined() {
        // Over a letter inside the word, as in 2:245's يَبْصُۜطُ, it has no space to lose.
        val yabsutu = text(0x064A, 0x064E, 0x0628, 0x0652, 0x0635, 0x064F, 0x06DC, 0x0637, 0x064F)
        assertEquals(text(0x064A, 0x064E, 0x0628, 0x06E1, 0x0635, 0x064F, 0x06DC, 0x0637, 0x064F), draw(yabsutu, null))
        // Every other pause sign stays where Tanzil writes it.
        assertEquals(draw(kalla, min) + " " + Char(0x06D6) + " " + draw(min, null), draw("$kalla " + Char(0x06D6) + " $min", null))
        // With no word before it there is nothing to join.
        assertEquals(" $sakta", draw(" $sakta", null))
    }

    // Search finds words by their place in the stored text and lights them in the drawn one.
    @Test fun searchPositionsMoveToTheDrawnWords() {
        val stored = listOf(kalla, Char(0x06D6).toString(), bal, sakta, raana).joinToString(" ")
        assertEquals(4, draw(stored, null).split(' ').size)
        assertEquals(setOf(0, 1), HafsSpelling.drawnWordPositions(stored, setOf(0, 1)))
        assertEquals(setOf(2), HafsSpelling.drawnWordPositions(stored, setOf(2, 3)), "بَلْ and its seen are one drawn word")
        assertEquals(setOf(3), HafsSpelling.drawnWordPositions(stored, setOf(4)), "رَانَ stands one place earlier")
        assertEquals(emptySet(), HafsSpelling.drawnWordPositions(stored, emptySet()))
        // With no seen standing apart nothing moves.
        assertEquals(setOf(0, 2), HafsSpelling.drawnWordPositions("$kalla ${Char(0x06D6)} $bal", setOf(0, 2)))
    }

    // --- the whole ---

    @Test fun nothingElseChanges() {
        assertEquals("", draw("", null))
        val plain = text(0x0627, 0x0644, 0x0644, 0x0647) + " " + text(0x0661, 0x0662) + Char(0x00A0) + text(0x06D6, 0x06DE, 0x06E9, 0x0670)
        assertEquals(plain, draw(plain, null))
    }

    // Applied exactly once: a second pass would turn the ring R1 wrote into a head of kha.
    @Test fun itIsNotIdempotentSoItIsAppliedOnceToStoredText() {
        val once = draw(text(0x0648, 0x0627, 0x06DF), null)
        assertNotEquals(once, draw(once, null))
    }
}
