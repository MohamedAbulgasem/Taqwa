package world.taqwa.app.audio

import kotlin.concurrent.Volatile

/**
 * Who is holding the process-wide `AVAudioSession`.
 *
 * There is one session for the whole app: recitation's `AVPlayer`, the adhan audition and the
 * reciter clip all activate the same shared instance. So a short player that deactivates it when
 * its own clip ends silences whatever else is playing — a user auditioning a notification sound
 * while a surah plays had the surah cut dead. The short players ask here first and leave the
 * session alone while recitation holds it; recitation hands it back itself in `stop()`.
 *
 * A flag rather than a look at `RecitationPlayer.state`: `:audio` is below `:recitation` and must
 * not depend on it, and this is the whole of what the short players need to know.
 */
internal object IosAudioSession {

    @Volatile
    var recitationHoldsSession: Boolean = false
}
