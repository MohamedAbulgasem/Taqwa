package world.taqwa.app.adhkar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdhkarProgressTest {

    private val today = LocalDate(2026, 10, 11)

    @Test fun `progress encodes as the date and the counts in page order`() {
        assertEquals("2026-10-11|0,3,1", AdhkarProgress(today, listOf(0, 3, 1)).encode())
    }

    @Test fun `encode and decode round trip`() {
        val progress = AdhkarProgress(today, listOf(0, 3, 1))
        assertEquals(progress, AdhkarProgress.decode(progress.encode(), pages = 3))
        val istighfar = AdhkarProgress(today, listOf(57))
        assertEquals(istighfar, AdhkarProgress.decode("2026-10-11|57", pages = 1))
    }

    @Test fun `decode is null for a different page count`() {
        assertNull(AdhkarProgress.decode("2026-10-11|0,3,1", pages = 4))
        assertNull(AdhkarProgress.decode("2026-10-11|0,3,1", pages = 2))
    }

    @Test fun `decode is null for anything that is not progress`() {
        for (raw in listOf(
            null, "", "|", "2026-10-11", "2026-10-11|", "|0,3,1", "0,3,1", "2026-10-11|0,x,1",
            "2026-10-11|0,,1", "2026-10-11|0,-3,1", "2026-13-40|0,3,1", "yesterday|0,3,1",
            "2026-10-11|0,3,1|9", "2026-10-11|99999999999,0,0",
        )) {
            assertNull(AdhkarProgress.decode(raw, pages = 3), "decoded $raw")
        }
    }

    @Test fun `a day with no pages still round trips`() {
        val empty = AdhkarProgress(today, emptyList())
        assertEquals(empty, AdhkarProgress.decode(empty.encode(), pages = 0))
    }

    @Test fun `forDate keeps the counts of the same date`() {
        assertEquals(
            AdhkarProgress(today, listOf(1, 3, 0)),
            AdhkarProgress.forDate("2026-10-11|1,3,0", today, pages = 3),
        )
    }

    @Test fun `forDate starts again from zeros when the stored date is another day`() {
        assertEquals(
            AdhkarProgress(today, listOf(0, 0, 0)),
            AdhkarProgress.forDate("2026-10-10|1,3,0", today, pages = 3),
        )
    }

    @Test fun `forDate starts from zeros when there is nothing or it does not read`() {
        val zeros = AdhkarProgress(today, listOf(0, 0, 0))
        assertEquals(zeros, AdhkarProgress.forDate(null, today, pages = 3))
        assertEquals(zeros, AdhkarProgress.forDate("garbage", today, pages = 3))
        // A set that changed length since it was saved is a different set: nothing carries over.
        assertEquals(zeros, AdhkarProgress.forDate("2026-10-11|1,3", today, pages = 3))
    }
}
