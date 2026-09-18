package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
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
        val firstDate = from.toLocalDateTime(zone).date
        val lead = notifications.remindBeforeMinutes
        val out = mutableListOf<ScheduledNotification>()

        // Carried across days as well as within one, so the Fajr reminder is measured against
        // the previous evening's Isha rather than against nothing.
        var previousPrayerInstant: Instant? = null

        // The night a Tahajjud belongs to began the evening before, so the first day of the
        // window needs the Maghrib of the day before it. Only computed when it will be used.
        var previousMaghrib: Instant? = if (notifications.tahajjud) {
            engine.timesFor(location, firstDate.plus(-1, DateTimeUnit.DAY), settings).time(Prayer.MAGHRIB)
        } else {
            null
        }

        for (offset in 0 until windowDays) {
            val date = firstDate.plus(offset, DateTimeUnit.DAY)
            // Recomputing per local date is what makes DST correct: the engine returns instants,
            // and a day that is 23 or 25 hours long still has exactly five prayers.
            val times = engine.timesFor(location, date, settings)

            val evening = previousMaghrib
            if (notifications.tahajjud && evening != null) {
                val fajr = times.time(Prayer.FAJR)
                val at = NightThirds.lastThirdStart(evening, fajr)
                // Strictly inside the night: where the times have crossed there is no last
                // third to announce, and Fajr's own notification is about to say the rest.
                if (at > from && at > evening && at < fajr) {
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
            previousMaghrib = if (notifications.tahajjud) times.time(Prayer.MAGHRIB) else null

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
                        voice = notifications.voice,
                        title = copy.title(prayer, NotificationKind.PRAYER),
                        body = copy.body(prayer, NotificationKind.PRAYER, clock, 0),
                    )
                }

                if (lead > 0) {
                    // Clamped to the previous prayer. With the 30-minute lead and a short
                    // Maghrib->Isha gap — high latitude in summer, or a user's own minute
                    // adjustments pulling the two together — an unclamped reminder for Isha can
                    // land before Maghrib has even been called, which reads as a wrong time
                    // rather than as a nudge.
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
                previousPrayerInstant = at
            }
        }

        return out.sortedBy { it.instant }.take(capacity)
    }
}
