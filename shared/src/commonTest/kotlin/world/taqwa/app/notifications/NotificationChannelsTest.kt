package world.taqwa.app.notifications

import world.taqwa.app.domain.AdhanVoice
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

    // The whole upgrade story: a user who never opens the new setting must keep the channels they
    // already have, which means the original voice has to spell every id exactly as the
    // voice-less build did. These literals are that build's output, written out rather than
    // derived, so a refactor of the id format cannot quietly agree with itself.
    @Test
    fun theOriginalVoiceProducesExactlyTheChannelIdsTheAppAlreadyShipped() {
        assertEquals("prayer_fajr_silent", NotificationChannels.channelId(Prayer.FAJR, PrayerSound.SILENT))
        assertEquals("prayer_fajr_notification_chime3", NotificationChannels.channelId(Prayer.FAJR, PrayerSound.NOTIFICATION))
        assertEquals("prayer_fajr_takbir", NotificationChannels.channelId(Prayer.FAJR, PrayerSound.TAKBIR))
        assertEquals("prayer_fajr_adhan", NotificationChannels.channelId(Prayer.FAJR, PrayerSound.ADHAN))
        PrayerSound.entries.forEach { sound ->
            assertEquals(
                NotificationChannels.channelId(Prayer.ISHA, sound),
                NotificationChannels.channelId(Prayer.ISHA, sound, AdhanVoice.ORIGINAL),
                "$sound",
            )
        }
    }

    @Test
    fun anotherVoiceSuffixesTheIdOfTheTwoLevelsThatAreARecording() {
        assertEquals("prayer_asr_takbir_azeez", NotificationChannels.channelId(Prayer.ASR, PrayerSound.TAKBIR, AdhanVoice.AZEEZ))
        assertEquals("prayer_asr_adhan_azemi", NotificationChannels.channelId(Prayer.ASR, PrayerSound.ADHAN, AdhanVoice.AZEMI))
    }

    // Silent has no sound and the chime is the same clip whichever adhan was picked, so neither
    // may fork into one channel per voice.
    @Test
    fun silentAndTheChimeKeepOneChannelAcrossEveryVoice() {
        listOf(PrayerSound.SILENT, PrayerSound.NOTIFICATION).forEach { sound ->
            val ids = AdhanVoice.entries.map { NotificationChannels.channelId(Prayer.DHUHR, sound, it) }
            assertEquals(1, ids.toSet().size, "$sound: $ids")
        }
    }

    @Test
    fun everyPrayerSoundAndVoiceCombinationGetsItsOwnChannel() {
        val voiced = listOf(PrayerSound.TAKBIR, PrayerSound.ADHAN)
        val ids = voiced.flatMap { sound ->
            AdhanVoice.entries.map { NotificationChannels.channelId(Prayer.ISHA, sound, it) }
        }
        assertEquals(voiced.size * AdhanVoice.entries.size, ids.toSet().size, "$ids")
    }

    // A channel the sweep cannot name is a channel the user cannot get rid of.
    @Test
    fun allChannelIdsForContainsEveryIdChannelIdCanProduce() {
        Prayer.entries.forEach { prayer ->
            val all = NotificationChannels.allChannelIdsFor(prayer)
            PrayerSound.entries.forEach { sound ->
                AdhanVoice.entries.forEach { voice ->
                    val id = NotificationChannels.channelId(prayer, sound, voice)
                    assertEquals(1, all.count { it == id }, "$prayer $sound $voice -> $id")
                }
            }
        }
    }

    // The Notification level has carried four sounds under four ids: the phone's default tone,
    // the 2.4 s chime, the six-second bell motif and the Mixkit announce tone. The scheduler
    // deletes whatever is not live, so
    // every superseded id has to be in the set for an upgrade to drop it, and the live id must
    // differ from both or Android would keep an old sound under an old channel.
    @Test
    fun theLiveChimeChannelReplacesBothSupersededNotificationChannels() {
        val ids = NotificationChannels.allChannelIdsFor(Prayer.MAGHRIB)
        val live = NotificationChannels.channelId(Prayer.MAGHRIB, PrayerSound.NOTIFICATION)
        assertEquals("prayer_maghrib_notification_chime3", live)
        listOf("prayer_maghrib_notification", "prayer_maghrib_notification_chime", "prayer_maghrib_notification_chime2").forEach { stale ->
            assertNotEquals(stale, live)
            assertEquals(1, ids.count { it == stale }, stale)
        }
        // Four sounds, of which two fork per voice, plus the three retired chime ids.
        val voicedExtras = 2 * (AdhanVoice.entries.size - 1)
        assertEquals(PrayerSound.entries.size + voicedExtras + 3, ids.toSet().size)
    }

    @Test
    fun staleNotificationIdsAreExactlyTheSupersededOnesAndNeverTheLiveOne() {
        val stale = NotificationChannels.staleNotificationIds(Prayer.FAJR)
        assertEquals(listOf("prayer_fajr_notification", "prayer_fajr_notification_chime", "prayer_fajr_notification_chime2"), stale)
        assertTrue(NotificationChannels.channelId(Prayer.FAJR, PrayerSound.NOTIFICATION) !in stale)
        assertTrue(stale.all { it in NotificationChannels.allChannelIdsFor(Prayer.FAJR) })
    }

    @Test
    fun tahajjudHasChannelsOfItsOwnAndNeverFajrs() {
        val id = NotificationChannels.channelId(Prayer.FAJR, PrayerSound.NOTIFICATION, kind = NotificationKind.TAHAJJUD)
        assertEquals("tahajjud_notification_chime3", id)
        assertTrue(id !in NotificationChannels.allChannelIdsFor(Prayer.FAJR))
        assertTrue(id in NotificationChannels.allTahajjudChannelIds())
    }

    @Test
    fun aReminderStillRidesItsPrayersChannel() {
        assertEquals(
            NotificationChannels.channelId(Prayer.ASR, PrayerSound.NOTIFICATION),
            NotificationChannels.channelId(Prayer.ASR, PrayerSound.NOTIFICATION, kind = NotificationKind.REMINDER),
        )
    }

    @Test
    fun everyTahajjudChannelIsOneTheSweepCanDelete() {
        val all = NotificationChannels.allTahajjudChannelIds()
        PrayerSound.entries.forEach { sound ->
            AdhanVoice.entries.forEach { voice ->
                assertTrue(NotificationChannels.channelId(Prayer.FAJR, sound, voice, NotificationKind.TAHAJJUD) in all)
            }
        }
    }
}
