package world.taqwa.app.notifications

import world.taqwa.app.widget.isIosPlatform
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SoundAssetsTest {

    @Test
    fun silentHasNoBundledFileOrDuration() {
        assertNull(SoundAssets.androidRawResourceName(PrayerSound.SILENT))
        assertNull(SoundAssets.iosResourceFileName(PrayerSound.SILENT))
        assertNull(SoundAssets.duration(PrayerSound.SILENT))
    }

    // Not the phone's default tone: a prayer must never sound like a message arriving. Six
    // seconds rather than the original 2.4 — the short one was missed at a desk.
    @Test
    fun notificationIsTaqwasOwnChime() {
        assertEquals(3.79.seconds, SoundAssets.duration(PrayerSound.NOTIFICATION))
        assertEquals("chime", SoundAssets.androidRawResourceName(PrayerSound.NOTIFICATION))
        assertEquals("chime.caf", SoundAssets.iosResourceFileName(PrayerSound.NOTIFICATION))
    }

    @Test
    fun takbirResolvesToTheMeasuredFifteenPointEightSeconds() {
        assertEquals(15.80.seconds, SoundAssets.duration(PrayerSound.TAKBIR))
        assertEquals("takbir", SoundAssets.androidRawResourceName(PrayerSound.TAKBIR))
        assertEquals("takbir.caf", SoundAssets.iosResourceFileName(PrayerSound.TAKBIR))
    }

    @Test
    fun adhanResolvesToTheMeasuredTwentyNinePointNineFiveSeconds() {
        // Only iOS stops a notification sound at 30 s; Android plays the whole recording.
        assertEquals(if (isIosPlatform) 29.95.seconds else 154.10.seconds, SoundAssets.duration(PrayerSound.ADHAN))
        assertEquals("adhan_full", SoundAssets.androidRawResourceName(PrayerSound.ADHAN))
        assertEquals("adhan-30s.caf", SoundAssets.iosResourceFileName(PrayerSound.ADHAN))
    }

    // The whole point of the original being the default: adding the setting must not rename a
    // single file that shipped before it, because the Android channel ids are built from them.
    @Test
    fun theOriginalVoiceKeepsTheFileNamesItShippedWith() {
        PrayerSound.entries.forEach { sound ->
            assertEquals(
                SoundAssets.androidRawResourceName(sound),
                SoundAssets.androidRawResourceName(sound, AdhanVoice.ORIGINAL),
                "$sound",
            )
            assertEquals(
                SoundAssets.iosResourceFileName(sound),
                SoundAssets.iosResourceFileName(sound, AdhanVoice.ORIGINAL),
                "$sound",
            )
        }
    }

    @Test
    fun everyVoiceHasANameAndADurationForEverySoundButSilent() {
        AdhanVoice.entries.forEach { voice ->
            PrayerSound.entries.filter { it != PrayerSound.SILENT }.forEach { sound ->
                assertNotNull(SoundAssets.androidRawResourceName(sound, voice), "$voice $sound")
                assertNotNull(SoundAssets.iosResourceFileName(sound, voice), "$voice $sound")
                assertNotNull(SoundAssets.androidPreviewRawResourceName(sound, voice), "$voice $sound")
                assertNotNull(SoundAssets.iosPreviewResourceFileName(sound, voice), "$voice $sound")
                assertNotNull(SoundAssets.duration(sound, voice), "$voice $sound")
            }
            assertNull(SoundAssets.androidRawResourceName(PrayerSound.SILENT, voice), "$voice")
            assertNull(SoundAssets.duration(PrayerSound.SILENT, voice), "$voice")
        }
    }

    // Two voices must never resolve to the same clip, or picking one would silently play the other.
    @Test
    fun theVoicedLevelsResolveToADifferentFilePerVoice() {
        listOf(PrayerSound.TAKBIR, PrayerSound.ADHAN).forEach { sound ->
            // A recording from the phone has no bundled files of its own: it borrows the original's.
            val bundled = AdhanVoice.entries.filter { it != AdhanVoice.CUSTOM }
            val android = bundled.map { SoundAssets.androidRawResourceName(sound, it) }
            val ios = bundled.map { SoundAssets.iosResourceFileName(sound, it) }
            assertEquals(bundled.size, android.toSet().size, "$sound android")
            assertEquals(bundled.size, ios.toSet().size, "$sound ios")
            assertEquals(SoundAssets.androidRawResourceName(sound), SoundAssets.androidRawResourceName(sound, AdhanVoice.CUSTOM))
        }
    }

    // The chime is one clip whichever adhan was chosen, so the voice must not reach it.
    @Test
    fun theChimeIsTheSameFileForEveryVoice() {
        AdhanVoice.entries.forEach { voice ->
            assertEquals("chime", SoundAssets.androidRawResourceName(PrayerSound.NOTIFICATION, voice))
            assertEquals("chime.caf", SoundAssets.iosResourceFileName(PrayerSound.NOTIFICATION, voice))
            assertEquals(SoundAssets.duration(PrayerSound.NOTIFICATION), SoundAssets.duration(PrayerSound.NOTIFICATION, voice))
        }
    }

    @Test
    fun everyBundledDurationIsUnderThePlatformThirtySecondCap() {
        AdhanVoice.entries.forEach { voice ->
            PrayerSound.entries.filter { isIosPlatform || it != PrayerSound.ADHAN }.mapNotNull { SoundAssets.duration(it, voice) }.forEach {
                assertTrue(it.inWholeMilliseconds <= 30_000, "$voice: $it exceeds the 30s cap")
            }
        }
    }

    @Test
    fun androidResourceNamesContainNoHyphenBecauseResourceNamesForbidThem() {
        AdhanVoice.entries.forEach { voice ->
            PrayerSound.entries.mapNotNull { SoundAssets.androidRawResourceName(it, voice) }.forEach {
                assertTrue(!it.contains("-"), "'$it' would not compile as an Android resource name")
            }
            PrayerSound.entries.mapNotNull { SoundAssets.androidPreviewRawResourceName(it, voice) }.forEach {
                assertTrue(!it.contains("-"), "'$it' would not compile as an Android resource name")
            }
        }
    }

    // The notification is capped at 30 s, but the sheet says the complete adhan can be heard in
    // the app; the play button is where that is true.
    @Test
    fun theSheetPreviewsTheCompleteAdhanAndTheClipForEverythingElse() {
        assertEquals("adhan_full", SoundAssets.androidPreviewRawResourceName(PrayerSound.ADHAN))
        assertEquals("adhan-full.m4a", SoundAssets.iosPreviewResourceFileName(PrayerSound.ADHAN))
        assertEquals("adhan_azeez_full", SoundAssets.androidPreviewRawResourceName(PrayerSound.ADHAN, AdhanVoice.AZEEZ))
        assertEquals("adhan-azemi-full.m4a", SoundAssets.iosPreviewResourceFileName(PrayerSound.ADHAN, AdhanVoice.AZEMI))
        assertEquals("takbir", SoundAssets.androidPreviewRawResourceName(PrayerSound.TAKBIR))
        assertEquals("chime.caf", SoundAssets.iosPreviewResourceFileName(PrayerSound.NOTIFICATION))
        assertNull(SoundAssets.androidPreviewRawResourceName(PrayerSound.SILENT))
    }

    // The voice sheet's caption quotes it, so every voice needs one and it is always longer than
    // the 30-second clip cut from it.
    @Test
    fun everyVoiceHasAFullLengthLongerThanItsNotificationClip() {
        AdhanVoice.entries.forEach { voice ->
            val full = SoundAssets.fullAdhanDuration(voice)
            val notification = SoundAssets.duration(PrayerSound.ADHAN, voice)!!
            if (isIosPlatform) assertTrue(full > notification, "$voice: $full") else assertEquals(full, notification, "$voice")
        }
    }

    @Test
    fun everyVoiceCarriesItsMeasuredFullLength() {
        // ffprobe on the shipped files, assets/audio/README.md.
        assertEquals(86.15.seconds, SoundAssets.fullAdhanDuration(AdhanVoice.AZEEZ))
        assertEquals(211.00.seconds, SoundAssets.fullAdhanDuration(AdhanVoice.AZEMI))
        assertEquals(154.10.seconds, SoundAssets.fullAdhanDuration(AdhanVoice.ORIGINAL))
    }
}
