package world.taqwa.timetables.golden

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks [GOLDEN_CASES] before it is ever written out (brief D): about 2,000 rows, no two seeds
 * within 5 km of each other (a fixture-only proxy for R69's "never a real table's own point": the
 * real tables are not held here, so this only catches two invented points colliding with each
 * other), and, resolved with Automatic settings, every [EntryClass] appears somewhere. Separately —
 * by [DayPipeline.day] at a neutral point, not through [GOLDEN_CASES] at all — every entry the
 * registry holds computes a day without failing.
 */
class GoldenCasesTest {

    @Test
    fun `about 2000 place-days`() {
        val n = GOLDEN_CASES.size
        assertTrue(n in 1500..2500, "expected about 2000 place-days, got $n")
    }

    @Test
    fun `every entry class is resolved by automatic settings somewhere in the vector`() {
        val classes = GOLDEN_CASES
            .filter { it.settings is SettingsCase.Automatic }
            .map { Registry.resolve(it.seed.place()).entryClass }
            .toSet()
        assertEquals(EntryClass.entries.toSet(), classes, "missing: ${EntryClass.entries.toSet() - classes}")
    }

    @Test
    fun `no two seeds are within 5 km of each other`() {
        val seeds = SEEDS
        for (i in seeds.indices) {
            for (j in i + 1 until seeds.size) {
                val km = haversineKm(seeds[i].lat, seeds[i].lon, seeds[j].lat, seeds[j].lon)
                assertTrue(km > 5.0, "${seeds[i].label} and ${seeds[j].label} are only ${"%.2f".format(km)} km apart")
            }
        }
    }

    /**
     * Every one of the ~101 registry entries, computed at a neutral point (the equator, an "XX"
     * country: no region rule or country default reads either, so this is independent of the seed
     * list entirely) on one fixed date. A unit-bearing entry falls to its own safe edge there; a
     * cautious entry combines its own members, unconditionally non-empty (`RegistryEntry.init`). The
     * point is only that [DayPipeline.day] does not throw and returns a day in the usual order.
     */
    @Test
    fun `every registry entry computes a day at a neutral point`() {
        val place = Place(0.0, 0.5, "UTC", "XX")
        val zone = TimeZone.of(place.zoneId)
        val date = LocalDate(2026, 6, 21)
        val problems = mutableListOf<String>()
        for (entry in Registry.entries) {
            try {
                val day = DayPipeline.day(entry, place, date)
                val order = listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha)
                for (k in 0 until order.size - 1) {
                    if (order[k] >= order[k + 1]) problems += "${entry.id}: $order not in order"
                }
            } catch (e: Exception) {
                problems += "${entry.id}: ${e.message}"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
