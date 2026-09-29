package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.app.prayer.engine.registry.authorities.PlaceCurves
import world.taqwa.app.prayer.engine.registry.authorities.TwilightCurves
import world.taqwa.app.prayer.engine.registry.data.DeLateDawnCurve
import world.taqwa.app.prayer.engine.registry.data.EndOfEatingDawns
import world.taqwa.app.prayer.engine.registry.data.RabitaCurves
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Continental Europe under ruling R87 (research-mawaqit, brief L), in values the engine computes at run
 * time: no mosque calendar is committed (ruling R69), so the survey itself runs in tools/timetables
 * against the local archive (ContinentalMawaqitSurveyTest).
 */
class ContinentalCautiousTest {
    private fun clock(place: Place, date: LocalDate, zone: TimeZone, model: SunModel = SunModel.EXACT): SunClock {
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        return SunClock(place.lat, place.lon, date, offset, model)
    }

    private fun endOf(epochSeconds: Double): Long = floor(epochSeconds / 60.0).toLong() * 60
    private fun startOf(epochSeconds: Double): Long = ceil(epochSeconds / 60.0).toLong() * 60

    private fun days(year: Int, step: Int = 1): Sequence<LocalDate> = generateSequence(LocalDate(year, 1, 1)) { d ->
        d.plus(step, DateTimeUnit.DAY).takeIf { it.year == year }
    }

    // Germany: the late-dawn family (Fajr) and the 18° family (the end of eating).

    private val berlinZone = TimeZone.of("Europe/Berlin")
    private val berlin = Place(52.52437, 13.41053, "Europe/Berlin", "DE")
    private val koln = Place(50.9375, 6.9603, "Europe/Berlin", "DE")

    @Test
    fun `germany's 18 degree family ends at the 18 point 4 degree dawn or the middle of the night`() {
        // The member lists themselves are RegistryTest's (with the UK's).
        val r = Registry.resolve(berlin)
        assertEquals("de.cautious", r.entry.id)
        val eighteen = r.members.single { it.id == "de.eighteen" }.method
        assertEquals(EndOfEating.DawnOrMiddle(18.4), eighteen.endOfEating)
        assertEquals(4, r.members.single { it.id == "de.latedawn" }.method.authorityMinutes.maghrib)
        assertEquals(SAFE_END, eighteen.endOfEatingMarginSeconds)
    }

    @Test
    fun `koln's october fajr is the late dawn family's 13 degrees and no earlier than its curve`() {
        val date = LocalDate(2026, 10, 15)
        val r = Registry.resolve(koln)
        val depression = DeLateDawnCurve.fajr[curveSlot(date)]
        assertTrue(depression in 12.9..13.1, "October's curve is about 13°: $depression")
        // The member reads the sun at 0h UT, as Diyanet does (its Isha then never leads on a threshold night).
        val dawn = assertNotNull(clock(koln, date, berlinZone, SunModel.DAILY_0H_UT).altitudeTime(-depression, morning = true))
        val day = DayPipeline.day(r, date, berlinZone)
        assertEquals(startOf(dawn + SAFE_START), day.fajr.epochSeconds)
        val members = DayPipeline.members(r, date, berlinZone)
        val late = r.members.indexOfFirst { it.id == "de.latedawn" }
        for ((i, member) in members.withIndex()) {
            if (i != late) assertTrue(member.fajr < day.fajr, "${r.members[i].id} Fajr ${member.fajr} is not before ${day.fajr}")
        }
    }

    @Test
    fun `berlin's fast begins at the middle of a june night the sun leaves above the 18 degree dawn`() {
        val date = LocalDate(2026, 6, 21)
        assertNull(clock(berlin, date, berlinZone).altitudeTime(-18.4, morning = true))
        val sunset = assertNotNull(clock(berlin, date.minus(1, DateTimeUnit.DAY), berlinZone).altitudeTime(-0.8333, morning = false))
        val sunrise = assertNotNull(clock(berlin, date, berlinZone).altitudeTime(-0.8333, morning = true))
        val day = DayPipeline.day(Registry.resolve(berlin), date, berlinZone)
        assertEquals(endOf((sunset + sunrise) / 2 + SAFE_END), day.endOfEating.epochSeconds)
        // The Fajr shown is a later member's, not the 18° family's own middle of the night.
        assertTrue(day.fajr.epochSeconds > day.endOfEating.epochSeconds + 30 * 60, "Fajr ${day.fajr} is not well after the end ${day.endOfEating}")
    }

    @Test
    fun `berlin's fast begins at the 18 point 4 degree dawn on a winter night`() {
        val date = LocalDate(2026, 1, 15)
        val dawn = assertNotNull(clock(berlin, date, berlinZone).altitudeTime(-18.4, morning = true))
        val day = DayPipeline.day(Registry.resolve(berlin), date, berlinZone)
        assertEquals(endOf(dawn + SAFE_END), day.endOfEating.epochSeconds)
    }

    /**
     * The two new German members lead on what they were added for alone: the late-dawn family on Fajr, the
     * 18° family on the end of eating. Every other time is what Diyanet and VIKZ give, anywhere in Germany.
     */
    @Test
    fun `germany's new members move nothing but fajr and the end of eating`() {
        val places = listOf(
            "Freiburg" to Place(47.9990, 7.8421, "Europe/Berlin", "DE"),
            "München" to Place(48.1351, 11.5820, "Europe/Berlin", "DE"),
            "Frankfurt" to Place(50.1109, 8.6821, "Europe/Berlin", "DE"),
            "Köln" to koln,
            "Berlin" to berlin,
            "Hamburg" to Place(53.5511, 9.9937, "Europe/Berlin", "DE"),
            "Flensburg" to Place(54.7937, 9.4470, "Europe/Berlin", "DE"),
        )
        for ((name, place) in places) {
            val r = Registry.resolve(place)
            assertEquals("de.cautious", r.entry.id, name)
            val old = r.members.indices.filter { r.members[it].id == "tr.diyanet.europe" || r.members[it].id == "de.vikz" }
            for (date in days(2026, step = 3)) {
                val members = DayPipeline.members(r, date, berlinZone)
                val all = Cautious.combine(members, 0)
                val without = Cautious.combine(old.map { members[it] }, 0)
                val label = "$name $date"
                assertEquals(without.sunrise, all.sunrise, "$label sunrise")
                assertEquals(without.dhuhr, all.dhuhr, "$label dhuhr")
                assertEquals(without.asr, all.asr, "$label asr")
                assertEquals(without.asrOther, all.asrOther, "$label asrOther")
                assertEquals(without.maghrib, all.maghrib, "$label maghrib")
                assertEquals(without.isha, all.isha, "$label isha")
                assertEquals(without.sunset, all.sunset, "$label sunset")
                assertTrue(all.fajr >= without.fajr, "$label fajr")
                assertTrue(all.endOfEating <= without.endOfEating, "$label endOfEating")
            }
        }
    }

    /**
     * The threshold night in Germany (the class of ruling R72, as in the UK): on the night 16° is last or
     * first reached, VIKZ's Isha (16° + 10 min) lies at the sun's lowest point plus ten, and the next end of
     * eating, the middle of the night, at or before it: Isha has no end (ruling R26). One or two nights a
     * year at a place, by up to 10 min; never a day put back in order.
     */
    @Test
    fun `the latest isha meets the next end of eating only on germany's threshold nights`() {
        val places = listOf(
            Place(47.9990, 7.8421, "Europe/Berlin", "DE"), // Freiburg
            Place(48.1351, 11.5820, "Europe/Berlin", "DE"), // München
            Place(50.1109, 8.6821, "Europe/Berlin", "DE"), // Frankfurt
            koln, berlin,
            Place(53.5511, 9.9937, "Europe/Berlin", "DE"), // Hamburg
            Place(54.7937, 9.4470, "Europe/Berlin", "DE"), // Flensburg
            Place(51.5, 10.0, "Europe/Berlin", "DE"),
            Place(52.0, 10.0, "Europe/Berlin", "DE"),
            Place(53.0, 10.0, "Europe/Berlin", "DE"),
        )
        for (place in places) {
            val r = Registry.resolve(place)
            val unended = HashMap<LocalDate, PrayerDay>()
            fun unended(date: LocalDate) = unended.getOrPut(date) { DayPipeline.unended(r, date, berlinZone) }
            val nights = mutableListOf<String>()
            for (date in days(2026)) {
                val next = unended(date.plus(1, DateTimeUnit.DAY))
                val day = Ends.withNextDay(unended(date), next)
                val label = "${place.lat} ${place.lon} $date"
                if (day.ends[Prayer.ISHA] == null) {
                    val gap = (day.isha - next.endOfEating).inWholeMinutes
                    assertTrue(gap in 0..10, "$label: the next end of eating is $gap min before Isha")
                    assertTrue(date.month.number in 5..7, "$label: Isha without an end outside May to July")
                    nights += date.toString()
                }
                assertFalse(day.repaired, "$label put back in order")
            }
            assertTrue(nights.size <= 2, "${place.lat} ${place.lon}: Isha without an end on $nights")
        }
    }

    // The Netherlands and Belgium: Maghrib (ruling R87).

    @Test
    fun `the netherlands' maghrib is never before diyanet's own anywhere and the members agree in autumn`() {
        val zone = TimeZone.of("Europe/Amsterdam")
        val places = listOf(
            Place(52.37403, 4.88969, "Europe/Amsterdam", "NL"), // Amsterdam
            Place(51.9244, 4.4777, "Europe/Amsterdam", "NL"), // Rotterdam
            Place(52.0907, 5.1214, "Europe/Amsterdam", "NL"), // Utrecht
            Place(53.2194, 6.5665, "Europe/Amsterdam", "NL"), // Groningen
            Place(50.8514, 5.6910, "Europe/Amsterdam", "NL"), // Maastricht
        )
        val ramadan = Registry.ramadanCalendar()
        for (place in places) {
            val r = Registry.resolve(place)
            assertEquals("nl.cautious", r.entry.id)
            val diyanet = r.members.indexOfFirst { it.id == "tr.diyanet.europe" }
            val moroccan = r.members.indexOfFirst { it.id == "nl.moroccan" }
            assertEquals(7, r.members[moroccan].method.authorityMinutes.maghrib)
            for (date in days(2026, step = 2)) {
                val members = DayPipeline.members(r, date, zone)
                val day = DayPipeline.day(r, date, zone)
                val label = "${place.lat} ${place.lon} $date"
                // Never before Diyanet's method at the user's own point (what the Turkish and Arab mosques print at
                // theirs, the survey's ground truth). Diyanet's member itself carries its Amsterdam point across its
                // reach (ruling R15: a start at the later of that point and the user's), up to 2 min past this.
                val diyanetHere = DayComputer.compute(Diyanet.europeMethod, GeoPoint(place.lat, place.lon), date, zone, AsrSchool.STANDARD, ramadan)
                assertTrue(day.maghrib >= diyanetHere.maghrib, "$label Maghrib ${day.maghrib} before Diyanet's own ${diyanetHere.maghrib}")
                assertTrue(day.maghrib >= members[diyanet].maghrib - 2.minutes, "$label Maghrib ${day.maghrib} before Diyanet's member's ${members[diyanet].maghrib}")
                // MWL's Maghrib is the sun's own, so the members never agree: the cap is the Moroccan member's floor.
                assertEquals(members[moroccan].maghrib, day.maghrib, "$label Maghrib is the Moroccan member's")
                // Seven to nine minutes (plus 30 s) after the sun's own sunset, Maghrib rounded up and sunset down.
                val afterSunset = members[moroccan].maghrib - members[moroccan].sunset
                assertTrue(afterSunset >= 7.minutes && afterSunset <= 11.minutes, "$label Maghrib $afterSunset after sunset")
            }
        }
    }

    /**
     * Ruling R88: Belgium's Maghrib is spec §3.6's cap, the most-followed member's own minutes (EMB's sunset + 2,
     * followed by 42 of 64 Brussels mosques), wherever the two members spread past [Cautious.MAGHRIB_AGREEMENT],
     * and the later of the two where they agree. Diyanet's member (sunset + 7 on its own sun, + 6..9 on the
     * exact one) spreads past it on every day, so the cap decides all year and the Diyanet calendars' earlier
     * Maghrib is recorded as `maghribCap` in the survey. The EMB member is EMB's own entry, placed the same
     * way inside the Brussels table's reach and beyond it (track L's + 7 for everyone, 5–7 min after EMB's
     * own for its own majority, is gone with the edge hook it needed).
     */
    @Test
    fun `belgium's maghrib is capped at emb's own minutes and its emb member is emb's own entry`() {
        val zone = TimeZone.of("Europe/Brussels")
        val places = listOf(
            Place(50.8467, 4.3525, "Europe/Brussels", "BE"), // Brussels, inside EMB's table
            Place(51.2194, 4.4025, "Europe/Brussels", "BE"), // Antwerp
            Place(51.0543, 3.7174, "Europe/Brussels", "BE"), // Ghent, beyond its reach
            Place(50.6326, 5.5797, "Europe/Brussels", "BE"), // Liège
            Place(49.6833, 5.8167, "Europe/Brussels", "BE"), // Arlon
        )
        val embEntry = Registry.byId("be.emb")!!
        for (place in places) {
            val r = Registry.resolve(place)
            assertEquals("be.cautious", r.entry.id)
            val emb = r.members.indexOfFirst { it.id == "be.emb" }
            val diyanet = r.members.indexOfFirst { it.id == "tr.diyanet.europe" }
            assertEquals(1, r.members[emb].shareRank, "${place.lat}: EMB is the most followed")
            val own = Registry.resolveEntry(embEntry, place)
            val ownMethod = own.method!!
            assertEquals(2, ownMethod.authorityMinutes.maghrib, "${place.lat}: EMB's own entry")
            // The member is EMB's own entry placed the same way (its curves are arrays, so field by field).
            val member = r.members[emb].method
            val placed: (TimetableMethod) -> List<Any?> = {
                listOf(it.id, it.authorityMinutes, it.margins, it.endOfEatingMarginSeconds, it.fixedPoint, it.fixedPointMode, it.fajrAngle, it.isha)
            }
            assertEquals(placed(ownMethod), placed(member), "${place.lat}: the member is EMB's own entry, inside the table and beyond it")
            val spreads = mutableListOf<Duration>()
            for (date in days(2026, step = 2)) {
                val members = DayPipeline.members(r, date, zone)
                val day = DayPipeline.day(r, date, zone)
                val label = "${place.lat} ${place.lon} $date"
                val ownDay = DayPipeline.day(own, date, zone)
                assertEquals(DayPipeline.unended(own, date, zone), members[emb], "$label the EMB member's day is EMB's own")
                val spread = members[diyanet].maghrib - members[emb].maghrib
                spreads += spread
                val expected = if (spread > Cautious.MAGHRIB_AGREEMENT) members[emb].maghrib else maxOf(members[emb].maghrib, members[diyanet].maghrib)
                assertEquals(expected, day.maghrib, "$label Maghrib")
                assertTrue(day.maghrib >= ownDay.maghrib && day.maghrib <= ownDay.maghrib + Cautious.MAGHRIB_AGREEMENT, "$label Maghrib ${day.maghrib} against EMB's own ${ownDay.maghrib}")
            }
            // Diyanet's sunset + 7 on its own sun runs 3 to 7 min after EMB's + 2 at EMB's own point and beyond
            // every Diyanet table: past the agreement on every day, so the cap decides all year and Maghrib is
            // EMB's own. At Antwerp and Ghent Diyanet's member computes at its own table's point since the
            // monitor round while EMB's stays at Brussels, whose winter sunset comes later: on a few winter days
            // the two agree within 2 min and Maghrib is the later member's (asserted day by day above).
            val range = "${place.lat}: Diyanet's Maghrib ${spreads.min()}..${spreads.max()} after EMB's"
            assertTrue(spreads.min() > Duration.ZERO && spreads.max() <= 7.minutes, range)
            val agreeing = spreads.count { it <= Cautious.MAGHRIB_AGREEMENT }
            if (place.lat == 51.2194 || place.lat == 51.0543) {
                assertTrue(agreeing in 0..40, "$range: $agreeing sampled days within the agreement")
            } else {
                assertEquals(0, agreeing, "$range: within the agreement on $agreeing sampled days")
            }
        }
    }

    // Brief L item 6: ends carried south from a table's latitude, against the rule each is built from.

    /**
     * EMB's end of eating is its Brussels table's earliest dawns carried to another latitude by the fraction of
     * the night (TwilightCurves.endOfEating), checked across southern Belgium over 2026–2029 against EMB's own
     * rule read as an end at the place (18°, and from May to July the later of 18° and the earlier of its
     * clock-time floor and the proportion from 45°; 7g's reading of the table, PlaceCurves.fajrRuleAsEnd):
     * - the carried dawn is never after the rule's moment but at Arlon on 8 July, by 16 s;
     * - beyond the table's reach (Arlon, Namur, Liège: the edge, its −30 s end margin) the minute shown is never
     *   past the rule's moment, 33 s before it at the closest;
     * - within it (Charleroi) the table's own end rides with its fitted margin (ruling R44), as at Brussels, where
     *   the gate holds it to EMB's printed times. There, as at Brussels itself, it may pass 7g's rule by up to a
     *   minute, since the rule only approximates the table (no floor, brief L item 6). It is never after
     *   Brussels's own end (ruling R89): before it, on the days Brussels's end was held to its own Fajr (spec
     *   §3.3's order) and Charleroi's, held to the later of the two points' Fajr, was not, Charleroi's came a
     *   minute after (8 April and May days in 2027–2029).
     */
    @Test
    fun `emb's end south of brussels is never past its own rule's minute beyond the table's reach`() {
        val zone = TimeZone.of("Europe/Brussels")
        val emb = Registry.byId("be.emb")!!
        val brussels = Registry.resolveEntry(emb, Place(50.8467, 4.3525, "Europe/Brussels", "BE"))
        val dawns = EndOfEatingDawns.embBrussels
        val places = listOf(
            "Arlon" to Place(49.6833, 5.8167, "Europe/Brussels", "BE"),
            "Namur" to Place(50.4674, 4.8720, "Europe/Brussels", "BE"),
            "Charleroi" to Place(50.4108, 4.4446, "Europe/Brussels", "BE"),
            "Liège" to Place(50.6326, 5.5797, "Europe/Brussels", "BE"),
        )
        for ((name, place) in places) {
            val r = Registry.resolveEntry(emb, place)
            val withinReach = r.unitName != null
            assertEquals(name == "Charleroi", withinReach, "$name within the Brussels table's reach")
            val rule = PlaceCurves.fajrRuleAsEnd("be.emb", 18.0, place.lat)
            val carried = TwilightCurves.endOfEating(place.lat, dawns.latitude, dawns.depressions)
            val carriedPast = mutableListOf<String>()
            for (year in 2026..2029) {
                for (date in days(year)) {
                    val sun = clock(place, date, zone)
                    val moment = assertNotNull(sun.altitudeTime(-rule[curveSlot(date)], morning = true), "$name $date")
                    val carriedAt = assertNotNull(sun.altitudeTime(-carried[curveSlot(date)], morning = true), "$name $date")
                    if (carriedAt > moment) {
                        assertTrue(carriedAt - moment < 20.0, "$name $date: the carried dawn ${carriedAt - moment} s after EMB's rule")
                        carriedPast += "${date.month.number}-${date.day}"
                    }
                    val end = DayPipeline.day(r, date, zone).endOfEating
                    if (withinReach) {
                        assertTrue(end.epochSeconds <= moment + 60, "$name $date: the end ${end.epochSeconds - moment} s past EMB's rule")
                        val atTable = DayPipeline.day(brussels, date, zone)
                        assertTrue(end <= atTable.endOfEating, "$name $date: the end ${end - atTable.endOfEating} after Brussels's own")
                    } else {
                        assertTrue(end.epochSeconds <= moment, "$name $date: the end ${end.epochSeconds - moment} s past EMB's rule")
                    }
                }
            }
            val expected = if (name == "Arlon") List(4) { "7-8" } else emptyList()
            assertEquals(expected, carriedPast, "$name: the days the carried dawn passes EMB's rule")
        }
    }

    /**
     * Rabita prints no imsak: its fast begins at its Fajr, its own curve ([RabitaCurves]), and its end is its
     * Helsinki calendar's earliest dawns carried south. At Malmö and Lund, the southernmost Swedish cities, that
     * end is never after the Fajr curve, slot by slot and in the minute shown.
     */
    @Test
    fun `rabita's end carried south to malmo and lund is never after its own fajr curve`() {
        val zone = TimeZone.of("Europe/Stockholm")
        val places = listOf(
            "Malmö" to Place(55.6050, 13.0038, "Europe/Stockholm", "SE"),
            "Lund" to Place(55.7047, 13.1910, "Europe/Stockholm", "SE"),
        )
        for ((name, place) in places) {
            val r = Registry.resolve(place)
            assertEquals("se.cautious", r.entry.id, name)
            val rabita = r.members.indexOfFirst { it.id == "se.rabita" }
            val end = assertNotNull((r.members[rabita].method.endOfEating as? EndOfEating.DawnAngle)?.bySlot, name)
            for (i in end.indices) {
                assertTrue(end[i] >= RabitaCurves.fajr[i], "$name slot $i: the end at ${end[i]}° after the Fajr curve's ${RabitaCurves.fajr[i]}°")
            }
            for (year in 2026..2029) {
                for (date in days(year)) {
                    val fajr = assertNotNull(clock(place, date, zone).altitudeTime(-RabitaCurves.fajr[curveSlot(date)], morning = true), "$name $date")
                    val shown = DayPipeline.members(r, date, zone)[rabita].endOfEating.epochSeconds
                    assertTrue(shown <= fajr, "$name $date: the end ${shown - fajr} s after Rabita's Fajr curve")
                }
            }
        }
    }

    // France: Diyanet as a member.

    @Test
    fun `france's sunrise is diyanet's and its isha asr and end of eating cover diyanet's`() {
        // The member list itself is RegistryTest's (with the UK's).
        val zone = TimeZone.of("Europe/Paris")
        val lyon = Place(45.7640, 4.8357, "Europe/Paris", "FR")
        val r = Registry.resolve(lyon)
        assertEquals("fr.cautious", r.entry.id)
        val diyanet = r.members.indexOfFirst { it.id == "tr.diyanet.europe" }
        for (date in days(2026, step = 5)) {
            val members = DayPipeline.members(r, date, zone)
            val day = DayPipeline.day(r, date, zone)
            val label = "Lyon $date"
            assertEquals(members[diyanet].sunrise, day.sunrise, "$label sunrise is Diyanet's, the sun's less 7")
            assertTrue(day.isha >= members[diyanet].isha, "$label Isha before Diyanet's")
            assertTrue(day.asr >= members[diyanet].asr, "$label Asr before Diyanet's")
            assertTrue(day.endOfEating <= members[diyanet].endOfEating, "$label end of eating after Diyanet's")
            // Maghrib stays the 12–13° family's + 4 (spec §3.6's cap where Diyanet's + 7 spreads past 2 min; the
            // latest, Diyanet's, within 2 min of it where they agree), never Diyanet's + 7 outright.
            val twelve = members[r.members.indexOfFirst { it.id == "fr.twelve" }].maghrib
            assertTrue(day.maghrib >= twelve && day.maghrib <= twelve + 2.minutes, "$label Maghrib ${day.maghrib} against the 12° family's $twelve")
        }
    }
}
