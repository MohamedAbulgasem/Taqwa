package world.taqwa.app.widget

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Set from Swift at launch (`iOSApp.swift`), next to the widget-refresh hook.
 *
 * `WidgetCenter.getCurrentConfigurations` is Swift-only and asynchronous, so the hook takes a
 * completion rather than returning: Swift calls it back with the kind of every widget WidgetKit
 * reports as placed, or with null when WidgetKit failed. Which widget each kind is, is decided
 * here rather than in Swift, so that it can be tested. Null until the app has launched — widget
 * extensions never set it.
 */
var iosWidgetPlacementHook: ((completion: (kinds: List<String>?) -> Unit) -> Unit)? = null

/** Long enough for a WidgetKit round trip, short enough that a screen never waits on it. */
private const val PLACEMENT_TIMEOUT_MILLIS = 2_000L

/**
 * The widgets that [kinds], as `WidgetConfiguration` kinds in `iosApp/TaqwaWidget`, mean. Each
 * kind is one widget: the Lock Screen widget is its own kind, `TaqwaLockScreenWidget`, and the
 * prayer widget on the Home Screen is `TaqwaHomeWidget` alone. Folding the two together once made
 * a Lock Screen widget read as the prayer widget "On your Home Screen".
 */
private fun placementOf(kinds: List<String>) = WidgetPlacement(
    prayer = "TaqwaHomeWidget" in kinds,
    ayah = "TaqwaAyahWidget" in kinds,
    lockScreen = "TaqwaLockScreenWidget" in kinds,
)

/**
 * Bridges the Swift callback into a suspend call.
 *
 * Every way of not getting an answer — no hook, a throw out of the hook, WidgetKit's failure, or
 * WidgetKit never calling back — lands on null, which the Widgets screen and the Settings root
 * read as "cannot tell": no status, no count and no offer, rather than one they cannot stand
 * behind.
 */
actual fun createWidgetPlacementSource(): WidgetPlacementSource = object : WidgetPlacementSource {
    override suspend fun current(): WidgetPlacement? {
        val hook = iosWidgetPlacementHook ?: return null
        return withTimeoutOrNull(PLACEMENT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                // WidgetKit's completion handlers are not contractually single-shot, and a second
                // `resume` on the same continuation throws. The flag makes a repeated callback a
                // no-op instead; `isActive` covers the race with the timeout cancelling us.
                var answered = false
                fun answer(placement: WidgetPlacement?) {
                    if (answered) return
                    answered = true
                    if (continuation.isActive) continuation.resume(placement)
                }
                val completion: (List<String>?) -> Unit = { kinds -> answer(kinds?.let(::placementOf)) }
                runCatching { hook(completion) }.onFailure { answer(null) }
            }
        }
    }
}
