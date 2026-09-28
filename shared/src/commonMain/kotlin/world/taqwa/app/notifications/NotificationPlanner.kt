package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.prayer.NightThirds
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

object NotificationPlanner {

    /** iOS will hold no more than this many pending local notifications. It is a platform limit. */
    const val IOS_PENDING_LIMIT = 64

    /**
     * How many whole days of prayers fit in [capacity]. Five obligatory prayers a day, doubled
     * when a reminder precedes each one — so 64 slots is twelve days, or six with reminders on —
     * and one more a day when Tahajjud is on.
     */
    fun windowDaysFor(capacity: Int, notifications: NotificationSettings): Int {
        val perDay = ObligatoryPrayers.size * (if (notifications.remindBeforeMinutes > 0) 2 else 1) +
            if (notifications.tahajjud) 1 else 0
        return (capacity / perDay).coerceAtLeast(1)
    }

    fun plan(
        location: GeoLocation,
        settings: PrayerSettings,
        notifications: NotificationSettings,
        engine: PrayerTimesEngine,
        from: Instant,
        windowDays: Int,
        capacity: Int,
        copy: NotificationCopy = EnglishNotificationCopy,
        formatClockTime: (Instant, String) -> String = ::isoClockTime,
    ): List<ScheduledNotification> {
        if (!notifications.enabled) return emptyList()

        val zone = TimeZone.of(location.timeZoneId)
        // The plan starts a day early: in a high-latitude summer yesterday's Isha (even its
        // Maghrib) can fall after midnight (spec §3.3), and a reschedule between midnight and that
        // Isha must keep its alarm. Only what is still after [from] is kept, so of yesterday that
        // is at most the starts that fall after midnight; the window still ends where it did.
        val firstDate = from.toLocalDateTime(zone).date.plus(-1, DateTimeUnit.DAY)
        val lead = notifications.remindBeforeMinutes
        val out = mutableListOf<ScheduledNotification>()

        // Carried across days as well as within one, so the Fajr reminder is measured against
        // the previous evening's Isha rather than against nothing.
        var previousPrayerInstant: Instant? = null

        // The night a Tahajjud belongs to began the evening before, so the first day of the
        // window needs the day before it: its Maghrib begins the night, and its Isha must be in
        // before a night prayer is called. Only computed when it will be used.
        var previousDay: DayPrayerTimes? = if (notifications.tahajjud) {
            engine.timesFor(location, firstDate.plus(-1, DateTimeUnit.DAY), settings)
        } else {
            null
        }

        // Carries each day's times into the next iteration's `times`, so every date the loop
        // touches is solved once: `nextFajr` below needs tomorrow's Fajr a day ahead of the loop's
        // own cursor, and without this it would otherwise ask the engine for the same date twice.
        var carriedTimes: DayPrayerTimes? = null

        for (offset in 0 until windowDays + 1) {
            val date = firstDate.plus(offset, DateTimeUnit.DAY)
            // Recomputing per local date is what makes DST correct: the engine returns instants,
            // and a day that is 23 or 25 hours long still has exactly five prayers.
            val times = carriedTimes ?: engine.timesFor(location, date, settings)
            val nextDayTimes = engine.timesFor(location, date.plus(1, DateTimeUnit.DAY), settings)
            carriedTimes = nextDayTimes
            // R72/R83: on the Longyearbyen/Bodo nights where the combined polar Maghrib reaches or
            // passes the next end of eating, the Isha window is empty and Maghrib's own start can
            // already sit at or after that morning's Fajr. Neither belongs in the plan: an alarm
            // for a prayer after the next one (here, the next day's Fajr) has begun is wrong.
            val nextFajr = nextDayTimes.time(Prayer.FAJR)

            val evening = previousDay
            if (notifications.tahajjud && evening != null) {
                val maghrib = evening.time(Prayer.MAGHRIB)
                val isha = evening.time(Prayer.ISHA)
                val fajr = times.time(Prayer.FAJR)
                val at = NightThirds.lastThirdStart(maghrib, fajr)
                // Strictly inside the night and after its Isha: where the times have crossed there
                // is no last third to announce, and Fajr's own notification is about to say the
                // rest; where the last third begins before Isha (a short far-northern night,
                // Fairbanks in mid-April and late August), a night prayer is not called before
                // the night's obligatory one.
                if (at > from && at > maghrib && at > isha && at < fajr) {
                    out += ScheduledNotification(
                        id = "TAHAJJUD-$date",
                        prayer = Prayer.FAJR,
                        kind = NotificationKind.TAHAJJUD,
                        instant = at,
                        timeZoneId = location.timeZoneId,
                        sound = notifications.tahajjudSound,
                        voice = notifications.voice,
                        title = copy.title(Prayer.FAJR, NotificationKind.TAHAJJUD),
                        body = copy.body(
                            Prayer.FAJR, NotificationKind.TAHAJJUD, formatClockTime(fajr, location.timeZoneId), 0,
                        ),
                    )
                }
            }
            previousDay = if (notifications.tahajjud) times else null

            ObligatoryPrayers.forEach { prayer ->
                val at = times.time(prayer)
                val clock = formatClockTime(at, location.timeZoneId)

                // R83: Maghrib and Isha's own "next obligatory prayer" is the next day's Fajr; a
                // start at or after it has an empty window and is skipped entirely, reminder
                // included. `previousPrayerInstant` still advances below, unconditionally: it is
                // this prayer's actual instant, not whether it was announced, and the next
                // prayer's reminder is still clamped against it.
                val emptyWindow = (prayer == Prayer.MAGHRIB || prayer == Prayer.ISHA) && at >= nextFajr

                if (!emptyWindow) {
                    if (at > from) {
                        out += ScheduledNotification(
                            id = "${prayer.name}-PRAYER-$date",
                            prayer = prayer,
                            kind = NotificationKind.PRAYER,
                            instant = at,
                            timeZoneId = location.timeZoneId,
                            sound = notifications.soundFor(prayer),
                            voice = notifications.voice,
                            title = copy.title(prayer, NotificationKind.PRAYER),
                            body = copy.body(prayer, NotificationKind.PRAYER, clock, 0),
                        )
                    }

                    if (lead > 0) {
                        // Clamped to the previous prayer. With the 30-minute lead and a short
                        // Maghrib->Isha gap — high latitude in summer, or a user's own minute
                        // adjustments pulling the two together — an unclamped reminder for Isha
                        // can land before Maghrib has even been called, which reads as a wrong
                        // time rather than as a nudge.
                        val remindAt = maxOf(at - lead.minutes, previousPrayerInstant ?: Instant.DISTANT_PAST)
                        if (remindAt > from) {
                            out += ScheduledNotification(
                                id = "${prayer.name}-REMINDER-$date",
                                prayer = prayer,
                                kind = NotificationKind.REMINDER,
                                instant = remindAt,
                                timeZoneId = location.timeZoneId,
                                // A reminder is a nudge, not the call: it never plays the adhan.
                                sound = PrayerSound.NOTIFICATION,
                                // Stamped even though the chime ignores it, so every entry in a
                                // plan carries the setting it was planned under.
                                voice = notifications.voice,
                                title = copy.title(prayer, NotificationKind.REMINDER),
                                body = copy.body(prayer, NotificationKind.REMINDER, clock, lead),
                            )
                        }
                    }
                }
                previousPrayerInstant = at
            }
        }

        return out.sortedBy { it.instant }.take(capacity)
    }
}
