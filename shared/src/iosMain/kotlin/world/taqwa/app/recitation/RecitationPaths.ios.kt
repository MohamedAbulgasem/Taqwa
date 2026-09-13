package world.taqwa.app.recitation

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
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
    val url = manager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: return documentsFallback()
    // Best effort, once per launch and cheap: a failure here costs a backup that is larger than it
    // needs to be, which is not worth failing a launch over.
    url.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    return (url.path ?: return documentsFallback()).toPath()
}

/**
 * Application Support is always resolvable in practice, but this runs on the launch path — the
 * lazy `recitationPaths` is touched by `reconcile()` on every start — and throwing there loses the
 * whole app over a directory lookup. Documents is the same fallback `DataStoreFactory.ios.kt`
 * takes: audio in a backed-up directory is a far smaller failure than an app that will not open.
 */
private fun documentsFallback(): Path {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
    return (documents ?: NSTemporaryDirectory()).toPath()
}
