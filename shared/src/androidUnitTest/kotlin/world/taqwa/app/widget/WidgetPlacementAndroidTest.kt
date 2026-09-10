package world.taqwa.app.widget

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The Android source's fallbacks. The hook is installed by `TaqwaApplication`, which never runs
 * here — and that is exactly the state being tested: anything but a hook that answers must come
 * back [WidgetPlacement.Unknown], which hides the offer rather than guessing at it.
 *
 * No Android class is loaded: the source reads a Kotlin function reference and nothing else, which
 * is why this needs no Robolectric.
 */
class WidgetPlacementAndroidTest {

    @AfterTest
    fun clearHook() {
        androidWidgetPlacementHook = null
    }

    @Test
    fun `no hook installed reports Unknown`() = runTest {
        androidWidgetPlacementHook = null
        assertEquals(WidgetPlacement.Unknown, createWidgetPlacementSource().current())
    }

    @Test
    fun `a hook that throws reports Unknown rather than propagating`() = runTest {
        androidWidgetPlacementHook = { error("AppWidgetManager unavailable") }
        assertEquals(WidgetPlacement.Unknown, createWidgetPlacementSource().current())
    }

    @Test
    fun `an installed hook is reported as it answers`() = runTest {
        androidWidgetPlacementHook = { WidgetPlacement(prayer = true, ayah = false) }
        assertEquals(
            WidgetPlacement(prayer = true, ayah = false),
            createWidgetPlacementSource().current(),
        )
    }
}
