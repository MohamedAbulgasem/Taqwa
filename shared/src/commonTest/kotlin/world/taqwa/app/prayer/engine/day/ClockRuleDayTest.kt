package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.ClockRule
import world.taqwa.app.prayer.engine.method.ClockTime
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The day computer's last step, an authority's own clock rule (ruling R82), on invented rules: each
 * gives times a fixed number of seconds from the method's own day, so that every expectation is
 * read from the engine's own values (ruling R69: no printed time).
 */
class ClockRuleDayTest {
    private val noRamadan = RamadanCalendar { false }
    private val rome = TimeZone.of("Europe/Rome")
    private val point = GeoPoint(45.0, 10.0)
    private val date = LocalDate(2026, 3, 10)
    private val method = TimetableMethod(id = "test.clock", fajrAngle = 18.0, isha = IshaRule.Angle(17.0))

    private fun compute(m: TimetableMethod, at: GeoPoint = point, on: LocalDate = date, zone: TimeZone = rome, school: AsrSchool = AsrSchool.STANDARD) =
        DayComputer.compute(m, at, on, zone, school, noRamadan, emptySet())

    private val own = compute(method)

    private fun Instant.s(seconds: Int) = (epochSeconds + seconds).toDouble()
    private fun startOf(epochSeconds: Double) = Instant.fromEpochSeconds(ceil(epochSeconds / 60.0).toLong() * 60)
    private fun endOf(epochSeconds: Double) = Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    /** A rule that gives [times] everywhere (a data class, as a registry rule must be). */
    private data class Fixed(val times: ClockTimes) : ClockRule {
        override fun on(date: LocalDate, point: GeoPoint, zone: TimeZone) = times
    }

    /** A rule whose times at a point [northShiftSeconds] later per degree of latitude above 45° N. */
    private data class ByLatitude(val times: ClockTimes, val northShiftSeconds: Double) : ClockRule {
        override fun on(date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTimes {
            val k = (point.lat - 45.0) * northShiftSeconds
            fun ClockTime?.moved() = this?.copy(epochSeconds = epochSeconds + k)
            return times.copy(
                fajr = times.fajr.moved(), sunrise = times.sunrise.moved(), dhuhr = times.dhuhr.moved(),
                asrStandard = times.asrStandard.moved(), asrHanafi = times.asrHanafi.moved(), maghrib = times.maghrib.moved(),
                isha = times.isha.moved(), endOfEating = times.endOfEating?.plus(k),
            )
        }
    }

    private fun sun(epochSeconds: Double) = ClockTime(epochSeconds, estimated = false)
    private fun ruled(epochSeconds: Double) = ClockTime(epochSeconds, estimated = true)

    /** Every event 3 min 10 s after the method's own (ends 3 min 10 s before), each the sun's. */
    private val later = ClockTimes(
        fajr = sun(own.fajr.s(190)), sunrise = sun(own.sunrise.s(-190)), dhuhr = sun(own.dhuhr.s(190)),
        asrStandard = sun(own.asr.s(190)), asrHanafi = sun(own.asrOther.s(190)), maghrib = sun(own.maghrib.s(190)),
        isha = sun(own.isha.s(190)), endOfEating = own.endOfEating.s(-190), sunset = null, noSunset = false,
    )

    @Test
    fun `without a clock rule the day is the method's own`() {
        assertEquals(own, compute(method.copy(clockRule = null)))
        assertTrue(own.notFollowed.isEmpty())
    }

    @Test
    fun `the rule's starts are rounded up and its ends down with the method's own margins`() {
        val margins = EventOffsets(fajr = 20, sunrise = -20, dhuhr = 40, asr = 50, maghrib = 30, isha = 45)
        val day = compute(method.copy(clockRule = Fixed(later), margins = margins))
        val t = later
        assertEquals(startOf(t.fajr!!.epochSeconds + 20), day.fajr)
        assertEquals(startOf(t.dhuhr!!.epochSeconds + 40), day.dhuhr)
        assertEquals(startOf(t.asrStandard!!.epochSeconds + 50), day.asr)
        assertEquals(startOf(t.asrHanafi!!.epochSeconds + 50), day.asrOther)
        assertEquals(startOf(t.maghrib!!.epochSeconds + 30), day.maghrib)
        assertEquals(startOf(t.isha!!.epochSeconds + 45), day.isha)
        assertEquals(endOf(t.sunrise!!.epochSeconds - 20), day.sunrise)
        // The end of eating keeps the method's own end margin (0 here).
        assertEquals(endOf(t.endOfEating!!), day.endOfEating)
        assertTrue(day.notFollowed.isEmpty())
        assertTrue(Invariants.holds(day), "$day")
    }

    @Test
    fun `a hanafi day shows the rule's hanafi asr and keeps the standard as the other`() {
        val day = compute(method.copy(clockRule = Fixed(later)), school = AsrSchool.HANAFI)
        assertEquals(startOf(later.asrHanafi!!.epochSeconds), day.asr)
        assertEquals(startOf(later.asrStandard!!.epochSeconds), day.asrOther)
    }

    @Test
    fun `starts are the latest and ends the earliest over the fixed point and the user's`() {
        val north = GeoPoint(46.0, 10.0)
        val rule = ByLatitude(later, northShiftSeconds = 600.0)
        val day = compute(method.copy(clockRule = rule, fixedPoint = north))
        val own2 = compute(method.copy(fixedPoint = north))
        assertEquals(startOf(later.dhuhr!!.epochSeconds + 600), day.dhuhr, "the northern point's, 10 min later")
        assertEquals(minOf(own2.sunrise, endOf(later.sunrise!!.epochSeconds)), day.sunrise, "the user's own, the earlier")
        assertEquals(endOf(later.endOfEating!!), day.endOfEating, "the user's own, the earlier")
    }

    @Test
    fun `sunrise is never after the method's own`() {
        val day = compute(method.copy(clockRule = Fixed(later.copy(sunrise = sun(own.sunrise.s(400))))))
        assertEquals(own.sunrise, day.sunrise)
    }

    @Test
    fun `where the rule's fajr is shown its own dawn ends eating never after that fajr`() {
        // The authority's own end (spec §3.3), even after the method's own dawn.
        val afterOwn = later.copy(endOfEating = own.endOfEating.s(100))
        val day = compute(method.copy(clockRule = Fixed(afterOwn)))
        assertEquals(endOf(afterOwn.endOfEating!!), day.endOfEating)
        assertTrue(day.endOfEating > own.endOfEating && day.endOfEating <= day.fajr)
        val afterFajr = later.copy(endOfEating = later.fajr!!.epochSeconds + 600)
        assertEquals(startOf(later.fajr!!.epochSeconds), compute(method.copy(clockRule = Fixed(afterFajr))).endOfEating)
    }

    @Test
    fun `a fajr the rule puts at or after the sunrise shown is not followed and the day's own stays before it`() {
        val afterSunrise = later.copy(fajr = sun(own.sunrise.s(1800)), endOfEating = own.endOfEating.s(400))
        val day = compute(method.copy(clockRule = Fixed(afterSunrise)))
        assertEquals(own.fajr, day.fajr)
        assertTrue(day.fajr < day.sunrise)
        assertEquals(setOf(Prayer.FAJR), day.notFollowed)
        // Its end of eating is then the earlier of the rule's and the method's own.
        assertEquals(own.endOfEating, day.endOfEating)
        assertTrue(Invariants.holds(day), "$day")
    }

    @Test
    fun `a fajr the rule puts before the sunrise shown is followed`() {
        val day = compute(method.copy(clockRule = Fixed(later)))
        assertEquals(startOf(later.fajr!!.epochSeconds), day.fajr)
        assertFalse(Prayer.FAJR in day.notFollowed)
    }

    @Test
    fun `maghrib is never before the sun's own and isha comes after the maghrib shown`() {
        val early = later.copy(maghrib = sun(own.maghrib.s(-1200)), isha = sun(own.maghrib.s(-600)))
        val day = compute(method.copy(clockRule = Fixed(early)))
        assertEquals(own.maghrib, day.maghrib)
        assertEquals(own.isha, day.isha, "the method's own Isha, after the Maghrib shown")
        assertTrue(Invariants.holds(day), "$day")
    }

    @Test
    fun `an isha before the maghrib shown gives way to a minute after it`() {
        // The rule's Maghrib is late and its Isha before it: the method's own Isha is before it too.
        val odd = later.copy(maghrib = sun(own.isha.s(1200)), isha = sun(own.isha.s(600)))
        val day = compute(method.copy(clockRule = Fixed(odd)))
        assertEquals(startOf(odd.maghrib!!.epochSeconds), day.maghrib)
        assertEquals(day.maghrib + 1.minutes, day.isha)
    }

    @Test
    fun `a rule's estimates are set by rule and its sun's times are not`() {
        val estimates = later.copy(fajr = ruled(own.fajr.s(190)), maghrib = ruled(own.maghrib.s(190)))
        val day = compute(method.copy(clockRule = Fixed(estimates)))
        assertEquals(setOf(Prayer.FAJR, Prayer.MAGHRIB), day.setByRule)
    }

    @Test
    fun `a null time leaves the method's own`() {
        val partial = later.copy(dhuhr = null, isha = null, endOfEating = null)
        val day = compute(method.copy(clockRule = Fixed(partial)))
        assertEquals(own.dhuhr, day.dhuhr)
        assertEquals(own.isha, day.isha)
        assertEquals(minOf(own.endOfEating, day.fajr), day.endOfEating)
    }

    @Test
    fun `the ends follow the times shown`() {
        val day = compute(method.copy(clockRule = Fixed(later)))
        assertEquals(day.sunrise, day.ends[Prayer.FAJR])
        assertEquals(day.asr, day.ends[Prayer.DHUHR])
        assertEquals(day.sunset, day.ends[Prayer.ASR])
        assertTrue(day.ends.getValue(Prayer.MAGHRIB) <= day.isha)
        assertTrue(day.ends.getValue(Prayer.MAGHRIB) > day.maghrib)
    }

    // A polar day: Tromsø's latitude on the June solstice, where the sun does not set.

    private val arctic = GeoPoint(69.65, 18.95)
    private val oslo = TimeZone.of("Europe/Oslo")
    private val solstice = LocalDate(2026, 6, 21)
    private val polar = compute(method, arctic, solstice, oslo)

    /** A whole invented day around the method's own Dhuhr (the transit, rounded up), as a clock rule might keep one. */
    private val wholeDay: ClockTimes = polar.dhuhr.epochSeconds.toDouble().let { noon ->
        ClockTimes(
            fajr = ruled(noon - 7 * 3600.0), sunrise = ruled(noon - 5.75 * 3600), dhuhr = sun(noon + 300.0),
            asrStandard = ruled(noon + 3 * 3600.0), asrHanafi = ruled(noon + 4 * 3600.0), maghrib = ruled(noon + 6 * 3600.0),
            isha = ruled(noon + 7.3 * 3600), endOfEating = noon - 7 * 3600.0, sunset = noon + 6 * 3600.0 - 300,
            noSunset = true, notFollowed = setOf(Prayer.SUNRISE),
        )
    }

    @Test
    fun `where the sun does not set the rule's whole day stands alone`() {
        assertTrue(polar.polar)
        val day = compute(method.copy(clockRule = Fixed(wholeDay)), arctic, solstice, oslo)
        val t = wholeDay
        assertEquals(startOf(t.fajr!!.epochSeconds), day.fajr)
        assertEquals(endOf(t.sunrise!!.epochSeconds), day.sunrise, "the rule's sunrise, though the method's own is earlier")
        assertTrue(day.sunrise > polar.sunrise)
        assertEquals(startOf(t.maghrib!!.epochSeconds), day.maghrib, "no sunset to wait for")
        assertEquals(endOf(t.sunset!!), day.sunset)
        assertEquals(startOf(t.isha!!.epochSeconds), day.isha)
        assertEquals(endOf(t.endOfEating!!), day.endOfEating, "the rule's Fajr is shown, so its own dawn ends eating")
        assertEquals(setOf(Prayer.SUNRISE), day.notFollowed)
        assertFalse(day.polar, "every time is the rule's: none follows the nearest latitude")
        assertEquals(setOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA), day.setByRule)
        assertTrue(Invariants.holds(day), "$day")
    }

    @Test
    fun `a polar day the rule gives only in part still follows the nearest latitude`() {
        val partial = wholeDay.copy(fajr = null, isha = null)
        assertTrue(compute(method.copy(clockRule = Fixed(partial)), arctic, solstice, oslo).polar)
    }

    @Test
    fun `where the sun does set a rule's maghrib before it waits for it`() {
        // The same rule on a date the sun sets: the method's own Maghrib is the later.
        val ordinaryDay = compute(method, arctic, LocalDate(2026, 3, 10), oslo)
        val early = ClockTimes(
            fajr = null, sunrise = null, dhuhr = null, asrStandard = null, asrHanafi = null,
            maghrib = ruled(ordinaryDay.maghrib.s(-3600)), isha = null, endOfEating = null, sunset = null, noSunset = false,
        )
        val day = compute(method.copy(clockRule = Fixed(early)), arctic, LocalDate(2026, 3, 10), oslo)
        assertEquals(ordinaryDay.maghrib, day.maghrib)
        assertFalse(Prayer.MAGHRIB in day.setByRule)
    }
}
