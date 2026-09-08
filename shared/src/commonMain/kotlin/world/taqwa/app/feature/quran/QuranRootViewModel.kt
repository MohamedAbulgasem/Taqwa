package world.taqwa.app.feature.quran

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.Surah
import world.taqwa.app.settings.SettingsRepository

/** Which of the two lists the tab root is showing (spec §2.1 point 3). */
enum class RootTab { SURAH, JUZ }

/**
 * One row of the Juz list (spec §2.1 point 5): a juz's start and end expressed as surah/ayah
 * pairs, computed from [world.taqwa.app.quran.Juz]'s bare start position by [computeJuzRows] —
 * the database has no "end" column, so every juz's end is the ayah immediately before the next
 * juz's start, and the last juz ends at the last ayah of the last surah.
 */
data class JuzRow(
    val number: Int,
    val startSurah: Surah,
    val startAyah: Int,
    val endSurah: Surah,
    val endAyah: Int,
)

/** The "Continue reading" card (spec §2.1 point 2): the last-read position, plus the juz it falls in. */
data class ContinueCard(val surah: Surah, val ayah: Int, val juz: Int)

sealed interface QuranRootUiState {
    data object Loading : QuranRootUiState

    data class Ready(
        val surahs: List<Surah>,
        val juzs: List<JuzRow>,
        val continueCard: ContinueCard?,
        val filter: String,
        val tab: RootTab,
        val mode: ReadingMode,
    ) : QuranRootUiState {
        /**
         * [surahs] narrowed by [filter] (spec §2.1 point 1): case-insensitive against the English
         * meaning and the surah number, harakat-insensitive against the Arabic name via
         * [QuranText.normaliseForSearch], and against the Latin name and every alias with
         * [collapseLatin] applied to both sides, so "mursalat", "Al-Mursalaat", "Yaseen" and
         * "baqara" all find their surah however the name is spelled.
         */
        val filteredSurahs: List<Surah> get() {
            val query = filter.trim()
            if (query.isEmpty()) return surahs
            val queryLower = query.lowercase()
            val queryArabic = QuranText.normaliseForSearch(query)
            val queryCollapsed = query.collapseLatin()
            return surahs.filter { surah ->
                surah.meaning.lowercase().contains(queryLower) ||
                    surah.number.toString().contains(query) ||
                    QuranText.normaliseForSearch(surah.nameArabic).contains(queryArabic) ||
                    (queryCollapsed.isNotEmpty() && (listOf(surah.nameLatin) + surah.aliases).any {
                        it.collapseLatin().contains(queryCollapsed)
                    })
            }
        }
    }
}

/**
 * Folds a Latin surah name to what people actually type: lower case, letters and digits only (no
 * hyphens, apostrophes or spaces), doubled vowels collapsed ("Faatiha" → "fatiha") and a final
 * "h" dropped ("Fatihah" → "fatiha", "Baqarah" → "baqara"), so the same query matches every
 * common spelling. Applied to both the query and the names, never shown.
 */
internal fun String.collapseLatin(): String {
    val letters = lowercase().filter { it.isLetterOrDigit() }
    val folded = StringBuilder(letters.length)
    letters.forEach { c -> if (!(c in "aeiou" && folded.endsWith(c))) folded.append(c) }
    return folded.toString().removeSuffix("h")
}

/**
 * Every juz's end, one ayah before [nextStartSurah]:[nextStartAyah] — within the same surah when
 * that start is not ayah 1, or the last ayah of the previous surah when it is.
 */
private fun ayahBefore(nextStartSurah: Int, nextStartAyah: Int, surahsByNumber: Map<Int, Surah>): Pair<Int, Int> =
    if (nextStartAyah > 1) {
        nextStartSurah to (nextStartAyah - 1)
    } else {
        val previous = surahsByNumber.getValue(nextStartSurah - 1)
        previous.number to previous.ayahCount
    }

/** Builds the Juz list's rows (spec §2.1 point 5) from the database's bare juz starts. */
internal fun computeJuzRows(juzs: List<world.taqwa.app.quran.Juz>, surahs: List<Surah>): List<JuzRow> {
    val byNumber = surahs.associateBy { it.number }
    val sorted = juzs.sortedBy { it.number }
    val lastSurah = surahs.maxByOrNull { it.number }
    return sorted.mapIndexed { index, juz ->
        val next = sorted.getOrNull(index + 1)
        val (endSurahNumber, endAyah) = if (next != null) {
            ayahBefore(next.startSurah, next.startAyah, byNumber)
        } else {
            requireNotNull(lastSurah) { "computeJuzRows needs at least one surah" }
            lastSurah.number to lastSurah.ayahCount
        }
        JuzRow(
            number = juz.number,
            startSurah = byNumber.getValue(juz.startSurah),
            startAyah = juz.startAyah,
            endSurah = byNumber.getValue(endSurahNumber),
            endAyah = endAyah,
        )
    }
}

/** The juz a given surah/ayah falls in, by the last juz row whose start is at or before it. */
private fun juzNumberFor(surah: Int, ayah: Int, juzRows: List<JuzRow>): Int {
    val containing = juzRows.lastOrNull { row ->
        row.startSurah.number < surah || (row.startSurah.number == surah && row.startAyah <= ayah)
    }
    return containing?.number ?: juzRows.firstOrNull()?.number ?: 1
}

/**
 * The Quran tab root (spec §2.1): the surah and juz lists, the current filter and tab, and the
 * continue-reading card when a last-read position exists. Follows [world.taqwa.app.feature.today.TodayViewModel]'s
 * shape — a plain class over a [MutableStateFlow], no platform ViewModel base class — so it is
 * usable, and testable, from `commonMain`/`commonTest` alike.
 *
 * Deliberately does not know about [world.taqwa.app.nav.Screen]: like every other view model in
 * this app, navigation is the caller's job. [pageFor] only resolves the Mushaf page a tap should
 * open — the composable decides, from [QuranRootUiState.Ready.mode], whether to call that or open
 * the reader directly.
 */
class QuranRootViewModel(
    private val source: QuranSource,
    private val settings: SettingsRepository,
    private val languageTag: String,
) {
    private val _state = MutableStateFlow<QuranRootUiState>(QuranRootUiState.Loading)
    val state: StateFlow<QuranRootUiState> = _state.asStateFlow()

    suspend fun load() {
        val surahs = source.surahs()
        val byNumber = surahs.associateBy { it.number }
        val juzRows = computeJuzRows(source.juzs(), surahs)
        val mode = settings.readingSettings(languageTag).first().mode
        val position = settings.readingPosition.first()
        val continueCard = position?.let { pos ->
            byNumber[pos.surah]?.let { surah ->
                ContinueCard(surah, pos.ayah, juzNumberFor(pos.surah, pos.ayah, juzRows))
            }
        }
        val previous = _state.value as? QuranRootUiState.Ready
        _state.value = QuranRootUiState.Ready(
            surahs = surahs,
            juzs = juzRows,
            continueCard = continueCard,
            filter = previous?.filter ?: "",
            tab = previous?.tab ?: RootTab.SURAH,
            mode = mode,
        )
    }

    fun setFilter(text: String) {
        (_state.value as? QuranRootUiState.Ready)?.let { _state.value = it.copy(filter = text) }
    }

    fun setTab(tab: RootTab) {
        (_state.value as? QuranRootUiState.Ready)?.let { _state.value = it.copy(tab = tab) }
    }

    /** Resolves the Mushaf page for a surah/ayah, for the composable to call before navigating in Mushaf mode. */
    suspend fun pageFor(surah: Int, ayah: Int): Int = source.pageOf(surah, ayah)
}
