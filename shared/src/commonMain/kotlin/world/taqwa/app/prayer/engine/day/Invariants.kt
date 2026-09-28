package world.taqwa.app.prayer.engine.day

import world.taqwa.app.domain.Prayer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The order every day keeps (spec §3.3): `endOfEating ≤ fajr < sunrise < dhuhr < asr < maghrib <
 * isha`, `maghrib ≥ sunset`, an imsak no later than Fajr, and ends that make sense: each after its
 * own prayer's start, Maghrib's no later than Isha's start. Tests and the gate assert it on the
 * unadjusted day; at runtime [repair] restores it without ever throwing.
 */
object Invariants {
    private val MINUTE = 1.minutes

    fun holds(day: PrayerDay): Boolean = with(day) {
        endOfEating <= fajr && fajr < sunrise && sunrise < dhuhr && dhuhr < asr && asr < maghrib &&
            maghrib < isha && maghrib >= sunset && (imsak == null || imsak <= fajr) && endsHold(day)
    }

    private fun endsHold(day: PrayerDay): Boolean =
        day.ends.all { (prayer, end) -> end > day.start(prayer) } &&
            day.ends[Prayer.MAGHRIB].let { it == null || it <= day.isha }

    /**
     * [day] itself when it [holds]; otherwise the day put back in order on the safe side, by whole
     * minutes, with [PrayerDay.repaired] set. Sunrise and sunset never move. Starts move later (a
     * minute after what must precede them; Maghrib at least to the minute after the rounded-down
     * sunset, where the raw sunset's own start would round) and the end of eating and imsak earlier
     * (to Fajr). The one start that can only move earlier is a Fajr at or after sunrise: sunrise is
     * the astronomical anchor, so such a Fajr becomes the minute before sunrise. The engine's own
     * days never bring one here: [DayComputer] keeps Fajr before the sunrise it shows, moving the
     * sunrise precaution before it moves Fajr, and [Cautious] pairs the Fajr shown with its own
     * member's sunrise (ruling R90); this is the last resort for a day built elsewhere. Maghrib's
     * end is held to the repaired Isha, and an end no longer after its own start is dropped.
     */
    fun repair(day: PrayerDay): PrayerDay {
        if (holds(day)) return day
        val fajr = if (day.fajr < day.sunrise) day.fajr else day.sunrise - MINUTE
        val dhuhr = maxOf(day.dhuhr, day.sunrise + MINUTE)
        val asr = maxOf(day.asr, dhuhr + MINUTE)
        val maghrib = if (day.maghrib >= day.sunset) maxOf(day.maghrib, asr + MINUTE) else maxOf(asr + MINUTE, day.sunset + MINUTE)
        val isha = maxOf(day.isha, maghrib + MINUTE)
        val ordered = day.copy(
            fajr = fajr,
            endOfEating = minOf(day.endOfEating, fajr),
            imsak = day.imsak?.let { minOf(it, fajr) },
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha,
            repaired = true,
        )
        val ends = day.ends
            .mapValues { (prayer, end) -> if (prayer == Prayer.MAGHRIB) minOf(end, isha) else end }
            .filter { (prayer, end) -> end > ordered.start(prayer) }
        return ordered.copy(ends = ends)
    }

    private fun PrayerDay.start(prayer: Prayer): Instant = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}
