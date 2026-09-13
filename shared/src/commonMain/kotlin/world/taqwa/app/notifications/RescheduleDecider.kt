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
    ): Boolean = needsTopUp(plan.maxOfOrNull { it.instant }, now, minimumHorizon)

    /**
     * The same question from a caller that never built the plan and knows only how far the armed
     * one reaches — Android's alarm receiver, which wakes in a process with nothing in memory.
     * A null [furthest] means nothing is scheduled, which always needs a top-up.
     */
    fun needsTopUp(furthest: Instant?, now: Instant, minimumHorizon: Duration): Boolean {
        if (furthest == null) return true
        return furthest - now < minimumHorizon
    }
}
