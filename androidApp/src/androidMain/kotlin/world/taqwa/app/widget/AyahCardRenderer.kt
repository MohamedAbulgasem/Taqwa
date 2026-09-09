package world.taqwa.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import world.taqwa.app.quran.QuranText
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Everything one draw of the ayah card's *text* needs.
 *
 * [widthPx] / [heightPx] are the cell Glance actually measured, in device pixels; [density] is
 * what converts the spec's dp and sp figures into them. The colours arrive already resolved from
 * `WidgetPalette` as plain ARGB ints — the renderer knows nothing about the background choice, it
 * only ever draws on transparency (see [AyahCardRenderer]).
 *
 * [languageTag] is the mirror's own tag, not the device's: it decides the digit set of the footer
 * reference through `WidgetDigits.localize`, and it must agree with the text sitting beside it,
 * which was written into the mirror under that same tag.
 */
class AyahCardInput(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val entry: AyahPoolEntry,
    val languageTag: String,
    val arabicUi: Boolean,
    val translationRtl: Boolean,
    val showTranslation: Boolean,
    val textArgb: Int,
    val accentArgb: Int,
)

/**
 * Draws the ayah card's text to a transparent bitmap with `android.graphics` (design spec §2).
 *
 * Glance has no way to use a bundled font — its `TextStyle` carries no font family at all — and
 * the Uthmani Hafs face is the whole point of a Quran card: the OS Arabic face does not carry the
 * Hafs riwayah's glyph shapes, and the ayah roundel is a Hafs glyph. So the text is drawn here
 * with `StaticLayout` and handed to Glance as a single `Image`.
 *
 * Only the *text* is in the bitmap. The card's background, corner radius and `appWidgetBackground`
 * stay in the Glance tree, so the Translucent background choice keeps working — a bitmap baked
 * with an opaque background would sit as a solid rectangle on top of the launcher blur.
 */
object AyahCardRenderer {

    // -- Spec §2 metrics ------------------------------------------------------------------------

    private const val PADDING_DP = 14f

    /** Auto-fit range for the Arabic, in sp, walked from the top down in 1 sp steps. */
    private const val ARABIC_MAX_SP = 28
    private const val ARABIC_MIN_SP = 17

    private const val TRANSLATION_RATIO = 0.6f
    private const val TRANSLATION_MIN_SP = 11.5f
    private const val TRANSLATION_MAX_SP = 15f

    private const val ARABIC_LINE_HEIGHT = 1.75f
    private const val TRANSLATION_LINE_HEIGHT = 1.45f

    /** The translation is the primary text colour at this alpha; the reference and the rule are
     * the two weaker steps of the same colour. */
    private const val SECONDARY_ALPHA = 0.62f
    private const val TERTIARY_ALPHA = 0.42f
    private const val RULE_ALPHA = 0.12f

    private const val TRANSLATION_GAP_DP = 8f
    private const val FOOTER_GAP_DP = 9f
    private const val FOOTER_RULE_GAP_DP = 8f
    private const val COMPACT_FOOTER_GAP_DP = 6f

    /** Under this drawn height the card takes the compact footer: no rule, a tighter gap and a
     * smaller Arabic surah name. Android's 4x2 cell and iOS's Medium both land below it. */
    private const val COMPACT_HEIGHT_DP = 170f

    /** Between the surah's Latin name and its reference, as the reader's own headers set it. */
    private const val REFERENCE_SEPARATOR = " \u00B7 "

    private const val FOOTER_LATIN_SP = 12f
    private const val FOOTER_ARABIC_SP = 16f
    private const val FOOTER_ARABIC_UI_SP = 15f
    private const val FOOTER_ARABIC_COMPACT_SP = 14f

    // -- Fonts ----------------------------------------------------------------------------------

    /** Where the Compose resources plugin packages the app's fonts inside the APK's assets. */
    private const val FONT_DIR = "composeResources/world.taqwa.app.resources/font/"

    private const val FONT_HAFS = "uthmanic_hafs.ttf"
    private const val FONT_MANROPE = "Manrope-Regular.ttf"
    private const val FONT_MANROPE_SEMIBOLD = "Manrope-SemiBold.ttf"

    /**
     * `createFromAsset` re-parses the file on every call, and the Hafs face is a megabyte; a
     * widget redrawn on every unlock cannot pay that each time. Keyed by file name because the
     * assets belong to the app process, which is the only process that ever draws this.
     */
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    /**
     * The bundled face, or [Typeface.DEFAULT] when the asset cannot be read.
     *
     * A missing font must degrade to system text, never take the widget down: a crash inside
     * `provideGlance` leaves the launcher showing the error view for good, while the wrong face
     * still shows today's ayah.
     */
    private fun typeface(context: Context, file: String): Typeface =
        typefaceCache.getOrPut(file) {
            runCatching { Typeface.createFromAsset(context.assets, FONT_DIR + file) }
                .getOrNull() ?: Typeface.DEFAULT
        }

    // -- Entry point ------------------------------------------------------------------------------

    /**
     * Renders [input] to a fresh transparent `ARGB_8888` bitmap.
     *
     * The layout is chosen by auto-fit (spec §2): the largest Arabic size from 28 sp down to 17 sp
     * at which the Arabic, the translation and the footer all fit the cell. Nothing fits at the
     * floor only on a cell too short for the ayah it drew today — there the translation is
     * clamped to the lines that remain and ellipsised, because the Arabic is never cut.
     */
    fun render(context: Context, input: AyahCardInput): Bitmap {
        val scale = renderScale(input.widthPx, input.heightPx)
        val width = max(1, (input.widthPx * scale).toInt())
        val height = max(1, (input.heightPx * scale).toInt())
        val density = input.density * scale
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val padPx = PADDING_DP * density
        val innerWidth = (width - padPx * 2f).toInt().coerceAtLeast(1)
        val innerHeight = height - padPx * 2f
        // The threshold is on the *drawn* height in dp, which is what the eye sees, so it is
        // measured before the render scale above shrinks anything.
        val compact = input.heightPx / input.density < COMPACT_HEIGHT_DP

        val footer = buildFooter(context, input, density, compact)
        val blocks = fit(context, input, density, innerWidth, innerHeight, footer, compact)
        draw(canvas, blocks, width, height, padPx, innerWidth, input.textArgb)
        return bitmap
    }

    /**
     * How much to shrink the bitmap below the cell's true pixel size.
     *
     * Glance inlines a bitmap into the `RemoteViews` it hands the launcher, and an oversized one
     * is a `TransactionTooLargeException` rather than a soft failure — a 5x5 cell on a 480 dpi
     * phone is already 3.5 MB of ARGB. The budget below leaves every cell up to about 4x4 at full
     * resolution and only starts costing sharpness past that, where `ContentScale.Fit` scales the
     * bitmap back up.
     */
    private fun renderScale(widthPx: Int, heightPx: Int): Float {
        val pixels = widthPx.toLong() * heightPx.toLong()
        if (pixels <= MAX_BITMAP_PIXELS) return 1f
        return sqrt(MAX_BITMAP_PIXELS.toDouble() / pixels.toDouble()).toFloat()
    }

    /**
     * 700k pixels is 2.8 MB as ARGB_8888. The host counts about three times that per widget —
     * `dumpsys appwidget` reported `views_bitmap_memory=8390372` for one card at this budget —
     * against a ceiling of 1.5 screenfuls of ARGB, which on the 1344x2992 device this was
     * measured on is 24 MB. A 4x3 cell there is 1.35 M pixels, so it renders at about 0.72 scale.
     */
    private const val MAX_BITMAP_PIXELS = 700_000L

    // -- Auto-fit -----------------------------------------------------------------------------

    /** The three text blocks of one candidate layout, with the gaps the spec puts between them. */
    private class CardBlocks(
        val arabic: StaticLayout,
        val translation: StaticLayout?,
        val footer: FooterRow,
        val arabicUi: Boolean,
        val translationGapPx: Float,
        val footerGapPx: Float,
        val rulePx: Float,
        val ruleGapPx: Float,
    ) {
        /** Everything from the gap above the rule to the bottom of the footer row. */
        val footerBlockHeight: Float
            get() = footerGapPx + rulePx + ruleGapPx + footer.height

        val contentHeight: Float
            get() = arabic.height + (translation?.let { translationGapPx + it.height } ?: 0f)

        val totalHeight: Float
            get() = contentHeight + footerBlockHeight
    }

    private fun fit(
        context: Context,
        input: AyahCardInput,
        density: Float,
        innerWidth: Int,
        innerHeight: Float,
        footer: FooterRow,
        compact: Boolean,
    ): CardBlocks {
        for (sp in ARABIC_MAX_SP downTo ARABIC_MIN_SP) {
            val blocks = blocksAt(context, input, density, innerWidth, footer, compact, sp.toFloat(), maxLines = null)
            if (blocks.totalHeight <= innerHeight) return blocks
        }
        // Nothing fit even at the floor. The Arabic keeps its full height whatever that costs —
        // half an ayah is not an ayah — and the translation takes whatever height is left.
        val floorBlocks = blocksAt(context, input, density, innerWidth, footer, compact, ARABIC_MIN_SP.toFloat(), maxLines = null)
        val translation = floorBlocks.translation ?: return floorBlocks
        val available = innerHeight - floorBlocks.arabic.height - floorBlocks.translationGapPx -
            floorBlocks.footerBlockHeight
        val lineHeight = (translation.getLineBottom(0) - translation.getLineTop(0)).toFloat()
        val lines = if (lineHeight <= 0f) 0 else floor(available / lineHeight).toInt()
        if (lines >= translation.lineCount) return floorBlocks
        return blocksAt(
            context, input, density, innerWidth, footer, compact, ARABIC_MIN_SP.toFloat(),
            maxLines = lines,
        )
    }

    /**
     * One candidate layout at [arabicSp]. A null [maxLines] lets the translation take as many
     * lines as it needs; a positive one clamps it and ellipsises the end; zero drops it entirely,
     * which is what a cell with no room left for even one line gets.
     */
    private fun blocksAt(
        context: Context,
        input: AyahCardInput,
        density: Float,
        innerWidth: Int,
        footer: FooterRow,
        compact: Boolean,
        arabicSp: Float,
        maxLines: Int?,
    ): CardBlocks {
        val arabic = arabicLayout(context, input, density, innerWidth, arabicSp)
        val translationSp = (arabicSp * TRANSLATION_RATIO).coerceIn(TRANSLATION_MIN_SP, TRANSLATION_MAX_SP)
        val translation = when {
            !input.showTranslation || input.entry.translation.isEmpty() -> null
            maxLines != null && maxLines <= 0 -> null
            else -> translationLayout(context, input, density, innerWidth, translationSp, maxLines)
        }
        return CardBlocks(
            arabic = arabic,
            translation = translation,
            footer = footer,
            arabicUi = input.arabicUi,
            translationGapPx = TRANSLATION_GAP_DP * density,
            footerGapPx = (if (compact) COMPACT_FOOTER_GAP_DP else FOOTER_GAP_DP) * density,
            // Spec §2 asks for a one-*pixel* rule, not one dp: a hairline that stays a hairline
            // however dense the screen, as the in-app card dividers do.
            rulePx = if (compact) 0f else 1f,
            ruleGapPx = if (compact) 0f else FOOTER_RULE_GAP_DP * density,
        )
    }

    // -- Blocks ---------------------------------------------------------------------------------

    /**
     * The ayah with its roundel: the database's own Uthmani text, the shared marker separator, and
     * the ayah number in Arabic-Indic digits, which the Hafs face draws as the roundel (spec §5.2).
     * The digit run alone takes the accent, exactly as the reader's `AyahCard` styles it.
     */
    private fun arabicLayout(
        context: Context,
        input: AyahCardInput,
        density: Float,
        widthPx: Int,
        sp: Float,
    ): StaticLayout {
        val digits = QuranText.arabicIndic(input.entry.ayah)
        val text = input.entry.arabic + QuranText.MARKER_SEPARATOR + digits
        val spanned = SpannableString(text)
        spanned.setSpan(
            ForegroundColorSpan(input.accentArgb),
            text.length - digits.length,
            text.length,
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE,
        )
        val paint = textPaint(context, FONT_HAFS, sp, density, input.textArgb)
        return StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            // ALIGN_NORMAL plus an RTL paragraph is what puts the Arabic hard against the right
            // edge and wraps it right-to-left; ALIGN_OPPOSITE would flip it back.
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, spacingMultiplier(paint, sp * density * ARABIC_LINE_HEIGHT))
            .setIncludePad(false)
            .build()
    }

    /** The translation paragraph, in its own language's direction — an Arabic UI reading an
     * English translation still reads that paragraph left to right (spec §5.1). */
    private fun translationLayout(
        context: Context,
        input: AyahCardInput,
        density: Float,
        widthPx: Int,
        sp: Float,
        maxLines: Int?,
    ): StaticLayout {
        val text = input.entry.translation
        val paint = textPaint(context, FONT_MANROPE, sp, density, input.textArgb.withAlpha(SECONDARY_ALPHA))
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(
                if (input.translationRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR,
            )
            .setLineSpacing(0f, spacingMultiplier(paint, sp * density * TRANSLATION_LINE_HEIGHT))
            .setIncludePad(false)
        if (maxLines != null) {
            builder.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        }
        return builder.build()
    }

    /**
     * The multiplier that turns a face's *natural* line height into the spec's.
     *
     * `StaticLayout`'s spacing multiplier scales what the font asks for, and Hafs asks for a great
     * deal — its stacked diacritics need it. Where the face already wants more room than the spec
     * allots, it keeps it: 1f is the floor, because a multiplier below one would set the
     * diacritics of one line into the line above, which is the exact failure the in-app reader's
     * fixed 2.0x line height exists to avoid.
     */
    private fun spacingMultiplier(paint: TextPaint, desiredPx: Float): Float {
        val metrics = paint.fontMetrics
        val natural = metrics.descent - metrics.ascent
        if (natural <= 0f) return 1f
        return (desiredPx / natural).coerceAtLeast(1f)
    }

    // -- Footer -----------------------------------------------------------------------------------

    /** One styled run of the footer row; its width is measured once, at construction. */
    private class FooterRun(val text: String, val paint: TextPaint) {
        val width: Float = paint.measureText(text)
    }

    /**
     * The footer's two ends. Both are laid out on one shared baseline so the Latin name and the
     * Hafs surah name beside it sit on the same line however differently the two faces are cut.
     */
    private class FooterRow(private val start: List<FooterRun>, private val end: List<FooterRun>) {
        private val metrics = (start + end).map { it.paint.fontMetrics }
        private val ascent = metrics.minOf { it.ascent }
        private val descent = metrics.maxOf { it.descent }

        val height: Float = descent - ascent

        /**
         * Draws the row between [left] and [right] with its top at [top]. The start end follows
         * the *UI's* direction, not the text's: under an Arabic UI the surah name sits on the
         * right and the reference on the left.
         */
        fun draw(canvas: Canvas, left: Float, right: Float, top: Float, rtl: Boolean) {
            val baseline = top - ascent
            val atLeft = if (rtl) end else start
            val atRight = if (rtl) start else end
            var x = left
            atLeft.forEach { run ->
                canvas.drawText(run.text, x, baseline, run.paint)
                x += run.width
            }
            x = right - atRight.sumOf { it.width.toDouble() }.toFloat()
            atRight.forEach { run ->
                canvas.drawText(run.text, x, baseline, run.paint)
                x += run.width
            }
        }
    }

    private fun buildFooter(
        context: Context,
        input: AyahCardInput,
        density: Float,
        compact: Boolean,
    ): FooterRow {
        val entry = input.entry
        val primary = input.textArgb
        val tertiary = input.textArgb.withAlpha(TERTIARY_ALPHA)
        // Built by interpolation rather than pre-formatted upstream, so it needs the same pass
        // through WidgetDigits every other number a widget composes itself gets — including the
        // ar-LY / ar-MA rule, where CLDR keeps Arabic on Western digits.
        val reference = WidgetDigits.localize("${entry.surah}:${entry.ayah}", input.languageTag)
        val arabicNameSp = when {
            compact -> FOOTER_ARABIC_COMPACT_SP
            input.arabicUi -> FOOTER_ARABIC_UI_SP
            else -> FOOTER_ARABIC_SP
        }
        val arabicName = FooterRun(
            entry.surahArabic,
            textPaint(context, FONT_HAFS, arabicNameSp, density, primary),
        )
        val referenceRun = FooterRun(
            reference,
            referencePaint(context, FOOTER_LATIN_SP, density, tertiary, reference),
        )
        return if (input.arabicUi) {
            // No Latin at all under an Arabic UI: the surah name is already in the reader's own
            // script and a transliteration beside it would be noise.
            FooterRow(start = listOf(arabicName), end = listOf(referenceRun))
        } else {
            FooterRow(
                start = listOf(
                    FooterRun(
                        entry.surahLatin,
                        textPaint(context, FONT_MANROPE_SEMIBOLD, FOOTER_LATIN_SP, density, primary),
                    ),
                    FooterRun(
                        REFERENCE_SEPARATOR + reference,
                        referencePaint(context, FOOTER_LATIN_SP, density, tertiary, reference),
                    ),
                ),
                end = listOf(arabicName),
            )
        }
    }

    /**
     * Manrope for the reference, unless the locale's digits are not in it.
     *
     * Manrope carries Latin only, so an `ar-EG` reference — «١٣:٢٨» — would come out as tofu
     * boxes. The system face has those digits, and swapping to it is invisible for the Western
     * digits every other locale uses.
     */
    private fun referencePaint(
        context: Context,
        sp: Float,
        density: Float,
        argb: Int,
        text: String,
    ): TextPaint {
        val paint = textPaint(context, FONT_MANROPE, sp, density, argb)
        if (!paint.hasGlyph(text)) paint.typeface = Typeface.DEFAULT
        return paint
    }

    // -- Drawing ------------------------------------------------------------------------------

    private fun draw(
        canvas: Canvas,
        blocks: CardBlocks,
        width: Int,
        height: Int,
        padPx: Float,
        innerWidth: Int,
        textArgb: Int,
    ) {
        val left = padPx
        val right = width - padPx
        // The footer is pinned to the bottom rather than floated up under the translation. The
        // spec's 9 dp is a minimum — it is what the auto-fit above checks — and the alternative
        // leaves whatever height the chosen size did not use as a band of dead space *below* the
        // footer, which reads as a card that failed to fill its cell.
        val footerTop = height - padPx - blocks.footer.height
        val contentBottom = footerTop - blocks.footerGapPx - blocks.rulePx - blocks.ruleGapPx

        // The Arabic and its translation are centred together in the space above the footer.
        // Spec §2 asks for this outright when the translation is off; it is right with one too,
        // and for the same reason. The launcher's 4x3 cell is nearer 380 dp tall than the 180 dp
        // the provider asks for as a minimum, and the auto-fit stops at 28 sp, so on a real home
        // screen there is always height the text cannot use. Top-aligned, all of it collects
        // into one dead band between the translation and the rule; centred, it becomes margin.
        var y = padPx + max(0f, (contentBottom - padPx - blocks.contentHeight) / 2f)
        canvas.withTranslation(left, y) { blocks.arabic.draw(canvas) }
        y += blocks.arabic.height
        blocks.translation?.let { translation ->
            y += blocks.translationGapPx
            canvas.withTranslation(left, y) { translation.draw(canvas) }
        }

        if (blocks.rulePx > 0f) {
            val ruleTop = footerTop - blocks.ruleGapPx - blocks.rulePx
            val rulePaint = Paint().apply { color = textArgb.withAlpha(RULE_ALPHA) }
            canvas.drawRect(left, ruleTop, left + innerWidth, ruleTop + blocks.rulePx, rulePaint)
        }
        blocks.footer.draw(canvas, left, right, footerTop, rtl = blocks.arabicUi)
    }

    /** Runs [block] with the canvas translated to ([x], [y]) and restores it afterwards —
     * `StaticLayout.draw` always draws at the canvas origin. */
    private inline fun Canvas.withTranslation(x: Float, y: Float, block: () -> Unit) {
        val saved = save()
        translate(x, y)
        block()
        restoreToCount(saved)
    }

    // -- Paints ---------------------------------------------------------------------------------

    private fun textPaint(
        context: Context,
        font: String,
        sp: Float,
        density: Float,
        argb: Int,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typeface(context, font)
        // sp, not dp: the card is drawn at the *design* size and does not follow the system font
        // scale. A widget cell does not grow when the user enlarges system text, so honouring the
        // scale here would only push the ayah out of a cell it already exactly fills.
        textSize = sp * density
        color = argb
    }

    /** The same ARGB colour at [fraction] of its own alpha. */
    private fun Int.withAlpha(fraction: Float): Int {
        val alpha = ((this ushr 24) * fraction).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (this and 0x00FFFFFF)
    }
}
