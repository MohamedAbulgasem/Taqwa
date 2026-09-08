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
