package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Ruling R25: a year of days at every high latitude we can think of, with every kind of Isha and
 * high-latitude rule. After `Invariants.repair` (as the entry point will call it) every day must hold
 * its order in whole minutes, never start a prayer before the place's own real sign nor end one
 * after it, and never move an estimated Isha more than 20 minutes earlier, or an estimated end of
 * eating more than 20 minutes later, than the day before, except that the limit never holds Isha
 * into the next night's end (ruling R62).
 *
 * Days are walked in pairs (the day before and the day) every [STRIDE] days, and every day within
 * [EDGE_DAYS] of an edge (the sun starting or stopping to rise or set, a twilight sign starting or
 * stopping to occur) is walked as well. With a stride of 1 (every day of 2026, 70,080 place-method
 * days) it passed with 175 days repaired, all near polar night (Asr within a minute of Dhuhr, or at
 * or after Maghrib or sunset on nearly sunless days), and Longyearbyen's seventh-of-the-night Fajr
 * on 21 June, in the minute of a substitute sunrise after a night of a few minutes.
 */
class HighLatitudeYearWalkTest {
    private class City(val name: String, val point: GeoPoint, val zone: TimeZone)

    private val cities = listOf(
        City("Murmansk", GeoPoint(68.97, 33.07), TimeZone.of("Europe/Moscow")),
        City("Tromsø", GeoPoint(69.6492, 18.9553), TimeZone.of("Europe/Oslo")),
        City("Longyearbyen", GeoPoint(78.2232, 15.6267), TimeZone.of("Europe/Oslo")),
        City("Norilsk", GeoPoint(69.3535, 88.2027), TimeZone.of("Asia/Krasnoyarsk")),
        City("Vorkuta", GeoPoint(67.4981, 64.0341), TimeZone.of("Europe/Moscow")),
        City("Alta", GeoPoint(69.9689, 23.2716), TimeZone.of("Europe/Oslo")),
        City("Utqiagvik", GeoPoint(71.2906, -156.7886), TimeZone.of("America/Anchorage")),
        City("Rovaniemi", GeoPoint(66.5039, 25.7294), TimeZone.of("Europe/Helsinki")),
        City("Salekhard", GeoPoint(66.53, 66.6019), TimeZone.of("Asia/Yekaterinburg")),
        City("Bodø", GeoPoint(67.2804, 14.4049), TimeZone.of("Europe/Oslo")),
        City("Kiruna", GeoPoint(67.8558, 20.2253), TimeZone.of("Europe/Stockholm")),
    ) + (46..66).map { City("$it° N 20° E", GeoPoint(it.toDouble(), 20.0), TimeZone.of("Europe/Stockholm")) }

    private val methods = listOf(
        TimetableMethod(id = "walk.18-17", fajrAngle = 18.0, isha = IshaRule.Angle(17.0)),
        TimetableMethod(id = "walk.isna", fajrAngle = 15.0, isha = IshaRule.Angle(15.0)),
        TimetableMethod(id = "walk.egypt", fajrAngle = 19.5, isha = IshaRule.Angle(17.5)),
        TimetableMethod(id = "walk.after-90", fajrAngle = 18.5, isha = IshaRule.AfterMaghrib(90)),
        TimetableMethod(id = "walk.seventh", fajrAngle = 18.0, isha = IshaRule.Angle(17.0), highLatitude = HighLatRule.Legacy("seventh")),
        TimetableMethod(
            id = "walk.fraction", fajrAngle = 18.0, isha = IshaRule.Angle(17.0),
            highLatitude = HighLatRule.NightFraction(fajrFraction = 0.3, ishaFraction = 0.25),
        ),
    )

    private val first = LocalDate(2026, 1, 1)
    private val last = LocalDate(2026, 12, 31)

    private fun startOf(epochSeconds: Double) = Instant.fromEpochSeconds(ceil(epochSeconds / 60.0).toLong() * 60)
    private fun endOf(epochSeconds: Double) = Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    /** The place's own sky on one date, for the method's angles. */
    private class Own(
        val sunrise: Double?, val sunset: Double?, val dawn: Double?, val dusk: Double?,
        val transit: Double, val asr: Double?,
    )

    private fun own(city: City, method: TimetableMethod, date: LocalDate): Own {
        val offset = city.zone.offsetAt(date.atTime(12, 0).toInstant(city.zone)).totalSeconds
        val clock = SunClock(city.point.lat, city.point.lon, date, offset, SunModel.EXACT)
        val transit = clock.transit()
        val declination = SolarMath.sun(SolarMath.julianDay(transit)).declinationDeg
        val sunUp = 90.0 - abs(city.point.lat - declination) > -0.8333
        return Own(
            sunrise = clock.altitudeTime(-0.8333, morning = true),
            sunset = clock.altitudeTime(-0.8333, morning = false),
            dawn = clock.altitudeTime(-method.fajrAngle, morning = true),
            dusk = (method.isha as? IshaRule.Angle)?.let { clock.altitudeTime(-it.degrees, morning = false) },
            transit = transit,
            asr = if (sunUp) clock.asr(1.0, AsrModel.EXACT_MOMENT) else null,
        )
    }

    /** Every date within [EDGE_DAYS] of a change in whether the sun rises and sets, or a sign occurs. */
    private fun edgeDays(city: City, method: TimetableMethod): Set<LocalDate> {
        val edges = mutableSetOf<LocalDate>()
        var before: List<Boolean>? = null
        var date = first.minus(1, DateTimeUnit.DAY)
        while (date <= last.plus(1, DateTimeUnit.DAY)) {
            val o = own(city, method, date)
            val state = listOf(o.sunrise != null, o.sunset != null, o.dawn != null, o.dusk != null)
            if (before != null && state != before) (-EDGE_DAYS..EDGE_DAYS).forEach { edges += date.plus(it, DateTimeUnit.DAY) }
            before = state
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return edges
    }

    @Test
    fun `a year at high latitudes keeps its order its own signs and its daily limit after repair`() {
        val failures = mutableListOf<String>()
        val repaired = mutableListOf<String>()
        var days = 0
        for (city in cities) {
            for (method in methods) {
                val edges = edgeDays(city, method)
                var date = first
                var index = 0
                while (date <= last) {
                    if (index % STRIDE == 0 || date in edges) {
                        days++
                        check(city, method, date, failures, repaired)
                    }
                    date = date.plus(1, DateTimeUnit.DAY)
                    index++
                }
            }
        }
        println("Year walk: $days days, ${repaired.size} repaired")
        repaired.groupBy { it.substringAfterLast(" | ") }.forEach { (why, list) ->
            // "<city> walk.<method> <date>: …" → the places and dates, whatever the method.
            val placesAndDates = list.map { it.substringBefore(":") }
                .map { it.substringBefore(" walk.") + " " + it.substringAfterLast(" ") }.distinct()
            println("  ${list.size} repaired for $why (${placesAndDates.size} place-days): " + placesAndDates.joinToString("; "))
        }
        assertTrue(failures.isEmpty(), "${failures.size} failures in $days days:\n" + failures.take(40).joinToString("\n"))
    }

    /** Which parts of the order a day breaks, for the report of the days that needed repair. */
    private fun broken(day: PrayerDay): String = buildList {
        if (day.endOfEating > day.fajr) add("end of eating after Fajr")
        if (day.fajr >= day.sunrise) add("Fajr not before sunrise")
        if (day.sunrise >= day.dhuhr) add("Dhuhr not after sunrise")
        if (day.dhuhr >= day.asr) add("Asr in Dhuhr's minute or before")
        if (day.asr >= day.maghrib) add("Asr at or after Maghrib")
        if (day.maghrib >= day.isha) add("Isha not after Maghrib")
        if (day.maghrib < day.sunset) add("Maghrib before sunset")
        day.ends.forEach { (prayer, end) ->
            val start = when (prayer) {
                Prayer.FAJR -> day.fajr
                Prayer.DHUHR -> day.dhuhr
                Prayer.ASR -> day.asr
                Prayer.MAGHRIB -> day.maghrib
                else -> day.isha
            }
            if (end <= start) add("$prayer's end not after its start")
        }
    }.joinToString(", ")

    private fun day(city: City, method: TimetableMethod, date: LocalDate): PrayerDay =
        DayComputer.compute(method, city.point, date, city.zone, AsrSchool.STANDARD, { false })

    private fun check(city: City, method: TimetableMethod, date: LocalDate, failures: MutableList<String>, repaired: MutableList<String>) {
        val where = "${city.name} ${method.id} $date"
        fun Instant.clock(): String = toLocalDateTime(city.zone).time.toString()
        fun fail(what: String) {
            failures += "$where: $what"
        }

        val raw = day(city, method, date)
        val shown = Invariants.repair(raw)
        if (shown.repaired) repaired += "$where: ${describe(raw, city.zone)} | ${broken(raw)}"
        if (!Invariants.holds(shown)) fail("does not hold after repair: ${describe(shown, city.zone)}")
        listOfNotNull(
            shown.fajr, shown.sunrise, shown.dhuhr, shown.asr, shown.asrOther, shown.maghrib, shown.isha,
            shown.sunset, shown.endOfEating, shown.imsak,
        ).plus(shown.ends.values).forEach { if (it.epochSeconds % 60 != 0L) fail("$it is not a whole minute") }

        // Never a start before the place's own real sign, never an end after it.
        val o = own(city, method, date)
        o.dawn?.let { if (shown.fajr < startOf(it)) fail("fajr ${shown.fajr.clock()} before its own dawn ${startOf(it).clock()}") }
        o.dawn?.let { if (shown.endOfEating > endOf(it)) fail("end of eating ${shown.endOfEating.clock()} after its own dawn") }
        o.sunrise?.let { if (shown.sunrise > endOf(it)) fail("sunrise ${shown.sunrise.clock()} after its own ${endOf(it).clock()}") }
        if (shown.dhuhr < startOf(o.transit)) fail("dhuhr ${shown.dhuhr.clock()} before its own transit")
        o.asr?.let { if (shown.asr < startOf(it)) fail("asr ${shown.asr.clock()} before its own ${startOf(it).clock()}") }
        o.sunset?.let {
            if (shown.maghrib < startOf(it)) fail("maghrib ${shown.maghrib.clock()} before its own sunset ${startOf(it).clock()}")
            if (shown.sunset > endOf(it)) fail("sunset ${shown.sunset.clock()} after its own")
        }
        when (val rule = method.isha) {
            is IshaRule.Angle -> o.dusk?.let { if (shown.isha < startOf(it)) fail("isha ${shown.isha.clock()} before its own dusk ${startOf(it).clock()}") }
            is IshaRule.AfterMaghrib -> o.sunset?.let {
                val own = startOf(it + rule.minutes * 60)
                if (shown.isha < own) fail("isha ${shown.isha.clock()} before its own maghrib + ${rule.minutes} ${own.clock()}")
            }
        }

        // The daily limit, against the day before, where either day's sign was set by rule.
        val yesterday = date.minus(1, DateTimeUnit.DAY)
        val before = Invariants.repair(day(city, method, yesterday))
        val o0 = own(city, method, yesterday)
        val ishaByRule = Prayer.ISHA in shown.setByRule || Prayer.ISHA in before.setByRule ||
            shown.polar || before.polar || (method.isha is IshaRule.Angle && (o.dusk == null || o0.dusk == null))
        val eatingByRule = Prayer.FAJR in shown.setByRule || Prayer.FAJR in before.setByRule ||
            shown.polar || before.polar || o.dawn == null || o0.dawn == null
        // Ruling R62: the limit never holds Isha into the next night's end, so an Isha may step
        // further only down to a minute before the next day's end of eating (and never below its
        // own rule, checked above).
        val nextEnd = day(city, method, date.plus(1, DateTimeUnit.DAY)).endOfEating
        if (ishaByRule && shown.isha < minOf(before.isha + 1.days - 20.minutes, nextEnd - 1.minutes)) {
            fail("isha ${shown.isha.clock()} more than 20 minutes earlier than ${before.isha.clock()} the day before")
        }
        if (eatingByRule && shown.endOfEating > before.endOfEating + 1.days + 20.minutes) {
            fail("end of eating ${shown.endOfEating.clock()} more than 20 minutes later than ${before.endOfEating.clock()} the day before")
        }
    }

    private fun describe(day: PrayerDay, zone: TimeZone): String =
        listOf(day.endOfEating, day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha, day.sunset)
            .joinToString(" ") { it.toLocalDateTime(zone).let { t -> "${t.date.day}/${t.time}" } } +
            (if (day.polar) " polar" else "")

    private companion object {
        /**
         * Every how many days a pair is walked, besides the edges. Every day (1) walks 70,080 days
         * in about 8 s on the JVM but over a minute on the iOS simulator; 4 keeps the suite quick.
         */
        const val STRIDE = 4

        /** How many days either side of an edge are all walked. */
        const val EDGE_DAYS = 3
    }
}
