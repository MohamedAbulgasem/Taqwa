package world.taqwa.app.recitation

/** Replaced by the real engine query in the platform task; until then no language is readable. */
actual fun createSpeechVoices(): SpeechVoices = object : SpeechVoices {
    override suspend fun status(language: String): VoiceStatus = VoiceStatus.Unsupported
    override fun installVoice(engine: String) = Unit
}
