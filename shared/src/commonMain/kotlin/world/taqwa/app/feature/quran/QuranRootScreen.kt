package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaSegmented
import world.taqwa.app.design.contentWidth
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.design.quran
import world.taqwa.app.feature.settings.SettingsGutter
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.displayName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_continue
import world.taqwa.app.resources.quran_continue_detail
import world.taqwa.app.resources.quran_juz_n
import world.taqwa.app.resources.quran_juz_range
import world.taqwa.app.resources.quran_madani
import world.taqwa.app.resources.quran_makki
import world.taqwa.app.resources.quran_search_hint
import world.taqwa.app.resources.quran_surah_subtitle
import world.taqwa.app.resources.quran_surah_subtitle_arabic_ui
import world.taqwa.app.resources.quran_tab_juz
import world.taqwa.app.resources.quran_tab_surah
import world.taqwa.app.resources.quran_title

/** The card frame's own corner radius (spec §2.1) — [TaqwaCard]'s, kept in one place here since
 * the list section below rebuilds that frame by hand out of two cap items rather than a single
 * [TaqwaCard], see [CardTopCap] and [CardBottomCap]. */
private val CardCorner = 18.dp

/**
 * The Quran tab root (spec §2.1, "Root A"): a filter field, an optional continue-reading card, a
 * Surah | Juz switch, then the matching list. [pageFor] resolves the Mushaf page a tap should
 * open; it is suspend because [world.taqwa.app.quran.QuranSource.pageOf] hits the database, so a
 * tap in Mushaf mode launches its own short-lived coroutine rather than blocking composition.
 *
 * The whole screen is one [LazyColumn] (rather than a [SettingsScaffold]-style scrolling `Column`)
 * so the up-to-114 surah rows are only ever composed near the viewport, not all at once — the
 * screen otherwise reproduces [world.taqwa.app.feature.settings.SettingsScaffold]'s own tab-root
 * layout (the 20 dp top spacer a tab root uses in place of a back chevron, the title, the 20 dp
 * gap) by hand,
 * since that helper is built on `verticalScroll` rather than a lazy list.
 */
@Composable
fun QuranRootScreen(
    state: QuranRootUiState,
    onFilterChange: (String) -> Unit,
    onTabChange: (RootTab) -> Unit,
    pageFor: suspend (surah: Int, ayah: Int) -> Int,
    onOpenReader: (surah: Int, ayah: Int) -> Unit,
    onOpenMushaf: (page: Int) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val ready = state as? QuranRootUiState.Ready
    val scope = rememberCoroutineScope()

    // Hoisted here, above the LazyColumn, rather than declared inside the `item` that draws the
    // field: a LazyColumn is free to recompose or move item slots around as the row list beneath
    // it grows and shrinks on every keystroke, and state declared at this level survives that
    // regardless, so the field keeps both its text and its IME focus while the user types.
    var fieldValue by remember { mutableStateOf(TextFieldValue(ready?.filter.orEmpty())) }

    // Translation mode opens the reader directly; Mushaf mode needs the page number first,
    // which only the database — through pageFor — knows.
    fun open(surah: Int, ayah: Int) {
        if (ready?.mode == ReadingMode.TRANSLATION) {
            onOpenReader(surah, ayah)
        } else {
            scope.launch { onOpenMushaf(pageFor(surah, ayah)) }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // safeDrawing, not systemBars: sideways the navigation bar and the camera cutout sit
            // at the left and right edges, which only safeDrawing reports.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // A tab root has no back chevron, so it no longer reserves the chevron's 52 dp either:
        // with the title's own 4 dp on top of this, the title lands 24 dp below the status bar,
        // level with the Prayer tab's, so switching tabs no longer drops it by half a chevron.
        // Same value as SettingsScaffold's own tab-root spacer.
        item(key = "top-spacer") { Spacer(Modifier.height(20.dp)) }
        item(key = "title") {
            Text(
                stringResource(Res.string.quran_title),
                style = TaqwaText.screenTitle,
                color = colors.textPrimary,
                // Capped like every item below it, so the title stays over its own cards
                // instead of drifting to the far edge of a landscape screen.
                modifier = Modifier.contentWidth().padding(start = SettingsGutter, end = SettingsGutter, top = 4.dp),
            )
        }
        item(key = "title-gap") { Spacer(Modifier.height(20.dp)) }

        if (ready == null) return@LazyColumn

        item(key = "filter") {
            Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) {
                QuranSearchField(fieldValue) { newValue ->
                    fieldValue = newValue
                    onFilterChange(newValue.text)
                }
            }
        }
        item(key = "filter-gap") { Spacer(Modifier.height(12.dp)) }

        ready.continueCard?.let { card ->
            item(key = "continue") {
                Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) {
                    ContinueReadingCard(card) { open(card.surah.number, card.ayah) }
                }
            }
            item(key = "continue-gap") { Spacer(Modifier.height(12.dp)) }
        }

        item(key = "tabs") {
            Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) {
                TaqwaSegmented(
                    options = listOf(
                        stringResource(Res.string.quran_tab_surah),
                        stringResource(Res.string.quran_tab_juz),
                    ),
                    selectedIndex = if (ready.tab == RootTab.SURAH) 0 else 1,
                    onSelect = { onTabChange(if (it == 0) RootTab.SURAH else RootTab.JUZ) },
                )
            }
        }
        item(key = "tabs-gap") { Spacer(Modifier.height(12.dp)) }

        if (ready.tab == RootTab.SURAH) {
            surahListItems(ready.filteredSurahs) { surah -> open(surah.number, 1) }
        } else {
            juzListItems(ready.juzs) { juz -> open(juz.startSurah.number, juz.startAyah) }
        }

        item(key = "bottom-spacer") { Spacer(Modifier.height(40.dp)) }
    }
}

/**
 * The card-shaped list of surahs, laid out as plain [LazyListScope] items instead of one
 * [TaqwaCard] (spec §2.1 point 4): [CardTopCap] and [CardBottomCap] draw the rounded corners a
 * `TaqwaCard` would have drawn as a single border, each row draws its own hairline sides via
 * [CardRow], and [CardDivider] sits between rows exactly as it did inside the old `TaqwaCard`
 * column — the result is pixel-for-pixel the same card at rest, just no longer one composable
 * that has to lay out all of its children before anything scrolls.
 */
private fun LazyListScope.surahListItems(surahs: List<Surah>, onOpen: (Surah) -> Unit) {
    if (surahs.isEmpty()) return
    item(key = "surah-cap-top") {
        Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) { CardTopCap() }
    }
    itemsIndexed(surahs, key = { _, surah -> "surah-${surah.number}" }) { index, surah ->
        Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) {
            Column {
                CardRow { SurahRow(surah) { onOpen(surah) } }
                if (index != surahs.lastIndex) CardDivider()
            }
        }
    }
    item(key = "surah-cap-bottom") {
        Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) { CardBottomCap() }
    }
}

private fun LazyListScope.juzListItems(juzs: List<JuzRow>, onOpen: (JuzRow) -> Unit) {
    if (juzs.isEmpty()) return
    item(key = "juz-cap-top") {
        Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) { CardTopCap() }
    }
    itemsIndexed(juzs, key = { _, juz -> "juz-${juz.number}" }) { index, juz ->
        Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) {
            Column {
                CardRow { JuzListRow(juz) { onOpen(juz) } }
                if (index != juzs.lastIndex) CardDivider()
            }
        }
    }
    item(key = "juz-cap-bottom") {
        Box(Modifier.contentWidth().padding(horizontal = SettingsGutter)) { CardBottomCap() }
    }
}

/** The rounded top edge a [TaqwaCard] would have drawn as part of its own single border — the top
 * slice of one whole rounded rectangle, so it reads as the top of the same card once it sits
 * directly above the first row's own [CardRow] sides.
 *
 * A `border` on a shape rounded at two corners also strokes that shape's straight *inner* edge,
 * which drew a hairline all the way across the card just above the first row. So the rect is drawn
 * a full [CardCorner] taller than the cap and clipped to the cap's bounds: its bottom edge lands
 * outside and is never painted, leaving only the rounded top and the two sides, which continue
 * into the rows' own sides. */
@Composable
private fun CardTopCap() {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(CardCorner)
            .clipToBounds()
            .drawBehind {
                val stroke = 1.dp.toPx()
                val radius = CornerRadius(CardCorner.toPx())
                val height = size.height + CardCorner.toPx()
                drawRoundRect(colors.surface, size = Size(size.width, height), cornerRadius = radius)
                // Inset by half the stroke: a stroked outline straddles the path it follows, so an
                // uninset rect would spend the outer half of each side line on the clipped-away
                // pixel column and read as a lighter hairline than the rows' own sides.
                drawRoundRect(
                    colors.hairline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, height - stroke),
                    cornerRadius = radius,
                    style = Stroke(stroke),
                )
            },
    )
}

/** [CardTopCap]'s mirror for the card's bottom edge: the same oversized rect, shifted up by
 * [CardCorner] so it is the straight *top* edge that falls outside the clip. */
@Composable
private fun CardBottomCap() {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(CardCorner)
            .clipToBounds()
            .drawBehind {
                val stroke = 1.dp.toPx()
                val corner = CardCorner.toPx()
                val radius = CornerRadius(corner)
                val height = size.height + corner
                drawRoundRect(
                    colors.surface,
                    topLeft = Offset(0f, -corner),
                    size = Size(size.width, height),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    colors.hairline,
                    topLeft = Offset(stroke / 2, -corner + stroke / 2),
                    size = Size(size.width - stroke, height - stroke),
                    cornerRadius = radius,
                    style = Stroke(stroke),
                )
            },
    )
}

/** One row's own share of the card's frame: the surface fill and the two side hairlines, which —
 * unlike the top and bottom edges — never overlap another item's own line, so every row can draw
 * them independently without doubling any line up with its neighbour. */
@Composable
private fun CardRow(content: @Composable () -> Unit) {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(colors.hairline, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                drawLine(colors.hairline, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
            },
    ) { content() }
}

/** The filter field (spec §2.1 point 1): a hairline card, a hand-drawn magnifier, and a placeholder.
 * [value] is a [TextFieldValue], not a bare [String] — see [QuranRootScreen]'s own hoisted state —
 * so the field's cursor position and IME composing state survive the list beneath it changing. */
@Composable
private fun QuranSearchField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = size.minDimension * 0.13f
            val lensRadius = size.minDimension * 0.30f
            val lensCenter = Offset(size.width * 0.42f, size.height * 0.42f)
            drawCircle(color = colors.textTertiary, radius = lensRadius, center = lensCenter, style = Stroke(width = stroke))
            drawLine(
                color = colors.textTertiary,
                start = Offset(size.width * 0.64f, size.height * 0.64f),
                end = Offset(size.width * 0.92f, size.height * 0.92f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        Box(Modifier.weight(1f)) {
            if (value.text.isEmpty()) {
                Text(stringResource(Res.string.quran_search_hint), style = TaqwaText.caption, color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TaqwaText.caption.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The continue-reading card (spec §2.1 point 2). */
@Composable
private fun ContinueReadingCard(card: ContinueCard, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val arabic = isRtlLocale()
    TaqwaCard(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                stringResource(Res.string.quran_continue),
                style = TaqwaText.sectionLabel,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (arabic) {
                        // The Arabic name stands in for the Latin one under an Arabic UI (spec
                        // §5.3); the trailing badge below is then dropped rather than repeating it.
                        Text(
                            card.surah.nameArabic,
                            style = TaqwaText.quran(20).copy(fontFamily = mushafFamily(), lineHeight = TextUnit.Unspecified),
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    } else {
                        Text(card.surah.nameLatin, style = TaqwaText.rowLabel, color = colors.textPrimary)
                    }
                    // Neither name above carries leading of its own — the Arabic one has its line
                    // height stripped for the badge, the Latin one is a snug row label — so the
                    // detail line sits on top of it unless the gap is drawn explicitly.
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            Res.string.quran_continue_detail,
                            format.localizedDigits(card.ayah),
                            format.localizedDigits(card.surah.ayahCount),
                            format.localizedDigits(card.juz),
                        ),
                        style = TaqwaText.caption.copy(fontSize = 13.sp),
                        color = colors.textSecondary,
                    )
                }
                if (!arabic) {
                    // A single-line badge, so the 2.0x line height meant for wrapped ayah text is
                    // dropped — kept, it would double this row's height for no visible reason.
                    Text(
                        card.surah.nameArabic,
                        style = TaqwaText.quran(26).copy(fontFamily = mushafFamily(), lineHeight = TextUnit.Unspecified),
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val fraction = (card.ayah.toFloat() / card.surah.ayahCount).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.hairline),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.accent),
                )
            }
        }
    }
}

@Composable
private fun SurahRow(surah: Surah, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val arabic = isRtlLocale()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(30.dp).border(1.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                format.localizedDigits(surah.number),
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        }
        Column(Modifier.weight(1f)) {
            if (arabic) {
                // The Mushaf font, per spec — this is the surah's real name from the database
                // (surah.name_ar), never retyped, so mushafFamily() is the only face allowed to
                // draw it. It stands in for the Latin label under an Arabic UI (spec §5.3), so the
                // trailing badge this row otherwise shows is dropped instead of repeating it.
                Text(
                    surah.nameArabic,
                    style = TaqwaText.quran(20).copy(fontFamily = mushafFamily(), lineHeight = TextUnit.Unspecified),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    stringResource(
                        Res.string.quran_surah_subtitle_arabic_ui,
                        format.localizedDigits(surah.ayahCount),
                        revelationName(surah.revelation),
                    ),
                    style = TaqwaText.caption.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
            } else {
                Text(surah.nameLatin, style = TaqwaText.rowLabel, color = colors.textPrimary)
                Text(
                    stringResource(
                        Res.string.quran_surah_subtitle,
                        surah.meaning,
                        format.localizedDigits(surah.ayahCount),
                        revelationName(surah.revelation),
                    ),
                    style = TaqwaText.caption.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
            }
        }
        if (!arabic) {
            Text(
                surah.nameArabic,
                style = TaqwaText.quran(20).copy(fontFamily = mushafFamily(), lineHeight = TextUnit.Unspecified),
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun JuzListRow(juz: JuzRow, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val arabic = isRtlLocale()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.quran_juz_n, format.localizedDigits(juz.number)),
                style = TaqwaText.rowLabel,
                color = colors.textPrimary,
            )
            Text(
                stringResource(
                    Res.string.quran_juz_range,
                    juz.startSurah.displayName(arabic),
                    format.localizedDigits(juz.startAyah),
                    juz.endSurah.displayName(arabic),
                    format.localizedDigits(juz.endAyah),
                ),
                style = TaqwaText.caption.copy(fontSize = 12.sp),
                color = colors.textSecondary,
            )
        }
        // "Juz N" in Arabic script regardless of the UI language — the same dual-script badge
        // the surah rows show — but this label is ours, not the Quran's, so it is drawn in the
        // ordinary Arabic UI face rather than mushafFamily(), which spec §5 reserves for actual
        // Quran text.
        Text(
            juzArabicLabel(juz.number),
            fontSize = 20.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Right,
            maxLines = 1,
        )
    }
}

/** "Juz" is not Quran text and has no database row of its own, so — like the hardcoded Arabic
 * copy in [world.taqwa.app.hijri.HijriFormatter] — this is a plain literal, not a resource: it
 * must always render in Arabic script as a decorative pair to the Latin "Juz N" label, regardless
 * of which language the rest of the UI is in. */
private fun juzArabicLabel(number: Int): String = "الجزء ${QuranText.arabicIndic(number)}"

@Composable
private fun revelationName(revelation: Revelation): String = stringResource(
    if (revelation == Revelation.MAKKI) Res.string.quran_makki else Res.string.quran_madani,
)
