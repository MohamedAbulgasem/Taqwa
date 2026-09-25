package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.CountdownDigits
import world.taqwa.app.qibla.QiblaMath
import kotlin.time.Instant

/**
 * The one document the site is rendered from. Per city: the facts (slug, country, zone, the method
 * and Asr school the app would use, the Qibla, and for every day of both months the six instants
 * as epoch seconds for the page's live countdown); and per page language, every string the page
 * shows about that city, already written the way the app writes it. The site build adds only
 * its own sentences around these values, so it never formats a number or a date itself.
 */
class Document(private val strings: AppStrings, private val timetable: Timetable = Timetable()) {

    fun build(cities: List<City>, now: Instant): Map<String, Any?> = linkedMapOf(
        "generated" to now.toString(),
        "prayersArabic" to Prayer.entries.map { strings.prayer("ar", it) },
        "regions" to Regions.ORDER,
        "cities" to cities.map { city(it, now) },
    )

    fun city(city: City, now: Instant): Map<String, Any?> {
        val months = timetable.months(city, now)
        val days = months.flatMap { it.days }
        val today = timetable.localToday(city, now)
        val location = timetable.location(city)
        val qibla = Qibla(QiblaMath.bearing(location), QiblaMath.distanceKm(location))
        val method = timetable.method(city)
        return linkedMapOf(
            "slug" to city.slug,
            "id" to city.id,
            "country" to city.countryCode,
            "region" to city.region,
            "timeZone" to city.timeZone,
            "latitude" to city.latitude,
            "longitude" to city.longitude,
            "languages" to city.languages,
            "featured" to city.languages.filter { it in city.featured },
            "method" to method.name,
            "madhab" to city.madhab.name,
            "qibla" to linkedMapOf("bearing" to qibla.bearing, "km" to qibla.km),
            "today" to today.toString(),
            "months" to months.map { linkedMapOf("year" to it.year, "month" to it.month, "days" to it.days.size) },
            "days" to days.map { day ->
                linkedMapOf(
                    "date" to day.date.toString(),
                    "friday" to day.friday,
                    "offset" to day.utcOffsetSeconds,
                    "highLatitude" to (day.highLatitudeRule != null),
                    "ramadanIsha" to day.ramadanIsha,
                    "epochs" to Prayer.entries.map { day.times.getValue(it).epochSeconds },
                )
            },
            "pages" to linkedMapOf(
                *city.languages.map { it to page(city, it, months, today, qibla, method) }.toTypedArray(),
            ),
        )
    }

    private data class Qibla(val bearing: Double, val km: Double)

    private fun page(
        city: City,
        language: String,
        months: List<TimetableMonth>,
        today: LocalDate,
        qibla: Qibla,
        method: CalculationMethodId,
    ): Map<String, Any?> {
        val f = Formats(language, city.countryCode)
        val zone = TimeZone.of(city.timeZone)
        val days = months.flatMap { it.days }
        val todayRow = days.first { it.date == today }
        val prayers = Prayer.entries.map { strings.prayer(language, it) }
        fun clock(instant: Instant) = instant.toLocalDateTime(zone).let { f.clock(it.hour, it.minute) }

        val rules: List<HighLatitudePreference> = days.mapNotNull { it.highLatitudeRule }.distinct()
        // A change on the first day shown (a page built the morning the clocks went back) has no
        // day before it to compare with, so the clock at that day's midnight stands in.
        fun offsetBefore(i: Int): Int =
            if (i > 0) days[i - 1].utcOffsetSeconds else zone.offsetAt(days[0].date.atStartOfDayIn(zone)).totalSeconds
        val clockChanges = days.indices
            .filter { days[it].utcOffsetSeconds != offsetBefore(it) }
            .map { i ->
                linkedMapOf(
                    "index" to i,
                    "date" to f.longDate(days[i].date),
                    "offset" to f.utcOffset(days[i].utcOffsetSeconds),
                )
            }

        return linkedMapOf(
            "locale" to f.locale.toLanguageTag(),
            "digits" to f.digitSet(),
            "countdownDigits" to if (CountdownDigits.westernFallback(f.locale.toLanguageTag())) WESTERN else f.digitSet(),
            "city" to city.name(language),
            "country" to f.countryName(city.countryCode),
            "method" to strings.method(language, method),
            "madhab" to strings.madhab(language, city.madhab),
            "prayers" to prayers,
            "nextIn" to prayers.map { strings.format(language, "today_next_in", it) },
            "jumuah" to strings.get(language, "today_jumuah"),
            "qibla" to strings.get(language, "qibla_title"),
            "qiblaDetail" to strings.format(
                language, "today_qibla_detail", f.digits(qibla.bearing.toInt()), f.distance(qibla.km),
            ),
            "bearing" to f.bearing(qibla.bearing),
            "distance" to f.distance(qibla.km),
            "offset" to f.utcOffset(todayRow.utcOffsetSeconds),
            "today" to linkedMapOf(
                "weekday" to f.weekday(today),
                "full" to f.fullDate(today),
                "date" to f.longDate(today),
                "hijri" to f.hijri(todayRow.hijri.year, todayRow.hijri.month, todayRow.hijri.day),
            ),
            "highLatitude" to rules.map { strings.highLatitude(language, it) },
            "clockChanges" to clockChanges,
            "months" to months.map { month ->
                linkedMapOf(
                    "title" to f.monthYear(month.year, month.month),
                    "hijri" to f.hijriSpan(month.days.map { it.hijri.year to it.hijri.month }.distinct()),
                )
            },
            "days" to days.map { day ->
                linkedMapOf(
                    "day" to f.digits(day.date.day),
                    "weekday" to f.weekdayShort(day.date),
                    "date" to f.longDate(day.date),
                    "full" to f.fullDate(day.date),
                    "hijri" to f.hijriDayMonth(day.hijri.month, day.hijri.day),
                    "hijriLong" to f.hijri(day.hijri.year, day.hijri.month, day.hijri.day),
                    "times" to Prayer.entries.map { clock(day.times.getValue(it)) },
                )
            },
        )
    }

    private companion object {
        const val WESTERN = "0123456789"
    }
}
