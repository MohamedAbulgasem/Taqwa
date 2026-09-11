package world.taqwa.app.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasbeehPresetsTest {

    @Test
    fun theSevenBuiltInsAreInSpecOrderWithTheirTotals() {
        assertEquals(
            listOf(
                "after_prayer" to 100,
                "subhanallah" to 33,
                "alhamdulillah" to 33,
                "allahu_akbar" to 34,
                "astaghfirullah" to 100,
                "la_ilaha_illallah" to 100,
                "subhanallahi_wa_bihamdihi" to 100,
            ),
            TasbeehPresets.builtIn.map { it.id to it.total },
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
