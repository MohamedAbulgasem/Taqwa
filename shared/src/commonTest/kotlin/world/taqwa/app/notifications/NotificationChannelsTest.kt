package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NotificationChannelsTest {

    @Test
    fun changingTheSoundProducesADifferentChannelId() {
        assertNotEquals(
            NotificationChannels.channelId(Prayer.FAJR, PrayerSound.TAKBIR),
            NotificationChannels.channelId(Prayer.FAJR, PrayerSound.ADHAN),
        )
    }

    @Test
    fun theSamePrayerAndSoundAlwaysProduceTheSameId() {
        assertEquals(
            NotificationChannels.channelId(Prayer.ISHA, PrayerSound.NOTIFICATION),
            NotificationChannels.channelId(Prayer.ISHA, PrayerSound.NOTIFICATION),
        )
    }

    @Test
    fun differentPrayersWithTheSameSoundStillGetDifferentChannels() {
        assertNotEquals(
            NotificationChannels.channelId(Prayer.FAJR, PrayerSound.TAKBIR),
            NotificationChannels.channelId(Prayer.DHUHR, PrayerSound.TAKBIR),
        )
    }

    @Test
    fun allChannelIdsForCoversEverySoundExactlyOnce() {
        val ids = NotificationChannels.allChannelIdsFor(Prayer.MAGHRIB)
        PrayerSound.entries.forEach { sound ->
            assertEquals(1, ids.count { it == NotificationChannels.channelId(Prayer.MAGHRIB, sound) }, "$sound")
        }
    }

    // The Notification level used to be the phone's default tone under this id. The scheduler
    // deletes whatever is not live, so the old id has to be in the set for an upgrade to drop it,
    // and the chime channel must be a different id or Android would keep the old sound.
    @Test
    fun theChimeChannelReplacesThePreChimeNotificationChannel() {
        val ids = NotificationChannels.allChannelIdsFor(Prayer.MAGHRIB)
        val chime = NotificationChannels.channelId(Prayer.MAGHRIB, PrayerSound.NOTIFICATION)
        assertNotEquals("prayer_maghrib_notification", chime)
        assertEquals(1, ids.count { it == "prayer_maghrib_notification" })
        assertEquals(PrayerSound.entries.size + 1, ids.toSet().size)
    }
}
