package world.taqwa.app.wear.face

import java.io.File
import java.io.IOException

/** The Taqwa face as this app last saw it: its version code, and whether it was the watch's face. */
internal data class FaceSeen(val versionCode: Long, val active: Boolean)

/**
 * What the app keeps of the Taqwa face, in [file], so that it outlives the process:
 * - [spent]: whether the one activation, or the one refusal of its permission, is used (Google: the
 *   active face "can be set by this means only once", and the permission "has a maximum rejection
 *   count of 1"). After it, neither Today's chip nor the phone's row asks again.
 * - [seen]: the face's version and activity when last seen, so that a version this process did
 *   not make (the system updates the bundled face with the app on Wear OS 6, seen on emulator-5558
 *   in plan 5 Task 5's check 4) is known as a change just made, which the system must not be asked
 *   about at once ([FacePush]).
 *
 * Each is set in memory first and written as well as the disk allows, so a failed write still
 * holds for the life of the process. A reinstall of the app removes the file. Whether the system's
 * own count resets with it is not Taqwa's to know: a tap that finds it spent is told so
 * ([FaceRefused]) and spends this one too.
 */
internal class FaceMemory(private val file: File) {

    private var loaded = false
    private var spentNow = false
    private var seenNow: FaceSeen? = null

    var spent: Boolean
        get() = load().spentNow
        set(value) {
            load().spentNow = value
            save()
        }

    var seen: FaceSeen?
        get() = load().seenNow
        set(value) {
            if (load().seenNow == value) return
            seenNow = value
            save()
        }

    private fun load(): FaceMemory {
        if (loaded) return this
        loaded = true
        val lines = try {
            if (file.isFile) file.readLines() else emptyList()
        } catch (_: IOException) {
            emptyList()
        }
        spentNow = SPENT in lines
        seenNow = lines.firstOrNull { it.startsWith(SEEN) }?.removePrefix(SEEN)?.split(' ')?.let { parts ->
            parts.getOrNull(0)?.toLongOrNull()?.let { code -> FaceSeen(code, parts.getOrNull(1) == ACTIVE) }
        }
        return this
    }

    private fun save() {
        val text = buildString {
            if (spentNow) appendLine(SPENT)
            seenNow?.let { appendLine("$SEEN${it.versionCode} ${if (it.active) ACTIVE else INACTIVE}") }
        }
        try {
            if (text.isEmpty()) file.delete() else file.writeText(text)
        } catch (_: IOException) {
            // Kept in memory: the process holds it, and the next write tries again.
        }
    }

    private companion object {
        const val SPENT = "spent"
        const val SEEN = "seen "
        const val ACTIVE = "active"
        const val INACTIVE = "inactive"
    }
}
