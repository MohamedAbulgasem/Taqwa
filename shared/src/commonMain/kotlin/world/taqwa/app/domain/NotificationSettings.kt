package world.taqwa.app.domain

/** The four levels of the spec's sound sheet, in the order the sheet shows them. */
enum class PrayerSound { SILENT, NOTIFICATION, TAKBIR, ADHAN }

/**
 * Which recording the Takbir and Adhan levels play — one voice for all five prayers, never one
 * per prayer. [ORIGINAL] is the recording shipped since 0.1.0 and stays the default, so a user
 * who never opens the setting hears exactly what they heard before.
 */
enum class AdhanVoice { ORIGINAL, AZEEZ, AZEMI }

/**
 * When the Duha reminder rings (Duha and Iqama spec §2): at its best time, midway between sunrise
 * and solar noon, or once its time is in, which is when every authority Taqwa knows agrees it has
 * begun. [BEST] is the default.
 */
enum class DuhaTiming { BEST, START }

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
    /**
     * A reminder for the morning and evening adhkar, half an hour after Fajr and after Asr
     * (adhkar spec §5). Off until someone turns it on, like Tahajjud: it is a nudge towards
     * something voluntary, and nobody's plan changes until they ask for it.
     */
    val adhkar: Boolean = false,
    val adhkarSound: PrayerSound = PrayerSound.NOTIFICATION,
    /**
     * A reminder for Duha, the forenoon prayer (Duha and Iqama spec §2): off until someone turns
     * it on, like Tahajjud, and rung at [duhaTiming].
     */
    val duha: Boolean = false,
    val duhaTiming: DuhaTiming = DuhaTiming.BEST,
    val duhaSound: PrayerSound = PrayerSound.NOTIFICATION,
    /**
     * A reminder some minutes after each adhan, for the iqama (spec §3). Off by default. Each
     * prayer's minutes stay stored while it is off, so turning it back on brings back the
     * person's own; 0 is Off for that prayer.
     */
    val iqama: Boolean = false,
    val iqamaMinutes: Map<Prayer, Int> = IqamaDefaults,
    val iqamaSound: PrayerSound = PrayerSound.NOTIFICATION,
) {
    /** Takbir is the default for every prayer, including one the map has never held. */
    fun soundFor(prayer: Prayer): PrayerSound = sounds[prayer] ?: PrayerSound.TAKBIR

    /** The minutes after [prayer]'s adhan its iqama reminder rings; a gap never set is its default. */
    fun iqamaMinutesFor(prayer: Prayer): Int = iqamaMinutes[prayer] ?: IqamaDefaults[prayer] ?: 0

    companion object {
        /** "Remind me before" choices, in minutes. 0 is Never, and is the default. */
        val LeadOptions = listOf(0, 5, 10, 15, 30)

        /** What Tahajjud may sound like. Not the adhan: no adhan is called for a night prayer. */
        val TahajjudSounds = listOf(PrayerSound.SILENT, PrayerSound.NOTIFICATION, PrayerSound.TAKBIR)

        /** What the adhkar reminder may sound like: Tahajjud's levels, for the same reason. */
        val AdhkarSounds = TahajjudSounds

        /** What the Duha reminder may sound like: Tahajjud's levels. No adhan is called for Duha. */
        val DuhaSounds = TahajjudSounds

        /** What the iqama reminder may sound like: Tahajjud's levels, never a second adhan. */
        val IqamaSounds = TahajjudSounds

        /** The iqama gaps on offer, in minutes after the adhan. 0 is Off for that prayer. */
        val IqamaOptions = listOf(0, 5, 10, 15, 20, 25, 30)

        /** Each prayer's gap until the person sets their own: a short one for Maghrib, a long one for Fajr. */
        val IqamaDefaults: Map<Prayer, Int> = mapOf(
            Prayer.FAJR to 15,
            Prayer.DHUHR to 10,
            Prayer.ASR to 10,
            Prayer.MAGHRIB to 5,
            Prayer.ISHA to 10,
        )
    }
}
