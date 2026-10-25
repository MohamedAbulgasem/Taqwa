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
 * - Maghrib (ruling R155, the owner's decision of 24 Oct 2026) is the latest time any member gives
 *   from the sun ([PrayerDay.maghribBySun]: its sunset plus its own minutes, before its own
 *   day-length rule holds it later), never before the most-followed member's own Maghrib, and never
 *   before the minute after the real sunset at the user's point. A Maghrib a member's far-north or
 *   winter rule sets rather than the sun (Diyanet's five-hour winter day, a time borrowed on a polar
 *   day, a clock rule's estimate) counts only when that member is the most followed; and where the
 *   real sun does not set at the user's point that day no member's Maghrib counts as from the sun
 *   (a member's own sun model can still set it). South of about 62.6° every member follows the sun,
 *   so Maghrib is the latest member's, like every other start.
 * - [PrayerDay.earliestStart] keeps each prayer's earliest member start, for "Show where timetables
 *   differ"; a declared Isha's placeholder (DayComputer's point 9, the minute after that member's
 *   Maghrib) is no member's start, so Isha's is the earliest among the members whose Isha is in play.
 * - Each prayer ends at the earliest end in play: a member's own ends where it has them (so Maghrib
 *   ends at the earliest Isha or the earliest red twilight), else the combined sunrise, sunset,
 *   earliest Standard Asr and earliest Isha; Isha's end only when every member has one. As R26 leaves
 *   out an Isha end not after the latest Isha, a Maghrib end not after the Maghrib shown is left out (R158)
 *   (in the far north a member's Isha can come a minute or two after its own Maghrib and no later
 *   than another member's, the Maghrib shown): never a later member's end, which would be late.
 * - A time is [PrayerDay.setByRule], and the day [PrayerDay.polar], only when every member it was
 *   taken from (every member giving the chosen minute) is, and never through no member; `repaired`
 *   if any member was. Maghrib is taken from the members whose Maghrib from the sun is the minute
 *   shown, and from the most-followed member where its own Maghrib is; a minute only the real sunset
 *   gives is taken from no member, and marks nothing.
 * - What any member declares [PrayerDay.notFollowed] the day declares: a member's own time that no
 *   day in order can show (a Fajr the sun had risen by, ruling R90) is not shown here either, whether
 *   or not another member's later time is.
 */
object Cautious {

    /**
     * [members]' cautious day; [mostFollowed] is the index of the most-followed member (the lowest share rank), and
     * [realSunset] the real sunset at the user's point that day ([world.taqwa.app.prayer.engine.DayPipeline.realSunset]),
     * unrounded, or null where the sun does not set there that day.
     */
    fun combine(members: List<PrayerDay>, mostFollowed: Int, realSunset: Instant?): PrayerDay {
        require(members.isNotEmpty()) { "a cautious day needs members" }
        require(mostFollowed in members.indices) { "most-followed member $mostFollowed of ${members.size}" }
        val date = members.first().date
        require(members.all { it.date == date }) { "members for different dates: ${members.map { it.date }}" }

        fun latest(time: (PrayerDay) -> Instant) = members.maxOf(time)
        fun earliest(time: (PrayerDay) -> Instant) = members.minOf(time)

        // Ruling R155: each member's Maghrib from the sun, where the real sun sets at the user's point.
        val bySun = members.map { member -> member.maghribBySun.takeIf { realSunset != null } }
        val sunsetFloor = realSunset?.let { minuteAfter(it) }
        val chief = members[mostFollowed]
        val maghrib = (bySun.filterNotNull() + chief.maghrib + listOfNotNull(sunsetFloor)).max()
        val maghribFromSun = members.indices.filter { bySun[it] == maghrib }
        val maghribFrom = (maghribFromSun + listOf(mostFollowed).filter { chief.maghrib == maghrib }).distinct().map { members[it] }
        // Held where only the most-followed member's own day-length rule gives the minute shown, and only with a Maghrib
        // the sun gives before the hold: where the real sun does not set at the user's point nothing comes from the sun,
        // so the day shows the most-followed member's own minute without a hold (PrayerDay.maghribBeforeHold never null
        // on a held day).
        val beforeHold = (bySun.filterNotNull() + listOfNotNull(sunsetFloor)).maxOrNull()
        val maghribHeld = beforeHold != null && maghribFromSun.isEmpty() && maghrib != sunsetFloor && chief.maghrib == maghrib &&
            chief.maghribHeld

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
            // The earliest member's end, and none where it is not after the latest Isha (ruling R26). The pipeline's
            // members come without Isha ends (DayPipeline.members), so there R26 acts through Ends.withNextDay, which
            // leaves out the next day's end of eating where it is not after the Isha shown.
            nextEndOfEating = ishaEnds.takeIf { it.size == members.size }?.min()
                ?.takeIf { end -> end > latest { it.isha } },
        ).let { ends ->
            // Ruling R158, as R26 leaves out an Isha end not after the latest Isha (Ends.withNextDay): a Maghrib end not
            // after the Maghrib shown is left out (Haukipudas on 4 June 2027: Diyanet's Isha 23:57, a minute after its own
            // Maghrib, and Rabita's Maghrib 23:58, the latest from the sun). By that member's timetable Maghrib's time is
            // over when it is shown; a later member's end would be a late end.
            if (ends.getValue(Prayer.MAGHRIB) > maghrib) ends else ends - Prayer.MAGHRIB
        }

        val chosen = mapOf(
            Prayer.FAJR to fajr,
            Prayer.SUNRISE to sunrise,
            Prayer.DHUHR to latest { it.dhuhr },
            Prayer.ASR to latest { it.asr },
            Prayer.ISHA to latest { it.isha },
        )
        // A time is set by rule, and the day polar, only through the members it was taken from;
        // where a member with the real sign gives the same minute, that minute is real.
        val takenFrom = chosen.mapValues { (prayer, time) -> members.filter { it.start(prayer) == time } } +
            (Prayer.MAGHRIB to maghribFrom)
        val setByRule = takenFrom.filter { (prayer, from) -> from.isNotEmpty() && from.all { prayer in it.setByRule } }.keys

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
            polar = takenFrom.values.any { from -> from.isNotEmpty() && from.all { it.polar } },
            repaired = members.any { it.repaired },
            methodId = "cautious:" + members.joinToString("+") { it.methodId },
            notFollowed = members.flatMap { it.notFollowed }.toSet(),
            maghribHeld = maghribHeld,
            maghribBeforeHold = beforeHold.takeIf { maghribHeld },
        )
    }

    /** The minute after [instant] rounded down to its minute: the first whole minute the sun has set by. */
    internal fun minuteAfter(instant: Instant): Instant =
        Instant.fromEpochSeconds(instant.epochSeconds.floorDiv(60L) * 60) + 1.minutes

    private fun PrayerDay.start(prayer: Prayer): Instant = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}
