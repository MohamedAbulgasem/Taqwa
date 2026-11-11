package world.taqwa.app.notifications

import kotlinx.coroutines.runBlocking
import world.taqwa.app.di.appContainer
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.watchfeed.settleTheWatch
import world.taqwa.app.widget.WidgetMirrorRefresher
import world.taqwa.app.widget.refreshWidgets

/**
 * The Kotlin side of `BGAppRefreshTask`. Swift owns task registration and the execution budget;
 * this does the task's work, in [afterBackgroundRefresh]'s order: the reschedule, the widget's
 * mirror, the widget reload, then the watch's turn, last and bounded. It reports whether the
 * reschedule ran, so `iOSApp.swift` can call `setTaskCompleted(success:)` honestly rather than
 * always `true`.
 */
object BackgroundRefreshBridge {

    fun runBackgroundRefresh(): Boolean = runBlocking {
        // The app's own coordinator, not a bare one over the stored location: its
        // `locationFor` reads the position the phone last knew and moves the plan with it
        // (spec §16.5). Same process-wide DataStore either way.
        val settingsRepository = appContainer.settingsRepository
        afterBackgroundRefresh(
            reschedule = { appContainer.notificationCoordinator.reschedule(RescheduleTrigger.BACKGROUND_REFRESH) },
            // The widget's two-day schedule moves forward with the same stored location, so the
            // home screen keeps counting even on a day the app is never opened.
            refreshMirror = { WidgetMirrorRefresher.refresh(settingsRepository, PrayerTimesEngine()) },
            // Swift's hook (`installWidgetRefreshHook`, set at app init) reloads the timelines
            // here, before the watch is given its turn, so a slow watch ask can never cost this
            // refresh its reload. Swift's own `reloadWidgets()` after this returns stays as it
            // was, and its `lastMirrorKey` makes it a no-op once this one has reloaded.
            redrawWidgets = { refreshWidgets() },
            // The reschedule asked the watch (plan 2 Task 4), and iOS may suspend the app once
            // this returns: the ask gets its turn last, bounded, after every widget step.
            settleWatch = { settleTheWatch { appContainer.watchFeedSync } },
        )
    }
}

/**
 * The bridge's body, its steps passed in, as Android's `afterSystemEvent` and `topUp` take theirs,
 * so that their order and their independence can be tested without the app's container or Swift.
 *
 * Each step runs in its own `runCatching`, in this order, and none can cost another its turn. The
 * watch is settled last, after every widget step: the reschedule only asked it, so a watch link
 * that hangs costs the reminders and the widgets nothing (plan 2's Global Constraints).
 *
 * The answer is whether the reschedule ran. Success is "the work ran", not "something got
 * scheduled": a reader with notifications off has an empty plan by choice, and reporting that as
 * failure taught iOS to grant the task less often — the task that also keeps the widget and the
 * location current.
 */
internal suspend fun afterBackgroundRefresh(
    reschedule: suspend () -> Unit,
    refreshMirror: suspend () -> Unit,
    redrawWidgets: suspend () -> Unit,
    settleWatch: suspend () -> Unit,
): Boolean {
    val rescheduled = runCatching { reschedule() }
    runCatching { refreshMirror() }
    runCatching { redrawWidgets() }
    runCatching { settleWatch() }
    return rescheduled.isSuccess
}
