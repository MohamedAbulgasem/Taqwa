package world.taqwa.app.prayer

import kotlin.time.Instant

/**
 * The night in thirds (spec §17.6). The night runs from Maghrib to the Fajr that follows it, and
 * its last third — the time the hadith of the descent speaks of, and the time Tahajjud is best
 * prayed — begins two thirds of the way through. In Tripoli in September that is a night of
 * about ten and a half hours and a last third that opens a little before two.
 */
object NightThirds {

    /** When the last third begins, given the evening's [maghrib] and the morning's [fajr]. */
    fun lastThirdStart(maghrib: Instant, fajr: Instant): Instant {
        val night = fajr - maghrib
        // A night of no length — times that crossed at a high latitude — has no thirds; the
        // answer collapses onto Fajr, which the planner then declines to schedule twice.
        if (!night.isPositive()) return fajr
        return maghrib + night * 2 / 3
    }
}
