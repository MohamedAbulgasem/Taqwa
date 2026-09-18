package world.taqwa.app.feature.quran

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.quran.SearchHit
import world.taqwa.app.quran.SearchQuery
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.Bookmark
import world.taqwa.app.settings.BookmarkStore
import world.taqwa.app.settings.SettingsRepository

/** How long the typing has to stop before an ayah search runs (spec 2b §2.1). */
private const val SEARCH_DEBOUNCE_MS = 250L

/** The most hits the search shows; one more than this is fetched, so "capped" is known rather
 * than guessed from a full page of results. */
private const val SEARCH_LIMIT = 100

/** The translation every unusable stored id falls back to, exactly as [ReaderViewModel] does. */
private const val FALLBACK_TRANSLATION = "en.sahih"

/** Which of the three lists the tab root is showing (spec §2.1 point 3, spec 2b §2.2). */
enum class RootTab { SURAH, JUZ, BOOKMARKS }

/**
 * What the search field is doing (spec 2b §2.1). [Idle] is both "nothing typed" and "too short to
 * search": either way the query is only narrowing the surah list, never the ayahs.
 */
sealed interface SearchState {
    data object Idle : SearchState

    /** Typing has not settled yet, or the query is out with the database. */
    data object Searching : SearchState

    /** [query] travels with the hits so a result that arrives late can be told apart from — and
     * the screen can highlight — the query it actually answers. */
    data class Results(val hits: List<SearchHit>, val capped: Boolean, val query: String) : SearchState
}

/**
 * One row of the Bookmarks tab (spec 2b §2.2): the stored [bookmark] resolved against the Quran
 * database, so the row can name its surah and show the ayah's own text without the screen having
 * to query anything itself.
 */
data class BookmarkRow(val bookmark: Bookmark, val surah: Surah, val arabic: String, val juz: Int, val page: Int)

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
        val search: SearchState = SearchState.Idle,
        val bookmarks: List<BookmarkRow> = emptyList(),
        /** The translation the ayah search actually runs against, resolved by
         * [QuranRootViewModel.load]. No default: a `Ready` built without one would silently claim
         * to be searching Saheeh International whatever the reader had chosen. */
        val translationId: String,
        /** [translationId]'s own language, for the direction a hit's translation snippet reads in
         * (spec §5.1) — an Urdu translation stays right-to-left under an English UI and an English
         * one left-to-right under an Arabic one. "en" until [QuranRootViewModel.load] resolves it. */
        val translationLanguage: String = "en",
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
 * The Quran tab root (spec §2.1, spec 2b §2.1-§2.2): the surah, juz and bookmark lists, the
 * current filter and tab, the debounced ayah search the filter also drives, and the
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
    private val bookmarks: BookmarkStore,
    private val languageTag: String,
) {
    private val _state = MutableStateFlow<QuranRootUiState>(QuranRootUiState.Loading)
    val state: StateFlow<QuranRootUiState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /** The translation the Latin search runs against, resolved once by [load]. */
    private var translationId: String = FALLBACK_TRANSLATION
    private var translationLanguage: String = "en"

    /** Ayah lists by surah, so a hundred bookmarks in one surah cost one query, not a hundred. */
    private val ayahsBySurah = mutableMapOf<Int, List<Ayah>>()

    /** The last list [start]'s collection saw, replayed by [load] in case the bookmarks arrived
     * before there was a surah list to resolve them against. */
    private var latestBookmarks: List<Bookmark> = emptyList()

    suspend fun load() {
        val surahs = source.surahs()
        val byNumber = surahs.associateBy { it.number }
        val juzRows = computeJuzRows(source.juzs(), surahs)
        val reading = settings.readingSettings(languageTag).first()
        val translations = source.translations()
        translationId = resolveTranslationId(reading.translationId, translations)
        // The same resolution ReaderViewModel does for its own cards: the catalogue's language for
        // the translation actually in use, "en" only if the row somehow has none.
        translationLanguage = translations.firstOrNull { it.id == translationId }?.language ?: "en"
        val position = settings.readingPosition.first()
        val continueCard = position?.let { pos ->
            byNumber[pos.surah]?.let { surah ->
                ContinueCard(surah, pos.ayah, juzNumberFor(pos.surah, pos.ayah, juzRows))
            }
        }
        // A reload keeps whatever the reader had already typed, chosen or been shown: everything
        // below comes from the freshly loaded database, everything from `previous` from the screen.
        _state.update { current ->
            val previous = current as? QuranRootUiState.Ready
            QuranRootUiState.Ready(
                surahs = surahs,
                juzs = juzRows,
                continueCard = continueCard,
                filter = previous?.filter ?: "",
                tab = previous?.tab ?: RootTab.SURAH,
                mode = reading.mode,
                search = previous?.search ?: SearchState.Idle,
                bookmarks = previous?.bookmarks.orEmpty(),
                translationId = translationId,
                translationLanguage = translationLanguage,
            )
        }
        applyBookmarks(latestBookmarks)
    }

    /**
     * The translation the search searches. "None" means the reader chose Arabic-only cards, and an
     * id this build no longer bundles was dropped between versions — neither can be searched, so
     * both fall back to Saheeh International, the same way [ReaderViewModel] resolves its own.
     */
    private fun resolveTranslationId(stored: String, translations: List<TranslationInfo>): String = when {
        stored == ReadingSettings.NO_TRANSLATION -> FALLBACK_TRANSLATION
        translations.any { it.id == stored } -> stored
        else -> FALLBACK_TRANSLATION
    }

    /**
     * Call once, from the same `LaunchedEffect` as [load] — the shape [ReaderViewModel.start]
     * uses. The scope collects the bookmarks for as long as the screen lives, and also carries the
     * debounced search job and [removeBookmark]'s write.
     */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch { bookmarks.bookmarks.collect { applyBookmarks(it) } }
    }

    /**
     * The filter drives both halves of the search (spec 2b §2.1): [QuranRootUiState.Ready.filteredSurahs]
     * narrows synchronously on every keystroke, while the ayah search waits [SEARCH_DEBOUNCE_MS]
     * and is cancelled outright by the next keystroke — like [ReaderViewModel.onFirstVisibleAyah],
     * relaunching one job means only the last query in a burst ever reaches the database.
     *
     * [immediate] skips that wait, for the one caller that is not a keystroke: the query restored
     * into a fresh view model on the way back from a hit (see `App.kt`). Nobody is typing then, so
     * there is nothing to debounce, and the wait would only blank the ayah section for a quarter
     * of a second on a screen that had results on it a moment ago.
     */
    fun setFilter(text: String, immediate: Boolean = false) {
        if (_state.value !is QuranRootUiState.Ready) return
        searchJob?.cancel()
        val activeScope = scope
        // Under two letters — or before start() handed over a scope — the query narrows the surah
        // list only, and no ayah search runs at all.
        if (!SearchQuery.isLongEnough(text) || activeScope == null) {
            _state.update { (it as? QuranRootUiState.Ready)?.copy(filter = text, search = SearchState.Idle) ?: it }
            return
        }
        _state.update { (it as? QuranRootUiState.Ready)?.copy(filter = text, search = SearchState.Searching) ?: it }
        searchJob = activeScope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            // Arabic in, Arabic searched: someone typing Arabic is quoting the Quran, not their
            // translation (spec 2b §2.1) — unless their translation is itself in the Arabic
            // script (Urdu, Farsi), in which case they are typing their own language: that
            // translation comes first and the Arabic text fills what is left.
            val hits = when {
                SearchQuery.searchesTranslationFirst(text, translationLanguage) -> {
                    val own = source.searchTranslation(translationId, text, SEARCH_LIMIT + 1)
                    if (own.size > SEARCH_LIMIT) {
                        own
                    } else {
                        val seen = own.map { it.surah to it.ayah }.toSet()
                        own + source.searchArabic(text, SEARCH_LIMIT + 1 - own.size)
                            .filter { (it.surah to it.ayah) !in seen }
                    }
                }
                SearchQuery.isArabic(text) -> source.searchArabic(text, SEARCH_LIMIT + 1)
                else -> source.searchTranslation(translationId, text, SEARCH_LIMIT + 1)
            }
                .let { withTranslations(it) }
            // A search that lost the race — the query moved on while the database was answering —
            // must never overwrite the newer query's state.
            _state.update { current ->
                if (current !is QuranRootUiState.Ready || current.filter != text) return@update current
                current.copy(
                    search = SearchState.Results(
                        hits = hits.take(SEARCH_LIMIT),
                        capped = hits.size > SEARCH_LIMIT,
                        query = text,
                    ),
                )
            }
        }
    }

    /**
     * An Arabic hit carries no translation of its own — the Arabic search reads the ayah table
     * alone — so the row said what was found and not what it means. The reader's translation is
     * filled in here, one read per surah touched (spec §17.3). Not under an Arabic interface:
     * the reader of the Arabic line needs no second line to tell them what it says.
     */
    private suspend fun withTranslations(hits: List<SearchHit>): List<SearchHit> {
        if (UiLanguage.of(languageTag) == UiLanguage.ARABIC || hits.none { it.translation == null }) return hits
        val bySurah = hits.filter { it.translation == null }.map { it.surah }.distinct()
            .associateWith { source.translationTexts(translationId, it) }
        return hits.map { hit ->
            if (hit.translation != null) hit else hit.copy(translation = bySurah[hit.surah]?.get(hit.ayah))
        }
    }

    /** Resolves each stored bookmark against the loaded surah list and its surah's ayahs, newest
     * first (the store's own order). A bookmark whose surah or ayah does not exist — a preference
     * file is user data — is dropped rather than failing the whole tab. */
    private suspend fun applyBookmarks(list: List<Bookmark>) {
        latestBookmarks = list
        val ready = _state.value as? QuranRootUiState.Ready ?: return
        val byNumber = ready.surahs.associateBy { it.number }
        val rows = list.mapNotNull { bookmark ->
            val surah = byNumber[bookmark.surah] ?: return@mapNotNull null
            val ayahs = ayahsBySurah.getOrPut(bookmark.surah) { source.ayahs(bookmark.surah) }
            val ayah = ayahs.firstOrNull { it.number == bookmark.ayah } ?: return@mapNotNull null
            BookmarkRow(bookmark, surah, ayah.text, ayah.juz, ayah.page)
        }
        _state.update { (it as? QuranRootUiState.Ready)?.copy(bookmarks = rows) ?: it }
    }

    /**
     * Removes a bookmark from the store; the collection [start] began is what updates the rows,
     * so this never touches the state itself. Precondition: [start] has been called — the write
     * runs on its scope, so a call before that silently does nothing. The screen only ever draws
     * a remove target once the rows this same scope collects have arrived, so there is no way to
     * tap one earlier.
     */
    fun removeBookmark(surah: Int, ayah: Int) {
        scope?.launch { bookmarks.remove(surah, ayah) }
    }

    fun setTab(tab: RootTab) {
        _state.update { (it as? QuranRootUiState.Ready)?.copy(tab = tab) ?: it }
    }

    /** Resolves the Mushaf page for a surah/ayah, for the composable to call before navigating in Mushaf mode. */
    suspend fun pageFor(surah: Int, ayah: Int): Int = source.pageOf(surah, ayah)
}
