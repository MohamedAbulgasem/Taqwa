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
     * The Prayer screen's timeline (spec §2.1): today's rows, the next start and the ring.
     *
     * - **Current** is the latest prayer that has begun, as today, except that Fajr stops being
     *   current at sunrise and Asr at sunset ([DayPrayerTimes.ends], decision §10.7): a Fajr prayed
     *   after sunrise is late, so nothing is highlighted until Dhuhr. A day without those ends (a
     *   polar one can lack them) keeps today's rule.
     * - **Next** is the first start after [now], whichever day it belongs to, and is never an
     *   instant at or before [now] (never a passed prayer, follow-up to R83). In a high-latitude
     *   summer [yesterday]'s Isha, even its Maghrib, can fall after local midnight (Tromsø on 17 May:
     *   Maghrib 00:13 on the 18th), and it counts while it is still ahead, but only when it starts
     *   before today's Fajr: the 17th's Isha at 00:41, after the 18th's Fajr at 00:38, does not. An
     *   Isha's end may be missing (ruling R26); nothing here reads it. On the rare nights where a
     *   combined polar Maghrib/Isha reaches or passes the next day's Fajr (R72), [tomorrow]'s own
     *   Fajr is itself already behind [now]; see [firstOfTomorrowAfter].
     * - The **ring** fills from the latest start that has arrived to the next one; the highlight
     *   rule does not move it, so after sunrise it still counts from Fajr to Dhuhr.
     *
     * Only today's rows are listed: a prayer of yesterday's that has begun after midnight is the
     * ring's start and highlights no row.
     *
     * [zone] is the location's, the one [today] was computed in; it decides which row is Jumuʿah
     * (see [isJumuah]). The widgets keep today's rule until the Ramadan release: [buildForWidgets].
     */
    fun build(
        yesterday: DayPrayerTimes,
        today: DayPrayerTimes,
        tomorrow: DayPrayerTimes,
        now: Instant,
        showSunrise: Boolean,
        zone: TimeZone,
    ): TodayState {
        val todays = ObligatoryPrayers.map { PrayerTime(it, today.time(it)) }
        val yesterdays = yesterdaysStarts(yesterday, today)

        val latestToday = todays.filter { it.instant <= now }.maxByOrNull { it.instant }
        val latestYesterday = yesterdays.filter { it.instant <= now }.maxByOrNull { it.instant }
        // Today's prayer is current only when nothing of yesterday's began after it, and only while
        // its time lasts on the screen's rule.
        val current = latestToday
            ?.takeIf { latestYesterday == null || it.instant >= latestYesterday.instant }
            ?.takeIf { !hasEndedForTheHighlight(it.prayer, today, now) }
            ?.prayer

        val rows = today.times.filter { showSunrise || it.prayer != Prayer.SUNRISE }.map { pt ->
            TimelineRow(
                prayer = pt.prayer,
                instant = pt.instant,
                status = when {
                    pt.prayer == current -> PrayerStatus.CURRENT
                    pt.instant <= now -> PrayerStatus.PASSED
                    else -> PrayerStatus.UPCOMING
                },
                isJumuah = isJumuah(pt.prayer, pt.instant, zone),
            )
        }

        val next = (yesterdays + todays).filter { it.instant > now }.minByOrNull { it.instant }
            ?: firstOfTomorrowAfter(tomorrow, now)

        val previous = listOfNotNull(latestToday, latestYesterday).maxOfOrNull { it.instant }
            ?: yesterday.time(Prayer.ISHA)

        return TodayState(
            rows = rows,
            next = next,
            countdown = next.instant - now,
            ringProgress = progress(previous, next.instant, now),
        )
    }

    /**
     * [yesterday]'s obligatory starts that still count on [today] (spec §3.3): in a high-latitude
     * summer its Isha, even its Maghrib, can fall after local midnight, and it is the next prayer
     * while it is ahead. Each counts only while it starts before today's Fajr: once today's Fajr is
     * in, the night is over, whatever a later start of yesterday's says (an interim guard; the
     * engine's order is its own ruling).
     */
    fun yesterdaysStarts(yesterday: DayPrayerTimes, today: DayPrayerTimes): List<PrayerTime> =
        ObligatoryPrayers.map { PrayerTime(it, yesterday.time(it)) }
            .filter { it.instant < today.time(Prayer.FAJR) }

    /**
     * The fallback once nothing of yesterday's or today's remains ahead of [now]: the first of
     * [tomorrow]'s obligatory starts that is still after it. On an ordinary day that is always
     * tomorrow's Fajr, the earliest of its five. On the rare nights where a combined polar
     * Maghrib/Isha reaches or passes the next day's Fajr (R72), that Fajr is already behind [now],
     * and checking only it — the previous shape of this fallback — let an already-passed instant
     * back out as "next" (with a negative countdown): the real fix is checking all five, not just
     * Fajr.
     *
     * The final `?:` is defensive, not a path real data reaches: a caller builds [today] (and so
     * [tomorrow]) from [now]'s own date in the timeline's zone, which puts [now] before tomorrow's
     * midnight; tomorrow's Dhuhr — solar noon, never subject to the polar substitution that can
     * move Fajr/Maghrib/Isha — falls after that midnight and so is always still ahead of [now],
     * whatever Fajr does. [minByOrNull] therefore always finds a candidate in practice.
     */
    private fun firstOfTomorrowAfter(tomorrow: DayPrayerTimes, now: Instant): PrayerTime =
        ObligatoryPrayers.map { PrayerTime(it, tomorrow.time(it)) }
            .filter { it.instant > now }
            .minByOrNull { it.instant }
            ?: PrayerTime(Prayer.FAJR, tomorrow.time(Prayer.FAJR))

    /**
     * Whether [prayer], begun, is no longer highlighted at [now]: Fajr from sunrise, Asr from sunset
     * (spec §2.1). Dhuhr, Maghrib and Isha keep today's rule; their ends mark a preferred time,
     * not a lapse the screen shows.
     */
    private fun hasEndedForTheHighlight(prayer: Prayer, day: DayPrayerTimes, now: Instant): Boolean = when (prayer) {
        Prayer.FAJR, Prayer.ASR -> day.ends[prayer]?.let { now >= it } ?: false
        else -> false
    }

    /**
     * The widgets' timeline. The highlight keeps today's rule, unchanged (spec §2.1, decision
     * §10.7, ruling R8): the latest obligatory prayer of today that has begun stays current until
     * the next one. The widgets move to [build]'s highlight in the Ramadan release; until then
     * their mirror (`WidgetMirrorWriter.snapshotOf`) is written from this.
     *
     * The next start and the countdown look across [yesterday] as the screen's do (spec §3.3,
     * ruling R8 amended), and are never a passed instant either (follow-up to R83, see
     * [firstOfTomorrowAfter]): a start of yesterday's still ahead after midnight and before today's
     * Fajr ([yesterdaysStarts]) comes first, then today's, then tomorrow's. The ring measures
     * the interval the user is currently inside, so between midnight and Fajr it starts on the
     * latest of yesterday's starts that has begun (its Isha, or its Maghrib while that Isha is
     * still ahead), just as the interval after Isha ends on tomorrow's Fajr.
     */
    fun buildForWidgets(
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

        val starts = yesterdaysStarts(yesterday, today) + ObligatoryPrayers.map { PrayerTime(it, today.time(it)) }
        val next = starts.filter { it.instant > now }.minByOrNull { it.instant }
            ?: firstOfTomorrowAfter(tomorrow, now)

        // Post-midnight, no obligatory prayer today has passed yet, so the interval the user is
        // inside began at the latest of yesterday's starts that has begun. Synthesising it from
        // today's own Fajr (an earlier shape) collapsed to Fajr itself, since `next` is Fajr in
        // exactly that case, leaving `total` at 0 and the ring flat from midnight until Fajr.
        val previous = starts.filter { it.instant <= now }.maxOfOrNull { it.instant }
            ?: yesterday.time(Prayer.ISHA)

        return TodayState(
            rows = rows,
            next = next,
            countdown = next.instant - now,
            ringProgress = progress(previous, next.instant, now),
        )
    }

    private fun progress(previous: Instant, next: Instant, now: Instant): Float {
        val total = (next - previous).inWholeSeconds
        val elapsed = (now - previous).inWholeSeconds
        return if (total <= 0L) 0f else (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
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
