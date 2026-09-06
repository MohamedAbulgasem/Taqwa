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
     * The Notification level once meant the phone's default tone and now means Taqwa's own
     * chime. By the same immutability rule, that is a different channel: the suffix makes Android
     * create it afresh instead of keeping the old sound under the old id.
     */
    private const val CHIME_SUFFIX = "_chime"

    fun channelId(prayer: Prayer, sound: PrayerSound): String =
        "prayer_${prayer.name.lowercase()}_${sound.name.lowercase()}" +
            if (sound == PrayerSound.NOTIFICATION) CHIME_SUFFIX else ""

    /** Every channel this prayer could ever have had, across all four sounds and including the
     * pre-chime Notification id, so an upgrade deletes it — the set the Android scheduler checks
     * when deciding which stale channels it may delete. */
    fun allChannelIdsFor(prayer: Prayer): List<String> =
        PrayerSound.entries.map { channelId(prayer, it) } +
            "prayer_${prayer.name.lowercase()}_${PrayerSound.NOTIFICATION.name.lowercase()}"
}
