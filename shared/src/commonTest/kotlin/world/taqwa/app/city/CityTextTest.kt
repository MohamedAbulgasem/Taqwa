package world.taqwa.app.city

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every folding rule of design spec §4, in the words of the examples that motivated it. */
class CityTextTest {

    @Test
    fun turkishDottedCapitalFoldsToPlainI() {
        // `İ`.lowercase() is locale-invariant in Kotlin: "i" plus U+0307, which the
        // combining-mark strip then removes. Typing "istanbul" has to find İstanbul.
        assertEquals("istanbul", CityText.fold("İstanbul"))
        assertEquals(CityText.fold("Istanbul"), CityText.fold("İstanbul"))
    }

    @Test
    fun turkishDotlessIFoldsToPlainI() {
        assertEquals("izmir", CityText.fold("Izmır"))
    }

    @Test
    fun umlautsAndTheRestOfTheLatinMarksFoldToTheirBareLetters() {
        assertEquals(CityText.fold("Zurich"), CityText.fold("Zürich"))
        assertEquals("zurich", CityText.fold("Zürich"))
        assertEquals("malaga", CityText.fold("Málaga"))
        assertEquals("goteborg", CityText.fold("Göteborg"))
        assertEquals("sao paulo", CityText.fold("São Paulo"))
        assertEquals("nimes", CityText.fold("Nîmes"))
        assertEquals("arhus", CityText.fold("Århus"))
        assertEquals("besancon", CityText.fold("Besançon"))
        assertEquals("gaziantep", CityText.fold("Gaziantep"))
        assertEquals("sanliurfa", CityText.fold("Şanlıurfa"))
        assertEquals("nurnberg", CityText.fold("Nürnberg"))
        assertEquals("brondby", CityText.fold("Brøndby"))
        assertEquals("lod", CityText.fold("Łód"))
        assertEquals("cacak", CityText.fold("Čačak"))
        assertEquals("zilina", CityText.fold("Žilina"))
        assertEquals("dakovo", CityText.fold("Đakovo"))
    }

    @Test
    fun theLettersThatExpandToTwoDoSo() {
        assertEquals("grossenhain", CityText.fold("Großenhain"))
        assertEquals("aeroskobing", CityText.fold("Ærøskøbing"))
        assertEquals("oeuf", CityText.fold("Œuf"))
    }

    @Test
    fun combiningMarksAreStrippedEvenWhenTheyAreNotPrecomposed() {
        // "Zürich" typed as u + U+0308 rather than as ü.
        assertEquals("zurich", CityText.fold("Zürich"))
    }

    @Test
    fun arabicHarakatAreInvisibleToSearch() {
        assertEquals("مكه", CityText.fold("مَكَّة"))
        assertEquals(CityText.fold("مكة"), CityText.fold("مكه"))
    }

    @Test
    fun arabicAlefVariantsAndAlefMaksuraFold() {
        assertEquals(CityText.fold("الاسكندريه"), CityText.fold("الإسكندرية"))
        assertEquals(CityText.fold("عيسي"), CityText.fold("عيسى"))
        assertEquals("ا", CityText.fold("ٱ"))
        assertEquals("ا", CityText.fold("آ"))
    }

    @Test
    fun tatweelIsRemoved() {
        assertEquals("طرابلس", CityText.fold("طـــرابلس"))
        assertEquals(CityText.fold("طرابلس"), CityText.fold("طـرابـلس"))
    }

    @Test
    fun superscriptAlefIsRemoved() {
        assertEquals("الرحمن", CityText.fold("الرحمٰن"))
    }

    @Test
    fun whitespaceIsCollapsedAndTrimmed() {
        assertEquals("cape town", CityText.fold("  Cape   Town \n"))
        assertEquals("new york city", CityText.fold("New\tYork  City"))
    }

    @Test
    fun emptyAndBlankFoldToNothing() {
        assertEquals("", CityText.fold(""))
        assertEquals("", CityText.fold("   "))
        assertEquals("", CityText.fold("ًْ"))
    }

    @Test
    fun punctuationOtherThanApostrophesAndHyphensSurvives() {
        // Nothing else in the rules removes punctuation, so a punctuation-only query stays
        // non-empty and simply matches no city — it must not fold to "" and be read as blank.
        assertEquals("...", CityText.fold("..."))
        assertTrue(CityText.fold("...").isNotEmpty())
        assertEquals("st. johns", CityText.fold("St. John's"))
    }

    // Amended 10 September 2026 (spec §4). The bundle spells the apostrophe four different ways
    // and nobody types any of them, and a hyphen is a word break to a searcher.

    @Test
    fun apostrophesAreDroppedInAllFourSpellingsTheBundleUses() {
        assertEquals("xian", CityText.fold("Xi\u2019an"))
        assertEquals("xian", CityText.fold("Xi'an"))
        assertEquals("xian", CityText.fold("Xi\u2018an"))
        assertEquals("xian", CityText.fold("Xi\u02BBan"))
        assertEquals("xian", CityText.fold("Xi\u02BCan"))
    }

    @Test
    fun hyphensBecomeSpacesSoATwoWordQueryFindsAHyphenatedName() {
        assertEquals("saint denis", CityText.fold("Saint-Denis"))
        assertEquals(CityText.fold("Saint Denis"), CityText.fold("Saint-Denis"))
        // The en dash the data also uses.
        assertEquals("saint denis", CityText.fold("Saint\u2013Denis"))
        // And a trailing hyphen collapses with the whitespace rather than leaving a space behind.
        assertEquals("denis", CityText.fold("-Denis-"))
    }

    // The generated table (spec §4): every Latin letter whose NFD decomposition is one ASCII
    // letter plus combining marks, not the 28 that were curated by hand.

    @Test
    fun theGeneratedTableCoversTheMarkedLettersTheBundleActuallyUses() {
        assertEquals("thane", CityText.fold("Th\u0101ne"))
        assertEquals("ota", CityText.fold("\u014Cta"))
        assertEquals("nis", CityText.fold("Ni\u0161"))
        assertEquals("ha noi", CityText.fold("H\u00E0 N\u1ED9i"))
        assertEquals("wroclaw", CityText.fold("Wroc\u0142aw"))
        assertEquals("krakow", CityText.fold("Krak\u00F3w"))
        assertEquals("saidpur", CityText.fold("Sa\u00EFdpur"))
        assertEquals("bac giang", CityText.fold("B\u1EAFc Giang"))
    }

    @Test
    fun theStrokeLettersNoDecompositionCanReachAreStillFolded() {
        assertEquals("dakovo", CityText.fold("\u0110akovo"))
        assertEquals("hal", CityText.fold("\u0126al"))
        assertEquals("torshavn", CityText.fold("T\u00F3rshavn"))
        assertEquals("dd", CityText.fold("\u00D0\u00F0"))
        assertEquals("e", CityText.fold("\u018F"))
        assertEquals("t", CityText.fold("\u0166"))
        assertEquals("n", CityText.fold("\u014A"))
    }

    @Test
    fun foldingIsIdempotent() {
        val once = CityText.fold("İstanbul  Zürich مَكَّة")
        assertEquals(once, CityText.fold(once))
    }
}
