package world.taqwa.app.notifications

import world.taqwa.app.domain.AdhanVoice
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
    /** Which recording [sound] plays, for the two levels that are a recording at all. It is
     * stamped here rather than read at fire time so the receiver stays free of settings. */
    val voice: AdhanVoice = AdhanVoice.ORIGINAL,
    val title: String,
    val body: String,
)
