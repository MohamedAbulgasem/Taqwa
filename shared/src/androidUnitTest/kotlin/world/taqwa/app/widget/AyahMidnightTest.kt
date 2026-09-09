package world.taqwa.app.widget

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one part of the ayah widget's daily alarm that arithmetic can get wrong quietly: which
 * instant "tomorrow's midnight" is. Everything else in `AyahWidgetScheduler` is `AlarmManager`
 * plumbing, verifiable on a device with `dumpsys alarm`.
 *
 * Constants are epoch millis computed from the tz database independently of `java.time`, so the
 * test cannot agree with the implementation by sharing its mistake.
 */
class AyahMidnightTest {

    private val london = ZoneId.of("Europe/London")

    @Test
    fun `an ordinary day rolls over at the next local midnight`() {
        // 2026-06-10 23:59:59.000 BST -> 2026-06-11 00:00:00.000 BST, one second later.
        assertEquals(1_781_132_400_000L, AyahMidnight.nextMidnight(1_781_132_399_000L, london))
    }

    @Test
    fun `an instant exactly on midnight arms the following one, not itself`() {
        // 2026-06-11 00:00 BST in, 2026-06-12 00:00 BST out. Returning the same instant would arm
        // an already-due alarm and spin: the midnight receiver re-arms from its own delivery.
        val midnight = 1_781_132_400_000L
        val next = AyahMidnight.nextMidnight(midnight, london)
        assertEquals(midnight + 24L * 60L * 60L * 1000L, next)
        assertTrue(next > midnight)
    }

    @Test
    fun `a spring-forward day is 23 hours long and the alarm follows the wall clock`() {
        // Europe/London 2026-03-29: clocks go 01:00 GMT -> 02:00 BST. From midday on that day the
        // next midnight is 2026-03-30 00:00 BST, which is 23:00 UTC on the 29th — 11.5 hours away,
        // not the 12.5 a fixed 24-hour step from the previous midnight would have produced.
        assertEquals(1_774_825_200_000L, AyahMidnight.nextMidnight(1_774_783_800_000L, london))

        // And measured from the start of that same day: 23 hours, not 24.
        val startOfDst = AyahMidnight.nextMidnight(1_774_742_400_000L, london)
        assertEquals(1_774_825_200_000L, startOfDst)
        assertEquals(23L * 60L * 60L * 1000L, startOfDst - 1_774_742_400_000L)
    }

    @Test
    fun `a zone whose midnight does not exist gets the first instant that does`() {
        // America/Santiago starts summer time at 24:00 on 2026-09-05, so 2026-09-06 00:00 is never
        // on the clock there. The alarm must land on 01:00 -03 rather than an hour into the 5th.
        val santiago = ZoneId.of("America/Santiago")
        // 2026-09-05 12:00 -04 in; 2026-09-06 01:00 -03 (the first instant of the 6th) out.
        assertEquals(1_788_667_200_000L, AyahMidnight.nextMidnight(1_788_624_000_000L, santiago))
    }

    @Test
    fun `the result is always ahead and never more than a long day away`() {
        // Walked across the London spring-forward and autumn-back transitions in 37-minute steps,
        // a stride that is coprime with the hour so every offset within a day gets exercised.
        val step = 37L * 60L * 1000L
        var now = 1_774_400_000_000L // 2026-03-25
        val end = 1_793_000_000_000L // 2026-10-26
        while (now < end) {
            val next = AyahMidnight.nextMidnight(now, london)
            assertTrue(next > now, "midnight $next was not ahead of $now")
            assertTrue(next - now <= 25L * 60L * 60L * 1000L, "midnight $next was too far past $now")
            now += step
        }
    }

    @Test
    fun `a pre-epoch clock still moves forwards`() {
        // A device whose date was dragged back must not end up with an alarm permanently in the
        // past — the widget would then never redraw again until `updatePeriodMillis` caught it.
        val next = AyahMidnight.nextMidnight(-1_000L, ZoneId.of("UTC"))
        assertEquals(0L, next)
        assertTrue(next > -1_000L)
    }
}
