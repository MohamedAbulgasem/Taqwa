package world.taqwa.app.quran

/**
 * The one text copy and share both produce (spec 2b §2.3). The ayah number goes in ornate
 * brackets rather than as bare digits: the roundel is a font feature of ours, and bare digits
 * after the text read as the next ayah's start wherever the text is pasted.
 */
object AyahShareText {
    private const val OPEN = "﴿"
    private const val CLOSE = "﴾"

    fun format(
        arabic: String,
        ayahNumber: Int,
        /** Translation text to translation name, or null when the reader shows no translation. */
        translation: Pair<String, String>?,
        surahName: String,
        surah: Int,
        ayah: Int,
        digits: (Int) -> String,
    ): String = buildString {
        append(arabic).append(' ').append(OPEN).append(QuranText.arabicIndic(ayahNumber)).append(CLOSE)
        if (translation != null) {
            append("\n\n").append(translation.first).append(" (").append(translation.second).append(')')
        }
        append("\n\n").append(surahName).append(' ').append(digits(surah)).append(':').append(digits(ayah))
    }
}
