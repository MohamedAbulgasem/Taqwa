package world.taqwa.app.wear.face

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.IOException
import world.taqwa.app.wear.R

/**
 * The face the app carries (plan 5 Task 4): `assets/default_watchface.apk`, its version code, and
 * the validation token Google's validator gave that very APK (`@string/default_wf_token`), which
 * FaceBundleTask writes at build time. The system installs it into Taqwa's slot with the app; this
 * is what [FacePush] adds again or updates the slot to.
 *
 * Its [versionCode] is known without touching the APK, and [apk] is asked for only by an add or an
 * update: [FaceStage] copies the face out of the app and reads it once for each install of the app,
 * and copies it again when the system has cleared the cache since.
 */
internal class BundledFace(val token: String, val versionCode: Long, private val staged: () -> File) {

    /** A face already on disk, as the tests give it. */
    constructor(apk: File, token: String, versionCode: Long) : this(token, versionCode, { apk })

    /** The face's APK, `cacheDir/face-<versionCode>.apk`, copied again if it is gone. Throws [IOException] when it cannot be. */
    fun apk(): File = staged()

    companion object {
        private const val ASSET = "default_watchface.apk"
        private const val RECORD = "face-bundled"
        private const val TAG = "TaqwaFace"

        /**
         * The bundled face, or null when the build carries no face or no token. A process start
         * after the first of an install reads only the token and [FaceStage]'s record.
         */
        fun load(context: Context): BundledFace? {
            val token = context.getString(R.string.default_wf_token).trim()
            if (token.isEmpty()) return null
            val install = try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "${info.longVersionCode}:${info.lastUpdateTime}"
            } catch (e: PackageManager.NameNotFoundException) {
                Log.i(TAG, "TaqwaFace bundled none ${e.javaClass.simpleName}")
                return null
            }
            return FaceStage(
                cache = context.cacheDir,
                record = File(context.noBackupFilesDir, RECORD),
                copy = { file -> context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } } },
                versionOf = { file ->
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(file.path, 0)?.longVersionCode
                },
                log = { Log.i(TAG, it) },
            ).load(token, install)
        }
    }
}
