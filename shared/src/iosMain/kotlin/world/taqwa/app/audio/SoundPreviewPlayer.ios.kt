package world.taqwa.app.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.darwin.NSObject
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.notifications.SoundAssets

@OptIn(ExperimentalForeignApi::class)
private class IosSoundPreviewPlayer : SoundPreviewPlayer {

    private var player: AVAudioPlayer? = null

    /** `AVAudioPlayer.delegate` is weak, like every other Cocoa delegate — hold it here so the
     * end-of-clip callback that releases the audio session actually fires. */
    private val playerDelegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            releaseSession()
        }
    }

    override fun play(sound: PrayerSound) {
        stop()
        when (sound) {
            // Nothing to audition; the button press itself is the reassurance.
            PrayerSound.SILENT -> return
            PrayerSound.NOTIFICATION, PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val fileName = SoundAssets.iosResourceFileName(sound)!!
                val path = NSBundle.mainBundle.pathForResource(
                    fileName.substringBeforeLast('.'), fileName.substringAfterLast('.'),
                ) ?: return
                claimSession()
                player = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
                player?.setDelegate(playerDelegate)
                player?.prepareToPlay()
                player?.play()
            }
        }
    }

    override fun stop() {
        player?.stop()
        player = null
        releaseSession()
    }

    /**
     * The default session category is silenced by the hardware ring/silent switch, so an adhan
     * audition on a phone that is on silent plays nothing and the user reads a working button as
     * broken. `.playback` is the category that ignores the switch; it is claimed only for the
     * length of the preview and released in [releaseSession] as soon as the clip ends or is
     * stopped, so the app does not sit holding the audio route.
     */
    private fun claimSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }

    private fun releaseSession() {
        AVAudioSession.sharedInstance().setActive(false, null)
    }
}

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = IosSoundPreviewPlayer()
