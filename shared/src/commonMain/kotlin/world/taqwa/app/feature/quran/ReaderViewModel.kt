package world.taqwa.app.feature.quran

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.SettingsRepository

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Ready(
        val surah: Surah,
        /** `ayahs(1).first().text` read from the database, never a literal (spec §2.3) — null for
         * Al-Faatiha (where it is ayah 1 already) and At-Tawbah (which has none). */
        val basmala: String?,
        val ayahs: List<Ayah>,
        val translation: Map<Int, String>,
        val transliteration: Map<Int, String>?,
        val settings: ReadingSettings,
        /** The next surah's card (spec §2.3), null after An-Nas (114). */
        val nextSurah: Surah?,
        val currentAyah: Int,
        /** Juz, then page — the caption's two numbers (spec §2.2), formatted by the screen. */
        val caption: Pair<Int, Int>,
        /** The language of [translation]'s current text, for the reading direction the translation
         * paragraph itself is drawn in (spec §5.1) — never the UI's own direction. */
        val translationLanguage: String,
        /** Ayah 1:2 with the ayah marker, read from the database like [basmala] — the reading-settings
         * sheet's (task 7) live size preview, never a literal. */
        val previewAyah: String,
        /** The reading-settings sheet's translation picker (task 7, spec §2.5): every bundled text
         * except the transliteration, tafsir first then the rest by language. */
        val translations: List<TranslationInfo>,
    ) : ReaderUiState
}

/** Surahs 1 (Al-Faatiha, where the basmala is ayah 1 itself) and 9 (At-Tawbah, which has none). */
private val SURAHS_WITHOUT_A_LEADING_BASMALA = setOf(1, 9)

/** How long a scroll has to sit still before its position is worth persisting (spec §2.3). */
private const val POSITION_DEBOUNCE_MS = 500L

/**
 * Translation mode's reader (spec §2.3): one surah, its ayahs as cards, the chosen translation and
 * optional transliteration, and the "next surah" footer. Follows [QuranRootViewModel] and
 * [world.taqwa.app.feature.qibla.QiblaViewModel]'s shape — a plain class over a
 * [MutableStateFlow], [start] taking the caller's [CoroutineScope] rather than owning one, so it
 * needs no platform ViewModel base class and stays testable under `runTest`.
 *
 * [start] both loads the surah once and subscribes to [SettingsRepository.readingSettings] for as
 * long as the caller's scope lives, so a change made in the reading-settings sheet (task 7) —
 * arabic size, transliteration, translation, mode — re-renders this screen without it doing
 * anything else. Only the parts that actually depend on the changed setting are re-fetched: the
 * surah, its ayahs, the basmala and the next surah are loaded once and cached; the translation
 * text is re-fetched only when [ReadingSettings.translationId] itself changes, and the
 * transliteration text only when [ReadingSettings.transliteration] flips on.
 */
class ReaderViewModel(
    private val source: QuranSource,
    private val settings: SettingsRepository,
    private val languageTag: String,
    private val surah: Int,
) {
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var positionJob: Job? = null

    // Loaded once per screen instance (the surah number never changes under it) and reused across
    // every settings update, so a slider tweak in the sheet does not re-hit the database for data
    // that has not changed.
    private var surahInfo: Surah? = null
    private var ayahList: List<Ayah>? = null
    private var basmalaText: String? = null
    private var nextSurahInfo: Surah? = null
    private var previewAyahText: String? = null

    private var loadedTranslationId: String? = null
    private var loadedTranslation: Map<Int, String> = emptyMap()
    private var transliterationLoaded = false
    private var loadedTransliteration: Map<Int, String>? = null

    /** Call once, from a `LaunchedEffect(viewModel) { viewModel.start(this) }` — the same shape as
     * [world.taqwa.app.feature.qibla.QiblaViewModel.start]. */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            // The same DataStore also receives every debounced reading position, so without the
            // filter each scroll stop re-ran applySettings and refetched the translations.
            settings.readingSettings(languageTag).distinctUntilChanged().collect { applySettings(it) }
        }
    }

    private suspend fun applySettings(newSettings: ReadingSettings) {
        if (surahInfo == null) {
            surahInfo = source.surah(surah)
            ayahList = source.ayahs(surah)
            nextSurahInfo = if (surah < 114) source.surah(surah + 1) else null
            // Al-Faatiha's own ayahs, fetched once regardless of which surah is open: ayah 1 for
            // the basmala (spec §2.3), ayah 2 for the reading-settings sheet's size preview
            // (task 7, spec §2.5) — never a literal, either way.
            val alFatiha = source.ayahs(1)
            basmalaText = if (surah in SURAHS_WITHOUT_A_LEADING_BASMALA) null else alFatiha.first().text
            previewAyahText = QuranText.withMarker(alFatiha[1].text, 2)
        }

        // A translation id the sheet stored that this build no longer bundles (e.g. dropped
        // between versions) falls back to Saheeh International rather than showing empty cards;
        // "none" is the one id that legitimately means empty cards (spec §2.5, translation off).
        val translations = source.translations()
        val translationId = when {
            newSettings.translationId == ReadingSettings.NO_TRANSLATION -> ReadingSettings.NO_TRANSLATION
            translations.any { it.id == newSettings.translationId } -> newSettings.translationId
            else -> "en.sahih"
        }
        if (translationId != loadedTranslationId) {
            loadedTranslation = if (translationId == ReadingSettings.NO_TRANSLATION) emptyMap() else source.translationTexts(translationId, surah)
            loadedTranslationId = translationId
        }

        if (newSettings.transliteration) {
            if (!transliterationLoaded) {
                loadedTransliteration = source.translationTexts("en.transliteration", surah)
                transliterationLoaded = true
            }
        } else {
            transliterationLoaded = false
            loadedTransliteration = null
        }

        val previous = _state.value as? ReaderUiState.Ready
        val ayahs = ayahList.orEmpty()
        val currentAyah = previous?.currentAyah ?: 1
        val ayah = ayahs.firstOrNull { it.number == currentAyah } ?: ayahs.firstOrNull()
        val sheetTranslations = orderForSheet(translations.filter { it.kind != TextKind.TRANSLITERATION })
        _state.value = ReaderUiState.Ready(
            surah = surahInfo!!,
            basmala = basmalaText,
            ayahs = ayahs,
            translation = loadedTranslation,
            transliteration = loadedTransliteration,
            settings = newSettings,
            nextSurah = nextSurahInfo,
            currentAyah = currentAyah,
            caption = (ayah?.juz ?: surahInfo!!.startJuz) to (ayah?.page ?: surahInfo!!.startPage),
            translationLanguage = translations.firstOrNull { it.id == translationId }?.language ?: "en",
            previewAyah = previewAyahText!!,
            translations = sheetTranslations,
        )
    }

    /**
     * Persists a change made in the reading-settings sheet (task 7): writes through
     * [SettingsRepository.setReadingSettings] on the scope handed to [start], and [applySettings]
     * picks the change straight back up through the same collection [start] already subscribes
     * to — this never touches [_state] directly, exactly like [switchToMushaf].
     */
    fun updateSettings(newSettings: ReadingSettings) {
        scope?.launch { settings.setReadingSettings(newSettings) }
    }

    /**
     * The mode toggle's translation-to-Mushaf half (spec §2.2): persists Mushaf as the reading
     * mode and resolves the page containing the current ayah, so the caller (App.kt, which alone
     * knows about [world.taqwa.app.nav.Navigator]) only has to call `Navigator.replace` with the
     * result — this view model, like every other one in the app, never imports `nav` itself.
     */
    suspend fun switchToMushaf(): Int {
        val ready = _state.value as? ReaderUiState.Ready
        if (ready != null) settings.setReadingSettings(ready.settings.copy(mode = ReadingMode.MUSHAF))
        return source.pageOf(surah, ready?.currentAyah ?: 1)
    }

    /**
     * The first ayah whose card top is on screen (spec §2.3): debounced 500 ms so a fast scroll
     * does not write the last-read position, or move the caption, once per frame. Cancelling and
     * relaunching the same [Job] — rather than tracking a timestamp — means only the last call in
     * a burst ever survives to write anything.
     */
    fun onFirstVisibleAyah(ayah: Int) {
        val activeScope = scope ?: return
        positionJob?.cancel()
        positionJob = activeScope.launch {
            delay(POSITION_DEBOUNCE_MS)
            val found = ayahList.orEmpty().firstOrNull { it.number == ayah } ?: return@launch
            settings.setReadingPosition(ReadingPosition(surah, found.number, found.page))
            (_state.value as? ReaderUiState.Ready)?.let {
                _state.value = it.copy(currentAyah = found.number, caption = found.juz to found.page)
            }
        }
    }
}
