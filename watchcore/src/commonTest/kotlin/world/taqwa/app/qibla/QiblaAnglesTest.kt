package world.taqwa.app.qibla

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The phone's angle rules, which both watches now judge their dials by: the signed delta across the
 * 0/360 seam, and the aligned decision with and without B40's memory. The plan's cases come first,
 * on Cape Town's bearing; the B40 cases after them moved here from QiblaMathTest unchanged.
 */
class QiblaAnglesTest {

    @Test
    fun theDeltaIsTheShortestSignedTurnAcrossTheSeam() {
        assertEquals(20.0, QiblaAngles.angleDelta(350.0, 10.0), absoluteTolerance = 1e-9)
        assertEquals(-20.0, QiblaAngles.angleDelta(10.0, 350.0), absoluteTolerance = 1e-9)
        // A half turn is +180 whichever way it is asked, never -180: the range is (-180, 180].
        assertEquals(180.0, QiblaAngles.angleDelta(0.0, 180.0), absoluteTolerance = 1e-9)
        assertEquals(180.0, QiblaAngles.angleDelta(180.0, 0.0), absoluteTolerance = 1e-9)
    }

    @Test
    fun withNoMemoryItIsAlignedWithinFiveDegrees() {
        assertTrue(QiblaAngles.isAligned(18.4, 23.35), "4.95 degrees off")
        assertFalse(QiblaAngles.isAligned(18.3, 23.35), "5.05 degrees off")
    }

    @Test
    fun withMemoryAnAlignedDialHoldsToSevenDegreesAndANewOneNeedsFive() {
        assertTrue(QiblaAngles.isAligned(29.0, 23.35, wasAligned = true), "5.65 degrees off, aligned before")
        assertFalse(QiblaAngles.isAligned(29.0, 23.35, wasAligned = false), "5.65 degrees off, not aligned before")
        assertFalse(QiblaAngles.isAligned(30.5, 23.35, wasAligned = true), "7.15 degrees off, aligned before")
    }

    // --- Hysteresis: in at 5 degrees, out only beyond 7, so a phone resting on the edge neither
    // flickers the rim nor buzzes again and again. The rule both watches' dials use, as the phone does.

    @Test
    fun aDialNotYetAlignedLightsWithinFiveDegreesAndNotJustBeyond() {
        listOf(104.99, 105.0, 95.01, 95.0).forEach {
            assertTrue(QiblaAngles.isAligned(heading = it, bearing = 100.0, wasAligned = false), "heading $it")
        }
        listOf(105.01, 106.99, 107.0, 107.01, 94.99, 93.01, 93.0, 92.99).forEach {
            assertFalse(QiblaAngles.isAligned(heading = it, bearing = 100.0, wasAligned = false), "heading $it")
        }
    }

    @Test
    fun anAlignedDialStaysLitUpToSevenDegreesAndGoesOutJustBeyond() {
        listOf(104.99, 105.0, 105.01, 106.99, 107.0, 93.01, 93.0).forEach {
            assertTrue(QiblaAngles.isAligned(heading = it, bearing = 100.0, wasAligned = true), "heading $it")
        }
        listOf(107.01, 92.99).forEach {
            assertFalse(QiblaAngles.isAligned(heading = it, bearing = 100.0, wasAligned = true), "heading $it")
        }
    }

    @Test
    fun theHysteresisHoldsAcrossTheZeroThreeSixtySeam() {
        // The qibla just east of north, approached from the west of north...
        assertTrue(QiblaAngles.isAligned(heading = 357.01, bearing = 2.0, wasAligned = false))
        assertTrue(QiblaAngles.isAligned(heading = 357.0, bearing = 2.0, wasAligned = false))
        assertFalse(QiblaAngles.isAligned(heading = 356.99, bearing = 2.0, wasAligned = false))
        assertTrue(QiblaAngles.isAligned(heading = 355.01, bearing = 2.0, wasAligned = true))
        assertTrue(QiblaAngles.isAligned(heading = 355.0, bearing = 2.0, wasAligned = true))
        assertFalse(QiblaAngles.isAligned(heading = 354.99, bearing = 2.0, wasAligned = true))
        // ...and just west of north, approached from the east.
        assertTrue(QiblaAngles.isAligned(heading = 2.99, bearing = 358.0, wasAligned = false))
        assertTrue(QiblaAngles.isAligned(heading = 3.0, bearing = 358.0, wasAligned = false))
        assertFalse(QiblaAngles.isAligned(heading = 3.01, bearing = 358.0, wasAligned = false))
        assertTrue(QiblaAngles.isAligned(heading = 4.99, bearing = 358.0, wasAligned = true))
        assertTrue(QiblaAngles.isAligned(heading = 5.0, bearing = 358.0, wasAligned = true))
        assertFalse(QiblaAngles.isAligned(heading = 5.01, bearing = 358.0, wasAligned = true))
    }
}
