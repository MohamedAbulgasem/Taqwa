package world.taqwa.app.recitation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import java.io.File

/**
 * The Taqwa launcher icon as a 512 px PNG **file**, for the media notification and the lock
 * screen.
 *
 * The artwork is the app's icon and not the reciter's monogram, by decision (spec §12): the
 * monogram is an in-app device, and what the lock screen should say is which app is speaking.
 *
 * Rendered from the adaptive icon rather than read from a `mipmap` file, because the launcher
 * icon is a vector in two layers and there is no bitmap of it at this size to read; asked of the
 * package manager rather than of `R`, because this lives in `:shared` and `R` belongs to
 * `:androidApp`.
 *
 * **A URI, never bytes on the item (spec §18.2).** Until 1.0.0 (30) every queue item carried the
 * PNG as `artworkData`. Media3 mirrors the queue into the platform session and, for every item
 * that has `artworkData`, decodes it and attaches the *bitmap* to the platform queue item — a
 * megabyte each, 571 of them for Al-Baqarah, pushed to every controller on every queue change.
 * On a tester's Android 16 phone that exhausted `system_server`'s file descriptors and restarted
 * the device. An item that names its artwork by URI costs the queue a string; the one bitmap the
 * notification and the lock screen need is loaded from it, for the item that is playing. The
 * URI is a `content://` one served by [ArtworkProvider], because the system's own player reads
 * it too, from another process.
 */
internal object AppIconArtwork {

    private const val SIZE = 512
    private const val FILE_NAME = "recitation-artwork.png"

    /** The one path [ArtworkProvider] answers. */
    const val PATH = "/icon.png"

    @Volatile
    private var cached: File? = null

    /** Null only if the platform cannot produce the icon at all, which would be a broken install. */
    fun uri(context: Context): Uri? {
        file(context) ?: return null
        return Uri.parse("content://${context.packageName}.artwork$PATH")
    }

    /** The PNG itself, rendered on first use; what [ArtworkProvider] opens. */
    fun file(context: Context): File? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: render(context)?.also { cached = it }
        }
    }

    // Written once per process rather than once per install: it is 20 KB, and an update that
    // changes the icon must not keep showing the old one out of the cache directory.
    private fun render(context: Context): File? = try {
        val icon = context.packageManager.getApplicationIcon(context.applicationInfo)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, SIZE, SIZE)
        icon.draw(Canvas(bitmap))
        val file = File(context.cacheDir, FILE_NAME)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        file
    } catch (e: Exception) {
        // A notification without artwork is a notification; a crash in a media service is not.
        null
    }
}
