package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Task 7a's proof, as far as it can be committed: Saudi Arabia, the Gulf and Egypt. Their official
 * tables are restricted (tools/timetables/official/archive, docs/research/2026-09-prayer-times/proof/
 * 7a-gulf-egypt.md), so no printed time is here: these tests pin the rules the gate proved (the
 * authorities' own minutes, the units and their points, the recorded exceptions) and the principle
 * the fitted margins keep (spec §3.5: a start never before the method at the user's own point, an
 * end never after it).
 */
class GulfEgyptProofTest {

    private fun place(lat: Double, lon: Double, zone: String, country: String) = Place(lat, lon, zone, country)

    private fun day(id: String, place: Place, date: LocalDate): PrayerDay =
        DayPipeline.day(assertNotNull(Registry.byId(id)), place, date)

    private val makkah = place(21.4225, 39.8262, "Asia/Riyadh", "SA")
    private val doha = place(25.28545, 51.53096, "Asia/Qatar", "QA")

    private fun daysOf2026(step: Int = 7): List<LocalDate> =
        generateSequence(LocalDate(2026, 1, 1)) { it.plus(step, DateTimeUnit.DAY) }.takeWhile { it.year == 2026 }.toList()

    // Umm al-Qura.

    @Test
    fun `umm al qura's isha is two hours after maghrib in its ramadan and ninety minutes otherwise`() {
        val ramadan = day("sa.ummalqura", makkah, LocalDate(2026, 3, 1))
        assertEquals(120.minutes, ramadan.isha - ramadan.maghrib)
        val september = day("sa.ummalqura", makkah, LocalDate(2026, 9, 20))
        assertEquals(90.minutes, september.isha - september.maghrib)
    }

    @Test
    fun `umm al qura's end of eating is its fajr minute or the one before away from its lag dates`() {
        val turaif = place(31.68, 38.66, "Asia/Riyadh", "SA")
        for (at in listOf(makkah, turaif)) {
            for (date in daysOf2026(step = 3).filter { it !in UmmAlQuraDates.lagDates }) {
                val d = day("sa.ummalqura", at, date)
                val gap = d.fajr - d.endOfEating
                assertTrue(gap == 0.minutes || gap == 1.minutes, "$date at ${at.lat}: end of eating $gap before Fajr")
            }
        }
    }

    @Test
    fun `umm al qura's one exception is its lag dates at fajr sunrise and the end of eating`() {
        val uq = assertNotNull(Registry.byId("sa.ummalqura"))
        assertEquals(EntryClass.A, uq.entryClass)
        for (event in listOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.END_OF_EATING)) {
            assertEquals(2, lateLimitFor(event, null, uq)?.minutes, "$event")
        }
        for (event in listOf(TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA, TimedEvent.IMSAK)) {
            assertNull(lateLimitFor(event, null, uq), "$event")
        }
        // The Other method keeps the plain safe rounding and no exception (ruling R31).
        val other = assertNotNull(Registry.byId("other.ummalqura"))
        assertTrue(other.lateLimits.isEmpty())
        assertEquals(margins(start = SAFE_START, sunrise = SAFE_SUNRISE), assertNotNull(other.method).margins)
        assertEquals(SAFE_END, other.method.endOfEatingMarginSeconds)
    }

    // Qatar.

    @Test
    fun `qatar's isha is ninety minutes after a maghrib at least three after sunset in ramadan and summer`() {
        for (date in listOf(LocalDate(2026, 3, 1), LocalDate(2026, 7, 1), LocalDate(2026, 12, 1))) {
            val d = day("qa.calendarhouse", doha, date)
            assertEquals(90.minutes, d.isha - d.maghrib, "$date")
            assertTrue(d.maghrib - d.sunset >= 3.minutes, "$date: Maghrib ${d.maghrib - d.sunset} after sunset")
        }
    }

    @Test
    fun `al khor follows doha's table and keeps its own later summer sun`() {
        val r = Registry.resolve(place(25.68389, 51.50583, "Asia/Qatar", "QA"))
        assertEquals("qa.calendarhouse", r.entry.id)
        assertEquals("Al Khor", r.unitName)
        assertEquals(EntryClass.B, r.entryClass)
        assertEquals(GeoPoint(25.28545, 51.53096), assertNotNull(r.method).fixedPoint)
        val unit = assertNotNull(Units.of("qa.calendarhouse")).unit("al-khor")
        assertEquals(3, lateLimitFor(TimedEvent.MAGHRIB, unit, r.entry)?.minutes)
        assertEquals(3, lateLimitFor(TimedEvent.ISHA, unit, r.entry)?.minutes)
        assertNull(lateLimitFor(TimedEvent.FAJR, unit, r.entry))
        // Az Za'ayin, 12 km south, is Doha's, within Doha's own limit.
        val zaayin = Registry.resolve(place(25.57744, 51.48306, "Asia/Qatar", "QA"))
        assertEquals("Doha", zaayin.unitName)
        assertNull(lateLimitFor(TimedEvent.MAGHRIB, Units.of("qa.calendarhouse")!!.unit("doha"), zaayin.entry))
    }

    @Test
    fun `qatar's other zones claim nothing and wait ninety seconds either way`() {
        val base = assertNotNull(Registry.byId("qa.calendarhouse")?.method)
        val dukhan = Registry.resolve(place(25.43, 50.79, "Asia/Qatar", "QA"))
        assertEquals("Dukhan", dukhan.unitName)
        assertFalse(dukhan.measured)
        assertEquals(EntryClass.D_AUTHORITY, dukhan.entryClass)
        val zone = assertNotNull(dukhan.method)
        assertEquals(base.margins.fajr + 90, zone.margins.fajr)
        assertEquals(base.margins.maghrib + 90, zone.margins.maghrib)
        assertEquals(base.margins.sunrise - 90, zone.margins.sunrise)
        assertEquals(base.endOfEatingMarginSeconds - 90, zone.endOfEatingMarginSeconds)
    }

    // The UAE.

    @Test
    fun `dubai's three zones each follow their own table`() {
        val dubai = Registry.resolve(place(25.07725, 55.30927, "Asia/Dubai", "AE"))
        assertEquals("Dubai", dubai.unitName)
        assertEquals(EntryClass.B, dubai.entryClass)
        assertEquals(3, lateLimitFor(TimedEvent.END_OF_EATING, Units.of("ae.iacad.dubai")!!.unit("dubai"), dubai.entry)?.minutes)
        val hatta = Registry.resolve(place(24.80073, 56.12726, "Asia/Dubai", "AE"))
        assertEquals("Hatta", hatta.unitName)
        assertEquals("ae.iacad.hatta", hatta.method?.id)
        assertEquals(EntryClass.B, hatta.entryClass)
        assertTrue(hatta.measured)
        val lahbab = Registry.resolve(place(25.03763, 55.59135, "Asia/Dubai", "AE"))
        assertEquals("Dubai Rural", lahbab.unitName)
        assertEquals(EntryClass.D_AUTHORITY, lahbab.entryClass)
        assertTrue(lahbab.measured)
        assertEquals(GeoPoint(24.66, 55.60), lahbab.method?.fixedPoint)
    }

    // Oman.

    @Test
    fun `oman is class b at muscat and claims nothing beyond it`() {
        val muscat = Registry.resolve(place(23.58413, 58.40778, "Asia/Muscat", "OM"))
        assertEquals("Muscat", muscat.unitName)
        assertEquals(EntryClass.B, muscat.entryClass)
        val sohar = Registry.resolve(place(24.34745, 56.70937, "Asia/Muscat", "OM"))
        assertNull(sohar.unitName)
        assertFalse(sohar.measured)
        assertEquals(EntryClass.D_AUTHORITY, sohar.entryClass)
        val base = assertNotNull(Registry.byId("om.mara")?.method)
        assertEquals(base.margins.dhuhr + 60, assertNotNull(sohar.method).margins.dhuhr)
    }

    // Egypt.

    @Test
    fun `egypt's towns printed from an off point take the later start and the earlier end`() {
        val national = assertNotNull(Registry.byId("eg.esa")?.method)
        val units = assertNotNull(Units.of("eg.esa"))
        fun starts(m: TimetableMethod) = listOf(m.margins.fajr, m.margins.dhuhr, m.margins.asr, m.margins.maghrib, m.margins.isha)
        for (id in listOf("kharga", "taba")) {
            val m = assertNotNull(units.unit(id).method)
            starts(m).zip(starts(national)).forEach { (own, n) -> assertTrue(own > n, "$id: start margin $own") }
            assertEquals(national.margins.sunrise, m.margins.sunrise, id)
            assertEquals(national.endOfEatingMarginSeconds, m.endOfEatingMarginSeconds, id)
        }
        val rasGharib = assertNotNull(units.unit("ras-gharib").method)
        assertEquals(starts(national), starts(rasGharib))
        assertTrue(rasGharib.margins.sunrise < national.margins.sunrise)
        assertTrue(rasGharib.endOfEatingMarginSeconds < national.endOfEatingMarginSeconds)
        val taba = units.unit("taba")
        assertEquals(3, lateLimitFor(TimedEvent.SUNRISE, taba, Registry.byId("eg.esa")!!)?.minutes)
        assertNull(lateLimitFor(TimedEvent.MAGHRIB, taba, Registry.byId("eg.esa")!!))
        // Beyond every unit a start waits at least as long as Taba's own, and the ends come before Ras Gharib's.
        val edge = units.outside(GeoPoint(27.0, 31.0))
        starts(edge).zip(starts(assertNotNull(taba.method))).forEach { (e, t) -> assertTrue(e >= t, "edge $e against Taba's $t") }
        assertTrue(edge.margins.sunrise <= rasGharib.margins.sunrise)
        assertTrue(edge.endOfEatingMarginSeconds <= rasGharib.endOfEatingMarginSeconds)
    }

    // The principle every fitted margin keeps (spec §3.5).

    @Test
    fun `a start is never before the method at the user's own point and an end never after it`() {
        val cases = listOf(
            // Bahrain: the book's east point for the ends, its west point for the starts.
            Triple("bh.council", place(26.22787, 50.58565, "Asia/Bahrain", "BH"), null),
            Triple("bh.council", place(26.11528, 50.50694, "Asia/Bahrain", "BH"), null),
            // Al Khor with Doha's point.
            Triple("qa.calendarhouse", place(25.68389, 51.50583, "Asia/Qatar", "QA"), null),
            // Egypt's towns printed from an off point, against ESA's method at the town.
            Triple("eg.esa", place(25.45101, 30.54653, "Africa/Cairo", "EG"), "eg.esa"),
            Triple("eg.esa", place(29.4925, 34.8969, "Africa/Cairo", "EG"), "eg.esa"),
            Triple("eg.esa", place(28.35831, 33.07829, "Africa/Cairo", "EG"), "eg.esa"),
        )
        for ((id, at, national) in cases) {
            val entry = assertNotNull(Registry.byId(id))
            val shown = Registry.resolveEntry(entry, at)
            // The method at the user's point alone: the unit's own, else the entry's, without its points.
            val alone = assertNotNull(if (national != null) entry.method else shown.method)
                .copy(fixedPoint = null, startPoints = emptyList())
            val zone = TimeZone.of(at.zoneId)
            for (date in daysOf2026(step = 5)) {
                val d = DayPipeline.unended(shown, date, zone)
                val floor = DayComputer.compute(alone, GeoPoint(at.lat, at.lon), date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                val where = "$id at ${at.lat},${at.lon} on $date"
                assertTrue(d.fajr >= floor.fajr && d.dhuhr >= floor.dhuhr && d.asr >= floor.asr, where)
                assertTrue(d.maghrib >= floor.maghrib && d.isha >= floor.isha, where)
                assertTrue(d.sunrise <= floor.sunrise && d.endOfEating <= floor.endOfEating, where)
            }
        }
    }

    @Test
    fun `the old picker's gulf and egyptian methods keep their definitions and adhan2's asr`() {
        assertEquals(IshaRule.AfterMaghrib(90), Registry.byId("other.qatar")?.method?.isha)
        assertEquals(3, Registry.byId("other.qatar")?.method?.authorityMinutes?.maghrib)
        assertEquals(3, Registry.byId("other.dubai")?.method?.authorityMinutes?.maghrib)
        // Their Asr waits for adhan2's one-declination Asr (Task 7h's finding, measured here over 2026-2027).
        val withAllowance = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE)
        for (id in listOf("other.qatar", "other.dubai", "other.kuwait", "other.egyptian")) {
            assertEquals(withAllowance, Registry.byId(id)?.method?.margins, id)
            assertEquals(SAFE_END, Registry.byId(id)?.method?.endOfEatingMarginSeconds, id)
        }
    }
}
