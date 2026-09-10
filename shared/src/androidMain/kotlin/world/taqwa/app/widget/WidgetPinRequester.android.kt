package world.taqwa.app.widget

import android.appwidget.AppWidgetManager
import world.taqwa.app.settings.appContext

/** Set from `TaqwaApplication.onCreate`, next to [androidWidgetUpdateHook]: the widget receiver
 * classes live in `androidApp`, which `shared` cannot see. Takes the widget being asked for —
 * each maps to its own provider — and returns whether the launcher took the request. */
var androidWidgetPinHook: ((PinnableWidget) -> Boolean)? = null

actual fun createWidgetPinRequester(): WidgetPinRequester = object : WidgetPinRequester {
    // Whether the launcher takes pin requests at all; it does not vary by provider, so the
    // parameter is only here because the interface is per-widget.
    override fun isSupported(widget: PinnableWidget): Boolean =
        androidWidgetPinHook != null &&
            (AppWidgetManager.getInstance(appContext)?.isRequestPinAppWidgetSupported ?: false)

    override fun requestPin(widget: PinnableWidget) {
        runCatching { androidWidgetPinHook?.invoke(widget) }
    }
}
