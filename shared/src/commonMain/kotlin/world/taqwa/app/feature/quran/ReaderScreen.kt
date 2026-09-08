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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.displayName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_juz_page
import world.taqwa.app.resources.quran_next_surah

/**
 * Translation mode's reader (spec §2.3): the header, the basmala, one [AyahCard] per ayah, and the
 * "next surah" footer. [initialAyah] is [world.taqwa.app.nav.Screen.Reader]'s own ayah — the one a
 * tap on the tab root or the continue-reading card asked to open — and is only used once, to scroll
 * there; after that, [ReaderViewModel.onFirstVisibleAyah] tracks whatever the user actually scrolls
 * to, which is why it is a plain constructor-time value here rather than part of [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    initialAyah: Int,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onChangeSettings: (ReadingSettings) -> Unit,
    onFirstVisibleAyah: (Int) -> Unit,
    onOpenNextSurah: (Int) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val arabic = isRtlLocale()
    val format = LocalPlatformFormat.current
    val ready = state as? ReaderUiState.Ready
    // The reading-settings sheet (task 7, spec §2.5) is hosted here rather than by the caller: the
    // Aa button is this screen's own affordance, and the Mushaf screen (task 8) will host its own
    // instance of [ReadingSheet] the same way, since only each screen knows its own [mushafMode].
    var showSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.systemBars)) {
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
        )

        if (ready == null) return@Column

        val hasBasmala = ready.basmala != null
        val listState = rememberLazyListState()
        val jumpOffsetPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        var selectedAyah by remember(ready.surah.number) { mutableStateOf<Int?>(null) }

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
                    ready.ayahs.getOrNull(ayahIndex)?.let { onFirstVisibleAyah(it.number) }
                }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            items(ready.ayahs, key = { it.number }) { ayah ->
                AyahCard(
                    text = ayah.text,
                    ayahNumber = ayah.number,
                    transliteration = ready.transliteration?.get(ayah.number),
                    translation = ready.translation[ayah.number],
                    translationLanguage = ready.translationLanguage,
                    sizeSp = ready.settings.arabicSizeSp,
                    selected = selectedAyah == ayah.number,
                    // One ayah at a time: tapping another moves the selection, tapping the same
                    // one clears it, which is the model 2b's action row will sit on.
                    onClick = { selectedAyah = if (selectedAyah == ayah.number) null else ayah.number },
                )
            }
            ready.nextSurah?.let { next ->
                item(key = "next-surah") {
                    // Arabic name under an Arabic UI (spec §5.3): the Mushaf font is not required
                    // inside the format string itself, only when Quran text is drawn directly.
                    NextSurahCard(next.displayName(arabic)) { onOpenNextSurah(next.number) }
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

/** The reading-settings sheet's grab handle (spec §2.5): a plain 36×4 dp pill in the hairline
 * colour, replacing material3's own default drag handle so it matches the rest of the app's
 * hairline-drawn chrome rather than the library's default grey. Shared with [MushafScreen], whose
 * sheet is the same sheet. */
@Composable
internal fun SheetDragHandle() {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .padding(vertical = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.hairline),
    )
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
