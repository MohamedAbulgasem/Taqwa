package world.taqwa.app.wear.face

import java.io.File
import java.io.IOException

/**
 * The bundled face on disk, copied out of the app's APK and read once for each install of the app
 * (plan 5 Task 5 Step 4), not at each process start, which on a watch is every complication, Tile,
 * Data Layer or nudge wake (P5.5 review round 1; Google: "Your app should run as little and
 * infrequently as possible").
 * - [cache] holds the copy the system is handed, `face-<versionCode>.apk`. The system may clear the
 *   cache at any time, so [BundledFace.apk] copies the face again when its file is gone, without
 *   reading it again. Only an add or an update asks for it.
 * - [record] holds `<install> <versionCode>`: the app's install ([load]'s `install`, its version code
 *   and last update time) and the version code read from the face it bundles. It lives outside the
 *   cache, so a start that finds it copies and reads nothing, and a cleared cache costs one copy at
 *   the next add or update.
 *
 * [copy] writes the bundled asset to a file, and throws [IOException] when the build carries none.
 * [versionOf] reads an APK's version code (PackageManager.getPackageArchiveInfo), null when the file
 * is no APK. A copy is written beside the face's name and renamed onto it, so a copy cut short never
 * passes for the face.
 */
internal class FaceStage(
    private val cache: File,
    private val record: File,
    private val copy: (File) -> Unit,
    private val versionOf: (File) -> Long?,
    private val log: (String) -> Unit = {},
) {

    /** The bundled face for this [install] of the app, or null when [token] is empty or the build carries no face. */
    fun load(token: String, install: String): BundledFace? {
        if (token.isEmpty()) return null
        val versionCode = recorded(install) ?: staged(install) ?: return null
        return BundledFace(token, versionCode) {
            apkOf(versionCode).takeIf { it.isFile }
                ?: copied { part -> moved(part, versionCode) }.also { log("TaqwaFace bundled $versionCode copied again") }
        }
    }

    /** The version code recorded for [install], or null for another install's record, or none. */
    private fun recorded(install: String): Long? = try {
        record.takeIf { it.isFile }?.readText()?.trim()?.split(' ')
            ?.takeIf { it.size == 2 && it[0] == install }
            ?.get(1)?.toLongOrNull()
    } catch (_: IOException) {
        null
    }

    /** The first start of an [install]: the face copied, read, and its version code recorded. */
    private fun staged(install: String): Long? = try {
        copied { part ->
            val versionCode = versionOf(part)
            if (versionCode == null) {
                log("TaqwaFace bundled none unreadable")
            } else {
                val apk = moved(part, versionCode)
                // Earlier installs' copies are no longer the bundled face.
                cache.listFiles { f -> f.isFace() && f != apk }?.forEach { it.delete() }
                remember(install, versionCode)
                log("TaqwaFace bundled $versionCode copied")
            }
            versionCode
        }
    } catch (e: IOException) {
        log("TaqwaFace bundled none ${e.javaClass.simpleName}")
        null
    }

    /** [use] given the asset copied to a part file, which is gone afterwards unless [use] moved it. */
    private inline fun <T> copied(use: (File) -> T): T {
        val part = File(cache.apply { mkdirs() }, PART)
        try {
            copy(part)
            return use(part)
        } finally {
            part.delete()
        }
    }

    private fun moved(part: File, versionCode: Long): File =
        apkOf(versionCode).also { if (!part.renameTo(it)) throw IOException("the face could not be staged") }

    private fun remember(install: String, versionCode: Long) {
        val part = File(record.path + ".part")
        try {
            part.writeText("$install $versionCode\n")
            if (!part.renameTo(record)) part.delete()
        } catch (_: IOException) {
            // Not remembered: the next start copies and reads the face again, as it did before.
            part.delete()
        }
    }

    private fun apkOf(versionCode: Long): File = File(cache, "$PREFIX$versionCode$SUFFIX")

    private fun File.isFace(): Boolean = isFile && name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    private companion object {
        const val PREFIX = "face-"
        const val SUFFIX = ".apk"
        const val PART = "face.part"
    }
}
