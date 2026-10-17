package world.taqwa.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield

/**
 * Calls [onFront] each time the app is in front: every entry into STARTED, the first included. On
 * iOS, Compose Multiplatform's STARTED is the app in front and RESUMED the app active
 * (ComposeContainerLifecycleDelegate in CMP 1.12.1), so Control Center, Notification Center and a
 * system alert, which only make the app inactive for a moment, are not a new time in front.
 *
 * The first entry is not always the launch. iOS can compose the scene in the background (a
 * background refresh, a finished download) and bring it forward later, and it can build the scene
 * afresh while a recitation keeps the process alive; either way the person opening the app is a
 * time in front like any other.
 *
 * [onFront] is given what [left] read when the app last left the front. That reading is kept in
 * [lastLeft], which outlives this watcher: a composition built afresh while the process lived on
 * starts from the old one's last leave, so a request counted before it began watching (the widget
 * tap that rebuilt the scene) still counts as one made since the leave. Only a process that has
 * never left the front starts from what [left] reads when watching begins. [onFront] runs inside
 * the time in front, so leaving cancels it.
 */
internal suspend fun <T> Lifecycle.onEachTimeInFront(
    left: () -> T,
    lastLeft: MutableStateFlow<T?> = MutableStateFlow(null),
    onFront: suspend (T) -> Unit,
) {
    var whenItLeft = lastLeft.value ?: left()
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        try {
            onFront(whenItLeft)
            awaitCancellation()
        } finally {
            whenItLeft = left()
            lastLeft.value = whenItLeft
        }
    }
}

/** The app's last [Leave] in this process, whichever composition saw it (see [onEachTimeInFront]). */
internal val processLastLeave = MutableStateFlow<Leave?>(null)

/** What was known as the app left the front: the request count, and whether it left for a link of its own. */
internal data class Leave(val requests: Long, val forOwnLink: Boolean)

/**
 * What the iPhone app does each time it is in front (bug hunt B1 and B19).
 *
 * The notifications are planned again at once, and the widgets with them. iOS drops a request
 * added while notifications are refused and never retries it, and an app only ever resumed from
 * the app switcher would run its plan out in days. The first time in front is included, so this
 * is also iOS's launch plan. A background launch that never comes to the front (a refresh task or
 * a download wake) keeps the plan already armed; the refresh task also makes its own.
 *
 * With a voice going, the recited ayah is opened once the app is active and a turn later, so a URL
 * or a notification response delivered with the activation is usually counted first; anything
 * later still wins after the reading settings load ([jumpToPlaying]). Not after a link the app
 * opened itself (iOS Settings, a mail, a web page): the person comes back to the screen they left
 * from, and that holds for the whole return, however long the trip and whatever brings them back.
 * And [openPlaying] yields to any request counted since the app left. A visit that ends before the
 * app is active ends the wait with it.
 */
internal suspend fun Lifecycle.eachTimeInFront(
    requestsMade: () -> Long,
    leftForOwnLink: () -> Boolean,
    reschedule: () -> Unit,
    playing: () -> Boolean,
    openPlaying: (yieldToRequestsSince: Long) -> Unit,
    lastLeave: MutableStateFlow<Leave?> = processLastLeave,
) = onEachTimeInFront(left = { Leave(requestsMade(), leftForOwnLink()) }, lastLeft = lastLeave) { left ->
    reschedule()
    if (left.forOwnLink || !playing()) return@onEachTimeInFront
    currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    yield()
    openPlaying(left.requests)
}
