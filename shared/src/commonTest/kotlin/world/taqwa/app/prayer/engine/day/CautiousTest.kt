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
import kotlin.time.Duration.Companion.seconds
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

    /** The real sunset at the user's point in these tests: before every member's Maghrib, so that it holds nothing. */
    private val sunsetHere = at("18:48")

    private fun combine(members: List<PrayerDay>, mostFollowed: Int = 0, realSunset: Instant? = sunsetHere) =
        Cautious.combine(members, mostFollowed, realSunset)

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
        val day = combine(listOf(one, two, three), mostFollowed = 0)
        assertEquals(at("05:50"), day.fajr)
        assertEquals(at("07:08"), day.sunrise)
        assertEquals(at("13:03"), day.dhuhr)
        assertEquals(at("17:05"), day.asr)
        assertEquals(at("17:06"), day.asrOther)
        assertEquals(at("18:53"), day.maghrib, "the latest member's")
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

    /**
     * Ruling R155 (the owner's decision of 24 Oct 2026): Maghrib is the latest member's, like every other
     * start, at any spread and whichever member is the most followed. The 26 September cap (the
     * most-followed member's own Maghrib past a 2-minute agreement) is gone.
     */
    @Test
    fun `maghrib is the latest member's at any spread and the 2 minute agreement is gone`() {
        // Brussels-like: EMB's sunset + 2, the most followed, against Diyanet's + 9.
        val emb = one.copy(maghrib = at("18:52"))
        val diyanet = two.copy(maghrib = at("18:59"))
        assertEquals(at("18:59"), combine(listOf(emb, diyanet), mostFollowed = 0).maghrib, "Diyanet's, past EMB's own")
        assertEquals(at("18:59"), combine(listOf(emb, diyanet), mostFollowed = 1).maghrib)
        // Three minutes apart, past the old agreement: the latest whichever member is the most followed.
        val late = three.copy(maghrib = at("18:56"))
        for (mostFollowed in 0..2) {
            val day = combine(listOf(one, two, late), mostFollowed)
            assertEquals(at("18:56"), day.maghrib, "most followed $mostFollowed")
            assertEquals(emptySet(), day.setByRule)
            assertFalse(day.polar || day.maghribHeld)
        }
        assertEquals(at("18:52"), combine(listOf(one, two, late)).earliestStart[Prayer.MAGHRIB])
    }

    /** A member whose own day-length rule (Diyanet's five-hour winter day) holds its Maghrib from [sun] to [shown]. */
    private fun PrayerDay.held(shown: String, sun: String) = copy(maghrib = at(shown), maghribHeld = true, maghribBeforeHold = at(sun))

    /**
     * Ruling R155's far-north guard: a member whose Maghrib its own day-length rule holds later than the sun
     * counts at its Maghrib from the sun, unless it is the most-followed member, whose own Maghrib always counts.
     */
    @Test
    fun `a member held by its own day length rule counts at its sun time unless it is the most followed`() {
        // Trondheim-like: IRN, the most followed, from the sun; Diyanet held to 19:30, its sunset + 7 at 18:55.
        val irn = one
        val diyanet = two.held(shown = "19:30", sun = "18:55")
        val trondheim = combine(listOf(irn, diyanet), mostFollowed = 0)
        assertEquals(at("18:55"), trondheim.maghrib, "Diyanet's sun time, never its held minute")
        assertFalse(trondheim.maghribHeld)
        assertNull(trondheim.maghribBeforeHold)
        // IRN's own Maghrib later than Diyanet's sun time: IRN's.
        assertEquals(at("18:57"), combine(listOf(irn.copy(maghrib = at("18:57")), diyanet), mostFollowed = 0).maghrib)

        // Luleå-like: Diyanet the most followed, held; Rabita's earlier: Diyanet's held minute.
        val lulea = combine(listOf(diyanet, three), mostFollowed = 0)
        assertEquals(at("19:30"), lulea.maghrib)
        assertTrue(lulea.maghribHeld)
        assertEquals(at("18:55"), lulea.maghribBeforeHold, "the latest Maghrib the sun gives")
        assertEquals(at("18:55"), lulea.maghribBySun)
        assertEquals(emptySet(), lulea.setByRule, "a held Maghrib is not set by rule")
        assertFalse(lulea.polar)
        // Where the real sun does not set at the user's point no Maghrib comes from the sun, so nothing is held from it:
        // the most-followed member's own minute is shown, and the day is held only with a Maghrib before the hold.
        val noSunset = combine(listOf(diyanet, three), mostFollowed = 0, realSunset = null)
        assertEquals(at("19:30"), noSunset.maghrib)
        assertFalse(noSunset.maghribHeld)
        assertNull(noSunset.maghribBeforeHold)
    }

    /**
     * Item A of the adversarial check: the minute shown can be a member's Maghrib from the sun that is no
     * member's own Maghrib shown (Trondheim in December). It is real: no flag comes from an empty match.
     */
    @Test
    fun `a maghrib that is a member's sun time keeps the day's own flags`() {
        val irn = one
        val diyanet = two.held(shown = "19:30", sun = "18:55")
        val day = combine(listOf(irn, diyanet), mostFollowed = 0)
        assertTrue(listOf(irn, diyanet).none { it.maghrib == day.maghrib }, "no member's own Maghrib shown")
        assertEquals(emptySet(), day.setByRule)
        assertFalse(day.polar)
        // Every other time as without the guard.
        val plain = combine(listOf(irn, two), mostFollowed = 0)
        assertEquals(plain.copy(maghrib = day.maghrib), day)
    }

    /** A Maghrib a rule sets (a polar day's borrowed time, a clock rule's estimate) counts only for the most followed. */
    @Test
    fun `a member's maghrib set by rule counts only when that member is the most followed`() {
        val borrowed = three.copy(maghrib = at("19:10"), setByRule = setOf(Prayer.MAGHRIB), polar = true, sunrise = at("07:11"))
        val day = combine(listOf(one, two, borrowed), mostFollowed = 0)
        assertEquals(at("18:53"), day.maghrib, "the latest Maghrib from the sun")
        assertEquals(emptySet(), day.setByRule)
        assertFalse(day.polar)
        val ruled = combine(listOf(borrowed, one, two), mostFollowed = 0)
        assertEquals(at("19:10"), ruled.maghrib, "the most followed member's own")
        assertEquals(setOf(Prayer.MAGHRIB), ruled.setByRule)
        assertTrue(ruled.polar)
    }

    /** Ruling R155's floor (the Lapland fix): never before the minute after the real sunset at the user's point. */
    @Test
    fun `maghrib is never before the minute after the real sunset at the user's point`() {
        val sunset = at("18:55") + 20.seconds
        val day = combine(listOf(one, two, three), realSunset = sunset)
        assertEquals(at("18:56"), day.maghrib)
        assertEquals(at("18:56"), combine(listOf(one, two, three), realSunset = at("18:55")).maghrib, "on the minute")
        // A minute only the real sunset gives is taken from no member and marks nothing, whatever the
        // most-followed member's own is.
        assertEquals(emptySet(), day.setByRule)
        assertFalse(day.polar || day.maghribHeld)
        val ruledChief = three.copy(setByRule = setOf(Prayer.MAGHRIB), polar = true, sunrise = at("07:11"))
        val floored = combine(listOf(one, two, ruledChief), mostFollowed = 2, realSunset = sunset)
        assertEquals(at("18:56"), floored.maghrib)
        assertEquals(emptySet(), floored.setByRule)
        assertFalse(floored.polar)
        // Where the members are later, the floor holds nothing.
        assertEquals(at("18:53"), combine(listOf(one, two, three), realSunset = at("18:51") + 59.seconds).maghrib)
    }

    /**
     * Item E of the adversarial check: where the real sun does not set at the user's point, no member's Maghrib
     * counts as from the sun (a member's own sun model can still set it): the most-followed member's own.
     */
    @Test
    fun `where the real sun does not set at the user's point only the most followed member's maghrib counts`() {
        val day = combine(listOf(one, two, three), mostFollowed = 2, realSunset = null)
        assertEquals(three.maghrib, day.maghrib)
        assertEquals(emptySet(), day.setByRule)
        val ruled = three.copy(setByRule = setOf(Prayer.MAGHRIB), polar = true, sunrise = at("07:11"))
        val polarDay = combine(listOf(one, two, ruled), mostFollowed = 2, realSunset = null)
        assertEquals(three.maghrib, polarDay.maghrib)
        assertEquals(setOf(Prayer.MAGHRIB), polarDay.setByRule)
        assertTrue(polarDay.polar)
    }

    @Test
    fun `a cautious day ends each prayer at the earliest end in play`() {
        val day = combine(listOf(one, two, three), mostFollowed = 0)
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
        val day = combine(members, mostFollowed = 0)
        for (prayer in listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)) {
            assertEquals(members.minOf { it.ends.getValue(prayer) }, day.ends[prayer], "$prayer")
        }
        // ISNA's 15° Isha comes before the 17° dusk, so Maghrib ends at it.
        assertEquals(members[0].isha, day.ends[Prayer.MAGHRIB])
        assertEquals(members[1].isha, day.isha)
        assertEquals(members[0].isha, day.earliestStart[Prayer.ISHA])
    }

    @Test
    fun `a maghrib end not after the maghrib shown is left out`() {
        // Ruling R158, as R26 leaves out an Isha end not after the latest Isha, as at Haukipudas on 4 June 2027: one
        // member's Isha comes a minute or two after its own Maghrib and no later than the other member's Maghrib, the
        // latest from the sun and the one shown. By that member's timetable Maghrib's time is over when it is shown, so
        // no end is the cautious one: the other member's later Isha would be a late end. This day needs no repair (5
        // such app city-days in 2027 are repaired, where the Maghrib shown is not before the latest Isha either).
        val held = member("held", "05:50 07:10 13:02 17:05 18:52 18:54", asrOther = "16:17", sunset = "18:50")
        val later = member("later", "05:50 07:08 13:03 16:18 18:54 20:25", asrOther = "17:06", sunset = "18:51")
        val day = combine(listOf(held, later), mostFollowed = 0)
        assertEquals(at("18:54"), day.maghrib)
        assertNull(day.ends[Prayer.MAGHRIB], "no end, never the later member's Isha")
        assertTrue(Invariants.holds(day), "in order without a repair")
        // Where the earliest end in play is after the Maghrib shown, it is Maghrib's end.
        val open = combine(listOf(held.copy(isha = at("18:56")), later), mostFollowed = 0)
        assertEquals(at("18:56"), open.ends[Prayer.MAGHRIB])
    }

    @Test
    fun `a combined isha end not after the latest isha is left out`() {
        val early = one.copy(ends = mapOf(Prayer.ISHA to at("20:15")))
        val late = two.copy(ends = mapOf(Prayer.ISHA to at("20:35")))
        // The earliest member's end (20:15) is before the latest Isha (the second's 20:25).
        assertNull(combine(listOf(early, late), mostFollowed = 0).ends[Prayer.ISHA])
        val after = early.copy(ends = mapOf(Prayer.ISHA to at("20:26")))
        assertEquals(at("20:26"), combine(listOf(after, late), mostFollowed = 0).ends[Prayer.ISHA])
    }

    @Test
    fun `flags from any member carry over`() {
        val flagged = two.copy(setByRule = setOf(Prayer.ISHA), polar = true, repaired = true)
        val day = combine(listOf(one, flagged, three), mostFollowed = 0)
        assertEquals(setOf(Prayer.ISHA), day.setByRule)
        assertTrue(day.polar)
        assertTrue(day.repaired)
        val clean = combine(listOf(one, two, three), mostFollowed = 0)
        assertEquals(emptySet(), clean.setByRule)
        assertFalse(clean.polar || clean.repaired)
    }

    @Test
    fun `a flag or a polar day counts only through the members a time was taken from`() {
        // A polar member none of whose times is chosen leaves the day unflagged and not polar.
        val quiet = three.copy(polar = true, setByRule = Prayer.entries.toSet(), sunrise = at("07:11"))
        val day = combine(listOf(one, two, quiet), mostFollowed = 0)
        assertEquals(emptySet(), day.setByRule, "every chosen time is also a real member's")
        assertFalse(day.polar)

        // A flagged Fajr that ties with the real ones is real; a flagged Isha that is the latest is not.
        val flagged = two.copy(setByRule = setOf(Prayer.FAJR, Prayer.ISHA))
        assertEquals(setOf(Prayer.ISHA), combine(listOf(one, flagged, three), mostFollowed = 0).setByRule)

        // A polar member whose Isha is taken makes the day polar.
        val polarIsha = three.copy(isha = at("20:40"), polar = true, setByRule = Prayer.entries.toSet())
        val polarDay = combine(listOf(one, two, polarIsha), mostFollowed = 0)
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
        val day = combine(listOf(earlySunrise, lateDawn, three), mostFollowed = 0)
        assertEquals(at("07:09"), day.fajr)
        assertEquals(at("07:10"), day.sunrise, "the sunrise of the member whose Fajr is shown")
        assertEquals(at("07:10"), day.ends[Prayer.FAJR])
        assertTrue(Invariants.holds(day))
        // The mark follows the member the sunrise is taken from (three's own sunrise is a minute later here).
        val ruled = lateDawn.copy(setByRule = setOf(Prayer.SUNRISE))
        assertEquals(setOf(Prayer.SUNRISE), combine(listOf(earlySunrise, ruled, three.copy(sunrise = at("07:11"))), mostFollowed = 0).setByRule)

        // Members tied on the Fajr shown: the earliest of their sunrises.
        val tied = three.copy(fajr = at("07:09"), sunrise = at("07:12"))
        assertEquals(at("07:10"), combine(listOf(earlySunrise, lateDawn, tied), mostFollowed = 0).sunrise)
        assertEquals(at("07:12"), combine(listOf(earlySunrise, lateDawn.copy(sunrise = at("07:13")), tied), mostFollowed = 0).sunrise)

        // Where the earliest sunrise is after the Fajr shown nothing changes: the earliest sunrise.
        assertEquals(at("07:08"), combine(listOf(one, two, three), mostFollowed = 0).sunrise)
    }

    @Test
    fun `what any member declares not followed the day declares`() {
        // Ruling R90: a member whose own Fajr the sun had risen by keeps a Fajr before the sunrise
        // and declares its own; the day declares it too, whether that member's Fajr is shown or not.
        val declared = three.copy(fajr = at("05:40"), notFollowed = setOf(Prayer.FAJR))
        assertEquals(setOf(Prayer.FAJR), combine(listOf(one, two, declared), mostFollowed = 0).notFollowed)
        assertEquals(at("05:50"), combine(listOf(one, two, declared), mostFollowed = 0).fajr)
        assertEquals(emptySet(), combine(listOf(one, two, three), mostFollowed = 0).notFollowed)
    }

    @Test
    fun `members must be one date and the most followed one of them`() {
        assertFailsWith<IllegalArgumentException> { combine(emptyList(), mostFollowed = 0) }
        assertFailsWith<IllegalArgumentException> { combine(listOf(one, two), mostFollowed = 2) }
        val tomorrow = two.copy(date = LocalDate(2026, 10, 2))
        assertFailsWith<IllegalArgumentException> { combine(listOf(one, tomorrow), mostFollowed = 0) }
    }
}
