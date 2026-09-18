package world.taqwa.app.notifications

import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

/**
 * Android channel identity. A channel's sound is immutable after creation, so changing a
 * prayer's sound must create a channel Android has never seen before rather than editing the
 * old one — channel id therefore encodes the sound, not just the prayer. The chosen adhan voice
 * changes the sound of the Takbir and Adhan levels in exactly the same way, so it is part of the
 * id too.
 */
object NotificationChannels {
    /**
     * The Notification level's sound has changed twice: the phone's default tone, then Taqwa's
     * own 2.4 s chime, and now a longer and brighter six-second one that carries in an office.
     * Each change needs an id Android has never seen, so the suffix names the generation of the
     * chime rather than just saying "chime"; the ids left behind are in [staleNotificationIds].
     */
    private const val CHIME_SUFFIX = "_chime3"

    /**
     * Notification-level suffixes this app has used before [CHIME_SUFFIX]: `""` is the original
     * default-tone channel and `_chime` the 2.4 s chime. They only ever get added to — an id
     * dropped from here is an id the scheduler stops deleting, which leaves a user stuck with an
     * old sound under a channel they cannot remove.
     */
    private val RETIRED_CHIME_SUFFIXES = listOf("", "_chime", "_chime2")

    /**
     * The levels whose sound is a recording of a muezzin, and so the only ones the voice can
     * change. Silent has no sound and Notification is the chime, which is the same clip whichever
     * adhan the user picked.
     */
    private val VOICED_SOUNDS = setOf(PrayerSound.TAKBIR, PrayerSound.ADHAN)

    /**
     * The voice suffix is empty for [AdhanVoice.ORIGINAL], so every channel that existed before
     * this setting did keeps the exact id it already has: an upgrade creates nothing, deletes
     * nothing and churns nothing for the user who never opens the setting.
     */
    fun channelId(
        prayer: Prayer,
        sound: PrayerSound,
        voice: AdhanVoice = AdhanVoice.ORIGINAL,
        kind: NotificationKind = NotificationKind.PRAYER,
    ): String = base(prayer, sound, kind) +
        when {
            sound == PrayerSound.NOTIFICATION -> CHIME_SUFFIX
            sound in VOICED_SOUNDS && voice != AdhanVoice.ORIGINAL -> "_${voice.name.lowercase()}"
            else -> ""
        }

    /** Every channel id the Notification level has ever used and no longer does. */
    fun staleNotificationIds(prayer: Prayer): List<String> =
        RETIRED_CHIME_SUFFIXES.map { base(prayer, PrayerSound.NOTIFICATION) + it }

    /** Every channel this prayer could ever have had — all four sounds across every voice, plus
     * every superseded Notification id, so an upgrade deletes them. This is the set the Android
     * scheduler checks when deciding which stale channels it may delete, so it has to enumerate
     * every id [channelId] can return or a channel becomes undeletable. */
    fun allChannelIdsFor(prayer: Prayer): List<String> =
        PrayerSound.entries
            .flatMap { sound -> AdhanVoice.entries.map { voice -> channelId(prayer, sound, voice) } }
            .distinct() + staleNotificationIds(prayer)

    /**
     * Every channel Tahajjud could ever have had. It has channels of its own rather than Fajr's
     * (spec §17.6): the system's channel list is where a person silences one kind of
     * notification and keeps another, and a night prayer filed under "Fajr · Notification"
     * could not be told from the reminder that shares that channel.
     */
    fun allTahajjudChannelIds(): List<String> =
        PrayerSound.entries
            .flatMap { sound -> AdhanVoice.entries.map { voice -> channelId(Prayer.FAJR, sound, voice, NotificationKind.TAHAJJUD) } }
            .distinct()

    // A reminder rides its prayer's own Notification channel, as it always has; only Tahajjud
    // leaves the prayer's namespace.
    private fun base(prayer: Prayer, sound: PrayerSound, kind: NotificationKind = NotificationKind.PRAYER): String =
        if (kind == NotificationKind.TAHAJJUD) {
            "tahajjud_${sound.name.lowercase()}"
        } else {
            "prayer_${prayer.name.lowercase()}_${sound.name.lowercase()}"
        }
}
