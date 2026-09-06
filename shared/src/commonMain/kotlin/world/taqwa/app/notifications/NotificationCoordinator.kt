package world.taqwa.app.notifications

import kotlinx.coroutines.flow.first
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Ties settings, the prayer engine, the pure planner and a platform [NotificationScheduler]
 * together. Every platform entry point — app foreground, a boot receiver, a background task —
 * calls [reschedule] and never touches [NotificationPlanner] or the scheduler directly.
 */
class NotificationCoordinator(
    private val engine: PrayerTimesEngine,
    private val settingsRepository: SettingsRepository,
    private val locationOf: suspend () -> GeoLocation?,
    private val scheduler: NotificationScheduler,
    private val now: () -> Instant,
    private val capacity: Int = NotificationPlanner.IOS_PENDING_LIMIT,
) {
    companion object {
        /** Below this much runway left in the window, a background task is worth requesting. */
        val TOP_UP_HORIZON = 3.days
    }

    suspend fun reschedule(trigger: RescheduleTrigger): List<ScheduledNotification> {
        val location = locationOf()
        if (location == null) {
            // Nothing to schedule against, and nothing stale should be left behind either —
            // this is what happens when a user revokes location after granting it once.
            scheduler.cancelAll()
            return emptyList()
        }
        val prayerSettings = settingsRepository.prayerSettings.first()
        val notificationSettings = settingsRepository.notificationSettings.first()
        val windowDays = NotificationPlanner.windowDaysFor(capacity, notificationSettings)
        val plan = NotificationPlanner.plan(
            location = location,
            settings = prayerSettings,
            notifications = notificationSettings,
            engine = engine,
            from = now(),
            windowDays = windowDays,
            capacity = capacity,
        )
        // trigger is not branched on: every reason for waking up resolves to the same correct
        // plan for right now. It exists so callers and logs can say why a reschedule happened.
        scheduler.scheduleAll(plan)
        return plan
    }

    fun needsTopUp(plan: List<ScheduledNotification>): Boolean =
        RescheduleDecider.needsTopUp(plan, now(), TOP_UP_HORIZON)
}
