package world.taqwa.app.recitation

import okio.Path
import okio.Path.Companion.toPath
import world.taqwa.app.settings.appContext

/**
 * `context.filesDir` — the same base DataStore writes `taqwa.preferences_pb` into. App-private,
 * excluded from cloud backup by the manifest's `android:allowBackup` rules, and removed with the
 * app, which is exactly what spec §8 asks of downloaded audio.
 */
actual fun recitationFilesDirectory(): Path = appContext.filesDir.path.toPath()
