package world.taqwa.app.domain

/** The four levels of the spec's sound sheet, in the order the sheet shows them. */
enum class PrayerSound { SILENT, NOTIFICATION, TAKBIR, ADHAN }

/**
 * Which recording the Takbir and Adhan levels play — one voice for all five prayers, never one
 * per prayer. [ORIGINAL] is the recording shipped since 0.1.0 and stays the default, so a user
 * who never opens the setting hears exactly what they heard before.
 */
enum class AdhanVoice { ORIGINAL, AZEEZ, AZEMI }

data class NotificationSettings(
    val enabled: Boolean = true,
    val sounds: Map<Prayer, PrayerSound> = ObligatoryPrayers.associateWith { PrayerSound.TAKBIR },
    val remindBeforeMinutes: Int = 0,
    val voice: AdhanVoice = AdhanVoice.ORIGINAL,
    /**
     * A notification when the last third of the night begins (spec §17.6). Off until someone
     * turns it on: it is a voluntary prayer, and an app that woke everyone at three in the
     * morning by default would be uninstalled by four.
     */
    val tahajjud: Boolean = false,
    val tahajjudSound: PrayerSound = PrayerSound.NOTIFICATION,
) {
    /** Takbir is the default for every prayer, including one the map has never held. */
    fun soundFor(prayer: Prayer): PrayerSound = sounds[prayer] ?: PrayerSound.TAKBIR

    companion object {
        /** "Remind me before" choices, in minutes. 0 is Never, and is the default. */
        val LeadOptions = listOf(0, 5, 10, 15, 30)

        /** What Tahajjud may sound like. Not the adhan: no adhan is called for a night prayer. */
        val TahajjudSounds = listOf(PrayerSound.SILENT, PrayerSound.NOTIFICATION, PrayerSound.TAKBIR)
    }
}
