package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.astro.altitudeTime
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.app.prayer.engine.registry.authorities.TwilightCurves
import world.taqwa.app.prayer.engine.registry.data.DiyanetBesideDistricts
import world.taqwa.app.prayer.engine.registry.data.EndOfEatingDawns
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** EMB's summer Fajr floor at Brussels, minutes after 00:00 UTC (TwilightCurves' own constant, Task 7g). */
private const val EMB_FAJR_FLOOR_UTC_MINUTES = 84

/**
 * The registry's answer for the spec's example places (task 5, step 1). Coordinates are the app's own
 * (cities.csv) unless the brief names a point.
 */
class RegistryTest {

    private fun resolve(lat: Double, lon: Double, zone: String, country: String) =
        Registry.resolve(Place(lat, lon, zone, country))

    private fun TimetableMethod.ishaDegrees(): Double = (isha as IshaRule.Angle).degrees

    private fun near(expected: Double, actual: Double, label: String) = near(expected, actual, 1e-9, label)

    private fun near(expected: Double, actual: Double, tolerance: Double, label: String) =
        assertTrue(abs(expected - actual) < tolerance, "$label: expected $expected, got $actual")

    // The brief's examples, one by one.

    @Test
    fun `makkah follows umm al qura as a class a authority`() {
        val r = resolve(21.4225, 39.8262, "Asia/Riyadh", "SA")
        assertEquals("sa.ummalqura", r.entry.id)
        assertEquals(EntryClass.A, r.entryClass)
        assertEquals(AboutTemplate.AUTHORITY_CHECKED, r.about)
        assertTrue(r.saudi)
        assertFalse(r.shiaRegion)
        assertTrue(r.measured)
        assertEquals(AsrSchool.STANDARD, r.entry.school)
    }

    @Test
    fun `istanbul follows diyanet at its own district point`() {
        val r = resolve(41.0082, 28.9784, "Europe/Istanbul", "TR")
        assertEquals("tr.diyanet", r.entry.id)
        assertEquals("İstanbul", r.unitName)
        near(41.012, assertNotNull(r.unitPoint).lat, "lat")
        near(28.974, r.unitPoint.lon, "lon")
        // Ruling R15: the district point rides as the method's fixed point, beside the user's own.
        assertEquals(r.unitPoint, assertNotNull(r.method).fixedPoint)
        near(41.0082, r.point.lat, "user lat")
        assertTrue(r.measured)
        assertEquals(EntryClass.A, r.entryClass)
    }

    @Test
    fun `a unit's fixed point makes starts later and ends earlier for a user west of it`() {
        val zone = TimeZone.of("Europe/Istanbul")
        val date = LocalDate(2026, 9, 26)
        val atPoint = resolve(41.012, 28.974, "Europe/Istanbul", "TR")
        val west = resolve(41.012, 28.965, "Europe/Istanbul", "TR")
        assertEquals("İstanbul", west.unitName)
        val a = DayComputer.compute(atPoint.method!!, atPoint.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
        val b = DayComputer.compute(west.method!!, west.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
        assertTrue(b.maghrib >= a.maghrib && b.fajr >= a.fajr)
        assertTrue(b.sunrise <= a.sunrise && b.endOfEating <= a.endOfEating)
    }

    @Test
    fun `istanbul ankara and jakarta reach as far as their class's late limit east to west`() {
        // Ruling R40: a minute of time is a quarter degree of longitude; A allows 1 min, B 2.
        near(20.98, Units.of("tr.diyanet")!!.unit("9541").radiusKm, 0.01, "İstanbul km")
        near(21.31, Units.of("tr.diyanet")!!.unit("9206").radiusKm, 0.01, "Ankara km")
        // The app's own city points are inside again.
        assertEquals("İstanbul", resolve(41.01384, 28.94966, "Europe/Istanbul", "TR").unitName)
        assertEquals("Ankara", resolve(39.91987, 32.85427, "Europe/Istanbul", "TR").unitName)
        val jakarta = resolve(-6.21462, 106.84513, "Asia/Jakarta", "ID")
        assertEquals("Kota Jakarta", jakarta.unitName)
        assertTrue(jakarta.measured)
        // Beyond the reach the entry's edge applies: Esenyurt (25 km west), 60 km east of Jakarta.
        val esenyurt = resolve(41.02697, 28.67732, "Europe/Istanbul", "TR")
        assertNull(esenyurt.unitName)
        assertEquals(EntryClass.D_AUTHORITY, esenyurt.entryClass)
        // Rulings R103 and R106: beyond Kota Jakarta's 8 km core (DKI's own boundary) the edge, with its allowance for
        // an unknown seat, replaces R46's reach unit: Depok and Bekasi, which print their own tables, never get
        // Jakarta's. Since the round of 12 Nov 2026 each city's own point is its own kota's unit (KemenagKabkota).
        for ((lat, lon, own) in listOf(Triple(-6.4, 106.81861, "Kota Depok"), Triple(-6.2349, 106.9896, "Kota Bekasi"))) {
            val r = resolve(lat, lon, "Asia/Jakarta", "ID")
            assertEquals(own, r.unitName)
            assertEquals(GeoPoint(lat, lon), assertNotNull(r.method).fixedPoint)
            assertTrue(r.measured)
            assertEquals(EntryClass.B, r.entryClass)
        }
        near(-1.0, jakarta.method!!.horizonDeg, "core horizon")
        val eastOfJakarta = resolve(-6.21462, 107.39, "Asia/Jakarta", "ID")
        assertNull(eastOfJakarta.unitName)
        near(-2.0, assertNotNull(eastOfJakarta.method).horizonDeg, "edge horizon")
        // At the edge of the reach the unit's point is at most the class's minute later than the user's own.
        val zone = TimeZone.of("Europe/Istanbul")
        val date = LocalDate(2026, 3, 20)
        val east = resolve(41.012, 28.974 + 0.25 * 0.99, "Europe/Istanbul", "TR") // a quarter degree is a minute
        assertEquals("İstanbul", east.unitName)
        val withUnit = DayComputer.compute(east.method!!, east.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
        val ownOnly = DayComputer.compute(east.method!!.copy(fixedPoint = null), east.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
        assertTrue(withUnit.dhuhr - ownOnly.dhuhr <= kotlin.time.Duration.parse("1m"), "${withUnit.dhuhr} vs ${ownOnly.dhuhr}")
    }

    @Test
    fun `a turkish district outside the checked ones is diyanet's method at the user's point`() {
        // Alanya, 130 km from Antalya's point (every il's centre has been a unit since 13 Nov 2026, ruling R200).
        val r = resolve(36.54375, 31.99982, "Europe/Istanbul", "TR")
        assertEquals("tr.diyanet", r.entry.id)
        assertNull(r.unitName)
        assertFalse(r.measured)
        assertEquals(EntryClass.D_AUTHORITY, r.entryClass)
        assertEquals(AboutTemplate.AUTHORITY_UNCHECKED, r.about)
        near(36.54375, r.point.lat, "lat")
        val method = assertNotNull(r.method)
        assertEquals(60, method.margins.fajr)
        assertEquals(-60, method.margins.sunrise)
    }

    @Test
    fun `the nine districts of 12 november 2026 are units at the app's points and the edge beyond 15 km`() {
        // Ruling R140: İzmir, Bursa, Antalya, Konya, Gaziantep, Diyarbakır and İstanbul's Çatalca, Şile and Silivri,
        // each at the app's own point for the city (cities.csv), class A and measured there, the point riding as the
        // fixed point (R15); 16 km south, beyond the 15 km reach, the edge as before.
        val nine = listOf(
            Triple("9560", "İzmir", GeoPoint(38.41273, 27.13838)),
            Triple("9335", "Bursa", GeoPoint(40.19559, 29.06013)),
            Triple("9225", "Antalya", GeoPoint(36.90812, 30.69556)),
            Triple("9676", "Konya", GeoPoint(37.87135, 32.48464)),
            Triple("9479", "Gaziantep", GeoPoint(37.05944, 37.3825)),
            Triple("9402", "Diyarbakır", GeoPoint(37.91363, 40.21721)),
            Triple("9538", "Çatalca", GeoPoint(41.14073, 28.45967)),
            Triple("9547", "Şile", GeoPoint(41.17891, 29.61085)),
            Triple("9548", "Silivri", GeoPoint(41.07393, 28.24644)),
        )
        for ((id, name, point) in nine) {
            val r = resolve(point.lat, point.lon, "Europe/Istanbul", "TR")
            assertEquals("tr.diyanet", r.entry.id, name)
            assertEquals(id, r.unitId, name)
            assertEquals(name, r.unitName)
            assertEquals(point, r.unitPoint, name)
            assertEquals(point, assertNotNull(r.method).fixedPoint, name)
            assertTrue(r.measured, name)
            assertEquals(EntryClass.A, r.entryClass, name)
            assertEquals(15.0, Units.of("tr.diyanet")!!.unit(id).radiusKm, name)
            val beyond = resolve(point.lat - 16.0 / 111.19, point.lon, "Europe/Istanbul", "TR")
            assertNull(beyond.unitName, "$name 16 km south")
            assertEquals(EntryClass.D_AUTHORITY, beyond.entryClass, "$name 16 km south")
            assertEquals(60, assertNotNull(beyond.method).margins.fajr, "$name 16 km south")
        }
        // Çatalca's and Silivri's reaches overlap (their points are 19 km apart): the nearer unit holds the place and
        // the other's point rides beside it (R142), so no start is before either table nor any end after it.
        val catalca = GeoPoint(41.14073, 28.45967)
        val silivri = GeoPoint(41.07393, 28.24644)
        val nearSilivri = resolve(41.09, 28.33, "Europe/Istanbul", "TR")
        assertEquals("Silivri", nearSilivri.unitName)
        assertEquals(listOf(catalca), assertNotNull(nearSilivri.method).envelopePoints)
        val nearCatalca = resolve(41.12, 28.40, "Europe/Istanbul", "TR")
        assertEquals("Çatalca", nearCatalca.unitName)
        assertEquals(listOf(silivri), assertNotNull(nearCatalca.method).envelopePoints)
        // Beyond the other's reach, the unit's point alone.
        assertEquals(emptyList<GeoPoint>(), assertNotNull(resolve(41.07393, 28.24644, "Europe/Istanbul", "TR").method).envelopePoints)
        // Esenyurt, 25 km west of İstanbul's point and 22 km from Çatalca's, is still the edge.
        assertNull(resolve(41.02697, 28.67732, "Europe/Istanbul", "TR").unitName)
    }

    @Test
    fun `the districts beside the nine ride beside them near their border and nowhere else`() {
        // Ruling R171: Güzelbahçe's table rides beside İzmir's on İzmir's western rim, not at İzmir's own point.
        val units = Units.of("tr.diyanet")!!
        val rim = resolve(38.37422, 26.97942, "Europe/Istanbul", "TR")
        assertEquals("9560", rim.unitId)
        assertEquals(listOf(GeoPoint(38.35, 26.88)), assertNotNull(rim.method).envelopePoints)
        assertEquals(emptyList<GeoPoint>(), assertNotNull(resolve(38.41273, 27.13838, "Europe/Istanbul", "TR").method).envelopePoints)
        // Aksu's rides on Antalya's eastern side, but stops 2 km short of Antalya's point (13.9 km from Aksu's).
        assertEquals(emptyList<GeoPoint>(), assertNotNull(resolve(36.90812, 30.69556, "Europe/Istanbul", "TR").method).envelopePoints)
        val nearAksu = resolve(36.94, 30.80, "Europe/Istanbul", "TR")
        assertEquals("9225", nearAksu.unitId)
        assertEquals(listOf(GeoPoint(36.95, 30.84)), assertNotNull(nearAksu.method).envelopePoints)
        // Konya's Selçuklu, Meram and Karatay share its town and ride across it.
        assertEquals(
            setOf(GeoPoint(37.90, 32.47), GeoPoint(37.88, 32.47), GeoPoint(37.88, 32.49)),
            assertNotNull(resolve(37.87135, 32.48464, "Europe/Istanbul", "TR").method).envelopePoints.toSet(),
        )
        // The districts are no units: Menemen's own town, 23 km from İzmir's point, is still the edge.
        assertNull(resolve(38.60754, 27.06938, "Europe/Istanbul", "TR").unitName)
        assertTrue(Diyanet.besideDistricts("9560").none { units.units.any { u -> u.id == it.id } })
        // Their minutes after a table are each unit's recorded exceptions, as the gate measured them and no figure of
        // the code's: İzmir's and Bursa's beside their districts, and Şile's, with no district within reach, across
        // its own reach alone (ruling R255).
        assertTrue(Diyanet.besideDistricts("9547").isEmpty())
        for (id in listOf("9560", "9335", "9547")) {
            assertEquals(DiyanetBesideDistricts.late[id].orEmpty(), units.unit(id).lateLimits.associate { it.minutes to it.events }, id)
        }
    }

    @Test
    fun `every il's centre is a diyanet unit at its own point`() {
        // Ruling R200: the 81 il centres and İstanbul's Çatalca, Şile and Silivri, each a class A unit measured on its
        // own table, its point riding as the fixed point (R15) and no two sharing an IlceID or a point.
        val units = Units.of("tr.diyanet")!!.units
        assertEquals(84, units.size)
        assertEquals(units.size, units.map { it.id }.toSet().size)
        assertEquals(units.size, units.map { it.point }.toSet().size)
        for (unit in units) {
            val r = resolve(unit.point.lat, unit.point.lon, "Europe/Istanbul", "TR")
            assertEquals("tr.diyanet", r.entry.id, unit.name)
            assertEquals(unit.id, r.unitId, unit.name)
            assertEquals(unit.point, assertNotNull(r.method).fixedPoint, unit.name)
            assertTrue(r.measured, unit.name)
            assertEquals(EntryClass.A, r.entryClass, unit.name)
            // A reach is 15 km, Adana's 20, and İstanbul's and Ankara's their class's minute east to west (R40).
            assertTrue(unit.radiusKm in 15.0..21.5, "${unit.name} ${unit.radiusKm}")
        }
    }

    @Test
    fun `the districts beside every unit ride near their own point and stop short of the unit's`() {
        // Ruling R201: a district with a table of its own whose point is within a unit's reach and 15 km more (or
        // whose own town is inside the reach, R204) rides beside that unit, within 15 km of its own point, and its
        // disc stops 2 km short of the unit's point unless the two share a town (5 km, or by name, R204): there the
        // unit's table alone is the town's. No such district is a unit.
        val units = Units.of("tr.diyanet")!!
        val ids = units.units.map { it.id }.toSet()
        val towns = DiyanetBesideDistricts.districts.associate { it.id to it.town }
        var pairs = 0
        for (unit in units.units) {
            val atUnit = assertNotNull(resolve(unit.point.lat, unit.point.lon, "Europe/Istanbul", "TR").method).envelopePoints
            val shared = mutableListOf<GeoPoint>()
            for (d in Diyanet.besideDistricts(unit.id)) {
                pairs++
                val km = distanceKm(unit.point, d.point)
                assertFalse(d.id in ids, "${d.name} beside ${unit.name}")
                val townInside = towns[d.id]?.let { distanceKm(unit.point, it) <= unit.radiusKm } ?: false
                assertTrue(km <= unit.radiusKm + 15.0 || townInside, "${d.name} beside ${unit.name}: $km km")
                assertTrue(d.radiusKm <= 15.0, "${d.name} beside ${unit.name}")
                if (km <= 5.0 || d.id in DiyanetBesideDistricts.sharedTown[unit.id].orEmpty()) {
                    shared += d.point
                    assertEquals(15.0, d.radiusKm, "${d.name} beside ${unit.name}")
                } else {
                    assertTrue(d.radiusKm <= km - 2.0 + 1e-9, "${d.name} beside ${unit.name}: ${d.radiusKm} of $km km")
                    assertFalse(d.point in atUnit, "${d.name} at ${unit.name}'s own point")
                }
                // Where the unit holds the district's own point, the district's point rides there.
                val own = resolve(d.point.lat, d.point.lon, "Europe/Istanbul", "TR")
                if (own.unitId == unit.id) assertTrue(d.point in assertNotNull(own.method).envelopePoints, "${d.name} in ${unit.name}")
            }
            assertTrue(atUnit.containsAll(shared), "${unit.name}'s own town")
            // More than the class's minute is allowed only as the gate measured it, across the unit's reach and beside
            // the districts and units that ride there (rulings R201 and R255): each limit is the unit's recorded one,
            // never a figure of the code's, and no event stands under two limits.
            assertTrue(unit.lateLimits.all { it.minutes > 1 }, unit.name)
            assertEquals(DiyanetBesideDistricts.late[unit.id].orEmpty(), unit.lateLimits.associate { it.minutes to it.events }, unit.name)
            assertEquals(unit.lateLimits.sumOf { it.events.size }, unit.lateLimits.flatMap { it.events }.toSet().size, unit.name)
        }
        assertTrue(pairs > 0)
        // İstanbul and Ankara are held to the same rule: Küçükçekmece's point rides in İstanbul's west.
        val west = resolve(41.0, 28.76, "Europe/Istanbul", "TR")
        assertEquals("9541", west.unitId)
        assertTrue(Diyanet.besideDistricts("9541").first { it.name == "Küçükçekmece" }.point in assertNotNull(west.method).envelopePoints)
    }

    @Test
    fun `a district rides wherever the user is nearer to its point or its town than to the unit's`() {
        // Ruling R204: the disc of R201 stops 2 km short of the unit's point on every side, so it left a district's far
        // side and flanks on the unit's table alone. Wherever a place the unit holds is within 15 km of the district's
        // point and nearer to it than to the unit's, the district rides; only the unit's own town stays clear (R203).
        val units = Units.of("tr.diyanet")!!
        var checked = 0
        for (unit in units.units) {
            val clear = Diyanet.towns[unit.id]
            for (d in Diyanet.besideDistricts(unit.id)) {
                val dLat = unit.point.lat - d.point.lat
                val dLon = unit.point.lon - d.point.lon
                val places = listOf(
                    // Just on the district's side of half way, beyond its point, and to its north and south.
                    GeoPoint(d.point.lat + 0.45 * dLat, d.point.lon + 0.45 * dLon),
                    GeoPoint(d.point.lat - 0.1 * dLat, d.point.lon - 0.1 * dLon),
                    GeoPoint(d.point.lat + 0.06, d.point.lon),
                    GeoPoint(d.point.lat - 0.06, d.point.lon),
                )
                for (at in places) {
                    val toDistrict = distanceKm(at, d.point)
                    if (toDistrict > 15.0 || toDistrict >= distanceKm(at, unit.point)) continue
                    if (clear != null && distanceKm(at, clear) <= 2.0) continue
                    val r = resolve(at.lat, at.lon, "Europe/Istanbul", "TR")
                    if (r.unitId != unit.id) continue
                    checked++
                    assertTrue(d.point in assertNotNull(r.method).envelopePoints, "${d.name} beside ${unit.name} at $at")
                }
            }
        }
        assertTrue(checked > 100, "$checked places")
        // A district's own town where it lies away from the fitted point: every one the unit holds has the district's
        // table riding (Atakum's town is 16 km from the point its table fits and 5 km from Samsun's).
        var towns = 0
        for (district in DiyanetBesideDistricts.districts) {
            val town = district.town ?: continue
            assertTrue(distanceKm(town, district.point) > 2.0, district.name)
            val r = resolve(town.lat, town.lon, "Europe/Istanbul", "TR")
            val unit = units.units.firstOrNull { it.id == r.unitId } ?: continue
            if (distanceKm(town, unit.point) <= 2.0) continue
            towns++
            assertTrue(district.point in assertNotNull(r.method).envelopePoints, "${district.name}'s town in ${unit.name}")
        }
        assertTrue(towns > 0)
        // A district that shares its unit's town by name rides across it, at the unit's own point too.
        for ((unitId, shared) in DiyanetBesideDistricts.sharedTown) {
            val unit = units.unit(unitId)
            val atUnit = assertNotNull(resolve(unit.point.lat, unit.point.lon, "Europe/Istanbul", "TR").method).envelopePoints
            val points = Diyanet.besideDistricts(unitId).filter { it.id in shared }.map { it.point }
            assertEquals(shared.size, points.size, unit.name)
            assertTrue(atUnit.containsAll(points), unit.name)
        }
    }

    @Test
    fun `in a unit's own town as the app places it no neighbouring district rides`() {
        // Ruling R203: a unit that stands at Diyanet's computing point names the app's own point for its city, and
        // within 2 km of that point the unit's table rides alone, as within 2 km of the unit's own point (R171).
        val units = Units.of("tr.diyanet")!!
        assertTrue(Diyanet.towns.isNotEmpty())
        for ((id, town) in Diyanet.towns) {
            val unit = units.unit(id)
            // The same town, another point: a unit at the app's point needs no entry.
            assertTrue(distanceKm(unit.point, town) > 0.05 && distanceKm(unit.point, town) <= 5.0, unit.name)
            val neighbours = Diyanet.besideDistricts(id)
                .filter { distanceKm(unit.point, it.point) > 5.0 && it.id !in DiyanetBesideDistricts.sharedTown[id].orEmpty() }
                .map { it.point }
            // The town's point and a kilometre and a half from it to the north, south, east and west.
            for ((dLat, dLon) in listOf(0.0 to 0.0, 0.0135 to 0.0, -0.0135 to 0.0, 0.0 to 0.017, 0.0 to -0.017)) {
                val at = GeoPoint(town.lat + dLat, town.lon + dLon)
                assertTrue(distanceKm(at, town) <= 2.0, unit.name)
                val r = resolve(at.lat, at.lon, "Europe/Istanbul", "TR")
                assertEquals(id, r.unitId, unit.name)
                assertTrue(assertNotNull(r.method).envelopePoints.none { it in neighbours }, "${unit.name} at $at")
            }
        }
        // Beyond the town the neighbours ride as everywhere: Başakşehir's table 3 km west of the app's İstanbul.
        val istanbul = Diyanet.towns.getValue("9541")
        val west = resolve(istanbul.lat, istanbul.lon - 0.036, "Europe/Istanbul", "TR")
        assertEquals("9541", west.unitId)
        assertTrue(Diyanet.besideDistricts("9541").first { it.name == "Başakşehir" }.point in assertNotNull(west.method).envelopePoints)
    }

    @Test
    fun `kuala lumpur is jakim zone wly01 with two start points`() {
        val r = resolve(3.1412, 101.68653, "Asia/Kuala_Lumpur", "MY")
        assertEquals("my.jakim", r.entry.id)
        assertEquals("WLY01", r.unitName)
        assertEquals(2, assertNotNull(r.method).startPoints.size)
        assertTrue(r.measured)
    }

    @Test
    fun `cameron highlands carries its own lowered horizon`() {
        val r = resolve(4.4721, 101.3801, "Asia/Kuala_Lumpur", "MY")
        assertEquals("PHG06", r.unitName)
        near(-2.5, assertNotNull(r.method).horizonDeg, "horizon")
    }

    @Test
    fun `a malaysian place outside the fitted zones is later than any fitted zone`() {
        // Selangau, Sarawak: a city of the app's list no zone's list of districts names, beyond every listed town's
        // reach (JakimZoneCities.unattached; Kapit, here until the zone round of 13 Nov 2026, is SWK04's town now).
        val selangau = resolve(2.52477, 112.32543, "Asia/Kuching", "MY")
        assertEquals("my.jakim", selangau.entry.id)
        assertNull(selangau.unitId)
        assertFalse(selangau.measured)
        assertEquals(EntryClass.D_AUTHORITY, selangau.entryClass)
        val edge = assertNotNull(selangau.method)
        for (unit in Units.of("my.jakim")!!.units) {
            val m = unit.method ?: continue
            assertTrue(edge.margins.fajr >= m.margins.fajr, "${unit.id} fajr")
            assertTrue(edge.margins.dhuhr >= m.margins.dhuhr, "${unit.id} dhuhr")
            assertTrue(edge.margins.isha >= m.margins.isha, "${unit.id} isha")
        }
    }

    @Test
    fun `jakarta is kemenag with the lowland horizon`() {
        val r = resolve(-6.21462, 106.84513, "Asia/Jakarta", "ID")
        assertEquals("id.kemenag", r.entry.id)
        near(-1.0, assertNotNull(r.method).horizonDeg, "horizon")
        assertTrue(r.measured)
        assertTrue("id.muhammadiyah" in r.entry.nearby)
    }

    @Test
    fun `bogor is kemenag with the highland horizon`() {
        val r = resolve(-6.59444, 106.78917, "Asia/Jakarta", "ID")
        assertEquals("id.kemenag", r.entry.id)
        near(-2.0, assertNotNull(r.method).horizonDeg, "horizon")
        assertTrue(r.measured)
    }

    @Test
    fun `a kemenag place far from the fitted units takes the deepest horizon and claims nothing`() {
        // Timika (Kab. Mimika), whose app point lies about 25 km from the regency seat's: no unit of its own.
        val r = resolve(-4.61407, 136.67679, "Asia/Jayapura", "ID")
        assertEquals("id.kemenag", r.entry.id)
        near(-2.0, assertNotNull(r.method).horizonDeg, "horizon")
        assertFalse(r.measured)
        assertNull(r.unitName)
        // Merauke, the far place this test held before the second round of 12 Nov 2026, is now its regency's own unit.
        val merauke = resolve(-8.49958, 140.40613, "Asia/Jayapura", "ID")
        assertEquals("Kab. Merauke", merauke.unitName)
        assertTrue(merauke.measured)
        assertEquals(EntryClass.B, merauke.entryClass)
    }

    @Test
    fun `medan palembang semarang surabaya and yogyakarta are fitted kemenag kota at the lowland horizon`() {
        // Monitor round (29 Sep 2026): five more kota at the app's own points, each carrying its point as the fixed point.
        val kota = listOf(
            Triple("Kota Medan", 3.58333, 98.66667),
            Triple("Kota Palembang", -2.91673, 104.7458),
            Triple("Kota Semarang", -6.99306, 110.42083),
            Triple("Kota Surabaya", -7.24917, 112.75083),
            Triple("Kota Yogyakarta", -7.80139, 110.36472),
        )
        for ((name, lat, lon) in kota) {
            val r = resolve(lat, lon, "Asia/Jakarta", "ID")
            assertEquals("id.kemenag", r.entry.id)
            assertEquals(name, r.unitName)
            assertEquals(EntryClass.B, r.entryClass)
            assertTrue(r.measured, name)
            val method = assertNotNull(r.method)
            near(-1.0, method.horizonDeg, "$name horizon")
            assertEquals(GeoPoint(lat, lon), method.fixedPoint, name)
            // Muhammadiyah's units are Kemenag's, so the five are its too.
            assertEquals(name, Registry.resolveEntry(Registry.byId("id.muhammadiyah")!!, Place(lat, lon, "Asia/Jakarta", "ID")).unitName)
        }
        // Beyond a kota's radius the edge keeps the deepest plausible horizon and claims nothing: Belawan, Medan's port, 22 km north.
        val belawan = resolve(3.78, 98.68, "Asia/Jakarta", "ID")
        assertNull(belawan.unitName)
        near(-2.0, assertNotNull(belawan.method).horizonDeg, "edge horizon")
        assertFalse(belawan.measured)
        assertEquals(EntryClass.D_AUTHORITY, belawan.entryClass)
    }

    @Test
    fun `a kemenag unit reaches only its own kab or kota and every neighbouring place keeps the edge or its own unit`() {
        // Ruling R103: Kemenag prints one table per kabupaten/kota and a user follows their own, so a unit's circle
        // stays inside its own boundary. The app's own places in the neighbouring kabupaten resolve to the edge.
        val neighbours = listOf(
            Triple("Kasihan (Bantul)", -7.82694, 110.32917),
            Triple("Gamping Lor (Sleman)", -7.79556, 110.32639),
            Triple("Melati (Sleman)", -7.73333, 110.36667),
            Triple("Deli Tua (Deli Serdang)", 3.5078, 98.6839),
            Triple("Sunggal (Kota Medan, beyond its circle)", 3.5765, 98.6151),
            Triple("Mranggen (Demak)", -7.0268, 110.5158),
            Triple("Kamal (Bangkalan)", -7.16778, 112.71917),
            Triple("Paseh (Kab. Bandung)", -7.068, 107.794),
            Triple("Margahayukencana (Kab. Bandung)", -6.97083, 107.5675),
            Triple("Dalung (Badung)", -8.62553, 115.17042),
            Triple("Kuta (Badung)", -8.72332, 115.17234),
            Triple("Batubulan (Gianyar)", -8.62379, 115.26203),
        )
        for ((name, lat, lon) in neighbours) {
            val zone = if (lon > 114.0) "Asia/Makassar" else "Asia/Jakarta"
            val r = resolve(lat, lon, zone, "ID")
            assertEquals("id.kemenag", r.entry.id)
            assertOwnUnitOrEdge(name, r)
        }
        // Ruling R106: the app's own places in the former Jabodetabek reach (beyond DKI's 8 km core, within 55 km of
        // Jakarta's point) take the edge, never a Jakarta unit; Kota Bogor's own point keeps its own unit.
        val reach = listOf(
            Triple("Utan (Jakarta Timur)", -6.17694, 106.94667), Triple("Bekasi", -6.2349, 106.9896),
            Triple("Cikarang (Kab. Bekasi)", -6.26111, 107.15278), Triple("Depok", -6.4, 106.81861),
            Triple("Sawangan (Depok)", -6.40278, 106.77444), Triple("Cibinong (Kab. Bogor)", -6.48167, 106.85417),
            Triple("Citeureup (Kab. Bogor)", -6.48556, 106.88194), Triple("Cileungsir (Kab. Bogor)", -6.39472, 106.95917),
            Triple("Parung (Kab. Bogor)", -6.42139, 106.73306), Triple("Ciampea (Kab. Bogor)", -6.55472, 106.70083),
            Triple("Caringin (Kab. Bogor)", -6.70611, 106.82139), Triple("Tangerang", -6.17806, 106.63),
            Triple("Ciputat", -6.2375, 106.69556), Triple("South Tangerang", -6.28862, 106.71789),
            Triple("Pamulang (Tangerang Selatan)", -6.34278, 106.73833), Triple("Serpong (Tangerang Selatan)", -6.31694, 106.66417),
            Triple("Cikupa (Kab. Tangerang)", -6.23639, 106.50833), Triple("Curug (Kab. Tangerang)", -6.26583, 106.55639),
            Triple("Pasarkemis (Kab. Tangerang)", -6.17028, 106.53028), Triple("Sepatan (Kab. Tangerang)", -6.11889, 106.575),
            Triple("Teluknaga (Kab. Tangerang)", -6.09889, 106.63806), Triple("Kresek (Kab. Tangerang)", -6.13139, 106.37972),
            Triple("Karawang", -6.30525, 107.3197), Triple("Rengasdengklok (Karawang)", -6.15917, 107.29806),
        )
        for ((name, lat, lon) in reach) {
            val r = resolve(lat, lon, "Asia/Jakarta", "ID")
            assertNotEquals("Kota Jakarta", r.unitName, name)
            assertOwnUnitOrEdge(name, r)
        }
        assertEquals("Kota Bogor", resolve(-6.59444, 106.78917, "Asia/Jakarta", "ID").unitName)
        assertNull(Units.of("id.kemenag")!!.units.firstOrNull { it.id == "1301-reach" }, "R46's reach unit is closed (R106)")
        // Each unit's own point resolves to its unit, rounded to three decimals as the app rounds every location
        // (ruling R113: a radius is never below 0.1 km, and the rounding moves a point at most about 0.08 km).
        for (unit in Units.of("id.kemenag")!!.units) {
            val zone = when {
                unit.point.lon > 130.0 -> "Asia/Jayapura"
                unit.point.lon > 114.0 -> "Asia/Makassar"
                else -> "Asia/Jakarta"
            }
            fun e3(degrees: Double) = kotlin.math.round(degrees * 1000.0) / 1000.0
            assertEquals(unit.name, resolve(e3(unit.point.lat), e3(unit.point.lon), zone, "ID").unitName, "${unit.id} at three decimals")
            assertTrue(unit.radiusKm >= 0.1, "${unit.id} radius ${unit.radiusKm}")
        }
    }

    /**
     * The app's places pinned beside a Kemenag unit's boundary that lie inside their own kab/kota's unit of the round
     * of 12 Nov 2026 (each circle inside its own kab/kota, rulings R103/R113): the place keeps its own table, never a
     * neighbour's. Every other pinned place keeps the edge.
     */
    private val ownKabkota = mapOf<String, String>(
        "Melati (Sleman)" to "Kab. Sleman",
        "Bekasi" to "Kota Bekasi",
        "Cikarang (Kab. Bekasi)" to "Kab. Bekasi",
        "Depok" to "Kota Depok",
        "Cibinong (Kab. Bogor)" to "Kab. Bogor",
        "Tangerang" to "Kota Tangerang",
        "South Tangerang" to "Kota Tangerang Selatan",
        "Karawang" to "Kab. Karawang",
    )

    private fun assertOwnUnitOrEdge(name: String, r: Resolution) {
        val own = ownKabkota[name]
        assertEquals(own, r.unitName, name)
        if (own == null) {
            assertNull(assertNotNull(r.method).fixedPoint, name)
            near(-2.0, r.method!!.horizonDeg, "$name horizon")
            assertFalse(r.measured, name)
            assertEquals(EntryClass.D_AUTHORITY, r.entryClass, name)
        } else {
            assertTrue(r.measured, name)
            assertEquals(EntryClass.B, r.entryClass, name)
            assertEquals(Units.of("id.kemenag")!!.units.first { it.name == own }.point, r.method!!.fixedPoint, name)
        }
    }

    @Test
    fun `london inside the m25 follows london unified alone`() {
        val r = resolve(51.5074, -0.1278, "Europe/London", "GB")
        assertEquals("gb.london.lupt", r.entry.id)
        val method = assertNotNull(r.method)
        assertEquals(366, method.fajrAngleByDayOfYear?.size)
        assertEquals(366, method.ishaAngleByDayOfYear?.size)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        assertFalse(r.entry.schoolKnown)
    }

    @Test
    fun `manchester takes cautious times over the uk members`() {
        val r = resolve(53.48095, -2.23743, "Europe/London", "GB")
        assertEquals("gb.cautious", r.entry.id)
        assertEquals(EntryClass.C, r.entryClass)
        assertEquals(AboutTemplate.CAUTIOUS, r.about)
        assertNull(r.method)
        val ids = r.entry.members.map { it.id }
        assertEquals("gb.wifaqul", ids.first())
        assertTrue("other.mwl" in ids)
        // The UK's 15°, Karachi and late-dawn families are conventions (Task 7g, research-uk), not named timetables.
        assertEquals(listOf("gb.wifaqul", "gb.fifteen", "other.mwl", "gb.karachi", "gb.latedawn"), ids)
        assertEquals(ids - setOf("gb.fifteen", "gb.karachi", "gb.latedawn"), r.entry.nearby)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        assertFalse(r.entry.schoolKnown)
    }

    @Test
    fun `france belgium the netherlands and germany take cautious times over their surveyed members`() {
        // Ruling R87 (research-mawaqit, ContinentalCautiousTest): Diyanet joins France; the conventions (France's
        // families, the Dutch Moroccan calendar, Germany's late-dawn and 18° families) are not named timetables.
        class Case(val lat: Double, val lon: Double, val zone: String, val country: String, val id: String, val members: List<String>, val nearby: List<String>)
        val cases = listOf(
            Case(45.7640, 4.8357, "Europe/Paris", "FR", "fr.cautious", listOf("fr.twelve", "fr.gmp", "fr.fifteen", "tr.diyanet.europe"), listOf("fr.gmp", "tr.diyanet.europe")),
            Case(50.8467, 4.3525, "Europe/Brussels", "BE", "be.cautious", listOf("be.emb", "tr.diyanet.europe"), listOf("be.emb", "tr.diyanet.europe")),
            Case(52.37403, 4.88969, "Europe/Amsterdam", "NL", "nl.cautious", listOf("nl.moroccan", "tr.diyanet.europe", "other.mwl"), listOf("tr.diyanet.europe", "other.mwl")),
            Case(52.52437, 13.41053, "Europe/Berlin", "DE", "de.cautious", listOf("tr.diyanet.europe", "de.vikz", "de.latedawn", "de.eighteen"), listOf("tr.diyanet.europe", "de.vikz")),
        )
        for (c in cases) {
            val r = resolve(c.lat, c.lon, c.zone, c.country)
            assertEquals(c.id, r.entry.id)
            assertEquals(EntryClass.C, r.entryClass, c.id)
            assertEquals(c.members, r.entry.members.map { it.id }, c.id)
            assertEquals(c.members.indices.map { it + 1 }, r.entry.members.map { it.shareRank }, c.id)
            assertEquals(c.nearby, r.entry.nearby, c.id)
        }
    }

    @Test
    fun `toronto takes cautious times over its three mosques`() {
        val r = resolve(43.70643, -79.39864, "America/Toronto", "CA")
        assertEquals("ca.toronto", r.entry.id)
        assertEquals(EntryClass.C, r.entryClass)
        assertEquals(listOf("ca.ift", "ca.iit", "ca.mac"), r.entry.members.map { it.id })
        assertEquals(listOf(1, 2, 3), r.entry.members.map { it.shareRank })
        assertFalse(r.entry.schoolKnown)
    }

    @Test
    fun `toronto's members carry their mosques' points across the gta`() {
        val points = mapOf(
            "ca.ift" to GeoPoint(43.7980, -79.2417),
            "ca.iit" to GeoPoint(43.8187, -79.2298),
            "ca.mac" to GeoPoint(43.6555, -79.3858),
        )
        for ((lat, lon) in listOf(43.5890 to -79.6441, 43.6834 to -79.7663)) { // Mississauga, Brampton
            val r = resolve(lat, lon, "America/Toronto", "CA")
            assertEquals("ca.toronto", r.entry.id)
            for (member in r.members) assertEquals(points[member.id], member.method.fixedPoint, member.id)
            // Chosen alone in the GTA, a member keeps its point too; far away it is the user's own.
            assertEquals(points["ca.ift"], Registry.resolveEntry(Registry.byId("ca.ift")!!, Place(lat, lon, "America/Toronto", "CA")).method!!.fixedPoint)
        }
        assertNull(Registry.resolveEntry(Registry.byId("ca.ift")!!, Place(49.2827, -123.1207, "America/Vancouver", "CA")).method!!.fixedPoint)
    }

    @Test
    fun `every table printed for one point carries that point within its reach`() {
        // Ruling R30 extended: Dublin's two tables, Lakemba's and Cape Town's MJC and community calendar.
        val cases = listOf(
            Triple("ie.ifi", GeoPoint(53.3498, -6.2603), Place(53.2707, -6.1413, "Europe/Dublin", "IE")), // Dún Laoghaire
            Triple("ie.icci", GeoPoint(53.3498, -6.2603), Place(53.2707, -6.1413, "Europe/Dublin", "IE")),
            Triple("au.lma", GeoPoint(-33.92, 151.0756), Place(-33.8150, 151.0011, "Australia/Sydney", "AU")), // Parramatta
            Triple("za.mjc", GeoPoint(-33.92584, 18.42322), Place(-33.9346, 18.8668, "Africa/Johannesburg", "ZA")), // Stellenbosch
            Triple("za.voc", GeoPoint(-33.92584, 18.42322), Place(-34.0518, 18.6180, "Africa/Johannesburg", "ZA")), // Mitchells Plain
        )
        for ((id, point, near) in cases) {
            assertEquals(point, Registry.resolveEntry(Registry.byId(id)!!, near).method!!.fixedPoint, id)
            // Far away (a class D reach is three minutes east to west) it is the user's own point alone.
            val far = Place(near.lat, near.lon + 5.0, near.zoneId, near.countryCode)
            assertNull(Registry.resolveEntry(Registry.byId(id)!!, far).method!!.fixedPoint, id)
        }
        // As cautious members too.
        val dublin = resolve(53.2707, -6.1413, "Europe/Dublin", "IE")
        for (member in dublin.members) assertEquals(GeoPoint(53.3498, -6.2603), member.method.fixedPoint, member.id)
        val lakemba = resolve(-33.8150, 151.0011, "Australia/Sydney", "AU").members.first { it.id == "au.lma" }
        assertEquals(GeoPoint(-33.92, 151.0756), lakemba.method.fixedPoint)
        val cape = resolve(-33.9346, 18.8668, "Africa/Johannesburg", "ZA")
        for (member in cape.members) assertNotNull(member.method.fixedPoint, member.id)
    }

    @Test
    fun `cape town's jamiat member is its cape town table wherever in the metro`() {
        val zone = TimeZone.of("Africa/Johannesburg")
        val date = LocalDate(2026, 12, 21)
        val tablePoint = GeoPoint(-(33 + 56 / 60.0), 18 + 28 / 60.0)
        for ((lat, lon) in listOf(-33.9346 to 18.8668, -34.0518 to 18.6180)) { // Stellenbosch, Mitchells Plain
            val r = resolve(lat, lon, "Africa/Johannesburg", "ZA")
            assertEquals("za.cape", r.entry.id)
            val jamiat = r.members.first { it.id == "za.jamiat.shafii" }.method
            assertEquals(tablePoint, jamiat.fixedPoint)
            val here = DayComputer.compute(jamiat, r.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
            val table = DayComputer.compute(jamiat.copy(fixedPoint = null), tablePoint, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
            assertTrue(here.dhuhr >= table.dhuhr && here.maghrib >= table.maghrib && here.fajr >= table.fajr, "starts at ($lat, $lon)")
            assertTrue(here.sunrise <= table.sunrise && here.endOfEating <= table.endOfEating, "ends at ($lat, $lon)")
        }
    }

    @Test
    fun `ottawa and montreal follow isna`() {
        for ((lat, lon) in listOf(45.41117 to -75.69812, 45.50884 to -73.58781)) {
            val r = resolve(lat, lon, "America/Toronto", "CA")
            assertEquals("ca.isna", r.entry.id)
            val method = assertNotNull(r.method)
            near(15.0, method.fajrAngle, "fajr")
            near(15.0, method.ishaDegrees(), "isha")
            assertEquals(AsrSchool.STANDARD, r.entry.school)
        }
    }

    @Test
    fun `norway takes irn and diyanet while sweden and its neighbours take diyanet and rabita`() {
        // Ruling R64: IRN's calendar is Norway's, so it is a member there and nowhere else in the Nordics.
        val oslo = resolve(59.91273, 10.74609, "Europe/Oslo", "NO")
        assertEquals("no.cautious", oslo.entry.id)
        assertEquals(EntryClass.C, oslo.entryClass)
        assertEquals(setOf("no.irn", "tr.diyanet.europe"), oslo.members.map { it.id }.toSet())
        val stockholm = resolve(59.32938, 18.06871, "Europe/Stockholm", "SE")
        assertEquals("se.cautious", stockholm.entry.id)
        assertEquals(EntryClass.C, stockholm.entryClass)
        assertEquals(setOf("tr.diyanet.europe", "se.rabita"), stockholm.members.map { it.id }.toSet())
        assertEquals("no.cautious", resolve(78.22322, 15.64689, "Europe/Oslo", "SJ").entry.id) // Longyearbyen
        // Ruling R82: within the reach of IRN's Tromsø calendar Automatic follows IRN's own rule alone, measured.
        val tromso = resolve(69.6492, 18.9553, "Europe/Oslo", "NO")
        assertEquals("no.irn", tromso.entry.id)
        assertEquals("Tromsø", tromso.unitName)
        assertEquals(EntryClass.D_AUTHORITY, tromso.entryClass)
        assertTrue(tromso.measured)
        assertEquals("no.cautious", resolve(69.9689, 23.2716, "Europe/Oslo", "NO").entry.id) // Alta, beyond its reach
        assertEquals("se.cautious", resolve(55.6761, 12.5683, "Europe/Copenhagen", "DK").entry.id)
        assertEquals("se.cautious", resolve(60.1699, 24.9384, "Europe/Helsinki", "FI").entry.id)
    }

    @Test
    fun `a cautious entry is measured only where every member's own placement is`() {
        // Review I3: Bodø is beyond every IRN calendar's reach, where its edge claims nothing; Winnipeg is far
        // from every Canadian family's tables; Perth is beyond Lakemba's reach. Oslo and Toronto have every
        // member's own table.
        val bodo = resolve(67.2804, 14.4049, "Europe/Oslo", "NO")
        assertEquals("no.cautious", bodo.entry.id)
        assertFalse(bodo.measured)
        val winnipeg = resolve(49.8951, -97.1384, "America/Winnipeg", "CA")
        assertEquals("ca.cautious", winnipeg.entry.id)
        assertFalse(winnipeg.measured)
        val perth = resolve(-31.9523, 115.8613, "Australia/Perth", "AU")
        assertEquals("au.cautious", perth.entry.id)
        assertFalse(perth.measured)
        assertTrue(resolve(59.91273, 10.74609, "Europe/Oslo", "NO").measured)
        val toronto = resolve(43.6555, -79.3858, "America/Toronto", "CA")
        assertEquals("ca.toronto", toronto.entry.id)
        assertTrue(toronto.measured)
    }

    @Test
    fun `canada outside toronto montreal and ottawa takes cautious times`() {
        val r = resolve(51.05011, -114.08529, "America/Edmonton", "CA") // Calgary
        assertEquals("ca.cautious", r.entry.id)
        assertEquals(EntryClass.C, r.entryClass)
    }

    @Test
    fun `chicago takes the later of its eighteen degree block and its fifteen degree mosques with hanafi asr`() {
        // Task 7g: of the seven Chicago tables held, three follow 18°/15° and four 15°/15°, all Hanafi.
        val r = resolve(41.85003, -87.65005, "America/Chicago", "US")
        assertEquals("us.chicago", r.entry.id)
        assertEquals(EntryClass.C, r.entryClass)
        val fajr = r.members.associate { it.id to it.method.fajrAngle }
        assertEquals(mapOf("us.isna" to 15.0, "us.chicago.eighteen" to 18.0), fajr)
        for (m in r.members) near(15.0, m.method.ishaDegrees(), "isha")
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        assertTrue(r.entry.schoolKnown)
    }

    @Test
    fun `a table printed for its own point rides beside the user's within a minute's reach and nowhere else`() {
        // The city-point audit of 10–11 Oct 2026 (tablesAtTheirPoints, rulings R30, R40 and R44).
        val houston = resolve(29.76328, -95.36327, "America/Chicago", "US")
        assertEquals("isgh", houston.unitId)
        assertEquals(GeoPoint(29.7369, -95.4251), houston.method?.fixedPoint)
        assertTrue(houston.measured)
        assertNull(houston.unitLabel, "About calls the place by the app's own name, never a mosque's")
        // Phoenix is beyond every mosque's reach: the convention at the user's own point, exactly as before.
        val phoenix = resolve(33.44838, -112.07404, "America/Phoenix", "US")
        assertNull(phoenix.unitId)
        assertFalse(phoenix.measured)
        assertSame(Registry.byId("us.isna")!!.method, phoenix.method)
        // Calgary's own point is within a minute of Calgary Islamic Centre SW: that family's member rides there. The
        // cautious entry is not measured there, as before: the Edmonton families' tables are 280 km away.
        val calgary = resolve(51.05011, -114.08529, "America/Edmonton", "CA")
        assertEquals("ca.cautious", calgary.entry.id)
        assertFalse(calgary.measured)
        assertEquals(GeoPoint(51.0395, -114.1685), calgary.members.single { it.id == "ca.isha90" }.method.fixedPoint)
        assertNull(calgary.members.single { it.id == "ca.eighteen" }.method.fixedPoint)
        // BIK's base table reaches as far as class D: Pejë, 61 km west of it, rides its point; Delhi's table claims nothing.
        assertEquals("base", resolve(42.65913, 20.28828, "Europe/Belgrade", "XK").unitId)
        val delhi = resolve(28.65195, 77.23149, "Asia/Kolkata", "IN")
        assertEquals("delhi", delhi.unitId)
        assertFalse(delhi.measured)
    }

    @Test
    fun `the syrian awqaf's two damascus samples are one table so each unit reads the other's point for its starts`() {
        // The fix round of 11 Oct 2026: with the nearest sample alone, the app's Jaramana (in the Umayyad Mosque's unit, within
        // the imsakiya's reach) came before the imsakiya's Fajr and Isha.
        val imsakiya = GeoPoint(33.5138, 36.2765)
        val umayyad = GeoPoint(33.5116, 36.3064)
        val jaramana = resolve(33.4862, 36.34614, "Asia/Damascus", "SY")
        assertEquals("umayyad-mosque", jaramana.unitId)
        assertEquals(umayyad, jaramana.method?.fixedPoint)
        assertEquals(listOf(imsakiya), jaramana.method?.startPoints)
        val darayya = resolve(33.45835, 36.23256, "Asia/Damascus", "SY")
        assertEquals("damascus", darayya.unitId)
        assertEquals(imsakiya, darayya.method?.fixedPoint)
        assertEquals(listOf(umayyad), darayya.method?.startPoints)
        // Beyond both reaches the Ministry's method at the user's own point, exactly as before the units.
        val homs = resolve(34.72405, 36.72559, "Asia/Damascus", "SY")
        assertNull(homs.unitId)
        assertSame(Registry.byId("sy.awqaf")!!.method, homs.method)
    }

    @Test
    fun `new york follows isna fifteen and fifteen with the standard asr`() {
        val r = resolve(40.71427, -74.00597, "America/New_York", "US")
        assertEquals("us.isna", r.entry.id)
        val method = assertNotNull(r.method)
        near(15.0, method.fajrAngle, "fajr")
        near(15.0, method.ishaDegrees(), "isha")
        assertEquals(AsrSchool.STANDARD, r.entry.school)
        assertEquals(EntryClass.D_AUTHORITY, r.entryClass)
    }

    @Test
    fun `karachi follows the karachi method with the hanafi asr`() {
        val r = resolve(24.8608, 67.0104, "Asia/Karachi", "PK")
        assertEquals("pk.karachi", r.entry.id)
        val method = assertNotNull(r.method)
        near(18.0, method.fajrAngle, "fajr")
        near(18.0, method.ishaDegrees(), "isha")
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        assertFalse(r.shiaRegion)
    }

    @Test
    fun `gilgit is in the shia regions list`() {
        assertTrue(resolve(35.91869, 74.31245, "Asia/Karachi", "PK").shiaRegion)
    }

    @Test
    fun `zahedan is calculated by taqwa with the hanafi asr its sunnis follow`() {
        val r = Registry.resolve(Place(29.4963, 60.8629, "Asia/Tehran", "IR", admin1 = "Sistan and Baluchestan"))
        assertEquals(EntryClass.D_NONE, r.entryClass)
        assertEquals(AboutTemplate.CALCULATED, r.about)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        assertTrue(r.entry.schoolKnown)
        assertTrue(r.shiaRegion)
    }

    @Test
    fun `sanandaj takes the standard asr of kurdistan's shafi'is`() {
        val r = Registry.resolve(Place(35.31495, 46.99883, "Asia/Tehran", "IR", admin1 = "Kurdistan Province"))
        assertEquals(AsrSchool.STANDARD, r.entry.school)
        assertTrue(r.entry.schoolKnown)
        assertTrue(r.shiaRegion)
        // Sonqor, in Kermanshah just east of the old Shafi'i box's edge, is not assumed Shafi'i.
        val sonqor = Registry.resolve(Place(34.78300, 47.60000, "Asia/Tehran", "IR", admin1 = "Kermanshah"))
        assertEquals("ir.default", sonqor.entry.id)
        assertEquals(AsrSchool.HANAFI, sonqor.entry.school)
    }

    @Test
    fun `tehran takes the later asr because its sunni school is not known`() {
        val r = resolve(35.69439, 51.42151, "Asia/Tehran", "IR")
        assertEquals(EntryClass.D_NONE, r.entryClass)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        assertFalse(r.entry.schoolKnown)
        assertTrue(r.shiaRegion)
    }

    @Test
    fun `benghazi stops eating at the nineteen and a half degree dawn its mosques use`() {
        val r = resolve(32.11486, 20.06859, "Africa/Tripoli", "LY")
        assertEquals("ly.awqaf", r.entry.id)
        assertEquals(EndOfEating.DawnAngle(19.5), assertNotNull(r.method).endOfEating)
    }

    @Test
    fun `sabha in the south also stops eating at nineteen and a half degrees`() {
        val r = resolve(27.03766, 14.42832, "Africa/Tripoli", "LY")
        assertEquals(EndOfEating.DawnAngle(19.5), assertNotNull(r.method).endOfEating)
    }

    @Test
    fun `tripoli stops eating at the national dawn`() {
        val r = resolve(32.88743, 13.18733, "Africa/Tripoli", "LY")
        assertEquals("ly.awqaf", r.entry.id)
        assertEquals(EndOfEating.SameAsFajrDawn, assertNotNull(r.method).endOfEating)
        assertEquals("Tripoli", r.unitName)
    }

    @Test
    fun `dammam is saudi and in the eastern province's shia region`() {
        val r = resolve(26.43442, 50.10326, "Asia/Riyadh", "SA")
        assertEquals("sa.ummalqura", r.entry.id)
        assertTrue(r.shiaRegion)
        assertTrue(r.saudi)
    }

    @Test
    fun `tamanrasset and oran's band are measured and stay class d on their fitted seats`() {
        // Arzew, 33 km out of Oran's seat, and the seat itself where the place's region is not known: the fitted band.
        for ((lat, lon) in listOf(35.85 to -0.3167, 35.69906 to -0.63588, 22.785 to 5.52278)) {
            val r = resolve(lat, lon, "Africa/Algiers", "DZ")
            assertEquals("dz.marw", r.entry.id)
            assertTrue(r.measured)
            assertEquals(EntryClass.D_AUTHORITY, r.entryClass)
            assertEquals(AboutTemplate.AUTHORITY_UNCHECKED, r.about)
        }
        // In Oran's wilaya by its region, the seat's own fitted day (rulings R219 and R230): measured, and class D too.
        val oran = Registry.resolve(Place(35.69906, -0.63588, "Africa/Algiers", "DZ", admin1 = "Oran"))
        assertEquals("31", oran.unitId)
        assertEquals(EntryClass.D_AUTHORITY, oran.entryClass)
        assertEquals(AboutTemplate.AUTHORITY_UNCHECKED, oran.about)
        assertTrue(oran.measured)
        val algiers = resolve(36.73225, 3.08746, "Africa/Algiers", "DZ")
        assertEquals(EntryClass.B, algiers.entryClass)
        assertTrue(algiers.measured)
    }

    @Test
    fun `a saharan town follows its own wilaya's seat where the nearest seat is another's`() {
        // Aoulef is in Adrar wilaya, 169 km from Adrar and 141 km from In Salah (the app's city list).
        val known = Registry.resolve(Place(26.96667, 1.08333, "Africa/Algiers", "DZ", admin1 = "Adrar"))
        assertEquals("Adrar", known.unitName)
        assertEquals(known.unitPoint, assertNotNull(known.method).fixedPoint)
        val unknown = resolve(26.96667, 1.08333, "Africa/Algiers", "DZ")
        assertEquals("In Salah", unknown.unitName)
        // Spellings differ between the region names and the seats.
        for ((region, seat) in listOf("Boumerdes" to "Boumerdès", "Medea" to "Médéa", "El Menia" to "El Meniaa", "Tamanghasset" to "Tamanrasset")) {
            assertEquals(seat, Registry.resolve(Place(36.0, 3.0, "Africa/Algiers", "DZ", admin1 = region)).unitName, region)
        }
    }

    @Test
    fun `a brunei district is its region when known`() {
        // Sengkurong is in Brunei-Muara but west of the longitude the bands give to Tutong.
        val known = Registry.resolve(Place(4.88333, 114.83333, "Asia/Brunei", "BN", admin1 = "Brunei-Muara District"))
        assertEquals("Brunei-Muara", known.unitName)
        assertEquals(0, assertNotNull(known.method).authorityMinutes.fajr)
        assertEquals("Tutong", resolve(4.88333, 114.83333, "Asia/Brunei", "BN").unitName)
    }

    @Test
    fun `a brunei district is given only within its own circle`() {
        // Ruling R309, on made-up places. At sea north of Tutong, 63 km from Bandar Seri Begawan, past Brunei-Muara's 60 km:
        // a phone there whose nearest city is in Brunei-Muara is given the nearest district whose circle holds it, Tutong.
        val sea = Registry.resolve(Place(5.33, 114.58, "Asia/Brunei", "BN", admin1 = "Brunei-Muara District"))
        assertEquals("Tutong", sea.unitName)
        assertEquals(1, assertNotNull(sea.method).authorityMinutes.fajr)
        // Past every district's circle: the set's edge, no district and no table point, as beyond Brunei before.
        val far = Registry.resolveEntry(Registry.byId("bn.mora")!!, Place(5.95, 114.90, "Asia/Brunei", "BN", admin1 = "Brunei-Muara District"))
        assertNull(far.unitName)
        assertNull(assertNotNull(far.method).fixedPoint)
        assertFalse(far.measured)
        // Wherever a district is given, by any region or none, its own circle holds the place.
        val units = assertNotNull(Units.of("bn.mora"))
        val regions = listOf(null, "Belait District", "Tutong District", "Brunei-Muara District", "Temburong District")
        var given = 0
        for (i in 0..60) for (j in 0..60) for (region in regions) {
            val place = Place(4.0 + i * 0.04, 113.8 + j * 0.04, "Asia/Brunei", "BN", admin1 = region)
            val unit = units.unitFor(place) ?: continue
            given++
            assertTrue(distanceKm(GeoPoint(place.lat, place.lon), unit.point) <= unit.radiusKm, "${unit.id} at ${place.lat},${place.lon} ($region)")
        }
        assertTrue(given > 1000, "only $given places given a district")
    }

    @Test
    fun `casablanca is its own habous unit`() {
        val r = resolve(33.58831, -7.61138, "Africa/Casablanca", "MA")
        assertEquals("ma.habous", r.entry.id)
        assertEquals("Casablanca", r.unitName)
        assertTrue(r.measured)
    }

    @Test
    fun `brunei's districts are its bandar seri begawan table with their own minutes`() {
        val bsb = GeoPoint(4.89035, 114.94006)
        val cases = listOf(
            Triple(4.58361, 114.2312, "Belait" to 3), // Kuala Belait
            Triple(4.67747, 114.49587, "Belait" to 3), // Liang, nearer Tutong's seat
            Triple(4.80278, 114.64917, "Tutong" to 1),
            Triple(4.89035, 114.94006, "Brunei-Muara" to 0),
            Triple(4.7092, 115.0717, "Temburong" to 0), // Bangar
        )
        for ((lat, lon, expected) in cases) {
            val r = resolve(lat, lon, "Asia/Brunei", "BN")
            assertEquals("bn.mora", r.entry.id)
            assertEquals(expected.first, r.unitName)
            val method = assertNotNull(r.method)
            assertEquals(bsb, method.fixedPoint)
            assertEquals(expected.second, method.authorityMinutes.fajr)
            assertEquals(1 + expected.second, method.authorityMinutes.dhuhr)
            // Task 7c checked Brunei-Muara's own table (class B); the other districts stay unmeasured.
            val checked = expected.first == "Brunei-Muara"
            assertEquals(checked, r.measured, expected.first)
            assertEquals(if (checked) EntryClass.B else EntryClass.D_AUTHORITY, r.entryClass, expected.first)
        }
        val kotaKinabalu = Registry.resolveEntry(Registry.byId("bn.mora")!!, Place(5.9804, 116.0735, "Asia/Kuching", "MY"))
        assertNull(kotaKinabalu.unitName)
        assertNull(assertNotNull(kotaKinabalu.method).fixedPoint)
    }

    @Test
    fun `kazan follows dum rt with its fixed zuhr`() {
        val r = resolve(55.78874, 49.12214, "Europe/Moscow", "RU")
        assertEquals("ru.dumrt", r.entry.id)
        val method = assertNotNull(r.method)
        assertEquals(12 * 60, method.fixedDhuhrLocalMinutes)
        assertTrue(method.highLatitude is HighLatRule.DumRtSummer)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
    }

    @Test
    fun `moscow follows dum rf`() {
        val r = resolve(55.75204, 37.61781, "Europe/Moscow", "RU")
        assertEquals("ru.dumrf", r.entry.id)
        assertTrue(r.measured)
        assertEquals(AsrSchool.STANDARD, r.entry.school)
    }

    @Test
    fun `murmansk is the russia entry with dum rf's own rule for the missing signs`() {
        val r = resolve(68.96778, 33.09922, "Europe/Moscow", "RU")
        assertEquals("ru.dumrf", r.entry.id)
        assertFalse(r.measured)
        assertTrue(assertNotNull(r.method).highLatitude is HighLatRule.NightFraction)
    }

    // Rules in fractions of the night, as curves for the place.

    /** The exact sunrise of [date] and the sunset before it at [p], UTC epoch seconds. */
    private fun sunriseAndLastSunset(p: GeoPoint, date: LocalDate, zone: TimeZone): Pair<Double, Double> {
        fun offset(d: LocalDate) = zone.offsetAt(d.atTime(12, 0).toInstant(zone)).totalSeconds
        val sunrise = SunClock(p.lat, p.lon, date, offset(date), SunModel.EXACT).altitudeTime(-0.8333, morning = true)!!
        val yesterday = date.plus(-1, DateTimeUnit.DAY)
        val lastSunset = SunClock(p.lat, p.lon, yesterday, offset(yesterday), SunModel.EXACT).altitudeTime(-0.8333, morning = false)!!
        return sunrise to lastSunset
    }

    /**
     * [entryId]'s Fajr at [place] on each of [dates] is never before its rule, the moment
     * [beforeSunrise] seconds before the exact sunrise (given the date and the night before), plus
     * the method's own Fajr margin: no tolerance.
     */
    private fun assertFajrKeepsItsRule(entryId: String, place: Place, dates: List<LocalDate>, beforeSunrise: (LocalDate, Double) -> Double) {
        val r = Registry.resolveEntry(Registry.byId(entryId)!!, place)
        val method = assertNotNull(r.method)
        val zone = TimeZone.of(place.zoneId)
        for (date in dates) {
            val day = DayComputer.compute(method, r.point, date, zone, r.entry.school, Registry.ramadanCalendar())
            val (sunrise, lastSunset) = sunriseAndLastSunset(GeoPoint(place.lat, place.lon), date, zone)
            val rule = sunrise - beforeSunrise(date, sunrise - lastSunset)
            assertTrue(
                day.fajr.epochSeconds >= rule + method.margins.fajr,
                "$entryId $date: fajr ${day.fajr} rule ${rule.toLong()} + ${method.margins.fajr} s",
            )
        }
    }

    private fun datesOf(year: Int, month: Int): List<LocalDate> {
        val first = LocalDate(year, month, 1)
        return generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it.month == first.month }.toList()
    }

    private val moscow = Place(55.75204, 37.61781, "Europe/Moscow", "RU")

    @Test
    fun `dum rf's august fajr is never before three tenths of the night before sunrise`() {
        assertFajrKeepsItsRule("ru.dumrf", moscow, listOf(LocalDate(2026, 8, 12))) { _, night -> 0.3 * night }
        val r = Registry.resolve(moscow)
        val zone = TimeZone.of("Europe/Moscow")
        val date = LocalDate(2026, 8, 12)
        val day = DayComputer.compute(assertNotNull(r.method), r.point, date, zone, r.entry.school, Registry.ramadanCalendar())
        val at18 = SunClock(r.point.lat, r.point.lon, date, 3 * 3600, SunModel.EXACT).altitudeTime(-18.0, morning = true)!!
        assertTrue(day.fajr.epochSeconds > at18 + 30 * 60, "the 18° dawn is 43 min earlier that day")
    }

    // Ruling R28: leap years read the curves by month and day, and the neighbour envelope absorbs the drift.

    @Test
    fun `dum rf keeps its rule on every day of august 2028`() {
        assertFajrKeepsItsRule("ru.dumrf", moscow, datesOf(2028, 8)) { _, night -> 0.3 * night }
    }

    @Test
    fun `irn's edge keeps its hour before sunrise on 1 april and 30 september 2028`() {
        // Oslo and Trondheim follow their own calendars' curves (ruling R48); beyond them, as at Bergen,
        // the edge keeps Fajr no earlier than an hour before sunrise from April to September.
        val bergen = Place(60.39299, 5.32415, "Europe/Oslo", "NO")
        assertFajrKeepsItsRule("no.irn", bergen, listOf(LocalDate(2028, 4, 1), LocalDate(2028, 9, 30))) { _, _ -> 3600.0 }
    }

    @Test
    fun `emb keeps its summer rule on 1 may and 31 july 2028`() {
        // Task 7g: the later of 18° and the earlier of EMB's clock-time floor (minutes after 00:00 UTC) and the
        // proportion from 45°.
        val brussels = Place(50.85045, 4.34878, "Europe/Brussels", "BE")
        val zone = TimeZone.of("Europe/Brussels")
        assertFajrKeepsItsRule("be.emb", brussels, listOf(LocalDate(2028, 5, 1), LocalDate(2028, 7, 31))) { date, night ->
            val at45 = GeoPoint(45.0, brussels.lon)
            val (sunrise45, lastSunset45) = sunriseAndLastSunset(at45, date, zone)
            val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
            val fajr45 = SunClock(45.0, brussels.lon, date, offset, SunModel.EXACT).altitudeTime(-18.0, morning = true)!!
            val proportion = (sunrise45 - fajr45) / (sunrise45 - lastSunset45) * night
            val (sunrise, _) = sunriseAndLastSunset(GeoPoint(brussels.lat, brussels.lon), date, zone)
            val clock = sunrise - (date.atStartOfDayIn(TimeZone.UTC).epochSeconds + EMB_FAJR_FLOOR_UTC_MINUTES * 60)
            val at18 = SunClock(brussels.lat, brussels.lon, date, offset, SunModel.EXACT).altitudeTime(-18.0, morning = true)
            val summer = maxOf(clock, proportion)
            if (at18 == null) summer else minOf(sunrise - at18, summer)
        }
    }

    @Test
    fun `a cautious member carries its curve for the place`() {
        val berlin = Place(52.52437, 13.41053, "Europe/Berlin", "DE")
        val r = Registry.resolve(berlin)
        assertEquals("de.cautious", r.entry.id)
        val diyanet = r.members.first { it.id == "tr.diyanet.europe" }
        assertNotNull(diyanet.method.fajrAngleByDayOfYear)
        assertNull(r.entry.members.first { it.id == "tr.diyanet.europe" }.method.fajrAngleByDayOfYear)
        val june = diyanet.method.fajrAngleByDayOfYear!![curveSlot(LocalDate(2026, 6, 21))]
        assertTrue(june < 18.0, "takdir moves June's Fajr later than 18°: $june")
        val istanbul = Registry.resolveEntry(Registry.byId("tr.diyanet.europe")!!, Place(41.0082, 28.9784, "Europe/Istanbul", "TR"))
        assertTrue(assertNotNull(istanbul.method).fajrAngleByDayOfYear!!.all { it == 18.0 }, "no takdir south of 44.5°")
    }

    // Ruling R39: an authority whose Fajr start follows a curve ends the fast at its own earliest dawn.

    @Test
    fun `a curve rule's end of eating reads its own dawns and not the late start curve`() {
        // Each against its old end, the late start curve's dawn: never later (but for the old end's own
        // envelope), and earlier on some day by about as much as its own table showed that end late
        // (IRN's frozen August, EMB's clock floor in May, Diyanet's spring ramp at Berlin, DUM RF's May).
        class Case(val id: String, val point: GeoPoint, val zone: String, val minutes: Int)
        val cases = listOf(
            Case("no.irn", GeoPoint(60.39299, 5.32415), "Europe/Oslo", 45), // Bergen: the edge's hour before sunrise
            Case("be.emb", GeoPoint(50.8467, 4.3525), "Europe/Brussels", 10),
            // Hamburg, beyond Diyanet's city tables: at its own tables Fajr follows the table's own curve (Task 7b).
            Case("tr.diyanet.europe", GeoPoint(53.55073, 9.99302), "Europe/Berlin", 10),
            Case("ru.dumrf", GeoPoint(55.75204, 37.61781), "Europe/Moscow", 5),
            // Ruling R80: the Grande Mosquée's own dawns (its page's earlier method from October) and the
            // ICCI's rule read as an end, in place of R39's 18° dawn.
            Case("fr.gmp", GeoPoint(48.8418, 2.3556), "Europe/Paris", 7),
            Case("ie.icci", GeoPoint(53.3498, -6.2603), "Europe/Dublin", 1),
        )
        val dates = (1..12).flatMap { m -> listOf(LocalDate(2026, m, 1), LocalDate(2026, m, 15)) }
        val oneMinute = kotlin.time.Duration.parse("1m")
        for (c in cases) {
            val r = Registry.resolveEntry(Registry.byId(c.id)!!, Place(c.point.lat, c.point.lon, c.zone, "XX"))
            val method = assertNotNull(r.method, c.id)
            val end = assertNotNull(method.endOfEating as? EndOfEating.DawnAngle, c.id)
            assertNotNull(end.bySlot, c.id)
            val old = method.copy(endOfEating = EndOfEating.SameAsFajrDawn)
            val zone = TimeZone.of(c.zone)
            var most = kotlin.time.Duration.ZERO
            for (date in dates) {
                val now = DayComputer.compute(method, r.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar()).endOfEating
                val before = DayComputer.compute(old, r.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar()).endOfEating
                assertTrue(now <= before + oneMinute, "${c.id} $date: $now after $before")
                if (before - now > most) most = before - now
            }
            assertTrue(most >= kotlin.time.Duration.parse("${c.minutes}m"), "${c.id}: at most $most earlier")
        }
        // Diyanet south of its takdir keeps its dawn.
        val sarajevo = Registry.resolveEntry(Registry.byId("tr.diyanet.europe")!!, Place(43.84864, 18.35644, "Europe/Sarajevo", "BA"))
        assertEquals(EndOfEating.SameAsFajrDawn, sarajevo.method!!.endOfEating)
        // In the other hemisphere a table's slots mean nothing: its deepest dawn, no curve.
        val sydney = Registry.resolveEntry(Registry.byId("no.irn")!!, Place(-33.86785, 151.20732, "Australia/Sydney", "AU"))
        assertNull((sydney.method!!.endOfEating as EndOfEating.DawnAngle).bySlot)
    }

    @Test
    fun `an end curve holds north of its table and deepens south of it and is always reached`() {
        val oslo = world.taqwa.app.prayer.engine.registry.data.EndOfEatingDawns.irnOslo
        val atTable = TwilightCurves.endOfEating(oslo.latitude, oslo.latitude, oslo.depressions)
        val north = TwilightCurves.endOfEating(63.43, oslo.latitude, oslo.depressions) // Trondheim
        val south = TwilightCurves.endOfEating(58.15, oslo.latitude, oslo.depressions) // Kristiansand
        val june = curveSlot(LocalDate(2026, 6, 21))
        val january = curveSlot(LocalDate(2026, 1, 15))
        for (s in listOf(january, june)) {
            assertTrue(south[s] >= atTable[s] - 1e-9, "south slot $s: ${south[s]} vs ${atTable[s]}")
            assertTrue(north[s] <= atTable[s] + 1e-9, "north slot $s")
        }
        // Trondheim's midsummer sun sinks only about 3°: the end stays reachable.
        assertTrue(north[june] < 3.2, "Trondheim midsummer ${north[june]}")
        assertTrue(south[june] > atTable[june], "the fraction of the night deepens it south")
    }

    @Test
    fun `a night rule read as an end is never after the same rule read as a start`() {
        // Ruling R80: the ICCI's TwilightAngle rule at Dublin, as its end of eating and as its Fajr.
        val end = TwilightCurves.fajrAsEnd(53.3498, 18.0) { 18.0 / 60 }
        val start = TwilightCurves.fajr(53.3498, 18.0) { 18.0 / 60 }
        for (i in end.indices) assertTrue(end[i] >= start[i] - 1e-9, "slot $i: end ${end[i]} before start ${start[i]}")
        // In winter the rule is the 18° dawn itself; at midsummer, where the sun misses 18° (it sinks about
        // 13°), the rule's own moment, 18/60 of the night before sunrise.
        assertEquals(18.0, end[curveSlot(LocalDate(2026, 12, 21))])
        val june = end[curveSlot(LocalDate(2026, 6, 21))]
        assertTrue(june < 12.0 && june > start[curveSlot(LocalDate(2026, 6, 21))], "midsummer $june")
    }

    @Test
    fun `the grande mosquee's end is floored at its own rule where its dawns carried south fall short`() {
        // Ruling R80's fix round: the Moonsighting Committee's Fajr read as an end is never after it read as a start
        // (on the start curves' tenth-of-a-degree latitudes, where both take the same minutes), to 0.02°, a few
        // seconds: the start takes the sun's declination at noon, the end at the moment itself, and in early
        // October, as the sun moves fastest south, that puts the start up to 0.015° deeper than the end.
        for (lat in listOf(41.7, 48.8, 51.1)) {
            val end = TwilightCurves.moonsightingAsEnd(lat)
            val start = TwilightCurves.moonsighting(lat).first
            for (i in end.indices) assertTrue(end[i] >= start[i] - 0.02, "$lat slot $i: end ${end[i]} before start ${start[i]}")
        }
        // At Propriano (the edge) the Paris dawns carried south by the fraction of the night fall short of that
        // rule in January; the end takes the rule there (read at Propriano's own noon, T3 round), and never less
        // than it on any day.
        val lat = 41.676
        val gmp = Registry.resolveEntry(Registry.byId("fr.gmp")!!, Place(lat, 8.903, "Europe/Paris", "FR"))
        val end = assertNotNull((gmp.method!!.endOfEating as EndOfEating.DawnAngle).bySlot)
        val rule = TwilightCurves.moonsightingAsEnd(lat, 8.903)
        val carried = TwilightCurves.endOfEating(lat, EndOfEatingDawns.gmpParis.latitude, EndOfEatingDawns.gmpParis.depressions)
        for (i in end.indices) assertTrue(end[i] >= rule[i] && end[i] >= carried[i], "slot $i: ${end[i]}")
        val january = curveSlot(LocalDate(2027, 1, 11))
        assertTrue(rule[january] > carried[january], "January: ${rule[january]} vs ${carried[january]}")
        assertEquals(rule[january], end[january])
    }

    @Test
    fun `moonsighting's fajr is never before eighteen degrees and later in a london summer`() {
        val r = Registry.resolveEntry(Registry.byId("other.moonsighting")!!, Place(51.5074, -0.1278, "Europe/London", "GB"))
        val fajr = assertNotNull(assertNotNull(r.method).fajrAngleByDayOfYear)
        val isha = assertNotNull(r.method.ishaAngleByDayOfYear)
        assertTrue(fajr.all { it <= 18.0 } && isha.all { it <= 18.0 })
        val june = fajr[curveSlot(LocalDate(2026, 6, 21))]
        assertTrue(june < 13.0, "June Fajr $june")
    }

    // Registry-wide invariants.

    @Test
    fun `ids are unique and every id resolves back to its entry`() {
        val ids = Registry.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}")
        for (entry in Registry.entries) assertEquals(entry, Registry.byId(entry.id))
        assertNull(Registry.byId("other.tehran"))
    }

    @Test
    fun `beyond its units an authority's end of eating moves as far as its sunrise and takes no fitted margin`() {
        // Ruling R44: a margin fitted at a table's point applies there only; elsewhere SAFE_END or lower.
        // The city-point audit of 10–11 Oct 2026 (tablesAtTheirPoints): an entry computed at the user's own point whose
        // tables became units keeps its own method beyond every reach, exactly as before the units.
        val ownMethodBeyond = setOf(
            "us.isna", "ca.isna", "uz.board", "xk.bik", "gb.wifaqul",
            "ps.gaza.awqaf", "lb.fatwa", "sy.awqaf", "iq.sunni",
        )
        var checked = 0
        for (entry in Registry.entries) {
            val base = entry.method ?: continue
            val units = Units.of(entry.id) ?: continue
            val edge = units.outside(GeoPoint(0.0, 0.0))
            if (entry.id == "pk.karachi" || entry.id == "in.karachi") {
                // Rulings R192 and R194 (13 Nov 2026): Dawat-e-Islami prints a card for every Pakistani and Indian town and
                // the cards held are later than the method, so beyond the cards a place takes the latest margin any held
                // card asks (SouthAsia.pakistanBeyondCards, indiaBeyondCards): never earlier than its own method's, the end
                // of eating at SAFE_END or lower.
                assertTrue(edge.margins.fajr >= base.margins.fajr && edge.margins.dhuhr >= base.margins.dhuhr, entry.id)
                assertTrue(edge.margins.asr >= base.margins.asr && edge.margins.maghrib >= base.margins.maghrib, entry.id)
                assertTrue(edge.margins.isha >= base.margins.isha && edge.margins.sunrise <= base.margins.sunrise, entry.id)
                assertTrue(edge.endOfEatingMarginSeconds <= minOf(base.endOfEatingMarginSeconds, SAFE_END), entry.id)
                continue
            }
            if (entry.id in ownMethodBeyond) {
                assertSame(base, edge, "${entry.id}: its own method beyond every reach")
                continue
            }
            if (entry.id == "ci.default" || entry.id == "tj.default") {
                // Not an authority's edge: Côte d'Ivoire's and Tajikistan's calculated times, their own method with the
                // printed town figure of COSIM or of the Council of Ulama riding beside the user's point
                // (Generic.coteDIvoireAt, Generic.tajikistanAt), margins untouched.
                assertEquals(base, edge.copy(startPoints = emptyList(), endPoints = emptyList(), endsBoundAtUserPoint = false), entry.id)
                continue
            }
            if (entry.id == "ma.habous") {
                // Ruling R199: every Habous place is a unit on its own margins, so the national sunrise margin is the
                // edge's already and the rule below would move the end by nothing. The edge ends eating a minute before
                // the national margin, as its four starts are a minute after theirs, and never above SAFE_END.
                assertEquals(0, edge.margins.sunrise - base.margins.sunrise, edge.id)
                assertEquals(minOf(base.endOfEatingMarginSeconds - 60, SAFE_END), edge.endOfEatingMarginSeconds, edge.id)
                checked++
                continue
            }
            assertEquals(
                minOf(base.endOfEatingMarginSeconds + (edge.margins.sunrise - base.margins.sunrise), SAFE_END),
                edge.endOfEatingMarginSeconds,
                "${edge.id}: end of eating ${edge.endOfEatingMarginSeconds} s, sunrise ${edge.margins.sunrise} s",
            )
            assertTrue(edge.endOfEatingMarginSeconds <= SAFE_END, edge.id)
            checked++
        }
        assertTrue(checked >= 20, "only $checked authorities with units")
    }

    @Test
    fun `where an authority's zone points are not known its times take a minute more`() {
        val safe = margins(start = SAFE_START, sunrise = SAFE_SUNRISE)
        // Kuwait (Task 7a): its 25 days' never-early fit, and a minute more for its unknown point.
        val kuwait = assertNotNull(Registry.byId("kw.awqaf")!!.method)
        assertEquals(-28 + 60, kuwait.margins.dhuhr)
        assertEquals(11 - 60, kuwait.margins.sunrise)
        assertEquals(6 - 60, kuwait.endOfEatingMarginSeconds)
        val dagestan = assertNotNull(Registry.byId("ru.dumrd")!!.method)
        assertEquals(safe.widened(60), dagestan.margins)
        assertEquals(-60, dagestan.endOfEatingMarginSeconds)

        val sharjah = resolve(25.3463, 55.4209, "Asia/Dubai", "AE")
        assertEquals("ae.awqaf", sharjah.entry.id)
        val awqaf = assertNotNull(sharjah.method)
        // Awqaf (Task 7a): its one day's fit, IACAD's seasonal allowance, and a minute for its unknown zone points.
        assertEquals(144 + 14 + 60, awqaf.margins.dhuhr)
        assertEquals(-222 - 22 - 60, awqaf.margins.sunrise)

        // IGGÖ's and FIDS's tables are Diyanet's method printed for Wien and Zürich (point tables, Task 7g).
        val iggo = assertNotNull(Registry.byId("at.iggo")!!.method)
        val graz = resolve(47.06667, 15.45, "Europe/Vienna", "AT")
        assertNull(graz.unitName)
        assertEquals(iggo.margins.fajr + 60, assertNotNull(graz.method).margins.fajr)
        val fids = assertNotNull(Registry.byId("ch.fids")!!.method)
        val geneva = resolve(46.20222, 6.14569, "Europe/Zurich", "CH")
        assertEquals("ch.fids", geneva.entry.id)
        assertNull(geneva.unitName)
        val genevaMethod = assertNotNull(geneva.method)
        assertEquals(fids.margins.fajr + 60, genevaMethod.margins.fajr)
        assertNotNull(genevaMethod.fajrAngleByDayOfYear, "the edge keeps Diyanet's European Fajr curve")
        // At Zürich itself, Diyanet's own Zürich method, curves (subtask 7b) and margins alike (review of track T4:
        // the margins fitted on FIDS's one month were early against Diyanet's Zürich 2027, which FIDS prints).
        val zurich = resolve(47.36667, 8.55, "Europe/Zurich", "CH")
        assertEquals("Zürich", zurich.unitName)
        val zurichMethod = assertNotNull(zurich.method)
        assertEquals("ch.fids.zurich", zurichMethod.id)
        val diyanetZurich = assertNotNull(
            Registry.resolveEntry(Registry.byId("tr.diyanet.europe")!!, Place(47.36667, 8.55, "Europe/Zurich", "CH")).method,
        )
        assertEquals("tr.diyanet.europe.zurich", diyanetZurich.id)
        assertEquals(diyanetZurich.copy(id = zurichMethod.id), zurichMethod)
        assertNotNull(zurichMethod.ishaAngleByDayOfYear, "Zürich keeps Diyanet's own Isha curve")
    }

    @Test
    fun `a late limit covers only its own events and a unit's comes before its entry's`() {
        val dhuhr = setOf(TimedEvent.DHUHR)
        kotlin.test.assertFailsWith<IllegalArgumentException> { LateLimit(0, "no minutes", dhuhr) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { LateLimit(3, " ", dhuhr) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { LateLimit(3, "no events", emptySet()) }
        // Ruling R41: a Tatarstan-like Dhuhr exception lifts Dhuhr only.
        val entryDhuhr = LateLimit(14, "a fixed Zuhr printed as a clock time", dhuhr)
        val entryAsr = LateLimit(2, "its Asr runs a minute after this core's", setOf(TimedEvent.ASR))
        val entry = single(
            id = "xx.test", nameKey = "authority_test", entryClass = EntryClass.D_AUTHORITY,
            method = TimetableMethod(id = "xx.test", fajrAngle = 18.0, isha = IshaRule.Angle(17.0)),
            school = AsrSchool.STANDARD, lateLimits = listOf(entryDhuhr, entryAsr),
        )
        val unitDhuhr = LateLimit(15, "a locality printing a later Zuhr", dhuhr)
        val unit = AuthorityUnit("u", "U", GeoPoint(0.0, 0.0), 1.0, lateLimits = listOf(unitDhuhr))
        assertEquals(unitDhuhr, lateLimitFor(TimedEvent.DHUHR, unit, entry))
        assertEquals(entryAsr, lateLimitFor(TimedEvent.ASR, unit, entry))
        assertEquals(entryDhuhr, lateLimitFor(TimedEvent.DHUHR, null, entry))
        for (event in listOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.END_OF_EATING, TimedEvent.IMSAK)) {
            assertNull(lateLimitFor(event, unit, entry), "$event takes the class limit")
        }
        // One owner covers an event once.
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            AuthorityUnit("u", "U", GeoPoint(0.0, 0.0), 1.0, lateLimits = listOf(unitDhuhr, entryDhuhr))
        }
        val uq = Registry.byId("sa.ummalqura")!!
        // Umm al-Qura's lag dates (Task 7a) and its units' reach (the review of 9 Oct 2026) lift every event but imsak.
        assertEquals(2, lateLimitFor(TimedEvent.FAJR, null, uq)?.minutes)
        assertEquals(2, lateLimitFor(TimedEvent.DHUHR, null, uq)?.minutes)
        assertEquals(3, lateLimitFor(TimedEvent.SUNRISE, null, uq)?.minutes)
        assertNull(lateLimitFor(TimedEvent.IMSAK, null, uq))
    }

    @Test
    fun `every entry is either cautious over members or a single method`() {
        for (entry in Registry.entries) {
            if (entry.entryClass == EntryClass.C) {
                assertNull(entry.method, entry.id)
                assertTrue(entry.members.size >= 2, entry.id)
                assertEquals(entry.members.indices.map { it + 1 }, entry.members.map { it.shareRank }, entry.id)
                assertEquals(AboutTemplate.CAUTIOUS, entry.about, entry.id)
            } else {
                assertNotNull(entry.method, entry.id)
                assertTrue(entry.members.isEmpty(), entry.id)
            }
            for (id in entry.nearby) assertNotNull(Registry.byId(id), "${entry.id} lists unknown $id")
            if (entry.scope == Scope.COUNTRY) assertTrue(entry.countries.isNotEmpty(), entry.id)
            assertTrue(entry.id == entry.id.lowercase() && ' ' !in entry.id, entry.id)
        }
    }

    @Test
    fun `other methods are the old picker's methods without tehran and the only ones that apply anywhere`() {
        val ids = Registry.otherMethods.map { it.id }
        val old = listOf(
            "other.mwl", "other.isna", "other.egyptian", "other.ummalqura", "other.karachi", "other.moonsighting",
            "other.turkey", "other.kuwait", "other.qatar", "other.dubai", "other.singapore",
        )
        // Ruling R50: the named timetables are offered where Automatic lists them, not everywhere.
        assertEquals(old, ids)
        assertTrue(ids.none { "tehran" in it })
        val farAway = Place(-13.83333, -171.76666, "Pacific/Apia", "WS")
        for (entry in Registry.otherMethods) {
            assertEquals(Scope.GLOBAL, entry.scope, entry.id)
            assertTrue(Registry.inScope(entry, farAway), entry.id)
        }
        // Samoa has no timetable of its own: only its Automatic (the safe default) and the Other methods.
        val automatic = Registry.resolve(farAway).entry.id
        for (entry in Registry.entries.filter { !it.id.startsWith("other.") && it.id != automatic }) {
            assertFalse(Registry.inScope(entry, farAway), "${entry.id} applies in Samoa")
        }
        assertEquals("method_muslim_world_league", Registry.byId("other.mwl")!!.shortNameKey)
        assertEquals("method_moonsighting", Registry.byId("other.moonsighting")!!.shortNameKey)
        // A named cautious member applies where its cautious entry is Automatic.
        val toronto = Place(43.70643, -79.39864, "America/Toronto", "CA")
        val manchester = Place(53.48095, -2.23743, "Europe/London", "GB")
        for ((place, cautious) in listOf(toronto to Registry.resolve(toronto).entry, manchester to Registry.resolve(manchester).entry)) {
            assertEquals(EntryClass.C, cautious.entryClass, cautious.id)
            for (member in cautious.members.mapNotNull { Registry.byId(it.id) }) {
                assertTrue(Registry.inScope(member, place), "${member.id} at ${cautious.id}")
            }
        }
    }

    @Test
    fun `a named timetable applies at home and where automatic lists it but not across the world`() {
        val london = Place(51.5074, -0.1278, "Europe/London", "GB")
        val manchester = Place(53.48095, -2.23743, "Europe/London", "GB")
        val istanbul = Place(41.0082, 28.9784, "Europe/Istanbul", "TR")
        val lupt = Registry.byId("gb.london.lupt")!!
        assertTrue(Registry.inScope(lupt, london))
        // Declared global: its own country, within its area (R45's three reaches, the owner's decision of 17 Oct 2026).
        assertTrue(Registry.inScope(lupt, Place(51.45625, -0.97113, "Europe/London", "GB"))) // Reading, 59 km
        assertFalse(Registry.inScope(lupt, manchester)) // 262 km
        assertFalse(Registry.inScope(lupt, istanbul))
        assertFalse(Registry.inScope(Registry.byId("tr.diyanet.europe")!!, Place(-26.20227, 28.04363, "Africa/Johannesburg", "ZA")))
        assertFalse(Registry.inScope(Registry.byId("id.muhammadiyah")!!, london))
        assertTrue(Registry.inScope(Registry.byId("id.muhammadiyah")!!, Place(-6.21462, 106.84513, "Asia/Jakarta", "ID")))
    }

    @Test
    fun `the old picker's methods take the plain safe rounding and never an authority's fitted margins`() {
        val safe = margins(start = SAFE_START, sunrise = SAFE_SUNRISE)
        val old = Registry.otherMethods.filter { it.id.startsWith("other.") }
        assertEquals(11, old.size)
        for (entry in old) {
            val method = assertNotNull(entry.method, entry.id)
            // Task 7h's OtherMethodsProofTest measured adhan2 0.0.7's own Asr up to ~34 s later than
            // this core's iterated Asr (it always takes one declination for the whole day, not
            // iterated to the Asr moment). Every Other method's Asr carries ADHAN2_ASR_ALLOWANCE on
            // top of the plain safe Asr margin (7h, 7a, 7c measured it), and other.turkey more
            // still: adhan2 TURKEY's lead over Diyanet's daily model (Task 7b).
            val allowanceFixed = setOf(
                "other.mwl", "other.isna", "other.karachi", "other.moonsighting",
                "other.egyptian", "other.kuwait", "other.qatar", "other.dubai", "other.singapore",
            )
            val expectedAsr = when (entry.id) {
                "other.turkey" -> safe.asr + ADHAN2_ASR_ALLOWANCE + Diyanet.TURKEY_ASR_EXTRA
                in allowanceFixed -> safe.asr + ADHAN2_ASR_ALLOWANCE
                else -> safe.asr
            }
            assertEquals(safe.copy(asr = expectedAsr), method.margins, entry.id)
            assertEquals(SAFE_END, method.endOfEatingMarginSeconds, entry.id)
        }
        // Their definitions stay: Umm al-Qura's rebuilt Fajr, Qatar's Isha after Maghrib, Dubai's minutes.
        val uq = assertNotNull(Registry.byId("other.ummalqura")!!.method)
        assertEquals(Registry.byId("sa.ummalqura")!!.method!!.fajrDeclinationBias, uq.fajrDeclinationBias)
        assertEquals(IshaRule.AfterMaghrib(90), Registry.byId("other.qatar")!!.method!!.isha)
        assertEquals(3, Registry.byId("other.dubai")!!.method!!.authorityMinutes.maghrib)
    }

    @Test
    fun `a scoped choice applies only in its own area`() {
        val london = Place(51.5074, -0.1278, "Europe/London", "GB")
        val manchester = Place(53.48095, -2.23743, "Europe/London", "GB")
        val istanbul = Place(41.0082, 28.9784, "Europe/Istanbul", "TR")
        val toronto = Place(43.70643, -79.39864, "America/Toronto", "CA")
        val chicago = Registry.byId("us.chicago")!!
        val cautiousUk = Registry.byId("gb.cautious")!!
        assertTrue(Registry.inScope(Registry.byId("other.mwl")!!, istanbul))
        assertTrue(Registry.inScope(Registry.byId("tr.diyanet")!!, istanbul))
        assertFalse(Registry.inScope(Registry.byId("tr.diyanet")!!, london))
        assertTrue(Registry.inScope(cautiousUk, manchester))
        assertFalse(Registry.inScope(cautiousUk, london))
        assertFalse(Registry.inScope(chicago, toronto))
        assertTrue(Registry.inScope(Registry.byId("ca.iit")!!, toronto))
    }

    @Test
    fun `a chosen entry is resolved with its own units at the place`() {
        val bogor = Place(-6.59444, 106.78917, "Asia/Jakarta", "ID")
        val r = Registry.resolveEntry(Registry.byId("id.muhammadiyah")!!, bogor)
        val method = assertNotNull(r.method)
        near(18.0, method.fajrAngle, "fajr")
        near(-2.0, method.horizonDeg, "horizon")
    }

    // The Ramadan calendar.

    @Test
    fun `ramadan is umm al qura's inside its dates`() {
        val calendar = Registry.ramadanCalendar()
        assertTrue(calendar.isRamadan(LocalDate(2026, 2, 18)))
        assertTrue(calendar.isRamadan(LocalDate(2026, 3, 19)))
        assertFalse(calendar.isRamadan(LocalDate(2026, 2, 17)))
        assertFalse(calendar.isRamadan(LocalDate(2026, 3, 20)))
    }

    @Test
    fun `beyond umm al qura's dates ramadan is the tabular month widened by a day each side`() {
        val calendar = Registry.ramadanCalendar()
        // Ramadan 1452 runs into January 2031; the next one begins late in 2031.
        var first = LocalDate(2031, 6, 1)
        while (TabularHijriCalendar.fromGregorian(first).month != 9) first = first.plus(1, DateTimeUnit.DAY)
        assertTrue(calendar.isRamadan(first))
        assertTrue(calendar.isRamadan(first.plus(-1, DateTimeUnit.DAY)))
        assertFalse(calendar.isRamadan(first.plus(-2, DateTimeUnit.DAY)))
        val shifted = Registry.ramadanCalendar(hijriOffsetDays = 1)
        assertTrue(shifted.isRamadan(first.plus(-2, DateTimeUnit.DAY)))
    }

    @Test
    fun `authorities other than umm al qura see ramadan a day either side of its dates`() {
        val muis = Registry.ramadanCalendarFor(Registry.byId("sg.muis")!!)
        assertTrue(muis.isRamadan(LocalDate(2026, 3, 20)), "MUIS's Ramadan 1447 ended a day after Umm al-Qura's")
        val uq = Registry.ramadanCalendarFor(Registry.byId("sa.ummalqura")!!)
        assertFalse(uq.isRamadan(LocalDate(2026, 3, 20)))
    }
}
