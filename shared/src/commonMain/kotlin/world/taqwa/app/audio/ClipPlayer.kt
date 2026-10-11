package world.taqwa.app.audio

/**
 * A short audition from bytes already in hand — the reciter picker's fifteen-second preview (spec
 * §3, §5.5), which is a Compose resource file rather than a platform sound resource.
 *
 * Separate from [SoundPreviewPlayer] rather than a fourth case inside it: that one auditions a
 * *notification* sound, chooses its file from [world.taqwa.app.notifications.SoundAssets] and
 * plays it with notification audio attributes; a recitation preview is media, comes from
 * `composeResources`, and on Android has no `raw/` resource to point a `Uri` at. The two share
 * nothing but the word "preview".
 *
 * Both platforms ignore the ring/silent switch for these, as the adhan audition does: a preview
 * that plays nothing on a phone set to silent reads as a broken button.
 */
expect class ClipPlayer() {

    /**
     * Plays [bytes] from the start, stopping whatever this player was playing. MP3. [onEnd] is
     * called once, on the main thread, when the clip plays out by itself — not when [stop] cuts
     * it — so the picker can hand the audio back to a recitation it paused for the audition.
     *
     * A clip that cannot be played at all (a file that will not write, bytes that will not
     * decode, nothing to play) ends at once: [onEnd] is called, possibly before this returns,
     * because a caller that marked itself as playing and waits for the end would otherwise wait
     * for ever, in silence.
     */
    fun play(bytes: ByteArray, onEnd: () -> Unit = {})

    fun stop()
}
