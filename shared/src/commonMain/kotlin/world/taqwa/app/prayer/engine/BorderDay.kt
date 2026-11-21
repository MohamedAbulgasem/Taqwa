package world.taqwa.app.prayer.engine

import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.PrayerDay
import kotlin.time.Instant

/**
 * A place that may stand in either of two countries (ruling R259, the second trust audit's G4 and G5): the day held
 * to each country's own day there ([world.taqwa.app.prayer.engine.registry.Registry.border]). The days are combined
 * as a chosen timetable is held to Automatic's until confirmed ([PausedTimetable.combine], ruling R52): each start
 * the later, sunrise, sunset, the end of eating, imsak and every end the earlier, a time set by a high-latitude rule
 * keeping that mark from the day it came from, and what either day declares not followed staying declared. Strictly
 * so, with no most-followed member (the far-north Maghrib of ruling R155 is a cautious entry's own, between timetables
 * of one place; here neither country's Maghrib may be passed by).
 *
 * Besides: [PrayerDay.earliestStart] keeps each prayer's earliest start of either side ("Show where timetables
 * differ"), and a held Maghrib ([PrayerDay.maghribHeld]) is the held one only where the Maghrib shown is that day's.
 */
internal object BorderDay {

    /** [days], one for each side, the nearest city's country's first, all for the same date, school and point. */
    fun combine(days: List<PrayerDay>): PrayerDay {
        require(days.size >= 2) { "a border day needs two sides" }
        val combined = days.reduce { shown, other -> PausedTimetable.combine(shown, other) }
        val maghribFrom = days.firstOrNull { it.maghrib == combined.maghrib } ?: days.first()
        val held = maghribFrom.maghribHeld && combined.maghrib == maghribFrom.maghrib
        return combined.copy(
            earliestStart = ObligatoryPrayers.associateWith { prayer -> days.minOf { it.earliestStart[prayer] ?: it.start(prayer) } },
            methodId = "border:" + days.joinToString("+") { it.methodId },
            maghribHeld = held,
            maghribBeforeHold = maghribFrom.maghribBeforeHold.takeIf { held },
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
