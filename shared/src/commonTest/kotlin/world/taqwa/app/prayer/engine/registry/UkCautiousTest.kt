package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.data.UkLateDawnCurve
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The UK outside London (`gb.cautious`, research-uk, ruling R75), in values the engine computes at run
 * time: no mosque calendar is committed (ruling R69), so the survey itself runs in tools/timetables
 * against the local archive (UkMawaqitSurveyTest).
 */
class UkCautiousTest {
    private val zone = TimeZone.of("Europe/London")
    private val birmingham = Place(52.4862, -1.8904, "Europe/London", "GB")

    private fun clock(place: Place, date: LocalDate): SunClock {
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        return SunClock(place.lat, place.lon, date, offset, SunModel.EXACT)
    }

    /** An end: rounded down to the minute, in epoch seconds. */
    private fun endOf(epochSeconds: Double): Long = floor(epochSeconds / 60.0).toLong() * 60

    /** A start: rounded up to the minute, in epoch seconds. */
    private fun startOf(epochSeconds: Double): Long = ceil(epochSeconds / 60.0).toLong() * 60

    @Test
    fun `the karachi family's fast begins at its own dawn or the middle of the night`() {
        val karachi = Registry.resolve(birmingham).members.single { it.id == "gb.karachi" }.method
        assertEquals(EndOfEating.DawnOrMiddle(18.6), karachi.endOfEating)
        assertEquals(SAFE_END, karachi.endOfEatingMarginSeconds)
    }

    @Test
    fun `birmingham's fast begins at the middle of a june night the sun leaves above the karachi dawn`() {
        val date = LocalDate(2026, 6, 21)
        assertNull(clock(birmingham, date).altitudeTime(-18.6, morning = true))
        val sunset = assertNotNull(clock(birmingham, date.minus(1, DateTimeUnit.DAY)).altitudeTime(-0.8333, morning = false))
        val sunrise = assertNotNull(clock(birmingham, date).altitudeTime(-0.8333, morning = true))
        val day = DayPipeline.day(Registry.resolve(birmingham), date, zone)
        assertEquals(endOf((sunset + sunrise) / 2 + SAFE_END), day.endOfEating.epochSeconds)
    }

    @Test
    fun `birmingham's fast begins at the karachi dawn on a winter night`() {
        val date = LocalDate(2026, 1, 15)
        val dawn = assertNotNull(clock(birmingham, date).altitudeTime(-18.6, morning = true))
        val day = DayPipeline.day(Registry.resolve(birmingham), date, zone)
        assertEquals(endOf(dawn + SAFE_END), day.endOfEating.epochSeconds)
    }

    @Test
    fun `birmingham's june fajr is the late dawn family's and no earlier than its curve`() {
        val date = LocalDate(2026, 6, 21)
        val r = Registry.resolve(birmingham)
        val depression = UkLateDawnCurve.fajr[curveSlot(date)]
        val dawn = assertNotNull(clock(birmingham, date).altitudeTime(-depression, morning = true))
        val day = DayPipeline.day(r, date, zone)
        assertEquals(startOf(dawn + SAFE_START), day.fajr.epochSeconds)
        val members = DayPipeline.members(r, date, zone)
        val late = r.members.indexOfFirst { it.id == "gb.latedawn" }
        for ((i, member) in members.withIndex()) {
            if (i != late) assertTrue(member.fajr < day.fajr, "${r.members[i].id} Fajr ${member.fajr} is not before ${day.fajr}")
        }
    }

    /**
     * The late-dawn family leads on Fajr alone (research-uk): its Isha, Zuhr, Asr, Maghrib, sunrise and
     * sunset are never the ones shown, anywhere in the UK outside London, on any day.
     */
    @Test
    fun `the late dawn member moves nothing but fajr anywhere in the uk`() {
        val places = listOf(
            "Plymouth" to Place(50.3755, -4.1427, "Europe/London", "GB"),
            "Birmingham" to birmingham,
            "Manchester" to Place(53.48095, -2.23743, "Europe/London", "GB"),
            "Belfast" to Place(54.5973, -5.9301, "Europe/London", "GB"),
            "Glasgow" to Place(55.8642, -4.2518, "Europe/London", "GB"),
            "Aberdeen" to Place(57.1497, -2.0943, "Europe/London", "GB"),
            "Lerwick" to Place(60.1553, -1.1450, "Europe/London", "GB"),
        )
        for ((name, place) in places) {
            val r = Registry.resolve(place)
            assertEquals("gb.cautious", r.entry.id, name)
            val late = r.members.indexOfFirst { it.id == "gb.latedawn" }
            var date = LocalDate(2026, 1, 1)
            while (date.year == 2026) {
                val members = DayPipeline.members(r, date, zone)
                val all = Cautious.combine(members, 0)
                val without = Cautious.combine(members.filterIndexed { i, _ -> i != late }, 0)
                val label = "$name $date"
                assertEquals(without.sunrise, all.sunrise, "$label sunrise")
                assertEquals(without.dhuhr, all.dhuhr, "$label dhuhr")
                assertEquals(without.asr, all.asr, "$label asr")
                assertEquals(without.asrOther, all.asrOther, "$label asrOther")
                assertEquals(without.maghrib, all.maghrib, "$label maghrib")
                assertEquals(without.isha, all.isha, "$label isha")
                assertEquals(without.sunset, all.sunset, "$label sunset")
                assertEquals(without.endOfEating, all.endOfEating, "$label endOfEating")
                assertTrue(all.fajr >= without.fajr, "$label fajr")
                date = date.plus(3, DateTimeUnit.DAY)
            }
        }
    }

    /**
     * The threshold night (research-uk, the class of ruling R72): on the night a member's Isha angle
     * (15°, 17° or 18°) is last reached before the summer, or first reached again after it, that
     * member's Isha, the latest, lies near the sun's lowest point, and the next end of eating, the
     * middle of the night on those nights, at or just before it, so that Isha has no end (ruling R26).
     * It happens from April to August at some latitudes only: up to two nights a year at a point, by up
     * to 3 min (a scan every 0.05° from 50.05° to 60.8° N at 2.1° and 4.0° W over 2026 and 2027 found
     * it in 77 of 864 point-years, and none at the half degrees along 2.1° W in 2026). It is recorded,
     * not absorbed (the proof, and the survey's outliers). Walked here over 2026 at nine cities (one
     * night at Stornoway and one at Thurso) and at seven of the scan's points where it happens: two
     * nights at Hyde in Tameside (15° and 18°, one of them 3 min) and at 54.45° N 2.1° W (17° and 18°),
     * 3 min at Motherwell, and 15°, 17° and 18° nights elsewhere.
     */
    @Test
    fun `the latest isha meets the next end of eating only on threshold nights`() {
        val places = listOf(
            Place(50.3755, -4.1427, "Europe/London", "GB"), // Plymouth
            birmingham,
            Place(53.48095, -2.23743, "Europe/London", "GB"), // Manchester
            Place(53.7960, -1.7594, "Europe/London", "GB"), // Bradford
            glasgow,
            Place(57.1497, -2.0943, "Europe/London", "GB"), // Aberdeen
            Place(58.2090, -6.3860, "Europe/London", "GB"), // Stornoway
            Place(58.5936, -3.5221, "Europe/London", "GB"), // Thurso
            Place(60.1553, -1.1450, "Europe/London", "GB"), // Lerwick
            Place(53.45, -2.1, "Europe/London", "GB"), // Hyde, Tameside
            Place(54.45, -2.1, "Europe/London", "GB"),
            Place(55.8, -4.0, "Europe/London", "GB"), // Motherwell
            Place(51.6, -4.0, "Europe/London", "GB"), // near Swansea
            Place(53.1, -4.0, "Europe/London", "GB"), // Snowdonia
            Place(58.8, -4.0, "Europe/London", "GB"),
            Place(59.2, -2.1, "Europe/London", "GB"),
        )
        for (place in places) {
            val r = Registry.resolve(place)
            assertEquals("gb.cautious", r.entry.id, "${place.lat} ${place.lon}")
            // DayPipeline.day, each date's day computed once: Isha's end is the next day's end of eating.
            val unended = HashMap<LocalDate, PrayerDay>()
            fun unended(date: LocalDate) = unended.getOrPut(date) { DayPipeline.unended(r, date, zone) }
            fun reached(degrees: Double, date: LocalDate) = clock(place, date).altitudeTime(-degrees, morning = false) != null
            var date = LocalDate(2026, 1, 1)
            val nights = mutableListOf<String>()
            while (date.year == 2026) {
                val next = unended(date.plus(1, DateTimeUnit.DAY))
                val day = Ends.withNextDay(unended(date), next)
                val label = "${place.lat} ${place.lon} $date"
                if (day.ends[Prayer.ISHA] == null) {
                    val gap = (day.isha - next.endOfEating).inWholeMinutes
                    assertTrue(gap in 0..3, "$label: the next end of eating is $gap min before Isha")
                    val members = DayPipeline.members(r, date, zone)
                    val onThreshold = members.indices.filter { members[it].isha == day.isha }.any { i ->
                        val degrees = assertNotNull((r.members[i].method.isha as? IshaRule.Angle)?.degrees, "$label ${r.members[i].id}")
                        reached(degrees, date) &&
                            (!reached(degrees, date.plus(1, DateTimeUnit.DAY)) || !reached(degrees, date.minus(1, DateTimeUnit.DAY)))
                    }
                    assertTrue(onThreshold, "$label: Isha without an end, not on the night its angle is last or first reached")
                    nights += date.toString()
                }
                assertFalse(day.repaired, "$label put back in order")
                date = date.plus(1, DateTimeUnit.DAY)
            }
            assertTrue(nights.size <= 2, "${place.lat} ${place.lon}: Isha without an end on $nights")
        }
    }

    // Scotland: the 15° family takes Glasgow's Asr (research-uk).

    /**
     * Glasgow's Asr after the exact time, in seconds, January to December (research-uk: each month's
     * latest over its 11 calendars, less the printed minute, plus 15 s, less our 30 s start margin).
     */
    private val glasgowAsrSeconds = intArrayOf(196, 391, 597, 237, 250, 280, 388, 473, 509, 531, 491, 368)

    private val glasgow = Place(55.8642, -4.2518, "Europe/London", "GB")

    private fun fifteenAt(place: Place) = Registry.resolve(place).members.single { it.id == "gb.fifteen" }.method

    @Test
    fun `glasgow's 15 degree family takes glasgow's asr minutes month by month`() {
        val scottish = fifteenAt(glasgow)
        val english = fifteenAt(Place(54.8925, -2.9329, "Europe/London", "GB")) // Carlisle
        val point = GeoPoint(glasgow.lat, glasgow.lon)
        val ramadan = Registry.ramadanCalendar()
        for (month in 1..12) {
            val date = LocalDate(2026, month, 15)
            val here = DayComputer.compute(scottish, point, date, zone, AsrSchool.HANAFI, ramadan)
            // The same sun under England's minutes (Asr + 4), at Glasgow's own point.
            val there = DayComputer.compute(english, point, date, zone, AsrSchool.HANAFI, ramadan)
            val later = glasgowAsrSeconds[month - 1] - 4 * 60
            for ((name, gap) in listOf("asr" to here.asr - there.asr, "asrOther" to here.asrOther - there.asrOther)) {
                val seconds = gap.inWholeSeconds
                assertTrue(seconds in later - 60..later + 60, "$date $name moved $seconds s, not about $later")
            }
            val dhuhr = (here.dhuhr - there.dhuhr).inWholeSeconds
            if (month == 6) assertTrue(dhuhr in 0..60, "$date dhuhr moved $dhuhr s") else assertEquals(0L, dhuhr, "$date dhuhr")
            // The cautious Asr is never before the family's own.
            val day = DayPipeline.day(Registry.resolve(glasgow), date, zone)
            assertTrue(day.asr >= here.asr && day.asrOther >= here.asrOther, "$date cautious Asr")
        }
        assertEquals(null, english.monthlyOffsets)
        assertEquals(glasgowAsrSeconds.toList(), scottish.monthlyOffsets?.get(Prayer.ASR)?.toList())
    }

    @Test
    fun `scotland ends at the border and the north channel`() {
        val inside = listOf(
            "Glasgow" to glasgow.let { GeoPoint(it.lat, it.lon) }, "Edinburgh" to GeoPoint(55.9533, -3.1883),
            "Lerwick" to GeoPoint(60.1553, -1.1450), "Stornoway" to GeoPoint(58.2090, -6.3860), "St Kilda" to GeoPoint(57.8130, -8.5700),
            "Stranraer" to GeoPoint(54.9030, -5.0240), "Gretna" to GeoPoint(54.9950, -3.0660), "Coldstream" to GeoPoint(55.6490, -2.2510),
            "Eyemouth" to GeoPoint(55.8700, -2.0900), "Campbeltown" to GeoPoint(55.4250, -5.6070),
        )
        val outside = listOf(
            "Berwick-upon-Tweed" to GeoPoint(55.7710, -2.0070), "Cornhill-on-Tweed" to GeoPoint(55.6450, -2.2160),
            "Longtown" to GeoPoint(55.0080, -2.9700), "Carlisle" to GeoPoint(54.8925, -2.9329),
            "Newcastle" to GeoPoint(54.9783, -1.6178), "Belfast" to GeoPoint(54.5973, -5.9301), "Larne" to GeoPoint(54.8520, -5.8230),
            "Rathlin Island" to GeoPoint(55.2900, -6.1900), "Isle of Man" to GeoPoint(54.4160, -4.3680),
        )
        for ((name, point) in inside) assertTrue(Regions.scotland.contains(point), name)
        for ((name, point) in outside) assertFalse(Regions.scotland.contains(point), name)
    }
}
