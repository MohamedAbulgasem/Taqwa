package world.taqwa.app.recitation

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/** `speech://<generation>/<surah>/<ayah>` — the translation read after an ayah (read-aloud spec §5.5). */
internal const val SPEECH_SCHEME = "speech"

internal fun speechUri(generation: Long, surah: Int, ayah: Int): String = "$SPEECH_SCHEME://$generation/$surah/$ayah"

/** What the service is to read, sent by the app with the queue it belongs to. */
internal data class SpeechScript(
    val generation: Long,
    val engine: String,
    val voiceId: String,
    val language: String,
    val texts: Map<Int, String>,
)

/**
 * Lets an engine go, and never throws. `shutdown()` unbinds a service, and an engine that never
 * finished binding, or whose process has died, can answer that with an exception: from the
 * service's teardown or a main-looper callback it would crash the app, and from a synthesis on
 * ExoPlayer's loading thread it would reach ExoPlayer as the very error [SpeechDataSource] exists
 * to keep from it. Every engine this app binds is let go through here.
 */
internal fun TextToSpeech.shutdownQuietly() {
    runCatching { shutdown() }
}

/**
 * The service's voice (read-aloud spec §5.5): one `TextToSpeech` bound to the engine the app
 * chose, turning a translation into a WAV the player plays like any other item.
 *
 * [render] is called on ExoPlayer's loading thread, which is already reading ahead while the ayah
 * before it plays, so the synthesis is done before the voice is due. It never throws: an engine
 * that fails, a voice that has gone, a timeout — each is answered with no file, which
 * [SpeechDataSource] plays as a tenth of a second of silence, and the recitation carries on,
 * because an error from a data source would stop the whole surah.
 *
 * **Nothing here makes the main thread wait.** A synthesis holds [lock] for as long as the engine
 * takes — seconds for a long tafsir, and up to its timeout for an engine that hangs — while
 * [setScript] and [release] arrive on the main thread, from a session command and from the
 * service's teardown, where waiting that long would be an ANR. So neither of them takes the
 * lock: they change what a synthesis checks ([script], [released]) and, on release, shut the
 * engine down, which `TextToSpeech` allows from any thread. A synthesis whose queue has moved on
 * finds out when it looks again, before it binds and again before its file is put in place, and a
 * wait it no longer needs is cut short by ExoPlayer, which interrupts a loading thread when it
 * lets go of the item.
 */
@UnstableApi
internal class Speaker(private val context: Context) {

    /** What the queue being played reads. Replaced by [setScript]; checked by every [render]. */
    @Volatile private var script: SpeechScript? = null

    /** Set once, when the service goes. From then on every render is silence. */
    @Volatile private var released = false

    /**
     * The bound engine. Bound and let go under [lock], except that [release] takes it without the
     * lock; `getAndSet` on both sides is what makes exactly one of them shut each engine down.
     */
    private val tts = AtomicReference<TextToSpeech?>(null)

    /** The engine [tts] is bound to, and the voice last put on it. Under [lock]. */
    private var ttsEngine: String? = null
    private var ttsVoice: String? = null

    /** Makes every utterance id unique, so a late callback can never end the wrong wait. */
    private var utterances = 0L

    /** Held by one synthesis at a time, on a loading thread, and never by the main thread. */
    private val lock = ReentrantLock()

    private val dir: File get() = File(context.cacheDir, "speech")

    /**
     * A new queue's script, or none. Main thread; never waits.
     *
     * Every file in the directory goes. Each script is a new generation whose translations are
     * all still to be made, so anything there belongs to an older queue — or to an older process,
     * whose generations counted from one again and whose files would otherwise be read as this
     * one's. With no script, the engine goes too: at once if no synthesis holds it, otherwise
     * when that synthesis finishes (see [render]).
     */
    fun setScript(value: SpeechScript?) {
        script = value
        clear()
        if (value == null) letGo()
    }

    /**
     * The WAV for the translation after [ayah] of queue [generation], or null for silence: no such
     * text, a queue that has moved on, a service that has gone, or any failure of the engine.
     * Loading thread; waits for the engine.
     */
    fun render(generation: Long, ayah: Int): File? {
        if (released || script?.generation != generation) return null
        try {
            lock.lockInterruptibly()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        }
        val wav = try {
            synthesize(generation, ayah)
        } catch (e: InterruptedException) {
            // ExoPlayer has let go of the item — a new queue, a seek, the player released — and
            // interrupted its loading thread. The flag is put back for the loader, which clears it.
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            null
        } finally {
            try {
                // Read-aloud was turned off, or the service went, while this ran: nothing will ask
                // for the engine again, and a bound engine keeps its own process alive for nothing.
                if (script == null || released) unbind()
            } finally {
                // In a `finally` of its own: whatever the line above throws, a lock left held
                // would silence every translation after this one.
                lock.unlock()
            }
        }
        // A `setScript(null)` that landed after the check above but before the unlock found the
        // lock held and left the engine to this synthesis, which had already looked. Looked at
        // again now that the lock is free, so that engine is not left bound for nothing.
        if (script == null && !released) letGo()
        return wav
    }

    /**
     * The service is going. Main thread; never waits. ExoPlayer is released first, which
     * interrupts a synthesis still waiting; one that is not waiting finds [released] when it next
     * looks.
     */
    fun release() {
        released = true
        tts.getAndSet(null)?.shutdownQuietly()
        clear()
    }

    /** Under [lock]. Every failure it foresees is null; [render] makes silence of anything thrown. */
    private fun synthesize(generation: Long, ayah: Int): File? {
        // Looked at again now that the lock is held: the wait for it may have outlasted the queue.
        val current = script?.takeIf { it.generation == generation && !released } ?: return null
        val text = current.texts[ayah] ?: return null
        dir.mkdirs()
        val out = File(dir, "$generation-$ayah$WAV")
        if (out.length() > WAV_HEADER_BYTES) return out
        val engine = bind(current.engine) ?: return null
        if (!useVoice(engine, current.voiceId)) return null
        val id = "speech-$generation-$ayah-${++utterances}"
        val done = CountDownLatch(1)
        var ok = false
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId != id) return
                ok = true
                done.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.countDown()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) done.countDown()
            }
        })
        val partial = File(dir, "$generation-$ayah$PART")
        if (engine.synthesizeToFile(text, Bundle(), partial, id) != TextToSpeech.SUCCESS) {
            unbind()
            return null
        }
        val finished = try {
            done.await(SYNTHESIS_BASE_MS + text.length * SYNTHESIS_PER_CHAR_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            // Kept bound for the queue that replaces this one, but with nothing of ours queued.
            engine.stop()
            partial.delete()
            throw e
        }
        if (!finished || !ok) {
            // An engine that hung or failed is let go, so the next translation binds it afresh
            // instead of queueing behind it for the rest of the surah.
            unbind()
            partial.delete()
            return null
        }
        // The queue may have moved on, or the service gone, while the engine worked.
        if (released || script?.generation != generation ||
            partial.length() <= WAV_HEADER_BYTES || !partial.renameTo(out)
        ) {
            partial.delete()
            return null
        }
        trim(generation, ayah)
        return out
    }

    /** The engine the script names, bound once and kept for the queue. Under [lock]; waits. */
    private fun bind(engine: String): TextToSpeech? {
        val bound = tts.get()
        if (bound != null && ttsEngine == engine) return bound
        unbind()
        val ready = CountDownLatch(1)
        var started = false
        val made = TextToSpeech(context, { status ->
            started = status == TextToSpeech.SUCCESS
            ready.countDown()
        }, engine)
        val answered = try {
            ready.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            made.shutdownQuietly()
            throw e
        }
        if (!answered || !started) {
            made.shutdownQuietly()
            return null
        }
        tts.set(made)
        ttsEngine = engine
        // [release] may have run while the engine was starting, too early to find it there.
        if (released) {
            unbind()
            return null
        }
        return made
    }

    /** Puts the script's voice on the engine, once. False when the engine no longer has it. */
    private fun useVoice(engine: TextToSpeech, voiceId: String): Boolean {
        if (ttsVoice == voiceId) return true
        val voice = engine.voices?.firstOrNull { it.name == voiceId } ?: return false
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) return false
        ttsVoice = voiceId
        return true
    }

    /** Under [lock]. */
    private fun unbind() {
        ttsEngine = null
        ttsVoice = null
        tts.getAndSet(null)?.shutdownQuietly()
    }

    /** Lets the engine go if no synthesis holds it; one that does lets it go itself. Never waits. */
    private fun letGo() {
        if (!lock.tryLock()) return
        try {
            if (script == null) unbind()
        } finally {
            lock.unlock()
        }
    }

    /** A rolling window: the few translations behind the one just made, never a whole surah's. */
    private fun trim(generation: Long, ayah: Int) {
        val prefix = "$generation-"
        dir.listFiles()?.forEach { file ->
            val n = file.name.takeIf { it.startsWith(prefix) && it.endsWith(WAV) }
                ?.removePrefix(prefix)?.removeSuffix(WAV)?.toIntOrNull()
            if (n != null && n < ayah - KEEP_BEHIND) file.delete()
        }
    }

    private fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val WAV = ".wav"
        const val PART = ".part"
        const val WAV_HEADER_BYTES = 44L
        const val BIND_TIMEOUT_MS = 5_000L
        const val SYNTHESIS_BASE_MS = 15_000L
        const val SYNTHESIS_PER_CHAR_MS = 40L
        const val KEEP_BEHIND = 3
    }
}

/**
 * Serves `speech://` items: the [Speaker]'s WAV when there is one, and otherwise a tenth of a
 * second of silence held in memory, so that no failure of the voice — nor a file cleared from the
 * cache between its making and its reading — ever reaches ExoPlayer as an error (read-aloud spec
 * §7). The silence is never a file: two loading threads writing one at once, or the system
 * clearing the cache under it, would be exactly the error this exists to prevent.
 */
@UnstableApi
internal class SpeechDataSource(private val speaker: Speaker) : DataSource {

    private val file = FileDataSource()
    private val silence = ByteArrayDataSource(SILENT_WAV)
    private var opened: DataSource? = null
    private var requested: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        file.addTransferListener(transferListener)
        silence.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        requested = uri
        val generation = uri.authority?.toLongOrNull()
        val ayah = uri.pathSegments.getOrNull(1)?.toIntOrNull()
        val wav = if (generation != null && ayah != null) speaker.render(generation, ayah) else null
        if (wav != null) {
            try {
                opened = file
                return file.open(dataSpec.buildUpon().setUri(Uri.fromFile(wav)).build())
            } catch (e: IOException) {
                // Gone between its making and here — a new script clears the directory, and so
                // does the system when it needs the room — or asked for past its end.
                runCatching { file.close() }
            }
        }
        opened = silence
        return silence.open(
            dataSpec.buildUpon()
                .setPosition(dataSpec.position.coerceAtMost(SILENT_WAV.size.toLong()))
                .setLength(C.LENGTH_UNSET.toLong())
                .build(),
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        opened?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    /** The `speech://` URI, not the WAV behind it: it is the item ExoPlayer asked for. */
    override fun getUri(): Uri? = requested

    override fun close() {
        requested = null
        val current = opened
        opened = null
        current?.close()
    }

    @UnstableApi
    class Factory(private val speaker: Speaker) : DataSource.Factory {
        override fun createDataSource(): DataSource = SpeechDataSource(speaker)
    }

    private companion object {
        /** A tenth of a second of 16 kHz, 16-bit mono silence: a translation that cannot be read. */
        val SILENT_WAV: ByteArray = run {
            val rate = 16_000
            val bytesPerSample = 2
            val data = rate / 10 * bytesPerSample
            // Zero-filled by allocate(): everything after the 44-byte header is the silence.
            ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + data)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16) // the fmt chunk's size
                putShort(1) // PCM
                putShort(1) // mono
                putInt(rate)
                putInt(rate * bytesPerSample) // bytes per second
                putShort(bytesPerSample.toShort()) // block align
                putShort(16) // bits per sample
                put("data".toByteArray())
                putInt(data)
            }.array()
        }
    }
}
