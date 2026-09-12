package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Al-Fatiha (7 ayahs) and Al-Baqarah (286) are the two ends of the range the queue has to be
 * right at: the shortest surah in the app and the longest, the second of which is 571 items with
 * gaps and the one place an off-by-one would be hardest to see on a device.
 */
class RecitationQueueTest {

    private fun fatiha(gapMs: Long = 300L) = RecitationQueue(1, (1..7).toList(), gapMs)

    private fun baqarah(gapMs: Long = 300L) = RecitationQueue(2, (1..286).toList(), gapMs)

    @Test
    fun `a seven ayah surah with a gap alternates ayah and silence`() {
        val queue = fatiha()
        assertEquals(13, queue.size)
        assertEquals(13, queue.items.size)
        assertEquals(QueueItem.Ayah(1), queue.items[0])
        assertEquals(QueueItem.Gap(300L), queue.items[1])
        assertEquals(QueueItem.Ayah(2), queue.items[2])
        assertEquals(QueueItem.Ayah(7), queue.items.last())
        assertTrue(queue.items.count { it is QueueItem.Gap } == 6)
    }

    @Test
    fun `a seven ayah surah without a gap is one item per ayah`() {
        val queue = fatiha(gapMs = 0L)
        assertFalse(queue.hasGaps)
        assertEquals(7, queue.size)
        assertEquals(List(7) { QueueItem.Ayah(it + 1) }, queue.items)
        (1..7).forEach { assertEquals(it - 1, queue.indexOfAyah(it)) }
        (0..6).forEach { assertEquals(it + 1, queue.ayahAt(it)) }
    }

    @Test
    fun `a single ayah surah has no gap even when the reciter asks for one`() {
        val queue = RecitationQueue(112, listOf(1), 300L)
        assertFalse(queue.hasGaps)
        assertEquals(1, queue.size)
        assertEquals(listOf(QueueItem.Ayah(1)), queue.items)
    }

    @Test
    fun `ayah n starts at queue index two n minus two when gaps are in`() {
        val queue = fatiha()
        assertEquals(0, queue.indexOfAyah(1))
        assertEquals(2, queue.indexOfAyah(2))
        assertEquals(12, queue.indexOfAyah(7))
    }

    @Test
    fun `a two hundred and eighty six ayah surah maps every ayah both ways`() {
        val queue = baqarah()
        assertEquals(571, queue.size)
        (1..286).forEach { n ->
            val index = queue.indexOfAyah(n)!!
            assertEquals((n - 1) * 2, index)
            assertEquals(n, queue.ayahAt(index))
            assertFalse(queue.isGap(index))
        }
        assertEquals(570, queue.indexOfAyah(286))
    }

    @Test
    fun `a gap keeps reporting the ayah that just ended`() {
        val queue = fatiha()
        assertTrue(queue.isGap(1))
        assertEquals(1, queue.ayahAt(1))
        assertTrue(queue.isGap(11))
        assertEquals(6, queue.ayahAt(11))
        val long = baqarah()
        assertTrue(long.isGap(569))
        assertEquals(285, long.ayahAt(569))
    }

    @Test
    fun `next skips the gap and stops at the last ayah`() {
        val queue = fatiha()
        assertEquals(2, queue.next(0))
        assertEquals(2, queue.next(1))
        assertEquals(12, queue.next(10))
        // The gap before the last ayah still has an ayah after it; the last ayah does not.
        assertEquals(12, queue.next(11))
        assertNull(queue.next(12))
    }

    @Test
    fun `next at the end of a long surah is nothing`() {
        val queue = baqarah()
        assertEquals(570, queue.next(568))
        assertNull(queue.next(570))
    }

    @Test
    fun `previous inside two seconds goes back an ayah`() {
        val queue = fatiha()
        assertEquals(2, queue.previous(index = 4, positionMs = 0L))
        assertEquals(2, queue.previous(index = 4, positionMs = 1_999L))
    }

    @Test
    fun `previous past two seconds restarts the ayah`() {
        val queue = fatiha()
        assertEquals(4, queue.previous(index = 4, positionMs = 2_000L))
        assertEquals(4, queue.previous(index = 4, positionMs = 9_000L))
    }

    @Test
    fun `previous at the first ayah restarts it however early it is pressed`() {
        val queue = fatiha()
        assertEquals(0, queue.previous(index = 0, positionMs = 0L))
        assertEquals(0, queue.previous(index = 0, positionMs = 30_000L))
    }

    @Test
    fun `previous during a gap restarts the ayah that just ended`() {
        val queue = fatiha()
        assertEquals(2, queue.previous(index = 3, positionMs = 10L))
        assertEquals(2, queue.previous(index = 3, positionMs = 250L))
    }

    @Test
    fun `previous without gaps still follows the two second rule`() {
        val queue = fatiha(gapMs = 0L)
        assertEquals(1, queue.previous(index = 2, positionMs = 500L))
        assertEquals(2, queue.previous(index = 2, positionMs = 5_000L))
        assertEquals(0, queue.previous(index = 0, positionMs = 100L))
    }

    @Test
    fun `starting in the middle is an ordinary index`() {
        val queue = baqarah()
        val index = queue.indexOfAyah(255)!!
        assertEquals(508, index)
        assertEquals(255, queue.ayahAt(index))
        assertEquals(510, queue.next(index))
        assertEquals(506, queue.previous(index, positionMs = 100L))
    }

    @Test
    fun `an ayah the surah does not have has no index`() {
        val queue = fatiha()
        assertNull(queue.indexOfAyah(0))
        assertNull(queue.indexOfAyah(8))
        assertNull(queue.indexOfAyah(-1))
        assertFalse(queue.contains(8))
        assertTrue(queue.contains(7))
    }

    @Test
    fun `an index from a queue that has been replaced is clamped rather than thrown on`() {
        val queue = fatiha()
        assertEquals(1, queue.ayahAt(-4))
        assertEquals(7, queue.ayahAt(400))
        assertEquals(0, queue.previous(-4, 0L))
        assertNull(queue.next(400))
    }

    @Test
    fun `a container index builds the queue it describes`() {
        val index = TaqaIndex(
            reciter = "ar.alafasy",
            surah = 112,
            kbps = 64,
            ayahs = listOf(
                TaqaAyah(1, 0L, 100L),
                TaqaAyah(2, 100L, 100L),
                TaqaAyah(3, 200L, 100L),
                TaqaAyah(4, 300L, 100L),
            ),
            dataStart = 128L,
        )
        val queue = RecitationQueue.of(index, gapMs = 300L)
        assertEquals(112, queue.surah)
        assertEquals(4, queue.ayahCount)
        assertEquals(7, queue.size)
        assertEquals(listOf(1, 2, 3, 4), queue.ayahs)
    }
}
