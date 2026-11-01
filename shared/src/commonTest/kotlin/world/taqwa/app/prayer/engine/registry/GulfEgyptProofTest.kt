package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import kotlin.math.floor
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
    fun `umm al qura's exceptions are its lag dates and its units' reach`() {
        // Class A's minute is passed on the lag dates at the tables' own points, and across a unit's reach (its own
        // tables replayed there, sa-ummalqura-reach.tsv, the review of 9 Oct 2026): every start up to 2 min after
        // the unit's table, sunrise and the end of eating up to 3 before it.
        val uq = assertNotNull(Registry.byId("sa.ummalqura"))
        assertEquals(EntryClass.A, uq.entryClass)
        for (event in listOf(TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA)) {
            assertEquals(2, lateLimitFor(event, null, uq)?.minutes, "$event")
        }
        for (event in listOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING)) {
            assertEquals(3, lateLimitFor(event, null, uq)?.minutes, "$event")
        }
        assertNull(lateLimitFor(TimedEvent.IMSAK, null, uq))
        // Nine units of the north and the east, where a lag date's later Fajr and the reach's edge meet: Fajr 3.
        val haql = Units.of("sa.ummalqura")!!.units.single { it.id == "haql" }
        assertEquals(3, lateLimitFor(TimedEvent.FAJR, haql, uq)?.minutes)
        assertEquals(2, lateLimitFor(TimedEvent.DHUHR, haql, uq)?.minutes)
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
        // Rafah's point is south-west of the town and Sidi Barrani's north of it (ruling R160): only the times their
        // whole year asks for are their own.
        val rafah = assertNotNull(units.unit("rafah").method)
        assertEquals(national.margins.copy(fajr = rafah.margins.fajr), rafah.margins)
        assertTrue(rafah.margins.fajr > national.margins.fajr)
        assertEquals(national.endOfEatingMarginSeconds, rafah.endOfEatingMarginSeconds)
        val sidiBarrani = assertNotNull(units.unit("sidi-barrani").method)
        assertEquals(national.margins.copy(isha = sidiBarrani.margins.isha), sidiBarrani.margins)
        assertTrue(sidiBarrani.margins.isha > national.margins.isha)
        assertTrue(sidiBarrani.endOfEatingMarginSeconds < national.endOfEatingMarginSeconds)
        val taba = units.unit("taba")
        assertEquals(3, lateLimitFor(TimedEvent.SUNRISE, taba, Registry.byId("eg.esa")!!)?.minutes)
        assertNull(lateLimitFor(TimedEvent.MAGHRIB, taba, Registry.byId("eg.esa")!!))
        // Beyond every unit a start waits at least as long as Taba's own, and the ends come before Ras Gharib's.
        val edge = units.outside(GeoPoint(27.0, 31.0))
        starts(edge).zip(starts(assertNotNull(taba.method))).forEach { (e, t) -> assertTrue(e >= t, "edge $e against Taba's $t") }
        assertTrue(edge.margins.sunrise <= rasGharib.margins.sunrise)
        assertTrue(edge.endOfEatingMarginSeconds <= rasGharib.endOfEatingMarginSeconds)
    }

    @Test
    fun `kharga's dhuhr is never before esa's noon west of the town at its latest rounding`() {
        // ESA's Kharga noon runs 68 to 70 s after the town's every day (noon follows longitude alone) and its own sun
        // puts noon up to 4 s after this engine's, in early November (ruling R159): its printed Dhuhr is at most the
        // town's noon plus 74 s, to the nearest minute.
        val kharga = place(25.45101, 30.54653, "Africa/Cairo", "EG")
        val zone = TimeZone.of(kharga.zoneId)
        var date = LocalDate(2026, 1, 1)
        while (date.year <= 2028) {
            val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
            val noon = SunClock(kharga.lat, kharga.lon, date, offset, SunModel.EXACT).transit()
            val latest = floor((noon + ESA_KHARGA_NOON_LAG + 30) / 60).toLong() * 60
            val shown = day("eg.esa", kharga, date).dhuhr.epochSeconds
            assertTrue(shown >= latest, "Kharga's Dhuhr on $date is ${(latest - shown) / 60} min before ESA's latest")
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }

    @Test
    fun `rafah's fajr is never before esa's dawn south-west of the town at its latest rounding`() {
        // ESA prints Rafah's table from a point about 0.06° south and west of the town (ruling R160): its printed Fajr
        // is at most that point's 19.5° dawn plus 5 s (its own sun and the fit), to the nearest minute.
        val rafah = place(31.28204, 34.23869, "Africa/Cairo", "EG")
        everyDayAgainstEsa(rafah, ESA_RAFAH, -19.5, morning = true, ESA_RAFAH_DAWN_AFTER, start = true) { it.fajr.epochSeconds }
    }

    @Test
    fun `sidi barrani's isha is never before esa's dusk north of the town at its latest rounding`() {
        // ESA prints Sidi Barrani's table from a point about 0.16° north of the town (ruling R160): its printed Isha is
        // at most that point's 17.5° dusk plus 3 s (its own sun and the fit), to the nearest minute.
        val sidiBarrani = place(31.61166, 25.92539, "Africa/Cairo", "EG")
        everyDayAgainstEsa(sidiBarrani, ESA_SIDI_BARRANI, -17.5, morning = false, ESA_SIDI_BARRANI_DUSK_AFTER, start = true) {
            it.isha.epochSeconds
        }
    }

    @Test
    fun `sidi barrani's end of eating is never after esa's dawn north of the town at its earliest rounding`() {
        // The fast begins at ESA's printed Fajr, at least that point's 19.5° dawn less 4 s (its own sun and the fit), to
        // the nearest minute (ruling R160).
        val sidiBarrani = place(31.61166, 25.92539, "Africa/Cairo", "EG")
        everyDayAgainstEsa(sidiBarrani, ESA_SIDI_BARRANI, -19.5, morning = true, -ESA_SIDI_BARRANI_DAWN_BEFORE, start = false) {
            it.endOfEating.epochSeconds
        }
    }

    /**
     * Every day of 2026–2028 at [town], the engine's time (rounded as shown) against ESA's printed minute at its own
     * [esa] point: the sun at [altitude] there plus [allowance] s, to the nearest minute. A start is never before it,
     * an end never after it. No printed time is here (R69).
     */
    private fun everyDayAgainstEsa(
        town: Place, esa: GeoPoint, altitude: Double, morning: Boolean, allowance: Double, start: Boolean,
        shown: (PrayerDay) -> Long,
    ) {
        val zone = TimeZone.of(town.zoneId)
        var date = LocalDate(2026, 1, 1)
        while (date.year <= 2028) {
            val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
            val sun = assertNotNull(SunClock(esa.lat, esa.lon, date, offset, SunModel.EXACT).altitudeTime(altitude, morning))
            val printed = floor((sun + allowance + 30) / 60).toLong() * 60
            val time = shown(day("eg.esa", town, date))
            if (start) {
                assertTrue(time >= printed, "on $date ${(printed - time) / 60} min before ESA's latest")
            } else {
                assertTrue(time <= printed, "on $date ${(time - printed) / 60} min after ESA's earliest")
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
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
            Triple("eg.esa", place(31.28204, 34.23869, "Africa/Cairo", "EG"), "eg.esa"),
            Triple("eg.esa", place(31.61166, 25.92539, "Africa/Cairo", "EG"), "eg.esa"),
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

    private companion object {
        /** ESA's Kharga noon after the town's, at most: its point's 70 s and its sun's 4 s (ruling R159). */
        const val ESA_KHARGA_NOON_LAG = 74.0

        /** ESA's Rafah and Sidi Barrani points, fitted on their held days (ruling R160). */
        val ESA_RAFAH = GeoPoint(31.22204, 34.17869)
        val ESA_SIDI_BARRANI = GeoPoint(31.76666, 25.92539)

        /**
         * ESA's printed minute against this engine's sun at ESA's point, 2026–2028 (ruling R160): its almanac sun runs
         * −2.5 to +2.3 s from this core's at 19.5° dawn and −1.7 to +3.1 s at 17.5° dusk; the fitted offsets on the held
         * days add +2.3 s at Rafah's dawn, −0.4 s at Sidi Barrani's dusk and −0.9 s at its dawn.
         */
        const val ESA_RAFAH_DAWN_AFTER = 5.0
        const val ESA_SIDI_BARRANI_DUSK_AFTER = 3.0
        const val ESA_SIDI_BARRANI_DAWN_BEFORE = 4.0
    }
}
