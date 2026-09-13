package world.taqwa.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.notifications.SoundAssets
import world.taqwa.app.settings.appContext

private class AndroidSoundPreviewPlayer : SoundPreviewPlayer {

    private var mediaPlayer: MediaPlayer? = null

    override fun play(sound: PrayerSound, voice: AdhanVoice) {
        stop()
        // Silent has nothing to audition; the button press itself is the reassurance that
        // nothing plays.
        if (sound == PrayerSound.SILENT) return

        val name = SoundAssets.androidPreviewRawResourceName(sound, voice)!!
        val uri = Uri.parse("android.resource://${appContext.packageName}/raw/$name")

        // Wrapped exactly as ClipPlayer is: setDataSource throws IOException on a raw resource
        // that resource shrinking removed, and this is called straight from a tap in the sound
        // sheet — on the main thread, where an uncaught throw is the whole app.
        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(appContext, uri)
                setOnCompletionListener { player -> player.release(); mediaPlayer = null }
                // prepareAsync, not prepare: the full adhan is 211 seconds of ogg and decoding
                // its header synchronously is an ANR-shaped call in the middle of Settings. The
                // start is guarded because stop() may have released this player first — see below.
                setOnPreparedListener { player -> runCatching { player.start() } }
                prepareAsync()
            }
        }.getOrNull()
    }

    /**
     * Also reached with a player still preparing — the reader taps a second sound before the
     * first has started. `stop()` throws from the Preparing state, so it is guarded and
     * `release()` runs either way; a prepared callback that lands afterwards finds a released
     * player and its own `runCatching` above swallows the result.
     */
    override fun stop() {
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
    }
}

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = AndroidSoundPreviewPlayer()
