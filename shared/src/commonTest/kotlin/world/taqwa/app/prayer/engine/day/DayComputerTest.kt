package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.DayRule
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.Harmonics
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.RamadanRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DayComputerTest {
    private val noRamadan = RamadanCalendar { false }
    private val alwaysRamadan = RamadanCalendar { true }

    private fun local(date: LocalDate, hhmm: String, zone: TimeZone): Instant {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return date.atTime(h, m).toInstant(zone)
    }

    private fun Instant.clock(zone: TimeZone): String = toLocalDateTime(zone).time.toString()

    private fun daysInYear(year: Int) = LocalDate(year, 12, 31).dayOfYear

    private fun PrayerDay.starts() = listOf(fajr, dhuhr, asr, asrOther, maghrib, isha)

    private fun PrayerDay.everyInstant() =
        listOfNotNull(fajr, sunrise, dhuhr, asr, asrOther, maghrib, isha, sunset, endOfEating, imsak)

    private fun plain(id: String) = TimetableMethod(id = id, fajrAngle = 18.0, isha = IshaRule.Angle(17.0))

    private fun compute(
        method: TimetableMethod,
        point: GeoPoint,
        date: LocalDate,
        zone: TimeZone,
        ramadan: RamadanCalendar = noRamadan,
        lagDates: Set<LocalDate> = emptySet(),
        school: AsrSchool = AsrSchool.STANDARD,
    ) = DayComputer.compute(method, point, date, zone, school, ramadan, lagDates)

    // Umm al-Qura: structure only (its official times are not openly licensed, so none are here).

    private val makkah = GeoPoint(21.426666, 39.831666)
    private val riyadhZone = TimeZone.of("Asia/Riyadh")
    private val uqBias = Harmonics(
        -0.008390,
        listOf(0.032536 to 0.323067, -0.046890 to 0.030917, 0.003527 to 0.019364),
    )
    private val ummAlQura = TimetableMethod(
        id = "test.ummalqura",
        fajrAngle = 18.5,
        isha = IshaRule.AfterMaghrib(90, 120),
        fajrDeclinationBias = uqBias,
        asrBiasFactor = 0.4,
        margins = EventOffsets(fajr = 8, sunrise = -4, dhuhr = 8, asr = 8, maghrib = 8, isha = 8),
        dayRule = DayRule.LAG_DATES_UQ,
    )
    private val uqRamadan = RamadanCalendar { it in UmmAlQuraDates.ramadanDates }

    @Test
    fun `umm al qura days are whole minutes in order and never before the raw events`() {
        for (date in listOf(LocalDate(2026, 9, 20), LocalDate(2027, 2, 15))) {
            val day = compute(ummAlQura, makkah, date, riyadhZone, uqRamadan, UmmAlQuraDates.lagDates)
            day.everyInstant().forEach { assertEquals(0L, it.epochSeconds % 60, "$date: $it is not a whole minute") }
            val order = listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha)
            assertEquals(order.sorted(), order, "$date: out of order")
            assertEquals(order.size, order.toSet().size, "$date: two prayers at one minute")
            assertEquals("test.ummalqura", day.methodId)

            val clock = SunClock(makkah.lat, makkah.lon, date, 3 * 3600, SunModel.EXACT)
            val bias = uqBias.at(date.dayOfYear, daysInYear(date.year))
            val rawSunset = clock.altitudeTime(-0.8333, morning = false)!!
            val ishaMinutes = if (uqRamadan.isRamadan(date)) 120 else 90
            val raw = listOf(
                "fajr" to (day.fajr to clock.altitudeTime(-18.5, morning = true, declinationBiasDeg = bias)!!),
                "dhuhr" to (day.dhuhr to clock.transit()),
                "asr" to (day.asr to clock.asr(1.0, AsrModel.EXACT_MOMENT, declinationBiasDeg = 0.4 * bias)!!),
                "asrOther" to (day.asrOther to clock.asr(2.0, AsrModel.EXACT_MOMENT, declinationBiasDeg = 0.4 * bias)!!),
                "maghrib" to (day.maghrib to rawSunset),
                "isha" to (day.isha to rawSunset + ishaMinutes * 60),
            )
            for ((name, pair) in raw) {
                val (shown, model) = pair
                assertTrue(shown.epochSeconds >= model, "$date $name ${shown.clock(riyadhZone)} before raw $model")
                assertTrue(shown.epochSeconds - model < 60 + 8 + 1, "$date $name ${shown.clock(riyadhZone)} too late")
            }
            val rawSunrise = clock.altitudeTime(-0.8333, morning = true)!!
            assertTrue(day.sunrise.epochSeconds <= rawSunrise, "$date sunrise after raw")
            assertTrue(day.endOfEating <= day.fajr, "$date end of eating after fajr")
        }
    }

    @Test
    fun `umm al qura isha is ninety minutes after maghrib and two hours in ramadan`() {
        assertFalse(uqRamadan.isRamadan(LocalDate(2026, 9, 20)))
        assertTrue(uqRamadan.isRamadan(LocalDate(2027, 2, 15)))
        val september = compute(ummAlQura, makkah, LocalDate(2026, 9, 20), riyadhZone, uqRamadan)
        assertEquals(90.minutes, september.isha - september.maghrib)
        val ramadan = compute(ummAlQura, makkah, LocalDate(2027, 2, 15), riyadhZone, uqRamadan)
        assertEquals(120.minutes, ramadan.isha - ramadan.maghrib)
    }

    @Test
    fun `umm al qura dates are parsed from the derived lists`() {
        assertEquals(212, UmmAlQuraDates.ramadanDates.size)
        assertEquals(114, UmmAlQuraDates.lagDates.size)
        assertTrue(LocalDate(2026, 9, 15) in UmmAlQuraDates.lagDates)
        assertFalse(LocalDate(2026, 9, 20) in UmmAlQuraDates.lagDates)
        assertTrue(LocalDate(2026, 2, 18) in UmmAlQuraDates.ramadanDates)
    }

    @Test
    fun `on a lag date starts take the later of today and yesterday and sunrise the earlier`() {
        val date = LocalDate(2026, 9, 15)
        val sameDay = ummAlQura.copy(dayRule = DayRule.SAME_DAY)
        val today = compute(sameDay, makkah, date, riyadhZone, uqRamadan)
        val yesterday = compute(sameDay, makkah, date.minus(1, DateTimeUnit.DAY), riyadhZone, uqRamadan)
        val lagged = compute(ummAlQura, makkah, date, riyadhZone, uqRamadan, lagDates = setOf(date))
        today.starts().zip(yesterday.starts()).zip(lagged.starts()).forEach { (pair, shown) ->
            assertEquals(maxOf(pair.first, pair.second + 1.days), shown)
        }
        assertEquals(minOf(today.sunrise, yesterday.sunrise + 1.days), lagged.sunrise)
        assertEquals(today, compute(ummAlQura, makkah, date, riyadhZone, uqRamadan, lagDates = emptySet()))
    }

    @Test
    fun `after the listed lag dates every umm al qura day takes the later of today and yesterday`() {
        // Ruling R71: the derived list covers 2024 to 2030; after its last date no list says which
        // days lag, so every day takes the later of the two days' starts and the earlier of their ends.
        assertEquals(LocalDate(2030, 12, 31), UmmAlQuraDates.lastDate)
        assertTrue(UmmAlQuraDates.lagDates.all { it <= UmmAlQuraDates.lastDate })
        val sameDay = ummAlQura.copy(dayRule = DayRule.SAME_DAY)
        var moved = 0
        for (date in (1..12).map { LocalDate(2031, it, 10) } + LocalDate(2031, 1, 1)) {
            // Without Ramadan, so that both days count Isha's minutes alike.
            val today = compute(sameDay, makkah, date, riyadhZone)
            val yesterday = compute(sameDay, makkah, date.minus(1, DateTimeUnit.DAY), riyadhZone)
            val shown = compute(ummAlQura, makkah, date, riyadhZone, lagDates = UmmAlQuraDates.lagDates)
            today.starts().zip(yesterday.starts()).zip(shown.starts()).forEach { (pair, start) ->
                assertEquals(maxOf(pair.first, pair.second + 1.days), start, "$date")
            }
            assertEquals(minOf(today.sunrise, yesterday.sunrise + 1.days), shown.sunrise, "$date")
            assertEquals(minOf(today.endOfEating, yesterday.endOfEating + 1.days), shown.endOfEating, "$date")
            if (shown.starts() != today.starts()) moved++
        }
        assertTrue(moved > 0, "the rule moved no start in 2031")
        // Within the list's years a day it does not list is its own day, the week after its last
        // lag date included.
        for (unlisted in listOf(LocalDate(2030, 6, 10), LocalDate(2030, 12, 26), LocalDate(2030, 12, 31))) {
            assertFalse(unlisted in UmmAlQuraDates.lagDates)
            assertEquals(
                compute(sameDay, makkah, unlisted, riyadhZone, uqRamadan),
                compute(ummAlQura, makkah, unlisted, riyadhZone, uqRamadan, UmmAlQuraDates.lagDates),
                "$unlisted",
            )
        }
    }

    @Test
    fun `the lag rule uses the umm al qura dates when a caller gives none`() {
        val date = LocalDate(2026, 9, 15)
        val implicit = DayComputer.compute(ummAlQura, makkah, date, riyadhZone, AsrSchool.STANDARD, uqRamadan)
        assertEquals(compute(ummAlQura, makkah, date, riyadhZone, uqRamadan, UmmAlQuraDates.lagDates), implicit)
        val sameDay = compute(ummAlQura, makkah, date, riyadhZone, uqRamadan, lagDates = emptySet())
        assertTrue(implicit.starts() != sameDay.starts(), "the lag rule changed nothing on $date")
    }

    @Test
    fun `a lag day on the first of january borrows the last day of a leap year`() {
        val date = LocalDate(2025, 1, 1)
        val sameDay = ummAlQura.copy(dayRule = DayRule.SAME_DAY)
        val today = compute(sameDay, makkah, date, riyadhZone, uqRamadan)
        val newYearsEve = compute(sameDay, makkah, LocalDate(2024, 12, 31), riyadhZone, uqRamadan)
        val lagged = compute(ummAlQura, makkah, date, riyadhZone, uqRamadan, lagDates = setOf(date))
        today.starts().zip(newYearsEve.starts()).zip(lagged.starts()).forEach { (pair, shown) ->
            assertEquals(maxOf(pair.first, pair.second + 1.days), shown)
        }
        assertEquals(minOf(today.sunrise, newYearsEve.sunrise + 1.days), lagged.sunrise)
    }

    @Test
    fun `the neighbours rule on the first of january reads the previous year's 31 december slot`() {
        // Ruling R28: 31 December is slot 365 in every year, a 12° dawn there, 18° every other day.
        val spike = DoubleArray(366) { if (it == 365) 12.0 else 18.0 }
        val neighbours = plain("test.newyear").copy(dayRule = DayRule.NEIGHBOURS_MUIS, fajrAngleByDayOfYear = spike)
        for (year in listOf(2024, 2025)) {
            val newYear = compute(neighbours, makkah, LocalDate(year + 1, 1, 1), riyadhZone)
            val eve = compute(plain("test.newyear").copy(fajrAngle = 12.0), makkah, LocalDate(year, 12, 31), riyadhZone)
            assertEquals(eve.fajr + 1.days, newYear.fajr, "after $year")
        }
    }

    @Test
    fun `a curve's slot is its month and day so 29 february has its own and march keeps its slot`() {
        assertEquals(59, curveSlot(LocalDate(2028, 2, 29)))
        assertEquals(60, curveSlot(LocalDate(2028, 3, 1)))
        assertEquals(60, curveSlot(LocalDate(2026, 3, 1)))
        assertEquals(365, curveSlot(LocalDate(2026, 12, 31)))
        val leapDay = plain("test.leapday").copy(fajrAngleByDayOfYear = DoubleArray(366) { if (it == 59) 12.0 else 18.0 })
        val at12 = plain("test.leapday").copy(fajrAngle = 12.0)
        assertEquals(compute(at12, makkah, LocalDate(2028, 2, 29), riyadhZone).fajr, compute(leapDay, makkah, LocalDate(2028, 2, 29), riyadhZone).fajr)
        for (date in listOf(LocalDate(2026, 3, 1), LocalDate(2028, 3, 1), LocalDate(2028, 2, 28))) {
            assertEquals(compute(plain("test.leapday"), makkah, date, riyadhZone).fajr, compute(leapDay, makkah, date, riyadhZone).fajr, "$date")
        }
    }

    // MUIS: open data (data.gov.sg Open Data Licence), rows from
    // tools/timetables/official/open/SG-MUIS/off-muis-2026.txt, Asia/Singapore.

    private val singaporeZone = TimeZone.of("Asia/Singapore")
    private val singapore = GeoPoint(1.28967, 103.85007)
    private val muis = TimetableMethod(
        id = "test.muis",
        fajrAngle = 20.0,
        isha = IshaRule.Angle(18.0),
        sunModel = SunModel.CLASSIC_NOON,
        margins = EventOffsets(fajr = 21, sunrise = 58, dhuhr = 79, asr = 31, maghrib = 18, isha = 20),
        dayRule = DayRule.NEIGHBOURS_MUIS,
        ramadan = RamadanRule(60),
        fixedPoint = singapore,
    )
    private val muisRows = mapOf(
        LocalDate(2026, 1, 15) to listOf("05:51", "07:13", "13:16", "16:39", "19:17", "20:30"),
        LocalDate(2026, 7, 15) to listOf("05:43", "07:06", "13:12", "16:36", "19:17", "20:31"),
    )

    @Test
    fun `muis rule b is never early and at most two minutes late anywhere in singapore`() {
        val tampines = GeoPoint(1.3496, 103.9568)
        for ((date, row) in muisRows) {
            val day = compute(muis, tampines, date, singaporeZone)
            val shown = listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha)
            for (i in 0..5) {
                val official = local(date, row[i], singaporeZone)
                val late = if (i == 1) official - shown[i] else shown[i] - official
                assertTrue(
                    late >= 0.minutes && late <= 2.minutes,
                    "$date ${Prayer.entries[i]}: shown ${shown[i].clock(singaporeZone)} official ${row[i]}",
                )
            }
        }
    }

    @Test
    fun `the neighbours rule takes the later of yesterday and tomorrow for starts`() {
        val date = LocalDate(2026, 7, 15)
        val sameDay = muis.copy(dayRule = DayRule.SAME_DAY)
        val before = compute(sameDay, singapore, date.minus(1, DateTimeUnit.DAY), singaporeZone)
        val after = compute(sameDay, singapore, date.plus(1, DateTimeUnit.DAY), singaporeZone)
        val shown = compute(muis, singapore, date, singaporeZone)
        before.starts().zip(after.starts()).zip(shown.starts()).forEach { (pair, start) ->
            assertEquals(maxOf(pair.first + 1.days, pair.second - 1.days), start)
        }
        assertEquals(minOf(before.sunrise + 1.days, after.sunrise - 1.days), shown.sunrise)
    }

    @Test
    fun `a fixed point keeps the given point beside it`() {
        val date = LocalDate(2026, 7, 15)
        val tampines = GeoPoint(1.3496, 103.9568)
        val free = muis.copy(fixedPoint = null)
        val atFixed = compute(free, singapore, date, singaporeZone)
        val atUser = compute(free, tampines, date, singaporeZone)
        val shown = compute(muis, tampines, date, singaporeZone)
        shown.starts().forEachIndexed { i, start -> assertEquals(maxOf(atFixed.starts()[i], atUser.starts()[i]), start) }
        assertEquals(minOf(atFixed.sunrise, atUser.sunrise), shown.sunrise)
        assertEquals(minOf(atFixed.sunset, atUser.sunset), shown.sunset)
        assertEquals(minOf(atFixed.endOfEating, atUser.endOfEating), shown.endOfEating)
        // At the fixed point itself there is one point.
        assertEquals(atFixed, compute(muis, singapore, date, singaporeZone))
    }

    @Test
    fun `an ends only fixed point bounds the ends and leaves the starts at the user's point`() {
        // Ruling R45: beyond a point table's reach its point bounds sunrise, the end of eating and imsak only.
        val date = LocalDate(2026, 7, 15)
        val user = GeoPoint(1.35, 103.85)
        val plain = muis.copy(fixedPoint = null, imsakMinutesBeforeFajr = 10)
        val alone = compute(plain, user, date, singaporeZone)
        for (fixed in listOf(GeoPoint(1.35, 103.40), GeoPoint(1.35, 104.30))) { // west, then east of the user
            val both = compute(plain.copy(fixedPoint = fixed), user, date, singaporeZone)
            val endsOnly = compute(plain.copy(fixedPoint = fixed, fixedPointMode = FixedPointMode.ENDS_ONLY), user, date, singaporeZone)
            assertEquals(alone.starts(), endsOnly.starts(), "$fixed: starts at the user's point")
            assertEquals(both.sunrise, endsOnly.sunrise)
            assertEquals(both.endOfEating, endsOnly.endOfEating)
            assertEquals(both.imsak, endsOnly.imsak)
            assertEquals(minOf(alone.sunrise, compute(plain, fixed, date, singaporeZone).sunrise), endsOnly.sunrise)
        }
        // West of the user a both-ways fixed point would move the starts; ends-only does not.
        assertTrue(compute(plain.copy(fixedPoint = GeoPoint(1.35, 103.40)), user, date, singaporeZone).starts() != alone.starts())
        // East of the user the fixed point's earlier sunrise bounds the ends.
        assertTrue(compute(plain.copy(fixedPoint = GeoPoint(1.35, 104.30), fixedPointMode = FixedPointMode.ENDS_ONLY), user, date, singaporeZone).sunrise < alone.sunrise)
    }

    @Test
    fun `west of a fixed point starts can only move later and ends earlier`() {
        val tuas = GeoPoint(1.32, 103.63)
        for (date in muisRows.keys) {
            val atFixed = compute(muis, singapore, date, singaporeZone)
            val atTuas = compute(muis, tuas, date, singaporeZone)
            atTuas.starts().zip(atFixed.starts()).forEach { (west, fixed) ->
                assertTrue(west >= fixed, "$date: ${west.clock(singaporeZone)} before ${fixed.clock(singaporeZone)}")
            }
            assertTrue(atTuas.starts() != atFixed.starts(), "$date: Tuas sets 53 s later, some start must move")
            assertTrue(atTuas.sunrise <= atFixed.sunrise)
            assertTrue(atTuas.sunset <= atFixed.sunset)
            assertTrue(atTuas.endOfEating <= atFixed.endOfEating)
        }
    }

    @Test
    fun `the ramadan rule adds its seconds to maghrib on ramadan dates`() {
        val date = LocalDate(2026, 7, 15)
        val outside = compute(muis, singapore, date, singaporeZone, noRamadan)
        val inside = compute(muis, singapore, date, singaporeZone, alwaysRamadan)
        assertEquals(outside.maghrib + 1.minutes, inside.maghrib)
        assertEquals(outside.isha, inside.isha)
        assertEquals(outside.fajr, inside.fajr)
    }

    // Ruling R74: a Ramadan-only Isha floor for an angle Isha (Sudan's Fiqh Academy: Maghrib + 90).

    private val khartoumZone = TimeZone.of("Africa/Khartoum")
    private val khartoum = GeoPoint(15.5007, 32.5599)
    private val ishaFloorMethod = TimetableMethod(
        id = "test.isha-floor",
        fajrAngle = 18.3,
        isha = IshaRule.Angle(18.0),
        ramadan = RamadanRule(ishaFloorMinutesAfterMaghrib = 90),
    )

    @Test
    fun `an angle isha floor holds isha at maghrib plus its minutes only in ramadan`() {
        val date = LocalDate(2026, 3, 1)
        val floored = compute(ishaFloorMethod, khartoum, date, khartoumZone, alwaysRamadan)
        val bare = compute(ishaFloorMethod.copy(ramadan = null), khartoum, date, khartoumZone, alwaysRamadan)
        assertEquals(bare.maghrib, floored.maghrib, "the floor must not move maghrib itself")
        assertEquals(maxOf(bare.isha, bare.maghrib + 90.minutes), floored.isha)
        // On this date and point the 90-min floor is the later of the two.
        assertEquals(bare.maghrib + 90.minutes, floored.isha)
        assertTrue(floored.isha > bare.isha)

        // Outside Ramadan the same method gives the bare angle, unchanged.
        val outside = compute(ishaFloorMethod, khartoum, date, khartoumZone, noRamadan)
        assertEquals(bare.isha, outside.isha)
        assertEquals(bare.maghrib, outside.maghrib)
    }

    @Test
    fun `the isha floor never moves isha earlier than the angle itself`() {
        // A floor of one minute after maghrib, which the 18 degree dusk already clears every day.
        val tinyFloor = ishaFloorMethod.copy(ramadan = RamadanRule(ishaFloorMinutesAfterMaghrib = 1))
        val date = LocalDate(2026, 3, 1)
        val floored = compute(tinyFloor, khartoum, date, khartoumZone, alwaysRamadan)
        val bare = compute(tinyFloor.copy(ramadan = null), khartoum, date, khartoumZone, alwaysRamadan)
        assertEquals(bare.isha, floored.isha, "the angle already clears a floor this small")
    }

    @Test
    fun `a ramadan rule with no isha floor leaves an angle isha exactly as before`() {
        // The default (null): every existing Angle-Isha method that sets only maghribExtraSeconds
        // (MUIS) keeps its Isha untouched by the floor logic.
        val date = LocalDate(2026, 7, 15)
        val withMaghribRule = compute(muis, singapore, date, singaporeZone, alwaysRamadan)
        val withoutRamadanRule = compute(muis.copy(ramadan = null), singapore, date, singaporeZone, alwaysRamadan)
        assertEquals(withoutRamadanRule.isha, withMaghribRule.isha)
    }

    // Diyanet's algorithm (daily sun, its temkin minutes), which it prints rounded to the nearest
    // minute. The margins are checked against its own raw times, computed here, never against a
    // printed row (ruling R69): the gate holds those.

    private val istanbulZone = TimeZone.of("Europe/Istanbul")
    private val istanbulPoint = GeoPoint(41.012, 28.974)
    private val diyanet = TimetableMethod(
        id = "test.diyanet",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(17.0),
        sunModel = SunModel.DAILY_0H_UT,
        asrModel = AsrModel.DAILY_0H_UT,
        authorityMinutes = EventOffsets(sunrise = -7, dhuhr = 5, asr = 4, maghrib = 7),
        margins = EventOffsets(fajr = -20, sunrise = 20, dhuhr = -20, asr = -20, maghrib = -20, isha = -20),
    )

    /** Diyanet's raw Fajr, sunrise, Dhuhr, Asr, Maghrib and Isha on [date] at İstanbul, epoch seconds before rounding. */
    private fun diyanetRaw(date: LocalDate): List<Double> {
        val offset = istanbulZone.offsetAt(date.atTime(12, 0).toInstant(istanbulZone)).totalSeconds
        val clock = SunClock(istanbulPoint.lat, istanbulPoint.lon, date, offset, SunModel.DAILY_0H_UT)
        return listOf(
            clock.altitudeTime(-18.0, morning = true)!!,
            clock.altitudeTime(-0.8333, morning = true)!! - 7 * 60,
            clock.transit() + 5 * 60,
            clock.asr(1.0, AsrModel.DAILY_0H_UT)!! + 4 * 60,
            clock.altitudeTime(-0.8333, morning = false)!! + 7 * 60,
            clock.altitudeTime(-17.0, morning = false)!!,
        )
    }

    private fun PrayerDay.row() = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha).map { it.epochSeconds }

    /** [raw] rounded to the nearest minute, as Diyanet prints it. */
    private fun nearestMinute(raw: Double): Long = floor((raw + 30) / 60).toLong() * 60

    private val september = (1..30).map { LocalDate(2026, 9, it) }

    @Test
    fun `diyanet's margins of thirty seconds round its own raw times to the nearest minute`() {
        val nearest = diyanet.copy(
            margins = EventOffsets(fajr = -30, sunrise = 30, dhuhr = -30, asr = -30, maghrib = -30, isha = -30),
        )
        for (date in september) {
            val raw = diyanetRaw(date)
            // A raw time exactly on the half minute rounds up as a start and down as sunrise, the
            // safe side each way; there is none this month.
            assertTrue(raw.none { abs(it / 60 - floor(it / 60) - 0.5) < 1e-6 }, "$date")
            assertEquals(raw.map(::nearestMinute), compute(nearest, istanbulPoint, date, istanbulZone).row(), "$date")
        }
    }

    @Test
    fun `diyanet with the ten second safety is never before its nearest minute and at most one after`() {
        var later = 0
        for (date in september) {
            val printed = diyanetRaw(date).map(::nearestMinute)
            val shown = compute(diyanet, istanbulPoint, date, istanbulZone).row()
            for ((i, pair) in shown.zip(printed).withIndex()) {
                val (at, nearest) = pair
                // Index 1 is sunrise, an end: never after, at most a minute before.
                if (i == 1) assertTrue(at in nearest - 60..nearest, "$date sunrise") else assertTrue(at in nearest..nearest + 60, "$date #$i")
                if (at != nearest) later++
            }
        }
        // A raw time within ten seconds past the half minute lands a minute on the safe side.
        assertTrue(later > 0)
    }

    // Rules of single authorities.

    @Test
    fun `kosovo fajr begins twenty minutes after the dawn`() {
        val kosovo = plain("test.kosovo").copy(fajrAfterDawnMinutes = 20)
        val prishtina = GeoPoint(42.6629, 21.1655)
        val date = LocalDate(2026, 9, 26)
        val zone = TimeZone.of("Europe/Belgrade")
        val day = compute(kosovo, prishtina, date, zone)
        assertTrue(day.fajr - day.endOfEating >= 20.minutes, "fajr ${day.fajr} imsak ${day.endOfEating}")
        assertTrue(day.fajr - day.endOfEating <= 21.minutes, "fajr ${day.fajr} imsak ${day.endOfEating}")
        val dawn = compute(plain("test.dawn"), prishtina, date, zone)
        assertEquals(dawn.endOfEating, day.endOfEating)
    }

    @Test
    fun `a fixed dhuhr is local noon when the transit is earlier and the transit when later`() {
        val fixed = plain("test.fixed").copy(fixedDhuhrLocalMinutes = 720)
        val free = plain("test.free")

        val kazan = GeoPoint(55.79, 49.12)
        val moscow = TimeZone.of("Europe/Moscow")
        val autumn = LocalDate(2026, 9, 26)
        assertTrue(compute(free, kazan, autumn, moscow).dhuhr < local(autumn, "12:00", moscow))
        assertEquals(local(autumn, "12:00", moscow), compute(fixed, kazan, autumn, moscow).dhuhr)

        val date = LocalDate(2026, 9, 20)
        val transit = compute(free, makkah, date, riyadhZone).dhuhr
        assertTrue(transit > local(date, "12:00", riyadhZone))
        assertEquals(transit, compute(fixed, makkah, date, riyadhZone).dhuhr)
    }

    @Test
    fun `a fixed dhuhr stays on its minute whatever the dhuhr margin`() {
        val fixed = plain("test.fixed").copy(fixedDhuhrLocalMinutes = 720, margins = EventOffsets(dhuhr = 45))
        val moscow = TimeZone.of("Europe/Moscow")
        val autumn = LocalDate(2026, 9, 26)
        assertEquals("12:00", compute(fixed, GeoPoint(55.79, 49.12), autumn, moscow).dhuhr.clock(moscow))
    }

    @Test
    fun `jamiatul ulama suhoor ends five minutes before fajr`() {
        val jamiat = TimetableMethod(
            id = "test.jamiat",
            fajrAngle = 18.0,
            isha = IshaRule.Angle(15.0),
            sunModel = SunModel.DAILY_0H_UT,
            endOfEating = EndOfEating.MinutesBeforeFajr(5),
        )
        val johannesburg = GeoPoint(-26.2041, 28.0473)
        val date = LocalDate(2026, 9, 26)
        val zone = TimeZone.of("Africa/Johannesburg")
        val day = compute(jamiat, johannesburg, date, zone)
        // Ruling R27: five minutes before the authority's dawn as an end (rounded down, no start
        // margin), so never later than its printed Fajr less five.
        val rawDawn = SunClock(johannesburg.lat, johannesburg.lon, date, 2 * 3600, SunModel.DAILY_0H_UT)
            .altitudeTime(-18.0, morning = true)!!
        assertEquals(floor(rawDawn / 60).toLong() * 60 - 300, day.endOfEating.epochSeconds)
        val gap = day.fajr - day.endOfEating
        assertTrue(gap >= 5.minutes && gap <= 6.minutes, "fajr ${day.fajr} end of eating ${day.endOfEating}")
    }

    @Test
    fun `a phase year clock reads 29 february as both its 28 february and its 1 march`() {
        // Ruling R29. Jamiat's perpetual table prints 29 February as 1 March.
        val perpetual = TimetableMethod(
            id = "test.perpetual",
            fajrAngle = 18.0,
            isha = IshaRule.Angle(18.0),
            sunModel = SunModel.DAILY_0H_UT,
            phaseYear = 2026,
            asrModel = AsrModel.DAILY_0H_UT,
            authorityMinutes = EventOffsets(dhuhr = 5, maghrib = 3),
            endOfEating = EndOfEating.MinutesBeforeFajr(5),
        )
        val johannesburg = GeoPoint(-26.1667, 28.0333)
        val zone = TimeZone.of("Africa/Johannesburg")
        val feb28 = compute(perpetual, johannesburg, LocalDate(2028, 2, 28), zone)
        val feb29 = compute(perpetual, johannesburg, LocalDate(2028, 2, 29), zone)
        val mar1 = compute(perpetual, johannesburg, LocalDate(2028, 3, 1), zone)
        fun later(a: Instant, b: Instant) = maxOf(a + 1.days, b - 1.days)
        fun earlier(a: Instant, b: Instant) = minOf(a + 1.days, b - 1.days)
        assertEquals(later(feb28.fajr, mar1.fajr), feb29.fajr)
        assertEquals(earlier(feb28.sunrise, mar1.sunrise), feb29.sunrise)
        assertEquals(later(feb28.dhuhr, mar1.dhuhr), feb29.dhuhr)
        assertEquals(later(feb28.asr, mar1.asr), feb29.asr)
        assertEquals(later(feb28.maghrib, mar1.maghrib), feb29.maghrib)
        assertEquals(later(feb28.isha, mar1.isha), feb29.isha)
        assertEquals(earlier(feb28.endOfEating, mar1.endOfEating), feb29.endOfEating)
        // Without a phase year 29 February is its own day.
        val exact = perpetual.copy(phaseYear = null)
        assertTrue(compute(exact, johannesburg, LocalDate(2028, 2, 29), zone).fajr > compute(exact, johannesburg, LocalDate(2028, 2, 28), zone).fajr + 1.days - 2.minutes)
    }

    @Test
    fun `minutes before fajr count back from the authority dawn whatever our start margin`() {
        val jamiat = TimetableMethod(
            id = "test.jamiat",
            fajrAngle = 18.0,
            isha = IshaRule.Angle(15.0),
            authorityMinutes = EventOffsets(fajr = 1),
            endOfEating = EndOfEating.MinutesBeforeFajr(5),
        )
        val date = LocalDate(2026, 9, 20)
        val plainDay = compute(jamiat, makkah, date, riyadhZone)
        val marginDay = compute(jamiat.copy(margins = EventOffsets(fajr = 50)), makkah, date, riyadhZone)
        assertEquals(plainDay.endOfEating, marginDay.endOfEating, "a start margin never moves the end of eating")
        assertTrue(marginDay.fajr >= plainDay.fajr)
        val rawDawn = SunClock(makkah.lat, makkah.lon, date, 3 * 3600, SunModel.EXACT).altitudeTime(-18.0, morning = true)!!
        // The authority's Fajr is its dawn plus its minute; the end of eating is five minutes before
        // that, rounded down.
        assertEquals(floor((rawDawn + 60) / 60).toLong() * 60 - 300, marginDay.endOfEating.epochSeconds)
    }

    @Test
    fun `a dawn angle end of eating is floored at its own angle and never after fajr`() {
        val date = LocalDate(2026, 9, 27)
        val benghazi = GeoPoint(32.1167, 20.0667)
        val zone = TimeZone.of("Africa/Tripoli")
        val method = plain("test.libya").copy(endOfEating = EndOfEating.DawnAngle(19.5))
        val day = compute(method, benghazi, date, zone)
        val raw = SunClock(benghazi.lat, benghazi.lon, date, 2 * 3600, SunModel.EXACT)
            .altitudeTime(-19.5, morning = true)!!
        assertEquals(floor(raw / 60).toLong() * 60, day.endOfEating.epochSeconds)
        assertTrue(day.endOfEating < day.fajr)
    }

    @Test
    fun `a dawn angle with its own curve reads the slot's depression in place of its angle`() {
        val date = LocalDate(2028, 2, 29)
        val benghazi = GeoPoint(32.1167, 20.0667)
        val zone = TimeZone.of("Africa/Tripoli")
        val curve = DoubleArray(366) { if (it == curveSlot(date)) 21.0 else 12.0 }
        val curved = plain("test.curve.end").copy(endOfEating = EndOfEating.DawnAngle(19.5, curve))
        val fixed = plain("test.fixed.end").copy(endOfEating = EndOfEating.DawnAngle(21.0))
        assertEquals(compute(fixed, benghazi, date, zone).endOfEating, compute(curved, benghazi, date, zone).endOfEating)
        // Another day reads its own slot (12°), capped at the Fajr shown.
        val next = date.plus(1, DateTimeUnit.DAY)
        val shallow = plain("test.shallow.end").copy(endOfEating = EndOfEating.DawnAngle(12.0))
        assertEquals(compute(shallow, benghazi, next, zone).endOfEating, compute(curved, benghazi, next, zone).endOfEating)
        assertFailsWith<IllegalArgumentException> { EndOfEating.DawnAngle(18.0, DoubleArray(365)) }
    }

    // DawnOrMiddle (research-uk): the dawn at its angle, or the middle of the night where the sun does not reach it.

    private val birmingham = GeoPoint(52.4862, -1.8904)
    private val londonZone = TimeZone.of("Europe/London")

    /** [date]'s own sunrise or sunset at [point], raw, on the local-noon offset the engine uses. */
    private fun rawHorizon(point: GeoPoint, date: LocalDate, zone: TimeZone, morning: Boolean): Double {
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        return SunClock(point.lat, point.lon, date, offset, SunModel.EXACT).altitudeTime(-0.8333, morning)!!
    }

    @Test
    fun `a dawn or middle end on a night without its angle is the middle of the night rounded down`() {
        val date = LocalDate(2026, 6, 21)
        val method = plain("test.middle").copy(endOfEating = EndOfEating.DawnOrMiddle(18.6), endOfEatingMarginSeconds = -30)
        val offset = londonZone.offsetAt(date.atTime(12, 0).toInstant(londonZone)).totalSeconds
        // The sun does not get 18.6 deg low at Birmingham in late June.
        assertEquals(null, SunClock(birmingham.lat, birmingham.lon, date, offset, SunModel.EXACT).altitudeTime(-18.6, morning = true))
        val middle = (rawHorizon(birmingham, date.minus(1, DateTimeUnit.DAY), londonZone, morning = false) +
            rawHorizon(birmingham, date, londonZone, morning = true)) / 2
        val day = compute(method, birmingham, date, londonZone)
        assertEquals(floor((middle - 30) / 60).toLong() * 60, day.endOfEating.epochSeconds)
        assertTrue(day.endOfEating < day.fajr)
    }

    @Test
    fun `a dawn or middle end on a night with its angle is the real dawn like a dawn angle`() {
        val date = LocalDate(2026, 1, 15)
        val middle = plain("test.middle").copy(endOfEating = EndOfEating.DawnOrMiddle(18.6), endOfEatingMarginSeconds = -30)
        val angle = plain("test.angle").copy(endOfEating = EndOfEating.DawnAngle(18.6), endOfEatingMarginSeconds = -30)
        val offset = londonZone.offsetAt(date.atTime(12, 0).toInstant(londonZone)).totalSeconds
        val raw = SunClock(birmingham.lat, birmingham.lon, date, offset, SunModel.EXACT).altitudeTime(-18.6, morning = true)!!
        val day = compute(middle, birmingham, date, londonZone)
        assertEquals(floor((raw - 30) / 60).toLong() * 60, day.endOfEating.epochSeconds)
        assertEquals(compute(angle, birmingham, date, londonZone).endOfEating, day.endOfEating)
    }

    @Test
    fun `a dawn or middle end ignores the method's own high latitude rule`() {
        val date = LocalDate(2026, 6, 21)
        val end = EndOfEating.DawnOrMiddle(18.6)
        val standard = plain("test.standard").copy(endOfEating = end)
        val seventh = plain("test.seventh").copy(endOfEating = end, highLatitude = HighLatRule.NightFraction(1.0 / 7, 1.0 / 7))
        val a = compute(standard, birmingham, date, londonZone)
        val b = compute(seventh, birmingham, date, londonZone)
        // The Fajr each method estimates differs; the end of eating is the middle of the night for both.
        assertTrue(a.fajr != b.fajr, "the two rules should give different Fajr estimates")
        assertEquals(a.endOfEating, b.endOfEating)
    }

    @Test
    fun `a dawn or middle end keeps whole minutes in order through a year at high latitude`() {
        val method = plain("test.middle").copy(endOfEating = EndOfEating.DawnOrMiddle(18.6), endOfEatingMarginSeconds = -30)
        for ((point, zone) in listOf(GeoPoint(60.1553, -1.1450) to londonZone, GeoPoint(69.6492, 18.9553) to TimeZone.of("Europe/Oslo"))) {
            var date = LocalDate(2026, 1, 1)
            var before: PrayerDay? = null
            while (date.year == 2026) {
                val day = compute(method, point, date, zone)
                val label = "$point $date"
                assertTrue(day.endOfEating <= day.fajr, label)
                assertEquals(0L, day.endOfEating.epochSeconds % 60, label)
                before?.let { assertTrue(day.endOfEating - it.endOfEating <= 1.days + DayComputer.DAILY_LIMIT, label) }
                before = day
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    @Test
    fun `imsak is an end its minutes before the authority dawn rounded down`() {
        val method = plain("test.imsak").copy(
            authorityMinutes = EventOffsets(fajr = 2),
            margins = EventOffsets(fajr = 148),
            endOfEatingMarginSeconds = -30,
            imsakMinutesBeforeFajr = 10,
        )
        val date = LocalDate(2026, 9, 20)
        val day = compute(method, makkah, date, riyadhZone)
        val rawDawn = SunClock(makkah.lat, makkah.lon, date, 3 * 3600, SunModel.EXACT).altitudeTime(-18.0, morning = true)!!
        assertEquals(floor((rawDawn + 120 - 30) / 60).toLong() * 60 - 600, day.imsak?.epochSeconds)
        assertEquals(day.endOfEating - 10.minutes, day.imsak)
        assertTrue(day.imsak!! < day.fajr - 10.minutes, "the start margin must not move imsak later")
        assertEquals(null, compute(plain("test.none"), makkah, date, riyadhZone).imsak)
    }

    @Test
    fun `isha after maghrib counts from the authority maghrib without our margin`() {
        val method = TimetableMethod(
            id = "test.after",
            fajrAngle = 18.0,
            isha = IshaRule.AfterMaghrib(90),
            authorityMinutes = EventOffsets(maghrib = 7),
            margins = EventOffsets(maghrib = -20, isha = 0),
        )
        val date = LocalDate(2026, 9, 20)
        val day = compute(method, makkah, date, riyadhZone)
        val rawSunset = SunClock(makkah.lat, makkah.lon, date, 3 * 3600, SunModel.EXACT).altitudeTime(-0.8333, morning = false)!!
        assertEquals(ceil((rawSunset + 420 + 5400) / 60).toLong() * 60, day.isha.epochSeconds)
        assertEquals(ceil((rawSunset + 420 - 20) / 60).toLong() * 60, day.maghrib.epochSeconds)
    }

    @Test
    fun `an end of eating after fajr is held at fajr`() {
        val method = plain("test.clamp").copy(endOfEating = EndOfEating.DawnAngle(15.0))
        val day = compute(method, makkah, LocalDate(2026, 9, 20), riyadhZone)
        assertEquals(day.fajr, day.endOfEating)
    }

    @Test
    fun `a day when the clocks change keeps whole minutes in order on its own date`() {
        val london = TimeZone.of("Europe/London")
        val point = GeoPoint(51.5074, -0.1278)
        val date = LocalDate(2026, 3, 29)
        val day = compute(plain("test.dst"), point, date, london)
        day.everyInstant().forEach {
            assertEquals(0L, it.epochSeconds % 60, "$it is not a whole minute")
            assertEquals(date, it.toLocalDateTime(london).date, "$it is not on $date")
        }
        val order = listOf(day.endOfEating, day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha)
        assertEquals(order.sorted(), order)
        assertTrue(day.fajr < day.sunrise && day.sunset <= day.maghrib && day.maghrib < day.isha)
        assertTrue(day.dhuhr.clock(london) in "13:00".."13:10", "Dhuhr ${day.dhuhr.clock(london)} is not on summer time")
        val before = compute(plain("test.dst"), point, date.minus(1, DateTimeUnit.DAY), london)
        val clockJump = day.sunrise.toLocalDateTime(london).time.toSecondOfDay() -
            before.sunrise.toLocalDateTime(london).time.toSecondOfDay()
        assertTrue(clockJump in 55 * 60..60 * 60, "sunrise moved $clockJump s on the local clock")
    }

    @Test
    fun `a twilight dip makes fajr earlier and isha later`() {
        val base = TimetableMethod(id = "test.dip", fajrAngle = 18.0, isha = IshaRule.Angle(18.0))
        val dipped = base.copy(twilightDipDeg = 1.0)
        val kualaLumpur = GeoPoint(3.139, 101.687)
        val zone = TimeZone.of("Asia/Kuala_Lumpur")
        val date = LocalDate(2026, 2, 25)
        val a = compute(base, kualaLumpur, date, zone)
        val b = compute(dipped, kualaLumpur, date, zone)
        assertTrue(b.fajr < a.fajr, "dipped fajr ${b.fajr} base ${a.fajr}")
        assertTrue(b.isha > a.isha, "dipped isha ${b.isha} base ${a.isha}")
        assertEquals(a.sunrise, b.sunrise)
        assertEquals(a.maghrib, b.maghrib)
    }

    @Test
    fun `start points make starts the latest while sunrise and the end of eating stay at the point`() {
        val kualaLumpur = GeoPoint(3.139, 101.687)
        val south = GeoPoint(2.62, 101.69)
        val northWest = GeoPoint(3.73, 101.45)
        val zone = TimeZone.of("Asia/Kuala_Lumpur")
        val date = LocalDate(2026, 6, 1)
        val base = TimetableMethod(id = "test.jakim", fajrAngle = 18.0, isha = IshaRule.Angle(18.0))
        val zoned = base.copy(startPoints = listOf(south, northWest))
        val each = listOf(kualaLumpur, south, northWest).map { compute(base, it, date, zone) }
        val shown = compute(zoned, kualaLumpur, date, zone)
        shown.starts().forEachIndexed { i, start -> assertEquals(each.maxOf { it.starts()[i] }, start) }
        assertEquals(each[0].sunrise, shown.sunrise)
        assertEquals(each[0].sunset, shown.sunset)
        assertEquals(each[0].endOfEating, shown.endOfEating)
        assertTrue(shown.maghrib > each[0].maghrib, "the north-west point sets later in June")
    }

    @Test
    fun `asr other is the other school`() {
        val date = LocalDate(2026, 9, 20)
        val standard = compute(plain("test.asr"), makkah, date, riyadhZone, school = AsrSchool.STANDARD)
        val hanafi = compute(plain("test.asr"), makkah, date, riyadhZone, school = AsrSchool.HANAFI)
        assertTrue(standard.asr < standard.asrOther)
        assertEquals(standard.asr, hanafi.asrOther)
        assertEquals(standard.asrOther, hanafi.asr)
    }

    // The day-of-year curves and monthly offsets (London Unified, Tunisia).

    @Test
    fun `day of year angle curves replace the fixed angles on their own day`() {
        val date = LocalDate(2026, 9, 20)
        val index = curveSlot(date)
        val curved = plain("test.curve").copy(
            fajrAngleByDayOfYear = DoubleArray(366) { if (it == index) 15.0 else 30.0 },
            ishaAngleByDayOfYear = DoubleArray(366) { if (it == index) 14.0 else 30.0 },
        )
        val fixed = plain("test.curve").copy(fajrAngle = 15.0, isha = IshaRule.Angle(14.0))
        val a = compute(curved, makkah, date, riyadhZone)
        val b = compute(fixed, makkah, date, riyadhZone)
        assertEquals(b.fajr, a.fajr)
        assertEquals(b.isha, a.isha)
        assertEquals(b.endOfEating, a.endOfEating)
        assertFailsWith<IllegalArgumentException> { plain("test.short").copy(fajrAngleByDayOfYear = DoubleArray(365)) }
        assertFailsWith<IllegalArgumentException> {
            TimetableMethod("test.after", 18.0, IshaRule.AfterMaghrib(90), ishaAngleByDayOfYear = DoubleArray(366))
        }
    }

    @Test
    fun `monthly offsets move a prayer in their own month only`() {
        val septemberAsr = IntArray(12) { if (it == 8) 120 else 0 }
        val monthly = plain("test.monthly").copy(monthlyOffsets = mapOf(Prayer.ASR to septemberAsr))
        val september = LocalDate(2026, 9, 20)
        val a = compute(plain("test.monthly"), makkah, september, riyadhZone)
        val b = compute(monthly, makkah, september, riyadhZone)
        assertEquals(a.asr + 2.minutes, b.asr)
        assertEquals(a.asrOther + 2.minutes, b.asrOther)
        // Dhuhr ends at the (Standard) Asr, so its end moves with it.
        assertEquals(a.copy(asr = b.asr, asrOther = b.asrOther, ends = a.ends + (Prayer.DHUHR to b.asr)), b)
        val october = LocalDate(2026, 10, 20)
        assertEquals(
            compute(plain("test.monthly"), makkah, october, riyadhZone),
            compute(monthly, makkah, october, riyadhZone),
        )
    }

    @Test
    fun `harmonics follow the day of year phase`() {
        assertEquals(0.5, Harmonics(0.5, emptyList()).at(100, 365))
        // day 92 of a leap year is t = pi / 2
        assertTrue(abs(Harmonics(0.0, listOf(0.0 to 1.0)).at(92, 366) - 1.0) < 1e-12)
        assertTrue(abs(Harmonics(0.0, listOf(0.0 to 0.0, 1.0 to 0.0)).at(92, 366) + 1.0) < 1e-12)
        assertTrue(abs(uqBias.at(91, 365) - 0.3433) < 1e-4)
        assertTrue(abs(uqBias.at(288, 365) + 0.2748) < 1e-4)
    }

    // Ruling R90 (b): Fajr stays before the sunrise shown; the sunrise precaution gives way first.

    private val rovaniemi = GeoPoint(66.5039, 25.7294)
    private val helsinki = TimeZone.of("Europe/Helsinki")

    /** A method whose 18° dawn is missing at Rovaniemi in May: Fajr a twentieth of the night before sunrise. */
    private val shallow = TimetableMethod(
        id = "test.shallow", fajrAngle = 18.0, isha = IshaRule.Angle(17.0),
        highLatitude = HighLatRule.NightFraction(fajrFraction = 0.05, ishaFraction = 0.25),
    )

    private fun sunSunrise(point: GeoPoint, date: LocalDate, zone: TimeZone): Double {
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        return SunClock(point.lat, point.lon, date, offset, SunModel.EXACT).altitudeTime(-0.8333, morning = true)!!
    }

    private fun endOf(epochSeconds: Double) = Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    @Test
    fun `a sunrise precaution before the fajr shown gives way to the minute after fajr`() {
        val date = LocalDate(2026, 5, 1)
        val sun = endOf(sunSunrise(rovaniemi, date, helsinki))
        val plain = compute(shallow, rovaniemi, date, helsinki)
        assertTrue(plain.fajr < plain.sunrise && plain.sunrise == sun, "the plain day: Fajr before the sun's sunrise")

        // The authority prints its sunrise 25 minutes before the sun's: before its own Fajr estimate.
        val precaution = shallow.copy(id = "test.precaution", authorityMinutes = EventOffsets(sunrise = -25))
        val day = compute(precaution, rovaniemi, date, helsinki)
        assertEquals(plain.fajr, day.fajr, "Fajr keeps its promise")
        assertEquals(day.fajr + 1.minutes, day.sunrise, "the sunrise moves later to the minute after Fajr")
        assertTrue(day.sunrise <= sun, "never past the sun's own sunrise")
        assertEquals(day.sunrise, day.ends[Prayer.FAJR])
        assertTrue(day.endOfEating <= day.fajr && day.endOfEating <= plain.endOfEating)
        assertFalse(Prayer.FAJR in day.notFollowed)
        assertTrue(Invariants.holds(day) && !day.repaired)
        day.everyInstant().forEach { assertEquals(0L, it.epochSeconds % 60) }
    }

    @Test
    fun `only where the sun has risen by the fajr shown does fajr move earlier and it is declared`() {
        val date = LocalDate(2026, 5, 1)
        val sun = endOf(sunSunrise(rovaniemi, date, helsinki))
        // The authority's Fajr is printed half an hour after its estimate: after the sun's sunrise.
        val late = shallow.copy(id = "test.late", authorityMinutes = EventOffsets(sunrise = -25, fajr = 30))
        val day = compute(late, rovaniemi, date, helsinki)
        assertEquals(sun, day.sunrise, "the precaution gives way as far as the sun, never past it")
        assertEquals(sun - 1.minutes, day.fajr, "Fajr the minute before the sun's sunrise")
        assertEquals(setOf(Prayer.FAJR), day.notFollowed, "declared: no day in order shows the authority's Fajr")
        assertEquals(day.sunrise, day.ends[Prayer.FAJR])
        assertTrue(day.endOfEating <= day.fajr)
        assertTrue(Invariants.holds(day) && !day.repaired)
        day.everyInstant().forEach { assertEquals(0L, it.epochSeconds % 60) }
    }
}
