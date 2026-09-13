package world.taqwa.app.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * `AVAudioPlayer` straight from the bytes — no temp file, since the initialiser takes `NSData`.
 *
 * The session is claimed as `.playback`, like the adhan audition, so a preview is heard on a phone
 * whose ring switch is set to silent. Unlike that audition it is **never deactivated**: recitation
 * itself may be playing through `RecitationPlayer`'s own `AVPlayer` on the same shared session,
 * and deactivating it at the end of a fifteen-second clip would silence the surah. The session is
 * process-wide state; whoever is still playing keeps it.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class ClipPlayer actual constructor() {

    private var player: AVAudioPlayer? = null

    actual fun play(bytes: ByteArray) {
        stop()
        if (bytes.isEmpty()) return
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
        player = AVAudioPlayer(data = data, error = null).also {
            it.prepareToPlay()
            it.play()
        }
    }

    actual fun stop() {
        player?.stop()
        player = null
    }
}
