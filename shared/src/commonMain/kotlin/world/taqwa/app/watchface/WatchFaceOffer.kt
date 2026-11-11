package world.taqwa.app.watchface

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** How an "Add to Apple Watch" ended: the face was taken, or it was not, for whatever reason. */
enum class WatchFaceResult { ADDED, FAILED }

/**
 * Settings' "Add to Apple Watch" (plan 4 Task 10, spec 4.5): one of Apple's faces with Taqwa's
 * complications already in place, from the `.watchface` file the iPhone app bundles, handed to
 * ClockKit's `CLKWatchFaceLibrary.addWatchFace(at:)`.
 *
 * Only the iPhone offers it. Plan 5's Android rows ("Install on your watch") have their own source,
 * and sit beside this one in `WatchRows.kt`.
 */
interface WatchFaceOffer {
    /**
     * True while the face can be offered: a watch is paired and a face file is bundled. Asked again
     * each time the row is shown, never remembered, since a watch is paired and unpaired outside
     * the app. Never throws: a platform that cannot tell offers nothing.
     */
    fun available(): Boolean

    /**
     * Hands the face to the system, which shows its own add-face sheet, and calls [onDone] once,
     * on the main thread, with how it ended. Never throws.
     */
    fun add(onDone: (WatchFaceResult) -> Unit)

    /**
     * Says [available] may have changed, each time the session says so, for as long as it is
     * collected, so the row follows a watch paired or unpaired while Settings is open (the owner's
     * report of 9 November). Nothing is kept for a later collector, which asks [available] at its
     * own start. The iPhone's says what `TaqwaWatchSession`'s delegate hears
     * ([WatchFaceShareBridge.sessionChanged]); Android's, and an offer by default, says nothing.
     */
    fun changes(): Flow<Unit> = emptyFlow()
}

expect fun createWatchFaceOffer(): WatchFaceOffer
