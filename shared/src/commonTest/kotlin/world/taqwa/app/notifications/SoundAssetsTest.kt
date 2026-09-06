package world.taqwa.app.notifications

import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SoundAssetsTest {

    @Test
    fun silentAndNotificationHaveNoBundledFileOrDuration() {
        listOf(PrayerSound.SILENT, PrayerSound.NOTIFICATION).forEach {
            assertNull(SoundAssets.androidRawResourceName(it), "$it")
            assertNull(SoundAssets.iosResourceFileName(it), "$it")
            assertNull(SoundAssets.duration(it), "$it")
        }
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
}
