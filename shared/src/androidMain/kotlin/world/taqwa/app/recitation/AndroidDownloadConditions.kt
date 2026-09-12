package world.taqwa.app.recitation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs

/**
 * What the device can offer a download right now: the kind of network, and the room on the volume
 * the audio is written to.
 *
 * WorkManager's own `NetworkType.UNMETERED` constraint already keeps a Wi-Fi-only download from
 * starting on mobile data, and this asks the same question again inside the worker. That is not
 * redundant: a constraint that is not met leaves the work sitting silently in the queue, whereas
 * the reader who tapped Download is owed a sentence saying why nothing is happening. The check
 * here is what turns silence into [DownloadFailure.NEEDS_WIFI].
 */
class AndroidDownloadConditions(private val context: Context) : DownloadConditions {

    override suspend fun network(): NetworkKind {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkKind.NONE
        val active = manager.activeNetwork ?: return NetworkKind.NONE
        val capabilities = manager.getNetworkCapabilities(active) ?: return NetworkKind.NONE
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkKind.NONE
        }
        // NOT_METERED rather than "is it Wi-Fi": a metered hotspot is mobile data with extra
        // steps, and an unmetered mobile connection is one the reader is not paying by the byte
        // for. The platform's own answer is the one the setting means.
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            NetworkKind.UNMETERED
        } else {
            NetworkKind.METERED
        }
    }

    override suspend fun freeBytes(): Long = try {
        StatFs(context.filesDir.absolutePath).availableBytes
    } catch (e: IllegalArgumentException) {
        // A volume that cannot be stat'd is not a volume a download should be started against;
        // zero free space refuses it, which is the safe direction.
        0L
    }
}
