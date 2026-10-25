package world.taqwa.timetables

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.prayer.ClockChange
import world.taqwa.app.prayer.ClockChanges
import java.time.zone.ZoneRulesProvider
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
    /**
     * Ruling R155's far-north guard decided Maghrib: a member's own Maghrib is after the one shown (its far-north or
     * winter rule set it rather than the sun and it is not the most followed, or the real sun did not set there and
     * only the most-followed member's own counted); the app's `maghribGuardToday`. Never south of about 62.6°.
     */
    val guard: Boolean = false,
    /**
     * A cautious place's members' Maghribs from the sun ([world.taqwa.app.prayer.engine.day.PrayerDay.maghribBySun]),
     * in the resolution's order: null where a rule sets the member's Maghrib, and every one null where the real sun
     * does not set at the city's point that day ([sunSets]), as the engine counts them (ruling R155). On a guard day the
     * Maghrib shown can be one of these, no member's own Maghrib. Empty for a single method.
     */
    val maghribBySun: List<Instant?> = emptyList(),
    /** The real sun sets at the city's point that day ([DayPipeline.realSunset]); which guard sentence a page says. */
    val sunSets: Boolean = true,
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
    /** The clock changes a page is checked against ([staleClock]); the app's own table. */
    private val clockChanges: List<ClockChange> = ClockChanges.table,
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
        val utcOffsetSeconds = zone.offsetAt(day.dhuhr).totalSeconds
        staleClock(city, date, utcOffsetSeconds)?.let { error(it) }
        val times = mapOf(
            Prayer.FAJR to day.fajr, Prayer.SUNRISE to day.sunrise, Prayer.DHUHR to day.dhuhr,
            Prayer.ASR to day.asr, Prayer.MAGHRIB to day.maghrib, Prayer.ISHA to day.isha,
        )
        // A cautious place's members, each computed with its own method at the city's point in the
        // page's school, as the app's About screen lists them (DayPipeline.members).
        val memberDays = if (source.effective.members.isEmpty()) {
            emptyList()
        } else {
            DayPipeline.members(source.effective, date, zone, source.school, settings.hijriOffsetDays)
        }
        val members = memberDays.map { listOf(it.fajr, it.sunrise, it.dhuhr, it.asr, it.maghrib, it.isha, it.endOfEating) }
        // Ruling R155: a member's Maghrib counts from the sun only where the real sun sets at the city's point that day.
        val sunSets = memberDays.isEmpty() || DayPipeline.realSunset(source.effective.point, date, zone) != null
        return TimetableDay(
            date = date,
            times = times,
            hijri = TabularHijriCalendar.fromGregorian(date),
            friday = date.dayOfWeek == DayOfWeek.FRIDAY,
            utcOffsetSeconds = utcOffsetSeconds,
            asrOther = day.asrOther,
            endOfEating = day.endOfEating,
            imsak = day.imsak,
            sunset = day.sunset,
            setByRule = day.setByRule,
            polar = day.polar,
            ramadanIsha = ramadanIsha(source, date),
            members = members,
            guard = members.any { it[MEMBER_MAGHRIB] > day.maghrib },
            maghribBySun = memberDays.map { it.maghribBySun.takeIf { sunSets } },
            sunSets = sunSets,
        )
    }

    /**
     * Why [city]'s page cannot be drawn on [date], or null. [utcOffsetSeconds] is the offset this JVM's tz
     * database gives the city's zone that day; a [ClockChange] for the zone in force on [date] says what it
     * is. Zone data that predates the change draws every time an hour off (an hour early where the clocks
     * stayed forward), so the build stops and names the change rather than publish it.
     */
    internal fun staleClock(city: City, date: LocalDate, utcOffsetSeconds: Int): String? {
        val change = clockChanges.filter { city.timeZone in it.zones && date >= it.from }.maxByOrNull { it.from } ?: return null
        if (utcOffsetSeconds == change.trueOffsetSeconds) return null
        val tzdb = runCatching { ZoneRulesProvider.getVersions(city.timeZone).lastKey() }.getOrDefault("unknown")
        return "${city.slug}: ${city.timeZone} keeps ${utc(change.trueOffsetSeconds)} from ${change.from} (ClockChanges " +
            "'${change.id}'), but this JVM's tz database ($tzdb) gives ${utc(utcOffsetSeconds)} on $date: build the " +
            "pages on a JDK whose tz database knows the change."
    }

    private fun utc(seconds: Int): String =
        "UTC%s%02d:%02d".format(if (seconds < 0) "-" else "+", kotlin.math.abs(seconds) / 3600, kotlin.math.abs(seconds) % 3600 / 60)

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
