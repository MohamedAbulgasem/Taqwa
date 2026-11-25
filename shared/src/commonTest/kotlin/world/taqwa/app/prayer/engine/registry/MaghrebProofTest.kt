package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.registry.ownLateLimitFor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.day.RamadanCalendar
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Maghreb
import world.taqwa.app.prayer.engine.registry.data.HabousCities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `tamanrasset's asr and r265's sunrise are its only recorded exceptions and oran has none`() {
        val entry = Registry.byId("dz.marw")!!
        val units = Units.of("dz.marw")!!.units
        val tamanrasset = units.single { it.id == "11" }
        // Oran's fitted margins are the band around its seat's unit (ruling R217), still class D.
        val oran = units.single { it.id == "31-seat" }
        // The recorded exceptions alone: the two bands' reaches' measured figures (ruling R270) are ReachLimits'.
        assertEquals(6, assertNotNull(ownLateLimitFor(TimedEvent.ASR, tamanrasset, entry)).minutes)
        // Ruling R265: the real sun's cap puts Tamanrasset's sunrise up to 4 min before the print (an end, the safe side).
        assertEquals(4, assertNotNull(ownLateLimitFor(TimedEvent.SUNRISE, tamanrasset, entry)).minutes)
        for (event in TimedEvent.entries.filter { it != TimedEvent.ASR && it != TimedEvent.SUNRISE }) assertNull(ownLateLimitFor(event, tamanrasset, entry), "$event")
        for (event in TimedEvent.entries) assertNull(ownLateLimitFor(event, oran, entry), "$event")
        assertEquals(EntryClass.D_AUTHORITY, tamanrasset.entryClass)
        assertEquals(EntryClass.D_AUTHORITY, oran.entryClass)
    }

    private val algiersZone = TimeZone.of("Africa/Algiers")
    private val noRamadan = RamadanCalendar { false }

    private fun dz(point: GeoPoint, admin1: String? = null) = Registry.resolve(Place(point.lat, point.lon, "Africa/Algiers", "DZ", admin1))

    /** [km] due west of [point]. */
    private fun west(point: GeoPoint, km: Double) = GeoPoint(point.lat, point.lon - km / (111.32 * kotlin.math.cos(point.lat * kotlin.math.PI / 180)))

    /** The 24 wilayas of Algiers's calendar that carry a fitted seat, and Dellys, a town of Boumerdès (ruling R230). */
    private val fittedSeatIds = listOf(
        "2", "4", "6", "9", "10", "15", "18", "19", "21", "23", "24", "25", "26", "27", "28", "31", "34", "35", "36", "41", "42",
        "43", "44", "48", "35-dellys",
    )

    private fun fittedSeats() = Units.of("dz.marw")!!.units.filter { it.id in fittedSeatIds }.also { assertEquals(25, it.size) }

    private fun wilayaOf(unit: AuthorityUnit) = if (unit.id == "35-dellys") "Boumerdès" else unit.name

    @Test
    fun `a wilaya of algiers's calendar is a fitted unit of its own for twenty km around its seat and claims no checked timetable`() {
        // Rulings R230 and R232: margins of ours over Algiers's method at the seat's point, a published method, measured.
        val units = Units.of("dz.marw")!!.units
        for (unit in fittedSeats()) {
            assertEquals(20.0, unit.radiusKm, unit.name)
            assertEquals(EntryClass.D_AUTHORITY, unit.entryClass, unit.name)
            assertTrue(unit.measured, unit.name)
            val seat = assertNotNull(unit.method, unit.name)
            assertTrue(seat.id.startsWith("dz.marw.seat."), unit.name)
            assertEquals(unit.point, seat.fixedPoint, unit.name)
            assertEquals(FixedPointMode.TABLE, seat.fixedPointMode, unit.name)
            // The method is Algiers's own: its angles, its Asr, its minutes; the margins alone are the seat's.
            assertEquals(Maghreb.algeriaMethod.copy(id = seat.id, margins = seat.margins, endOfEatingMarginSeconds = seat.endOfEatingMarginSeconds, fixedPoint = seat.fixedPoint, fixedPointMode = seat.fixedPointMode), seat, unit.name)
            // Ruling R219: by its region alone, at the seat and 19 km out.
            for (r in listOf(dz(unit.point, wilayaOf(unit)), dz(west(unit.point, 19.0), wilayaOf(unit)))) {
                assertEquals(unit.id, r.unitId, unit.name)
                assertEquals(EntryClass.D_AUTHORITY, r.entryClass, unit.name)
                assertEquals(AboutTemplate.AUTHORITY_UNCHECKED, r.about, unit.name)
                assertTrue(r.measured, unit.name)
                assertEquals(unit.point, assertNotNull(r.method).fixedPoint, unit.name)
                assertEquals(FixedPointMode.TABLE, r.method!!.fixedPointMode, unit.name)
            }
        }
        // The seats of the calendar's own base cities and of the other calendars' wilayas are no fitted seats.
        for (id in listOf("16", "17", "1", "11", "30")) assertFalse(units.single { it.id == id }.method?.id.orEmpty().startsWith("dz.marw.seat."), id)
    }

    @Test
    fun `a fitted seat's margins leave every start at least as late as algiers's own`() {
        // Ruling R231: the fit only ever adds to a start. (An end's margin has no such rule to hold here: it is fitted
        // against the place's own table, which only the gate reads.)
        val base = Maghreb.algeriaMethod.margins
        for (unit in fittedSeats()) {
            val m = assertNotNull(unit.method).margins
            assertTrue(m.fajr >= base.fajr && m.dhuhr >= base.dhuhr && m.asr >= base.asr && m.maghrib >= base.maghrib && m.isha >= base.isha, unit.name)
        }
    }

    /** The units fitted on the calendar's days alone: the 25 seats, Oran's band and Tamanrasset's (ruling R231 as revised). */
    private fun unitsOfTheHeldDays() = fittedSeats() + Units.of("dz.marw")!!.units.filter { it.id == "31-seat" || it.id == "11" }

    @Test
    fun `a unit fitted on the calendar's days is measured through its last day and every other unit through none`() {
        // Ruling R231 as revised.
        val held = unitsOfTheHeldDays().also { assertEquals(27, it.size) }
        for (unit in Units.of("dz.marw")!!.units) {
            assertEquals(if (unit in held) Maghreb.lastHeldDay else null, unit.measuredThrough, unit.id)
        }
        for (unit in held) assertTrue(unit.measured, unit.id)
    }

    @Test
    fun `after the last held day a fitted place is a whole minute to the safe side and claims no figure`() {
        // Ruling R231 as revised: the margins are of one calendar's days. Through its last day the day is the fitted day
        // and the figure stands; after it every start is a minute later, sunrise and the end of eating a minute earlier,
        // and no figure is claimed, the class and the wording as they were.
        val last = Maghreb.lastHeldDay
        val through = listOf(LocalDate(2026, 6, 16), LocalDate(2026, 12, 21), LocalDate(2027, 2, 8), last)
        val after = listOf(last.plus(1, DateTimeUnit.DAY), LocalDate(2027, 6, 21), LocalDate(2027, 12, 21), LocalDate(2029, 3, 20))
        for (unit in unitsOfTheHeldDays()) {
            // Oran's band is its seat where the region is not known, and the rest of its wilaya (Arzew) where it is.
            val places = if (unit.id == "31-seat") {
                listOf(Place(unit.point.lat, unit.point.lon, "Africa/Algiers", "DZ"), Place(35.85, -0.3167, "Africa/Algiers", "DZ", "Oran"))
            } else {
                listOf(unit.point, west(unit.point, 12.0)).map { Place(it.lat, it.lon, "Africa/Algiers", "DZ", wilayaOf(unit)) }
            }
            for (place in places) {
                val point = GeoPoint(place.lat, place.lon)
                val r = Registry.resolve(place)
                assertEquals(unit.id, r.unitId, unit.id)
                assertEquals(last, r.measuredThrough, unit.id)
                val method = assertNotNull(r.method, unit.id)
                // The fitted day alone: the days that ride on every date (the place's own sky), and none that begins later.
                val fitted = method.copy(alongside = method.alongside.filter { it.after == null })
                assertEquals(1, method.alongside.size - fitted.alongside.size, unit.id)
                for (on in through) {
                    assertEquals(r, r.on(on), "${unit.id} $on")
                    val day = PrayerEngine.dayTimes(place, on, EngineSettings())
                    assertTrue(day.effective.measured, "${unit.id} $on")
                    assertEquals(DayComputer.compute(fitted, point, on, algiersZone, AsrSchool.STANDARD, noRamadan), DayComputer.compute(method, point, on, algiersZone, AsrSchool.STANDARD, noRamadan), "${unit.id} $on")
                }
                for (on in after) {
                    val beyond = r.on(on)
                    assertFalse(beyond.measured, "${unit.id} $on")
                    assertEquals(r.copy(measured = false), beyond, "${unit.id} $on")
                    assertEquals(EntryClass.D_AUTHORITY, beyond.entryClass, "${unit.id} $on")
                    assertEquals(AboutTemplate.AUTHORITY_UNCHECKED, beyond.about, "${unit.id} $on")
                    val day = PrayerEngine.dayTimes(place, on, EngineSettings())
                    assertFalse(day.effective.measured, "${unit.id} $on")
                    assertFalse(day.resolution.measured, "${unit.id} $on")
                    assertEquals(EntryClass.D_AUTHORITY, day.effective.entryClass, "${unit.id} $on")
                    for (school in AsrSchool.entries) {
                        // The unit's own day with nothing beside it, the fitted day (the place's own sky beside it, at a
                        // seat) and the day shown: each start the later of the fitted day's and the unit's own a whole
                        // minute on, sunrise and the end of eating the earlier of the fitted day's and its own a minute back.
                        val bare = DayComputer.compute(method.copy(alongside = emptyList()), point, on, algiersZone, school, noRamadan)
                        val was = DayComputer.compute(fitted, point, on, algiersZone, school, noRamadan)
                        val shown = DayComputer.compute(method, point, on, algiersZone, school, noRamadan)
                        val where = "${unit.id} $on $school $point"
                        assertEquals(maxOf(was.fajr, bare.fajr + 1.minutes), shown.fajr, "$where Fajr")
                        assertEquals(maxOf(was.dhuhr, bare.dhuhr + 1.minutes), shown.dhuhr, "$where Dhuhr")
                        assertEquals(maxOf(was.asr, bare.asr + 1.minutes), shown.asr, "$where Asr")
                        assertEquals(maxOf(was.maghrib, bare.maghrib + 1.minutes), shown.maghrib, "$where Maghrib")
                        assertEquals(maxOf(was.isha, bare.isha + 1.minutes), shown.isha, "$where Isha")
                        assertEquals(minOf(was.sunrise, bare.sunrise - 1.minutes), shown.sunrise, "$where sunrise")
                        assertEquals(minOf(was.endOfEating, bare.endOfEating - 1.minutes), shown.endOfEating, "$where end of eating")
                    }
                }
            }
        }
    }

    @Test
    fun `a day alongside that begins after a date rides on no day up to it and a unit not measured is measured through no date`() {
        val last = Maghreb.lastHeldDay
        val point = GeoPoint(36.7538, 3.0588)
        val always = Alongside(point, Maghreb.algeriaMethod)
        val later = Alongside(point, Maghreb.algeriaMethod, after = last)
        for (on in listOf(LocalDate(2026, 6, 16), last, last.plus(1, DateTimeUnit.DAY), LocalDate(2030, 1, 1))) {
            assertTrue(always.rides(on), "$on")
            assertEquals(on > last, later.rides(on), "$on")
        }
        assertFailsWith<IllegalArgumentException> { AuthorityUnit("x", "X", point, 20.0, measured = false, measuredThrough = last) }
    }

    @Test
    fun `a checked base city and an unmeasured rest of a wilaya keep their figure on every date`() {
        // Ruling R231 as revised touches only the units fitted on the calendar's days. Ruling R275 (the second audit's
        // G9) is another matter: past its unit's last checked run Algiers is shown as the published method, as its page
        // is held then, with its figure, name and unit unchanged.
        val units = Units.of("dz.marw")!!.units
        val constantine = units.single { it.id == "25" }.point
        val places = listOf(
            Place(36.7538, 3.0588, "Africa/Algiers", "DZ", "Algiers"),
            west(constantine, 30.0).let { Place(it.lat, it.lon, "Africa/Algiers", "DZ", "Constantine") },
        )
        for (place in places) {
            val r = Registry.resolve(place)
            assertNull(r.measuredThrough, r.unitId)
            assertTrue(assertNotNull(r.method).alongside.none { it.after != null }, r.unitId)
            for (on in listOf(LocalDate(2026, 12, 1), Maghreb.lastHeldDay.plus(1, DateTimeUnit.DAY), LocalDate(2028, 6, 6))) {
                val shown = r.on(on)
                val checked = r.entryClass != EntryClass.A && r.entryClass != EntryClass.B || CheckedDays.covers(r.entry.id, r.unitId, on)
                val expected = if (checked) r else r.copy(entryClass = EntryClass.D_AUTHORITY, about = AboutTemplate.AUTHORITY_UNCHECKED, checkedClass = r.entryClass)
                assertEquals(expected, shown, "${r.unitId} $on")
                assertEquals(r.measured, PrayerEngine.dayTimes(place, on, EngineSettings()).effective.measured, "${r.unitId} $on")
            }
        }
    }

    @Test
    fun `without its region no place is handed a fitted seat`() {
        // Ruling R219: the nearest fitted seat can be a neighbouring wilaya's, whose table is not the place's own.
        val units = Units.of("dz.marw")!!.units
        for (unit in fittedSeats()) {
            for (point in listOf(unit.point, west(unit.point, 10.0), west(unit.point, -10.0))) {
                for (r in listOf(dz(point), dz(point, "Nowhere"))) {
                    assertEquals("dz.marw", r.entry.id, unit.name)
                    assertFalse(assertNotNull(r.method, unit.name).id.startsWith("dz.marw.seat."), unit.name)
                    assertEquals(EntryClass.D_AUTHORITY, r.entryClass, unit.name)
                    // Computed at a seat, as before the fitted units: the user's point is never left out of it.
                    assertEquals(r.unitPoint, r.method!!.fixedPoint, unit.name)
                }
            }
        }
        // Oran's seat without a region is its fitted band, as before.
        assertEquals("31-seat", dz(units.single { it.id == "31" }.point).unitId)
        // Rouiba is in Algiers and nearer Boumerdès's seat: Boumerdès's fit is not Rouiba's.
        val rouiba = GeoPoint(36.738, 3.281)
        assertFalse(assertNotNull(dz(rouiba).method).id.startsWith("dz.marw.seat."))
        assertEquals("16", dz(rouiba, "Algiers").unitId)
    }

    @Test
    fun `beyond twenty km a wilaya stays as it was and oran's fitted band stays around its seat`() {
        val units = Units.of("dz.marw")!!.units
        val constantine = units.single { it.id == "25" }.point
        val far = dz(west(constantine, 30.0), admin1 = "Constantine")
        assertEquals("25-rest", far.unitId)
        assertEquals("Constantine", far.unitName)
        assertEquals(EntryClass.D_AUTHORITY, far.entryClass)
        assertFalse(far.measured)
        assertEquals("dz.marw.wilaya", assertNotNull(far.method).id)
        assertEquals(constantine, far.method!!.fixedPoint)

        val oran = units.single { it.id == "31" }.point
        assertEquals("31", dz(oran, admin1 = "Oran").unitId)
        // Arzew, 33 km from the seat: the fitted Oran margins as before, measured, class D.
        val arzew = dz(GeoPoint(35.85, -0.3167), admin1 = "Oran")
        assertEquals("31-seat", arzew.unitId)
        assertEquals("dz.marw.oran", assertNotNull(arzew.method).id)
        assertTrue(arzew.measured)
        assertEquals(EntryClass.D_AUTHORITY, arzew.entryClass)
        // Dellys, a town of Boumerdès the calendar gives a line of its own: its own unit inside the wilaya.
        val dellys = units.single { it.id == "35-dellys" }.point
        assertEquals("35-dellys", dz(dellys, admin1 = "Boumerdes").unitId)
        assertEquals("35", dz(units.single { it.id == "35" }.point, admin1 = "Boumerdes").unitId)
    }

    @Test
    fun `a fitted wilaya's day is its seat's fitted day and never before the place's own sky`() {
        // Ruling R232. The user's own signs with Algiers's margins and none of the Ministry's minutes; Asr the later of
        // its model's and the exact moment's. A start is the later of the seat's and the place's; an end the earlier.
        val ownSun = Maghreb.algeriaMethod.copy(authorityMinutes = EventOffsets())
        val ownExactAsr = ownSun.copy(asrModel = AsrModel.EXACT_MOMENT)
        val dates = listOf(
            LocalDate(2026, 12, 21), LocalDate(2027, 2, 8), LocalDate(2027, 3, 20), LocalDate(2027, 6, 21), LocalDate(2027, 9, 23),
            LocalDate(2028, 1, 15),
        )
        for (unit in fittedSeats()) {
            val seat = assertNotNull(unit.method)
            for (on in dates) {
                // After the last held day the seat's day is a whole minute to the safe side (ruling R231 as revised).
                val wider = if (on > Maghreb.lastHeldDay) 1.minutes else 0.minutes
                for (school in AsrSchool.entries) {
                    val fitted = DayComputer.compute(seat, unit.point, on, algiersZone, school, noRamadan)
                    val table = fitted.copy(
                        fajr = fitted.fajr + wider, dhuhr = fitted.dhuhr + wider, asr = fitted.asr + wider, maghrib = fitted.maghrib + wider,
                        isha = fitted.isha + wider, sunrise = fitted.sunrise - wider, endOfEating = fitted.endOfEating - wider,
                    )
                    for (point in listOf(unit.point, west(unit.point, 19.0), GeoPoint(unit.point.lat - 0.17, unit.point.lon))) {
                        val r = dz(point, wilayaOf(unit))
                        assertEquals(unit.id, r.unitId, unit.name)
                        val shown = DayComputer.compute(assertNotNull(r.method), point, on, algiersZone, school, noRamadan)
                        val own = DayComputer.compute(ownSun, point, on, algiersZone, school, noRamadan)
                        val exactAsr = DayComputer.compute(ownExactAsr, point, on, algiersZone, school, noRamadan).asr
                        val where = "${unit.name} $on $school $point"
                        assertEquals(maxOf(table.fajr, own.fajr), shown.fajr, "$where Fajr")
                        assertEquals(maxOf(table.dhuhr, own.dhuhr), shown.dhuhr, "$where Dhuhr")
                        assertEquals(maxOf(table.asr, own.asr, exactAsr), shown.asr, "$where Asr")
                        assertEquals(maxOf(table.maghrib, own.maghrib), shown.maghrib, "$where Maghrib")
                        assertEquals(maxOf(table.isha, own.isha), shown.isha, "$where Isha")
                        assertTrue(shown.sunrise <= table.sunrise, "$where sunrise")
                        assertTrue(shown.endOfEating <= table.endOfEating, "$where end of eating")
                    }
                }
            }
        }
    }

    @Test
    fun `every habous place is a unit on its own horizon and the national sunrise and maghrib margins are the edge's alone`() {
        val entry = Registry.byId("ma.habous")!!
        val set = Units.of("ma.habous")!!
        val units = set.units
        assertEquals(HabousCities.cities.size, units.size)
        assertEquals(units.size, units.map { it.id }.toSet().size)
        assertEquals(HabousCities.cities.size, HabousCities.cities.map { it.ville }.toSet().size)
        // Beyond every unit the elevation is not known: the two recorded exceptions, nothing else.
        val limits = TimedEvent.entries.associateWith { lateLimitFor(it, null, entry)?.minutes }
        assertEquals(setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB), limits.filterValues { it != null }.keys)
        assertTrue(limits[TimedEvent.SUNRISE]!! > 2 && limits[TimedEvent.MAGHRIB]!! > 2)
        val base = entry.method!!
        val edge = set.outside(GeoPoint(31.21673, -8.23184))
        assertEquals(base.horizonDeg, edge.horizonDeg)
        assertEquals(base.margins.sunrise to base.margins.maghrib, edge.margins.sunrise to edge.margins.maghrib)
        // A place beyond every unit is not at Habous's point either: a minute or more past the fitted margin on every
        // other start, and the end of eating a minute or more before the fitted one and never past the plain safe end.
        val starts = listOf<(EventOffsets) -> Int>({ it.fajr }, { it.dhuhr }, { it.asr }, { it.isha })
        for (start in starts) assertTrue(start(edge.margins) >= start(base.margins) + 60)
        assertTrue(edge.endOfEatingMarginSeconds <= minOf(base.endOfEatingMarginSeconds - 60, SAFE_END))
        for (city in HabousCities.cities) {
            val unit = units.single { it.id == city.id }
            val own = assertNotNull(unit.method, unit.id)
            // The unit sits at Habous's point, or at the town with Habous's point read besides.
            assertEquals(city.town ?: city.point, unit.point, unit.id)
            assertEquals(city.town?.let { city.point }, own.fixedPoint, unit.id)
            assertEquals(FixedPointMode.BOTH, own.fixedPointMode, unit.id)
            // Its own table gives its sunrise and Maghrib: the class's two minutes, and more only where another place's
            // table can ride beside it (the two reaches meet) or Habous prints the place twice; never the edge's limit.
            val ridden = unit.id == "agoudal" ||
                units.any { it.id != unit.id && distanceKm(unit.point, it.point) <= unit.radiusKm + it.radiusKm }
            // Its recorded limits (ruling R199); its reach's measured figures (ruling R270, ReachLimits) stand beside them.
            val limit = ownLateLimitFor(TimedEvent.SUNRISE, unit, entry)!!.minutes
            assertEquals(limit, ownLateLimitFor(TimedEvent.MAGHRIB, unit, entry)!!.minutes, unit.id)
            assertTrue(limit >= 2 && limit < minOf(limits[TimedEvent.SUNRISE]!!, limits[TimedEvent.MAGHRIB]!!), unit.id)
            if (limit > 2) assertTrue(ridden, unit.id)
            for (event in TimedEvent.entries - setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB)) {
                val other = ownLateLimitFor(event, unit, entry) ?: continue
                assertTrue(ridden && other.minutes > 2, "${unit.id} $event")
            }
            // Its horizon is the plain one or below it, read to a tenth of a degree.
            assertTrue(city.dip in 0.0..2.8, unit.id)
            assertEquals(base.horizonDeg - city.dip, own.horizonDeg, 1e-9, unit.id)
            // On that horizon sunrise is never later than computed and Maghrib never before it: the day's own floor
            // holds a Maghrib to the method's sunset, so a negative margin would only show as lateness.
            assertTrue(city.sunrise <= 0 && city.maghrib >= 0, unit.id)
            // A safety past the fit: twice as much where one autumn alone is held.
            val safety = if (city.seasons) 20 else 40
            assertTrue(own.margins.sunrise <= city.sunrise - safety, unit.id)
            assertTrue(own.margins.maghrib >= city.maghrib + safety, unit.id)
            // One autumn also widens the other prayers by one figure, the end of eating as much the other way.
            val extra = starts.map { it(own.margins) - it(base.margins) }
            assertEquals(1, extra.toSet().size, unit.id)
            assertEquals(base.endOfEatingMarginSeconds - extra.first(), own.endOfEatingMarginSeconds, unit.id)
            if (city.seasons) assertEquals(0, extra.first(), unit.id) else assertTrue(extra.first() >= 10, unit.id)
            // And its table is read to the north and to the south of its point as well, as far each way, so that the
            // fitted latitude may be that far out in a season no held page covers; the nine cities held in other seasons
            // are read at their point alone.
            if (city.seasons) {
                assertTrue(own.envelopePoints.isEmpty(), unit.id)
            } else {
                assertEquals(2, own.envelopePoints.size, unit.id)
                val (north, south) = own.envelopePoints.sortedByDescending { it.lat }
                assertTrue(own.envelopePoints.all { it.lon == city.point.lon }, unit.id)
                assertEquals(north.lat - city.point.lat, city.point.lat - south.lat, 1e-9, unit.id)
                assertTrue(north.lat - city.point.lat >= 0.05 - 1e-9, unit.id)
            }
            // And nearer than the edge's margins, which cover the highest place at midsummer.
            assertTrue(own.margins.maghrib < edge.margins.maghrib && own.margins.sunrise > edge.margins.sunrise, unit.id)
        }
        assertEquals(9, HabousCities.cities.count { it.seasons })
    }

    @Test
    fun `every other habous table whose reach holds a place rides there as its own day`() {
        val entry = Registry.byId("ma.habous")!!
        val units = Units.of("ma.habous")!!.units
        fun pointOf(unit: AuthorityUnit) = unit.method!!.fixedPoint ?: unit.point
        fun check(at: GeoPoint, label: String): Pair<String?, Set<GeoPoint>> {
            val r = Registry.resolveEntry(entry, Place(at.lat, at.lon, "Africa/Casablanca", "MA"))
            val holding = units.filter { distanceKm(at, it.point) <= it.radiusKm }
            val holder = holding.minByOrNull { distanceKm(at, it.point) }
            assertEquals(holder?.id, r.unitId, label)
            // Every other table that holds the place, however near the place's own point is (ruling R198): beyond
            // every table none.
            val riders = holding.filter { it.id != holder?.id }
            val riding = r.method!!.alongside
            assertEquals(riders.map(::pointOf).toSet(), riding.map { it.point }.toSet(), label)
            assertEquals(riders.map { it.method!!.id }.toSet(), riding.map { it.method.id }.toSet(), label)
            // A rider is another table's whole day at its own point, on its own horizon and margins.
            for (rider in riding) {
                val from = riders.single { it.method!!.id == rider.method.id }.method!!
                assertNull(rider.method.fixedPoint, label)
                assertEquals(from.horizonDeg, rider.method.horizonDeg, label)
                assertEquals(from.margins, rider.method.margins, label)
                assertEquals(from.envelopePoints, rider.method.envelopePoints, label)
            }
            return r.unitId to riding.map { it.point }.toSet()
        }
        var atOwnPoint = 0
        var pairs = 0
        for (unit in units) {
            // At a place's own point too: a higher place's table 20 km away is as much a local table there.
            val (id, riding) = check(unit.point, unit.id)
            if (id == unit.id && riding.isNotEmpty()) atOwnPoint++
            val other = units.filter { it.id != unit.id }.minByOrNull { distanceKm(unit.point, it.point) }!!
            val apart = distanceKm(unit.point, other.point)
            fun toward(share: Double) = GeoPoint(
                unit.point.lat + (other.point.lat - unit.point.lat) * share,
                unit.point.lon + (other.point.lon - unit.point.lon) * share,
            )
            check(toward(0.1), "${unit.id} near")
            if (apart in 10.0..40.0) {
                val line = check(toward(0.45), "${unit.id} line")
                if (line.first == unit.id) {
                    assertTrue(pointOf(other) in line.second, "${unit.id} line")
                    pairs++
                }
            }
        }
        assertTrue(atOwnPoint > 0)
        assertTrue(pairs > 0)
        // Beyond every table none rides (check holds it, wherever this point falls).
        check(GeoPoint(29.9, -5.1), "beyond")
    }

    @Test
    fun `beyond every habous table no start is earlier and no end later than any place's own table gives at its point`() {
        val entry = Registry.byId("ma.habous")!!
        val base = entry.method!!
        val zone = TimeZone.of("Africa/Casablanca")
        val units = Units.of("ma.habous")!!.units
        // The edge's margins are the entry's own, with no unit's horizon: held here against every unit's own method
        // (its horizon, margins, safety and the latitudes it is read at) at the point Habous computes it for, through
        // the year. A place beyond every unit is assumed no higher than the highest place Habous prints for.
        val dates = (1..12).flatMap { listOf(LocalDate(2027, it, 6), LocalDate(2027, it, 21)) }
        for (city in HabousCities.cities) {
            val unit = units.single { it.id == city.id }
            val at = city.point
            val here = Registry.resolveEntry(entry, Place(at.lat, at.lon, zone.id, "MA"))
            val own = here.copy(method = Registry.methodInUnit(entry.id, base, unit, at, zone.id).copy(alongside = emptyList()))
            val beyond = here.copy(method = Registry.methodBeyondUnits(entry.id, base, at, zone.id))
            for (date in dates) {
                val table = DayPipeline.day(own, date, zone)
                val edge = DayPipeline.day(beyond, date, zone)
                val label = "${unit.id} $date"
                assertTrue(edge.fajr >= table.fajr, "$label fajr")
                assertTrue(edge.sunrise <= table.sunrise, "$label sunrise")
                assertTrue(edge.dhuhr >= table.dhuhr, "$label dhuhr")
                assertTrue(edge.asr >= table.asr, "$label asr")
                assertTrue(edge.maghrib >= table.maghrib, "$label maghrib")
                assertTrue(edge.isha >= table.isha, "$label isha")
                assertTrue(edge.endOfEating <= table.endOfEating, "$label end of eating")
            }
        }
    }

    /**
     * Ruling R261 (the second audit's G6, 14 Nov 2026): the app places Ceuta and Melilla in Spain, and Habous prints a
     * table for each (Sebta, ville 24; Melilla, ville 40). Within 10 km of either table's point that table's own day, read
     * as a Habous place's (its point, horizon and margins, never a unit of Morocco's), rides beside Spain's safe default:
     * sunrise and the end of eating never after it, no start before it, and nothing earlier than Spain's day alone. The
     * place keeps Spain's entry, class and measure; the mainland and Gibraltar are untouched.
     */
    @Test
    fun `ceuta and melilla keep spain's day with habous's own sebta and melilla tables beside it`() {
        val madrid = Registry.resolve(Place(40.4168, -3.7038, "Europe/Madrid", "ES"))
        assertTrue(assertNotNull(madrid.method).alongside.isEmpty())
        assertEquals(2, HabousCities.spain.size)
        assertTrue(HabousCities.spain.none { s -> HabousCities.cities.any { it.ville == s.ville } }, "Sebta and Melilla are no units of Morocco's")
        for ((at, zoneId, ville) in listOf(
            Triple(GeoPoint(35.88919, -5.32042), "Europe/Madrid", 24),
            Triple(GeoPoint(35.29369, -2.93833), "Africa/Ceuta", 40),
        )) {
            val zone = TimeZone.of(zoneId)
            val r = Registry.resolve(Place(at.lat, at.lon, zoneId, "ES"))
            assertEquals("default.safe.europe", r.entry.id)
            assertNull(r.unitId)
            assertEquals(madrid.entryClass, r.entryClass)
            assertEquals(madrid.measured, r.measured)
            val table = Maghreb.spainHabousTables.single { it.id == HabousCities.spain.single { c -> c.ville == ville }.id }
            val m = assertNotNull(r.method)
            assertEquals(madrid.method!!.margins, m.margins)
            val beside = m.alongside.single()
            assertEquals(table.point, beside.point)
            assertEquals(table.method!!.horizonDeg, beside.method.horizonDeg)
            assertEquals(table.method!!.margins, beside.method.margins)
            val alone = r.copy(method = m.copy(alongside = emptyList()))
            val habous = r.copy(point = table.point, method = Registry.methodAlone("default.safe.europe", table.method!!, table.point, zoneId))
            var date = LocalDate(2027, 1, 1)
            while (date.year == 2027) {
                val shown = DayPipeline.unended(r, date, zone)
                for (floor in listOf(DayPipeline.unended(alone, date, zone), DayPipeline.unended(habous, date, zone))) {
                    assertTrue(shown.fajr >= floor.fajr && shown.dhuhr >= floor.dhuhr && shown.asr >= floor.asr, "$ville $date")
                    assertTrue(shown.maghrib >= floor.maghrib && shown.isha >= floor.isha, "$ville $date")
                    assertTrue(shown.sunrise <= floor.sunrise && shown.endOfEating <= floor.endOfEating, "$ville $date")
                }
                date = date.plus(3, DateTimeUnit.DAY)
            }
        }
        // Across the strait, and Gibraltar, nothing rides.
        for ((at, cc) in listOf(GeoPoint(36.0128, -5.6045) to "ES", GeoPoint(36.1408, -5.3536) to "GI", GeoPoint(36.1408, -5.4562) to "ES")) {
            val r = Registry.resolve(Place(at.lat, at.lon, "Europe/Madrid", cc))
            assertTrue(assertNotNull(r.method).alongside.isEmpty(), "$at")
        }
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
