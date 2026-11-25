package world.taqwa.app.prayer.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.AsrSchool.HANAFI
import world.taqwa.app.prayer.engine.method.AsrSchool.STANDARD
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Ruling R180: the real sun at the user's own point is the floor of the two starts it gives outright. No day leaves
 * the pipeline with its Dhuhr before the real transit there, or its Maghrib before the real sunset, whatever table,
 * margin, fixed point or member gave it ([DayPipeline.onRealSun]). Ruling R265 carries it to Asr (each school, never
 * before the real shadow time) and to the ends: the sunrise shown and Fajr's end never after the real sunrise, nor
 * Dhuhr's end after the real shadow-1 Asr.
 */
class RealSunFloorTest {

    private val cairo = Place(30.06263, 31.24967, "Africa/Cairo", "EG")
    private val longyearbyen = Place(78.22334, 15.64689, "Arctic/Longyearbyen", "SJ")
    private val trondheim = Place(63.43049, 10.39506, "Europe/Oslo", "NO")

    private fun Place.point() = GeoPoint(lat, lon)

    private fun Place.zone() = TimeZone.of(zoneId)

    private fun Instant.isWholeMinute() = epochSeconds % 60 == 0L && nanosecondsOfSecond == 0

    /** The floors as the rule words them: the first whole minute after the event. */
    private fun firstMinuteAfter(event: Instant): Instant = Instant.fromEpochSeconds(event.epochSeconds.floorDiv(60L) * 60 + 60)

    private fun shown(place: Place, date: LocalDate, school: AsrSchool = STANDARD): PrayerDay =
        DayPipeline.unended(Registry.resolve(place), date, place.zone(), school)

    private fun assertOnOrAfterTheSun(day: PrayerDay, point: GeoPoint, date: LocalDate, zone: TimeZone, school: AsrSchool, label: String) {
        val transit = DayPipeline.realTransit(point, date, zone)
        assertTrue(day.dhuhr > transit, "$label: Dhuhr ${day.dhuhr} is not after the real transit $transit")
        day.earliestStart[Prayer.DHUHR]?.let { assertTrue(it > transit, "$label: a member's Dhuhr $it is said to begin before the real transit $transit") }
        // Ruling R265: Asr in each school after its real shadow time, and the ends the sun gives not after it (Asr and
        // Dhuhr's end where no rule sets the day's Asr: a rule there stands in for the sun).
        val asrBySun = Prayer.ASR !in day.setByRule && !day.polar
        if (asrBySun) DayPipeline.realAsr(point, date, zone, school)?.let { real ->
            assertTrue(day.asr > real, "$label: Asr ${day.asr} is not after the real shadow time $real")
            day.earliestStart[Prayer.ASR]?.let { assertTrue(it > real, "$label: a member's Asr $it is said to begin before the real shadow time $real") }
        }
        if (asrBySun) DayPipeline.realAsr(point, date, zone, school.other)?.let { real ->
            assertTrue(day.asrOther > real, "$label: the other school's Asr ${day.asrOther} is not after its real shadow time $real")
        }
        DayPipeline.realAsr(point, date, zone, STANDARD)?.let { real ->
            day.ends[Prayer.DHUHR]?.let { assertTrue(it <= real, "$label: Dhuhr's end $it is after the real shadow-1 Asr $real") }
        }
        // The sunrise and Fajr's end never after the real sunrise, but on the minute after a Fajr shown at or just before it.
        DayPipeline.realSunrise(point, date, zone)?.let { real ->
            for ((what, end) in listOfNotNull("sunrise" to day.sunrise, day.ends[Prayer.FAJR]?.let { "Fajr's end" to it })) {
                assertTrue(end <= real || end == day.fajr + 1.minutes, "$label: $what $end is after the real sunrise $real")
            }
        }
        val sunset = DayPipeline.realSunset(point, date, zone) ?: return
        assertTrue(day.maghrib > sunset, "$label: Maghrib ${day.maghrib} is not after the real sunset $sunset")
        day.earliestStart[Prayer.MAGHRIB]?.let { assertTrue(it > sunset, "$label: a member's Maghrib $it is said to begin before the real sunset $sunset") }
    }

    @Test
    fun `a day already after the real sun is the same day`() {
        val date = LocalDate(2027, 2, 20)
        val day = shown(cairo, date)
        assertSame(day, DayPipeline.onRealSun(day, cairo.point(), date, cairo.zone(), STANDARD))
    }

    @Test
    fun `a dhuhr before the real transit is raised to the first whole minute after it and nothing else moves`() {
        val date = LocalDate(2027, 2, 20)
        val day = shown(cairo, date)
        val transit = DayPipeline.realTransit(cairo.point(), date, cairo.zone())
        for (early in listOf(1.seconds, 59.seconds, 1.minutes, 7.minutes)) {
            val before = day.copy(dhuhr = Instant.fromEpochSeconds((transit - early).epochSeconds.floorDiv(60L) * 60))
            assertTrue(before.dhuhr < transit)

            val raised = DayPipeline.onRealSun(before, cairo.point(), date, cairo.zone(), STANDARD)

            assertEquals(firstMinuteAfter(transit), raised.dhuhr, "$early before")
            assertTrue(raised.dhuhr > transit && raised.dhuhr - transit <= 1.minutes && raised.dhuhr.isWholeMinute())
            assertEquals(before.copy(dhuhr = raised.dhuhr), raised, "only Dhuhr moved")
        }
    }

    @Test
    fun `a maghrib before the real sunset is raised to the first whole minute after it and is the sun's own`() {
        val date = LocalDate(2027, 2, 20)
        val day = shown(cairo, date)
        val sunset = assertNotNull(DayPipeline.realSunset(cairo.point(), date, cairo.zone()))
        for (early in listOf(1.seconds, 59.seconds, 1.minutes, 7.minutes)) {
            val before = day.copy(
                maghrib = Instant.fromEpochSeconds((sunset - early).epochSeconds.floorDiv(60L) * 60),
                setByRule = setOf(Prayer.MAGHRIB, Prayer.ISHA),
            )
            assertTrue(before.maghrib < sunset)

            val raised = DayPipeline.onRealSun(before, cairo.point(), date, cairo.zone(), STANDARD)

            assertEquals(firstMinuteAfter(sunset), raised.maghrib, "$early before")
            assertTrue(raised.maghrib > sunset && raised.maghrib - sunset <= 1.minutes && raised.maghrib.isWholeMinute())
            assertEquals(setOf(Prayer.ISHA), raised.setByRule, "the raised Maghrib is no rule's")
            assertEquals(raised.maghrib, raised.maghribBySun)
            assertEquals(before.copy(maghrib = raised.maghrib, setByRule = raised.setByRule), raised, "only Maghrib moved")
        }
    }

    /**
     * Ruling R293: an Isha the day already declares not followed (DayComputer's point 9, the authority's Isha before
     * the Maghrib shown) follows a Maghrib the floor raises past it, the minute after, with Maghrib's end; an Isha not
     * declared is left for the repair, so a wrong slot or margin still shows in the gate.
     */
    @Test
    fun `a declared isha follows the maghrib the floor raises past it and an undeclared one is left for the repair`() {
        val date = LocalDate(2027, 2, 20)
        val day = shown(cairo, date)
        val sunset = assertNotNull(DayPipeline.realSunset(cairo.point(), date, cairo.zone()))
        val early = Instant.fromEpochSeconds((sunset - 7.minutes).epochSeconds.floorDiv(60L) * 60)
        val declared = day.copy(
            maghrib = early, isha = early + 1.minutes, notFollowed = setOf(Prayer.ISHA),
            ends = day.ends + (Prayer.MAGHRIB to early + 1.minutes),
        )

        val raised = DayPipeline.onRealSun(declared, cairo.point(), date, cairo.zone(), STANDARD)

        assertEquals(firstMinuteAfter(sunset), raised.maghrib)
        assertEquals(raised.maghrib + 1.minutes, raised.isha, "the minute after the raised Maghrib")
        assertEquals(raised.isha, raised.ends[Prayer.MAGHRIB], "Maghrib's end with it")
        assertEquals(setOf(Prayer.ISHA), raised.notFollowed)
        assertTrue(Invariants.holds(raised), "in order without the repair")
        assertSame(raised, Invariants.repair(raised))

        val undeclared = DayPipeline.onRealSun(declared.copy(notFollowed = emptySet()), cairo.point(), date, cairo.zone(), STANDARD)
        assertEquals(early + 1.minutes, undeclared.isha, "not moved here")
        assertTrue(Invariants.repair(undeclared).repaired, "the repair, which the gate names")

        // A day joined from two (a safe side, a cautious, paused or border day) declares what either side declared: its
        // Isha, here the other side's two minutes later, is not point 9's, and stays for the repair (ruling R295).
        val joined = declared.copy(isha = early + 3.minutes)
        val fromTheOtherSide = DayPipeline.onRealSun(joined, cairo.point(), date, cairo.zone(), STANDARD)
        assertEquals(firstMinuteAfter(sunset), fromTheOtherSide.maghrib)
        assertEquals(early + 3.minutes, fromTheOtherSide.isha, "not carried")
        assertTrue(Invariants.repair(fromTheOtherSide).repaired, "the repair, which the gate names")
    }

    /**
     * Ruling R293 end to end, the gate's reach rows of June 2027 north of Luleå (the far-north days out of order of
     * 15 Nov 2026): Diyanet's sun (computed once a day, at 0h UT) sets up to a quarter of an hour before the real one
     * where the sun only grazes the horizon, so its Maghrib and the Isha point 9 put after it are before the real
     * sunset; the floor raises Maghrib, and Isha now follows it instead of being repaired.
     */
    @Test
    fun `north of lulea in june no day is repaired and a raised maghrib carries its declared isha`() {
        val entry = assertNotNull(Registry.byId("tr.diyanet.europe"))
        val zone = TimeZone.of("Europe/Stockholm")
        var carried = 0
        for (point in listOf(GeoPoint(65.739159, 22.15465), GeoPoint(65.878666, 22.15465), GeoPoint(65.791564, 22.662521))) {
            val resolution = Registry.resolveEntry(entry, Place(point.lat, point.lon, zone.id, "SE"))
            assertEquals("Luleå", resolution.unitName, "$point")
            var date = LocalDate(2027, 6, 1)
            while (date <= LocalDate(2027, 7, 10)) {
                val day = DayPipeline.day(resolution, date, zone)
                assertFalse(day.repaired, "$point $date: repaired")
                assertTrue(Prayer.ISHA in day.ends, "$point $date: Isha has its end")
                val sunset = DayPipeline.realSunset(point, date, zone)
                if (sunset != null && day.maghrib == firstMinuteAfter(sunset) && day.isha == day.maghrib + 1.minutes) {
                    assertTrue(Prayer.ISHA in day.notFollowed, "$point $date")
                    carried++
                }
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
        assertTrue(carried > 0, "no day of the floor's Maghrib with Isha after it")
    }

    @Test
    fun `a start on the event's own minute waits for the next`() {
        // The transit rounded down to its minute is not after the transit: the first whole minute after it is.
        val date = LocalDate(2027, 2, 20)
        val day = shown(cairo, date)
        val transit = DayPipeline.realTransit(cairo.point(), date, cairo.zone())
        val sunset = assertNotNull(DayPipeline.realSunset(cairo.point(), date, cairo.zone()))
        val onTheMinute = day.copy(
            dhuhr = Instant.fromEpochSeconds(transit.epochSeconds.floorDiv(60L) * 60),
            maghrib = Instant.fromEpochSeconds(sunset.epochSeconds.floorDiv(60L) * 60),
        )

        val raised = DayPipeline.onRealSun(onTheMinute, cairo.point(), date, cairo.zone(), STANDARD)

        assertEquals(onTheMinute.dhuhr + 1.minutes, raised.dhuhr)
        assertEquals(onTheMinute.maghrib + 1.minutes, raised.maghrib)
    }

    @Test
    fun `where the sun does not set there is no sunset to wait for and dhuhr still waits for the transit`() {
        for (date in listOf(LocalDate(2027, 6, 21), LocalDate(2026, 12, 21))) {
            val zone = longyearbyen.zone()
            assertNull(DayPipeline.realSunset(longyearbyen.point(), date, zone), "$date: the sun neither rises nor sets")
            val day = shown(longyearbyen, date)
            val transit = DayPipeline.realTransit(longyearbyen.point(), date, zone)
            val before = day.copy(dhuhr = firstMinuteAfter(transit) - 3.minutes)

            val raised = DayPipeline.onRealSun(before, longyearbyen.point(), date, zone, STANDARD)

            assertEquals(firstMinuteAfter(transit), raised.dhuhr, "$date")
            assertEquals(before.maghrib, raised.maghrib, "$date: Maghrib is left as its rule gave it")
            assertEquals(before.setByRule, raised.setByRule - Prayer.DHUHR)
        }
    }

    @Test
    fun `a held maghrib stands only while it is after the real sun's`() {
        val date = LocalDate(2026, 12, 21)
        val zone = trondheim.zone()
        val sunset = assertNotNull(DayPipeline.realSunset(trondheim.point(), date, zone))
        val floor = firstMinuteAfter(sunset)
        val day = shown(trondheim, date)

        // Held well after the sun, with the sun's own Maghrib before the real sunset: the hold stands, on the floor.
        val held = day.copy(maghrib = floor + 40.minutes, maghribHeld = true, maghribBeforeHold = floor - 2.minutes)
        val kept = DayPipeline.onRealSun(held, trondheim.point(), date, zone, STANDARD)
        assertEquals(held.copy(maghribBeforeHold = floor), kept)
        assertEquals(floor, kept.maghribBySun)

        // Held, yet before the real sunset: raised to the floor, which is the sun's and no hold.
        val early = day.copy(maghrib = floor - 1.minutes, maghribHeld = true, maghribBeforeHold = floor - 6.minutes)
        val raised = DayPipeline.onRealSun(early, trondheim.point(), date, zone, STANDARD)
        assertEquals(floor, raised.maghrib)
        assertFalse(raised.maghribHeld)
        assertNull(raised.maghribBeforeHold)
        assertEquals(floor, raised.maghribBySun)
    }

    /**
     * The earliest member starts of a cautious day ("Show where timetables differ") are held to the floor too, though
     * the day's own Dhuhr and Maghrib, the latest members', are already after it: no member's Dhuhr is said to have
     * begun before the real transit at the user's point, nor its Maghrib before the real sunset.
     */
    @Test
    fun `a cautious day's earliest member starts wait for the real sun too`() {
        val date = LocalDate(2027, 3, 4)
        val zone = trondheim.zone()
        val transitFloor = firstMinuteAfter(DayPipeline.realTransit(trondheim.point(), date, zone))
        val sunsetFloor = firstMinuteAfter(assertNotNull(DayPipeline.realSunset(trondheim.point(), date, zone)))
        val day = shown(trondheim, date)
        assertTrue(day.earliestStart.isNotEmpty(), "Trondheim's day is a cautious one")
        val late = day.copy(dhuhr = transitFloor + 5.minutes, maghrib = sunsetFloor + 5.minutes, maghribHeld = false, maghribBeforeHold = null)

        val early = late.copy(earliestStart = late.earliestStart + mapOf(Prayer.DHUHR to transitFloor - 2.minutes, Prayer.MAGHRIB to sunsetFloor - 1.minutes))
        val floored = DayPipeline.onRealSun(early, trondheim.point(), date, zone, STANDARD)
        assertEquals(transitFloor, floored.earliestStart[Prayer.DHUHR])
        assertEquals(sunsetFloor, floored.earliestStart[Prayer.MAGHRIB])
        assertEquals(early.earliestStart - Prayer.DHUHR - Prayer.MAGHRIB, floored.earliestStart - Prayer.DHUHR - Prayer.MAGHRIB)
        assertEquals(early, floored.copy(earliestStart = early.earliestStart), "nothing else moves")

        // Already after the sun: the same day.
        val after = late.copy(earliestStart = late.earliestStart + mapOf(Prayer.DHUHR to transitFloor, Prayer.MAGHRIB to sunsetFloor + 1.minutes))
        assertSame(after, DayPipeline.onRealSun(after, trondheim.point(), date, zone, STANDARD))
    }

    @Test
    fun `an asr before the real shadow time is raised to the first whole minute after it in each school`() {
        val date = LocalDate(2027, 6, 19)
        for (school in AsrSchool.entries) {
            val day = shown(cairo, date, school)
            val real = assertNotNull(DayPipeline.realAsr(cairo.point(), date, cairo.zone(), school))
            val realOther = assertNotNull(DayPipeline.realAsr(cairo.point(), date, cairo.zone(), school.other))
            for (early in listOf(1.seconds, 59.seconds, 3.minutes)) {
                val before = day.copy(
                    asr = Instant.fromEpochSeconds((real - early).epochSeconds.floorDiv(60L) * 60),
                    asrOther = Instant.fromEpochSeconds((realOther - early).epochSeconds.floorDiv(60L) * 60),
                )
                val raised = DayPipeline.onRealSun(before, cairo.point(), date, cairo.zone(), school)
                assertEquals(firstMinuteAfter(real), raised.asr, "$school $early before")
                assertEquals(firstMinuteAfter(realOther), raised.asrOther, "$school other $early before")
                assertEquals(before.copy(asr = raised.asr, asrOther = raised.asrOther), raised, "$school: only Asr moved")
            }
        }
        // The Hanafi Asr is later than the Standard one: a day in one school is not floored by the other's shadow.
        assertTrue(assertNotNull(DayPipeline.realAsr(cairo.point(), date, cairo.zone(), HANAFI)) >
            assertNotNull(DayPipeline.realAsr(cairo.point(), date, cairo.zone(), STANDARD)))
    }

    @Test
    fun `an asr a rule sets or a polar day's is not floored`() {
        val date = LocalDate(2027, 6, 19)
        val day = shown(cairo, date)
        val real = assertNotNull(DayPipeline.realAsr(cairo.point(), date, cairo.zone(), STANDARD))
        val early = Instant.fromEpochSeconds((real - 10.minutes).epochSeconds.floorDiv(60L) * 60)
        val ruled = day.copy(asr = early, setByRule = day.setByRule + Prayer.ASR)
        assertEquals(early, DayPipeline.onRealSun(ruled, cairo.point(), date, cairo.zone(), STANDARD).asr)
        val polar = day.copy(asr = early, polar = true)
        assertEquals(early, DayPipeline.onRealSun(polar, cairo.point(), date, cairo.zone(), STANDARD).asr)
        // In the polar night the shadow formula still answers, with a sun below the horizon: no real Asr there.
        assertNull(DayPipeline.realAsr(longyearbyen.point(), LocalDate(2026, 12, 21), longyearbyen.zone(), STANDARD))
    }

    @Test
    fun `a sunrise after the real sunrise is the minute the sun rose in and fajr's end with it`() {
        val date = LocalDate(2027, 12, 22)
        val day = shown(cairo, date)
        val sunrise = assertNotNull(DayPipeline.realSunrise(cairo.point(), date, cairo.zone()))
        val minute = Instant.fromEpochSeconds(sunrise.epochSeconds.floorDiv(60L) * 60)
        for (late in listOf(1.minutes, 3.minutes)) {
            val before = day.copy(sunrise = minute + late, ends = day.ends + (Prayer.FAJR to minute + late))
            val capped = DayPipeline.onRealSun(before, cairo.point(), date, cairo.zone(), STANDARD)
            assertEquals(minute, capped.sunrise, "$late after")
            assertEquals(minute, capped.ends[Prayer.FAJR], "$late after")
            assertTrue(capped.sunrise <= sunrise && sunrise - capped.sunrise < 1.minutes)
            assertEquals(before.copy(sunrise = capped.sunrise, ends = capped.ends), capped, "only sunrise and Fajr's end moved")
        }
        // A sunrise already at or before the sun's stays.
        val early = day.copy(sunrise = minute - 2.minutes, ends = day.ends + (Prayer.FAJR to minute - 2.minutes))
        assertEquals(early.sunrise, DayPipeline.onRealSun(early, cairo.point(), date, cairo.zone(), STANDARD).sunrise)
    }

    @Test
    fun `a sunrise cap stays a minute after the fajr shown and fajr never moves earlier`() {
        // A Fajr an authority's rule puts after the sun has risen (declared not shown, ruling R82): the repair would
        // move that Fajr before a capped sunrise, so the sunrise stands on the minute after the Fajr shown.
        val date = LocalDate(2027, 12, 22)
        val day = shown(cairo, date)
        val sunrise = assertNotNull(DayPipeline.realSunrise(cairo.point(), date, cairo.zone()))
        val minute = Instant.fromEpochSeconds(sunrise.epochSeconds.floorDiv(60L) * 60)
        val ruled = day.copy(fajr = minute + 1.minutes, endOfEating = minute - 10.minutes, sunrise = minute + 4.minutes,
            ends = day.ends + (Prayer.FAJR to minute + 4.minutes), notFollowed = setOf(Prayer.FAJR),
        )
        val shown = Invariants.repair(DayPipeline.onRealSun(ruled, cairo.point(), date, cairo.zone(), STANDARD))
        assertEquals(ruled.fajr, shown.fajr)
        assertEquals(ruled.fajr + 1.minutes, shown.sunrise)
        assertEquals(ruled.fajr + 1.minutes, shown.ends[Prayer.FAJR])
        assertFalse(shown.repaired)
    }

    @Test
    fun `where the first shadow comes before the dhuhr shown dhuhr shows no end`() {
        val date = LocalDate(2027, 8, 30)
        val day = shown(cairo, date)
        val real = assertNotNull(DayPipeline.realAsr(cairo.point(), date, cairo.zone(), STANDARD))
        val late = day.copy(dhuhr = Instant.fromEpochSeconds(real.epochSeconds.floorDiv(60L) * 60), asr = day.asr + 5.minutes)
        val shown = DayPipeline.onRealSun(late, cairo.point(), date, cairo.zone(), STANDARD)
        assertNull(shown.ends[Prayer.DHUHR])
        assertTrue(Invariants.holds(shown))
    }

    @Test
    fun `dhuhr's end is never after the real shadow-1 asr in either school`() {
        val date = LocalDate(2027, 8, 30)
        for (school in AsrSchool.entries) {
            val day = shown(cairo, date, school)
            val real = assertNotNull(DayPipeline.realAsr(cairo.point(), date, cairo.zone(), STANDARD))
            val minute = Instant.fromEpochSeconds(real.epochSeconds.floorDiv(60L) * 60)
            val before = day.copy(ends = day.ends + (Prayer.DHUHR to minute + 12.minutes))
            val capped = DayPipeline.onRealSun(before, cairo.point(), date, cairo.zone(), school)
            assertEquals(minute, capped.ends[Prayer.DHUHR], "$school")
            assertTrue(shown(cairo, date, school).ends.getValue(Prayer.DHUHR) <= real, "$school: the day shown")
        }
    }

    /**
     * End to end: a method whose margins put Dhuhr and Maghrib five minutes early (a table printed for a point far
     * to the east, a fit gone wrong) still leaves the pipeline at the real sun, in order.
     */
    @Test
    fun `a method five minutes early on both leaves the pipeline at the real sun in order`() {
        val resolution = Registry.resolve(cairo)
        val method = assertNotNull(resolution.method)
        val early = resolution.copy(method = method.copy(margins = method.margins.copy(dhuhr = -300, maghrib = -300, isha = -6000)))
        var date = LocalDate(2026, 11, 12)
        repeat(60) {
            val zone = cairo.zone()
            val day = DayPipeline.day(early, date, zone)
            assertEquals(firstMinuteAfter(DayPipeline.realTransit(cairo.point(), date, zone)), day.dhuhr, "$date")
            assertEquals(firstMinuteAfter(assertNotNull(DayPipeline.realSunset(cairo.point(), date, zone))), day.maghrib, "$date")
            assertTrue(Invariants.holds(day), "$date: in order")
            assertTrue(day.isha > day.maghrib && day.asr > day.dhuhr, "$date")
            assertTrue(day.ends.all { (_, end) -> end.isWholeMinute() })
            date = date.plus(7, DateTimeUnit.DAY)
        }
    }

    /**
     * The property, over every entry that has units: at unit points spread over each entry's list, at the point itself
     * and a few kilometres west of it (where the user's own sun is later than the table's), Automatic there and the
     * entry chosen there, through fourteen months, each school on alternate days. A fixed-offset zone by longitude
     * stands in for the place's own: the rule is about the sun at the point on the pipeline's own date, whatever the
     * clock.
     */
    @Test
    fun `no unit point of any entry and no place beside one is before the real sun`() {
        var days = 0
        for (entry in Registry.entries) {
            val units = Units.of(entry.id)?.units ?: continue
            val step = maxOf(1, units.size / UNITS_PER_ENTRY)
            val country = entry.countries.firstOrNull()?.uppercase() ?: "XX"
            for (unit in units.filterIndexed { i, _ -> i % step == 0 }) {
                for (west in listOf(0.0, 0.07)) {
                    val point = GeoPoint(unit.point.lat, unit.point.lon - west)
                    val zoneId = offsetZone(point.lon)
                    val zone = TimeZone.of(zoneId)
                    val place = Place(point.lat, point.lon, zoneId, country)
                    val resolutions = listOf(Registry.resolveEntry(entry, place), Registry.resolve(place)).distinct()
                    for (resolution in resolutions) {
                        var date = LocalDate(2026, 11, 12)
                        var turn = 0
                        while (date < LocalDate(2028, 1, 1)) {
                            // Each school on alternate days (ruling R265 floors both Asr times and caps ends either way).
                            val school = if (turn++ % 2 == 0) resolution.entry.school else resolution.entry.school.other
                            val day = DayPipeline.unended(resolution, date, zone, school)
                            assertOnOrAfterTheSun(day, resolution.point, date, zone, school, "${resolution.entry.id} ${unit.id} $date")
                            days++
                            date = date.plus(DAY_STEP, DateTimeUnit.DAY)
                        }
                    }
                }
            }
        }
        assertTrue(days > 10_000, "$days days walked")
    }

    /** The same property at places from the equator to Svalbard, Automatic and every Other method, every third day. */
    @Test
    fun `no place from the equator to the pole is before the real sun under automatic or any other method`() {
        val places = listOf(
            cairo, longyearbyen, trondheim,
            Place(31.7167, 35.1500, "Asia/Hebron", "PS"), // Battir
            Place(31.50161, 34.46672, "Asia/Gaza", "PS"),
            Place(26.22787, 50.58565, "Asia/Bahrain", "BH"),
            Place(53.33306, -6.24889, "Europe/Dublin", "IE"),
            Place(50.85045, 4.34878, "Europe/Brussels", "BE"),
            Place(-36.84853, 174.76349, "Pacific/Auckland", "NZ"),
            Place(-33.92584, 18.42322, "Africa/Johannesburg", "ZA"),
            Place(49.24966, -123.11934, "America/Vancouver", "CA"),
            Place(69.6492, 18.9553, "Europe/Oslo", "NO"), // Tromsø
            Place(65.84811, 24.14662, "Europe/Helsinki", "FI"), // Tornio
            Place(-54.8, -68.3, "America/Argentina/Ushuaia", "AR"),
            Place(0.31628, 32.58219, "Africa/Kampala", "UG"),
            Place(21.4225, 39.8262, "Asia/Riyadh", "SA"),
            Place(-13.83333, -171.76666, "Pacific/Apia", "WS"),
        )
        var days = 0
        for (place in places) {
            val zone = place.zone()
            val resolutions = (listOf(Registry.resolve(place)) + Registry.otherMethods.map { Registry.resolveEntry(it, place) }).distinct()
            for (resolution in resolutions) {
                var date = LocalDate(2026, 11, 12)
                while (date < LocalDate(2027, 11, 12)) {
                    for (school in AsrSchool.entries) {
                        val day = DayPipeline.unended(resolution, date, zone, school)
                        assertOnOrAfterTheSun(day, resolution.point, date, zone, school, "${resolution.entry.id} $school at ${place.lat},${place.lon} $date")
                    }
                    days++
                    date = date.plus(3, DateTimeUnit.DAY)
                }
            }
        }
        assertTrue(days > 5_000, "$days days walked")
    }

    @Test
    fun `the real sun sets only where it rises again after under ruling R321`() {
        // 69.895, 18.852 in Tromsø's reach (IRN) on 16 May 2026: the sun's lowest is above the real horizon, so the
        // night is the first of the midnight sun there, with no sunset and no next sunrise; a sunset 22:40:58 UTC was
        // reported with no sunrise after it, and the day showed the sun's Maghrib and an Isha past IRN's reach.
        val point = GeoPoint(69.895, 18.852)
        val zone = TimeZone.of("Europe/Oslo")
        val date = LocalDate(2026, 5, 16)
        assertNull(DayPipeline.realSunset(point, date, zone))
        assertNull(DayPipeline.realSunrise(point, date.plus(1, DateTimeUnit.DAY), zone))
        // Across the edge of the midnight sun near Tromsø: on every night of May and the end of July the real sun sets
        // exactly where it rises again the next morning.
        var nights = 0
        for (step in 0..900) {
            val at = GeoPoint(69.3 + step / 1000.0, 18.852)
            for (d in listOf(LocalDate(2026, 5, 1), LocalDate(2026, 7, 20))) {
                var day = d
                repeat(31) {
                    val set = DayPipeline.realSunset(at, day, zone) != null
                    val rise = DayPipeline.realSunrise(at, day.plus(1, DateTimeUnit.DAY), zone) != null
                    assertEquals(set, rise, "$at $day: a sunset $set, the next sunrise $rise")
                    if (set) nights++
                    day = day.plus(1, DateTimeUnit.DAY)
                }
            }
        }
        assertTrue(nights > 10_000, "$nights nights")
    }

    @Test
    fun `the real sun sets only where it rises again after at the solstice too`() {
        // The final review of 20 Nov 2026: within about a week of the June solstice R321's iteration settles onto the sun's
        // lowest point, so a night the sun misses the horizon by under 1e-6° still had one side (65.802, 12.533 on 25 June
        // 2026, Norway's Cautious ground: no sunset, a sunrise at 23:12:45 UTC). On the app's own grid across the band the
        // midnight sun's southern edge crosses that month, at two meridians, the real sun sets exactly where it rises again.
        val zone = TimeZone.of("Europe/Oslo")
        assertNull(DayPipeline.realSunset(GeoPoint(65.802, 12.533), LocalDate(2026, 6, 25), zone))
        assertNull(DayPipeline.realSunrise(GeoPoint(65.802, 12.533), LocalDate(2026, 6, 26), zone))
        var nights = 0
        for (step in 0..250) {
            for (lon in listOf(12.533, 18.852)) {
                val at = GeoPoint(65.7 + step / 1000.0, lon)
                var day = LocalDate(2026, 6, 10)
                repeat(21) {
                    val set = DayPipeline.realSunset(at, day, zone) != null
                    val rise = DayPipeline.realSunrise(at, day.plus(1, DateTimeUnit.DAY), zone) != null
                    assertEquals(set, rise, "$at $day: a sunset $set, the next sunrise $rise")
                    if (set) nights++
                    day = day.plus(1, DateTimeUnit.DAY)
                }
            }
        }
        assertTrue(nights > 1_000, "$nights nights")
    }

    @Test
    fun `maghrib's floor is the minute after the sun's own sunset where the iteration stops short of it`() {
        // The final review of 20 Nov 2026: where the iteration does not settle, the step it stops on was kept as the sunset,
        // and in the evening it stops before the sun's own moment. At 69.671, 18.900 (Tromsø's reach) on a night of May 2026 the
        // step read 22:39:54.0 UTC and the sun sets at 22:40:00.4: the floor, the minute after the step, was 22:40:00,
        // 0.4 s before the sunset itself. A start takes the later of the two moments.
        val zone = TimeZone.of("Europe/Oslo")
        val point = GeoPoint(69.671, 18.900)
        val date = LocalDate(2026, 5, 17)
        val sunset = assertNotNull(DayPipeline.realSunset(point, date, zone))
        assertTrue(sunset >= Instant.fromEpochMilliseconds(1_779_057_600_400), "the real sunset $sunset is before the sun's own, 22:40:00.4")
        assertEquals(Instant.fromEpochSeconds(1_779_057_660), firstMinuteAfter(sunset))
        val day = DayPipeline.unended(Registry.resolve(Place(point.lat, point.lon, "Europe/Oslo", "NO")), date, zone)
        assertTrue(day.maghrib >= Instant.fromEpochSeconds(1_779_057_660), "Maghrib ${day.maghrib} before the minute after the sun's own sunset")
    }

    @Test
    fun `the sunrise cap reads the sun's own sunrise where the iteration stops after it`() {
        // The mirror: in the morning the step stops after the sun's own moment. At 67.608, 18.852 on 15 July 2026 it read
        // 22:51:08.1 UTC (14 July) for a sunrise at 22:51:02.6. An end takes the earlier of the two.
        val sunrise = assertNotNull(DayPipeline.realSunrise(GeoPoint(67.608, 18.852), LocalDate(2026, 7, 15), TimeZone.of("Europe/Stockholm")))
        assertTrue(sunrise <= Instant.fromEpochMilliseconds(1_784_069_462_700), "the real sunrise $sunrise is after the sun's own, 22:51:02.6")
    }

    private fun offsetZone(lon: Double): String {
        val hours = (lon / 15.0).roundToInt()
        return when {
            hours == 0 -> "UTC"
            hours > 0 -> "UTC+$hours"
            else -> "UTC$hours"
        }
    }

    private companion object {
        /** At most about this many units of an entry are walked, spread over its list. */
        const val UNITS_PER_ENTRY = 30

        /** Days between the dates walked: not a divisor of a week or a month, so weekdays and month days both turn. */
        const val DAY_STEP = 13
    }
}
