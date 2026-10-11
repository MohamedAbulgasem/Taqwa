package world.taqwa.app.notifications

import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.time.Instant

/**
 * [TAHAJJUD] is not about its [ScheduledNotification.prayer]'s own time: it carries
 * [world.taqwa.app.domain.Prayer.FAJR], the prayer its night ends at and the clock time its body
 * quotes, and is told apart everywhere by this kind (spec §17.6).
 *
 * [ADHKAR_MORNING] and [ADHKAR_EVENING] are the same kind of stand-in (adhkar spec §5): they
 * carry FAJR and ASR, the prayers they follow by half an hour, and their bodies quote the time
 * their window ends (sunrise, Maghrib) rather than that prayer's own.
 */
enum class NotificationKind { PRAYER, REMINDER, TAHAJJUD, ADHKAR_MORNING, ADHKAR_EVENING }

/** The set an adhkar reminder is for, which a tap on it opens; null for every other kind. */
val NotificationKind.adhkarSet: AdhkarSet?
    get() = when (this) {
        NotificationKind.ADHKAR_MORNING -> AdhkarSet.MORNING
        NotificationKind.ADHKAR_EVENING -> AdhkarSet.EVENING
        NotificationKind.PRAYER, NotificationKind.REMINDER, NotificationKind.TAHAJJUD -> null
    }

/** Either half of the adhkar reminder: one setting, one channel, one tier of the plan. */
val NotificationKind.isAdhkar: Boolean
    get() = adhkarSet != null

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
