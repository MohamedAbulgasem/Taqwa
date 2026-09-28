package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.day.Sky
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.authorities.Europe
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Ruling R90: beyond the Tromsø circle, and wherever else an authority's sunrise precaution comes
 * before its own dawn at the polar edge, no repair pulls Fajr before a member's own real dawn. Every
 * day of 2026 is walked under Automatic at nine Arctic places (Norway's cautious times, the Nordic
 * cautious times, DUM RF's Murmansk) and at four points just beyond the Tromsø unit's reach, where
 * Automatic is Norway's cautious times again. Structural only (ruling R69): every figure is the
 * engine's own or the sun's at the place.
 *
 * What holds on every day: the shown Fajr is never before any member's own Fajr, nor before any
 * member's own real dawn at the place (where the dawn occurs and Fajr is not declared not
 * followed); the sunrise shown is never after the sun's own under the sun of the member whose Fajr
 * is shown; the end of eating is never after the Fajr shown; the day holds its order without a
 * repair for Fajr or the end of eating; whole minutes. What remains, and is printed: the days the
 * sun itself rises at or before every member's Fajr, where Fajr is the minute before the sun's
 * sunrise and declared not followed (R90 (b)); and the days the sunrise shown, the shown-Fajr
 * member's own (R90 (a)), is after the sunrise another member's sun model gives, in the week the
 * sun stops setting, when one model has it grazing the horizon and the other not.
 */
class ArcticDawnWalkTest {
    private class Spot(val name: String, val place: Place) {
        val zone: TimeZone = TimeZone.of(place.zoneId)
        val point: GeoPoint = GeoPoint(place.lat, place.lon)
    }

    /** One walked day: the day before repair, the day shown, and each member's own method, day and sky. */
    private class Walked(val date: LocalDate, val raw: PrayerDay, val shown: PrayerDay, val members: List<Member>)

    private class Member(val id: String, val method: TimetableMethod, val day: PrayerDay, val sky: Sky)

    private fun startOf(epochSeconds: Double) = Instant.fromEpochSeconds(ceil(epochSeconds / 60.0).toLong() * 60)
    private fun endOf(epochSeconds: Double) = Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    /** The member's own Fajr dawn at the sky's point on [date] (its angle that day, with its bias), or null where the sun does not reach it. */
    private fun realDawn(method: TimetableMethod, sky: Sky, date: LocalDate): Double? {
        val angle = (method.fajrAngleByDayOfYear?.get(curveSlot(date)) ?: method.fajrAngle) + method.twilightDipDeg
        val bias = method.fajrDeclinationBias?.at(date.dayOfYear, LocalDate(date.year, 12, 31).dayOfYear) ?: 0.0
        return sky.altitudeTime(date, -angle, morning = true, biasDeg = bias)
    }

    private fun walk(spot: Spot): List<Walked> {
        val resolution = Registry.resolve(spot.place)
        val zone = spot.zone
        val single = resolution.method
        val memberSkies = if (single != null) {
            listOf(resolution.entry.id to Sky(single, spot.point, zone))
        } else {
            resolution.members.map { it.id to Sky(it.method, spot.point, zone) }
        }
        val mostFollowed = resolution.members.indices.minByOrNull { resolution.members[it].shareRank } ?: 0
        return generateSequence(LocalDate(2026, 1, 1)) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it.year == 2026 }
            .map { date ->
                val members: List<Member>
                val raw: PrayerDay
                if (single != null) {
                    val day = DayComputer.compute(
                        single, spot.point, date, zone, resolution.entry.school,
                        Registry.ramadanCalendarFor(resolution.entry, 0), Registry.lagDates,
                    )
                    members = listOf(Member(resolution.entry.id, single, day, memberSkies.single().second))
                    raw = day
                } else {
                    val days = DayPipeline.members(resolution, date, zone)
                    members = resolution.members.zip(days).mapIndexed { i, (member, day) -> Member(member.id, member.method, day, memberSkies[i].second) }
                    raw = Cautious.combine(days, mostFollowed)
                }
                Walked(date, raw, Invariants.repair(raw), members)
            }
            .toList()
    }

    @Test
    fun `no fajr before a member's own dawn across the arctic edge in 2026`() {
        val failures = mutableListOf<String>()
        val summary = StringBuilder()
        var totalDeclared = 0
        for (spot in spots) {
            val walked = walk(spot)
            val zone = spot.zone
            fun Instant.clock() = toLocalDateTime(zone).let { "${it.date.day}/${it.time}" }
            var rawBroken = 0
            var fajrBroken = 0
            var declared = 0
            var beforeDawnDays = 0
            var worstBeforeDawn = 0L
            var remainsWorst = 0L
            var sunriseAfterOtherSun = 0
            var worstSunriseAfterOtherSun = 0L
            for (w in walked) {
                val where = "${spot.name} ${w.date}"
                fun fail(what: String) {
                    failures += "$where: $what"
                }
                val shown = w.shown
                if (!Invariants.holds(w.raw)) rawBroken++
                if (w.raw.fajr >= w.raw.sunrise || w.raw.endOfEating > w.raw.fajr) {
                    fajrBroken++
                    fail("repaired for Fajr: fajr ${w.raw.fajr.clock()} sunrise ${w.raw.sunrise.clock()} end of eating ${w.raw.endOfEating.clock()}")
                }
                if (!Invariants.holds(shown)) fail("does not hold after repair")
                listOfNotNull(shown.fajr, shown.sunrise, shown.dhuhr, shown.asr, shown.asrOther, shown.maghrib, shown.isha, shown.sunset, shown.endOfEating, shown.imsak)
                    .plus(shown.ends.values).forEach { if (it.epochSeconds % 60 != 0L) fail("$it is not a whole minute") }
                if (shown.endOfEating > shown.fajr) fail("end of eating ${shown.endOfEating.clock()} after fajr ${shown.fajr.clock()}")
                val fajrDeclared = Prayer.FAJR in shown.notFollowed
                if (fajrDeclared) declared++
                var dayBeforeDawn = 0L
                for (m in w.members) {
                    if (shown.fajr < m.day.fajr) fail("fajr ${shown.fajr.clock()} before ${m.id}'s own ${m.day.fajr.clock()}")
                    val dawn = realDawn(m.method, m.sky, w.date)?.let(::startOf) ?: continue
                    val before = (dawn - shown.fajr).inWholeMinutes
                    if (before > 0) dayBeforeDawn = maxOf(dayBeforeDawn, before)
                    if (before > 0 && !fajrDeclared) fail("fajr ${shown.fajr.clock()} $before min before ${m.id}'s real dawn ${dawn.clock()}")
                }
                if (dayBeforeDawn > 0) {
                    beforeDawnDays++
                    worstBeforeDawn = maxOf(worstBeforeDawn, dayBeforeDawn)
                    if (fajrDeclared) remainsWorst = maxOf(remainsWorst, dayBeforeDawn)
                }
                // The sunrise shown is never after the sun's own at the place under the sun of the
                // member whose Fajr is shown (R90 (a): its own sunrise). Where another member's sun
                // model rises earlier (the week the sun stops setting), that is recorded.
                val shownFajrMembers = w.members.filter { it.day.fajr == shown.fajr }
                shownFajrMembers.mapNotNull { it.sky.sunrise(w.date) }.minOrNull()?.let { real ->
                    if (shown.sunrise > endOf(real)) fail("sunrise ${shown.sunrise.clock()} after the sun's ${endOf(real).clock()}")
                }
                w.members.mapNotNull { it.sky.sunrise(w.date) }.minOrNull()?.let { real ->
                    val late = (shown.sunrise - endOf(real)).inWholeMinutes
                    if (late > 0) {
                        sunriseAfterOtherSun++
                        worstSunriseAfterOtherSun = maxOf(worstSunriseAfterOtherSun, late)
                    }
                }
            }
            // The walk is the pipeline's own day: tied on the first of every month.
            val resolution = Registry.resolve(spot.place)
            for (w in walked.filter { it.date.day == 1 }) {
                assertEquals(DayPipeline.unended(resolution, w.date, zone), w.shown, "${spot.name} ${w.date}")
            }
            totalDeclared += declared
            summary.appendLine(
                "${spot.name} (${resolution.entry.id}): ${walked.size} days, $rawBroken repaired before ($fajrBroken for Fajr), " +
                    "$beforeDawnDays days with Fajr before a member's real dawn (worst $worstBeforeDawn min), " +
                    "$declared declared not followed (worst $remainsWorst min before a member's dawn), " +
                    "$sunriseAfterOtherSun days with the sunrise after another member's sun (worst $worstSunriseAfterOtherSun min)",
            )
        }
        println("Arctic dawn walk 2026:\n$summary")
        assertTrue(failures.isEmpty(), "${failures.size} failures:\n" + failures.take(40).joinToString("\n"))
        assertTrue(totalDeclared > 0, "the sun rises before every member's Fajr on some day: the days that remain are declared")
    }

    @Test
    fun `the reach points follow norway's cautious times and the cities their own entries`() {
        for (spot in spots) {
            val id = Registry.resolve(spot.place).entry.id
            val expected = when (spot.place.countryCode) {
                "NO" -> "no.cautious"
                "SE", "FI" -> "se.cautious"
                else -> "ru.dumrf"
            }
            assertEquals(expected, id, spot.name)
        }
    }

    private companion object {
        val tromso = GeoPoint(69.6489, 18.95508)

        /** Just beyond the Tromsø unit's reach, due north, south, east and west: Automatic leaves IRN's calendar there. */
        val reachPoints: List<Spot> = run {
            val beyond = Europe.irnUnits.unit("no.irn.tromso").radiusKm + 1.0
            val kmPerLon = 111.2 * cos(tromso.lat * PI / 180)
            listOf("north" to (beyond to 0.0), "south" to (-beyond to 0.0), "east" to (0.0 to beyond), "west" to (0.0 to -beyond))
                .map { (name, km) ->
                    Spot("Tromsø reach $name", Place(tromso.lat + km.first / 111.2, tromso.lon + km.second / kmPerLon, "Europe/Oslo", "NO"))
                }
        }

        val spots: List<Spot> = listOf(
            Spot("Finnsnes", Place(69.2297, 17.9812, "Europe/Oslo", "NO")),
            Spot("Narvik", Place(68.4385, 17.4272, "Europe/Oslo", "NO")),
            Spot("Bodø", Place(67.2804, 14.4049, "Europe/Oslo", "NO")),
            Spot("Alta", Place(69.9689, 23.2716, "Europe/Oslo", "NO")),
            Spot("Vadsø", Place(70.0744, 29.7487, "Europe/Oslo", "NO")),
            Spot("Kirkenes", Place(69.7271, 30.0450, "Europe/Oslo", "NO")),
            Spot("Kiruna", Place(67.8558, 20.2253, "Europe/Stockholm", "SE")),
            Spot("Rovaniemi", Place(66.5039, 25.7294, "Europe/Helsinki", "FI")),
            Spot("Murmansk", Place(68.9585, 33.0827, "Europe/Moscow", "RU")),
        ) + reachPoints
    }
}
