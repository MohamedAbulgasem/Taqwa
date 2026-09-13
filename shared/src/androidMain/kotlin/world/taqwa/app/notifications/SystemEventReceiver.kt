package world.taqwa.app.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import world.taqwa.app.di.appContainer
import world.taqwa.app.widget.WidgetMirrorRefresher
import world.taqwa.app.widget.androidAyahWidgetUpdateHook
import world.taqwa.app.widget.androidWidgetUpdateHook

/**
 * `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED` all invalidate
 * whatever is currently scheduled: a reboot or an app update clears every `AlarmManager` entry
 * outright, and a clock or timezone change can silently leave the existing plan pointing at the
 * wrong wall-clock moments. A change to the "Alarms & reminders" grant does too, in its own way:
 * an alarm keeps the exactness it was armed with, so the plan has to be re-armed to gain (or
 * lose) the minute.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = rescheduleTriggerFor(intent.action) ?: return
        val pendingResult = goAsync()
        // SupervisorJob and a handler that swallows: this runs at boot, where an uncaught throw
        // reaching the thread's default handler is an invisible crash at the worst moment.
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }).launch {
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
                    //
                    // Wrapped like every sibling below it: a stored timezone the device's tzdb no
                    // longer knows, or a DataStore read that fails, must not become a crash during
                    // BOOT_COMPLETED — the next foreground reschedule will catch up.
                    runCatching { appContainer.notificationCoordinator.reschedule(trigger) }
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
                    // few things that redraw it between midnights. The hook also re-arms the
                    // widget's own midnight alarm (`TaqwaWidgets.updateAyah`), which is what makes
                    // TIMEZONE_CHANGED complete: the redraw alone would have left tomorrow's alarm
                    // pointing at the *old* zone's midnight.
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

/**
 * The action-to-reason mapping, pulled out so it can be tested without a `Context`.
 *
 * `MY_PACKAGE_REPLACED` reuses [RescheduleTrigger.BOOT_COMPLETED] rather than earning a value of
 * its own: nothing branches on the trigger except [world.taqwa.app.location.LocationRefresher],
 * which asks only whether the position may have moved, and an update is a reboot's twin on both
 * counts — every alarm gone, the device still exactly where it was. A new enum value would read
 * better and mean the same thing everywhere it was matched.
 *
 * `Intent.ACTION_TIME_CHANGED` really is `"android.intent.action.TIME_SET"`; the manifest's
 * filter and this `when` agree despite the two names.
 *
 * The exact-alarm grant changing is [RescheduleTrigger.SETTINGS_CHANGED]: it is a setting, just
 * one that lives in the system's screen rather than the app's.
 */
internal fun rescheduleTriggerFor(action: String?): RescheduleTrigger? = when (action) {
    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> RescheduleTrigger.BOOT_COMPLETED
    Intent.ACTION_TIME_CHANGED -> RescheduleTrigger.TIME_SET
    Intent.ACTION_TIMEZONE_CHANGED -> RescheduleTrigger.TIMEZONE_CHANGED
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> RescheduleTrigger.SETTINGS_CHANGED
    else -> null
}
