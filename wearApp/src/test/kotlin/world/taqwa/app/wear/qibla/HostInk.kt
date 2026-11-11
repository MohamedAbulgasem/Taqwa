package world.taqwa.app.wear.qibla

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The watch's glyph ink on the host (Ruling 66 item 4), as glyph outlines, never a selection's box:
 * the app's own Manrope read by Java2D (each glyph's outline at its kerned position), and the
 * system fonts Manrope lacks from the HarfBuzz model `tools/watchface/qibla-ink.py` writes
 * (`fallback-ink.json`): Noto Naskh Arabic Bold in its compact (UI) or its elegant variant, and Noto
 * Sans Bengali at the weight asked. Every line is laid out by the page's own [GlyphInk], so its
 * runs stand where the bidirectional algorithm puts them, and each run is shaped here font by font.
 *
 * The fonts a character is drawn in follow Android's font fallback (minikin's itemizer), each run of
 * one direction on its own: a mark it keeps with what stands before it in the run (a comma, a point,
 * a no-break space) stays in that font when the font's cmap has it (the model's "sticky", and one the
 * model holds no run for is an error, never Manrope's mark in its place); any other character is Manrope's
 * when Manrope has it (minikin gives a character to the first family that has it, and Manrope is the
 * first), and else the system font of its script. So the space between two Arabic or Bengali words
 * is Manrope's, 0.200 em, never the system font's own (Naskh's 0.221, Bengali's 0.26), and each word
 * comes whole from the model, shaped alone, as the watch shapes it. A no-break space is the system
 * font's own after Bengali digits, between Arabic-Indic or Extended digits grouped by it, and after
 * Arabic or Bengali letters (the model holds it), and Manrope's where it begins its run, as between
 * Arabic-Indic digits and كم on a right-to-left page (emulator-5558, 31 October). French's narrow
 * no-break space, which neither Manrope's cmap nor the system fonts' has, is a quarter of an em with
 * no ink, wider than the watch's own (its next fallback's, 0.2485). A format character, such as a
 * direction mark a native reviewer may write into a short form, has no ink and no advance, as
 * Android draws it; after the Arabic letter mark the run's font goes on, and after a direction mark,
 * an embedding or an isolate a mark is judged afresh, as the watch judges it ([BIDI_CONTROLS]).
 */
internal class HostInk(val variant: Variant = Variant.UI) {

    /**
     * Which of the system's Arabic and Bengali fonts draws: the compact one, or the elegant one,
     * which emulator-5558 draws its Arabic in (R66's device turn: its glyph bounds, and the pixels
     * of its north ش, are the elegant font's). A watch may draw either, so the tests hold both.
     */
    enum class Variant(val naskh: String, val bengali: String) {
        UI("naskh-ui", "bengali-ui"),
        ELEGANT("naskh-elegant", "bengali-elegant"),
    }

    /** [text]'s ink in ems at [weight] (600 or 800), as the page lays it out: [rtl] is the page's direction. */
    fun line(text: String, weight: Int, rtl: Boolean): Ink = lines.getOrPut(Triple(text, weight, rtl)) {
        GlyphInk.of(text, rtl) { t, start, end, runRtl -> shape(t.substring(start, end), weight, runRtl) }
    }

    private val lines = HashMap<Triple<String, Int, Boolean>, Ink>()

    /** One run of one direction, font by font, its fonts' runs drawn from the left. */
    private fun shape(run: String, weight: Int, rtl: Boolean): Ink {
        val pieces = ArrayList<Pair<Face, StringBuilder>>()
        var previous: Face? = null
        for (c in run) {
            val face = faceFor(c, previous, weight)
            if (pieces.isNotEmpty() && pieces.last().first == face) pieces.last().second.append(c) else pieces += face to StringBuilder().append(c)
            // A format character keeps the run's font for the mark after it, as minikin carries a
            // run across one it needs no font for, save the bidirectional controls, after which the
            // watch judges a mark as at a run's start (emulator-5558, 31 October).
            previous = when {
                c in BIDI_CONTROLS -> null
                face == NoInk -> previous
                else -> face
            }
        }
        val drawn = if (rtl) pieces.reversed() else pieces
        var pen = 0f
        val boxes = ArrayList<InkBox>()
        for ((face, chars) in drawn) {
            val ink = face.shape(chars.toString(), rtl)
            ink.boxes.forEach { boxes += it.moved(pen, 0f) }
            pen += ink.advance
        }
        return Ink(boxes, pen)
    }

    private fun faceFor(c: Char, previous: Face?, weight: Int): Face {
        if (c.category == CharCategory.FORMAT) return NoInk
        if (c in STICKY && previous != null) {
            if (previous.has(c)) return previous
            check(!previous.draws(c)) {
                "the watch draws '$c' (U+${"%04X".format(c.code)}) in the font of what stands before it, whose cmap has it, " +
                    "and the model holds no run for it: add it to the font's singles in tools/watchface/qibla-ink.py"
            }
        }
        val manrope = manrope(weight)
        if (manrope.has(c)) return manrope
        return when {
            c == NARROW_NO_BREAK_SPACE -> NarrowSpace
            isArabicScript(c) -> model(variant.naskh)
            isBengali(c) -> model("${variant.bengali}-${if (weight >= 800) 800 else 600}")
            else -> error("no font draws '$c' (U+${"%04X".format(c.code)}) on the watch, by this model")
        }
    }

    /** A font a run is drawn in. */
    private interface Face {
        fun has(c: Char): Boolean

        /** Whether the watch draws [c] in this font after its own letters or digits: its cmap has it (a sticky mark), whether or not this model can shape it. */
        fun draws(c: Char): Boolean = has(c)

        fun shape(text: String, rtl: Boolean): Ink
    }

    /** Manrope by Java2D: kerned, as Android lays it out, each glyph's outline at its position. */
    private class Manrope(file: File) : Face {
        private val font: Font = Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(EM)
            .deriveFont(mapOf(TextAttribute.KERNING to TextAttribute.KERNING_ON))
        private val context = FontRenderContext(null, true, true)

        override fun has(c: Char): Boolean = font.canDisplay(c)

        override fun shape(text: String, rtl: Boolean): Ink {
            // Manrope's characters in a right-to-left run (the degree sign and the dot between an
            // Arabic number and its word) are drawn from the run's end; none of them mirrors.
            val chars = (if (rtl) text.reversed() else text).toCharArray()
            val glyphs = font.layoutGlyphVector(context, chars, 0, chars.size, Font.LAYOUT_LEFT_TO_RIGHT)
            val boxes = (0 until glyphs.numGlyphs).mapNotNull { i ->
                val b = glyphs.getGlyphOutline(i).bounds2D
                if (b.isEmpty) null else InkBox(b.minX.toFloat() / EM, b.maxX.toFloat() / EM, b.minY.toFloat() / EM, b.maxY.toFloat() / EM)
            }
            return Ink(boxes, glyphs.getGlyphPosition(glyphs.numGlyphs).x.toFloat() / EM)
        }
    }

    /**
     * A system font from the HarfBuzz model: a joined word or letter as a whole, else character by
     * character (a number from its digits and marks). A caption reaches it one word at a time: the
     * space between its words is not one of the sticky marks, so it is Manrope's ([faceFor]), and
     * each word is a run of its own, shaped alone, as Android shapes it and as the model holds it.
     * A no-break space is sticky and the font's own, so a run may hold one (after Bengali digits,
     * between grouped digits, between two Arabic words): such a run is set from its words and the
     * font's no-break spaces, each whole, drawn from the left, since no shaping crosses one
     * (`qibla-ink.py`'s check, each dial word on both sides of one and the grouped numbers).
     */
    private class Modelled(private val runs: Map<String, Ink>, private val sticky: Set<Char>) : Face {
        override fun has(c: Char): Boolean = c.toString() in runs || runs.keys.any { c in it }

        /** The sticky marks the font's cmap has (`qibla-ink.py`'s "sticky"), which the watch draws in it after its letters. */
        override fun draws(c: Char): Boolean = has(c) || c in sticky

        override fun shape(text: String, rtl: Boolean): Ink {
            runs[text]?.let { return it }
            var pen = 0f
            val boxes = ArrayList<InkBox>()
            for (part in parts(text).let { if (rtl) it.asReversed() else it }) {
                val inks = runs[part]?.let { listOf(it) }
                    ?: part.map { c -> runs[c.toString()] ?: error("the model has no run '$text' nor '$c' to set it from") }.let { if (rtl) it.asReversed() else it }
                for (ink in inks) {
                    ink.boxes.forEach { boxes += it.moved(pen, 0f) }
                    pen += ink.advance
                }
            }
            return Ink(boxes, pen)
        }

        /** [text]'s words and the no-break spaces between them, in logical order. */
        private fun parts(text: String): List<String> {
            val parts = ArrayList<String>()
            val word = StringBuilder()
            for (c in text) {
                if (c != NO_BREAK_SPACE) {
                    word.append(c)
                    continue
                }
                if (word.isNotEmpty()) parts += word.toString()
                word.clear()
                parts += c.toString()
            }
            if (word.isNotEmpty()) parts += word.toString()
            return parts
        }
    }

    /** A format character (a direction mark, the Arabic letter mark, an isolate): Android draws it with no ink and no advance; it only orders the runs around it. */
    private object NoInk : Face {
        override fun has(c: Char): Boolean = c.category == CharCategory.FORMAT
        override fun shape(text: String, rtl: Boolean): Ink = Ink(emptyList(), 0f)
    }

    private object NarrowSpace : Face {
        override fun has(c: Char): Boolean = c == NARROW_NO_BREAK_SPACE
        override fun shape(text: String, rtl: Boolean): Ink = Ink(emptyList(), 0.25f * text.length)
    }

    private fun manrope(weight: Int): Face = MANROPE.getValue(if (weight >= 800) 800 else 600)

    private fun model(name: String): Face = MODEL[name] ?: error("no $name in the HarfBuzz model")

    companion object {
        private const val EM = 1000f
        private const val NARROW_NO_BREAK_SPACE = '\u202F'
        private const val NO_BREAK_SPACE = '\u00A0'

        /**
         * The format characters after which the watch judges a sticky mark afresh, as at a run's
         * start: the direction marks, the embeddings and overrides, and the isolates. On
         * emulator-5558 (31 October, at 1,000 px) a no-break space after "كم" and one of these is
         * Manrope's 0.200 em, and after "১০০" and one of them too, where after the Arabic letter
         * mark, a joiner, the soft hyphen or a byte order mark it is the font's own (0.2207, 0.26).
         */
        private val BIDI_CONTROLS: Set<Char> = setOf('\u200E', '\u200F') + ('\u202A'..'\u202E') + ('\u2066'..'\u2069')

        /** The marks Android keeps in the font of the letters before them, when it has them (minikin's sticky list). */
        private val STICKY = setOf('!', ',', '-', '.', ':', ';', '?', NO_BREAK_SPACE, '\u2010', '\u2011', NARROW_NO_BREAK_SPACE)

        private val fonts = File(System.getProperty("taqwa.wearRes") ?: "src/main/res", "font")

        private val MANROPE: Map<Int, Face> by lazy {
            System.setProperty("java.awt.headless", "true")
            mapOf(600 to Manrope(File(fonts, "manrope_semibold.ttf")), 800 to Manrope(File(fonts, "manrope_extrabold.ttf")))
        }

        private val MODEL: Map<String, Face> by lazy {
            val text = checkNotNull(HostInk::class.java.getResource("fallback-ink.json")) { "no fallback-ink.json: run tools/watchface/qibla-ink.py" }.readText()
            val fonts = Json.parseToJsonElement(text).jsonObject.getValue("fonts").jsonObject
            fonts.mapValues { (_, font) ->
                val runs = (font.jsonObject.getValue("runs") as JsonObject).mapValues { (_, run) ->
                    val glyphs = run.jsonObject.getValue("glyphs").jsonArray.map { g ->
                        val (left, right, top, bottom) = g.jsonArray.map { it.jsonPrimitive.float }
                        InkBox(left, right, top, bottom)
                    }
                    Ink(glyphs, run.jsonObject.getValue("advance").jsonPrimitive.float)
                }
                Modelled(runs, font.jsonObject.getValue("sticky").jsonPrimitive.content.toSet())
            }
        }

        fun isArabicScript(c: Char): Boolean = c in '؀'..'ۿ' || c in 'ﭐ'..'﻿'

        fun isBengali(c: Char): Boolean = c in 'ঀ'..'৿'
    }
}
