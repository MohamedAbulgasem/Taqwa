package world.taqwa.app.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.darwin.NSObject
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.notifications.SoundAssets

@OptIn(ExperimentalForeignApi::class)
private class IosSoundPreviewPlayer : SoundPreviewPlayer {

    private var player: AVAudioPlayer? = null

    /** `AVAudioPlayer.delegate` is weak, like every other Cocoa delegate — hold it here so the
     * end-of-clip callback that releases the audio session actually fires. */
    private val playerDelegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            // Only the clip still in the player: a stopped or replaced one has already been let go
            // of, and its late callback must not take the session from whatever is playing now.
            if (this@IosSoundPreviewPlayer.player !== player) return
            this@IosSoundPreviewPlayer.player = null
            releaseSession()
        }
    }

    override fun play(sound: PrayerSound, voice: AdhanVoice) {
        stop()
        when (sound) {
            // Nothing to audition; the button press itself is the reassurance.
            PrayerSound.SILENT -> return
            PrayerSound.NOTIFICATION, PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val fileName = SoundAssets.iosPreviewResourceFileName(sound, voice)!!
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

    /**
     * The session is process-wide, so it is handed back only when nobody else needs it: a reader
     * auditioning a sound while a surah plays would otherwise have the surah cut dead the moment
     * the clip ended. Recitation deactivates on its own terms in `RecitationPlayer.stop()`.
     *
     * `NotifyOthersOnDeactivation` so the music or podcast interrupted for the audition is told it
     * may resume, which a bare `setActive(false)` never says.
     */
    private fun releaseSession() {
        if (IosAudioSession.recitationHoldsSession) return
        AVAudioSession.sharedInstance().setActive(
            false,
            withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
            error = null,
        )
    }
}

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = IosSoundPreviewPlayer()
