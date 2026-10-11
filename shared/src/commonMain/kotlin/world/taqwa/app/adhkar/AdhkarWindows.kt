package world.taqwa.app.adhkar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.Prayer
import kotlin.time.Instant

/**
 * How near the end of its time a set is (spec §3.4): [BEST] is the preferred part of the half day,
 * [MAKEUP] the rest of it, in which the adhkar may still be read, and [NONE] outside it.
 */
enum class AdhkarPhase { BEST, MAKEUP, NONE }

/** [set]'s state at an instant; [until] is when [phase] ends, null for [AdhkarPhase.NONE]. */
data class AdhkarWindow(val set: AdhkarSet, val phase: AdhkarPhase, val until: Instant?)

/**
 * When each set is read, from one day's prayer times (spec §3.4):
 *
 * - morning: best from Fajr until sunrise, make-up from sunrise until Dhuhr;
 * - evening: best from Asr until the shown Maghrib, make-up from Maghrib until Isha.
 *
 * Every function takes the times of the day the set belongs to and compares instants, never clock
 * times, so a window that crosses midnight (an Isha after twelve at a high latitude) needs no
 * special case. Which day that is, is [dayFor]'s decision and nobody else's: between midnight and
 * Fajr it is still last evening, so [dayFor] hands back yesterday's times, and [setFor],
 * [windowFor] and [dateKey] given that result all agree. Fed today's times at 00:30 instead, the
 * evening set would be dated by today's Asr and its make-up window would be over.
 *
 * A [DayPrayerTimes] always carries all six times, polar days included (the engine falls back to
 * a rule rather than leaving one out), so there is no sunrise-less day to handle. A sunrise at or
 * before Fajr, which the high-latitude rules can produce, leaves the best phase empty and the
 * whole morning make-up: no instant is both at or after Fajr and before sunrise.
 */
object AdhkarWindows {
    /**
     * The day's times the adhkar are read against at [now]: the local date's own, unless [now] is
     * before that day's Fajr, in which case yesterday's. Between midnight and Fajr the night is still
     * yesterday's evening, with its make-up window running until yesterday's Isha (which, at a high
     * latitude, can be after midnight) and its progress dated by yesterday's Asr. Without this the
     * calendar date alone would put 00:30 on today's times, where nothing is open and the evening set
     * would be keyed to an Asr that has not happened yet.
     *
     * Pure: [timesFor] is the caller's way to the engine (it is asked for at most two dates).
     */
    fun dayFor(now: Instant, zone: TimeZone, timesFor: (LocalDate) -> DayPrayerTimes): DayPrayerTimes {
        val date = now.toLocalDateTime(zone).date
        val today = timesFor(date)
        return if (now < today.time(Prayer.FAJR)) timesFor(date.minus(1, DateTimeUnit.DAY)) else today
    }

    /** The half day it is: [AdhkarSet.MORNING] from Fajr until Asr, [AdhkarSet.EVENING] otherwise. */
    fun setFor(now: Instant, today: DayPrayerTimes): AdhkarSet =
        if (now >= today.time(Prayer.FAJR) && now < today.time(Prayer.ASR)) AdhkarSet.MORNING else AdhkarSet.EVENING

    /**
     * Each boundary belongs to the phase that begins there: at Fajr the morning is best, at sunrise
     * make-up, at Dhuhr over. A set asked about outside its own half day is [AdhkarPhase.NONE].
     */
    fun windowFor(set: AdhkarSet, now: Instant, today: DayPrayerTimes): AdhkarWindow {
        val (open, best, late) = when (set) {
            AdhkarSet.MORNING -> Triple(today.time(Prayer.FAJR), today.time(Prayer.SUNRISE), today.time(Prayer.DHUHR))
            AdhkarSet.EVENING -> Triple(today.time(Prayer.ASR), today.time(Prayer.MAGHRIB), today.time(Prayer.ISHA))
        }
        return when {
            now < open || now >= late -> AdhkarWindow(set, AdhkarPhase.NONE, null)
            now < best -> AdhkarWindow(set, AdhkarPhase.BEST, best)
            else -> AdhkarWindow(set, AdhkarPhase.MAKEUP, late)
        }
    }

    /**
     * The date a set's progress is kept under: the local date of that set's own prayer — that
     * Fajr, that Asr — so the evening read after midnight still belongs to the day it began on, and
     * a new date is a new set with nothing counted.
     */
    fun dateKey(set: AdhkarSet, today: DayPrayerTimes, zone: TimeZone): LocalDate =
        today.time(if (set == AdhkarSet.MORNING) Prayer.FAJR else Prayer.ASR).toLocalDateTime(zone).date
}
