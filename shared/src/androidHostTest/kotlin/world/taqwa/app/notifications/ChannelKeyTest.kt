package world.taqwa.app.notifications

import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The alarm receiver posts into `channelId(prayer, sound, voice, kind)` from the alarm's own
 * extras, and makes that channel through [channelKeyOf] when it is missing (the adhan report,
 * 7 Nov 2026). The two must name the same channel for every alarm there can be, or the channel it
 * makes is not the one it posts into and the post is dropped as before.
 *
 * Pure: only the key and the id are computed, so no Android class is loaded.
 */
class ChannelKeyTest {

    @Test
    fun `the channel made for a missing one is the channel the alarm posts into`() {
        for (kind in NotificationKind.entries) {
            for (prayer in Prayer.entries) {
                for (sound in PrayerSound.entries) {
                    for (voice in AdhanVoice.entries) {
                        val key = channelKeyOf(prayer, sound, voice, kind)
                        assertEquals(
                            NotificationChannels.channelId(prayer, sound, voice, kind),
                            NotificationChannels.channelId(key.prayer, key.sound, key.voice, key.kind),
                            "$kind $prayer $sound $voice",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the key keeps the sound and the voice and folds only the prayer and the kind`() {
        assertEquals(
            ChannelKey(Prayer.ASR, PrayerSound.ADHAN, AdhanVoice.MAKKAH, NotificationKind.PRAYER),
            channelKeyOf(Prayer.ASR, PrayerSound.ADHAN, AdhanVoice.MAKKAH, NotificationKind.REMINDER),
        )
        assertEquals(
            ChannelKey(Prayer.FAJR, PrayerSound.TAKBIR, AdhanVoice.AZEEZ, NotificationKind.ADHKAR_MORNING),
            channelKeyOf(Prayer.ASR, PrayerSound.TAKBIR, AdhanVoice.AZEEZ, NotificationKind.ADHKAR_EVENING),
        )
        assertEquals(
            ChannelKey(Prayer.SUNRISE, PrayerSound.NOTIFICATION, AdhanVoice.ORIGINAL, NotificationKind.DUHA_BEST),
            channelKeyOf(Prayer.SUNRISE, PrayerSound.NOTIFICATION, AdhanVoice.ORIGINAL, NotificationKind.DUHA_START),
        )
        assertEquals(
            ChannelKey(Prayer.FAJR, PrayerSound.NOTIFICATION, AdhanVoice.ORIGINAL, NotificationKind.IQAMA),
            channelKeyOf(Prayer.ISHA, PrayerSound.NOTIFICATION, AdhanVoice.ORIGINAL, NotificationKind.IQAMA),
        )
        assertEquals(
            ChannelKey(Prayer.FAJR, PrayerSound.ADHAN, AdhanVoice.MADINAH, NotificationKind.TAHAJJUD),
            channelKeyOf(Prayer.FAJR, PrayerSound.ADHAN, AdhanVoice.MADINAH, NotificationKind.TAHAJJUD),
        )
    }
}
