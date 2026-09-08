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
