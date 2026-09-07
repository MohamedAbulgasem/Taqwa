package world.taqwa.app.quran

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.fwrite
import platform.posix.remove
import platform.posix.rename
import world.taqwa.app.resources.Res

@OptIn(ExperimentalForeignApi::class)
actual fun createQuranDriver(): SqlDriver {
    val directoryPath = quranDatabaseDirectory()
    val filePath = "$directoryPath/${QuranDb.FILE}"
    if (!NSFileManager.defaultManager.fileExistsAtPath(filePath) || userVersion(filePath) != QuranDb.VERSION) {
        val bytes = runBlocking { Res.readBytes(QuranDb.RESOURCE) }
        writeAtomically(bytes, filePath)
    }
    return NativeSqliteDriver(
        DatabaseConfiguration(
            name = QuranDb.FILE,
            version = QuranDb.VERSION,
            // The bundled file already has the schema; the framework must never create or
            // migrate it itself.
            create = { },
            upgrade = { _, _, _ -> },
            extendedConfig = DatabaseConfiguration.Extended(basePath = directoryPath),
        ),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun quranDatabaseDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val appSupportPath = requireNotNull(
        fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path,
    ) { "Could not resolve the Application Support directory" }
    val directory = "$appSupportPath/databases"
    fileManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    return directory
}

/**
 * Writes [bytes] to [path] by writing a sibling temp file first and renaming it over the
 * destination — `rename()` is atomic on POSIX filesystems, the same guarantee the Android actual
 * gets from `File.renameTo`. Plain POSIX I/O is used instead of `NSData`/`NSFileHandle` so this
 * has no dependency on the exact Objective-C interop surface Kotlin/Native happens to generate.
 */
@OptIn(ExperimentalForeignApi::class)
private fun writeAtomically(bytes: ByteArray, path: String) {
    val tempPath = "$path.tmp"
    val file = fopen(tempPath, "wb") ?: error("Could not open $tempPath for writing")
    // `fwrite` reports a short write by its return value rather than by failing, and `fclose` is
    // where a buffered write finally reaches the disk; both are checked, because renaming a
    // truncated file into place is exactly what the temp-then-rename dance exists to prevent.
    val written = try {
        if (bytes.isEmpty()) 0uL else bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file) }
    } finally {
        val closed = fclose(file)
        if (closed != 0) {
            remove(tempPath)
            error("Failed to flush $tempPath (fclose returned $closed)")
        }
    }
    if (written != bytes.size.toULong()) {
        remove(tempPath)
        error("Short write to $tempPath: $written of ${bytes.size} bytes")
    }
    check(rename(tempPath, path) == 0) { "Failed to install $path from $tempPath" }
}

/**
 * Reads `user_version` straight from the SQLite file header (bytes 60..63, big-endian) instead of
 * opening the database with a driver, so checking the version never requires two opens of the
 * same file. Returns -1 (never equal to a real version) for a missing or truncated file, which
 * forces a copy.
 */
@OptIn(ExperimentalForeignApi::class)
private fun userVersion(path: String): Int {
    val file = fopen(path, "rb") ?: return -1
    try {
        if (fseek(file, 60, SEEK_SET) != 0) return -1
        val buffer = ByteArray(4)
        val read = buffer.usePinned { pinned -> fread(pinned.addressOf(0), 1u, 4u, file) }
        if (read.toInt() < 4) return -1
        return ((buffer[0].toInt() and 0xFF) shl 24) or
            ((buffer[1].toInt() and 0xFF) shl 16) or
            ((buffer[2].toInt() and 0xFF) shl 8) or
            (buffer[3].toInt() and 0xFF)
    } finally {
        fclose(file)
    }
}

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.Default
