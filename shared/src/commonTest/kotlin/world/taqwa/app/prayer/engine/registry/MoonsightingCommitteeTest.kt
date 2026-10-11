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
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Settings › Other › Moonsighting Committee (`other.moonsighting`) against the Committee's own rule
 * ([CommitteeRule], computed with this core's sun at the place itself) and against adhan2's code, the
 * old engine's (T3 round of 11 Oct 2026). Newcastle upon Tyne, at 54.97° N, read its curves at 55.0°,
 * where a seventh of the night replaces the seasonal rule: its Isha came up to 24 min before the
 * Committee's own printed Isha from May to July 2026 (moonsighting.com's table for the city, held in
 * the archive) and its Fajr up to 68 min after it. The JVM sweep (`MoonsightingSweepTest`) holds every
 * latitude to the same rule; these are the places it was found at.
 */
class MoonsightingCommitteeTest {
    private val committee = Registry.byId("other.moonsighting")!!
    private val adhan2 = CalculationMethod.MOON_SIGHTING_COMMITTEE.parameters.copy(madhab = Madhab.SHAFI)

    private class Spot(val lat: Double, val lon: Double, val zone: String, val country: String)

    private fun days(spot: Spot, from: LocalDate, to: LocalDate): List<Pair<PrayerDay, CommitteeRule?>> {
        val r = Registry.resolveEntry(committee, Place(spot.lat, spot.lon, spot.zone, spot.country))
        val zone = TimeZone.of(spot.zone)
        val out = mutableListOf<Pair<PrayerDay, CommitteeRule?>>()
        var date = from
        while (date <= to) {
            val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
            out += DayPipeline.unended(r, date, zone, AsrSchool.STANDARD) to CommitteeRule.at(spot.lat, spot.lon, date, offset)
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return out
    }

    private fun adhan2(spot: Spot, date: LocalDate) =
        PrayerTimes(Coordinates(spot.lat, spot.lon), DateComponents(date.year, date.month.ordinal + 1, date.day), adhan2)

    @Test
    fun `newcastle's isha is never before the committee's and its fajr no longer waits for a seventh of the night`() {
        val newcastle = Spot(54.97328, -1.61396, "Europe/London", "GB")
        for ((day, rule) in days(newcastle, LocalDate(2026, 1, 1), LocalDate(2026, 12, 31))) {
            rule ?: continue
            val theirs = adhan2(newcastle, day.date)
            val isha = day.isha.epochSeconds
            val fajr = day.fajr.epochSeconds
            assertTrue(isha >= CommitteeRule.printed(rule.isha) && isha >= theirs.isha.epochSeconds, "${day.date}: Isha before the Committee's")
            assertTrue(isha - CommitteeRule.printed(rule.isha) <= 3 * 60, "${day.date}: Isha more than 3 min late")
            assertTrue(fajr >= CommitteeRule.printed(rule.laterFajr) && fajr >= theirs.fajr.epochSeconds, "${day.date}: Fajr before the Committee's")
            assertTrue(fajr - CommitteeRule.printed(rule.laterFajr) <= 5 * 60, "${day.date}: Fajr more than 5 min late")
            assertTrue(day.endOfEating.epochSeconds <= CommitteeRule.printed(rule.earlierFajr), "${day.date}: the fast begins after the Committee's Fajr")
        }
    }

    @Test
    fun `the seventh of the night begins at the place's own latitude of fifty-five degrees`() {
        // Midsummer: the seasonal rule's Isha is about 81 min after sunset at 55 N, a seventh of the night about 57.
        val date = LocalDate(2026, 6, 21)
        fun ishaAfterSunset(lat: Double): Long {
            val (day, _) = days(Spot(lat, -1.6, "Europe/London", "GB"), date, date).single()
            return (day.isha - day.sunset).inWholeMinutes
        }
        for (lat in listOf(54.951, 54.973, 54.999)) assertTrue(ishaAfterSunset(lat) >= 80, "$lat: ${ishaAfterSunset(lat)} min")
        for (lat in listOf(55.0, 55.018, 55.049)) assertTrue(ishaAfterSunset(lat) <= 60, "$lat: ${ishaAfterSunset(lat)} min")
    }

    @Test
    fun `south of fifty-five degrees south the committee's own seventh is followed`() {
        // The Committee's own tables switch to a seventh of the night from 55 S as from 55 N; adhan2's preset
        // does not. In the southern midsummer at Isla Hornos its Fajr is then over an hour after adhan2's
        // seasonal one and its Isha about half an hour before adhan2's.
        val hornos = Spot(-55.97, -67.27, "America/Punta_Arenas", "CL")
        for ((day, rule) in days(hornos, LocalDate(2026, 12, 15), LocalDate(2026, 12, 27))) {
            rule ?: continue
            val theirs = adhan2(hornos, day.date)
            assertTrue(day.fajr.epochSeconds >= CommitteeRule.printed(rule.laterFajr), "${day.date}: Fajr before the Committee's")
            assertTrue(day.fajr.epochSeconds - theirs.fajr.epochSeconds > 60 * 60, "${day.date}: Fajr within an hour of adhan2's")
            assertTrue(day.isha.epochSeconds >= CommitteeRule.printed(rule.isha), "${day.date}: Isha before the Committee's")
            assertTrue(theirs.isha.epochSeconds - day.isha.epochSeconds > 20 * 60, "${day.date}: Isha not the Committee's seventh")
            assertTrue(day.endOfEating.epochSeconds <= CommitteeRule.printed(rule.committeeFajr), "${day.date}: end after the Committee's Fajr")
        }
    }

    @Test
    fun `east of the date line in chukotka the fajr and the end of eating keep to the committee's own`() {
        // Provideniya, at 173.2 W, keeps Chukotka's UTC+12 (Asia/Anadyr): each civil date's noon there is the solar day
        // before the UTC date's, so curves read at that UTC date's own noon took the sun a day late. Its Fajr came up
        // to 2 min before the Committee's own from July to September and its end of eating up to 2 min after it from
        // April to June (the T3 review of 11 Oct 2026).
        val provideniya = Spot(64.4234, -173.2258, "Asia/Anadyr", "RU")
        for ((day, rule) in days(provideniya, LocalDate(2026, 4, 1), LocalDate(2027, 9, 30))) {
            rule ?: continue
            if (day.fajr.epochSeconds < CommitteeRule.printed(rule.laterFajr)) {
                // Ruling R90: shown before the rule's only where the day declares it.
                assertTrue(Prayer.FAJR in day.notFollowed && rule.sunrise - rule.laterFajr < 3 * 60, "${day.date}: Fajr early")
            }
            if (Prayer.ISHA !in day.notFollowed) {
                assertTrue(day.isha.epochSeconds >= CommitteeRule.printed(rule.isha), "${day.date}: Isha early")
            }
            assertTrue(day.endOfEating.epochSeconds <= CommitteeRule.printed(rule.committeeFajr), "${day.date}: end after the Committee's Fajr")
        }
    }

    @Test
    fun `on the edge of the midnight sun the fast still begins by the committee's fajr`() {
        // At Luleå and Kemi the whole June night lies within a quarter of a degree of the sun's lowest point:
        // the end, capped a quarter of a degree short of it, came after sunrise and the fast began at the Fajr
        // shown, up to 6 min after the Committee's own printed Fajr. Kemi, 65.74 N, has nights of minutes on
        // either side of a few days without one.
        val places = listOf(Spot(65.5848, 22.1547, "Europe/Stockholm", "SE"), Spot(65.73641, 24.56371, "Europe/Helsinki", "FI"))
        for (spot in places) {
            val r = Registry.resolveEntry(committee, Place(spot.lat, spot.lon, spot.zone, spot.country))
            assertIs<EndOfEating.DawnOrMiddle>(r.method!!.endOfEating)
            for ((day, rule) in days(spot, LocalDate(2026, 5, 1), LocalDate(2027, 8, 31))) {
                rule ?: continue
                assertTrue(day.endOfEating.epochSeconds <= CommitteeRule.printed(rule.committeeFajr), "${spot.lat} ${day.date}: end after the Committee's Fajr")
                if (day.fajr.epochSeconds < CommitteeRule.printed(rule.laterFajr)) {
                    // Ruling R90: shown before the rule's only where the day declares it, the rule's own Fajr so near
                    // sunrise that no day in order can show it.
                    assertTrue(Prayer.FAJR in day.notFollowed && rule.sunrise - rule.laterFajr < 3 * 60, "${spot.lat} ${day.date}: Fajr early")
                }
                if (Prayer.ISHA !in day.notFollowed) {
                    assertTrue(day.isha.epochSeconds >= CommitteeRule.printed(rule.isha), "${spot.lat} ${day.date}: Isha early")
                }
            }
        }
    }
}
