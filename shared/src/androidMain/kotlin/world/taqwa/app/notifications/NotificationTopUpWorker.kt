package world.taqwa.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import world.taqwa.app.di.appContainer
import world.taqwa.app.widget.WidgetMirrorRefresher
import world.taqwa.app.widget.androidWidgetUpdateHook
import java.util.concurrent.TimeUnit

/**
 * The safety net under the alarm plan: twice a day, whatever else has happened, rebuild it.
 *
 * Every other path that re-arms the prayer alarms needs an event — the app opening, a setting
 * changing, boot, an update, an alarm firing, "Alarms & reminders" being granted. Two things
 * leave none of those behind: the user *revoking* that permission (Android cancels every exact
 * alarm on the spot and, unlike a grant, tells the app nothing — verified on an Android 16
 * emulator, 56 alarms to none), and a plan that ran out while the phone sat unopened. Either way
 * the next prayer would pass in silence. WorkManager is the one scheduler that keeps running
 * through both, so this runs the same reschedule the receivers do, on a period long enough to
 * cost nothing and short enough that a lost plan is back before the next day's Fajr.
 *
 * It also keeps the home-screen widget's horizon moving for a reader with notifications off,
 * everyone who tapped "Don't allow" at onboarding among them. Their plan is empty, so no prayer
 * alarm ever fires, and the alarm and the boot/time-change receiver were the only writers of the
 * widget's mirror while the app stayed closed: once the mirror's two days ran out, the widget went
 * on naming a next prayer days old. So after the reschedule this rewrites the mirror and redraws
 * the widget, as the alarm does, and twelve hours keeps that well inside the mirror's two days.
 */
class NotificationTopUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = topUp(
        reschedule = { appContainer.notificationCoordinator.reschedule(RescheduleTrigger.BACKGROUND_REFRESH) },
        // From the stored location, through the same process-wide container and DataStore as the
        // app itself, exactly as the alarm and the boot receiver rewrite it.
        refreshMirror = { WidgetMirrorRefresher.refresh(appContainer.settingsRepository, appContainer.prayerTimesEngine) },
        // The hook itself, awaited, and not `refreshWidgets()`: that is fire-and-forget on a
        // process-lifetime scope, and a worker's process may be reclaimed as soon as doWork
        // returns, taking a redraw still in flight with it.
        redraw = { androidWidgetUpdateHook?.invoke() },
    )

    companion object {
        private const val UNIQUE_NAME = "taqwa-notification-top-up"

        /** Idempotent, called at every launch: KEEP leaves an existing chain's timing alone. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationTopUpWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/**
 * The worker's body, its three steps passed in so their order and their independence can be
 * tested without WorkManager or the app's container.
 *
 * The plan first, because its reschedule may store a new location (a journey noticed from the
 * phone's last known position, spec §16.5); then the mirror, from whatever location that left;
 * then the redraw, which reads the mirror. Each in its own `runCatching`, because none may cost
 * another its turn: a widget that failed to redraw must never cost the reschedule, nor a failed
 * reschedule the widget. And never a failure: a step that could not run is retried at the next
 * period by definition, and WorkManager's own retry would only add a backoff on top of that.
 */
internal suspend fun topUp(
    reschedule: suspend () -> Unit,
    refreshMirror: suspend () -> Unit,
    redraw: suspend () -> Unit,
): ListenableWorker.Result {
    runCatching { reschedule() }
    runCatching { refreshMirror() }
    runCatching { redraw() }
    return ListenableWorker.Result.success()
}
