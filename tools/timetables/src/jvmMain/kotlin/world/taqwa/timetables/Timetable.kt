package world.taqwa.timetables

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.hijri.HijriDate
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.prayer.CalculationMethodDefaults
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class TimetableDay(
    val date: LocalDate,
    val times: Map<Prayer, Instant>,
    val hijri: HijriDate,
    val friday: Boolean,
    /** The zone's offset at that day's Dhuhr: what the local clock reads through the day. */
    val utcOffsetSeconds: Int,
    /** The high-latitude rule the app's engine reports it applied that day, if any. */
    val highLatitudeRule: HighLatitudePreference?,
    /** Isha moved to Umm al-Qura's Ramadan time, two hours after Maghrib (see [Timetable.day]). */
    val ramadanIsha: Boolean = false,
)

data class TimetableMonth(val year: Int, val month: Int, val days: List<TimetableDay>)

/**
 * A city's prayer times, exactly as the app computes them for a user there who has not changed a
 * setting: the country's default method, the city's Asr school, the automatic high-latitude rule,
 * no minute adjustments, and the tabular Hijri date with no offset.
 */
class Timetable(private val engine: PrayerTimesEngine = PrayerTimesEngine()) {

    fun method(city: City): CalculationMethodId = CalculationMethodDefaults.forCountry(city.countryCode)

    fun settings(city: City) = PrayerSettings(
        method = method(city),
        madhab = city.madhab,
        highLatitude = HighLatitudePreference.AUTOMATIC,
    )

    fun location(city: City) = GeoLocation(
        latitude = city.latitude,
        longitude = city.longitude,
        timeZoneId = city.timeZone,
        cityName = city.name("en"),
        countryCode = city.countryCode,
        cityId = city.id,
    )

    fun day(city: City, date: LocalDate): TimetableDay {
        val computed = engine.timesFor(location(city), date, settings(city))
        val hijri = TabularHijriCalendar.fromGregorian(date)
        val engineTimes = Prayer.entries.associateWith { computed.time(it) }
        // Umm al-Qura's Isha is 90 minutes after Maghrib, and 120 in Ramadan. The app's engine
        // keeps 90 all year, which would put Makkah's Ramadan Isha half an hour early, so the page
        // adds the half hour and says so. The check on the interval means the day the app learns
        // the rule, this stops adding anything of its own.
        val ramadanIsha = method(city) == CalculationMethodId.UMM_AL_QURA && hijri.month == RAMADAN &&
            engineTimes.getValue(Prayer.ISHA) - engineTimes.getValue(Prayer.MAGHRIB) == UMM_AL_QURA_ISHA
        val times = if (ramadanIsha) {
            engineTimes + (Prayer.ISHA to engineTimes.getValue(Prayer.ISHA) + RAMADAN_EXTRA)
        } else {
            engineTimes
        }
        // Where the clock runs far enough ahead of the sun (Samoa, Tonga, Kiribati's eastern
        // islands), the engine answers a date with the next day's times. Dhuhr always sits near
        // local noon, so its date shows it at once; such a city has no page until that is fixed.
        val dhuhrDate = times.getValue(Prayer.DHUHR).toLocalDateTime(TimeZone.of(city.timeZone)).date
        check(dhuhrDate == date) {
            "${city.slug}: the app's engine puts the Dhuhr of $date on $dhuhrDate in ${city.timeZone}, " +
                "so the page would show another day's times; leave the city out until the engine is fixed"
        }
        return TimetableDay(
            date = date,
            times = times,
            hijri = hijri,
            friday = date.dayOfWeek == DayOfWeek.FRIDAY,
            utcOffsetSeconds = TimeZone.of(city.timeZone).offsetAt(times.getValue(Prayer.DHUHR)).totalSeconds,
            highLatitudeRule = computed.highLatitudeRuleApplied,
            ramadanIsha = ramadanIsha,
        )
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

    private companion object {
        const val RAMADAN = 9
        val UMM_AL_QURA_ISHA = 90.minutes
        val RAMADAN_EXTRA = 30.minutes
    }
}
