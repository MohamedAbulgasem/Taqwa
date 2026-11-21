package world.taqwa.app.feature.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.DarkColors
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.LocalTaqwaDark
import world.taqwa.app.design.TaqwaColors
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 52 dp watch on Home's watch card (Option B): it tells the step with no words and no colour of
 * its own. Before Taqwa is installed it is a dashed ghost with no amber at all; before the face is
 * added it is the real dark watch with the Taqwa face faint on its black screen; and while the watch
 * answers a tap a thin amber ring goes round it, and only then. It is a picture of a real object,
 * so Arabic does not mirror it.
 *
 * Read by pixels, as QiblaScreenTest reads its dial: "amber" is a pixel whose red is more than 40
 * above its blue, which no grey, white or black of the theme is.
 */
@OptIn(ExperimentalTestApi::class)
class WatchPreviewTest {

    /** Air round the drawing, so the ring, which reaches about 1 dp past the box, is captured whole. */
    private val air = 4.dp

    /** The drawing on its card's surface. Not TaqwaTheme: its system-bar effects crash the simulator test process. */
    @Composable
    private fun Drawing(look: WatchLook, ring: Boolean, colors: TaqwaColors = LightColors, direction: LayoutDirection = LayoutDirection.Ltr) {
        CompositionLocalProvider(
            LocalTaqwaColors provides colors,
            LocalTaqwaDark provides (colors == DarkColors),
            LocalLayoutDirection provides direction,
        ) {
            Box(Modifier.background(colors.surface).padding(air)) { WatchPreview(look, ring) }
        }
    }

    private fun ComposeUiTest.capture(): PixelMap = onRoot().captureToImage().toPixelMap()

    private fun Color.isAmber(): Boolean = (red - blue) * 255f > 40f

    /** How far pixel ([x], [y])'s centre is from the watch's centre, (25, 26) dp inside the drawing, in dp. */
    private fun ComposeUiTest.dpFromCentre(x: Int, y: Int): Float {
        val d = density.density
        return hypot(x + 0.5f - (air.value + 25f) * d, y + 0.5f - (air.value + 26f) * d) / d
    }

    /** The distance from the centre of each pixel for which [where] holds. */
    private fun ComposeUiTest.distances(image: PixelMap, where: (x: Int, y: Int) -> Boolean): List<Float> {
        val out = mutableListOf<Float>()
        for (y in 0 until image.height) for (x in 0 until image.width) if (where(x, y)) out += dpFromCentre(x, y)
        return out
    }

    @Test
    fun theGhostWatchHasNoAmber() = runComposeUiTest {
        var colors by mutableStateOf(LightColors)
        setContent { Drawing(WatchLook.GHOST, ring = false, colors = colors) }
        for (c in listOf(LightColors, DarkColors)) {
            colors = c
            waitForIdle()
            val image = capture()
            assertEquals(0, distances(image) { x, y -> image[x, y].isAmber() }.size, "amber pixels in the ghost watch, dark=${c == DarkColors}")
        }
    }

    @Test
    fun theRingIsDrawnOnlyWhileTheWatchAnswers() = runComposeUiTest {
        var look by mutableStateOf(WatchLook.GHOST)
        var ring by mutableStateOf(false)
        setContent { Drawing(look, ring) }
        for (each in WatchLook.entries) {
            look = each
            ring = false
            waitForIdle()
            val plain = capture()
            ring = true
            waitForIdle()
            val ringed = capture()
            assertEquals(0, distances(plain) { x, y -> plain[x, y].isAmber() && dpFromCentre(x, y) > 24.5f }.size, "$each: amber outside the watch with no ring")
            assertTrue(distances(ringed) { x, y -> ringed[x, y].isAmber() && dpFromCentre(x, y) > 24.5f }.isNotEmpty(), "$each: no ring")
            val changed = distances(ringed) { x, y -> ringed[x, y] != plain[x, y] }
            assertTrue(changed.isNotEmpty(), "$each: the ring changed nothing")
            assertTrue(changed.all { it in 24f..27f }, "$each: the ring changed pixels from ${changed.min()} to ${changed.max()} dp out")
        }
    }

    @Test
    fun theWatchIsTheSameDrawingUnderArabic() = runComposeUiTest {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        setContent { Drawing(WatchLook.FAINT, ring = true, direction = direction) }
        val ltr = capture()
        direction = LayoutDirection.Rtl
        waitForIdle()
        val rtl = capture()
        assertEquals(0, distances(ltr) { x, y -> ltr[x, y] != rtl[x, y] }.size, "pixels mirrored under Arabic")
    }

    /** The face sits on the watch's own black screen: f (100, 182), under every bar, is black. */
    @Test
    fun theFaintFaceIsDrawnOnTheWatchsBlackScreen() = runComposeUiTest {
        setContent { Drawing(WatchLook.FAINT, ring = false) }
        val image = capture()
        val d = density.density
        val pixel = image[((air.value + 25f) * d).toInt(), ((air.value + 43f) * d).toInt()]
        assertTrue(pixel.red < 0.02f && pixel.green < 0.02f && pixel.blue < 0.02f, "the screen at (25, 43) dp is $pixel")
    }
}
