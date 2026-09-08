package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.quran.LineType
import world.taqwa.app.quran.MushafLine
import world.taqwa.app.quran.MushafPage
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_juz_n
import world.taqwa.app.resources.quran_madani
import world.taqwa.app.resources.quran_makki

/** The frame's own width at the reference 375 dp screen, from which the base size scales (spec §5.1). */
private const val REFERENCE_FRAME_WIDTH_DP = 347f
private const val REFERENCE_BASE_SP = 28f
private const val MIN_BASE_SP = 20f
private const val MAX_BASE_SP = 34f

/** Line box height as a multiple of the font size — the same 1.9× the previous renderer used, a
 * little tighter than the reader's 2.0× so fifteen lines fit a phone's height. */
private const val LINE_HEIGHT = 1.9f

/** Arabic-Indic digits — what a word's trailing ayah number is written in, in every layout row. */
private val ARABIC_INDIC_DIGITS = '٠'..'٩'

/**
 * One printed page of the Madinah Mushaf (spec §2.4): the juz and surah caption, the double
 * hairline frame holding exactly the page's own lines, and the page number below in Arabic-Indic
 * digits. Everything inside is laid out right-to-left whatever the UI language is (spec §5.3).
 *
 * Every line of the page shares one font size — the largest, up to the width-derived base, at
 * which the page's widest line still fits the frame ([fittedSize]) — and each text line's words are
 * measured individually and spread across the frame ([placeWords]), so a page reads at one even
 * size with every line filled to the margin, the way the printed page is.
 *
 * [highlighted] is the tapped ayah as `surah to ayah`; every word of it on this page carries a
 * soft accent field and a reference pill shows at the foot of the frame.
 */
@Composable
fun MushafPageView(
    page: MushafPage,
    surahOf: (Int) -> Surah?,
    basmala: String,
    highlighted: Pair<Int, Int>?,
    onTapAyah: (Int, Int) -> Unit,
    onClearHighlight: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val family = mushafFamily()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The highlight belongs to whichever pages actually carry that ayah's words: the screen keeps
    // one selection across page turns, and a page the ayah is not on must show neither field nor
    // pill.
    val marked = highlighted?.takeIf { (surah, ayah) ->
        page.lines.any { line -> line.words.any { it.surah == surah && it.ayah == ayah } }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // 14 dp per side: the frame's own two hairlines, their 4 dp gap and the 10 dp of inner
            // padding the text actually starts after.
            val frameWidth = maxWidth - 28.dp
            val base = (REFERENCE_BASE_SP * (frameWidth.value / REFERENCE_FRAME_WIDTH_DP))
                .coerceIn(MIN_BASE_SP, MAX_BASE_SP)
            val frameWidthPx = with(density) { frameWidth.toPx() }
            // Keyed on everything the measurements depend on; the highlight is deliberately not
            // among them, since it only changes what is drawn behind words already placed.
            val layout = remember(page, base, frameWidthPx, family, colors.accent, density) {
                layoutPage(page, base, frameWidthPx, family, colors.accent, measurer, density)
            }
            val lineHeight: Dp = with(density) { (layout.sizeSp * LINE_HEIGHT).sp.toDp() }

            Column(Modifier.fillMaxSize()) {
                // The caption names the surah the page's first *text* line is in — the same rule
                // the header uses, so the two never disagree while a page straddles two surahs.
                val captionSurah = page.lines.firstOrNull { it.type == LineType.TEXT }
                    ?.firstSurah?.let(surahOf) ?: surahOf(page.firstSurah)
                PageCaption(page, captionSurah)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
                            .padding(4.dp)
                            .background(colors.surface, RoundedCornerShape(15.dp))
                            .border(1.dp, colors.hairline, RoundedCornerShape(15.dp))
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        // Pages 1 and 2 hold eight lines where every other page holds fifteen, so
                        // spreading them would leave the printed text floating (spec §2.4).
                        verticalArrangement = if (page.number <= 2) {
                            Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                        } else {
                            Arrangement.SpaceEvenly
                        },
                    ) {
                        page.lines.forEachIndexed { index, line ->
                            when (line.type) {
                                LineType.SURAH -> line.surah?.let(surahOf)?.let { SurahBand(it, layout.sizeSp, family) }
                                LineType.BASMALA -> BasicText(
                                    basmala,
                                    modifier = Modifier.fillMaxWidth().height(lineHeight),
                                    style = TextStyle(
                                        fontFamily = family,
                                        fontSize = layout.sizeSp.sp,
                                        lineHeight = (layout.sizeSp * LINE_HEIGHT).sp,
                                        color = colors.textPrimary,
                                        textAlign = TextAlign.Center,
                                        textDirection = TextDirection.Rtl,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                LineType.TEXT -> layout.lines[index]?.let { placed ->
                                    MushafTextLine(
                                        line = line,
                                        placed = placed,
                                        lineHeight = lineHeight,
                                        highlighted = marked,
                                        onTapAyah = onTapAyah,
                                    )
                                }
                            }
                        }
                    }
                    if (marked != null) {
                        ReferencePill(
                            marked,
                            // Straddling the frame's bottom hairline rather than sitting above it:
                            // the fifteenth line reaches within a few dp of that edge, and a pill
                            // fully inside the frame would cover the end of it.
                            Modifier.align(Alignment.BottomCenter).offset(y = 16.dp),
                            onClearHighlight,
                        )
                    }
                }
                Text(
                    QuranText.arabicIndic(page.number),
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One text line, measured and placed: each word's own text layout and where it sits. */
internal class PlacedLine(val words: List<TextLayoutResult>, val positions: List<PlacedWord>)

/** A whole page, measured: the one font size every line uses and the placed text lines, keyed by
 * their index in [MushafPage.lines]. */
internal class PageLayout(val sizeSp: Float, val lines: Map<Int, PlacedLine>)

/**
 * Measures the page (spec §2.4). Two passes: every text line is measured whole at [base] to find
 * the widest, which fixes the page's size; then every word is measured at that size and placed.
 * The natural gap between words is what the same font puts between them when the whole line is
 * measured as one string, so an unjustified line looks exactly as it would if drawn as one text.
 */
private fun layoutPage(
    page: MushafPage,
    base: Float,
    frameWidthPx: Float,
    family: FontFamily,
    accent: Color,
    measurer: TextMeasurer,
    density: androidx.compose.ui.unit.Density,
): PageLayout {
    fun style(sizeSp: Float) = TextStyle(fontFamily = family, fontSize = sizeSp.sp, textDirection = TextDirection.Rtl)
    fun measure(text: AnnotatedString, sizeSp: Float): TextLayoutResult =
        measurer.measure(text, style(sizeSp), softWrap = false, maxLines = 1, layoutDirection = LayoutDirection.Rtl, density = density)

    val textLines = page.lines.withIndex().filter { it.value.type == LineType.TEXT }
    val widestAtBase = textLines.maxOfOrNull { (_, line) ->
        measure(AnnotatedString(line.text.orEmpty()), base).size.width.toFloat()
    } ?: 0f
    val size = fittedSize(base, widestAtBase, frameWidthPx)

    val lines = textLines.associate { (index, line) ->
        val words = line.words.map { measure(annotateWord(it.text, accent), size) }
        val widths = words.map { it.size.width.toFloat() }
        val whole = measure(AnnotatedString(line.text.orEmpty()), size).size.width.toFloat()
        val gaps = (words.size - 1).coerceAtLeast(1)
        val naturalGap = ((whole - widths.sum()) / gaps).coerceAtLeast(0f)
        index to PlacedLine(words, placeWords(widths, frameWidthPx, naturalGap, isJustified(page, line)))
    }
    return PageLayout(size, lines)
}

/** The word's text with its trailing ayah digits, if any, in the accent colour: the Hafs font
 * itself draws bare Arabic-Indic digits as the roundel (spec §5.2), so nothing is added. */
internal fun annotateWord(word: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val digits = digitsStart(word)
    if (digits < 0) {
        append(word)
    } else {
        append(word.substring(0, digits))
        pushStyle(SpanStyle(color = accent))
        append(word.substring(digits))
        pop()
    }
}

/** Where a word's trailing Arabic-Indic digit run starts, or -1 when it has none. Only the last
 * word of an ayah carries one (spec §3.2 rule 6). */
internal fun digitsStart(word: String): Int {
    var i = word.length
    while (i > 0 && word[i - 1] in ARABIC_INDIC_DIGITS) i--
    return if (i == word.length) -1 else i
}

/**
 * One printed line (spec §2.4), drawn word by word at the positions [placed] holds. The soft
 * accent field behind a highlighted ayah is one rounded rectangle per run of its words, spanning
 * the gaps between them; a tap resolves to a word by its horizontal span and reports that word's
 * ayah.
 */
@Composable
private fun MushafTextLine(
    line: MushafLine,
    placed: PlacedLine,
    lineHeight: Dp,
    highlighted: Pair<Int, Int>?,
    onTapAyah: (Int, Int) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val field = colors.accent.copy(alpha = 0.16f)
    val runs = remember(line, highlighted) {
        if (highlighted == null) {
            emptyList()
        } else {
            markedRuns(line.words.size) { i ->
                line.words[i].surah == highlighted.first && line.words[i].ayah == highlighted.second
            }
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(lineHeight)
            .pointerInput(line, placed) {
                detectTapGestures { position ->
                    val index = wordAtX(placed.positions, position.x, size.width.toFloat()) ?: return@detectTapGestures
                    line.words[index].let { onTapAyah(it.surah, it.ayah) }
                }
            },
    ) {
        runs.forEach { run ->
            val right = placed.positions[run.first].right
            val left = placed.positions[run.last].left
            drawRoundRect(
                color = field,
                topLeft = Offset(left - 2.dp.toPx(), 0f),
                size = Size(right - left + 4.dp.toPx(), size.height),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
        placed.words.forEachIndexed { index, word ->
            val position = placed.positions[index]
            drawText(
                word,
                color = colors.textPrimary,
                topLeft = Offset(position.left, (size.height - word.size.height) / 2f),
            )
        }
    }
}

/** The line above the frame (spec §2.4): the juz on the start (right, under the page's own RTL)
 * side, the surah on the other. */
@Composable
private fun PageCaption(page: MushafPage, surah: Surah?) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.quran_juz_n, format.localizedDigits(page.juz)),
            style = TaqwaText.caption.copy(fontSize = 12.sp),
            color = colors.textSecondary,
            maxLines = 1,
        )
        if (surah != null) PageCaptionSurahName(surah)
    }
}

/** The caption's surah name: the Arabic name in the Mushaf font under an Arabic UI, the Latin one
 * in the UI face otherwise (spec §2.4), so a reader never meets a script they cannot read. */
@Composable
private fun PageCaptionSurahName(surah: Surah) {
    val colors = LocalTaqwaColors.current
    if (isRtlLocale()) {
        Text(
            surah.nameArabic,
            fontFamily = mushafFamily(),
            fontSize = 15.sp,
            color = colors.textSecondary,
            maxLines = 1,
        )
    } else {
        Text(
            surah.nameLatin,
            style = TaqwaText.caption.copy(fontSize = 12.sp),
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * A surah's opening band (spec §2.4): the name in the Mushaf font, framed, with the ayah count and
 * the revelation place on its sides — the printed page's own header ornament, drawn in hairlines
 * rather than as a picture.
 */
@Composable
internal fun SurahBand(surah: Surah, sizeSp: Float, family: FontFamily) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Both sides weighted so the name sits on the row's true centre rather than wherever two
        // labels of different lengths happen to leave it.
        // Arabic-Indic in every UI language, like the page number beneath: the band belongs to the
        // Mushaf, not to the interface.
        Text(
            QuranText.arabicIndic(surah.ayahCount),
            style = TaqwaText.caption.copy(fontSize = 11.sp),
            color = colors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            surah.nameArabic,
            style = TextStyle(
                fontFamily = family,
                fontSize = (sizeSp * 0.85f).sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                textDirection = TextDirection.Rtl,
            ),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            stringResource(if (surah.revelation == Revelation.MAKKI) Res.string.quran_makki else Res.string.quran_madani),
            style = TaqwaText.caption.copy(fontSize = 11.sp),
            color = colors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The tapped ayah's reference (spec §2.4), always in Arabic-Indic digits like the roundels
 * themselves; a tap clears the highlight. The action row it will grow arrives in slice 2b. */
@Composable
private fun ReferencePill(reference: Pair<Int, Int>, modifier: Modifier, onClear: () -> Unit) {
    val colors = LocalTaqwaColors.current
    // The finger gets 44 dp (spec §92) around a pill drawn no taller than it needs to be, the same
    // split the reader header's own round buttons use.
    Box(
        modifier
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClear,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .background(colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "${QuranText.arabicIndic(reference.first)}:${QuranText.arabicIndic(reference.second)}",
                style = TaqwaText.caption.copy(fontSize = 12.sp),
                color = colors.accent,
                maxLines = 1,
            )
        }
    }
}
