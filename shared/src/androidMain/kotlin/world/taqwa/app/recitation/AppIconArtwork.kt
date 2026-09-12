package world.taqwa.app.recitation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream

/**
 * The Taqwa launcher icon as a 512 px PNG, for the media notification and the lock screen.
 *
 * The artwork is the app's icon and not the reciter's monogram, by decision (spec §12): the
 * monogram is an in-app device, and what the lock screen should say is which app is speaking.
 *
 * Rendered from the adaptive icon rather than read from a `mipmap` file, because the launcher
 * icon is a vector in two layers and there is no bitmap of it at this size to read; asked of the
 * package manager rather than of `R`, because this lives in `:shared` and `R` belongs to
 * `:androidApp`. Done once and kept: 512 px of PNG is about 30 KB and every one of a surah's
 * items points at the same array.
 */
internal object AppIconArtwork {

    private const val SIZE = 512

    @Volatile
    private var cached: ByteArray? = null

    /** Null only if the platform cannot produce the icon at all, which would be a broken install. */
    fun bytes(context: Context): ByteArray? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: render(context)?.also { cached = it }
        }
    }

    private fun render(context: Context): ByteArray? = try {
        val icon = context.packageManager.getApplicationIcon(context.applicationInfo)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, SIZE, SIZE)
        icon.draw(Canvas(bitmap))
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    } catch (e: Exception) {
        // A notification without artwork is a notification; a crash in a media service is not.
        null
    }
}
