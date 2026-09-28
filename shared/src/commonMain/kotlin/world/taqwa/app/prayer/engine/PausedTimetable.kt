package world.taqwa.app.prayer.engine

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import kotlin.time.Instant

/**
 * A chosen timetable not yet confirmed (spec §8, ruling R52): it applies, but no earlier than
 * Automatic. Each start is the later of the two days', and sunrise, sunset, the end of eating,
 * imsak and every end the earlier, so the day is never early by either; a time set by a
 * high-latitude rule keeps that mark from the day it came from, and what either day declares not
 * followed stays declared.
 *
 * Where the two days leave a prayer no time, its later start at or after its earlier end (a
 * sun-based day against an authority's clock rule, as a Makkah-time Fajr after a nearest-latitude
 * sunrise at Tromsø, ruling R82), the start keeps its promise and the end gives way: the end, and
 * for Fajr the sunrise, is the one of the day whose start is shown, after the other day's. The
 * order is then kept as [Invariants.repair] keeps it.
 */
internal object PausedTimetable {

    /** [chosen] held to [automatic], both for the same date, school and point. */
    fun combine(chosen: PrayerDay, automatic: PrayerDay): PrayerDay {
        require(chosen.date == automatic.date) { "${chosen.date} and ${automatic.date}" }
        val setByRule = mutableSetOf<Prayer>()

        /** [prayer]'s time as [from] has it, with the high-latitude mark it has there. */
        fun take(prayer: Prayer, from: PrayerDay, time: (PrayerDay) -> Instant): Instant {
            if (prayer in from.setByRule) setByRule += prayer
            return time(from)
        }

        fun later(time: (PrayerDay) -> Instant): PrayerDay = if (time(automatic) > time(chosen)) automatic else chosen
        fun earlier(time: (PrayerDay) -> Instant): PrayerDay = if (time(automatic) < time(chosen)) automatic else chosen

        val fajrFrom = later { it.fajr }
        // Sunrise is the earlier where it is after the Fajr shown; otherwise the Fajr's own day's.
        val sunriseFrom = earlier { it.sunrise }.takeIf { it.sunrise > fajrFrom.fajr } ?: fajrFrom
        val startFrom = mapOf(
            Prayer.FAJR to fajrFrom,
            Prayer.SUNRISE to sunriseFrom,
            Prayer.DHUHR to later { it.dhuhr },
            Prayer.ASR to later { it.asr },
            Prayer.MAGHRIB to later { it.maghrib },
            Prayer.ISHA to later { it.isha },
        )
        val ends = chosen.ends.mapNotNull { (prayer, mine) ->
            val from = startFrom.getValue(prayer)
            val earliest = automatic.ends[prayer]?.let { minOf(it, mine) } ?: mine
            val end = if (earliest > from.start(prayer)) earliest else from.ends[prayer]
            end?.let { prayer to it }
        }.toMap()
        val combined = chosen.copy(
            fajr = take(Prayer.FAJR, fajrFrom) { it.fajr },
            sunrise = take(Prayer.SUNRISE, sunriseFrom) { it.sunrise },
            dhuhr = take(Prayer.DHUHR, startFrom.getValue(Prayer.DHUHR)) { it.dhuhr },
            asr = take(Prayer.ASR, startFrom.getValue(Prayer.ASR)) { it.asr },
            asrOther = maxOf(chosen.asrOther, automatic.asrOther),
            maghrib = take(Prayer.MAGHRIB, startFrom.getValue(Prayer.MAGHRIB)) { it.maghrib },
            isha = take(Prayer.ISHA, startFrom.getValue(Prayer.ISHA)) { it.isha },
            sunset = minOf(chosen.sunset, automatic.sunset),
            endOfEating = minOf(chosen.endOfEating, automatic.endOfEating),
            imsak = chosen.imsak?.let { mine -> automatic.imsak?.let { minOf(it, mine) } ?: mine },
            ends = ends,
            setByRule = setByRule,
            polar = chosen.polar || automatic.polar,
            repaired = chosen.repaired || automatic.repaired,
            notFollowed = chosen.notFollowed + automatic.notFollowed,
        )
        return Invariants.repair(combined)
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
