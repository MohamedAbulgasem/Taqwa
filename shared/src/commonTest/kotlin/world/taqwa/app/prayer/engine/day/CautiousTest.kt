package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CautiousTest {
    private val toronto = TimeZone.of("America/Toronto")
    private val date = LocalDate(2026, 10, 1)

    private fun at(hhmm: String): Instant {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return date.atTime(h, m).toInstant(toronto)
    }

    /**
     * A member day from its row (Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha); the other
     * school's Asr, the sunset and the end of eating are synthetic.
     */
    private fun member(
        id: String,
        row: String,
        asrOther: String,
        sunset: String,
        endOfEating: String? = null,
        imsak: String? = null,
    ): PrayerDay {
        val t = row.split(" ").map(::at)
        return PrayerDay(
            date = date,
            fajr = t[0], sunrise = t[1], dhuhr = t[2], asr = t[3], asrOther = at(asrOther),
            maghrib = t[4], isha = t[5], sunset = at(sunset),
            endOfEating = endOfEating?.let(::at) ?: t[0], imsak = imsak?.let(::at),
            methodId = id,
        )
    }

    // Three invented timetables for one Toronto day (ruling R69: no printed time); the first prints
    // the Hanafi Asr, the second an end of eating and an imsak of its own.
    private val one = member("one", "05:50 07:10 13:02 17:05 18:52 20:05", asrOther = "16:17", sunset = "18:50")
    private val two = member(
        "two", "05:50 07:08 13:03 16:18 18:53 20:25", asrOther = "17:06", sunset = "18:51",
        endOfEating = "05:44", imsak = "05:34",
    )
    private val three = member("three", "05:50 07:10 13:02 16:18 18:51 20:12", asrOther = "17:04", sunset = "18:49")

    @Test
    fun `toronto takes the latest start and the earliest sunrise of its three timetables`() {
        val day = Cautious.combine(listOf(one, two, three), mostFollowed = 0)
        assertEquals(at("05:50"), day.fajr)
        assertEquals(at("07:08"), day.sunrise)
        assertEquals(at("13:03"), day.dhuhr)
        assertEquals(at("17:05"), day.asr)
        assertEquals(at("17:06"), day.asrOther)
        assertEquals(at("18:53"), day.maghrib, "the members' Maghribs are within 2 minutes: the latest")
        assertEquals(at("20:25"), day.isha)
        assertEquals(at("18:49"), day.sunset)
        assertEquals(at("05:44"), day.endOfEating)
        assertEquals(at("05:34"), day.imsak)
        assertEquals(date, day.date)
        assertEquals("cautious:one+two+three", day.methodId)
        assertEquals(
            mapOf(
                Prayer.FAJR to at("05:50"),
                Prayer.DHUHR to at("13:02"),
                Prayer.ASR to at("16:18"),
                Prayer.MAGHRIB to at("18:51"),
                Prayer.ISHA to at("20:05"),
            ),
            day.earliestStart,
        )
    }

    @Test
    fun `a maghrib more than 2 minutes apart is capped at the most followed member's`() {
        val late = three.copy(maghrib = at("18:57"))
        assertEquals(at("18:52"), Cautious.combine(listOf(one, two, late), mostFollowed = 0).maghrib)
        assertEquals(at("18:53"), Cautious.combine(listOf(one, two, late), mostFollowed = 1).maghrib)
        assertEquals(at("18:52"), Cautious.combine(listOf(one, two, late), mostFollowed = 0).earliestStart[Prayer.MAGHRIB])
        // Exactly 2 minutes apart is still agreement.
        val twoApart = three.copy(maghrib = at("18:54"))
        assertEquals(at("18:54"), Cautious.combine(listOf(one, two, twoApart), mostFollowed = 0).maghrib)
    }

    @Test
    fun `a cautious day ends each prayer at the earliest end in play`() {
        val day = Cautious.combine(listOf(one, two, three), mostFollowed = 0)
        assertEquals(at("07:08"), day.ends[Prayer.FAJR])
        assertEquals(at("16:17"), day.ends[Prayer.DHUHR], "the earliest member's Standard Asr")
        assertEquals(at("18:49"), day.ends[Prayer.ASR])
        assertEquals(at("20:05"), day.ends[Prayer.MAGHRIB], "the earliest Isha in play")
        assertNull(day.ends[Prayer.ISHA], "no member knows its next day")
    }

    @Test
    fun `computed members combine their own ends and their next days`() {
        val point = GeoPoint(43.6532, -79.3832)
        val isna = TimetableMethod(id = "isna", fajrAngle = 15.0, isha = IshaRule.Angle(15.0))
        val mwl = TimetableMethod(id = "mwl", fajrAngle = 18.0, isha = IshaRule.Angle(17.0))
        val noRamadan = RamadanCalendar { false }
        val next = LocalDate(2026, 10, 2)
        val members = listOf(isna, mwl).map { method ->
            val today = DayComputer.compute(method, point, date, toronto, AsrSchool.STANDARD, noRamadan)
            val tomorrow = DayComputer.compute(method, point, next, toronto, AsrSchool.STANDARD, noRamadan)
            Ends.withNextDay(today, tomorrow)
        }
        val day = Cautious.combine(members, mostFollowed = 0)
        for (prayer in listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)) {
            assertEquals(members.minOf { it.ends.getValue(prayer) }, day.ends[prayer], "$prayer")
        }
        // ISNA's 15° Isha comes before the 17° dusk, so Maghrib ends at it.
        assertEquals(members[0].isha, day.ends[Prayer.MAGHRIB])
        assertEquals(members[1].isha, day.isha)
        assertEquals(members[0].isha, day.earliestStart[Prayer.ISHA])
    }

    @Test
    fun `a combined isha end not after the latest isha is left out`() {
        val early = one.copy(ends = mapOf(Prayer.ISHA to at("20:15")))
        val late = two.copy(ends = mapOf(Prayer.ISHA to at("20:35")))
        // The earliest member's end (20:15) is before the latest Isha (the second's 20:25).
        assertNull(Cautious.combine(listOf(early, late), mostFollowed = 0).ends[Prayer.ISHA])
        val after = early.copy(ends = mapOf(Prayer.ISHA to at("20:26")))
        assertEquals(at("20:26"), Cautious.combine(listOf(after, late), mostFollowed = 0).ends[Prayer.ISHA])
    }

    @Test
    fun `flags from any member carry over`() {
        val flagged = two.copy(setByRule = setOf(Prayer.ISHA), polar = true, repaired = true)
        val day = Cautious.combine(listOf(one, flagged, three), mostFollowed = 0)
        assertEquals(setOf(Prayer.ISHA), day.setByRule)
        assertTrue(day.polar)
        assertTrue(day.repaired)
        val clean = Cautious.combine(listOf(one, two, three), mostFollowed = 0)
        assertEquals(emptySet(), clean.setByRule)
        assertFalse(clean.polar || clean.repaired)
    }

    @Test
    fun `a flag or a polar day counts only through the members a time was taken from`() {
        // A polar member none of whose times is chosen leaves the day unflagged and not polar.
        val quiet = three.copy(polar = true, setByRule = Prayer.entries.toSet(), sunrise = at("07:11"))
        val day = Cautious.combine(listOf(one, two, quiet), mostFollowed = 0)
        assertEquals(emptySet(), day.setByRule, "every chosen time is also a real member's")
        assertFalse(day.polar)

        // A flagged Fajr that ties with the real ones is real; a flagged Isha that is the latest is not.
        val flagged = two.copy(setByRule = setOf(Prayer.FAJR, Prayer.ISHA))
        assertEquals(setOf(Prayer.ISHA), Cautious.combine(listOf(one, flagged, three), mostFollowed = 0).setByRule)

        // A polar member whose Isha is taken makes the day polar.
        val polarIsha = three.copy(isha = at("20:40"), polar = true, setByRule = Prayer.entries.toSet())
        val polarDay = Cautious.combine(listOf(one, two, polarIsha), mostFollowed = 0)
        assertTrue(polarDay.polar)
        assertEquals(setOf(Prayer.ISHA), polarDay.setByRule)
    }

    @Test
    fun `a sunrise not after the fajr shown gives way to the sunrise of the member whose fajr is shown`() {
        // Ruling R90 (a): at the polar edge one member's sunrise precaution comes before another's
        // dawn. Fajr keeps its promise (the latest member's); the sunrise, and Fajr's end, are that
        // member's own, not the earliest, so no repair pulls Fajr before a member's dawn.
        val earlySunrise = one.copy(sunrise = at("07:05"))
        val lateDawn = two.copy(fajr = at("07:09"), sunrise = at("07:10"))
        val day = Cautious.combine(listOf(earlySunrise, lateDawn, three), mostFollowed = 0)
        assertEquals(at("07:09"), day.fajr)
        assertEquals(at("07:10"), day.sunrise, "the sunrise of the member whose Fajr is shown")
        assertEquals(at("07:10"), day.ends[Prayer.FAJR])
        assertTrue(Invariants.holds(day))
        // The mark follows the member the sunrise is taken from (three's own sunrise is a minute later here).
        val ruled = lateDawn.copy(setByRule = setOf(Prayer.SUNRISE))
        assertEquals(setOf(Prayer.SUNRISE), Cautious.combine(listOf(earlySunrise, ruled, three.copy(sunrise = at("07:11"))), mostFollowed = 0).setByRule)

        // Members tied on the Fajr shown: the earliest of their sunrises.
        val tied = three.copy(fajr = at("07:09"), sunrise = at("07:12"))
        assertEquals(at("07:10"), Cautious.combine(listOf(earlySunrise, lateDawn, tied), mostFollowed = 0).sunrise)
        assertEquals(at("07:12"), Cautious.combine(listOf(earlySunrise, lateDawn.copy(sunrise = at("07:13")), tied), mostFollowed = 0).sunrise)

        // Where the earliest sunrise is after the Fajr shown nothing changes: the earliest sunrise.
        assertEquals(at("07:08"), Cautious.combine(listOf(one, two, three), mostFollowed = 0).sunrise)
    }

    @Test
    fun `what any member declares not followed the day declares`() {
        // Ruling R90: a member whose own Fajr the sun had risen by keeps a Fajr before the sunrise
        // and declares its own; the day declares it too, whether that member's Fajr is shown or not.
        val declared = three.copy(fajr = at("05:40"), notFollowed = setOf(Prayer.FAJR))
        assertEquals(setOf(Prayer.FAJR), Cautious.combine(listOf(one, two, declared), mostFollowed = 0).notFollowed)
        assertEquals(at("05:50"), Cautious.combine(listOf(one, two, declared), mostFollowed = 0).fajr)
        assertEquals(emptySet(), Cautious.combine(listOf(one, two, three), mostFollowed = 0).notFollowed)
    }

    @Test
    fun `members must be one date and the most followed one of them`() {
        assertFailsWith<IllegalArgumentException> { Cautious.combine(emptyList(), mostFollowed = 0) }
        assertFailsWith<IllegalArgumentException> { Cautious.combine(listOf(one, two), mostFollowed = 2) }
        val tomorrow = two.copy(date = LocalDate(2026, 10, 2))
        assertFailsWith<IllegalArgumentException> { Cautious.combine(listOf(one, tomorrow), mostFollowed = 0) }
    }
}
