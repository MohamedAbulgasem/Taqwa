package world.taqwa.app.audio

import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.PrayerSound

/** Lets the sound sheet's play button audition a choice before it is committed to. */
interface SoundPreviewPlayer {
    /** [voice] chooses the recording for the two levels that are one; it is ignored by the
     * chime and by Silent. The adhan voice sheet auditions a voice by asking for `ADHAN`. */
    fun play(sound: PrayerSound, voice: AdhanVoice = AdhanVoice.ORIGINAL)
    fun stop()
}

expect fun createSoundPreviewPlayer(): SoundPreviewPlayer
