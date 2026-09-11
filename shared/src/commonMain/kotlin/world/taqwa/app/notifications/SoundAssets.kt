package world.taqwa.app.notifications

import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.PrayerSound
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bundled sound file names and their measured durations (`assets/audio/README.md`), shared by
 * both schedulers, the Android channel setup and the sound sheet's play button. Silent resolves to
 * nothing: it plays no sound at all. Notification is Taqwa's own chime — a three-note bell motif
 * rung twice — not the phone's default tone, so a prayer never sounds like a message arriving.
 *
 * Everything is keyed on (sound, voice): only Takbir and Adhan carry a voice at all, and the
 * original voice keeps the file names it has had since 0.1.0 so nothing on disk moves.
 */
object SoundAssets {

    /**
     * Every clip's measured length, one row per voice — the single table the sheet subtitles, the
     * captions and the tests all read.
     *
     * Every figure is measured on the shipped file (`assets/audio/README.md`). Both new voices cut
     * their takbir at the first complete "Allahu akbar, Allahu akbar" pair, which is why theirs run
     * about five seconds against the original's sixteen: they are recited plainly where the
     * original holds each phrase. The 30-second clip is whatever whole phrases fit under the cap:
     * four takbirs for the original and Azemi, four takbirs and both shahadas for Azeez.
     */
    private data class VoiceClips(
        val takbir: Duration,
        val adhan30: Duration,
        val adhanFull: Duration,
    )

    private val CLIPS: Map<AdhanVoice, VoiceClips> = mapOf(
        AdhanVoice.ORIGINAL to VoiceClips(takbir = 15.80.seconds, adhan30 = 29.95.seconds, adhanFull = 154.10.seconds),
        AdhanVoice.AZEEZ to VoiceClips(takbir = 5.60.seconds, adhan30 = 27.55.seconds, adhanFull = 86.15.seconds),
        AdhanVoice.AZEMI to VoiceClips(takbir = 5.20.seconds, adhan30 = 24.70.seconds, adhanFull = 211.40.seconds),
    )

    private val CHIME = 3.79.seconds

    /**
     * The voice's file-name infix: nothing at all for the original, so its four files keep the
     * names — and, through [NotificationChannels], the Android channel ids — they already have.
     */
    private fun infix(voice: AdhanVoice, separator: Char): String =
        if (voice == AdhanVoice.ORIGINAL) "" else "$separator${voice.name.lowercase()}"

    private fun clips(voice: AdhanVoice): VoiceClips = CLIPS.getValue(voice)

    /** Android `res/raw/` resource name, no extension — Android resource names forbid hyphens. */
    fun androidRawResourceName(
        sound: PrayerSound,
        voice: AdhanVoice = AdhanVoice.ORIGINAL,
    ): String? = when (sound) {
        PrayerSound.SILENT -> null
        PrayerSound.NOTIFICATION -> "chime"
        PrayerSound.TAKBIR -> "takbir${infix(voice, '_')}"
        PrayerSound.ADHAN -> "adhan${infix(voice, '_')}_30s"
    }

    /** iOS bundle resource file name, with extension. */
    fun iosResourceFileName(
        sound: PrayerSound,
        voice: AdhanVoice = AdhanVoice.ORIGINAL,
    ): String? = when (sound) {
        PrayerSound.SILENT -> null
        PrayerSound.NOTIFICATION -> "chime.caf"
        PrayerSound.TAKBIR -> "takbir${infix(voice, '-')}.caf"
        PrayerSound.ADHAN -> "adhan${infix(voice, '-')}-30s.caf"
    }

    /**
     * What the sound sheet's play button plays. The notification itself is capped at 30 seconds
     * on both platforms, so `ADHAN` there is only the opening; the sheet promises the complete
     * adhan "inside the app", and this is where that promise is kept: the full recording of the
     * chosen voice, bundled once for each platform's preview player only.
     */
    fun androidPreviewRawResourceName(
        sound: PrayerSound,
        voice: AdhanVoice = AdhanVoice.ORIGINAL,
    ): String? = when (sound) {
        PrayerSound.ADHAN -> "adhan${infix(voice, '_')}_full"
        else -> androidRawResourceName(sound, voice)
    }

    fun iosPreviewResourceFileName(
        sound: PrayerSound,
        voice: AdhanVoice = AdhanVoice.ORIGINAL,
    ): String? = when (sound) {
        PrayerSound.ADHAN -> "adhan${infix(voice, '-')}-full.m4a"
        else -> iosResourceFileName(sound, voice)
    }

    /** The length of the clip a notification actually plays. Null only for Silent. */
    fun duration(sound: PrayerSound, voice: AdhanVoice = AdhanVoice.ORIGINAL): Duration? =
        when (sound) {
            PrayerSound.SILENT -> null
            PrayerSound.NOTIFICATION -> CHIME
            PrayerSound.TAKBIR -> clips(voice).takbir
            PrayerSound.ADHAN -> clips(voice).adhan30
        }

    /** The length of the complete recording — what the voice sheet's caption quotes. */
    fun fullAdhanDuration(voice: AdhanVoice): Duration = clips(voice).adhanFull
}
