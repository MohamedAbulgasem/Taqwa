package world.taqwa.app.prayer

import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import kotlin.time.Instant

object TimelineBuilder {

    fun build(
        today: DayPrayerTimes,
        tomorrow: DayPrayerTimes,
        now: Instant,
        showSunrise: Boolean,
    ): TodayState {
        val visible = today.times.filter { showSunrise || it.prayer != Prayer.SUNRISE }

        // "Current" is the most recent obligatory prayer whose time has arrived.
        val currentPrayer = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .lastOrNull { it.instant <= now }
            ?.prayer

        val rows = visible.map { pt ->
            TimelineRow(
                prayer = pt.prayer,
                instant = pt.instant,
                status = when {
                    pt.prayer == currentPrayer -> PrayerStatus.CURRENT
                    pt.instant <= now -> PrayerStatus.PASSED
                    else -> PrayerStatus.UPCOMING
                },
            )
        }

        val next = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .firstOrNull { it.instant > now }
            ?: PrayerTime(Prayer.FAJR, tomorrow.time(Prayer.FAJR))

        val previous = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .lastOrNull { it.instant <= now }
            ?.instant
            ?: (today.time(Prayer.FAJR) - (next.instant - today.time(Prayer.FAJR)))

        val total = (next.instant - previous).inWholeSeconds
        val elapsed = (now - previous).inWholeSeconds
        val progress = if (total <= 0L) 0f else (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

        return TodayState(
            rows = rows,
            next = next,
            countdown = next.instant - now,
            ringProgress = progress,
        )
    }
}
