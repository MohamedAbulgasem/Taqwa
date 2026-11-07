package world.taqwa.app.notifications

import android.content.Context
import world.taqwa.app.crash.RecordedAlarm
import kotlin.time.Instant

/**
 * The one moment the support email reports (adhan report, 7 Nov 2026): the last prayer alarm that
 * reached the app and posted, and whether Android had a channel for it (not its level, which is a
 * setting of the person's own and never leaves the phone). Beside the app's update time it says
 * whether the phone is letting the app run at all: a battery manager that stops apps leaves alarms
 * that never arrive, and a muted phone leaves them on time.
 *
 * One small preference file of its own, overwritten in place, so it never grows. The write comes
 * after the notification is posted and inside `runCatching`: a note that could not be written
 * must never cost an adhan.
 */
internal object AlarmLog {
    private const val PREFS = "taqwa_alarm_log"
    private const val ALARM_AT = "alarm_at"
    private const val ALARM_NO_CHANNEL = "alarm_no_channel"

    fun alarmPosted(context: Context, channelMissing: Boolean, at: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(ALARM_AT, at)
            .putBoolean(ALARM_NO_CHANNEL, channelMissing)
            .apply()
    }

    fun lastAlarm(context: Context): RecordedAlarm? {
        val p = prefs(context)
        val at = p.getLong(ALARM_AT, 0L).takeIf { it > 0L } ?: return null
        return RecordedAlarm(Instant.fromEpochMilliseconds(at), p.getBoolean(ALARM_NO_CHANNEL, false))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
