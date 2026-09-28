package world.taqwa.app.prayer.engine.registry.data

import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint

/**
 * JAKIM's 13 fitted zones: derived parameters, not official times (Task 7c: refitted with
 * `Fitter.fit("my.jakim/<zone>")` against each zone's own 2025 mirror table and its 2026 e-solat
 * table, safety 5 s, over the union of both years since a single year's fit does not always cover
 * the other — see `docs/research/2026-09-prayer-times/proof/7c-sea.md`; SBH06 excluded for its 2026
 * breaks). Margins are our convention: starts ⌈raw + m⌉, sunrise ⌊raw + m⌋.
 *
 * - WLY01 and SGR01 share Selangor's starts: the later of a south point (2.62, 101.69) and a
 *   north-west point (3.73, 101.45), fitted (JAKIM's Lampiran B names Tg Rhu Sepang and Kg Gedangsa).
 *   Their sunrise takes SGR01's margin, the earlier, since the two zones' syuruk points differ and a
 *   Selangor suburb may sit nearer KL's point.
 * - KTN01's Isha is a genuine 17° ([ishaAngleDeg]), not the 18° base with a negative margin: fitting
 *   the real angle needs a small, ordinary margin instead of one that tracks the declination's own
 *   drift only by luck.
 * - PHG06 (Cameron Highlands) is computed at a −2.5° horizon (research, MEDIUM: elevation depresses
 *   the visible horizon, so sunrise is earlier and sunset later there than at sea level, matching the
 *   in-sample residual). Its Maghrib keeps a small positive margin rather than the fitted floor
 *   (which needs about −67 s over 2025–2026): the invariant `maghrib ≥ sunset` compares against the
 *   unmargined, floor-rounded sunset at the same horizon, and a margin that negative ceils below it
 *   on some days (out of order, repaired later at runtime). A positive margin stays on the safe,
 *   never-early side (only later, which class B allows) without ever repairing.
 * - PLS01's sunrise carries its own exception ([sunriseLateLimitMinutes]): Perlis's printed sunrise
 *   spreads about 3 min from a plain classic-noon sunrise across the seasons, a minute past class B,
 *   on close to 30 % of the days checked (2025–2026); every other zone's spread stays inside 2 min.
 */
object JakimZones {
    class Zone(
        val id: String,
        val city: String,
        val point: GeoPoint,
        val radiusKm: Double,
        val margins: EventOffsets,
        val horizonDeg: Double = -0.8333,
        val startPoints: List<GeoPoint> = emptyList(),
        val imsak: Boolean = true,
        val ishaAngleDeg: Double = 18.0,
        val sunriseLateLimitMinutes: Int? = null,
    )

    private val selangor = listOf(GeoPoint(2.62, 101.69), GeoPoint(3.73, 101.45))

    private fun m(fajr: Int, sunrise: Int, dhuhr: Int, asr: Int, maghrib: Int, isha: Int) =
        EventOffsets(fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr, maghrib = maghrib, isha = isha)

    val zones: List<Zone> = listOf(
        Zone("WLY01", "Kuala Lumpur", GeoPoint(3.1412, 101.68653), 12.0, m(25, -28, 88, 26, 24, 27), startPoints = selangor),
        Zone("SGR01", "Shah Alam", GeoPoint(3.08507, 101.53281), 50.0, m(24, -111, 88, 26, 23, 26), startPoints = selangor),
        Zone("JHR02", "Johor Bahru", GeoPoint(1.4655, 103.7578), 40.0, m(81, 19, 143, 105, 86, 91)),
        Zone("KTN01", "Kota Bharu", GeoPoint(6.12361, 102.24333), 45.0, m(82, 46, 135, 81, 79, 77), ishaAngleDeg = 17.0),
        Zone("SBH07", "Kota Kinabalu", GeoPoint(5.9749, 116.0724), 40.0, m(70, 2, 110, 62, 68, 68)),
        Zone("SWK08", "Kuching", GeoPoint(1.55, 110.33333), 45.0, m(169, 104, 208, 175, 168, 172)),
        Zone("SBH01", "Sandakan", GeoPoint(5.8402, 118.1179), 50.0, m(123, 38, 148, 116, 118, 114)),
        Zone(
            "PLS01", "Kangar", GeoPoint(6.4414, 100.1986), 30.0, m(29, -117, 91, 28, 28, 28), imsak = false,
            sunriseLateLimitMinutes = 3,
        ),
        Zone("PNG01", "George Town", GeoPoint(5.4141, 100.3288), 25.0, m(58, -93, 109, 72, 64, 61)),
        Zone("WLY02", "Labuan", GeoPoint(5.2831, 115.2308), 20.0, m(33, -35, 93, 32, 32, 33)),
        Zone("PHG06", "Tanah Rata", GeoPoint(4.4721, 101.3801), 20.0, m(9, 57, 86, 15, 30, 7), horizonDeg = -2.5),
        Zone("JHR01", "Pulau Aur", GeoPoint(2.45, 104.52), 10.0, m(67, 27, 138, 81, 69, 72)),
        Zone("PHG01", "Pulau Tioman", GeoPoint(2.79, 104.17), 10.0, m(18, -7, 95, 25, 14, 16)),
    )
}
