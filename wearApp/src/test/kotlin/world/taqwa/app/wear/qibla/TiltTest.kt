package world.taqwa.app.wear.qibla

import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How far the screen tilts from level (plan 3 Task 7, open question 4): the angle between the
 * screen's normal and up, from a rotation matrix's last element, and the hold-level line that comes
 * in above 40° and goes again only under 30°.
 */
class TiltTest {

    private fun matrixWithZz(zz: Float) = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, zz)

    @Test
    fun aLevelScreenTiltsNotAtAll() {
        assertEquals(0.0, Tilt.degrees(matrixWithZz(1f)), 1e-9)
    }

    @Test
    fun aScreenAtFortyFiveDegreesTiltsFortyFive() {
        assertEquals(45.0, Tilt.degrees(matrixWithZz(cos(PI / 4).toFloat())), 1e-4)
        assertEquals(90.0, Tilt.degrees(matrixWithZz(0f)), 1e-9)
        assertEquals(180.0, Tilt.degrees(matrixWithZz(-1f)), 1e-9)
    }

    @Test
    fun aMatrixJustPastOneIsLevelAndNotANumber() {
        // Float rounding can put the element a hair past 1; acos would give NaN there.
        assertEquals(0.0, Tilt.degrees(matrixWithZz(1.0000001f)), 1e-9)
        assertEquals(180.0, Tilt.degrees(matrixWithZz(-1.0000001f)), 1e-9)
    }

    @Test
    fun theLevelTipsAboveFortyAndComesBackOnlyUnderThirty() {
        val level = Tilt.Level()

        assertFalse(level.tilted(35.0))
        assertTrue(level.tilted(41.0))
        assertTrue(level.tilted(35.0), "back under 40, but not yet under 30")
        assertFalse(level.tilted(29.0))
    }

    @Test
    fun theLevelsEdgesAreStrict() {
        val level = Tilt.Level()

        assertFalse(level.tilted(40.0), "more than 40° tips it, 40° itself does not")
        assertTrue(level.tilted(40.1))
        assertTrue(level.tilted(30.0), "under 30° levels it, 30° itself does not")
        assertFalse(level.tilted(29.9))
    }

    @Test
    fun aTiltThatIsNotKnownKeepsTheLevelAsItWas() {
        val level = Tilt.Level()
        assertFalse(level.tilted(Double.NaN))

        assertTrue(level.tilted(50.0))
        assertTrue(level.tilted(Double.NaN))
    }
}
