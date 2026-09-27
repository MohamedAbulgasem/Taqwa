package world.taqwa.app.recitation

import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.settings.appContext
import java.util.Locale
import kotlin.coroutines.resume

actual fun createSpeechVoices(): SpeechVoices = AndroidSpeechVoices()

/**
 * The phone's text-to-speech engines, asked per language (read-aloud spec §4). Each engine is
 * bound only for the question and released at once: holding Google's engine for the life of the
 * process would keep its own process alive for nothing.
 *
 * The user's default engine is asked first, then Google's, then any other. The first with an
 * offline voice installed answers Ready; otherwise the first that offers the language as a
 * download answers Missing; otherwise the language is Unsupported and the switch stays hidden.
 *
 * **Threads.** An engine is bound on the main thread: making a `TextToSpeech` only starts the
 * bind, and its start-up callback arrives on the main looper whichever thread made it. Everything
 * then asked of it — and the list of engines itself — is a binder call into another process, one
 * the engine may still be starting, so it is asked on [Dispatchers.IO] and the screen that wanted
 * the answer never waits on it.
 */
internal class AndroidSpeechVoices : SpeechVoices {

    override suspend fun status(language: String): VoiceStatus = withContext(Dispatchers.Main) {
        var missing: String? = null
        for (engine in withContext(Dispatchers.IO) { engines() }) {
            when (val answer = ask(engine, language)) {
                is VoiceStatus.Ready -> return@withContext answer
                is VoiceStatus.Missing -> if (missing == null) missing = answer.engine
                VoiceStatus.Unsupported -> Unit
            }
        }
        missing?.let { VoiceStatus.Missing(it) } ?: VoiceStatus.Unsupported
    }

    override fun installVoice(engine: String) {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .setPackage(engine)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    /**
     * The voice as its engine lists it, with this class's own verdict beside the raw fields: the
     * debug harness logs it after each Ready, so the offline rule ([isOffline]) can be checked on
     * a real phone's voices. Bound and let go like [status], on the same threads.
     */
    override suspend fun describe(voice: SpeechVoice): String = withContext(Dispatchers.Main) {
        val tts = bind(voice.engine) ?: return@withContext "${voice.engine} did not start"
        try {
            withContext(Dispatchers.IO) {
                val listed = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
                    .firstOrNull { it.name == voice.id }
                    ?: return@withContext "${voice.id} is not listed by ${voice.engine}"
                "${listed.name} features=${listed.features.orEmpty().sorted()} " +
                    "networkRequired=${listed.isNetworkConnectionRequired} offline=${listed.isOffline()} " +
                    "quality=${listed.quality} latency=${listed.latency}"
            }
        } finally {
            tts.shutdownQuietly()
        }
    }

    /** Installed engines, the user's default first and Google's second. Binder calls: IO only. */
    private fun engines(): List<String> {
        val installed = appContext.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
        val preferred = Settings.Secure.getString(appContext.contentResolver, DEFAULT_ENGINE)
        return (listOfNotNull(preferred, GOOGLE_ENGINE) + installed).distinct().filter { it in installed }
    }

    /** Main thread: binds [engine], puts the question to it on [Dispatchers.IO], and lets it go. */
    private suspend fun ask(engine: String, language: String): VoiceStatus {
        val tts = bind(engine) ?: return VoiceStatus.Unsupported
        return try {
            withContext(Dispatchers.IO) { answer(tts, engine, language) }
        } finally {
            tts.shutdownQuietly()
        }
    }

    /** What [tts] can do for [language]. Each question is a binder call into the engine: IO only. */
    private fun answer(tts: TextToSpeech, engine: String, language: String): VoiceStatus {
        val candidates = runCatching { tts.voices.orEmpty().map { it.candidate() } }.getOrDefault(emptyList())
        val best = VoicePick.best(language, candidates)
        return when {
            best != null -> VoiceStatus.Ready(SpeechVoice(engine, best.id))
            VoicePick.downloadable(language, candidates) -> VoiceStatus.Missing(engine)
            runCatching { tts.isLanguageAvailable(Locale.forLanguageTag(language)) }.getOrNull() == TextToSpeech.LANG_MISSING_DATA ->
                VoiceStatus.Missing(engine)
            else -> VoiceStatus.Unsupported
        }
    }

    /** A bound engine, or null when it would not start within [BIND_TIMEOUT_MS]. Main thread. */
    private suspend fun bind(engine: String): TextToSpeech? = withTimeoutOrNull(BIND_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(appContext, { status ->
                val bound = tts
                if (!continuation.isActive) return@TextToSpeech
                if (status == TextToSpeech.SUCCESS && bound != null) {
                    continuation.resume(bound)
                } else {
                    bound?.shutdownQuietly()
                    continuation.resume(null)
                }
            }, engine)
            continuation.invokeOnCancellation { tts.shutdownQuietly() }
        }
    }

    private fun Voice.candidate() = VoiceCandidate(
        id = name,
        language = locale.language,
        country = locale.country,
        offline = isOffline(),
        installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty(),
        quality = quality,
    )

    private companion object {
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val DEFAULT_ENGINE = "tts_default_synth"
        const val BIND_TIMEOUT_MS = 4_000L
    }
}

/** True only for a voice that makes its speech on the phone: see [isOfflineVoice]. */
internal fun Voice.isOffline(): Boolean = isOfflineVoice(isNetworkConnectionRequired, features.orEmpty())

/**
 * True only for a voice that makes its speech on the phone (read-aloud spec §1: no text leaves
 * it): one that needs no connection by the engine's own word, and does not list network synthesis
 * among its [features].
 *
 * That is all a voice's features can be trusted to say. Google's engine lists its timeout and
 * retry settings (`networkTimeoutMs`, `networkRetriesCount`) on every voice, local ones included,
 * as measured on the emulator and the S23: `en-us-x-iob-local` carries both, exactly as its twin
 * `en-us-x-iob-network` and the `en-US-language` alias do. Counting those two as the mark of a
 * network voice ruled every voice out, and every language read as Unsupported. The `-network`
 * twins are the ones that raise `isNetworkConnectionRequired`. Of the voices left, `VoicePick`'s
 * `-local` tie-break then chooses an explicitly local one, such as `en-us-x-iob-local`, over the
 * `en-US-language` alias when the engine rates the two alike.
 */
internal fun isOfflineVoice(networkRequired: Boolean, features: Set<String>): Boolean =
    !networkRequired && NETWORK_SYNTHESIS !in features

/**
 * `TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS`, written out because the constant has been
 * deprecated since API 21 in favour of `isNetworkConnectionRequired`, while an engine may still
 * set the key on a voice that does not raise the flag.
 */
private const val NETWORK_SYNTHESIS = "networkTts"
