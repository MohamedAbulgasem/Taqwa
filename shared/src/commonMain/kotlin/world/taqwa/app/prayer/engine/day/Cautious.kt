package world.taqwa.app.prayer.engine.day

import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Cautious times over the timetables in use where no majority follows one (spec §3.6): the
 * members are the days of each published timetable followed by roughly a tenth or more of a
 * place's mosques.
 *
 * - Each prayer starts at the latest member's start (the other school's Asr too); sunrise, sunset,
 *   the end of eating and imsak are the earliest member's.
 * - Where the earliest member's sunrise is not after the Fajr shown (ruling R90: at the polar edge,
 *   one member's sunrise precaution before another's dawn), the sunrise, and Fajr's end, are the
 *   earliest among the members whose Fajr is shown, each of which keeps its own Fajr before its own
 *   sunrise: Fajr keeps its promise and the end gives way, as R82(c).
 * - Maghrib is the latest member's while every member's Maghrib is within [MAGHRIB_AGREEMENT] of
 *   the others; beyond that the members are stacking their own precautions, and Maghrib is capped at
 *   the most-followed member's.
 * - [PrayerDay.earliestStart] keeps each prayer's earliest member start, for "Show where timetables
 *   differ"; a declared Isha's placeholder (DayComputer's point 9, the minute after that member's
 *   Maghrib) is no member's start, so Isha's is the earliest among the members whose Isha is in play.
 * - Each prayer ends at the earliest end in play: a member's own ends where it has them (so Maghrib
 *   ends at the earliest Isha or the earliest red twilight), else the combined sunrise, sunset,
 *   earliest Standard Asr and earliest Isha; Isha's end only when every member has one.
 * - A time is [PrayerDay.setByRule], and the day [PrayerDay.polar], only when every member it was
 *   taken from (every member giving the chosen minute) is; `repaired` if any member was.
 * - What any member declares [PrayerDay.notFollowed] the day declares: a member's own time that no
 *   day in order can show (a Fajr the sun had risen by, ruling R90) is not shown here either, whether
 *   or not another member's later time is.
 */
object Cautious {
    val MAGHRIB_AGREEMENT = 2.minutes

    fun combine(members: List<PrayerDay>, mostFollowed: Int): PrayerDay {
        require(members.isNotEmpty()) { "a cautious day needs members" }
        require(mostFollowed in members.indices) { "most-followed member $mostFollowed of ${members.size}" }
        val date = members.first().date
        require(members.all { it.date == date }) { "members for different dates: ${members.map { it.date }}" }

        fun latest(time: (PrayerDay) -> Instant) = members.maxOf(time)
        fun earliest(time: (PrayerDay) -> Instant) = members.minOf(time)

        val maghribs = members.map { it.maghrib }
        val agree = maghribs.max() - maghribs.min() <= MAGHRIB_AGREEMENT
        val maghrib = if (agree) maghribs.max() else members[mostFollowed].maghrib

        // A member whose Isha is declared not followed (DayComputer's point 9: its rule's Isha fell before
        // its own Maghrib, so it shows the minute after) has no Isha in play: it neither closes Maghrib's
        // window nor counts as a member's start in "Show where timetables differ". The earliest Isha, and
        // the members' own Maghrib ends, are the other members' (all of them where every member declares).
        val ishaInPlay = members.filter { Prayer.ISHA !in it.notFollowed }.ifEmpty { members }
        val earliestStart = ObligatoryPrayers.associateWith { prayer ->
            (if (prayer == Prayer.ISHA) ishaInPlay else members).minOf { it.start(prayer) }
        }
        val fajr = latest { it.fajr }
        val earliestSunrise = earliest { it.sunrise }
        // Ruling R90: the earliest sunrise where it is after the Fajr shown; otherwise the earliest
        // among the members whose Fajr is shown, so that Fajr stays before the sunrise.
        val sunrise = if (earliestSunrise > fajr) earliestSunrise else members.filter { it.fajr == fajr }.minOf { it.sunrise }
        val sunset = earliest { it.sunset }
        val ishaEnds = members.mapNotNull { it.ends[Prayer.ISHA] }
        val ends = Ends.of(
            sunrise = sunrise,
            standardAsr = earliest { it.ends[Prayer.DHUHR] ?: minOf(it.asr, it.asrOther) },
            sunset = sunset,
            earliestIsha = ishaInPlay.minOf { it.isha },
            maghribEndCap = ishaInPlay.mapNotNull { it.ends[Prayer.MAGHRIB] }.minOrNull(),
            // The earliest member's end, and none where it is not after the latest Isha (ruling R26).
            nextEndOfEating = ishaEnds.takeIf { it.size == members.size }?.min()
                ?.takeIf { end -> end > latest { it.isha } },
        )

        val chosen = mapOf(
            Prayer.FAJR to fajr,
            Prayer.SUNRISE to sunrise,
            Prayer.DHUHR to latest { it.dhuhr },
            Prayer.ASR to latest { it.asr },
            Prayer.MAGHRIB to maghrib,
            Prayer.ISHA to latest { it.isha },
        )
        // A time is set by rule, and the day polar, only through the members it was taken from;
        // where a member with the real sign gives the same minute, that minute is real.
        val takenFrom = chosen.mapValues { (prayer, time) -> members.filter { it.start(prayer) == time } }
        val setByRule = takenFrom.filter { (prayer, from) -> from.all { prayer in it.setByRule } }.keys

        return PrayerDay(
            date = date,
            fajr = chosen.getValue(Prayer.FAJR),
            sunrise = sunrise,
            dhuhr = chosen.getValue(Prayer.DHUHR),
            asr = chosen.getValue(Prayer.ASR),
            asrOther = latest { it.asrOther },
            maghrib = maghrib,
            isha = chosen.getValue(Prayer.ISHA),
            sunset = sunset,
            endOfEating = earliest { it.endOfEating },
            imsak = members.mapNotNull { it.imsak }.minOrNull(),
            earliestStart = earliestStart,
            ends = ends,
            setByRule = setByRule,
            polar = takenFrom.values.any { from -> from.all { it.polar } },
            repaired = members.any { it.repaired },
            methodId = "cautious:" + members.joinToString("+") { it.methodId },
            notFollowed = members.flatMap { it.notFollowed }.toSet(),
        )
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
