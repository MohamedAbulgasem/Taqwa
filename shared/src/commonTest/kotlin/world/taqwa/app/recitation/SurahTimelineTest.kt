package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals

/** Al-Fatiha as a clock: seven ayahs of a second each with the reciter's 300 ms between them. */
class SurahTimelineTest {

    private val fatiha = RecitationQueue(1, (1..7).toList(), gapMs = 300L)
    private val timeline = SurahTimeline.of(fatiha) { 1_000L }

    @Test
    fun `the surah is every ayah plus every gap`() {
        assertEquals(13, timeline.size)
        assertEquals(7_000L + 6 * 300L, timeline.totalMs)
    }

    @Test
    fun `each item starts where the ones before it end`() {
        assertEquals(0L, timeline.startOf(0))
        assertEquals(1_000L, timeline.startOf(1))
        assertEquals(1_300L, timeline.startOf(2))
        assertEquals(7_800L, timeline.startOf(12))
        assertEquals(300L, timeline.durationOf(1))
        assertEquals(1_000L, timeline.durationOf(12))
    }

    @Test
    fun `elapsed is the item's start plus the position inside it held inside the slot`() {
        assertEquals(1_450L, timeline.elapsed(2, 150L))
        // A platform that reports a few frames past the estimate does not reach the next ayah.
        assertEquals(2_300L, timeline.elapsed(2, 1_400L))
        assertEquals(1_000L, timeline.elapsed(1, -20L))
        // The gap counts: the clock keeps moving through the reciter's silence.
        assertEquals(1_150L, timeline.elapsed(1, 150L))
    }

    @Test
    fun `a surah time finds the item whose slot holds it`() {
        assertEquals(0, timeline.indexAt(0L))
        assertEquals(0, timeline.indexAt(999L))
        assertEquals(1, timeline.indexAt(1_000L))
        assertEquals(1, timeline.indexAt(1_299L))
        assertEquals(2, timeline.indexAt(1_300L))
        assertEquals(12, timeline.indexAt(8_799L))
        assertEquals(12, timeline.indexAt(8_800L))
        assertEquals(12, timeline.indexAt(99_999L))
        assertEquals(0, timeline.indexAt(-5L))
    }

    @Test
    fun `a seek snaps to the ayah and from inside a gap to the ayah the gap leads into`() {
        assertEquals(2, timeline.snapToAyah(1_450L, fatiha::isGap))
        assertEquals(2, timeline.snapToAyah(1_100L, fatiha::isGap))
        assertEquals(12, timeline.snapToAyah(8_799L, fatiha::isGap))
        assertEquals(0, timeline.snapToAyah(0L, fatiha::isGap))
    }

    @Test
    fun `a constant bit-rate file's length is its bytes over its rate`() {
        // Alafasy's first ayah: 49,513 bytes less a 407-byte ID3 tag at 64 kbps; measured 6.113 s.
        assertEquals(6_138L, SurahTimeline.estimateMs(49_106L, 64))
        // Minshawi's, 128 kbps, no tag; measured 4.971 s.
        assertEquals(4_971L, SurahTimeline.estimateMs(79_540L, 128))
        assertEquals(0L, SurahTimeline.estimateMs(1_000L, 0))
        assertEquals(0L, SurahTimeline.estimateMs(-1L, 64))
    }

    @Test
    fun `a queue without gaps is only its ayahs`() {
        val plain = SurahTimeline.of(RecitationQueue(112, (1..4).toList(), gapMs = 0L)) { n -> n * 100L }
        assertEquals(4, plain.size)
        assertEquals(1_000L, plain.totalMs)
        assertEquals(300L, plain.startOf(2))
    }
}
