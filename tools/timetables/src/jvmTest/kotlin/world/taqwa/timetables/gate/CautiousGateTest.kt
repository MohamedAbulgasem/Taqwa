package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.ClockRule
import world.taqwa.app.prayer.engine.method.ClockTime
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Member
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Scope
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Rulings R38 and R155 on a synthetic cautious entry made from MUIS: member `test.a` (most-followed)
 * is MUIS's method and prints the open MUIS rows of the fixture; member `test.b` is the same method
 * three minutes later on every time, and prints those rows shifted. Every start, Maghrib too, is
 * checked against the latest member's printed time. Ruling R156's far-north guard on members whose
 * own rule sets their Maghrib: one held by a day of twice 400 minutes around Dhuhr, one by an
 * invented clock rule's estimate, each printing the engine's own days.
 */
class CautiousGateTest {

    private val fixture: File = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
    private val archive: File = Files.createTempDirectory("gate-cautious").toFile()
    private val roots = OfficialRoots(archive, fixture)

    /** MUIS's method with Task 5's margins pinned, so a refit leaves these days alone. */
    private val muis = Registry.byId("sg.muis")!!.method!!.copy(
        margins = EventOffsets(fajr = 21, sunrise = 58, dhuhr = 79, asr = 31, maghrib = 18, isha = 20),
        endOfEatingMarginSeconds = 0,
    )
    private val later = EventOffsets(fajr = 3, sunrise = 3, dhuhr = 3, asr = 3, maghrib = 3, isha = 3)

    private fun member(id: String, rank: Int, offsets: EventOffsets = EventOffsets()) =
        Member(id, "timetable_cautious", muis.copy(id = id, authorityMinutes = offsets), rank)

    private fun cautious(vararg members: Member) = RegistryEntry(
        id = "test.cautious", shortNameKey = "timetable_cautious", entryClass = EntryClass.C, about = AboutTemplate.CAUTIOUS,
        members = members.toList(), school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.GLOBAL,
    )

    private val twoMembers = cautious(member("test.a", 1), member("test.b", 2, later))

    private fun gate(entry: RegistryEntry) = Gate(roots, lookup = { if (it == entry.id) entry else Registry.byId(it) })

    private val header = (GateManifest.HEADER + "member").joinToString("\t")

    private fun row(path: String, member: String?) =
        "$path\ttest.cautious\t1.28967\t103.85007\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t\t${member.orEmpty()}"

    private fun manifest(vararg rows: String) = GateManifest.parse("c.tsv", (listOf(header) + rows).joinToString("\n"))

    /** The fixture's first five MUIS days with every time [minutes] later, as `archive/<name>`. */
    private fun shifted(name: String, minutes: Int): String {
        val lines = fixture.resolve("open/SG-MUIS/muis-2026-02-a.txt").readLines().filter { !it.startsWith("#") }
        archive.resolve("archive").mkdirs()
        archive.resolve("archive/$name").writeText(
            lines.joinToString("\n") { line ->
                val (date, times) = line.split(' ').let { it[0] to it.drop(1) }
                date + times.joinToString("") { t ->
                    val (h, m) = t.split(':').map(String::toInt)
                    val total = h * 60 + m + minutes
                    " %02d:%02d".format(total / 60, total % 60)
                }
            },
        )
        return "archive/$name"
    }

    @AfterTest
    fun cleanUp() {
        archive.deleteRecursively()
    }

    @Test
    fun `two members as printed pass and no maghrib is guarded`() {
        val result = gate(twoMembers).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(shifted("b.txt", 3), "test.b")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(5, s.placeDayCount)
        // Never early and no late end (MUIS's Ramadan minute a day early leaves one Maghrib 2 late).
        for (event in listOf(Event.FAJR, Event.SUNRISE, Event.DHUHR, Event.ASR_STANDARD, Event.MAGHRIB, Event.ISHA)) {
            val e = s.event(event)
            assertEquals(listOf(5, 0, 0), listOf(e.checked, e.early, e.lateEnd), "${event.key}\n${result.report()}")
        }
        assertTrue(s.maghribGuards.isEmpty(), result.report())
        assertEquals(0, s.maghribGuardUnchecked)
        assertTrue("maghribGuard" !in Stamps.stamp(s, "core"))
        assertEquals(0, s.neverEarlyBroken, result.report())
    }

    @Test
    fun `a start before any member's printed time is early and maghrib is no exception`() {
        // test.b prints five minutes after test.a while its method runs three: two minutes it cannot meet, Maghrib's
        // too since ruling R155 (but on the 18th, where MUIS's Ramadan minute a day early puts the engine's 2 later).
        val result = gate(twoMembers).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(shifted("b.txt", 5), "test.b")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(5, s.event(Event.FAJR).early, result.report())
        assertEquals(5, s.event(Event.ISHA).early, result.report())
        assertEquals(4, s.event(Event.MAGHRIB).early, result.report())
        assertEquals(0, s.event(Event.SUNRISE).lateEnd, result.report())
        assertTrue(result.violations().any { "test.cautious maghrib: 4 early" in it }, result.report())
    }

    @Test
    fun `maghrib is checked against the latest member`() {
        // test.b prints one minute after test.a: the latest Maghrib is the bar.
        val close = cautious(member("test.a", 1), member("test.b", 2, EventOffsets(maghrib = 1)))
        val result = gate(close).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(shifted("b.txt", 1), "test.b")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(0, s.event(Event.MAGHRIB).early, result.report())
        // Measured against test.b, not test.a: a minute less late than against test.a's time.
        val againstA = gate(close).evaluate(manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a")))
        assertEquals(
            againstA.entries.getValue("test.cautious").event(Event.MAGHRIB).late.withIndex().sumOf { (m, n) -> m * n } - 5,
            s.event(Event.MAGHRIB).late.withIndex().sumOf { (m, n) -> m * n },
        )
    }

    @Test
    fun `a less-followed member's table alone checks maghrib`() {
        // Under the 26 Sep cap these days were unchecked; since ruling R155 test.b's later Maghrib is shown and checked.
        val result = gate(twoMembers).evaluate(manifest(row(shifted("b.txt", 3), "test.b")))
        val s = result.entries.getValue("test.cautious")
        assertEquals(listOf(5, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), result.report())
        assertTrue(s.maghribGuards.isEmpty(), result.report())
        assertEquals(0, s.neverEarlyBroken, result.report())
    }

    // ── Ruling R156: the far-north guard ──

    private val point = "1.28967,103.85007"
    private val zone = TimeZone.of("Asia/Singapore")
    private val dates = (14..18).map { LocalDate(2026, 2, it) }

    /**
     * MUIS's method held to a day of twice 400 minutes around its Dhuhr (as Diyanet's five-hour winter day holds
     * Trondheim's): in Singapore its Maghrib some 35 minutes after its sunset, and its sunrise as far before.
     */
    private fun held(id: String, rank: Int) = Member(id, "timetable_cautious", muis.copy(id = id, dayAroundDhuhrMinutes = 400), rank)

    /** An invented clock rule whose Maghrib, an estimate, is six and a half hours after noon: after Singapore's sunset. */
    private data object EstimatedMaghrib : ClockRule {
        override fun on(date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTimes {
            val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
            val noon = SunClock(point.lat, point.lon, date, offset, SunModel.EXACT).transit()
            return ClockTimes(
                fajr = null, sunrise = null, dhuhr = null, asrStandard = null, asrHanafi = null,
                maghrib = ClockTime(noon + 6.5 * 3600, estimated = true), isha = null, endOfEating = null, sunset = null, noSunset = false,
            )
        }
    }

    private fun ruled(id: String, rank: Int) = Member(id, "timetable_cautious", muis.copy(id = id, clockRule = EstimatedMaghrib), rank)

    /** [member]'s own days on the fixture's five dates as the engine gives them at the rows' point, printed as `archive/<name>`. */
    private fun engineTable(entry: RegistryEntry, member: String, name: String): String {
        val resolution = Registry.resolveEntry(entry, Place(1.28967, 103.85007, "Asia/Singapore", ""))
        val index = resolution.members.indexOfFirst { it.id == member }
        fun clock(at: Instant) = at.toLocalDateTime(zone).let { "%02d:%02d".format(it.hour, it.minute) }
        archive.resolve("archive").mkdirs()
        archive.resolve("archive/$name").writeText(
            dates.joinToString("\n") { date ->
                val day = DayPipeline.members(resolution, date, zone)[index]
                "$date " + listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha).joinToString(" ") { clock(it) }
            },
        )
        return "archive/$name"
    }

    @Test
    fun `a member's bar is its printed maghrib unless its own rule set it and it is not the most followed`() {
        val day = DayPipeline.members(Registry.resolveEntry(twoMembers, Place(1.28967, 103.85007, "Asia/Singapore", "")), dates[0], zone)[0]
        assertEquals(MaghribBar.Printed, maghribBar(day, mostFollowed = false, sunSets = true))
        assertEquals(MaghribBar.Printed, maghribBar(day, mostFollowed = true, sunSets = false))
        // A member's own sun model setting the sun where the real sun does not (item E): nothing to check it by.
        assertEquals(MaghribBar.None, maghribBar(day, mostFollowed = false, sunSets = false))
        val sun = day.maghrib - 30.minutes
        val heldDay = day.copy(maghribHeld = true, maghribBeforeHold = sun)
        assertEquals(MaghribBar.Sun(sun), maghribBar(heldDay, mostFollowed = false, sunSets = true))
        assertEquals(MaghribBar.Printed, maghribBar(heldDay, mostFollowed = true, sunSets = true))
        assertEquals(MaghribBar.None, maghribBar(heldDay, mostFollowed = false, sunSets = false))
        val ruledDay = day.copy(setByRule = setOf(Prayer.MAGHRIB))
        assertEquals(MaghribBar.None, maghribBar(ruledDay, mostFollowed = false, sunSets = true))
        assertEquals(MaghribBar.Printed, maghribBar(ruledDay, mostFollowed = true, sunSets = true))
    }

    @Test
    fun `a held maghrib of a less-followed member is checked at its sun time and its gap recorded`() {
        val entry = cautious(member("test.a", 1), held("test.h", 2))
        val result = gate(entry).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(engineTable(entry, "test.h", "h.txt"), "test.h")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(listOf(5, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), result.report())
        val guard = s.maghribGuards.getValue("test.h")
        assertEquals(listOf(5, 5), listOf(guard.days, guard.atSun))
        // Its printed Maghrib is its held one, half an hour and more after the Maghrib shown: as printed it would be early.
        assertTrue(guard.most <= -30, "shown ${guard.least}..${guard.most} min after test.h's printed Maghrib")
        assertEquals(setOf("test.h"), s.maghribGuards.keys)
        assertEquals(0, s.neverEarlyBroken, result.report())
        assertTrue("test.h's printed Maghrib not its own from the sun on 5 days" in result.report(), result.report())
        @Suppress("UNCHECKED_CAST")
        val stamp = Stamps.stamp(s, "core")["maghribGuard"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val h = (stamp["members"] as Map<String, Map<String, Any?>>).getValue("test.h")
        assertEquals(listOf(5, 5, listOf("2026-02-14..2026-02-18")), listOf(h["days"], h["checkedAtSun"], h["dates"]))
        assertEquals(null, stamp["uncheckedDays"])
    }

    @Test
    fun `a held member's table alone checks maghrib at its sun time`() {
        val entry = cautious(member("test.a", 1), held("test.h", 2))
        val result = gate(entry).evaluate(manifest(row(engineTable(entry, "test.h", "h.txt"), "test.h")))
        val s = result.entries.getValue("test.cautious")
        assertEquals(listOf(5, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), result.report())
        // The Maghrib shown is test.a's, the sun's own minute: test.h's from the sun, give or take the real sunset's minute.
        assertTrue(s.event(Event.MAGHRIB).worst <= 1, result.report())
        assertEquals(5, s.maghribGuards.getValue("test.h").atSun)
        assertEquals(0, s.neverEarlyBroken, result.report())
    }

    @Test
    fun `the most-followed member's held maghrib counts as printed`() {
        val entry = cautious(held("test.h", 1), member("test.a", 2))
        val result = gate(entry).evaluate(
            manifest(row(engineTable(entry, "test.h", "h.txt"), "test.h"), row("open/SG-MUIS/muis-2026-02-a.txt", "test.a")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(listOf(5, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), result.report())
        // Its held minute is shown, exactly as it prints it.
        assertEquals(5, s.event(Event.MAGHRIB).late[0], result.report())
        assertTrue(s.maghribGuards.isEmpty(), result.report())
    }

    @Test
    fun `a maghrib a rule sets is not the bar and a date no member's time checks is recorded not broken`() {
        val entry = cautious(member("test.a", 1), ruled("test.r", 2))
        val r = engineTable(entry, "test.r", "r.txt")
        val both = gate(entry).evaluate(manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(r, "test.r")))
        val s = both.entries.getValue("test.cautious")
        assertEquals(listOf(5, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), both.report())
        val guard = s.maghribGuards.getValue("test.r")
        assertEquals(listOf(5, 0), listOf(guard.days, guard.atSun))
        assertTrue(guard.most < 0, "shown ${guard.least}..${guard.most} min after test.r's printed Maghrib")
        assertEquals(0, s.neverEarlyBroken, both.report())

        val alone = gate(entry).evaluate(manifest(row(r, "test.r")))
        val t = alone.entries.getValue("test.cautious")
        assertEquals(listOf(0, 0), listOf(t.event(Event.MAGHRIB).checked, t.event(Event.MAGHRIB).early), alone.report())
        assertEquals(5, t.maghribGuardUnchecked)
        assertEquals(0, t.broken)
        assertTrue(alone.violations().isEmpty(), alone.report())
        assertTrue("5 cells no member's time checks (2026-02-14..2026-02-18; ruling R156)" in alone.report(), alone.report())
        @Suppress("UNCHECKED_CAST")
        val stamp = Stamps.stamp(t, "core")["maghribGuard"] as Map<String, Any?>
        assertEquals(listOf(5, listOf("2026-02-14..2026-02-18")), listOf(stamp["uncheckedDays"], stamp["uncheckedDates"]))
    }

    @Test
    fun `a day with no maghrib end is recorded with its dates and never broken`() {
        // Ruling R158, as at Luleå: test.s's Isha comes two minutes after its own Maghrib, before test.b's Maghrib (three
        // minutes later), the one shown; no end in play is after it, so the day shows none for Maghrib. Recorded in the
        // report and the stamp, never broken.
        val short = Member("test.s", "timetable_cautious", muis.copy(id = "test.s", isha = IshaRule.AfterMaghrib(2)), 1)
        val entry = cautious(short, member("test.b", 2, later))
        val result = gate(entry).evaluate(
            manifest(row(engineTable(entry, "test.s", "s.txt"), "test.s"), row(engineTable(entry, "test.b", "b.txt"), "test.b")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(listOf(5, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), result.report())
        assertEquals(5, s.maghribNoEnd, result.report())
        assertEquals(0, s.neverEarlyBroken, result.report())
        assertTrue(
            "maghrib: no end shown on 5 place-days (2026-02-14..2026-02-18; ruling R158: no end in play after the Maghrib shown)" in
                result.report(),
            result.report(),
        )
        @Suppress("UNCHECKED_CAST")
        val stamp = Stamps.stamp(s, "core")["maghribNoEnd"] as Map<String, Any?>
        assertEquals(listOf(5, listOf("2026-02-14..2026-02-18")), listOf(stamp["days"], stamp["dates"]))
        // Two members as printed: every day ends Maghrib, and the stamp says nothing.
        val plain = gate(twoMembers).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(shifted("b.txt", 3), "test.b")),
        ).entries.getValue("test.cautious")
        assertEquals(0, plain.maghribNoEnd)
        assertEquals(null, Stamps.stamp(plain, "core")["maghribNoEnd"])
    }

    @Test
    fun `the stamp records each member's checked dates at its point`() {
        // Ruling R115: a page shows only days every member's table was checked on at that place,
        // so the stamp carries each member's checked dates by the point its rows were read at.
        val result = gate(twoMembers).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(shifted("b.txt", 3), "test.b")),
        )
        val s = result.entries.getValue("test.cautious")
        val stamp = Stamps.stamp(s, "core")
        assertEquals(listOf("2026-02-14..2026-02-18"), stamp["checked"])
        @Suppress("UNCHECKED_CAST")
        val members = stamp["members"] as Map<String, Map<String, Any?>>
        assertEquals(setOf("test.a", "test.b"), members.keys)
        assertEquals(listOf("2026-02-14..2026-02-18"), members.getValue("test.a")["checked"])
        assertEquals(
            mapOf("1.28967,103.85007" to listOf("2026-02-14..2026-02-18")),
            members.getValue("test.b")["points"],
        )
    }

    // ── Ruling R117: a member's days left out for a recorded reason ──

    private fun excuse(member: String, days: String, kind: String = "fault", at: String = "*") =
        "@excuse\ttest.cautious\t$member\t$at\t$days\t$kind\ta made-up reason"

    /** test.b's table three minutes later, without [left] (its fault days). */
    private fun bWithout(vararg left: String): String {
        val path = shifted("b.txt", 3)
        val file = archive.resolve(path)
        file.writeText(file.readLines().filter { line -> left.none { line.startsWith(it) } }.joinToString("\n"))
        return path
    }

    @Suppress("UNCHECKED_CAST")
    private fun members(result: GateResult) =
        Stamps.stamp(result.entries.getValue("test.cautious"), "core")["members"] as Map<String, Map<String, Any?>>

    @Test
    fun `an excused member day is recorded at its point with its kind and nothing else changes`() {
        val rows = arrayOf(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(bWithout("2026-02-16", "2026-02-17"), "test.b"))
        val plain = gate(twoMembers).evaluate(manifest(*rows))
        val excused = gate(twoMembers).evaluate(manifest(*rows, excuse("test.b", "2026-02-16"), excuse("test.b", "2026-02-17", "unpublished")))
        assertEquals(mapOf(point to mapOf("fault" to listOf("2026-02-16"), "unpublished" to listOf("2026-02-17"))), members(excused).getValue("test.b")["excused"])
        assertEquals(null, members(excused).getValue("test.a")["excused"])
        assertEquals(listOf("2026-02-14..2026-02-15", "2026-02-18"), members(excused).getValue("test.b")["checked"])
        // The excuse checks nothing and excuses no cell: the stamps differ in the excused days alone.
        val withoutExcused = Stamps.stamp(excused.entries.getValue("test.cautious"), "core").toMutableMap().also { stamp ->
            @Suppress("UNCHECKED_CAST")
            stamp["members"] = (stamp["members"] as Map<String, Map<String, Any?>>).mapValues { (_, m) -> m - "excused" }
        }
        assertEquals(Stamps.stamp(plain.entries.getValue("test.cautious"), "core"), withoutExcused)
        assertTrue("excused for test.b (recorded faults, ruling R117): 2026-02-16, at 1 point" in excused.report(), excused.report())
    }

    @Test
    fun `an excuse at one point leaves the member's other points alone`() {
        val far = "1.35,103.82"
        val elsewhere = "open/SG-MUIS/muis-2026-02-a.txt\ttest.cautious\t1.35\t103.82\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t\ttest.b"
        val b = bWithout("2026-02-16")
        val result = gate(twoMembers).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(b, "test.b"), elsewhere, excuse("test.b", "2026-02-16", at = point)),
        )
        assertEquals(mapOf(point to mapOf("fault" to listOf("2026-02-16"))), members(result).getValue("test.b")["excused"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("2026-02-14..2026-02-18"), (members(result).getValue("test.b")["points"] as Map<String, Any?>)[far])
    }

    @Test
    fun `an excused day the member's rows hold is a mistake`() {
        // A day is checked or excused, never both: the line contradicts the rows.
        val error = assertFailsWith<GateError> {
            gate(twoMembers).evaluate(manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), excuse("test.a", "2026-02-15..2026-02-16")))
        }
        assertTrue("c.tsv:3: test.a is checked at $point on 2026-02-15..2026-02-16, which this line excuses" in error.problems, error.message)
    }

    @Test
    fun `an excuse names a member of a cautious entry with rows`() {
        val a = row("open/SG-MUIS/muis-2026-02-a.txt", "test.a")
        val unknown = assertFailsWith<GateError> { gate(twoMembers).evaluate(manifest(a, excuse("test.z", "2026-02-16"))) }
        assertTrue("c.tsv:3: test.z is not a member of test.cautious (test.a test.b)" in unknown.problems, unknown.message)
        val rowless = assertFailsWith<GateError> { gate(twoMembers).evaluate(manifest(a, excuse("test.b", "2026-02-16"))) }
        assertTrue("c.tsv:3: no row of test.b for test.cautious" in rowless.problems, rowless.message)
        val single = GateManifest.parse(
            "s.tsv",
            listOf(header, "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF\tdaily\t-\ttest\t\t", "@excuse\tsg.muis\tsg.muis\t*\t2026-02-16\tfault\tr")
                .joinToString("\n"),
        )
        val notCautious = assertFailsWith<GateError> { Gate(roots).evaluate(single) }
        assertTrue("s.tsv:3: sg.muis is not cautious; only a member's days are excused" in notCautious.problems, notCautious.message)
    }

    @Test
    fun `a cautious row names its member and no other row does`() {
        val missing = assertFailsWith<GateError> { gate(twoMembers).evaluate(manifest(row("open/SG-MUIS/muis-2026-02-a.txt", null))) }
        assertTrue("names the member whose table it is (test.cautious: test.a test.b)" in missing.message!!, missing.message)
        val unknown = assertFailsWith<GateError> { gate(twoMembers).evaluate(manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.z"))) }
        assertTrue("c.tsv:2" in unknown.message!!, unknown.message)
        val single = GateManifest.parse(
            "s.tsv",
            listOf(header, "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF\tdaily\t-\ttest\t\ttest.a").joinToString("\n"),
        )
        val error = assertFailsWith<GateError> { Gate(roots).evaluate(single) }
        assertTrue("sg.muis is not cautious" in error.message!!, error.message)
    }
}
