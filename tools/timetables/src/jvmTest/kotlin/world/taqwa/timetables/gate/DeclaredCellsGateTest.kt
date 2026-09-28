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
    fun `without a declaration the early fajr and the late sunrise break the gate`() {
        val s = Gate(roots, broken(null)).evaluate(muis).entries.getValue("sg.muis")
        assertEquals(5, s.event(Event.FAJR).early)
        assertEquals(5, s.event(Event.SUNRISE).lateEnd)
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

    @Test
    fun `runs of dates are written as ranges`() {
        val dates = listOf(1, 2, 3, 5, 7, 8).map { LocalDate(2026, 4, it) }
        assertEquals(listOf("2026-04-01..2026-04-03", "2026-04-05", "2026-04-07..2026-04-08"), dateRanges(dates))
    }
}
