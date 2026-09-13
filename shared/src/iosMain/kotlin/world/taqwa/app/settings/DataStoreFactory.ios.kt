package world.taqwa.app.settings

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * `Library/Application Support/Taqwa`, created on first use and marked excluded from iCloud
 * backup. The settings file holds the reader's coordinates or chosen city, and "your location
 * never leaves your phone" has to include Apple's backup; Documents, where the file lived until
 * 0.11, is backed up by default and there is no per-file rule that survives DataStore rewriting
 * the file on every edit — the directory attribute does.
 *
 * The first run after the upgrade moves the existing file out of Documents so nobody loses their
 * settings. Best effort throughout: if the directory cannot be made or the move fails, the old
 * path is used as before rather than starting from a blank file.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun dataStoreDirectory(): String {
    val fm = NSFileManager.defaultManager
    val documentsUrl = fm.directory(NSDocumentDirectory)
    val documents = requireNotNull(documentsUrl.path)
    // Every fallback below leaves the settings — and with them the reader's coordinates — in
    // Documents, which iCloud backs up. There is no per-file rule that survives DataStore
    // rewriting the file, so the flag goes on the directory, as it does on `Taqwa` above. Best
    // effort, and Taqwa is the only thing that has ever put a file there.
    fun documentsFallback(): String {
        documentsUrl.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        return documents
    }
    val support = fm.directory(NSApplicationSupportDirectory)
    val dir = support.URLByAppendingPathComponent("Taqwa", isDirectory = true) ?: return documentsFallback()
    val dirPath = dir.path ?: return documentsFallback()

    if (!fm.fileExistsAtPath(dirPath)) {
        val made = fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        if (!made) return documentsFallback()
    }
    // Set every time, not only on creation: the attribute is cheap and a restore from an old
    // backup could bring the directory back without it.
    dir.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)

    val oldFile = "$documents/$SETTINGS_FILE"
    val newFile = "$dirPath/$SETTINGS_FILE"
    if (fm.fileExistsAtPath(oldFile)) {
        if (!fm.fileExistsAtPath(newFile)) {
            val moved = fm.moveItemAtPath(oldFile, toPath = newFile, error = null)
            if (!moved) return documentsFallback()
        }
        // A move is a rename, so this is usually nothing — but an iCloud restore can put the
        // pre-0.11 file back beside a new one that is already in use, and that copy has the
        // reader's coordinates in it and would go on being backed up. Whichever way we got here,
        // Documents does not keep one.
        fm.removeItemAtPath(oldFile, null)
    }
    return dirPath
}

@OptIn(ExperimentalForeignApi::class)
private fun NSFileManager.directory(which: NSSearchPathDirectory): NSURL = requireNotNull(
    URLForDirectory(
        directory = which,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ),
)
