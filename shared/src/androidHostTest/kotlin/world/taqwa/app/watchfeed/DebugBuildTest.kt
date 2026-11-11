package world.taqwa.app.watchfeed

import android.content.pm.ApplicationInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An ask's step times are always logged in a debug build (Ruling 70 item 1), and in a signed one
 * only once its tag is set to DEBUG (`stepsLogOn`): on Android, a debug build is the application's
 * own `FLAG_DEBUGGABLE`, which the debug build sets and a release build never does.
 */
class DebugBuildTest {

    @Test
    fun aDebuggableApplicationIsADebugBuild() {
        assertTrue(debuggable(ApplicationInfo.FLAG_DEBUGGABLE))
        assertTrue(debuggable(ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_HAS_CODE or ApplicationInfo.FLAG_ALLOW_BACKUP))
    }

    @Test
    fun anyOtherApplicationIsNot() {
        assertFalse(debuggable(0))
        assertFalse(debuggable(ApplicationInfo.FLAG_HAS_CODE or ApplicationInfo.FLAG_ALLOW_BACKUP or ApplicationInfo.FLAG_SYSTEM))
        assertFalse(debuggable(ApplicationInfo.FLAG_TEST_ONLY))
    }
}
