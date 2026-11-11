package world.taqwa.app.wear.app

import kotlin.math.ceil
import kotlin.math.min

/**
 * A font as the watch lays a block out, for [fitAbove]: [advance] units of text at 16.5, wrapped
 * into [width]-unit lines, each [lineHeight] of its size below the one before, the last reaching
 * [descent] of its size below its baseline; cut when it needs more lines than it is given.
 */
internal fun fakeFont(advance: Float, width: Float, lineHeight: Float, descent: Float): (Float, Int) -> Tried = { size, lines ->
    val needed = ceil(advance * size / 16.5f / width - 1e-4f).toInt().coerceAtLeast(1)
    val drawn = min(needed, lines)
    Tried(cut = needed > lines, below = (drawn - 1) * lineHeight * size + descent * size)
}

/**
 * [fakeFont] as Android lays it out on a screen of [unitPx] pixels to the unit: the lines' spacing
 * (Compose's `LineHeightStyleSpan` takes the ceiling of its line height) and the font's descent
 * (`FontMetricsInt`) each rounded up to a whole pixel.
 */
internal fun pixelFont(advance: Float, width: Float, lineHeight: Float, descent: Float, unitPx: Float): (Float, Int) -> Tried = { size, lines ->
    val needed = ceil(advance * size / 16.5f / width - 1e-4f).toInt().coerceAtLeast(1)
    val drawn = min(needed, lines)
    val px = size * unitPx
    Tried(cut = needed > lines, below = ((drawn - 1) * ceil(lineHeight * px - 1e-4f) + ceil(descent * px - 1e-4f)) / unitPx)
}

/**
 * How far a vowelled phrase's ink in the watch's Naskh reaches across the break between its two
 * lines, as a share of its size: the lowest ink of the first line below its baseline and the highest
 * of the second above its own, where the two overlap across. [NASKH_INK_ACROSS] is the most of
 * 3,888 phrases of whole words, up to 60 code points, from the app's adhkar and 377 of the Quran's
 * ayahs, each wrapped as the counter wraps it at font 1.0, 1.15 and 1.3 (shaped with HarfBuzz in
 * `NotoNaskhArabic-Bold`, 30 October): "شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ" over "أَنْ".
 * [NASKH_KASRA_OVER_ALIF] is review round 2's own: the kasra under ٱللَّٰهِ (0.161 below) over the
 * dagger alif above ٱللَّٰهِ (1.132 above), which lines 1.2 apart drew as one mark.
 */
internal const val NASKH_INK_ACROSS = 1.446f
internal const val NASKH_KASRA_OVER_ALIF = 1.293f

/**
 * The watch's Naskh, the system font a phrase in Arabic script is drawn in on emulator-5558: its own
 * lines stand [NASKH_LINE_HEIGHT] of its size apart, and a line's box reaches [NASKH_DESCENT] of its
 * size below its baseline. These are `NotoNaskhArabic-Bold.ttf`'s (hhea 2189 and 1298 over 2048),
 * which the 36-character phrase's baselines, at 127 and 191 px for 16.5 units of 2.27 px, match; its
 * `UI` twin's would be 1.36 and 0.29.
 */
internal const val NASKH_LINE_HEIGHT = 1.703f
internal const val NASKH_DESCENT = 0.634f

/**
 * Roboto, the system font a phrase in Latin script is drawn in: its own lines stand
 * [ROBOTO_LINE_HEIGHT] of its size apart, and a line's box reaches [ROBOTO_DESCENT] below its
 * baseline (`Roboto-Regular.ttf`'s hhea 1900 and 500 over 2048, pulled from emulator-5558).
 */
internal const val ROBOTO_LINE_HEIGHT = 1.172f
internal const val ROBOTO_DESCENT = 0.244f
