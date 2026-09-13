package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.contentWidth
import world.taqwa.app.feature.recitation.BackToAyahPill
import world.taqwa.app.feature.recitation.FOLLOW_VIEWPORT_FRACTION
import world.taqwa.app.feature.recitation.Follow
import world.taqwa.app.feature.recitation.PILL_SETTLE_MS
import world.taqwa.app.feature.recitation.PillGap
import world.taqwa.app.feature.recitation.QuranRecitation
import world.taqwa.app.feature.recitation.rememberFollowing
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.displayName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_juz_page
import world.taqwa.app.resources.quran_next_surah
import world.taqwa.app.share.shareText
import world.taqwa.app.design.components.TaqwaBottomSheet

/** The gutter between an ayah card and the edge of the reader's own content column. */
private val ReaderGutter = 24.dp

/**
 * Translation mode's reader (spec §2.3): the header, the basmala, one [AyahCard] per ayah, and the
 * "next surah" footer. [initialAyah] is [world.taqwa.app.nav.Screen.Reader]'s own ayah — the one a
 * tap on the tab root or the continue-reading card asked to open — and is only used once, to scroll
 * there; after that, [ReaderViewModel.onFirstVisibleAyah] tracks whatever the user actually scrolls
 * to, which is why it is a plain constructor-time value here rather than part of [state].
 *
 * [selectInitialAyah] additionally opens that ayah selected, as though the reader had arrived and
 * the user had tapped it: the ayah widget shows one ayah with its actions, so a tap on it lands on
 * the same ayah with the same actions rather than on a surah scrolled to roughly the right place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    initialAyah: Int,
    selectInitialAyah: Boolean = false,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onChangeSettings: (ReadingSettings) -> Unit,
    onFirstVisibleAyah: (Int) -> Unit,
    onOpenNextSurah: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    /** The copy/share text for one ayah (spec 2b §2.3), null before the surah has loaded — built
     * by the view model, but with the surah name and digits this screen's own locale decides. */
    shareTextFor: (Int) -> String?,
    /** The recitation surface (spec 3a §5): the header button, the ayah row's Play, the lit ayah
     * and the room the player bar takes at the foot. */
    recitation: QuranRecitation = QuranRecitation(),
) {
    val colors = LocalTaqwaColors.current
    val arabic = isRtlLocale()
    val format = LocalPlatformFormat.current
    val ready = state as? ReaderUiState.Ready
    // The reading-settings sheet (task 7, spec §2.5) is hosted here rather than by the caller: the
    // Aa button is this screen's own affordance, and the Mushaf screen (task 8) will host its own
    // instance of [ReadingSheet] the same way, since only each screen knows its own [mushafMode].
    var showSheet by remember { mutableStateOf(false) }
    // The clipboard and the share sheet are the screen's own business (spec 2b §2.3): the view
    // model only produces the text.
    // LocalClipboardManager is deprecated in Compose MP 1.12 in favour of LocalClipboard, but its
    // replacement takes a ClipEntry, which has no common constructor — only foundation's own
    // internal AnnotatedString.toClipEntry() builds a plain-text one. Migrating therefore needs an
    // expect/actual of our own on both targets; tracked as the clipboard follow-up in slice 2c.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    // Where the header button would start playing from: whatever ayah the reader has scrolled to
    // (spec §5.1). Tracked here rather than in the view model because the header is drawn before
    // the list exists and the first value has to be the ayah the screen was opened at.
    var headerAyah by remember(state) { mutableStateOf(initialAyah) }

    // safeDrawing, not systemBars: held sideways the navigation bar and the camera cutout move
    // to the left and right edges, and only safeDrawing reports those.
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ReaderHeader(
            title = ready?.let { if (arabic) it.surah.nameArabic else it.surah.nameLatin } ?: "",
            caption = ready?.let {
                stringResource(
                    Res.string.quran_juz_page,
                    format.localizedDigits(it.caption.first),
                    format.localizedDigits(it.caption.second),
                )
            } ?: "",
            mushafSelected = false,
            onBack = onBack,
            onToggleMode = onToggleMode,
            onOpenSheet = { showSheet = true },
            recitation = recitation.header,
            onRecitation = { ready?.let { recitation.onHeader(it.surah.number, headerAyah) } },
        )

        if (ready == null) return@Column

        val hasBasmala = ready.basmala != null
        val listState = rememberLazyListState()
        val jumpOffsetPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        var selectedAyah by remember(ready.surah.number) {
            mutableStateOf(initialAyah.takeIf { selectInitialAyah })
        }

        // Runs once per surah (the reader is re-created — a fresh view model — whenever the
        // surah changes, via App.kt's `remember(screen)`), so a settings change from the sheet
        // does not re-trigger the jump and fight the user's own scrolling.
        LaunchedEffect(ready.surah.number) {
            if (initialAyah > 1) {
                val ayahIndex = ready.ayahs.indexOfFirst { it.number == initialAyah }
                if (ayahIndex >= 0) {
                    // Spec §2.3: the card lands 8 dp below the top edge, not flush against it.
                    listState.scrollToItem((if (hasBasmala) 1 else 0) + ayahIndex, scrollOffset = -jumpOffsetPx)
                }
            }
        }

        LaunchedEffect(listState, ready.surah.number, hasBasmala) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { index ->
                    val ayahIndex = firstVisibleAyahIndex(index, hasBasmala)
                    ready.ayahs.getOrNull(ayahIndex)?.let {
                        headerAyah = it.number
                        onFirstVisibleAyah(it.number)
                    }
                }
        }

        val playingAyah = recitation.ayahIn(ready.surah.number)
        val following = rememberFollowing()
        val scope = rememberCoroutineScope()
        // The item index of an ayah: the basmala, when there is one, occupies index 0.
        fun itemIndexOf(ayah: Int): Int? =
            ready.ayahs.indexOfFirst { it.number == ayah }.takeIf { it >= 0 }?.plus(if (hasBasmala) 1 else 0)

        /** Where the followed ayah comes to rest: a third of the way down the viewport. */
        fun restingOffset(): Int = listState.layoutInfo.viewportSize.height / FOLLOW_VIEWPORT_FRACTION

        /** How many screenfuls past the visible range the ayah is: 0 while it is on screen. */
        fun screensAway(target: Int): Int {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) return 0
            val first = visible.first().index
            val last = visible.last().index
            val span = (last - first + 1).coerceAtLeast(1)
            return when {
                target in first..last -> 0
                target < first -> (first - target + span - 1) / span
                else -> (target - last + span - 1) / span
            }
        }

        // A touch is anything that starts or stops the list moving that we did not start
        // ourselves; [FollowingState.move] is what tells the two apart.
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { following.moved() }
        }

        // The bar or the media notification asking for the recited ayah (spec §15.5): the pill's
        // own answer, on request.
        LaunchedEffect(recitation.jumpToken) {
            if (recitation.jumpToken == 0) return@LaunchedEffect
            val target = playingAyah?.let(::itemIndexOf) ?: return@LaunchedEffect
            following.rearm()
            following.pill = null
            following.move { listState.animateScrollToItem(target, scrollOffset = -restingOffset()) }
        }

        LaunchedEffect(playingAyah, ready.surah.number) {
            val target = playingAyah?.let(::itemIndexOf)
            if (target == null) {
                following.pill = null
                return@LaunchedEffect
            }
            when (following.decide(screensAway(target))) {
                Follow.SCROLL -> {
                    following.pill = null
                    following.move { listState.animateScrollToItem(target, scrollOffset = -restingOffset()) }
                }
                Follow.PILL -> following.pill = playingAyah
                Follow.LEAVE_ALONE -> Unit
            }
        }
        // ...and the pill comes up when the *reader* leaves, not only when the voice moves on. The
        // effect above fires on an ayah boundary, which with a long ayah can be minutes away; a
        // reader who has scrolled several screens off in the middle of Al-Baqarah 282 would have
        // nothing offering the way back until it ended. Asked once the scroll has settled, so it
        // is not re-evaluated on every frame of a fling, and cancelled by the next scroll.
        LaunchedEffect(playingAyah, listState) {
            val target = playingAyah?.let(::itemIndexOf) ?: return@LaunchedEffect
            snapshotFlow { listState.isScrollInProgress }.collectLatest { moving ->
                if (moving) return@collectLatest
                delay(PILL_SETTLE_MS)
                if (screensAway(target) > 1) following.pill = playingAyah
            }
        }
        // The pill goes as soon as the ayah is back on screen, however it got there — the reader
        // scrolling to it themselves is the commonest way, and a pill still offering to take them
        // where they already are would be the app talking over them.
        LaunchedEffect(playingAyah, listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
                .collect { visible ->
                    val target = playingAyah?.let(::itemIndexOf) ?: return@collect
                    if (target in visible) following.pill = null
                }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                // The 24 dp gutters moved off the list and onto each item, because each item is now
                // capped and centred ([contentWidth]) and the gutter belongs inside that capped
                // column, not against the screen's own edges.
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp + recitation.barSpace),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (hasBasmala) {
                    item(key = "basmala") {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                ready.basmala.orEmpty(),
                                fontFamily = mushafFamily(),
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center,
                                color = colors.textPrimary,
                                modifier = Modifier.contentWidth().padding(horizontal = ReaderGutter),
                            )
                        }
                    }
                }
                items(ready.ayahs, key = { it.number }) { ayah ->
                    Box(Modifier.contentWidth().padding(horizontal = ReaderGutter)) {
                        AyahCard(
                            text = ayah.text,
                            ayahNumber = ayah.number,
                            transliteration = ready.transliteration?.get(ayah.number),
                            translation = ready.translation[ayah.number],
                            translationLanguage = ready.translationLanguage,
                            sizeSp = ready.settings.arabicSizeSp,
                            selected = selectedAyah == ayah.number,
                            playing = playingAyah == ayah.number,
                            bookmarked = ayah.number in ready.bookmarked,
                            // Built only for the selected card: every other card would otherwise pay
                            // for a row it never draws.
                            actions = if (selectedAyah != ayah.number) {
                                null
                            } else {
                                {
                                    AyahActions(
                                        bookmarked = ayah.number in ready.bookmarked,
                                        onBookmark = { onToggleBookmark(ayah.number) },
                                        // Answers the row, which only says "Copied" when something
                                        // actually was: null here means the text was not available.
                                        onCopy = {
                                            val copy = shareTextFor(ayah.number)
                                            if (copy != null) clipboard.setText(AnnotatedString(copy))
                                            copy != null
                                        },
                                        onShare = { shareTextFor(ayah.number)?.let(::shareText) },
                                        // The equaliser only while the voice is actually going: a
                                        // paused ayah showing three bouncing bars would be the one
                                        // moving thing on a screen with nothing playing.
                                        playing = playingAyah == ayah.number && recitation.live,
                                        onPlay = {
                                            if (playingAyah == ayah.number) {
                                                recitation.onToggle()
                                            } else {
                                                recitation.onPlayAyah(ready.surah.number, ayah.number)
                                            }
                                        },
                                    )
                                }
                            },
                            // One ayah at a time: tapping another moves the selection, tapping the same
                            // one clears it, which is what the action row sits on.
                            onClick = { selectedAyah = if (selectedAyah == ayah.number) null else ayah.number },
                        )
                    }
                }
                ready.nextSurah?.let { next ->
                    item(key = "next-surah") {
                        // Arabic name under an Arabic UI (spec §5.3): the Mushaf font is not required
                        // inside the format string itself, only when Quran text is drawn directly.
                        Box(Modifier.contentWidth().padding(horizontal = ReaderGutter)) {
                            NextSurahCard(next.displayName(arabic)) { onOpenNextSurah(next.number) }
                        }
                    }
                }
            }
            val pill = following.pill
            if (pill != null) {
                BackToAyahPill(
                    ayah = pill,
                    onClick = {
                        val target = itemIndexOf(pill) ?: return@BackToAyahPill
                        following.rearm()
                        following.pill = null
                        scope.launch {
                            following.move { listState.animateScrollToItem(target, -restingOffset()) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = recitation.barSpace + PillGap),
                )
            }
        }

        if (showSheet) {
            TaqwaBottomSheet(onDismissRequest = { showSheet = false }) {
                ReadingSheet(
                    settings = ready.settings,
                    translations = ready.translations,
                    previewAyah = ready.previewAyah,
                    mushafMode = false,
                    onChange = { newSettings ->
                        if (newSettings.mode == ReadingMode.MUSHAF) {
                            // The reader's own mode-toggle path already persists mode = MUSHAF and
                            // navigates (App.kt), so this never also writes it through
                            // [onChangeSettings] — that would be a redundant, and momentarily
                            // stale, second write.
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

/** The "next surah" footer card (spec §2.3): none after An-Nas, since [ReaderUiState.Ready.nextSurah]
 * is already null there. */
@Composable
private fun NextSurahCard(nextSurahName: String, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaCard(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.quran_next_surah, nextSurahName),
                style = TaqwaText.rowLabel,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            ForwardChevron()
        }
    }
}

/** The direction of reading progress — right in an LTR UI, left under Arabic — mirroring
 * [world.taqwa.app.feature.settings.BackChevron]'s own literal-coordinate drawing, since a
 * disclosure chevron drawn from a `Row`'s trailing edge would need the opposite mirroring rule
 * BackChevron uses for "back", not the same one. */
@Composable
private fun ForwardChevron() {
    val colors = LocalTaqwaColors.current
    val pointsRight = LocalLayoutDirection.current == LayoutDirection.Ltr
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        fun x(fraction: Float) = if (pointsRight) w * fraction else w * (1f - fraction)
        val path = Path().apply {
            moveTo(x(0.38f), w * 0.14f)
            lineTo(x(0.70f), w * 0.50f)
            lineTo(x(0.38f), w * 0.86f)
        }
        drawPath(
            path = path,
            color = colors.textTertiary,
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
