package world.taqwa.app.wear.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One line of a screen's type (call 15): its size in the drawing's [units], how far the wearer's
 * font size may grow it ([cap], a factor), how many lines it may take, and how far it may shrink to
 * fit ([floor], in units; by default it never shrinks below the drawing's size).
 *
 * [maxLineHeight], for a line that may take more than one, is the farthest apart its lines'
 * baselines may stand, as a share of the size it is drawn at. A font that sets its own lines farther
 * apart (the watch's Naskh, 1.70) has them drawn that far apart, and one that sets them nearer
 * (Roboto, 1.17) keeps its own ([lineSpacing]); by default every font keeps its own. The first
 * line's top and the last line's bottom stay the font's own, so a block of one line is drawn as
 * without it. It is fitted with `LineText`'s `bottom`, which measures the text.
 */
internal data class Line(
    val units: Float,
    val cap: Float,
    val maxLines: Int = 1,
    val floor: Float = units,
    val maxLineHeight: Float? = null,
) {

    /**
     * The sizes, in units, the line may be drawn at under [fontScale]: from its [floor] to the
     * drawing's size grown by the scale, up to its [cap]. A smaller font size never shrinks it below
     * 100%, the phone dial's letters' rule.
     */
    fun range(fontScale: Float): ClosedFloatingPointRange<Float> = floor..units * fontScale.coerceIn(1f, cap)

    /**
     * The highest its glyphs reach, in units, set at [y] (its baseline) under [fontScale]: about 0.8
     * of the largest size it may be drawn at above the baseline ([farEdge]'s rule).
     */
    fun top(y: Float, fontScale: Float): Float = y - ASCENT * range(fontScale).endInclusive
}

/**
 * How far apart a block's lines stand, as a share of its size, when its fonts set them [own] apart:
 * [most] when its own are farther apart than that, else null, its own.
 */
internal fun lineSpacing(own: Float, most: Float?): Float? = most?.takeIf { own > it }

/** The frame's own lines: each page's title, and Today's city and notice (Task 4). */
internal object FrameType {
    val title = Line(12f, 1.3f)
    val notice = Line(15f, 1.2f, maxLines = 3, floor = 11f)
    val city = Line(10.5f, 1.3f)
}

/** The letter spacing a line is drawn with: [tracking] units, or none for a line in Arabic script, which is never tracked. */
internal fun trackingFor(text: String, tracking: Float): Float = if (text.any(::isArabicScript)) 0f else tracking

private fun isArabicScript(c: Char): Boolean =
    c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F' || c in '\u08A0'..'\u08FF' || c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF'

/** Where a child of [UnitsBox] sits: [y] units from the drawing's top, at its first baseline, or at its middle when [centred]. */
private data class AtUnits(val y: Float, val centred: Boolean) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@AtUnits
}

/**
 * A page drawn in the drawing's units: the 200-unit square centred on the screen, each child
 * centred across it, and set at its height by [atUnits]. Each child keeps its own size, so a screen
 * reader's focus is the line's and not the page's.
 */
@Composable
internal fun UnitsBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placed = measurables.map { it.measure(loose) to (it.parentData as? AtUnits) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val square = min(constraints.maxWidth, constraints.maxHeight)
            val top = (constraints.maxHeight - square) / 2
            for ((placeable, at) in placed) {
                val x = (constraints.maxWidth - placeable.width) / 2
                if (at == null) {
                    placeable.place(x, (constraints.maxHeight - placeable.height) / 2)
                    continue
                }
                val y = top + (at.y / WatchUnits.ACROSS * square).roundToInt()
                val baseline = placeable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified }
                placeable.place(x, if (at.centred || baseline == null) y - placeable.height / 2 else y - baseline)
            }
        }
    }
}

/** Sets a child of [UnitsBox] at [y] units: its first baseline there, or its middle when [centred]. */
internal fun Modifier.atUnits(y: Float, centred: Boolean = false): Modifier = this.then(AtUnits(y, centred))

/**
 * A [Line] of text, drawn as the drawings draw it: centred, in [WatchFonts.manrope] (or [family]),
 * at [y] units (its baseline, or its middle when [centred]), inside the round screen's chord there
 * ([WatchUnits.chordUnits]), sized by [Line.range] with `TextAutoSize` and cut with an ellipsis
 * after its `maxLines`. [tracking] is in units, and dropped for Arabic script ([trackingFor]);
 * [tabular] draws its figures tabular (`tnum`); [inset] is how far, in units, the line keeps from
 * the round screen's edge at each end ([WatchUnits.chordUnits]'s, 6 by default). Must sit in a
 * [UnitsBox].
 *
 * [bottom], for a line set at its baseline, is the lowest height, in units, its lines' box may
 * reach. `TextAutoSize` fits a width and a number of lines, never a height: a block of two lines
 * grows down from its baseline by its line height and its font's descent, which in the watch's
 * Naskh are 1.70 and 0.63 of its size, so it could run into the line beneath it. With [bottom], its
 * lines stand as its fonts set them, held to [Line.maxLineHeight] ([lineSpacing]), and the line is
 * drawn at the largest of `TextAutoSize.StepBased`'s own steps whose lines' box ends above it
 * ([fitAbove]); when none holds it whole, at its floor, in as many lines as end above it.
 */
@Composable
internal fun LineText(
    text: String,
    line: Line,
    y: Float,
    color: Color,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    centred: Boolean = false,
    tracking: Float = 0f,
    tabular: Boolean = false,
    family: FontFamily = WatchFonts.manrope,
    inset: Float = CHORD_INSET,
    bottom: Float? = null,
) {
    require(bottom == null || !centred) { "a bottom is for a line set at its baseline" }
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val sizes = line.range(density.fontScale)
    val width = WatchUnits.chordUnits(farEdge(y, centred, line, sizes.endInclusive), LocalRoundScreen.current, inset)
    val placed = modifier.atUnits(y, centred).width(units.x(width))
    val style = TextStyle(
        color = color,
        fontFamily = family,
        fontWeight = weight,
        textAlign = TextAlign.Center,
        letterSpacing = trackingFor(text, tracking).takeIf { it != 0f }?.let { units.sp(it, density) } ?: 0.sp,
        fontFeatureSettings = if (tabular) "tnum" else null,
    )
    require(bottom != null || line.maxLineHeight == null) { "a line's farthest spacing is fitted with its bottom" }
    if (bottom == null) {
        BasicText(
            text = text,
            modifier = placed,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = line.maxLines,
            autoSize = TextAutoSize.StepBased(
                minFontSize = units.sp(sizes.start, density),
                maxFontSize = units.sp(sizes.endInclusive, density),
                stepSize = units.sp(STEP, density),
            ),
        )
        return
    }
    // The same steps as StepBased's, measured as the line will be drawn (its font, its width, the
    // page's direction), with the room below the baseline as one more limit.
    val measurer = rememberTextMeasurer()
    val widthPx = with(density) { units.x(width).roundToPx() }
    val (spaced, fitted) = remember(
        text, style, sizes, line.maxLines, line.maxLineHeight, bottom - y, widthPx, units, density, measurer,
    ) {
        val unitPx = with(density) { units.x(1f).toPx() }
        // How far apart its fonts set its lines: each line's box is the tallest of the fonts drawn
        // in it (Android's fallback line spacing), measured whole at the largest size.
        val whole = measurer.measure(
            text = text,
            style = style.copy(fontSize = units.sp(sizes.endInclusive, density)),
            constraints = Constraints.fixedWidth(widthPx),
        )
        val tallest = (0 until whole.lineCount).maxOf { whole.getLineBottom(it) - whole.getLineTop(it) }
        val spaced = lineSpacing(own = tallest / (sizes.endInclusive * unitPx), most = line.maxLineHeight)?.let {
            // Compose's own default, written out: the lines stand that far apart, and the block
            // keeps its font's own top and bottom (Trim.Both under Mode.Fixed).
            style.copy(
                lineHeight = it.em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.Both),
            )
        } ?: style
        spaced to fitAbove(sizes, STEP, line.maxLines, room = bottom - y) { size, lines ->
            val laid = measurer.measure(
                text = text,
                style = spaced.copy(fontSize = units.sp(size, density)),
                overflow = TextOverflow.Ellipsis,
                maxLines = lines,
                constraints = Constraints.fixedWidth(widthPx),
            )
            val last = laid.lineCount - 1
            Tried(cut = laid.isLineEllipsized(last), below = (laid.getLineBottom(last) - laid.firstBaseline) / unitPx)
        }
    }
    BasicText(
        text = text,
        modifier = placed,
        style = spaced.copy(fontSize = units.sp(fitted.size, density)),
        overflow = TextOverflow.Ellipsis,
        maxLines = fitted.lines,
    )
}

/** One size tried for [fitAbove]: whether its last line ends in an ellipsis, and how far its lines' box reaches below its first baseline, in units. */
internal data class Tried(val cut: Boolean, val below: Float)

/** The size, in units, and the number of lines a block is drawn with. */
internal data class Fitted(val size: Float, val lines: Int)

/**
 * The largest size of [sizes] at which [measure] lays the text out whole in [maxLines] and its lines
 * end within [room] units below its first baseline. The sizes tried are `TextAutoSize.StepBased`'s:
 * the floor and every [step] above it up to the range's top, so a block with room to spare is drawn
 * exactly as StepBased draws it, and a smaller size is taken never to need more room than a larger.
 * When no size lays it out whole within [room], the block stays at its floor, in as many of its
 * [maxLines] as end within [room], one at least, cut with an ellipsis where its text runs on: a
 * block too wide for its lines even at its floor keeps them all while they fit, and one too tall
 * gives up its last lines until what is left fits.
 */
internal fun fitAbove(
    sizes: ClosedFloatingPointRange<Float>,
    step: Float,
    maxLines: Int,
    room: Float,
    measure: (size: Float, lines: Int) -> Tried,
): Fitted {
    fun sizeAt(k: Int) = sizes.start + k * step
    fun fits(k: Int): Boolean = measure(sizeAt(k), maxLines).let { !it.cut && it.below <= room + EPSILON }
    // The highest step that fits, by halves: steps 0..lo fit as far as is known, and none above hi.
    var lo = -1
    var hi = floor((sizes.endInclusive - sizes.start) / step + EPSILON).toInt()
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (fits(mid)) lo = mid else hi = mid - 1
    }
    if (lo >= 0) return Fitted(sizeAt(lo), maxLines)
    // Cut by its width, by its room or by both: at its floor, the most lines that end within it.
    for (lines in maxLines downTo 2) {
        if (measure(sizes.start, lines).below <= room + EPSILON) return Fitted(sizes.start, lines)
    }
    return Fitted(sizes.start, 1)
}

/**
 * The height, in units, at which a line at [y] comes nearest the round screen's edge, and so where
 * its chord is narrowest: its top or its bottom, whichever is farther from the middle. A line's
 * glyphs reach about 0.8 of its size above the baseline and 0.25 below it; a centred block reaches
 * half its lines' height each way.
 */
internal fun farEdge(y: Float, centred: Boolean, line: Line, size: Float): Float {
    val (above, below) = if (centred) {
        val half = line.maxLines * size * LINE_HEIGHT / 2
        half to half
    } else {
        size * ASCENT to size * DESCENT
    }
    val top = y - above
    val bottom = y + below
    return if (abs(top - WatchUnits.ACROSS / 2) > abs(bottom - WatchUnits.ACROSS / 2)) top else bottom
}

/** How far a line keeps from the round screen's edge at each end, in units: [WatchUnits.chordUnits]'s own default. */
private const val CHORD_INSET = 6f

/** How finely a line's size is searched, in units: only the search's grain, never a rule. */
private const val STEP = 0.5f

/** How near a size's measure may come to its room and still fit, in units: float rounding, never a rule. */
private const val EPSILON = 1e-3f
private const val ASCENT = 0.8f
private const val DESCENT = 0.25f
private const val LINE_HEIGHT = 1.25f
