package world.taqwa.app.notifications

import kotlinx.coroutines.runBlocking
import world.taqwa.app.di.appContainer
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.widget.WidgetMirrorRefresher

/**
 * The Kotlin side of `BGAppRefreshTask`. Swift owns task registration and the execution budget;
 * this only does the rescheduling work and reports whether it produced a usable result, so
 * `iOSApp.swift` can call `setTaskCompleted(success:)` honestly rather than always `true`.
 */
object BackgroundRefreshBridge {

    fun runBackgroundRefresh(): Boolean = runBlocking {
        // The app's own coordinator, not a bare one over the stored location: its
        // `locationFor` reads the position the phone last knew and moves the plan with it
        // (spec §16.5). Same process-wide DataStore either way.
        val settingsRepository = appContainer.settingsRepository
        val rescheduled = runCatching { appContainer.notificationCoordinator.reschedule(RescheduleTrigger.BACKGROUND_REFRESH) }
        // The widget's two-day schedule moves forward with the same stored location, so the
        // home screen keeps counting even on a day the app is never opened. Swift reloads the
        // timelines right after this returns.
        runCatching { WidgetMirrorRefresher.refresh(settingsRepository, PrayerTimesEngine()) }
        // Success is "the work ran", not "something got scheduled": a reader with notifications
        // off has an empty plan by choice, and reporting that as failure taught iOS to grant the
        // task less often — the task that also keeps the widget and the location current.
        rescheduled.isSuccess
    }
}
