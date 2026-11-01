package world.taqwa.app.widget

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The Android source's fallbacks. The hook is installed by `TaqwaApplication`, which never runs
 * here — and that is exactly the state being tested: anything but a hook that answers must come
 * back null, the source's "cannot tell", which shows no status and no offer rather than guessing.
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
    fun `no hook installed cannot tell`() = runTest {
        androidWidgetPlacementHook = null
        assertNull(createWidgetPlacementSource().current())
    }

    @Test
    fun `a hook that throws cannot tell rather than propagating`() = runTest {
        androidWidgetPlacementHook = { error("AppWidgetManager unavailable") }
        assertNull(createWidgetPlacementSource().current())
    }

    @Test
    fun `a hook that finds no AppWidgetManager cannot tell`() = runTest {
        androidWidgetPlacementHook = { null }
        assertNull(createWidgetPlacementSource().current())
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
