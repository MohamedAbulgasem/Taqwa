package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AyahPoolTest {

    // One ayah for each day of a year, so a round within which no ayah repeats lasts a year (spec
    // §3, "Amended 19 November 2026").
    @Test
    fun hasAYearOfEntries() {
        assertEquals(365, AyahPool.REFS.size)
    }

    @Test
    fun hasNoDuplicates() {
        assertEquals(AyahPool.REFS.size, AyahPool.REFS.toSet().size)
    }

    @Test
    fun everySurahIsInRange() {
        AyahPool.REFS.forEach { (surah, _) ->
            assertTrue(surah in 1..114, "surah $surah out of range")
        }
    }

    @Test
    fun everyAyahIsAtLeastOne() {
        AyahPool.REFS.forEach { (surah, ayah) ->
            assertTrue(ayah >= 1, "ayah $ayah of surah $surah is below 1")
        }
    }
}
