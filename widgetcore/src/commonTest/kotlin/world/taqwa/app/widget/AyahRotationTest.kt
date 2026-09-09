package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AyahRotationTest {

    @Test
    fun epochDayOfUnixEpochIsZero() {
        assertEquals(0L, AyahRotation.epochDay(1970, 1, 1))
    }

    @Test
    fun epochDayOfKnownDates() {
        assertEquals(11017L, AyahRotation.epochDay(2000, 3, 1))
        assertEquals(20705L, AyahRotation.epochDay(2026, 9, 9))
    }

    @Test
    fun epochDayBeforeTheEpochIsNegative() {
        assertEquals(-1L, AyahRotation.epochDay(1969, 12, 31))
    }

    @Test
    fun permutationIsAlwaysAFullPermutationOfTheRange() {
        for (round in -3L..3L) {
            for (seed in listOf(1L, 42L, -99L)) {
                val order = AyahRotation.permutation(round, seed, 50)
                assertEquals((0 until 50).toSet(), order.toSet(), "round=$round seed=$seed")
                assertEquals(50, order.size)
            }
        }
    }

    @Test
    fun indexForCoversAllFiftyExactlyOnceWithinARoundBoundary() {
        val seed = 7L
        for (round in listOf(-2L, -1L, 0L, 1L, 5L)) {
            val boundary = round * 50
            val seen = (0 until 50).map { AyahRotation.indexFor(boundary + it, seed, 50) }.toSet()
            assertEquals(50, seen.size, "round=$round")
        }
    }

    @Test
    fun indexForNeverRepeatsAcrossARoundBoundary() {
        for (round in 0L until 20L) {
            for (seed in listOf(1L, 2L, 3L, 4L, 5L)) {
                val lastOfRound = AyahRotation.indexFor(round * 50 + 49, seed, 50)
                val firstOfNextRound = AyahRotation.indexFor((round + 1) * 50, seed, 50)
                assertNotEquals(lastOfRound, firstOfNextRound, "round=$round seed=$seed")
            }
        }
    }

    @Test
    fun indexForIsDeterministic() {
        assertEquals(
            AyahRotation.indexFor(20705L, 123L, 50),
            AyahRotation.indexFor(20705L, 123L, 50),
        )
    }

    @Test
    fun sizeOneAlwaysReturnsZero() {
        assertEquals(0, AyahRotation.indexFor(0L, 999L, 1))
        assertEquals(0, AyahRotation.indexFor(-500L, 999L, 1))
        assertEquals(0, AyahRotation.indexFor(500L, 1L, 1))
    }

    @Test
    fun negativeEpochDaysWork() {
        val result = AyahRotation.indexFor(-20705L, 7L, 50)
        assertTrue(result in 0 until 50)
    }
}
