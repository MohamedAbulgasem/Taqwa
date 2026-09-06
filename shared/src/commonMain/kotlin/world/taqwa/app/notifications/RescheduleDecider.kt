package world.taqwa.app.notifications

import kotlin.time.Duration
import kotlin.time.Instant

/** Why the app woke up. Platform entry points name their reason; the coordinator debounces on it. */
enum class RescheduleTrigger {
    APP_FOREGROUND,
    BACKGROUND_REFRESH,
    SETTINGS_CHANGED,
    LOCATION_CHANGED,
    BOOT_COMPLETED,
    TIME_SET,
    TIMEZONE_CHANGED,
    ALARM_FIRED,
}

object RescheduleDecider {
    /**
     * True when the furthest scheduled notification is nearer than [minimumHorizon] — the signal
     * that iOS's rolling window has drained and a `BGAppRefreshTask` is worth requesting.
     */
    fun needsTopUp(
        plan: List<ScheduledNotification>,
        now: Instant,
        minimumHorizon: Duration,
    ): Boolean {
        val furthest = plan.maxOfOrNull { it.instant } ?: return true
        return furthest - now < minimumHorizon
    }
}
