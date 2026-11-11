package world.taqwa.app.watchfeed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An ask's step times are logged in a debug build (Ruling 70 item 1): on the iPhone, a debug
 * Kotlin binary, which Xcode's Debug configuration builds and its Release (an archive) never does.
 * A test binary is a debug binary.
 */
class DebugBuildTest {

    @Test
    fun aTestBinaryIsADebugBuild() {
        assertTrue(isDebugBuild())
    }

    /** Android's `setprop log.tag.TaqwaWatch DEBUG` has no iPhone counterpart: the line stays a debug binary's. */
    @Test
    fun theIPhoneHasNoTagToTurnTheStepsLineOn() {
        assertFalse(stepsTagEnabled())
    }
}
