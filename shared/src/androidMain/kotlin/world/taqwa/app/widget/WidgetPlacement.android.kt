package world.taqwa.app.widget

/**
 * Set from `TaqwaApplication.onCreate`, next to [androidWidgetUpdateHook] and
 * [androidWidgetPinHook]: answering means querying `AppWidgetManager` for the receiver classes,
 * and those live in `androidApp`, which `shared` cannot see. The query itself is the one the
 * refresh alarms already rely on (`anyWidgetPlaced` / `anyAyahWidgetPlaced`).
 */
var androidWidgetPlacementHook: (() -> WidgetPlacement)? = null

/**
 * Android answers immediately — `getAppWidgetIds` is a synchronous binder call — so the suspend in
 * [WidgetPlacementSource.current] costs nothing here; it exists for iOS.
 *
 * No hook installed (a unit test, or a process that is not the app) or a hook that threw gives
 * [WidgetPlacement.Unknown], which hides the offer rather than guessing at it.
 */
actual fun createWidgetPlacementSource(): WidgetPlacementSource = object : WidgetPlacementSource {
    override suspend fun current(): WidgetPlacement {
        val hook = androidWidgetPlacementHook ?: return WidgetPlacement.Unknown
        return runCatching { hook() }.getOrDefault(WidgetPlacement.Unknown)
    }
}
