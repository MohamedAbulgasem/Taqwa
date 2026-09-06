package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

/**
 * Android channel identity. A channel's sound is immutable after creation, so changing a
 * prayer's sound must create a channel Android has never seen before rather than editing the
 * old one — channel id therefore encodes the sound, not just the prayer.
 */
object NotificationChannels {
    fun channelId(prayer: Prayer, sound: PrayerSound): String =
        "prayer_${prayer.name.lowercase()}_${sound.name.lowercase()}"

    /** Every channel this prayer could ever have had, across all four sounds — the set the
     * Android scheduler checks when deciding which stale channels it may delete. */
    fun allChannelIdsFor(prayer: Prayer): List<String> =
        PrayerSound.entries.map { channelId(prayer, it) }
}
