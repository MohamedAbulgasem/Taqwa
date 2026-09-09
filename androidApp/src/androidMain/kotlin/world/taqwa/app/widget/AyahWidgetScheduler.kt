package world.taqwa.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The ayah widget's own refresh alarm: one firing per day, just after local midnight.
 *
 * The prayer widgets tick every five minutes because their countdown is wrong the moment it is
 * drawn. This card changes exactly once a day — the rotation is a function of the local date
 * (spec §5) — so a five-minute chain would be fifty pointless redraws an hour of a megabyte-sized
 * bitmap each. Hence a separate, far cheaper cadence, and hence the ayah widget is deliberately
 * *not* part of [TaqwaWidgets.updateAll].
 *
 * The same restraint applies to the alarm's own flavour, for the reasons spelled out in
 * [WidgetRefreshScheduler]: an inexact `setWindow`, so it batches and defers under Doze and needs
 * no exact-alarm grant, and plain `RTC`, so it never wakes a sleeping phone — nobody is reading a
 * home screen at 00:00, and the redraw lands on the next wake anyway. `updatePeriodMillis` (6 h)
 * is the backstop underneath it if the alarm is ever lost.
 */
object AyahWidgetScheduler {

    const val ACTION_AYAH_WIDGET_REFRESH: String = "world.taqwa.app.AYAH_WIDGET_REFRESH"

    /** One past [WidgetRefreshScheduler]'s `0x7A9A`, so the daily alarm and the rolling one
     * cannot collide on a `PendingIntent`. */
    private const val REQUEST_CODE = 0x7A9B

    /** Spec §6's 00:00–00:05 delivery window. Generous on purpose: the ayah has already changed
     * by the time the alarm is armed for, so the only thing lateness costs is how soon the card
     * agrees — and a wide window is what lets the platform batch this with whatever else it is
     * already waking for. */
    private const val WINDOW_MILLIS: Long = 5L * 60L * 1000L

    /**
     * The first instant of tomorrow in [zone], as epoch millis.
     *
     * Local midnight, not "now plus 24 hours": the rotation turns over on the *date*, so the
     * redraw has to follow the wall clock through daylight-saving shifts and a flight across
     * timezones rather than drift a little further from midnight each day.
     *
     * Pure and zone-injected so it can be reasoned about without a device.
     */
    fun nextMidnight(zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.now(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Arms tomorrow's redraw. Idempotent — `FLAG_UPDATE_CURRENT` on a fixed request code replaces
     * the pending alarm rather than stacking another — so the receiver may call it from every
     * `onUpdate` without multiplying it.
     */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // Losing the alarm costs a day's freshness at worst, never correctness, so it must not
        // propagate out of a broadcast and take the process down with it.
        runCatching {
            alarmManager.setWindow(
                AlarmManager.RTC, nextMidnight(), WINDOW_MILLIS, pendingIntent(context),
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.cancel(pendingIntent(context)) }
    }

    /** Explicit and `IMMUTABLE`, as [WidgetRefreshScheduler]'s is and for the same reasons. */
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TaqwaAyahRefreshReceiver::class.java)
            .apply { action = ACTION_AYAH_WIDGET_REFRESH }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** True while at least one ayah widget is on a home screen; nothing else is worth an alarm. */
internal fun anyAyahWidgetPlaced(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    return manager.getAppWidgetIds(ComponentName(context, TaqwaAyahWidgetReceiver::class.java)).isNotEmpty()
}

/**
 * Midnight's delivery: redraw the card for the new date, then arm tomorrow's.
 *
 * The re-arm runs first and synchronously, as the prayer chain's does — if the redraw times out
 * or the process is reclaimed mid-coroutine, a broken chain would silently drop the widget back
 * to the six-hourly `updatePeriodMillis`.
 */
class TaqwaAyahRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AyahWidgetScheduler.ACTION_AYAH_WIDGET_REFRESH) return
        val appContext = context.applicationContext

        // A widget removed while the process was dead never reached onDisabled; let the chain end
        // here rather than tick against an empty launcher forever.
        if (!anyAyahWidgetPlaced(appContext)) return

        AyahWidgetScheduler.schedule(appContext)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeout(WORK_BUDGET_MILLIS) { runCatching { TaqwaWidgets.updateAyah(appContext) } }
            } catch (_: TimeoutCancellationException) {
                // Tomorrow is already armed, and `updatePeriodMillis` sits underneath: skipping
                // one delivery is the whole cost.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        /** Comfortably inside `goAsync`'s own allowance, with room for `finish()` to run. */
        const val WORK_BUDGET_MILLIS = 8_000L
    }
}
