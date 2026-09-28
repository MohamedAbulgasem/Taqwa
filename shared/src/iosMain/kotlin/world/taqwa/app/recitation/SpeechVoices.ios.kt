package world.taqwa.app.recitation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVSpeechSynthesisVoice

actual fun createSpeechVoices(): SpeechVoices = IosSpeechVoices()

/**
 * Apple's voices on this phone (read-aloud spec §4). There is no Missing on iOS: its voice
 * downloads live in the Settings app, which cannot be linked to, so a language without an
 * installed voice is hidden and the switch appears once the voice is there.
 *
 * Constructing it asks nothing of the phone: the app container builds one at start-up, and the
 * voice list is only read when a screen asks about a language.
 */
internal class IosSpeechVoices : SpeechVoices {

    /**
     * On the main thread, like everything else in this app that touches AVFoundation: Apple
     * documents no thread for the voice list, and the main thread is the one every other speech
     * call in the player is made on. It is cheap enough there: on the iOS 26.2 simulator the list
     * took 97 ms the first time a process asked and 8 ms after, and a whole status 8 ms.
     */
    override suspend fun status(language: String): VoiceStatus = withContext(Dispatchers.Main) {
        val candidates = AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .filterNot { excluded(it.identifier) }
            .map { voice ->
                val tag = voice.language
                VoiceCandidate(
                    id = voice.identifier,
                    language = tag.substringBefore('-'),
                    country = tag.substringAfter('-', ""),
                    offline = true,
                    installed = true,
                    // `AVSpeechSynthesisVoiceQuality` arrives as its NSInteger: default 1,
                    // enhanced 2, premium 3. Apple's own voices go ahead of the Eloquence set,
                    // then Apple's quality order.
                    quality = voice.quality.toInt() + if (voice.identifier.startsWith("com.apple.voice.")) 10 else 0,
                )
            }
        VoicePick.best(language, candidates)?.let { VoiceStatus.Ready(SpeechVoice("", it.id)) } ?: VoiceStatus.Unsupported
    }

    /** Nothing to open: see the class comment. */
    override fun installVoice(engine: String) = Unit

    /**
     * Novelty voices ("Bells", "Bubbles", "Zarvox", all `com.apple.speech.synthesis.voice.*`) and
     * the user's personal voice are not for scripture.
     */
    private fun excluded(id: String): Boolean =
        ".speech.synthesis.voice." in id || "personalvoice" in id.lowercase()
}
