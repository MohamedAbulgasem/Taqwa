package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.ImsakFrom
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.JakimZoneCities
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.units.EnvelopeRows
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rules the review round of 13 Nov 2026 left on JAKIM's zones (rulings R226 and R228 as revised): a zone read on one
 * year's table carries the forward margin, a Dhuhr margin under zero is only ever beside a start point far enough west,
 * a listed town is its zone's alone, a hill's table stops at the hill, and the days the engine holds as printed are the
 * days the monitor's fetcher lets through. Rules only: no printed time is held here, and nothing is read from the archive.
 */
class JakimZoneRulesTest {

    private val gateDir = TestPaths.repoRoot.resolve("tools/timetables/official/gate")
    private val units = Units.of("my.jakim")!!.units.associateBy { it.id }

    private fun clock(zone: String) = if (zone.startsWith("SBH") || zone.startsWith("SWK") || zone == "WLY02") "Asia/Kuching" else "Asia/Kuala_Lumpur"

    /** Every point a place takes [z] from: its fitted point, its towns of the app's list and the places its list names. */
    private fun anchors(z: JakimZones.Zone): List<GeoPoint> =
        (listOf(z.point) + JakimZoneCities.byZone[z.id].orEmpty().map { GeoPoint(it.lat, it.lon) } + z.places).distinct()

    @Test
    fun `a zone read on one year's table alone carries the forward margin on every event to its safe side`() {
        val rows = GateManifest.load(gateDir).only(setOf("my-jakim")).rows.filter { it.entry == "my.jakim" && it.lat == null }
        for (z in JakimZones.zones) {
            // A table's year by its file's name; a file that names none is a year of its own.
            val years = rows.filter { it.unit == z.id }
                .map { it.path.substringAfterLast('/') }.map { Regex("""-(20\d\d)""").find(it)?.groupValues?.get(1) ?: it }.toSet()
            assertTrue(years.isNotEmpty(), "${z.id}: no table at its unit")
            // One way only: a second year `prove` adds leaves the wider margins standing until the zone is refitted.
            if (years.size == 1) assertTrue(z.oneYear, "${z.id}: only $years is held, so it is a one-year zone")
            val forward = if (z.oneYear) JakimZones.FORWARD_SECONDS else 0
            val method = assertNotNull(units.getValue(z.id).method, z.id)
            val m = method.margins
            assertEquals(
                listOf(z.margins.fajr + forward, z.margins.dhuhr + forward, z.margins.asr + forward, z.margins.maghrib + forward, z.margins.isha + forward),
                listOf(m.fajr, m.dhuhr, m.asr, m.maghrib, m.isha), "${z.id}: every start $forward s later",
            )
            assertEquals(z.margins.sunrise - forward, m.sunrise, "${z.id}: the sunrise $forward s earlier")
            val imsak = z.imsakMarginSeconds
            if (imsak == null) assertEquals(ImsakFrom.Dawn, method.imsakFrom, z.id)
            else assertEquals(ImsakFrom.PrintedFajr(imsak - forward), method.imsakFrom, "${z.id}: the imsak $forward s earlier")
        }
        assertTrue(JakimZones.FORWARD_SECONDS > 0)
    }

    @Test
    fun `a dhuhr margin under zero stands only beside a start point west of every place that takes the zone`() {
        // Starts are the latest of the place's own and the start points'. Noon moves 240 s a degree of longitude, so a
        // start point at least margin / 240 degrees west of a place keeps its Dhuhr, margin and all, after the place's
        // own noon; a place takes the zone up to CITY_KM west of its westmost town.
        for (z in JakimZones.zones) {
            val margin = assertNotNull(units.getValue(z.id).method).margins.dhuhr
            if (margin >= 0) continue
            val west = assertNotNull(z.startPoints.minOfOrNull { it.lon }, "${z.id}: a Dhuhr margin of $margin s and no start point")
            for (p in anchors(z)) {
                val reach = JakimZones.CITY_KM / (KM_PER_DEGREE * cos(Math.toRadians(p.lat)))
                assertTrue(
                    (p.lon - reach) - west >= -margin / SECONDS_PER_DEGREE,
                    "${z.id}: its start point at $west is not ${-margin} s of noon west of the reach of (${p.lat}, ${p.lon})",
                )
            }
        }
    }

    @Test
    fun `a listed town takes its own zone and no other zone rides beside it`() {
        for ((zone, towns) in JakimZoneCities.byZone) {
            for (town in towns) {
                val here = EnvelopeRows.jakimAt(GeoPoint(town.lat, town.lon), clock(zone))
                assertEquals(zone, here.taken?.id, "town ${town.id}")
                assertTrue(here.riding.isEmpty(), "town ${town.id} of $zone: ${here.riding.map { it.id }} ride beside it")
            }
        }
    }

    @Test
    fun `a hill's table is taken and rides only at the hill`() {
        val hills = JakimZones.zones.filter { it.hill }
        assertTrue(hills.isNotEmpty())
        for (z in hills) {
            val towns = JakimZoneCities.byZone[z.id].orEmpty().map { GeoPoint(it.lat, it.lon) }
            val fitted = z !in JakimZones.named
            val listed = towns + listOfNotNull(z.point.takeIf { fitted })
            val peaks = z.places + listOfNotNull(z.point.takeIf { !fitted })
            assertTrue(peaks.isNotEmpty(), "${z.id}: a hill zone with no named place")
            for (peak in peaks) {
                val at = EnvelopeRows.jakimAt(peak, clock(z.id))
                assertTrue(z.id == at.taken?.id || at.riding.any { it.id == z.id }, "${z.id}: not at its own named place")
                val step = 2 * JakimZones.HILL_KM / KM_PER_DEGREE
                val ring = listOf(
                    GeoPoint(peak.lat + step, peak.lon), GeoPoint(peak.lat - step, peak.lon),
                    GeoPoint(peak.lat, peak.lon + step), GeoPoint(peak.lat, peak.lon - step),
                )
                for (p in ring) {
                    // Another named place or a listed town of the same zone near the ring point is its own case.
                    if ((peaks + listed).any { it != peak && km(p, it) <= 3 * 2 * JakimZones.HILL_KM + JakimZones.CITY_KM }) continue
                    val there = EnvelopeRows.jakimAt(p, clock(z.id))
                    assertFalse(there.taken?.id == z.id, "${z.id}: taken ${km(p, peak)} km from its named place")
                    assertFalse(there.riding.any { it.id == z.id }, "${z.id}: rides ${km(p, peak)} km from its named place")
                }
            }
        }
    }

    @Test
    fun `the days the engine holds as printed are the days the fetcher lets through and no others`() {
        val fetcher = TestPaths.repoRoot.resolve("tools/timetables/monitor/fetchers/jakim.py").readText()
        val block = fetcher.substringAfter("\nHELD_AS_PRINTED = {").substringBefore("}")
        val listed = Regex(""""([A-Z]{3}\d\d)": \("(\d{4}-\d\d-\d\d)", "(\d{4}-\d\d-\d\d)"\)""").findAll(block)
            .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toSet()
        val held = JakimZones.zones.flatMap { z -> z.printedLater.map { Triple(z.id, it.from.toString(), it.to.toString()) } }.toSet()
        assertEquals(held, listed, "jakim.py's HELD_AS_PRINTED against JakimZones' printedLater")
        // A held day is never a silent one: the zone records how far its ends stand before the print on it.
        for (z in JakimZones.zones.filter { it.printedLater.isNotEmpty() }) {
            assertTrue(z.late.any { it.why == JakimZones.Why.PRINTED_LATER }, "${z.id}: a held run with no recorded limit")
        }
    }

    private fun km(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (a.lat - b.lat) * KM_PER_DEGREE
        val dLon = (a.lon - b.lon) * KM_PER_DEGREE * cos(Math.toRadians((a.lat + b.lat) / 2))
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon)
    }

    private companion object {
        const val KM_PER_DEGREE = 111.2
        const val SECONDS_PER_DEGREE = 240.0
    }
}
