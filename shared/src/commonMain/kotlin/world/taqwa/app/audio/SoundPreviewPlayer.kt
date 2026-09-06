package world.taqwa.app.audio

import world.taqwa.app.domain.PrayerSound

/** Lets the sound sheet's play button audition a choice before it is committed to. */
interface SoundPreviewPlayer {
    fun play(sound: PrayerSound)
    fun stop()
}

expect fun createSoundPreviewPlayer(): SoundPreviewPlayer
