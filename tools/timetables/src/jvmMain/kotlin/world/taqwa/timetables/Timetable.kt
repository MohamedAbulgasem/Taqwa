package world.taqwa.timetables

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.hijri.HijriDate
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.time.Instant

data class TimetableDay(
    val date: LocalDate,
    val times: Map<Prayer, Instant>,
    val hijri: HijriDate,
    val friday: Boolean,
    /** The zone's offset at that day's Dhuhr: what the local clock reads through the day. */
    val utcOffsetSeconds: Int,
    /** The other school's Asr (the page's own school is in [times]). */
    val asrOther: Instant,
    /** When the fast begins, never after Fajr. */
    val endOfEating: Instant,
    /** The authority's printed imsak, where it has one. */
    val imsak: Instant?,
    /** The astronomical sunset, rounded down. */
    val sunset: Instant,
    /** The prayers a high-latitude rule set rather than the sun's angle. */
    val setByRule: Set<Prayer>,
    /** The sun neither rose nor set: some times follow the nearest latitude where it does. */
    val polar: Boolean,
    /** Isha on its timetable's Ramadan interval (Umm al-Qura's 120 minutes after Maghrib). */
    val ramadanIsha: Boolean = false,
    /**
     * A cautious place's members' own days, in the resolution's order, seven instants each: the six
     * prayers in [Prayer] order, then the end of eating. Empty for a single method.
     */
    val members: List<List<Instant>> = emptyList(),
    /** A member's Maghrib is after the one shown: the Maghrib cap decided it (spec §3.6). */
    val capped: Boolean = false,
)

data class TimetableMonth(val year: Int, val month: Int, val days: List<TimetableDay>)

/** Maghrib's place in a member's seven instants ([TimetableDay.members]): the six prayers in [Prayer] order, then the end of eating. */
internal const val MEMBER_MAGHRIB = 4

/**
 * A city's prayer times, exactly as the app computes them for a user there who has not changed a
 * setting (unless [settings] says otherwise): the timetable Automatic resolves to, the place's own
 * Asr school, no adjustments and the tabular Hijri date. Everything comes from the app's engine;
 * the page adds nothing of its own.
 */
class Timetable(
    private val engine: PrayerTimesEngine = PrayerTimesEngine(),
    private val settings: PrayerSettings = PrayerSettings(),
) {

    fun location(city: City) = GeoLocation(
        latitude = city.latitude,
        longitude = city.longitude,
        timeZoneId = city.timeZone,
        cityName = city.name("en"),
        countryCode = city.countryCode,
        cityId = city.id,
        region = city.admin1,
    )

    /** The engine's whole answer for [city] on [date]: its times and the timetable behind them. */
    fun source(city: City, date: LocalDate): EngineDay = engine.dayFor(location(city), date, settings)

    fun day(city: City, date: LocalDate): TimetableDay {
        val source = source(city, date)
        val day = source.day
        val zone = TimeZone.of(city.timeZone)
        val times = mapOf(
            Prayer.FAJR to day.fajr, Prayer.SUNRISE to day.sunrise, Prayer.DHUHR to day.dhuhr,
            Prayer.ASR to day.asr, Prayer.MAGHRIB to day.maghrib, Prayer.ISHA to day.isha,
        )
        // A cautious place's members, each computed with its own method at the city's point in the
        // page's school, as the app's About screen lists them (DayPipeline.members).
        val members = if (source.effective.members.isEmpty()) {
            emptyList()
        } else {
            DayPipeline.members(source.effective, date, zone, source.school, settings.hijriOffsetDays)
                .map { listOf(it.fajr, it.sunrise, it.dhuhr, it.asr, it.maghrib, it.isha, it.endOfEating) }
        }
        return TimetableDay(
            date = date,
            times = times,
            hijri = TabularHijriCalendar.fromGregorian(date),
            friday = date.dayOfWeek == DayOfWeek.FRIDAY,
            utcOffsetSeconds = zone.offsetAt(day.dhuhr).totalSeconds,
            asrOther = day.asrOther,
            endOfEating = day.endOfEating,
            imsak = day.imsak,
            sunset = day.sunset,
            setByRule = day.setByRule,
            polar = day.polar,
            ramadanIsha = ramadanIsha(source, date),
            members = members,
            capped = members.any { it[MEMBER_MAGHRIB] > day.maghrib },
        )
    }

    /** Whether [date] is a Ramadan date on which [source]'s Isha counts its Ramadan minutes. */
    private fun ramadanIsha(source: EngineDay, date: LocalDate): Boolean {
        val rule = source.effective.method?.isha as? IshaRule.AfterMaghrib ?: return false
        if (rule.ramadanMinutes == rule.minutes) return false
        return Registry.ramadanCalendarFor(source.effectiveEntry, settings.hijriOffsetDays).isRamadan(date)
    }

    /** The date on the city's own calendar at [now]. */
    fun localToday(city: City, now: Instant): LocalDate = now.toLocalDateTime(TimeZone.of(city.timeZone)).date

    /**
     * The city's current month and the next, by the city's own calendar at [now]: on the first of
     * a month in UTC, a city still on the last day of the previous one keeps that month.
     */
    fun months(city: City, now: Instant): List<TimetableMonth> {
        val today = localToday(city, now)
        val first = LocalDate(today.year, today.month.number, 1)
        return (0 until 2).map { offset ->
            val start = first.plus(offset, DateTimeUnit.MONTH)
            val days = generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }
                .takeWhile { it.month == start.month }
                .map { day(city, it) }
                .toList()
            TimetableMonth(start.year, start.month.number, days)
        }
    }
}
