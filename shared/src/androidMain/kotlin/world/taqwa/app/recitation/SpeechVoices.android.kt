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
 */
internal class AndroidSpeechVoices : SpeechVoices {

    override suspend fun status(language: String): VoiceStatus = withContext(Dispatchers.Main) {
        var missing: String? = null
        for (engine in engines()) {
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

    /** Installed engines, the user's default first and Google's second. */
    private fun engines(): List<String> {
        val installed = appContext.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
        val preferred = Settings.Secure.getString(appContext.contentResolver, DEFAULT_ENGINE)
        return (listOfNotNull(preferred, GOOGLE_ENGINE) + installed).distinct().filter { it in installed }
    }

    private suspend fun ask(engine: String, language: String): VoiceStatus {
        val tts = bind(engine) ?: return VoiceStatus.Unsupported
        return try {
            val candidates = runCatching { tts.voices.orEmpty().map { it.candidate() } }.getOrDefault(emptyList())
            val best = VoicePick.best(language, candidates)
            when {
                best != null -> VoiceStatus.Ready(SpeechVoice(engine, best.id))
                VoicePick.downloadable(language, candidates) -> VoiceStatus.Missing(engine)
                runCatching { tts.isLanguageAvailable(Locale(language)) }.getOrNull() == TextToSpeech.LANG_MISSING_DATA ->
                    VoiceStatus.Missing(engine)
                else -> VoiceStatus.Unsupported
            }
        } finally {
            tts.shutdown()
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
                    bound?.shutdown()
                    continuation.resume(null)
                }
            }, engine)
            continuation.invokeOnCancellation { tts?.shutdown() }
        }
    }

    private fun Voice.candidate() = VoiceCandidate(
        id = name,
        language = locale.language,
        country = locale.country,
        offline = !isNetworkConnectionRequired,
        installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty(),
        quality = quality,
    )

    private companion object {
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val DEFAULT_ENGINE = "tts_default_synth"
        const val BIND_TIMEOUT_MS = 4_000L
    }
}
