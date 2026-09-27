package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The session's side of the queue (read-aloud spec §5.5). `AyahPlayer` sees only the media ids the
 * app sent ([mediaIdOf]) and rebuilds the queue from them ([queueItemsOf]); these hold that rebuild
 * to the app's own [RecitationQueue] — item for item, and in every answer its arithmetic gives — so
 * the lock screen's buttons and the app's bar can never move by two different rules.
 */
class QueueItemsTest {

    private fun idsOf(queue: RecitationQueue): List<String> =
        queue.items.map { mediaIdOf(it, "ar.alafasy", queue.surah, generation = 3L) }

    /** The app's items as the session rebuilds them: a silence's length is not among the ids' business. */
    private fun shapeOf(queue: RecitationQueue): List<QueueItem> =
        queue.items.map { if (it is QueueItem.Gap) QueueItem.Gap(0L) else it }

    private fun assertSameArithmetic(app: RecitationQueue) {
        val rebuilt = RecitationQueue(0, assertNotNull(queueItemsOf(idsOf(app))))
        assertEquals(shapeOf(app), rebuilt.items)
        assertEquals(app.ayahs, rebuilt.ayahs)
        for (i in app.items.indices) {
            assertEquals(app.isAyah(i), rebuilt.isAyah(i), "isAyah($i)")
            assertEquals(app.isSpeech(i), rebuilt.isSpeech(i), "isSpeech($i)")
            assertEquals(app.isGap(i), rebuilt.isGap(i), "isGap($i)")
            assertEquals(app.ayahAt(i), rebuilt.ayahAt(i), "ayahAt($i)")
            assertEquals(app.ayahIndexAt(i), rebuilt.ayahIndexAt(i), "ayahIndexAt($i)")
            assertEquals(app.next(i), rebuilt.next(i), "next($i)")
            assertEquals(app.previous(i, 0L), rebuilt.previous(i, 0L), "previous($i) at its start")
            assertEquals(app.previous(i, 5_000L), rebuilt.previous(i, 5_000L), "previous($i) late in it")
        }
    }

    @Test
    fun ayahsOnly() {
        val app = RecitationQueue(112, listOf(1, 2, 3, 4), gapMs = 0L)
        assertEquals((1..4).map { QueueItem.Ayah(it) }, queueItemsOf(idsOf(app)))
        assertSameArithmetic(app)
    }

    @Test
    fun ayahsWithTheReciterGap() {
        val app = RecitationQueue(112, listOf(1, 2, 3), gapMs = 300L)
        assertEquals(
            listOf(QueueItem.Ayah(1), QueueItem.Gap(0L), QueueItem.Ayah(2), QueueItem.Gap(0L), QueueItem.Ayah(3)),
            queueItemsOf(idsOf(app)),
        )
        assertSameArithmetic(app)
    }

    @Test
    fun ayahsWithBreathTranslationAndGap() {
        // Read after ayahs 2 and 3, the last: ayah 1 is a run's first ayah, read with ayah 2.
        val app = RecitationQueue(1, listOf(1, 2, 3), gapMs = 300L, spoken = setOf(2, 3))
        assertEquals(
            listOf(
                QueueItem.Ayah(1), QueueItem.Gap(0L),
                QueueItem.Ayah(2), QueueItem.Gap(0L), QueueItem.Speech(2), QueueItem.Gap(0L),
                QueueItem.Ayah(3), QueueItem.Gap(0L), QueueItem.Speech(3),
            ),
            queueItemsOf(idsOf(app)),
        )
        assertSameArithmetic(app)
    }

    @Test
    fun aWholeSurahReadAloudHasTheAppsArithmetic() {
        // Al-Fatiha with every ayah read, and with no reciter gap at all.
        assertSameArithmetic(RecitationQueue(1, (1..7).toList(), gapMs = 300L, spoken = (1..7).toSet()))
        assertSameArithmetic(RecitationQueue(1, (1..7).toList(), gapMs = 0L, spoken = setOf(1, 4, 7)))
    }

    @Test
    fun noAyahAtAllIsNoQueue() {
        assertNull(queueItemsOf(emptyList()))
        assertNull(queueItemsOf(listOf(gapUri(300L), speechUri(3L, 1, 1))))
    }
}
