package world.taqwa.app.prayer

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import kotlin.time.Instant

object TimelineBuilder {

    /**
     * [yesterday] and [tomorrow] exist for the same reason: the ring measures the interval the
     * user is currently inside, and between midnight and Fajr that interval starts on yesterday's
     * Isha, just as the interval after Isha ends on tomorrow's Fajr.
     *
     * [zone] is the location's, the one [today] was computed in; it decides which row is
     * Jumuʿah (see [isJumuah]).
     */
    fun build(
        yesterday: DayPrayerTimes,
        today: DayPrayerTimes,
        tomorrow: DayPrayerTimes,
        now: Instant,
        showSunrise: Boolean,
        zone: TimeZone,
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
                isJumuah = isJumuah(pt.prayer, pt.instant, zone),
            )
        }

        val next = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .firstOrNull { it.instant > now }
            ?: PrayerTime(Prayer.FAJR, tomorrow.time(Prayer.FAJR))

        // Post-midnight, no obligatory prayer today has passed yet, so the interval the user is
        // inside began at yesterday's Isha. Synthesising it from today's own Fajr (the previous
        // shape) collapsed to Fajr itself, since `next` is Fajr in exactly that case, leaving
        // `total` at 0 and the ring flat from midnight until Fajr every night.
        val previous = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .lastOrNull { it.instant <= now }
            ?.instant
            ?: yesterday.time(Prayer.ISHA)

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

    /**
     * Whether this is Friday's Dhuhr, which the Prayer screen marks as Jumuʿah.
     *
     * The day is read in [zone], the location's own, and never the device's: the timeline shows
     * the chosen city's day, so a phone still on Thursday evening that is showing Jakarta, where
     * Friday has begun, marks Jakarta's Friday Dhuhr.
     */
    fun isJumuah(prayer: Prayer, instant: Instant, zone: TimeZone): Boolean =
        prayer == Prayer.DHUHR && instant.toLocalDateTime(zone).dayOfWeek == DayOfWeek.FRIDAY
}
