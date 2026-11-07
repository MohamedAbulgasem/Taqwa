package world.taqwa.app.quran

enum class Revelation { MAKKI, MADANI }

data class Surah(
    val number: Int, val nameArabic: String, val nameLatin: String, val meaning: String,
    val revelation: Revelation, val ayahCount: Int, val startPage: Int, val startJuz: Int,
    /** Other Latin spellings the surah is known by (Tanzil's own, and common names such as
     * "Yaseen"); never shown, only matched by the Quran root's filter. */
    val aliases: List<String> = emptyList(),
)

/**
 * Which of [Surah.nameArabic] and [Surah.nameLatin] a caller should show, given whether the UI
 * itself is Arabic (spec §5.3's "Arabic names under an Arabic UI" rule) — a pure function so the
 * choice is unit-testable without a composable, unlike the isRtlLocale() check that feeds it.
 * The Arabic branch still requires [world.taqwa.app.design.mushafFamily] at the call site: this
 * only picks the string, never renders it.
 */
fun Surah.displayName(rtl: Boolean): String = if (rtl) nameArabic else nameLatin

/**
 * One ayah. [followedBy] is what is read straight after it, which the Hafs face's drawing needs for
 * the ayah's last tanwin (HafsSpelling R4): the next ayah's text, or at a surah's end
 * [world.taqwa.app.widget.HafsSpelling.afterLastAyahOf]. [QuranRepository] fills it; null after 114:6.
 */
data class Ayah(
    val surah: Int, val number: Int, val text: String, val page: Int, val juz: Int, val hizbQuarter: Int, val sajdah: Int,
    val followedBy: String? = null,
)

/** One search result (spec 2b §2.1): the ayah's own Arabic and, for a translation hit, the
 * translation text the match was found in. */
data class SearchHit(
    val surah: Int,
    val ayah: Int,
    val arabic: String,
    val translation: String?,
    /** The words of [arabic], by position, that an Arabic query found; empty for a translation hit. */
    val matchedWords: Set<Int> = emptySet(),
    /** What is read straight after the ayah, for its drawing ([Ayah.followedBy]). */
    val followedBy: String? = null,
)

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

/**
 * One printed page. [followedBy] is what is read straight after its last line — the next page's
 * first basmala or text line, null after page 604 — so the page's last word draws as it does in its
 * ayah ([Ayah.followedBy]).
 */
data class MushafPage(
    val number: Int, val firstSurah: Int, val firstAyah: Int, val juz: Int, val lines: List<MushafLine>,
    val followedBy: String? = null,
)
