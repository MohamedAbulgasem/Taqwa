package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.time.Instant

enum class NotificationKind { PRAYER, REMINDER }

/**
 * One notification, fully decided. Title and body are baked in at schedule time so that no
 * platform code — a broadcast receiver, a background task — ever has to resolve a string
 * resource or know what language the user reads.
 */
data class ScheduledNotification(
    val id: String,
    val prayer: Prayer,
    val kind: NotificationKind,
    val instant: Instant,
    val timeZoneId: String,
    val sound: PrayerSound,
    val title: String,
    val body: String,
)
