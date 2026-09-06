package world.taqwa.app.qibla

import world.taqwa.app.domain.GeoLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiblaMathTest {

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val makkah = GeoLocation(21.4225, 39.8262, "Asia/Riyadh", "Makkah", "SA")
    private val auckland = GeoLocation(-36.8485, 174.7633, "Pacific/Auckland", "Auckland", "NZ")
    private val quito = GeoLocation(-0.1807, -78.4678, "America/Guayaquil", "Quito", "EC")

    @Test
    fun londonBearingMatchesTheKnownValueOfAboutOneHundredAndNineteenDegrees() {
        val bearing = QiblaMath.bearing(london)
        assertTrue(bearing in 118.0..120.0, "was $bearing")
    }

    @Test
    fun bearingIsAlwaysWithinACompassRangeIncludingNearEquatorial() {
        listOf(london, auckland, quito).forEach {
            val b = QiblaMath.bearing(it)
            assertTrue(b in 0.0..360.0, "was $b for $it")
        }
    }

    @Test
    fun distanceFromMakkahToItselfIsEffectivelyZero() {
        assertTrue(QiblaMath.distanceKm(makkah) < 1.0)
    }

    @Test
    fun londonIsRoughlyFortyEightHundredToFiveThousandKilometresFromMakkah() {
        val d = QiblaMath.distanceKm(london)
        assertTrue(d in 4500.0..5100.0, "was $d")
    }

    @Test
    fun theKaabasAntipodeIsRoughlyHalfTheEarthsCircumferenceAway() {
        // The antipode of 21.4225N, 39.8262E sits in the South Pacific.
        val antipode = GeoLocation(-21.4225, -140.1738, "Pacific/Tahiti", "Antipode", "PF")
        val d = QiblaMath.distanceKm(antipode)
        assertTrue(d in 19800.0..20100.0, "was $d")
    }

    @Test
    fun angleDeltaIsTheShortestSignedDifference() {
        assertEquals(10.0, QiblaMath.angleDelta(350.0, 0.0), absoluteTolerance = 0.001)
        assertEquals(-10.0, QiblaMath.angleDelta(0.0, 350.0), absoluteTolerance = 0.001)
        assertEquals(0.0, QiblaMath.angleDelta(45.0, 45.0), absoluteTolerance = 0.001)
    }

    @Test
    fun angleDeltaNeverExceedsAHalfTurnAcrossTheWholeCircle() {
        (0..350 step 10).forEach { a ->
            (0..350 step 10).forEach { b ->
                val delta = QiblaMath.angleDelta(a.toDouble(), b.toDouble())
                assertTrue(delta in -180.0..180.0, "delta $delta for $a -> $b")
            }
        }
    }

    @Test
    fun isAlignedIsTrueWithinFiveDegreesEitherSideAndFalseJustBeyond() {
        assertTrue(QiblaMath.isAligned(heading = 115.0, bearing = 119.0))
        assertTrue(QiblaMath.isAligned(heading = 124.0, bearing = 119.0))
        assertTrue(!QiblaMath.isAligned(heading = 113.0, bearing = 119.0))
        assertTrue(!QiblaMath.isAligned(heading = 125.0, bearing = 119.0))
    }

    @Test
    fun isAlignedWrapsCorrectlyAcrossTheZeroThreeSixtyDegreeSeam() {
        assertTrue(QiblaMath.isAligned(heading = 358.0, bearing = 2.0))
        assertTrue(!QiblaMath.isAligned(heading = 350.0, bearing = 2.0))
    }
}
