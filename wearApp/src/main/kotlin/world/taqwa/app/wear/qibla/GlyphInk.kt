package world.taqwa.app.wear.qibla

import java.text.Bidi

/**
 * A glyph's ink: the box its outline fills, y down (Ruling 66 item 4). Never a selection's box,
 * which runs the line's whole height (1.37 of Manrope's em, where a digit's ink is 0.75).
 */
internal data class InkBox(val left: Float, val right: Float, val top: Float, val bottom: Float) {
    fun moved(dx: Float, dy: Float) = InkBox(left + dx, right + dx, top + dy, bottom + dy)
    fun scaled(k: Float) = InkBox(left * k, right * k, top * k, bottom * k)
    val isEmpty: Boolean get() = right <= left || bottom <= top
}

/**
 * A line's glyph ink: each glyph's box about the line's pen, which starts at 0 at its left, and its
 * baseline, in the order the glyphs are drawn from the left; and the line's advance, in the same
 * measure (pixels as the platform shapes it, units or ems as the page reads it).
 */
internal data class Ink(val boxes: List<InkBox>, val advance: Float) {
    fun scaled(k: Float) = Ink(boxes.map { it.scaled(k) }, advance * k)

    /** The boxes about the middle of the advance, where the page centres a line. */
    fun centred(): List<InkBox> = boxes.map { it.moved(-advance / 2, 0f) }
}

/**
 * The platform's shaper for one run of one direction ([GlyphInk]): the ink of `text[start, end)`,
 * shaped right to left when [rtl], its glyphs in the order they are drawn from the left and its pen
 * from 0. On the watch it is [QiblaInk]'s glyph outlines; the host tests give their own.
 */
internal fun interface RunShaper {
    fun shape(text: String, start: Int, end: Int, rtl: Boolean): Ink
}

/** A line's glyph ink, its runs laid out as Android lays a line out: by the Unicode bidirectional algorithm. */
internal object GlyphInk {

    /**
     * [text]'s ink, each of its runs of one direction shaped by [shaper] and set from the left in
     * the order the bidirectional algorithm draws them (`java.text.Bidi`, the platform's ICU). The
     * paragraph's direction is the page's, [rtl]: Compose resolves a text direction it is not given
     * (`TextDirection.Unspecified`) to the layout's, never to the first strong letter's. So an
     * Arabic detail, "٢٣° · ٦٬٦٠٠ كم", is drawn كم first from the left and its numbers left to right
     * inside it, and an Urdu one in Latin "km", "6,600 km", is drawn "km 6,600" (emulator-5558).
     */
    fun of(text: String, rtl: Boolean, shaper: RunShaper): Ink {
        if (text.isEmpty()) return Ink(emptyList(), 0f)
        val bidi = Bidi(text, if (rtl) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT)
        val count = bidi.runCount
        val levels = ByteArray(count) { bidi.getRunLevel(it).toByte() }
        val runs: Array<Any> = Array(count) { it }
        Bidi.reorderVisually(levels, 0, runs, 0, count)
        var pen = 0f
        val boxes = ArrayList<InkBox>()
        for (run in runs) {
            val i = run as Int
            val shaped = shaper.shape(text, bidi.getRunStart(i), bidi.getRunLimit(i), bidi.getRunLevel(i) % 2 == 1)
            for (box in shaped.boxes) if (!box.isEmpty) boxes += box.moved(pen, 0f)
            pen += shaped.advance
        }
        return Ink(boxes, pen)
    }
}
