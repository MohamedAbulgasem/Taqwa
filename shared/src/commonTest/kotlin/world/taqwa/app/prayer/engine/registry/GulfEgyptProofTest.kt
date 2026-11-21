package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.registry.ownLateLimitFor
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
import world.taqwa.app.prayer.engine.astro.altitudeTime
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.Egypt
import world.taqwa.app.prayer.engine.registry.authorities.Gulf
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
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

    /**
     * What rides beside the edge at [at] given [inner], the units an authority's own rule rides there (rulings R260 and
     * R262): [inner] where [ridingBeyond]'s nearest is among them or nothing rides beyond, else [inner] and [ridingBeyond]'s
     * units together, nearest first.
     */
    private fun withNearest(inner: List<AuthorityUnit>, all: List<AuthorityUnit>, at: GeoPoint): List<AuthorityUnit> {
        val beyond = ridingBeyond(all, at)
        if (beyond.isEmpty() || beyond.first() in inner) return inner
        return (inner + beyond.filter { it !in inner }).sortedBy { distanceKm(at, it.point) }
    }

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
        // Its recorded exceptions (the reach's measured figures, ruling R270, stand beside them in ReachLimits).
        assertEquals(3, ownLateLimitFor(TimedEvent.MAGHRIB, unit, r.entry)?.minutes)
        assertEquals(3, ownLateLimitFor(TimedEvent.ISHA, unit, r.entry)?.minutes)
        assertNull(ownLateLimitFor(TimedEvent.FAJR, unit, r.entry))
        // Az Za'ayin, 12 km south, is Doha's, within Doha's own limit.
        val zaayin = Registry.resolve(place(25.57744, 51.48306, "Asia/Qatar", "QA"))
        assertEquals("Doha", zaayin.unitName)
        assertNull(ownLateLimitFor(TimedEvent.MAGHRIB, Units.of("qa.calendarhouse")!!.unit("doha"), zaayin.entry))
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
    fun `oman's 86 localities are class b units and claim nothing beyond them`() {
        val units = assertNotNull(Units.of("om.mara"))
        assertEquals(86, units.units.size)
        val muscat = Registry.resolve(place(23.58413, 58.40778, "Asia/Muscat", "OM"))
        assertEquals("Muscat", muscat.unitName)
        assertEquals(EntryClass.B, muscat.entryClass)
        assertEquals(30.0, units.unit("muscat").radiusKm)
        // Rulings R166, R169: the ministry's other localities at their own fitted points, 20 km each, rounded to the
        // nearest minute (the national margins 30 s earlier at every event); the app's Sohar, Nizwa, Salalah and Bahla
        // at most 2.5 km from their own locality's point.
        val nearest = assertNotNull(units.unit("sohar").method)
        val base = assertNotNull(Registry.byId("om.mara")?.method)
        assertEquals(base.margins.fajr - 30, nearest.margins.fajr)
        assertEquals(base.margins.sunrise - 30, nearest.margins.sunrise)
        assertEquals(base.endOfEatingMarginSeconds - 30, nearest.endOfEatingMarginSeconds)
        assertNull(units.unit("muscat").method)
        for ((id, at) in listOf(
            "sohar" to GeoPoint(24.34745, 56.70937), "nizwa" to GeoPoint(22.93333, 57.53333),
            "salalah" to GeoPoint(17.01505, 54.09237), "bahla" to GeoPoint(22.97886, 57.3047),
        )) {
            val r = Registry.resolve(place(at.lat, at.lon, "Asia/Muscat", "OM"))
            assertEquals("om.mara", r.entry.id, id)
            assertEquals(id, r.unitId)
            assertEquals(units.unit(id).point, r.unitPoint, id)
            assertTrue(distanceKm(at, units.unit(id).point) < 2.5, id)
            assertTrue(r.measured, id)
            assertEquals(EntryClass.B, r.entryClass, id)
            assertEquals(20.0, units.unit(id).radiusKm, id)
        }
        // Seeb, no locality of the ministry's, is Muscat's.
        assertEquals("muscat", Registry.resolve(place(23.67027, 58.18911, "Asia/Muscat", "OM")).unitId)
        // Beyond every reach and ride (the Rub' al Khali), the edge as before.
        val beyond = Registry.resolve(place(20.5, 54.0, "Asia/Muscat", "OM"))
        assertNull(beyond.unitName)
        assertFalse(beyond.measured)
        assertEquals(EntryClass.D_AUTHORITY, beyond.entryClass)
        assertEquals(base.margins.dhuhr + 60, assertNotNull(beyond.method).margins.dhuhr)
        // Ruling R167: halfway between Bidbid's point and Muscat's, inside both reaches, the other rides beside as its own
        // day, since their margins differ (Muscat rounds up, Bidbid to the nearest minute).
        val (bidbid, muscatUnit) = units.unit("bidbid") to units.unit("muscat")
        val half = Registry.resolve(
            place((bidbid.point.lat + muscatUnit.point.lat) / 2, (bidbid.point.lon + muscatUnit.point.lon) / 2, "Asia/Muscat", "OM"),
        )
        assertTrue(half.unitId == "bidbid" || half.unitId == "muscat")
        val beside = assertNotNull(half.method).alongside.single()
        assertEquals(if (half.unitId == "bidbid") muscatUnit.point else bidbid.point, beside.point)
        assertEquals((if (half.unitId == "bidbid") base else nearest).margins, beside.method.margins)
        // Two localities sharing their margins ride as points: Al Wasil's beside Bidiyah's.
        val bidiyah = Registry.resolve(place(22.45, 58.8, "Asia/Muscat", "OM"))
        assertEquals("bidiyah", bidiyah.unitId)
        assertTrue(units.unit("al-wasil").point in assertNotNull(bidiyah.method).envelopePoints)
        // Ruling R170: Al Hamra's unit at the town, 12 km, its table's point 0.66° north riding beside; Bahla's own.
        val hamra = Registry.resolve(place(23.11667, 57.28333, "Asia/Muscat", "OM"))
        assertEquals("al-hamra", hamra.unitId)
        assertEquals("Al Hamra", hamra.unitName)
        assertTrue(GeoPoint(23.78, 57.285) in assertNotNull(hamra.method).envelopePoints)
        assertEquals(12.0, units.unit("al-hamra").radiusKm)
        assertEquals("bahla", Registry.resolve(place(22.97886, 57.3047, "Asia/Muscat", "OM")).unitId)
        // Near Al Hamra's table's own point (the coast by As Suwayq) nothing is named Al Hamra any more.
        assertTrue(Registry.resolve(place(23.78, 57.285, "Asia/Muscat", "OM")).unitId != "al-hamra")
        // Each locality's own point is its own.
        for (u in units.units) {
            assertEquals(u.id, Registry.resolve(place(u.point.lat, u.point.lon, "Asia/Muscat", "OM")).unitId)
        }
        // A locality's own point with no other reach holding it carries nothing beside it.
        val masirah = units.unit("masirah").point
        val alone = assertNotNull(Registry.resolve(place(masirah.lat, masirah.lon, "Asia/Muscat", "OM")).method)
        assertTrue(alone.envelopePoints.isEmpty() && alone.alongside.isEmpty())
    }

    @Test
    fun `an oman locality's table rides past its own circle on the edge`() {
        // Ruling R210 (the audit of 12 Nov 2026): the ministry prints by locality, with no boundaries, so just past a
        // circle, where no other locality's circle holds the place, the locality's table is still read: half again the
        // circle, as its own day beside the user's own. Inside another locality's circle nothing rides but a table
        // whose own circle holds the place too (R167).
        val units = assertNotNull(Units.of("om.mara"))
        val base = assertNotNull(Registry.byId("om.mara")?.method)
        fun at(p: GeoPoint) = Registry.resolve(place(p.lat, p.lon, "Asia/Muscat", "OM"))
        fun north(p: GeoPoint, km: Double) = GeoPoint(p.lat + km / 111.195, p.lon)
        fun read(m: TimetableMethod) = m.envelopePoints + m.alongside.map { it.point }
        // The Rub' al Khali, about 140 km from Muqshin, the nearest locality: past every ride, R260's too.
        val plain = assertNotNull(at(GeoPoint(20.5, 54.0)).method)
        assertTrue(plain.envelopePoints.isEmpty() && plain.alongside.isEmpty())
        // Ruling R240: the edge stands a minute to the safe side of every table it lies beside, whichever way that
        // table is rounded: its starts a minute after the later rounding (Muscat's, up), its sunrise and end of eating a
        // minute before the earlier (the localities', to the nearest).
        fun starts(m: TimetableMethod) = listOf(m.margins.fajr, m.margins.dhuhr, m.margins.asr, m.margins.maghrib, m.margins.isha)
        for (u in units.units) {
            val table = u.method ?: base
            starts(plain).zip(starts(table)).forEach { (e, t) -> assertTrue(e >= t + 60, "${u.id}: the edge's start margin $e against its $t") }
            assertTrue(plain.margins.sunrise <= table.margins.sunrise - 60, u.id)
            assertTrue(plain.endOfEatingMarginSeconds <= table.endOfEatingMarginSeconds - 60, u.id)
        }
        var onTheEdge = 0
        var inAnother = 0
        var pastTheRide = 0
        for (u in units.units) {
            assertTrue(Gulf.omanRideKm(u) > u.radiusKm, u.id)
            val past = at(north(u.point, u.radiusKm + 0.5))
            assertTrue(past.unitId != u.id, u.id)
            val m = assertNotNull(past.method)
            if (past.unitId == null) {
                onTheEdge++
                // On the edge: the user's own day with the edge's margins, the locality's own day with its own beside it.
                assertTrue(u.point in read(m), "${u.id}: its table does not ride just past its circle on the edge")
                assertEquals(EntryClass.D_AUTHORITY, past.entryClass, u.id)
                assertFalse(past.measured, u.id)
                assertNull(m.fixedPoint, u.id)
                assertEquals(plain.margins, m.margins, u.id)
                assertTrue(m.envelopePoints.isEmpty(), u.id)
                val beside = m.alongside.first { it.point == u.point }
                assertEquals((u.method ?: base).margins, beside.method.margins, u.id)
            } else {
                inAnother++
                // Inside another locality's circle: that locality's printed table holds the place, and this one's is not read.
                assertTrue(u.point !in read(m), "${u.id}: its table rides past its circle inside ${past.unitId}'s")
            }
            // An alongside day carries nothing beside it of its own.
            for (a in m.alongside) assertTrue(a.method.alongside.isEmpty() && a.method.envelopePoints.isEmpty(), u.id)
            // Past the end of its ride, where no other ride holds the place, the nearest locality and any about as near
            // ride (ruling R260), as their own days: exactly those, and none inside another locality's circle.
            val beyondAt = north(u.point, Gulf.omanRideKm(u) + 0.5)
            val beyond = at(beyondAt)
            val bm = assertNotNull(beyond.method)
            if (beyond.unitId == null && units.units.none { distanceKm(beyondAt, it.point) <= Gulf.omanRideKm(it) }) {
                pastTheRide++
                assertEquals(ridingBeyond(units.units, beyondAt).map { it.point }.toSet(), read(bm).toSet(), "${u.id} past its ride")
                assertEquals(plain.margins, bm.margins, u.id)
                assertNull(bm.fixedPoint, u.id)
            } else if (beyond.unitId != null) {
                assertTrue(u.point !in read(bm) || distanceKm(beyondAt, u.point) <= u.radiusKm, "${u.id}: rides past its ride inside ${beyond.unitId}'s circle")
            }
            // Its own point stays its own unit's, with its own margins, and reads no table whose circle does not hold it.
            val own = at(u.point)
            assertEquals(u.id, own.unitId)
            val mine = assertNotNull(own.method)
            assertEquals((u.method ?: base).margins, mine.margins, u.id)
            for (other in units.units) {
                if (other.id != u.id && distanceKm(u.point, other.point) > other.radiusKm) {
                    assertTrue(other.point !in read(mine), "${u.id}: its own point reads ${other.id}'s table, whose circle does not hold it")
                }
            }
        }
        assertTrue(onTheEdge > 0 && inAnother > 0 && pastTheRide > 0, "every side of the rule is met: $onTheEdge on the edge, $inAnother inside another circle, $pastTheRide past a ride")
        // The audit's places: As Sifah, east of Muscat's circle (41 km), reads Muscat's table as its own day; Jebel Shams,
        // north of Al Hamra's (14 km), Al Hamra's at the town and at its table's own point.
        val sifah = at(GeoPoint(23.44, 58.78))
        assertNull(sifah.unitId)
        val muscat = assertNotNull(sifah.method).alongside.single { it.point == units.unit("muscat").point }
        assertEquals(base.margins, muscat.method.margins)
        val shams = at(GeoPoint(23.24, 57.26))
        assertNull(shams.unitId)
        val hamra = assertNotNull(shams.method).alongside.filter { it.method.margins == assertNotNull(units.unit("al-hamra").method).margins }.map { it.point }
        assertTrue(units.unit("al-hamra").point in hamra && GeoPoint(23.78, 57.285) in hamra)
    }

    @Test
    fun `past an oman locality's ride the nearest locality's own day still rides beside the edge`() {
        // Ruling R260 (the second audit's G3, 14 Nov 2026): past every ride, where no circle holds the place, the
        // nearest locality's own day and any locality no more than a quarter farther ride beside the edge's, out to five
        // minutes of longitude from the locality's point; farther, the edge alone. The audit's points: 30.5 km west of
        // Adam (an end of eating after Adam's printed Fajr) and 37 km south-east of it (an Isha before Adam's).
        val units = assertNotNull(Units.of("om.mara"))
        val adam = units.unit("adam")
        val zone = TimeZone.of("Asia/Muscat")
        val entry = assertNotNull(Registry.byId("om.mara"))
        for (p in listOf(GeoPoint(22.36973, 57.23839), GeoPoint(22.13451, 57.78901))) {
            val r = Registry.resolve(place(p.lat, p.lon, "Asia/Muscat", "OM"))
            assertNull(r.unitId, "$p")
            assertEquals(EntryClass.D_AUTHORITY, r.entryClass, "$p")
            assertTrue(units.units.none { distanceKm(p, it.point) <= Gulf.omanRideKm(it) }, "$p is past every ride")
            val m = assertNotNull(r.method)
            val riding = ridingBeyond(units.units, p)
            assertEquals(adam, riding.first(), "$p")
            assertEquals(riding.map { it.point }.toSet(), m.alongside.map { it.point }.toSet(), "$p")
            val alone = m.copy(alongside = emptyList())
            for (date in daysOf2026(step = 5)) {
                val d = DayPipeline.unended(r, date, zone)
                val table = DayComputer.compute(adam.method ?: entry.method!!, adam.point, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                val edge = DayComputer.compute(alone, p, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                for (floor in listOf(table, edge)) {
                    assertTrue(d.fajr >= floor.fajr && d.dhuhr >= floor.dhuhr && d.asr >= floor.asr, "$p $date")
                    assertTrue(d.maghrib >= floor.maghrib && d.isha >= floor.isha, "$p $date")
                    assertTrue(d.sunrise <= floor.sunrise && d.endOfEating <= floor.endOfEating, "$p $date")
                }
            }
        }
        // The rule itself, for every locality: a locality rides only as the nearest or within a quarter of the nearest's
        // distance, and never farther than five minutes of longitude from its point.
        for (u in units.units) {
            val far = GeoPoint(u.point.lat - (edgeRideKm(u) + 1.0) / 111.195, u.point.lon)
            assertTrue(u !in ridingBeyond(units.units, far), u.id)
            val near = GeoPoint(u.point.lat - (Gulf.omanRideKm(u) + 1.0) / 111.195, u.point.lon)
            val riding = ridingBeyond(units.units, near)
            val nearest = units.units.minOf { distanceKm(near, it.point) }
            assertTrue(riding.isNotEmpty() && riding.all { distanceKm(near, it.point) <= nearest * EDGE_NEAR_FACTOR }, u.id)
            assertEquals(riding.sortedBy { distanceKm(near, it.point) }, riding, u.id)
        }
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
        // El Mahalla El Kubra's table is printed from about 21 km west of the town and Baltim's from about 20 km south
        // (ruling R164): Mahalla's starts are its own and its ends the national ones; Baltim's dawn, Asr, Maghrib and
        // sunrise are its own.
        val mahalla = assertNotNull(units.unit("el-mahalla-el-kubra").method)
        starts(mahalla).zip(starts(national)).forEach { (own, n) -> assertTrue(own > n, "Mahalla: start margin $own") }
        assertEquals(national.margins.sunrise, mahalla.margins.sunrise)
        assertEquals(national.endOfEatingMarginSeconds, mahalla.endOfEatingMarginSeconds)
        val baltim = assertNotNull(units.unit("baltim").method)
        assertEquals(national.margins.copy(fajr = baltim.margins.fajr, sunrise = baltim.margins.sunrise, asr = baltim.margins.asr, maghrib = baltim.margins.maghrib), baltim.margins)
        assertTrue(baltim.margins.fajr > national.margins.fajr && baltim.margins.asr > national.margins.asr && baltim.margins.maghrib > national.margins.maghrib)
        assertTrue(baltim.margins.sunrise < national.margins.sunrise)
        assertEquals(national.endOfEatingMarginSeconds, baltim.endOfEatingMarginSeconds)
        // Taba's own earlier sunrise and dawn: 3 min before its table's at the town, 4 across its reach (ruling R181).
        val taba = units.unit("taba")
        assertEquals(4, lateLimitFor(TimedEvent.SUNRISE, taba, Registry.byId("eg.esa")!!)?.minutes)
        assertNull(lateLimitFor(TimedEvent.MAGHRIB, taba, Registry.byId("eg.esa")!!))
        // Beyond every unit a start waits at least as long as Taba's own, and the ends come before Ras Gharib's.
        val edge = units.outside(GeoPoint(27.0, 31.0))
        starts(edge).zip(starts(assertNotNull(taba.method))).forEach { (e, t) -> assertTrue(e >= t, "edge $e against Taba's $t") }
        assertTrue(edge.margins.sunrise <= rasGharib.margins.sunrise)
        assertTrue(edge.endOfEatingMarginSeconds <= rasGharib.endOfEatingMarginSeconds)
    }

    @Test
    fun `esa's 48 other cities are its units and where two reaches overlap the other town rides beside`() {
        // Ruling R163: the cities ESA's daily page prints beside the first 30 are units of 20 km, class B and measured,
        // at the app's point for the city (Tanta, Asyut) or ESA's own fitted point (Marsa Alam, 6th of October); 21 km
        // south of Tanta's, the edge as before.
        val units = assertNotNull(Units.of("eg.esa"))
        assertEquals(78, units.units.size)
        for ((id, point) in listOf(
            "tanta" to GeoPoint(30.78847, 31.00192), "asyut" to GeoPoint(27.18096, 31.18368),
            "marsa-alam" to GeoPoint(25.06, 34.88), "6th-of-october" to GeoPoint(29.95, 30.93),
        )) {
            val r = Registry.resolve(place(point.lat, point.lon, "Africa/Cairo", "EG"))
            assertEquals("eg.esa", r.entry.id, id)
            assertEquals(id, r.unitId)
            assertEquals(point, r.unitPoint, id)
            assertTrue(r.measured, id)
            assertEquals(EntryClass.B, r.entryClass, id)
            assertEquals(20.0, units.unit(id).radiusKm, id)
        }
        val beyond = Registry.resolve(place(30.78847 - 21.0 / 111.19, 31.00192, "Africa/Cairo", "EG"))
        assertNull(beyond.unitId)
        assertEquals(EntryClass.D_AUTHORITY, beyond.entryClass)
        // The app's 6th of October City, 19 km south-east of ESA's point, is that town's, its own point riding beside.
        assertEquals("6th-of-october", Registry.resolve(place(29.81667, 31.05, "Africa/Cairo", "EG")).unitId)
        // New Cairo's point is inside Cairo's 30 km: New Cairo holds it and Cairo's point rides beside (ruling R165).
        val newCairo = Registry.resolve(place(30.03, 31.47, "Africa/Cairo", "EG"))
        assertEquals("new-cairo", newCairo.unitId)
        assertEquals(listOf(GeoPoint(30.08, 31.27)), assertNotNull(newCairo.method).envelopePoints)
        // Aga, nearer El Mahalla El Kubra but inside Mansoura's reach: Mansoura's own day rides beside Mahalla's, which
        // has margins of its own, and the reverse at Nabaruh.
        val aga = Registry.resolve(place(30.94162, 31.29039, "Africa/Cairo", "EG"))
        assertEquals("el-mahalla-el-kubra", aga.unitId)
        assertEquals(listOf(GeoPoint(31.03637, 31.38069)), assertNotNull(aga.method).alongside.map { it.point })
        assertEquals(emptyList(), aga.method!!.envelopePoints)
        val nabaruh = Registry.resolve(place(31.09755, 31.30312, "Africa/Cairo", "EG"))
        assertEquals("mansoura", nabaruh.unitId)
        val mahallaDay = assertNotNull(nabaruh.method).alongside.single()
        assertEquals(GeoPoint(30.97063, 31.1669), mahallaDay.point)
        assertEquals(units.unit("el-mahalla-el-kubra").method!!.margins, mahallaDay.method.margins)
        // A town's own point with no other reach holding it carries nothing beside it.
        val tanta = assertNotNull(Registry.resolve(place(30.78847, 31.00192, "Africa/Cairo", "EG")).method)
        assertTrue(tanta.envelopePoints.isEmpty() && tanta.alongside.isEmpty())
    }

    @Test
    fun `an egyptian place is called by its own name and never by the unit's`() {
        // The audit of 12 Nov 2026 (finding F34): Helwan, 4 km from 15th of May's point, read "the prayer times used in
        // 15th of May", Rosetta "New Rashid", and Mansoura's Arabic sentence carried "Mansoura" in Latin letters. No
        // Egyptian unit names the place: About and the site call it by the app's own name for it in the reader's
        // language (a null label), in a unit's own town, in another town its reach takes in, and beyond every unit.
        for (unit in assertNotNull(Units.of("eg.esa")).units) {
            val r = Registry.resolve(place(unit.point.lat, unit.point.lon, "Africa/Cairo", "EG"))
            assertEquals(unit.id, r.unitId)
            assertEquals(unit.name, r.unitName, unit.id)
            assertNull(r.unitLabel, unit.id)
            assertNull(r.unitCityId, unit.id)
        }
        for ((town, at) in listOf(
            "15th-of-may" to GeoPoint(29.84144, 31.30084), // Helwan
            "new-rashid" to GeoPoint(31.39951, 30.41718), // Rosetta
        )) {
            val r = Registry.resolve(place(at.lat, at.lon, "Africa/Cairo", "EG"))
            assertEquals(town, r.unitId)
            assertNull(r.unitLabel, town)
        }
        assertNull(Registry.resolve(place(26.0, 28.0, "Africa/Cairo", "EG")).unitLabel)
    }

    @Test
    fun `past an egyptian town's reach its table rides beside the edge out to twice the reach`() {
        // Ruling R213 (the audit of 12 Nov 2026, finding F13): just past a town's reach the edge alone showed an end
        // after the town's table and, east of a town printed from a point off it, a start before it. Out to twice its
        // reach a town's own day at its own point is a day alongside the edge's; farther, the edge alone as before.
        val units = assertNotNull(Units.of("eg.esa"))
        val national = assertNotNull(Registry.byId("eg.esa")?.method)
        fun from(unit: AuthorityUnit, km: Double, east: Boolean = false): Place {
            val p = if (east) GeoPoint(unit.point.lat, unit.point.lon + km / (111.19 * cos(unit.point.lat * DEG))) else GeoPoint(unit.point.lat - km / 111.19, unit.point.lon)
            return place(p.lat, p.lon, "Africa/Cairo", "EG")
        }
        // Siwa stands alone: its unit to 20 km, its table beside the edge to 40 (R213), past the belt still as the
        // nearest town (R260) out to five minutes of longitude, about 121 km, and the edge alone beyond.
        val siwa = units.unit("siwa")
        assertEquals("siwa", Registry.resolve(from(siwa, 19.0)).unitId)
        val far = Registry.resolve(from(siwa, 41.0))
        assertNull(far.unitId)
        assertEquals(listOf(siwa.point), assertNotNull(far.method).alongside.map { it.point })
        val farther = Registry.resolve(from(siwa, edgeRideKm(siwa) + 1.0))
        assertNull(farther.unitId)
        assertTrue(assertNotNull(farther.method).alongside.isEmpty())
        for (km in listOf(20.5, 30.0, 39.5)) {
            val belt = Registry.resolve(from(siwa, km))
            assertNull(belt.unitId, "$km km")
            assertEquals(EntryClass.D_AUTHORITY, belt.entryClass, "$km km")
            val beside = assertNotNull(belt.method).alongside.single()
            assertEquals(siwa.point, beside.point, "$km km")
            assertEquals(national.margins, beside.method.margins, "$km km")
            // The place's own day is the edge's, as it is beyond the belt.
            assertEquals(far.method!!.margins, belt.method!!.margins, "$km km")
            assertEquals(far.method!!.endOfEatingMarginSeconds, belt.method!!.endOfEatingMarginSeconds, "$km km")
        }
        // A town printed from a point off the town rides with its own margins.
        val taba = units.unit("taba")
        val eastOfTaba = Registry.resolve(from(taba, 30.0, east = true))
        assertNull(eastOfTaba.unitId)
        assertEquals(assertNotNull(taba.method).margins, assertNotNull(eastOfTaba.method).alongside.single { it.point == taba.point }.method.margins)
        // Every town within twice its own reach rides, the nearest first, and beyond those only the nearest town and any
        // about as near where the nearest's own belt does not hold the place (ruling R262): Cairo's reach is 30 km.
        for (unit in units.units) {
            for (place in listOf(from(unit, unit.radiusKm * 1.5), from(unit, unit.radiusKm * 1.5, east = true))) {
                val at = GeoPoint(place.lat, place.lon)
                val riding = Egypt.beside(at)
                assertTrue(unit in riding, "${unit.id} rides at one and a half of its reach")
                val belts = units.units.filter { distanceKm(at, it.point) <= it.radiusKm * Egypt.BESIDE_REACHES }.sortedBy { distanceKm(at, it.point) }
                assertEquals(withNearest(belts, units.units, at), riding, unit.id)
                assertEquals(riding.sortedBy { distanceKm(at, it.point) }, riding, unit.id)
            }
            // Past every belt the nearest town and any about as near ride instead (ruling R260), and past the nearest
            // town's own belt beside the farther belts that hold the place (ruling R262).
            val past = GeoPoint(unit.point.lat - unit.radiusKm * 2.05 / 111.19, unit.point.lon)
            val belts = units.units.filter { distanceKm(past, it.point) <= it.radiusKm * Egypt.BESIDE_REACHES }.sortedBy { distanceKm(past, it.point) }
            assertEquals(withNearest(belts, units.units, past), Egypt.beside(past), "${unit.id} at 2.05 reaches")
        }
        // The rule itself: in the belt no start is before the town's own day at its own point and no end after it,
        // nor before or after the edge's at the place.
        val zone = TimeZone.of("Africa/Cairo")
        val entry = assertNotNull(Registry.byId("eg.esa"))
        for (unit in listOf("siwa", "taba", "ras-gharib", "sidi-barrani", "kharga", "baltim", "el-mahalla-el-kubra", "cairo", "aswan").map(units::unit)) {
            for (east in listOf(false, true)) for (share in listOf(1.05, 1.5, 1.95)) {
                val at = from(unit, unit.radiusKm * share, east)
                val shown = Registry.resolveEntry(entry, at)
                if (shown.unitId != null) continue // another town's reach holds the place: R165's rule, not this one.
                val alone = assertNotNull(shown.method).copy(alongside = emptyList())
                for (date in daysOf2026(step = 9)) {
                    val d = DayPipeline.unended(shown, date, zone)
                    val table = DayComputer.compute(unit.method ?: national, unit.point, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                    val edge = DayComputer.compute(alone, GeoPoint(at.lat, at.lon), date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                    val where = "${unit.id} at $share of its reach, east $east, on $date"
                    for (floor in listOf(table, edge)) {
                        assertTrue(d.fajr >= floor.fajr && d.dhuhr >= floor.dhuhr && d.asr >= floor.asr, where)
                        assertTrue(d.maghrib >= floor.maghrib && d.isha >= floor.isha, where)
                        assertTrue(d.sunrise <= floor.sunrise && d.endOfEating <= floor.endOfEating, where)
                    }
                }
            }
        }
    }

    @Test
    fun `past every egyptian belt the nearest town's table and any about as near ride beside the edge`() {
        // Ruling R260 (the second audit's G2, 14 Nov 2026): the app's an-Najaylah stands 62 km from Marsa Matruh's point
        // and 66 km from Sidi Barrani's, past every belt. It ended the fast after Marsa Matruh's printed Fajr on every day
        // of 2026 and began Maghrib before Sidi Barrani's on some; both towns' own days now ride beside the edge's, the
        // nearer first, and nothing is earlier than the edge alone nor before either town's own day.
        val units = assertNotNull(Units.of("eg.esa"))
        val entry = assertNotNull(Registry.byId("eg.esa"))
        val zone = TimeZone.of("Africa/Cairo")
        val najaylah = GeoPoint(31.4071, 26.5832)
        val r = Registry.resolve(place(najaylah.lat, najaylah.lon, "Africa/Cairo", "EG"))
        assertNull(r.unitId)
        assertEquals(EntryClass.D_AUTHORITY, r.entryClass)
        val towns = listOf(units.unit("marsa-matruh"), units.unit("sidi-barrani"))
        assertTrue(units.units.none { distanceKm(najaylah, it.point) <= it.radiusKm * Egypt.BESIDE_REACHES })
        assertEquals(towns, Egypt.beside(najaylah))
        val m = assertNotNull(r.method)
        assertEquals(towns.map { it.point }, m.alongside.map { it.point })
        val alone = m.copy(alongside = emptyList())
        for (date in daysOf2026(step = 1)) {
            val d = DayPipeline.unended(r, date, zone)
            val edge = DayComputer.compute(alone, najaylah, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
            val floors = towns.map { DayComputer.compute(it.method ?: entry.method!!, it.point, date, zone, entry.school, Registry.ramadanCalendarFor(entry)) } + edge
            for (floor in floors) {
                assertTrue(d.fajr >= floor.fajr && d.dhuhr >= floor.dhuhr && d.asr >= floor.asr, "$date")
                assertTrue(d.maghrib >= floor.maghrib && d.isha >= floor.isha, "$date")
                assertTrue(d.sunrise <= floor.sunrise && d.endOfEating <= floor.endOfEating, "$date")
            }
        }
        // The app's other cities past every belt each read their nearest town.
        for ((city, nearest) in listOf(
            GeoPoint(24.47669, 32.94626) to "aswan", GeoPoint(27.73264, 30.84129) to "minya", GeoPoint(27.55642, 30.81057) to "asyut",
            GeoPoint(27.44023, 30.81712) to "asyut", GeoPoint(27.64148, 30.85012) to "minya", GeoPoint(29.11007, 32.66012) to "abu-zenima",
        )) {
            val here = Registry.resolve(place(city.lat, city.lon, "Africa/Cairo", "EG"))
            assertNull(here.unitId, "$city")
            assertEquals(units.unit(nearest).point, assertNotNull(here.method).alongside.first().point, "$city")
        }
    }

    @Test
    fun `past the nearest locality's belt or ride inside a farther one's the nearest still rides`() {
        // Ruling R262 (the edges lane's second review, 15 Nov 2026): R260 rode the nearest locality only where no inner
        // rule rode anything, so 3 km past Quriyat's 30 km ride, inside Muscat's 45 km one, Muscat's table alone rode and
        // the end of eating was a minute after Quriyat's printed Fajr on some days of 2027; half a kilometre past Tanta's
        // belt, inside Cairo's, the same against Tanta. The nearest now rides beside the farther locality's table, and
        // nothing is before either's own day, nor any end after it, nor earlier than the edge alone.
        val cases = listOf(
            Triple("om.mara", GeoPoint(23.27329, 58.61653), listOf("quriyat", "muscat")),
            Triple("eg.esa", GeoPoint(30.42424, 31.00192), listOf("tanta", "cairo")),
        )
        for ((id, p, expected) in cases) {
            val units = assertNotNull(Units.of(id))
            val entry = assertNotNull(Registry.byId(id))
            val zoneId = if (id == "om.mara") "Asia/Muscat" else "Africa/Cairo"
            val zone = TimeZone.of(zoneId)
            val r = Registry.resolve(place(p.lat, p.lon, zoneId, if (id == "om.mara") "OM" else "EG"))
            assertNull(r.unitId, "$p")
            val nearest = units.units.minBy { distanceKm(p, it.point) }
            assertEquals(expected.first(), nearest.id, "$p")
            val m = assertNotNull(r.method)
            val read = (m.envelopePoints + m.alongside.map { it.point }).toSet()
            for (u in expected.map(units::unit)) assertTrue(u.point in read, "$p reads ${u.id}")
            val alone = m.copy(alongside = emptyList(), envelopePoints = emptyList())
            for (date in daysOf2026(step = 3)) {
                val d = DayPipeline.unended(r, date, zone)
                val edge = DayComputer.compute(alone, p, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                val floors = expected.map(units::unit).map {
                    DayComputer.compute(it.method ?: entry.method!!, it.point, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                } + edge
                for (floor in floors) {
                    assertTrue(d.fajr >= floor.fajr && d.dhuhr >= floor.dhuhr && d.asr >= floor.asr, "$p $date")
                    assertTrue(d.maghrib >= floor.maghrib && d.isha >= floor.isha, "$p $date")
                    assertTrue(d.sunrise <= floor.sunrise && d.endOfEating <= floor.endOfEating, "$p $date")
                }
            }
        }
        // The rule itself, for every Omani locality: just past its ride, wherever it is the nearest and no circle holds
        // the place, it rides, whatever farther ride holds the place too.
        val oman = assertNotNull(Units.of("om.mara"))
        var inside = 0
        for (u in oman.units) for (bearing in 0 until 16) {
            val km = Gulf.omanRideKm(u) + 0.5
            val a = bearing * 22.5 * DEG
            val at = GeoPoint(u.point.lat + km * cos(a) / 111.195, u.point.lon + km * sin(a) / (111.195 * cos(u.point.lat * DEG)))
            if (oman.units.minBy { distanceKm(at, it.point) } != u) continue
            val r = Registry.resolve(place(at.lat, at.lon, "Asia/Muscat", "OM"))
            if (r.unitId != null) continue
            if (oman.units.any { distanceKm(at, it.point) <= Gulf.omanRideKm(it) }) inside++
            val m = assertNotNull(r.method)
            assertTrue(u.point in m.envelopePoints + m.alongside.map { it.point }, "${u.id} at bearing ${bearing * 22.5}")
        }
        assertTrue(inside > 0, "no point past a ride inside another")
    }

    @Test
    fun `an egyptian town's exception is its reach's third minute and never the entry's`() {
        // Ruling R181: ESA's entry keeps class B's 2 min; a town whose own tables, replayed across its reach, read 3 on
        // some days records that for those events alone, its later starts and its earlier ends each as what they are;
        // Taba's ends, already 3 at the town, 4 and never a start.
        val entry = assertNotNull(Registry.byId("eg.esa"))
        assertTrue(entry.lateLimits.isEmpty())
        val units = assertNotNull(Units.of("eg.esa"))
        val ends = setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING)
        val prayed = setOf(TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA)
        for (unit in units.units) {
            for (limit in unit.lateLimits) {
                assertTrue(limit.events.all { it in ends } || limit.events.all { it in prayed }, "${unit.id}: starts or ends, not both")
                assertEquals(if (unit.id == "taba") 4 else 3, limit.minutes, unit.id)
                assertTrue("reach" in limit.reason && unit.name in limit.reason, "${unit.id}: the reason names the town's reach")
            }
        }
        assertEquals(ends, units.unit("taba").lateLimits.single().events)
        // Cairo, whose 30 km takes in other towns' reaches: its starts and its end of eating, and Dhuhr within the class's 2.
        val cairo = units.unit("cairo")
        assertEquals(3, lateLimitFor(TimedEvent.MAGHRIB, cairo, entry)?.minutes)
        assertEquals(3, lateLimitFor(TimedEvent.END_OF_EATING, cairo, entry)?.minutes)
        assertNull(lateLimitFor(TimedEvent.DHUHR, cairo, entry))
        // A town whose replay stayed within 2 everywhere keeps the class limit.
        assertTrue(units.unit("alexandria").lateLimits.isEmpty())
        assertTrue(units.units.count { it.lateLimits.isEmpty() } > units.units.size / 2, "most towns keep the class limit")
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

    @Test
    fun `esa's 48 cities of 12 november 2026 keep esa's own rule projected to 2028`() {
        // Ruling R172: ESA serves no day past 2026, so its rule is projected. At each town's ESA point (fitted on its
        // 2026 year, archive/code/manual/eg-esa/esa_points.py), ESA's own almanac sun with one offset an event: for a
        // start the smallest any printed minute of 2026 allows, for sunrise and the fast the largest; every day of
        // 2026 to 2028 at the town's unit, the start shown is never before that projection and the end never after
        // it. No printed time is here (R69). It holds the engine to the projection, and the projection is not a bound
        // on ESA's print (the review of 13 Nov 2026): no one offset reproduces every minute of 2026, whose print is a
        // minute after the projection on about one event-day in twenty. What ESA printed is held by the gate on its
        // tables; what it will print for 2027, only by its 2027 tables once it serves them.
        val units = assertNotNull(Units.of("eg.esa"))
        for (town in ESA_TOWNS) {
            val at = units.unit(town.id).point
            val place = place(at.lat, at.lon, "Africa/Cairo", "EG")
            var date = LocalDate(2026, 1, 1)
            while (date.year <= 2028) {
                val esa = esaTimes(date, town.lat, town.lon)
                val shown = day("eg.esa", place, date)
                val starts = listOf(shown.fajr, shown.dhuhr, shown.asr, shown.maghrib, shown.isha)
                for ((i, k) in listOf(0, 2, 3, 4, 5).withIndex()) {
                    val latest = nearestMinute(esa[k] + town.starts[i])
                    assertTrue(starts[i].epochSeconds >= latest, "${town.id} ${"FDAMI"[i]} on $date: ${(latest - starts[i].epochSeconds) / 60} min before ESA's latest")
                }
                assertTrue(shown.sunrise.epochSeconds <= nearestMinute(esa[1] + town.sunrise), "${town.id} sunrise on $date after ESA's earliest")
                assertTrue(shown.endOfEating.epochSeconds <= nearestMinute(esa[0] + town.dawn), "${town.id} end of eating on $date after ESA's earliest")
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    /** ESA's printed minute for a moment [seconds] (epoch s): to the nearest minute. */
    private fun nearestMinute(seconds: Double): Long = floor((seconds + 30) / 60).toLong() * 60

    /**
     * ESA's own sun (R159: PrayTimes' almanac, the sun at local noon) at [lat], [lon] on [date]: Fajr 19.5°, sunrise 0.833°, Dhuhr, the Asr shadow at the noon declination, Maghrib 0.833°, Isha 17.5°,
     * as epoch seconds, unrounded.
     */
    private fun esaTimes(date: LocalDate, lat: Double, lon: Double): DoubleArray {
        val jd = date.toEpochDays() + 2440587.5 - lon / 360.0
        val la = lat * DEG

        fun sun(t: Double): Pair<Double, Double> {
            val d = jd + t - 2451545.0
            val g = (357.529 + 0.98560028 * d) * DEG
            val q = 280.459 + 0.98564736 * d
            val lam = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * DEG
            val e = (23.439 - 0.00000036 * d) * DEG
            val ra = atan2(cos(e) * sin(lam), cos(lam)) / DEG / 15
            val eqt = mod(q / 15 - mod(ra, 24.0) + 12, 24.0) - 12
            return asin(sin(e) * sin(lam)) to eqt
        }
        fun mid(t: Double) = mod(12 - sun(t).second, 24.0)
        fun hourAngle(altitude: Double, decl: Double) =
            acos(((-sin(altitude * DEG) - sin(decl) * sin(la)) / (cos(decl) * cos(la))).coerceIn(-1.0, 1.0)) / DEG / 15
        fun angle(a: Double, t: Double, morning: Boolean) = mid(t) + hourAngle(a, sun(t).first) * if (morning) -1 else 1
        val noonDecl = sun(13.0 / 24).first
        val asrAltitude = -atan(1 / (1 + tan(abs(la - noonDecl)))) / DEG
        val hours = listOf(
            angle(19.5, 5.0 / 24, true), angle(0.833, 6.0 / 24, true), mid(12.0 / 24),
            mid(13.0 / 24) + hourAngle(asrAltitude, noonDecl), angle(0.833, 18.0 / 24, false), angle(17.5, 18.0 / 24, false),
        )
        // The hours are UTC less the longitude's; the day's UTC midnight is epoch days × 86,400.
        return DoubleArray(6) { date.toEpochDays() * 86400.0 + (hours[it] - lon / 15) * 3600 }
    }

    private fun mod(x: Double, m: Double) = ((x % m) + m) % m

    /** A town's ESA point and its offsets to ESA's sun, s: the latest for Fajr, Dhuhr, Asr, Maghrib and Isha, the earliest for sunrise and dawn. */
    private class EsaTown(val id: String, val lat: Double, val lon: Double, val starts: List<Double>, val sunrise: Double, val dawn: Double)

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
            Triple("eg.esa", place(30.97063, 31.1669, "Africa/Cairo", "EG"), "eg.esa"),
            Triple("eg.esa", place(31.55582, 31.08918, "Africa/Cairo", "EG"), "eg.esa"),
            // Oman's app towns beside the ministry's points (rulings R166, R169), and Al Hamra (R170).
            Triple("om.mara", place(24.34745, 56.70937, "Asia/Muscat", "OM"), null),
            Triple("om.mara", place(17.01505, 54.09237, "Asia/Muscat", "OM"), null),
            Triple("om.mara", place(23.11667, 57.28333, "Asia/Muscat", "OM"), null),
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
        private const val DEG = kotlin.math.PI / 180

        /** ESA's 48 cities of R163 (R172): each one's ESA point fitted on its 2026 year and its offsets (esa_points.py offsets). */
        private val ESA_TOWNS = listOf(
            EsaTown("tanta", 30.790, 30.992, starts = listOf(-2.3, -1.8, -11.4, 1.6, -6.6), sunrise = -2.7, dawn = 3.1),
            EsaTown("mansoura", 31.062, 31.394, starts = listOf(-0.7, -1.9, -10.3, 0.4, -9.3), sunrise = -2.1, dawn = 6.0),
            EsaTown("el-mahalla-el-kubra", 30.984, 30.946, starts = listOf(-3.4, -2.0, -11.7, 1.7, -5.2), sunrise = -2.1, dawn = 3.1),
            EsaTown("zagazig", 30.590, 31.498, starts = listOf(-1.2, -1.9, -8.3, 1.0, -6.5), sunrise = -1.8, dawn = 3.4),
            EsaTown("asyut", 27.186, 31.174, starts = listOf(-1.8, -1.4, -8.4, 0.7, -6.9), sunrise = -2.6, dawn = 3.0),
            EsaTown("faiyum", 29.308, 30.838, starts = listOf(0.0, 1.0, -7.2, 3.4, -5.8), sunrise = -0.7, dawn = 3.8),
            EsaTown("esna", 25.288, 32.554, starts = listOf(-2.8, -1.1, -6.9, 1.3, -3.9), sunrise = -0.2, dawn = 0.3),
            EsaTown("ismailia", 30.596, 32.250, starts = listOf(-4.7, -2.6, -11.7, 0.7, -7.2), sunrise = -1.7, dawn = 1.3),
            EsaTown("6th-of-october", 29.950, 30.930, starts = listOf(-0.4, 0.0, -8.5, 2.0, -4.5), sunrise = -1.6, dawn = 4.1),
            EsaTown("damanhur", 31.044, 30.466, starts = listOf(-2.0, -1.3, -10.3, 0.7, -6.2), sunrise = -1.6, dawn = 1.7),
            EsaTown("new-cairo", 30.042, 31.402, starts = listOf(-1.9, -2.0, -7.9, 1.3, -5.5), sunrise = -1.5, dawn = 1.3),
            EsaTown("minya", 28.090, 30.756, starts = listOf(-3.5, -1.1, -7.6, 2.1, -4.1), sunrise = 0.1, dawn = 2.0),
            EsaTown("beni-suef", 29.128, 31.088, starts = listOf(-0.4, 0.2, -8.7, 1.8, -7.8), sunrise = -0.9, dawn = 4.8),
            EsaTown("sohag", 26.558, 31.696, starts = listOf(-1.0, -0.3, -5.0, 3.0, -3.1), sunrise = -1.8, dawn = 2.6),
            EsaTown("qena", 26.166, 32.706, starts = listOf(-2.3, -1.7, -8.5, 2.2, -4.9), sunrise = -2.7, dawn = 0.5),
            EsaTown("10th-of-ramadan", 30.312, 31.736, starts = listOf(-1.3, -2.0, -8.9, 1.7, -6.8), sunrise = -2.8, dawn = 1.0),
            EsaTown("kafr-el-sheikh", 31.110, 30.936, starts = listOf(-3.6, -1.5, -10.6, 2.5, -4.3), sunrise = -0.8, dawn = 3.5),
            EsaTown("obour", 30.254, 31.572, starts = listOf(-1.0, -0.6, -10.0, 1.9, -7.3), sunrise = -1.8, dawn = 3.9),
            EsaTown("delengat", 30.830, 30.530, starts = listOf(-3.9, -2.3, -8.8, 1.3, -7.1), sunrise = -1.3, dawn = 1.5),
            EsaTown("nag-hammadi", 26.040, 32.232, starts = listOf(-3.5, -1.3, -8.3, 0.4, -1.5), sunrise = -0.9, dawn = 0.3),
            EsaTown("safaga", 26.726, 33.942, starts = listOf(-3.3, -1.3, -6.4, 0.6, -2.4), sunrise = -2.2, dawn = 2.6),
            EsaTown("el-hamul", 31.312, 31.152, starts = listOf(-3.4, -1.3, -10.8, 1.5, -5.4), sunrise = -1.5, dawn = 2.0),
            EsaTown("baltim", 31.372, 31.092, starts = listOf(-1.8, 0.3, -9.8, 3.1, -2.7), sunrise = -0.5, dawn = 3.9),
            EsaTown("sadat-city", 30.440, 30.514, starts = listOf(-1.4, -1.6, -9.8, 0.8, -5.8), sunrise = -1.9, dawn = 3.3),
            EsaTown("badr", 30.136, 31.740, starts = listOf(-3.2, -2.4, -10.6, 0.0, -6.9), sunrise = -2.2, dawn = 0.0),
            EsaTown("el-hammam", 30.850, 29.396, starts = listOf(-0.3, -0.8, -8.1, 2.1, -7.3), sunrise = -1.8, dawn = 4.3),
            EsaTown("new-salhia", 30.626, 31.930, starts = listOf(-0.6, -0.5, -9.7, 0.6, -5.7), sunrise = -3.0, dawn = 2.2),
            EsaTown("ras-sudr", 29.604, 32.668, starts = listOf(-3.6, -0.7, -7.7, 1.5, -4.9), sunrise = -0.4, dawn = 2.7),
            EsaTown("abu-zenima", 29.058, 33.080, starts = listOf(-2.6, -1.0, -7.6, 1.5, -4.6), sunrise = -0.8, dawn = 1.3),
            EsaTown("berenice", 23.750, 35.500, starts = listOf(-1.7, -1.4, -7.5, 1.4, -4.1), sunrise = -1.1, dawn = 0.7),
            EsaTown("marsa-alam", 25.060, 34.882, starts = listOf(-2.9, -1.4, -8.5, 0.0, -3.0), sunrise = -1.2, dawn = 0.3),
            EsaTown("el-alamein", 30.830, 28.952, starts = listOf(-0.4, -0.5, -9.9, 2.4, -6.4), sunrise = -1.9, dawn = 1.4),
            EsaTown("baris", 24.410, 30.498, starts = listOf(-1.2, -1.9, -8.5, 1.2, -4.1), sunrise = -2.8, dawn = 1.5),
            EsaTown("toshka", 22.784, 31.418, starts = listOf(-0.4, -0.7, -7.2, 2.3, -2.6), sunrise = -1.0, dawn = 1.9),
            EsaTown("15th-of-may", 29.844, 31.374, starts = listOf(-1.3, -2.0, -8.8, 1.8, -7.8), sunrise = -2.2, dawn = 4.0),
            EsaTown("new-administrative-capital", 29.950, 31.848, starts = listOf(-4.7, -0.8, -9.5, 0.2, -2.9), sunrise = -0.1, dawn = 1.0),
            EsaTown("new-fashn", 28.820, 30.656, starts = listOf(-1.5, -2.6, -9.5, 1.5, -5.4), sunrise = -3.6, dawn = 1.5),
            EsaTown("new-mansoura", 31.486, 31.408, starts = listOf(-5.0, -2.1, -9.8, 0.3, -6.2), sunrise = -2.1, dawn = 1.7),
            EsaTown("new-nubaria", 30.668, 30.064, starts = listOf(-2.6, -2.1, -8.1, 1.1, -4.8), sunrise = -2.8, dawn = 1.8),
            EsaTown("new-beni-mazar", 28.528, 30.952, starts = listOf(-0.8, -0.5, -7.8, 2.0, -4.4), sunrise = -0.9, dawn = 1.3),
            EsaTown("new-port-said", 31.038, 32.610, starts = listOf(-2.9, -0.5, -11.3, 1.2, -6.3), sunrise = -2.5, dawn = 2.4),
            EsaTown("new-bir-al-abd", 31.034, 32.986, starts = listOf(-2.3, -0.7, -11.2, 1.7, -3.7), sunrise = -0.2, dawn = 1.5),
            EsaTown("new-toshka", 22.880, 31.582, starts = listOf(-0.2, -0.5, -5.4, 1.4, -4.2), sunrise = -2.7, dawn = 2.1),
            EsaTown("capital-gardens", 30.124, 31.896, starts = listOf(-3.4, -1.3, -9.3, 1.9, -4.5), sunrise = -1.4, dawn = 0.7),
            EsaTown("new-rashid", 31.388, 30.360, starts = listOf(-6.0, -2.2, -9.9, 1.1, -4.9), sunrise = -2.3, dawn = 0.4),
            EsaTown("new-sphinx", 30.104, 30.748, starts = listOf(-4.1, -1.9, -9.2, 0.8, -1.6), sunrise = -1.6, dawn = 1.8),
            EsaTown("east-owainat", 22.728, 28.718, starts = listOf(-0.6, -0.8, -7.9, 0.6, -6.2), sunrise = -1.2, dawn = 2.0),
            EsaTown("west-nubaria", 30.690, 29.658, starts = listOf(-3.0, -2.0, -10.6, 0.1, -8.0), sunrise = -2.4, dawn = 2.7),
        )

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
