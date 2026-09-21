package world.taqwa.app.recitation

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Serves one file to anyone who asks: the launcher icon as a PNG, the recitation's artwork
 * (spec §18.2).
 *
 * The artwork has to be named by a URI the *system* can open. The lock screen and the media
 * player in the shade are SystemUI, another process; a `file://` path into this app's cache is
 * a path it may not read, and it logged a `FileNotFoundException` for every metadata update it
 * was handed one. A `content://` URI it can.
 *
 * Exported, with no permission, deliberately: what it serves is the icon every launcher already
 * shows, it is read-only, and it answers exactly one path with exactly one file that
 * [AppIconArtwork] wrote — a request for anything else is refused before a file name is formed,
 * so there is no path for a caller to traverse. It has no rows: every table method is a no-op.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException("No context")
        if (mode != "r" || uri.path != AppIconArtwork.PATH) throw FileNotFoundException(uri.toString())
        val file = AppIconArtwork.file(context) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
