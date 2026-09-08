package world.taqwa.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.net.Uri
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.createPlatformFormat

private const val PREFS_NAME = "taqwa_scheduled_alarms"
private const val KEY_IDS = "ids"

/** Next unused request code, and the per-id assignments, persisted beside [KEY_IDS]. */
private const val KEY_NEXT_CODE = "next_request_code"
private const val KEY_CODE_PREFIX = "request_code_"
private const val ALARM_ACTION = "world.taqwa.app.PRAYER_ALARM"

/** Slop allowed when exact alarms are unavailable. The window starts at the prayer time, so a
 * notification is never early — only up to this much late. */
private const val INEXACT_WINDOW_MILLIS = 5L * 60L * 1000L

/** Wait, buzz, pause, buzz — two short pulses, the length of a knock rather than an alarm. */
private val NOTIFICATION_VIBRATION = longArrayOf(0L, 220L, 160L, 220L)

const val EXTRA_ID = "id"
const val EXTRA_REQUEST_CODE = "request_code"
const val EXTRA_PRAYER = "prayer"
const val EXTRA_SOUND = "sound"
const val EXTRA_TITLE = "title"
const val EXTRA_BODY = "body"

/**
 * `AlarmManager`-backed [NotificationScheduler]. `scheduleAll` is a full replace: everything
 * previously scheduled by this class is cancelled first, using the request-code set persisted
 * from the last call — `AlarmManager` has no "list what I've scheduled" API — and the new
 * plan's own id set is persisted in turn for next time.
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun scheduleAll(plan: List<ScheduledNotification>) {
        cancelAll()
        ensureChannels(plan)
        plan.forEach(::schedule)
        val ids = plan.map { it.id }.toSet()
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
        forgetRequestCodesOutside(ids)
    }

    override fun cancelAll() {
        val previousIds = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        previousIds.forEach { id -> alarmManager.cancel(pendingIntentFor(id)) }
        prefs.edit().remove(KEY_IDS).apply()
    }

    /**
     * A stable, collision-free request code per notification id.
     *
     * `id.hashCode()` was neither: `String.hashCode` is a 32-bit fold, so two ids can collide,
     * and a collision means both a shared `AlarmManager` slot — one alarm silently replacing the
     * other — and, since `PrayerAlarmReceiver` used the same number as the notification id, one
     * notification overwriting the other when they did fire. Sequential codes cannot collide, and
     * persisting the assignment is what keeps `cancelAll` able to rebuild the exact
     * `PendingIntent` a previous process scheduled.
     */
    private fun requestCodeFor(id: String): Int {
        val key = KEY_CODE_PREFIX + id
        val existing = prefs.getInt(key, -1)
        if (existing >= 0) return existing
        val next = prefs.getInt(KEY_NEXT_CODE, 1)
        prefs.edit().putInt(key, next).putInt(KEY_NEXT_CODE, next + 1).apply()
        return next
    }

    /** Ids churn daily, so their codes would otherwise accumulate in the preference file forever. */
    private fun forgetRequestCodesOutside(ids: Set<String>) {
        val stale = prefs.all.keys
            .filter { it.startsWith(KEY_CODE_PREFIX) && it.removePrefix(KEY_CODE_PREFIX) !in ids }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    /**
     * Exact where the platform allows it, an inexact window where it does not.
     *
     * `SCHEDULE_EXACT_ALARM` is user-revocable from API 31, and `USE_EXACT_ALARM` — which is
     * auto-granted but Play-policy-restricted — only exists from API 33, so on API 31–32 a user
     * who turns off "Alarms & reminders" would otherwise take a `SecurityException` straight out
     * of `NotificationCoordinator.reschedule` on every cold start. A few minutes' slop on the
     * adhan is far better than an unrecoverable crash loop, and `runCatching` covers the
     * remaining race where the permission is revoked between the check and the call.
     */
    private fun schedule(entry: ScheduledNotification) {
        val at = entry.instant.toEpochMilliseconds()
        val pendingIntent = pendingIntentFor(entry.id, entry)
        runCatching {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MILLIS, pendingIntent,
                )
            }
        }
    }

    private fun pendingIntentFor(id: String, entry: ScheduledNotification? = null): PendingIntent {
        val requestCode = requestCodeFor(id)
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ALARM_ACTION
            putExtra(EXTRA_ID, id)
            // The receiver posts under this number too, so two notifications can never overwrite
            // each other for the same reason two alarms can never share a slot.
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            if (entry != null) {
                putExtra(EXTRA_PRAYER, entry.prayer.name)
                putExtra(EXTRA_SOUND, entry.sound.name)
                putExtra(EXTRA_TITLE, entry.title)
                putExtra(EXTRA_BODY, entry.body)
            }
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * One channel per (prayer, sound) actually used by this plan, and nothing else.
     *
     * A channel's sound is immutable, so switching a prayer from Adhan to Takbir to Notification
     * leaves three channels behind; without the sweep below the user sees a list of entries they
     * cannot remove and — while every channel was named `"Prayer: Fajr"` in hardcoded English —
     * could not even tell apart. Names come from the same [LocalizedNotificationCopy] that bakes
     * the notification text, so the channel reads in the user's own language and says which sound
     * it carries. An existing channel is re-created deliberately: Android updates the name and
     * leaves the immutable sound alone, which is what relabels channels after a locale change.
     */
    private fun ensureChannels(plan: List<ScheduledNotification>) {
        val copy = LocalizedNotificationCopy(createPlatformFormat())
        val live = plan.map { it.prayer to it.sound }.toSet()
        live.forEach { (prayer, sound) ->
            val id = NotificationChannels.channelId(prayer, sound)
            val existing = notificationManager.getNotificationChannel(id)
            val channel =
                NotificationChannel(id, copy.channelName(prayer, sound), NotificationManager.IMPORTANCE_HIGH)
            if (existing == null) configureSound(channel, sound)
            notificationManager.createNotificationChannel(channel)
        }
        deleteStaleChannels(live)
    }

    /**
     * Every channel this app could ever have created for a prayer that the current plan touches,
     * minus the ones the plan actually uses. Prayers absent from the plan are left alone: their
     * channels are the record of a sound the user may switch back to, and `scheduleAll` is called
     * often enough that deleting on absence would churn.
     */
    private fun deleteStaleChannels(live: Set<Pair<Prayer, PrayerSound>>) {
        val liveIds = live.map { (prayer, sound) -> NotificationChannels.channelId(prayer, sound) }.toSet()
        live.map { it.first }.toSet().forEach { prayer ->
            NotificationChannels.allChannelIdsFor(prayer)
                .filterNot { it in liveIds }
                .forEach { runCatching { notificationManager.deleteNotificationChannel(it) } }
        }
    }

    private fun configureSound(channel: NotificationChannel, sound: PrayerSound) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        when (sound) {
            PrayerSound.SILENT -> channel.setSound(null, null)
            PrayerSound.NOTIFICATION, PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val name = SoundAssets.androidRawResourceName(sound)!!
                channel.setSound(Uri.parse("android.resource://${context.packageName}/raw/$name"), attrs)
            }
        }
        // Notification is the level people pick for the prayers they keep during a working day —
        // Dhuhr and Asr at a desk — where a chime alone gets lost under a room. A buzz beside the
        // tone is the whole point of that level, so it is not left to the channel default. Takbir
        // and Adhan are long and loud enough to stand on their own, and a phone buzzing through
        // thirty seconds of adhan would be worse than silence.
        if (sound == PrayerSound.NOTIFICATION) {
            channel.enableVibration(true)
            channel.vibrationPattern = NOTIFICATION_VIBRATION
        }
    }
}
