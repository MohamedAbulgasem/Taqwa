package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Europe
import world.taqwa.app.prayer.engine.registry.authorities.IrnArctic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * IRN's Tromsø rule (ruling R82) and the Tromsø unit that follows it. Structural only (ruling R69):
 * every expectation is the engine's own sun at the unit's point or at Makkah's latitude on its
 * meridian, never a printed time.
 */
class IrnArcticTest {
    private val oslo = TimeZone.of("Europe/Oslo")
    private val tromso = GeoPoint(69.6489, 18.95508)
    private val rule = IrnArctic()
    private val makkahLatitude = 21.4225

    private fun clock(lat: Double, date: LocalDate) =
        SunClock(lat, tromso.lon, date, oslo.offsetAt(date.atTime(12, 0).toInstant(oslo)).totalSeconds, SunModel.EXACT)

    private fun near(expected: Double, actual: Double, what: String, seconds: Double = 0.5) =
        assertTrue(abs(expected - actual) <= seconds, "$what: $actual, expected $expected")

    /** Seconds after local midnight on the wall clock. */
    private fun wall(epochSeconds: Double): Double {
        val t = Instant.fromEpochSeconds(floor(epochSeconds).toLong()).toLocalDateTime(oslo)
        return t.hour * 3600.0 + t.minute * 60 + t.second + (epochSeconds - floor(epochSeconds))
    }

    private fun LocalDate.next(days: Int = 1) = plus(days, DateTimeUnit.DAY)

    @Test
    fun `the makkah hold is the day at makkah's latitude on the place's meridian`() {
        for (date in listOf(LocalDate(2026, 12, 20), LocalDate(2027, 1, 5), LocalDate(2026, 6, 15), LocalDate(2026, 7, 30))) {
            val t = rule.on(date, tromso, oslo)
            val makkah = clock(makkahLatitude, date)
            near(makkah.altitudeTime(-18.0, morning = true)!!, t.fajr!!.epochSeconds, "$date fajr")
            near(makkah.altitudeTime(-18.0, morning = false)!!, t.isha!!.epochSeconds, "$date isha")
            near(makkah.altitudeTime(-0.8333, morning = false)!! + 300, t.maghrib!!.epochSeconds, "$date maghrib")
            assertTrue(t.fajr!!.estimated && t.isha!!.estimated && t.maghrib!!.estimated, "$date: Makkah time is IRN's estimate")
            near(t.fajr!!.epochSeconds, t.endOfEating!!, "$date: IRN prints no imsak, so the fast begins at its Fajr")
        }
    }

    @Test
    fun `the ordinary rule is sixteen and fifteen degrees within nine hours of noon and maghrib five after irn's sunset`() {
        for (date in listOf(LocalDate(2026, 3, 1), LocalDate(2026, 3, 30), LocalDate(2026, 10, 1))) {
            val t = rule.on(date, tromso, oslo)
            val here = clock(tromso.lat, date)
            val noon = here.transit()
            val dawn = here.altitudeTime(-16.0, morning = true)
            val dusk = here.altitudeTime(-15.0, morning = false)
            near(maxOf(dawn ?: Double.MIN_VALUE, noon - 9 * 3600), t.fajr!!.epochSeconds, "$date fajr")
            near(minOf(dusk ?: Double.MAX_VALUE, noon + 9 * 3600), t.isha!!.epochSeconds, "$date isha")
            val horizon = if (date.month.ordinal < 6) -0.70 else -1.00 // IRN's sunset: higher while the days lengthen
            near(here.altitudeTime(horizon, morning = false)!! + 300, t.maghrib!!.epochSeconds, "$date maghrib")
            assertFalse(t.maghrib!!.estimated, "$date: sunset + 5 is the sun's")
            assertEquals(dawn == null || dawn < noon - 9 * 3600, t.fajr!!.estimated, "$date: the nine hours are a rule")
        }
    }

    @Test
    fun `dhuhr is noon plus five and plus ten from 21 march to 20 september`() {
        for ((date, minutes) in listOf(
            LocalDate(2026, 3, 19) to 5, LocalDate(2026, 3, 21) to 10, LocalDate(2026, 9, 20) to 10, LocalDate(2026, 9, 22) to 5,
        )) {
            val t = rule.on(date, tromso, oslo)
            near(clock(tromso.lat, date).transit() + minutes * 60, t.dhuhr!!.epochSeconds, "$date dhuhr")
            assertFalse(t.dhuhr!!.estimated)
        }
    }

    @Test
    fun `asr is the later of the shadow and midway to maghrib and the hanafi never after maghrib less 65`() {
        for (date in listOf(LocalDate(2026, 1, 25), LocalDate(2026, 3, 15), LocalDate(2026, 6, 1), LocalDate(2026, 10, 10))) {
            val t = rule.on(date, tromso, oslo)
            val here = clock(tromso.lat, date)
            val maghrib = t.maghrib!!.epochSeconds
            val mid = (here.transit() + maghrib - 300) / 2
            val one = here.asr(1.0, AsrModel.EXACT_MOMENT)
            val two = here.asr(2.0, AsrModel.EXACT_MOMENT)
            near(maxOf(mid, one ?: mid), t.asrStandard!!.epochSeconds, "$date standard")
            near(minOf(maxOf(mid, two ?: mid), maghrib - 65 * 60), t.asrHanafi!!.epochSeconds, "$date hanafi")
        }
    }

    @Test
    fun `the blends are straight lines on the wall clock across a clock change`() {
        // 31 October is the first anchor of IRN's autumn blend and, in 2027, the day the clocks go back.
        for (year in listOf(2026, 2027)) {
            val from = LocalDate(year, 10, 31)
            val to = LocalDate(year, 11, 30)
            val a = rule.on(from, tromso, oslo)
            val b = rule.on(to, tromso, oslo)
            for (k in listOf(1, 7, 15, 29)) {
                val t = rule.on(from.next(k), tromso, oslo)
                for ((what, pick) in listOf<Pair<String, (ClockTimes) -> Double>>(
                    "fajr" to { it.fajr!!.epochSeconds }, "maghrib" to { it.maghrib!!.epochSeconds }, "isha" to { it.isha!!.epochSeconds },
                )) {
                    val line = wall(pick(a)) + (wall(pick(b)) - wall(pick(a))) * k / 30.0
                    near(line, wall(pick(t)), "$year +$k $what", seconds = 1.0)
                }
            }
        }
        // The spring Fajr blend, a week long.
        val from = LocalDate(2026, 4, 22)
        val a = rule.on(from, tromso, oslo).fajr!!.epochSeconds
        val b = rule.on(from.next(7), tromso, oslo).fajr!!.epochSeconds
        val mid = rule.on(from.next(3), tromso, oslo).fajr!!
        near(wall(a) + (wall(b) - wall(a)) * 3 / 7.0, wall(mid.epochSeconds), "spring fajr blend", seconds = 1.0)
        assertTrue(mid.estimated)
    }

    @Test
    fun `sunrise is irn's own where the sun rises noon in the polar night and makkah's under the midnight sun`() {
        val autumn = LocalDate(2026, 10, 1)
        val ordinary = rule.on(autumn, tromso, oslo)
        near(clock(tromso.lat, autumn).altitudeTime(-0.85, morning = true)!!, ordinary.sunrise!!.epochSeconds, "IRN's sunrise")
        assertTrue(ordinary.notFollowed.isEmpty())

        val night = LocalDate(2026, 12, 20)
        val polarNight = rule.on(night, tromso, oslo)
        near(clock(tromso.lat, night).transit(), polarNight.sunrise!!.epochSeconds, "noon in the polar night")
        assertTrue(polarNight.sunrise!!.estimated && polarNight.noSunset)
        assertTrue(polarNight.notFollowed.isEmpty(), "IRN prints noon, and noon is shown")

        val summer = LocalDate(2026, 6, 21)
        val midnightSun = rule.on(summer, tromso, oslo)
        near(clock(makkahLatitude, summer).altitudeTime(-0.8333, morning = true)!!, midnightSun.sunrise!!.epochSeconds, "Makkah's sunrise")
        assertTrue(midnightSun.fajr!!.epochSeconds < midnightSun.sunrise!!.epochSeconds)
        assertTrue(midnightSun.noSunset)
        assertEquals(setOf(Prayer.SUNRISE), midnightSun.notFollowed, "IRN prints the sun's lowest point")
        near(midnightSun.maghrib!!.epochSeconds - 300, midnightSun.sunset!!, "IRN's own sunset")
    }

    @Test
    fun `where the sun rises but irn's calculator finds no sunrise the sun's own stands and irn's is not followed`() {
        // Around the start of the midnight sun the sun still dips below the horizon but not below
        // IRN's lower one: IRN prints the sun's lowest point there.
        var found = 0
        var date = LocalDate(2026, 5, 10)
        while (date <= LocalDate(2026, 5, 25)) {
            val here = clock(tromso.lat, date)
            val real = here.altitudeTime(-0.8333, morning = true)
            if (real != null && here.altitudeTime(-1.05, morning = true) == null) {
                found++
                val t = rule.on(date, tromso, oslo)
                near(real, t.sunrise!!.epochSeconds, "$date")
                assertEquals(setOf(Prayer.SUNRISE), t.notFollowed, "$date")
            }
            date = date.next()
        }
        assertTrue(found > 0, "no such day in May 2026")
    }

    @Test
    fun `the widened rule takes each start the latest and each end the earliest of the dates a day either way`() {
        val widened = IrnArctic(windowShiftDays = listOf(-1, 0, 1))
        val singles = listOf(-1, 0, 1).map { IrnArctic(windowShiftDays = listOf(it)) }
        val dates = listOf(
            LocalDate(2026, 1, 10), LocalDate(2026, 2, 9), LocalDate(2026, 3, 21), LocalDate(2026, 4, 15), LocalDate(2026, 4, 22),
            LocalDate(2026, 4, 29), LocalDate(2026, 8, 14), LocalDate(2026, 8, 21), LocalDate(2026, 8, 29), LocalDate(2026, 9, 20),
            LocalDate(2026, 10, 31), LocalDate(2026, 11, 30),
        )
        for (date in dates) {
            val w = widened.on(date, tromso, oslo)
            val s = singles.map { it.on(date, tromso, oslo) }
            for ((what, pick) in listOf<Pair<String, (ClockTimes) -> Double>>(
                "fajr" to { it.fajr!!.epochSeconds }, "dhuhr" to { it.dhuhr!!.epochSeconds },
                "asr" to { it.asrStandard!!.epochSeconds }, "hanafi" to { it.asrHanafi!!.epochSeconds },
                "maghrib" to { it.maghrib!!.epochSeconds }, "isha" to { it.isha!!.epochSeconds },
            )) {
                near(s.maxOf(pick), pick(w), "$date $what")
            }
            near(s.minOf { it.endOfEating!! }, w.endOfEating!!, "$date end of eating")
        }
        // Somewhere a shifted date must actually move a start.
        assertTrue(dates.any { d -> singles.map { it.on(d, tromso, oslo).maghrib!!.epochSeconds }.distinct().size > 1 })
    }

    @Test
    fun `across the clock change on 31 october 2027 the widened unit is never before the plain rule and within its limits`() {
        // Review I2: taken a day early, the autumn blend's anchor is 30 October, still summer time in
        // 2027, so its line on the wall clock starts an hour later. The unit keeps it (never early if
        // IRN's dates do move a day earlier) and its late limits carry that November.
        val plain = unit.copy(method = unit.method!!.copy(clockRule = IrnArctic()))
        val tromsoUnit = Europe.irnUnits.unit("no.irn.tromso")
        val entry = Registry.byId("no.irn")!!
        val starts = listOf<Pair<TimedEvent, (PrayerDay) -> Instant>>(
            TimedEvent.FAJR to { it.fajr }, TimedEvent.DHUHR to { it.dhuhr }, TimedEvent.ASR to { it.asr },
            TimedEvent.ASR to { it.asrOther }, TimedEvent.MAGHRIB to { it.maghrib }, TimedEvent.ISHA to { it.isha },
        )

        fun lead(from: LocalDate, to: LocalDate): Map<TimedEvent, Long> {
            val most = mutableMapOf<TimedEvent, Long>()
            var date = from
            while (date <= to) {
                val widened = DayPipeline.unended(unit, date, oslo)
                val own = DayPipeline.unended(plain, date, oslo)
                for ((event, pick) in starts) {
                    val minutes = (pick(widened) - pick(own)).inWholeMinutes
                    assertTrue(minutes >= 0, "$date $event: before the rule on its own dates")
                    most[event] = maxOf(most[event] ?: 0, minutes)
                }
                assertTrue(widened.endOfEating <= own.endOfEating && widened.sunrise <= own.sunrise, "$date")
                date = date.next()
            }
            return most
        }

        val clockChange = lead(LocalDate(2027, 10, 24), LocalDate(2027, 12, 5))
        for ((event, minutes) in clockChange) {
            val limit = lateLimitFor(event, tromsoUnit, entry)!!.minutes
            assertTrue(minutes < limit, "$event: $minutes min after the rule on its own dates, limit $limit")
        }
        assertTrue(clockChange.getValue(TimedEvent.FAJR) > 45 && clockChange.getValue(TimedEvent.MAGHRIB) > 45, "$clockChange")
        // In 2026 the clocks went back on 25 October: the widening costs minutes only.
        val ordinary = lead(LocalDate(2026, 10, 24), LocalDate(2026, 12, 5))
        assertTrue(ordinary.values.all { it <= 10 }, "$ordinary")
    }

    // --- The Tromsø unit ------------------------------------------------------------------------


    private fun startOf(epochSeconds: Double) = Instant.fromEpochSeconds(ceil(epochSeconds / 60.0).toLong() * 60)
    private fun endOf(epochSeconds: Double) = Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    @Test
    fun `the tromso unit follows the widened rule and its edge does not`() {
        assertEquals("Tromsø", unit.unitName)
        assertEquals(IrnArctic(windowShiftDays = listOf(-1, 0, 1)), unit.method!!.clockRule)
        assertTrue(unit.measured)
        // Alta and Bodø are beyond the reach of every IRN calendar: the edge, with no rule.
        for ((lat, lon) in listOf(69.9689 to 23.2716, 67.2804 to 14.4049)) {
            val edge = Registry.resolveEntry(Registry.byId("no.irn")!!, Place(lat, lon, "Europe/Oslo", "NO"))
            assertNull(edge.unitName, "$lat")
            assertNull(edge.method!!.clockRule, "$lat")
        }
        assertNull(Registry.resolveEntry(Registry.byId("no.irn")!!, Place(59.91273, 10.74609, "Europe/Oslo", "NO")).method!!.clockRule)
    }

    @Test
    fun `automatic follows irn within the tromso unit's reach and norway's cautious times beyond it`() {
        assertEquals("no.irn", Registry.resolve(tromsoPlace).entry.id)
        assertEquals("no.irn", Registry.resolve(Place(69.6492, 18.9553, "Europe/Oslo", "NO")).entry.id) // the city's own point
        val reach = Europe.irnUnits.unit("no.irn.tromso").radiusKm
        // Due south, just inside and just outside the reach (a degree of latitude is 111.2 km).
        val inside = Place(tromso.lat - (reach - 1) / 111.2, tromso.lon, "Europe/Oslo", "NO")
        val outside = Place(tromso.lat - (reach + 1) / 111.2, tromso.lon, "Europe/Oslo", "NO")
        assertEquals("no.irn", Registry.resolve(inside).entry.id)
        assertEquals("no.cautious", Registry.resolve(outside).entry.id)
        assertEquals("no.cautious", Registry.resolve(Place(69.9689, 23.2716, "Europe/Oslo", "NO")).entry.id) // Alta
    }

    @Test
    fun `no day at the unit is repaired or leaves isha without its end from 2026 to 2035`() {
        val broken = decade.filter { it.repaired || Prayer.ISHA !in it.ends }.map { it.date }
        assertTrue(broken.isEmpty(), "${broken.size} days: ${broken.take(10)}")
    }

    @Test
    fun `across the unit's reach no day is repaired and isha loses its end only on a night before the midnight sun`() {
        // Review M2: R72's kind of night. In the week before the midnight sun the real sunset is past
        // midnight, Isha follows the Maghrib after it, and at some points of the reach, on some night
        // of the decade, that Isha is not before the next day's end of eating (the dawn before the
        // real sunrise): the day shows no Isha end, and the planner skips its alert (ruling R83).
        // Nothing is early and no end late. Walked 2026-2035 they are 16 May 2026 at 27 km north and
        // 18 May 2029 at the reach's south edge (16 May 2033 at its north-east and north-west, not
        // walked here); the unit's own point has none ([decade]).
        for ((where, days) in reachDecade) {
            val repaired = days.filter { it.repaired }.map { it.date }
            assertTrue(repaired.isEmpty(), "$where: ${repaired.size} days repaired: ${repaired.take(5)}")
            val empty = days.filter { Prayer.ISHA !in it.ends }.map { it.date }
            assertTrue(empty.size <= 1 && empty.all { it.month == Month.MAY && it.day in 14..20 }, "$where: $empty")
        }
    }

    @Test
    fun `fajr is never at or after a real sunrise and maghrib never before a real sunset`() {
        for (day in decade) {
            val here = clock(tromso.lat, day.date)
            here.altitudeTime(-0.8333, morning = true)?.let { assertTrue(day.fajr < endOf(it), "${day.date} fajr ${day.fajr}") }
            here.altitudeTime(-0.8333, morning = false)?.let { assertTrue(day.maghrib >= startOf(it), "${day.date} maghrib ${day.maghrib}") }
            assertTrue(day.fajr < day.sunrise, "${day.date}")
        }
    }

    @Test
    fun `fajr is declared not followed only where irn's is not before the sunrise shown and sunrise only where irn prints none`() {
        var fajrDays = 0
        var sunriseDays = 0
        for (day in decade.filter { it.date.year == 2026 }) {
            val t = unit.method!!.clockRule!!.on(day.date, tromso, oslo)
            val irnFajr = startOf(t.fajr!!.epochSeconds + unit.method!!.margins.fajr)
            assertEquals(irnFajr >= day.sunrise, Prayer.FAJR in day.notFollowed, "${day.date}")
            if (Prayer.FAJR in day.notFollowed) fajrDays++ else assertEquals(irnFajr, day.fajr, "${day.date}")
            if (Prayer.SUNRISE in day.notFollowed) sunriseDays++
        }
        // About fifty summer mornings, a couple more for the widened dates, and the midnight sun's.
        assertTrue(fajrDays in 45..60, "$fajrDays Fajr days")
        assertTrue(sunriseDays in 60..75, "$sunriseDays sunrise days")
    }

    @Test
    fun `in the polar night sunrise is at noon and the whole day is irn's makkah time`() {
        val date = LocalDate(2026, 12, 21)
        val day = decade.first { it.date == date }
        val t = unit.method!!.clockRule!!.on(date, tromso, oslo)
        assertFalse(day.polar, "IRN's rule gives the whole day: nothing follows the nearest latitude")
        assertEquals(endOf(clock(tromso.lat, date).transit() + unit.method!!.margins.sunrise), day.sunrise)
        assertEquals(startOf(t.maghrib!!.epochSeconds + unit.method!!.margins.maghrib), day.maghrib)
        assertEquals(startOf(t.isha!!.epochSeconds + unit.method!!.margins.isha), day.isha)
        assertTrue(day.notFollowed.isEmpty())
        assertTrue(Prayer.MAGHRIB in day.setByRule)
    }

    @Test
    fun `under the midnight sun makkah's sunrise comes after irn's fajr`() {
        val day = decade.first { it.date == LocalDate(2026, 6, 21) }
        assertFalse(day.polar)
        assertFalse(Prayer.FAJR in day.notFollowed)
        assertEquals(setOf(Prayer.SUNRISE), day.notFollowed)
        assertTrue(day.fajr < day.sunrise && day.sunrise < day.dhuhr)
        assertNotNull(day.ends[Prayer.ISHA])
    }

    private companion object {
        val tromsoPlace = Place(69.6489, 18.95508, "Europe/Oslo", "NO")
        val unit = Registry.resolveEntry(Registry.byId("no.irn")!!, tromsoPlace)

        /** Every day of 2026 to 2035 at [place] under IRN, with the next day's end; walked once. */
        fun walk(place: Place): List<PrayerDay> {
            val zone = TimeZone.of("Europe/Oslo")
            val resolution = Registry.resolveEntry(Registry.byId("no.irn")!!, place)
            val unended = generateSequence(LocalDate(2026, 1, 1)) { it.plus(1, DateTimeUnit.DAY) }
                .takeWhile { it <= LocalDate(2036, 1, 1) }
                .map { DayPipeline.unended(resolution, it, zone) }.toList()
            return unended.zipWithNext { today, next -> Ends.withNextDay(today, next) }
        }

        /**
         * Points across the unit's reach, each walked as [decade] is: just inside it due north, south,
         * east and west, and 27 km north (review M2).
         */
        val reachDecade: Map<String, List<PrayerDay>> by lazy {
            val reach = Europe.irnUnits.unit("no.irn.tromso").radiusKm - 0.5
            val kmPerLon = 111.2 * cos(tromsoPlace.lat * PI / 180)
            val kmNorthEast = listOf(
                "north" to (reach to 0.0), "south" to (-reach to 0.0), "east" to (0.0 to reach), "west" to (0.0 to -reach),
                "27 km north" to (27.0 to 0.0),
            )
            kmNorthEast.associate { (name, km) ->
                val place = Place(tromsoPlace.lat + km.first / 111.2, tromsoPlace.lon + km.second / kmPerLon, "Europe/Oslo", "NO")
                assertEquals("Tromsø", Registry.resolveEntry(Registry.byId("no.irn")!!, place).unitName, name)
                assertEquals("no.irn", Registry.resolve(place).entry.id, name)
                name to walk(place)
            }
        }

        /** Every day of 2026 to 2035 at the unit's point, each with the next day's end (as the app has it); walked once. */
        val decade: List<PrayerDay> by lazy { walk(tromsoPlace) }
    }
}
