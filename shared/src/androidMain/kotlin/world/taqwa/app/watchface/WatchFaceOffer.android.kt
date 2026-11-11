package world.taqwa.app.watchface

/**
 * An Android phone has no Apple Watch to add a face to, so the row is never shown there. An add
 * that came anyway is a failure, said at once, never a silence.
 */
actual fun createWatchFaceOffer(): WatchFaceOffer = object : WatchFaceOffer {
    override fun available(): Boolean = false
    override fun add(onDone: (WatchFaceResult) -> Unit) = onDone(WatchFaceResult.FAILED)
}
