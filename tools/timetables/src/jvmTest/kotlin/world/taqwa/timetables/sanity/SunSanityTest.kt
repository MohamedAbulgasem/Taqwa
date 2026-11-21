package world.taqwa.timetables.sanity

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.timetables.TestPaths
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The sun-sanity pass ([SunSanity]): its checks on invented days, the listed groups' ceilings, and a sample of the real
 * pass (every unit and region point, every 40th app city, each Settings choice at 3 cities and each Other method at 20,
 * every 9th day of 2026-11-12..2027-12-31, the window the ceilings were measured on) whose findings must all be listed
 * in `official/sun-sanity/open-findings.tsv` and its excused groups in `excused.tsv`, within their ceilings. The whole
 * pass is `./gradlew -p tools/timetables sunSanity` (a few minutes on 15 threads); it needs no archive.
 */
class SunSanityTest {

    private val date = LocalDate(2027, 2, 20)
    private val makkah = GeoPoint(21.4225, 39.8262)
    private val sky = SunSanity.sky(makkah, date, TimeZone.of("Asia/Riyadh"))

    /** A day in order around [sky], each start a whole minute after its event. */
    private fun day(
        fajr: Double = sky.sunrise!! - 80 * 60,
        dhuhr: Double = sky.transit + 60,
        maghrib: Double = sky.sunset!! + 60,
        isha: Double = sky.sunset!! + 91 * 60,
        endOfEating: Double = fajr - 60,
        asr: Double = sky.asrStandard!! + 60,
        asrOther: Double = sky.asrHanafi!! + 60,
        sunrise: Double = sky.sunrise!!,
        ends: Map<Prayer, Double> = emptyMap(),
        notFollowed: Set<Prayer> = emptySet(),
        setByRule: Set<Prayer> = emptySet(),
        polar: Boolean = false,
    ) = PrayerDay(
        date = date, fajr = up(fajr), sunrise = down(sunrise), dhuhr = up(dhuhr), asr = up(asr),
        asrOther = up(asrOther), maghrib = up(maghrib), isha = up(isha), sunset = down(sky.sunset!!),
        endOfEating = down(endOfEating), imsak = null, methodId = "test", notFollowed = notFollowed, setByRule = setByRule,
        polar = polar, ends = ends.mapValues { down(it.value) },
    )

    private fun up(s: Double) = Instant.fromEpochSeconds((ceil(s / 60) * 60).toLong())
    private fun down(s: Double) = Instant.fromEpochSeconds((floor(s / 60) * 60).toLong())

    @Test
    fun `a day after the sun's events breaks nothing`() {
        assertEquals(emptyList(), SunSanity.breaches(day(), sky).map { it.check })
    }

    @Test
    fun `each check finds its own breach, by how far it is on the wrong side`() {
        val dhuhr = SunSanity.breaches(day(dhuhr = sky.transit - 120), sky).single()
        assertEquals(SunSanity.Check.DHUHR, dhuhr.check)
        assertTrue(dhuhr.seconds in 60.0..120.0, "${dhuhr.seconds}")
        assertEquals(listOf(SunSanity.Check.MAGHRIB), SunSanity.breaches(day(maghrib = sky.sunset!! - 90), sky).map { it.check })
        assertEquals(
            listOf(SunSanity.Check.FAJR, SunSanity.Check.END_OF_EATING),
            SunSanity.breaches(day(fajr = sky.sunrise!! + 120, endOfEating = sky.sunrise!! + 120), sky).map { it.check },
        )
        assertEquals(
            listOf(SunSanity.Check.ISHA),
            SunSanity.breaches(day(isha = sky.sunset!! + 30), sky).map { it.check }.filter { it == SunSanity.Check.ISHA },
        )
    }

    @Test
    fun `ruling R265's checks find an asr before its shadow and a sunrise or an end after the sun`() {
        assertTrue(sky.asrHanafi!! > sky.asrStandard!! && sky.asrStandard!! > sky.transit)
        val asr = SunSanity.breaches(day(asr = sky.asrStandard!! - 100), sky).single()
        assertEquals(SunSanity.Check.ASR, asr.check)
        assertTrue(asr.seconds in 40.0..100.0, "${asr.seconds}")
        // The other school's against its own shadow, and a day computed in the Hanafi school reads its asr as Hanafi.
        assertEquals(listOf(SunSanity.Check.ASR), SunSanity.breaches(day(asrOther = sky.asrHanafi!! - 120), sky).map { it.check })
        val hanafiDay = day(asr = sky.asrHanafi!! + 60, asrOther = sky.asrStandard!! + 60)
        assertEquals(emptyList(), SunSanity.breaches(hanafiDay, sky, AsrSchool.HANAFI).map { it.check })
        assertEquals(listOf(SunSanity.Check.ASR), SunSanity.breaches(hanafiDay, sky, AsrSchool.STANDARD).map { it.check })
        // The sunrise shown, or Fajr's end, past the minute the sun rose in; Dhuhr's end past the first shadow's minute.
        assertEquals(listOf(SunSanity.Check.SUNRISE), SunSanity.breaches(day(sunrise = sky.sunrise!! + 60), sky).map { it.check })
        assertEquals(listOf(SunSanity.Check.SUNRISE), SunSanity.breaches(day(ends = mapOf(Prayer.FAJR to sky.sunrise!! + 60)), sky).map { it.check })
        assertEquals(emptyList(), SunSanity.breaches(day(ends = mapOf(Prayer.FAJR to sky.sunrise!!, Prayer.DHUHR to sky.asrStandard!!)), sky).map { it.check })
        assertEquals(listOf(SunSanity.Check.DHUHR_END), SunSanity.breaches(day(ends = mapOf(Prayer.DHUHR to sky.asrStandard!! + 60)), sky).map { it.check })
        // A rule's Asr is excused by name, never dropped.
        assertEquals(SunSanity.Excuse.SET_BY_RULE, SunSanity.breaches(day(asr = sky.asrStandard!! - 600, setByRule = setOf(Prayer.ASR)), sky).single().excuse)
    }

    @Test
    fun `ruling R265's checks fail the pass unexcused or listed as open and are listed when the day excuses them`() {
        for (check in SunSanity.FLOORED_UNLESS_EXCUSED) {
            val result = SunSanity.Result(date, date)
            val key = SunSanity.Key("xx.test", check, null)
            result.groups[key] = mutableListOf(SunSanity.PointTally("a", null).apply { add(date, AsrSchool.STANDARD, 1.0) })
            val listed = SunSanity.Listed(key.entryId, key.check, null, 600, 10, "note")
            assertTrue(SunSanity.problems(result, emptyList(), emptyList()).single().contains("R265"), "$check unexcused")
            assertEquals(2, SunSanity.problems(result, listOf(listed), emptyList()).size, "$check listed as open")
            val excusedKey = SunSanity.Key("xx.test", check, SunSanity.Excuse.SET_BY_RULE)
            val excusedOnly = SunSanity.Result(date, date).apply {
                groups[excusedKey] = mutableListOf(SunSanity.PointTally("a", null).apply { add(date, AsrSchool.STANDARD, 1.0) })
            }
            val excusedListed = SunSanity.Listed(excusedKey.entryId, check, SunSanity.Excuse.SET_BY_RULE, 600, 10, "note")
            assertEquals(emptyList(), SunSanity.problems(excusedOnly, emptyList(), listOf(excusedListed)), "$check excused and listed")
        }
    }

    @Test
    fun `a breach the day itself accounts for is excused by name, never dropped`() {
        val declared = SunSanity.breaches(day(fajr = sky.sunrise!! + 300, notFollowed = setOf(Prayer.FAJR)), sky)
        assertTrue(declared.isNotEmpty() && declared.all { it.excuse == SunSanity.Excuse.DECLARED })
        val byRule = SunSanity.breaches(day(isha = sky.sunset!! - 600, setByRule = setOf(Prayer.ISHA)), sky)
        assertEquals(SunSanity.Excuse.SET_BY_RULE, byRule.single { it.check == SunSanity.Check.ISHA }.excuse)
        val polar = SunSanity.breaches(day(isha = sky.sunset!! - 600, polar = true), sky)
        assertEquals(SunSanity.Excuse.POLAR, polar.single { it.check == SunSanity.Check.ISHA }.excuse)
    }

    @Test
    fun `a Dhuhr before transit is never excused, on a polar day or by a rule`() {
        // The sun transits on a polar day too, and no rule may put noon before it (review of 12 Nov 2026).
        for (d in listOf(
            day(dhuhr = sky.transit - 300, polar = true),
            day(dhuhr = sky.transit - 300, setByRule = setOf(Prayer.DHUHR)),
            day(dhuhr = sky.transit - 300, notFollowed = setOf(Prayer.DHUHR)),
        )) {
            assertEquals(null, SunSanity.breaches(d, sky).single { it.check == SunSanity.Check.DHUHR }.excuse)
        }
    }

    @Test
    fun `a listed group fails past either ceiling, and an unlisted excused group fails too`() {
        val result = SunSanity.Result(date, date)
        fun tally(label: String, worst: Double) = SunSanity.PointTally(label, null).apply { add(date, AsrSchool.STANDARD, worst) }
        val finding = SunSanity.Key("xx.test", SunSanity.Check.ISHA, null)
        val excused = SunSanity.Key("xx.test", SunSanity.Check.FAJR, SunSanity.Excuse.DECLARED)
        result.groups[finding] = mutableListOf(tally("a", 20.4), tally("b", 3.0))
        result.groups[excused] = mutableListOf(tally("a", 600.0))
        fun listed(key: SunSanity.Key, worst: Int, points: Int) = SunSanity.Listed(key.entryId, key.check, key.excuse, worst, points, "note")
        assertEquals(emptyList(), SunSanity.problems(result, listOf(listed(finding, 21, 2)), listOf(listed(excused, 600, 1))))
        assertEquals(1, SunSanity.problems(result, listOf(listed(finding, 20, 2)), listOf(listed(excused, 600, 1))).size, "worse than its worst")
        assertEquals(1, SunSanity.problems(result, listOf(listed(finding, 21, 1)), listOf(listed(excused, 600, 1))).size, "more points")
        val unlisted = SunSanity.problems(result, listOf(listed(finding, 21, 2)), emptyList())
        assertTrue(unlisted.single().contains("not listed in excused.tsv"), unlisted.toString())
    }

    @Test
    fun `a Dhuhr before transit or a Maghrib before sunset fails the pass found or listed, whatever its ceilings`() {
        // Ruling R180: the engine's floor forbids both, so no list can give either a ceiling to stay under.
        for (check in SunSanity.FLOORED) {
            val result = SunSanity.Result(date, date)
            val key = SunSanity.Key("xx.test", check, null)
            val listed = SunSanity.Listed(key.entryId, key.check, key.excuse, 600, 10, "note")
            assertEquals(1, SunSanity.problems(result, listOf(listed), emptyList()).size, "$check listed, not found")
            result.groups[key] = mutableListOf(SunSanity.PointTally("a", null).apply { add(date, AsrSchool.STANDARD, 1.0) })
            assertTrue(SunSanity.problems(result, emptyList(), emptyList()).single().contains("R180"), "$check found, not listed")
            assertEquals(2, SunSanity.problems(result, listOf(listed), emptyList()).size, "$check found and listed within its ceilings")
        }
        assertEquals(setOf(SunSanity.Check.DHUHR, SunSanity.Check.MAGHRIB), SunSanity.FLOORED)
    }

    @Test
    fun `a method five minutes before the sun is found nowhere and the floor's cost is counted instead`() {
        // The what-if the open findings were once cleared with, turned round: every method's Dhuhr and Maghrib put
        // five minutes earlier. The floor leaves the pass nothing to find, and says what it moved.
        val cities = SunSanity.cities(TestPaths.appFiles.resolve("cities.csv"))
        val points = SunSanity.points(cities, everyCity = 1500, chosenPerEntry = 1, otherPerEntry = 1).filter { it.kind == SunSanity.Kind.CITY }
        val from = LocalDate(2027, 2, 8)
        val result = SunSanity.run(points, from, LocalDate(2027, 3, 9), everyDays = 5) { method ->
            method.copy(margins = method.margins.copy(dhuhr = method.margins.dhuhr - 300, maghrib = method.margins.maghrib - 300))
        }
        assertTrue(result.groups.keys.none { it.check in SunSanity.FLOORED }, "${result.groups.keys}")
        assertTrue(result.raised.keys.any { it.second == Prayer.DHUHR } && result.raised.keys.any { it.second == Prayer.MAGHRIB })
        val untouched = SunSanity.run(points, from, from)
        assertTrue(untouched.raised.values.flatten().all { it.worst >= 60 && it.worst % 60 == 0.0 }, "a start is moved by whole minutes")
        assertTrue(SunSanity.report(result, emptyList()).contains("ruling R180"))
        // What the raise moved along with it is counted, never more than the days raised.
        assertTrue(SunSanity.report(result, emptyList()).contains("moved another time too"))
        assertTrue(result.cascaded.values.sum() <= result.raised.values.flatten().sumOf { it.days })
    }

    @Test
    fun `every Other method and each Settings choice is checked at app cities`() {
        val cities = SunSanity.cities(TestPaths.appFiles.resolve("cities.csv"))
        val chosen = SunSanity.points(cities, everyCity = 1000, chosenPerEntry = 2, otherPerEntry = 5).filter { it.kind == SunSanity.Kind.CHOSEN }
        for (other in Registry.otherMethods) {
            assertEquals(5, chosen.count { it.entryId == other.id }, other.id)
        }
        assertTrue(chosen.any { it.entryId == "be.emb" } && chosen.any { it.entryId == "ca.mac" }, "named timetables offered nearby")
        // Never where Automatic already follows the entry: that city is checked under Automatic.
        assertTrue(chosen.all { Registry.automaticEntry(it.place).id != it.entryId })
    }

    @Test
    fun `where the sun does not set, Maghrib is not checked`() {
        val noSunset = SunSanity.Sky(sky.transit, sky.sunrise, null, sky.asrStandard, sky.asrHanafi)
        assertTrue(SunSanity.breaches(day(maghrib = sky.sunset!! - 3600, isha = sky.sunset!! + 3600), noSunset).none { it.check == SunSanity.Check.MAGHRIB })
    }

    @Test
    fun `the open findings and the excused groups parse and none is a start before the real sun`() {
        val open = SunSanity.openFindings(TestPaths.repoRoot.resolve(OPEN_FINDINGS))
        val excused = SunSanity.excusedGroups(TestPaths.repoRoot.resolve(EXCUSED))
        val all = open + excused
        assertTrue(all.all { it.note.isNotBlank() && it.worstSeconds > 0 && it.points > 0 }, "a listed group without its note or ceilings")
        assertEquals(all.size, all.map { it.key }.toSet().size, "a group listed twice")
        // Ruling R180: the engine's floor leaves no Dhuhr before transit and no Maghrib before sunset to list or excuse.
        assertTrue(all.none { it.check in SunSanity.FLOORED }, "a Dhuhr before transit or a Maghrib before sunset is listed")
        // Ruling R265: an Asr, a sunrise or Dhuhr's end on the wrong side of the sun is listed only with the day's excuse.
        assertTrue(open.none { it.check in SunSanity.FLOORED_UNLESS_EXCUSED }, "an R265 check is listed as open")
        assertTrue(excused.all { it.excuse != null } && open.all { it.excuse == null })
    }

    @Test
    fun `a sample of the real pass finds nothing unlisted or past its ceilings`() {
        val cities = SunSanity.cities(TestPaths.appFiles.resolve("cities.csv"))
        val points = SunSanity.points(cities, everyCity = 40, chosenPerEntry = 3, otherPerEntry = 20)
        val result = SunSanity.run(points, LocalDate(2026, 11, 12), LocalDate(2027, 12, 31), everyDays = 9)
        val open = SunSanity.openFindings(TestPaths.repoRoot.resolve(OPEN_FINDINGS))
        val excused = SunSanity.excusedGroups(TestPaths.repoRoot.resolve(EXCUSED))
        println(SunSanity.report(result, open, excused, placesShown = 3))
        val problems = SunSanity.problems(result, open, excused)
        assertTrue(problems.isEmpty(), "sun-sanity problems: $problems")
    }
}
