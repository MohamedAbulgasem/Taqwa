package world.taqwa.app.widget

// WidgetKit's `WidgetCenter` is Swift-only and not reachable from Kotlin/Native interop. The
// real reload call — `WidgetCenter.shared.reloadAllTimelines()` — lives in `iOSApp.swift` and
// fires on foreground and after `BackgroundRefreshBridge` runs (Task 24). This is intentionally
// a no-op rather than a fake implementation of something Kotlin cannot actually do.
actual fun refreshWidgets() { }
