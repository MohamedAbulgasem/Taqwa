package world.taqwa.app.prayer.engine

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.PrayerDay
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The user's minute adjustments (spec §2.2, §3.3), applied to a day already in order.
 *
 * - A later start is always applied. An earlier start is applied only when it was confirmed under
 *   the timetable in use ([EngineSettings.confirmedAdjustments] names the entry); otherwise it is
 *   paused, left out, and reported (spec §8: kept overrides that begin earlier wait for a new
 *   confirmation).
 * - Sunrise and the end of eating never move later: a later sunrise is ignored, and the end of
 *   eating only follows an earlier Fajr down.
 * - Floors for an earlier start, each at the user's own point (ruling R51): Maghrib at its sunset
 *   with the method's horizon, rounded up (never the day's [PrayerDay.sunset], the earlier of the
 *   unit's point and the user's, which west of the point is before the user's own), Dhuhr a minute
 *   after the transit and Asr at the astronomical Standard Asr. A floor never lifts a time above
 *   its own unadjusted value; where the sun does not set, or never casts the Standard shadow, that
 *   start does not move earlier.
 * - The order is kept: a later start stops a minute before the next time, and a later Isha a minute
 *   before its end, the next day's end of eating (never below its own unadjusted value); then each
 *   start is at least a minute after the one before it, which only ever moves a start later. Ends
 *   that no longer follow their start are dropped, as
 *   [world.taqwa.app.prayer.engine.day.Invariants.repair] does.
 */
internal object Adjustments {
    private val MINUTE = 1.minutes

    class Applied(val day: PrayerDay, val paused: Set<Prayer>)

    /**
     * [day] with [minutes] applied under the timetable [entryId]. [dhuhrFloor] is the whole minute a
     * minute after the transit at the user's point, [asrFloor] the astronomical Standard Asr there
     * rounded up (null when the sun never casts that shadow), [maghribFloor] the sunset there with
     * the method's horizon rounded up (null when the sun does not set); each is asked for only when
     * an earlier start needs it.
     */
    fun apply(
        day: PrayerDay,
        minutes: Map<Prayer, Int>,
        confirmed: Map<Prayer, String>,
        entryId: String,
        dhuhrFloor: () -> Instant,
        asrFloor: () -> Instant?,
        maghribFloor: () -> Instant?,
    ): Applied {
        if (minutes.values.all { it == 0 }) return Applied(day, emptySet())
        val paused = mutableSetOf<Prayer>()
        fun offset(prayer: Prayer): Int {
            val m = minutes[prayer] ?: 0
            return when {
                m == 0 -> 0
                prayer == Prayer.SUNRISE -> minOf(m, 0)
                m < 0 && confirmed[prayer] != entryId -> {
                    paused += prayer
                    0
                }
                else -> m
            }
        }

        fun shifted(prayer: Prayer, unadjusted: Instant, floor: (() -> Instant?)?): Instant {
            val m = offset(prayer)
            if (m == 0) return unadjusted
            val moved = unadjusted + m.minutes
            return if (m < 0 && floor != null) maxOf(moved, minOf(unadjusted, floor() ?: unadjusted)) else moved
        }

        var fajr = shifted(Prayer.FAJR, day.fajr, floor = null)
        var sunrise = day.sunrise + offset(Prayer.SUNRISE).minutes
        var dhuhr = shifted(Prayer.DHUHR, day.dhuhr, dhuhrFloor)
        var asr = shifted(Prayer.ASR, day.asr, asrFloor)
        var maghrib = shifted(Prayer.MAGHRIB, day.maghrib, maghribFloor)
        var isha = shifted(Prayer.ISHA, day.isha, floor = null)

        // A later start stops a minute before the next time, never below its own unadjusted value.
        fun capped(value: Instant, unadjusted: Instant, next: Instant): Instant =
            if (value > unadjusted) minOf(value, maxOf(unadjusted, next - MINUTE)) else value
        // Isha's next time is its end, the next day's end of eating, where the day has one.
        day.ends[Prayer.ISHA]?.let { isha = capped(isha, day.isha, it) }
        maghrib = capped(maghrib, day.maghrib, isha)
        asr = capped(asr, day.asr, maghrib)
        dhuhr = capped(dhuhr, day.dhuhr, asr)
        fajr = capped(fajr, day.fajr, sunrise)

        // Each time at least a minute after the one before it: only ever later.
        sunrise = maxOf(sunrise, fajr + MINUTE)
        dhuhr = maxOf(dhuhr, sunrise + MINUTE)
        asr = maxOf(asr, dhuhr + MINUTE)
        maghrib = maxOf(maghrib, asr + MINUTE)
        isha = maxOf(isha, maghrib + MINUTE)

        val starts = mapOf(
            Prayer.FAJR to fajr, Prayer.SUNRISE to sunrise, Prayer.DHUHR to dhuhr, Prayer.ASR to asr,
            Prayer.MAGHRIB to maghrib, Prayer.ISHA to isha,
        )
        val ends = day.ends
            .mapValues { (prayer, end) ->
                when (prayer) {
                    Prayer.FAJR -> minOf(end, sunrise)
                    Prayer.MAGHRIB -> minOf(end, isha)
                    else -> end
                }
            }
            .filter { (prayer, end) -> end > starts.getValue(prayer) }
        val adjusted = day.copy(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha,
            endOfEating = minOf(day.endOfEating, fajr),
            imsak = day.imsak?.let { minOf(it, fajr) },
            ends = ends,
        )
        return Applied(adjusted, paused)
    }
}
