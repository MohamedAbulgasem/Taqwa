package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun ftsFoldsQuotesAndPrefixesEveryToken() {
        // Harakat and the alef wasla fold away (QuranText.normaliseForSearch); each token is a
        // quoted prefix term; tokens are joined by a space, which FTS5 reads as AND.
        assertEquals("\"الرحمن\"*", SearchQuery.fts("ٱلرَّحْمَٰنِ"))
        assertEquals("\"رب\"* \"العالمين\"*", SearchQuery.fts("  رَبِّ   ٱلْعَالَمِينَ "))
    }

    @Test
    fun ftsStripsCharactersThatWouldBreakTheMatchSyntax() {
        // Double quotes, asterisks, parentheses and colons in the raw text never reach FTS5.
        assertEquals("\"رب\"*", SearchQuery.fts("\"رب\"*():"))
    }

    @Test
    fun ftsIsNullWhenNothingSearchableRemains() {
        assertNull(SearchQuery.fts("   "))
        assertNull(SearchQuery.fts("\"\"*"))
    }

    @Test
    fun twoLettersIsTheMinimum() {
        assertFalse(SearchQuery.isLongEnough("a"))
        assertFalse(SearchQuery.isLongEnough(" ر "))
        assertTrue(SearchQuery.isLongEnough("ab"))
        assertTrue(SearchQuery.isLongEnough("رب"))
    }
}
