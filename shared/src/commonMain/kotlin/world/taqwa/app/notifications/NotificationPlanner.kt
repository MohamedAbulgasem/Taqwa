package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

object NotificationPlanner {

    /** iOS will hold no more than this many pending local notifications. It is a platform limit. */
    const val IOS_PENDING_LIMIT = 64

    /**
     * How many whole days of prayers fit in [capacity]. Five obligatory prayers a day, doubled
     * when a reminder precedes each one — so 64 slots is twelve days, or six with reminders on.
     */
    fun windowDaysFor(capacity: Int, notifications: NotificationSettings): Int {
        val perDay = ObligatoryPrayers.size * if (notifications.remindBeforeMinutes > 0) 2 else 1
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
        val firstDate = from.toLocalDateTime(zone).date
        val lead = notifications.remindBeforeMinutes
        val out = mutableListOf<ScheduledNotification>()

        for (offset in 0 until windowDays) {
            val date = firstDate.plus(offset, DateTimeUnit.DAY)
            // Recomputing per local date is what makes DST correct: the engine returns instants,
            // and a day that is 23 or 25 hours long still has exactly five prayers.
            val times = engine.timesFor(location, date, settings)

            ObligatoryPrayers.forEach { prayer ->
                val at = times.time(prayer)
                val clock = formatClockTime(at, location.timeZoneId)

                if (at > from) {
                    out += ScheduledNotification(
                        id = "${prayer.name}-PRAYER-$date",
                        prayer = prayer,
                        kind = NotificationKind.PRAYER,
                        instant = at,
                        timeZoneId = location.timeZoneId,
                        sound = notifications.soundFor(prayer),
                        title = copy.title(prayer, NotificationKind.PRAYER),
                        body = copy.body(prayer, NotificationKind.PRAYER, clock, 0),
                    )
                }

                if (lead > 0) {
                    val remindAt = at - lead.minutes
                    if (remindAt > from) {
                        out += ScheduledNotification(
                            id = "${prayer.name}-REMINDER-$date",
                            prayer = prayer,
                            kind = NotificationKind.REMINDER,
                            instant = remindAt,
                            timeZoneId = location.timeZoneId,
                            // A reminder is a nudge, not the call: it never plays the adhan.
                            sound = PrayerSound.NOTIFICATION,
                            title = copy.title(prayer, NotificationKind.REMINDER),
                            body = copy.body(prayer, NotificationKind.REMINDER, clock, lead),
                        )
                    }
                }
            }
        }

        return out.sortedBy { it.instant }.take(capacity)
    }
}
