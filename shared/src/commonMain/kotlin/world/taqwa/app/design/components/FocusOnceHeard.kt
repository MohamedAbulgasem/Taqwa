package world.taqwa.app.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Moves focus to [requester]'s node a moment after it appears, for a screen reader to follow.
 * Android's accessibility bridge tells a screen reader that focus has moved only to a node it has
 * already seen, and it looks for new nodes at most every 100 ms; asked at once, focus moved without
 * TalkBack hearing of it, and TalkBack put its own on the Back button instead (seen on the emulator,
 * Widgets' review round 2). Then [onMoved], so the screen forgets what asked for it.
 *
 * Only where a screen reader runs ([world.taqwa.app.design.screenReaderRunning]): moving focus also
 * scrolls what it lands on into view, which a sighted reader, who never had focus there, must not
 * meet. A node that is focusable only from a keyboard (a `clickable`'s own) is made focusable for
 * the screen reader with `focusProperties { canFocus = true }` ahead of it.
 */
@Composable
internal fun FocusOnceHeard(requester: FocusRequester, onMoved: () -> Unit = {}) {
    val moved by rememberUpdatedState(onMoved)
    LaunchedEffect(requester) {
        delay(NewNodeHeardMs)
        requester.requestFocus()
        moved()
    }
}

/** Long enough for the bridge's next look for new nodes, and short enough to follow the tap. */
private const val NewNodeHeardMs = 250L
