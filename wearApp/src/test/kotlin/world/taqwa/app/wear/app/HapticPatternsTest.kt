package world.taqwa.app.wear.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The watch's buzzes are the phone's (`Haptics.android.kt` in `:shared`, which `:wearApp` does not
 * link, so the file is read), and the windows in which Task 6 sends no tick are the waveforms' own
 * lengths (call 14).
 */
class HapticPatternsTest {

    private val phone: String by lazy {
        val path = System.getProperty("taqwa.phoneHaptics") ?: "../shared/src/androidMain/kotlin/world/taqwa/app/qibla/Haptics.android.kt"
        File(path).readText()
    }

    @Test
    fun theQuietWindowsAreTheWaveformsLengths() {
        assertEquals(140L, HapticPatterns.PART_MS)
        assertEquals(400L, HapticPatterns.SET_MS)
        assertEquals(HapticPatterns.PART_MS, HapticPatterns.PART.sum())
        assertEquals(HapticPatterns.SET_MS, HapticPatterns.SET.sum())
    }

    @Test
    fun theWaveformsAreThePhones() {
        // The phone's two waveforms, in its order: the part's end, then the set's.
        val waveforms = Regex("""longArrayOf\(([^)]*)\)""").findAll(phone)
            .map { match -> match.groupValues[1].split(',').map { it.trim().toLong() } }
            .toList()

        assertEquals(listOf(HapticPatterns.PART.toList(), HapticPatterns.SET.toList()), waveforms)
    }

    @Test
    fun theAlignmentIsThePhonesTick() {
        // The phone's tick, its Qibla's buzz on entering alignment, is one shot.
        val tick = Regex("""fun tick\(\)\s*\{\s*buzz\(VibrationEffect\.createOneShot\((\d+),""").find(phone)

        assertEquals(HapticPatterns.ALIGNED_MS, tick?.groupValues?.get(1)?.toLong())
        assertEquals(30L, HapticPatterns.ALIGNED_MS)
    }

    @Test
    fun theCountIsThePhonesPredefinedTick() {
        // From API 29 the phone counts with the OEM's own lightest primitive; the watch's minSdk is
        // 30, so the phone's fallback below 29 has no place on it.
        assertTrue("VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)" in phone)
    }
}
