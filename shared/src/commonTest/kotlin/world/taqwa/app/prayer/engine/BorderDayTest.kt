package world.taqwa.app.prayer.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A place that may be in either of two countries (ruling R259, the second trust audit's G4 and G5): held to both
 * countries' Automatic entries, each start the later and each end the earlier, so never before either side's day and
 * never ending after it, in both Asr schools. Real entries at the review's places, a date every 9 days of 2026.
 */
class BorderDayTest {

    private class Case(val name: String, val place: Place, val own: String, val beside: String)

    private val cases = listOf(
        // Cholpon-Ata: the nearest app city is Almaty (Kazakhstan), the zone Kyrgyzstan's.
        Case("Cholpon-Ata", Place(42.648, 77.071, "Asia/Bishkek", "KZ", "Almaty", alsoCountryCode = "KG", alsoAdmin1 = "Issyk-Kul"), "kz.qmdb", "kg.default"),
        // Johor Bahru on a Singapore network: its own Malaysia, and Singapore beside.
        Case("Johor Bahru", Place(1.4655, 103.7578, "Asia/Singapore", "MY", "Johor", alsoCountryCode = "SG"), "my.jakim", "sg.muis"),
        // Gaziantep under Syria's zone: Türkiye, and Syria beside.
        Case("Gaziantep", Place(37.0662, 37.3833, "Asia/Damascus", "TR", "Gaziantep", alsoCountryCode = "SY"), "tr.diyanet", "sy.awqaf"),
    )

    private val dates = generateSequence(LocalDate(2026, 1, 1)) { it.plus(9, DateTimeUnit.DAY) }.takeWhile { it.year == 2026 }.toList()

    /** [day]'s Asr in [school], [dayIn] the school it was computed in. */
    private fun asrIn(day: PrayerDay, dayIn: AsrSchool, school: AsrSchool): Instant = if (dayIn == school) day.asr else day.asrOther

    @Test
    fun `a border place is held to both countries' entries and is cautious`() {
        for (c in cases) {
            val border = Registry.resolve(c.place)
            assertEquals(Registry.BORDER_PREFIX + c.own + "+" + c.beside, border.entry.id, c.name)
            assertEquals(EntryClass.C, border.entry.entryClass, c.name)
            assertEquals(listOf(c.own, c.beside), border.sides.map { it.entry.id }, c.name)
            // Either side can be chosen there, each its own country's.
            for (id in listOf(c.own, c.beside)) assertTrue(Registry.inScope(assertNotNull(Registry.byId(id)), c.place), "${c.name} $id")
        }
    }

    @Test
    fun `a border day starts no prayer before either side's day and ends nothing after it`() {
        for (c in cases) {
            val border = Registry.resolve(c.place)
            val zone = TimeZone.of(c.place.zoneId)
            val alone: List<Resolution> = border.sides
            for (date in dates) {
                val shown = DayPipeline.day(border, date, zone)
                for (side in alone) {
                    val its = DayPipeline.day(side, date, zone)
                    val at = "${c.name} ${side.entry.id} $date"
                    assertTrue(shown.fajr >= its.fajr, "$at Fajr")
                    assertTrue(shown.dhuhr >= its.dhuhr, "$at Dhuhr")
                    for (school in AsrSchool.entries) {
                        assertTrue(asrIn(shown, border.entry.school, school) >= asrIn(its, side.entry.school, school), "$at Asr $school")
                    }
                    assertTrue(shown.maghrib >= its.maghrib, "$at Maghrib")
                    assertTrue(shown.isha >= its.isha, "$at Isha")
                    assertTrue(shown.sunrise <= its.sunrise, "$at sunrise")
                    assertTrue(shown.endOfEating <= its.endOfEating, "$at end of eating")
                }
            }
        }
    }

    @Test
    fun `the Asr school shown is the later one where the sides' differ`() {
        val border = Registry.resolve(cases.first { it.name == "Gaziantep" }.place)
        val schools = border.sides.map { it.entry.school }.toSet()
        assertEquals(if (AsrSchool.HANAFI in schools) AsrSchool.HANAFI else schools.single(), border.entry.school)
    }

    @Test
    fun `one side chosen and confirmed there is that side's day alone`() {
        val c = cases.first { it.name == "Cholpon-Ata" }
        val automaticId = PrayerEngine.automaticEntryId(c.place)
        val settings = EngineSettings(timetable = TimetableChoice.Entry(c.beside), timetableConfirmed = true, timetableConfirmedUnder = automaticId)
        val date = LocalDate(2026, 3, 1)
        val chosen = PrayerEngine.dayTimes(c.place, date, settings)
        assertEquals(c.beside, chosen.effective.entry.id)
        val kyrgyz = Registry.resolve(assertNotNull(Registry.alsoPlace(c.place)))
        assertEquals(DayPipeline.day(kyrgyz, date, TimeZone.of(c.place.zoneId), chosen.school).fajr, chosen.day.fajr)
        // Not yet confirmed, it is held to Automatic's border day (ruling R52).
        val paused = PrayerEngine.dayTimes(c.place, date, settings.copy(timetableConfirmed = false))
        assertTrue(paused.timetablePaused)
        assertEquals(DayPipeline.day(Registry.resolve(c.place), date, TimeZone.of(c.place.zoneId), paused.school).fajr, paused.day.fajr)
    }
}
