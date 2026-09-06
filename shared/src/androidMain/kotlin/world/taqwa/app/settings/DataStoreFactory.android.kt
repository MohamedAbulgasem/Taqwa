package world.taqwa.app.settings

// `appContext` moved to `:widgetcore` (same package, `AppContext.kt`) — the widget model's
// Android KeyValueStore needs it and `widgetcore` cannot depend on `shared`. It is re-exposed
// from here via `api(project(":widgetcore"))`, so existing imports are unchanged.

actual fun dataStoreDirectory(): String = appContext.filesDir.path
