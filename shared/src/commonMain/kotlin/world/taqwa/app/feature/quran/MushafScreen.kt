package world.taqwa.app.feature.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.quran.MushafPage
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_juz_page

/** The Madinah Mushaf's own page count (spec §2.4) — the pager's whole extent. */
private const val MUSHAF_PAGES = 604

/**
 * Mushaf mode (spec §2.4): the reader header over a pager of the 604 printed pages. The pager is
 * wrapped in a right-to-left layout direction whatever the UI language is, so page N+1 always sits
 * to the left of page N — the next page sits to the left, so a swipe from left to right turns
 * forward, as in the book itself (spec §5.3).
 *
 * [pageLoader] is [MushafViewModel.page] — each composed pager page asks for its own lines rather
 * than the state carrying them, because the pager holds three pages at a time and the view model,
 * not this screen, owns the cache that makes that cheap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafScreen(
    state: MushafUiState,
    startPage: Int,
    pageLoader: suspend (Int) -> MushafPage,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onChangeSettings: (ReadingSettings) -> Unit,
    onPageShown: (Int) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val arabic = isRtlLocale()
    val format = LocalPlatformFormat.current
    val ready = state as? MushafUiState.Ready
    var showSheet by remember { mutableStateOf(false) }
    // The tapped ayah (spec §2.4), hoisted here rather than per page so it survives a page turn
    // and so tapping the same ayah again clears it. The action row this will grow is slice 2b's.
    var highlighted by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.systemBars)) {
        ReaderHeader(
            title = ready?.let { if (arabic) it.surah.nameArabic else it.surah.nameLatin } ?: "",
            caption = ready?.let {
                stringResource(
                    Res.string.quran_juz_page,
                    format.localizedDigits(it.juz),
                    format.localizedDigits(it.currentPage),
                )
            } ?: "",
            mushafSelected = true,
            onBack = onBack,
            onToggleMode = onToggleMode,
            onOpenSheet = { showSheet = true },
        )

        if (ready == null) return@Column

        val pagerState = rememberPagerState(initialPage = (startPage - 1).coerceIn(0, MUSHAF_PAGES - 1)) {
            MUSHAF_PAGES
        }
        LaunchedEffect(pagerState) {
            // settledPage, not currentPage: mid-swipe the header must not flicker between two
            // surahs, and the last-read position must only follow a page the reader stopped on.
            snapshotFlow { pagerState.settledPage }.collect { onPageShown(it + 1) }
        }

        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val number = index + 1
                    val page by produceState<MushafPage?>(initialValue = null, number) {
                        value = pageLoader(number)
                    }
                    page?.let {
                        MushafPageView(
                            page = it,
                            surahOf = ready.surahsByNumber::get,
                            basmala = ready.basmala,
                            highlighted = highlighted,
                            onTapAyah = { surah, ayah ->
                                // A second tap on the same ayah clears it (spec §2.4).
                                highlighted = if (highlighted == surah to ayah) null else surah to ayah
                            },
                            onClearHighlight = { highlighted = null },
                        )
                    }
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                containerColor = colors.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { SheetDragHandle() },
            ) {
                ReadingSheet(
                    settings = ready.settings,
                    translations = ready.translations,
                    previewAyah = ready.previewAyah,
                    // The size slider does not apply to a printed page (spec §2.4): the sheet
                    // shows it disabled with its own note.
                    mushafMode = true,
                    onChange = { newSettings ->
                        if (newSettings.mode == ReadingMode.TRANSLATION) {
                            // The mode toggle's own path already persists the mode and navigates,
                            // exactly as [ReaderScreen] does for the opposite direction.
                            showSheet = false
                            onToggleMode()
                        } else {
                            onChangeSettings(newSettings)
                        }
                    },
                    onDismiss = { showSheet = false },
                )
            }
        }
    }
}
