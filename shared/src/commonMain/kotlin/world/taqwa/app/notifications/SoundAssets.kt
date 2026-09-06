package world.taqwa.app.notifications

import world.taqwa.app.domain.PrayerSound
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bundled sound file names and their measured durations (`assets/audio/README.md`), shared by
 * both schedulers, the Android channel setup and the sound sheet's play button. Silent and
 * Notification resolve to nothing here: Silent plays no sound at all, and Notification uses
 * whatever system default tone the platform already provides.
 */
object SoundAssets {

    /** Android `res/raw/` resource name, no extension — Android resource names forbid hyphens. */
    fun androidRawResourceName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.SILENT, PrayerSound.NOTIFICATION -> null
        PrayerSound.TAKBIR -> "takbir"
        PrayerSound.ADHAN -> "adhan_30s"
    }

    /** iOS bundle resource file name, with extension. */
    fun iosResourceFileName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.SILENT, PrayerSound.NOTIFICATION -> null
        PrayerSound.TAKBIR -> "takbir.caf"
        PrayerSound.ADHAN -> "adhan-30s.caf"
    }

    fun duration(sound: PrayerSound): Duration? = when (sound) {
        PrayerSound.SILENT, PrayerSound.NOTIFICATION -> null
        PrayerSound.TAKBIR -> 15.80.seconds
        PrayerSound.ADHAN -> 29.95.seconds
    }
}
