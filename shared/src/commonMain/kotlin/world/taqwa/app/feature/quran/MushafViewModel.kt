package world.taqwa.app.feature.quran

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.AyahShareText
import world.taqwa.app.quran.LineType
import world.taqwa.app.quran.MushafPage
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.BookmarkStore
import world.taqwa.app.settings.SettingsRepository

sealed interface MushafUiState {
    data object Loading : MushafUiState

    data class Ready(
        /** The 1-based printed page the pager has settled on. */
        val currentPage: Int,
        /** The surah named in the header: the one the page's first *text* line starts in, which is
         * not always the page's own first surah — a page that opens with the previous surah's last
         * line still belongs to that surah while it is on screen. */
        val surah: Surah,
        val juz: Int,
        val settings: ReadingSettings,
        /** The reading-settings sheet's picker (spec §2.5), ordered exactly as the reader orders it. */
        val translations: List<TranslationInfo>,
        /** Al-Faatiha 1:2 with its marker — the sheet's live size preview, read from the database. */
        val previewAyah: String,
        /** Al-Faatiha 1:1, the text every `BASMALA` line draws — read from the database, never typed. */
        val basmala: String,
        /** Every surah by number, for the surah bands and the header; loaded once. */
        val surahsByNumber: Map<Int, Surah>,
        /** Every bookmark as `surah to ayah` (spec 2b §2.2), not just the current page's: a page
         * can hold the end of one surah and the start of the next, and the reference pill asks
         * about whichever ayah was tapped. */
        val bookmarked: Set<Pair<Int, Int>>,
    ) : MushafUiState
}

/** How long a page has to stay settled before its position is worth persisting (spec §2.4). */
private const val POSITION_DEBOUNCE_MS = 400L

/** How many decoded pages to keep. The pager holds one page either side of the current one
 * (`beyondViewportPageCount = 1`), so five covers a settle plus both neighbours in either
 * direction of travel without holding the whole Mushaf in memory. */
private const val PAGE_CACHE_SIZE = 5

/**
 * Mushaf mode (spec §2.4): the 604 printed pages, one per pager page. Shaped exactly like
 * [ReaderViewModel] — a plain class over a [MutableStateFlow] with [start] taking the caller's
 * scope — so it needs no platform ViewModel base class and stays testable under `runTest`.
 *
 * Page content is deliberately *not* part of [MushafUiState]: the pager composes several pages at
 * once and each asks for its own through [page], which caches the last [PAGE_CACHE_SIZE]. Only the
 * things the chrome around the pager needs — the header's surah and juz, the settings, the
 * basmala — live in the state.
 */
class MushafViewModel(
    private val source: QuranSource,
    private val settings: SettingsRepository,
    private val bookmarks: BookmarkStore,
    private val languageTag: String,
    startPage: Int,
) {
    private val _state = MutableStateFlow<MushafUiState>(MushafUiState.Loading)
    val state: StateFlow<MushafUiState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var positionJob: Job? = null

    private var currentPage: Int = startPage

    // Insertion-ordered, oldest first: a plain LinkedHashMap is what Kotlin's mutableMapOf gives
    // on every target, so eviction is "drop the first key" rather than an LruCache the common
    // source set does not have.
    private val pages = mutableMapOf<Int, MushafPage>()

    private var surahsByNumber: Map<Int, Surah>? = null
    private var basmalaText: String? = null
    private var previewAyahText: String? = null

    /** One surah's ayahs per entry, filled on demand: the pages themselves carry the words, so
     * this is only what the share text needs (spec 2b §2.5) — the tapped ayah's whole text in one
     * piece rather than the page's own broken lines. Al-Faatiha lands here from [applySettings]
     * too, so the basmala and a share of 1:1 never hit the database twice. */
    private val ayahsBySurah = mutableMapOf<Int, List<Ayah>>()

    /** The last set the bookmark collection saw, so an unrelated [applySettings] re-emission
     * carries the bookmarks forward instead of blanking them — [ReaderViewModel]'s own reason. */
    private var bookmarkedAyahs: Set<Pair<Int, Int>> = emptySet()

    /** Call once, from a `LaunchedEffect(viewModel) { viewModel.start(this) }`. */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            // Same filter as the reader's: this DataStore also receives the debounced reading
            // position, and without it every page turn would re-run applySettings.
            settings.readingSettings(languageTag).distinctUntilChanged().collect { applySettings(it) }
        }
        // A second collection rather than a combine, as in [ReaderViewModel.start]: a bookmark
        // toggle must not re-run applySettings and its translation-catalogue lookup.
        scope.launch {
            bookmarks.bookmarks.collect { list ->
                bookmarkedAyahs = list.map { it.surah to it.ayah }.toSet()
                _state.update { (it as? MushafUiState.Ready)?.copy(bookmarked = bookmarkedAyahs) ?: it }
            }
        }
    }

    private suspend fun applySettings(newSettings: ReadingSettings) {
        if (surahsByNumber == null) {
            surahsByNumber = source.surahs().associateBy { it.number }
            // Al-Faatiha's own ayahs: ayah 1 is every page's basmala line (spec §2.4), ayah 2 the
            // reading sheet's size preview (spec §2.5). Both from the database, never literals.
            val alFatiha = ayahsOf(1)
            basmalaText = alFatiha.first().text
            previewAyahText = QuranText.withMarker(alFatiha[1].text, 2)
        }
        val translations = orderForSheet(source.translations().filter { it.kind != TextKind.TRANSLITERATION })
        val page = page(currentPage)
        _state.value = MushafUiState.Ready(
            currentPage = currentPage,
            surah = surahOf(page),
            juz = page.juz,
            settings = newSettings,
            translations = translations,
            previewAyah = previewAyahText!!,
            basmala = basmalaText!!,
            surahsByNumber = surahsByNumber!!,
            bookmarked = bookmarkedAyahs,
        )
    }

    /** One surah's ayahs, cached. */
    private suspend fun ayahsOf(surah: Int): List<Ayah> =
        ayahsBySurah.getOrPut(surah) { source.ayahs(surah) }

    /** The page's lines, from the cache when it holds them. Every composed pager page calls this. */
    suspend fun page(number: Int): MushafPage {
        pages[number]?.let { return it }
        val loaded = source.page(number)
        if (pages.size >= PAGE_CACHE_SIZE) pages.remove(pages.keys.first())
        pages[number] = loaded
        return loaded
    }

    /**
     * The pager settled on [number] (spec §2.4): the header follows immediately, the last-read
     * position only after [POSITION_DEBOUNCE_MS], so flicking through twenty pages writes once.
     * Both live in the same cancellable [Job], so a page that has already been turned past never
     * gets to rename the header behind the page that replaced it.
     */
    fun onPageShown(number: Int) {
        val activeScope = scope ?: return
        currentPage = number
        positionJob?.cancel()
        positionJob = activeScope.launch {
            val page = page(number)
            (_state.value as? MushafUiState.Ready)?.let {
                _state.value = it.copy(currentPage = number, surah = surahOf(page), juz = page.juz)
            }
            delay(POSITION_DEBOUNCE_MS)
            val (surah, ayah) = firstAyahOf(page)
            settings.setReadingPosition(ReadingPosition(surah, ayah, number))
        }
    }

    /**
     * The reference pill's bookmark (spec 2b §2.5): writes through [BookmarkStore] and lets the
     * collection [start] began put the change back into [_state], so the glyph follows what was
     * actually stored rather than an optimistic flip. Takes the surah as well as the ayah because
     * the pill's ayah is whichever word was tapped, which need not be the header's surah.
     */
    fun toggleBookmark(surah: Int, ayah: Int) {
        scope?.launch { bookmarks.toggle(surah, ayah) }
    }

    /**
     * The one text the pill's copy and share both put out (spec 2b §2.3), or null for an ayah the
     * source does not have. Never carries a translation: the Mushaf shows the printed page alone,
     * whatever translation the reader is set to (spec 2b §2.5). Suspending where
     * [ReaderViewModel.shareTextFor] is not, because the reader already holds its surah's ayahs
     * and this screen holds pages — the tapped ayah's own text has to be fetched (once per surah).
     * [surahName] and [digits] come from the caller, which alone knows the UI's language.
     */
    suspend fun shareTextFor(surah: Int, ayah: Int, surahName: String, digits: (Int) -> String): String? {
        val found = ayahsOf(surah).firstOrNull { it.number == ayah } ?: return null
        return AyahShareText.format(
            arabic = found.text,
            ayahNumber = found.number,
            translation = null,
            surahName = surahName,
            surah = surah,
            ayah = found.number,
            digits = digits,
        )
    }

    /** Persists a change made in the reading-settings sheet; [start]'s own collection picks it
     * straight back up, exactly as in [ReaderViewModel.updateSettings]. */
    fun updateSettings(newSettings: ReadingSettings) {
        scope?.launch { settings.setReadingSettings(newSettings) }
    }

    /**
     * The mode toggle's Mushaf-to-translation half (spec §2.2): stores translation as the reading
     * mode and answers with the surah and ayah of the current page's first text line. Returns a
     * plain pair rather than a `Screen.Reader`, because — like every other view model in the app —
     * this one never imports `nav`; App.kt, which does, builds the screen from it.
     */
    suspend fun switchToReader(): Pair<Int, Int> {
        (_state.value as? MushafUiState.Ready)?.let {
            settings.setReadingSettings(it.settings.copy(mode = ReadingMode.TRANSLATION))
        }
        return firstAyahOf(page(currentPage))
    }

    /** The page's first text line's own ayah — what both the header and the last-read position
     * are keyed on (spec §2.4), falling back to the page row's when a page somehow has no text
     * line at all. */
    private fun firstAyahOf(page: MushafPage): Pair<Int, Int> {
        val line = page.lines.firstOrNull { it.type == LineType.TEXT }
        return (line?.firstSurah ?: page.firstSurah) to (line?.firstAyah ?: page.firstAyah)
    }

    private fun surahOf(page: MushafPage): Surah {
        val number = firstAyahOf(page).first
        return surahsByNumber?.get(number) ?: error("surah $number missing from the surah table")
    }
}
