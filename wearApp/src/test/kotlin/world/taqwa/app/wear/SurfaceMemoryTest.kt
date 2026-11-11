package world.taqwa.app.wear

import android.app.Application
import android.content.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [SurfaceMemory] over the app's own `SharedPreferences`, on Robolectric's Android at the watch's
 * least API level (its default, Android 16, fails here before the test, in Robolectric's
 * FileDescriptor interceptor): an opening asks only when [SurfaceRefresh.due] says so, a feed's
 * request always asks, and only an ask that went out is remembered, across the memory's own life
 * too, as across a process's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30])
class SurfaceMemoryTest {

    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("surfaces-test", Context.MODE_PRIVATE)

    private val now = TestFeeds.at(0, 14, 58)

    private val inputs = SurfaceInputs(
        feed = TestFeeds.sample.writtenAt,
        newerVersion = false,
        day = 20_744L,
        zone = "Africa/Johannesburg",
        locales = "en-ZA,ar-EG",
        twentyFourHour = true,
        versionCode = 100_043L,
        installedAt = 1_794_200_000_000L,
    )

    @Test
    fun anOpeningAsksOnceForTheSameInputs() {
        val memory = SurfaceMemory(prefs)
        var asks = 0
        assertTrue(memory.askIfDue(inputs, now) { asks++; true })
        assertFalse(memory.askIfDue(inputs, now + 60L) { asks++; true })
        assertEquals(1, asks)
        assertTrue(memory.askIfDue(inputs.copy(day = inputs.day + 1), now + 120L) { asks++; true })
        assertEquals(2, asks)
    }

    @Test
    fun anAskThatDidNotGoOutIsNotRemembered() {
        val memory = SurfaceMemory(prefs)
        var asks = 0
        assertTrue(memory.askIfDue(inputs, now) { asks++; false })
        assertTrue(memory.askIfDue(inputs, now + 60L) { asks++; true }, "the next opening asks again")
        assertEquals(2, asks)
        assertEquals(SurfaceStamp(inputs, now + 60L), memory.last())
    }

    @Test
    fun aFeedsAskIsAlwaysMadeAndSpareTheNextOpening() {
        val memory = SurfaceMemory(prefs)
        var asks = 0
        memory.askIfDue(inputs, now) { asks++; true }
        memory.askAnyway(inputs, now + 60L) { asks++; true }
        assertEquals(2, asks, "the same feed again is asked for all the same")
        val next = inputs.copy(feed = inputs.feed!! + 3_600L)
        memory.askAnyway(next, now + 120L) { asks++; true }
        assertFalse(memory.askIfDue(next, now + 180L) { asks++; true }, "the opening after it does not ask")
        assertEquals(3, asks)
    }

    @Test
    fun theStampOutlivesTheMemory() {
        SurfaceMemory(prefs).askIfDue(inputs.copy(feed = null, newerVersion = true), now) { true }
        assertEquals(SurfaceStamp(inputs.copy(feed = null, newerVersion = true), now), SurfaceMemory(prefs).last())
        SurfaceMemory(prefs).askIfDue(inputs, now + 60L) { true }
        assertEquals(SurfaceStamp(inputs, now + 60L), SurfaceMemory(prefs).last())
    }

    @Test
    fun nothingKeptIsNoStamp() {
        assertNull(SurfaceMemory(prefs).last())
        prefs.edit().putLong("at", now).commit()
        assertNull(SurfaceMemory(prefs).last(), "a stamp missing its inputs is none")
    }
}
