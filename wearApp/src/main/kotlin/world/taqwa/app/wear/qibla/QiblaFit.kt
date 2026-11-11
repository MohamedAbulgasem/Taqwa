package world.taqwa.app.wear.qibla

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.WatchUnits

/**
 * Where the Qibla page's caption and detail stand, and the detail's size (Ruling 66 items 2 to 5),
 * in the drawing's units, from the glyphs' own ink (item 4: their outlines, [GlyphInk]).
 *
 * - **The half anchor (item 2).** With c the caption's growth by the wearer's font size and d the
 *   detail's drawn size over its 10, the caption's last baseline is 155 − 14·c and the detail's
 *   155 + 14·max(0, d − c): y 141 and 155 at font size 1.0, the approved drawing. So the caption
 *   rises as it grows, and the detail sinks only where it has grown past the caption.
 * - **The size (item 3).** One line always, on a fixed grid of halves: at a font size of 1.0 or less
 *   10, 9.5 or 9; above it from the grown size (floor(20·min(f, 1.3))/2) down by halves to F, the
 *   size the same text takes at 1.0. The size drawn is the first that keeps [LETTER_AIR] from every
 *   turning letter's ink at every heading and stays [LETTER_AIR] inside the ticks; above 1.0 the
 *   letters are judged at their largest ([JUDGED_GROW]), so the size never falls as the font grows.
 *   If none keeps the air, the last is drawn (9 at 1.0, F above it).
 * - **The letters judged** are the feed's `words.cardinals` on every turning dial, Searching before
 *   its first reading among them (`QiblaScreen.dialLetters`), so a reading's arrival never resizes or
 *   moves the detail. A dimmed dial has none, and keeps only to the ticks.
 * - **The gap is exact geometry.** A letter's ink box L, about the letter's middle, touches a glyph's
 *   box G of the detail wherever that middle lies in G − L, and the middles turn on the circle of
 *   [QiblaType.LETTER_RADIUS] (72.05 units out). So the least gap over every heading is 72.05 less
 *   the farthest corner of G − L, where that box lies inside the circle; its nearest point less
 *   72.05 where it lies outside; and below 0 where the circle crosses it.
 * - **The insurance (item 5)** fades a letter by [letterAlpha] at each frame, wherever it is. Where
 *   the size kept the air it is 1 at every heading, so it is idle in all seven languages with the
 *   short forms.
 */
internal object QiblaFit {

    /** How far, in units, the detail's ink keeps from every turning letter's ink and inside the ticks. */
    const val LETTER_AIR = 1f

    /** The growth the turning letters are judged at above the drawing's font size: their cap ([QiblaType.north]). */
    val JUDGED_GROW: Float = QiblaType.north.cap

    /** The detail's baseline at the drawing's size, and how far under the caption's last baseline it stands there. */
    private const val ANCHOR = QiblaType.DETAIL_Y
    private const val RISE = QiblaType.DETAIL_Y - QiblaType.CAPTION_Y

    /** The size grid's step, in units. */
    private const val STEP = 0.5f

    private const val MIDDLE = WatchUnits.ACROSS / 2

    /** How far the wearer's font size grows the dial's letters, as `QiblaPage` draws them: north's largest size over its drawing's (they never shrink). */
    fun letterGrow(fontScale: Float): Float = growth(QiblaType.north, fontScale)

    /** How far the wearer's font size grows [line]: its largest size over its drawing's. */
    fun growth(line: Line, fontScale: Float): Float = line.range(fontScale).endInclusive / line.units

    /** The caption's last baseline under [fontScale]: 155 − 14·c, y 141 at the drawing's size. */
    fun captionY(line: Line, fontScale: Float): Float = ANCHOR - RISE * growth(line, fontScale)

    /** The detail's baseline, at [size], under the caption [line]: 155 + 14·max(0, d − c), y 155 at the drawing's size. */
    fun detailY(line: Line, fontScale: Float, size: Float): Float =
        ANCHOR + RISE * max(0f, size / QiblaType.detail.units - growth(line, fontScale))

    /** The sizes the detail is tried at under [fontScale], from the largest: 10, 9.5 and 9 at 1.0 or less; above it, from the grown size's half down to [atOne], the size at 1.0. */
    fun sizes(fontScale: Float, atOne: Float): List<Float> {
        val detail = QiblaType.detail
        if (fontScale <= 1f) return generateSequence(detail.units) { it - STEP }.takeWhile { it >= detail.floor - EPSILON }.toList()
        val top = floor(detail.range(fontScale).endInclusive / STEP + EPSILON) * STEP
        return generateSequence(top) { it - STEP }.takeWhile { it >= atOne - EPSILON }.toList()
    }

    /**
     * The detail as the page draws it: its [size] and baseline [y] in units, its glyphs' [ink] on the
     * dial (units from the drawing's top left, y down), and how far that ink keeps from the letters
     * it was judged against ([letterGap], infinite with none) and inside the ticks ([tickGap]).
     */
    data class Detail(val size: Float, val y: Float, val ink: List<InkBox>, val letterGap: Float, val tickGap: Float) {
        val keepsAir: Boolean get() = letterGap >= LETTER_AIR && tickGap >= LETTER_AIR
    }

    /**
     * The detail under the caption [caption] at [fontScale] (items 2 and 3). [ink] gives its text's
     * glyph ink at a size, in units about its pen and baseline; [letters] gives the turning letters'
     * ink about their middles at a growth (none on a dimmed dial).
     */
    fun detail(caption: Line, fontScale: Float, ink: (size: Float) -> Ink, letters: (grow: Float) -> List<List<InkBox>>): Detail {
        val atOne = first(caption, 1f, sizes(1f, atOne = QiblaType.detail.floor), ink, letters(1f))
        if (fontScale <= 1f) return atOne
        return first(caption, fontScale, sizes(fontScale, atOne.size), ink, letters(JUDGED_GROW))
    }

    /** The first of [sizes] whose detail keeps the air, or the last when none does. */
    private fun first(caption: Line, fontScale: Float, sizes: List<Float>, ink: (Float) -> Ink, letters: List<List<InkBox>>): Detail {
        var last: Detail? = null
        for (size in sizes) {
            val placed = placed(caption, fontScale, size, ink(size), letters)
            if (placed.keepsAir) return placed
            last = placed
        }
        return checkNotNull(last) { "no size to try at $fontScale" }
    }

    /** The detail at [size] under [caption]: [ink] centred across the dial and set at its baseline. */
    fun placed(caption: Line, fontScale: Float, size: Float, ink: Ink, letters: List<List<InkBox>>): Detail {
        val y = detailY(caption, fontScale, size)
        val boxes = ink.centred().map { it.moved(MIDDLE, y) }
        return Detail(size, y, boxes, letterGap(boxes, letters), tickGap(boxes))
    }

    /** A dial letter's ink about its middle: its glyphs' [ink] (units, about its pen) drawn with the pen [penStart] from the left of a layout [width] wide, centred on the middle, its baseline LETTER_DROP below it, grown by [grow], as `QiblaPage` draws it. */
    fun letterInk(ink: Ink, width: Float, penStart: Float, grow: Float): List<InkBox> =
        ink.boxes.map { it.moved(penStart - width / 2, QiblaDialMeasures.LETTER_DROP * QiblaDialMeasures.K * grow) }

    /** The least gap, over every heading, between the detail's [detail] ink (on the dial) and any of the [letters] (each about its middle): exact, see [QiblaFit]. */
    fun letterGap(detail: List<InkBox>, letters: List<List<InkBox>>): Float {
        var least = Float.POSITIVE_INFINITY
        for (letter in letters) for (l in letter) for (g in detail) least = min(least, circleGap(g, l))
        return least
    }

    /**
     * The letters' room for a caption line set at its baseline [y] (Ruling 68 item 4): the widest
     * box centred across the dial, from [top] to [bottom] about that baseline (its glyphs' highest
     * and lowest ink, in units), whose every point keeps [LETTER_AIR] from every one of the [letters]
     * (each its ink about its middle) at every heading. By [QiblaFit]'s exact geometry: a letter's box
     * L meets the line's box wherever its middle lies in G − L, whose farthest corner is
     * √((w/2 + a)² + Y²) out, with a the letter's widest reach across and Y the farther of the box's
     * two heights from the middle; so w/2 = √((r − air)² − Y²) − a, r the letters' circle. A box
     * wholly beyond the circle and its air is clear at any width (infinite), and one the circle's air
     * crosses at the middle has no room (0).
     */
    fun captionRoom(y: Float, top: Float, bottom: Float, letters: List<List<InkBox>>): Float {
        val inner = QiblaType.LETTER_RADIUS - LETTER_AIR
        var half = Float.POSITIVE_INFINITY
        for (letter in letters) for (l in letter) {
            val y0 = y + top - l.bottom - MIDDLE
            val y1 = y + bottom - l.top - MIDDLE
            val near = if (y0 > 0) y0 else if (y1 < 0) -y1 else 0f
            if (near >= QiblaType.LETTER_RADIUS + LETTER_AIR) continue
            val far = max(abs(y0), abs(y1))
            if (far >= inner) return 0f
            half = min(half, sqrt(inner * inner - far * far) - max(l.right, -l.left))
        }
        return if (half == Float.POSITIVE_INFINITY) half else max(0f, 2 * half)
    }

    /** How far inside the ticks' circle ([QiblaType.TICK_ROOM]) the farthest corner of the [detail]'s ink keeps. */
    fun tickGap(detail: List<InkBox>): Float {
        if (detail.isEmpty()) return Float.POSITIVE_INFINITY
        var far = 0f
        for (b in detail) far = max(far, farthest(b.left - MIDDLE, b.right - MIDDLE, b.top - MIDDLE, b.bottom - MIDDLE))
        return QiblaType.TICK_ROOM - far
    }

    /**
     * Item 5: how opaque a turning letter is drawn with its middle at [x], [y] (units on the dial):
     * clamp(gap / [LETTER_AIR], 0, 1), the gap its ink keeps from the [detail]'s, [letter] its ink
     * about its middle; 0 where its middle lies inside G − L, so it would sit on the ink.
     */
    fun letterAlpha(x: Float, y: Float, detail: List<InkBox>, letter: List<InkBox>): Float {
        var gap = Float.POSITIVE_INFINITY
        for (l in letter) for (g in detail) {
            val dx = max(max(g.left - l.right - x, x - (g.right - l.left)), 0f)
            val dy = max(max(g.top - l.bottom - y, y - (g.bottom - l.top)), 0f)
            gap = min(gap, sqrt(dx * dx + dy * dy))
        }
        return (gap / LETTER_AIR).coerceIn(0f, 1f)
    }

    /** The gap over the letters' circle between [g] (on the dial) and [l] (about a letter's middle): signed, below 0 where they meet at some heading. */
    private fun circleGap(g: InkBox, l: InkBox): Float {
        val x0 = g.left - l.right - MIDDLE
        val x1 = g.right - l.left - MIDDLE
        val y0 = g.top - l.bottom - MIDDLE
        val y1 = g.bottom - l.top - MIDDLE
        val far = farthest(x0, x1, y0, y1)
        val nx = if (x0 > 0) x0 else if (x1 < 0) x1 else 0f
        val ny = if (y0 > 0) y0 else if (y1 < 0) y1 else 0f
        val near = sqrt(nx * nx + ny * ny)
        val r = QiblaType.LETTER_RADIUS
        return when {
            far < r -> r - far
            near > r -> near - r
            else -> -min(far - r, r - near)
        }
    }

    /** The farthest corner from the middle of the box [x0]..[x1], [y0]..[y1], about the middle. */
    private fun farthest(x0: Float, x1: Float, y0: Float, y1: Float): Float {
        val x = max(x0 * x0, x1 * x1)
        val y = max(y0 * y0, y1 * y1)
        return sqrt(x + y)
    }

    private const val EPSILON = 1e-3f
}
