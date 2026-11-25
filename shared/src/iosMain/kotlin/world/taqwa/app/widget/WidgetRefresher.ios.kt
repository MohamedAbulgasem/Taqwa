package world.taqwa.app.widget

/**
 * WidgetKit's `WidgetCenter` is Swift-only and not reachable from Kotlin/Native interop, so the
 * real `reloadAllTimelines()` call cannot live in Kotlin. Instead the Swift app installs a hook
 * here at launch (`iOSApp.swift`) and Kotlin invokes it whenever the mirror has just been
 * rewritten — a real reload, routed through the one language that can make it.
 *
 * `PrayerViewModel.refresh()` runs once a second while the Prayer screen is showing, but calls this
 * only when the widget snapshot it writes has changed: each time its countdown passes a whole
 * minute or its ring one of the 120 steps it takes from one prayer to the next, mostly at
 * different moments (`WidgetMirrorWriter`), so once to twice a minute with the prayers two hours
 * apart or more, and more often when they are closer (about 2.7 times a minute at 70 minutes).
 * The other callers call it after their own writes whether or not anything changed (the ayah
 * pool mirror at every app start, settings and language changes, the background refresh), so the
 * Swift side still ignores calls where nothing a widget reads has changed.
 */
object WidgetRefreshBridge {
    /** Set from Swift. Null until the app has launched — widget extensions never set it. */
    var onRefresh: (() -> Unit)? = null
}

actual fun refreshWidgets() {
    WidgetRefreshBridge.onRefresh?.invoke()
}
