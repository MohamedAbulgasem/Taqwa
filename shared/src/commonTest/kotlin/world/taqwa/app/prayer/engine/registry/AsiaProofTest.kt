package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
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
