package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    // The Notification level has carried three sounds under three ids: the phone's default tone,
    // the 2.4 s chime, and the six-second one. The scheduler deletes whatever is not live, so
    // every superseded id has to be in the set for an upgrade to drop it, and the live id must
    // differ from both or Android would keep an old sound under an old channel.
    @Test
    fun theLiveChimeChannelReplacesBothSupersededNotificationChannels() {
        val ids = NotificationChannels.allChannelIdsFor(Prayer.MAGHRIB)
        val live = NotificationChannels.channelId(Prayer.MAGHRIB, PrayerSound.NOTIFICATION)
        assertEquals("prayer_maghrib_notification_chime2", live)
        listOf("prayer_maghrib_notification", "prayer_maghrib_notification_chime").forEach { stale ->
            assertNotEquals(stale, live)
            assertEquals(1, ids.count { it == stale }, stale)
        }
        assertEquals(PrayerSound.entries.size + 2, ids.toSet().size)
    }

    @Test
    fun staleNotificationIdsAreExactlyTheSupersededOnesAndNeverTheLiveOne() {
        val stale = NotificationChannels.staleNotificationIds(Prayer.FAJR)
        assertEquals(listOf("prayer_fajr_notification", "prayer_fajr_notification_chime"), stale)
        assertTrue(NotificationChannels.channelId(Prayer.FAJR, PrayerSound.NOTIFICATION) !in stale)
        assertTrue(stale.all { it in NotificationChannels.allChannelIdsFor(Prayer.FAJR) })
    }
}
