package world.taqwa.app.notifications

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import kotlin.time.Instant

/**
 * Supplies notification text. Task 26 replaces [EnglishNotificationCopy] with a localised
 * implementation; the planner never knows which it has.
 */
interface NotificationCopy {
    fun title(prayer: Prayer, kind: NotificationKind): String
    fun body(prayer: Prayer, kind: NotificationKind, clockTime: String, minutesBefore: Int): String
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

    override fun title(prayer: Prayer, kind: NotificationKind): String = name(prayer)

    override fun body(
        prayer: Prayer,
        kind: NotificationKind,
        clockTime: String,
        minutesBefore: Int,
    ): String = when (kind) {
        NotificationKind.PRAYER -> "It is time for ${name(prayer)} · $clockTime"
        NotificationKind.REMINDER -> "${name(prayer)} in $minutesBefore minutes · $clockTime"
    }
}

/** A 24-hour HH:MM in the location's own zone. Task 27 replaces this with CLDR formatting. */
fun isoClockTime(instant: Instant, timeZoneId: String): String {
    val t = instant.toLocalDateTime(TimeZone.of(timeZoneId))
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
