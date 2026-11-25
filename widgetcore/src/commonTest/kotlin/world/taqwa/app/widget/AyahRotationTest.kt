package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AyahRotationTest {

    /** The rotation's properties are held at the size it actually runs at: the pool's own. */
    private val pool = AyahPool.REFS.size

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
                val order = AyahRotation.permutation(round, seed, pool)
                assertEquals((0 until pool).toSet(), order.toSet(), "round=$round seed=$seed")
                assertEquals(pool, order.size)
            }
        }
    }

    @Test
    fun indexForCoversTheWholePoolExactlyOnceWithinARound() {
        val seed = 7L
        for (round in listOf(-2L, -1L, 0L, 1L, 5L)) {
            val boundary = round * pool
            val seen = (0 until pool).map { AyahRotation.indexFor(boundary + it, seed, pool) }.toSet()
            assertEquals(pool, seen.size, "round=$round")
        }
    }

    @Test
    fun indexForNeverRepeatsAcrossARoundBoundary() {
        for (round in 0L until 20L) {
            for (seed in listOf(1L, 2L, 3L, 4L, 5L)) {
                val lastOfRound = AyahRotation.indexFor(round * pool + pool - 1, seed, pool)
                val firstOfNextRound = AyahRotation.indexFor((round + 1) * pool, seed, pool)
                assertNotEquals(lastOfRound, firstOfNextRound, "round=$round seed=$seed")
            }
        }
    }

    @Test
    fun indexForIsDeterministic() {
        assertEquals(
            AyahRotation.indexFor(20705L, 123L, pool),
            AyahRotation.indexFor(20705L, 123L, pool),
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
        val result = AyahRotation.indexFor(-20705L, 7L, pool)
        assertTrue(result in 0 until pool)
    }
}
