package world.taqwa.app.domain

/** The four levels of the spec's sound sheet, in the order the sheet shows them. */
enum class PrayerSound { SILENT, NOTIFICATION, TAKBIR, ADHAN }

data class NotificationSettings(
    val enabled: Boolean = true,
    val sounds: Map<Prayer, PrayerSound> = ObligatoryPrayers.associateWith { PrayerSound.TAKBIR },
    val remindBeforeMinutes: Int = 0,
) {
    /** Takbir is the default for every prayer, including one the map has never held. */
    fun soundFor(prayer: Prayer): PrayerSound = sounds[prayer] ?: PrayerSound.TAKBIR

    companion object {
        /** "Remind me before" choices, in minutes. 0 is Never, and is the default. */
        val LeadOptions = listOf(0, 5, 10, 15, 30)
    }
}
