package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Maghreb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Task 7d: Libya, Tunisia, Algeria, Morocco, Mauritania and Sudan. The official tables are
 * restricted, so the gate (tools/timetables/official/gate/ly-awqaf.tsv and its neighbours) proves
 * the margins against them; this test holds what may be committed: the owner's own adhan times for
 * Benghazi and Sabha on 27 Sep 2026 (spec §10.3) and the structure the proof rests on.
 */
class MaghrebProofTest {
    private val tripoliZone = TimeZone.of("Africa/Tripoli")
    private val date = LocalDate(2026, 9, 27)

    private fun libya(lat: Double, lon: Double) = Registry.resolve(Place(lat, lon, "Africa/Tripoli", "LY"))

    private fun day(r: Resolution, on: LocalDate = date): PrayerDay = DayPipeline.day(r, on, tripoliZone)

    private fun at(hour: Int, minute: Int): Instant = LocalDateTime(2026, 9, 27, hour, minute).toInstant(tripoliZone)

    /** The adhan the owner heard (spec §10.3): Fajr at the 19.5° dawn, Maghrib at sunset + 1. */
    private class Adhan(val name: String, val point: GeoPoint, val fajr: Pair<Int, Int>, val maghrib: Pair<Int, Int>)

    private val adhans = listOf(
        Adhan("Benghazi", GeoPoint(32.1167, 20.0667), 5 to 2, 18 to 31),
        Adhan("Sabha", GeoPoint(27.0377, 14.4283), 5 to 28, 18 to 54),
    )

    @Test
    fun `benghazi and sabha are never before the adhan their mosques called on 27 september 2026`() {
        for (a in adhans) {
            val r = libya(a.point.lat, a.point.lon)
            assertEquals("ly.awqaf", r.entry.id)
            assertEquals(a.name, r.unitName)
            val d = day(r)
            val fajr = at(a.fajr.first, a.fajr.second)
            val maghrib = at(a.maghrib.first, a.maghrib.second)
            assertTrue(d.fajr >= fajr, "${a.name} Fajr ${d.fajr} before the adhan $fajr")
            assertTrue(d.maghrib >= maghrib, "${a.name} Maghrib ${d.maghrib} before the adhan $maghrib")
            // Ruling R73: Fajr and Maghrib follow that same adhan there, at most 2 min after it.
            assertTrue(d.fajr - fajr <= 2.minutes, "${a.name} Fajr ${d.fajr} more than 2 min after the adhan $fajr")
            assertTrue(d.maghrib - maghrib <= 2.minutes, "${a.name} Maghrib ${d.maghrib} more than 2 min after the adhan $maghrib")
            // The fast begins at the 19.5° dawn their mosques call, never after it, and at most a minute before.
            assertTrue(d.endOfEating <= fajr, "${a.name} end of eating ${d.endOfEating} after the adhan $fajr")
            assertTrue(fajr - d.endOfEating <= 1.minutes, "${a.name} end of eating ${d.endOfEating}")
            // Fajr's own start margin (the plain safe 30 s) keeps it after the end of eating.
            assertTrue(d.fajr > d.endOfEating, a.name)
        }
    }

    @Test
    fun `ruling r73 moves benghazi and sabha's fajr and maghrib to the local adhan while tripoli and misrata stay unchanged`() {
        for (a in adhans + listOf(Adhan("Tobruk", GeoPoint(32.0836, 23.9764), 0 to 0, 0 to 0), Adhan("Kufra", GeoPoint(24.1833, 23.2833), 0 to 0, 0 to 0))) {
            val method = assertNotNull(libya(a.point.lat, a.point.lon).method, a.name)
            assertEquals(19.5, method.fajrAngle, a.name)
            assertEquals(1, method.authorityMinutes[Prayer.MAGHRIB], a.name)
            assertEquals(SAFE_START, method.margins.fajr, a.name)
            assertEquals(SAFE_START, method.margins.maghrib, a.name)
        }
        for (western in listOf("Tripoli" to (32.8872 to 13.1913), "Misrata" to (32.3754 to 15.0925))) {
            val (name, point) = western
            val method = assertNotNull(libya(point.first, point.second).method, name)
            assertEquals(18.5, method.fajrAngle, name)
            assertEquals(0, method.authorityMinutes[Prayer.MAGHRIB], name)
            assertEquals(63, method.margins.fajr, name)
            assertEquals(261, method.margins.maghrib, name)
        }
    }

    @Test
    fun `beyond every city the east and south keep the edge's minute on the local adhan's fajr and maghrib`() {
        // Rulings R44 and R45: beyond 30 km of the 22 cities every start takes a minute more. R73's
        // plain safe 30 s is the local margin at a unit; the edge adds its minute on top, as the
        // west's edge does on the national margin, and Dhuhr keeps it too.
        val edgeMinute = 60
        val eastSouth = listOf("Ubari" to (26.5921 to 12.7805), "Al Qubbah" to (32.7612 to 22.2472), "the Fezzan desert" to (26.0 to 12.0))
        for ((name, point) in eastSouth) {
            val r = libya(point.first, point.second)
            assertNull(r.unitName, name)
            val method = assertNotNull(r.method, name)
            assertEquals("ly.awqaf.edge", method.id, name)
            assertEquals(19.5, method.fajrAngle, name)
            assertEquals(SAFE_START + edgeMinute, method.margins.fajr, name)
            assertEquals(SAFE_START + edgeMinute, method.margins.maghrib, name)
            assertEquals(Maghreb.libyaMethod.margins.dhuhr + edgeMinute, method.margins.dhuhr, name)
        }
        // The west's edge is the national method's, a minute wider: Fajr 63 + 60, Maghrib 261 + 60.
        for ((name, point) in listOf("Nalut" to (31.8793 to 10.9754), "Gharyan" to (32.1722 to 13.0203))) {
            val r = libya(point.first, point.second)
            assertNull(r.unitName, name)
            val method = assertNotNull(r.method, name)
            assertEquals("ly.awqaf.edge", method.id, name)
            assertEquals(18.5, method.fajrAngle, name)
            assertEquals(63 + edgeMinute, method.margins.fajr, name)
            assertEquals(261 + edgeMinute, method.margins.maghrib, name)
        }
    }

    @Test
    fun `the east and the south stop eating at the nineteen and a half degree dawn with no fitted margin`() {
        for (a in adhans + listOf(Adhan("Tobruk", GeoPoint(32.0836, 23.9764), 0 to 0, 0 to 0), Adhan("Kufra", GeoPoint(24.1833, 23.2833), 0 to 0, 0 to 0))) {
            val method = assertNotNull(libya(a.point.lat, a.point.lon).method)
            assertEquals(EndOfEating.DawnAngle(19.5), method.endOfEating, a.name)
            assertTrue(method.endOfEatingMarginSeconds <= SAFE_END, a.name)
        }
        for ((lat, lon) in listOf(32.8872 to 13.1913, 32.3754 to 15.0925, 30.1333 to 9.5)) {
            val method = assertNotNull(libya(lat, lon).method)
            assertEquals(EndOfEating.SameAsFajrDawn, method.endOfEating, "$lat,$lon")
        }
    }

    @Test
    fun `every libyan city keeps its day in order through 2026 and stops eating by its fajr`() {
        val cities = Units.of("ly.awqaf")!!.units
        assertEquals(22, cities.size)
        var d = LocalDate(2026, 1, 1)
        var checked = 0
        while (d.year == 2026) {
            for (city in cities) {
                checked++
                val r = libya(city.point.lat, city.point.lon)
                val day = day(r, d)
                assertFalse(day.repaired, "${city.name} $d out of order")
                assertTrue(day.endOfEating <= day.fajr, "${city.name} $d")
                assertTrue(assertNotNull(day.imsak) <= day.fajr, "${city.name} $d")
            }
            d = d.plus(16, DateTimeUnit.DAY)
        }
        assertEquals(22 * 23, checked)
    }

    @Test
    fun `jalu and awjila print their asr seven and a half minutes after the rule`() {
        val jalu = assertNotNull(libya(29.0331, 21.5482).method)
        val brega = assertNotNull(libya(30.4061, 19.5739).method)
        assertEquals(450, jalu.monthlyOffsets!!.getValue(Prayer.ASR).distinct().single())
        assertEquals(brega.margins, jalu.margins)
        assertNull(brega.monthlyOffsets)
    }

    @Test
    fun `libya is class d at its cities and claims nothing beyond them`() {
        val tripoli = libya(32.8872, 13.1913)
        assertEquals(EntryClass.D_AUTHORITY, tripoli.entryClass)
        assertTrue(tripoli.measured)
        val desert = libya(26.0, 12.0)
        assertNull(desert.unitName)
        assertFalse(desert.measured)
    }

    @Test
    fun `tunisia is class b at its six delegations and its dips keep the national end of eating`() {
        val entry = Registry.byId("tn.inm")!!
        assertEquals(EntryClass.B, entry.entryClass)
        val units = Units.of("tn.inm")!!.units
        assertEquals(6, units.size)
        for (id in listOf("tataouine", "tala")) {
            val dipped = assertNotNull(units.single { it.id == id }.method)
            assertEquals(entry.method!!.endOfEatingMarginSeconds, dipped.endOfEatingMarginSeconds, id)
            assertTrue(dipped.twilightDipDeg > 0.0, id)
        }
    }

    @Test
    fun `algeria's base cities are b within their reach and the rest of their wilaya claims nothing`() {
        fun dz(lat: Double, lon: Double, admin1: String? = null) = Registry.resolve(Place(lat, lon, "Africa/Algiers", "DZ", admin1))
        val algiers = dz(36.7538, 3.0588)
        assertEquals(EntryClass.B, algiers.entryClass)
        assertTrue(algiers.measured)
        // Far west of Adrar's seat, in its wilaya: the seat's table, no figure claimed.
        val west = dz(27.3, -1.9, admin1 = "Adrar")
        assertEquals("Adrar", west.unitName)
        assertEquals(GeoPoint(27.8742, -0.2939), assertNotNull(west.method).fixedPoint)
        assertFalse(west.measured)
        assertEquals(EntryClass.D_AUTHORITY, west.entryClass)
    }

    @Test
    fun `tamanrasset's asr is its only recorded exception and oran has none`() {
        val entry = Registry.byId("dz.marw")!!
        val units = Units.of("dz.marw")!!.units
        val tamanrasset = units.single { it.id == "11" }
        val oran = units.single { it.id == "31" }
        assertEquals(6, assertNotNull(lateLimitFor(TimedEvent.ASR, tamanrasset, entry)).minutes)
        for (event in TimedEvent.entries.filter { it != TimedEvent.ASR }) assertNull(lateLimitFor(event, tamanrasset, entry), "$event")
        for (event in TimedEvent.entries) assertNull(lateLimitFor(event, oran, entry), "$event")
        assertEquals(EntryClass.D_AUTHORITY, tamanrasset.entryClass)
        assertEquals(EntryClass.D_AUTHORITY, oran.entryClass)
    }

    @Test
    fun `morocco's national margins are exceptions for sunrise and maghrib beyond the fitted cities only`() {
        val entry = Registry.byId("ma.habous")!!
        val units = Units.of("ma.habous")!!.units
        assertEquals(10, units.size)
        for (event in TimedEvent.entries) {
            val limit = lateLimitFor(event, null, entry)
            when (event) {
                TimedEvent.SUNRISE -> assertEquals(7, assertNotNull(limit).minutes)
                TimedEvent.MAGHRIB -> assertEquals(6, assertNotNull(limit).minutes)
                else -> assertNull(limit, "$event")
            }
        }
        for (unit in units) {
            assertEquals(2, lateLimitFor(TimedEvent.MAGHRIB, unit, entry)!!.minutes, unit.id)
            assertEquals(2, lateLimitFor(TimedEvent.SUNRISE, unit, entry)!!.minutes, unit.id)
            // Each fitted city's own elevation: its Maghrib before the edge's, its sunrise after it.
            val own = assertNotNull(unit.method, unit.id).margins
            assertTrue(own.maghrib < 510 && own.sunrise > -510, unit.id)
        }
        val edge = Units.of("ma.habous")!!.outside(GeoPoint(31.63, -8.0))
        assertEquals(510, edge.margins.maghrib)
        assertEquals(-510, edge.margins.sunrise)
        // The unfitted cities' points are not known: a minute more on the other starts.
        val base = entry.method!!.margins
        assertEquals(listOf(base.fajr, base.dhuhr, base.asr, base.isha).map { it + 60 }, listOf(edge.margins.fajr, edge.margins.dhuhr, edge.margins.asr, edge.margins.isha))
    }

    @Test
    fun `mauritania and sudan keep every start at least the plain safe rounding after their method`() {
        for (id in listOf("mr.ministry", "sd.ministry")) {
            val m = Registry.byId(id)!!.method!!.margins
            for (start in listOf(m.fajr, m.dhuhr, m.asr, m.maghrib, m.isha)) assertTrue(start >= SAFE_START, id)
            assertTrue(Registry.byId(id)!!.method!!.endOfEatingMarginSeconds <= SAFE_END, id)
        }
        val sudan = Registry.byId("sd.ministry")!!
        assertEquals(EndOfEating.DawnAngle(19.5), sudan.method!!.endOfEating)
        assertEquals(7, lateLimitFor(TimedEvent.END_OF_EATING, null, sudan)!!.minutes)
        assertEquals(5, Registry.byId("mr.ministry")!!.method!!.imsakMinutesBeforeFajr)
    }

    // Ruling R74: Sudan's Ramadan-only Isha floor (a 2026 imsakiya circulating under the Fiqh
    // Academy's name puts Isha at Maghrib + 90, against the verified 2022 table's 18.0° angle).

    private val khartoumZone = TimeZone.of("Africa/Khartoum")
    private val khartoum = GeoPoint(15.5007, 32.5599)

    private fun sudan() = Registry.resolve(Place(khartoum.lat, khartoum.lon, "Africa/Khartoum", "SD"))

    private fun sudanDay(r: Resolution, on: LocalDate): PrayerDay = DayPipeline.day(r, on, khartoumZone)

    @Test
    fun `sudan's isha is never earlier than maghrib plus ninety minutes on its ramadan dates`() {
        val r = sudan()
        assertEquals("sd.ministry", r.entry.id)
        val floorless = r.copy(method = r.method!!.copy(ramadan = null))

        // 1 Mar 2026 falls inside Ramadan 1447 (Registry.ramadanCalendarFor's tabular ±1 day fallback).
        val ramadanDate = LocalDate(2026, 3, 1)
        val ramadanDay = sudanDay(r, ramadanDate)
        val ramadanNoFloor = sudanDay(floorless, ramadanDate)
        assertEquals(ramadanNoFloor.maghrib, ramadanDay.maghrib, "the floor must not move Maghrib itself")
        assertEquals(maxOf(ramadanNoFloor.isha, ramadanNoFloor.maghrib + 90.minutes), ramadanDay.isha)
        // On this date the 90-min floor is the later of the two (matches the research's 13-19 min gap).
        assertTrue(ramadanDay.isha > ramadanNoFloor.isha, "the floor should move Isha later on this date")
        assertEquals(ramadanNoFloor.maghrib + 90.minutes, ramadanDay.isha)

        // Late September is nowhere near Ramadan 1447/1448: nothing changes there.
        val nonRamadanDate = LocalDate(2026, 9, 27)
        val nonRamadanDay = sudanDay(r, nonRamadanDate)
        val nonRamadanNoFloor = sudanDay(floorless, nonRamadanDate)
        assertEquals(nonRamadanNoFloor.isha, nonRamadanDay.isha, "outside Ramadan Isha must be unchanged")
    }

    @Test
    fun `no other registry method carries a ramadan isha floor`() {
        for (entry in Registry.entries.filter { it.id != "sd.ministry" }) {
            entry.method?.let { assertNull(it.ramadan?.ishaFloorMinutesAfterMaghrib, entry.id) }
            Units.of(entry.id)?.units?.forEach { unit ->
                unit.method?.let { assertNull(it.ramadan?.ishaFloorMinutesAfterMaghrib, "${entry.id} ${unit.id}") }
            }
        }
        assertEquals(90, Registry.byId("sd.ministry")!!.method!!.ramadan!!.ishaFloorMinutesAfterMaghrib)
    }
}
