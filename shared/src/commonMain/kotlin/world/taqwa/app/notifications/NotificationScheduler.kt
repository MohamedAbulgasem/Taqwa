package world.taqwa.app.notifications

/**
 * The only door from pure planning into a real OS. `scheduleAll` fully replaces whatever is
 * currently pending — the coordinator never diffs an old plan against a new one, and neither
 * does the scheduler; a full replace is simpler than a diff and iOS has no diff primitive
 * anyway.
 */
interface NotificationScheduler {
    fun scheduleAll(plan: List<ScheduledNotification>)
    fun cancelAll()
}

expect fun createNotificationScheduler(): NotificationScheduler
