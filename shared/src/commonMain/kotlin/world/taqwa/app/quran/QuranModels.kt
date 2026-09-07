package world.taqwa.app.quran

enum class Revelation { MAKKI, MADANI }

data class Surah(
    val number: Int, val nameArabic: String, val nameLatin: String, val meaning: String,
    val revelation: Revelation, val ayahCount: Int, val startPage: Int, val startJuz: Int,
)

data class Ayah(val surah: Int, val number: Int, val text: String, val page: Int, val juz: Int, val hizbQuarter: Int, val sajdah: Int)

data class Juz(val number: Int, val startSurah: Int, val startAyah: Int)

enum class TextKind { TRANSLATION, TAFSIR, TRANSLITERATION }

data class TranslationInfo(val id: String, val language: String, val name: String, val translator: String, val licence: String, val sourceUrl: String, val kind: TextKind)

enum class LineType { SURAH, BASMALA, TEXT }

data class LineWord(val position: Int, val surah: Int, val ayah: Int, val word: Int, val text: String)

data class MushafLine(
    val line: Int, val type: LineType, val surah: Int?, val text: String?,
    val firstSurah: Int?, val firstAyah: Int?, val lastSurah: Int?, val lastAyah: Int?,
    val endsSurah: Boolean, val words: List<LineWord>,
)

data class MushafPage(val number: Int, val firstSurah: Int, val firstAyah: Int, val juz: Int, val lines: List<MushafLine>)
