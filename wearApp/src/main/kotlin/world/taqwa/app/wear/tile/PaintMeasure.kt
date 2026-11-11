package world.taqwa.app.wear.tile

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint

/**
 * The Tile's texts, measured in this process before the Tile is sent ([TileMeasure]), as the Tile
 * renderer's text views will draw them: in the font it draws in (call 7), at the drawing's weight,
 * with tabular figures for the times as the layout asks for them.
 *
 * A text's width is measured at the size it is drawn, in the screen's pixels ([pixelsPerUnit] to
 * a unit), since a text view's paint hints each advance to a whole pixel there: a review's probe on
 * the Wear OS 6 emulator (9 November 2026) found the figure space 11 px at 20.81 px where its
 * linear advance is 11.7, so the start's time held wide with nine of them, measured at 1000 px and
 * scaled, came out 9 px narrower in the renderer's frame than it was fitted to. The font is the
 * renderer's own: from Android 16 (API 36) ProtoLayout's renderer draws every Tile in the system
 * surfaces' font, whatever the layout asks (`ProtoLayoutThemeImpl.getFontSet`, renderer 1.3), with
 * the bold style for a weight of 700 and over and the weight on the font's axis
 * (`ProtoLayoutInflater.applyFontStyle`), and so it is measured here; below, in the system's
 * default font at the drawing's weight, as before.
 *
 * A line's extent is the line a text view sets the text in: one line of a `StaticLayout` that takes
 * its spacing from the fallback fonts its letters are drawn in, as a text view does (the system's
 * Naskh for Arabic and Urdu, whose line is far taller than Roboto's), with the fonts' own padding
 * and without it, measured at 1000 px and scaled. `StaticLayout` gives that from API 28, below the
 * watch app's 30; the run's own metrics (`Paint.getFontMetricsInt` over a text) only from 33, past
 * a Galaxy on Wear OS 3.
 */
internal class PaintMeasure(private val pixelsPerUnit: Float) : TileMeasure {

    private val paints = HashMap<Pair<Int, Boolean>, TextPaint>()

    private fun paint(weight: Int, tabular: Boolean): TextPaint = paints.getOrPut(weight to tabular) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            if (Build.VERSION.SDK_INT >= SYSTEM_FONT_FROM) {
                // As the renderer sets a text view: Typeface.create(normal, style), then
                // TextView.setTypeface(typeface, style), which creates it again in that style and
                // fakes the bold only where the font has none, then the weight on its axis.
                val style = if (weight >= 700) Typeface.BOLD else Typeface.NORMAL
                val chosen = Typeface.create(Typeface.create(Typeface.create(SYSTEM_FONT, Typeface.NORMAL), style), style)
                typeface = chosen
                isFakeBoldText = style == Typeface.BOLD && !chosen.isBold
                fontVariationSettings = "'wght' $weight"
            } else {
                typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            }
            if (tabular) fontFeatureSettings = "tnum"
        }
    }

    override fun width(text: String, units: Float, weight: Int, tabular: Boolean): Float {
        val paint = paint(weight, tabular)
        paint.textSize = units * pixelsPerUnit
        return paint.measureText(text) / pixelsPerUnit
    }

    override fun extent(text: String, units: Float, weight: Int): TextExtent {
        val paint = paint(weight, tabular = false)
        paint.textSize = SIZE
        fun line(includePad: Boolean): StaticLayout {
            val width = paint.measureText(text).toInt() + 2 * SIZE.toInt()
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setIncludePad(includePad)
                .setUseLineSpacingFromFallbacks(true)
                .setMaxLines(1)
                .build()
        }
        val bare = line(includePad = false)
        val padded = line(includePad = true)
        val scale = units / SIZE
        return TextExtent(
            above = (bare.getLineBaseline(0) - bare.getLineTop(0)) * scale,
            below = (bare.getLineBottom(0) - bare.getLineBaseline(0)) * scale,
            tall = maxOf(bare.height, padded.height) * scale,
        )
    }

    private companion object {
        const val SIZE = 1000f

        /** The family ProtoLayout's renderer draws a Tile in from Android 16 (`ProtoLayoutThemeImpl.SYSTEM_FONT_ALIAS`). */
        const val SYSTEM_FONT = "font-family-system-surfaces-device-default"

        /** Android 16, from which the renderer draws in [SYSTEM_FONT] (`Utils.isAtLeastBaklava`). */
        const val SYSTEM_FONT_FROM = 36
    }
}
