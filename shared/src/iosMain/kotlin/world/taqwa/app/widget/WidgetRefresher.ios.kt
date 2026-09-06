package world.taqwa.app.widget

/**
 * WidgetKit's `WidgetCenter` is Swift-only and not reachable from Kotlin/Native interop, so the
 * real `reloadAllTimelines()` call cannot live in Kotlin. Instead the Swift app installs a hook
 * here at launch (`iOSApp.swift`) and Kotlin invokes it whenever the mirror has just been
 * rewritten — a real reload, routed through the one language that can make it.
 *
 * `TodayViewModel.refresh()` runs once a second while the Today screen is on screen, so the Swift
 * side is responsible for ignoring calls where the serialised snapshot has not actually changed.
 */
object WidgetRefreshBridge {
    /** Set from Swift. Null until the app has launched — widget extensions never set it. */
    var onRefresh: (() -> Unit)? = null
}

actual fun refreshWidgets() {
    WidgetRefreshBridge.onRefresh?.invoke()
}
