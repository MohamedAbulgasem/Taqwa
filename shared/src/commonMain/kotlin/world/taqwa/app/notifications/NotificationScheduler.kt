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

    /**
     * Withdraws these entries, by [ScheduledNotification.id], whether still pending or already on
     * screen, and leaves the rest of the plan armed. For a reminder whose point has passed early:
     * a set of adhkar finished before its notification is due (adhkar spec §5). An id that is not
     * armed is ignored.
     */
    fun cancel(ids: Set<String>)
}

expect fun createNotificationScheduler(): NotificationScheduler
