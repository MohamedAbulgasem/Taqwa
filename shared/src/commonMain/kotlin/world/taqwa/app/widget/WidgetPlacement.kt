package world.taqwa.app.widget

/**
 * Which Taqwa widgets are on a home screen right now.
 *
 * Read on demand and never cached across a resume: widgets are added and removed *outside* the
 * app, so a cached answer is wrong precisely when it matters.
 */
data class WidgetPlacement(val prayer: Boolean, val ayah: Boolean) {
    companion object {
        /**
         * What every platform reports when it cannot answer — no hook installed, a failure, or a
         * timeout. Both widgets read as placed, so nothing is offered while the answer is unknown:
         * offering to add a widget somebody already has is worse than staying quiet, because it
         * makes the screen look wrong to the one person able to notice.
         */
        val Unknown = WidgetPlacement(prayer = true, ayah = true)
    }
}

interface WidgetPlacementSource {
    /** Suspend because iOS answers asynchronously; Android answers immediately. */
    suspend fun current(): WidgetPlacement
}

expect fun createWidgetPlacementSource(): WidgetPlacementSource

/** What the Appearance screen offers under one widget's preview. */
sealed interface WidgetOffer {
    /** Already on a home screen — no row, no note; the preview is only a preview. */
    data object None : WidgetOffer

    /** A tappable row that asks the launcher to place [widget]. */
    data class Pin(val widget: PinnableWidget) : WidgetOffer

    /** A caption giving the platform's own steps; nothing tappable. */
    data class Instructions(val path: WidgetAddPath) : WidgetOffer
}

/**
 * The three-way choice beneath a preview, as a pure function so the branching is testable without
 * a composable.
 *
 * [placed] wins over everything: while placement is [WidgetPlacement.Unknown] both widgets read as
 * placed and the screen shows nothing at all, which is what makes the row fade in when the answer
 * arrives rather than flash away when it turns out to be unnecessary.
 */
fun widgetOffer(
    widget: PinnableWidget,
    placed: Boolean,
    pinnable: Boolean,
    addPath: WidgetAddPath,
): WidgetOffer = when {
    placed -> WidgetOffer.None
    pinnable -> WidgetOffer.Pin(widget)
    else -> WidgetOffer.Instructions(addPath)
}
