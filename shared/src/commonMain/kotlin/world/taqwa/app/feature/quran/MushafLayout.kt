package world.taqwa.app.feature.quran

import world.taqwa.app.quran.LineType
import world.taqwa.app.quran.MushafLine
import world.taqwa.app.quran.MushafPage
import kotlin.math.floor

/**
 * The pure arithmetic behind a printed Mushaf page (spec §2.4): one font size for the whole page,
 * chosen so its widest line just fits the frame, and every line's words spread across the frame's
 * width. Nothing here touches Compose, so [MushafPageView] stays a thin drawing layer over rules
 * that are unit-tested on their own.
 */

/** Font sizes snap to this step so two pages of similar density do not land on two sizes a
 * hair apart. */
private const val SIZE_STEP_SP = 0.5f

/**
 * The size every line of a page is set at: [base] (the width-derived default, spec §5.1) unless
 * the page's widest line, measured at [base] as [widestAtBase] pixels, would not fit [frameWidth]
 * pixels — then the largest half-step below the size at which it exactly does. Text width scales
 * linearly with font size, so one measurement per line at [base] is enough to know the answer.
 */
internal fun fittedSize(base: Float, widestAtBase: Float, frameWidth: Float): Float {
    if (widestAtBase <= 0f || widestAtBase <= frameWidth) return base
    val exact = base * frameWidth / widestAtBase
    return floor(exact / SIZE_STEP_SP) * SIZE_STEP_SP
}

/**
 * How a page's rows sit in the frame's height: the size they are drawn at, each text row's height
 * as a multiple of that size, and whether the rows have to scroll because nothing fits.
 */
internal data class VerticalFit(val sizeSp: Float, val lineMultiple: Float, val scrolls: Boolean)

/** The line box the page is set at when there is room: 1.9× the font size (spec §2.4). */
internal const val PREFERRED_LINE_MULTIPLE = 1.9f

/**
 * The tightest a line box goes. The Hafs face carries its marks well above and below the
 * letters; closer than this and one line's kasra meets the next line's fatha.
 */
internal const val TIGHTEST_LINE_MULTIPLE = 1.6f

/** Below this the page is no longer read but deciphered, and scrolling it is kinder. */
internal const val SMALLEST_PAGE_SP = 14f

/**
 * Makes the page fit the frame's **height** as well as its width (spec §19).
 *
 * The size comes from the width ([fittedSize]), and until 1.0.0 (31) nothing asked whether
 * fifteen rows of 1.9× that size fit the height too. On a tall phone they do. On a short, wide one
 * — a 408 × 760 dp screen with a three-button bar and the player bar up — they do not, and the
 * column handed its last rows whatever height was left: four lines of the Quran drawn on top of
 * each other.
 *
 * Three steps, each taken only if the one before it is not enough. Keep the size and the 1.9×
 * line box. Keep the size and close the lines up, no tighter than [TIGHTEST_LINE_MULTIPLE] — the
 * printed page is set closer than 1.9×, and the reader loses nothing but air. Then shrink the
 * size, in the same half-steps as [fittedSize], until the rows fit at the tightest box. Should
 * that ask for less than [SMALLEST_PAGE_SP], the page scrolls at its width-fitted size instead,
 * as it already does sideways.
 *
 * @param sizeSp the width-fitted size.
 * @param textRows the rows whose height follows the font: text lines and the basmala.
 * @param fixedPx the height of everything that does not: surah bands, and the gaps pages 1–2 keep.
 * @param availablePx the frame's inner height.
 * @param pxPerSp the density and the reader's font scale together.
 */
internal fun fitToHeight(
    sizeSp: Float,
    textRows: Int,
    fixedPx: Float,
    availablePx: Float,
    pxPerSp: Float,
): VerticalFit {
    val asSet = VerticalFit(sizeSp, PREFERRED_LINE_MULTIPLE, scrolls = false)
    if (textRows <= 0 || sizeSp <= 0f || pxPerSp <= 0f || availablePx <= 0f) return asSet
    // A pixel a row held back: each row's height is rounded to whole pixels when it is placed,
    // and fifteen round-ups must not add up to a row pushed past the frame.
    val room = availablePx - fixedPx - textRows
    fun multipleThatFills(size: Float) = room / (textRows * size * pxPerSp)

    val atSize = multipleThatFills(sizeSp)
    if (atSize >= PREFERRED_LINE_MULTIPLE) return asSet
    if (atSize >= TIGHTEST_LINE_MULTIPLE) return VerticalFit(sizeSp, atSize, scrolls = false)

    val exact = room / (textRows * TIGHTEST_LINE_MULTIPLE * pxPerSp)
    val smaller = floor(exact / SIZE_STEP_SP) * SIZE_STEP_SP
    if (smaller < SMALLEST_PAGE_SP) return VerticalFit(sizeSp, PREFERRED_LINE_MULTIPLE, scrolls = true)
    // The half-step down leaves a little room over; the lines take it back rather than the
    // page ending short of its frame.
    return VerticalFit(smaller, multipleThatFills(smaller).coerceAtMost(PREFERRED_LINE_MULTIPLE), scrolls = false)
}

/**
 * Spec §2.4: text lines are set justified to the frame width, except a line that ends a surah —
 * which is set to the start, as the printed page does — and every line of pages 1 and 2, the two
 * short opening pages, which the printed Mushaf does not fill either.
 */
internal fun isJustified(page: MushafPage, line: MushafLine): Boolean =
    line.type == LineType.TEXT && !line.endsSurah && page.number > 2

/** One word's horizontal span on its line, in pixels from the line's left edge. [right] is
 * exclusive: `right - left` is exactly the word's measured width. */
internal data class PlacedWord(val left: Float, val right: Float)

/**
 * Where each word of a right-to-left line sits (spec §2.4). The first word's right edge is the
 * line's right edge; each following word is placed to the left of the previous one, separated by
 * a gap. A justified line divides its slack evenly between the gaps so the last word ends on the
 * left edge, the way a printed line is filled; an unjustified line keeps [naturalGap], the width
 * of one space in the same font and size. A justified line whose words already exceed the width —
 * which the page size makes impossible bar sub-pixel rounding — falls back to touching words rather
 * than negative gaps, so nothing is ever drawn over its neighbour.
 */
internal fun placeWords(widths: List<Float>, lineWidth: Float, naturalGap: Float, justify: Boolean): List<PlacedWord> {
    if (widths.isEmpty()) return emptyList()
    val gaps = widths.size - 1
    val gap = when {
        gaps == 0 -> 0f
        justify -> ((lineWidth - widths.sum()) / gaps).coerceAtLeast(0f)
        else -> naturalGap
    }
    var right = lineWidth
    return widths.map { width ->
        val placed = PlacedWord(right - width, right)
        right -= width + gap
        placed
    }
}

/**
 * The index of the word a tap at [x] pixels landed on, or null when it fell in the slack an
 * unjustified line leaves at its left end. The gap after a word — to its left, on a right-to-left
 * line — counts as that word, so no tap between two words falls through (spec §2.4).
 */
internal fun wordAtX(placed: List<PlacedWord>, x: Float, lineWidth: Float): Int? {
    placed.forEachIndexed { index, word ->
        val upper = if (index == 0) lineWidth else word.right
        val lower = placed.getOrNull(index + 1)?.right ?: word.left
        if (x >= lower && x <= upper) return index
    }
    return null
}

/**
 * The runs of consecutive words for which [marked] is true, as `first..last` index ranges. The
 * highlight is drawn per run rather than per word so it spans the gaps between an ayah's words
 * and reads as one field behind the ayah rather than a row of stripes.
 */
internal fun markedRuns(count: Int, marked: (Int) -> Boolean): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    for (i in 0 until count) {
        if (marked(i)) {
            if (start < 0) start = i
        } else if (start >= 0) {
            runs += start until i
            start = -1
        }
    }
    if (start >= 0) runs += start until count
    return runs
}
