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

    /** Redraws both providers. Cheap when a provider has no instances: Glance's `updateAll`
     * resolves to an empty id list and does nothing. */
    suspend fun updateAll(context: Context) {
        TaqwaSmallGlanceWidget().updateAll(context)
        TaqwaMediumGlanceWidget().updateAll(context)
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
