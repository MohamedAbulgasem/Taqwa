package world.taqwa.app.qibla

import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The World Magnetic Model 2025, for the one number the iOS compass needs from it: the declination
 * at the city on the Qibla screen, which turns `CLHeading.magneticHeading` into true north without
 * asking iOS for a location — and so works for someone who picked their city by hand and keeps
 * location off. Android's `GeomagneticField` is the same model family and stays in use there.
 *
 * A port of NOAA's reference implementation (`GeomagnetismLibrary.c`): WGS-84 geodetic coordinates
 * to geocentric spherical ones, Schmidt semi-normalised associated Legendre functions, the
 * spherical-harmonic sums of the time-adjusted coefficients, and the rotation back to the geodetic
 * frame. It reproduces all 100 of NOAA's published test points (`WorldMagneticModelTest`).
 *
 * The model was produced by NOAA's National Centers for Environmental Information and the British
 * Geological Survey for the US National Geospatial-Intelligence Agency and the UK Defence
 * Geographic Centre, and is in the public domain. It is valid from 2025.0 to 2030.0; after that it
 * extrapolates, drifting by a fraction of a degree a year — replace [COEFFICIENTS] with WMM2030's
 * `WMM.COF` when it is published (ncei.noaa.gov/products/world-magnetic-model).
 */
object WorldMagneticModel {

    /** The field at a point, in nanotesla: north (X), east (Y) and down (Z). */
    data class Field(val northNanoTesla: Double, val eastNanoTesla: Double, val downNanoTesla: Double)

    /** Degrees east of true north that a compass points; negative is west. */
    fun declinationDegrees(
        latitudeDegrees: Double,
        longitudeDegrees: Double,
        altitudeKm: Double,
        decimalYear: Double,
    ): Double {
        val f = field(latitudeDegrees, longitudeDegrees, altitudeKm, decimalYear)
        return atan2(f.eastNanoTesla, f.northNanoTesla) * 180.0 / PI
    }

    /** NOAA's convention: the year plus the whole days elapsed since 1 January over the days in it. */
    fun decimalYear(date: LocalDate): Double {
        val y = date.year
        val daysInYear = if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 366.0 else 365.0
        return y + (date.dayOfYear - 1) / daysInYear
    }

    fun field(latitudeDegrees: Double, longitudeDegrees: Double, altitudeKm: Double, decimalYear: Double): Field {
        // At exactly a geographic pole the east component divides by zero and "north" has no
        // direction anyway; no city sits there, so the point is nudged a hair towards the equator.
        val latitude = latitudeDegrees.coerceIn(-POLE_LIMIT_DEGREES, POLE_LIMIT_DEGREES)
        val phi = latitude * DEG
        val lambda = longitudeDegrees * DEG

        // Geodetic (WGS-84) to geocentric spherical: radius and latitude.
        val sinLat = sin(phi)
        val cosLat = cos(phi)
        val rc = A / sqrt(1.0 - EPS_SQ * sinLat * sinLat)
        val xp = (rc + altitudeKm) * cosLat
        val zp = (rc * (1.0 - EPS_SQ) + altitudeKm) * sinLat
        val r = sqrt(xp * xp + zp * zp)
        val phiG = asin(zp / r)

        // Gauss-normalised associated Legendre functions of sin(phiG) and their derivatives,
        // then Schmidt quasi-normalised; the derivative's sign flips for spherical coordinates.
        val x = sin(phiG)
        val z = sqrt((1.0 - x) * (1.0 + x))
        val p = DoubleArray(TERMS)
        val dp = DoubleArray(TERMS)
        p[0] = 1.0
        for (n in 1..MAX_DEGREE) {
            for (m in 0..n) {
                val i = index(n, m)
                if (n == m) {
                    val i1 = index(n - 1, m - 1)
                    p[i] = z * p[i1]
                    dp[i] = z * dp[i1] + x * p[i1]
                } else if (n == 1) {
                    p[i] = x * p[0]
                    dp[i] = x * dp[0] - z * p[0]
                } else {
                    val i1 = index(n - 2, m)
                    val i2 = index(n - 1, m)
                    if (m > n - 2) {
                        p[i] = x * p[i2]
                        dp[i] = x * dp[i2] - z * p[i2]
                    } else {
                        val k = ((n - 1) * (n - 1) - m * m).toDouble() / ((2 * n - 1) * (2 * n - 3)).toDouble()
                        p[i] = x * p[i2] - k * p[i1]
                        dp[i] = x * dp[i2] - z * p[i2] - k * dp[i1]
                    }
                }
            }
        }
        for (i in 1 until TERMS) {
            p[i] *= SCHMIDT[i]
            dp[i] = -dp[i] * SCHMIDT[i]
        }

        // The spherical-harmonic sums (WMM technical report, equations 10-12) over coefficients
        // moved from the epoch to the requested date by their secular variation.
        val dt = decimalYear - coefficients.epoch
        var bx = 0.0
        var by = 0.0
        var bz = 0.0
        for (n in 1..MAX_DEGREE) {
            val radiusPower = (RE / r).pow(n + 2)
            for (m in 0..n) {
                val i = index(n, m)
                val g = coefficients.g[i] + dt * coefficients.gDot[i]
                val h = coefficients.h[i] + dt * coefficients.hDot[i]
                val cosM = cos(m * lambda)
                val sinM = sin(m * lambda)
                bz -= radiusPower * (g * cosM + h * sinM) * (n + 1) * p[i]
                by += radiusPower * (g * sinM - h * cosM) * m * p[i]
                bx -= radiusPower * (g * cosM + h * sinM) * dp[i]
            }
        }
        by /= cos(phiG)

        // Back from the geocentric frame to the geodetic one, which differs by phiG - phi.
        val psi = phiG - phi
        return Field(
            northNanoTesla = bx * cos(psi) - bz * sin(psi),
            eastNanoTesla = by,
            downNanoTesla = bx * sin(psi) + bz * cos(psi),
        )
    }

    private const val MAX_DEGREE = 12
    private const val TERMS = (MAX_DEGREE + 1) * (MAX_DEGREE + 2) / 2
    private const val DEG = PI / 180.0
    private const val POLE_LIMIT_DEGREES = 89.999999

    /** WGS-84 semi-major axis and first eccentricity squared (from the semi-minor axis), in km. */
    private const val A = 6378.137
    private const val B = 6356.7523142
    private const val EPS_SQ = 1.0 - (B * B) / (A * A)

    /** The model's reference radius, in km. */
    private const val RE = 6371.2

    private fun index(n: Int, m: Int) = n * (n + 1) / 2 + m

    /** Schmidt quasi-normalisation factors relative to Gauss normalisation, by [index]. */
    private val SCHMIDT = DoubleArray(TERMS).also { s ->
        s[0] = 1.0
        for (n in 1..MAX_DEGREE) {
            s[index(n, 0)] = s[index(n - 1, 0)] * (2 * n - 1).toDouble() / n
            for (m in 1..n) {
                s[index(n, m)] = s[index(n, m - 1)] *
                    sqrt(((n - m + 1) * (if (m == 1) 2 else 1)).toDouble() / (n + m))
            }
        }
    }

    private class Coefficients(
        val epoch: Double,
        val g: DoubleArray,
        val h: DoubleArray,
        val gDot: DoubleArray,
        val hDot: DoubleArray,
    )

    private val coefficients: Coefficients by lazy {
        val lines = COEFFICIENTS.trim('\n').lines()
        val epoch = lines.first().trim().split(Regex("\\s+")).first().toDouble()
        val g = DoubleArray(TERMS)
        val h = DoubleArray(TERMS)
        val gDot = DoubleArray(TERMS)
        val hDot = DoubleArray(TERMS)
        for (line in lines.drop(1)) {
            if (line.startsWith("9999")) break
            val v = line.trim().split(Regex("\\s+"))
            val i = index(v[0].toInt(), v[1].toInt())
            g[i] = v[2].toDouble()
            h[i] = v[3].toDouble()
            gDot[i] = v[4].toDouble()
            hDot[i] = v[5].toDouble()
        }
        Coefficients(epoch, g, h, gDot, hDot)
    }

    /**
     * NOAA's `WMM.COF` for WMM2025, byte for byte (SHA-256 dfa8597825af4e0b87ff4198a5b4fb661b3c49f4cd090cd0164e0259b075582f), from `WMM2025COF.zip`
     * at ncei.noaa.gov/products/world-magnetic-model. Columns: degree n, order m, g and h in nT,
     * and their secular variation in nT a year.
     */
    private const val COEFFICIENTS = """
    2025.0            WMM-2025     11/13/2024
  1  0  -29351.8       0.0       12.0        0.0
  1  1   -1410.8    4545.4        9.7      -21.5
  2  0   -2556.6       0.0      -11.6        0.0
  2  1    2951.1   -3133.6       -5.2      -27.7
  2  2    1649.3    -815.1       -8.0      -12.1
  3  0    1361.0       0.0       -1.3        0.0
  3  1   -2404.1     -56.6       -4.2        4.0
  3  2    1243.8     237.5        0.4       -0.3
  3  3     453.6    -549.5      -15.6       -4.1
  4  0     895.0       0.0       -1.6        0.0
  4  1     799.5     278.6       -2.4       -1.1
  4  2      55.7    -133.9       -6.0        4.1
  4  3    -281.1     212.0        5.6        1.6
  4  4      12.1    -375.6       -7.0       -4.4
  5  0    -233.2       0.0        0.6        0.0
  5  1     368.9      45.4        1.4       -0.5
  5  2     187.2     220.2        0.0        2.2
  5  3    -138.7    -122.9        0.6        0.4
  5  4    -142.0      43.0        2.2        1.7
  5  5      20.9     106.1        0.9        1.9
  6  0      64.4       0.0       -0.2        0.0
  6  1      63.8     -18.4       -0.4        0.3
  6  2      76.9      16.8        0.9       -1.6
  6  3    -115.7      48.8        1.2       -0.4
  6  4     -40.9     -59.8       -0.9        0.9
  6  5      14.9      10.9        0.3        0.7
  6  6     -60.7      72.7        0.9        0.9
  7  0      79.5       0.0       -0.0        0.0
  7  1     -77.0     -48.9       -0.1        0.6
  7  2      -8.8     -14.4       -0.1        0.5
  7  3      59.3      -1.0        0.5       -0.8
  7  4      15.8      23.4       -0.1        0.0
  7  5       2.5      -7.4       -0.8       -1.0
  7  6     -11.1     -25.1       -0.8        0.6
  7  7      14.2      -2.3        0.8       -0.2
  8  0      23.2       0.0       -0.1        0.0
  8  1      10.8       7.1        0.2       -0.2
  8  2     -17.5     -12.6        0.0        0.5
  8  3       2.0      11.4        0.5       -0.4
  8  4     -21.7      -9.7       -0.1        0.4
  8  5      16.9      12.7        0.3       -0.5
  8  6      15.0       0.7        0.2       -0.6
  8  7     -16.8      -5.2       -0.0        0.3
  8  8       0.9       3.9        0.2        0.2
  9  0       4.6       0.0       -0.0        0.0
  9  1       7.8     -24.8       -0.1       -0.3
  9  2       3.0      12.2        0.1        0.3
  9  3      -0.2       8.3        0.3       -0.3
  9  4      -2.5      -3.3       -0.3        0.3
  9  5     -13.1      -5.2        0.0        0.2
  9  6       2.4       7.2        0.3       -0.1
  9  7       8.6      -0.6       -0.1       -0.2
  9  8      -8.7       0.8        0.1        0.4
  9  9     -12.9      10.0       -0.1        0.1
 10  0      -1.3       0.0        0.1        0.0
 10  1      -6.4       3.3        0.0        0.0
 10  2       0.2       0.0        0.1       -0.0
 10  3       2.0       2.4        0.1       -0.2
 10  4      -1.0       5.3       -0.0        0.1
 10  5      -0.6      -9.1       -0.3       -0.1
 10  6      -0.9       0.4        0.0        0.1
 10  7       1.5      -4.2       -0.1        0.0
 10  8       0.9      -3.8       -0.1       -0.1
 10  9      -2.7       0.9       -0.0        0.2
 10 10      -3.9      -9.1       -0.0       -0.0
 11  0       2.9       0.0        0.0        0.0
 11  1      -1.5       0.0       -0.0       -0.0
 11  2      -2.5       2.9        0.0        0.1
 11  3       2.4      -0.6        0.0       -0.0
 11  4      -0.6       0.2        0.0        0.1
 11  5      -0.1       0.5       -0.1       -0.0
 11  6      -0.6      -0.3        0.0       -0.0
 11  7      -0.1      -1.2       -0.0        0.1
 11  8       1.1      -1.7       -0.1       -0.0
 11  9      -1.0      -2.9       -0.1        0.0
 11 10      -0.2      -1.8       -0.1        0.0
 11 11       2.6      -2.3       -0.1        0.0
 12  0      -2.0       0.0        0.0        0.0
 12  1      -0.2      -1.3        0.0       -0.0
 12  2       0.3       0.7       -0.0        0.0
 12  3       1.2       1.0       -0.0       -0.1
 12  4      -1.3      -1.4       -0.0        0.1
 12  5       0.6      -0.0       -0.0       -0.0
 12  6       0.6       0.6        0.1       -0.0
 12  7       0.5      -0.1       -0.0       -0.0
 12  8      -0.1       0.8        0.0        0.0
 12  9      -0.4       0.1        0.0       -0.0
 12 10      -0.2      -1.0       -0.1       -0.0
 12 11      -1.3       0.1       -0.0        0.0
 12 12      -0.7       0.2       -0.1       -0.1
999999999999999999999999999999999999999999999999
999999999999999999999999999999999999999999999999
"""
}
