package world.taqwa.app.notifications

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * The chosen recording, by content URI, for the Adhan channel's sound: the system plays a
 * notification's sound from its own process and has to be able to open it, as with the
 * recitation's [world.taqwa.app.recitation.ArtworkProvider]. Read-only, one fixed file, nothing else.
 */
class CustomAdhanProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = context?.let { CustomAdhanFiles.mime(it) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException("No context")
        if (mode != "r" || uri.path != CustomAdhanFiles.PATH) throw FileNotFoundException(uri.toString())
        val file = CustomAdhanFiles.file(context)
        if (!file.exists()) throw FileNotFoundException(uri.toString())
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
