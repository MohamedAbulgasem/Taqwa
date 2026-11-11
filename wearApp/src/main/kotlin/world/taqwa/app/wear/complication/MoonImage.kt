package world.taqwa.app.wear.complication

import android.graphics.Bitmap

/**
 * The lit part of the moon as a bitmap [sizePx] pixels square, white on transparent, for a
 * monochromatic image that the face tints and lays over a dark disc of its own. The picture itself
 * is [ComplicationFields.moonPixels], which is plain arithmetic and is tested as such; this only
 * hands it to Android.
 */
fun ComplicationFields.moonImage(shape: MoonShape, sizePx: Int): Bitmap =
    Bitmap.createBitmap(moonPixels(shape, sizePx), sizePx, sizePx, Bitmap.Config.ARGB_8888)
