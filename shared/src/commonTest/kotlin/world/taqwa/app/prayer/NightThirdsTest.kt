package world.taqwa.app.prayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class NightThirdsTest {
    @Test
    fun `the last third opens two thirds of the way from Maghrib to Fajr`() {
        // A nine-hour night: 19:00 to 04:00. Thirds of three hours; the last opens at 01:00.
        val maghrib = Instant.parse("2026-09-18T19:00:00Z")
        val fajr = Instant.parse("2026-09-19T04:00:00Z")
        assertEquals(Instant.parse("2026-09-19T01:00:00Z"), NightThirds.lastThirdStart(maghrib, fajr))
    }

    @Test
    fun `a night that does not divide evenly is not rounded past its own end`() {
        val maghrib = Instant.parse("2026-09-18T19:00:00Z")
        val fajr = Instant.parse("2026-09-19T04:00:01Z")
        val start = NightThirds.lastThirdStart(maghrib, fajr)
        assertEquals(true, start > maghrib && start < fajr)
    }

    @Test
    fun `a night of no length has no thirds`() {
        val at = Instant.parse("2026-06-21T01:00:00Z")
        assertEquals(at, NightThirds.lastThirdStart(at, at))
        assertEquals(at, NightThirds.lastThirdStart(Instant.parse("2026-06-21T02:00:00Z"), at))
    }
}
