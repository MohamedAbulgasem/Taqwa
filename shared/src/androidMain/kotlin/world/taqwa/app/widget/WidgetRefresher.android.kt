package world.taqwa.app.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Set once from `TaqwaApplication.onCreate` (Step 9) — `shared` cannot reference the Glance
 * widget classes, which live in `androidApp` alongside the generated `R` class. */
var androidWidgetUpdateHook: (suspend () -> Unit)? = null

/**
 * Process-lifetime scope for widget nudges. A widget refresh has no owner to be cancelled with —
 * it must outlive whichever screen provoked it — and `SupervisorJob` keeps one failed update from
 * poisoning the scope for every later one.
 */
private val widgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Deliberately fire-and-forget. This used to be `runBlocking { hook() }`, which ran on whatever
 * dispatcher the caller was on — and the caller is `TodayViewModel.refresh()`, launched from
 * `App.kt`'s `LaunchedEffect` on the composition's **Main** dispatcher. So the UI thread blocked
 * on a SharedPreferences write plus two full Glance recompositions plus `AppWidgetManager` IPC,
 * on the app's primary screen. Nothing awaits the result of a widget update, so there is no
 * reason for the caller to wait for it.
 *
 * The `runCatching` matters: an exception escaping a bare `launch` on a scope with no handler
 * reaches the thread's default handler and takes the process down. A widget that failed to redraw
 * must never do that — it will be redrawn on the next tick anyway.
 */
actual fun refreshWidgets() {
    val hook = androidWidgetUpdateHook ?: return
    widgetRefreshScope.launch { runCatching { hook() } }
}
