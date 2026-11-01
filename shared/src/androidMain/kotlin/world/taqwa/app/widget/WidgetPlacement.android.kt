package world.taqwa.app.widget

/**
 * Set from `TaqwaApplication.onCreate`, next to [androidWidgetUpdateHook] and
 * [androidWidgetPinHook]: answering means querying `AppWidgetManager` for the receiver classes,
 * and those live in `androidApp`, which `shared` cannot see. The query itself is the one the
 * refresh alarms already rely on (`anyWidgetPlaced` / `anyAyahWidgetPlaced`). The hook answers
 * null when the phone has no AppWidgetManager to ask.
 */
var androidWidgetPlacementHook: (() -> WidgetPlacement?)? = null

/**
 * Android answers immediately — `getAppWidgetIds` is a synchronous binder call — so the suspend in
 * [WidgetPlacementSource.current] costs nothing here; it exists for iOS.
 *
 * No hook installed (a unit test, or a process that is not the app) or a hook that threw cannot
 * tell, and says so with null: the Widgets screen then shows no status and no offer rather than
 * guessing at either.
 */
actual fun createWidgetPlacementSource(): WidgetPlacementSource = object : WidgetPlacementSource {
    override suspend fun current(): WidgetPlacement? {
        val hook = androidWidgetPlacementHook ?: return null
        return runCatching { hook() }.getOrNull()
    }
}
