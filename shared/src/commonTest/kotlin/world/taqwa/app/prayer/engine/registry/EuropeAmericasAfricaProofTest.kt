package world.taqwa.app.prayer.engine.registry

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Task 7g's proof, in the few points the repository may hold: the London Unified Prayer Timetable is
 * openly licensed (London Prayer Times, "completely free for all use"; ruling R33), so four of its 2026
 * rows are pinned here. Everything else in the group is checked by the gate against the local archive
 * (docs/research/2026-09-prayer-times/proof/7g-europe-americas-africa-oceania.md).
 *
 * Ruling R80's ends are checked here against each authority's own code, computed at run time by adhan2
 * (never a printed time): the ICCI's site runs adhan-js's MWL with the TwilightAngle rule, which adhan2
 * reproduces to the minute on every day of the ICCI's 2026 table, and the Grande Mosquée's 2026 method is
 * the Moonsighting Committee's, which adhan2 reproduces on 271 of its 273 January to September rows (the
 * gate, locally).
 */
class EuropeAmericasAfricaProofTest {
    private val zone = TimeZone.of("Europe/London")

    /** date, then Fajr (also the start of the fast), sunrise, Zuhr, Asr (Standard), Asr (Hanafi), Maghrib, Isha. */
    private val lupt = listOf(
        "2026-01-15 06:20 07:57 12:15 14:02 14:35 16:24 18:01",
        "2026-03-29 05:07 06:40 13:10 16:35 17:28 19:32 20:49",
        "2026-06-18 02:39 04:40 13:07 17:25 18:40 21:24 22:44",
        "2026-10-01 05:30 06:58 12:55 15:56 16:44 18:42 20:00",
    )

    private fun at(date: LocalDate, hhmm: String): Instant =
        date.atTime(LocalTime.parse(hhmm)).toInstant(zone)

    @Test
    fun `london unified is never early at its point and within class B`() {
        val r = Registry.resolve(Place(51.5074, -0.1278, "Europe/London", "GB"))
        assertEquals("gb.london.lupt", r.entry.id)
        assertEquals(EntryClass.B, r.entryClass)
        for (line in lupt) {
            val cells = line.split(' ')
            val date = LocalDate.parse(cells[0])
            val (fajr, sunrise, dhuhr, asrStandard, asrHanafi, maghrib, isha) = cells.drop(1).map { at(date, it) }
            val day = DayComputer.compute(r.method!!, r.point, date, zone, AsrSchool.HANAFI, Registry.ramadanCalendarFor(r.entry))
            val starts = listOf(
                "fajr" to (day.fajr to fajr), "dhuhr" to (day.dhuhr to dhuhr), "asr" to (day.asr to asrHanafi),
                "asrOther" to (day.asrOther to asrStandard), "maghrib" to (day.maghrib to maghrib), "isha" to (day.isha to isha),
            )
            for ((name, pair) in starts) {
                val (shown, printed) = pair
                assertTrue(shown >= printed, "$date $name $shown before $printed")
                assertTrue(shown - printed <= 2.minutes, "$date $name $shown more than 2 min after $printed")
            }
            assertTrue(day.sunrise <= sunrise && sunrise - day.sunrise <= 2.minutes, "$date sunrise ${day.sunrise}")
            assertTrue(day.endOfEating <= fajr && fajr - day.endOfEating <= 2.minutes, "$date end of eating ${day.endOfEating}")
        }
    }

    /** An authority whose printed Fajr is also where its fast begins, and the code that computes it. */
    private class OwnDawn(val id: String, val point: GeoPoint, val zone: String, val country: String, val code: CalculationParameters)

    private val gmp = OwnDawn(
        "fr.gmp", GeoPoint(48.8418, 2.3556), "Europe/Paris", "FR",
        CalculationMethod.MOON_SIGHTING_COMMITTEE.parameters.copy(madhab = Madhab.SHAFI),
    )
    private val icci = OwnDawn(
        "ie.icci", GeoPoint(53.3498, -6.2603), "Europe/Dublin", "IE",
        CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters.copy(madhab = Madhab.SHAFI, highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE),
    )

    /** The authority's own Fajr on [date], as its code computes it. */
    private fun OwnDawn.fajr(date: LocalDate): Instant = Instant.fromEpochSeconds(
        PrayerTimes(Coordinates(point.lat, point.lon), DateComponents(date.year, date.month.ordinal + 1, date.day), code).fajr.epochSeconds,
    )

    private fun OwnDawn.resolution() = Registry.resolveEntry(Registry.byId(id)!!, Place(point.lat, point.lon, zone, country))

    /** The late limit the registry records for the end of eating, else class D's. */
    private fun OwnDawn.endLimit(): Duration =
        (lateLimitFor(TimedEvent.END_OF_EATING, null, Registry.byId(id)!!)?.minutes ?: 3).minutes

    @Test
    fun `the grande mosquee and the icci end the fast at their own fajr in june`() {
        // Ruling R80: each authority's own dawn as an end, not R39's 18° dawn (up to 114 and 86 min before):
        // in June within class D's 3 minutes (the Grande Mosquée's 4: its end also holds for everyone within
        // its table's reach, whose own dawn by its rule comes earlier to the south in summer), and within the
        // limit each records.
        for (own in listOf(gmp, icci)) {
            val r = own.resolution()
            val zone = TimeZone.of(own.zone)
            val most = if (own === gmp) 4.minutes else 3.minutes
            for (day in 1..30) {
                val date = LocalDate(2026, 6, day)
                val shown = DayPipeline.unended(r, date, zone)
                val theirs = own.fajr(date)
                assertTrue(shown.endOfEating <= shown.fajr, "${own.id} $date: end after the Fajr shown")
                assertTrue(shown.endOfEating <= theirs, "${own.id} $date: end ${shown.endOfEating} after its own Fajr $theirs")
                val before = theirs - shown.endOfEating
                assertTrue(before <= most && before <= own.endLimit(), "${own.id} $date: end $before before its Fajr")
            }
        }
    }

    @Test
    fun `their end of eating is never after their own fajr in any year of a leap cycle`() {
        // The end curves are by month and day; the sun on a month and day moves by up to about three
        // quarters of a day across a leap cycle, and the codes round to the minute.
        for (own in listOf(gmp, icci)) {
            val r = own.resolution()
            val zone = TimeZone.of(own.zone)
            var date = LocalDate(2027, 1, 1)
            var most = Duration.ZERO
            while (date.year <= 2030) {
                val end = DayPipeline.unended(r, date, zone).endOfEating
                val theirs = own.fajr(date)
                assertTrue(end <= theirs, "${own.id} $date: end $end after its own Fajr $theirs")
                // The Grande Mosquée's page carries its earlier method (an earlier Fajr) from October, and
                // the end follows it there and on the days either side of that change.
                val sameMethod = own !== gmp || date.month.ordinal + 1 in 2..8
                if (sameMethod && theirs - end > most) most = theirs - end
                date = date.plus(1, DateTimeUnit.DAY)
            }
            assertTrue(most <= 4.minutes, "${own.id}: up to $most before its own Fajr")
        }
    }

    @Test
    fun `away from paris the grande mosquee still ends the fast by its own rule`() {
        // Ruling R80's fix round: the Grande Mosquée's dawns carried south by the fraction of the night come
        // after the Moonsighting Committee's own Fajr in a southern January (its minutes take a larger share of
        // the night further south), so its end is also floored at that rule: at Propriano and Bonifacio, beyond
        // its table's reach (the edge), and inside it, where the end is read on the mosque's curve, at Étampes
        // (48 km south of the mosque; in winter) and 50 km east-south-east of it (in summer, when its own dawn
        // comes earlier to the south). France's cautious entry takes the same end most of the year.
        val places = listOf(GeoPoint(41.676, 8.903), GeoPoint(41.387, 9.159), GeoPoint(48.4347, 2.1615), GeoPoint(48.670, 2.987))
        for (point in places) {
            for (id in listOf("fr.gmp", "fr.cautious")) {
                val own = OwnDawn(id, point, "Europe/Paris", "FR", gmp.code)
                val r = own.resolution()
                val zone = TimeZone.of(own.zone)
                var date = LocalDate(2027, 1, 1)
                while (date.year <= 2030) {
                    val end = DayPipeline.unended(r, date, zone).endOfEating
                    val theirs = own.fajr(date)
                    assertTrue(end <= theirs, "$id at $point $date: end $end after the Moonsighting Committee's Fajr $theirs")
                    date = date.plus(1, DateTimeUnit.DAY)
                }
            }
        }
    }

    @Test
    fun `the moonsighting committee's other method ends the fast by its own fajr`() {
        // The Other method's fast began at its start curve's dawn, which is widened late over the neighbouring
        // days and takes the shorter night: in April and May from about 47° N it came after adhan2's own
        // Moonsighting Fajr (up to 62 s at London, 85 at Manchester and two minutes in Scotland, where a
        // seventh of the night takes 18°'s place). It now ends at the rule itself read as an end.
        val places = listOf(
            OwnDawn("other.moonsighting", GeoPoint(51.5074, -0.1278), "Europe/London", "GB", gmp.code),
            OwnDawn("other.moonsighting", GeoPoint(53.4808, -2.2426), "Europe/London", "GB", gmp.code),
            OwnDawn("other.moonsighting", GeoPoint(55.9533, -3.1883), "Europe/London", "GB", gmp.code),
            OwnDawn("other.moonsighting", GeoPoint(43.6532, -79.3832), "America/Toronto", "CA", gmp.code),
            OwnDawn("other.moonsighting", GeoPoint(-33.9249, 18.4241), "Africa/Johannesburg", "ZA", gmp.code),
        )
        for (own in places) {
            val r = own.resolution()
            val zone = TimeZone.of(own.zone)
            var date = LocalDate(2027, 1, 1)
            while (date.year <= 2030) {
                val end = DayPipeline.unended(r, date, zone).endOfEating
                val theirs = own.fajr(date)
                assertTrue(end <= theirs, "${own.point} $date: end $end after the Moonsighting Committee's Fajr $theirs")
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    private operator fun <T> List<T>.component6() = this[5]
    private operator fun <T> List<T>.component7() = this[6]

    @Test
    fun `the m25 holds london's edge towns and not the towns beyond it`() {
        fun follows(lat: Double, lon: Double) = Registry.automaticEntry(Place(lat, lon, "Europe/London", "GB")).id
        // Inside the motorway: Enfield, Staines, Rainham, Watford, Caterham, Uxbridge.
        for ((lat, lon) in listOf(51.652 to -0.081, 51.433 to -0.510, 51.523 to 0.200, 51.656 to -0.396, 51.282 to -0.078, 51.546 to -0.479)) {
            assertEquals("gb.london.lupt", follows(lat, lon), "($lat, $lon)")
        }
        // Beyond it: Potters Bar, Brentwood, Egham, Redhill.
        for ((lat, lon) in listOf(51.698 to -0.192, 51.620 to 0.305, 51.431 to -0.552, 51.240 to -0.170)) {
            assertFalse(follows(lat, lon) == "gb.london.lupt", "($lat, $lon)")
        }
    }
}
