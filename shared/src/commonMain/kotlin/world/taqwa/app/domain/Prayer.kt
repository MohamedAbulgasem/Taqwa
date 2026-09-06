package world.taqwa.app.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/** The five that are prayed. Sunrise marks the end of the Fajr window and is never notified. */
val ObligatoryPrayers = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

data class PrayerTime(val prayer: Prayer, val instant: Instant)

data class DayPrayerTimes(
    val date: LocalDate,
    val times: List<PrayerTime>,
    val highLatitudeRuleApplied: HighLatitudePreference?,
    val nearestLatitudeFallbackApplied: Boolean = false,
) {
    fun time(p: Prayer): Instant =
        times.firstOrNull { it.prayer == p }?.instant
            ?: error("No time computed for $p on $date")
}
