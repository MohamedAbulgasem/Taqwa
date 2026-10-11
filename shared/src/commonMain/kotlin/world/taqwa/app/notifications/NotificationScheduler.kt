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
     * Withdraws these adhkar reminders, by [ScheduledNotification.id], whether still pending or
     * already on screen, and leaves the rest of the plan armed. The case is a set of adhkar
     * finished before, or after, its reminder (adhkar spec §5). Both platforms find an adhkar
     * reminder on screen by its id whatever has been rescheduled since it fired: iOS keeps the
     * identifier, and Android posts it under its id as the tag. An id that is neither pending nor
     * shown is ignored.
     */
    fun cancel(ids: Set<String>)
}

expect fun createNotificationScheduler(): NotificationScheduler
