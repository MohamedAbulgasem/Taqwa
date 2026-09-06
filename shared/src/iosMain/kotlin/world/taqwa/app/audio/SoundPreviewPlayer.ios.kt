package world.taqwa.app.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.notifications.SoundAssets

@OptIn(ExperimentalForeignApi::class)
private class IosSoundPreviewPlayer : SoundPreviewPlayer {

    private var player: AVAudioPlayer? = null

    override fun play(sound: PrayerSound) {
        stop()
        when (sound) {
            // Nothing to audition; the button press itself is the reassurance.
            PrayerSound.SILENT -> return
            // iOS has no public API to play "the" default notification tone outside a
            // delivered notification; this system sound is the closest available stand-in.
            PrayerSound.NOTIFICATION -> AudioServicesPlaySystemSound(1007u)
            PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val fileName = SoundAssets.iosResourceFileName(sound)!!
                val path = NSBundle.mainBundle.pathForResource(
                    fileName.substringBeforeLast('.'), fileName.substringAfterLast('.'),
                ) ?: return
                player = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
                player?.play()
            }
        }
    }

    override fun stop() {
        player?.stop()
        player = null
    }
}

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = IosSoundPreviewPlayer()
