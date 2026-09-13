package world.taqwa.app.recitation

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Application Support inside the app's own container, not Documents.
 *
 * DataStore's own directory is Documents, and this deliberately parts company with it: Documents
 * is where a user's *own* files belong, and a gigabyte of audio the app fetched and can fetch
 * again is not that. Apple's guidance is Application Support with the backup flag cleared, and
 * `NSURLIsExcludedFromBackupKey` is set here for the reason spec §8 gives — the downloads are
 * disposable, and iCloud must not be asked to carry them.
 *
 * The directory does not exist on a fresh install (Documents does; Application Support does not),
 * so it is created before it is handed out.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun recitationFilesDirectory(): Path {
    val manager = NSFileManager.defaultManager
    val url: NSURL = manager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )!!
    // Best effort, once per launch and cheap: a failure here costs a backup that is larger than it
    // needs to be, which is not worth failing a launch over.
    url.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    return requireNotNull(url.path).toPath()
}
