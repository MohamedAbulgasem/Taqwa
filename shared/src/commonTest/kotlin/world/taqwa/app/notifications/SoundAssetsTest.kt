package world.taqwa.app.notifications

import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
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

    // Not the phone's default tone: a prayer must never sound like a message arriving.
    @Test
    fun notificationIsTaqwasOwnChime() {
        assertEquals(2.40.seconds, SoundAssets.duration(PrayerSound.NOTIFICATION))
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
        assertEquals(29.95.seconds, SoundAssets.duration(PrayerSound.ADHAN))
        assertEquals("adhan_30s", SoundAssets.androidRawResourceName(PrayerSound.ADHAN))
        assertEquals("adhan-30s.caf", SoundAssets.iosResourceFileName(PrayerSound.ADHAN))
    }

    @Test
    fun everyBundledDurationIsUnderThePlatformThirtySecondCap() {
        PrayerSound.entries.mapNotNull { SoundAssets.duration(it) }.forEach {
            assertTrue(it.inWholeMilliseconds <= 30_000, "$it exceeds the 30s cap")
        }
    }

    @Test
    fun androidResourceNamesContainNoHyphenBecauseResourceNamesForbidThem() {
        PrayerSound.entries.mapNotNull { SoundAssets.androidRawResourceName(it) }.forEach {
            assertTrue(!it.contains("-"), "'$it' would not compile as an Android resource name")
        }
    }

    // The notification is capped at 30 s, but the sheet says the complete adhan can be heard in
    // the app; the play button is where that is true.
    @Test
    fun theSheetPreviewsTheCompleteAdhanAndTheClipForEverythingElse() {
        assertEquals("adhan_full", SoundAssets.androidPreviewRawResourceName(PrayerSound.ADHAN))
        assertEquals("adhan-full.m4a", SoundAssets.iosPreviewResourceFileName(PrayerSound.ADHAN))
        assertEquals("takbir", SoundAssets.androidPreviewRawResourceName(PrayerSound.TAKBIR))
        assertEquals("chime.caf", SoundAssets.iosPreviewResourceFileName(PrayerSound.NOTIFICATION))
        assertNull(SoundAssets.androidPreviewRawResourceName(PrayerSound.SILENT))
    }
}
