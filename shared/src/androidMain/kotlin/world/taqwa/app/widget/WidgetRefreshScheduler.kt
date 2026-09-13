package world.taqwa.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The rolling, low-cost half of the Android widget's refresh cadence.
 *
 * Glance only re-renders when something asks it to. Until now the only askers were the app's own
 * mirror writes (which stop the moment the Today screen closes) and `updatePeriodMillis`, whose
 * floor is 30 minutes and which the platform is free to stretch further. Since I8 the countdown is
 * computed from the device clock *at draw time*, so between two draws the number on the home
 * screen is simply frozen — "ASR IN 1:32" stays on screen long after it stopped being true.
 *
 * This scheduler closes that gap with a repeating **inexact window** alarm on the 5-minute
 * boundaries. Inexact is the point, not a compromise:
 *
 *  - `setWindow` batches with whatever else the platform is already waking for and is deferred
 *    wholesale under Doze, so it costs nothing while the phone is idle in a pocket — and a widget
 *    nobody is looking at does not need to be right.
 *  - It needs no `SCHEDULE_EXACT_ALARM` grant. The app asks for that one — and only that one —
 *    and it is spent on the prayer alarms, whose whole justification is that the adhan must land
 *    on the minute. A cosmetic redraw must never compete for that budget, so this deliberately
 *    does not use `setExact*`.
 *  - `RTC`, not `RTC_WAKEUP`: this must never wake a sleeping device. If the screen is off there
 *    is no one to read the widget, and the alarm will fire on the next wake anyway.
 *
 * `AlarmManager` has no repeating-inexact primitive that survives Doze usefully (`setRepeating` is
 * silently downgraded and drifts), so the repeat is built by hand: every delivery re-arms the next
 * one from the receiver itself. The chain is started from the Glance receiver's `onEnabled` and
 * `onUpdate` and broken in `onDisabled`, so it exists only while a widget is on a home screen.
 */
object WidgetRefreshScheduler {

    /** Target staleness ceiling while the phone is awake. */
    const val INTERVAL_MILLIS: Long = 5L * 60L * 1000L

    /**
     * Latitude given to the platform to batch the delivery.
     *
     * A full interval's window is the obvious choice and is wrong. `setWindow` is a promise of
     * "any time in this range", and with nothing else to batch against the platform spends the
     * whole range: on a booted device, a `[15:05, 15:10]` alarm was still undelivered at 15:08,
     * `whenElapsed` 2m20s overdue with `maxWhenElapsed` 2m39s away. Since the receiver re-arms
     * from the boundary after *its own delivery time*, a delivery at 15:10 arms 15:15 — and if
     * that one is also delivered at its far edge, redraws land 15:10, 15:20, 15:30: ten minutes
     * apart, twice the staleness the grid was chosen for.
     *
     * A minute is latitude enough to batch with a neighbouring wakeup while bounding the visible
     * error. It stays fully inexact — `setWindow` never needs `SCHEDULE_EXACT_ALARM` whatever its
     * window, and Doze still defers it wholesale — and it clears the platform's own floor for
     * inexact windows, which for a trigger five minutes out is a tenth of that futurity, 30s.
     */
    private const val WINDOW_MILLIS: Long = 60L * 1000L

    const val ACTION_WIDGET_REFRESH: String = "world.taqwa.app.WIDGET_REFRESH"

    /** Distinct from every request code [world.taqwa.app.notifications.AndroidNotificationScheduler]
     * hands out (those start at 1 and count up), so the two alarm families cannot collide on a
     * `PendingIntent`. */
    private const val REQUEST_CODE = 0x7A9A

    /**
     * The next epoch-aligned [intervalMillis] boundary strictly after [nowMillis].
     *
     * Aligning to the epoch rather than to "now + interval" is what keeps the widget ticking on
     * :00/:05/:10 instead of drifting to whatever ragged offset the first schedule happened to land
     * on — and it means a reschedule provoked by an unrelated event (an unlock, a prayer boundary)
     * re-joins the same grid instead of starting a new one.
     *
     * Deliberately *strictly* after: called at exactly 12:05:00.000 it returns 12:10, never the
     * instant it was handed. Returning `now` would arm an alarm that is already due, and the
     * receiver re-arms from its own delivery time — so that would spin.
     *
     * Pure, and kept clear of every `AlarmManager` call, so it can be tested without a device.
     */
    fun nextBoundaryAfter(nowMillis: Long, intervalMillis: Long = INTERVAL_MILLIS): Long {
        require(intervalMillis > 0L) { "intervalMillis must be positive, was $intervalMillis" }
        // Not a bare `nowMillis % intervalMillis`: Kotlin's `%` keeps the sign of the dividend, so
        // for a pre-1970 clock (a device whose date has been dragged backwards) that is negative
        // and would push the boundary *backwards*, arming an alarm in the past on every tick.
        val sinceBoundary = ((nowMillis % intervalMillis) + intervalMillis) % intervalMillis
        return nowMillis + (intervalMillis - sinceBoundary)
    }

    /**
     * Arms the next window, delivered to [receiver].
     *
     * Idempotent: `FLAG_UPDATE_CURRENT` on a fixed request code means re-arming replaces the
     * pending alarm rather than stacking a second one, so calling this from `onUpdate` — which
     * fires once per widget instance — cannot multiply the alarm.
     */
    fun schedule(
        context: Context,
        receiver: Class<out BroadcastReceiver>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = nextBoundaryAfter(nowMillis)
        // Any failure here costs freshness, never correctness — the mirror write, the unlock
        // receiver and `updatePeriodMillis` all remain as backstops — so it must not propagate out
        // of a broadcast and take the process with it.
        runCatching {
            alarmManager.setWindow(
                AlarmManager.RTC, at, WINDOW_MILLIS, pendingIntent(context, receiver),
            )
        }
    }

    fun cancel(context: Context, receiver: Class<out BroadcastReceiver>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.cancel(pendingIntent(context, receiver)) }
    }

    /**
     * Explicit, and explicitly `IMMUTABLE`: an implicit broadcast `PendingIntent` handed to the
     * system alarm service would be a component another app could redirect, and API 31+ rejects a
     * `PendingIntent` that declares neither mutability flag outright.
     */
    private fun pendingIntent(context: Context, receiver: Class<out BroadcastReceiver>): PendingIntent {
        val intent = Intent(context, receiver).apply { action = ACTION_WIDGET_REFRESH }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
