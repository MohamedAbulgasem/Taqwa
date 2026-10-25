package world.taqwa.app.notifications

import kotlin.time.Duration.Companion.minutes

/**
 * Which entries the Android scheduler asks an exact alarm for (Duha and Iqama spec §3.4), kept
 * out of the scheduler, which has no host test, so the rule is pinned by one. An exact alarm is
 * a cost the platform asks apps to keep for what has to be on the minute, and an inexact one can
 * arrive up to an hour late, so:
 *
 * - the prayers, their reminders, Tahajjud and the iqama are exact (where the platform allows):
 *   an iqama reminder an hour late would ring after the iqama;
 * - the adhkar reminder is always inexact: its window stays open until sunrise or Maghrib;
 * - Duha is inexact while at least [DUHA_INEXACT_WINDOW] of its window is left when it rings, and
 *   exact under that, which happens only in far-north winters, so where the platform allows exact
 *   alarms it is never dropped for lateness.
 */
object AlarmPrecision {

    /** The platform's bound on an inexact alarm, from Android 12: within an hour. */
    val DUHA_INEXACT_WINDOW = 60.minutes

    fun wantsExact(entry: ScheduledNotification): Boolean = when (entry.kind) {
        NotificationKind.PRAYER, NotificationKind.REMINDER, NotificationKind.TAHAJJUD, NotificationKind.IQAMA -> true
        NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING -> false
        NotificationKind.DUHA_BEST, NotificationKind.DUHA_START -> {
            val end = entry.windowEnd
            end == null || end - entry.instant < DUHA_INEXACT_WINDOW
        }
    }
}
