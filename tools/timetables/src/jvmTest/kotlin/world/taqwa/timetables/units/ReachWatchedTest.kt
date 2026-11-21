package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.timetables.TestPaths
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ruling R321: the points a probe finer than the sweep read a unit's worst at ([ReachRows.Spec.watched]) are rows of
 * that unit's reach file, so the gate and `prove` keep reading them; Tromsø's (IRN) are the Tromsø probe's worst points
 * on the app's own grid, the over-limit Isha point of 16 May 2026 among them.
 */
class ReachWatchedTest {

    @Test
    fun `every watched point is read by its unit's reach rows`() {
        val specs = ReachRows.SPECS.filter { it.watched.isNotEmpty() }
        assertTrue(specs.any { it.entry == "no.irn" }, "no.irn watches the Tromsø probe's points (R321)")
        for (spec in specs) {
            val lines = TestPaths.repoRoot.resolve("tools/timetables/official/gate/${spec.file}").readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
            val names = lines.first().split('\t').map { it.trim() }
            val (iEntry, iLat, iLon) = listOf("entry", "lat", "lon").map { names.indexOf(it) }
            val rows = lines.drop(1).map { it.split('\t') }
            for ((unit, points) in spec.watched) {
                for (w in points) {
                    val lat = String.format(Locale.ROOT, "%.6f", w.at.lat)
                    val lon = String.format(Locale.ROOT, "%.6f", w.at.lon)
                    assertTrue(
                        rows.any { it[iEntry] == "${spec.entry}/$unit" && it[iLat] == lat && it[iLon] == lon },
                        "${spec.file}: no row of ${spec.entry}/$unit at $lat, $lon (${w.why})",
                    )
                }
            }
        }
    }

    @Test
    fun `tromso watches the probe's over-limit isha point on the app's own grid`() {
        val watched = ReachRows.SPECS.single { it.entry == "no.irn" }.watched.getValue("no.irn.tromso")
        assertTrue(watched.any { it.at == GeoPoint(69.895, 18.852) }, "the probe's Isha point of 16 May 2026")
        // The app keeps a location to three decimals (SettingsRepository.setLocation, PrayerEngine's canonical place),
        // so a watched point is one a user can stand at.
        for (w in watched) {
            for (x in listOf(w.at.lat, w.at.lon)) {
                assertTrue(abs(x * 1000 - round(x * 1000)) < 1e-6, "${w.at} is not on the app's grid of a thousandth of a degree")
            }
        }
    }
}
