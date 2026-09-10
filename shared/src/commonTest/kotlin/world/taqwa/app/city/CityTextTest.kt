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
    fun punctuationSurvivesRatherThanBeingSilentlyDropped() {
        // Nothing in the rules removes punctuation, so a punctuation-only query stays non-empty
        // and simply matches no city — it must not fold to "" and be treated as a blank query.
        assertEquals("...", CityText.fold("..."))
        assertTrue(CityText.fold("-'-").isNotEmpty())
        assertEquals("st. john's", CityText.fold("St. John's"))
    }

    @Test
    fun foldingIsIdempotent() {
        val once = CityText.fold("İstanbul  Zürich مَكَّة")
        assertEquals(once, CityText.fold(once))
    }
}
