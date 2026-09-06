package world.taqwa.app.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitTimeZone
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
import kotlin.time.Instant

/**
 * `UNUserNotificationCenter`-backed [NotificationScheduler]. iOS has no partial-update API for
 * pending requests, so `scheduleAll` tears every request down and rebuilds from the new plan,
 * the same full-replace shape as the Android path. [NotificationPlanner] already enforces the
 * 64-request cap; the `take` here is a defensive re-clamp, not the real limiter.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationScheduler : NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    /**
     * `removeAllPendingNotificationRequests()` is asynchronous, so tearing everything down and
     * adding the new plan on the very next line can race the teardown and silently lose entries.
     * This reads what is genuinely pending instead and removes only the requests the new plan
     * does not re-use, from inside the query's completion handler. Ids are stable across
     * identical plans — [NotificationPlanner] guarantees it — and `addNotificationRequest`
     * replaces a request with the same identifier, so the remove set and the add set are disjoint
     * by construction and the asynchronous removal cannot reach a request this call just added.
     */
    override fun scheduleAll(plan: List<ScheduledNotification>) {
        val entries = plan.take(NotificationPlanner.IOS_PENDING_LIMIT)
        val keep = entries.map { it.id }.toSet()
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            val stale = pending.orEmpty()
                .mapNotNull { (it as? UNNotificationRequest)?.identifier }
                .filterNot { it in keep }
            if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale)
            entries.forEach(::schedule)
        }
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

        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                entry.id, content, triggerFor(entry.instant, entry.timeZoneId),
            ),
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

    internal companion object {

        /**
         * The trigger for one entry, with its calendar and timezone **pinned onto the
         * components** rather than merely used to derive them.
         *
         * `NSDateComponents` produced by `components(unitFlags:fromDate:)` carry a `calendar` and
         * a `timeZone` only when [NSCalendarUnitCalendar] / [NSCalendarUnitTimeZone] are among
         * the requested units; bare components are re-read by
         * `UNCalendarNotificationTrigger` against `NSCalendar.current` in the *device's* zone.
         * Two silent failures follow from leaving them bare: a user who picked a city in another
         * zone gets every adhan off by the whole UTC offset, and a user whose iOS region calendar
         * is Islamic (Umm al-Qura) yields a year like 1448 that the trigger reads as Gregorian —
         * a date in the distant past, so the notification never fires at all. That is also why
         * the calendar here is built explicitly as Gregorian instead of taken from
         * `NSCalendar.currentCalendar`.
         */
        internal fun triggerFor(instant: Instant, timeZoneId: String): UNCalendarNotificationTrigger {
            val timeZone = NSTimeZone.timeZoneWithName(timeZoneId) ?: NSTimeZone.localTimeZone
            val calendar = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)!!
                .apply { this.timeZone = timeZone }
            val components = calendar.components(
                unitFlags = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                    NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
                    NSCalendarUnitCalendar or NSCalendarUnitTimeZone,
                fromDate = NSDate.dateWithTimeIntervalSince1970(instant.epochSeconds.toDouble()),
            ).apply {
                // Belt and braces: the two units above are documented to populate these, but the
                // trigger is silently wrong if either comes back nil, so set them outright too.
                setCalendar(calendar)
                setTimeZone(timeZone)
            }
            return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = components, repeats = false,
            )
        }
    }
}
