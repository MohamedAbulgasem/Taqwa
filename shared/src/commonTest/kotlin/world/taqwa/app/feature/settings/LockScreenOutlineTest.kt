package world.taqwa.app.feature.settings

import androidx.compose.ui.graphics.compositeOver
import world.taqwa.app.design.DarkColors
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.PreviewLockScreen
import world.taqwa.app.design.TaqwaColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Lock Screen phones in the iPhone's step drawings are slabs of [PreviewLockScreen], the slate
 * of the picture above them. On the light theme's pale tiles they stand out at 13.6:1; on the dark
 * theme's tiles they sat at 1.1:1, and their edge was lost (the Lead's follow-up 3, 31 Oct).
 */
class LockScreenOutlineTest {

    /** What the eye meets at the phone's edge: its outline where it has one, otherwise the slate,
     * against its tile over the card. */
    private fun edge(colors: TaqwaColors): Float {
        val tile = howToTile(colors).compositeOver(colors.surface)
        return contrastRatio(lockScreenOutline(colors) ?: PreviewLockScreen, tile)
    }

    @Test
    fun `on the dark theme the phone's edge stands out from its tile as much as on the light`() {
        assertTrue(edge(DarkColors) >= edge(LightColors), "dark ${edge(DarkColors)}:1 against light ${edge(LightColors)}:1")
    }

    @Test
    fun `on the light theme the phone is drawn as it was with no outline`() {
        assertNull(lockScreenOutline(LightColors))
        assertEquals(13.6f, edge(LightColors), 0.05f)
    }
}
