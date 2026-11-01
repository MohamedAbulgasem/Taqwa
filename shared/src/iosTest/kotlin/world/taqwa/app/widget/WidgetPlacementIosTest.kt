package world.taqwa.app.widget

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The iPhone source, with a stand-in for the hook `iOSApp.swift` installs. Swift hands over the
 * kinds WidgetKit reports as placed, or nothing when WidgetKit failed; which widget each kind is,
 * is decided here, where it can be tested.
 *
 * The case that matters most is the first. The Lock Screen widget is a kind of its own, and it
 * used to be folded into the prayer widget, so a Lock Screen widget alone put the check on the
 * prayer card, "On your Home Screen", and counted it on the Settings root.
 */
class WidgetPlacementIosTest {

    @AfterTest
    fun clearHook() {
        iosWidgetPlacementHook = null
    }

    /** A hook that answers at once with [kinds], or with WidgetKit's failure when null. */
    private fun answering(kinds: List<String>?) {
        iosWidgetPlacementHook = { completion -> completion(kinds) }
    }

    @Test
    fun `a Lock Screen widget alone is not the prayer widget on the Home Screen`() = runTest {
        answering(listOf("TaqwaLockScreenWidget"))
        assertEquals(
            WidgetPlacement(prayer = false, ayah = false, lockScreen = true),
            createWidgetPlacementSource().current(),
        )
    }

    @Test
    fun `each WidgetKit kind reports its own widget`() = runTest {
        answering(listOf("TaqwaHomeWidget"))
        assertEquals(WidgetPlacement(prayer = true, ayah = false, lockScreen = false), createWidgetPlacementSource().current())
        answering(listOf("TaqwaAyahWidget"))
        assertEquals(WidgetPlacement(prayer = false, ayah = true, lockScreen = false), createWidgetPlacementSource().current())
        answering(listOf("TaqwaAyahWidget", "TaqwaLockScreenWidget", "TaqwaHomeWidget", "TaqwaHomeWidget"))
        assertEquals(WidgetPlacement(prayer = true, ayah = true, lockScreen = true), createWidgetPlacementSource().current())
    }

    @Test
    fun `no Taqwa widget placed is an answer and not a failure`() = runTest {
        answering(emptyList())
        assertEquals(WidgetPlacement(prayer = false, ayah = false, lockScreen = false), createWidgetPlacementSource().current())
    }

    @Test
    fun `a WidgetKit failure cannot tell`() = runTest {
        answering(null)
        assertNull(createWidgetPlacementSource().current())
    }

    @Test
    fun `no hook installed cannot tell`() = runTest {
        iosWidgetPlacementHook = null
        assertNull(createWidgetPlacementSource().current())
    }

    @Test
    fun `a hook that throws cannot tell`() = runTest {
        iosWidgetPlacementHook = { error("WidgetCenter unavailable") }
        assertNull(createWidgetPlacementSource().current())
    }

    /** Runs on the test's virtual clock, so the two-second wait costs nothing. */
    @Test
    fun `a hook that never answers cannot tell once the wait is over`() = runTest {
        iosWidgetPlacementHook = { }
        assertNull(createWidgetPlacementSource().current())
    }

    @Test
    fun `a second answer from WidgetKit is ignored`() = runTest {
        iosWidgetPlacementHook = { completion ->
            completion(listOf("TaqwaHomeWidget"))
            completion(null)
        }
        assertEquals(WidgetPlacement(prayer = true, ayah = false, lockScreen = false), createWidgetPlacementSource().current())
    }
}
