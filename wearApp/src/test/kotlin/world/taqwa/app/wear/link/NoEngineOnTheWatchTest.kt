package world.taqwa.app.wear.link

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * No time is computed on the watch (plan 2, Global Constraints): `:wearApp` never links `:shared`
 * or the engine, so every instant it shows is the phone's own and app = site = watch.
 */
class NoEngineOnTheWatchTest {

    @Test
    fun theWatchHasNoPrayerEngine() {
        for (name in listOf("world.taqwa.app.prayer.PrayerTimesEngine", "com.batoulapps.adhan2.PrayerTimes")) {
            assertFailsWith<ClassNotFoundException>(name) { Class.forName(name) }
        }
        val classPath = System.getProperty("java.class.path").orEmpty().split(File.pathSeparator)
        assertTrue(classPath.isNotEmpty())
        assertTrue(classPath.none { "adhan2" in it }, "no adhan2 on the watch's class path")
    }
}
