package world.taqwa.app.wear.tile

/**
 * Stand-ins for the watch's own measure of the Tile's texts (`PaintMeasure`), for the JVM tests.
 *
 * A line's extent is its fonts', as the emulator's fonts set it (hhea ascent and descent, which
 * Android's text layout uses): Roboto's 0.928 and 0.244 of the em, and for Arabic script the
 * system's Naskh (NotoNaskhArabic, 1.069 and 0.634), which the renderer's fallback line spacing
 * makes the line's. Bengali is given the Naskh's too, so a test never asks less room of it than the
 * watch does. A text view is as tall as its line, its baseline its ascent below its top.
 */
internal object TestMeasures {

    private val LATIN_LINE = TextExtent(above = 0.928f, below = 0.244f, tall = 0.928f + 0.244f)
    private val NASKH_LINE = TextExtent(above = 1.069f, below = 0.634f, tall = 1.069f + 0.634f)

    /** Whether [c] is drawn in a fallback font with a tall line: Arabic script or Bengali. */
    fun isTall(c: Char): Boolean = c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ࢠ'..'ࣿ' ||
        c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿' || isBengali(c)

    private fun isBengali(c: Char): Boolean = c in 'ঀ'..'৿'

    /** The line of [text] at [units], in units. */
    fun extent(text: String, units: Float): TextExtent {
        val line = if (text.any(::isTall)) NASKH_LINE else LATIN_LINE
        return TextExtent(line.above * units, line.below * units, line.tall * units)
    }

    /** Rough widths, half an em and a twentieth for every character, with [extent]'s lines. */
    val ROUGH: TileMeasure = object : TileMeasure {
        override fun width(text: String, units: Float, weight: Int, tabular: Boolean): Float = text.length * units * 0.55f
        override fun extent(text: String, units: Float, weight: Int): TextExtent = extent(text, units)
    }

    /**
     * Roboto's own widths: the advances of the emulator's system font (Roboto-Regular.ttf, the
     * variable Roboto of Wear OS 6, from emulator-5558's /system/fonts), shaped one character at a
     * time by HarfBuzz at weight 800 for the label and 700 with tabular figures for the countdown
     * (`hb-shape --variations=wght=...`, 30 October 2026). Summed without kerning, they come within
     * 0.1 unit of the whole line shaped ("MAGHRIB" and "in 2 h 54 min": 133.6 units to HarfBuzz's
     * 133.7). A weight under 800 takes the 700 table. A letter in Arabic script is 0.5 of the em
     * and a Bengali one 0.6, wider than the Naskh's own (0.40 and 0.44 a letter in the Arabic and
     * Urdu countdowns). Any other letter throws, so that a word the table has not been taught
     * cannot pass by it. The figure space and the no-break space the countdown's static value is
     * held wide with (`heldClock`), and the two direction marks, which have no advance, were shaped
     * the same way on 9 November 2026, from the Roboto Android Studio's layoutlib ships, whose
     * advances are the emulator's (its 0, : and space are the table's).
     */
    val ROBOTO: TileMeasure = object : TileMeasure {
        override fun width(text: String, units: Float, weight: Int, tabular: Boolean): Float {
            val table = if (weight >= 800) HEAVY else BOLD
            return text.sumOf { c ->
                val em = when {
                    isBengali(c) -> 0.6f
                    isTall(c) -> 0.5f
                    else -> table[c] ?: throw IllegalArgumentException("no Roboto advance for '$c' (U+${"%04X".format(c.code)})")
                }
                em.toDouble()
            }.toFloat() * units
        }

        override fun extent(text: String, units: Float, weight: Int): TextExtent = extent(text, units)
    }

    /**
     * [ROBOTO]'s widths as a text view's paint gives them at the size it draws, on a screen of
     * [pixelsPerUnit] pixels a unit: each letter's advance a whole number of pixels, as the
     * renderer's hinted paint gives it (a review's probe on the Wear OS 6 emulator, 9 November
     * 2026: at 20.81 px the figure space was 11 px where its linear advance is 11.7, so nine of
     * them came to 99). Rounded to the nearest pixel here, which is not the hinter's own rule: what
     * it holds is that a text of many like spaces does not grow as its linear width does.
     */
    fun hinted(pixelsPerUnit: Float): TileMeasure = object : TileMeasure {
        override fun width(text: String, units: Float, weight: Int, tabular: Boolean): Float =
            text.sumOf { c -> Math.round(ROBOTO.width(c.toString(), units, weight, tabular) * pixelsPerUnit).toDouble() }.toFloat() / pixelsPerUnit

        override fun extent(text: String, units: Float, weight: Int): TextExtent = extent(text, units)
    }

    private const val CHARS ="ABCDEFGHIJKLMNOPQRSTUVWXYZİŞĞÖÜÇÂÊÎÔÛÉÈÀʿ’ abcdefghijklmnopqrstuvwxyzışğöüçéèàâêîôûʼ0123456789:.," +
        "\u2007\u00A0\u200E\u200F"

    private val HEAVY: Map<Char, Float> = CHARS.toList().zip(
        listOf(
            0.6763f, 0.6421f, 0.6553f, 0.6484f, 0.5610f, 0.5479f, 0.6816f, 0.7046f, 0.2974f, 0.5605f,
            0.6377f, 0.5415f, 0.8760f, 0.7041f, 0.6899f, 0.6479f, 0.6899f, 0.6465f, 0.6206f, 0.6255f,
            0.6621f, 0.6572f, 0.8721f, 0.6367f, 0.6235f, 0.6084f, 0.2974f, 0.6206f, 0.6816f, 0.6899f,
            0.6621f, 0.6553f, 0.6763f, 0.5610f, 0.2974f, 0.6899f, 0.6621f, 0.5610f, 0.5610f, 0.6763f,
            0.1792f, 0.2363f, 0.2490f, 0.5332f, 0.5630f, 0.5205f, 0.5625f, 0.5425f, 0.3604f, 0.5732f,
            0.5620f, 0.2700f, 0.2656f, 0.5405f, 0.2700f, 0.8638f, 0.5625f, 0.5635f, 0.5630f, 0.5630f,
            0.3726f, 0.5132f, 0.3413f, 0.5625f, 0.5112f, 0.7300f, 0.5122f, 0.5107f, 0.5122f, 0.2793f,
            0.5132f, 0.5732f, 0.5635f, 0.5625f, 0.5205f, 0.5425f, 0.5425f, 0.5332f, 0.5332f, 0.5425f,
            0.2793f, 0.5635f, 0.5625f, 0.2363f, 0.5767f, 0.5767f, 0.5767f, 0.5767f, 0.5767f, 0.5767f,
            0.5767f, 0.5767f, 0.5767f, 0.5767f, 0.2910f, 0.2964f, 0.2578f,
            0.5620f, 0.2490f, 0f, 0f,
        ),
    ).toMap()

    private val BOLD: Map<Char, Float> = CHARS.toList().zip(
        listOf(
            0.6719f, 0.6382f, 0.6543f, 0.6499f, 0.5625f, 0.5488f, 0.6816f, 0.7061f, 0.2925f, 0.5586f,
            0.6357f, 0.5410f, 0.8755f, 0.7056f, 0.6895f, 0.6445f, 0.6895f, 0.6406f, 0.6157f, 0.6196f,
            0.6592f, 0.6533f, 0.8750f, 0.6348f, 0.6191f, 0.6069f, 0.2925f, 0.6157f, 0.6816f, 0.6895f,
            0.6592f, 0.6543f, 0.6719f, 0.5625f, 0.2925f, 0.6895f, 0.6592f, 0.5625f, 0.5625f, 0.6719f,
            0.1714f, 0.2295f, 0.2485f, 0.5352f, 0.5625f, 0.5210f, 0.5630f, 0.5400f, 0.3579f, 0.5713f,
            0.5601f, 0.2646f, 0.2603f, 0.5342f, 0.2646f, 0.8662f, 0.5605f, 0.5645f, 0.5625f, 0.5640f,
            0.3662f, 0.5137f, 0.3384f, 0.5601f, 0.5063f, 0.7344f, 0.5088f, 0.5034f, 0.5088f, 0.2729f,
            0.5137f, 0.5713f, 0.5645f, 0.5601f, 0.5210f, 0.5400f, 0.5400f, 0.5352f, 0.5352f, 0.5400f,
            0.2729f, 0.5645f, 0.5601f, 0.2295f, 0.5737f, 0.5737f, 0.5737f, 0.5737f, 0.5737f, 0.5737f,
            0.5737f, 0.5737f, 0.5737f, 0.5737f, 0.2817f, 0.2900f, 0.2461f,
            0.5620f, 0.2485f, 0f, 0f,
        ),
    ).toMap()

    init {
        check(HEAVY.size == CHARS.length && BOLD.size == CHARS.length) { "an advance for every character of the table" }
    }
}
