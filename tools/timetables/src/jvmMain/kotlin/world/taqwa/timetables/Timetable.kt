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
    /** Whether a high-latitude rule set any of the day's times, or the sun neither rose nor set. */
    val setByRule: Boolean,
    /** Isha on its timetable's Ramadan interval (Umm al-Qura's 120 minutes after Maghrib). */
    val ramadanIsha: Boolean = false,
)

data class TimetableMonth(val year: Int, val month: Int, val days: List<TimetableDay>)

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
        val times = mapOf(
            Prayer.FAJR to day.fajr, Prayer.SUNRISE to day.sunrise, Prayer.DHUHR to day.dhuhr,
            Prayer.ASR to day.asr, Prayer.MAGHRIB to day.maghrib, Prayer.ISHA to day.isha,
        )
        return TimetableDay(
            date = date,
            times = times,
            hijri = TabularHijriCalendar.fromGregorian(date),
            friday = date.dayOfWeek == DayOfWeek.FRIDAY,
            utcOffsetSeconds = TimeZone.of(city.timeZone).offsetAt(day.dhuhr).totalSeconds,
            setByRule = day.setByRule.isNotEmpty() || day.polar,
            ramadanIsha = ramadanIsha(source, date),
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
