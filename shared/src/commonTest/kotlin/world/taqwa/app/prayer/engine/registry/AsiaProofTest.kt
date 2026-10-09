package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.authorities.PlaceCurves
import world.taqwa.app.prayer.engine.registry.authorities.TwilightCurves
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Task 7f (South Asia and Central Asia): what the gate proved against the authorities' own
 * official tables (`docs/research/2026-09-prayer-times/proof/7f-asia.md`) is restricted data and
 * cannot be committed as printed times (none of pk.karachi, in.karachi, bd.ifb, uz.board, kz.qmdb
 * or kg.default is openly licensed, unlike MUIS, DUM RT or LUPT). This test only holds the
 * derived, committed parameters (angles, margins, the class boundary) and how the registry
 * resolves each place, never a printed official minute.
 */
class AsiaProofTest {

    private fun resolve(lat: Double, lon: Double, zone: String, country: String) =
        Registry.resolve(Place(lat, lon, zone, country))

    private fun TimetableMethod.ishaDegrees(): Double = (isha as IshaRule.Angle).degrees

    private fun near(expected: Double, actual: Double, label: String) =
        assertTrue(abs(expected - actual) < 1e-9, "$label: expected $expected, got $actual")

    @Test
    fun `karachi resolves to a hanafi 18 18 method with the task 7f margins`() {
        val r = resolve(24.8607, 67.0011, "Asia/Karachi", "PK")
        assertEquals("pk.karachi", r.entry.id)
        assertEquals(EntryClass.D_AUTHORITY, r.entryClass)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        val method = assertNotNull(r.method)
        near(18.0, method.fajrAngle, "fajr")
        near(18.0, method.ishaDegrees(), "isha")
        // Fitted (task 7f, pk-karachi.tsv): never early against the Banuri Daimi timetable and
        // Dawat-e-Islami's Karachi AJAX. A single Asr margin covers both the Standard and Hanafi
        // columns the Daimi table prints, so it is the larger (later) of the two fits.
        assertEquals(71, method.margins.asr)
        assertEquals(-26, method.endOfEatingMarginSeconds)
    }

    @Test
    fun `delhi keeps its own fitted margins rather than karachi's`() {
        val r = resolve(28.6139, 77.2090, "Asia/Kolkata", "IN")
        assertEquals("in.karachi", r.entry.id)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        val method = assertNotNull(r.method)
        // Delhi's elevation (701 ft) widens sunrise and Maghrib well past Karachi's own fit
        // (in-karachi.tsv): kept as a separate method rather than shared margins.
        assertTrue(method.margins.sunrise < -100, "Delhi sunrise margin: ${method.margins.sunrise}")
        assertTrue(method.margins.maghrib > 100, "Delhi maghrib margin: ${method.margins.maghrib}")
    }

    @Test
    fun `dhaka resolves to the ifb method with its authority minutes and a fitted core`() {
        val r = resolve(23.7104, 90.4074, "Asia/Dhaka", "BD")
        assertEquals("bd.ifb", r.entry.id)
        assertEquals(AsrSchool.HANAFI, r.entry.school)
        val method = assertNotNull(r.method)
        near(18.0, method.fajrAngle, "fajr")
        assertEquals(EventOffsets(fajr = 3, dhuhr = 3, maghrib = 3), method.authorityMinutes)
        // Isha and the end of eating are the least certain (task 7f, bd-ifb.tsv): both carry a
        // recorded late-limit exception (spec's honesty rule), never a silently widened default.
        assertTrue(r.entry.lateLimits.any { TimedEvent.ISHA in it.events })
        assertTrue(r.entry.lateLimits.any { TimedEvent.END_OF_EATING in it.events })
    }

    @Test
    fun `tashkent resolves to the current 15point5 degree uzbek board method`() {
        val r = resolve(41.2995, 69.2401, "Asia/Tashkent", "UZ")
        assertEquals("uz.board", r.entry.id)
        val method = assertNotNull(r.method)
        near(15.5, method.fajrAngle, "fajr")
        near(15.5, method.ishaDegrees(), "isha")
        assertEquals(4, method.authorityMinutes.maghrib)
    }

    @Test
    fun `qmdb switches its authority minutes at 48 degrees north`() {
        val almaty = resolve(43.238293, 76.945465, "Asia/Almaty", "KZ")
        val astana = resolve(51.133333, 71.433333, "Asia/Almaty", "KZ")
        assertEquals("kz.qmdb", almaty.entry.id)
        assertEquals("kz.qmdb", astana.entry.id)
        val southMethod = assertNotNull(almaty.method)
        val northMethod = assertNotNull(astana.method)
        assertEquals(EventOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3), southMethod.authorityMinutes)
        assertEquals(EventOffsets(sunrise = -5, dhuhr = 5, asr = 5, maghrib = 5), northMethod.authorityMinutes)
        // Both places share the one set of fitted margins (task 7f, kz-qmdb.tsv). The authority's
        // own minutes differ by the region, and so does the end of eating's margin: at and above
        // 48N it keeps clear of QMDB's AngleBased residual (end-of-eating audit, 3 Oct 2026), while
        // below 48N it stays 0, since an end only ever moves earlier where it must.
        assertEquals(southMethod.margins, northMethod.margins)
        // The northern round (3 Oct 2026): QMDB's 2027 and northern tables decide Fajr and Isha.
        assertTrue(northMethod.margins.fajr >= 8, "fajr margin: ${northMethod.margins.fajr}")
        assertTrue(northMethod.margins.isha >= 11, "isha margin: ${northMethod.margins.isha}")
        assertEquals(0, southMethod.endOfEatingMarginSeconds)
        assertTrue(northMethod.endOfEatingMarginSeconds <= -374, "north end-of-eating margin: ${northMethod.endOfEatingMarginSeconds}")
        // The north-west round (6 Oct 2026): QMDB's northernmost places and Ayagoz move Asr and Maghrib later.
        assertTrue(northMethod.margins.asr >= -6, "asr margin: ${northMethod.margins.asr}")
        assertTrue(northMethod.margins.maghrib >= -14, "maghrib margin: ${northMethod.margins.maghrib}")
    }

    @Test
    fun `qmdb ends the fast earlier from 46 to 48 degrees north where its AngleBased Fajr binds`() {
        // The north-west round (6 Oct 2026): at margin 0 the end of eating came up to 3 min after QMDB's
        // printed Fajr around the solstice at Atyrau, Shalqar, Ayagoz and Oteshqali Atambayev. The band keeps
        // QMDB's own minutes of 3 and carries its own end margin; below 46N the rule never binds.
        val atyrau = assertNotNull(resolve(47.116667, 51.883333, "Asia/Atyrau", "KZ").method)
        val edge = assertNotNull(resolve(46.0, 51.9, "Asia/Atyrau", "KZ").method)
        val below = assertNotNull(resolve(45.99, 51.9, "Asia/Atyrau", "KZ").method)
        val top = assertNotNull(resolve(47.994187, 51.622514, "Asia/Atyrau", "KZ").method)
        for (method in listOf(atyrau, edge, top)) {
            assertEquals(EventOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3), method.authorityMinutes)
            assertTrue(method.endOfEatingMarginSeconds <= -150, "46-48N end-of-eating margin: ${method.endOfEatingMarginSeconds}")
        }
        // Below 46N the band's margin is gone: 0 at a QMDB place's own unit (Aktau), and beyond every unit the
        // edge's SAFE_END (ruling R44: a fitted margin applies at its tables' points only; city points, 9 Oct 2026).
        assertEquals(0, assertNotNull(resolve(43.635379, 51.169135, "Asia/Aqtau", "KZ").method).endOfEatingMarginSeconds)
        assertEquals(SAFE_END, below.endOfEatingMarginSeconds)
    }

    @Test
    fun `a qmdb city unit bounds the user's own band and curves as well as its own`() {
        // Ruling R44 (city points, 9 Oct 2026): QMDB's place rides as the fixed point beside the user's. North of 46N
        // inside Baikonur's unit (its point 45.97N) the band's end-of-eating margin still applies, Baikonur's own
        // minutes and band staying the city's.
        val north = resolve(46.2, 63.3, "Asia/Qyzylorda", "KZ")
        assertEquals("baikonur", north.unitId)
        val m = assertNotNull(north.method)
        assertEquals(GeoPoint(45.966111, 63.307778), m.fixedPoint)
        assertEquals(EventOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3), m.authorityMinutes)
        assertTrue(m.endOfEatingMarginSeconds <= -150, "46-48N end-of-eating margin: ${m.endOfEatingMarginSeconds}")
        // North of 48N inside Oteshqali Atambayev's unit (its point 47.99N): QMDB's minutes of 5, the north's margin.
        val over = resolve(48.2, 51.62, "Asia/Atyrau", "KZ")
        assertEquals("oteshqali", over.unitId)
        assertEquals(EventOffsets(sunrise = -5, dhuhr = 5, asr = 5, maghrib = 5), over.method!!.authorityMinutes)
        assertTrue(over.method!!.endOfEatingMarginSeconds <= -374)
        // 0.3 deg north of Pavlodar's point, inside its unit: each Fajr slot no deeper than the city's own curve and
        // the user's own latitude's (a later start than either), each end-of-eating slot no shallower.
        val user = resolve(52.6, 76.96, "Asia/Almaty", "KZ")
        assertEquals("pavlodar", user.unitId)
        val city = assertNotNull(resolve(52.315556, 76.956389, "Asia/Almaty", "KZ").method)
        val own = PlaceCurves.at(Registry.byId("kz.qmdb")!!.method!!, GeoPoint(52.6, 76.96))
        val here = assertNotNull(user.method)
        val fajr = assertNotNull(here.fajrAngleByDayOfYear)
        val end = assertIs<EndOfEating.DawnAngle>(here.endOfEating).bySlot!!
        for (i in fajr.indices) {
            assertTrue(fajr[i] <= city.fajrAngleByDayOfYear!![i] && fajr[i] <= own.fajrAngleByDayOfYear!![i], "Fajr slot $i")
            val cityEnd = assertIs<EndOfEating.DawnAngle>(city.endOfEating).bySlot!![i]
            val ownEnd = assertIs<EndOfEating.DawnAngle>(own.endOfEating).bySlot!![i]
            assertTrue(end[i] >= cityEnd && end[i] >= ownEnd, "end of eating slot $i")
        }
    }

    @Test
    fun `qmdb reads its curves at the place's own latitude as well as the grid`() {
        // QMDB's own Bugrovoe (55.049N) sits just short of a 0.1 degree step, so the grid alone read its curve
        // at 55.0N, about 0.05 degrees to its south, and its Fajr came 1 min early against QMDB's printed one
        // from the solstice to mid-August (north-west round, 6 Oct 2026). Each slot now takes the later start of
        // the grid's curve and the place's own, and the end of eating the earlier dawn of the two.
        val lat = 55.049091
        val method = assertNotNull(resolve(lat, 69.724025, "Asia/Almaty", "KZ").method)
        val grid = TwilightCurves.fajr(lat, 15.0) { 15.0 / 60 }
        val own = TwilightCurves.fajr(lat, 15.0, onGrid = false) { 15.0 / 60 }
        val fajr = assertNotNull(method.fajrAngleByDayOfYear)
        val end = assertIs<EndOfEating.DawnAngle>(method.endOfEating)
        val ends = assertNotNull(end.bySlot)
        for (i in fajr.indices) {
            assertTrue(fajr[i] <= minOf(grid[i], own[i]) + 1e-12, "slot $i: Fajr ${fajr[i]} deeper than a curve")
            assertTrue(ends[i] >= maxOf(grid[i], own[i]) - 1e-12, "slot $i: end ${ends[i]} shallower than a curve")
        }
        // In mid-May the place's own curve is the shallower (the later Fajr): about 0.035 degrees, half a minute there.
        val may = curveSlot(LocalDate(2026, 5, 15))
        assertTrue(own[may] < grid[may] - 0.02, "15 May: own ${own[may]} vs grid ${grid[may]}")
        assertEquals(own[may], fajr[may])
        assertEquals(grid[may], ends[may])
    }

    @Test
    fun `bishkek resolves to the kyrgyz default with fitted margins`() {
        val r = resolve(42.8746, 74.5698, "Asia/Bishkek", "KG")
        assertEquals("kg.default", r.entry.id)
        val method = assertNotNull(r.method)
        near(18.0, method.fajrAngle, "fajr")
        near(16.0, method.ishaDegrees(), "isha")
        // The Muftiate's Maghrib runs well past sunset (task 7f, kg-default.tsv: about 7 min).
        assertTrue(method.margins.maghrib > 300, "Bishkek maghrib margin: ${method.margins.maghrib}")
    }
}
