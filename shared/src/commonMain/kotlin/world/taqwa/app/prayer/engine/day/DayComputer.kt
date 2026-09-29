package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.ClockRule
import world.taqwa.app.prayer.engine.method.ClockTime
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.DayRule
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.RamadanRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * One method, one point, one civil date → the day as the authority prints it, rounded once.
 *
 * 1. The raw events (UTC epoch seconds) come from [SunClock][world.taqwa.app.prayer.engine.astro.SunClock]
 *    for each date the [DayRule] draws on, at the given point and at the method's
 *    [TimetableMethod.fixedPoint] beside it, and, for starts, at every [TimetableMethod.startPoints]
 *    too. A Fajr, Isha or end-of-eating angle the sun does not reach is set by [HighLatitude] (and
 *    flagged in [PrayerDay.setByRule]); an [EndOfEating.DawnOrMiddle] at the middle of the night,
 *    whatever the method's own rule. DUM RT's white nights ([HighLatRule.DumRtSummer]) replace
 *    Fajr and the end of suhoor on the mornings whose 18° dawn is missing at every point, and Isha
 *    on the evenings before them; a night too close to call takes each event's safe side (R61),
 *    and where the rule is read at the user's own point every night takes the latest Fajr and Isha
 *    and the earliest end of sahur across a band of latitude (R65).
 * 2. Starts take the latest candidate; sunrise, sunset, the end of eating and imsak the earliest
 *    over the given and the fixed point (never the start points).
 * 3. The authority's offsets (minutes, monthly seconds, Ramadan seconds) and our margins are added,
 *    then starts are rounded up and ends down to the minute. An [IshaRule.AfterMaghrib] counts its
 *    minutes from the authority's Maghrib: raw sunset plus the authority's Maghrib offsets,
 *    including the Ramadan seconds, before our margins.
 * 4. The [PrayerDay.ends] come from those values and the 17° dusk at the given and fixed points
 *    ([Ends]); Isha's end is the next day's end of eating, which the caller adds
 *    ([Ends.withNextDay]).
 * 5. **The daily rate limit** (rulings R17, R22, R24): while a sign is missing, and on the days
 *    after it returns, the Isha shown is never more than [DAILY_LIMIT] earlier than the day
 *    before's, and the end of eating and imsak shown never more than [DAILY_LIMIT] later, whatever
 *    their source (an estimate, or a polar day's borrowed time). The Fajr start is not limited: a
 *    later Fajr is the safe side. DUM RT's white nights, and those too close to call, are the
 *    authority's own and not limited.
 * 6. **The authority's clock rule** ([TimetableMethod.clockRule], ruling R82), last: its times, the
 *    authority's own and so not rate limited, at every point (starts the latest, ends the
 *    earliest), with the method's margins, as [ClockTimes] describes; an authority time no day in
 *    order can show is declared in [PrayerDay.notFollowed].
 * 7. **The fixed point's own day** (ruling R89): where a table's point rides beside the user's, the
 *    sunrise, end of eating and imsak shown are never after those the same method shows at that
 *    point alone, after every step above.
 * 8. **Fajr before the sunrise shown** (ruling R90), last of all: where the Fajr shown is not before
 *    the sunrise shown (an authority's sunrise printed minutes before the sun's, or a Fajr its own
 *    rule puts after sunrise, at the polar edge), the sunrise moves later to the minute after Fajr,
 *    never past the sun's own sunrise at the user's point; only where the sun has risen by that Fajr
 *    does Fajr move earlier, to the minute before, and it is declared in [PrayerDay.notFollowed].
 * 9. **Isha after the Maghrib shown** (monitor round, brief D), with it: where the Isha shown is not
 *    after the Maghrib shown (an authority whose takdir holds its day short of the sun's, so that
 *    its Isha, counted from its own earlier Maghrib, falls before the real sunset the Maghrib shown
 *    never precedes: Diyanet's Nordic tables from Umeå north in June), Isha moves later to the minute
 *    after Maghrib and is declared in [PrayerDay.notFollowed]: no day in order can show it.
 *
 * Ramadan, the month and the Isha minutes are always [date]'s, even when a day rule borrows a
 * neighbouring date's astronomy. Where [date] itself has no sunrise or no sunset the day is
 * [PrayerDay.polar] (spec §3.8): the place still gives every time it can from its own sky and its
 * own nights (ruling R23), and the rest is borrowed from the nearest latitude towards the equator
 * where the day has both, in half-degree steps on the same meridian, bound by the place's own real
 * signs: no start before the place's own, no end after it (ruling R19); only the borrowed times and
 * the place's own estimates are set by rule (ruling R21).
 */
object DayComputer {

    /** The polar rule's step towards the equator, in degrees of latitude. */
    const val POLAR_STEP_DEG = 0.5

    /** How far the shown Isha may move earlier, or the end of eating later, from one day to the next. */
    val DAILY_LIMIT: Duration = 20.minutes

    /**
     * [lagDates] are the dates a [DayRule.LAG_DATES_UQ] method borrows the previous day on; they
     * default to Umm al-Qura's own list, so that no caller loses the rule by leaving them out.
     * [lagDatesUntil] is the last date the list covers: after it every day is taken as a lag date
     * (ruling R71).
     */
    fun compute(
        method: TimetableMethod,
        point: GeoPoint,
        date: LocalDate,
        zone: TimeZone,
        school: AsrSchool,
        ramadan: RamadanCalendar,
        lagDates: Set<LocalDate> = UmmAlQuraDates.lagDates,
        lagDatesUntil: LocalDate = UmmAlQuraDates.lastDate,
    ): PrayerDay = Place(method, point, zone, school, ramadan, lagDates, lagDatesUntil).day(date)

    /**
     * One method at one point and zone. The days it computes share its skies, and with them every
     * crossing already found, so that the rate limit's look back costs little.
     */
    private class Place(
        private val method: TimetableMethod,
        private val point: GeoPoint,
        private val zone: TimeZone,
        private val school: AsrSchool,
        private val ramadan: RamadanCalendar,
        private val lagDates: Set<LocalDate>,
        private val lagDatesUntil: LocalDate,
        /** The skies over the place's points, shared with [atFixedPoint] so that its sun is solved once. */
        private val skies: HashMap<GeoPoint, Sky> = HashMap(),
    ) {
        // A fixed point never drops the user's own (spec §3.5): starts are the later of the two,
        // sunrise, sunset and the end of eating the earlier.
        private val here = listOfNotNull(method.fixedPoint, point).distinct()

        // Ruling R45: an ends-only fixed point (first in [here]) bounds the ends and not the starts.
        private val endsOnly = method.fixedPointMode == FixedPointMode.ENDS_ONLY && here.size == 2
        private val startHere = if (endsOnly) here.drop(1) else here

        /**
         * Ruling R89: the same method at the fixed point alone, where one rides beside the user's point:
         * the day the table's own point shows, which bounds the ends shown here ([day]).
         */
        private val atFixedPoint: Place? =
            if (here.size == 2) Place(method, here.first(), zone, school, ramadan, lagDates, lagDatesUntil, skies) else null

        private fun sky(point: GeoPoint): Sky = skies.getOrPut(point) { Sky(method, point, zone) }

        /**
         * Whether a [DayRule.LAG_DATES_UQ] row on [date] may hold the previous day's astronomy: on a
         * listed lag date, and (ruling R71) on every day after the list's last date
         * ([lagDatesUntil]), where no list says which days lag, so starts take the later of the day
         * and the day before and the ends the earlier.
         */
        private fun lagsOn(date: LocalDate): Boolean = date in lagDates || date > lagDatesUntil

        /**
         * [date]'s day, every step applied. [capIsha] is ruling R62's cap on a limited Isha
         * ([limited]); off only for reading the next day's end of eating, which needs no Isha.
         */
        fun day(date: LocalDate, capIsha: Boolean = true): PrayerDay {
            val own = clocked(date, banded(date, limited(date, unlimited(date), capIsha)))
            val table = atFixedPoint?.day(date, capIsha)
            val bounded = if (table != null) own.endsNoLaterThan(table) else own
            return bounded.fajrBeforeSunrise(date).ishaAfterMaghrib()
        }

        /**
         * Point 9: Isha stays after the Maghrib shown. Where the Isha shown is at or before it (an
         * authority's own Isha counted from a Maghrib its takdir puts before the real sunset, which the
         * Maghrib shown never precedes), Isha moves later to the minute after Maghrib, Maghrib's end
         * with it, and it is declared not followed (as R82's cells and point 8's Fajr): no day in order
         * can show it. Nothing moves where Isha is already after Maghrib.
         */
        private fun PrayerDay.ishaAfterMaghrib(): PrayerDay {
            if (isha > maghrib) return this
            val moved = maghrib + 1.minutes
            return copy(isha = moved, ends = ends + (Prayer.MAGHRIB to moved), notFollowed = notFollowed + Prayer.ISHA)
        }

        /**
         * Point 8 (ruling R90): Fajr stays before the sunrise shown, and the sunrise precaution gives
         * way before Fajr does. Where the Fajr shown is at or after the sunrise shown:
         * - where the sun's own sunrise at the user's point, rounded down, is after that Fajr, the
         *   sunrise shown moves later to the minute after Fajr, never past the sun's own (an
         *   authority's precaution gives way, never the sun); a borrowed sunrise on a day the sun does
         *   not rise at the point gives way the same way;
         * - only where even the sun's sunrise is at or before that Fajr does Fajr move earlier, to the
         *   minute before the later of the sunrise shown and the sun's own, and it is declared not
         *   followed (as R82's cells): no day in order can show it.
         * Fajr's end is the sunrise shown; the end of eating and imsak stay never after the Fajr shown
         * and never move later. Nothing moves where Fajr is already before the sunrise.
         */
        private fun PrayerDay.fajrBeforeSunrise(date: LocalDate): PrayerDay {
            if (fajr < sunrise) return this
            val sun = sky(point).sunrise(date)?.let(::endOf)
            if (sun == null || sun > fajr) {
                val moved = fajr + 1.minutes
                return copy(sunrise = moved, ends = ends + (Prayer.FAJR to moved))
            }
            val moved = maxOf(sunrise, sun)
            val earlier = moved - 1.minutes
            return copy(
                fajr = earlier,
                sunrise = moved,
                endOfEating = minOf(endOfEating, earlier),
                imsak = imsak?.let { minOf(it, earlier) },
                ends = ends + (Prayer.FAJR to moved),
                notFollowed = notFollowed + Prayer.FAJR,
            )
        }

        /**
         * Point 7 (ruling R89): [this] with its sunrise, end of eating and imsak never after [table]'s,
         * the day the same method shows at the fixed point alone, after every step that moves an end
         * (the end of eating held to the Fajr shown, the rate limit, the clock rule). Each is already the
         * earlier over the two points before those steps; but the end of eating is then held to the
         * Fajr shown, the later of the two points', while at the table's own point it is held to that
         * point's own Fajr, which can be the earlier by a minute (Charleroi within the Brussels table's
         * reach, on the days EMB's eating dawn at Brussels comes a minute after its own Fajr). Nothing
         * else moves, and nothing moves where the bound does not bind.
         */
        private fun PrayerDay.endsNoLaterThan(table: PrayerDay): PrayerDay {
            val sunriseHere = minOf(this.sunrise, table.sunrise)
            val eatingHere = minOf(this.endOfEating, table.endOfEating)
            val imsakHere = this.imsak?.let { own -> table.imsak?.let { minOf(own, it) } ?: own }
            if (sunriseHere == this.sunrise && eatingHere == this.endOfEating && imsakHere == this.imsak) return this
            return copy(
                sunrise = sunriseHere,
                endOfEating = eatingHere,
                imsak = imsakHere,
                ends = ends.mapValues { (prayer, end) -> if (prayer == Prayer.FAJR) minOf(end, sunriseHere) else end },
            )
        }

        /**
         * Point 6: [day] under the method's [ClockRule] (ruling R82), read at each of the place's
         * points. Starts are the latest over the start points, ends the earliest over [here], each
         * with the method's margins only (the rule's times carry the authority's own minutes).
         * - Sunrise: the earlier of the rule's and the day's own; the rule's alone on a polar day.
         * - Fajr: the rule's where it is before that sunrise; otherwise the day's own, before it, and
         *   the rule's Fajr is declared not followed.
         * - Dhuhr and Asr: the rule's.
         * - Maghrib: the later of the rule's and the day's own; the rule's alone where the sun does
         *   not set (and its sunset stands in for the day's).
         * - Isha: the rule's where it is after the Maghrib shown; otherwise the day's own, after it.
         * - The end of eating: the rule's where the rule's Fajr is shown (the authority's own dawn as
         *   an end, spec §3.3), never after that Fajr; otherwise the earliest of the rule's, the day's
         *   own and the Fajr shown.
         * A time shown from the rule is set by rule exactly where the rule calls it an estimate; the
         * rule's own declarations join [PrayerDay.notFollowed]. A polar day whose every time the rule
         * gives is no longer [PrayerDay.polar]: nothing on it follows the nearest latitude.
         */
        private fun clocked(date: LocalDate, day: PrayerDay): PrayerDay {
            val rule = method.clockRule ?: return day
            val at = (here + method.startPoints).distinct().associateWith { rule.on(date, it, zone) }
            val forStarts = (startHere + method.startPoints).map { at.getValue(it) }
            val forEnds = here.map { at.getValue(it) }
            val setByRule = day.setByRule.toMutableSet()
            val notFollowed = day.notFollowed.toMutableSet()
            at.values.forEach { notFollowed += it.notFollowed }

            fun start(pick: (ClockTimes) -> ClockTime?, margin: Int): Ruled? {
                val times = forStarts.map { pick(it) ?: return null }
                return Ruled(startOf(times.maxOf { it.epochSeconds } + margin), times.any { it.estimated })
            }

            fun end(pick: (ClockTimes) -> ClockTime?, margin: Int): Ruled? {
                val times = forEnds.map { pick(it) ?: return null }
                return Ruled(endOf(times.minOf { it.epochSeconds } + margin), times.any { it.estimated })
            }

            val fromRule = mutableSetOf<Prayer>()

            /** [ruled] shown for [prayer]: set by rule exactly where the rule's time is an estimate. */
            fun shown(prayer: Prayer, ruled: Ruled): Instant {
                if (ruled.estimated) setByRule += prayer else setByRule -= prayer
                fromRule += prayer
                return ruled.at
            }

            val margins = method.margins
            val ruleSunrise = end({ it.sunrise }, margins.sunrise)
            val sunrise = if (ruleSunrise != null && (day.polar || ruleSunrise.at <= day.sunrise)) {
                shown(Prayer.SUNRISE, ruleSunrise)
            } else {
                day.sunrise
            }
            val ruleFajr = start({ it.fajr }, margins.fajr)
            val fajr = if (ruleFajr != null && ruleFajr.at < sunrise) {
                shown(Prayer.FAJR, ruleFajr)
            } else {
                if (ruleFajr != null) notFollowed += Prayer.FAJR
                minOf(day.fajr, sunrise - 1.minutes)
            }
            val dhuhr = start({ it.dhuhr }, margins.dhuhr)?.let { shown(Prayer.DHUHR, it) } ?: day.dhuhr
            val standard = start({ it.asrStandard }, margins.asr)
            val hanafi = start({ it.asrHanafi }, margins.asr)
            val (ownSchool, otherSchool) = if (school == AsrSchool.HANAFI) hanafi to standard else standard to hanafi
            val asr = ownSchool?.let { shown(Prayer.ASR, it) } ?: day.asr
            val asrOther = otherSchool?.at ?: day.asrOther
            val noSunset = at.values.all { it.noSunset }
            val ruleMaghrib = start({ it.maghrib }, margins.maghrib)
            val maghrib = if (ruleMaghrib != null && (noSunset || ruleMaghrib.at >= day.maghrib)) {
                shown(Prayer.MAGHRIB, ruleMaghrib)
            } else {
                day.maghrib
            }
            val ruleSunset = forEnds.map { it.sunset }.takeIf { noSunset && it.all { s -> s != null } }?.minOf { it!! }
            val sunset = ruleSunset?.let(::endOf) ?: day.sunset
            val ruleIsha = start({ it.isha }, margins.isha)
            val isha = if (ruleIsha != null && ruleIsha.at > maghrib) {
                shown(Prayer.ISHA, ruleIsha)
            } else {
                maxOf(day.isha, maghrib + 1.minutes)
            }
            val ruleEating = forEnds.map { it.endOfEating }.takeIf { it.all { e -> e != null } }
                ?.minOf { it!! }?.let { endOf(it + method.endOfEatingMarginSeconds) }
            // Where the rule's Fajr is shown its dawn is the authority's own end (spec §3.3); otherwise
            // the fast begins at the earliest of the rule's, the day's own and the Fajr shown.
            val endOfEating = if (ruleFajr != null && fajr == ruleFajr.at && ruleEating != null) {
                minOf(ruleEating, fajr)
            } else {
                listOfNotNull(day.endOfEating, ruleEating, fajr).min()
            }
            // Red twilight ends Maghrib only where it is after the Maghrib shown (a rule's Maghrib can be later).
            val redTwilight = here.mapNotNull { sky(it).altitudeTime(date, -Ends.RED_TWILIGHT_DEG, morning = false) }
                .minOrNull()?.let(::endOf)?.takeIf { it > maghrib }
            val starts = mapOf(Prayer.FAJR to fajr, Prayer.DHUHR to dhuhr, Prayer.ASR to asr, Prayer.MAGHRIB to maghrib)
            val ends = Ends.of(
                sunrise = sunrise,
                standardAsr = if (school == AsrSchool.STANDARD) asr else asrOther,
                sunset = sunset,
                earliestIsha = isha,
                maghribEndCap = redTwilight,
                nextEndOfEating = null,
            ).filter { (prayer, end) -> end > (starts[prayer] ?: isha) }
            return day.copy(
                fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr, asrOther = asrOther, maghrib = maghrib,
                isha = isha, sunset = sunset, endOfEating = endOfEating, imsak = day.imsak?.let { minOf(it, fajr) },
                ends = ends, setByRule = setByRule, notFollowed = notFollowed,
                polar = day.polar && !fromRule.containsAll(Prayer.entries),
            )
        }

        /**
         * Ruling R65: where DUM RT's rule is read at the user's own point (no table's point beside
         * it), the locality whose table DUM RT would print may lie [HighLatRule.DumRtSummer.bandDeg]
         * of latitude away, and around the white nights the real dawn moves minutes with every few km.
         * On every night Fajr and Isha are the latest, and the end of sahur the earliest, that a table
         * printed at any latitude within that band would give (sampled every [BAND_STEP_DEG], each
         * sample a table's own point), and never less safe than [own].
         */
        private fun banded(date: LocalDate, own: PrayerDay): PrayerDay {
            val rule = method.highLatitude as? HighLatRule.DumRtSummer ?: return own
            if (method.fixedPoint != null || own.polar) return own
            val tables = bandTables(rule).map { it.day(date) }
            var fajr = own.fajr
            var isha = own.isha
            var setByRule = own.setByRule
            val latestFajr = tables.maxBy { it.fajr }
            if (latestFajr.fajr > fajr) {
                fajr = latestFajr.fajr
                setByRule = if (Prayer.FAJR in latestFajr.setByRule) setByRule + Prayer.FAJR else setByRule - Prayer.FAJR
            }
            val endOfEating = minOf(own.endOfEating, tables.minOf { it.endOfEating })
            val latestIsha = tables.maxBy { it.isha }
            if (latestIsha.isha > isha) {
                isha = latestIsha.isha
                setByRule = if (Prayer.ISHA in latestIsha.setByRule) setByRule + Prayer.ISHA else setByRule - Prayer.ISHA
            }
            if (fajr == own.fajr && endOfEating == own.endOfEating && isha == own.isha) return own
            return own.copy(fajr = fajr, endOfEating = minOf(endOfEating, fajr), isha = isha, setByRule = setByRule)
        }

        /** R65's tables across the band: the method at each sampled latitude, that latitude its own table's point. */
        private fun bandTables(rule: HighLatRule.DumRtSummer): List<Place> {
            val steps = (rule.bandDeg / BAND_STEP_DEG).roundToInt()
            return (-steps..steps).map { k ->
                val at = GeoPoint(point.lat + k * rule.bandDeg / steps, point.lon)
                Place(method.copy(fixedPoint = at), at, zone, school, ramadan, lagDates, lagDatesUntil)
            }
        }

        /**
         * How DUM RT's rule holds on [date]'s morning, null where it does not: [RuleNight.UNCERTAIN]
         * where the night is too close to call ([uncertain]), else [RuleNight.WHITE] where its Fajr
         * dawn is missing at every one of the place's points (the table applies its rule on exactly
         * those nights, town by town). A night whose dawn is real at either point keeps the real dawn:
         * an earlier end of suhoor and, on its evening, the real Isha, both on the safe side.
         */
        private fun ruleNight(date: LocalDate): RuleNight? {
            val rule = method.highLatitude as? HighLatRule.DumRtSummer ?: return null
            if (uncertain(rule, date)) return RuleNight.UNCERTAIN
            val white = here.all { HighLatitude.sign(sky(it), method.highLatitude, fajrTwilight, date)?.estimated != false }
            return if (white) RuleNight.WHITE else null
        }

        /**
         * Ruling R61: whether [date]'s morning is too close to call, the sun's lowest over the night
         * before it near the Fajr depression. At a table's own point (the method's fixed point, whose
         * table the whole unit follows) within [HighLatRule.DumRtSummer.knifeEdgeDeg], where DUM RT's
         * sun and ours may disagree; at the user's own point within [HighLatRule.DumRtSummer.bandDeg],
         * where the locality whose table DUM RT would print may lie that much latitude away (a degree
         * of latitude moves the sun's lowest a degree).
         */
        private fun uncertain(rule: HighLatRule.DumRtSummer, date: LocalDate): Boolean {
            val table = method.fixedPoint
            if (table != null) return nearDepression(table, date, rule.knifeEdgeDeg)
            return here.any { nearDepression(it, date, maxOf(rule.knifeEdgeDeg, rule.bandDeg)) }
        }

        /** Whether the sun's lowest before [date]'s morning at [point] is within [degrees] of the Fajr depression. */
        private fun nearDepression(point: GeoPoint, date: LocalDate, degrees: Double): Boolean {
            val lowest = lowestMoment(sky(point), date)
            val declination = SolarMath.sun(SolarMath.julianDay(lowest)).declinationDeg + fajrTwilight.biasOn(date)
            val towardsPole = if (point.lat >= 0) declination else -declination
            return abs(90.0 - abs(point.lat) - towardsPole - fajrTwilight.degreesOn(date)) < degrees
        }

        /** The sun's lower culmination before [date]'s noon under [sky]: its lowest over the night before. */
        private fun lowestMoment(sky: Sky, date: LocalDate): Double = sky.clock(date).transit() - SolarMath.SECONDS_PER_DAY / 2

        /** The day before the rate limit. */
        private fun unlimited(date: LocalDate): PrayerDay {
            val offsets = Offsets(method, date, ramadan)
            return computeAt(here, method.startPoints, date, offsets) ?: polarDay(date, offsets)
        }

        // --- The daily rate limit ---------------------------------------------------------------

        private val fajrTwilight = Twilight(
            morning = true,
            degreesOn = { d -> fajrDegrees(method, d) },
            biasOn = { d -> fajrBias(method, d) },
        )
        private val ishaTwilight: Twilight? = (method.isha as? IshaRule.Angle)?.let { isha ->
            Twilight(morning = false, degreesOn = { d -> ishaDegrees(method, isha, d) })
        }

        /** The sign the end of eating is read from: the Fajr dawn, or the method's own dawn angle. */
        private val eatingTwilight: Twilight = when (val rule = method.endOfEating) {
            is EndOfEating.DawnAngle -> Twilight(morning = true, degreesOn = { d -> rule.degreesOn(d) })
            is EndOfEating.DawnOrMiddle -> Twilight(morning = true, degreesOn = { rule.degrees })
            else -> fajrTwilight
        }

        /** Whether the end of eating is read from its own dawn angle ([eatingTwilight]) rather than the Fajr dawn. */
        private val ownEatingDawn: Boolean =
            method.endOfEating is EndOfEating.DawnAngle || method.endOfEating is EndOfEating.DawnOrMiddle

        /**
         * The rule that sets an end-of-eating dawn the sun does not reach: the middle of the night for
         * [EndOfEating.DawnOrMiddle], whatever the method's own rule; the method's own otherwise.
         */
        private val eatingRule: HighLatRule =
            if (method.endOfEating is EndOfEating.DawnOrMiddle) HighLatRule.Legacy(HighLatRule.Legacy.MIDDLE) else method.highLatitude

        /**
         * [today] with its Isha never more than [DAILY_LIMIT] earlier than any earlier day's (less
         * [DAILY_LIMIT] a day) and its end of eating and imsak never later than any earlier day's
         * (plus [DAILY_LIMIT] a day): `max over k ≥ 0 of U(D − k) + k·(1 day − 20 min)` for Isha,
         * the mirror for the ends, over the days before the limit, in UTC so that a clock change
         * moves nothing. It applies only where the sign is missing on some day of the look back
         * (ruling R22: read over the whole look back); the search for the value stops once no
         * earlier day can move it, since no Isha lies much past the middle of its night and no
         * dawn much before it. [capIsha] holds a limited Isha before the next day's end of eating
         * (ruling R62); off only for reading that next day's end, which needs no Isha.
         */
        private fun limited(date: LocalDate, today: PrayerDay, capIsha: Boolean = true): PrayerDay {
            if (neverMissing) return today
            val offsets = Offsets(method, date, ramadan)
            val ishaDays = lookbackDays(date, ishaAbove(offsets))
            val eatingDays = lookbackDays(date, eatingBelow(offsets))
            val limitIsha = ruleNight(date.plus(1, DateTimeUnit.DAY)) == null && missingLately(date, ishaDays, evening = true)
            val limitEating = ruleNight(date) == null && missingLately(date, eatingDays, evening = false)
            if (!limitIsha && !limitEating) return today

            val step = DAILY_LIMIT.inWholeSeconds.toDouble()
            val ishaReach = middleOfTonight(date) + ishaAbove(offsets)
            val eatingReach = middleOfLastNight(date) - eatingBelow(offsets)
            var isha = today.isha
            var eating = today.endOfEating
            var imsak = today.imsak
            for (k in 1..maxOf(ishaDays, eatingDays)) {
                // Past this, no earlier day can move a value: each lies within its bound, and the
                // bound falls behind by the step less a minute of drift a day.
                val gain = k * (step - DRIFT_SECONDS)
                val ishaOpen = limitIsha && k <= ishaDays && isha.epochSeconds < ishaReach - gain
                val eatingOpen = limitEating && k <= eatingDays &&
                    minOf(eating, imsak ?: eating).epochSeconds > eatingReach + gain
                if (!ishaOpen && !eatingOpen) break
                val earlier = unlimited(date.minus(k, DateTimeUnit.DAY))
                val shift = k.days
                val allowance = DAILY_LIMIT * k
                if (ishaOpen) isha = maxOf(isha, earlier.isha + shift - allowance)
                if (eatingOpen) {
                    eating = minOf(eating, earlier.endOfEating + shift + allowance)
                    imsak = imsak?.let { own -> earlier.imsak?.let { minOf(own, it + shift + allowance) } ?: own }
                }
            }
            // Ruling R62: the limit never holds Isha into the next night's end. A limited Isha is
            // at most a minute before the next day's end of eating as shown, and never earlier
            // than the rule's own Isha: `max(own, min(limited, next end − 1 min))`. The next day's
            // end is its final one (ruling R92): after its clock rule, its band, its table's bound
            // and R90, the same steps the end shown for that day goes through, so a limited Isha
            // never lands on the end shown.
            if (capIsha && isha > today.isha) {
                val nextEnd = day(date.plus(1, DateTimeUnit.DAY), capIsha = false).endOfEating
                isha = maxOf(today.isha, minOf(isha, nextEnd - 1.minutes))
            }
            if (isha == today.isha && eating == today.endOfEating && imsak == today.imsak) return today
            return today.copy(
                isha = isha,
                endOfEating = minOf(eating, today.fajr),
                imsak = imsak,
                setByRule = if (isha > today.isha) today.setByRule + Prayer.ISHA else today.setByRule,
            )
        }

        /**
         * Whether no sign this method reads can go missing at any of its points on any day of any
         * year (the latitude alone decides): then the rate limit has nothing to do. Every angle,
         * with its dip and the largest declination bias, is met with a degree to spare at the
         * lowest the sun can sink there, which also means the sun rises and sets.
         */
        private val neverMissing: Boolean by lazy {
            val fajr = (method.fajrAngleByDayOfYear?.max() ?: method.fajrAngle) + method.twilightDipDeg
            val isha = when (val rule = method.isha) {
                is IshaRule.Angle -> (method.ishaAngleByDayOfYear?.max() ?: rule.degrees) + method.twilightDipDeg
                is IshaRule.AfterMaghrib -> 0.0
            }
            val eating = when (val rule = method.endOfEating) {
                is EndOfEating.DawnAngle -> rule.deepest
                is EndOfEating.DawnOrMiddle -> rule.degrees
                else -> 0.0
            }
            val bias = method.fajrDeclinationBias?.let { h -> abs(h.c0) + h.terms.sumOf { (a, b) -> abs(a) + abs(b) } } ?: 0.0
            val deepest = maxOf(fajr, isha, eating, 1.0)
            (here + method.startPoints).all { 90.0 - abs(it.lat) - MAX_DECLINATION_DEG - bias >= deepest + 1.0 }
        }

        /**
         * Whether the Isha (for [evening]) or the end of eating was set by rule on [date] or any
         * of the [days] before it: its sign missing at one of the day's points, or the day polar
         * there. Days whose sun surely gives the sign are passed over without a crossing.
         */
        private fun missingLately(date: LocalDate, days: Int, evening: Boolean): Boolean {
            val points = if (evening) startHere + method.startPoints else here
            val twilight = if (evening) ishaTwilight else eatingTwilight
            return (0..days).any { k ->
                val d = date.minus(k, DateTimeUnit.DAY)
                points.any { point -> missingOn(sky(point), twilight, d) }
            }
        }

        private fun missingOn(sky: Sky, twilight: Twilight?, date: LocalDate): Boolean {
            if (twilight == null) {
                // Isha after Maghrib is missing only where the day is polar and borrows it.
                return !HighLatitude.surelyRisesAndSets(sky, date) && (sky.sunrise(date) == null || sky.sunset(date) == null)
            }
            if (HighLatitude.surelyOccurs(sky, twilight, date)) return false
            if (sky.sunrise(date) == null || sky.sunset(date) == null) return true
            return HighLatitude.sign(sky, method.highLatitude, twilight, date)?.estimated != false
        }

        /** Enough days for a jump of a whole night and the bound's own room, at most [MAX_LOOKBACK_DAYS]. */
        private fun lookbackDays(date: LocalDate, room: Double): Int {
            val sky = sky(here.first())
            val night = sky.sunset(date)?.let { dusk -> sky.sunrise(date.plus(1, DateTimeUnit.DAY))?.minus(dusk) }
                ?: SolarMath.SECONDS_PER_DAY
            val days = ceil((night + room) / (DAILY_LIMIT.inWholeSeconds - DRIFT_SECONDS)).toInt() + 2
            return minOf(MAX_LOOKBACK_DAYS, days)
        }

        /** The sun's lower culmination after [date]'s noon, the latest over every point. */
        private fun middleOfTonight(date: LocalDate): Double =
            (startHere + method.startPoints).maxOf { sky(it).clock(date).transit() } + SolarMath.SECONDS_PER_DAY / 2

        /** The sun's lower culmination before [date]'s noon, the earliest over the given and fixed points. */
        private fun middleOfLastNight(date: LocalDate): Double =
            here.minOf { sky(it).clock(date).transit() } - SolarMath.SECONDS_PER_DAY / 2

        /**
         * How far past the middle of its night a shown Isha can lie, from any source: a real dusk is
         * before the sun's lower culmination; an estimate at most half the night after sunset
         * (more for an authority fraction over a half); an Isha after Maghrib its minutes after a
         * sunset that is before it; a Ramadan floor its own minutes after Maghrib, the same as an
         * Isha after Maghrib, on the days it can apply; the authority's offsets and a minute of
         * rounding on top.
         */
        private fun ishaAbove(offsets: Offsets): Double {
            val afterMaghrib = when (val rule = method.isha) {
                is IshaRule.Angle -> method.ramadan?.ishaFloorMinutesAfterMaghrib?.let {
                    it * 60.0 + abs(offsets.authority(Prayer.MAGHRIB)) + abs(method.ramadan.maghribExtraSeconds)
                } ?: 0.0
                is IshaRule.AfterMaghrib -> maxOf(rule.minutes, rule.ramadanMinutes) * 60.0 +
                    abs(offsets.authority(Prayer.MAGHRIB)) + abs(method.ramadan?.maghribExtraSeconds ?: 0)
            }
            val white = (method.highLatitude as? HighLatRule.DumRtSummer)?.let {
                it.ishaAfterMaghribMinutes * 60.0 + abs(offsets.total(Prayer.MAGHRIB)) + abs(method.ramadan?.maghribExtraSeconds ?: 0)
            } ?: 0.0
            return maxOf(afterMaghrib, white) + wider(evening = true) + abs(offsets.total(Prayer.ISHA)) + ROUNDING_AND_SLACK
        }

        /** The mirror of [ishaAbove]: how far before the middle of its night an end of eating or imsak can lie. */
        private fun eatingBelow(offsets: Offsets): Double {
            val fajr = abs(offsets.authority(Prayer.FAJR)) + abs(method.endOfEatingMarginSeconds) +
                abs(offsets.total(Prayer.FAJR)) + method.fajrAfterDawnMinutes * 60.0
            val beforeFajr = ((method.endOfEating as? EndOfEating.MinutesBeforeFajr)?.minutes ?: 0) * 60.0
            val imsak = (method.imsakMinutesBeforeFajr ?: 0) * 60.0
            val white = (method.highLatitude as? HighLatRule.DumRtSummer)?.let {
                it.sahurBeforeSunriseMinutes * 60.0 + abs(offsets.total(Prayer.SUNRISE))
            } ?: 0.0
            return maxOf(fajr + maxOf(beforeFajr, imsak), white) + wider(evening = false) + ROUNDING_AND_SLACK
        }

        /** An authority's fraction of the night over a half, as seconds of the longest night. */
        private fun wider(evening: Boolean): Double {
            val rule = method.highLatitude as? HighLatRule.NightFraction ?: return 0.0
            val fraction = if (evening) rule.ishaFraction else rule.fajrFraction
            return maxOf(0.0, fraction - 0.5) * SolarMath.SECONDS_PER_DAY
        }

        // --- Polar days ---------------------------------------------------------------------------

        /**
         * Rule 5: where [date] has no sunrise or no sunset at the place, the day at the nearest
         * latitude towards the equator where it has both stands in, but only for what the place
         * cannot give itself ([atThePlace]).
         */
        private fun polarDay(date: LocalDate, offsets: Offsets): PrayerDay {
            var steps = 1
            while (true) {
                val degrees = POLAR_STEP_DEG * steps
                val nearer = here.map { towardsEquator(it, degrees) }
                val startPoints = method.startPoints.map { towardsEquator(it, degrees) }
                val day = computeAt(nearer, startPoints, date, offsets)
                if (day != null) return atThePlace(day, date, offsets)
                check(nearer.any { it.lat != 0.0 }) { "${method.id}: no day at the equator on $date" }
                steps++
            }
        }

        private fun towardsEquator(point: GeoPoint, degrees: Double): GeoPoint =
            if (abs(point.lat) <= degrees) point.copy(lat = 0.0) else point.copy(lat = point.lat - sign(point.lat) * degrees)

        /**
         * Ruling R23: a polar [date] computed at the place wherever the place can give the time, and
         * borrowed from the [substitute] latitude's day only where it cannot.
         * - The place gives Fajr (and the end of eating and imsak) from its own last night, its
         *   sunset the day before to its sunrise: its real dawn, else its own estimate. It gives its
         *   own sunrise where it rises, Dhuhr always, Asr while its sun is up at noon, Maghrib where
         *   it sets, and Isha from its own tonight, its sunset to its sunrise the next day (an Isha
         *   after Maghrib: from its own Maghrib). A time it gives is set by rule only when it is its
         *   own estimate; the rate limit applies to it as to any other day.
         * - What is borrowed is bound by the place's own real signs (ruling R19): a start no earlier
         *   than the place's own, an end no later; and it is set by rule unless the place's own
         *   minute is the one shown (ruling R21).
         * A time counts as given only where every point it is read at gives it.
         */
        private fun atThePlace(substitute: PrayerDay, date: LocalDate, offsets: Offsets): PrayerDay {
            val own = here.map { ownSigns(sky(it), date) }
            val ownStarts = (if (endsOnly) own.drop(1) else own) + method.startPoints.map { ownSigns(sky(it), date) }
            val setByRule = mutableSetOf<Prayer>()
            val borrowed = mutableSetOf<Prayer>()

            /**
             * A start: the place's own (latest over its points), or the substitute's no earlier than
             * the place's real one; [prayer] (null for the other school's Asr) is the key it marks.
             */
            fun start(prayer: Prayer?, theirs: Instant, given: (OwnSigns) -> Sign?, real: (OwnSigns) -> Double?, offset: Int): Instant {
                val signs = ownStarts.map(given)
                if (signs.all { it != null }) {
                    if (prayer != null && signs.any { it!!.estimated }) setByRule += prayer
                    return startOf(signs.maxOf { it!!.epochSeconds } + offset)
                }
                if (prayer != null) borrowed += prayer
                val ownReal = ownStarts.mapNotNull(real).maxOrNull()?.let { startOf(it + offset) }
                val shown = ownReal?.let { maxOf(theirs, it) } ?: theirs
                if (prayer != null && shown != ownReal) setByRule += prayer
                return shown
            }

            /** An end: the place's own (earliest over its points), or the substitute's no later than the place's real one. */
            fun end(prayer: Prayer?, theirs: Instant, given: (OwnSigns) -> Sign?, real: (OwnSigns) -> Double?, offset: Int): Instant {
                val signs = own.map(given)
                if (signs.all { it != null }) {
                    if (prayer != null && signs.any { it!!.estimated }) setByRule += prayer
                    return endOf(signs.minOf { it!!.epochSeconds } + offset)
                }
                if (prayer != null) borrowed += prayer
                val ownReal = own.mapNotNull(real).minOrNull()?.let { endOf(it + offset) }
                val shown = ownReal?.let { minOf(theirs, it) } ?: theirs
                if (prayer != null && shown != ownReal) setByRule += prayer
                return shown
            }

            fun Double?.real(): Sign? = this?.let { Sign(it, estimated = false) }

            val fajrOffset = method.fajrAfterDawnMinutes * 60 + offsets.total(Prayer.FAJR)
            val fajr = start(Prayer.FAJR, substitute.fajr, { it.dawn }, { it.realDawn }, fajrOffset)
            val sunrise = end(Prayer.SUNRISE, substitute.sunrise, { it.sunrise.real() }, { it.sunrise }, offsets.total(Prayer.SUNRISE))
            val transit = startOf(ownStarts.maxOf { it.transit } + offsets.total(Prayer.DHUHR))
            val dhuhr = method.fixedDhuhrLocalMinutes?.let { maxOf(transit, localTime(date, it, zone)) } ?: transit
            val asr = start(Prayer.ASR, substitute.asr, { it.asr.real() }, { it.asr }, offsets.total(Prayer.ASR))
            val asrOther = start(null, substitute.asrOther, { it.asrOther.real() }, { it.asrOther }, offsets.total(Prayer.ASR))
            val maghribOffset = offsets.total(Prayer.MAGHRIB) + offsets.ramadanMaghrib
            val maghrib = start(Prayer.MAGHRIB, substitute.maghrib, { it.sunset.real() }, { it.sunset }, maghribOffset)
            val isha = when (val rule = method.isha) {
                is IshaRule.Angle -> {
                    val angle = start(Prayer.ISHA, substitute.isha, { it.dusk }, { it.realDusk }, offsets.total(Prayer.ISHA))
                    // Ruling R74: the Ramadan floor on top, same as the ordinary (non-polar) day.
                    offsets.ishaFloorMinutes?.let { maxOf(angle, maghrib + it.minutes) } ?: angle
                }
                is IshaRule.AfterMaghrib -> start(
                    Prayer.ISHA, substitute.isha, { it.sunset.real() }, { it.sunset },
                    offsets.authority(Prayer.MAGHRIB) + offsets.ramadanMaghrib + offsets.ishaMinutes(rule) * 60 +
                        offsets.total(Prayer.ISHA),
                )
            }
            val sunset = end(null, substitute.sunset, { it.sunset.real() }, { it.sunset }, 0)
            val dawnEndOffset = offsets.authority(Prayer.FAJR) + method.endOfEatingMarginSeconds
            val endOfEating = when (val rule = method.endOfEating) {
                EndOfEating.SameAsFajrDawn -> end(null, substitute.endOfEating, { it.dawn }, { it.realDawn }, dawnEndOffset)
                is EndOfEating.DawnAngle, is EndOfEating.DawnOrMiddle ->
                    end(null, substitute.endOfEating, { it.eatingDawn }, { it.realEatingDawn }, method.endOfEatingMarginSeconds)
                is EndOfEating.MinutesBeforeFajr ->
                    end(null, substitute.endOfEating, { it.dawn }, { it.realDawn }, dawnEndOffset - rule.minutes * 60)
            }
            val imsak = substitute.imsak?.let { theirs ->
                end(null, theirs, { it.dawn }, { it.realDawn }, dawnEndOffset - (method.imsakMinutesBeforeFajr ?: 0) * 60)
            }

            // Dhuhr ends at the Standard Asr shown where the place gives it, else at the earlier of
            // the substitute's and the place's own; Maghrib at the earlier of Isha and the place's
            // own red twilight (and the substitute's own end, where Isha is borrowed).
            val standardAsr = if (school == AsrSchool.STANDARD) asr else asrOther
            val ownRedTwilight = own.mapNotNull { it.redTwilight }.minOrNull()?.let(::endOf)
            val maghribEndCap = listOfNotNull(
                ownRedTwilight,
                substitute.ends[Prayer.MAGHRIB].takeIf { Prayer.ISHA in borrowed },
            ).minOrNull()
            val ends = Ends.of(
                sunrise = sunrise,
                standardAsr = if (Prayer.ASR in borrowed) minOf(standardAsr, substitute.ends.getValue(Prayer.DHUHR)) else standardAsr,
                sunset = sunset,
                earliestIsha = isha,
                maghribEndCap = maghribEndCap,
                nextEndOfEating = null,
            )
            return substitute.copy(
                fajr = fajr,
                sunrise = sunrise,
                dhuhr = dhuhr,
                asr = asr,
                asrOther = asrOther,
                maghrib = maghrib,
                isha = isha,
                sunset = sunset,
                endOfEating = minOf(endOfEating, fajr),
                imsak = imsak,
                ends = ends,
                setByRule = setByRule,
                polar = true,
            )
        }

        /**
         * What the place has on a polar [date] at [sky]'s point: its dawn and dusk where their own
         * nights exist (the real sign, else its own estimate), its real crossings for binding what
         * is borrowed, and its sunrise, transit, Asr (while its sun is up at noon), sunset and red
         * twilight where they occur.
         */
        private fun ownSigns(sky: Sky, date: LocalDate): OwnSigns {
            val clock = sky.clock(date)
            val transit = clock.transit()
            // Asr is a shadow's length: a place whose sun stays below the horizon has none.
            val declination = SolarMath.sun(SolarMath.julianDay(transit)).declinationDeg
            val sunUp = 90.0 - abs(sky.point.lat - declination) > method.horizonDeg
            val asrBias = fajrBias(method, date) * method.asrBiasFactor
            val sunrise = sky.sunrise(date)
            val sunset = sky.sunset(date)
            val lastNight = sunrise != null && sky.sunset(date.minus(1, DateTimeUnit.DAY)) != null
            val tonight = sunset != null && sky.sunrise(date.plus(1, DateTimeUnit.DAY)) != null
            val rule = method.highLatitude
            return OwnSigns(
                dawn = if (lastNight) HighLatitude.sign(sky, rule, fajrTwilight, date) else null,
                eatingDawn = if (lastNight && ownEatingDawn) HighLatitude.sign(sky, eatingRule, eatingTwilight, date) else null,
                realDawn = sky.altitudeTime(date, -fajrDegrees(method, date), morning = true, biasDeg = fajrBias(method, date)),
                realEatingDawn = if (ownEatingDawn) sky.altitudeTime(date, -eatingTwilight.degreesOn(date), morning = true) else null,
                sunrise = sunrise,
                transit = transit,
                asr = if (sunUp) clock.asr(school.shadowFactor, method.asrModel, asrBias) else null,
                asrOther = if (sunUp) clock.asr(school.other.shadowFactor, method.asrModel, asrBias) else null,
                sunset = sunset,
                dusk = if (tonight) ishaTwilight?.let { HighLatitude.sign(sky, rule, it, date) } else null,
                realDusk = (method.isha as? IshaRule.Angle)?.let { sky.altitudeTime(date, -ishaDegrees(method, it, date), morning = false) },
                redTwilight = sky.altitudeTime(date, -Ends.RED_TWILIGHT_DEG, morning = false),
            )
        }

        // --- One day at its points ------------------------------------------------------------------

        /**
         * The day at the points [here] (every time; one point, or a fixed point and the user's) and
         * [startPoints] (starts only), or null where [date] has no sunrise or no sunset there.
         */
        private fun computeAt(here: List<GeoPoint>, startPoints: List<GeoPoint>, date: LocalDate, offsets: Offsets): PrayerDay? {
            val ruleShifts = when (method.dayRule) {
                DayRule.SAME_DAY -> SAME_DAY
                DayRule.NEIGHBOURS_MUIS -> NEIGHBOURS
                DayRule.LAG_DATES_UQ -> if (lagsOn(date)) LAG else SAME_DAY
            }
            // Ruling R29: a phase year has no 29 February of its own, so that date is both the phase
            // year's 28 February (its own clock) and its 1 March (the next date's, moved back a day).
            val shifts = if (method.phaseYear != null && date.month == Month.FEBRUARY && date.day == 29) {
                (ruleShifts + ruleShifts.map { it + 1 }).distinct()
            } else {
                ruleShifts
            }
            val skies = here.map { sky(it) }
            val atHere = skies.flatMap { sky -> shifts.map { rawEvents(sky, date, it) ?: return null } }
            val forStarts = (if (endsOnly) atHere.drop(shifts.size) else atHere) + startPoints.flatMap { p ->
                val sky = sky(p)
                shifts.map { rawEvents(sky, date, it) ?: return null }
            }
            val sunsetHere = skies.minOf { it.sunset(date) ?: return null }

            fun latest(event: (RawEvents) -> Double) = forStarts.maxOf(event)
            fun earliest(event: (RawEvents) -> Double) = atHere.minOf(event)
            fun total(prayer: Prayer) = offsets.total(prayer)
            val ramadanMaghrib = offsets.ramadanMaghrib

            val fajrOffset = method.fajrAfterDawnMinutes * 60 + total(Prayer.FAJR)
            var fajr = startOf(latest { it.dawn } + fajrOffset)
            // TimetableMethod.dayAroundDhuhrMinutes: the authority's day is never shorter than twice
            // these minutes around its own Dhuhr (the transit plus its Dhuhr minutes): its sunrise no
            // later than Dhuhr − minutes, its Maghrib no earlier than Dhuhr + minutes, each with the
            // event's own margin (Diyanet's 5-hour winter day at Trondheim).
            val halfDay = method.dayAroundDhuhrMinutes?.let { it * 60.0 }
            val authorityDhuhr = latest { it.transit } + offsets.authority(Prayer.DHUHR)
            val sunriseRaw = earliest { it.sunrise } + total(Prayer.SUNRISE)
            val sunrise = endOf(
                if (halfDay == null) sunriseRaw else minOf(sunriseRaw, authorityDhuhr - halfDay + method.margins[Prayer.SUNRISE]),
            )
            val transit = startOf(latest { it.transit } + total(Prayer.DHUHR))
            val dhuhr = method.fixedDhuhrLocalMinutes?.let { maxOf(transit, localTime(date, it, zone)) } ?: transit
            val asr = startOf(latest { it.asr } + total(Prayer.ASR))
            val asrOther = startOf(latest { it.asrOther } + total(Prayer.ASR))
            val maghribRaw = latest { it.sunset }
            val maghribAt = maghribRaw + total(Prayer.MAGHRIB) + ramadanMaghrib
            val maghrib = startOf(
                if (halfDay == null) maghribAt else maxOf(maghribAt, authorityDhuhr + halfDay + method.margins[Prayer.MAGHRIB]),
            )
            val ishaRaw = when (val rule = method.isha) {
                is IshaRule.Angle -> latest { it.dusk!! }
                is IshaRule.AfterMaghrib ->
                    maghribRaw + offsets.authority(Prayer.MAGHRIB) + ramadanMaghrib + offsets.ishaMinutes(rule) * 60
            }
            var isha = startOf(ishaRaw + total(Prayer.ISHA))
            // Ruling R74: on the entry's Ramadan dates, an angle Isha is never earlier than the
            // shown Maghrib plus the authority's floor (Sudan: 90 min against a Fiqh Academy imsakiya).
            if (method.isha is IshaRule.Angle) {
                offsets.ishaFloorMinutes?.let { floorMinutes -> isha = maxOf(isha, maghrib + floorMinutes.minutes) }
            }

            // The authority's dawn as an end: its Fajr minutes, without our start margin, rounded down.
            val dawnEnd = endOf(earliest { it.dawn } + offsets.authority(Prayer.FAJR) + method.endOfEatingMarginSeconds)
            var endOfEating = when (val rule = method.endOfEating) {
                EndOfEating.SameAsFajrDawn -> dawnEnd
                is EndOfEating.DawnAngle, is EndOfEating.DawnOrMiddle ->
                    endOf(earliest { it.eatingDawn!! } + method.endOfEatingMarginSeconds)
                // Ruling R27: counted back from the authority's dawn as an end, like imsak, never from
                // our rounded start, which a margin can carry a minute past the printed Fajr.
                is EndOfEating.MinutesBeforeFajr -> dawnEnd - rule.minutes.minutes
            }

            val setByRule = mutableSetOf<Prayer>()
            if (forStarts.any { it.dawnSetByRule }) setByRule += Prayer.FAJR
            if (forStarts.any { it.duskSetByRule }) setByRule += Prayer.ISHA

            // DUM RT's white nights: one printed morning time, sunrise − 121, is both the end of suhoor
            // and the Fajr shown. It is rounded as each: down from the sunrise shown for the end of
            // eating, up from the raw sunrise with the Fajr margin for the start, so that neither is
            // on the wrong side of the printed minute (they differ by a minute at most).
            // Ruling R61: a night too close to call takes each event's safe side of the rule and the
            // real sign: the later Fajr, the earlier end of suhoor (a sun that only just reaches 18°
            // dawns at its lowest, so no table whose sun reaches it dawns before that) and, on the
            // evening before, the later Isha.
            val white = method.highLatitude as? HighLatRule.DumRtSummer
            if (white != null) {
                val ruleFajr = startOf(
                    latest { it.sunrise } + offsets.authority(Prayer.SUNRISE) + method.margins[Prayer.FAJR] -
                        white.sahurBeforeSunriseMinutes * 60,
                )
                val ruleEnd = sunrise - white.sahurBeforeSunriseMinutes.minutes
                when (ruleNight(date)) {
                    RuleNight.WHITE -> {
                        endOfEating = ruleEnd
                        fajr = ruleFajr
                        setByRule += Prayer.FAJR
                    }
                    RuleNight.UNCERTAIN -> {
                        val realFajr = forStarts.filter { !it.dawnSetByRule }.maxOfOrNull { it.dawn }?.let { startOf(it + fajrOffset) }
                        if (realFajr == null || realFajr < ruleFajr) {
                            fajr = ruleFajr
                            setByRule += Prayer.FAJR
                        } else {
                            fajr = realFajr
                        }
                        val lowest = skies.minOf { lowestMoment(it, date) }
                        endOfEating = minOf(ruleEnd, endOf(lowest + offsets.authority(Prayer.FAJR) + method.endOfEatingMarginSeconds))
                    }
                    null -> Unit
                }
                val ruleIsha = maghrib + white.ishaAfterMaghribMinutes.minutes
                when (ruleNight(date.plus(1, DateTimeUnit.DAY))) {
                    RuleNight.WHITE -> {
                        isha = ruleIsha
                        setByRule += Prayer.ISHA
                    }
                    RuleNight.UNCERTAIN -> if (ruleIsha >= isha) {
                        isha = ruleIsha
                        setByRule += Prayer.ISHA
                    }
                    null -> Unit
                }
            }

            val sunset = endOf(sunsetHere)
            val redTwilightEnd = skies
                .mapNotNull { it.altitudeTime(date, -Ends.RED_TWILIGHT_DEG, morning = false) }
                .minOrNull()?.let(::endOf)
            return PrayerDay(
                date = date,
                fajr = fajr,
                sunrise = sunrise,
                dhuhr = dhuhr,
                asr = asr,
                asrOther = asrOther,
                maghrib = maghrib,
                isha = isha,
                sunset = sunset,
                endOfEating = minOf(endOfEating, fajr),
                // An end, like the end of eating: the authority's dawn rounded down, less its minutes.
                imsak = method.imsakMinutesBeforeFajr?.let { dawnEnd - it.minutes },
                // Isha's end is the next day's end of eating: the caller adds it with Ends.withNextDay.
                ends = Ends.of(
                    sunrise = sunrise,
                    standardAsr = if (school == AsrSchool.STANDARD) asr else asrOther,
                    sunset = sunset,
                    earliestIsha = isha,
                    maghribEndCap = redTwilightEnd,
                    nextEndOfEating = null,
                ),
                setByRule = setByRule,
                methodId = method.id,
            )
        }

        /**
         * The astronomy of [date] + [shift] days under [sky], moved by −[shift] days so that it can
         * stand in for [date]'s row; null where that day has no sunrise or no sunset (or, a numerical
         * safety net, no Asr).
         */
        private fun rawEvents(sky: Sky, date: LocalDate, shift: Int): RawEvents? {
            val day = if (shift == 0) date else date.plus(shift, DateTimeUnit.DAY)
            val clock = sky.clock(day)
            val sunrise = sky.sunrise(day) ?: return null
            val sunset = sky.sunset(day) ?: return null
            val asrBias = fajrBias(method, day) * method.asrBiasFactor
            val asr = clock.asr(school.shadowFactor, method.asrModel, asrBias) ?: return null
            val asrOther = clock.asr(school.other.shadowFactor, method.asrModel, asrBias) ?: return null

            val rule = method.highLatitude
            val dawn = HighLatitude.sign(sky, rule, fajrTwilight, day) ?: return null
            val dusk = ishaTwilight?.let { HighLatitude.sign(sky, rule, it, day) ?: return null }
            val eatingDawn = if (ownEatingDawn) HighLatitude.sign(sky, eatingRule, eatingTwilight, day) ?: return null else null
            val moved = shift * SolarMath.SECONDS_PER_DAY
            return RawEvents(
                dawn = dawn.epochSeconds - moved,
                sunrise = sunrise - moved,
                transit = clock.transit() - moved,
                asr = asr - moved,
                asrOther = asrOther - moved,
                sunset = sunset - moved,
                dusk = dusk?.epochSeconds?.minus(moved),
                eatingDawn = eatingDawn?.epochSeconds?.minus(moved),
                dawnSetByRule = dawn.estimated,
                duskSetByRule = dusk?.estimated == true,
            )
        }
    }

    /**
     * What a place has on a polar date at one point. [dawn], [eatingDawn] and [dusk] are what it
     * gives from its own nights (null where the night is not its own); the `real` crossings, and
     * the rest, are null where it has no such sign.
     */
    private class OwnSigns(
        val dawn: Sign?,
        val eatingDawn: Sign?,
        val realDawn: Double?,
        val realEatingDawn: Double?,
        val sunrise: Double?,
        val transit: Double,
        val asr: Double?,
        val asrOther: Double?,
        val sunset: Double?,
        val dusk: Sign?,
        val realDusk: Double?,
        val redTwilight: Double?,
    )

    /** The authority's offsets for one method and date, in seconds. */
    private class Offsets(private val method: TimetableMethod, date: LocalDate, ramadan: RamadanCalendar) {
        private val isRamadan = ramadan.isRamadan(date)
        private val month = date.month.number

        val ramadanMaghrib: Int = if (isRamadan) method.ramadan?.maghribExtraSeconds ?: 0 else 0

        /** [RamadanRule.ishaFloorMinutesAfterMaghrib] on this date; null off Ramadan or where unset. */
        val ishaFloorMinutes: Int? = if (isRamadan) method.ramadan?.ishaFloorMinutesAfterMaghrib else null

        fun authority(prayer: Prayer): Int =
            method.authorityMinutes[prayer] * 60 + (method.monthlyOffsets?.get(prayer)?.get(month - 1) ?: 0)

        fun total(prayer: Prayer): Int = authority(prayer) + method.margins[prayer]

        fun ishaMinutes(rule: IshaRule.AfterMaghrib): Int = if (isRamadan) rule.ramadanMinutes else rule.minutes
    }

    /** A time from a clock rule, rounded, and whether the rule calls it an estimate. */
    private class Ruled(val at: Instant, val estimated: Boolean)

    /** A morning DUM RT's rule sets ([WHITE]), or one too close to call that takes each event's safe side ([UNCERTAIN]). */
    private enum class RuleNight { WHITE, UNCERTAIN }

    /** One date's astronomy for a method at a point; nullable fields are absent for the method. */
    private class RawEvents(
        val dawn: Double,
        val sunrise: Double,
        val transit: Double,
        val asr: Double,
        val asrOther: Double,
        val sunset: Double,
        /** the Isha altitude crossing, for an [IshaRule.Angle] */
        val dusk: Double?,
        /** the [EndOfEating.DawnAngle] or [EndOfEating.DawnOrMiddle] sign */
        val eatingDawn: Double?,
        val dawnSetByRule: Boolean,
        val duskSetByRule: Boolean,
    )

    private fun fajrDegrees(method: TimetableMethod, date: LocalDate): Double =
        (method.fajrAngleByDayOfYear?.get(curveSlot(date)) ?: method.fajrAngle) + method.twilightDipDeg

    private fun ishaDegrees(method: TimetableMethod, isha: IshaRule.Angle, date: LocalDate): Double =
        (method.ishaAngleByDayOfYear?.get(curveSlot(date)) ?: isha.degrees) + method.twilightDipDeg

    private fun fajrBias(method: TimetableMethod, date: LocalDate): Double =
        method.fajrDeclinationBias?.at(date.dayOfYear, daysInYear(date.year)) ?: 0.0

    /** A local wall-clock time on [date], as a start (a whole minute even in a zone with odd seconds). */
    private fun localTime(date: LocalDate, minutesAfterMidnight: Int, zone: TimeZone): Instant {
        val instant = date.atTime(LocalTime.fromSecondOfDay(minutesAfterMidnight * 60)).toInstant(zone)
        return startOf(instant.epochSeconds.toDouble())
    }

    private fun daysInYear(year: Int): Int = LocalDate(year, 12, 31).dayOfYear

    /** A start: rounded up to the minute. */
    private fun startOf(epochSeconds: Double): Instant =
        Instant.fromEpochSeconds(ceil(epochSeconds / 60.0).toLong() * 60)

    /** An end (sunrise, end of eating, sunset): rounded down to the minute. */
    private fun endOf(epochSeconds: Double): Instant =
        Instant.fromEpochSeconds(floor(epochSeconds / 60.0).toLong() * 60)

    /** R65's sampling of its latitude band, in degrees. */
    private const val BAND_STEP_DEG = 0.05

    /** A night's middle moves less than this from one day to the next, in seconds. */
    private const val DRIFT_SECONDS = 60.0

    /** A minute of rounding, and room between a night's middle and the sun's lower culmination. */
    private const val ROUNDING_AND_SLACK = 60.0 + 10 * 60.0

    /** The longest look back (a night is at most a day: 76 steps of 19 minutes). */
    private const val MAX_LOOKBACK_DAYS = 80

    /** The sun's greatest declination, with a little to spare. */
    private const val MAX_DECLINATION_DEG = 23.5

    private val SAME_DAY = listOf(0)
    private val NEIGHBOURS = listOf(-1, 1)
    private val LAG = listOf(0, -1)
}
