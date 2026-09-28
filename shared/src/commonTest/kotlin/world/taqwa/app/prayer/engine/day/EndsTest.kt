package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class EndsTest {
    private val noRamadan = RamadanCalendar { false }
    private val makkah = GeoPoint(21.426666, 39.831666)
    private val riyadh = TimeZone.of("Asia/Riyadh")
    private val date = LocalDate(2026, 9, 20)

    private fun method(isha: IshaRule) = TimetableMethod(id = "test.ends", fajrAngle = 18.0, isha = isha)

    private fun compute(
        isha: IshaRule,
        point: GeoPoint = makkah,
        on: LocalDate = date,
        zone: TimeZone = riyadh,
        school: AsrSchool = AsrSchool.STANDARD,
    ) = DayComputer.compute(method(isha), point, on, zone, school, noRamadan)

    /** The 17° dusk at Makkah on [date], rounded down. */
    private val redTwilightEnd: Instant = run {
        val raw = SunClock(makkah.lat, makkah.lon, date, 3 * 3600, SunModel.EXACT).altitudeTime(-17.0, morning = false)!!
        Instant.fromEpochSeconds(floor(raw / 60).toLong() * 60)
    }

    @Test
    fun `fajr ends at sunrise dhuhr at the standard asr and asr at sunset`() {
        val standard = compute(IshaRule.Angle(17.0))
        assertEquals(standard.sunrise, standard.ends[Prayer.FAJR])
        assertEquals(standard.asr, standard.ends[Prayer.DHUHR])
        assertEquals(standard.sunset, standard.ends[Prayer.ASR])
        assertTrue(standard.sunset <= standard.maghrib)

        val hanafi = compute(IshaRule.Angle(17.0), school = AsrSchool.HANAFI)
        assertEquals(hanafi.asrOther, hanafi.ends[Prayer.DHUHR], "Dhuhr ends at the Standard Asr in either school")
        assertEquals(standard.ends[Prayer.DHUHR], hanafi.ends[Prayer.DHUHR])
    }

    @Test
    fun `maghrib ends at the earlier of isha and the 17 degree dusk`() {
        val early = compute(IshaRule.Angle(15.0))
        assertTrue(early.isha < redTwilightEnd)
        assertEquals(early.isha, early.ends[Prayer.MAGHRIB])

        val late = compute(IshaRule.Angle(18.0))
        assertEquals(redTwilightEnd, late.ends[Prayer.MAGHRIB])

        val same = compute(IshaRule.Angle(17.0))
        assertEquals(redTwilightEnd, same.ends[Prayer.MAGHRIB], "the dusk rounded down, the Isha start up")
        assertEquals(same.isha, redTwilightEnd + 1.minutes)

        val afterMaghrib = compute(IshaRule.AfterMaghrib(90))
        assertEquals(redTwilightEnd, afterMaghrib.ends[Prayer.MAGHRIB])
    }

    @Test
    fun `where the sun never gets 17 degrees low maghrib ends at isha`() {
        val london = compute(IshaRule.Angle(17.0), GeoPoint(51.5074, -0.1278), LocalDate(2026, 6, 21), TimeZone.of("Europe/London"))
        assertTrue(Prayer.ISHA in london.setByRule)
        assertEquals(london.isha, london.ends[Prayer.MAGHRIB])
    }

    @Test
    fun `isha ends at the next day's end of eating once the caller has it`() {
        val today = compute(IshaRule.Angle(17.0))
        assertNull(today.ends[Prayer.ISHA])
        val tomorrow = compute(IshaRule.Angle(17.0), on = date.plus(1, DateTimeUnit.DAY))
        val joined = Ends.withNextDay(today, tomorrow)
        assertEquals(tomorrow.endOfEating, joined.ends[Prayer.ISHA])
        assertEquals(today.copy(ends = today.ends + (Prayer.ISHA to tomorrow.endOfEating)), joined)
        assertFailsWith<IllegalArgumentException> { Ends.withNextDay(today, today) }
    }

    @Test
    fun `an isha end that is not after the isha start is left out`() {
        // Ruling R26: at Murmansk on 20 May an Isha 90 minutes after a late Maghrib falls after the
        // next day's end of eating; Isha keeps its start, and it has no end.
        val murmansk = GeoPoint(68.97, 33.07)
        val moscow = TimeZone.of("Europe/Moscow")
        val date = LocalDate(2026, 5, 20)
        val today = compute(IshaRule.AfterMaghrib(90), murmansk, date, moscow)
        val tomorrow = compute(IshaRule.AfterMaghrib(90), murmansk, date.plus(1, DateTimeUnit.DAY), moscow)
        assertTrue(tomorrow.endOfEating <= today.isha, "isha ${today.isha} next end of eating ${tomorrow.endOfEating}")
        val joined = Ends.withNextDay(today, tomorrow)
        assertNull(joined.ends[Prayer.ISHA])
        assertEquals(today, joined, "Isha itself is not capped")

        // One minute after the start is still an end.
        val later = tomorrow.copy(endOfEating = today.isha + 1.minutes)
        assertEquals(today.isha + 1.minutes, Ends.withNextDay(today, later).ends[Prayer.ISHA])
        val same = tomorrow.copy(endOfEating = today.isha)
        assertNull(Ends.withNextDay(today, same).ends[Prayer.ISHA])
    }

    @Test
    fun `every end is a whole minute and a polar day ends at its own latitude`() {
        val murmansk = compute(IshaRule.Angle(17.0), GeoPoint(68.97, 33.07), LocalDate(2026, 6, 21), TimeZone.of("Europe/Moscow"))
        for (day in listOf(compute(IshaRule.Angle(17.0)), murmansk)) {
            assertEquals(setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB), day.ends.keys)
            day.ends.values.forEach { assertEquals(0L, it.epochSeconds % 60) }
            assertEquals(day.sunrise, day.ends[Prayer.FAJR])
            assertEquals(day.sunset, day.ends[Prayer.ASR])
        }
    }

    @Test
    fun `the pure helper leaves out what it is not given`() {
        val t = Instant.fromEpochSeconds(1_790_000_000L / 60 * 60)
        val ends = Ends.of(
            sunrise = t, standardAsr = t + 400.minutes, sunset = t + 600.minutes,
            earliestIsha = t + 700.minutes, maghribEndCap = null, nextEndOfEating = null,
        )
        assertEquals(
            mapOf(
                Prayer.FAJR to t, Prayer.DHUHR to t + 400.minutes, Prayer.ASR to t + 600.minutes,
                Prayer.MAGHRIB to t + 700.minutes,
            ),
            ends,
        )
        val withAll = Ends.of(t, t + 400.minutes, t + 600.minutes, t + 700.minutes, t + 680.minutes, t + 1100.minutes)
        assertEquals(t + 680.minutes, withAll[Prayer.MAGHRIB])
        assertEquals(t + 1100.minutes, withAll[Prayer.ISHA])
    }
}
