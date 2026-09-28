package world.taqwa.app.prayer.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Ruling R34: one pipeline from a place to its day. */
class DayPipelineTest {

    private fun entry(id: String): RegistryEntry = assertNotNull(Registry.byId(id), id)

    private fun compute(method: TimetableMethod, entry: RegistryEntry, point: GeoPoint, date: LocalDate, zone: TimeZone): PrayerDay =
        DayComputer.compute(method, point, date, zone, entry.school, Registry.ramadanCalendarFor(entry), Registry.lagDates)

    private fun PrayerDay.instants(): List<Instant> =
        listOfNotNull(fajr, sunrise, dhuhr, asr, asrOther, maghrib, isha, sunset, endOfEating, imsak) + ends.values

    private fun assertWholeMinutes(day: PrayerDay) =
        day.instants().forEach { assertEquals(0L, it.epochSeconds % 60, "$it is not a whole minute") }

    @Test
    fun `a single method is its computed day in order with isha ending at the next day's end of eating`() {
        val muis = entry("sg.muis")
        val place = Place(1.35, 103.94, "Asia/Singapore", "SG") // Tampines; MUIS computes at its fixed point too
        val zone = TimeZone.of(place.zoneId)
        val date = LocalDate(2026, 3, 1) // in Ramadan: Maghrib + 1 min
        val resolution = Registry.resolveEntry(muis, place)
        val method = assertNotNull(resolution.method)

        val day = DayPipeline.day(muis, place, date)

        val today = Invariants.repair(compute(method, muis, resolution.point, date, zone))
        val next = Invariants.repair(compute(method, muis, resolution.point, date.plus(1, DateTimeUnit.DAY), zone))
        assertEquals(today.copy(ends = today.ends + (Prayer.ISHA to next.endOfEating)), day)
        assertWholeMinutes(day)
    }

    @Test
    fun `a cautious entry combines its members computed at the user's point`() {
        val uk = entry("gb.cautious")
        val place = Place(52.4862, -1.8904, "Europe/London", "GB") // Birmingham
        val point = GeoPoint(place.lat, place.lon)
        val zone = TimeZone.of(place.zoneId)
        val date = LocalDate(2026, 11, 20)

        val day = DayPipeline.day(uk, place, date)

        val resolution = Registry.resolveEntry(uk, place)
        val members = resolution.members.map { member ->
            DayComputer.compute(member.method, point, date, zone, uk.school, Registry.ramadanCalendarFor(uk), Registry.lagDates)
        }
        val combined = Invariants.repair(Cautious.combine(members, mostFollowed = 0))
        assertEquals(combined.fajr, day.fajr)
        assertEquals(members.maxOf { it.isha }, day.isha)
        assertEquals(members.minOf { it.sunrise }, day.sunrise)
        assertEquals(members.minOf { it.endOfEating }, day.endOfEating)
        assertTrue(day.methodId.startsWith("cautious:"))
        assertNotNull(day.ends[Prayer.ISHA])
        assertWholeMinutes(day)
    }

    /**
     * A member keeps its own Ramadan calendar: on 17 February 2026, the day before Umm al-Qura's
     * Ramadan 1447, a cautious entry of its own (any other authority's calendar takes a day either
     * side, so the entry's would call it Ramadan) still gets Umm al-Qura's Isha at Maghrib + 90.
     */
    @Test
    fun `a cautious member keeps its own ramadan calendar`() {
        val uq = entry("other.ummalqura")
        val mwl = entry("other.mwl")
        val cautious = RegistryEntry(
            id = "test.cautious", shortNameKey = "timetable_cautious", entryClass = EntryClass.C, about = AboutTemplate.CAUTIOUS,
            members = listOf(uq.asMember(1), mwl.asMember(2)), school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.GLOBAL,
        )
        val place = Place(21.4225, 39.8262, "Asia/Riyadh", "SA") // Makkah
        val zone = TimeZone.of(place.zoneId)
        val point = GeoPoint(place.lat, place.lon)
        val date = LocalDate(2026, 2, 17)
        assertTrue(Registry.ramadanCalendarFor(cautious).isRamadan(date), "the entry's own calendar calls it Ramadan")
        assertTrue(!Registry.ramadanCalendarFor(uq).isRamadan(date), "Umm al-Qura's does not")

        val day = DayPipeline.day(cautious, place, date)

        val method = assertNotNull(uq.method)
        val own = compute(method, uq, point, date, zone)
        val widened = DayComputer.compute(method, point, date, zone, uq.school, Registry.ramadanCalendarFor(cautious), Registry.lagDates)
        assertEquals(own.isha, day.isha)
        assertEquals(30L, (widened.isha - own.isha).inWholeMinutes, "Maghrib + 120 would have been shown")
    }

    @Test
    fun `a place away from its unit's point is never earlier than either point`() {
        val dumRt = entry("ru.dumrt")
        val zone = TimeZone.of("Europe/Moscow")
        val kazan = GeoPoint(55.78874, 49.12214)
        val user = GeoPoint(55.70, 49.30) // about 14 km south-east of Kazan's point, inside its unit
        val date = LocalDate(2026, 10, 15)

        val resolution = Registry.resolveEntry(dumRt, Place(user.lat, user.lon, "Europe/Moscow", "RU"))
        assertEquals("Kazan", resolution.unitName)
        val day = DayPipeline.day(resolution, date, zone)

        // Each point as its own table's point: with no fixed point the method is DUM RT's edge, which
        // takes a band of latitude around the point (ruling R65), not the point alone.
        for (at in listOf(kazan, user)) {
            val there = compute(assertNotNull(resolution.method).copy(fixedPoint = at), dumRt, at, date, zone)
            assertTrue(day.fajr >= there.fajr && day.dhuhr >= there.dhuhr && day.asr >= there.asr, "starts at $at")
            assertTrue(day.maghrib >= there.maghrib && day.isha >= there.isha, "evening starts at $at")
            assertTrue(day.sunrise <= there.sunrise && day.endOfEating <= there.endOfEating, "ends at $at")
        }
        assertEquals(AsrSchool.HANAFI, dumRt.school)
        assertWholeMinutes(day)
    }
}
