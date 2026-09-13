package world.taqwa.app.notifications

import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.createPlatformFormat
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
    /**
     * Given the chance to re-acquire the device's position before the plan is built. Returns the
     * location that should be planned against, so a foreground reschedule after a flight plans
     * the destination's times rather than the origin's. Defaults to [locationOf] for callers
     * that have no location stack to hand.
     */
    private val locationFor: suspend (RescheduleTrigger) -> GeoLocation? = { locationOf() },
) {
    companion object {
        /** Below this much runway left in the window, a background task is worth requesting. */
        val TOP_UP_HORIZON = 3.days
    }

    suspend fun reschedule(trigger: RescheduleTrigger): List<ScheduledNotification> {
        val location = locationFor(trigger)
        if (location == null) {
            // Nothing to schedule against, and nothing stale should be left behind either —
            // this is what happens when a user revokes location after granting it once.
            scheduler.cancelAll()
            return emptyList()
        }
        val prayerSettings = settingsRepository.prayerSettings.first()
        val notificationSettings = settingsRepository.notificationSettings.first()
        val windowDays = NotificationPlanner.windowDaysFor(capacity, notificationSettings)
        // Resolved here, at schedule time, rather than injected: a reschedule can be triggered
        // from a boot receiver or a background task, where there is no composition to read a
        // locale from and the device's own default is the only truth available.
        val format = createPlatformFormat()
        val plan = NotificationPlanner.plan(
            location = location,
            settings = prayerSettings,
            notifications = notificationSettings,
            engine = engine,
            from = now(),
            windowDays = windowDays,
            capacity = capacity,
            copy = LocalizedNotificationCopy(format),
            formatClockTime = { instant, tz -> localizedClockTime(instant, tz, format) },
        )
        // trigger is not branched on: every reason for waking up resolves to the same correct
        // plan for right now. It exists so callers and logs can say why a reschedule happened.
        scheduler.scheduleAll(plan)
        return plan
    }

    fun needsTopUp(plan: List<ScheduledNotification>): Boolean =
        RescheduleDecider.needsTopUp(plan, now(), TOP_UP_HORIZON)

    /** As above, for a caller holding only how far the armed plan reaches. */
    fun needsTopUp(furthest: Instant?): Boolean =
        RescheduleDecider.needsTopUp(furthest, now(), TOP_UP_HORIZON)

    private fun localizedClockTime(instant: Instant, timeZoneId: String, format: PlatformFormat): String {
        val t = instant.toLocalDateTime(TimeZone.of(timeZoneId))
        return format.clockTime(t.hour, t.minute)
    }
}
