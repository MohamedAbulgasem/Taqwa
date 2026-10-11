package world.taqwa.app.prayer.engine.registry

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.method.AsrSchool
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Settings › Other › Moonsighting Committee across the method's range, at and between the 0.1° steps
 * its curves were once read on (T3 round of 11 Oct 2026), every day of the years named: from 40° to
 * 64° of latitude north and south every hundredth of a degree, nearer the equator every twentieth, in
 * 2027 and 2028 (a common year and the curves' leap reference year); from 64° to 66° every hundredth in
 * every year of the leap cycle (2025–2028), where on the edge of the midnight sun the night changes by
 * minutes a day; all on the prime meridian in UTC; from 40° to 66° every tenth at 150° E and 120° W,
 * where 12:00 UTC is far from the local noon, over the leap cycle; and in real zones over the leap
 * cycle: east of 180° in Chukotka's UTC+12 (173.23° W, 58° to 66° N every twentieth), where each civil
 * date's noon is the solar day before the UTC date's, on the Chatham Islands (176.5° W, UTC+12:45 with
 * summer time, 40° to 47° S every twentieth), and in Alaska's summer time (149.9° W, 55° to 66° N every
 * tenth). About 7.4 million place-days, about a minute on four threads. (The round's own probe ran
 * every hundredth from 66° S to 66° N on all four years, and at 25° E and 1.6° W in their own zones, the
 * same way.) Each day is held to [CommitteeRule], the Committee's rule with this core's own sun at the
 * place itself:
 * - no Fajr before the later of the Committee's own Fajr and adhan2's, no Isha before the Committee's,
 *   no sunrise after the sun's, as a table prints them (to the nearest minute), except a Fajr the day
 *   declares not followed (ruling R90: the rule's Fajr within a minute or two of sunrise, on the edge of
 *   the midnight sun). The nearest minute is the Committee's own rounding: at its 18 places off the
 *   midnight sun's edge (2026, the archive) its printed Fajr, sunrise and Isha lie within 30 s of the
 *   rule's exact moment with this core's sun on 85–89 % of 19,709 cells, as many either side, every
 *   Fajr and Isha within 75 s (a floor or a ceiling would put them on one side). A start shown may so
 *   lie up to 29 s before the rule's exact moment; against those tables none came before their printed
 *   minute;
 * - no end of eating after the Committee's own Fajr; nor after adhan2's, but on the first nights after
 *   the midnight sun, where the night has more than tripled overnight and adhan2, counting the night to
 *   the next sunrise, puts its Fajr before the middle of the night just ended (no dawn that night comes
 *   earlier): within [ADHAN2_EDGE_LATE] there;
 * - at 0°, where adhan2 dates its days as this engine does, and from 55° S northwards (south of it
 *   adhan2 has no seventh of the night), no Fajr or Isha before adhan2's own printed minute either.
 * On main before this round: Isha up to 24 min early from 54.95° to 55° N, Fajr up to 5 min early
 * between the steps north of 64° N and 8 min south of 54° S, the end up to 6 min late from 65.3° N.
 * Before the T3 review's fix, in Chukotka's UTC+12 east of 180°: Fajr up to 2 min early on 1,095
 * place-days from July to September, from 58.45° N up, and the end up to 2 min after the Committee's
 * Fajr on 932, April to June (TwilightCurves.CivilNoons).
 */
class MoonsightingSweepTest {
    private val entry = Registry.byId("other.moonsighting")!!
    private val adhan2 = CalculationMethod.MOON_SIGHTING_COMMITTEE.parameters.copy(madhab = Madhab.SHAFI)

    private class Sweep(val lats: List<Double>, val lon: Double, val zone: String, val years: IntRange)

    private class Tally {
        var days = 0
        var declared = 0
        var adhan2Edge = 0
        val failures = mutableListOf<String>()
        fun add(other: Tally) {
            days += other.days
            declared += other.declared
            adhan2Edge += other.adhan2Edge
            failures += other.failures
        }
    }

    private fun steps(from: Double, to: Double, step: Double): List<Double> {
        val n = Math.round((to - from) / step).toInt()
        return (0..n).map { Math.round((from + it * step) * 1000.0) / 1000.0 }
    }

    private fun sweep(lat: Double, lon: Double, zoneId: String, years: IntRange): Tally {
        val tally = Tally()
        val r = Registry.resolveEntry(entry, Place(lat, lon, zoneId, if (lat >= 0) "GB" else "AR"))
        val zone = TimeZone.of(zoneId)
        // From 55 S southwards adhan2 has no seventh of the night, and the Committee's own Fajr is later and its Isha
        // earlier than adhan2's there: the Committee's is followed.
        val adhan2Rule = lat > -SEVENTH_FROM
        val withAdhan2 = lon == 0.0 && adhan2Rule
        fun fail(date: LocalDate, what: String, seconds: Long) {
            if (tally.failures.size < 5) tally.failures += "$lat,$lon $date $what by $seconds s"
        }
        var date = LocalDate(years.first, 1, 1)
        while (date.year <= years.last) {
            val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
            val rule = CommitteeRule.at(lat, lon, date, offset)
            if (rule != null) {
                tally.days++
                val day = DayPipeline.unended(r, date, zone, AsrSchool.STANDARD)
                val fajr = day.fajr.epochSeconds
                if (fajr < CommitteeRule.printed(rule.laterFajr)) {
                    // Ruling R90: a Fajr shown before the rule's only where the day declares it, the rule's own
                    // Fajr so near sunrise that no day in order can show it.
                    if (Prayer.FAJR in day.notFollowed && rule.sunrise - rule.laterFajr < 3 * 60) {
                        tally.declared++
                    } else {
                        fail(date, "Fajr early", CommitteeRule.printed(rule.laterFajr) - fajr)
                    }
                }
                val isha = day.isha.epochSeconds
                if (Prayer.ISHA !in day.notFollowed && isha < CommitteeRule.printed(rule.isha)) fail(date, "Isha early", CommitteeRule.printed(rule.isha) - isha)
                val sunrise = day.sunrise.epochSeconds
                if (sunrise > CommitteeRule.printed(rule.sunrise)) fail(date, "sunrise late", sunrise - CommitteeRule.printed(rule.sunrise))
                val end = day.endOfEating.epochSeconds
                if (end > CommitteeRule.printed(rule.committeeFajr)) fail(date, "end after the Committee's Fajr", end - CommitteeRule.printed(rule.committeeFajr))
                val adhan2Late = end - CommitteeRule.printed(rule.adhan2Fajr)
                if (adhan2Rule && adhan2Late > 0) {
                    val firstNights = rule.nightAfter > 3 * rule.nightBefore && rule.sunrise - rule.adhan2Fajr > rule.nightBefore / 2
                    if (firstNights && adhan2Late <= ADHAN2_EDGE_LATE) tally.adhan2Edge++ else fail(date, "end after adhan2's Fajr", adhan2Late)
                }
                if (withAdhan2) {
                    val theirs = try {
                        PrayerTimes(Coordinates(lat, lon), DateComponents(date.year, date.month.ordinal + 1, date.day), adhan2)
                    } catch (e: IllegalStateException) {
                        null
                    }
                    if (theirs != null) {
                        if (Prayer.FAJR !in day.notFollowed && fajr < theirs.fajr.epochSeconds) fail(date, "Fajr before adhan2's", theirs.fajr.epochSeconds - fajr)
                        if (Prayer.ISHA !in day.notFollowed && isha < theirs.isha.epochSeconds) fail(date, "Isha before adhan2's", theirs.isha.epochSeconds - isha)
                    }
                }
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return tally
    }

    @Test
    fun `the moonsighting committee's other method is never early anywhere in its range`() {
        fun bothHemispheres(lats: List<Double>) = lats + lats.map { -it }
        val sweeps = listOf(
            // A common year and a leap one, every hundredth of a degree where the curves bend fastest.
            Sweep(bothHemispheres(steps(40.0, 63.99, 0.01)), 0.0, "UTC", 2027..2028),
            Sweep(steps(-39.95, 39.95, 0.05), 0.0, "UTC", 2027..2028),
            // The edge of the midnight sun, where a curve must hold every year of the leap cycle.
            Sweep(bothHemispheres(steps(64.0, 66.0, 0.01)), 0.0, "UTC", 2025..2028),
            // Far from the prime meridian, where 12:00 UTC is far from the local noon.
            Sweep(bothHemispheres(steps(40.0, 66.0, 0.1)), 150.0, "UTC+10:00", 2025..2028),
            Sweep(bothHemispheres(steps(40.0, 66.0, 0.1)), -120.0, "UTC-08:00", 2025..2028),
            // Real zones. East of 180 in Chukotka's UTC+12 each civil date's noon is the solar day before the UTC date's
            // (Provideniya, Lavrentiya, Lorino, Sireniki), and on the Chatham Islands, a quarter of an hour further, too,
            // with summer time; Alaska's summer time on the other side of the line.
            Sweep(steps(58.0, 66.0, 0.05), -173.23, "Asia/Anadyr", 2025..2028),
            Sweep(steps(-47.0, -40.0, 0.05), -176.5, "Pacific/Chatham", 2025..2028),
            Sweep(steps(55.0, 66.0, 0.1), -149.9, "America/Anchorage", 2025..2028),
        )
        val pool = Executors.newFixedThreadPool(THREADS)
        val total = Tally()
        try {
            val futures = sweeps.flatMap { s -> s.lats.map { lat -> pool.submit<Tally> { sweep(lat, s.lon, s.zone, s.years) } } }
            for (f in futures) total.add(f.get())
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.MINUTES)
        }
        println("MoonsightingSweepTest: ${total.days} place-days, ${total.declared} Fajr declared before the rule's (R90), ${total.adhan2Edge} ends after adhan2's Fajr on the first nights after the midnight sun")
        assertTrue(total.days > 6_000_000, "only ${total.days} place-days")
        assertTrue(total.failures.isEmpty(), "${total.failures.size} places fail:\n" + total.failures.take(40).joinToString("\n"))
    }

    private companion object {
        const val THREADS = 4
        const val SEVENTH_FROM = 55.0

        /** The most an end may come after adhan2's Fajr on the first nights after the midnight sun (none after the Committee's). */
        const val ADHAN2_EDGE_LATE = 4 * 60L
    }
}
