package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Member
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

/**
 * Ruling R38 on a synthetic cautious entry made from MUIS: member `test.a` (most-followed) is
 * MUIS's method and prints the open MUIS rows of the fixture; member `test.b` is the same method
 * three minutes later on every time, and prints those rows shifted. Their Maghribs spread beyond
 * the two-minute agreement, so the engine caps Maghrib at `test.a`'s.
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
    fun `two members as printed pass and the cap's gap to each is recorded`() {
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
        assertEquals(0, s.maghribUnchecked)
        val a = s.maghribGaps.getValue("test.a")
        val b = s.maghribGaps.getValue("test.b")
        assertEquals(listOf(5, 5), listOf(a.capped, b.capped))
        assertTrue(a.least >= 0, "the cap is never before the most-followed member's Maghrib")
        assertTrue(b.most < 0, "the cap is before the later member's Maghrib on every day: ${b.least}..${b.most}")
        @Suppress("UNCHECKED_CAST")
        val cap = Stamps.stamp(s, "core")["maghribCap"] as Map<String, Any>
        assertEquals(0, cap["uncheckedDays"])
    }

    @Test
    fun `a start before any member's printed time is early but a capped maghrib answers only to the most-followed`() {
        // test.b prints five minutes after test.a while its method runs three: two minutes it cannot meet.
        val result = gate(twoMembers).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(shifted("b.txt", 5), "test.b")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(5, s.event(Event.FAJR).early, result.report())
        assertEquals(5, s.event(Event.ISHA).early, result.report())
        assertEquals(0, s.event(Event.MAGHRIB).early, result.report())
        assertEquals(0, s.event(Event.SUNRISE).lateEnd, result.report())
    }

    @Test
    fun `maghribs within the agreement are checked against the latest member`() {
        // test.b prints one minute after test.a: the members agree, and the latest Maghrib is the bar.
        val close = cautious(member("test.a", 1), member("test.b", 2, EventOffsets(maghrib = 1)))
        val result = gate(close).evaluate(
            manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a"), row(shifted("b.txt", 1), "test.b")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(0, s.maghribGaps.getValue("test.b").capped)
        assertEquals(0, s.event(Event.MAGHRIB).early, result.report())
        // Measured against test.b, not test.a: a minute less late than against test.a's time.
        val againstA = gate(close).evaluate(manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a")))
        assertEquals(
            againstA.entries.getValue("test.cautious").event(Event.MAGHRIB).late.withIndex().sumOf { (m, n) -> m * n } - 5,
            s.event(Event.MAGHRIB).late.withIndex().sumOf { (m, n) -> m * n },
        )
    }

    @Test
    fun `a spread without the most-followed member's table is unchecked and breaks the gate`() {
        val three = cautious(member("test.a", 1), member("test.b", 2, later), member("test.c", 3))
        val result = gate(three).evaluate(
            manifest(row(shifted("b.txt", 3), "test.b"), row(shifted("c.txt", 0), "test.c")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(5, s.maghribUnchecked)
        assertTrue(result.violations().any { "test.cautious maghrib: 5 capped days unchecked" in it }, result.report())
    }

    /**
     * Only `test.b`'s table held: nothing printed shows the spread, but the engine's own members
     * are three minutes apart, so it capped at `test.a`'s Maghrib. Checked against `test.b`'s time
     * it would read as early; it is unchecked instead.
     */
    @Test
    fun `a capped maghrib with only a less-followed member held is unchecked not early`() {
        val result = gate(twoMembers).evaluate(manifest(row(shifted("b.txt", 3), "test.b")))
        val s = result.entries.getValue("test.cautious")
        assertEquals(5, s.maghribUnchecked, result.report())
        assertEquals(0, s.event(Event.MAGHRIB).checked)
        assertEquals(0, s.event(Event.MAGHRIB).early)
        assertEquals(0, s.event(Event.FAJR).early, result.report())
        assertTrue(result.violations().any { "test.cautious maghrib: 5 capped days unchecked" in it }, result.report())
        assertTrue(result.violations().none { "maghrib: 5 early" in it }, result.report())
    }

    /**
     * `test.b` and `test.c` held, both three minutes after `test.a` and agreeing with each other in
     * print, so nothing printed shows a spread; the engine's own members are three minutes apart and
     * it capped at `test.a`'s Maghrib, whose table is not held. Unchecked, not early.
     */
    @Test
    fun `members agreeing in print without the most-followed still leave a capped maghrib unchecked`() {
        val three = cautious(member("test.a", 1), member("test.b", 2, later), member("test.c", 3, later))
        val result = gate(three).evaluate(
            manifest(row(shifted("b.txt", 3), "test.b"), row(shifted("c.txt", 3), "test.c")),
        )
        val s = result.entries.getValue("test.cautious")
        assertEquals(5, s.maghribUnchecked, result.report())
        assertEquals(listOf(0, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), result.report())
        assertEquals(0, s.event(Event.FAJR).early, result.report())
        assertTrue(result.violations().none { "maghrib: 5 early" in it }, result.report())
    }

    @Test
    fun `the most-followed member's table alone checks the capped maghrib`() {
        val result = gate(twoMembers).evaluate(manifest(row("open/SG-MUIS/muis-2026-02-a.txt", "test.a")))
        val s = result.entries.getValue("test.cautious")
        assertEquals(0, s.maghribUnchecked)
        assertEquals(listOf(5, 0), listOf(s.event(Event.MAGHRIB).checked, s.event(Event.MAGHRIB).early), result.report())
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

    private val point = "1.28967,103.85007"

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
