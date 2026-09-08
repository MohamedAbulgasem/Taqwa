package world.taqwa.app.notifications

import world.taqwa.app.domain.PrayerSound
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bundled sound file names and their measured durations (`assets/audio/README.md`), shared by
 * both schedulers, the Android channel setup and the sound sheet's play button. Silent resolves to
 * nothing: it plays no sound at all. Notification is Taqwa's own chime — a three-note bell motif
 * rung twice — not the phone's default tone, so a prayer never sounds like a message arriving.
 */
object SoundAssets {

    /** Android `res/raw/` resource name, no extension — Android resource names forbid hyphens. */
    fun androidRawResourceName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.SILENT -> null
        PrayerSound.NOTIFICATION -> "chime"
        PrayerSound.TAKBIR -> "takbir"
        PrayerSound.ADHAN -> "adhan_30s"
    }

    /** iOS bundle resource file name, with extension. */
    fun iosResourceFileName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.SILENT -> null
        PrayerSound.NOTIFICATION -> "chime.caf"
        PrayerSound.TAKBIR -> "takbir.caf"
        PrayerSound.ADHAN -> "adhan-30s.caf"
    }

    /**
     * What the sound sheet's play button plays. The notification itself is capped at 30 seconds
     * on both platforms, so `ADHAN` there is the four-takbir opening; the sheet promises the
     * complete adhan "inside the app", and this is where that promise is kept: the full 2:34
     * recording, bundled once for each platform's preview player only.
     */
    fun androidPreviewRawResourceName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.ADHAN -> "adhan_full"
        else -> androidRawResourceName(sound)
    }

    fun iosPreviewResourceFileName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.ADHAN -> "adhan-full.m4a"
        else -> iosResourceFileName(sound)
    }

    fun duration(sound: PrayerSound): Duration? = when (sound) {
        PrayerSound.SILENT -> null
        PrayerSound.NOTIFICATION -> 3.79.seconds
        PrayerSound.TAKBIR -> 15.80.seconds
        PrayerSound.ADHAN -> 29.95.seconds
    }
}
