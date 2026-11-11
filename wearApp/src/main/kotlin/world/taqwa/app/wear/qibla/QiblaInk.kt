package world.taqwa.app.wear.qibla

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.text.PositionedGlyphs
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.BreakIterator

/**
 * The watch's own measure of the Qibla page's glyph ink (Ruling 66 item 4): the glyphs' outlines,
 * never `TextLayoutResult.getPathForRange`, whose boxes are the line's selection rectangles, about
 * 1.37 of Manrope's em tall where a digit's ink is 0.75 (`QiblaFitTest`'s guard).
 *
 * - **API 31 and later** (every watch Wear OS 4 reaches): each run of one direction is shaped by
 *   `TextRunShaper.shapeTextRun`, with the whole text as its context, in the page's own typeface
 *   and its system fallbacks, so the Arabic is joined and the figures Manrope lacks come from the
 *   fonts that draw them. Each glyph's box is its own font's `getGlyphBounds`, at its `getGlyphX`
 *   and `getGlyphY`; a variable font's weight the shaper sets apart from the font (API 35's
 *   `getWeightOverride`) is set on it first. The bounds are read at [OUTLINE_PX] and scaled to the
 *   size drawn: at the size drawn they are the glyph's mask rounded out to whole pixels (a digit
 *   0.79 of the em at 10 units, where its outline is 0.75), up to a pixel more a side than the
 *   outline the host tests measure, which would part the watch's layout from theirs.
 * - **API 30** (Wear OS 3, `wearApp`'s minSdk): `Paint.getTextPath` for each grapheme cluster, at
 *   the place the run's advances give it, and the path's bounds. A cluster shaped alone loses its
 *   joining, so an Arabic letter is measured in its isolated form, which is larger.
 *
 * Runs are laid out by [GlyphInk], and every line is remembered by its text, weight, size and the
 * page's direction, so a frame never measures.
 */
internal class QiblaInk(private val typefaces: (weight: Int) -> Typeface, private val unitPx: Float) {

    private companion object {
        /** The size a glyph's bounds are read at, in pixels, so that its whole-pixel rounding is a thousandth of the em. */
        const val OUTLINE_PX = 1_000f
    }

    private data class Key(val text: String, val weight: Int, val size: Float, val rtl: Boolean)

    private val remembered = HashMap<Key, Ink>()

    /** [text]'s ink at [size] units in Manrope's [weight] (600 or 800), the page's direction [rtl]: units about its pen and baseline. */
    fun line(text: String, weight: Int, size: Float, rtl: Boolean): Ink = remembered.getOrPut(Key(text, weight, size, rtl)) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaces(weight)
            textSize = size * unitPx
        }
        val shaper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Outlines(paint, Paint(paint).apply { textSize = OUTLINE_PX }) else Paths(paint)
        GlyphInk.of(text, rtl, shaper).scaled(1f / unitPx)
    }

    /** [paint] shapes at the size drawn; [outline], the same at [OUTLINE_PX], gives each glyph's bounds, scaled to it. */
    @RequiresApi(Build.VERSION_CODES.S)
    private class Outlines(private val paint: Paint, private val outline: Paint) : RunShaper {
        private val bounds = RectF()
        private val scale = paint.textSize / outline.textSize
        private val weighted = HashMap<Pair<Font, Float>, Font>()

        override fun shape(text: String, start: Int, end: Int, rtl: Boolean): Ink {
            val glyphs = TextRunShaper.shapeTextRun(text, start, end - start, 0, text.length, 0f, 0f, rtl, paint)
            val boxes = ArrayList<InkBox>(glyphs.glyphCount())
            for (i in 0 until glyphs.glyphCount()) {
                fontOf(glyphs, i).getGlyphBounds(glyphs.getGlyphId(i), outline, bounds)
                if (bounds.isEmpty) continue
                val x = glyphs.getGlyphX(i)
                val y = glyphs.getGlyphY(i)
                boxes += InkBox(x + bounds.left * scale, x + bounds.right * scale, y + bounds.top * scale, y + bounds.bottom * scale)
            }
            return Ink(boxes, glyphs.advance)
        }

        /** The glyph's font, with the weight the shaper asks of it where it asks one apart from the font (a variable fallback, API 35). */
        private fun fontOf(glyphs: PositionedGlyphs, i: Int): Font {
            val font = glyphs.getFont(i)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return font
            val weight = glyphs.getWeightOverride(i)
            if (weight == PositionedGlyphs.NO_OVERRIDE) return font
            return weighted.getOrPut(font to weight) {
                runCatching { Font.Builder(font).setFontVariationSettings("'wght' $weight").build() }.getOrDefault(font)
            }
        }
    }

    private class Paths(private val paint: Paint) : RunShaper {
        private val path = Path()
        private val bounds = RectF()

        override fun shape(text: String, start: Int, end: Int, rtl: Boolean): Ink {
            val chars = text.toCharArray()
            val advances = FloatArray(end - start)
            val total = paint.getTextRunAdvances(chars, start, end - start, 0, chars.size, rtl, advances, 0)
            val clusters = BreakIterator.getCharacterInstance().apply { setText(text.substring(start, end)) }
            val boxes = ArrayList<InkBox>()
            var from = clusters.first()
            var to = clusters.next()
            while (to != BreakIterator.DONE) {
                var before = 0f
                for (k in 0 until from) before += advances[k]
                var width = 0f
                for (k in from until to) width += advances[k]
                val x = if (rtl) total - before - width else before
                path.reset()
                paint.getTextPath(text, start + from, start + to, x, 0f, path)
                path.computeBounds(bounds, true)
                if (!bounds.isEmpty) boxes += InkBox(bounds.left, bounds.right, bounds.top, bounds.bottom)
                from = to
                to = clusters.next()
            }
            return Ink(boxes, total)
        }
    }
}
