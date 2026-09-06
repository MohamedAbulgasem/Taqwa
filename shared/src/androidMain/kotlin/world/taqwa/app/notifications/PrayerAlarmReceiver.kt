package world.taqwa.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

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
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return

        val notification = NotificationCompat.Builder(context, NotificationChannels.channelId(prayer, sound))
            .setSmallIcon(notificationSmallIconResId)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // The scheduler's own sequential request code, not id.hashCode(): two ids that folded
        // to the same 32-bit hash used to overwrite each other's notification.
        val notificationId = intent.getIntExtra(EXTRA_REQUEST_CODE, id.hashCode())
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
