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
import androidx.core.app.NotificationManagerCompat
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.createPlatformFormat
import kotlin.time.Instant

private const val PREFS_NAME = "taqwa_scheduled_alarms"
private const val KEY_IDS = "ids"

/** Next unused request code, and the per-id assignments, persisted beside [KEY_IDS]. */
private const val KEY_NEXT_CODE = "next_request_code"
private const val KEY_CODE_PREFIX = "request_code_"
private const val ALARM_ACTION = "world.taqwa.app.PRAYER_ALARM"

/** How far the plan last handed to [AndroidNotificationScheduler.scheduleAll] reaches; see
 * [scheduledPlanHorizon]. */
private const val KEY_HORIZON = "plan_horizon_millis"

/** Wait, buzz, pause, buzz — two short pulses, the length of a knock rather than an alarm. */
private val NOTIFICATION_VIBRATION = longArrayOf(0L, 220L, 160L, 220L)

const val EXTRA_ID = "id"
const val EXTRA_REQUEST_CODE = "request_code"
const val EXTRA_PRAYER = "prayer"
const val EXTRA_SOUND = "sound"
const val EXTRA_VOICE = "voice"
const val EXTRA_KIND = "kind"
const val EXTRA_TITLE = "title"
const val EXTRA_BODY = "body"

/** [ScheduledNotification.windowEnd] as epoch millis; absent on every entry without one. */
const val EXTRA_WINDOW_END = "window_end"

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
        prefs.edit()
            .putStringSet(KEY_IDS, ids)
            // Written for PrayerAlarmReceiver's top-up check, which wakes in a process that has
            // never built a plan and so has nothing in memory to ask.
            .putLong(KEY_HORIZON, plan.maxOfOrNull { it.instant.toEpochMilliseconds() } ?: 0L)
            .apply()
        forgetRequestCodesOutside(ids)
    }

    override fun cancelAll() {
        val previousIds = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        previousIds.forEach { id -> alarmManager.cancel(pendingIntentFor(id)) }
        prefs.edit().remove(KEY_IDS).remove(KEY_HORIZON).apply()
    }

    /**
     * The alarm, if it is still pending, and the notification, if it is already on screen, for
     * each id.
     *
     * The alarm is found by the request code the id was armed under. An id without one has no
     * alarm here: it was never armed, or it fired and a reschedule since then has forgotten its
     * code ([forgetRequestCodesOutside], on every [scheduleAll], so on every cold start). No code
     * is handed out for it. A cancelled id stays in [KEY_IDS]; cancelling an alarm that is no
     * longer set, on the next full replace, does nothing.
     *
     * The notification is found by its tag, not by that code: [PrayerAlarmReceiver] posts an
     * adhkar reminder under its own id ([AdhkarReminderDelivery.tagFor]). A reminder that fired
     * before the app was last opened is still found and withdrawn when its set is finished. The
     * other kinds post untagged and are found only while their code is remembered, which is why
     * this is only ever called for the adhkar reminder.
     */
    override fun cancel(ids: Set<String>) {
        if (ids.isEmpty()) return
        val shown = NotificationManagerCompat.from(context)
        ids.forEach { id ->
            val code = prefs.getInt(KEY_CODE_PREFIX + id, -1)
            if (code < 0) return@forEach
            alarmManager.cancel(pendingIntentFor(id))
            shown.cancel(code)
        }
        // minSdk 26, so activeNotifications (API 23) is always there. Guarded all the same: a
        // notification that could not be withdrawn is not a reason to fail the caller, which is
        // a set of adhkar being marked done.
        runCatching {
            notificationManager.activeNotifications
                .filter { it.tag != null && it.tag in ids }
                .forEach { notificationManager.cancel(it.tag, it.id) }
        }
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
     * Exact where the platform allows it, inexact-but-Doze-proof where it does not.
     *
     * `SCHEDULE_EXACT_ALARM` is user-revocable from API 31, and the app does not ask for
     * `USE_EXACT_ALARM` (Play restricts it to alarm-clock and calendar apps), so a user who has
     * not granted "Alarms & reminders" would otherwise take a `SecurityException` straight out of
     * `NotificationCoordinator.reschedule` on every cold start. An inexact adhan, up to an hour
     * late as the app's own note says, is far better than an unrecoverable crash loop, and
     * `runCatching` covers the remaining race
     * where the permission is revoked between the check and the call.
     *
     * The fallback is `setAndAllowWhileIdle`, not `setWindow`: both are inexact and neither fires
     * early, but a plain window alarm is held until the next Doze maintenance window, which on an
     * idle phone overnight is hours rather than minutes — and Fajr is the prayer people most rely
     * on being told about. `setAndAllowWhileIdle` is the one inexact form the platform still
     * delivers in Doze.
     *
     * The adhkar reminder is always inexact (adhkar spec §5), granted or not: it is a nudge into a
     * window that stays open until sunrise or Maghrib, not the start of a prayer, and an exact
     * alarm is a cost the platform asks apps to keep for what has to be on the minute. The price
     * is the platform's own bound on an inexact alarm, which from Android 12 is "within an hour"
     * of the time asked for. One that arrives after its window has closed is dropped by the
     * receiver ([AdhkarReminderDelivery]), so lateness can cost a reminder but never post a wrong one.
     */
    private fun schedule(entry: ScheduledNotification) {
        val at = entry.instant.toEpochMilliseconds()
        val pendingIntent = pendingIntentFor(entry.id, entry)
        runCatching {
            if (!entry.kind.isAdhkar && canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
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
                putExtra(EXTRA_VOICE, entry.voice.name)
                putExtra(EXTRA_KIND, entry.kind.name)
                putExtra(EXTRA_TITLE, entry.title)
                putExtra(EXTRA_BODY, entry.body)
                // Only the adhkar reminder has a window that closes (sunrise, Maghrib).
                entry.windowEnd?.let { putExtra(EXTRA_WINDOW_END, it.toEpochMilliseconds()) }
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
        val live = plan.map(::channelKeyOf).toSet()
        live.forEach { (prayer, sound, voice, kind) ->
            val id = NotificationChannels.channelId(prayer, sound, voice, kind)
            val existing = notificationManager.getNotificationChannel(id)
            // The channel name says which sound it carries, not which voice: a user switching
            // voices is not meant to accumulate a list of channels they have to read carefully,
            // and the old voice's channel is deleted by the sweep below the moment it goes idle.
            val channel =
                NotificationChannel(id, copy.channelName(prayer, sound, kind), NotificationManager.IMPORTANCE_HIGH)
            if (existing == null) configureSound(channel, sound, voice)
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
    private fun deleteStaleChannels(live: Set<ChannelKey>) {
        val liveIds = live.map { (prayer, sound, voice, kind) ->
            NotificationChannels.channelId(prayer, sound, voice, kind)
        }.toSet()
        // Tahajjud's and the adhkar reminder's channels are swept on every plan, in it or not: a
        // prayer missing from a plan is a rarity to leave alone, but either of these missing means
        // it is switched off, and someone who never asked for it, or has stopped, should not find
        // its channel in system settings.
        val candidates = live.filter { it.kind == NotificationKind.PRAYER }
            .flatMap { NotificationChannels.allChannelIdsFor(it.prayer) }
            .toSet() + NotificationChannels.allTahajjudChannelIds() + NotificationChannels.allAdhkarChannelIds()
        candidates.filterNot { it in liveIds }
            .forEach { runCatching { notificationManager.deleteNotificationChannel(it) } }
    }

    /** A channel's identity: one [ChannelKey] per channel id, so each is created once a plan. */
    private data class ChannelKey(
        val prayer: Prayer,
        val sound: PrayerSound,
        val voice: AdhanVoice,
        val kind: NotificationKind,
    )

    /**
     * A reminder shares its prayer's channels, so it folds into PRAYER. Both adhkar kinds share
     * one channel whatever prayer they follow, so they fold into one key too — two keys for one
     * id would create the channel twice a plan.
     */
    private fun channelKeyOf(entry: ScheduledNotification): ChannelKey = when (entry.kind) {
        NotificationKind.TAHAJJUD -> ChannelKey(entry.prayer, entry.sound, entry.voice, NotificationKind.TAHAJJUD)
        NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING ->
            ChannelKey(Prayer.FAJR, entry.sound, entry.voice, NotificationKind.ADHKAR_MORNING)
        NotificationKind.PRAYER, NotificationKind.REMINDER ->
            ChannelKey(entry.prayer, entry.sound, entry.voice, NotificationKind.PRAYER)
    }

    private fun configureSound(channel: NotificationChannel, sound: PrayerSound, voice: AdhanVoice) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        when (sound) {
            PrayerSound.SILENT -> channel.setSound(null, null)
            PrayerSound.NOTIFICATION, PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val name = SoundAssets.androidRawResourceName(sound, voice)!!
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

/**
 * How far the plan currently armed on this device reaches, or null when nothing is scheduled.
 *
 * Read back out of the same preference file [AndroidNotificationScheduler] writes rather than
 * from the coordinator: the one caller ([PrayerAlarmReceiver]) usually wakes a process that has
 * never built a plan, so an in-memory answer would always be "nothing scheduled" and every alarm
 * would rewrite every entry.
 */
internal fun scheduledPlanHorizon(context: Context): Instant? {
    val millis = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(KEY_HORIZON, 0L)
    return if (millis > 0L) Instant.fromEpochMilliseconds(millis) else null
}
