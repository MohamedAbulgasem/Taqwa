package world.taqwa.app.feature.settings

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.feature.prayer.WhoseTimes
import world.taqwa.app.feature.prayer.sourceLabelRes
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.CheckedDays
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.ProofStamps
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_source_published_method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ruling R275 (the second audit's G9): a checked label is a claim about a day. Where the day is past the last run the
 * gate compared the place's unit on, or in a hole between runs, the app shows the authority's published method (the
 * "Published method" label, the unchecked card and About), as the site holds the city's page then; on a day a run
 * holds, the checked class. Every date here is read off the committed stamp's own runs, so the rule is held whatever
 * the weekly monitor's prove has added since (tests hold rules, never live figures).
 */
class CheckedPerDateTest {
    private val places = mapOf(
        "Casablanca" to Place(33.5731, -7.5898, "Africa/Casablanca", "MA"),
        "Cairo" to Place(30.0444, 31.2357, "Africa/Cairo", "EG"),
        "Trabzon" to Place(41.0027, 39.7168, "Europe/Istanbul", "TR"),
        "Algiers" to Place(36.7538, 3.0588, "Africa/Algiers", "DZ"),
        "Kuala Lumpur" to Place(3.139, 101.6869, "Asia/Kuala_Lumpur", "MY"),
        "Muscat" to Place(23.588, 58.3829, "Asia/Muscat", "OM"),
        "Amman" to Place(31.9539, 35.9106, "Asia/Amman", "JO"),
        "Jakarta" to Place(-6.2088, 106.8456, "Asia/Jakarta", "ID"),
    )

    /**
     * The runs of days in a row the stamp keeps for [place]'s unit (the unit's own, else the entry's), a lone sampled day
     * passed over, as [CheckedDays] reads them.
     */
    private fun runs(place: Place): List<ClosedRange<LocalDate>> {
        val resolution = Registry.resolve(place)
        val stamp = assertNotNull(ProofStamps.of(resolution.entry.id), resolution.entry.id)
        val raw = resolution.unitId?.takeIf { stamp.worstLateByUnit.isNotEmpty() }?.let { stamp.checkedByUnit[it] } ?: stamp.checked
        return CheckedDays.blocks(raw)
    }

    @Test
    fun `runs are days in a row and a lone sampled day is passed over`() {
        val runs = listOf("2026-01-01..2026-01-10", "2026-01-11..2026-01-20", "2026-02-01", "2026-02-04", "2026-03-01..2026-03-02")
        assertEquals(listOf(LocalDate(2026, 1, 1)..LocalDate(2026, 1, 20), LocalDate(2026, 3, 1)..LocalDate(2026, 3, 2)), CheckedDays.blocks(runs))
        // Tunisia's every third day and Oman's sampled 2029 make no place checked on the days between them.
        val tabarka = Place(36.952, 8.758, "Africa/Tunis", "TN")
        val muscat = places.getValue("Muscat")
        for ((place, date) in listOf(tabarka to runs(tabarka).maxOf { it.endInclusive }.plus(3, DateTimeUnit.DAY), muscat to LocalDate(2029, 1, 1))) {
            assertEquals(EntryClass.D_AUTHORITY, day(place, date).effective.entryClass, "$place on $date")
        }
    }

    private fun day(place: Place, date: LocalDate) = PrayerEngine.dayTimes(place, date, EngineSettings())

    @Test
    fun `a checked authority is checked on the last day of its last run and its published method the day after`() {
        for ((name, place) in places) {
            val registered = Registry.resolve(place)
            assertTrue(registered.entryClass == EntryClass.A || registered.entryClass == EntryClass.B, "$name: ${registered.entryClass}")
            val last = runs(place).maxOf { it.endInclusive }
            val onLast = day(place, last).effective
            assertEquals(registered.entryClass, onLast.entryClass, "$name on $last, its last checked day")
            assertNull(onLast.checkedClass, name)
            assertNull(sourceLabelRes(WhoseTimes.of(day(place, last))), "$name: no label on a checked day")

            val after = last.plus(1, DateTimeUnit.DAY)
            val engineDay = day(place, after)
            val shown = engineDay.effective
            assertEquals(EntryClass.D_AUTHORITY, shown.entryClass, "$name on $after")
            assertEquals(AboutTemplate.AUTHORITY_UNCHECKED, shown.about, name)
            assertEquals(registered.entryClass, shown.checkedClass, name)
            assertEquals(Res.string.prayer_source_published_method, sourceLabelRes(WhoseTimes.of(engineDay)), "$name: 'Published method'")
            val about = assertIs<AboutTimesUiState.AuthorityUnchecked>(aboutTimesUiState(engineDay, place, after), name)
            // And About says through when it was checked: the last day of that run (ruling R278), never a later one.
            assertEquals(after, about.date, name)
            assertEquals(last.toString(), checkedThrough(shown, assertNotNull(about.stamp, name), after), name)
            // Nothing else moves: the same entry, unit and measured flag, so "checked on … days so far" keeps the days that were.
            assertEquals(registered.entry.id, shown.entry.id, name)
            assertEquals(registered.unitId, shown.unitId, name)
            assertEquals(registered.measured, shown.measured, name)
        }
    }

    @Test
    fun `a day in a hole between checked runs is the published method and a day a run holds is checked`() {
        for ((name, place) in places) {
            val spans = runs(place).sortedBy { it.start }
            val registered = Registry.resolve(place).entryClass
            for ((before, next) in spans.zipWithNext()) {
                val hole = before.endInclusive.plus(1, DateTimeUnit.DAY)
                if (hole >= next.start || spans.any { hole in it }) continue
                assertEquals(EntryClass.D_AUTHORITY, day(place, hole).effective.entryClass, "$name in the hole on $hole")
                assertEquals(registered, day(place, next.start).effective.entryClass, "$name on ${next.start}")
            }
        }
    }

    @Test
    fun `the label agrees with the checked days at every unit of every checked authority`() {
        // The rule at the reference point of every unit of every stamped entry, on the last day of each run and the day
        // after: checked where a run of the stamp's (the unit's own where it keeps them, the entry's otherwise) holds the
        // day and the stamp holds the unit, the published method otherwise, as the site's proven rule reads it.
        var seen = 0
        for (stamp in ProofStamps.byEntry.values) {
            val entry = Registry.byId(stamp.entryId) ?: continue
            for (unit in Units.of(entry.id)?.units.orEmpty()) {
                val place = Place(unit.point.lat, unit.point.lon, "UTC", entry.countries.firstOrNull() ?: "XX")
                val resolution = Registry.resolveEntry(entry, place, inUnit = unit)
                if (resolution.entryClass != EntryClass.A && resolution.entryClass != EntryClass.B) continue
                val held = stamp.worstLateByUnit.isEmpty() || unit.id in stamp.worstLateByUnit
                val spans = CheckedDays.blocks(stamp.checkedByUnit[unit.id] ?: stamp.checked)
                for (end in spans.map { it.endInclusive }) {
                    for (date in listOf(end, end.plus(1, DateTimeUnit.DAY))) {
                        val checked = held && spans.any { date in it }
                        assertEquals(checked, CheckedDays.covers(entry.id, unit.id, date), "${entry.id} ${unit.id} on $date")
                        val shown = resolution.on(date).entryClass
                        assertEquals(if (checked) resolution.entryClass else EntryClass.D_AUTHORITY, shown, "${entry.id} ${unit.id} on $date")
                        seen++
                    }
                }
            }
        }
        assertTrue(seen > 1000, "$seen unit-days looked at")
    }
}
