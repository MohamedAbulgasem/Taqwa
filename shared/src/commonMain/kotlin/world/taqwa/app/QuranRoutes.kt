package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import world.taqwa.app.di.AppContainer
import world.taqwa.app.feature.quran.MushafScreen
import world.taqwa.app.feature.quran.MushafUiState
import world.taqwa.app.feature.quran.MushafViewModel
import world.taqwa.app.feature.quran.QuranRootScreen
import world.taqwa.app.feature.quran.QuranRootUiState
import world.taqwa.app.feature.quran.QuranRootViewModel
import world.taqwa.app.feature.quran.ReaderScreen
import world.taqwa.app.feature.quran.ReaderUiState
import world.taqwa.app.feature.quran.ReaderViewModel
import world.taqwa.app.feature.recitation.BarState
import world.taqwa.app.feature.recitation.QuranRecitation
import world.taqwa.app.feature.recitation.RecitationController
import world.taqwa.app.feature.recitation.RecitationState
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.nav.LaunchRequests
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.quran.displayName
import world.taqwa.app.settings.SettingsRepository

@Composable
internal fun QuranRootRoute(
    container: AppContainer,
    settings: SettingsRepository,
    platformFormat: PlatformFormat,
    quranQueryState: MutableState<TextFieldValue>,
    navigator: Navigator,
) {
    var quranQuery by quranQueryState
    val viewModel = remember {
        QuranRootViewModel(
            source = container.quranRepository,
            settings = settings,
            bookmarks = container.bookmarkStore,
            languageTag = platformFormat.languageTag(),
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.load()
        viewModel.start(this)
        // The field outlived the screen (see quranQuery); the view model
        // did not, so the restored query is searched again on the way back.
        // start() first: setFilter needs the scope to run its search.
        // immediate: nobody is typing, so the debounce would only leave the
        // ayah section blank for a quarter of a second on the way back.
        if (quranQuery.text.isNotEmpty()) {
            viewModel.setFilter(quranQuery.text, immediate = true)
        }
    }
    // The debug harnesses' typed search (see LaunchRequests.search).
    LaunchedEffect(viewModel) {
        LaunchRequests.pendingSearch.collect { asked ->
            asked ?: return@collect
            viewModel.state.first { it is QuranRootUiState.Ready }
            quranQuery = TextFieldValue(asked)
            viewModel.setFilter(asked, immediate = true)
            LaunchRequests.consumeSearch()
        }
    }
    val quranState by viewModel.state.collectAsState()
    QuranRootScreen(
        state = quranState,
        query = quranQuery,
        onQueryChange = { value ->
            // Only when the text itself changed: a TextFieldValue also
            // changes on a caret move or a selection, and setFilter
            // cancels the running search and re-queries, which would blank
            // the ayah section for the debounce every time the field is
            // tapped.
            val changed = value.text != quranQuery.text
            quranQuery = value
            if (changed) viewModel.setFilter(value.text)
        },
        onTabChange = viewModel::setTab,
        pageFor = viewModel::pageFor,
        onOpenReader = { surah, ayah -> navigator.push(Screen.Reader(surah, ayah)) },
        onOpenMushaf = { page -> navigator.push(Screen.Mushaf(page)) },
        onRemoveBookmark = viewModel::removeBookmark,
    )
}

@Composable
internal fun ReaderRoute(
    screen: Screen.Reader,
    container: AppContainer,
    settings: SettingsRepository,
    platformFormat: PlatformFormat,
    navigator: Navigator,
    scope: CoroutineScope,
    layoutDirection: LayoutDirection,
    recitationStateState: State<RecitationState>,
    bar: BarState?,
    barSpace: Dp,
    jumpTokenState: State<Int>,
    recitation: RecitationController,
) {
    val recitationState by recitationStateState
    val jumpToken by jumpTokenState
    val viewModel = remember(screen) {
        ReaderViewModel(
            source = container.quranRepository,
            settings = settings,
            bookmarks = container.bookmarkStore,
            languageTag = platformFormat.languageTag(),
            surah = screen.surah,
        )
    }
    LaunchedEffect(viewModel) { viewModel.start(this) }
    val readerState by viewModel.state.collectAsState()
    ReaderScreen(
        state = readerState,
        initialAyah = screen.ayah,
        selectInitialAyah = screen.selectAyah,
        onBack = { navigator.pop() },
        onToggleMode = {
            scope.launch {
                val page = viewModel.switchToMushaf()
                navigator.replace(Screen.Mushaf(page))
            }
        },
        onChangeSettings = viewModel::updateSettings,
        onFirstVisibleAyah = viewModel::onFirstVisibleAyah,
        onOpenNextSurah = { next -> navigator.replace(Screen.Reader(next, 1)) },
        onToggleBookmark = viewModel::toggleBookmark,
        // The surah's name and the reference digits follow the UI's own
        // language (spec 2b §2.3), which only this layer knows — so the
        // view model is handed both rather than resolving them itself.
        shareTextFor = { ayah ->
            (readerState as? ReaderUiState.Ready)?.let { ready ->
                viewModel.shareTextFor(
                    ayah = ayah,
                    surahName = ready.surah.displayName(layoutDirection == LayoutDirection.Rtl),
                    digits = platformFormat::localizedDigits,
                )
            }
        },
        recitation = QuranRecitation(
            header = recitationState.header(screen.surah),
            playing = bar?.let { it.surah to it.ayah },
            live = bar?.playing == true,
            barSpace = barSpace,
            jumpToken = jumpToken,
            onHeader = recitation::onHeaderTap,
            onPlayAyah = recitation::requestPlay,
            onToggle = recitation::toggle,
        ),
    )
}

@Composable
internal fun MushafRoute(
    screen: Screen.Mushaf,
    container: AppContainer,
    settings: SettingsRepository,
    platformFormat: PlatformFormat,
    navigator: Navigator,
    scope: CoroutineScope,
    layoutDirection: LayoutDirection,
    recitationStateState: State<RecitationState>,
    bar: BarState?,
    barSpace: Dp,
    jumpTokenState: State<Int>,
    recitation: RecitationController,
) {
    val recitationState by recitationStateState
    val jumpToken by jumpTokenState
    val viewModel = remember(screen) {
        MushafViewModel(
            source = container.quranRepository,
            settings = settings,
            bookmarks = container.bookmarkStore,
            languageTag = platformFormat.languageTag(),
            startPage = screen.page,
        )
    }
    LaunchedEffect(viewModel) { viewModel.start(this) }
    val mushafState by viewModel.state.collectAsState()
    MushafScreen(
        state = mushafState,
        startPage = screen.page,
        initialHighlight = screen.highlightSurah?.let { sur ->
            screen.highlightAyah?.let { a -> sur to a }
        },
        pageLoader = viewModel::page,
        onBack = { navigator.pop() },
        onToggleMode = {
            scope.launch {
                val (surah, ayah) = viewModel.switchToReader()
                navigator.replace(Screen.Reader(surah, ayah))
            }
        },
        onChangeSettings = viewModel::updateSettings,
        onPageShown = viewModel::onPageShown,
        onToggleBookmark = viewModel::toggleBookmark,
        // As in the reader's branch: the surah's name and the reference
        // digits are the UI language's business, so this layer resolves
        // them. A page can straddle two surahs, so the name is looked up
        // by the tapped ayah's own surah, never the header's.
        shareTextFor = { surah, ayah ->
            (mushafState as? MushafUiState.Ready)?.surahsByNumber?.get(surah)?.let { named ->
                viewModel.shareTextFor(
                    surah = surah,
                    ayah = ayah,
                    surahName = named.displayName(layoutDirection == LayoutDirection.Rtl),
                    digits = platformFormat::localizedDigits,
                )
            }
        },
        recitation = QuranRecitation(
            // The page's own surah, which is the one the header names.
            // Whether the voice is on *this page* is a question only the
            // Mushaf can answer — it knows which page the recited ayah is
            // printed on — so it is the screen that raises this to
            // Playing when the ayah is here.
            header = recitationState.header(
                (mushafState as? MushafUiState.Ready)?.surah?.number ?: 0,
            ),
            playing = bar?.let { it.surah to it.ayah },
            live = bar?.playing == true,
            barSpace = barSpace,
            jumpToken = jumpToken,
            onHeader = recitation::onHeaderTap,
            onPlayAyah = recitation::requestPlay,
            onToggle = recitation::toggle,
        ),
        pageOfAyah = { surah, ayah -> container.quranRepository.pageOf(surah, ayah) },
    )
}
