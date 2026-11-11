package world.taqwa.app.wear.face

import java.io.File

/** The Taqwa face's package: the app's own, then Google's `.watchfacepush.` and the face's name. */
internal const val FACE_PACKAGE = "world.taqwa.app.watchfacepush.taqwa"

/** A face in one of the app's Watch Face Push slots, as the system lists it. */
internal data class InstalledFace(val slotId: String, val packageName: String, val versionCode: Long)

/**
 * Watch Face Push, as narrow as Taqwa needs it: the faces in the app's slots and the slots free, a
 * face added or updated from its APK and validation token, whether a face is the active one, and
 * the one activation. [AndroidFacePushPort] is the system's; the tests play it.
 *
 * Every call may throw: the system's own exceptions for a call it refuses, and anything a service
 * that answers an error throws. [FacePush] decides what each means. [setActive] throws
 * [FaceRefused] only when the system will never make Taqwa's face active from here: the activation
 * spent, or its permission missing. Any other failure of it is passing.
 */
internal interface FacePushPort {
    /** The faces in the app's slots, or null where this watch has no Watch Face Push. */
    suspend fun installed(): List<InstalledFace>?

    suspend fun remainingSlots(): Int

    suspend fun add(apk: File, token: String): InstalledFace

    suspend fun update(slotId: String, apk: File, token: String): InstalledFace

    suspend fun isActive(packageName: String): Boolean

    /** Makes the face in [slotId] the watch's face. Throws [FaceRefused] once spent. */
    suspend fun setActive(slotId: String)
}

/** The system will not make the face active from the app again: the one activation, or the one refusal, is used. */
internal class FaceRefused(cause: Throwable?) : Exception(cause)
