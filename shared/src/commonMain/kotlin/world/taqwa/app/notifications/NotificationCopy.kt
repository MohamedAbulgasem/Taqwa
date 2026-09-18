package world.taqwa.app.notifications

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.time.Instant

/**
 * Supplies notification text. [LocalizedNotificationCopy] is what the app actually schedules
 * with; [EnglishNotificationCopy] remains the planner's default so the pure planner tests need
 * no locale. The planner never knows which it has.
 */
interface NotificationCopy {
    fun title(prayer: Prayer, kind: NotificationKind): String
    fun body(prayer: Prayer, kind: NotificationKind, clockTime: String, minutesBefore: Int): String

    /**
     * The name Android shows for a notification channel. A channel's sound is immutable, so a
     * prayer accumulates one channel per sound the user has tried; naming them all after the
     * prayer alone leaves a list of identical entries the user cannot tell apart. The sound is
     * therefore part of the name, in the same language as the notification itself.
     */
    fun channelName(prayer: Prayer, sound: PrayerSound, kind: NotificationKind = NotificationKind.PRAYER): String
}

object EnglishNotificationCopy : NotificationCopy {

    private fun name(prayer: Prayer) = when (prayer) {
        Prayer.FAJR -> "Fajr"
        Prayer.SUNRISE -> "Sunrise"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
    }

    override fun title(prayer: Prayer, kind: NotificationKind): String =
        if (kind == NotificationKind.TAHAJJUD) "Tahajjud" else name(prayer)

    override fun body(
        prayer: Prayer,
        kind: NotificationKind,
        clockTime: String,
        minutesBefore: Int,
    ): String = when (kind) {
        NotificationKind.PRAYER -> "It is time for ${name(prayer)} · $clockTime"
        NotificationKind.REMINDER -> "${name(prayer)} in $minutesBefore minutes · $clockTime"
        NotificationKind.TAHAJJUD -> "The last third of the night has begun · ${name(prayer)} at $clockTime"
    }

    override fun channelName(prayer: Prayer, sound: PrayerSound, kind: NotificationKind): String {
        val soundName = when (sound) {
            PrayerSound.SILENT -> "Silent"
            PrayerSound.NOTIFICATION -> "Notification"
            PrayerSound.TAKBIR -> "Takbir"
            PrayerSound.ADHAN -> "Adhan"
        }
        return "${title(prayer, kind)} · $soundName"
    }
}

/**
 * A 24-hour HH:MM in the location's own zone, in Western digits. Only the planner's default —
 * the app passes a CLDR-formatted `PlatformFormat.clockTime` through `NotificationCoordinator`,
 * so a real notification carries the locale's own digit set.
 */
fun isoClockTime(instant: Instant, timeZoneId: String): String {
    val t = instant.toLocalDateTime(TimeZone.of(timeZoneId))
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
