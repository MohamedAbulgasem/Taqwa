package world.taqwa.app.feature.quran

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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
import world.taqwa.app.quran.LineWord
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

/**
 * The smallest fraction of the page's base size a line may be set at to stay on one line. The
 * spec allows a 30% shrink (spec §2.4); this floor allows 45%, purely as insurance. Once a line
 * cannot fit even at the floor it wraps, and `maxLines = 1` then drops everything after the first
 * line — losing Quran text silently — so the floor is the
 * one number here that must never be reached. The Hafs font sets the densest pages (juz 30, where
 * a line can carry four ayahs and their roundels) noticeably wider than the printed Mushaf's own
 * per-page typesetting does, and no line on the pages checked (1, 2, 3, 42, 586, 604) comes near
 * even 70%.
 */
private const val MIN_SHRINK = 0.55f

/** Arabic-Indic digits — what a word's trailing ayah number is written in, in every layout row. */
private val ARABIC_INDIC_DIGITS = '٠'..'٩'

/** One word of a line, together with where it sits in the line's own joined text. [end] is
 * exclusive, so `text.substring(start, end)` is exactly [word]'s text. */
internal data class WordRange(val word: LineWord, val start: Int, val end: Int)

/**
 * Where each of [line]'s words lands in [MushafLine.text]. The pipeline guarantees the line's text
 * is its words joined with a single space (spec §3.2 rule 3 verifies it per ayah), so this is
 * arithmetic on the word lengths rather than a search — which matters, because a word may itself
 * contain a space (a pause mark, or the ayah's trailing digits) and searching would mis-align.
 */
internal fun wordRanges(line: MushafLine): List<WordRange> {
    var cursor = 0
    return line.words.map { word ->
        val start = cursor
        cursor += word.text.length
        val range = WordRange(word, start, cursor)
        cursor += 1 // the joining space
        range
    }
}

/**
 * The word a tap at character [offset] landed on (spec §2.4). The space that follows a word counts
 * as that word, so no tap between two words falls through; an offset past the end of the line —
 * which a tap in the slack of a short line produces — is nobody's.
 */
internal fun wordAt(ranges: List<WordRange>, offset: Int): LineWord? =
    ranges.firstOrNull { offset >= it.start && offset <= it.end }?.word

/**
 * Spec §2.4: text lines are set justified to the frame width, except a line that ends a surah —
 * which is set to the start, as the printed page does — and every line of pages 1 and 2, the two
 * short opening pages, which the printed Mushaf does not fill either.
 */
internal fun isJustified(page: MushafPage, line: MushafLine): Boolean =
    line.type == LineType.TEXT && !line.endsSurah && page.number > 2

/** Where a word's trailing Arabic-Indic digit run starts, or -1 when it has none. Only the last
 * word of an ayah carries one (spec §3.2 rule 6). */
private fun digitsStart(word: String): Int {
    var i = word.length
    while (i > 0 && word[i - 1] in ARABIC_INDIC_DIGITS) i--
    return if (i == word.length) -1 else i
}

/**
 * One printed page of the Madinah Mushaf (spec §2.4): the juz and surah caption, the double
 * hairline frame holding exactly the page's own lines, and the page number below in Arabic-Indic
 * digits. Everything inside is laid out right-to-left whatever the UI language is (spec §5.3).
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
                        page.lines.forEach { line ->
                            when (line.type) {
                                LineType.SURAH -> line.surah?.let(surahOf)?.let { SurahBand(it, base, family) }
                                LineType.BASMALA -> BasicText(
                                    basmala,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = TextStyle(
                                        fontFamily = family,
                                        fontSize = base.sp,
                                        lineHeight = (base * 1.9f).sp,
                                        color = colors.textPrimary,
                                        textAlign = TextAlign.Center,
                                        textDirection = TextDirection.Rtl,
                                    ),
                                    maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = (base * MIN_SHRINK).sp,
                                        maxFontSize = base.sp,
                                        stepSize = 0.5.sp,
                                    ),
                                )
                                LineType.TEXT -> MushafTextLine(
                                    line = line,
                                    family = family,
                                    base = base,
                                    justify = isJustified(page, line),
                                    highlighted = marked,
                                    onTapAyah = onTapAyah,
                                )
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
 * One printed line (spec §2.4): a single [BasicText] that may never wrap, so it auto-shrinks — by
 * as much as [MIN_SHRINK] allows — until it fits. The ayah numbers the layout already carries at the end of a word are drawn
 * in the accent colour — the Hafs font itself makes them roundels (spec §5.2), so nothing is added
 * to the text here.
 */
@Composable
private fun MushafTextLine(
    line: MushafLine,
    family: FontFamily,
    base: Float,
    justify: Boolean,
    highlighted: Pair<Int, Int>?,
    onTapAyah: (Int, Int) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val ranges = remember(line) { wordRanges(line) }
    val text = remember(line, highlighted, colors.accent) {
        annotateLine(line.text.orEmpty(), ranges, highlighted, colors.accent)
    }
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    // If a line still overflows at the shrink floor, the second line is the safety net: Quran text
    // must never be clipped away, and one wrapped line on one page is the lesser failure.
    var overflowed by remember(line) { mutableStateOf(false) }

    BasicText(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(ranges) {
                detectTapGestures { position ->
                    val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                    wordAt(ranges, offset)?.let { onTapAyah(it.surah, it.ayah) }
                }
            },
        style = TextStyle(
            fontFamily = family,
            color = colors.textPrimary,
            textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
            textDirection = TextDirection.Rtl,
            lineHeight = (base * 1.9f).sp,
        ),
        onTextLayout = {
            layout = it
            if (it.didOverflowWidth && !overflowed) overflowed = true
        },
        // softWrap stays on with maxLines = 1: with it off the line is measured against unbounded
        // width, TextAutoSize never sees an overflow, and a line too wide for the frame is simply
        // clipped at its left end instead of shrinking (page 586 did exactly that). Wrapping on,
        // capped at one line, is what makes the auto-size loop shrink until the line fits.
        maxLines = if (overflowed) 2 else 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = (base * MIN_SHRINK).sp,
            maxFontSize = base.sp,
            stepSize = 0.5.sp,
        ),
    )
}

/** The line's own text with two kinds of span over it: the accent on every ayah-ending digit run,
 * and the soft accent field behind every word of the highlighted ayah. */
internal fun annotateLine(
    text: String,
    ranges: List<WordRange>,
    highlighted: Pair<Int, Int>?,
    accent: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (highlighted != null) {
        val field = SpanStyle(background = accent.copy(alpha = 0.16f))
        fun isMarked(range: WordRange) =
            range.word.surah == highlighted.first && range.word.ayah == highlighted.second
        // One span per *run* of the ayah's words, not one per word: spanning the spaces between
        // them is what makes the field read as a single block behind the ayah rather than stripes.
        var i = 0
        while (i < ranges.size) {
            if (!isMarked(ranges[i])) {
                i++
                continue
            }
            var last = i
            while (last + 1 < ranges.size && isMarked(ranges[last + 1])) last++
            addStyle(field, ranges[i].start, ranges[last].end)
            i = last + 1
        }
    }
    ranges.forEach { range ->
        val digits = digitsStart(range.word.text)
        if (digits >= 0) addStyle(SpanStyle(color = accent), range.start + digits, range.end)
    }
}

/**
 * A surah's opening band (spec §2.4): the name in the Mushaf font, framed, with the ayah count and
 * the revelation place on its sides — the printed page's own header ornament, drawn in hairlines
 * rather than as a picture.
 */
@Composable
internal fun SurahBand(surah: Surah, base: Float, family: FontFamily) {
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
                fontSize = (base * 0.85f).sp,
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
