package world.taqwa.app.wear.face

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The face button's touch target (plan 5 Task 5, review round 1 of the re-issue): Today's chip and
 * the face screen's button are an amber pill about 36 dp tall at the default font size, and Google's
 * Wear app quality item WO-V2 asks for "a minimum of 48x48dp touch targets". So the button lays out
 * the pill as before and lays a touch target over it, the pill grown evenly to at least 48 dp each
 * way, which is what a tap lands on and what a screen reader finds. The button keeps the pill's own
 * size, so nothing around it moves and nothing drawn changes (the house rule: accessibility changes
 * nothing drawn at default settings).
 *
 * These run [FaceButtonMeasure], the button's own measure policy, over stand-ins for its two
 * children: the pill, and the touch target.
 */
class FaceButtonTest {

    @Test
    fun theButtonIsTouchedOverAtLeast48DpEachWay() {
        // The pill as emulator-5558 draws it (320 dpi, 2 px a dp): about 150 by 36 dp.
        for (density in DENSITIES) {
            val pill = IntSize(dp(150, density), dp(36, density))
            val button = measure(pill, density)
            val target = button.touch.size
            assertTrue(target.height >= dp(48, density), "at $density the target is ${target.height} px tall, under 48 dp")
            assertTrue(target.width >= dp(48, density), "at $density the target is ${target.width} px wide, under 48 dp")
            assertEquals(Constraints.fixed(target.width, target.height), button.touch.given, "the target is measured at its own size")
        }
    }

    @Test
    fun theTargetLiesEvenlyOverThePill() {
        for (density in DENSITIES) {
            val pill = IntSize(dp(150, density), dp(36, density))
            val button = measure(pill, density)
            val at = button.touch.at
            val size = button.touch.size
            // Over the whole pill: from above its top to below its bottom, across its width.
            assertTrue(at.y <= 0 && at.y + size.height >= pill.height, "at $density the target $at $size misses part of the pill $pill")
            assertTrue(at.x <= 0 && at.x + size.width >= pill.width, "at $density the target $at $size misses part of the pill $pill")
            // Evenly: as much above the pill as below it, to the pixel.
            val above = -at.y
            val below = at.y + size.height - pill.height
            assertTrue(below - above in 0..1, "at $density the target reaches $above px above the pill and $below px below it")
            assertEquals(0, at.x, "a pill wider than 48 dp is its own width")
        }
    }

    @Test
    fun theButtonKeepsThePillsSizeSoNothingAroundItMoves() {
        for (density in DENSITIES) {
            val pill = IntSize(dp(150, density), dp(36, density))
            val button = measure(pill, density)
            assertEquals(pill, IntSize(button.result.width, button.result.height), "at $density the button is the pill's size")
            assertEquals(IntOffset.Zero, button.pill.at, "at $density the pill is drawn where it always was")
            assertEquals(PARENT, button.pill.given, "the pill is given the caller's constraints, as before")
        }
    }

    @Test
    fun aPillOver48DpIsItsOwnTouchTarget() {
        // A large font: the pill outgrows 48 dp, and the target is the pill itself.
        val density = 2f
        val pill = IntSize(dp(190, density), dp(52, density))
        val button = measure(pill, density)
        assertEquals(pill, button.touch.size)
        assertEquals(IntOffset.Zero, button.touch.at)
    }

    @Test
    fun aSmallPillGrowsEveryWay() {
        val density = 1f
        val button = measure(IntSize(30, 20), density)
        assertEquals(IntSize(48, 48), button.touch.size)
        assertEquals(IntOffset(-9, -14), button.touch.at)
        assertEquals(IntSize(30, 20), IntSize(button.result.width, button.result.height))
    }

    // ── Stand-ins ────────────────────────────────────────────────────────────────────

    private class Measured(val result: MeasureResult, val pill: Piece, val touch: Piece)

    /** The policy run once over a pill of [pill] px and a touch target, in a parent of [PARENT]. */
    private fun measure(pill: IntSize, density: Float): Measured {
        val drawn = Piece(pill)
        val touch = Piece(IntSize.Zero)
        val scope = object : MeasureScope {
            override val density = density
            override val fontScale = 1f
            override val layoutDirection = LayoutDirection.Ltr
        }
        val result = with(FaceButtonMeasure) { scope.measure(listOf(drawn, touch), PARENT) }
        result.placeChildren()
        return Measured(result, drawn, touch)
    }

    /** A child that wants [wants] px, within the constraints it is given, and records what it is given and where it is placed. */
    private class Piece(private val wants: IntSize) : Measurable {
        var given: Constraints? = null
        var size: IntSize = IntSize.Zero
        var at: IntOffset = IntOffset(Int.MIN_VALUE, Int.MIN_VALUE)

        override val parentData: Any? = null

        override fun measure(constraints: Constraints): Placeable {
            given = constraints
            val piece = this
            return object : Placeable() {
                init {
                    measurementConstraints = constraints
                    measuredSize = constraints.constrain(wants)
                    piece.size = measuredSize
                }

                override fun get(alignmentLine: AlignmentLine): Int = AlignmentLine.Unspecified

                override fun placeAt(position: IntOffset, zIndex: Float, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
                    piece.at = position
                }
            }
        }

        override fun minIntrinsicWidth(height: Int) = wants.width
        override fun maxIntrinsicWidth(height: Int) = wants.width
        override fun minIntrinsicHeight(width: Int) = wants.height
        override fun maxIntrinsicHeight(width: Int) = wants.height
    }

    private fun dp(value: Int, density: Float) = (value * density).roundToInt()

    private companion object {
        /** Watches from 1.5 to 3 px a dp, emulator-5558's 2 among them. */
        val DENSITIES = listOf(1f, 1.5f, 2f, 2.25f, 2.625f, 3f)

        /** Today's list and the face screen give the button a width and leave its height open. */
        val PARENT = Constraints(maxWidth = 900, maxHeight = Constraints.Infinity)
    }
}
