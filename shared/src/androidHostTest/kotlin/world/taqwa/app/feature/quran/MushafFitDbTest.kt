package world.taqwa.app.feature.quran

import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import world.taqwa.app.quran.DrawnQuran
import world.taqwa.app.quran.LineType

/**
 * Every line of all 604 Mushaf pages, as [MushafPageView] lays it out, measured through the JDK's
 * shaper with the bundled Hafs face, on the shapes that are hardest for the page: the short
 * 408 × 760 dp phone with a three-button bar and the player bar up (spec §19), the narrowest common
 * Android phone, the small and the large iPhone, each at 100 % and at the largest text size.
 *
 * The drawing (HafsSpelling: R1's head of kha, R2's dropped tatweels, R6's lam-alef with hamza and
 * madda, R5 and R4's tanwins, and the table's words, the sajdah's line among them) changes word
 * widths and mark heights, while the page's size is still set from the stored spelling. This
 * holds the drawn page to the frame it is given:
 *
 * - **Width.** The page's own arithmetic ([fittedSize], [fitToHeight], [placeWords], [isJustified])
 *   with Compose's whole-pixel text widths. No line's words may run past the frame's text width, and
 *   no gap between two words may close below a stated floor.
 * - **Height.** Each row is as tall as [fitToHeight] makes it, rows a row apart, every word filled
 *   from its outline where the page places it, and the ink two neighbouring rows share counted, in
 *   the drawn spelling and in the stored one. At the tightest line box (1.6 x) a mark of one row can
 *   graze a mark of the next; the stored text already does on a few rows, and the drawing may add
 *   a graze of a few pixels but never print a mark over another.
 *
 * Every page is measured the way the screen does at the largest text size: so large a base that each
 * page is set by its widest line, which leaves the least slack. At 100 % the pages with room to
 * spare keep their base size, which only adds slack.
 */
class MushafFitDbTest {

    private val frc = FontRenderContext(null, true, true)
    private val font by lazy {
        System.setProperty("java.awt.headless", "true")
        Font.createFont(Font.TRUETYPE_FONT, resource("src/commonMain/composeResources/font/uthmanic_hafs.ttf")).deriveFont(EM)
    }

    /** A text's advance width at [EM] px, cached: the same words recur across shapes. */
    private val cache = HashMap<String, Float>()

    private fun width(text: String): Float = cache.getOrPut(text) {
        val chars = text.toCharArray()
        font.layoutGlyphVector(frc, chars, 0, chars.size, Font.LAYOUT_RIGHT_TO_LEFT).logicalBounds.width.toFloat()
    }

    /**
     * One screen the page is drawn on. [innerHeightDp] is the frame's inner height; null scrolls.
     * [touchingMore] is how many neighbouring row pairs share more ink drawn than stored, as measured
     * on 7 November 2026: a few pixels where a mark of one row grazes a mark of the next at the
     * tightest line box.
     */
    private data class Shape(val name: String, val widthDp: Float, val density: Float, val fontScale: Float, val innerHeightDp: Float?, val touchingMore: Int)

    private val shapes = listOf(
        // spec §19: 1021 × 1900 px at 400 dpi, three-button bar, the player bar up: the frame had 474 dp.
        Shape("408x760 three-button, player bar up, 200 %", 408f, 2.5f, 2f, 474f - 32f, touchingMore = 6),
        Shape("408x760 three-button, player bar up, 100 %", 408f, 2.5f, 1f, 474f - 32f, touchingMore = 6),
        Shape("360x640, 200 %", 360f, 3f, 2f, 400f, touchingMore = 4),
        Shape("iPhone SE 375x667, largest text", 375f, 2f, 3.1f, 420f, touchingMore = 5),
        Shape("iPhone 17 Pro Max 440x956, largest text", 440f, 3f, 3.1f, 680f, touchingMore = 0),
        Shape("landscape 600 dp column, 200 %", 632f, 2.625f, 2f, null, touchingMore = 0),
    )

    private class Outcome {
        var lines = 0
        val overflowing = mutableListOf<String>()
        var tightestGapEm = Float.MAX_VALUE
        var tightestGapAt = ""
        var storedTightestGapEm = Float.MAX_VALUE
        val newlyCrowded = mutableListOf<String>()
        var mostNewInk = 0
        var rowPairs = 0
        var touchingDrawn = 0
        var touchingStored = 0
        val multiples = mutableListOf<Float>()
        val sizes = mutableMapOf<Int, Float>()
    }

    /**
     * The pixels two neighbouring rows both ink, the upper row's baseline [rowPx] above the lower
     * one's, each word filled from its outline at the left edge the page gives it, sampled at pixel
     * centres (Java2D's non-antialiased fill).
     */
    private fun sharedInk(sized: Font, upper: List<Pair<String, Float>>, lower: List<Pair<String, Float>>, framePx: Float, rowPx: Float): Int {
        val height = ceil(rowPx * 3f).toInt()
        val width = ceil(framePx + 4f * sized.size2D).toInt()
        fun ink(row: List<Pair<String, Float>>, baseline: Float): java.awt.image.BufferedImage {
            val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_BYTE_BINARY)
            val g = image.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF)
            g.color = java.awt.Color.WHITE
            for ((text, left) in row) {
                val chars = text.toCharArray()
                val glyphs = sized.layoutGlyphVector(frc, chars, 0, chars.size, Font.LAYOUT_RIGHT_TO_LEFT)
                g.fill(glyphs.getOutline(left + 2f * sized.size2D, baseline))
            }
            g.dispose()
            return image
        }
        val a = ink(upper, rowPx)
        val b = ink(lower, rowPx * 2f)
        val ra = a.raster.dataBuffer as java.awt.image.DataBufferByte
        val rb = b.raster.dataBuffer as java.awt.image.DataBufferByte
        var shared = 0
        for (i in 0 until ra.size) shared += Integer.bitCount((ra.getElem(i) and rb.getElem(i)) and 0xFF)
        return shared
    }

    private fun measure(shape: Shape, rasterise: Boolean): Outcome {
        val out = Outcome()
        val basmala = DrawnQuran.basmala
        val drawnByPage = DrawnQuran.drawnWords.groupBy { it.page }
        val drawnLinesByPage = DrawnQuran.drawnLines.groupBy { it.page }
        // MushafPageView: 16 dp gutters, the column capped at 600 dp, 14 dp of frame on each side.
        val column = minOf(shape.widthDp - 32f, 600f)
        val frameDp = column - 28f
        val base = (28f * (frameDp / 347f)).coerceIn(20f, 34f) * shape.fontScale
        val framePx = frameDp * shape.density
        for (page in DrawnQuran.pages) {
            val textLines = page.lines.filter { it.type == LineType.TEXT }
            fun px(sizeSp: Float, em: Float) = em * sizeSp * shape.density / EM
            val widestAtBase = textLines.maxOf { ceil(px(base, width(it.text.orEmpty()))) }
            val byWidth = fittedSize(base, widestAtBase, framePx)
            val fit = if (shape.innerHeightDp == null) {
                VerticalFit(byWidth, PREFERRED_LINE_MULTIPLE, scrolls = false)
            } else {
                val bands = page.lines.count { it.type == LineType.SURAH }
                val gaps = if (page.number <= 2) (page.lines.size - 1).coerceAtLeast(0) else 0
                fitToHeight(byWidth, page.lines.size - bands, (40f * bands + 6f * gaps) * shape.density, shape.innerHeightDp * shape.density, shape.density)
            }
            val size = fit.sizeSp
            out.sizes[page.number] = size
            val words = drawnByPage.getValue(page.number).groupBy { it.line }
            val drawnLines = drawnLinesByPage.getValue(page.number).associateBy { it.line.line }
            // Width, line by line.
            for (line in textLines) {
                out.lines++
                val drawnWords = words.getValue(line.line).map { it.drawn }
                val widths = drawnWords.map { ceil(px(size, width(it))) }
                val whole = ceil(px(size, width(drawnLines.getValue(line.line).drawn)))
                val gaps = (widths.size - 1).coerceAtLeast(1)
                val naturalGap = ((whole - widths.sum()) / gaps).coerceAtLeast(0f)
                val placed = placeWords(widths, framePx, naturalGap, isJustified(page, line))
                if (placed.last().left < -0.5f) out.overflowing += "p${page.number} l${line.line} by ${-placed.last().left / shape.density} dp"
                placed.zipWithNext().forEach { (right, left) ->
                    val gapEm = (right.left - left.right) / (size * shape.density) * EM
                    if (gapEm < out.tightestGapEm) { out.tightestGapEm = gapEm; out.tightestGapAt = "p${page.number} l${line.line}" }
                }
                // The same line in the stored spelling, for comparison.
                val storedWidths = line.words.map { ceil(px(size, width(it.text))) }
                val storedWhole = ceil(px(size, width(line.text.orEmpty())))
                val storedGap = ((storedWhole - storedWidths.sum()) / gaps).coerceAtLeast(0f)
                placeWords(storedWidths, framePx, storedGap, isJustified(page, line)).zipWithNext().forEach { (right, left) ->
                    out.storedTightestGapEm = minOf(out.storedTightestGapEm, (right.left - left.right) / (size * shape.density) * EM)
                }
            }
            // Height: each row's ink against the next row's, drawn where the page draws them.
            if (rasterise) {
                val placedDrawn = HashMap<Int, List<Pair<String, Float>>>()
                val placedStored = HashMap<Int, List<Pair<String, Float>>>()
                for ((index, line) in page.lines.withIndex()) {
                    when (line.type) {
                        LineType.SURAH -> Unit
                        LineType.BASMALA -> {
                            val drawnBasmala = world.taqwa.app.widget.HafsSpelling.forDrawing(basmala, null, world.taqwa.app.widget.QuranPlace(1, 1))
                            placedDrawn[index] = listOf(drawnBasmala to (framePx - px(size, width(drawnBasmala))) / 2f)
                            placedStored[index] = listOf(basmala to (framePx - px(size, width(basmala))) / 2f)
                        }
                        LineType.TEXT -> {
                            fun place(texts: List<String>, whole: String): List<Pair<String, Float>> {
                                val widths = texts.map { ceil(px(size, width(it))) }
                                val gap = ((ceil(px(size, width(whole))) - widths.sum()) / (widths.size - 1).coerceAtLeast(1)).coerceAtLeast(0f)
                                return texts.zip(placeWords(widths, framePx, gap, isJustified(page, line)).map { it.left })
                            }
                            placedDrawn[index] = place(words.getValue(line.line).map { it.drawn }, drawnLines.getValue(line.line).drawn)
                            placedStored[index] = place(line.words.map { it.text }, line.text.orEmpty())
                        }
                    }
                }
                val rowPx = fit.lineMultiple * size * shape.density
                val sized = font.deriveFont(size * shape.density)
                for (index in 0 until page.lines.size - 1) {
                    val upperDrawn = placedDrawn[index] ?: continue
                    val lowerDrawn = placedDrawn[index + 1] ?: continue
                    val drawnInk = sharedInk(sized, upperDrawn, lowerDrawn, framePx, rowPx)
                    val storedInk = sharedInk(sized, placedStored.getValue(index), placedStored.getValue(index + 1), framePx, rowPx)
                    out.rowPairs++
                    if (drawnInk > 0) out.touchingDrawn++
                    if (storedInk > 0) out.touchingStored++
                    if (drawnInk > storedInk) {
                        out.mostNewInk = maxOf(out.mostNewInk, drawnInk)
                        out.newlyCrowded += "p${page.number} rows ${page.lines[index].line}-${page.lines[index + 1].line}: $drawnInk px shared, $storedInk before"
                    }
                }
                out.multiples += fit.lineMultiple
            }
        }
        return out
    }

    @Test fun everyDrawnLineFitsItsFrameOnEveryShape() {
        val report = StringBuilder()
        val outcomes = shapes.associateWith { measure(it, rasterise = it.innerHeightDp != null) }
        outcomes.forEach { (shape, o) ->
            report.append("${shape.name}: ${o.lines} lines, ${o.overflowing.size} past the frame, tightest gap ${"%.0f".format(o.tightestGapEm)}/1000 em at ${o.tightestGapAt} (stored ${"%.0f".format(o.storedTightestGapEm)}); ")
            report.append("rows at ${o.multiples.minOrNull()}..${o.multiples.maxOrNull()} x the size: ${o.rowPairs} neighbouring pairs, ${o.touchingDrawn} share ink drawn, ${o.touchingStored} stored, ${o.newlyCrowded.size} share more than before; ")
            report.append("sizes ${o.sizes.values.min()}..${o.sizes.values.max()} sp\n")
        }
        File("build/drawn-quran").mkdirs()
        File("build/drawn-quran/mushaf-fit.txt").writeText(report.toString() + outcomes.entries.joinToString("\n") { (s, o) -> "${s.name}\n  past: ${o.overflowing}\n  crowded: ${o.newlyCrowded}" })
        println(report)
        outcomes.forEach { (shape, o) ->
            assertEquals(emptyList(), o.overflowing, "${shape.name}: lines whose drawn words run past the frame")
            assertTrue(o.tightestGapEm >= MIN_GAP_EM, "${shape.name}: a gap closes to ${o.tightestGapEm}/1000 em at ${o.tightestGapAt}")
            // Where the drawing makes two rows meet more than the stored text did, it is a graze of a
            // few device pixels, never a mark printed over another; and it is pinned, so a change
            // to the rules or the page that adds one says so here.
            assertTrue(o.mostNewInk <= MAX_NEW_SHARED_PX, "${shape.name}: rows share up to ${o.mostNewInk} px of ink: ${o.newlyCrowded}")
            assertEquals(shape.touchingMore, o.newlyCrowded.size, "${shape.name}: rows whose ink meets more in the drawn text: ${o.newlyCrowded}")
        }
    }

    private fun resource(path: String): File {
        val moduleRelative = File(path)
        if (moduleRelative.exists()) return moduleRelative
        val rootRelative = File("shared/$path")
        if (rootRelative.exists()) return rootRelative
        error("$path not found from ${File(".").absolutePath}")
    }

    private companion object {
        const val EM = 1000f

        /** The floor for the space between two words, per 1000 em: the drawn page's tightest is 186. */
        const val MIN_GAP_EM = 150f

        /** The most ink two neighbouring rows may newly share: a graze, not an overprint. */
        const val MAX_NEW_SHARED_PX = 10
    }
}
