package world.taqwa.app.quran

/** Whether the reader shows translation text (with the Arabic above each verse) or the bare Mushaf page. */
enum class ReadingMode { TRANSLATION, MUSHAF }

/**
 * The reader's persisted preferences (spec §3.5). [defaultsFor] derives the language-appropriate
 * starting point — an Arabic device opens straight into [ReadingMode.MUSHAF] with no translation
 * clutter, everyone else opens into [ReadingMode.TRANSLATION] with the closest bundled
 * translation for their language, falling back to English.
 */
data class ReadingSettings(
    val mode: ReadingMode = ReadingMode.TRANSLATION,
    val arabicSizeSp: Int = 28,
    val transliteration: Boolean = false,
    val translationId: String = "en.sahih",
) {
    /** Keeps [arabicSizeSp] inside the reading-settings sheet's slider range. */
    fun clamped() = copy(arabicSizeSp = arabicSizeSp.coerceIn(MIN_SIZE, MAX_SIZE))

    companion object {
        const val MIN_SIZE = 22
        const val MAX_SIZE = 40
        const val SIZE_STEP = 2

        private val byLanguage = mapOf(
            "en" to "en.sahih",
            "ar" to "ar.muyassar",
            "id" to "id.indonesian",
            "ur" to "ur.junagarhi",
            "bn" to "bn.bengali",
            "tr" to "tr.diyanet",
            "fr" to "fr.hamidullah",
        )

        fun defaultsFor(languageTag: String): ReadingSettings {
            val lang = languageTag.substringBefore('-').lowercase()
            return ReadingSettings(
                mode = if (lang == "ar") ReadingMode.MUSHAF else ReadingMode.TRANSLATION,
                translationId = byLanguage[lang] ?: "en.sahih",
            )
        }
    }
}

/** The reader's last-read location, so reopening the app returns to where the user left off. */
data class ReadingPosition(val surah: Int, val ayah: Int, val page: Int)
