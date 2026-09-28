package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import kotlin.time.Instant

/**
 * When each prayer's time ends (spec §3.3), for the screen's highlight, About and the site. Pure:
 * it takes values already rounded (ends down), and [DayComputer] and [Cautious] supply them.
 *
 * - Fajr ends at sunrise.
 * - Dhuhr at the Standard (shadow-1) Asr, whichever school the day shows.
 * - Asr at the astronomical sunset, not Maghrib.
 * - Maghrib at the earlier of the earliest Isha in play and the end of the red twilight (the sun
 *   [RED_TWILIGHT_DEG] below the horizon), where the sun gets that low.
 * - Isha at the next day's end of eating, once the caller has the next day ([withNextDay]).
 */
object Ends {
    /** The red twilight (al-shafaq al-ahmar) is over when the sun is this far below the horizon. */
    const val RED_TWILIGHT_DEG = 17.0

    /**
     * [maghribEndCap] is any other end Maghrib may not pass besides [earliestIsha]: one
     * timetable's red twilight, or the earliest of cautious members' own Maghrib ends (each
     * already the earlier of that member's Isha and red twilight); null when there is none.
     */
    fun of(
        sunrise: Instant,
        standardAsr: Instant,
        sunset: Instant,
        earliestIsha: Instant,
        maghribEndCap: Instant?,
        nextEndOfEating: Instant?,
    ): Map<Prayer, Instant> = buildMap {
        put(Prayer.FAJR, sunrise)
        put(Prayer.DHUHR, standardAsr)
        put(Prayer.ASR, sunset)
        put(Prayer.MAGHRIB, maghribEndCap?.let { minOf(it, earliestIsha) } ?: earliestIsha)
        nextEndOfEating?.let { put(Prayer.ISHA, it) }
    }

    /**
     * [day] with Isha's end: [next]'s end of eating; [next] must be the following date. An end not
     * after the Isha start is left out (ruling R26, as `Invariants.repair` does): Isha keeps its
     * start, and the day shows no end for it.
     */
    fun withNextDay(day: PrayerDay, next: PrayerDay): PrayerDay {
        require(next.date == day.date.plus(1, DateTimeUnit.DAY)) { "${next.date} does not follow ${day.date}" }
        if (next.endOfEating <= day.isha) return day.copy(ends = day.ends - Prayer.ISHA)
        return day.copy(ends = day.ends + (Prayer.ISHA to next.endOfEating))
    }
}
