package world.taqwa.app.quran

import world.taqwa.app.i18n.UiLanguage

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
        /** The [translationId] meaning "no translation": each ayah keeps its card, Arabic only. A
         * sentinel rather than an empty string so a blank preference can never be mistaken for it. */
        const val NO_TRANSLATION = "none"

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
            // UiLanguage folds Java's legacy "in" for Indonesian; an unknown language is English.
            val lang = UiLanguage.of(languageTag).code
            return ReadingSettings(
                mode = if (lang == "ar") ReadingMode.MUSHAF else ReadingMode.TRANSLATION,
                translationId = byLanguage[lang] ?: "en.sahih",
            )
        }
    }
}

/** The reader's last-read location, so reopening the app returns to where the user left off. */
data class ReadingPosition(val surah: Int, val ayah: Int, val page: Int)
