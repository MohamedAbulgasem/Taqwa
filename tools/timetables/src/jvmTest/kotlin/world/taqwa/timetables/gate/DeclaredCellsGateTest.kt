package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.ClockRule
import world.taqwa.app.prayer.engine.method.ClockTime
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ruling R82: a cell whose authority time the day declares it does not show ([world.taqwa.app.prayer.engine.day.PrayerDay.notFollowed])
 * is counted as declared, neither early nor late, with its dates in the report and the stamp; every
 * other cell is held to the promise as before. On the open MUIS fixture with an invented clock rule.
 */
class DeclaredCellsGateTest {

    private val roots = OfficialRoots.of(File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI()))

    private val muis = GateManifest.parse(
        "fixture.tsv",
        GateManifest.HEADER.joinToString("\t") +
            "\nopen/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t",
    )

    /** An invented rule whose Fajr is an hour before noon, after every sunrise, and which prints no sunrise of its own. */
    private data object LateFajr : ClockRule {
        override fun on(date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTimes {
            val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
            val noon = SunClock(point.lat, point.lon, date, offset, SunModel.EXACT).transit()
            return ClockTimes(
                fajr = ClockTime(noon - 3600.0, estimated = true), sunrise = null, dhuhr = null, asrStandard = null,
                asrHanafi = null, maghrib = null, isha = null, endOfEating = null, sunset = null, noSunset = false,
                notFollowed = setOf(Prayer.SUNRISE),
            )
        }
    }

    /** Fajr two minutes early, sunrise two minutes late and Dhuhr two minutes early, with or without the rule. */
    private fun broken(rule: ClockRule?): (TimetableMethod) -> TimetableMethod = { m ->
        m.copy(
            clockRule = rule,
            margins = m.margins.copy(fajr = m.margins.fajr - 120, sunrise = m.margins.sunrise + 120, dhuhr = m.margins.dhuhr - 120),
        )
    }

    @Test
    fun `without a declaration the early fajr breaks the gate and the real sun caps the sunrise`() {
        val s = Gate(roots, broken(null)).evaluate(muis).entries.getValue("sg.muis")
        assertEquals(5, s.event(Event.FAJR).early)
        // The sunrise moved two minutes later is capped at the real sunrise at the place (ruling R265): no late end.
        assertEquals(0, s.event(Event.SUNRISE).lateEnd)
        assertEquals(0, s.event(Event.FAJR).declared)
    }

    @Test
    fun `declared cells are neither early nor late and every other cell is still held to the promise`() {
        val result = Gate(roots, broken(LateFajr)).evaluate(muis)
        val s = result.entries.getValue("sg.muis")
        for (event in listOf(Event.FAJR, Event.SUNRISE)) {
            val e = s.event(event)
            assertEquals(5, e.declared, event.key)
            assertEquals(0, e.checked, event.key)
            assertEquals(0, e.early + e.lateEnd, event.key)
        }
        // Dhuhr declares nothing: its five early days still break the gate, and the end of eating is checked.
        assertEquals(5, s.event(Event.DHUHR).early)
        assertEquals(5, s.event(Event.END_OF_EATING).checked)
        val violations = result.violations()
        assertTrue(violations.any { "sg.muis dhuhr: 5 early" in it }, result.report())
        assertFalse(violations.any { "fajr" in it || "sunrise" in it }, result.report())
        assertTrue("fajr: 5 cells declared not followed (2026-02-14..2026-02-18)" in result.report(), result.report())
    }

    /**
     * Ruling R294 (the review of 17 Nov 2026): a declared Isha is only ever moved later (point 9, the minute after a
     * Maghrib past the authority's Isha), so a declared Isha shown before the printed one is early, declared or not.
     * Here Isha's margin two hours early puts the engine's Isha before its Maghrib under a method that declares it:
     * the Isha shown is the minute after Maghrib, over an hour before MUIS's printed Isha.
     */
    @Test
    fun `a declared isha shown before the printed one is early`() {
        val ishaFirst: (TimetableMethod) -> TimetableMethod = { m ->
            m.copy(declaresIshaBeforeMaghrib = true, margins = m.margins.copy(isha = m.margins.isha - 7200))
        }
        val result = Gate(roots, ishaFirst).evaluate(muis)
        val isha = result.entries.getValue("sg.muis").event(Event.ISHA)
        assertEquals(5, isha.declared)
        assertEquals(0, isha.checked)
        assertEquals(5, isha.early)
        assertTrue(result.violations().any { "sg.muis isha: 5 early" in it }, result.report())
        assertTrue(isha.broken.all { "declared not followed" in it }, isha.broken.toString())

        // The same Isha margin without the declaration: point 9 leaves the day for the repair, and the gate still names it.
        val undeclared = Gate(roots, { m -> m.copy(margins = m.margins.copy(isha = m.margins.isha - 7200)) }).evaluate(muis)
        assertEquals(0, undeclared.entries.getValue("sg.muis").event(Event.ISHA).declared)
        assertTrue(undeclared.violations().isNotEmpty(), undeclared.report())
    }

    @Test
    fun `a stamp carries the declared cells and their dates and none where nothing is declared`() {
        val declared = Gate(roots, broken(LateFajr)).evaluate(muis).entries.getValue("sg.muis")

        @Suppress("UNCHECKED_CAST")
        val fajr = (Stamps.stamp(declared, "core")["events"] as Map<String, Map<String, Any?>>).getValue("fajr")
        assertEquals(5, fajr["declared"])
        assertEquals(listOf("2026-02-14..2026-02-18"), fajr["declaredDates"])

        val plain = Gate(roots).evaluate(muis).entries.getValue("sg.muis")
        val json = world.taqwa.timetables.Json.pretty(Stamps.stamp(plain, "core"))
        assertFalse("declared" in json, json)
    }

    /**
     * Ruling R302: a day whose Isha shows no end is declared, never out of order, only where the real sun at the row's
     * point makes the night too short for one (the gate recomputes that and passes it in); on any other night, and on a
     * repaired day whatever the night, it is out of order as before; and only for an entry a ruling names (IRN, R302, and
     * Norway's Cautious times, R314). Made-up
     * days with no ends at all.
     */
    @Test
    fun `a day without its isha end is declared on a night too short for one and out of order on any other`() {
        val s = EntryStats(requireNotNull(world.taqwa.app.prayer.engine.registry.Registry.byId("no.irn")))
        val t = kotlin.time.Instant.fromEpochSeconds(1_800_000_000)
        fun day(d: Int) = world.taqwa.app.prayer.engine.day.PrayerDay(LocalDate(2026, 5, d), t, t, t, t, t, t, t, t, t, null, methodId = "made-up")
        s.placeDay("g", LocalDate(2026, 5, 16), Split.TEST, false, day(16), null, shortNight = { true })
        s.placeDay("g", LocalDate(2026, 5, 17), Split.TEST, false, day(17), null, shortNight = { false })
        s.placeDay("g", LocalDate(2026, 5, 18), Split.TEST, false, day(18).copy(repaired = true), null, shortNight = { true })
        s.promiseDay("p", LocalDate(2026, 5, 19), day(19)) { true }
        s.promiseDay("p", LocalDate(2026, 5, 20), day(20)) { false }
        assertEquals(2, s.shortNights)
        assertEquals(listOf("2026-05-16", "2026-05-19"), s.shortNightDates.map { it.toString() })
        assertEquals(3, s.outOfOrder)
        @Suppress("UNCHECKED_CAST")
        val stamped = Stamps.stamp(s, "core")["shortNights"] as Map<String, Any?>
        assertEquals(2, stamped["days"])
        assertEquals(listOf("2026-05-16", "2026-05-19"), stamped["dates"])
        // A day with its Isha end is neither, whatever the sun.
        val ended = EntryStats(s.entry)
        ended.placeDay("g", LocalDate(2026, 5, 16), Split.TEST, false, day(16).copy(ends = mapOf(Prayer.ISHA to t)), null, shortNight = { true })
        assertEquals(0, ended.shortNights + ended.outOfOrder)
        assertFalse("shortNights" in Stamps.stamp(ended, "core"))
        // The rulings name IRN (R302) and Norway's Cautious times, IRN a member (R314): it declares the same night too.
        assertEquals(setOf("no.irn", "no.cautious"), EntryStats.SHORT_NIGHT_ENTRIES)
        val cautious = EntryStats(requireNotNull(world.taqwa.app.prayer.engine.registry.Registry.byId("no.cautious")))
        cautious.placeDay("g", LocalDate(2026, 5, 16), Split.TEST, false, day(16), null, shortNight = { true })
        cautious.promiseDay("p", LocalDate(2026, 5, 19), day(19)) { true }
        cautious.placeDay("g", LocalDate(2026, 5, 17), Split.TEST, false, day(17), null, shortNight = { false })
        assertEquals(2, cautious.shortNights)
        assertEquals(1, cautious.outOfOrder)
        assertTrue("shortNights" in Stamps.stamp(cautious, "core"))
        // An entry no ruling names declares nothing on the same night: its short night is out of order, own or promise-only.
        val other = requireNotNull(world.taqwa.app.prayer.engine.registry.Registry.byId("tr.diyanet.europe"))
        assertFalse(other.id in EntryStats.SHORT_NIGHT_ENTRIES)
        val undeclared = EntryStats(other)
        undeclared.placeDay("g", LocalDate(2026, 5, 16), Split.TEST, false, day(16), null, shortNight = { true })
        undeclared.promiseDay("p", LocalDate(2026, 5, 19), day(19)) { true }
        assertEquals(0, undeclared.shortNights)
        assertEquals(2, undeclared.outOfOrder)
        assertFalse("shortNights" in Stamps.stamp(undeclared, "core"))
    }

    @Test
    fun `runs of dates are written as ranges`() {
        val dates = listOf(1, 2, 3, 5, 7, 8).map { LocalDate(2026, 4, it) }
        assertEquals(listOf("2026-04-01..2026-04-03", "2026-04-05", "2026-04-07..2026-04-08"), dateRanges(dates))
    }
}
