package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

/**
 * Android channel identity. A channel's sound is immutable after creation, so changing a
 * prayer's sound must create a channel Android has never seen before rather than editing the
 * old one — channel id therefore encodes the sound, not just the prayer.
 */
object NotificationChannels {
    /**
     * The Notification level's sound has changed twice: the phone's default tone, then Taqwa's
     * own 2.4 s chime, and now a longer and brighter six-second one that carries in an office.
     * Each change needs an id Android has never seen, so the suffix names the generation of the
     * chime rather than just saying "chime"; the ids left behind are in [staleNotificationIds].
     */
    private const val CHIME_SUFFIX = "_chime2"

    /**
     * Notification-level suffixes this app has used before [CHIME_SUFFIX]: `""` is the original
     * default-tone channel and `_chime` the 2.4 s chime. They only ever get added to — an id
     * dropped from here is an id the scheduler stops deleting, which leaves a user stuck with an
     * old sound under a channel they cannot remove.
     */
    private val RETIRED_CHIME_SUFFIXES = listOf("", "_chime")

    fun channelId(prayer: Prayer, sound: PrayerSound): String =
        base(prayer, sound) + if (sound == PrayerSound.NOTIFICATION) CHIME_SUFFIX else ""

    /** Every channel id the Notification level has ever used and no longer does. */
    fun staleNotificationIds(prayer: Prayer): List<String> =
        RETIRED_CHIME_SUFFIXES.map { base(prayer, PrayerSound.NOTIFICATION) + it }

    /** Every channel this prayer could ever have had, across all four sounds and including every
     * superseded Notification id, so an upgrade deletes them — the set the Android scheduler
     * checks when deciding which stale channels it may delete. */
    fun allChannelIdsFor(prayer: Prayer): List<String> =
        PrayerSound.entries.map { channelId(prayer, it) } + staleNotificationIds(prayer)

    private fun base(prayer: Prayer, sound: PrayerSound): String =
        "prayer_${prayer.name.lowercase()}_${sound.name.lowercase()}"
}
