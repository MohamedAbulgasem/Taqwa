package world.taqwa.app.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * The signal that tells a *live* Glance session its mirror has changed.
 *
 * Glance's own `update()` / `updateAll()` is not that signal, which is what D1 (S23 round) was:
 * changing Settings ▸ Appearance ▸ Widget background stored the value and called `refreshWidgets()`,
 * and the widgets on the home screen kept their old background until a cold start or a system
 * dark-mode change. `GlanceAppWidget.update` is documented to "not restart `provideGlance` if it is
 * already running" — on a live session it sends `UpdateGlanceState`, which re-reads the widget's
 * *Glance state* (`stateDefinition`, which neither of these widgets has) and nothing else. Compose
 * then recomposes only what a changed snapshot state invalidated; the content lambda's parameters
 * are unchanged, so it is skipped, and the `SharedPreferences` read inside it never runs again.
 * Everything that did work — a cold start, a locale or dark-mode change, and any update landing
 * after the session had already timed out — worked by *starting a new session*, which is why the
 * fault looked intermittent rather than absolute.
 *
 * So the widgets read this counter inside their content lambda, which subscribes that lambda's own
 * recompose scope to it, and every redraw path bumps it (see `TaqwaWidgets.updateAll` /
 * `updateAyah`). A bump invalidates the lambda, the lambda re-reads the mirror, and the session
 * publishes new `RemoteViews` — warm, in the same process, with no session restart needed. The
 * `updateAll` call stays beside the bump: it is what covers the *dead*-session case, where there is
 * no composition to invalidate and a fresh `provideGlance` reads everything anyway.
 *
 * Process-wide and not persisted on purpose. It has no meaning across processes — a new process has
 * no live session to invalidate — and the widget process *is* the app process here, since Glance
 * runs its sessions in the app under WorkManager.
 */
object WidgetRedraw {

    /**
     * Bumped by every redraw path, read by every widget's content lambda. The value itself means
     * nothing; only that it changed.
     */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * Marks the mirrors as changed. A plain increment: two bumps racing to the same value would
     * cost at most one redundant redraw of the *same* new state, and each bump is followed by a
     * Glance `updateAll` regardless.
     */
    fun bump() {
        revision++
    }
}
