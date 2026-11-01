package world.taqwa.app.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Keeps a row where it is on screen while the content above it grows or shrinks (Duha and Iqama
 * spec §4.6): in Settings › Notifications the Iqama switch sits under the five prayers, and turning
 * it on or off adds or removes a line under each of them. Whatever height [above] gains or loses
 * while [armed] is scrolled away in the same frame, so the row stays under the finger through the
 * reveal, and through the jump under reduced motion. Where the scroll range cannot take it (the
 * top, or the end when the content shrinks), the row moves, as it always did. Any other change to
 * [above]'s height while armed, a rotation or a new font size, is followed too.
 */
internal class ScrollAnchor(val scroll: ScrollState) {
    /** Set by the control whose row should stay; cleared by whatever else changes [above]. */
    var armed = false
    private var height = -1
    private var pending = 0

    /** On the content above the row: how far its height moved while armed. */
    val above: Modifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        if (armed && height >= 0) pending += placeable.height - height
        height = placeable.height
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    /**
     * On the scroller, outside its verticalScroll. By the time [scroll]'s own measure returns it has
     * taken its new range from the content, and it reads its offset only when it places the content,
     * so a delta dispatched in between lands in this frame and may use the room the content has just
     * gained. Dispatched from inside the content it would meet the old range, and on a screen that
     * fitted, or at the end under reduced motion's single jump, the row would move.
     *
     * A range that shrinks under the offset has already pulled it in by the whole shrink while it
     * measured, the lines above included, so only what is still missing is dispatched: at the end,
     * with the lines going, nothing, and the row moves only by what went below it. Neither read is
     * observed, or every scroll frame would remeasure the scroller.
     */
    val scroller: Modifier = Modifier.layout { measurable, constraints ->
        val before = Snapshot.withoutReadObservation { scroll.value }
        val placeable = measurable.measure(constraints)
        if (pending != 0) {
            Snapshot.withoutReadObservation {
                scroll.dispatchRawDelta((before + pending - scroll.value).toFloat())
            }
            pending = 0
        }
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

@Composable
internal fun rememberScrollAnchor(): ScrollAnchor {
    val scroll = rememberScrollState()
    return remember(scroll) { ScrollAnchor(scroll) }
}
