package world.taqwa.app.prayer.engine.registry.authorities

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.ClockRule
import world.taqwa.app.prayer.engine.method.ClockTime
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.GeoPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.time.Instant

/**
 * Islamsk Råd Norge's rule for its Tromsø calendar (ruling R82): IRN's stated principle for places
 * north of 66.33° N, "Makkah time" (bonnetid.info, "Nord-områder"; its project history lists a method
 * "basert på Makkah tid"), with the constants its 2026 Tromsø calendar follows. Those constants are
 * derived parameters read from that calendar (rulings R42, R67, R81: angles, minutes, dates and
 * horizons, never a printed time); the calendar itself is restricted and not committed.
 *
 * Every event is read on the place's meridian, in two rules:
 * - **ordinary**: Fajr the later of the sun at [fajrDepression] and [nightLimitMinutes] before noon;
 *   Isha the earlier of the sun at [ishaDepression] and [nightLimitMinutes] after noon; Maghrib
 *   [maghribAfterSunsetMinutes] after IRN's sunset;
 * - **Makkah**: the same day at [makkahLatitude]: Fajr and Isha at [makkahDepression], Maghrib
 *   [maghribAfterSunsetMinutes] after its sunset.
 *
 * Its [windows] say which rule holds when, event by event, joined by straight lines on the wall clock
 * (a 25-hour day's 18:00 is still 18:00; 31 October, an anchor, is the day the clocks go back in
 * 2027). Asr, both schools, is the later of the shadow and midway between noon and IRN's own
 * Maghrib less its minutes, the Hanafi never after that Maghrib less [hanafiBeforeMaghribMinutes].
 * Dhuhr is noon plus [dhuhrWinterMinutes], plus [dhuhrSummerMinutes] within [dhuhrSummer]. IRN's
 * sunrise is its calculator's (the sun at [sunriseHorizonLengthening] while the days lengthen,
 * [sunriseHorizonShortening] while they shorten; its sunset likewise), noon in the polar night and
 * the sun's lowest point where it does not set: that last is no sunrise, so under the midnight sun
 * the sunrise shown is Makkah's, after IRN's own Fajr, and IRN's is declared not followed.
 *
 * Only 2026 is held, so until IRN's 2027 Tromsø calendar is, [windowShiftDays] reads every window
 * date and the Dhuhr change moved by each of those days, and each start is the latest and each end
 * the earliest of them. A moved anchor is read on its own date's wall clock, as the rule reads every
 * anchor: 30 October, the autumn blend's anchor a day early, is still summer time in a year whose
 * clocks go back on 31 October (2027, 2032, 2038), so that line starts an hour later and its
 * November runs up to 54 (Fajr) to 67 min (Maghrib) after the rule on its own dates, which the
 * unit's late limits record. No 2026 blend crosses a clock change, so the calendar cannot say how
 * IRN's line would; read on winter time instead, that November would be as early as this is late if
 * IRN's dates do move a day earlier and its line is the wall clock's, as this rule's is. IRN's other
 * Arctic calendars (Bodø, Narvik, Alta …) are not held: the rule stays inside the Tromsø unit.
 *
 * A data class: every constant is in its `toString`, which the proof stamps fingerprint.
 */
data class IrnArctic(
    val windowShiftDays: List<Int> = listOf(0),
    val makkahLatitude: Double = 21.4225,
    val makkahDepression: Double = 18.0,
    val fajrDepression: Double = 16.0,
    val ishaDepression: Double = 15.0,
    val nightLimitMinutes: Int = 540,
    val maghribAfterSunsetMinutes: Int = 5,
    val hanafiBeforeMaghribMinutes: Int = 65,
    val dhuhrWinterMinutes: Int = 5,
    val dhuhrSummerMinutes: Int = 10,
    val dhuhrSummer: Span = Span(MonthDay(3, 21), MonthDay(9, 20)),
    val sunriseHorizonLengthening: Double = -1.05,
    val sunriseHorizonShortening: Double = -0.85,
    val sunsetHorizonLengthening: Double = -0.70,
    val sunsetHorizonShortening: Double = -1.00,
    val windows: List<Window> = TROMSO_WINDOWS,
) : ClockRule {
    init {
        require(windowShiftDays.isNotEmpty()) { "IrnArctic: at least one window shift" }
    }

    /** A month and day, the same every year. */
    data class MonthDay(val month: Int, val day: Int) {
        fun inYear(year: Int, shiftDays: Int): LocalDate = LocalDate(year, month, day).plus(shiftDays, DateTimeUnit.DAY)
    }

    /** [from] to [to], both included. */
    data class Span(val from: MonthDay, val to: MonthDay)

    /** The events IRN moves between its two rules. */
    enum class Event { FAJR, MAGHRIB, ISHA }

    /**
     * For [events]: the ordinary rule until [blendFrom], a straight line on the wall clock to the
     * Makkah rule on [holdFrom], held through [holdTo], a straight line back to the ordinary rule on
     * [blendTo]. A window may run into the next year.
     */
    data class Window(val events: Set<Event>, val blendFrom: MonthDay, val holdFrom: MonthDay, val holdTo: MonthDay, val blendTo: MonthDay)

    override val notFollowedNoteKey: String get() = NOTE_KEY

    override fun on(date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTimes {
        val here = clock(point.lat, point.lon, date, zone)
        val noon = here.transit()
        val shifts = windowShiftDays.map { shift -> shifted(date, point, zone, here, noon, shift) }
        fun latest(pick: (Shifted) -> ClockTime) =
            shifts.map(pick).let { all -> ClockTime(all.maxOf { it.epochSeconds }, all.any { it.estimated }) }
        val maghrib = latest { it.maghrib }
        val realSunrise = here.altitudeTime(HORIZON, morning = true)
        val irnSunrise = here.altitudeTime(if (lengthening(date)) sunriseHorizonLengthening else sunriseHorizonShortening, morning = true)
        val sunUpAtNoon = sunUpAtNoon(point.lat, noon)
        val sunrise = when {
            irnSunrise != null -> ClockTime(irnSunrise, estimated = false)
            realSunrise != null -> ClockTime(realSunrise, estimated = false) // IRN prints its lowest point; the sun rises
            sunUpAtNoon -> ClockTime(crossing(makkahLatitude, point.lon, date, zone, HORIZON, morning = true), estimated = true)
            else -> ClockTime(noon, estimated = true) // IRN's own noon in the polar night
        }
        return ClockTimes(
            fajr = latest { it.fajr },
            sunrise = sunrise,
            dhuhr = latest { it.dhuhr },
            asrStandard = latest { it.asrStandard },
            asrHanafi = latest { it.asrHanafi },
            maghrib = maghrib,
            isha = latest { it.isha },
            // IRN prints no imsak: the fast begins at its Fajr, the earliest over the dates.
            endOfEating = shifts.minOf { it.fajr.epochSeconds },
            sunset = maghrib.epochSeconds - maghribAfterSunsetMinutes * 60.0,
            noSunset = here.altitudeTime(HORIZON, morning = false) == null,
            notFollowed = if (irnSunrise == null && (realSunrise != null || sunUpAtNoon)) setOf(Prayer.SUNRISE) else emptySet(),
        )
    }

    /** IRN's times that depend on its dates, with every date moved by [shift] days. */
    private class Shifted(
        val fajr: ClockTime,
        val dhuhr: ClockTime,
        val asrStandard: ClockTime,
        val asrHanafi: ClockTime,
        val maghrib: ClockTime,
        val isha: ClockTime,
    )

    private fun shifted(date: LocalDate, point: GeoPoint, zone: TimeZone, here: SunClock, noon: Double, shift: Int): Shifted {
        val maghrib = value(Event.MAGHRIB, date, point, zone, shift)
        val mid = (noon + maghrib.epochSeconds - maghribAfterSunsetMinutes * 60.0) / 2
        fun asr(shadowFactor: Double): ClockTime {
            val shadow = here.asr(shadowFactor, AsrModel.EXACT_MOMENT)
            return if (shadow != null && shadow >= mid) ClockTime(shadow, estimated = false) else ClockTime(mid, estimated = true)
        }
        val hanafiCap = maghrib.epochSeconds - hanafiBeforeMaghribMinutes * 60.0
        val hanafi = asr(2.0).let { if (it.epochSeconds > hanafiCap) ClockTime(hanafiCap, estimated = true) else it }
        val summer = date >= dhuhrSummer.from.inYear(date.year, shift) && date <= dhuhrSummer.to.inYear(date.year, shift)
        return Shifted(
            fajr = value(Event.FAJR, date, point, zone, shift),
            dhuhr = ClockTime(noon + (if (summer) dhuhrSummerMinutes else dhuhrWinterMinutes) * 60.0, estimated = false),
            asrStandard = asr(1.0),
            asrHanafi = hanafi,
            maghrib = maghrib,
            isha = value(Event.ISHA, date, point, zone, shift),
        )
    }

    /** IRN's own time for [event] on [date]: the rule its windows hold there, or the line between two. */
    private fun value(event: Event, date: LocalDate, point: GeoPoint, zone: TimeZone, shift: Int): ClockTime {
        for (window in windows.filter { event in it.events }) {
            // The window's occurrence that starts the year before (a window into the new year) or this year.
            for (startYear in listOf(date.year - 1, date.year)) {
                val blendFrom = window.blendFrom.inYear(startYear, shift)
                val holdFrom = window.holdFrom.inYear(startYear, shift)
                val wraps = window.holdTo.month < window.holdFrom.month
                val holdTo = window.holdTo.inYear(if (wraps) startYear + 1 else startYear, shift)
                val blendTo = window.blendTo.inYear(if (wraps || window.blendTo.month < window.holdFrom.month) startYear + 1 else startYear, shift)
                when {
                    date > blendFrom && date < holdFrom -> return blend(event, date, blendFrom, fromOrdinary = true, holdFrom, point, zone)
                    date >= holdFrom && date <= holdTo -> return ClockTime(makkah(event, date, point, zone), estimated = true)
                    date > holdTo && date < blendTo -> return blend(event, date, holdTo, fromOrdinary = false, blendTo, point, zone)
                }
            }
        }
        return ordinary(event, date, point, zone)
    }

    /** The straight line on the wall clock from [from] (under its first rule) to [to] (under the other). */
    private fun blend(event: Event, date: LocalDate, from: LocalDate, fromOrdinary: Boolean, to: LocalDate, point: GeoPoint, zone: TimeZone): ClockTime {
        val a = if (fromOrdinary) ordinary(event, from, point, zone).epochSeconds else makkah(event, from, point, zone)
        val b = if (fromOrdinary) makkah(event, to, point, zone) else ordinary(event, to, point, zone).epochSeconds
        val wallA = wallClock(a, zone)
        val wallB = wallClock(b, zone)
        val f = (date.toEpochDays() - from.toEpochDays()).toDouble() / (to.toEpochDays() - from.toEpochDays())
        return ClockTime(atWallClock(date, wallA + (wallB - wallA) * f, zone), estimated = true)
    }

    private fun ordinary(event: Event, date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTime {
        val c = clock(point.lat, point.lon, date, zone)
        val noon = c.transit()
        val limit = nightLimitMinutes * 60.0
        return when (event) {
            Event.FAJR -> c.altitudeTime(-fajrDepression, morning = true)
                ?.takeIf { it >= noon - limit }?.let { ClockTime(it, estimated = false) }
                ?: ClockTime(noon - limit, estimated = true)
            Event.ISHA -> c.altitudeTime(-ishaDepression, morning = false)
                ?.takeIf { it <= noon + limit }?.let { ClockTime(it, estimated = false) }
                ?: ClockTime(noon + limit, estimated = true)
            Event.MAGHRIB -> c.altitudeTime(if (lengthening(date)) sunsetHorizonLengthening else sunsetHorizonShortening, morning = false)
                ?.let { ClockTime(it + maghribAfterSunsetMinutes * 60.0, estimated = false) }
                // Never on the ordinary rule's dates at Tromsø; elsewhere in the unit, Makkah's evening.
                ?: ClockTime(makkah(Event.MAGHRIB, date, point, zone), estimated = true)
        }
    }

    private fun makkah(event: Event, date: LocalDate, point: GeoPoint, zone: TimeZone): Double = when (event) {
        Event.FAJR -> crossing(makkahLatitude, point.lon, date, zone, -makkahDepression, morning = true)
        Event.ISHA -> crossing(makkahLatitude, point.lon, date, zone, -makkahDepression, morning = false)
        Event.MAGHRIB -> crossing(makkahLatitude, point.lon, date, zone, HORIZON, morning = false) + maghribAfterSunsetMinutes * 60.0
    }

    /** A crossing at a latitude the sun crosses every day of the year (Makkah's). */
    private fun crossing(lat: Double, lon: Double, date: LocalDate, zone: TimeZone, altitudeDeg: Double, morning: Boolean): Double {
        val c = clock(lat, lon, date, zone)
        return c.altitudeTime(altitudeDeg, morning) ?: (c.transit() + if (morning) -HALF_DAY else HALF_DAY)
    }

    /** While the days lengthen: after the winter solstice and before the summer one (21 December and 21 June). */
    private fun lengthening(date: LocalDate): Boolean = date < LocalDate(date.year, 6, 21) || date > LocalDate(date.year, 12, 21)

    private fun sunUpAtNoon(lat: Double, noon: Double): Boolean =
        90.0 - abs(lat - SolarMath.sun(SolarMath.julianDay(noon)).declinationDeg) > HORIZON

    private fun clock(lat: Double, lon: Double, date: LocalDate, zone: TimeZone): SunClock =
        SunClock(lat, lon, date, zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds, SunModel.EXACT)

    /** Seconds after local midnight on the wall clock (not elapsed: a 25-hour day's 18:00 is 64 800). */
    private fun wallClock(epochSeconds: Double, zone: TimeZone): Double {
        val whole = floor(epochSeconds)
        val t = Instant.fromEpochSeconds(whole.toLong()).toLocalDateTime(zone)
        return t.hour * 3600.0 + t.minute * 60.0 + t.second + (epochSeconds - whole)
    }

    /** The instant [date]'s wall clock shows [seconds] after midnight. */
    private fun atWallClock(date: LocalDate, seconds: Double, zone: TimeZone): Double {
        val whole = floor(seconds)
        val time = LocalTime.fromSecondOfDay(whole.toInt().coerceIn(0, SECONDS_PER_DAY - 1))
        return date.atTime(time).toInstant(zone).epochSeconds + (seconds - whole)
    }

    companion object {
        /** About's sentence on what of IRN's Tromsø calendar is not followed. */
        const val NOTE_KEY = "about_not_followed_irn_tromso"

        private const val HORIZON = -0.8333
        private const val HALF_DAY = 43_200.0
        private const val SECONDS_PER_DAY = 86_400

        private fun md(month: Int, day: Int) = MonthDay(month, day)

        /** IRN's Tromsø windows, from its 2026 calendar: weekly steps in April and August. */
        val TROMSO_WINDOWS: List<Window> = listOf(
            Window(setOf(Event.FAJR, Event.MAGHRIB, Event.ISHA), md(10, 31), md(11, 30), md(1, 10), md(2, 9)),
            Window(setOf(Event.FAJR, Event.ISHA), md(4, 22), md(4, 29), md(8, 14), md(8, 21)),
            Window(setOf(Event.MAGHRIB), md(4, 15), md(4, 22), md(8, 22), md(8, 29)),
        )
    }
}
