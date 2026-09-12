package world.taqwa.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import world.taqwa.app.settings.appContext
import java.io.File

/**
 * `MediaPlayer` over a temp file. `setDataSource` takes a path, a `Uri` or a
 * `MediaDataSource` — the last is API 23+ and would be the tidy answer, but it needs a subclass
 * per call and the file is a hundred kilobytes that the cache directory reclaims on its own, so
 * the temp file wins on plainness.
 *
 * One file per player instance, overwritten each preview: two previews cannot overlap ([play]
 * stops the last one first), so there is never a second reader of it.
 */
actual class ClipPlayer actual constructor() {

    private var player: MediaPlayer? = null
    private val file: File by lazy { File(appContext.cacheDir, "recitation-preview.mp3") }

    actual fun play(bytes: ByteArray) {
        stop()
        val path = runCatching {
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull() ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(path)
                setOnCompletionListener { it.release(); player = null }
                prepare()
                start()
            }
        }.getOrNull()
    }

    actual fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}
