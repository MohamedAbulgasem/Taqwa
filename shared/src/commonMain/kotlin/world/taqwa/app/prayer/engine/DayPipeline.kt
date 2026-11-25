package world.taqwa.app.prayer.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Resolution
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * One pipeline from a place to its day (ruling R34), the same for the gate and, wrapped with
 * settings, adjustments and a cache, for the app (Task 8's `PrayerEngine`). Pure: no state.
 *
 * 1. A single method is computed at the user's point ([Resolution.point]); a unit's reference point,
 *    or the method's own fixed point, travels as the method's fixed point, so each start is the
 *    later of the two and sunrise, sunset and the end of eating the earlier (ruling R15, spec §3.5).
 * 2. A cautious entry computes each member with its own method, Ramadan calendar and the lag dates,
 *    at the user's point, and combines them ([Cautious.combine]; the most-followed member is the
 *    lowest share rank; the real sunset at the user's point, [realSunset], is Maghrib's floor and says
 *    whether the sun sets there that day, ruling R155). Where another entry's tables ride beside the
 *    place ([Resolution.alongside], the owner's decision of 17 Oct 2026: ISNA's Montréal and Ottawa
 *    mosques beside Canada's cautious times outside those cities), each table's own day at its own
 *    point is computed too, and the day is the safe side of them all ([DayComputer.safeSide]: each
 *    start the later, each end the earlier).
 * 3. The real sun at the user's own point is the floor of the three starts it gives outright and the cap of the ends
 *    it gives ([onRealSun], rulings R180 and R265): Dhuhr is never before the minute after the real transit there,
 *    Asr (each school) never before the minute after the real shadow time, nor Maghrib before the minute after the real
 *    sunset; the sunrise shown and Fajr's end never after the real sunrise, nor Dhuhr's end after the real shadow-1
 *    Asr, each rounded down. Every day leaves through this step, whatever table, margin, fixed point or member gave it.
 * 4. The day is put in order on the safe side ([Invariants.repair]).
 * 5. Isha's end is the next day's end of eating, the next day computed the same way
 *    ([Ends.withNextDay]).
 *
 * A place that may be in either of two countries ([Resolution.sides], ruling R259) runs steps 1–4 for each
 * country's own resolution and holds the one day to the other ([BorderDay]: each start the later, each end the
 * earlier) before the floor and the order are taken again.
 *
 * Every member, and the single method, is computed in one Asr [school] (the one that leads, the
 * entry's own unless the caller chooses), so that `asr` and `asrOther` stay one school each.
 */
object DayPipeline {

    /** [entry] (by id, not by place) at [place] on [date]. */
    fun day(
        entry: RegistryEntry,
        place: Place,
        date: LocalDate,
        school: AsrSchool = entry.school,
        hijriOffsetDays: Int = 0,
    ): PrayerDay = day(Registry.resolveEntry(entry, place), date, TimeZone.of(place.zoneId), school, hijriOffsetDays)

    /** What [resolution] gives on [date] in [zone]. */
    fun day(
        resolution: Resolution,
        date: LocalDate,
        zone: TimeZone,
        school: AsrSchool = resolution.entry.school,
        hijriOffsetDays: Int = 0,
    ): PrayerDay {
        val today = unended(resolution, date, zone, school, hijriOffsetDays)
        val next = unended(resolution, date.plus(1, DateTimeUnit.DAY), zone, school, hijriOffsetDays)
        return Ends.withNextDay(today, next)
    }

    /**
     * Steps 1–4: the day in order, before Isha's end is known. A place that may be in either of two countries
     * ([Resolution.sides], ruling R259) takes each side's own day through steps 1–4 and holds the one to the other
     * ([BorderDay]), then the floor and the order again.
     */
    fun unended(
        resolution: Resolution,
        date: LocalDate,
        zone: TimeZone,
        school: AsrSchool = resolution.entry.school,
        hijriOffsetDays: Int = 0,
    ): PrayerDay {
        val day = if (resolution.sides.isEmpty()) {
            beforeRealSun(resolution, date, zone, school, hijriOffsetDays)
        } else {
            BorderDay.combine(resolution.sides.map { unended(it, date, zone, school, hijriOffsetDays) })
        }
        return Invariants.repair(onRealSun(day, resolution.point, date, zone, school))
    }

    /**
     * Steps 1–2 alone: what the tables, methods and members give before the real sun's floor ([onRealSun]) and the
     * repair. No day shown anywhere is this one; it is here for the sun-sanity pass to count what the floor costs
     * (the place-days it moves and by how much), and for nothing else.
     */
    fun beforeRealSun(
        resolution: Resolution,
        date: LocalDate,
        zone: TimeZone,
        school: AsrSchool = resolution.entry.school,
        hijriOffsetDays: Int = 0,
    ): PrayerDay {
        if (resolution.sides.isNotEmpty()) {
            return BorderDay.combine(resolution.sides.map { beforeRealSun(it, date, zone, school, hijriOffsetDays) })
        }
        val point = resolution.point
        val method = resolution.method
        val day = if (method != null) {
            DayComputer.compute(
                method = method,
                point = point,
                date = date,
                zone = zone,
                school = school,
                ramadan = Registry.ramadanCalendarFor(resolution.entry, hijriOffsetDays),
                lagDates = Registry.lagDates,
            )
        } else {
            val members = resolution.members
            val mostFollowed = members.indices.minBy { members[it].shareRank }
            Cautious.combine(members(resolution, date, zone, school, hijriOffsetDays), mostFollowed, realSunset(point, date, zone))
        }
        // Step 2b: another entry's tables riding beside the place (Resolution.alongside), each its own day at its own point.
        return resolution.alongside.filter { it.rides(date) }.fold(day) { shown, other ->
            DayComputer.safeSide(
                shown,
                DayComputer.compute(
                    method = other.method,
                    point = other.point,
                    date = date,
                    zone = zone,
                    school = school,
                    ramadan = Registry.ramadanCalendarFor(resolution.entry, hijriOffsetDays),
                    lagDates = Registry.lagDates,
                ),
            )
        }
    }

    /**
     * Ruling R180 (the owner's ruling of 12 Nov 2026, "sun-check floors: yes"): [day] with its Dhuhr no earlier than
     * the first whole minute after the real transit at [point] ([realTransit]), and its Maghrib no earlier than the
     * first whole minute after the real sunset there ([realSunset]; no floor on a day the sun does not set, which has
     * no sunset to wait for). [day] itself where both already hold, which is nearly every day of nearly every place.
     *
     * Why a floor here and not later margins: a table printed in whole minutes for one point of a city, and a method
     * whose margin was fitted to reproduce such a table, can sit a few seconds before the sun at another point (the
     * Egyptian Survey's Dhuhr 6 s before transit, the Palestinian table's town figures up to 65 s). A margin raised
     * to cover the worst point moves every place and every day of the entry; the floor moves only the place-days
     * where the minute shown is before the event, by the least whole minutes that put it after, and it holds for
     * every entry, unit, member and Settings choice there is or will be, since no day reaches the app, the site or
     * the gate except through [unended]. Later is never early: the gate's late limits are proved with the floor in.
     *
     * The floor is the exact sun at sea level with standard refraction, in whole minutes as every start is shown
     * ([Cautious.minuteAfter], as ruling R155's cautious Maghrib floor, which this is for every other day). "Real" is
     * this engine's own exact model ([SunModel.EXACT] on [world.taqwa.app.prayer.engine.astro.SolarMath], good to a
     * few seconds of time; `RealSunModelTest` holds it against a sun written out separately), with no guard seconds
     * on top: a start the floor decides stands up to a minute after the model's event and can stand within a second
     * of it, and the sunset is the sea-level one, which the air and the ground at a place move by more than the
     * model errs. A raised Maghrib is the sun's own minute: it is no longer set by a rule ([PrayerDay.setByRule]),
     * and a day-length hold ([PrayerDay.maghribHeld]) stands only while the Maghrib shown is still after the sun's,
     * as a fixed table's on its own sun (DayComputer's `onOwnSun`). An Isha the day declares not followed (DayComputer's
     * point 9) follows a Maghrib raised to or past it, the minute after (ruling R293: Diyanet's sun, read once a day,
     * sets up to a quarter of an hour before the real one where it grazes the horizon north of Luleå in June). What else
     * a raised start leaves out of order (an Asr or an undeclared Isha within the minute, an end no longer after its
     * start) the next step puts right, later ([Invariants.repair]), and the gate names the day.
     *
     * A cautious day's earliest member starts ([PrayerDay.earliestStart], what "Show where timetables differ" reads)
     * are held to the same floor (the review of 13 Nov 2026): a member's Dhuhr or Maghrib before the real sun at the
     * user's point is never said to have begun there, so the line shows no Dhuhr before the transit either.
     *
     * Ruling R265 (the same 12 Nov ruling, carried to the third start and to the ends by the second trust audit's G32,
     * G33 and G34 on 14 Nov 2026): Asr, in the school the day shows and in the other ([PrayerDay.asrOther]), is never
     * before the first whole minute after the real shadow time at [point] ([realAsr]; a cautious day's earliest Asr
     * with it), and the mirror for the ends: the sunrise shown and Fajr's end never after the real sunrise there
     * ([realSunrise]), and Dhuhr's end never after the real shadow-1 Asr, each rounded down to its minute, with no cap
     * on a day the sun does not rise (or never casts the shadow). The case is R180's: a whole-minute print for one
     * point, and a margin fitted to reproduce it, can stand after the sun has risen (JAKIM's PHG03, up to 2 min 19 s
     * at Maran) or before the shadow at another point (the Palestinian table's Asr, up to 2 min 33 s).
     */
    internal fun onRealSun(day: PrayerDay, point: GeoPoint, date: LocalDate, zone: TimeZone, school: AsrSchool): PrayerDay {
        val transitFloor = Cautious.minuteAfter(realTransit(point, date, zone))
        val sunsetFloor = realSunset(point, date, zone)?.let { Cautious.minuteAfter(it) }
        // Ruling R265 (G34): Asr in the school the day shows, and the other school's, never before the minute after
        // the real shadow time at the point; no floor where the sun never casts that shadow, nor on a day whose Asr a
        // high-latitude or clock rule sets (PrayerDay.setByRule, a polar day): there the authority's rule stands in for
        // the sun (IRN's Makkah hold in Tromsø's midnight sun prints a Hanafi Asr before the second shadow, with its own
        // sunset to end it), as no Maghrib waits for a sunset that does not come; the sun-sanity pass lists those days
        // with the day's own excuse.
        val asrBySun = Prayer.ASR !in day.setByRule && !day.polar
        val asrFloor = if (asrBySun) realAsr(point, date, zone, school)?.let { Cautious.minuteAfter(it) } else null
        val asrOtherFloor = if (asrBySun) realAsr(point, date, zone, school.other)?.let { Cautious.minuteAfter(it) } else null
        // Ruling R265 (G32, G33): the sunrise shown and Fajr's end never after the real sunrise at the point, nor
        // Dhuhr's end after the real shadow-1 Asr there, each rounded down from the unrounded moment; no cap where the
        // sun does not rise (or casts no shadow). The sunrise stays a minute after the Fajr shown: where the sun rises
        // within the Fajr shown's minute or before it, it stands on that next minute, never moving the Fajr earlier
        // through the repair, and the sun-sanity pass lists the day with its own excuse. Two kinds of day do this,
        // both far north: a Fajr an authority's rule puts at or after the sun, which the day declares it does not show
        // (ruling R82; Harstad's cautious day, up to 2 min 33 s after the real sunrise), and a Fajr a high-latitude
        // rule sets and the day shows, within its minute before the sun (IRN at Mo i Rana, up to 32 s). Where the first shadow comes
        // before the Dhuhr shown (a sun a degree above the horizon at noon, its shadow-1 within the transit's minute),
        // Dhuhr shows no end, as an Isha whose end does not follow it (ruling R26).
        val sunriseCap = realSunriseSeconds(point, date, zone)?.let(::minuteOf)?.let { maxOf(it, day.fajr + 1.minutes) }
        val dhuhrEndCap = realAsrSeconds(point, date, zone, AsrSchool.STANDARD)?.let(::minuteOf)
        val dhuhr = maxOf(day.dhuhr, transitFloor)
        val asr = if (asrFloor != null) maxOf(day.asr, asrFloor) else day.asr
        val asrOther = if (asrOtherFloor != null) maxOf(day.asrOther, asrOtherFloor) else day.asrOther
        val maghrib = if (sunsetFloor != null) maxOf(day.maghrib, sunsetFloor) else day.maghrib
        // Ruling R293: where the floor raises Maghrib to or past an Isha the day already declares not followed (the
        // authority's Isha before the Maghrib shown, DayComputer's point 9), Isha moves to the minute after the raised
        // Maghrib and Maghrib's end with it, as point 9 moves it: the authority's Isha is before the real sunset, and no
        // day in order can show it. An Isha not declared stays for the repair, and the gate names it. Only point 9's own
        // Isha is carried, the minute after Maghrib and ending Maghrib: a day joined from two (a safe side, a cautious or
        // paused day, a border day) declares what either side declared, and an Isha it took from the side that did not
        // declare it stays for the repair (ruling R295, the review of 17 Nov 2026).
        val carried = maghrib > day.maghrib && day.isha <= maghrib && Prayer.ISHA in day.notFollowed &&
            day.isha == day.maghrib + 1.minutes && day.ends[Prayer.MAGHRIB] == day.isha
        val isha = if (carried) maghrib + 1.minutes else day.isha
        val sunrise = if (sunriseCap != null) minOf(day.sunrise, sunriseCap) else day.sunrise
        val ends = day.ends.mapValues { (prayer, end) ->
            when {
                prayer == Prayer.FAJR && sunriseCap != null -> minOf(end, sunriseCap)
                prayer == Prayer.DHUHR && dhuhrEndCap != null -> minOf(end, dhuhrEndCap)
                else -> end
            }
        }.filter { (prayer, end) -> prayer != Prayer.DHUHR || end > dhuhr }.let { if (carried) it + (Prayer.MAGHRIB to isha) else it }
        // The Maghrib a held day's own sun gives is held to the real sun too, and the day is held only while the
        // Maghrib shown is still after it (as a fixed table's on its own sun, DayComputer's onOwnSun).
        val beforeHold = day.maghribBeforeHold?.let { own -> if (sunsetFloor != null) maxOf(own, sunsetFloor) else own }
        val held = day.maghribHeld && beforeHold != null && maghrib > beforeHold
        val earliestBelow = day.earliestStart.any { (prayer, start) ->
            (prayer == Prayer.DHUHR && start < transitFloor) ||
                (prayer == Prayer.ASR && asrFloor != null && start < asrFloor) ||
                (prayer == Prayer.MAGHRIB && sunsetFloor != null && start < sunsetFloor)
        }
        if (dhuhr == day.dhuhr && asr == day.asr && asrOther == day.asrOther && maghrib == day.maghrib && isha == day.isha &&
            sunrise == day.sunrise && ends == day.ends && beforeHold == day.maghribBeforeHold && held == day.maghribHeld &&
            !earliestBelow
        ) {
            return day
        }
        val raised = buildSet {
            if (dhuhr != day.dhuhr) add(Prayer.DHUHR)
            if (asr != day.asr) add(Prayer.ASR)
            if (maghrib != day.maghrib) add(Prayer.MAGHRIB)
            if (sunrise != day.sunrise) add(Prayer.SUNRISE)
        }
        return day.copy(
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            asrOther = asrOther,
            maghrib = maghrib,
            isha = isha,
            ends = ends,
            earliestStart = if (!earliestBelow) day.earliestStart else day.earliestStart.mapValues { (prayer, start) ->
                when (prayer) {
                    Prayer.DHUHR -> maxOf(start, transitFloor)
                    Prayer.ASR -> if (asrFloor != null) maxOf(start, asrFloor) else start
                    Prayer.MAGHRIB -> if (sunsetFloor != null) maxOf(start, sunsetFloor) else start
                    else -> start
                }
            },
            setByRule = day.setByRule - raised,
            maghribHeld = held,
            maghribBeforeHold = beforeHold.takeIf { held },
        )
    }

    /** [epochSeconds] rounded down to its whole minute: an end the sun gives, never after it ([PrayerDay]'s rounding). */
    private fun minuteOf(epochSeconds: Double): Instant = Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    /**
     * The real transit at [point] on [date] (ruling R180): the moment the exact sun crosses the meridian there,
     * whatever sun model or fixed point a method reads, unrounded. Every place has one every day. [zone] decides
     * which noon is [date]'s, as every method's sky does.
     */
    fun realTransit(point: GeoPoint, date: LocalDate, zone: TimeZone): Instant {
        val noonOffset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val seconds = SunClock(point.lat, point.lon, date, noonOffset, SunModel.EXACT).transit()
        return Instant.fromEpochMilliseconds((seconds * 1000.0).roundToLong())
    }

    /**
     * The real sunset at [point] on [date] (ruling R155): the moment the exact sun's upper limb sets, with
     * standard refraction ([REAL_HORIZON_DEG]), whatever sun model or horizon a member's method reads, unrounded;
     * null where the sun does not set there that day (it stays up, or never rises). [zone] decides which evening
     * is [date]'s, as every method's sky does (the sunset after that date's local noon, past midnight in Lapland's
     * June). A cautious Maghrib is never before the minute after it, and where it is null no member's Maghrib
     * counts as from the sun ([Cautious.combine]). Read as a start ([SunClock.startTime], the final review of 20 Nov
     * 2026): never before the sun's own moment, where the iteration stops short of it (0.4 s past the floor at 69.671° N,
     * 18.900° E on a night of May 2026), so no Maghrib is before the real sunset.
     */
    fun realSunset(point: GeoPoint, date: LocalDate, zone: TimeZone): Instant? {
        val noonOffset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val seconds = SunClock(point.lat, point.lon, date, noonOffset, SunModel.EXACT)
            .startTime(REAL_HORIZON_DEG, morning = false) ?: return null
        return Instant.fromEpochMilliseconds((seconds * 1000.0).roundToLong())
    }

    /**
     * The real sunrise at [point] on [date] (ruling R265): the moment the exact sun's upper limb rises, with standard
     * refraction ([REAL_HORIZON_DEG]), unrounded; null where the sun does not rise there that day. The sunrise shown
     * and Fajr's end are never after it ([onRealSun]). Read as an end ([SunClock.endTime], the final review of 20 Nov
     * 2026): never after the sun's own moment, where the iteration stops past it.
     */
    fun realSunrise(point: GeoPoint, date: LocalDate, zone: TimeZone): Instant? = realSunriseSeconds(point, date, zone)?.let(::instantOf)

    /** [realSunrise] in UTC epoch seconds, unrounded: a cap is rounded down from this, never from a rounded instant. */
    private fun realSunriseSeconds(point: GeoPoint, date: LocalDate, zone: TimeZone): Double? {
        val noonOffset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        return SunClock(point.lat, point.lon, date, noonOffset, SunModel.EXACT).endTime(REAL_HORIZON_DEG, morning = true)
    }

    /**
     * The real Asr at [point] on [date] in [school] (ruling R265): the moment a thing's shadow is its noon shadow plus
     * the school's lengths of itself ([AsrSchool.shadowFactor]), the noon shadow read at the exact sun's transit and the
     * moment found on the exact sun ([AsrModel.NOON_SHADOW] on [SunModel.EXACT]), the sun's geometric centre as every
     * authority's Asr reads it, unrounded; null where the sun never casts that shadow there that day: where it is not
     * above the horizon at noon (the polar night, where the shadow formula still answers with a sun below the horizon).
     */
    fun realAsr(point: GeoPoint, date: LocalDate, zone: TimeZone, school: AsrSchool): Instant? =
        realAsrSeconds(point, date, zone, school)?.let(::instantOf)

    /** [realAsr] in UTC epoch seconds, unrounded. */
    private fun realAsrSeconds(point: GeoPoint, date: LocalDate, zone: TimeZone, school: AsrSchool): Double? {
        val noonOffset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val clock = SunClock(point.lat, point.lon, date, noonOffset, SunModel.EXACT)
        val noonDeclination = SolarMath.sun(SolarMath.julianDay(clock.transit())).declinationDeg
        if (abs(point.lat - noonDeclination) >= 90.0) return null
        return clock.asr(school.shadowFactor, AsrModel.NOON_SHADOW)
    }

    private fun instantOf(epochSeconds: Double): Instant = Instant.fromEpochMilliseconds((epochSeconds * 1000.0).roundToLong())

    /**
     * Ruling R302 (the owner's (a′) of 17 Nov 2026): whether the night after [date] at [point] is too short for any day
     * in order, read from the real sun alone, never from a day's own times. Maghrib is never before the minute after
     * the real sunset (R180), Isha is at least the minute after Maghrib, and Isha has an end only where the next day's
     * end of eating is after it, which is at or before a Fajr before the sunrise shown, itself never after the real
     * sunrise rounded down (R90, R265): so where the minute after the real sunset, plus the two minutes of Isha and its
     * end, reaches the next real sunrise's minute, no day can show Isha's end, and the day declares it not shown. The gate
     * recomputes this at each row's point rather than trust a day, so a late Maghrib margin or an early end of eating
     * that cost a night its Isha end is still out of order. False where the sun does not set that evening or does not
     * rise the next morning (a polar day or night, where no such night is). The week before and after the midnight sun
     * at the edge of Tromsø's reach (IRN), where the sun is down for a minute to about four. The sun found to set is the
     * sun found to rise again after ([SunClock.crossing] asks both whether the sun is below the horizon at that night's
     * own lowest point, one and the same reading, settled or not: ruling R321 and the final review of 20 Nov 2026): a
     * sunset with no next sunrise (69.895, 18.852 on 16 May 2026, where the sun never set) left such a night neither
     * pulled nor declared. Both are read as the floor and the cap read them, the sunset as a start and the sunrise as an
     * end, so the check counts the night the day itself can have.
     */
    fun nightTooShortForIsha(point: GeoPoint, date: LocalDate, zone: TimeZone): Boolean {
        val sunset = realSunset(point, date, zone) ?: return false
        val sunrise = realSunriseSeconds(point, date.plus(1, DateTimeUnit.DAY), zone)?.let(::minuteOf) ?: return false
        return Cautious.minuteAfter(sunset) + 2.minutes >= sunrise
    }

    /** The sun's altitude at the real sunrise and sunset: its upper limb on the horizon, with standard refraction. */
    const val REAL_HORIZON_DEG = -0.8333

    /**
     * A cautious [resolution]'s members' own days, in its members' order, before they are combined
     * (step 2): each with its own method and Ramadan calendar, at the user's point, in one [school].
     * Empty for a single method.
     */
    fun members(
        resolution: Resolution,
        date: LocalDate,
        zone: TimeZone,
        school: AsrSchool = resolution.entry.school,
        hijriOffsetDays: Int = 0,
    ): List<PrayerDay> = resolution.members.map { member ->
        DayComputer.compute(
            method = member.method,
            point = resolution.point,
            date = date,
            zone = zone,
            school = school,
            ramadan = Registry.ramadanCalendarFor(Registry.byId(member.id) ?: resolution.entry, hijriOffsetDays),
            lagDates = Registry.lagDates,
        )
    }
}
