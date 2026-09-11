package world.taqwa.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.di.appContainer
import world.taqwa.app.widget.WidgetMirrorRefresher
import world.taqwa.app.widget.androidWidgetUpdateHook

/** Set once from `TaqwaApplication` — `shared` cannot see androidApp's generated `R` class. */
var notificationSmallIconResId: Int = android.R.drawable.ic_popup_reminder

/**
 * Fires exactly once per scheduled alarm. It never re-derives content: title, body and which
 * channel to post into all travel in the intent extras baked in at schedule time, so this class
 * has no locale, no settings lookup and nothing to get wrong at 3am.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { Prayer.valueOf(it) } ?: return
        val sound = intent.getStringExtra(EXTRA_SOUND)?.let { PrayerSound.valueOf(it) } ?: return
        // Absent on an alarm scheduled by a build older than the voice setting, and on one
        // naming a voice this build does not know: either way the original is what played
        // when that alarm was set, and the original is what its channel carries.
        val voice = intent.getStringExtra(EXTRA_VOICE)
            ?.let { name -> AdhanVoice.entries.firstOrNull { it.name == name } }
            ?: AdhanVoice.ORIGINAL
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return

        // The scheduler's own sequential request code, not id.hashCode(): two ids that folded
        // to the same 32-bit hash used to overwrite each other's notification.
        val notificationId = intent.getIntExtra(EXTRA_REQUEST_CODE, id.hashCode())

        val notification = NotificationCompat.Builder(context, NotificationChannels.channelId(prayer, sound, voice))
            .setSmallIcon(notificationSmallIconResId)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, notificationId))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)

        refreshWidgets(context)
    }

    /**
     * Tapping the notification opens the app. The launcher intent rather than `MainActivity`
     * by name: `shared` cannot see androidApp's classes, and the launcher intent is what the
     * home screen icon fires, so the result is the same as opening the app by hand.
     */
    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * The one instant in the day when the home-screen widget is guaranteed to be wrong.
     *
     * This alarm is already exact, and it fires precisely when "ASR IN 0:01" has to become
     * "MAGHRIB IN 3:12" — the moment the widget's rolling five-minute refresh would be most
     * visibly late. Riding along costs one extra redraw per prayer and needs no alarm of its own.
     *
     * `goAsync` rather than [world.taqwa.app.widget.refreshWidgets]: that helper is deliberately
     * fire-and-forget on a process-lifetime scope, which is right for a running app but not here —
     * a broadcast receiver's process may be reclaimed the moment `onReceive` returns, killing the
     * redraw halfway. The budget is well inside `goAsync`'s own ten-second allowance.
     *
     * Nothing here may throw: this is the notification path, and a widget that failed to redraw
     * must never cost the user their adhan.
     */
    private fun refreshWidgets(context: Context) {
        val hook = androidWidgetUpdateHook ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeout(WIDGET_REFRESH_BUDGET_MILLIS) {
                    // A prayer has just arrived, so the mirror's two-day horizon has moved on by
                    // one prayer: rewrite it from the stored location first, then redraw. This is
                    // what keeps the widget counting for days on end without the app being
                    // opened. Same process-wide container and DataStore as the app itself.
                    runCatching {
                        WidgetMirrorRefresher.refresh(appContainer.settingsRepository, appContainer.prayerTimesEngine)
                    }
                    runCatching { hook() }
                }
            } catch (_: TimeoutCancellationException) {
                // The half-hourly update and the next window alarm will both catch up.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val WIDGET_REFRESH_BUDGET_MILLIS = 8_000L
    }
}
