package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {
    @Test
    fun aQueryWithAnyArabicLetterIsArabic() {
        assertTrue(SearchQuery.isArabic("الرحمن"))
        assertTrue(SearchQuery.isArabic("rabb العالمين"))
        assertFalse(SearchQuery.isArabic("mercy"))
        assertFalse(SearchQuery.isArabic("2:255"))
    }

    @Test
    fun arabicTokensFoldHarakatAndSplitOnWhitespace() {
        // Harakat and the alef wasla fold away (QuranText.normaliseForSearch); runs of spaces
        // collapse, so each token is a bare word the repository matches as a substring.
        assertEquals(listOf("الرحمن"), SearchQuery.arabicTokens("ٱلرَّحْمَٰنِ"))
        assertEquals(listOf("رب", "العالمين"), SearchQuery.arabicTokens("  رَبِّ   ٱلْعَالَمِينَ "))
    }

    @Test
    fun arabicTokensDropPunctuationSoAQuotedQueryStillFindsItsAyah() {
        // Pasted-and-quoted is how a query arrives from another app; the quotes are not in
        // ayah.text_search, so a token that kept them would match nothing.
        assertEquals(listOf("رب"), SearchQuery.arabicTokens("\"رَبِّ\""))
        assertEquals(listOf("رب", "العالمين"), SearchQuery.arabicTokens("(رَبِّ ٱلْعَالَمِينَ)"))
        // Punctuation between two words separates them rather than welding them into one token.
        assertEquals(listOf("رب", "العالمين"), SearchQuery.arabicTokens("رب،العالمين"))
        assertEquals(emptyList(), SearchQuery.arabicTokens("\"\" ()"))
    }

    @Test
    fun arabicTokensAreEmptyWhenNothingSearchableRemains() {
        // No token, so the caller runs no search at all.
        assertEquals(emptyList(), SearchQuery.arabicTokens(""))
        assertEquals(emptyList(), SearchQuery.arabicTokens("   "))
    }

    @Test
    fun twoLettersIsTheMinimum() {
        assertFalse(SearchQuery.isLongEnough("a"))
        assertFalse(SearchQuery.isLongEnough(" ر "))
        assertTrue(SearchQuery.isLongEnough("ab"))
        assertTrue(SearchQuery.isLongEnough("رب"))
    }
}
