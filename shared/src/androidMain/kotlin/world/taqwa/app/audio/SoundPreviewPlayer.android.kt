package world.taqwa.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.notifications.SoundAssets
import world.taqwa.app.settings.appContext

private class AndroidSoundPreviewPlayer : SoundPreviewPlayer {

    private var mediaPlayer: MediaPlayer? = null

    override fun play(sound: PrayerSound) {
        stop()
        // Silent has nothing to audition; the button press itself is the reassurance that
        // nothing plays.
        if (sound == PrayerSound.SILENT) return

        val name = SoundAssets.androidRawResourceName(sound)!!
        val uri = Uri.parse("android.resource://${appContext.packageName}/raw/$name")

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(appContext, uri)
            setOnCompletionListener { player -> player.release(); mediaPlayer = null }
            prepare()
            start()
        }
    }

    override fun stop() {
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
    }
}

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = AndroidSoundPreviewPlayer()
