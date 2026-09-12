package world.taqwa.app.feature.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.feature.recitation.BackToAyahPill
import world.taqwa.app.feature.recitation.Follow
import world.taqwa.app.feature.recitation.PillGap
import world.taqwa.app.feature.recitation.QuranRecitation
import world.taqwa.app.feature.recitation.rememberFollowing
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.quran.LineType
import world.taqwa.app.quran.MushafPage
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_juz_page
import world.taqwa.app.share.shareText
import world.taqwa.app.design.components.TaqwaBottomSheet

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
 *
 * [shareTextFor] is [MushafViewModel.shareTextFor] with the caller's own surah name and digits
 * already bound (spec 2b §2.3); it suspends because the tapped ayah's text is not on this screen,
 * so the pill's copy and share run it in [rememberCoroutineScope] and this screen — like the
 * reader — is what touches the clipboard and the platform share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafScreen(
    state: MushafUiState,
    startPage: Int,
    /** The ayah to open already highlighted, as a tap on the page would leave it; null when the
     * page itself is what was asked for. */
    initialHighlight: Pair<Int, Int>? = null,
    pageLoader: suspend (Int) -> MushafPage,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onChangeSettings: (ReadingSettings) -> Unit,
    onPageShown: (Int) -> Unit,
    onToggleBookmark: (Int, Int) -> Unit,
    shareTextFor: suspend (Int, Int) -> String?,
    /** The recitation surface (spec 3a §5). */
    recitation: QuranRecitation = QuranRecitation(),
    /** Which printed page an ayah is on, for following the voice across page turns. */
    pageOfAyah: suspend (Int, Int) -> Int = { _, _ -> 0 },
) {
    val colors = LocalTaqwaColors.current
    val arabic = isRtlLocale()
    val format = LocalPlatformFormat.current
    val ready = state as? MushafUiState.Ready
    var showSheet by remember { mutableStateOf(false) }
    // The tapped ayah (spec §2.4), hoisted here rather than per page so it survives a page turn
    // and so tapping the same ayah again clears it. The pill's three actions (spec 2b §2.5) act on
    // this ayah, which is why they are resolved here and not inside a page.
    var highlighted by remember { mutableStateOf(initialHighlight) }
    // LocalClipboardManager is deprecated in Compose MP 1.12, and its replacement takes a
    // ClipEntry with no common constructor — see [ReaderScreen]'s own note; the migration is one
    // expect/actual covering both screens, tracked as the clipboard follow-up in slice 2c.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    // The first ayah of the page on screen: where the header button starts playing from (spec
    // §5.1). Hoisted above the header because the header is drawn before the pager exists, and
    // filled in by the effect below as soon as the settled page's lines have loaded.
    var pageStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // safeDrawing, not systemBars: sideways the navigation bar and the camera cutout sit on the
    // left and right edges. The pager inside pads nothing of its own, so this is the only place
    // the page is inset and there is nothing here to double up with.
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
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
            recitation = recitation.header,
            onRecitation = {
                // The tapped ayah if there is one — it is the ayah the reader is looking at —
                // otherwise the first ayah printed on the page.
                val start = highlighted ?: pageStart
                if (start != null) recitation.onHeader(start.first, start.second)
            },
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
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { index ->
                pageStart = runCatching { pageLoader(index + 1) }.getOrNull()
                    ?.lines?.firstOrNull { it.type == LineType.TEXT }
                    ?.words?.firstOrNull()
                    ?.let { it.surah to it.ayah }
            }
        }

        // Following the voice across page turns (spec §5.3), under the same four-second rule the
        // reader's list uses — and the same refusal to move the page when the reciter is more
        // than a page away from where the reader actually is.
        val following = rememberFollowing()
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress }.collect { following.moved() }
        }
        val playing = recitation.playing
        val playingPage by produceState<Int?>(null, playing) {
            val reference = playing
            value = if (reference == null) null else runCatching { pageOfAyah(reference.first, reference.second) }.getOrNull()
        }
        LaunchedEffect(playingPage) {
            val page = playingPage
            if (page == null || page !in 1..MUSHAF_PAGES) {
                following.pill = null
                return@LaunchedEffect
            }
            val away = kotlin.math.abs(page - (pagerState.currentPage + 1))
            when (following.decide(away)) {
                Follow.SCROLL -> {
                    following.pill = null
                    if (away > 0) following.move { pagerState.animateScrollToPage(page - 1) }
                }
                Follow.PILL -> following.pill = playing?.second
                Follow.LEAVE_ALONE -> Unit
            }
        }
        LaunchedEffect(playingPage, pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { current ->
                if (playingPage == current + 1) following.pill = null
            }
        }

        Box(Modifier.weight(1f).padding(bottom = recitation.barSpace)) {
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
                    // Read into a val: `highlighted` is a delegated property, which Kotlin will
                    // not smart-cast, and the pill's actions all need the pair non-null.
                    val selected = highlighted
                    page?.let {
                        MushafPageView(
                            page = it,
                            surahOf = ready.surahsByNumber::get,
                            basmala = ready.basmala,
                            highlighted = highlighted,
                            bookmarked = selected != null && selected in ready.bookmarked,
                            onTapAyah = { surah, ayah ->
                                // A second tap on the same ayah clears it (spec §2.4).
                                highlighted = if (highlighted == surah to ayah) null else surah to ayah
                            },
                            onClearHighlight = { highlighted = null },
                            onBookmark = { selected?.let { (surah, ayah) -> onToggleBookmark(surah, ayah) } },
                            onCopy = {
                                selected?.let { (surah, ayah) ->
                                    scope.launch {
                                        shareTextFor(surah, ayah)?.let { clipboard.setText(AnnotatedString(it)) }
                                    }
                                }
                            },
                            onShare = {
                                selected?.let { (surah, ayah) ->
                                    scope.launch { shareTextFor(surah, ayah)?.let(::shareText) }
                                }
                            },
                            playing = recitation.playing,
                            playingLive = recitation.live,
                            onPlay = {
                                selected?.let { (surah, ayah) ->
                                    if (recitation.playing == surah to ayah) {
                                        recitation.onToggle()
                                    } else {
                                        recitation.onPlayAyah(surah, ayah)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            val pill = following.pill
            val target = playingPage
            if (pill != null && target != null) {
                BackToAyahPill(
                    ayah = pill,
                    onClick = {
                        following.rearm()
                        following.pill = null
                        scope.launch { following.move { pagerState.animateScrollToPage(target - 1) } }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PillGap),
                )
            }
        }

        if (showSheet) {
            TaqwaBottomSheet(onDismissRequest = { showSheet = false }) {
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
