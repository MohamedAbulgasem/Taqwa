package world.taqwa.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.createPlatformFormat

private const val PREFS_NAME = "taqwa_scheduled_alarms"
private const val KEY_IDS = "ids"
private const val ALARM_ACTION = "world.taqwa.app.PRAYER_ALARM"

/** Slop allowed when exact alarms are unavailable. The window starts at the prayer time, so a
 * notification is never early — only up to this much late. */
private const val INEXACT_WINDOW_MILLIS = 5L * 60L * 1000L

const val EXTRA_ID = "id"
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
        prefs.edit().putStringSet(KEY_IDS, plan.map { it.id }.toSet()).apply()
    }

    override fun cancelAll() {
        val previousIds = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        previousIds.forEach { id -> alarmManager.cancel(pendingIntentFor(id)) }
        prefs.edit().remove(KEY_IDS).apply()
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
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ALARM_ACTION
            putExtra(EXTRA_ID, id)
            if (entry != null) {
                putExtra(EXTRA_PRAYER, entry.prayer.name)
                putExtra(EXTRA_SOUND, entry.sound.name)
                putExtra(EXTRA_TITLE, entry.title)
                putExtra(EXTRA_BODY, entry.body)
            }
        }
        return PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
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
            PrayerSound.NOTIFICATION ->
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
            PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val name = SoundAssets.androidRawResourceName(sound)!!
                channel.setSound(Uri.parse("android.resource://${context.packageName}/raw/$name"), attrs)
            }
        }
    }
}
