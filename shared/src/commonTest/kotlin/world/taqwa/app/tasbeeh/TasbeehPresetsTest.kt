package world.taqwa.app.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasbeehPresetsTest {

    /**
     * Every built-in counts to a hundred. The three singles that used to carry the post-prayer
     * set's 33 · 33 · 34 out of the set were the one place a chip silently changed the target,
     * and 33 is a third of a set rather than a count anything is said on its own.
     */
    @Test
    fun theSevenBuiltInsAreInSpecOrderAndEveryOneOfThemCountsToAHundred() {
        assertEquals(
            listOf(
                "after_prayer" to 100,
                "subhanallah" to 100,
                "alhamdulillah" to 100,
                "allahu_akbar" to 100,
                "astaghfirullah" to 100,
                "la_ilaha_illallah" to 100,
                "subhanallahi_wa_bihamdihi" to 100,
            ),
            TasbeehPresets.builtIn.map { it.id to it.total },
        )
    }

    /** The 33 · 33 · 34 survives in the one place it belongs: inside the post-prayer set. */
    @Test
    fun onlyThePostPrayerSetIsSplitIntoThirds() {
        assertEquals(
            listOf(listOf(33, 33, 34)),
            TasbeehPresets.builtIn.map { preset -> preset.parts.map { it.count } }.filter { it.size > 1 },
        )
        assertEquals(
            listOf(100, 100, 100, 100, 100, 100),
            TasbeehPresets.builtIn.filter { it.parts.size == 1 }.map { it.total },
        )
    }

    @Test
    fun theDefaultIsThePostPrayerSetOfThreeParts() {
        assertEquals("after_prayer", TasbeehPresets.DEFAULT_ID)
        val parts = TasbeehPresets.default.parts
        assertEquals(listOf(33, 33, 34), parts.map { it.count })
        assertEquals(listOf("subhanallah", "alhamdulillah", "allahu_akbar"), parts.map { it.dhikr.id })
    }

    @Test
    fun everyBuiltInCarriesArabicAndItsEnglishPair() {
        TasbeehPresets.builtIn.flatMap { it.parts }.forEach { part ->
            assertTrue(part.dhikr.arabic.isNotBlank(), "arabic missing for ${part.dhikr.id}")
            assertTrue(!part.dhikr.transliteration.isNullOrBlank(), "transliteration missing for ${part.dhikr.id}")
            assertTrue(!part.dhikr.meaning.isNullOrBlank(), "meaning missing for ${part.dhikr.id}")
        }
        assertTrue(TasbeehPresets.builtIn.none { it.custom })
    }

    @Test
    fun byIdFindsBuiltInsAndCustomsAndNothingElse() {
        val mine = TasbeehPresets.custom("custom_7", "ya latif", 100)
        assertEquals(100, TasbeehPresets.byId("astaghfirullah", emptyList())?.total)
        assertEquals(mine, TasbeehPresets.byId("custom_7", listOf(mine)))
        assertNull(TasbeehPresets.byId("custom_7", emptyList()))
        assertNull(TasbeehPresets.byId("nothing_like_this", listOf(mine)))
    }

    @Test
    fun aCustomPresetIsOnePartWithNoTranslation() {
        val mine = TasbeehPresets.custom("custom_7", "ya latif", 33)
        assertTrue(mine.custom)
        assertEquals(1, mine.parts.size)
        assertEquals("ya latif", mine.parts.first().dhikr.arabic)
        assertNull(mine.parts.first().dhikr.transliteration)
        assertNull(mine.parts.first().dhikr.meaning)
        assertEquals(33, mine.total)
    }
}
