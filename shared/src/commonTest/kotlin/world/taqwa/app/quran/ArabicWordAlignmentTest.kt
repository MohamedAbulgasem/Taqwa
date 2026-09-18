package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class ArabicWordAlignmentTest {
    private fun matched(uthmani: String, plain: String, query: String): List<String> {
        val words = uthmani.split(" ")
        return ArabicWordAlignment.matchedWords(uthmani, plain, SearchQuery.arabicTokens(query)).sorted().map { words[it] }
    }

    @Test
    fun `a dagger alef word is found by its plain spelling`() {
        assertEquals(
            listOf("رَبِّ", "ٱلْعَـٰلَمِينَ"),
            matched("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ", "الحمد لله رب العالمين", "رب العالمين"),
        )
    }

    @Test
    fun `a waw that stands for an alef is found by its plain spelling`() {
        assertEquals(
            listOf("ٱلصَّلَوٰةَ"),
            matched("وَيُقِيمُونَ ٱلصَّلَوٰةَ وَمِمَّا", "ويقيمون الصلاة ومما", "الصلاة"),
        )
    }

    @Test
    fun `a hamza query finds its word`() {
        assertEquals(
            listOf("إِيَّاكَ", "وَإِيَّاكَ"),
            matched("إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", "إياك نعبد وإياك نستعين", "إياك"),
        )
    }

    @Test
    fun `two plain words written as one light that one word`() {
        assertEquals(
            listOf("يَـٰٓأَيُّهَا"),
            matched("يَـٰٓأَيُّهَا ٱلنَّاسُ ٱعْبُدُوا۟", "يا أيها الناس اعبدوا", "أيها"),
        )
    }

    @Test
    fun `the words after a joined pair stay in step`() {
        assertEquals(
            listOf("ٱلنَّاسُ"),
            matched("يَـٰٓأَيُّهَا ٱلنَّاسُ ٱعْبُدُوا۟", "يا أيها الناس اعبدوا", "الناس"),
        )
    }

    @Test
    fun `a free-standing pause mark keeps its place in both texts`() {
        assertEquals(
            listOf("فِيهِ"),
            matched("لَا رَيْبَ ۛ فِيهِ ۛ هُدًى", "لا ريب ۛ فيه ۛ هدى", "فيه"),
        )
    }

    @Test
    fun `texts that cannot be walked together light nothing`() {
        assertEquals(emptyList(), matched("ٱلْحَمْدُ لِلَّهِ", "الحمد لله رب العالمين", "الحمد"))
    }

    @Test
    fun `no tokens light nothing`() {
        assertEquals(emptySet(), ArabicWordAlignment.matchedWords("ٱلْحَمْدُ", "الحمد", emptyList()))
    }
}
