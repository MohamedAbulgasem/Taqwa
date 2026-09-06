package world.taqwa.app.widget

import world.taqwa.app.resources.Res
import world.taqwa.app.resources.widget_background_frosted
import world.taqwa.app.resources.widget_background_translucent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The spec's fourth widget background option is worded per platform — Android describes real
 * translucency, iOS describes its own system material — and neither user should ever see the
 * other's wording. [translucentOrFrostedLabelKey] is a pure function of a boolean precisely so
 * both branches can be checked here without a platform-specific test source set; [isIosPlatform]
 * itself is a one-line `expect`/`actual` fact that a build for each platform already proves.
 */
class WidgetBackgroundLabelsTest {

    @Test
    fun androidWordingIsTranslucent() {
        assertEquals(Res.string.widget_background_translucent, translucentOrFrostedLabelKey(isIos = false))
    }

    @Test
    fun iosWordingIsFrosted() {
        assertEquals(Res.string.widget_background_frosted, translucentOrFrostedLabelKey(isIos = true))
    }

    @Test
    fun theTwoWordingsAreNeverTheSame() {
        assertEquals(false, translucentOrFrostedLabelKey(true) == translucentOrFrostedLabelKey(false))
    }
}
