package world.taqwa.app.widget

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Set from Swift at launch (`iOSApp.swift`), next to the widget-refresh hook.
 *
 * `WidgetCenter.getCurrentConfigurations` is Swift-only and asynchronous, so the hook takes a
 * completion rather than returning: Swift calls it back with `(prayer, ayah)` once WidgetKit has
 * answered. Null until the app has launched — widget extensions never set it.
 */
var iosWidgetPlacementHook: ((completion: (Boolean, Boolean) -> Unit) -> Unit)? = null

/** Long enough for a WidgetKit round trip, short enough that a screen never waits on it. */
private const val PLACEMENT_TIMEOUT_MILLIS = 2_000L

/**
 * Bridges the Swift callback into a suspend call.
 *
 * Every way of not getting an answer — no hook, a throw out of the hook, or WidgetKit never
 * calling back — lands on [WidgetPlacement.Unknown], so the Appearance screen shows nothing rather
 * than an offer it cannot stand behind.
 */
actual fun createWidgetPlacementSource(): WidgetPlacementSource = object : WidgetPlacementSource {
    override suspend fun current(): WidgetPlacement {
        val hook = iosWidgetPlacementHook ?: return WidgetPlacement.Unknown
        return withTimeoutOrNull(PLACEMENT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                // WidgetKit's completion handlers are not contractually single-shot, and a second
                // `resume` on the same continuation throws. The flag makes a repeated callback a
                // no-op instead; `isActive` covers the race with the timeout cancelling us.
                var answered = false
                fun answer(placement: WidgetPlacement) {
                    if (answered) return
                    answered = true
                    if (continuation.isActive) continuation.resume(placement)
                }
                val completion: (Boolean, Boolean) -> Unit = { prayer, ayah ->
                    answer(WidgetPlacement(prayer = prayer, ayah = ayah))
                }
                runCatching { hook(completion) }.onFailure { answer(WidgetPlacement.Unknown) }
            }
        } ?: WidgetPlacement.Unknown
    }
}
