package world.taqwa.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import world.taqwa.app.di.appContainer
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
 */
class NotificationTopUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Never a failure: a reschedule that could not run is retried at the next period by
        // definition, and WorkManager's own retry would only add a backoff on top of that.
        runCatching { appContainer.notificationCoordinator.reschedule(RescheduleTrigger.BACKGROUND_REFRESH) }
        return Result.success()
    }

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
