package world.taqwa.app.widget

/**
 * Which Taqwa widgets are placed right now: [prayer] and [ayah] on a home screen, and on iPhone
 * [lockScreen], the Lock Screen widget, which is a widget of its own and never counts as the prayer
 * widget on the Home Screen. Android has no Lock Screen widget, so it never reports one.
 *
 * Read on demand and never cached across a resume: widgets are added and removed *outside* the
 * app, so a cached answer is wrong precisely when it matters.
 */
data class WidgetPlacement(val prayer: Boolean, val ayah: Boolean, val lockScreen: Boolean = false)

interface WidgetPlacementSource {
    /**
     * The widgets placed now, or null when this platform cannot tell: no hook installed, no
     * AppWidgetManager, a WidgetKit failure, or no answer within its wait. Cannot tell is not an
     * answer, so it never puts a check on a card or counts a widget as added, and it never offers
     * a widget either: offering one somebody already has is as wrong as claiming one they do not.
     *
     * Suspend because iOS answers asynchronously; Android answers immediately.
     */
    suspend fun current(): WidgetPlacement?
}

expect fun createWidgetPlacementSource(): WidgetPlacementSource

/**
 * Placement read again for a screen that shows [shown] (null before any answer): the new answer
 * when the platform gives one, and otherwise what was shown already. A read that cannot tell is no
 * news. On iPhone a pull of Control Center or Notification Center resumes the app, and the read
 * that follows can time out; it must not take away a true check, or a count, that the last answer
 * gave. Before any answer the screens show no status at all, which is also what a first read that
 * cannot tell leaves.
 */
suspend fun WidgetPlacementSource.reread(shown: WidgetPlacement?): WidgetPlacement? = current() ?: shown

/** What the Widgets screen offers at the foot of one widget's card. */
sealed interface WidgetOffer {
    /** Already on a home screen — nothing to add, so the card says so instead of offering. */
    data object None : WidgetOffer

    /** A button that asks the launcher to place [widget]. */
    data class Pin(val widget: PinnableWidget) : WidgetOffer

    /** The platform's own steps for adding the widget by hand; nothing asks the launcher. */
    data class Instructions(val path: WidgetAddPath) : WidgetOffer
}

/**
 * The three-way choice beneath a preview, as a pure function so the branching is testable without
 * a composable.
 *
 * [placed] wins over everything: a widget already placed is offered nothing. This is only ever
 * asked with a real answer. While placement is unread, or the platform cannot tell, the screen has
 * no offer to decide and shows nothing, which is what makes the foot of each card fade in when the
 * answer arrives rather than flash away.
 *
 * [refused] is true once the launcher has turned down a request for this widget: the button did
 * nothing, so the steps by hand take its place. It is never set by a request the launcher took.
 */
fun widgetOffer(
    widget: PinnableWidget,
    placed: Boolean,
    pinnable: Boolean,
    addPath: WidgetAddPath,
    refused: Boolean = false,
): WidgetOffer = when {
    placed -> WidgetOffer.None
    pinnable && !refused -> WidgetOffer.Pin(widget)
    else -> WidgetOffer.Instructions(addPath)
}
