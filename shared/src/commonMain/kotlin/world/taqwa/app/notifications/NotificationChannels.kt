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
            else -> voiceSuffix(sound, voice) + if (sound == PrayerSound.ADHAN) ADHAN_SUFFIX else ""
        }

    /**
     * Android plays the whole adhan from 1.5.0, where it played the 30-second clip before. The
     * sound is part of a channel that cannot change, so the Adhan level moves to ids Android has
     * never seen; the clip-era ids, the same ids without this suffix, are swept by [legacyAdhanIds].
     */
    private const val ADHAN_SUFFIX = "_full"

    private fun voiceSuffix(sound: PrayerSound, voice: AdhanVoice): String =
        if (sound in VOICED_SOUNDS && voice != AdhanVoice.ORIGINAL) "_${voice.name.lowercase()}" else ""

    /** The Adhan level's clip-era ids, before [ADHAN_SUFFIX], so an upgrade deletes them. */
    private fun legacyAdhanIds(prayer: Prayer, kind: NotificationKind): List<String> =
        AdhanVoice.entries.map { voice -> base(prayer, PrayerSound.ADHAN, kind) + voiceSuffix(PrayerSound.ADHAN, voice) }

    private fun everyId(prayer: Prayer, kind: NotificationKind): List<String> =
        (PrayerSound.entries.flatMap { sound -> AdhanVoice.entries.map { voice -> channelId(prayer, sound, voice, kind) } } +
            legacyAdhanIds(prayer, kind)).distinct()

    /** Every channel id the Notification level has ever used and no longer does. */
    fun staleNotificationIds(prayer: Prayer): List<String> =
        RETIRED_CHIME_SUFFIXES.map { base(prayer, PrayerSound.NOTIFICATION) + it }

    /** Every channel this prayer could ever have had — all four sounds across every voice, plus
     * every superseded Notification id, so an upgrade deletes them. This is the set the Android
     * scheduler checks when deciding which stale channels it may delete, so it has to enumerate
     * every id [channelId] can return or a channel becomes undeletable. */
    fun allChannelIdsFor(prayer: Prayer): List<String> =
        everyId(prayer, NotificationKind.PRAYER) + staleNotificationIds(prayer)

    /**
     * Every channel Tahajjud could ever have had. It has channels of its own rather than Fajr's
     * (spec §17.6): the system's channel list is where a person silences one kind of
     * notification and keeps another, and a night prayer filed under "Fajr · Notification"
     * could not be told from the reminder that shares that channel.
     */
    fun allTahajjudChannelIds(): List<String> =
        everyId(Prayer.FAJR, NotificationKind.TAHAJJUD)

    /**
     * Every channel the adhkar reminder could ever have had, for the reason Tahajjud has its own
     * (adhkar spec §5): a reminder to read filed under "Fajr" or "Asr" could not be silenced
     * without silencing the adhan. Morning and evening share one channel, as they share one
     * setting, so whichever [NotificationKind] asks, the ids are the same.
     */
    fun allAdhkarChannelIds(): List<String> =
        everyId(Prayer.FAJR, NotificationKind.ADHKAR_MORNING)

    /**
     * Every channel the Duha reminder could ever have had (Duha and Iqama spec §3.4). Both timings
     * share one channel, as they share one setting, so whichever [NotificationKind] asks, the ids
     * are the same.
     */
    fun allDuhaChannelIds(): List<String> =
        everyId(Prayer.SUNRISE, NotificationKind.DUHA_BEST)

    /**
     * Every channel the iqama reminder could ever have had. All five prayers' iqamas share one,
     * so it can be silenced in the system's list without silencing any adhan.
     */
    fun allIqamaChannelIds(): List<String> =
        everyId(Prayer.FAJR, NotificationKind.IQAMA)

    // A reminder rides its prayer's own Notification channel, as it always has; Tahajjud, the
    // adhkar reminder, Duha and the iqama leave the prayer's namespace.
    private fun base(prayer: Prayer, sound: PrayerSound, kind: NotificationKind = NotificationKind.PRAYER): String =
        when (kind) {
            NotificationKind.TAHAJJUD -> "tahajjud_${sound.name.lowercase()}"
            NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING -> "adhkar_${sound.name.lowercase()}"
            NotificationKind.DUHA_BEST, NotificationKind.DUHA_START -> "duha_${sound.name.lowercase()}"
            NotificationKind.IQAMA -> "iqama_${sound.name.lowercase()}"
            NotificationKind.PRAYER, NotificationKind.REMINDER -> "prayer_${prayer.name.lowercase()}_${sound.name.lowercase()}"
        }
}
