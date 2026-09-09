package world.taqwa.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The single place that knows how to redraw Taqwa's home-screen widgets, and the process-lifetime
 * hooks that decide when to.
 *
 * Everything the widgets render comes from the SharedPreferences mirror that `WidgetMirrorWriter`
 * publishes; nothing here reads settings, so nothing here needs — or may build — a `DataStore`.
 * The app's one store lives behind `appContainer` and stays there.
 */
object TaqwaWidgets {

    /**
     * Redraws both prayer providers. Cheap when a provider has no instances: Glance's `updateAll`
     * resolves to an empty id list and does nothing.
     *
     * The [WidgetRedraw.bump] first, and never only the `updateAll`: on a session that is still
     * live, `updateAll` alone does not re-run the content lambda, so the widget keeps drawing the
     * mirror as it stood when the session started (D1 — see [WidgetRedraw]).
     */
    suspend fun updateAll(context: Context) {
        WidgetRedraw.bump()
        TaqwaSmallGlanceWidget().updateAll(context)
        TaqwaMediumGlanceWidget().updateAll(context)
    }

    /**
     * Redraws the ayah widget alone.
     *
     * Kept out of [updateAll] on purpose. That runs every five minutes for the prayer countdown;
     * this card changes once a day and each of its draws builds a full-cell bitmap, so putting
     * the two on one cadence would spend a megabyte of allocation and a `StaticLayout` pass every
     * five minutes to redraw a picture that cannot have changed. Its own callers are the daily
     * midnight alarm, a pool-mirror rewrite from the app, and the clock/timezone broadcasts.
     *
     * Every redraw also re-arms tomorrow's midnight alarm, because the two can go out of step in
     * ways no other caller notices. `TIMEZONE_CHANGED` is the case that made this necessary:
     * `SystemEventReceiver` redrew the card for the new zone but left the alarm pointing at the
     * *old* zone's midnight, so the card would then turn over at the wrong hour until the next
     * `onUpdate`, up to six hours later. Re-arming here rather than at each call site is what
     * makes that impossible to forget again: [AyahWidgetScheduler.schedule] is idempotent
     * (`FLAG_UPDATE_CURRENT` on a fixed request code replaces the pending alarm rather than
     * stacking another) and reads the zone fresh each time, so calling it on every redraw path
     * costs one `AlarmManager` call and can only ever make the alarm more correct. Guarded on a
     * widget actually being placed, so a redraw after the last one was removed does not resurrect
     * the alarm `onDisabled` just cancelled.
     */
    suspend fun updateAyah(context: Context) {
        WidgetRedraw.bump()
        TaqwaAyahGlanceWidget().updateAll(context)
        if (anyAyahWidgetPlaced(context)) AyahWidgetScheduler.schedule(context)
    }

    /**
     * Asks the launcher to place the two-column widget, from onboarding's "Add widget" button.
     * The launcher shows its own confirmation sheet; false means it declined to ask at all (no
     * pin support, or a work profile that forbids it), and the caller treats that as "done".
     */
    fun requestPin(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, TaqwaMediumWidgetReceiver::class.java)
        return manager.requestPinAppWidget(provider, null, null)
    }

    /**
     * Refresh the widgets the instant the user unlocks the phone.
     *
     * This is the highest-value tick in the whole cadence and the cheapest: unlocking is the exact
     * moment someone looks at their home screen, and it costs one redraw per unlock. It closes the
     * gap the rolling `RTC` alarm deliberately leaves — that alarm does not wake a sleeping device,
     * so after a long screen-off stretch the first thing the user would otherwise see is the
     * countdown as it stood when the screen went dark.
     *
     * `ACTION_USER_PRESENT` cannot be declared in the manifest — it is one of the broadcasts
     * excluded from manifest registration — so it has to be a runtime receiver held for the life of
     * the process, which is why this is called from `TaqwaApplication.onCreate`.
     */
    fun registerUnlockRefresh(application: Application) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                // No goAsync/timeout dance: this receiver belongs to a live process that is not
                // being kept alive on its account, and the work is one Glance update.
                scope.launch { runCatching { updateAll(application) } }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        // ACTION_USER_PRESENT is a protected system broadcast, so NOT_EXPORTED is both correct and
        // sufficient — and from API 34 a runtime receiver must state one flag or the other.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(receiver, filter)
        }
    }

    /** Outlives any screen, and a `SupervisorJob` keeps one failed redraw from poisoning the rest. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
