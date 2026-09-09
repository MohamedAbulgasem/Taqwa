package world.taqwa.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import world.taqwa.app.di.appContainer
import world.taqwa.app.widget.WidgetMirrorRefresher
import world.taqwa.app.widget.androidAyahWidgetUpdateHook
import world.taqwa.app.widget.androidWidgetUpdateHook

/**
 * `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` all invalidate whatever is currently
 * scheduled: a reboot clears every `AlarmManager` entry outright, and a clock or timezone
 * change can silently leave the existing plan pointing at the wrong wall-clock moments.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> RescheduleTrigger.BOOT_COMPLETED
            Intent.ACTION_TIME_CHANGED -> RescheduleTrigger.TIME_SET
            Intent.ACTION_TIMEZONE_CHANGED -> RescheduleTrigger.TIMEZONE_CHANGED
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            // goAsync() buys roughly ten seconds before the system may kill the process, and a
            // reschedule that overruns it can be cut off mid-DataStore-write. Giving up a little
            // early leaves the store consistent; the next foreground reschedule will catch up.
            try {
                withTimeout(WORK_BUDGET_MILLIS) {
                    // Reuse the process-wide container rather than rebuilding a repository,
                    // refresher and coordinator here: DataStore permits one instance per file
                    // (createDataStore() already guarantees that), and one coordinator means one
                    // plan. appContext is set in TaqwaApplication.onCreate, which always runs
                    // before any receiver, so forcing the lazy container here is safe.
                    appContainer.notificationCoordinator.reschedule(trigger)
                    // The same events stale the widget: a reboot may land days after the mirror
                    // was written, and a clock or zone change moves every instant in it. Rewrite
                    // it from the stored location and redraw; neither may fail the reschedule.
                    runCatching {
                        WidgetMirrorRefresher.refresh(appContainer.settingsRepository, appContainer.prayerTimesEngine)
                    }
                    runCatching { androidWidgetUpdateHook?.invoke() }
                    // The ayah rotates on the local *date* (spec §5), so a clock or timezone
                    // change can move today's ayah outright; a reboot can land a day later. It is
                    // not on the prayer widgets' five-minute chain, so these events are among the
                    // few things that redraw it between midnights.
                    runCatching { androidAyahWidgetUpdateHook?.invoke() }
                }
            } catch (_: TimeoutCancellationException) {
                // Nothing useful to do from a broadcast receiver but stop cleanly.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        /** Comfortably inside goAsync()'s own allowance, with room for finish() to run. */
        const val WORK_BUDGET_MILLIS = 8_000L
    }
}
