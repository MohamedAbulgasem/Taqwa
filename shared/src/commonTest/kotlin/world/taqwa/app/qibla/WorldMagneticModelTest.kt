package world.taqwa.app.qibla

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldMagneticModelTest {

    // Every published point, field and angle alike: a sign or normalisation slip anywhere in the
    // synthesis moves X, Y or Z by far more than NOAA's sixth decimal, and the secular variation is
    // exercised across the model's whole life, 2025.0 to 2029.5.
    @Test
    fun reproducesEveryPointNoaaPublishedForTheModel() {
        val points = Wmm2025TestValues.points
        assertEquals(100, points.size)
        for (p in points) {
            val field = WorldMagneticModel.field(p.latitude, p.longitude, p.altitudeKm, p.year)
            val at = "${p.year} ${p.altitudeKm} km ${p.latitude},${p.longitude}"
            assertTrue(abs(field.northNanoTesla - p.north) < 0.01, "X at $at: ${field.northNanoTesla} vs ${p.north}")
            assertTrue(abs(field.eastNanoTesla - p.east) < 0.01, "Y at $at: ${field.eastNanoTesla} vs ${p.east}")
            assertTrue(abs(field.downNanoTesla - p.down) < 0.01, "Z at $at: ${field.downNanoTesla} vs ${p.down}")
            // NOAA prints the declination to two decimals.
            val declination = WorldMagneticModel.declinationDegrees(p.latitude, p.longitude, p.altitudeKm, p.year)
            assertTrue(abs(declination - p.declination) < 0.006, "D at $at: $declination vs ${p.declination}")
        }
    }

    @Test
    fun aDecimalYearCountsWholeDaysSinceNewYear() {
        assertEquals(2025.0, WorldMagneticModel.decimalYear(LocalDate(2025, 1, 1)))
        // 2024 is a leap year: 2 July is day 184, and 183 of 366 days is exactly half.
        assertEquals(2024.5, WorldMagneticModel.decimalYear(LocalDate(2024, 7, 2)))
    }
}
