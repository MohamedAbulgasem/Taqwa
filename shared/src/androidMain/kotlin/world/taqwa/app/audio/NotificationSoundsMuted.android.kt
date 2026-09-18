package world.taqwa.app.audio

import android.app.NotificationManager
import android.media.AudioManager
import world.taqwa.app.settings.appContext

actual fun notificationSoundsMuted(): Boolean = runCatching {
    val audio = appContext.getSystemService(AudioManager::class.java)
    val notifications = appContext.getSystemService(NotificationManager::class.java)
    val ringerOff = audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    val volumeZero = audio != null && audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0
    // Reading the filter needs no policy access; only changing it does.
    val doNotDisturb = notifications != null &&
        notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    ringerOff || volumeZero || doNotDisturb
}.getOrDefault(false)
