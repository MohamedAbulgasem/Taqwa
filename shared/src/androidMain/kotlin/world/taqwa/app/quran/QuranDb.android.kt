package world.taqwa.app.quran

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import world.taqwa.app.quran.db.QuranDatabase
import world.taqwa.app.resources.Res
import world.taqwa.app.settings.appContext

actual fun createQuranDriver(): SqlDriver {
    val file = appContext.getDatabasePath(QuranDb.FILE)
    if (!file.exists() || userVersion(file) != QuranDb.VERSION) {
        file.parentFile?.mkdirs()
        val bytes = runBlocking { Res.readBytes(QuranDb.RESOURCE) }
        val temp = File(file.parentFile, "${QuranDb.FILE}.tmp")
        temp.writeBytes(bytes)
        // File.renameTo is atomic when the source and destination are on the same filesystem,
        // which they are here (both under the app's private databases directory).
        check(temp.renameTo(file)) { "Failed to install $file from $temp" }
    }
    return AndroidSqliteDriver(
        schema = QuranDatabase.Schema,
        context = appContext,
        name = QuranDb.FILE,
        callback = object : AndroidSqliteDriver.Callback(QuranDatabase.Schema) {
            // The bundled file already has the schema; the framework must never create or
            // migrate it itself.
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        },
    )
}

/**
 * Reads `user_version` straight from the SQLite file header (bytes 60..63, big-endian) instead of
 * opening the database, so checking the version never requires two opens of the same file.
 * Returns -1 (never equal to a real version) for a missing or truncated file, which forces a copy.
 */
private fun userVersion(file: File): Int = runCatching {
    RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 64) return -1
        raf.seek(60)
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }
}.getOrDefault(-1)

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
