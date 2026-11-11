package world.taqwa.app.wear.face

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManager.SetWatchFaceAsActiveException
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import java.io.File

/**
 * Watch Face Push as the system gives it (`androidx.wear.watchfacepush` 1.0.0), behind
 * [FacePushPort]. The library works from Wear OS 6 (API 36) alone, and it calls the system library
 * wear-sdk, which no earlier watch has; the manifest lets those watches install the app anyway (the
 * library's minSdk overridden, wear-sdk not required: erratum p5). So the library is touched only
 * where the API level, the library's own [WatchFacePushManagerFactory.isSupported] and the system's
 * shared libraries all say yes. Elsewhere [installed] is null and nothing else is called.
 *
 * Of `setWatchFaceAsActive`'s four codes (`SetWatchFaceAsActiveException`), only
 * ERROR_MAXIMUM_ATTEMPTS_REACHED (the one activation spent) and ERROR_MISSING_PERMISSION are
 * [FaceRefused]: ERROR_UNKNOWN and ERROR_INVALID_SLOT_ID are passing, and leave the activation for
 * the next tap (erratum p5).
 */
internal class AndroidFacePushPort(private val context: Context) : FacePushPort {

    private val manager: WatchFacePushManager? by lazy {
        if (supported(context)) WatchFacePushManagerFactory.createWatchFacePushManager(context) else null
    }

    override suspend fun installed(): List<InstalledFace>? =
        manager?.listWatchFaces()?.installedWatchFaceDetails?.map { InstalledFace(it.slotId, it.packageName, it.versionCode) }

    override suspend fun remainingSlots(): Int = manager().listWatchFaces().remainingSlotCount

    override suspend fun add(apk: File, token: String): InstalledFace =
        open(apk).use { manager().addWatchFace(it, token) }.let { InstalledFace(it.slotId, it.packageName, it.versionCode) }

    override suspend fun update(slotId: String, apk: File, token: String): InstalledFace =
        open(apk).use { manager().updateWatchFace(slotId, it, token) }.let { InstalledFace(it.slotId, it.packageName, it.versionCode) }

    override suspend fun isActive(packageName: String): Boolean = manager().isWatchFaceActive(packageName)

    override suspend fun setActive(slotId: String) {
        try {
            manager().setWatchFaceAsActive(slotId)
        } catch (e: SetWatchFaceAsActiveException) {
            if (e.errorCode in REFUSED) throw FaceRefused(e)
            throw e
        }
    }

    private fun manager(): WatchFacePushManager = checkNotNull(manager) { "no Watch Face Push on this watch" }

    private fun open(apk: File): ParcelFileDescriptor = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY)

    companion object {
        private val REFUSED = setOf(
            SetWatchFaceAsActiveException.ERROR_MAXIMUM_ATTEMPTS_REACHED,
            SetWatchFaceAsActiveException.ERROR_MISSING_PERMISSION,
        )

        /** Whether this watch has Watch Face Push: Wear OS 6 or later, and the system's wear-sdk. */
        fun supported(context: Context): Boolean =
            Build.VERSION.SDK_INT >= WEAR_OS_6 &&
                WatchFacePushManagerFactory.isSupported() &&
                context.packageManager.systemSharedLibraryNames.orEmpty().contains(WEAR_SDK)

        private const val WEAR_OS_6 = 36
        private const val WEAR_SDK = "wear-sdk"
    }
}
