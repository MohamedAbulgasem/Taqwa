package world.taqwa.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Both providers share one rolling refresh alarm, so each has to hand the scheduler the same
 * answer to "is any Taqwa widget still on a home screen?" — `onDisabled` fires per provider, and
 * removing the small widget must not stop the medium one from ticking.
 *
 * `internal` rather than private because `TaqwaApplication` feeds the same answer to
 * `androidWidgetPlacementHook`: the Appearance screen's "is the prayer widget placed?" is the very
 * same query, and duplicating it would let the two drift.
 */
internal fun anyWidgetPlaced(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    return listOf(TaqwaSmallWidgetReceiver::class.java, TaqwaMediumWidgetReceiver::class.java)
        .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
}

/**
 * Starts the rolling refresh when a widget appears and stops it when the last one goes.
 *
 * `onUpdate` as well as `onEnabled`, because `onEnabled` only fires for the *first* instance of a
 * provider — and an alarm can be lost without the provider being disabled (a reboot clears every
 * `AlarmManager` entry; a force-stop cancels them all). `onUpdate` runs on every
 * `APPWIDGET_UPDATE`, including the half-hourly one and the one after boot, so it is the cheap
 * self-healing point. Re-arming is idempotent — see [WidgetRefreshScheduler.schedule].
 */
abstract class TaqwaGlanceReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context, TaqwaWidgetRefreshReceiver::class.java)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshScheduler.schedule(context, TaqwaWidgetRefreshReceiver::class.java)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!anyWidgetPlaced(context)) {
            WidgetRefreshScheduler.cancel(context, TaqwaWidgetRefreshReceiver::class.java)
        }
    }
}

class TaqwaSmallWidgetReceiver : TaqwaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaqwaSmallGlanceWidget()
}

class TaqwaMediumWidgetReceiver : TaqwaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaqwaMediumGlanceWidget()
}

/**
 * The ayah widget's provider, on its own daily alarm rather than the prayer widgets' rolling
 * five-minute one — see [AyahWidgetScheduler] for why the two cadences are separate.
 *
 * `onUpdate` as well as `onEnabled` for the same self-healing reason as [TaqwaGlanceReceiver]: an
 * alarm can be lost without the provider ever being disabled (a reboot clears every
 * `AlarmManager` entry, a force-stop cancels them all), and `onUpdate` is the cheap point at
 * which that is noticed — `updatePeriodMillis` guarantees it runs at least every six hours.
 */
class TaqwaAyahWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TaqwaAyahGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AyahWidgetScheduler.schedule(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        AyahWidgetScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!anyAyahWidgetPlaced(context)) AyahWidgetScheduler.cancel(context)
    }
}

/**
 * One link in the rolling five-minute chain: redraw, then arm the next window.
 *
 * The re-arm happens first and synchronously. If the redraw times out or the process is reclaimed
 * mid-coroutine, the chain must still be intact — a broken chain would silently return the widget
 * to the half-hourly cadence this whole mechanism exists to escape.
 *
 * This receiver never touches settings or DataStore. Everything it needs is in the widget's
 * SharedPreferences mirror, read by `provideGlance`, so there is no store for it to build a second
 * instance of.
 */
class TaqwaWidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetRefreshScheduler.ACTION_WIDGET_REFRESH) return
        val appContext = context.applicationContext

        // Nothing left to keep fresh — let the chain end rather than tick against an empty
        // launcher forever (a widget removed while the process was dead never reached onDisabled).
        if (!anyWidgetPlaced(appContext)) return

        WidgetRefreshScheduler.schedule(appContext, TaqwaWidgetRefreshReceiver::class.java)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeout(WORK_BUDGET_MILLIS) { runCatching { TaqwaWidgets.updateAll(appContext) } }
            } catch (_: TimeoutCancellationException) {
                // The next window is already armed; skipping one tick is the whole cost.
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
