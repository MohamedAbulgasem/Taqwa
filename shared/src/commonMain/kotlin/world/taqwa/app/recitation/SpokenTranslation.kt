package world.taqwa.app.recitation

import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.TextKind

/**
 * The phone voice that reads a language (read-aloud spec §4): Android's engine package and
 * `Voice.name`, or iOS's voice identifier with an empty [engine].
 */
data class SpeechVoice(val engine: String, val id: String)

/** What this phone can do for one language (read-aloud spec §4). Asked of it, never guessed. */
sealed interface VoiceStatus {
    /** An offline voice is on the phone. */
    data class Ready(val voice: SpeechVoice) : VoiceStatus

    /** Android only: [engine] offers the language, but its voice is not downloaded yet. */
    data class Missing(val engine: String) : VoiceStatus

    /** Nothing on this phone can read the language offline; the switch is hidden. */
    data object Unsupported : VoiceStatus
}

/** The platform's voices. */
interface SpeechVoices {
    suspend fun status(language: String): VoiceStatus

    /** Opens [engine]'s own voice installer (Android). Nothing on iOS. */
    fun installVoice(engine: String)
}

expect fun createSpeechVoices(): SpeechVoices

/**
 * A surah's translation as the player reads it (read-aloud spec §5.3). [texts] are already
 * prepared by [SpeechText.prepare] and keyed by the ayah they are read after.
 */
data class SpokenTranslation(
    val translationId: String,
    val kind: TextKind,
    val language: String,
    val voice: SpeechVoice,
    val texts: Map<Int, String>,
) {
    /** The ayahs a translation is read after, for [RecitationQueue]. */
    val spoken: Set<Int> get() = texts.keys

    /** The surah clock's slot for the translation read after ayah [n]; zero when there is none. */
    fun estimateMs(n: Int): Long = texts[n]?.let { SpeechText.estimateMs(it, language) } ?: 0L
}

/**
 * [translationId] read aloud over [surah], or null when this phone has no voice for its language
 * or there is nothing to read. For the debug harnesses; the controller builds its own with the
 * setting and a cached status.
 */
suspend fun buildSpokenTranslation(
    quran: QuranSource,
    voices: SpeechVoices,
    translationId: String,
    surah: Int,
): SpokenTranslation? {
    val info = quran.translations().firstOrNull { it.id == translationId } ?: return null
    val voice = (voices.status(info.language) as? VoiceStatus.Ready)?.voice ?: return null
    val texts = SpeechText.prepare(info.kind, info.language, surah, quran.translationTexts(translationId, surah))
    if (texts.isEmpty()) return null
    return SpokenTranslation(info.id, info.kind, info.language, voice, texts)
}
