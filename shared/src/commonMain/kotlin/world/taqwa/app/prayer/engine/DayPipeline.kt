package world.taqwa.app.prayer.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Resolution

/**
 * One pipeline from a place to its day (ruling R34), the same for the gate and, wrapped with
 * settings, adjustments and a cache, for the app (Task 8's `PrayerEngine`). Pure: no state.
 *
 * 1. A single method is computed at the user's point ([Resolution.point]); a unit's reference point,
 *    or the method's own fixed point, travels as the method's fixed point, so each start is the
 *    later of the two and sunrise, sunset and the end of eating the earlier (ruling R15, spec §3.5).
 * 2. A cautious entry computes each member with its own method, Ramadan calendar and the lag dates,
 *    at the user's point, and combines them ([Cautious.combine]; the most-followed member is the
 *    lowest share rank).
 * 3. The day is put in order on the safe side ([Invariants.repair]).
 * 4. Isha's end is the next day's end of eating, the next day computed the same way
 *    ([Ends.withNextDay]).
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

    /** Steps 1–3: the day in order, before Isha's end is known. */
    fun unended(
        resolution: Resolution,
        date: LocalDate,
        zone: TimeZone,
        school: AsrSchool = resolution.entry.school,
        hijriOffsetDays: Int = 0,
    ): PrayerDay {
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
            Cautious.combine(members(resolution, date, zone, school, hijriOffsetDays), mostFollowed)
        }
        return Invariants.repair(day)
    }

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
