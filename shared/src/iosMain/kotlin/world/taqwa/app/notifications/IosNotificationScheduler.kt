package world.taqwa.app.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import world.taqwa.app.domain.PrayerSound

/**
 * `UNUserNotificationCenter`-backed [NotificationScheduler]. iOS has no partial-update API for
 * pending requests, so `scheduleAll` tears every request down and rebuilds from the new plan,
 * the same full-replace shape as the Android path. [NotificationPlanner] already enforces the
 * 64-request cap; the `take` here is a defensive re-clamp, not the real limiter.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationScheduler : NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun scheduleAll(plan: List<ScheduledNotification>) {
        center.removeAllPendingNotificationRequests()
        plan.take(NotificationPlanner.IOS_PENDING_LIMIT).forEach(::schedule)
    }

    override fun cancelAll() {
        center.removeAllPendingNotificationRequests()
    }

    private fun schedule(entry: ScheduledNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(entry.title)
            setBody(entry.body)
            setSound(soundFor(entry.sound))
        }

        val timeZone = NSTimeZone.timeZoneWithName(entry.timeZoneId) ?: NSTimeZone.localTimeZone
        val calendar = NSCalendar.currentCalendar.apply { this.timeZone = timeZone }
        val components = calendar.components(
            unitFlags = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
            fromDate = NSDate.dateWithTimeIntervalSince1970(entry.instant.epochSeconds.toDouble()),
        )

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = components, repeats = false,
        )

        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(entry.id, content, trigger),
            withCompletionHandler = null,
        )
    }

    /** Silent means no sound at all; every other level either uses the bundled `.caf` or, for
     * "Notification", the system default — mirroring the Android channel setup in Task 17. */
    private fun soundFor(sound: PrayerSound): UNNotificationSound? {
        val fileName = SoundAssets.iosResourceFileName(sound)
        return when {
            sound == PrayerSound.SILENT -> null
            fileName != null -> UNNotificationSound.soundNamed(fileName)
            else -> UNNotificationSound.defaultSound
        }
    }
}
