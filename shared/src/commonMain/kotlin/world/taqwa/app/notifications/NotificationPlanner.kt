package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.adhkar.AdhkarSet
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
     * one more a day when Tahajjud is on, and two more when the adhkar reminder is.
     */
    fun windowDaysFor(capacity: Int, notifications: NotificationSettings): Int {
        val perDay = ObligatoryPrayers.size * (if (notifications.remindBeforeMinutes > 0) 2 else 1) +
            (if (notifications.tahajjud) 1 else 0) +
            (if (notifications.adhkar) 2 else 0)
        return (capacity / perDay).coerceAtLeast(1)
    }

    /**
     * The order the plan is filled in when [plan]'s capacity runs out (adhkar spec §5): every
     * prayer first, then the before-prayer reminders, then Tahajjud, then the adhkar reminder —
     * morning and evening as one tier, since they are one setting. Within a tier the soonest win.
     * A voluntary nudge therefore never takes the slot of an obligatory prayer's adhan on iOS's 64.
     * Every kind must have a tier, or it is never armed at all; a test holds it to that.
     */
    internal val FILL_ORDER: List<Set<NotificationKind>> = listOf(
        setOf(NotificationKind.PRAYER),
        setOf(NotificationKind.REMINDER),
        setOf(NotificationKind.TAHAJJUD),
        setOf(NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING),
    )

    /** Half an hour after Fajr and after Asr is when the adhkar reminder is meant to land. */
    private val ADHKAR_AFTER = 30.minutes

    /** It never lands later than this before sunrise or Maghrib, which close the two windows. */
    private val ADHKAR_BEFORE_END = 5.minutes

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
        /**
         * The adhkar reminders whose set has already been said ([adhkarNotificationId]s): never
         * planned, so a reschedule after a set is finished does not bring its reminder back.
         */
        doneAdhkar: Set<String> = emptySet(),
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

            if (notifications.adhkar) {
                // Morning adhkar are read between Fajr and sunrise, evening adhkar between Asr and
                // Maghrib (adhkar spec §5). Half an hour in, but never later than five minutes
                // before the window closes; where even that is not after the window opens (a
                // far-northern summer, or a hand-moved Fajr or Asr) the day has no reminder.
                for (set in AdhkarSet.entries) {
                    val morning = set == AdhkarSet.MORNING
                    val kind = if (morning) NotificationKind.ADHKAR_MORNING else NotificationKind.ADHKAR_EVENING
                    val prayer = if (morning) Prayer.FAJR else Prayer.ASR
                    val opens = times.time(prayer)
                    val closes = times.time(if (morning) Prayer.SUNRISE else Prayer.MAGHRIB)
                    val at = minOf(opens + ADHKAR_AFTER, closes - ADHKAR_BEFORE_END)
                    val id = adhkarNotificationId(set, date)
                    if (at > opens && at > from && id !in doneAdhkar) {
                        out += ScheduledNotification(
                            id = id,
                            prayer = prayer,
                            kind = kind,
                            instant = at,
                            timeZoneId = location.timeZoneId,
                            sound = notifications.adhkarSound,
                            voice = notifications.voice,
                            title = copy.title(prayer, kind),
                            // The time the window closes, which is what the reader needs to know.
                            body = copy.body(prayer, kind, formatClockTime(closes, location.timeZoneId), 0),
                            windowEnd = closes,
                        )
                    }
                }
            }

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
                        // The minutes it says are the minutes really left when it rings: the lead,
                        // or fewer where the clamp moved it later (every prayer instant is a whole
                        // minute, so this is exact). One the clamp pushed onto or past its own
                        // prayer has nothing left to remind of, and is not planned.
                        val minutesLeft = (at - remindAt).inWholeMinutes.toInt()
                        if (remindAt > from && minutesLeft > 0) {
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
                                body = copy.body(prayer, NotificationKind.REMINDER, clock, minutesLeft),
                            )
                        }
                    }
                }
                previousPrayerInstant = at
            }
        }

        // Filled tier by tier (FILL_ORDER), the soonest first within each, then put back in time
        // order. Where everything fits — the usual case, since windowDaysFor sizes the window to
        // the capacity — this is exactly the plan sorted by time; it only differs when something
        // has to go, and then what goes is the least important and the furthest away.
        val byTime = out.sortedBy { it.instant }
        val kept = mutableListOf<ScheduledNotification>()
        for (tier in FILL_ORDER) {
            kept += byTime.filter { it.kind in tier }.take((capacity - kept.size).coerceAtLeast(0))
        }
        return kept.sortedBy { it.instant }
    }
}
