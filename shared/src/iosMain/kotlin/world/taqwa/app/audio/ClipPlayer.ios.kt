package world.taqwa.app.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create
import platform.darwin.NSObject

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

    /** `AVAudioPlayer.delegate` is weak, like every Cocoa delegate: held here or the end of the
     * clip is never heard of. */
    private var delegate: NSObject? = null

    actual fun play(bytes: ByteArray, onEnd: () -> Unit) {
        stop()
        if (bytes.isEmpty()) return
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
        val started = AVAudioPlayer(data = data, error = null)
        val ended = object : NSObject(), AVAudioPlayerDelegateProtocol {
            override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
                // Only the clip still in the player: a stopped one has already been let go of.
                if (this@ClipPlayer.player !== player) return
                this@ClipPlayer.player = null
                delegate = null
                onEnd()
            }
        }
        delegate = ended
        started.setDelegate(ended)
        player = started
        started.prepareToPlay()
        started.play()
    }

    actual fun stop() {
        player?.stop()
        player = null
        delegate = null
    }
}
